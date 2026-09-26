# Task: FixLens: point-at-the-part marker (grounding) + live tracker

This is a fresh session. Start by reading these files in full:
1. `CLAUDE.md`, especially §5 (two-speed architecture), §9 (VLM I/O contract), §10 (tracker), §12 (rules), §15 notes
2. `.claude/STACK.md` (OpenCV is already an approved dependency)
3. `PLAN.md`, sections **M1** (steps 3–6) and **M2**
4. `docs/sessions-plan.md` and `docs/mnn-donor-notes.md` (how the VLM, KV cache and prompts work now)

Then read the current code before changing anything:
`camera/FrameGrabber.kt`, `ui/CameraScreen.kt` (see `CameraPreview`), `app/FixLensViewModel.kt`
(see `onQuestion`), `guide/ConversationContext.kt`, `guide/FixyPrompts.kt` and `vision/VlmEngine.kt`.

## Where we are

The Round-1 demo works: CameraX preview, Silero VAD + Moonshine live captions, Qwen3-VL-4B on MNN
through our JNI bridge, streamed answers, and sessions with KV-cache memory. **What's missing** is the
core novelty: **Fixy points at the exact part and the marker stays locked on it while the phone moves.**
None of this exists yet: no `GroundingParser`, `BoxMapper`, `MarkerOverlay`, `FrameRingBuffer` or
`FlowTracker`, and no OpenCV. The coordinate scale was never formally confirmed (M0 is still unticked).

**Goal:** a spoken question like "where do I check the oil?" gives a box line from the VLM. A marker then
appears on that part and **stays on it** while you move the phone around the A3 printout or engine bay.
If tracking fails, it re-grounds on its own.

Out of scope: KB/guide state machine (M4) and TTS (M3). Don't touch them. Keep sessions/KV memory
working exactly as it does now.

## Pre-approved decisions (don't ask again)

- Add **OpenCV Android** (`org.opencv:opencv`, latest 4.x on Maven Central) via the version catalog.
- Add **JUnit 4** as a `testImplementation`, for JVM unit tests only.
- Switch `ImageAnalysis` to **`OUTPUT_IMAGE_FORMAT_YUV_420_888`**. The Y plane is a free grayscale
  frame for the tracker. Convert to RGB only when a keyframe is needed (`ImageProxy.toBitmap()`),
  not on every frame. Right now `toBitmap()` runs 30×/s and wastes CPU the VLM needs.

Anything else new (dependencies, big architecture changes): ask first.

---

## Build in this order (every step runs on the phone before moving on)

### Step 1: One coordinate system, defined up front
Write this down in `docs/marker-tracking.md` before coding:
- **Analysis space:** the upright analysis frame after rotation (the space both the keyframe and
  tracker frames use), at the tracker's working resolution (~480 px long side).
- **Keyframe space:** `FrameGrabber.saveKeyframe` rotates upright and scales to a multiple of 32
  (448 long side). Keyframe ↔ analysis space is then a pure scale. Keep it that way.
- **View space:** `PreviewView` with `FILL_CENTER`. Map analysis → view with CameraX
  (`CoordinateTransform(ImageProxyTransformFactory().getOutputTransform(proxy), previewView.outputTransform)`),
  **or** bind Preview + ImageAnalysis in a `UseCaseGroup` with `previewView.viewPort` so both share
  one crop rect and the mapping becomes a scale + offset. Pick one, explain why, and use the same
  aspect ratio for Preview and Analysis.
- The tracker works in analysis space. Only the UI maps to view space, using a cached matrix.

### Step 2: `camera/FrameRingBuffer` + a lighter `FrameGrabber`
- On the analysis thread, each frame: take the Y plane (respect `rowStride`), rotate upright,
  downscale to ~480 px long side, and push `(gray Mat, timestampNs)` into a ring buffer of the last
  ~3 s (~90 frames; reuse Mats and don't allocate per frame).
- Keyframe capture: `saveKeyframe` now returns the file **plus the frame timestamp** and the
  keyframe→analysis scale. It converts only that one frame to RGB. It's fine to wait for the
  next frame (≤ 33 ms).
- Log analysis-thread ms per frame (sampled every 30 frames).

### Step 3: `vision/GroundingParser` (pure Kotlin, unit-tested)
- Stream-safe: feed chunks as they arrive. As soon as the **first line** is complete, emit
  `Box(x1,y1,x2,y2,label)` or `NoBox`. Everything after goes to the answer text.
- Accept these first-line forms:
  - `{"bbox_2d":[...],"label":"..."}`
  - a JSON **array** `[{"bbox_2d":...}]` (Qwen3-VL's native grounding output)
  - markdown fences ```` ```json ````
  - extra whitespace
  - `{"bbox_2d":null}`
- If the first line isn't valid JSON, treat the whole output as text (CLAUDE.md §9).
- The box line is **never shown in captions**. It also must not end up in the saved turn's answer
  text or the session title. It can stay in the KV cache.
- Unit tests with canned outputs: good, fenced, array, null, garbage, split across chunks.

### Step 4: `vision/BoxMapper` (pure Kotlin, unit-tested)
- Model coords → keyframe px. Support **`NORMALIZED_1000`** (expected for Qwen3-VL) and
  **`ABSOLUTE_PIXELS`** (Qwen2.5-VL), selected by config. Clamp, fix swapped corners, and reject
  degenerate boxes.
- Keyframe px → analysis space (scale).
- Unit tests for both modes and for rotation/aspect cases.

### Step 5: Prompt change for grounding
- In `FixyPrompts`, every question turn asks for a box line first. The exact wording is in CLAUDE.md §9.
- Until the KB exists (M4), there's no target phrase. So ask the model to point at *the part the
  user should look at for this question*, or output `{"bbox_2d":null}` if nothing applies.
- Also try Qwen3-VL's native grounding phrasing: *"Locate … and output bbox coordinates in JSON"*.
- Keep the change small and compatible with the KV-cache turn format in `ConversationContext`.
- Add a debug-only way to force a target phrase via the existing adb question hook, e.g.
  `point: yellow ring handle of the engine oil dipstick`.

### Step 6: `ui/MarkerOverlay` + **drawn test box first**
- Compose `Canvas` over the preview:
  - pulsing rounded box
  - dimmed mask outside it
  - small label chip with the part name
  - hold ~500 ms when lost, then fade out
- Match the existing look (`ui/Palette.kt`, `ui/fx/*`).
- **Verification before trusting the model:** a debug toggle draws a fixed test box, e.g. the
  analysis-space rect (25%,25%)–(75%,75%). It must appear exactly centred and half-size on screen.
  Then draw the raw VLM box on a *frozen* keyframe and check it lands on the part. **This is how we
  confirm 0–1000 vs pixels. Record the result.**

### Step 7: `tracking/FlowTracker` with fast-forward
Runs on the analysis thread. Publishes `StateFlow<MarkerState>` = box (analysis space), confidence,
status (Tracking / Holding / Lost), frame timestamp.
- **Seed:** find the ring-buffer frame closest to the keyframe timestamp. Run
  `goodFeaturesToTrack` inside the box, expanded ~30% if there are < 20 corners (small parts like
  a dipstick handle have few features; track the surrounding patch and keep the box's relative
  position inside it).
- **Fast-forward:** LK (`calcOpticalFlowPyrLK`, pyramid 3, win 21) frame to frame through the
  buffered frames up to "now". Log the frame count and ms.
- **Per frame:**
  - forward-backward LK check to drop bad points
  - `findHomography(RANSAC, 3 px)` when ≥ 8 points, else median translation
  - transform the box corners
  - EMA on the centre (α ≈ 0.4)
- **Confidence** = inlier ratio × point-count factor.
  - Re-detect features in the current box when points drop below ~50% of the initial count.
  - Low confidence for > 1 s → emit a **re-ground request**. The ViewModel sends a silent VLM
    side-request ("where is <label> now?") that is **rolled back from the KV cache**
    (`Keep.Never`), only when the VLM is idle, at most once every 3 s. Then it re-seeds the tracker.
- A new question clears the old marker.
- **Budget:** < 10 ms per frame on the iQOO 15. Log p50/p95 every ~2 s.

### Step 8: Wire it into the question flow
In `FixLensViewModel.onQuestion`:
1. Capture the keyframe and its timestamp.
2. Stream the reply through `GroundingParser`.
3. On `Box`, map it (`BoxMapper`) and seed the tracker immediately, **before the text finishes**.
4. Send the text to the answer card.

Cancel/barge-in clears any pending seed. Nothing here may run on the main thread except drawing.

---

## Verify on the device (must actually run)

1. Unit tests: `./gradlew testDebugUnitTest` passes (parser + mapper).
2. The drawn test box is centred and correct on screen.
3. On the A3 printout / a washer panel image:
   - ask 5 "where is X" questions
   - the box lands on the part in ≥ 4/5
   - record time to box (from end of speech to marker on screen)
4. Move the phone slowly around the printout for 10 s: the marker stays on the part. Tilt it and
   move closer and further away. Cover the camera: the marker holds, fades, and after it's
   uncovered it re-grounds.
5. Tracker p95 < 10 ms. No new jank in the preview. VLM answer latency not worse than before
   (the YUV change should improve it).
6. Sessions still work: KV memory across turns, rename, switching sessions, and the box line never
   shows in history or titles.

Use `adb logcat -s FixLens` and `scrcpy`/`adb exec-out screencap` to check. Ask me to hold the phone
and do the movement tests. Give me the exact steps in one batch.

## Wrap up

1. `docs/marker-tracking.md`:
   - coordinate spaces and the mapping choice
   - the **confirmed coordinate scale** (with how it was verified)
   - tracker parameters
   - measured numbers: time to box, tracker p50/p95, fast-forward ms
   - known failure cases
2. `CLAUDE.md` §15: tick **M0** (coordinate scale part) / **M1** / **M2** as appropriate, and add 2–4 notes
   with the measured numbers.
3. `PLAN.md`: mark progress; note deviations.
4. Commit in small logical pieces (e.g. "YUV + ring buffer", "grounding parser + mapper",
   "marker overlay", "flow tracker", "wiring"). Show me `git status` and the message before each commit.
5. Final report: what works, the numbers, what's flaky, and the next step.

## Ground rules

- CLAUDE.md §12 applies: no network, VLM never per frame or on the main thread, arm64 only.
- Don't break the Round-1 demo. If a step makes things worse, stop and tell me.
- Don't guess the coordinate scale. Confirm it with the drawn test box.
- Report honestly: say what was measured and what was estimated or skipped.
