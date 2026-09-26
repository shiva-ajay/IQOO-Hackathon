#!/usr/bin/env bash
# Patches a taobao-mnn Qwen3-VL model dir to interleaved M-RoPE (see qwen3vl_mrope_fix.cpp for why).
# Usage: tools/mnn-patches/qwen3vl_mrope_fix.sh <model_dir>
# Writes <model_dir>/llm.mnn (patched) and keeps the original as llm.mnn.chunked. Safe to re-run.
set -euo pipefail
MNN=${MNN:-/home/shiva-ajay/3D/MNN}
DIR=$1
HERE=$(cd "$(dirname "$0")" && pwd)
BIN=$(mktemp -d)/qwen3vl_mrope_fix
g++ -std=c++17 -O2 "$HERE/qwen3vl_mrope_fix.cpp" -o "$BIN" -I"$MNN/schema/current" -I"$MNN/3rd_party/flatbuffers/include"
[ -f "$DIR/llm.mnn.chunked" ] || cp "$DIR/llm.mnn" "$DIR/llm.mnn.chunked"
"$BIN" "$DIR/llm.mnn.chunked" "$DIR/llm.mnn"
