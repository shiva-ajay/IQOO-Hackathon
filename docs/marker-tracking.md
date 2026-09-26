# Marker + tracking (M1b / M2)

Fixy points at a part (a VLM box) and the marker stays locked on it while the phone moves (an OpenCV
tracker). This file defines the coordinate spaces, the mapping choice, the tracker parameters and the
measured results.

## 1. Coordinate spaces

```
 camera buffer (sensor orientation, 1280x720 YUV)
   │  crop to ImageProxy.cropRect (the shared ViewPort = exactly what the preview shows)
   │  rotate by imageInfo.rotationDegrees (upright)
   ├─► ANALYSIS space A: downscaled to 480 px long side, gray (Y plane)      ← tracker, MarkerState
   └─► KEYFRAME space K: RGB, 448 px long side, both sides multiples of 32   ← the VLM's image
                                          │
 VLM box (0..1000 or px of K) ──BoxMapper──► K px ──(per-axis scale)──► A px ──(FILL_CENTER)──► VIEW px
```

| Space | What | Size (iQOO 15, 20:9 screen) | Who uses it |
|---|---|---|---|
| **Analysis (A)** | Upright ViewPort crop of the analysis frame, gray, 480 px long side | ~216 × 480 | `FrameRingBuffer`, `FlowTracker`, `MarkerState.box` |
| **Keyframe (K)** | Same crop, upright RGB JPEG, long side 448, sides rounded to ×32 (MNN's resize is then a no-op) | ~192 × 448 | The VLM; `BoxMapper` input |
| **View (V)** | `PreviewView` pixels, `FILL_CENTER` | full screen | `MarkerOverlay` only |

- **K → A is a pure per-axis scale** (`ax = kx·aW/kW`, `ay = ky·aH/kH`). Both are resizes of the same
  upright crop; the ×32 rounding makes the two axis factors differ by a few %, which is why the scale is
  per-axis.
- **A → V** is `FILL_CENTER`: uniform scale `max(vW/aW, vH/aH)`, centred. Because the ViewPort crop has
  the view's aspect ratio, this is a plain scale in practice. The general formula is kept so a rounding
  mismatch still maps correctly.
- The tracker works only in A. The UI maps A → V at draw time (`BoxMapper.fillCenter`, cached per
  view size).

### Mapping choice: `UseCaseGroup` + `previewView.viewPort` (not `CoordinateTransform`)

- With a shared ViewPort, CameraX gives Preview and ImageAnalysis **the same field of view**: the
  analysis `ImageProxy.cropRect` is exactly the region the preview displays. So:
  - The VLM sees what the user sees ("Fixy sees what you see"). A box can never land on a part
    that is off-screen.
  - A → V needs no CameraX transform objects, main-thread `outputTransform` calls or per-frame
    matrices. It is a scale computed from two sizes.
- `CoordinateTransform(ImageProxyTransformFactory…, previewView.outputTransform)` also works. But it
  needs an `ImageProxy` and a main-thread `OutputTransform` at the same moment, and it maps a
  full-sensor frame onto a view that only shows part of it. That breaks the "VLM sees what you see"
  property.
- Both streams use **16:9** (1280×720 analysis). On a portrait phone the sensor's long axis is the
  screen's vertical axis, so 16:9 and 4:3 show the same visible field of view. 16:9 just wastes fewer
  pixels outside the crop (the visible crop is ~580×1280 of 720×1280 upright).
- Binding waits for the `PreviewView` to be laid out (`viewPort` is null before that).

### Verifying the mapping (debug)

- `adb shell am start -n com.fixlens/.app.MainActivity --ez testbox true` draws the A-space rect
  (25%,25%)–(75%,75%). It must appear centred and half-size on screen.
- `--ez freeze true` shows each question's keyframe at 50% over the live preview. With the phone
  still, it must line up with the preview; that checks the crop and rotation. It also draws the raw
  VLM box read as 0–1000 (amber) and as pixels (cyan).

## 2. Confirmed VLM coordinate scale: **0..1000 (`NORMALIZED_1000`)**

Verified on the iQOO 15 with `tools/m1/run_ground_test.py`. It runs the app's exact prompts on the phone with the
same MNN build and config, reads each box both ways, and draws both on the image:
- Synthetic shapes (red circle / blue square at known places, 448×352 and 192×448 keyframes): read as 0..1000,
  **12/12** boxes land on the shape (IoU 0.60–0.89); read as pixels, 0/12.
- Test photos (washer panels, engine bay), explicit target phrase: 0..1000 **8/11** centred on the part, pixels 0/11.
- In the app: the analysis-space test rect (25%,25%)–(75%,75%) (`--ez testbox true`) draws exactly centred at half
  the screen size (edges at 25%/75% of 1440×3168).

### The model graph had to be fixed first (M-RoPE)

Before the fix, grounding was broken, not just imprecise: 0/12 on the synthetic shapes. The boxes were near
placeholders like `[100,100,200,200]`, while the model still named the shapes and colours correctly. Full-precision
vision (`mllm.precision/memory = normal`) didn't help.

Cause: taobao-mnn's pre-converted `llm.mnn` (Qwen3-VL 4B and 2B) computes the rotary angles with Qwen2-VL-style
**chunked** M-RoPE: channels 0–23 by t, 24–43 by h, 44–63 by w. Qwen3-VL is trained with **interleaved** M-RoPE
(`mrope_interleaved`): channel i uses h if i%3==1 and w if i%3==2 (for i < 60), and t otherwise. For text tokens
t == h == w, so chat is unaffected. For image tokens, h only reaches θ ≤ 3e-3 and w only θ ≤ 2.5e-5, so the LLM
can barely tell rows apart and can't tell columns apart at all. (The MNN C++ side, i.e. patching, pos-embed
interpolation, mrope ids and deepstack, matches HF and was fine.)

Fix: `tools/mnn-patches/qwen3vl_mrope_fix.sh <model_dir>`. It rewrites the rotary subgraph losslessly with the
flatbuffer object API, as `pos_t·θT + pos_h·θH + pos_w·θW` with θ masked per axis in interleaved order (3 constants,
Concat → 2 Adds). It also regenerates θ exactly from `rope_theta = 5e6`; the shipped constants were rounded to
6 decimals. The weights (`llm.mnn.weight`) are untouched, and a `MNNDump2Json` diff shows no other semantic change.
The original stays as `llm.mnn.chunked`. `tools/push_models.sh` applies it before pushing.

## 3. Tracker parameters (`tracking/FlowTracker.kt`)

| Parameter | Value | Why |
|---|---|---|
| Analysis space | 218×480 gray (Y plane of 1280×720 YUV, ViewPort crop, upright) | Cheap LK, still sharp enough for corners |
| Ring buffer | 180 frames (~6 s) | Keyframe → box line takes 4.5–6 s; see failure cases |
| Seed corners | `goodFeaturesToTrack` max 80, quality 0.01, min distance 5 px | |
| Small targets | Patch widened ×1.3 → ×1.8 → ×2.5 until ≥ 20 corners; the box keeps its place in the patch | A dipstick handle alone has few corners |
| LK | `calcOpticalFlowPyrLK`, window 21, 3 pyramid levels, forward-backward error ≤ 1.5 px | |
| Model | Seed → now: RANSAC homography (3 px, ≥ 8 pts, sanity-checked: convex, area ×1/16..×16 vs seed, ≤ ×2 per step, no jumps) → similarity → median shift | One model from the seed frame, so the box shape doesn't drift |
| Smoothing | EMA α 0.4 on centre and size (none during fast-forward) | |
| Confidence | inlier ratio × min(1, inliers / 15); low below 0.3 | |
| Re-detect | When points < 50% of the count after the last (re)detect | |
| Fast-forward | Keyframe's buffered frame → now in ≤ 45 LK steps (stride grows with the gap) | |
| Lost | Hold 500 ms (dashed marker), then fade; the last good frame stays the LK anchor, so a brief occlusion recovers without the VLM | |
| Re-ground | Low confidence > 1 s → silent VLM side request (`Keep.Never`, rolled back), only when Fixy is idle, ≤ 1 per 3 s, ≤ 5 per question; skipped while the view is blank (std < 12) | |

## 4. Measured numbers (iQOO 15, 2026-09-26)

| What | Value | How |
|---|---|---|
| Analysis thread, YUV → gray → ring buffer | 5.8–7.7 ms/frame avg, 30 fps | `Analysis:` log, before tracking |
| **Tracker per frame** | **p50 1.6–3.6 ms, p95 3.9–8.6 ms** (budget < 10 ms) | `Tracker:` log during the user's handheld test |
| Fast-forward on seed | 139–179 buffered frames in 35–45 steps, **66–90 ms** | `Tracker seed` log |
| Keyframe capture (next frame + RGB + JPEG) | 32–41 ms total, 7–19 ms conversion | `Keyframe` / `Turn` logs |
| **End of speech → box line** (192×448 camera keyframe) | **4.4–5.6 s** (vision 0.4–0.7 s, prefill 1.7–2.3 s, ~2 s decoding the box line) | `Turn` log |
| End of speech → first word / whole answer | 4.7–5.7 s / 6.3–8.1 s | `Turn` log |
| Re-ground (lost → new box) | 5.2–6.2 s | `Re-ground` log |
| Grounding accuracy after the M-RoPE fix | synthetic 12/12; photos with a target phrase 8/11; natural question without a target 5/11 | `tools/m1/run_ground_test.py` |

The box line costs ~2 s of decoding before the first word (~25 tokens; Qwen writes digits one token each).

## 5. Known failure cases

- **Vague questions point at the whole object.** With no target phrase ("how do I open this laptop?"), the
  model boxes the whole device, and the chip label echoes the placeholder target. Target phrases (KB steps, M4)
  and multi-part pointing are the fix; that's next.
- **Neighbouring parts:** adjacent buttons get confused (start/pause vs power on the Heran panel), and so do similar caps
  (washer fluid vs coolant).
- **Slow VLM vs ring buffer:** re-grounds that take > 6 s seed on the oldest buffered frame ("keyframe no longer
  buffered"); if the phone moved meanwhile, the first box is off until the next re-ground.
- **Lost during fast-forward:** if the phone moved a lot while Fixy was thinking, the seed is lost at once and a
  re-ground follows immediately.
- **Textureless targets** (< 6 corners even ×2.5 widened): the marker is shown but not tracked.

## 6. Multi-part pointing (2026-09-27)

One answer can point at several parts. The VLM writes a JSON list (`FixyPrompts.GroundingStyle.Parts`, the default):
a box per part, or a point for a small single part. `GroundingParser` streams each part out the moment its braces
close, so markers appear one by one while the model is still writing. It also accepts fenced lists, `{"points":[…]}`
wrappers, and lists written without the opening `[`. `FlowTracker` tracks all parts as **one group**: one set of
corners over the area they span and one motion model applied to every target, so the cost per frame is the same
as for a single part. Parts that stream in later join the group, with corners of their own found around where
they are now. The overlay draws a box per part (a numbered ring per point), one hole per part in the dimmed mask,
and one chip per kind of part ("Screw ×8"). Re-grounding asks for all parts again.

Measured with `tools/m1/run_multi_test.py` / `run_zoom_test.py` / `run_prompt_check.py` (448-px keyframes):

| Case | Result |
|---|---|
| One part with a target phrase, Parts prompt (11 photos) | **8/11**, same as the single-box prompt; format 11/11 |
| Several medium parts (engine-bay caps) | oil cap correct every time; coolant / washer caps hit or miss (2/3, then 1/3) |
| Memory door on a ThinkPad underside ("how do I get to the memory?") | one box, exactly on the door |
| 13 laptop screws, Fixy prompt | **2–3/13**: the model invents a tidy symmetric grid instead of looking |
| 13 laptop screws, Qwen's plain "Point to every screw" with no persona | 8/13 best case; a 2× zoomed crop does **not** help (1/4, 2/4) |

**Decision (user, 2026-09-27):** tiny repeated parts like screws are not pointed at one by one. The prompt asks
for one box around the area that holds them, and the spoken step (KB) says how many. Multi-part pointing is for
a few distinct, medium-sized parts. Target phrases (KB steps, M4) are what make grounding dependable; free-form
questions stay hit-and-miss.

Cost: ~25 decode tokens per box, ~17 per point (Qwen writes digits one token each), so ~1.4–2 s per part on the
CPU. The first marker appears after ~5 s (cool phone); the spoken reply starts after the list.

**Thermal:** after ~1 h of back-to-back VLM runs the phone reached 41 °C and prefill slowed from ~4 s to ~7 s
(first marker 15–20 s). Let it cool before a demo, and avoid long benchmark runs right before one.
