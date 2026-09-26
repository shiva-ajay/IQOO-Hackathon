#!/usr/bin/env python3
"""Quick look at what the app's current Parts prompt returns for a few photos (no ground truth; overlays to eyeball).
Usage: tools/m1/run_prompt_check.py OUT "image|question" ..."""
import os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
import run_ground_test as g
from run_multi_test import elements

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
items = [a.split('|', 1) for a in sys.argv[2:]]
g.build_and_push(out)
system = '<|im_start|>system\n' + g.kotlin_system_prompt() + '<|im_end|>\n'
turns = []
for i, (path, q) in enumerate(items):
    g.push_keyframe(path, f'chk{i}.jpg', out)
    turns.append(f'<|im_start|>user\n<img>{g.PHONE}/img/chk{i}.jpg</img>{q}\n\n{g.grounding(g.DEFAULT_TARGET, "parts")}'
                 f'<|im_end|>\n<|im_start|>assistant\n')
spec = os.path.join(out, 'cases.txt')
open(spec, 'w').write('\n=====\n'.join([system] + turns))
for (path, q), r in zip(items, g.run_on_phone(spec, len(turns), 300)):
    im = Image.open(path).convert('RGB'); w, h = im.size; d = ImageDraw.Draw(im)
    s = r['out']
    import json, re
    for m in re.finditer(r'"bbox_2d"\s*:\s*\[([^\]]+)\]', s):
        x1, y1, x2, y2 = [float(v) for v in m.group(1).split(',')]
        d.rectangle((x1 / 1000 * w, y1 / 1000 * h, x2 / 1000 * w, y2 / 1000 * h), outline=(255, 159, 28), width=3)
    for x, y in [p for p in elements(s)]:
        d.ellipse((x / 1000 * w - 5, y / 1000 * h - 5, x / 1000 * w + 5, y / 1000 * h + 5), fill=(61, 214, 245))
    name = os.path.splitext(os.path.basename(path))[0]
    im.save(os.path.join(out, name + '_check.jpg'))
    print(f'\n[{name}] "{q}" first el {r["el1_ms"]:.0f} ms, list {r["box_ms"]:.0f} ms, total {r["total_ms"]:.0f} ms\n{s[:500]!r}')
print('Overlays:', out)
