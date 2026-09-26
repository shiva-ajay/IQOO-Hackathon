#!/bin/bash
# Side-load FixLens models onto the phone (the app has no network access, so this is the only way in).
# Usage: tools/push_models.sh [models_dir]   (default: ../fixlens-models)
set -euo pipefail
MODELS=${1:-$(dirname "$0")/../../fixlens-models}
ADB=${ADB:-adb}
DEST=/sdcard/Android/data/com.fixlens/files

# The pre-converted Qwen3-VL graph uses the wrong M-RoPE layout for images; fix it (idempotent). See docs/marker-tracking.md §2.
"$(dirname "$0")/mnn-patches/qwen3vl_mrope_fix.sh" "$MODELS/qwen3-vl-4b" || [ $? -eq 2 ]

$ADB shell mkdir -p $DEST/models $DEST/stt/moonshine-base-en $DEST/tts/piper
$ADB push "$MODELS/qwen3-vl-4b" $DEST/models/
for f in preprocess.onnx encode.int8.onnx uncached_decode.int8.onnx cached_decode.int8.onnx tokens.txt; do
  $ADB push "$MODELS/moonshine-base-en/$f" $DEST/stt/moonshine-base-en/
done
$ADB push "$MODELS/sherpa-onnx/silero_vad.onnx" $DEST/stt/
# Fixy's voice: a Piper voice from the sherpa-onnx tts-models release (vits-piper-<voice>.tar.bz2).
# Any Piper voice works: the app loads the one .onnx in tts/piper/ with its tokens.txt and espeak-ng-data/.
VOICE=${VOICE:-en_US-lessac-medium}
$ADB shell rm -rf $DEST/tts/piper
$ADB shell mkdir -p $DEST/tts/piper
$ADB push "$MODELS/piper-$VOICE/$VOICE.onnx" "$MODELS/piper-$VOICE/tokens.txt" "$MODELS/piper-$VOICE/espeak-ng-data" $DEST/tts/piper/

# adb creates these folders as shell:ext_data_rw with mode 2770, which the app's own uid can't
# enter. Open them up, or the app reports the models as missing.
$ADB shell "find $DEST/models $DEST/stt $DEST/tts -type d -exec chmod 777 {} + ; find $DEST/models $DEST/stt $DEST/tts -type f -exec chmod 666 {} +"
echo "Models pushed to $DEST"
