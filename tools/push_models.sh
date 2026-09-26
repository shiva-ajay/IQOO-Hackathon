#!/bin/bash
# Side-load FixLens models onto the phone (the app has no network access, so this is the only way in).
# Usage: tools/push_models.sh [models_dir]   (default: ../fixlens-models)
set -euo pipefail
MODELS=${1:-$(dirname "$0")/../../fixlens-models}
ADB=${ADB:-adb}
DEST=/sdcard/Android/data/com.fixlens/files

$ADB shell mkdir -p $DEST/models $DEST/stt/moonshine-base-en
$ADB push "$MODELS/qwen3-vl-4b" $DEST/models/
for f in preprocess.onnx encode.int8.onnx uncached_decode.int8.onnx cached_decode.int8.onnx tokens.txt; do
  $ADB push "$MODELS/moonshine-base-en/$f" $DEST/stt/moonshine-base-en/
done
$ADB push "$MODELS/sherpa-onnx/silero_vad.onnx" $DEST/stt/

# adb creates these folders as shell:ext_data_rw with mode 2770, which the app's own uid can't
# enter. Open them up, or the app reports the models as missing.
$ADB shell "find $DEST/models $DEST/stt -type d -exec chmod 777 {} + ; find $DEST/models $DEST/stt -type f -exec chmod 666 {} +"
echo "Models pushed to $DEST"
