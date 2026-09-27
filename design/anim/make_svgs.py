#!/usr/bin/env python3
"""The step-animation pictograms as SVG, for the pitch deck and for design review.

    python3 design/anim/make_svgs.py

Writes one animated SVG per how-to card (SMIL: loops in any browser; a static viewer shows the finished pose) and
`frames.svg`, a sheet of each card at several moments of its loop. The geometry mirrors ui/StepCues.kt: same unit
coordinates, timings and easing, so what you see here is what the phone draws. If you change one, change both.
"""
import math
import pathlib

OUT = pathlib.Path(__file__).resolve().parent

INK, PAPER, AMBER, OK, RED = "#0F1C2E", "#F6F1E7", "#FF9F1C", "#3DDC97", "#FF5A5F"
LOOP_MS, STILL_MS = 2600.0, 1700.0
FRAMES = 52

CARD_W, GLYPH, GX, GY = 96.0, 60.0, 18.0, 12.0
U = GLYPH
SW = 0.05 * U


# ---- timing, as in ui/fx/Motion.kt ----
def window(e, start, dur): return min(1.0, max(0.0, (e - start) / dur))
def lerp(a, b, t): return a + (b - a) * t
def ease_in_out_cubic(t): return 4 * t ** 3 if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2
def ease_out_cubic(t): return 1 - (1 - t) ** 3
def ease_out_back(t, o=1.7):
    c3 = o + 1
    u = t - 1
    return 1 + c3 * u ** 3 + o * u ** 2


# ---- geometry in glyph units (0..1), mapped into the card ----
def P(x, y): return (GX + x * U, GY + y * U)
def polar(c, r, deg):
    a = math.radians(deg)
    return (c[0] + r * math.cos(a), c[1] + r * math.sin(a))
def rot(p, c, deg):
    a = math.radians(deg)
    x, y = p[0] - c[0], p[1] - c[1]
    return (c[0] + x * math.cos(a) - y * math.sin(a), c[1] + x * math.sin(a) + y * math.cos(a))
def arc_pts(c, r, start, sweep, n=28): return [polar(c, r, start + sweep * i / (n - 1)) for i in range(n)]
def rrect_pts(x, y, w, h, r, n=5):
    pts = []
    for cx, cy, a0 in ((x + w - r, y + r, -90), (x + w - r, y + h - r, 0), (x + r, y + h - r, 90), (x + r, y + r, 180)):
        pts += [polar((cx, cy), r, a0 + 90 * i / (n - 1)) for i in range(n)]
    return pts


# Elements: ("line"|"path"|"circle"|"fillpath", color, opacity, width, data). Same list shape every frame.
def line(a, b, color, op, w=SW): return ("path", color, op, w, [a, b], False)
def poly(pts, color, op, w=SW, closed=False): return ("path", color, op, w, pts, closed)
def fill(pts, color, op): return ("fill", color, op, 0, pts, True)
def circ(c, r, color, op, w=SW): return ("circle", color, op, w, (c, r), False)
def dot(c, r, color, op): return ("dot", color, op, 0, (c, r), False)


def arrow(tip, deg, length, color, op, w=SW):
    return [line(tip, polar(tip, length, deg + 180 + s * 38), color, op, w) for s in (-1, 1)]


def arrow_arc(c, r, start, sweep, color, op, w, head):
    sweep = sweep if abs(sweep) >= 1 else (1 if sweep >= 0 else -1)
    end = start + sweep
    op = op if abs(sweep) > 1 else 0
    head_op = op if abs(sweep) >= 12 else 0
    return [poly(arc_pts(c, r, start, sweep), color, op, w)] + arrow(polar(c, r, end), end + (90 if sweep > 0 else -90), head, color, head_op, w)


# ---- the pictograms (ui/StepCues.kt) ----
def turn(ms, cw):
    vis, act = timing(ms)
    sign = 1 if cw else -1
    c, r = P(0.5, 0.5), 0.24 * U
    ang = sign * 80 * act
    els = [circ(c, r, PAPER, 0.92 * vis), circ(c, r * 0.62, PAPER, 0.35 * vis, SW * 0.6)]
    for k in range(16):
        d = k * 22.5 + ang
        els.append(line(polar(c, r * 1.02, d), polar(c, r * 1.2, d), PAPER, 0.7 * vis, SW * 0.7))
    els.append(dot(polar(c, r * 0.62, -90 + ang), 0.045 * U, AMBER, vis))
    return els + arrow_arc(c, 0.43 * U, -90 - sign * 110, sign * 220 * act, AMBER, vis, SW, 0.09 * U)


def slide(ms, pull):
    vis, act = timing(ms)
    out = act if pull else 1 - act
    dy = -0.2 * out
    els = [line(P(0.40, 0.56), P(0.40, 0.92), PAPER, 0.92 * vis), line(P(0.60, 0.56), P(0.60, 0.92), PAPER, 0.92 * vis),
           line(P(0.40, 0.92), P(0.60, 0.92), PAPER, 0.92 * vis),
           line(P(0.5, 0.42 + dy), P(0.5, 0.86 + dy), PAPER, 0.92 * vis), circ(P(0.5, 0.33 + dy), 0.07 * U, AMBER, vis)]
    k = act if pull else 1 - act
    cy = lerp(0.62, 0.34, k)
    a = vis * min(1, max(0.25, 1 - abs(act - 0.5) * 1.2))
    y0 = cy - 0.05 if not pull else cy + 0.05
    return els + [line(P(0.73, y0), P(0.80, cy), AMBER, a), line(P(0.87, y0), P(0.80, cy), AMBER, a)]


def level(ms):
    vis, _ = timing(ms)
    l, r, t, b, mx, mn = 0.38, 0.62, 0.10, 0.90, 0.34, 0.60
    rise = ease_out_back(window(ms, 160, 1100), 1.1)
    lv = lerp(b - 0.02, 0.47, rise)
    inset = SW / 2 / U
    liquid_top = max(t, lv)
    els = [fill([P(l, mx), P(r, mx), P(r, mn), P(l, mn)], OK, 0.18 * vis),
           fill([P(l + inset, liquid_top), P(r - inset, liquid_top), P(r - inset, b - inset), P(l + inset, b - inset)], AMBER, 0.9 * vis),
           poly([(x, y) for x, y in rrect_pts(*P(l, t), (r - l) * U, (b - t) * U, 0.06 * U)], PAPER, 0.92 * vis, closed=True)]
    for y in (mx, mn):
        els.append(line(P(r - 0.06, y), P(r + 0.12, y), PAPER, 0.92 * vis, SW * 0.8))
    done = window(ms, 1250, 220) * vis
    return els + [poly([P(0.14, 0.47), P(0.20, 0.53), P(0.29, 0.41)], OK, done)]


def pour(ms):
    vis, _ = timing(ms)
    tilt = lerp(-6, -34, ease_in_out_cubic(window(ms, 120, 480)))
    pv = P(0.66, 0.28)
    body = [rot(p, pv, tilt) for p in rrect_pts(*P(0.50, 0.16), 0.34 * U, 0.26 * U, 0.05 * U)]
    els = [poly(body, PAPER, 0.92 * vis, closed=True),
           line(rot(P(0.50, 0.20), pv, tilt), rot(P(0.33, 0.24), pv, tilt), PAPER, 0.92 * vis),
           line(rot(P(0.50, 0.29), pv, tilt), rot(P(0.33, 0.265), pv, tilt), PAPER, 0.92 * vis),
           line(rot(P(0.62, 0.16), pv, tilt), rot(P(0.74, 0.16), pv, tilt), PAPER, 0.92 * vis, SW * 1.5)]
    tip = rot(P(0.33, 0.2525), pv, tilt)
    fx = (tip[0] - GX) / U
    els += [line(P(fx - 0.15, 0.64), P(fx + 0.15, 0.64), PAPER, 0.92 * vis),
            line(P(fx - 0.15, 0.64), P(fx - 0.035, 0.80), PAPER, 0.92 * vis), line(P(fx + 0.15, 0.64), P(fx + 0.035, 0.80), PAPER, 0.92 * vis),
            line(P(fx - 0.035, 0.80), P(fx - 0.035, 0.92), PAPER, 0.92 * vis), line(P(fx + 0.035, 0.80), P(fx + 0.035, 0.92), PAPER, 0.92 * vis)]
    for i in range(3):
        f = window(ms, 560 + i * 240, 420)
        live = 0 < f < 1
        y = lerp(tip[1] + 0.05 * U, GY + 0.70 * U, f * f)
        els.append(dot((tip[0], y), 0.037 * U, AMBER, vis * (1 - window(f, 0.8, 0.2)) if live else 0))
    return els


def unplug(ms):
    vis, act = timing(ms)
    bx = lerp(0.35, 0.12, act)
    cord = [P(bx, 0.5)]
    end = P(max(bx - 0.12, 0.02), 0.84)
    ctrl = P(bx - 0.08, 0.5)
    cord = [(((1 - t) ** 2) * cord[0][0] + 2 * (1 - t) * t * ctrl[0] + t * t * end[0],
             ((1 - t) ** 2) * cord[0][1] + 2 * (1 - t) * t * ctrl[1] + t * t * end[1]) for t in [i / 15 for i in range(16)]]
    els = [poly(cord, PAPER, 0.92 * vis), poly(rrect_pts(*P(bx, 0.36), 0.18 * U, 0.28 * U, 0.04 * U), PAPER, 0.92 * vis, closed=True)]
    els += [line(P(bx + 0.18, y), P(bx + 0.28, y), PAPER, 0.92 * vis, SW * 0.8) for y in (0.44, 0.56)]
    plate = rrect_pts(*P(0.58, 0.26), 0.32 * U, 0.48 * U, 0.07 * U)
    els += [fill(plate, INK, 1), poly(plate, PAPER, 0.92 * vis, closed=True)]
    els += [dot(P(0.72, y), 0.028 * U, PAPER, 0.92 * vis) for y in (0.44, 0.56)]
    a = window(act, 0.35, 0.4) * vis
    return els + [line(P(0.50, 0.86), P(0.20, 0.86), AMBER, a)] + arrow(P(0.20, 0.86), 180, 0.07 * U, AMBER, a)


def switch_off(ms):
    vis, act = timing(ms)
    press = 1 - abs(act - 0.5) * 2
    i = 0.012 * press
    c = P(0.5, 0.60)
    on = 1 - act
    led = P(0.5, 0.27)
    return [poly(rrect_pts(*P(0.22, 0.14), 0.56 * U, 0.72 * U, 0.08 * U), PAPER, 0.92 * vis, closed=True),
            poly(rrect_pts(*P(0.38 + i, 0.40 + i), (0.24 - 2 * i) * U, (0.36 - 2 * i) * U, 0.04 * U), PAPER, 0.92 * vis, closed=True),
            poly(arc_pts(c, 0.07 * U, -60, 300), PAPER, 0.92 * vis, SW * 0.8),
            line((c[0], c[1] - 0.10 * U), (c[0], c[1] - 0.01 * U), PAPER, 0.92 * vis, SW * 0.8),
            dot(led, 0.07 * U, AMBER, 0.3 * on * vis), dot(led, 0.032 * U, AMBER, vis * lerp(0.25, 1, on))]


def engine_off(ms):
    vis, act = timing(ms)
    c, r = P(0.5, 0.5), 0.30 * U
    ang = 60 * (1 - act)
    key = [rot(p, c, ang) for p in rrect_pts(c[0] - 0.07 * U, c[1] - 0.22 * U, 0.14 * U, 0.44 * U, 0.07 * U)]
    return [circ(c, r, PAPER, 0.92 * vis), line(polar(c, r * 1.12, -90), polar(c, r * 1.32, -90), PAPER, 0.92 * vis),
            dot(polar(c, r * 1.22, -30), 0.028 * U, AMBER, vis * lerp(1, 0.3, act)),
            poly(key, PAPER, 0.92 * vis, closed=True), dot(rot((c[0], c[1] - 0.12 * U), c, ang), 0.026 * U, PAPER, 0.92 * vis)] + \
        arrow_arc(c, 0.44 * U, -32, -54 * act, AMBER, vis, SW, 0.08 * U)


def technician(ms):
    c = P(0.5, 0.5)
    pulse = window(ms, 200, 1200)
    live = 0.001 < pulse < 0.999
    jaw = rot((c[0], c[1] - 0.12 * U), c, 45)
    return [circ(c, lerp(0.40 * U, 0.50 * U, ease_out_cubic(pulse)), RED, 0.4 * (1 - pulse) if live else 0, SW * 0.7),
            circ(c, 0.40 * U, RED, 1),
            line(rot((c[0], c[1] - 0.02 * U), c, 45), rot((c[0], c[1] + 0.24 * U), c, 45), PAPER, 0.92, SW * 1.5),
            poly(arc_pts(jaw, 0.09 * U, -50 + 45, 280), PAPER, 0.92, SW * 1.3)]


def timing(ms):
    vis = window(ms, 0, 160) * (1 - window(ms, LOOP_MS - 380, 330))
    return vis, ease_in_out_cubic(window(ms, 160, 1050))


CARDS = [
    ("turn-anticlockwise", ["Turn", "anticlockwise"], AMBER, lambda ms: turn(ms, cw=False)),
    ("turn-clockwise", ["Turn clockwise"], AMBER, lambda ms: turn(ms, cw=True)),
    ("pull-out", ["Pull out"], AMBER, lambda ms: slide(ms, pull=True)),
    ("push-in", ["Push back in"], AMBER, lambda ms: slide(ms, pull=False)),
    ("level", ["Between the", "marks"], AMBER, level),
    ("pour", ["Pour a little"], AMBER, pour),
    ("unplug", ["Unplug first"], AMBER, unplug),
    ("switch-off", ["Switch off at", "the wall"], AMBER, switch_off),
    ("engine-off", ["Engine off"], AMBER, engine_off),
    ("technician", ["Call a", "technician"], RED, technician),
]


# ---- SVG output ----
def f(v): return f"{v:.2f}"
def d_of(pts, closed): return "M" + " L".join(f"{f(x)} {f(y)}" for x, y in pts) + (" Z" if closed else "")


def element_svg(frames):
    """One element across all frames: its still pose as attributes, and animate tags for what changes."""
    still = frames[int(STILL_MS / LOOP_MS * FRAMES)]
    kind, color, _, w, data, closed = still
    ops = [fr[2] for fr in frames]
    if kind in ("circle", "dot"):
        attrs = {"cx": [fr[4][0][0] for fr in frames], "cy": [fr[4][0][1] for fr in frames], "r": [fr[4][1] for fr in frames]}
        base = {k: v[int(STILL_MS / LOOP_MS * FRAMES)] for k, v in attrs.items()}
        paint = f'fill="none" stroke="{color}" stroke-width="{f(w)}"' if kind == "circle" else f'fill="{color}"'
        head = f'<circle cx="{f(base["cx"])}" cy="{f(base["cy"])}" r="{f(base["r"])}" {paint} opacity="{f(still[2])}"'
        anims = [anim(k, [f(x) for x in v]) for k, v in attrs.items() if max(v) - min(v) > 0.01]
    else:
        ds = [d_of(fr[4], fr[5]) for fr in frames]
        paint = f'fill="{color}"' if kind == "fill" else f'fill="none" stroke="{color}" stroke-width="{f(w)}" stroke-linecap="round" stroke-linejoin="round"'
        head = f'<path d="{d_of(data, closed)}" {paint} opacity="{f(still[2])}"'
        anims = [anim("d", ds)] if len(set(ds)) > 1 else []
    if max(ops) - min(ops) > 0.005:
        anims.append(anim("opacity", [f(o) for o in ops]))
    return head + (">" + "".join(anims) + ("</circle>" if kind in ("circle", "dot") else "</path>") if anims else "/>")


def anim(attr, values):
    return f'<animate attributeName="{attr}" dur="{LOOP_MS / 1000:.1f}s" repeatCount="indefinite" values="{";".join(values + values[:1])}"/>'


def card(label, accent, glyph, ms=None, x=0.0, y=0.0):
    """A card: animated (ms None) or frozen at [ms]."""
    h = GY + GLYPH + 8 + 15 * len(label) + 8
    out = [f'<g transform="translate({f(x)} {f(y)})">',
           f'<rect x="0.5" y="0.5" width="{CARD_W - 1}" height="{f(h - 1)}" rx="16" fill="{INK}" fill-opacity="0.86" stroke="{accent}" stroke-opacity="0.4"/>']
    if ms is None:
        frames = [glyph(LOOP_MS * i / FRAMES) for i in range(FRAMES)]
        out += [element_svg([fr[j] for fr in frames]) for j in range(len(frames[0]))]
    else:
        for kind, color, op, w, data, closed in glyph(ms):
            if op <= 0.001:
                continue
            if kind == "circle":
                out.append(f'<circle cx="{f(data[0][0])}" cy="{f(data[0][1])}" r="{f(data[1])}" fill="none" stroke="{color}" stroke-width="{f(w)}" opacity="{f(op)}"/>')
            elif kind == "dot":
                out.append(f'<circle cx="{f(data[0][0])}" cy="{f(data[0][1])}" r="{f(data[1])}" fill="{color}" opacity="{f(op)}"/>')
            elif kind == "fill":
                out.append(f'<path d="{d_of(data, True)}" fill="{color}" opacity="{f(op)}"/>')
            else:
                out.append(f'<path d="{d_of(data, closed)}" fill="none" stroke="{color}" stroke-width="{f(w)}" stroke-linecap="round" stroke-linejoin="round" opacity="{f(op)}"/>')
    for i, text in enumerate(label):
        out.append(f'<text x="{CARD_W / 2}" y="{f(GY + GLYPH + 8 + 12 + 15 * i)}" text-anchor="middle" fill="{PAPER}" '
                   f'font-family="Roboto, Inter, Helvetica, Arial, sans-serif" font-size="12" font-weight="500">{text}</text>')
    out.append("</g>")
    return "\n".join(out), h


def main():
    for name, label, accent, glyph in CARDS:
        body, h = card(label, accent, glyph)
        (OUT / f"{name}.svg").write_text(
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{int(CARD_W)}" height="{int(h)}" viewBox="0 0 {int(CARD_W)} {int(h)}">\n{body}\n</svg>\n')
    # Review sheet: each card at five moments of its loop, on a camera-dark background.
    moments = [300, 700, 1000, 1700, 2400]
    rows = []
    col_w, row_h = CARD_W + 16, 150
    for r, (name, label, accent, glyph) in enumerate(CARDS):
        for cidx, ms in enumerate(moments):
            body, _ = card(label, accent, glyph, ms, 16 + cidx * col_w, 34 + r * row_h)
            rows.append(body)
        rows.append(f'<text x="{16 + len(moments) * col_w}" y="{34 + r * row_h + 50}" fill="#9aa3ad" font-family="sans-serif" font-size="12">{name}</text>')
    heads = "".join(f'<text x="{16 + i * col_w + CARD_W / 2}" y="22" text-anchor="middle" fill="#9aa3ad" font-family="sans-serif" font-size="12">{ms} ms</text>' for i, ms in enumerate(moments))
    w, h = 16 + len(moments) * col_w + 150, 34 + len(CARDS) * row_h
    (OUT / "frames.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{int(w)}" height="{int(h)}" viewBox="0 0 {int(w)} {int(h)}">'
        f'<rect width="100%" height="100%" fill="#3a4148"/>{heads}\n' + "\n".join(rows) + "\n</svg>\n")
    print(f"{len(CARDS)} animated cards + frames.svg -> {OUT}")


if __name__ == "__main__":
    main()
