# FixLens — Step Animations Plan

> **Built (2026-09-27), priority A:** `ui/StepCues.kt` (how-to card top right, cue on the tracked part for turn /
> pull / push / pour), KB fields `steps[].anim` + `safety_anim` (validated: a turn's direction must be in the step's
> words), 24 steps + 25 safety lines tagged (`tools/kb/suggest_anims.py`, reviewed by hand). SVG versions and a frame
> sheet for review: `design/anim/` (`python3 design/anim/make_svgs.py`). Not yet tried on the phone.

Idea (2026-09-27): while a guided step is on screen, show a small looping animation that shows **how** to do it
(turn this way, pull out, pour, which screwdriver). Drawn by us as vector art, no photos, no new library.

---

## 1. What the KB actually asks users to do

Counted over the 348 steps of `2026-09-27.generic1` (keyword match on `steps[].say`; a step can count twice):

| Action | Steps | Examples |
|---|---|---|
| Look / check / find | 65 | "Find the dipstick", "Check the drain hose isn't bent" |
| Stop → technician / mechanic | 47 | last step of most entries, 11 technician-only entries |
| Clean / wipe / rinse / blow | 32 | AC filters, door seal, laptop fan, battery terminals |
| Switch off / unplug | 26 | + 69 safety lines, most of them "switch off / unplug / engine off" |
| Press / hold a button | 25 | child lock 3 s, remote power 8 s, start ≤ 5 s |
| Wait | 24 | "wait five minutes", "half an hour to an hour" |
| Level between marks | 22 | dipstick, coolant tank, brake fluid, oil window, battery water |
| Push in | 21 | dipstick back in, filter back in, plug in |
| Pull / slide out | 15 | dipstick, AC filters, detergent drawer, drain tube |
| Battery / lead order (+ / −) | 14 | bike terminals, jump start, remote batteries |
| Screw cap on / tighten | 10 | oil filler cap, washer drain filter "clockwise", valve cap |
| Pour / top up | 10 | oil, coolant, washer fluid, distilled water |
| Clips / pry | 9 | air filter box clips, laptop cover seam |
| Unscrew / twist open | 4 | oil filler cap, washer drain filter "anticlockwise", inlet hose |
| Knob turn | 4 | geyser "clockwise to warmer", fridge "one step colder" |
| **Real screws** | **2** | only `laptop_open_bottom_cover` (remove / refit) |

So a screwdriver card helps one entry today. Turning caps, pulling/pushing, levels, pouring and "switch off first"
cover most of the car and washer demo flows.

---

## 2. Two layers

```
┌──────────────────────────────┐
│ ←  Car oil check             │  TopBar
│                   ┌────────┐ │
│                   │  ↺     │ │  (1) How-to card, top right: tool + looping
│                   │ [cap]  │ │      action, one short label ("Unscrew")
│                   │Unscrew │ │
│        ╭──╮       └────────┘ │
│      ↺ │▓▓│ ↺                │  (2) In-scene cue ON the tracked marker:
│        ╰──╯                  │      arrow circling the real cap, moving
│                              │      with it as the phone moves
│ ┌──────────────────────────┐ │
│ │ Step 6 of 9 · Oil check  │ │  StepBanner + ConversationCard (as now)
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

1. **How-to card (top right)**: about 112 dp square, below the TopBar, semi-opaque dark card. Appears with the step,
   loops, fades out after the user says "done". If the marker's box sits under the card, the card moves to the
   top left.
2. **In-scene cue on the marker**: the same motion drawn around the tracked box (a curved arrow around the cap, an
   up/down arrow along the dipstick, a press ripple on a button). It rides on the existing tracker, so it stays on
   the real part. This is the stronger version: it builds on what makes FixLens new (the marker locked to the part).

---

## 3. Animation list

Priority A = the two demo flows (car oil, washer drain) and the wiring refusal. Build these first.

| # | Animation | Card (top right) | On the marker | Used by (steps) | Pri |
|---|---|---|---|---|---|
| 1 | **Turn to open / close** | cap or round filter rotating, curved arrow ↺ "Unscrew" / ↻ "Tighten" | curved arrow circling the box in that direction | oil filler cap, coolant tank cap, washer drain filter, inlet hose, bike oil cap, valve cap (~14) | A |
| 2 | **Pull out / push in** | dipstick or filter sliding out of its tube, then back | straight arrow along the part, pulsing | dipsticks, AC filters, detergent drawer, drain tube (~36) | A |
| 3 | **Level between marks** | gauge with MIN / MAX band, liquid settling inside the band (green tick) | small MIN–MAX bracket beside the box | dipstick, coolant, brake fluid, washer fluid, bike oil window, battery water (~22) | A |
| 4 | **Pour a little** | can tilting, three drops into an opening, "little at a time" | drops falling into the box | oil, coolant, washer fluid, distilled water (~10) | A |
| 5 | **Switch off / unplug first** | plug sliding out of a socket, or wall switch flipping to off; car/bike: key turning to off | none | the safety lines (69) and ~26 steps | A |
| 6 | **Stop: technician** | raised hand + person with a toolbox, red accent | marker hidden | escalation screen, 11 technician entries, last steps (~47) | A |
| 7 | **Hold for N seconds** | finger on a button with a ring filling for N s ("Hold 3 s") | ring filling around the box | child lock, remote reset 8 s, start ≤ 5 s (~25) | B |
| 8 | **Wait** | clock hand sweeping, "5 min" | none | settle oil, cool engine, geyser heat-up (~24) | B |
| 9 | **Undo clips / pry** | clip flipping open; plastic card prying along a seam, crossed-out screwdriver | arrow along the seam | air filter box, laptop cover (~9) | B |
| 10 | **Cable / lead order** | numbered sequence: ① minus off ② plus off; refit in reverse. Jump start: ① red to flat + ② red to good + ③ black to good − ④ black to bare metal | numbers on the terminals if both are boxed | bike and car battery, jump start (~14) | B |
| 11 | **Clean** | cloth wiping / water rinsing / short air puffs, by step verb | none | filters, seals, fan, terminals (~32) | C |
| 12 | **Screwdriver** | the right tool (see §4) turning ↺, with its tip shape drawn big | arrow around the screw area | laptop cover (2) | C |
| 13 | **Knob turn** | knob turning one notch, "warmer" / "colder" | curved arrow on the knob | geyser, fridge (~4) | C |
| 14 | **Don't** | the thing crossed out in red: hot radiator cap, metal in a microwave, tap water in a battery | none | from `steps[].caution` | C |
| 15 | **Remote camera test** | remote pointed at a phone, tip flashing | none | TV and AC remote entries (2) | C |

---

## 4. Screwdrivers: be careful

- **"Star screwdriver" in India usually means a Phillips (cross, +) screwdriver.** A real star is a Torx (six points).
  The card must draw the tip shape so there's no confusion: **+ Phillips**, **− flat**, **✱ Torx**, **⬡ hex**.
- **The tool comes from the KB, not from the camera.** Add the tool to the step (e.g. laptop cover: "small Phillips,
  size 0 or 1", taken from the Dell/HP/Lenovo service manuals; to check).
- **Reading the screw head with the VLM is optional and risky.** Laptop screws are 2–3 mm; the 4B model already
  invents screw positions (docs/marker-tracking.md §6). If we try it: crop the tracked area from the full 1280×720
  frame, ask one fixed-choice question ("cross, flat, six-point star, hexagon, or can't tell?"), and only show a
  guess that is one of those options, phrased as "looks like a cross screw". Anything else → the KB's tool.

---

## 5. Data: the KB decides, never the VLM

A wrong turning direction or tool costs more trust than no animation. So the animation is a verified part of the
step, like its words:

```json
{"n": 6, "say": "When it stops, cap the tube and slowly unscrew the round filter anticlockwise.",
 "target": "the round filter cap with a grip handle behind the bottom front flap",
 "anim": {"kind": "turn", "dir": "ccw", "label": "Unscrew"}}
```

- `kind`: `turn | pull | push | level | pour | unplug | technician | hold | wait | clips | order | clean | screwdriver | knob | dont`
  with small extras (`dir`, `seconds`, `tool`, `order: ["minus","plus"]`).
- Direction only where the step says it: "unscrew"/"anticlockwise" → `ccw`, "screw on"/"tighten"/"clockwise" →
  `cw` (standard right-hand thread). Anything else gets no direction.
- `build_kb.py` can **propose** `anim` from the step's verbs (the keyword table in §1); a person accepts each one,
  and `KbRepository.problems()` checks the kinds. No guessing at runtime.
- Optional entry-level `"tools": ["cloth", "funnel", "small Phillips screwdriver"]` for a "You'll need" card at the start
  of a guide (the idea behind MyFixit's toolbox field).

---

## 6. How to build it

- **Art:** each animation is drawn as an SVG (kept in `design/anim/` for the pitch deck) and as Compose code: static
  parts as `ImageVector` (Android Studio can import the SVG) and the motion (rotate, slide, fill, fade) as Compose
  animations. Android can't play SVG animations directly; **no Lottie** (a new dependency needs asking, CLAUDE.md §12.6).
- **Where:** `ui/anim/StepAnimations.kt` (one composable per `kind`), `HowToCard` placed top end in `CameraScreen`,
  the in-scene cue drawn in `MarkerOverlay` from the marker box already there.
- **Cost:** a few vector paths redrawn at 60 fps on the main thread's render: negligible. The tracker, camera,
  audio and VLM threads aren't touched.
- **Style:** the app's palette, flat line icons, thick strokes, one accent colour per meaning (action = brand
  colour, "don't" / technician = red), no text beyond one or two words.
- **Accessibility:** the label is also in the step text that Fixy speaks, so the animation is extra, never the only
  way to understand the step. Reduced-motion setting → show the last frame, still.

## 7. Suggested order (time-boxed)

1. `anim` field + validation + the 6 priority-A animations, card only (about 2–3 h).
2. The in-scene cue for `turn` and `pull/push` on the marker (about 1 h). Best demo value.
3. Tag the car oil and washer drain entries by hand; try both flows on the phone.
4. Priority B and C after the hackathon, with the `tools` field and the screw-head check.
