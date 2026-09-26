# Sources: car (`appliance: "car"`)

Checked 2026-09-27. Entries: [../entries/car.json](../entries/car.json). Rules: [../AUTHORING.md](../AUTHORING.md).
The old `car_engine_bay` appliance is replaced by `car`; the four old ids are kept.

## Documents

| Short name | Document | URL | Notes |
|---|---|---|---|
| **Swift** | Maruti Suzuki Swift (2024+) owner's manual 99011M75T08-74W | https://marutisuzuki.scene7.com/is/content/maruti/Swift_99011M75T08-74Wpdf | Primary. Page refs are the printed ones (ch.4 = PDF p.+134, ch.9 = +310, ch.10 = +361). Figures are greyscale |
| **City** | Honda City 5th gen (2022) owner's manual | https://www.hondacarindia.com/ownersmanual/webom/eng/city%20-%205th%20generation/city%205th%20generation%20-%202022.pdf | Printed page = PDF page − 2. Only manual that names part colours in text (p.263) |
| **Creta** | Hyundai Creta (2019) owner's manual | https://www.hyundai.com/content/dam/hyundai/in/en/data/connect-to-service/owners-manual/creta.pdf | Printed refs: ch.5 = PDF −89, ch.8 = PDF −292, ch.9 = PDF −310 (the text says "chapter 7" but the printed pages are ch.9) |
| **Nexon** | Tata Nexon BS-VI owner's manual, Tata Motors doc 543858409905 Rev 00 (10.01.20) | Official: https://cars.tatamotors.com/images/service/owners/owners-manual/pdf/nexon/Nexon-BS-VI_543858409905_Rev_00_10.01.20.pdf (served an HTML page from here). Same file from a Tata dealer: https://www.riyatata.com/assets/images/Nexon-BS-VI_543858409905_Rev_00_10.01.20.pdf | Printed page = PDF page − 9. The older `tmlcars.tatamotors.com` host didn't resolve |

Local text copies (pdftotext) live in the session scratchpad `kb/car/` (not in the repo).
Aggregator guides such as https://www.hamiltontires.com/engine-oil-dipstick-check-guide are used **only** for target
wording (dipstick handle colour), never for a step.

**Consensus rule used:** every `say`, safety line and escalation is backed by ≥ 2 of the four manuals (different
brands). Where they differ, the wording is true for all of them, or the step is dropped or becomes "check your manual"
or a workshop referral. A `caution` shown on the step card may come from a single manual when it only adds care.

**Licence:** all four manuals are "all rights reserved". We keep facts and order, every line is in our own words, and
nothing from the PDFs (text or figures) ships in the APK. Non-commercial hackathon demo; not legal advice.

**Target phrases** hold for most cars but come partly from figures and common layout, not manual text. Check them on
the demo car before the demo: dipstick colour, washer-cap symbol, brake reservoir position, red plus-terminal cover.

---

## car_check_engine_oil (diy)

| Line | Swift p.9-8 | City pp.263, 268-270 | Creta p.9-29/30 | Nexon p.176-177 |
|---|---|---|---|---|
| Safety: level ground, engine off | ✓ | ✓ | ✓ | ✓ |
| Safety: wait ~5 min | ≥ 5 min after stopping (or before starting) | ~3 min | warm up, off, ~5 min | warm up, off, 5 min |
| Hot parts | — | p.253 let engine cool | radiator hose can burn | p.173 don't touch hot engine |
| 1 Bonnet + support rod | p.7-4 | p.265 | p.5-27 | p.33 |
| 2-4 Pull, wipe, reinsert fully, pull again | ✓ | ✓ | ✓ | ✓ |
| 5 Between the two marks | upper/lower | upper/lower | F/L | MIN/MAX |
| 6 Near/below lower mark → open filler cap | ✓ | ✓ | ✓ (near or at L) | ✓ |
| 7 Add slowly, recommended oil; don't overfill | ✓ | ✓ | ✓ | ✓ |
| 8 Cap back, wait, recheck | idle 1 min, stop, wait 5 min | wait 3 min | — | — |

- **Disagreement, timing:** Maruti checks cold or 5 min after stopping; Honda 3 min after switching off; Hyundai and
  Tata warm the engine first, then wait 5 min. Spoken as "switch off and wait about five minutes", true for all four.
- **Disagreement, dipstick colour:** City says orange (p.263, 268); the other manuals' figures are greyscale. "Usually
  yellow or orange" is spoken as a finding aid, following the lead's brief and aggregator guides. Verify on the demo car.
- **Dropped:** "wait ten minutes" (old draft, no source); "milky oil" escalation (no source); Swift's "idle one minute
  before rechecking" (Swift only). "Oil warning light" symptoms moved to `car_oil_pressure_light`.
- `escalate_if`: oil leaking (City p.252: oil residue on the ground → dealer), burning smell (City p.252 abnormal
  smell), oil light stays on (Swift p.4-41, Creta p.5-59).

## car_check_coolant (caution)

| Line | Swift p.9-9/10 | City pp.271-273 | Creta pp.9-32 to 9-34 | Nexon p.178 |
|---|---|---|---|---|
| Safety: engine cold | ✓ | ✓ | ✓ | ✓ let it cool |
| Safety: never open a hot cap | ✓ p.10-17 | ✓ | ✓ | ✓ |
| 2-3 See-through tank, between marks | FULL/LOW | MIN/MAX | F/L | MIN/MAX |
| 4 Open the tank cap slowly, not the radiator | "check at the reservoir, not the radiator" | — | open slowly (radiator) | top up in the auxiliary tank only; open slowly |
| 5 Top up to the upper mark with the specified coolant | ✓ | ✓ | distilled water (!) | ✓ |
| Caution: don't mix types / don't overfill | ✓ / ✓ | — / — | — / ✓ | ✓ / — |
| 6 Close the cap firmly | line up the marks | — | — | — |
| 7 Topping up often → check for leaks | — | inspect for leaks | frequent additions → dealer | — |

- **Disagreement, what to add:** Creta says distilled water; Swift says don't dilute its blue premix; City says no straight
  water; Nexon allows water only in an emergency. Spoken as "the coolant type your manual says".
- **Disagreement, radiator cap:** City and Creta describe opening the radiator cap when cold. Swift and Nexon point to the
  reservoir only. We never open the radiator cap.
- **Dropped:** "line up the marks on the cap" (Swift only; now just "close it firmly"). "Temperature warning light" and
  "engine running hot" moved to `car_overheating`.

## car_top_up_washer_fluid (diy)

| Line | Swift p.9-40 | City p.263, 276 | Creta p.9-37 | Nexon p.179 |
|---|---|---|---|---|
| Safety: engine off and cool, fluid is flammable | ✓ (not when hot or running; alcohol) | — | ✓ flammable | p.173 ignition off |
| 2-3 Find and open the cap | ✓ | ✓ blue cap | ✓ | ✓ |
| 4 Washer fluid, diluted as the bottle says | diluted with water | commercial washer fluid only | washer fluid (water if none) | diluted with water |
| Caution: never radiator coolant/antifreeze | ✓ | ✓ | ✓ | — (no detergent) |
| 5 Don't overflow | — | ✓ | — | — |
| 6 Empty tank damages the pump → still no spray: workshop | ✓ | — | — | ✓ |

- **Disagreement, plain water:** Creta allows it when no washer fluid is at hand; City says commercial washer fluid only.
  Dropped "or clean water" from the old draft.
- **Target:** "often blue" is from City p.263. The windscreen-and-spray symbol on the cap is not in any manual's text
  (standard dashboard/cap symbol); verify on the demo car.
- **Dropped:** "pump makes noise" escalation (no source).

## car_electrical_wiring (call_technician)

FixLens policy: no wiring guidance (CLAUDE.md §3), the deliberate refusal demo. Supported by Swift p.10-16 (starter
fails for no obvious reason → "major electrical problem", workshop) and Creta p.9-6 (disconnect the battery before
touching ignition cables and wiring; shock risk) and p.9-47 (high-voltage ignition system). Fuse checks (Swift p.9-22,
City p.361) are left out on purpose: electrical, and FixLens keeps out of it. The old meaning mentioned "battery cables";
removed, because checking terminals and jump starting now have their own entries.

## car_battery_terminals (caution)

| Line | Swift p.9-20 | City pp.253, 303-304 | Creta pp.9-45 to 9-48 | Nexon pp.179-181 |
|---|---|---|---|---|
| Safety: flames/sparks away, explosive hydrogen | ✓ | ✓ | ✓ | ✓ |
| Safety: acid burns, eye protection, don't lean over | ✓ gloves + eyes | ✓ face shield | ✓ eyes; p.8-7 don't lean over | ✓ face shield; p.161 don't lean over |
| 3 Look for corrosion | ✓ | ✓ monthly | ✓ keep clean | ✓ "white or yellowish powder" |
| Caution: no metal across the posts | ✓ | — | — | ✓ never place tools on a battery |
| 4 Clamps tight | poor terminal contact (p.10-16) | — | ✓ clean and tight (+ p.8-4) | ✓ |
| 5 Corrosion or loose clamp → mechanic | cleaning: disconnect negative first | "or have a skilled technician do any battery maintenance" | disconnect negative before touching | "or have a skilled technician" |

- **Disagreement, cleaning:** all four describe cleaning (baking soda and water, brush or towel, petroleum jelly or
  grease), but Swift, Creta and Nexon require disconnecting the battery cables first, and City and Nexon offer the
  technician instead. Disconnecting cables is outside FixLens' no-cables policy, so the entry inspects only and sends
  cleaning and tightening to a mechanic.
- **Dropped:** topping up electrolyte with distilled water (Swift, Creta, Nexon; acid hazard, most batteries are
  maintenance-free); City's test-indicator window (City only).

## car_check_brake_fluid (caution)

| Line | Swift p.9-13/14 | City pp.253-254, 263, 275 | Creta p.9-35/36 | Nexon p.177 |
|---|---|---|---|---|
| Safety: level ground, parking brake, engine off | — | ✓ p.253 | — | p.173 ignition off |
| Safety: fluid harms skin, eyes, paint | ✓ | — | ✓ | ✓ |
| 2 Reservoir in the engine bay | ✓ | ✓ black cap | ✓ | ✓ |
| 3-4 Level through the side, between MAX and MIN | ✓ | ✓ | ✓ | ✓ |
| 5 At/near MIN → don't top up, brakes checked | near MIN → workshop | at/below MIN → dealer | add to MAX; excessively low → dealer | add fluid |
| Caution: rapid drop = leak | ✓ | — | frequent additions → dealer | — |

- **Disagreement, top-up:** Creta and Nexon let the owner add fluid; Swift and City send a low level to the workshop.
  Check-only, as the brief asks: a low level means pad wear or a leak.
- **Target:** "near the back of the engine bay on the driver's side" is read from the City p.263 and Creta p.9-3 figures
  (right-hand drive). Verify on the demo car.

## car_check_air_filter (diy)

| Line | Swift p.9-11 | Creta p.9-39 | City |
|---|---|---|---|
| 3 Undo the clips/clamps | ✓ side clamps | ✓ cover clips | no owner procedure (dealer schedule only) |
| 4 Lift the lid, take out the filter | ✓ | ✓ | |
| 5 Dirty → new filter | clean or replace | replace (caution: don't clean a used filter; elsewhere says compressed air is OK) | |
| 6 Refit, close all clips | ✓ securely | ✓ lock clips, gasket seated | |
| Caution: don't drive without it / no dirt in the box | — | ✓ | |

- Owner inspection is allowed by two brands (Swift, Creta), so the entry is kept as `diy`; City lists it for the
  dealer only.
- **Disagreement, cleaning:** Swift says clean or replace; Creta contradicts itself. Spoken as "needs a new filter".
- Safety line (engine off and cool): City p.253, Creta p.9-6, Nexon p.173.

## car_wont_start (diy)

| Line | Swift p.10-15/16 | City pp.206, 329-330 | Creta p.8-4 | Nexon pp.119, 122 |
|---|---|---|---|---|
| 1 Gear in P/N, brake or clutch pressed | — | ✓ | ✓ N or P | ✓ clutch fully / brake + N |
| 2 Lights on, try to start | headlights | interior lights | interior light | — |
| Caution: crank ≤ ~10 s | 12 s | 15 s | — | 10 s |
| 3 Lights dim/out → flat battery or poor terminals | ✓ | ✓ | ✓ | — |
| 4 Terminals clean and tight | ✓ | — | ✓ | — |
| 5 Jump start or charge | recharge | jump start | see jump starting | — |
| 6 Lights bright → fuel | enough fuel | ✓ | ✓ | — |
| 7 No push-start; still dead → mechanic | ✓ / ✓ | — / towing | ✓ / ✓ dealer | — |

- **Dropped:** fuse checks (Swift, City; electrical, see the wiring entry); immobilizer light (City, Swift, Nexon;
  symbols and behaviour differ); Swift's cold-weather accelerator trick (Swift only); Creta's diesel air-bleed.
- `escalate_if` "fuel smell" (City p.248 fuel hazard), "battery keeps dying" (Swift p.10-14, Creta p.8-7).
- Safety: parking brake (City p.206, Swift p.10-14), hazard lights (Creta p.8-3).

## car_jump_start (caution)

Jump starting is in all four manuals, so it has its own entry (split out of "won't start" so a flow never jump-starts a
car that is out of fuel).

| Line | Swift p.10-14/15 | City pp.333-336 | Creta pp.8-5 to 8-7 | Nexon pp.160-161 |
|---|---|---|---|---|
| Safety: sparks/flames away, explosive gas | ✓ | ✓ | ✓ | ✓ |
| Safety: not cracked, leaking or frozen | frozen | frozen | frozen, cracks, leaks | — |
| 1 Cars not touching; parking brakes; engines off | ✓ / ✓ / — | — / — / ✓ | ✓ / ✓ / ✓ | ✓ / ✓ / don't connect with engine running |
| 2 Accessories off | ✓ (keep hazards) | ✓ | ✓ | ✓ (keep hazards) |
| 3-5 + flat → + good → − good | ✓ | ✓ | ✓ | ✓ |
| 6 Last clip to bare metal, never − of the flat battery | engine mount bolt / ✓ | engine mounting bolt / "no other part" | chassis ground / ✓ | unpainted heavy metal, engine mounting / ✓ |
| 7 Run the booster car above idle | moderate speed | rpm slightly up | ~2000 rpm, few minutes | moderate speed |
| Caution: hands and leads clear of fans and belts | ✓ | — | ✓ | — |
| 9 Remove in exact reverse order | ✓ | ✓ | ✓ | ✓ |
| 10 Won't start / keeps going flat → mechanic | ✓ repeatedly flat | inspect | ✓ few attempts | ✓ in doubt |

- **Disagreement, jump points:** Creta has a separate red (+) jumper terminal; City has a terminal cover; Nexon's
  battery sensor must not be clamped. Step 3's caution: "Some cars have a separate jump point. Check your manual."
- **Disagreement, ground point:** engine mounting bolt (Swift, City, Nexon) vs chassis ground (Creta). Spoken as "bare,
  unpainted metal on your engine, such as an engine mounting bolt".
- Only 12 V (all four) is left out of the steps: any car's battery is 12 V; a booster pack user should read its label.

## car_oil_pressure_light (caution)

| Line | Swift p.4-41 | City pp.340-341 | Creta p.5-59 | Nexon p.63 |
|---|---|---|---|---|
| Safety: pull over, engine off at once | ✓ | ✓ level ground | ✓ | contact service centre |
| 1 Wait a few minutes | (p.9-8: 5 min) | ~3 min | (p.9-29: 5 min) | — |
| 3-4 Check level, add if low | ✓ | ✓ | ✓ | — |
| 5 Level fine → don't drive, workshop | ✓ | — | — | ✓ |
| 6 Added oil → restart; light stays on → off, workshop | — | ✓ (within 10 s) | ✓ | — |

- **Disagreement:** Swift says if the level is fine, don't drive until inspected; City restarts and drives on if the
  light goes out. The entry follows Swift for "level fine" and City/Creta for "you added oil".
- Target colour "red": Nexon table p.63. Dropped City's "10 seconds" (City only).

## car_overheating (caution)

| Line | Swift p.4-39, 10-17 | City pp.338-339 | Creta p.5-44, 8-8/9 | Nexon p.64 |
|---|---|---|---|---|
| Safety: pull over, AC off | ✓ | ✓ accessories off | ✓ | — |
| Safety: no bonnet while steaming; never radiator cap hot | ✓ / ✓ | ✓ / ✓ | ✓ / ✓ | — / ✓ |
| 1 Steam → engine off, wait | ✓ | ✓ | ✓ | — |
| 2 No steam → idle to cool | ✓ | ✓ keep running | ✓ leave running | — |
| 3 Fan spinning? no → engine off | — | ✓ | ✓ | — |
| Caution: fan starts by itself | ✓ | — | ✓ p.9-33 | — |
| 4 Back to normal → off, cool | ✓ | ✓ | ✓ | — |
| 5-6 Look for leaks; leaking → don't drive, workshop | ✓ | ✓ | ✓ | — |
| Caution: AC water under the car is normal | — | — | ✓ | — |
| 7 Cool → tank low → add slowly, ≤ upper mark | to FULL | to MAX | to halfway, slowly | — |
| 8 Restart; overheats again → workshop | not sure → workshop | ✓ | ✓ | — |

- **Disagreement, top-up level:** FULL (Swift), MAX (City), halfway (Creta). Spoken as "never above MAX", true for all.
- **Disagreement, radiator:** Swift adds to the radiator if needed, City and Creta open the radiator cap under a cloth
  once cold. Dropped: the entry only uses the plastic tank.
- **Disagreement, restart:** Nexon says don't restart until the fault is attended to; Swift, City and Creta idle and
  restart. The entry follows three of four and escalates on "overheats again".

## car_battery_charge_light (caution)

| Line | Swift p.4-41 | City p.342 | Creta p.5-58 | Nexon p.63 |
|---|---|---|---|---|
| 1 Normal at start-up | ✓ | — | ✓ | ✓ |
| 2 Switch off unneeded electrics | — | ✓ climate, demister, others | — | ✓ |
| 3 Charging system checked right away | ✓ immediately | ✓ immediately | ✓ as soon as possible | ✓ |

- **Disagreement, engine off?** City: if you must stop, don't switch the engine off (it may not restart). Creta: stop,
  switch off and check the alternator belt. Both dropped, as is Nexon's "run at 3000 rpm for 15 min" (Nexon only).
- `escalate_if` "lights going dim": Swift p.10-15 (flat-battery signs).

## car_check_engine_light (caution)

| Line | Swift p.4-39/40 | City p.343 | Creta p.5-57/58 | Nexon p.62 |
|---|---|---|---|---|
| 1 Normal at start-up | ✓ | — | ✓ | ✓ |
| Safety / 2 Blinking → stop safely, not on dry grass | ✓ withered grass | ✓ no flammable items | — | — |
| 3 Engine off, let it cool | stop immediately | ≥ 10 min, engine off | — | — |
| 4 Must move → slowly to a workshop | ✓ drive slowly | ✓ ≤ 50 km/h to dealer | — | — |
| 5 Steady → avoid high speed, get checked | workshop | avoid high speeds, inspect | dealer | service centre |

- **Disagreement, diesel:** Creta diesel says a blinking MIL clears by driving above 60 km/h (DPF); Nexon has a separate
  DPF lamp. The entry follows the petrol advice (Swift, City): stopping is the safe choice either way.
- Target colour "amber": Nexon p.62.

## car_brake_warning_light (caution)

| Line | Swift p.4-36/37, 9-13 | City p.343 | Creta p.5-55/56 | Nexon p.64 |
|---|---|---|---|---|
| Safety: slow down, stop safely; harder braking | ✓ | ✓ | ✓ | — |
| 1 Parking brake fully released | ✓ | — | ✓ | ✓ |
| 2 Still on → fluid may be low | ✓ | ✓ | ✓ | ✓ |
| 3 Engine off, check the reservoir | (workshop) | ✓ next stop | ✓ | — |
| 4 At/below MIN → not safe, workshop or tow | ✓ tow | ✓ dangerous to drive | ✓ tow | — |
| 5 Level fine, light on → brakes checked now | ✓ | ✓ malfunction | ✓ | ✓ ABS/EBD fault |
| Caution: brake + ABS lights together | ✓ | ✓ | — | ✓ |

- **Dropped:** topping up (Creta only allows it); Swift's "test the brakes on the shoulder" and City's "press the pedal
  lightly" (driving manoeuvres; the user is talking to a phone, so is likely stopped).
- Target: "circle with an exclamation mark or the word BRAKE" is the usual symbol, not manual text; red from City p.343
  and Nexon p.64.

---

## Matching notes

- Phrases avoid pairs of generic words ("car" or "engine" + "not", "check" + "air", "noise from the engine"), which made
  "my car stereo has no sound" or "check the air in my tyres" match. The title "Car engine won't start" has four words
  for the same reason.
- "check the brake oil" ties `car_check_engine_oil` and `car_check_brake_fluid` on purpose (returns NONE) rather than
  sending it to engine oil; "brake oil level" and "is my brake oil low" reach brake fluid.
- "can you help me fix the wiring" names no car word, so the app searches every appliance. It matches here, but may tie
  with other categories' wiring entries once they exist.
