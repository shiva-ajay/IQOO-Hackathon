#!/usr/bin/env python3
"""Coarse-to-fine check: are small parts found once they're bigger in frame (the user moved closer)?
Crops of the System76 laptop bottom at 2x zoom (screws ~13 px in the keyframe instead of ~6), plus the engine bay
with several medium parts. Same prompts as the app (Fixy system prompt + Parts instruction), and the plain one."""
import json, os, sys, tempfile
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
import run_ground_test as g
from run_multi_test import elements, MULTI

OUT = sys.argv[1] if len(sys.argv) > 1 else tempfile.mkdtemp(prefix='zoom_')
os.makedirs(OUT, exist_ok=True)
M1 = '/home/shiva-ajay/3D/fixlens-testdata/m1/'
M0 = '/home/shiva-ajay/3D/fixlens-testdata/m0/provisional/'
GT = json.load(open(M1 + 's76_gaze20_screws_gt.json'))
PARTS = MULTI.format(find='every exact part to act on for this question (e.g. each screw, a cap, a button), never the whole device')
POINT = 'Point to every screw in the image. Output JSON: [{"point_2d":[x,y],"label":"screw"}, ...]'
crops = {'bottom_left': (0, 360, 480, 665), 'bottom_right': (480, 360, 960, 665), 'top_left': (0, 0, 480, 330)}
images = []
for name, box in crops.items():
    im = Image.open(M1 + 's76_gaze20_plain.jpg').crop(box)
    path = os.path.join(OUT, f'{name}.jpg'); im.save(path, quality=92)
    gt = [(x - box[0], y - box[1]) for x, y in GT if box[0] <= x < box[2] and box[1] <= y < box[3]]
    images.append((name, path, gt))
images.append(('engine', M0 + 'car_volt_640.jpg', None))

g.build_and_push(OUT)
system = '<|im_start|>system\n' + g.kotlin_system_prompt() + '<|im_end|>\n'
cases, turns = [], []
for name, path, gt in images:
    size, kf = g.push_keyframe(path, name + '.jpg', OUT)
    img = f'<img>{g.PHONE}/img/{name}.jpg</img>'
    q = 'How do I open this laptop to clean it?' if gt is not None else 'Which caps can I open to check the fluids?'
    variants = [('app', q + '\n\n' + PARTS)] + ([('plain', POINT)] if gt is not None else [])
    for v, text in variants:
        cases.append((name, v, path, gt, kf))
        turns.append(f'<|im_start|>user\n{img}{text}<|im_end|>\n<|im_start|>assistant\n')
spec = os.path.join(OUT, 'cases.txt')
open(spec, 'w').write('\n=====\n'.join([system] + turns))
res = g.run_on_phone(spec, len(turns), 300)
print(f'\n{"image":<13} {"variant":<6} {"found":>6} {"points":>6} {"1st el ms":>9} {"list ms":>8} kf')
for (name, v, path, gt, kf), r in zip(cases, res):
    pts = elements(r['out'])
    im = Image.open(path).convert('RGB'); w, h = im.size
    px = [(x / 1000 * w, y / 1000 * h) for x, y in pts]
    hits = 0
    if gt:
        free = list(gt)
        for x, y in px:
            best = min(free, key=lambda p: (p[0] - x) ** 2 + (p[1] - y) ** 2, default=None)
            if best and ((best[0] - x) ** 2 + (best[1] - y) ** 2) ** 0.5 <= 0.035 * 960:
                hits += 1; free.remove(best)
    d = ImageDraw.Draw(im)
    for x, y in gt or []:
        d.ellipse((x - 14, y - 14, x + 14, y + 14), outline=(0, 255, 0), width=2)
    for x, y in px:
        d.ellipse((x - 6, y - 6, x + 6, y + 6), fill=(255, 159, 28))
    im.save(os.path.join(OUT, f'{name}_{v}.jpg'))
    found = f'{hits}/{len(gt)}' if gt is not None else '-'
    print(f'{name:<13} {v:<6} {found:>6} {len(pts):>6} {r["el1_ms"]:>9.0f} {r["box_ms"]:>8.0f} {kf}')
for (name, v, *_), r in zip(cases, res):
    print(f'\n[{name} {v}] {r["out"][:400]!r}')
print('Overlays:', OUT)
