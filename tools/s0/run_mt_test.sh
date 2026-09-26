#!/usr/bin/env bash
# Builds tools/s0/mt_test.cpp against the patched libMNN and runs it on the phone.
set -euo pipefail
MNN=/home/shiva-ajay/3D/MNN
NDK=${ANDROID_NDK:-$HOME/Android/ndk/28.2.13676358}
CXX=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++
OUT=$(mktemp -d)
$CXX -std=c++17 -O2 "$(dirname "$0")/mt_test.cpp" -o "$OUT/mt_test" \
  -I"$MNN/include" -I"$MNN/transformers/llm/engine/include" \
  -L"$MNN/project/android/build_64" -lMNN -llog -static-libstdc++
DIR=/data/local/tmp/fixlens-m0
adb push "$OUT/mt_test" "$MNN/project/android/build_64/libMNN.so" "$DIR/" >/dev/null
adb shell "cd $DIR && chmod +x mt_test && LD_LIBRARY_PATH=$DIR ./mt_test /sdcard/Android/data/com.fixlens/files/models/qwen3-vl-4b/config.json $DIR/img"
