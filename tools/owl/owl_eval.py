"""Phase 0a (docs/owlv2-pointer-plan.md §4): does OWLv2 find our parts? Laptop, CPU, Hugging Face weights.

Scores the same cases as the Qwen harnesses (tools/m1/cases.py, same hit rule: the top box's centre inside the
ground-truth box with 25% slack), plus the 13 ThinkPad screws and "not in view" checks, at several thresholds.

    ~/3D/owl-venv/bin/python tools/owl/owl_eval.py [--model DIR] [--out DIR]
"""
import argparse
import json
import os
import sys
import time

import torch
from PIL import Image, ImageDraw
from transformers import Owlv2ForObjectDetection, Owlv2Processor

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'm1'))
from cases import CASES  # noqa: E402

DATA = os.path.expanduser('~/3D/fixlens-testdata')
M0 = os.path.join(DATA, 'm0/provisional')
M1 = os.path.join(DATA, 'm1')
THRESHOLDS = [0.05, 0.1, 0.15, 0.2, 0.3]

# Short names, as Qwen would name the part in a normal question (phase 2), next to the long KB-style phrase.
SHORT = {
    "program selector dial": "dial",
    "start/pause button": "start button",
    "ARISTON brand logo": "logo",
    "cancel button marked with an X": "cancel button",
    "door lock indicator light with the padlock symbol": "padlock symbol",
    "yellow engine oil filler cap": "oil filler cap",
    "white coolant reservoir with the cap": "coolant reservoir",
    "windshield washer fluid cap": "washer fluid cap",
    "digital display showing the number": "digital display",
    "red power button": "power button",
}
SCREW_PHRASES = ["screw", "a screw", "screw head"]
ENGINE_PHRASES = ["yellow engine oil filler cap", "white coolant reservoir with the cap", "windshield washer fluid cap",
                  "engine oil dipstick"]
NOT_IN_VIEW = [("s76_gaze20_plain.jpg", p) for p in ENGINE_PHRASES] + \
              [("washer_ariston_640.jpg", p) for p in SCREW_PHRASES[:1] + ENGINE_PHRASES[:1]] + \
              [("washer_heran_640.jpg", p) for p in SCREW_PHRASES[:1] + ENGINE_PHRASES[:1]]


def path(name):
    return os.path.join(M1 if name.startswith('s76') else M0, name)


def iou(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0])); iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    ua = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / ua if ua > 0 else 0.0


def center_in(a, b, slack=0.0):
    cx, cy = (a[0] + a[2]) / 2, (a[1] + a[3]) / 2
    w, h = b[2] - b[0], b[3] - b[1]
    return b[0] - slack * w <= cx <= b[2] + slack * w and b[1] - slack * h <= cy <= b[3] + slack * h


def nms(dets, thr=0.3):
    keep = []
    for d in sorted(dets, key=lambda d: -d[1]):
        if all(iou(d[0], k[0]) < thr for k in keep):
            keep.append(d)
    return keep


class Owl:
    def __init__(self, model_dir):
        self.proc = Owlv2Processor.from_pretrained(model_dir)
        self.model = Owlv2ForObjectDetection.from_pretrained(model_dir).eval()
        self.cache = {}

    def run(self, name, phrases):
        """[(box px of the source image, score)] per phrase, all 3600 patches."""
        img = Image.open(path(name)).convert('RGB')
        t = time.time()
        with torch.no_grad():
            inputs = self.proc(text=[phrases], images=img, return_tensors='pt')
            out = self.model(**inputs)
        ms = (time.time() - t) * 1000
        side = max(img.size)  # padded bottom/right to a square, boxes normalised to it
        boxes = out.pred_boxes[0]  # cx, cy, w, h
        xyxy = torch.stack([boxes[:, 0] - boxes[:, 2] / 2, boxes[:, 1] - boxes[:, 3] / 2,
                            boxes[:, 0] + boxes[:, 2] / 2, boxes[:, 1] + boxes[:, 3] / 2], -1) * side
        scores = torch.sigmoid(out.logits[0])  # [3600, Q]
        res = {}
        for q, p in enumerate(phrases):
            res[p] = [(xyxy[i].tolist(), scores[i, q].item()) for i in range(xyxy.shape[0])]
        return img, res, ms, inputs['input_ids'].shape


def draw(img, dets, gts, out, color='lime'):
    im = img.copy()
    d = ImageDraw.Draw(im)
    for g in gts:
        d.rectangle(g, outline='red', width=2)
    for b, s in dets:
        d.rectangle(b, outline=color, width=2)
        d.text((b[0] + 2, b[1] + 2), f"{s:.2f}", fill=color)
    im.save(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--model', default=os.path.expanduser('~/3D/fixlens-models/owlv2-hf'))
    ap.add_argument('--out', default='/tmp/owl_eval')
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    owl = Owl(a.model)
    report = {}

    # --- Single parts: long (KB-style) and short phrases ---
    by_image = {}
    for img, phrase, _, gt in CASES:
        by_image.setdefault(img, []).append((phrase, gt))
    single = []  # (phrase kind, phrase, top score, hit)
    for name, items in by_image.items():
        phrases = [p for p, _ in items] + [SHORT[p] for p, _ in items if p in SHORT]
        img, res, ms, shape = owl.run(name, phrases)
        print(f"{name}: {len(phrases)} phrases in {ms:.0f} ms (tokens {tuple(shape)})")
        for p, gt in items:
            for kind, q in (("long", p), ("short", SHORT.get(p))):
                if q is None:
                    continue
                top = max(res[q], key=lambda d: d[1])
                hit = center_in(top[0], gt, 0.25)
                single.append((kind, q, top[1], hit))
                print(f"  [{kind:5}] {q!r:55} top {top[1]:.3f} {'HIT ' if hit else 'miss'} iou {iou(top[0], gt):.2f}")
                draw(img, [top], [gt], os.path.join(a.out, f"single_{kind}_{name[:-4]}_{q[:24].replace('/', '_').replace(' ', '_')}.jpg"))

    # --- Screws: all instances ---
    gt_pts = json.load(open(os.path.join(M1, 's76_gaze20_screws_gt.json')))
    img, res, ms, _ = owl.run('s76_gaze20_plain.jpg', SCREW_PHRASES)
    print(f"screws: {ms:.0f} ms")
    screws = {}
    for p in SCREW_PHRASES:
        for t in THRESHOLDS:
            dets = nms([d for d in res[p] if d[1] >= t])
            used, matched = set(), 0
            for (x, y) in gt_pts:
                for i, (b, _) in enumerate(dets):
                    if i not in used and b[0] - 4 <= x <= b[2] + 4 and b[1] - 4 <= y <= b[3] + 4:
                        used.add(i); matched += 1
                        break
            prec = len(used) / len(dets) if dets else 0.0
            screws[(p, t)] = (matched, len(dets), prec)
            print(f"  {p!r:14} t={t:.2f}: found {matched}/13, {len(dets)} boxes, precision {prec:.0%}")
            if t in (0.1, 0.2):
                g = [(x - 6, y - 6, x + 6, y + 6) for x, y in gt_pts]
                draw(img, dets, g, os.path.join(a.out, f"screws_{p.replace(' ', '_')}_{t}.jpg"))

    # --- Not in view: best score must stay under the threshold ---
    niv = []
    for name in sorted({n for n, _ in NOT_IN_VIEW}):
        phrases = [p for n, p in NOT_IN_VIEW if n == name]
        img, res, _, _ = owl.run(name, phrases)
        for p in phrases:
            top = max(res[p], key=lambda d: d[1])
            niv.append((name, p, top[1]))
            print(f"  not in view {name} {p!r}: best {top[1]:.3f}")
            draw(img, [top], [], os.path.join(a.out, f"niv_{name[:-4]}_{p[:20].replace(' ', '_')}.jpg"), 'orange')

    # --- Summary per threshold ---
    print("\nSUMMARY (pass: screws >= 9/13 at >= 70% precision, single >= 9/11, all not-in-view under t)")
    for t in THRESHOLDS:
        for kind in ("long", "short"):
            rows = [r for r in single if r[0] == kind]
            found = sum(1 for r in rows if r[3] and r[2] >= t)
            wrong = sum(1 for r in rows if not r[3] and r[2] >= t)
            print(f"t={t:.2f} single[{kind}]: {found}/{len(rows)} found, {wrong} wrong boxes above t")
        for p in SCREW_PHRASES:
            m, n, pr = screws[(p, t)]
            print(f"t={t:.2f} screws[{p}]: {m}/13, {n} boxes, precision {pr:.0%}")
        over = [(n, p, round(s, 3)) for n, p, s in niv if s >= t]
        print(f"t={t:.2f} not-in-view over t: {len(over)}/{len(niv)} {over}")
    print(f"\nOverlays: {a.out}")


if __name__ == '__main__':
    main()
