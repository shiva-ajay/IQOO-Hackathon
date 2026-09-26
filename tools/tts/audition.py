#!/usr/bin/env python3
"""Render the same Fixy lines in several Piper voices, to pick Fixy's voice by ear (laptop only).

Dev tool, not an app dependency:
    python3 -m venv /tmp/tts-venv && /tmp/tts-venv/bin/pip install sherpa-onnx==1.13.8 numpy
    /tmp/tts-venv/bin/python tools/tts/audition.py [voice ...]

Voices are the sherpa-onnx `vits-piper-<voice>.tar.bz2` releases, extracted to fixlens-models/piper-<voice>/.
Writes fixlens-models/piper-audition/<voice>_<line>.wav and prints synthesis time and RTF per file.
Push the chosen one with `VOICE=<voice> tools/push_models.sh`.
"""
import os
import sys
import time
import wave

import numpy as np
import sherpa_onnx

MODELS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../fixlens-models")
LINES = {
    "greeting": "Hi, I'm Fixy! Point me at what's broken and tell me what's happening.",
    "step": "Look at the side of the tank. The coolant should be between MIN and MAX.",
    "numbers": "Wait 10 minutes, then pour in about 0.5 litres at a time.",
    "filler": "Let me look.",
}


def main():
    voices = sys.argv[1:] or ["en_US-lessac-medium", "en_US-hfc_female-medium", "en_US-amy-medium"]
    out = os.path.join(MODELS, "piper-audition")
    os.makedirs(out, exist_ok=True)
    for v in voices:
        d = os.path.join(MODELS, f"piper-{v}")
        tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                    model=f"{d}/{v}.onnx", tokens=f"{d}/tokens.txt", data_dir=f"{d}/espeak-ng-data"),
                num_threads=2),
            max_num_sentences=1))
        tts.generate("Warm up.")
        for name, text in LINES.items():
            t = time.perf_counter()
            audio = tts.generate(text, sid=0, speed=1.0)
            ms = (time.perf_counter() - t) * 1000
            x = np.asarray(audio.samples, dtype=np.float32)
            secs = len(x) / audio.sample_rate
            with wave.open(os.path.join(out, f"{v}_{name}.wav"), "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(audio.sample_rate)
                w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
            print(f"{v:26s} {name:9s} {ms:5.0f} ms for {secs:.2f} s (RTF {ms / 1000 / secs:.3f})")


if __name__ == "__main__":
    main()
