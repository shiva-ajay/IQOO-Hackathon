#!/usr/bin/env python3
"""Multi-part pointing check on the phone: does the VLM point at *every* screw, not the whole laptop?

Usage: tools/m1/run_multi_test.py [--long-side 448 640] [--variants plain app app_target] [--out DIR]
Test image: a System76 laptop bottom from Wikimedia Commons (CC BY-SA) whose 13 screws were ringed in yellow;
the rings give exact ground truth and are painted out before the model sees the photo
(fixlens-testdata/m1/s76_gaze20_plain.jpg + s76_gaze20_screws_gt.json).
Scores recall (screws found) and precision (points that are on a screw), plus time to the first element and to
the whole list.
"""
import argparse, json, os, sys, tempfile
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
import run_ground_test as g

DATA = '/home/shiva-ajay/3D/fixlens-testdata/m1/'
IMAGE = 's76_gaze20_plain.jpg'
GT = json.load(open(DATA + 's76_gaze20_screws_gt.json'))
QUESTION = 'How do I open this laptop to clean it?'
MULTI = ('Find: {find}\n'
         'First: a JSON list, one entry per part: {{"point_2d":[x,y],"label":"..."}} for small parts like screws, '
         '{{"bbox_2d":[x1,y1,x2,y2],"label":"..."}} for bigger ones, or [] if none.\n'
         'Then: one or two short sentences as Fixy.')
# Answer first, point second: the chat turn names what to point at; a plain pointing request follows on the
# same KV cache (a rolled-back side request in the app).
CHAT_FIRST = (QUESTION + '\n\nFirst line: Point: <the exact parts to act on, e.g. "screws on the bottom cover">\n'
              'Then: one or two short sentences as Fixy.')
POINT_PLAIN = 'Point to every screw on the bottom of this laptop. Output JSON: [{"point_2d":[x,y],"label":"screw"}, ...]'
VARIANTS = {
    'sys_plain': ('app', ['{IMG}' + POINT_PLAIN]),
    'two_step': ('app', ['{IMG}' + CHAT_FIRST, POINT_PLAIN.replace('this laptop', 'the laptop in the picture above')]),
    'two_step_img': ('app', ['{IMG}' + CHAT_FIRST, '{IMG}' + POINT_PLAIN]),
    # Qwen3-VL's own pointing request, no persona: the best case for accuracy.
    'plain': (None, ['{IMG}' + POINT_PLAIN]),
    # The app today (no KB): the question plus a multi-part instruction with a generic target.
    'app': ('app', ['{IMG}' + QUESTION + '\n\n' + MULTI.format(find='every exact part to act on for this question (e.g. each screw, a cap, a button), never the whole device')]),
    # A KB step with a target phrase.
    'app_target': ('app', ['{IMG}Where are the screws of the bottom cover?\n\n' + MULTI.format(find='"each screw that holds the bottom cover"')]),
}
TOL = 0.035  # a point counts if it's within 3.5% of the image width of a screw


def elements(out):
    """All points/box centres in the first JSON list of the reply (model units)."""
    s = out.lstrip()
    if s.startswith('```'):
        s = s.split('\n', 1)[1] if '\n' in s else ''
        s = s.split('```', 1)[0]
    i = s.find('[')
    if i < 0:
        return []
    depth, j = 0, -1
    for k in range(i, len(s)):
        if s[k] == '[': depth += 1
        elif s[k] == ']':
            depth -= 1
            if depth == 0: j = k; break
    try:
        items = json.loads(s[i:j + 1] if j > 0 else s[i:s.rfind('}') + 1] + ']')
    except Exception:
        return []
    pts = []
    for it in items if isinstance(items, list) else []:
        if not isinstance(it, dict): continue
        if isinstance(it.get('point_2d'), list) and len(it['point_2d']) == 2:
            pts.append(tuple(float(v) for v in it['point_2d']))
        elif isinstance(it.get('bbox_2d'), list) and len(it['bbox_2d']) == 4:
            b = [float(v) for v in it['bbox_2d']]
            pts.append(((b[0] + b[2]) / 2, (b[1] + b[3]) / 2))
    return pts


def score(pts, size):
    w, h = size
    tol = TOL * w
    px = [(x / 1000 * w, y / 1000 * h) for x, y in pts]
    free = list(range(len(GT)))
    hits = 0
    for x, y in px:
        best = min(free, key=lambda i: (GT[i][0] - x) ** 2 + (GT[i][1] - y) ** 2, default=None)
        if best is not None and ((GT[best][0] - x) ** 2 + (GT[best][1] - y) ** 2) ** 0.5 <= tol:
            hits += 1
            free.remove(best)
    return hits, px


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--long-side', type=int, nargs='+', default=[448, 640])
    ap.add_argument('--variants', nargs='+', default=list(VARIANTS))
    ap.add_argument('--max-tokens', type=int, default=420)
    ap.add_argument('--out', default=tempfile.mkdtemp(prefix='multi_'))
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    g.build_and_push(args.out)
    system = '<|im_start|>system\n' + g.kotlin_system_prompt() + '<|im_end|>\n'
    cases, turns = [], []
    for ls in args.long_side:
        name = f'multi_{ls}.jpg'
        size, kf = g.push_keyframe(DATA + IMAGE, name, args.out, ls)
        for v in args.variants:
            sys_kind, text = VARIANTS[v]
            if sys_kind is None:
                continue  # plain runs in its own pass (no system prompt)
            cases.append((ls, v, size, kf, name, len(text)))
            img = f'<img>{g.PHONE}/img/{name}</img>'
            turns.append('\n-----\n'.join(
                f'<|im_start|>user\n{t.replace("{IMG}", img)}<|im_end|>\n<|im_start|>assistant\n' for t in text))
    results = []
    if turns:
        spec = os.path.join(args.out, 'cases_app.txt')
        open(spec, 'w').write('\n=====\n'.join([system] + turns))
        results += g.run_on_phone(spec, sum(c[5] for c in cases), args.max_tokens)
    if 'plain' in args.variants:
        plain_turns = []
        for ls in args.long_side:
            name = f'multi_{ls}.jpg'
            size, kf = Image.open(DATA + IMAGE).size, None
            cases.append((ls, 'plain', size, kf, name, 1))
            plain_turns.append(f'<|im_start|>user\n<img>{g.PHONE}/img/{name}</img>{POINT_PLAIN}<|im_end|>\n<|im_start|>assistant\n')
        spec = os.path.join(args.out, 'cases_plain.txt')
        open(spec, 'w').write('\n=====\n'.join([''] + plain_turns))
        results += g.run_on_phone(spec, len(plain_turns), args.max_tokens, joined=True)

    print(f'\n{"keyframe":>9} {"variant":<11} {"found":>6} {"points":>6} {"prec":>5} {"prompt":>6} {"gen":>4} '
          f'{"1st el ms":>9} {"list ms":>8} {"total ms":>8}')
    per_case, i = [], 0
    for c in cases:
        per_case.append(results[i:i + c[5]])
        i += c[5]
    for (ls, v, size, kf, name, nsteps), steps in zip(cases, per_case):
        r = steps[-1]
        pts = elements(r['out'])
        hits, px = score(pts, size)
        prec = hits / len(pts) if pts else 0
        print(f'{ls:>9} {v:<11} {hits:>3}/{len(GT):<2} {len(pts):>6} {prec:>5.2f} {r["prompt_tok"]:>6} {r["gen_tok"]:>4} '
              f'{r["el1_ms"]:>9.0f} {r["box_ms"]:>8.0f} {r["total_ms"]:>8.0f}')
        im = Image.open(DATA + IMAGE).convert('RGB')
        d = ImageDraw.Draw(im)
        for x, y in GT:
            d.ellipse((x - 14, y - 14, x + 14, y + 14), outline=(0, 255, 0), width=2)
        for x, y in px:
            d.ellipse((x - 6, y - 6, x + 6, y + 6), fill=(255, 159, 28))
        d.text((6, 6), f'{v} @{ls}: {hits}/{len(GT)} found, {len(pts)} points (green=GT, amber=model)', fill=(255, 255, 0))
        im.save(os.path.join(args.out, f'{v}_{ls}.jpg'))
    for (ls, v, *_), steps in zip(cases, per_case):
        for r in steps:
            print(f'\n[{v} @{ls} step {r.get("step", 0)}] prompt {r["prompt_tok"]} tok, {r["total_ms"]:.0f} ms: {r["out"][:500]!r}')
    print(f'\nOverlays: {args.out}')


if __name__ == '__main__':
    main()
