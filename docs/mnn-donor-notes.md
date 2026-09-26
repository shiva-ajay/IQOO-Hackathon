# MNN donor notes (for the M1 transplant)

Source: `alibaba/MNN` shallow clone at `/home/shiva-ajay/3D/MNN` (HEAD `6745000`, 2026-09-26).
Paths below are relative to that clone. Donor app: **`apps/Android/MnnLlmChat`**.

## 1. Donor app facts

| Item | Value |
|---|---|
| Modules | `:app`, `:mnn_tts` (`frameworks/mnn_tts/android`), `:model_downloader` (`frameworks/model_downloader/android`) |
| applicationId | `com.alibaba.mnnllm.android` (`.release` suffix on release; Play build on our phone = `com.alibaba.mnnllm.android.release` v0.8.4.gp) |
| Build | AGP 8.7.3, Kotlin 2.1.21, Gradle 8.9, compileSdk 35, minSdk 26, NDK 27.2, CMake 3.22.1, `abiFilters "arm64-v8a"` |
| Repos | google, mavenCentral, gradlePluginPortal, **jitpack.io** (Markwon fork, SpinKit, etc.) |
| Build-time downloads | `downloadSherpaMnn.gradle` fetches `libsherpa-mnn-jni-16k.zip` from `meta.alicdn.com` on every build if missing; `build_prebuilt.gradle` downloads builtin models only with `-PADD_BUILTIN=true` |

## 2. Kotlin wrapper: `app/src/main/java/com/alibaba/mnnllm/android/llm/LlmSession.kt`

- Loads `System.loadLibrary("mnnllmapp")`. Implements `ChatSession`.
- Useful natives (JNI symbols are `Java_com_alibaba_mnnllm_android_llm_LlmSession_*`, **rename them for `com.fixlens`**):
  ```kotlin
  external fun initNative(configPath: String?, history: List<String>?, mergedConfigStr: String?, configJsonStr: String?): Long
  external fun submitNative(id: Long, input: String, keepHistory: Boolean, listener: GenerateProgressListener): HashMap<String, Any>
  external fun resetNative(id: Long)
  external fun releaseNative(id: Long)
  external fun updateConfigNative(id: Long, configJson: String)
  external fun updateMaxNewTokensNative(id: Long, maxNewTokens: Int)
  external fun updateSystemPromptNative(id: Long, systemPrompt: String)
  ```
- **Streaming:** `GenerateProgressListener.onProgress(progress: String?): Boolean`, called synchronously on the
  calling thread per UTF-8 chunk; `null` = end; **return `true` to stop** (the only cancel mechanism).
  `submitNative` blocks, so run it on our single-thread VLM dispatcher.
- Returned stats map: `prompt_len`, `decode_len`, `vision_time`, `prefill_time`, `decode_time` (µs), ready for our latency logs.
- **Images:** passed as a *file path inside the prompt*: `"<img>/abs/path.jpg</img>" + text`
  (`chat/PromptUtils.kt:19-27`). The MNN engine (`transformers/llm/engine/src/omni.cpp`) regex-matches the tag and
  calls `MNN::CV::imread`. Optional size override: `<img><hw>H, W</hw>/path.jpg</img>`.
  `imread` ignores EXIF, so rotate and downscale in Kotlin and write a JPEG to `filesDir` first.
  (The app's own `processor.cpp` image loader is a stub; the engine does the work.)
- **Config:** `initNative` gets the merged model config JSON (`backend_type`, `thread_num`, `precision`, `use_mmap`,
  `temperature`, `sampler_type`, `max_new_tokens`, `system_prompt`, …) plus
  `configJsonStr = {"is_r1":false,"mmap_dir":"<dir>","keep_history":false}`. **The `mmap_dir` key must exist.**
- History: native `history_[0]` is the system prompt; `keep_history=false` truncates to it on each call.
  `resetNative` clears history + KV cache.

## 3. Native side

- JNI sources: `apps/Android/MnnLlmChat/app/src/main/cpp/`: `llm_mnn_jni.cpp`, `llm_session.{h,cpp}`,
  `llm_stream_buffer.hpp`, `utf8_stream_processor.hpp`, `mls_log.h`, `mls_config.h`, `third_party/nlohmann/json.hpp`.
  Skip: `processor.*`, `video/*`, `diffusion*`, `sana*`, `crash_util.cpp`, `mnn_wrapper_jni.cpp`.
- App CMake builds **`libmnnllmapp.so`** and links an **IMPORTED prebuilt `libMNN.so`** from
  `project/android/build_64/lib/libMNN.so` (it does *not* build MNN itself). Links `android log MNN mediandk`,
  `-Wl,-z,max-page-size=16384`.
- `.so` in the donor APK: `libmnnllmapp.so`, `libMNN.so` (single lib, `MNN_SEP_BUILD=OFF`: core + Express + CV +
  LLM + OpenCL), `libmnn_tts.so`, `libc++_shared.so`, `libsherpa-mnn-jni.so`. No separate `libllm.so` / `libMNN_CL.so`,
  and no Vulkan.
- Manifest needs `<uses-native-library android:name="libOpenCL.so" android:required="false"/>` for the GPU backend.
- MNN API used (`transformers/llm/engine/include/llm/llm.hpp`, `MNN::Transformer::Llm`): `createLLM`, `set_config`,
  `load`, `response(ChatMessages, ostream*, "<eop>", 0)`, `generate(1)` loop, `getContext()`, `reset()`.

## 4. Build steps

Native (produces `libMNN.so`, run once; README flags):
```bash
export ANDROID_NDK=/home/shiva-ajay/Android/ndk/28.2.13676358
cd /home/shiva-ajay/3D/MNN/project/android && mkdir -p build_64 && cd build_64
../build_64.sh "-DMNN_LOW_MEMORY=true -DMNN_BUILD_LLM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true -DMNN_ARM82=true \
  -DMNN_USE_LOGCAT=true -DMNN_OPENCL=true -DMNN_BUILD_OPENCV=true -DMNN_IMGCODECS=true -DMNN_BUILD_AUDIO=true \
  -DMNN_BUILD_DIFFUSION=ON -DMNN_SEP_BUILD=OFF -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' \
  -DCMAKE_INSTALL_PREFIX=."
make install
```
App: `cd apps/Android/MnnLlmChat && ./gradlew assembleStandardDebug` (pulls jitpack deps + sherpa-mnn zip).

**For FixLens (M1)**, rebuild libMNN with: `-DLLM_SUPPORT_HTTP_RESOURCE=OFF` (otherwise `omni.cpp` links cpp-httplib and
downloads `<img>http…</img>` URLs), `-DMNN_BUILD_DIFFUSION=OFF -DMNN_BUILD_AUDIO=OFF -DMNN_BUILD_BENCHMARK=OFF
-DMNN_BUILD_TEST=OFF`. (The trimmed flags are untested; fall back to the README set if the build breaks.)

## 5. Network code: do NOT carry over

- Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `REQUEST_INSTALL_PACKAGES`, foreground data-sync service.
- Whole `:model_downloader` module (`com.alibaba.mls.api.*`: HF / ModelScope / Modelers clients and downloaders,
  `DownloadForegroundService`).
- `android/modelmarket/*`, `modelist/ModelListManager`, `update/UpdateChecker`, `benchmark/LeaderboardService`,
  `com.alibaba.mnnllm.api.openai.*` (Ktor HTTP server), `chat/voice/*`, `asr/*`, `:mnn_tts`, sherpa-mnn,
  `utils/AnalyticsTracker`, `CrashReportContext`, Firebase / Crashlytics / Stetho, `GithubUtils`, `qnn/QnnModule`.
- Deps: okhttp, retrofit, Ktor, Firebase BOM, Stetho, zxing, jitpack libs.
- Native: `LLM_SUPPORT_HTTP_RESOURCE` (see §4).

## 6. Models on the phone

- MNN Chat side-load path (for M0): `adb shell mkdir -p /data/local/tmp/mnn_models && adb push <dir> /data/local/tmp/mnn_models/`.
  Every subfolder with a `config.json` shows up as a local model (`modelist/LocalModelsProvider.kt:15-21`).
- FixLens path (per STACK.md): `/sdcard/Android/data/com.fixlens/files/models/qwen3-vl-4b/` via `getExternalFilesDir`.
  This also avoids `/data/local/tmp` permission issues.

## 7. Qwen3-VL specifics

- No grounding/bbox code exists in MNN; our `GroundingParser` does it all.
- **Image size:** Qwen3-VL defaults are `image_min_pixels=65536` and `image_max_pixels=16777216`, so a full camera
  frame would become ~12k vision tokens. Set `"image_max_pixels"` (e.g. `640*640=409600`) in the config and also
  downscale the keyframe to 448–640 px ourselves. Patch alignment is 32 px (16×2 merge).

## 8. Transplant plan for M1

1. Build `libMNN.so` (FixLens flags, §4) → copy to `app/src/main/jniLibs/arm64-v8a/` or reference it as an IMPORTED lib;
   copy headers from `include/` and `transformers/llm/engine/include/` into `app/src/main/cpp/include/`.
2. Copy the JNI files from §3 into `app/src/main/cpp/`, rename JNI symbols to `Java_com_fixlens_vision_LlmSession_*`,
   and strip benchmark, wavform, Firebase/CrashReportContext and `processMultimodalPrompt` (always use `response(history_)`).
3. `CMakeLists.txt`: `add_library(fixlensllm SHARED llm_mnn_jni.cpp llm_session.cpp)`, link `android log MNN`,
   16 KB page flag. Gradle: `externalNativeBuild { cmake }`, `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`,
   `jniLibs.useLegacyPackaging = true`, `noCompress += listOf("mnn", "weight")`.
4. Kotlin: `vision/LlmSession.kt` (init/submit/reset/release natives), `GenerateProgressListener`, and `VlmEngine`
   wrapping it in a `Flow<String>` on a single-thread dispatcher, with cancel via `onProgress → true`.
5. Manifest: add `<uses-native-library android:name="libOpenCL.so" android:required="false"/>`. Still no INTERNET.
