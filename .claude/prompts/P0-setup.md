# Task: FixLens Phase P0 — Setup (tools, phone, downloads, empty app)

You are starting Phase **P0** of the FixLens build. This is a fresh session, so start by reading
these files in full. They are the source of truth:

1. `CLAUDE.md`: product, architecture and the non-negotiable rules in §12
2. `.claude/STACK.md`: exact stack, models, manifest rules and threads
3. `PLAN.md`: the build order. You are doing the **P0** section only.

**Goal of P0:** by the end, a blank **FixLens** app (package `com.fixlens`) builds from the command
line, installs on the iQOO 15 over USB, and opens. All models and the MNN donor code are downloaded
and ready for M0/M1. Time budget: about 1 hour of work, not counting download time.

Do **not** start M0/M1 work (no VLM integration, no camera code, no MNN transplant). P0 only.

---

## Known state of the laptop (verified in an earlier session)

- Ubuntu, RTX 4050, 16 GB RAM. Repo: `/home/shiva-ajay/3D/IQOO-Hackathon` (git, branch `main`,
  no commits yet; untracked: `CLAUDE.md`, `PLAN.md`, `.claude/`, `.kilo/`).
- **Present:** OpenJDK 21, Android SDK at `/home/shiva-ajay/Android` (platforms android-34, 35,
  36, 37.0; build-tools; cmake; cmdline-tools/latest), NDK `28.2.13676358`,
  `adb` at `/home/shiva-ajay/Android/platform-tools/adb`.
- **Missing:** Android Studio, `scrcpy`, `git-lfs`, `huggingface-cli`. No global `gradle` confirmed.
- **Phone:** `adb devices` showed no device yet.

Re-check all of this before acting. Things may have changed.

---

## Ground rules for this session

- Anything that needs `sudo`, a GUI or a tap on the phone: **don't try it yourself.** Give me the
  exact command or steps, then wait for me to confirm it's done. Batch these asks together, so I
  get one list and not a trickle.
- Keep big files **out of the repo**. Models go in `/home/shiva-ajay/3D/fixlens-models/`, and the
  MNN clone goes in `/home/shiva-ajay/3D/MNN/`.
- Run long downloads in the background and keep working while they run.
- Only use dependencies listed in `STACK.md`. Ask before adding anything else.
- If a model name, repo path or file name in this prompt doesn't match reality, look up the right
  one (Hugging Face / GitHub) and tell me what you used. Don't guess silently.

---

## Step 1: Things I (the human) must do. Give me this checklist first.

Print this as one checklist at the very start, then carry on with Step 2 while I work through it:

1. `sudo apt install -y scrcpy git-lfs && git lfs install`
2. Install Android Studio, latest stable (snap or tarball). It is not required for command-line
   builds, but I want it for debugging later.
3. On the iQOO 15:
   - Developer options → **USB debugging** ON, **Install via USB** ON,
     **USB debugging (Security settings)** ON (iQOO/Vivo needs all three).
   - Plug in over USB and accept the RSA fingerprint prompt.
   - Settings → Text-to-speech: preferred engine = **Speech Recognition & Synthesis from Google**.
     Download the offline **English (India or US)** voice. Test "Listen to an example" in
     **airplane mode**.
   - Battery: set "no restrictions" / allow background activity for apps we install later
     (FixLens and MNN Chat).
4. Tell you "done", and paste the output of `adb devices`.

---

## Step 2: Tools you can install yourself (no sudo)

- `pip install --user -U "huggingface_hub[cli]"`, then verify that `huggingface-cli` (or `hf`) works.
- Check that `sdkmanager --list_installed` shows platform-tools, build-tools, a stable platform and
  NDK 28.2. Accept any missing licenses.
- Make sure `ANDROID_HOME=/home/shiva-ajay/Android` is usable (set it in the project's
  `local.properties` with `sdk.dir=`. Don't edit my shell rc files without asking).

---

## Step 3: Start the downloads (background) to `/home/shiva-ajay/3D/fixlens-models/`

Verify the exact repo names on Hugging Face / GitHub first. These are the intended models:

| What | Expected source | Target subfolder |
|---|---|---|
| Qwen3-VL-4B-Instruct, MNN int4 (**primary**) | HF org `taobao-mnn` (e.g. `Qwen3-VL-4B-Instruct-MNN`) | `qwen3-vl-4b/` |
| Qwen3-VL-2B-Instruct, MNN int4 (fallback 1) | HF org `taobao-mnn` | `qwen3-vl-2b/` |
| sherpa-onnx Android AAR (arm64-v8a) | GitHub `k2-fsa/sherpa-onnx` releases (latest) | `sherpa-onnx/` |
| `silero_vad.onnx` | `k2-fsa/sherpa-onnx` releases, `asr-models` tag | `sherpa-onnx/` |
| Moonshine Base English int8 | `k2-fsa/sherpa-onnx` releases, `asr-models` tag (`sherpa-onnx-moonshine-base-en-int8`) | `moonshine-base-en/` |
| Moonshine Tiny English int8 (fallback) | same place | `moonshine-tiny-en/` |

- Run each download in the background. Record the final sizes, plus the file list for each MNN
  model folder (there should be `config.json`, `llm.mnn`, `llm.mnn.weight`, a visual encoder,
  tokenizer and so on).
- Don't copy anything into the app yet. That happens in M1/M3.

---

## Step 4: MNN donor investigation (read-only; time-box 30 min)

1. `git clone --depth 1 https://github.com/alibaba/MNN /home/shiva-ajay/3D/MNN`
2. Find the Android LLM chat app (expected under `apps/Android/MnnLlmChat`; verify). Then work out
   and write down:
   - Which Kotlin/Java class wraps the native LLM session (e.g. `LlmSession`), its public methods,
     and how it passes **images** for VL models and streams tokens back.
   - Which native sources and `.so` files it needs (`libMNN.so`, LLM/JNI libs, OpenCL/Vulkan
     backends, and so on), and how they are produced: prebuilt download, CMake build of MNN, or both.
   - The build command it expects and whether it downloads anything at build time.
   - All network-related code (model market, downloader, INTERNET permission). We will **not**
     carry any of this over.
3. Try one unmodified debug build of that app. If it builds within the time box, install it on the
   phone. If the build is too slow or fails, don't fight it. Instead, download the official MNN
   Chat release APK from GitHub releases and install that. (We need MNN Chat on the phone for M0
   either way.)
4. Write the findings to `docs/mnn-donor-notes.md` in this repo: class names, file paths, `.so`
   list, build steps, and the transplant plan for M1. Keep it short and concrete.

---

## Step 5: Create the FixLens app skeleton (in this repo)

Build it by hand with Gradle Kotlin DSL; don't use the Android Studio wizard.

- **Gradle:** add a Gradle wrapper (latest stable Gradle compatible with the latest stable AGP),
  version catalog `gradle/libs.versions.toml`, `settings.gradle.kts`, root `build.gradle.kts`,
  `app/build.gradle.kts`, `gradle.properties`, `local.properties` (gitignored).
- **App config:**
  - `namespace` / `applicationId` = `com.fixlens`, `minSdk 29`, `compileSdk`/`targetSdk` = the
    latest *stable* platform installed (36 unless 37 is stable; check).
  - `ndk { abiFilters += "arm64-v8a" }`, and pin `ndkVersion = "28.2.13676358"`.
  - Plugins: Android application, Kotlin Android, Kotlin Compose compiler, kotlinx-serialization.
  - Java/Kotlin target 17.
- **Dependencies for P0 only:** Compose BOM + ui + material3 + activity-compose +
  lifecycle-viewmodel-compose, kotlinx-coroutines-android, kotlinx-serialization-json. (CameraX,
  OpenCV and sherpa-onnx come in later milestones. Don't add them now.)
- **Manifest:**
  - Only `android.permission.CAMERA` and `android.permission.RECORD_AUDIO`.
  - Add an explicit guard so no library can merge INTERNET back in:
    `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`
    (and the same for `ACCESS_NETWORK_STATE`).
  - `android:screenOrientation="portrait"` on the single activity. App label "FixLens".
- **Code:**
  - `com.fixlens.app.FixLensApp` (Application) and `com.fixlens.app.MainActivity` (Compose).
  - The screen shows "FixLens", the subtitle "Offline repair assistant", and a button that
    requests the CAMERA + RECORD_AUDIO runtime permissions and shows their granted state. That's it.
  - Create the empty packages from CLAUDE.md §6 (`camera`, `vision`, `tracking`, `voice`, `kb`,
    `guide`, `ui`), each with a `package-info`-style placeholder or a `.gitkeep` so they exist in git.
  - Create `app/src/main/assets/kb/` and `app/src/main/assets/models/` (with `.gitkeep`).
  - Log tag `FixLens`: log one line on app start.
- **`.gitignore`:** standard Android (`build/`, `.gradle/`, `local.properties`, `*.iml`, `.idea/`
  except shared bits, `*.apk`), plus `*.mnn`, `*.mnn.weight`, `*.onnx`, `*.aar` under
  `app/libs/` for now, and `.kilo/`.

---

## Step 6: Verify (must actually run these, not assume)

```bash
./gradlew assembleDebug
# Prove there is no network permission in the FINAL merged APK:
$ANDROID_HOME/build-tools/<ver>/aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.fixlens -c android.intent.category.LAUNCHER 1
adb logcat -d -s FixLens | tail
```

Pass criteria:
- [ ] Build succeeds with no errors.
- [ ] `aapt2 dump permissions` lists only CAMERA and RECORD_AUDIO. **No INTERNET.**
- [ ] The APK contains only `lib/arm64-v8a` (if it has any native libs at all).
- [ ] The app installs and opens on the iQOO 15, the start log line appears in logcat, and the
      permission button works. (Ask me to confirm what I see on screen, or use `scrcpy`/`adb shell
      screencap` to check.)

If the phone isn't connected yet, finish everything else, then wait for me to connect it.

---

## Step 7: Wrap up

1. Update `PLAN.md`: tick the P0 checkboxes that are done, and note anything that deviated.
2. Update `CLAUDE.md` §15 "Notes" with: the Gradle/AGP/Kotlin/Compose BOM versions used, the
   model repos and sizes downloaded, and the MNN donor summary (one or two lines + link to
   `docs/mnn-donor-notes.md`).
3. Make the **first git commit** (skeleton + docs; no models, no build outputs). Show me
   `git status` and the commit summary first.
4. Give me a short final report:
   - what's done / not done (with the reason)
   - any download still running, and where it's going
   - anything I still need to do by hand
   - the exact first actions for **M0** (push which model folder where, for MNN Chat)
