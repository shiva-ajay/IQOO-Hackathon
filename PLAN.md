# FixLens — Build Plan

Source of truth for *what* we build: `CLAUDE.md` + `.claude/STACK.md`.
This file is the *order* we build it in. ~15 h of work, solo, iQOO 15.

---

## 0. The critical path in one picture

```
 [P0 Setup] ──► [M0 Model check on phone] ──► [M1 frame → VLM → box]  ◄── GATE @ hour 4
      │                                              │
      │  (parallel, Red Light)                       ▼
      ├──► KB JSON writing                     [M2 Tracker]
      ├──► Test photos / A3 printout                 │
      └──► Speech models download                    ▼
                                               [M3 Voice in/out]
                                                     │
                                                     ▼
                                               [M4 KB + Guide state machine]
                                                     │
                                                     ▼
                                               [M5 Freeze, airplane test, video]
```

The single biggest risk is **MNN + Qwen3-VL running inside our own app and returning usable boxes**.
Everything before hour 4 is aimed at killing that risk. Nothing else matters if it fails.

---

## Hour budget (target)

| Phase | Hours | Cumulative | Where |
|---|---|---|---|
| P0 Setup | 1.0 | 1.0 | Laptop |
| M0 Model check | 0.5 (parallel with P0 downloads) | 1.5 | Phone |
| M1 Frame → box | 2.5 | 4.0 | Laptop + phone — **gate** |
| M2 Tracker | 2.5 | 6.5 | Laptop + phone |
| M3 Voice | 2.5 | 9.0 | Laptop + phone |
| M4 KB + guide | 3.0 | 12.0 | Laptop (+ KB written earlier in Red Light) |
| M5 Freeze + polish + video | 3.0 | 15.0 | Phone |

---

## P0 — Setup (do this first)

### P0.1 Laptop toolchain
Already present: JDK 21, Android SDK (platforms 34–37, build-tools, cmake), NDK 28.2, `adb`.
Missing / to check:
- [ ] **Android Studio** (latest stable) — not found on this machine.
- [ ] **scrcpy** — `sudo apt install scrcpy` (or snap).
- [ ] **git-lfs** — `sudo apt install git-lfs && git lfs install` (needed to pull models from Hugging Face).
- [x] `huggingface-cli`: installed as `hf` 2.0.0 via **pipx** (`pip --user` is blocked by PEP 668 on Ubuntu 24.04).
      Its Xet backend stalled here, so models were fetched with resumable `curl` instead.
- Note: `sudo` is blocked by a Claude Code hook. scrcpy / git-lfs / Android Studio are left for the human.

### P0.2 Phone
- [x] Developer options → USB debugging on; `adb devices` shows the iQOO 15 (serial `10BFAT1RTA000XP`, Android 16 / SDK 36).
- [ ] "Install via USB" + "USB debugging (security settings)" on (Vivo/iQOO OS requires both).
- [ ] Google TTS: set as preferred engine, download the offline **English** voice, test in airplane mode.
- [x] **MNN Chat** already installed from Play (`com.alibaba.mnnllm.android.release` v0.8.4). Local models: `/data/local/tmp/mnn_models/<name>/`.

### P0.3 Start the big downloads now (they run while you do everything else)
- [ ] Qwen3-VL-4B-Instruct MNN int4 (`taobao-mnn/Qwen3-VL-4B-Instruct-MNN`), 2.95 GB → `../fixlens-models/qwen3-vl-4b/` (downloading).
- [ ] Qwen3-VL-2B-Instruct MNN int4 (`taobao-mnn/Qwen3-VL-2B-Instruct-MNN`), 1.48 GB → `../fixlens-models/qwen3-vl-2b/` (queued after 4B).
- [x] sherpa-onnx `1.13.8` AAR + `silero_vad.onnx` + Moonshine Base EN int8 + Tiny EN int8 → `../fixlens-models/`.
      (The newer `moonshine-*-en-quantized-2026-02-27` builds also exist and are smaller; consider them in M3.)
- [ ] OpenCV Android via Maven (resolved by Gradle, nothing to download manually).

### P0.4 Get MNN's native pieces
- [x] `git clone https://github.com/alibaba/MNN` (shallow) → `/home/shiva-ajay/3D/MNN` (HEAD `6745000`).
- [x] Locate the Android LLM chat app (`apps/Android/MnnLlmChat` — verify path) and its JNI wrapper
      (`LlmSession` / native `llm_session.cpp` or equivalent) and how it gets `libMNN.so` + LLM libs.
- [ ] Build that app once, unmodified, and run it on the phone. This proves the NDK/CMake path works.
      **Deviation:** only the native `libMNN.so` (README flags, NDK 28.2) was built. The Gradle half was skipped
      because it needs jitpack, Firebase and a sherpa-mnn download on a slow link, and MNN Chat is already on the phone for M0.
      Findings: [docs/mnn-donor-notes.md](docs/mnn-donor-notes.md).

**Recommended approach (decision):** don't ship the whole MNN Chat app. Use it as the donor:
create a clean `com.fixlens` Compose app and **transplant only** the JNI wrapper, its `.cpp`,
and the prebuilt `.so` files. This avoids stripping the model market / downloader / network code
and keeps the "no INTERNET permission" rule trivially true. If the transplant fights you for more
than ~45 min, fall back to forking the whole app and deleting features.

### P0.5 Project skeleton (in this repo)
- [x] Hand-written Gradle KTS project (no wizard), package `com.fixlens`, minSdk 29, compile/target 37.
      Gradle 9.8.0, AGP 9.4.1 (built-in Kotlin, so no kotlin-android plugin), Kotlin 2.4.20, Compose BOM 2026.09.00.
- [x] `abiFilters += "arm64-v8a"`, kotlinx-serialization plugin, portrait lock.
- [x] Manifest: `CAMERA` + `RECORD_AUDIO` only, with an INTERNET/ACCESS_NETWORK_STATE `tools:node="remove"` guard.
      Verified with `aapt2 dump permissions`. The only other entry is androidx.core's app-private
      `com.fixlens.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature level, not a network permission).
- [x] Create empty packages: `app camera vision tracking voice kb guide ui`.
- [x] `.gitignore` for build outputs, `*.mnn`, `*.mnn.weight`, `*.onnx`, `app/libs/*.aar`.
- [x] `./gradlew assembleDebug` + `adb install` works. The app opens on the iQOO 15, the start log line appears, and CAMERA + RECORD_AUDIO are granted via the button (verified 2026-09-26).
- [ ] First commit.

**Done when:** blank FixLens app installs and opens on the phone.

---

## M0 — Model check in MNN Chat (phone only, ~30 min)

Goal: decide the model before writing any integration code.

1. `adb push` Qwen3-VL-4B to the MNN Chat app's model dir (or load via its local import).
2. Take **5 photos per target**: car engine bay (dipstick, coolant cap, washer cap, battery,
   air-filter box) and the washing-machine A3 printout.
3. For each, send the exact grounding prompt from CLAUDE.md §9.
4. Record in a table: box correct? JSON first line followed? seconds to answer? coordinate scale?

| Result | Action |
|---|---|
| ≥4/5 correct, ≲3 s, JSON followed | Use 4B. Coordinates = 0–1000 (confirm by drawing) |
| Correct but slow | Switch to 2B |
| Boxes poor | Try Qwen2.5-VL-3B (absolute pixel coords) |
| Nothing works in MNN Chat | Start the MediaPipe + Gemma 3n path immediately |

Log the numbers into CLAUDE.md §15.

---

## M1 — Camera frame → VLM → box on screen (~2.5 h) — GATE AT HOUR 4

Build in this order, each step runnable on the phone:

1. **camera/** `CameraController`: CameraX `Preview` (PreviewView in Compose via `AndroidView`)
   + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, YUV). `FrameRingBuffer`: last ~3 s of
   downscaled grayscale frames + timestamps (needed for M2, cheap to build now).
2. **vision/** `VlmEngine`: load model once at startup behind a splash (`FixLensApp`),
   single-thread dispatcher, `suspend fun ask(image, prompt): Flow<String>` (token stream).
3. **vision/** `GroundingParser`: first line → JSON box (tolerate fences/whitespace), rest → text.
   Unit-test it on the laptop with canned outputs.
4. **vision/** `BoxMapper`: model coords (0–1000 or pixels, config flag) → keyframe px →
   PreviewView coords (rotation + FILL_CENTER crop). Unit-test the math.
5. **ui/** `MarkerOverlay` (Compose Canvas) + a debug "Ask" button with a hard-coded target
   ("yellow ring handle of the engine oil dipstick").
6. **Verify with a drawn test box** — feed a known box through BoxMapper first, then the real model.

**Done when:** point at the printout, press Ask, box lands on the named part.
**Gate:** not working by hour 4 → switch VLM runtime to MediaPipe + Gemma 3n. Everything
downstream (tracker, voice, KB) stays the same because it only depends on `VlmEngine`'s interface.

---

## M2 — Tracker with fast-forward (~2.5 h)

1. **tracking/** `FlowTracker` on the CameraX analysis thread:
   `goodFeaturesToTrack` inside box → `calcOpticalFlowPyrLK` → `findHomography(RANSAC)` → move box.
2. **Fast-forward:** on new box, find the buffered frame matching the keyframe timestamp and
   run flow through all buffered frames up to "now".
3. EMA smoothing (α ≈ 0.4), confidence = inlier ratio + point count, re-detect features when
   points drop, hold marker ~500 ms then fade when lost.
4. Low confidence > ~1 s → emit `ReGroundRequest` (the orchestrator handles it later; for now
   re-ask the VLM directly).
5. Overlay: pulsing box + dimmed mask outside.
6. Log per-frame tracker ms; must stay < 10 ms.

**Done when:** the marker stays locked while you walk the phone around the printout / engine bay.

**Status (2026-09-26): M1 + M2 done, verified on the iQOO 15** (numbers in docs/marker-tracking.md §4). The hour-4 gate
passed, so there's no MediaPipe switch. Deviations from the plan above:
- Grounding needed a **model graph fix** first: the pre-converted Qwen3-VL uses the wrong M-RoPE layout for images
  (`tools/mnn-patches/qwen3vl_mrope_fix.sh`).
- Coordinates: Preview + Analysis are bound as one `UseCaseGroup` with the preview's ViewPort (not
  `CoordinateTransform`), so the keyframe is exactly what the user sees. Analysis is 1280×720 YUV, and the tracker
  works on the gray Y plane at 218×480.
- No separate `CameraController`: `ui/CameraScreen.CameraPreview` binds, and `camera/FrameGrabber` is the analyzer.
- The tracker fits one model from the seed frame (homography, then similarity, then median shift) instead of
  frame-to-frame homographies, so the box shape doesn't drift. The last good frame stays the LK anchor, so brief
  occlusions recover by themselves.
- Next (asked for after testing): precise **multi-part pointing** (every screw, not the whole device), with all markers
  tracked together; then M4 guided steps (KB targets per step, "done" or a visual auto-check to advance).

---

## M3 — Voice in and out + Fixy greeting (~2.5 h)

1. **voice/** `SpeechInput`: copy recognizer + VAD setup from sherpa-onnx `SherpaOnnxVadAsr`.
   `AudioRecord` 16 kHz mono, `VOICE_COMMUNICATION`. Emit `Flow<SpeechEvent>` (level, start, end, transcript).
2. **voice/** `SpeechOutput`: `TextToSpeech`, rate 0.95, `USAGE_ASSISTANT`, `QUEUE_ADD` per sentence,
   `UtteranceProgressListener` → which sentence is being spoken.
3. **ui/** `TalkButton` (tap-to-talk, level animation), `CaptionBar`.
4. Hard-coded greeting on first tap. "Let me look…" earcon as soon as VAD ends.
5. Barge-in: VAD speech start while TTS speaking → `tts.stop()`.
6. Wire it: transcript → (temporary) VLM question with current frame → stream text into captions
   and TTS sentence by sentence; box into tracker.

**Done when:** spoken question → caption → spoken + captioned answer + marker, with barge-in.

**TTS voice (parked research, 2026-09-26; decide when M3 starts):** Google TTS may sound too robotic.
Candidates that all run through the sherpa-onnx AAR we already ship (1.13.8 has Kitten, Supertonic,
Pocket, Kokoro, Matcha and VITS/Piper configs, plus `generateWithCallback` for chunked audio):
- Shortlist: **Kitten TTS v0.8 mini/micro** (80M/40M) and **Supertonic 3** (99M, int8; check its OpenRAIL licence).
- Also listen to: Kyutai **Pocket TTS** (100M, streaming).
- Safe fallback: **Piper** medium.
- Avoid: **Kokoro** (slower than real time on a 2019 phone).
- Last resort: Google TTS.

Plan: listen on the laptop first, then measure on the phone: time to first audio (target < 300 ms)
and RTF, both with the VLM idle and while it is generating. Keep a `SpeechOutput` interface with
Google as a one-line fallback. Speak sentence by sentence, never raw tokens. Give TTS 2 threads.
This needs CLAUDE.md §11/§13 and STACK.md updated once chosen.

**Voice out: done (2026-09-27), Piper `en_US-lessac-medium`** (Supertonic 3 was tried first: too heavy, and its
fast mode sounded echoey). Built as `voice/SpeechChunker` + `voice/SpeechOutput` (see
CLAUDE.md §11 and §15 for the design and the phone numbers). The Google fallback wasn't built: without the voice
model Fixy answers in text only. Left: try a spoken question and mic barge-in by hand, then tick M3; optionally
highlight the caption being spoken.

---

## M4 — KB + retrieval + guide state machine (~3 h)

KB content can be written **during Red Light** (no laptop needed) — do it early.

1. **kb/** `fixlens_kb.json`: 5 car entries + 5–8 washer error codes for one brand + 1 wiring
   refusal/`call_technician` entry. Every `target` is a descriptive grounding phrase.
2. **kb/** `KbModels` (kotlinx.serialization), `KbRepository` (load on IO, validate, fail loudly
   in debug), `Retriever` stages 1–2 (exact code, keyword/alias). Unit-test on the laptop.
3. **kb/** Retriever stage 3 (LLM router via `VlmEngine`) and stage 4 (refuse).
4. **guide/** `FixyPrompts` (system prompt verbatim from CLAUDE.md §8), `GuideStateMachine`
   (Idle → Greeting → Scanning → Confirming → Explaining → Step(n) → Done, + Escalate, Unknown),
   safety gate before repair steps.
5. **guide/** `Orchestrator`: owns the flow from CLAUDE.md §5 — keyframe capture, retrieval,
   prompt build, parser → tracker/captions/TTS, re-ground requests, command matching
   ("done/next/repeat/back/stop"). KB `say` text spoken verbatim, not via the VLM.
6. **ui/** `StepCard`. Logging per question: STT ms, retrieval stage, TTFT, total, tracker conf,
   `entry_id` + `kb_version`.

**Done when:** full guided flow works: safety gate, "done" advances, wiring question → refusal,
`call_technician` → escalation, unknown → refusal line.

**Status (2026-09-27):** engine + wiring done and verified on the phone (see CLAUDE.md §15). Remaining:
- **Verify the DRAFT KB** against the demo car's manual (docs/kb-collection-plan.md §5) and pick the washer brand.
- Stage 3 (LLM router) isn't built. By user decision, "unknown" gets a normal VLM answer with pointing but no steps,
  instead of the refusal line; `call_technician` and `escalate_if` still escalate.
- Steps, safety lines and the greeting are now spoken verbatim (M3 voice out).
- Pointing at a part that isn't in view often marks something else; consider a yes/no visibility check first (~2.5 s).

---

## M5 — Freeze and polish (~3 h)

1. Airplane mode end-to-end run on both targets. Fix only blockers.
2. Latency pass: image size (448 vs 640), max tokens, thermal check after 10 questions.
3. Splash screen, visual polish of marker/captions/step card.
4. Record the **backup demo video** (scrcpy record + screen recording).
5. Pitch: offline proof (no INTERNET permission, airplane mode), novelty loop
   (voice → point → track), trust (KB-only, refusal, escalation), roadmap (STACK.md §9).
6. Update CLAUDE.md §15 with final status and measured latencies.

---

## Red Light work list (phone-only time)

Use these whenever the laptop is off-limits:
- M0 model check in MNN Chat.
- Write `fixlens_kb.json` (in a phone notes app, paste in later).
- Take test photos of the engine bay and printout; print the A3 panel.
- Verify Google TTS offline voice in airplane mode.
- Rehearse the demo script and pitch; record demo video (M5).

## Things that will bite (check early)

- Coordinate scale (0–1000 vs pixels) — always verify with a drawn box.
- PreviewView FILL_CENTER crop vs analysis frame aspect ratio — use the same aspect ratio for
  Preview and ImageAnalysis, or map through CameraX `CoordinateTransform`.
- Mic contention: TTS output feeding back into VAD → rely on `VOICE_COMMUNICATION` AEC; mute VAD
  input briefly if false barge-ins occur.
- Loading 3 GB model + OpenCV + sherpa-onnx at once: load sequentially behind the splash.
- iQOO/Vivo battery optimizer killing the app in background — disable for FixLens.
