# FixLens — Tech Stack (final)

Everything below runs **on-device, offline** on the iQOO 15 (Snapdragon 8 Elite Gen 5,
Adreno 840 GPU, Hexagon NPU, 16 GB LPDDR5X, Android 16). Do not substitute components
without asking.

---

## 1. Stack at a glance

| Layer | Choice | Source |
|---|---|---|
| Language | **Kotlin** | — |
| UI | **Jetpack Compose**, single Activity, `ViewModel` + `StateFlow` | Compose BOM |
| Starting point | Fork of MNN's open-source Android LLM chat app | `alibaba/MNN` (GitHub) |
| Vision + conversation (VLM) | **Qwen3-VL-4B-Instruct, int4, on MNN** | Pre-converted MNN models (`taobao-mnn` on Hugging Face) |
| Camera | **CameraX**: `Preview` + `ImageAnalysis` | Gradle |
| Tracking | **OpenCV Lucas-Kanade optical flow + RANSAC homography** | OpenCV Android SDK (Maven Central) |
| Marker overlay | Compose `Canvas` | Built-in |
| Speech-to-text | **sherpa-onnx**: Silero VAD + **Moonshine Base English (int8)** | sherpa-onnx GitHub releases (AAR + models) |
| Text-to-speech | **Piper** `en_US-lessac-medium` via the sherpa-onnx `OfflineTts` already in the AAR | sherpa-onnx `tts-models` release |
| Knowledge base | **JSON in assets** + `kotlinx.serialization`, 4-stage lookup | Gradle |
| IR remote | `ConsumerIrManager` + code catalog in assets (IRext + Flipper-IRDB) + **IRext AC decoder** (vendored C, MIT) | `tools/ir/build_ir_assets.py`, `app/src/main/cpp/irext/` |
| Reminders ("Alerts") | `AlarmManager` (inexact, 1 h window) + a local `Notification`; interval and text from the KB (`remind`) | Platform APIs, no library |
| Concurrency | Kotlin coroutines | Gradle |
| Persona | Fixy: hard-coded greeting + system prompt (no training) | Our code |

---

## 2. Models

### VLM (the brain: sees, answers, points)
| Priority | Model | Runtime | Use when |
|---|---|---|---|
| **Primary** | Qwen3-VL-4B-Instruct (int4) | MNN | Default |
| Fallback 1 | Qwen3-VL-2B-Instruct (int4) | MNN | 4B too slow (>~3 s) or thermal throttling |
| Fallback 2 | Qwen2.5-VL-3B-Instruct (int4) | MNN | Qwen3-VL boxes poor on our targets |
| Emergency | Gemma 3n E4B | MediaPipe LLM Inference | MNN integration not working by hour 4 |

- Approx. memory: ~3 GB for 4B int4. Load once at startup; keep resident.
- Coordinates: Qwen3-VL expected 0–1000 normalized; Qwen2.5-VL absolute pixels of the resized
  input. Keep this configurable in `BoxMapper` and verify on device.
- Runtime settings: image long side 448–640 px, temperature 0.2–0.3, max new tokens 120–150.
- Model files live **outside the APK**:
  `/sdcard/Android/data/com.fixlens/files/models/qwen3-vl-4b/` (side-loaded with `adb push`).

### Speech-to-text
| Component | Model | Notes |
|---|---|---|
| VAD | `silero_vad.onnx` | Detects speech start and end; ~2 MB |
| ASR | Moonshine Base English, int8 | Fast, accurate English; transcribes each VAD segment |
| ASR fallback (speed) | Moonshine Tiny English | If Base is too slow |
| ASR fallback (accuracy) | Whisper base.en (via sherpa-onnx) | If accent accuracy is poor |

- Reference implementation: sherpa-onnx Android example **SherpaOnnxVadAsr** (VAD + offline
  ASR). Copy its recognizer and VAD setup rather than writing from scratch.
- Audio: `AudioRecord`, 16 kHz, mono, PCM 16-bit, source `VOICE_COMMUNICATION`.

### Text-to-speech
- **Piper** `vits-piper-en_US-lessac-medium.tar.bz2` (sherpa-onnx `tts-models` release): `en_US-lessac-medium.onnx`
  (63 MB), `tokens.txt`, `espeak-ng-data/`. 22.05 kHz, single speaker. No new library: `OfflineTtsVitsModelConfig`.
- Speed 1.0, 2 ORT threads. `AudioTrack` float stream, `USAGE_MEDIA`, `PERFORMANCE_MODE_LOW_LATENCY`. Text is
  chunked by clause/sentence and pipelined (see CLAUDE.md §11).
- Compare voices on the laptop with `tools/tts/audition.py` (sherpa-onnx from pip in a scratch venv).
- (Tried and dropped: Supertonic 3, heavier and echoey in its fast mode. Don't use Kokoro, which is too slow on mobile.)

---

## 3. Gradle dependencies (use current stable versions)

```kotlin
// Compose
implementation(platform("androidx.compose:compose-bom:<latest>"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:<latest>")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<latest>")

// CameraX
implementation("androidx.camera:camera-core:<latest>")
implementation("androidx.camera:camera-camera2:<latest>")
implementation("androidx.camera:camera-lifecycle:<latest>")
implementation("androidx.camera:camera-view:<latest>")

// OpenCV (4.10+)
implementation("org.opencv:opencv:<4.10+>")

// Kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<latest>")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<latest>")

// sherpa-onnx (local AAR from GitHub releases)
implementation(files("libs/sherpa-onnx.aar"))

// MNN: native libs + LLM JNI wrapper carried over from the forked MNN Android app.
// Reuse the fork's prebuilt .so files and wrapper classes; do not rebuild MNN unless required.
```

Build config: `abiFilters += "arm64-v8a"` only. `minSdk` 29, `targetSdk` = latest stable.
Enable the `kotlinx-serialization` plugin.

---

## 4. Files and assets

| File | Location | Approx. size |
|---|---|---|
| Qwen3-VL-4B-Instruct MNN (int4) | App external files dir (adb push) | ~3 GB |
| `silero_vad.onnx` | `app/src/main/assets/models/` | ~2 MB |
| Moonshine Base English int8 (+ tokens) | `app/src/main/assets/models/moonshine-base-en/` | tens of MB |
| `fixlens_kb.json` | `app/src/main/assets/kb/` | a few KB |
| Piper voice (Fixy's voice) | App external files dir `tts/piper/` (adb push) | ~80 MB with espeak-ng-data |
| "Let me look." fillers | Rendered by the voice at startup (no file) | — |

---

## 5. Android manifest

- Permissions: `android.permission.CAMERA`, `android.permission.RECORD_AUDIO`, `android.permission.TRANSMIT_IR`
  (the IR blaster; a normal permission, no network), `android.permission.POST_NOTIFICATIONS` (Fixy's reminders,
  asked the first time one is scheduled) and `android.permission.RECEIVE_BOOT_COMPLETED` (re-arm reminders after a
  reboot). **Nothing else.** The reminders are local notifications posted by the app, not server pushes.
- **No `INTERNET` permission** (this is proof of offline operation for the judges).
- Lock orientation to portrait for the demo (it simplifies coordinate mapping).

---

## 6. Threads and dispatchers

| Work | Where |
|---|---|
| Camera analysis + tracker update | CameraX analysis executor (single background thread) |
| Audio capture + VAD + ASR | Dedicated audio thread / `Dispatchers.Default` job |
| VLM inference | **Single-thread dispatcher**, one request at a time, never per frame |
| KB load + validation | `Dispatchers.IO` at startup |
| UI | Main thread only, observing `StateFlow`s |

---

## 7. Performance budget (targets on iQOO 15)

| Stage | Target |
|---|---|
| End of speech detected (VAD) | ~0.3 s |
| Moonshine transcript | ~0.2–0.5 s |
| KB retrieval (stages 1–2) | < 5 ms |
| VLM time to box line | ~1–2 s |
| First spoken word after the user stops talking | ~2–3 s (a filler plays at once meanwhile) |
| First answer audio after its first clause is generated | ~0.3 s idle, ~0.4 s while the VLM decodes |
| Tracker update per frame | < 10 ms (to sustain ~30 fps) |
| Model load at startup | 5–10 s, behind a splash screen |

Log each of these under the `FixLens` tag.

---

## 8. Dev tools

- Ubuntu + Android Studio (latest stable), `adb`, **scrcpy** (mirror and control the phone over USB).
- Chrome Remote Desktop (phone → laptop) during Red Light phases.
- Test on-device models first in the MNN Android app before integrating.

---

## 9. Post-hackathon roadmap (not now)

- Hybrid KB: SQLite FTS5 + sqlite-vec + EmbeddingGemma-300M (RRF fusion).
- QLoRA fine-tune of Qwen VL on repair-grounding data (Colab or cloud GPU).
- Wake word "Hey Fixy" (sherpa-onnx keyword spotting), Telugu/Hindi voices.
- More appliances (refrigerators, water purifiers, inverters, routers) and blinking-LED diagnosis.
