# Fixy takes the remote: implementation plan

Status (2026-09-27): **built and unit-tested, not yet tried on the phone.** D1–D6 taken as recommended (D6: the
dataset was committed by hand, so it stays tracked). Done: R0–R5 and the pad (R6). Not done: KB `ir` steps (§8), the
screen-change probe (TV answers come from the user or the VLM check), the aim sensor, reading the AC display before the
first test (pairing sends "on, cool 24°"). Code: `ir/` (logic), `ui/remote/` (badge, pairing card, brand sheet, pad),
`cpp/irext/` + `cpp/fixlens_ir.cpp` (AC decoder), `tools/ir/` (asset build).

Fixy looks at an appliance (AC, TV, projector, fan), works out its brand, pairs with it through the phone's IR
blaster the way a universal remote does, and then controls it itself: it sends a command, checks the result
(beep, screen change, camera, or asking), and carries on. A badge in the top-right shows whenever Fixy holds the
remote.

---

## 0. Decisions needed before coding

| # | Decision | Why it needs you | Recommendation |
|---|---|---|---|
| D1 | Add `android.permission.TRANSMIT_IR` | STACK.md §5 allows only CAMERA + RECORD_AUDIO | Yes: normal permission, no dialog, no network |
| D2 | Vendor IRext's C decoder (`irext/core/decoder`, MIT, ~5.4k lines of plain C) into `app/src/main/cpp/irext/` | New dependency (CLAUDE.md §12.6) | Yes: the only open way to build any AC state offline |
| D3 | Short mic "listen window" (~1.5 s after each AC test code) to hear the AC's confirmation beep | Push-to-talk rule (CLAUDE.md §11) | Yes, as an extra signal only; the audio is analysed and dropped, never transcribed or stored |
| D4 | How pairing moves on | UX choice | **User-paced** (like Mi Remote), auto-confirmed when a beep or screen change is detected |
| D5 | Manual remote pad (AC temp +/-, TV volume…) after pairing | Scope | Yes but small; it's also the fallback when the VLM misreads |
| D6 | Commit `ir-dataset/` (22 MB) or `.gitignore` it | Repo size | Ignore it; commit only the build script + the compact `assets/ir/` |

---

## 1. Demo script (what the jury sees)

1. User points at the venue TV or AC: *"Fixy, the AC isn't cooling."*
2. Fixy: *"That's an LG split AC. Want me to take the remote and check it?"* Card: **Take the remote** / **Not now**.
3. Pairing panel opens mid-screen: "Pair with LG air conditioner · Model 1 of 6". Fixy: *"Point the top of your
   phone at the AC."* The code fires when the phone is aimed. Beep → **"Beep detected"** → second check
   (temperature up) → beep → panel shows the checkmark and shrinks into the **remote badge** top-right.
4. Fixy: *"Got it. I'll set it to cool at 24."* The badge's signal arcs pulse on each send. Fixy: *"Show me the AC
   display."* The camera reads "24" and Fixy says *"It's set to cool at 24. If the air is still warm in 10
   minutes, the filter may be clogged. Want me to show you where it is?"* → hands over to the existing KB guide.

That last hand-off (remote control → pointing at the filter) is what makes it feel like one agent, not two apps.

---

## 2. States

```
                 ┌────────────────────── "stop controlling" / badge ▸ Release ─────────────────────┐
                 ▼                                                                                  │
 Idle ──"take the remote"/"turn on the AC"──► Identify ──► BrandConfirm ──► Pairing ──► Paired ──► Controlling
                                              (VLM, 1 shot)  │    ▲           │  ▲          │           │
                                                             │    │           │  └ next model┘           │
                                              no brand ──► BrandAsk (voice/typed/picker)                 │
                                                                              │                          │
                                                               all models failed ──► NoMatch ──► (other brand /
                                                                                                  generic scan / give up)
```

- **Identify**: one VLM call on the keyframe returning `{"device":"ac|tv|projector|fan|other|none","brand":"LG"|null}`.
  Only `ac|tv|projector|fan` continue; anything else → *"I can only use the remote for ACs, TVs, projectors and fans."*
- **BrandConfirm**: always confirm (the 4B invents things). A brand is accepted only if it exists in the catalog
  (after alias matching). Unknown → BrandAsk.
- **BrandAsk**: voice ("It's a Voltas") or the picker. Fuzzy match to the catalog; show the top 3 when unsure.
- **Pairing**: §5.
- **Paired → Controlling**: the profile is saved; the badge appears; Fixy may act.
- **Release**: badge ▸ "Stop controlling", or the user says "stop using the remote". The profile stays saved for
  next time ("Use LG AC again?").

The remote flow is **its own small state machine** (`ir/RemoteFlow.kt`) next to the repair guide, not inside it:
a user can pair the TV in the middle of a guided repair and go back to the guide.

---

## 3. Code layout

```
com.fixlens.ir/
  IrBlaster.kt        ConsumerIrManager wrapper on its own thread "fixlens-ir"; carrier check; repeats;
                      pattern checks (positive, even length, < 2 s); logs every send.
  IrCatalog.kt        Loads assets/ir/catalog.json: categories → brands (+ aliases) → models (ordered).
  IrCodes.kt          Model + Button → IntArray pattern. Fixed codes (TV/projector/fan) from patterns.bin;
                      ACs through IrextAc.
  IrextAc.kt          Kotlin side of the JNI: open(binary), capabilities (modes, temp range, fan speeds),
                      encode(AcState, key) → IntArray. One remote open at a time (IRext keeps global state).
  AcState.kt          power, mode, tempC, fan, swing; clamped to the model's capabilities.
  Buttons.kt          Closed button sets per category (AcKey, TvKey…), the only things Fixy may press.
  Pairing.kt          Pure Kotlin: candidate list, dedup by test pattern, second-button check, resume. Unit-tested.
  RemoteFlow.kt       The §2 state machine. Pure Kotlin, unit-tested.
  RemoteProfile.kt    @Serializable: category, brand, modelId, source, confirmed buttons, lastSent AcState, label.
  RemoteCommands.kt   Rules for spoken commands ("set it to 24", "volume up", "turn it off") → button, no VLM.
  probe/
    BeepProbe.kt      Goertzel energy 2-4.5 kHz over the listen window → "beep" / "silent" / "unsure".
    ScreenProbe.kt    Mean luma change inside the tracked screen box (TV/projector) from analysis frames.
    AimProbe.kt       Gravity sensor: is the top edge raised toward the device (for ACs above head height).
app/src/main/cpp/
  irext/              Vendored decoder (D2) + LICENSE
  fixlens_ir.cpp      Thin JNI: nativeOpen(byte[]), nativeCaps(), nativeEncode(int[] state, int key) → int[]
com.fixlens.ui/
  remote/RemoteBadge.kt, PairingPanel.kt, BrandSheet.kt, RemotePad.kt, AimHint.kt, IrGlyphs.kt
tools/ir/build_ir_assets.py   ir-dataset/ → app/src/main/assets/ir/
```

Existing files touched: `AndroidManifest.xml` (D1), `CMakeLists.txt` (second library `fixlensir`),
`FixLensViewModel.kt` (remote flow wiring, `UiState.remote`), `CameraScreen.kt` (badge, panel, pad),
`Intent.kt` (a `Remote` intent), `FixyPrompts.kt` (identify + tool prompts), `RepairSession.kt`
(`remote: RemoteProfile? = null`, so old session files still load), `SpeechInput.kt` (the D3 listen window),
`MainActivity.kt` (debug hooks). The KB schema gains optional `ir` steps (§8).

---

## 4. Data build (`tools/ir/build_ir_assets.py`)

Input: `ir-dataset/`. Output `app/src/main/assets/ir/` (target **< 6 MB**; the script prints the size):

| File | Content |
|---|---|
| `catalog.json` | `{category: [{brand, aliases[], models: [{id, src: "irext"/"flipper", protocol, keys: {button: patternId}}]}]}` |
| `patterns.bin` | Deduplicated µs patterns (u16 lists) for TV/projector/fan; a brand's many models often share codes |
| `ac/<file>.bin` | IRext AC binaries, only for brands in the catalog |
| `brand_aliases.json` | Hand-written: display name ↔ IRext/Flipper names (e.g. `"LG": ["LG","乐金"]`, `"Mi": ["Xiaomi","MI"]`, rebadges like Blue Star → Midea family) |

Build rules:
- Categories: IRext 1 (AC), 2 (TV), 7 (fan), 8 (projector); Flipper `ACs/ TVs/ Projectors/ Fans/`. Flipper
  `parsed` entries are encoded to µs **at build time** (NEC, NECext, Samsung32, RC5, RC6, SIRC*, Kaseikyo, RCA),
  so the phone only ever sends raw lists. Flipper AC files are skipped (IRext covers ACs with full state).
- Button names are normalized to the closed sets in `Buttons.kt` (`POWER`, `Power`, `power` → `power`;
  `VOL+`, `vol+`, `Vol_up` → `vol_up`…). Unmappable buttons are dropped.
- Model order within a brand: models whose test-button pattern is shared by the most models first (the most
  common code first), then IRext `priority`, then Flipper.
- Per-protocol `carrierHz` and `repeat` are stored (NEC 38 kHz ×1, RC5/RC6 36 kHz, SIRC 40 kHz ×3).
- The script reports coverage for an Indian-market brand list (LG, Samsung, Voltas, Daikin, Blue Star, Lloyd,
  Hitachi, Carrier, Godrej, Whirlpool, Panasonic, Haier, O General, Sony, Mi, OnePlus, TCL, Vu, Epson, BenQ,
  Havells, Crompton, Atomberg) and fails if a brand we demo is missing.

---

## 5. Pairing in detail

### Test buttons (safe first, visible, reversible)

| Device | Test 1 | Test 2 (confirms the model) | Why |
|---|---|---|---|
| AC | `power` with the **state read from the display** (or cool 24 °C auto if unreadable) | `temp_up` | AC beeps on each accepted code; sending its current state changes nothing else |
| TV | `mute` | `vol_up` | Shows an on-screen icon and is easy to undo; `power` would switch the TV off |
| Projector | `menu` | `back`/`menu` again | Power-off on projectors needs two presses and a long cool-down; never test with power |
| Fan | `speed` (or `power` if no speed key) | `power` | Visible at once |

### Loop

```
for each candidate group (models sharing the same Test-1 pattern, most common first):
    wait until aimed (AimProbe; or the user taps "Send")      ← AC only
    send Test 1 (repeat per protocol)                          haptic tick + badge arcs
    listen/watch 1.8 s:  BeepProbe (AC) | ScreenProbe (TV/projector)
        detected   → mark "responded"
        otherwise  → ask: "Did the AC respond?"  [No response]  [It responded]   (voice yes/no works too)
    responded → for each model in the group: send Test 2 → same check → first success = the model
    no        → next group
all groups failed → NoMatch
```

- **Dedup** is the big saver: 20 LG TV models may have only 4 distinct `mute` codes → at most 4 tries.
- **Resume**: the position is saved in the profile draft; leaving and coming back continues at "Model 4 of 9".
- **NoMatch** offers: *Try another brand* · *Try common codes* (top 10 AC protocol families across all brands;
  top 10 NEC/Samsung TV codes) · *This may not have an IR receiver* (wired wall panels, RF fans).
- **Pace**: the next group is never sent before the user answers or a probe confirms; no queued bursts.

### Signals used, cheapest first

| Signal | Cost | Used for | Caveat |
|---|---|---|---|
| AC beep (BeepProbe) | ~0 | AC tests | `VOICE_COMMUNICATION` noise suppression may eat the beep: verify on the phone, else raw `MIC` source for the window |
| Screen change (ScreenProbe) | ~0 | TV mute icon, menu | Needs the screen in view and tracked: ground "the TV screen" once, then the tracker holds it |
| VLM read | ~5 s | AC display digits, "HDMI 2", "No signal" | Only after pairing or when the user asks |
| User answer | user time | Everything, fallback | Big buttons, the volume keys (up = yes, down = no), or voice |

---

## 6. Control phase (the agent)

- **Fast path, no VLM**: `RemoteCommands` parses "turn it off", "set it to 24", "make it cooler", "volume up",
  "mute", "switch input", "fan faster" → button → send in < 100 ms. Checked before the VLM turn in
  `FixLensViewModel.ask()`, the same way `handleGuide` is.
- **VLM path** (diagnosis, free-form): while a profile is active the turn prompt lists the allowed buttons and
  allows one action line before the reply:
  `{"remote":"temp_set","value":24}` or `{"remote":"input"}` or `{"remote":null}`. `GroundingParser`-style
  parsing; anything not in the closed list is ignored and logged. Max 3 actions per turn; multi-step plans are
  said aloud first ("I'll switch inputs until I see a picture").
- **After each action**: probe (beep / screen), then, if the question needs it, a rolled-back side request
  (`ConversationContext.side`) to read the result ("What does the AC display show? Reply with the number only").
- **AC state is one-way**: the pad and captions say **"Last sent: Cool · 24°"**, never "Current", until the camera
  confirms.
- **Never**: blind menu navigation, factory resets, channel scans, anything not in `Buttons.kt`.

---

## 7. UI

### Principles (so it looks like a product, not a demo)

- Use the existing palette (Ink `#0F1C2E`, Amber `#FF9F1C`, Paper `#F6F1E7`, Online green) and the existing card
  language (24 dp radius, `Ink @ 0.88`, 1 dp hairlines). No new colours except the existing Danger red for errors.
- No emoji, no sparkles, no gradients on text, no glow on everything. Motion only when it means something (a
  send, a state change). Durations 150–250 ms, `FastOutSlowIn`; one 400 ms moment for "paired".
- Glyphs drawn with `Canvas` like the current mic/keyboard glyphs: 1.5 dp strokes, round caps, 24 dp grid. No
  icon library, no brand logos (trademarks + asset size); brands are plain text.
- Plain, specific words: "Model 3 of 9", "Send test", "It responded", "No response", "Last sent". Not "AI is
  thinking…".
- One primary action per screen, in Amber; secondary actions outlined.
- Haptics: `CONFIRM` on a successful pair, `CLOCK_TICK` on each IR send.
- Everything reachable one-handed (the other hand is aiming); tap targets ≥ 48 dp; TalkBack labels on all controls.

### RemoteBadge (top-right)

Sits in `TopBar` left of the "On-device" chip (the chip collapses to its green dot while the badge shows, so the
title keeps room).

```
  ┌───────────────────┐
  │ ▯)))  LG AC       │   32 dp pill, Ink @ 0.55, hairline border
  └───────────────────┘
```
- Glyph: a small remote outline with three arcs off its top edge.
- **Pairing**: amber outline; the arcs breathe slowly (1.6 s).
- **Controlling, idle**: solid amber glyph, static arcs, label "LG AC".
- **Sending**: the arcs ripple outward once per send (3 arcs staggered 80 ms, 450 ms total), plus a haptic tick,
  so the user feels and sees every command.
- **Paused / lost** (no response to the last 2 commands): arcs grey, label "Check aim".
- Tap → small menu: *Remote pad* · *Pause* · *Stop controlling* · *Forget this device*.
- `contentDescription`: "Fixy is controlling the LG air conditioner remote".

### PairingPanel (middle of the screen)

Centered card, 88 % width, over a 40 % scrim so the camera stays visible (needed for ScreenProbe and aiming).

```
 ┌─────────────────────────────────────────┐
 │  PAIR WITH LG AIR CONDITIONER        ✕  │   label style (existing LabelStyle)
 │  Model 3 of 9   ● ● ◉ ○ ○ ○ ○ ○ ○        │
 │                                         │
 │            ┌───────────────┐            │
 │            │      ⏻        │            │   96 dp round test button (Amber)
 │            └───────────────┘            │
 │            Send test: Power             │
 │                                         │
 │   ▯↑  Point the top of your phone       │   AimHint: phone outline, top edge highlighted
 │       at the AC                         │
 │ ─────────────────────────────────────── │
 │   Did the AC respond?                   │   after a send
 │   [ No response ]     [ It responded ]  │
 │                           ✓ Beep heard  │   auto-detected chip (Online green)
 └─────────────────────────────────────────┘
```
- Steps slide horizontally (220 ms). The dots show progress; failed ones turn to a hollow grey ring.
- The second check reuses the layout: "One more check: Temperature up".
- **Paired**: the power button turns into a checkmark drawn in 400 ms; "Paired · Model 3"; after 700 ms the panel
  shrinks and moves into the badge's position (the badge appears where it lands), so the user learns where the
  remote now lives.
- ✕ or back → "Stop pairing?" (keeps the position for resume).

### BrandSheet (when the brand isn't read or is wrong)

Bottom sheet: search field on top, then "Suggested" (VLM guess + recent), then an A–Z list of catalog brands for
the category, with model counts in muted text ("LG · 14 models"). Voice works while it's open.

### RemotePad (manual, from the badge menu)

Bottom sheet laid out per device:
- **AC**: large "24°" readout labelled "Last sent", − / + buttons, Mode segmented (Cool · Dry · Fan · Auto · Heat,
  limited to the model's supported modes), Fan speed, Power.
- **TV**: Power, Vol ±, Mute, Ch ±, Input, D-pad + OK, Back, Home.
- **Projector**: Power (with "press twice" note), Input, Menu, D-pad, Back.
- **Fan**: Power, Speed, Swing, Timer.

### In the conversation card

Each send adds a one-line muted note under Fixy's answer: `Sent · Temperature 24° · Cool`, so the log of what Fixy
did stays visible and trustworthy.

---

## 8. Knowledge base

Optional `ir` on steps, verbatim like `say`:

```json
{"n": 2, "say": "I'll set it to cool at 24 degrees.", "ir": {"button": "set", "state": {"mode": "cool", "temp": 24}},
 "verify": "the AC display shows 24", "target": "temperature display of the AC"}
```
Entries to write: AC not cooling (mode/temperature, then filter), TV no picture (input cycle, check cable),
TV no sound (mute, volume), projector "no signal" (input). `KbRepository` validation: `ir.button` must be in
`Buttons.kt`; an `ir` step needs a paired profile, otherwise it is shown as a manual step ("Press Mode on your
remote until it shows Cool").

---

## 9. Threads (CLAUDE.md §12.4)

| Work | Where |
|---|---|
| IR send (`transmit` blocks for the pattern's length) | `fixlens-ir` single thread |
| IRext encode (JNI, global state) | same `fixlens-ir` thread, so it's never used concurrently |
| BeepProbe | the existing `SpeechInput` capture loop forwards the samples during the window |
| ScreenProbe | the CameraX analysis executor, beside the tracker (< 1 ms: a mean over the box) |
| AimProbe | sensor callback → `StateFlow` |
| Catalog load | `Dispatchers.IO` at start, beside the KB |
| UI | main thread, observing `UiState.remote` |

---

## 10. Persistence and logging

- `RepairSession.remote: RemoteProfile?`: the device paired in this session.
- `filesDir/remotes.json`: paired devices across sessions, so the next session offers "Use LG AC again?" without
  pairing. Recall's greeting can mention it.
- Log (`FixLens` tag) per send: device, brand, model id, button, carrier, pattern length, send ms, probe result;
  per pairing: tries, groups skipped by dedup, total time, the signal that confirmed it.

---

## 11. Debug hooks (adb, debug builds)

```
--ez irinfo true                       IR hardware present? carrier ranges (logged)
--es ir "tv/LG/mute"                   send the brand's first model's button
--es ir "tv/LG/#463/mute"              a specific model
--es ir "ac/LG/#2807/cool 24 auto"     an AC state through IRext
--es irpair "ac:LG"                    open the pairing panel directly (skips the VLM)
--es irbadge "sending"                 force a badge state, to check the animation
```

---

## 12. Tests

- **Unit (JVM)**: `Pairing` (dedup, ordering, second-button, resume, NoMatch), `RemoteFlow` transitions,
  `RemoteCommands` phrases ("set it to twenty four", "a bit cooler", "turn the TV off"), `AcState` clamping,
  brand fuzzy match, pattern checks (even length, < 2 s, positive).
- **Build script**: Flipper encoders against known vectors (NEC `address 04 command 08` → a known µs list);
  every catalog pattern < 2 s.
- **On the phone**: `--ez irinfo`; TV mute on a known TV; AC decode of 3 brands matching the scratch outputs;
  BeepProbe with a recorded AC beep played from a laptop; a full pair in airplane mode.

---

## 13. Build order and time

| Step | What | Est. | Done when |
|---|---|---|---|
| R0 | `irinfo` hook + permission (D1) | 0.5 h | The phone logs IR present and its carrier range |
| R1 | Build script + `IrBlaster` + `IrCatalog` + `--es ir` | 2 h | A TV at the venue mutes from adb |
| R2 | IRext JNI + `IrextAc` | 1.5 h | `--es ir "ac/…/cool 24"` works on a real AC |
| R3 | `Pairing` + `RemoteFlow` + tests + PairingPanel + BrandSheet | 3 h | Pairing a TV by tapping answers |
| R4 | RemoteBadge + profile + `RemoteCommands` fast path | 1.5 h | "Volume up" works and the badge ripples |
| R5 | VLM identify + tool line + probes (beep, screen) | 2 h | The §1 demo runs end to end |
| R6 | KB `ir` entries + RemotePad + polish | 1.5 h | Demo script recorded |

About **12 h**. That is most of the time left before the freeze, and M3/M4 aren't closed yet. Suggested cut line if
time runs short: R0–R4 + BrandSheet without VLM identification (the user says the brand) + the TV as the demo
device (fixed codes, no JNI). R2 and R5 are the first things to drop.

---

## 14. Edge cases (and where they're handled)

| Edge case | Handling |
|---|---|
| VLM invents a brand | Always confirm; catalog-only brands (§2) |
| Logo unreadable | Ask for the remote / its back; BrandSheet |
| Rebadged brand (Blue Star = Midea inside) | `brand_aliases.json` + "Try common codes" |
| Device has no IR (wired central AC, RF fan) | NoMatch explains it after all groups fail |
| TV power is a toggle | Test with mute (§5) |
| Projector power needs two presses | Never test with power; RemotePad notes it |
| Sending an AC code overwrites the user's settings | Read the display first and resend that state |
| Shared power codes → false match | Second-button check |
| One hand aiming, the other can't hold the mic | Tap answers, volume keys, beep auto-confirm |
| Other devices in the room react to test codes | Fixy warns before scanning ("This may also switch nearby TVs") |
| Two identical ACs in one room | One code reaches both; tell the user to aim at one |
| Carrier not supported by the phone | `IrBlaster` clamps to the nearest supported range and logs it |
| Protocol needs repeats (Sony ×3) | Per-protocol `repeat` from the build |
| Pattern > 2 s or odd length | Build-time and runtime checks |
| Noise suppression hides the beep | Use the raw MIC source for the window; fall back to asking |
| User leaves mid-pairing | Resume from the saved position |
| No IR hardware | Remote features hidden; Fixy says it can't on this phone |
| App restarted | Profile from `remotes.json`; badge only after the user agrees to reconnect |
| VLM asks for a button that doesn't exist | Closed list: ignored and logged |
| Airplane mode | Nothing needs the network; tested in R5 |

## 15. Out of scope

Learning codes from the original remote (the phone can't receive IR), RF/Bluetooth/Wi-Fi devices, fridges,
washing machines and cars (no IR receiver; the camera guide covers them), macros and schedules.
