#!/usr/bin/env python3
"""M1 grounding check on the phone: FixLens's exact prompts on the test photos, scored against hand-marked boxes.

Usage: tools/m1/run_ground_test.py [--style contract|native] [--mode target|question] [--out DIR]
  --mode target:   "Where is the <target>?" + Find: "<target>"   (a KB step's target phrase, M4)
  --mode question: the natural question + Find: "<default target>" (today, before the KB)
Each box is read both as 0..1000 and as keyframe pixels; the scale whose boxes land on the parts is the model's.
"""
import argparse, json, os, re, subprocess, sys, tempfile, time
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
import random
from cases import CASES, SYN_SHAPES, SYN_LAYOUT, SYN_RADIUS

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
SRC = '/home/shiva-ajay/3D/fixlens-testdata/m0/provisional/'
MNN = '/home/shiva-ajay/3D/MNN'
NDK = os.environ.get('ANDROID_NDK', os.path.expanduser('~/Android/ndk/28.2.13676358'))
PHONE = '/data/local/tmp/fixlens-m1'
CONFIG = os.environ.get('FIXLENS_VLM_CONFIG', '/sdcard/Android/data/com.fixlens/files/models/qwen3-vl-4b/config.json')
LONG_SIDE = 448

def kotlin_system_prompt():
    src = open(os.path.join(ROOT, 'app/src/main/java/com/fixlens/guide/FixyPrompts.kt')).read()
    expr = src.split('const val SYSTEM =', 1)[1].split('\n\n', 1)[0]
    return ''.join(json.loads('"' + m + '"') for m in re.findall(r'"((?:[^"\\]|\\.)*)"', expr))

DEFAULT_TARGET = 'the part I should look at for this question'

# Candidate for a cheaper turn: the format rule lives in the (once-prefilled) system prompt, each turn only says Find.
SHORT_RULE = ('Every reply starts with one line of JSON for the part named in Find: '
              '{"bbox_2d":[x1,y1,x2,y2],"label":"..."}, or {"bbox_2d":null} if it is not visible. '
              'Then one or two short sentences.')

DEFAULT_PARTS = 'every exact part to act on for this question (a cap, a button, a cover), never the whole device'

def grounding(target, style):
    if style == 'parts':  # FixyPrompts.GroundingStyle.Parts
        find = f'"{target}"' if target != DEFAULT_TARGET else DEFAULT_PARTS
        return (f'Find: {find}\n'
                'First: a JSON list, one entry per part: {"bbox_2d":[x1,y1,x2,y2],"label":"..."}, or '
                '{"point_2d":[x,y],"label":"..."} for a small single part. Many tiny identical parts (like screws) '
                'get one box around the area that holds them. [] if none.\n'
                'Then: one or two short sentences as Fixy.')
    if style == 'short':
        return f'Find: "{target}"'
    if style == 'contract':
        return (f'Find: "{target}"\n'
                'First line: JSON only, {"bbox_2d":[x1,y1,x2,y2],"label":"..."} or {"bbox_2d":null}\n'
                'Then: one or two short sentences as Fixy.')
    return (f'Locate {target} in the image and output its bbox coordinates in JSON on the first line, '
            'or {"bbox_2d":null} if nothing applies. Then answer in one or two short sentences as Fixy.')

def make_synthetic(out):
    """Noisy gray images with a red circle and a blue square at known places; returns cases with pixel GT."""
    cases = []
    rnd = random.Random(7)
    for shape, (w, h) in SYN_SHAPES.items():
        for name, ((cx, cy), (sx, sy)) in SYN_LAYOUT.items():
            img = f'{shape}_{name}.jpg'
            im = Image.new('RGB', (w, h))
            px = im.load()
            for y in range(h):
                for x in range(w):
                    g = 120 + rnd.randint(-25, 25)
                    px[x, y] = (g, g, g)
            d = ImageDraw.Draw(im)
            r = SYN_RADIUS * min(w, h)
            circle = (cx * w - r, cy * h - r, cx * w + r, cy * h + r)
            square = (sx * w - r, sy * h - r, sx * w + r, sy * h + r)
            d.ellipse(circle, fill=(220, 30, 30))
            d.rectangle(square, fill=(30, 60, 220))
            im.save(os.path.join(out, img), quality=92)
            cases.append((img, 'red circle', 'Where is the red circle?', tuple(int(v) for v in circle)))
            cases.append((img, 'blue square', 'Where is the blue square?', tuple(int(v) for v in square)))
    return cases

def keyframe_size(w, h):  # FrameGrabber.keyframeSize
    s = LONG_SIDE / max(w, h)
    align = lambda v: max(32, int(v / 32 + 0.5) * 32)
    return align(w * s), align(h * s)

def parse_box(out):
    """Python port of GroundingParser's first-value logic: returns (kind, [x1,y1,x2,y2] or None, label)."""
    s = out.lstrip()
    if s.startswith('```'):
        body = s.split('\n', 1)[1] if '\n' in s else ''
        s = body.split('```', 1)[0].strip()
    if not s or s[0] not in '{[':
        return 'NotJson', None, None
    depth, instr, esc, end = 0, False, False, -1
    for i, c in enumerate(s):
        if instr:
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == '"': instr = False
            continue
        if c == '"': instr = True
        elif c in '{[': depth += 1
        elif c in '}]':
            depth -= 1
            if depth == 0: end = i; break
    if end < 0: return 'Invalid', None, None
    try: v = json.loads(s[:end + 1])
    except Exception: return 'Invalid', None, None
    if isinstance(v, list):
        if not v: return 'NoBox', None, None
        v = v[0]
    if isinstance(v, dict):
        b = v.get('bbox_2d', v.get('bbox'))
        if b is None and isinstance(v.get('point_2d'), list):
            p = v['point_2d']; b = [p[0] - 15, p[1] - 15, p[0] + 15, p[1] + 15]
        if b is None: return 'NoBox', None, None
        if isinstance(b, list) and len(b) == 4: return 'Box', [float(x) for x in b], v.get('label')
    return 'Invalid', None, None

def iou(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0])); iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    ua = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / ua if ua > 0 else 0.0

def center_in(a, b, slack=0.0):
    cx, cy = (a[0] + a[2]) / 2, (a[1] + a[3]) / 2
    w, h = b[2] - b[0], b[3] - b[1]
    return b[0] - slack * w <= cx <= b[2] + slack * w and b[1] - slack * h <= cy <= b[3] + slack * h

def build_and_push(out):
    """Builds the harness against the libMNN the app ships and pushes both to the phone."""
    lib = os.path.join(ROOT, 'app/src/main/cpp/libs/arm64-v8a')
    cxx = f'{NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++'
    exe = os.path.join(out, 'ground_test')
    subprocess.check_call([cxx, '-std=c++17', '-O2', os.path.join(os.path.dirname(__file__), 'ground_test.cpp'), '-o', exe,
                           f'-I{MNN}/include', f'-I{MNN}/transformers/llm/engine/include', f'-L{lib}', '-lMNN', '-llog',
                           '-static-libstdc++'])
    subprocess.check_call(['adb', 'shell', f'mkdir -p {PHONE}/img'])
    subprocess.check_call(['adb', 'push', exe, os.path.join(lib, 'libMNN.so'), PHONE + '/'], stdout=subprocess.DEVNULL)

def push_keyframe(src_path, name, out, long_side=None):
    """Resizes an image like FrameGrabber.keyframeFromFile and pushes it; returns (orig size, keyframe size)."""
    global LONG_SIDE
    ls = long_side or LONG_SIDE
    im = Image.open(src_path).convert('RGB')
    s = ls / max(im.size)
    align = lambda v: max(32, int(v / 32 + 0.5) * 32)
    kw, kh = align(im.size[0] * s), align(im.size[1] * s)
    local = os.path.join(out, 'kf_' + name)
    im.resize((kw, kh), Image.BILINEAR).save(local, quality=90)
    subprocess.check_call(['adb', 'push', local, f'{PHONE}/img/{name}'], stdout=subprocess.DEVNULL)
    return im.size, (kw, kh)

def run_on_phone(spec, n, max_tokens, extra_json='{}', joined=False):
    """n = number of result lines expected (one per step)."""
    """Runs the cases file on the phone (detached, survives USB drops) and returns the per-case results."""
    subprocess.check_call(['adb', 'push', spec, PHONE + '/cases.txt'], stdout=subprocess.DEVNULL)
    subprocess.run(['adb', 'shell', 'am force-stop com.fixlens'])
    extra = extra_json.replace('"', '\\"')
    run = (f'cd {PHONE} && rm -f out.jsonl && (LD_LIBRARY_PATH={PHONE} timeout 1800 ./ground_test {CONFIG} {PHONE}/cases.txt '
           f'{max_tokens} "{extra}" {"joined" if joined else "split"} > out.jsonl 2> err.txt; echo DONE >> out.jsonl)')
    subprocess.run(['adb', 'shell', f"nohup sh -c '{run}' > /dev/null 2>&1 &"])
    out = ''
    for _ in range(600):
        time.sleep(4)
        p = subprocess.run(['adb', 'shell', f'cat {PHONE}/out.jsonl'], capture_output=True, text=True)
        if p.returncode != 0:
            subprocess.run(['adb', 'wait-for-device'])
            continue
        out = p.stdout
        if 'DONE' in out:
            break
    results = [json.loads(l) for l in out.splitlines() if l.startswith('{"case"')]
    if len(results) != n:
        err = subprocess.run(['adb', 'shell', f'cat {PHONE}/err.txt'], capture_output=True, text=True).stdout
        print(out[-2000:], err[-2000:]); sys.exit(1)
    return results

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--style', default='contract')
    ap.add_argument('--mode', default='target')
    ap.add_argument('--out', default=tempfile.mkdtemp(prefix='ground_'))
    ap.add_argument('--only', type=int, nargs='*')
    ap.add_argument('--set', default='real', help='real (test photos) or syn (synthetic shapes)')
    ap.add_argument('--prompt', default='app', help="app (Fixy system prompt + grounding) or plain (Qwen's own format, no system)")
    ap.add_argument('--extra', default='{}', help='extra MNN config JSON, e.g. {"mllm":{"precision":"normal"}}')
    ap.add_argument('--joined', action='store_true', help='prefill system + turn together instead of split')
    ap.add_argument('--max-tokens', type=int, default=150)
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    src_dir = SRC
    all_cases = CASES
    if args.set == 'syn':
        src_dir = args.out + '/'
        all_cases = make_synthetic(args.out)

    build_and_push(args.out)
    cases = [c for i, c in enumerate(all_cases) if not args.only or i in args.only]
    sizes = {}
    for img in sorted({c[0] for c in cases}):
        im = Image.open(src_dir + img).convert('RGB')
        kw, kh = keyframe_size(*im.size)
        sizes[img] = (im.size, (kw, kh))
        local = os.path.join(args.out, 'kf_' + img)
        im.resize((kw, kh), Image.BILINEAR).save(local, quality=90)
        subprocess.check_call(['adb', 'push', local, f'{PHONE}/img/{img}'], stdout=subprocess.DEVNULL)

    rules = kotlin_system_prompt() + (' ' + SHORT_RULE if args.style == 'short' else '')
    system = '<|im_start|>system\n' + rules + '<|im_end|>\n' if args.prompt == 'app' else ''
    turns = []
    for img, target, question, gt in cases:
        if args.prompt == 'plain':
            content = f'<img>{PHONE}/img/{img}</img>Locate the {target} in the image and output its bbox coordinates in JSON format.'
        else:
            q, t = (f'Where is the {target}?', target) if args.mode == 'target' else (question, DEFAULT_TARGET)
            content = f'<img>{PHONE}/img/{img}</img>{q}\n\n{grounding(t, args.style)}'
        turns.append(f'<|im_start|>user\n{content}<|im_end|>\n<|im_start|>assistant\n')
    spec = os.path.join(args.out, 'cases.txt')
    open(spec, 'w').write('\n=====\n'.join([system] + turns))
    subprocess.check_call(['adb', 'push', spec, PHONE + '/cases.txt'], stdout=subprocess.DEVNULL)
    print(f'Running {len(cases)} cases: set={args.set} prompt={args.prompt} style={args.style} mode={args.mode} '
          f'extra={args.extra} {"joined" if args.joined else "split"}', flush=True)
    results = run_on_phone(spec, len(cases), args.max_tokens, args.extra, args.joined)
    rows, hits = [], {'norm': 0, 'px': 0}
    for (img, target, question, gt), r in zip(cases, results):
        (ow, oh), (kw, kh) = sizes[img]
        kind, b, label = parse_box(r['out'])
        res = {}
        if b:
            for name, sx, sy in (('norm', kw / 1000, kh / 1000), ('px', 1, 1)):
                kb = [b[0] * sx, b[1] * sy, b[2] * sx, b[3] * sy]
                ob = [kb[0] * ow / kw, kb[1] * oh / kh, kb[2] * ow / kw, kb[3] * oh / kh]
                ok = center_in(ob, gt, 0.25)
                res[name] = (ob, iou(ob, gt), ok)
                hits[name] += ok
        rows.append((img, target, kind, b, label, res, r))
        im = Image.open(src_dir + img).convert('RGB'); d = ImageDraw.Draw(im)
        d.rectangle(gt, outline=(0, 255, 0), width=2)
        for name, col in (('norm', (255, 159, 28)), ('px', (61, 214, 245))):
            if name in res: d.rectangle([int(v) for v in res[name][0]], outline=col, width=3)
        d.text((4, 4), f'{target} | {kind} {b} | green=GT amber=0..1000 cyan=px', fill=(255, 255, 0))
        im.save(os.path.join(args.out, f'case{r["case"]:02d}_{img}'))

    print(f'\n{"#":>2} {"target":<44} {"kind":<8} {"raw box":<24} {"norm IoU":>8} {"px IoU":>7} {"box ms":>7} {"first":>6} {"total":>6}')
    for img, target, kind, b, label, res, r in rows:
        f = lambda n: (f'{res[n][1]:.2f}{"*" if res[n][2] else " "}' if n in res else '-')
        print(f'{r["case"]:>2} {target[:44]:<44} {kind:<8} {str([int(v) for v in b]) if b else "-":<24} {f("norm"):>8} {f("px"):>7} '
              f'{r["box_ms"]:>7.0f} {r["first_ms"]:>6.0f} {r["total_ms"]:>6.0f}')
    n = len(rows)
    boxes = sum(1 for x in rows if x[2] == 'Box')
    print(f'\nFormat followed (box line first): {boxes}/{n}. Centre on the part (* = within GT, 25% slack): '
          f'0..1000 {hits["norm"]}/{boxes}, pixels {hits["px"]}/{boxes}')
    med = lambda k: sorted(r[6][k] for r in rows)[n // 2]
    print(f'Median ms: prefill {med("prefill_ms")}, box line {med("box_ms")}, total {med("total_ms")}; '
          f'median prompt {med("prompt_tok")} tok, gen {med("gen_tok")} tok')
    for img, target, kind, b, label, res, r in rows:
        print(f'\n[{r["case"]}] {target}: {r["out"]!r}')
    print(f'\nOverlays: {args.out}')

if __name__ == '__main__':
    main()
