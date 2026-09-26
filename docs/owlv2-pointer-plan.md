# Plan: OWLv2 as Fixy's pointer (on the phone's AI chip)

Status: **proposal, not started.** Written 2026-09-27 with about 5 h left. Nothing in here is built yet.
Adding the two new libraries needs your OK (CLAUDE.md rule 6).

---

## 1. Why

Today Qwen3-VL-4B does two jobs: it talks, and it points. It talks well but points badly (measured, see
docs/marker-tracking.md §2 and §6):

| Case | Qwen3-VL-4B today |
|---|---|
| One part, with a KB target phrase | 8 of 11 found |
| Many small parts (13 laptop screws) | 2–3 of 13; it invents a tidy grid of points |
| The part isn't in view | Points at something else anyway (the keyboard as "engine") |
| Time until the box appears | ~5 s (CPU, and it competes with the answer) |

**OWLv2** (Google, Apache-2.0) is a small detector built for one job: "find *this phrase* in the picture". It
gives a **confidence score** for every box, so we can:
- ask for all instances (every screw),
- and say "not visible" when the score is low, instead of pointing at the wrong thing.

Qualcomm publishes a version already compiled for our chip, which runs on the **NPU**, not the CPU that Qwen uses:

| OWLv2-B/16 on Snapdragon 8 Elite Gen 5 (Galaxy S26, Qualcomm AI Hub) | |
|---|---|
| Image step (960×960 → features) | **~453 ms** |
| Each phrase (features + phrase → 3,600 boxes with scores) | **~2.8 ms** |
| Peak memory | ~40 MB + ~15 MB |

## 2. Before and after

**Before (today)**
```
 Mic ──► Moonshine ──► question
                          ▼
 Camera photo ──► Qwen3-VL-4B (CPU) ──► answer text + boxes (~5 s, often wrong)
                                                          │
 Camera video ──► OpenCV LK tracker ◄─────────────────────┘
```

**After**
```
 Mic ──► Moonshine (same) ──► question
                                 ▼
 Camera photo ─┬─► Qwen3-VL-4B (CPU, same) ──► answer text + the NAME of the part ("oil filler cap")
               │                                               │
               └─► OWLv2 image step (NPU, ~0.45 s, starts at once, in parallel with Qwen)
                          │ features                           │ name (or the KB step's target)
                          ▼                                    ▼
                    OWLv2 phrase step (NPU, ~3 ms) ──► boxes + scores
                          │  • score ≥ threshold: markers (all instances for plural parts like "screws")
                          │  • nothing above threshold: "Point the camera at the …" (no wrong marker)
                          │  • OWLv2 unavailable or failed: fall back to Qwen's box (today's behaviour)
                          ▼
 Camera video ──► OpenCV LK tracker (same)
```

In a **guided step** the KB already has the phrase, so the box comes about 0.5 s after "done" instead of about 5 s.

**Unchanged:** Moonshine, Qwen (it still answers and is the fallback pointer), the OpenCV tracker, the KB and
the guide, sessions and memory, and the no-network rule (the model is copied on with adb, like Qwen).

## 3. Time budget (5 h)

| Phase | Time | Result |
|---|---|---|
| 0. Go/no-go tests | 0:45 | Decide: OWLv2 or keep today's pipeline |
| 1. Pointer for guided steps | 1:30 | KB step phrases pointed at by OWLv2, falling back to Qwen |
| 2. Pointer for normal questions | 0:30 | Qwen names the part, OWLv2 places it |
| 3. Phone check + commits | 0:30 | Measured numbers, docs, commits |
| (Demo prep, not part of this plan) | ~1:45 | Check the KB text, rehearse, backup video |

**If phase 0 fails, stop there:** today's pipeline stays and all remaining time goes to demo prep.

---

## 4. Phase 0: go/no-go (45 min, two tracks in parallel)

### 0a. Laptop accuracy test (does OWLv2 find our parts?)

```bash
python3 -m venv ~/3D/owl-venv
~/3D/owl-venv/bin/pip install torch --index-url https://download.pytorch.org/whl/cpu
~/3D/owl-venv/bin/pip install transformers pillow
```
New script `tools/owl/owl_eval.py`, using Hugging Face `google/owlv2-base-patch16-ensemble` on the CPU:
- **Screws:** `fixlens-testdata/m1/s76_gaze20_plain.jpg`, phrase "screw", compared with the 13 known positions
  (`s76_gaze20_screws_gt.json`).
- **Engine:** `car_volt_640.jpg`, phrases "yellow engine oil filler cap", "coolant reservoir cap",
  "windshield washer fluid cap", using the ground-truth boxes in `tools/m1/cases.py`.
- **Washer panels:** the 5 + 3 panel cases in `tools/m1/cases.py`.
- **Not in view:** engine phrases on the laptop photo, and "screw" on the washer panel. The best score should
  stay under the threshold.
- Report found/total and false boxes at thresholds 0.1, 0.2 and 0.3, and save overlay images.

**Pass if** there is one threshold where all of these hold:
- the screws are ≥ 9/13 with ≥ 70% precision,
- the single parts are ≥ 9/11,
- all the "not in view" checks stay under the threshold.

### 0b. Phone load test (does it run on the iQOO 15's NPU?)

1. Get the model on the laptop (flags to confirm with `qai-hub-models fetch --help`):
   ```bash
   ~/3D/owl-venv/bin/pip install qai-hub-models
   ~/3D/owl-venv/bin/qai-hub-models fetch OWL-V2 --runtime precompiled_qnn_onnx --precision float \
       --chipset qualcomm-snapdragon-8-elite-gen5-for-galaxy --output-dir ~/3D/fixlens-models/owlv2
   ```
   Expect two parts (the vision encoder and the text detector), each an `.onnx` file with its context `.bin`.
2. Push it: `adb push ~/3D/fixlens-models/owlv2 /sdcard/Android/data/com.fixlens/files/models/` (then chmod,
   as in `tools/push_models.sh`).
3. Add the dependencies (**needs your OK**):
   ```kotlin
   implementation("com.microsoft.onnxruntime:onnxruntime-android-qnn:1.29.0")
   implementation("com.qualcomm.qti:qnn-runtime:2.50.0")   // the model was built with QAIRT 2.50; ORT alone brings 2.42
   ```
   In the manifest, inside `<application>`: `<uses-native-library android:name="libcdsprpc.so" android:required="false"/>`
   (the NPU's system library). No new permissions.
4. Add a debug-only adb hook, `--ez owltest true`, that:
   - loads both parts on the NPU (`SessionOptions.addQnn(mapOf("backend_path" to "libQnnHtp.so"))`),
   - runs one test image,
   - logs the load time, image ms, phrase ms and the top 5 boxes.

**Pass if** both parts load on the NPU (no fallback to the CPU), the image step is ≤ 800 ms, the phrase step is
≤ 20 ms, and the boxes match the laptop's for the same image and phrase.

**If it fails to load** (the file was compiled for the Galaxy variant of the chip), try in this order:
1. Compile for our chip in Qualcomm AI Hub (free account, ~20 min).
2. Load the plain ONNX so the NPU compiles it at first start (slow first load, then cached).
3. Otherwise **no-go**.

---

## 5. Phase 1: pointer for guided steps (1 h 30)

### New files (ours alone, so no clashes with the other session's work)

**`vision/OwlDetector.kt`**
```kotlin
class OwlDetector(private val modelDir: File) {
    fun load(): Boolean                              // ORT env + 2 NPU sessions, on its own thread "fixlens-owl"
    fun encode(image: Bitmap): OwlImage              // ~450 ms: pad bottom/right to a square, resize to 960,
                                                     //   CLIP normalize, run the vision part → features [1,60,60,768]
    fun find(image: OwlImage, phrase: OwlQuery, all: Boolean): List<Detection>
                                                     // ~3 ms: text part → 3,600 boxes + scores;
                                                     //   score ≥ threshold, NMS (IoU 0.5), top-1 or top-16
}
data class Detection(val box: PxBox /* source-bitmap px */, val score: Float)
```
- Box mapping: OWLv2 boxes are in 960-px space of the padded square, and the padding is bottom/right, so there's
  no offset. Divide by `960 / max(w, h)` to get source-bitmap px, then use the existing
  `BoxMapper.keyframeToAnalysis` for the tracker.
- The threshold starts at the value phase 0a found. It's a constant, with an adb override to tune it.

**`vision/OwlQueries.kt` + `assets/owl/queries.json`**
- OWLv2 needs CLIP token ids (16 per phrase). For the hackathon, **pre-tokenize on the laptop** with
  `tools/owl/tokenize_queries.py` (Hugging Face `CLIPTokenizer`). Tokenize every KB `target`, every KB entry's
  part names, and ~40 common part words (screw, cap, button, filter, dial, hose, tank, battery terminal…).
- At runtime, look up the exact phrase; if it's missing, try the phrase's last noun ("…filler cap" → "cap").
- Tokenizing free phrases on the phone (a CLIP byte-pair tokenizer in Kotlin, ~1 h) is phase 2b, only if there's time.

### Changes to existing files (small, isolated hooks)

- **`camera/FrameGrabber.kt`** (ours): a keyframe request can also return a **960-px upright bitmap** from the
  same frame, with the same crop and timestamp. The VLM keeps its 448-px JPEG. That's more detail for OWLv2, and
  the same timestamp, so the tracker's fast-forward works unchanged.
- **`app/FixLensViewModel.kt`** (shared: small hooks only):
  - Load `OwlDetector` at start on the IO thread, after Qwen. The KB target phrases' tokens are part of `queries.json`.
  - `pointAtStep(target)`: capture the keyframe plus the 960 bitmap, then `encode` and `find`.
    - Hits above the threshold: seed the tracker. Plural or "every" phrases get all instances, others top-1.
    - None: show the hint "Point the camera at the {target}" and **don't** fall back to Qwen's guess.
    - OWLv2 not loaded: today's Qwen side request.
  - Retry while none is found: re-run `find` on a fresh frame every ~1.5 s (0.45 s of NPU each). This replaces
    the slow Qwen re-ground for guided steps.
  - Logs: `Owl encode … ms, find "…" … ms, n hits, best score …`.
- **`app/src/main/AndroidManifest.xml`**: the `uses-native-library` line from phase 0b.
- **`app/build.gradle.kts` / `gradle/libs.versions.toml`**: the two dependencies. Keep `abiFilters` arm64-only.
  qnn-runtime is ~71 MB (every Hexagon version); trim it later with `packaging.jniLibs.excludes`, keeping
  `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnSystem.so` and our chip's `libQnnHtpV*Stub/Skel.so`.

### Tests
- JVM unit tests (no phone): preprocessing math, box mapping 960-square → bitmap → analysis, NMS, threshold,
  top-K, and the phrase lookup with its last-noun fallback.
- Phone: the adb question hook runs the oil guide against the engine photo shown full screen on the laptop, then
  checks that the markers land on the dipstick handle, the filler cap and the coolant tank.

---

## 6. Phase 2: pointer for normal questions (30 min)

- As soon as the keyframe is taken, start `encode` on the NPU. It runs **alongside** Qwen's prefill on the CPU,
  so the features are ready by the time Qwen names the part.
- When Qwen streams a part (`onTarget`, already built), look up its label in `queries.json`.
  - Known label: `find` on the ready features (~3 ms). A score above the threshold replaces Qwen's box.
  - Otherwise: keep Qwen's box (today's behaviour).
- **2b, only if time is left:** the Kotlin CLIP tokenizer (vocab ~1.3 MB + merges ~0.5 MB in assets), so any
  phrase works. Also a shorter Qwen format ("Point: <part>" instead of JSON coordinates) would save ~2 s of
  decoding.

## 7. Phase 3: check on the phone and commit (30 min)

- Measure: time from question to marker (guided step and normal question), OWLv2 encode and find ms on the iQOO
  15, and the hit rate on the M0 photos shown on screen.
- Record the numbers in docs/marker-tracking.md (new §7) and CLAUDE.md §15. Update STACK.md, since this adds a
  model and a runtime.
- Commits, shown to you first: (1) deps + OwlDetector + tests; (2) guided-step pointer; (3) question pointer; (4) docs.
  Stage only our changes, as before (the other session is editing the same files).

---

## 8. Risks

| Risk | Likelihood | What we do |
|---|---|---|
| The Galaxy-compiled file won't load on the iQOO | Medium | Compile for our chip in AI Hub (free account), or on-device compile at first run; else no-go |
| NPU runtime version mismatch | Handled | Pin `qnn-runtime:2.50.0` (what the model was built with) |
| OWLv2 misses our specific parts | Unknown until 0a | 0a decides; Qwen stays as the fallback |
| Phrase not in the pre-tokenized list | Medium | Last-noun fallback; the Kotlin tokenizer in 2b; otherwise Qwen's box |
| APK +80 MB | Certain | Fine for the demo; trim the Hexagon libraries later |
| NPU and CPU heat together | Low | The NPU is more efficient than the CPU; one encode per question or step, plus retries only while searching |
| Clashes with the other session's edits | Medium | New files + small hooks; stage only our hunks |

## 9. Decisions needed from you

1. **OK to add** `onnxruntime-android-qnn:1.29.0` and `qnn-runtime:2.50.0`? (Needed for 0b.)
2. **OK to start phase 0** now (45 min), with the rule that if it fails we drop OWLv2 and go to demo prep?
3. The M4 wiring (guided steps) is still **uncommitted**, since you stopped that commit. Commit it before this
   work starts, so OWLv2 builds on a clean base?
