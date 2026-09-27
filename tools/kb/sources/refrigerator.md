# Sources: refrigerator (`appliance: "refrigerator"`)

Checked 2026-09-27. Entries: [../entries/refrigerator.json](../entries/refrigerator.json). Rules: [../AUTHORING.md](../AUTHORING.md).
Covers both Indian fridge types: direct-cool (single door, manual defrost button) and frost-free (double door and
up, automatic defrost). Steps that apply to only one type say so ("If your fridge has a defrost button…").

## Documents

| Short name | Document | URL | Notes |
|---|---|---|---|
| **LG-cool** | LG India help library: "[LG refrigerator] LG Refrigerator is not cooling properly" (2026-07-04) | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT52006627-20153121335264/ | JS-rendered; text read from the page's JSON-LD `articleBody` with curl. Temp 1-2 °C lower (3 °C default), fill ~60 %, door/gasket, 10 cm from walls, 5-43 °C room, 1-2 h (summer 2-3 days) after install |
| **LG-cool2** | LG India: "[LG Refrigerator] The refrigerator is not cool enough" (2026-08-17) | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT20150037-20153109511988/ | Light/display off = power; vents blocked; away from gas stove; vacuum the mechanical-compartment cover with power off; **don't remove the back cover (gas pipes), technician only** |
| **LG-floor** | LG India: "[LG refrigerator leakage] Water is flowing from the bottom" | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT20150037-20153132890928/ | Airtight containers, door shut, cool hot food; check sink / water-purifier hose near the fridge |
| **LG-drain** | LG India: "How to Troubleshoot Water Leaking from the Ceiling or Pooling on the Floor in Your LG Top Freezer Refrigerator" (2026-06-25) | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT52006627-20155272210092/ | Freezer drain blocked by ice/debris; unplug + doors open 24 h; no hair dryer or hot water; still leaking → LG support |
| **LG-dc** | LG India: "In LG Single Door Direct Cool Refrigerator how to do manual Defrost" + "[Video] How to do Defrosting in LG Single Door Direct Cool Refrigerator" | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT20150037-20151713700041/ · https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT52006627-20155053278382/ | Push the centre button; releases by itself and restarts; chiller tray collects the water; no sharp items; every alternate day / every 3 days |
| **LG-hot** | LG India: "The front or the side of the refrigerator feels hot" | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT20150037-20153132871547/ | Hot sides/front are normal (anti-condensation heater, heat pipe); keep ≥ 5 cm from walls |
| **LG-frost** | LG US: "[LG refrigerator frost build-up] There is frost build-up and icicles in the freezer compartment" | https://www.lg.com/us/support/help-library/lg-refrigerator-frost-buildup-there-is-frost-buildup-and-icicles-in-the-freezer-compartment--20154713283186 | Door not closing, sticky gasket, hot food, unsealed moist food; never sharp objects |
| **LG-noise** | LG Levant: "[LG refrigerator - Noise] A loud noisy sound is heard" + LG US "noise during operation", "Noise (hissing, gurgling…)", "Rattling noise" | https://www.lg.com/levant_en/support/product-help/CT20158016-20153121314430 · https://www.lg.com/us/support/help-library/lg-refrigerator-noise-a-noise-is-heard-during-operation--20154629491561 · https://www.lg.com/us/support/help-library/noise-refrigerator--20154712685690 · https://www.lg.com/us/support/help-library/rattling-noise--20154712675586 | Level/sway, wait ≥ 10 min before re-plugging, objects on top, contact with other units, fan blocked by objects/frost, louder or continuous → service |
| **Samsung-cool** | Samsung UK "My Samsung fridge isn't cooling" | https://www.samsung.com/uk/support/home-appliances/my-samsung-fridge-isnt-cooling/ | Plugged in / test socket with another device, door and seals (damp cloth, mild detergent), 3 °C / −18 °C, space behind, sun/heat, overloading blocks vents, dust at the compressor fan |
| **Samsung-leak** | Samsung India "How to fix if there is water leakage in Refrigerator" | https://www.samsung.com/in/support/home-appliances/water-leakage-in-refrigerator/ | Unplug + breaker; drain line and chiller tray; hot food; food covered; door left open; 300 mm sides / 150 mm back / 300 mm top |
| **Samsung-ice** | Samsung India "How to troubleshoot excess ice formation in a Samsung direct cool refrigerator" (+ "How to defrost a Samsung single door refrigerator", video only) | https://www.samsung.com/in/support/home-appliances/how-to-troubleshoot-excess-ice-formation-in-a-samsung-direct-cool-refrigerator/ | Defrost button in the freezer compartment, ~2 h, twice a week; knob by season; hot food; fewer openings; gaskets; button stuck → service |
| **Samsung-noise** | Samsung India "Why there is an 'Abnormal noise' from the refrigerator?" | https://www.samsung.com/in/support/home-appliances/why-there-is-an-abnormal-noise-from-the-refrigerator/ | Normal sounds table; items knocking; ≥ 2 in from back wall; frost blocking the fan; persists → support |
| **Samsung-hot** | Samsung India "Why side walls of the Samsung Refrigerator are very hot?" | https://www.samsung.com/in/support/home-appliances/why-side-walls-of-the-samsung-refrigerator-are-very-hot/ | Search-result text only; agrees with LG-hot (hot pipes in the side walls, normal) |
| **Dacor** | Samsung Dacor DRF425300AP built-in refrigerator user manual (hosted on image-us.samsung.com) | https://image-us.samsung.com/SamsungUS/dacor/products/refrigeration/french-door-refrigeration/drf425300ap/download/Dacor-42-Built-InFrenchDoor-UserManual-DRF425300AP_DA.pdf | Safety section: burning smell or smoke → unplug immediately and contact service |
| **Godrej-DC** | Godrej direct cool refrigerator user manual (Edge / Marvel / Neo…, v4 2024) | https://static.godrejenterprises.com/DC_User_Manual_031781d33a.pdf | Needs browser headers to download. Text is outlined, so pages were rendered to images and read. Printed pages: parts p.1, installation p.3, operation + defrosting p.5, cleaning p.6, storage p.7, before calling service p.8, safety p.9, FAQ p.11, warranty p.12 |
| **Haier-codes** | Haier India "The error code for the refrigerator and freezer" | https://www.haier.com/in/service-support/self-service/20230118_205347.shtml | "None of the error codes can be resolved by the user"; power-cycling only hides them |
| **Haier-cool** | Haier India "Fridge compartment does not work" | https://www.haier.com/in/service-support/self-service/20161107_102212.shtml | Thermostat not at zero, cold-air outlet not blocked by food |
| **ENERGY STAR** | ENERGY STAR "Refrigerators" (tips) | https://www.energystar.gov/products/refrigerators | Public domain. 35-38 °F, airtight seals (replace if not), clean coils per manual, a few inches from the wall, away from heat/sun |

Local copies are in the session scratchpad `kb/` (not in the repo).

**Consensus rule used:** every `say` and safety line is backed by ≥ 2 brands, or 1 brand + ENERGY STAR. A `caution`
may come from one source when it only adds care. Numbers that differ by brand are spoken loosely ("about a hand's
width", "every few days", "a few hours").

**Licence:** manufacturer pages and manuals are "all rights reserved": facts and order only, every line in our own
words, nothing from them ships in the APK. ENERGY STAR is public domain. Not legal advice.

**Not reachable / not used:** Whirlpool (whirlpool.com PDFs return 403 "Access Denied"; no Whirlpool India text
source), the Samsung India RT28C3032GS manual (DA68-03374F-07.pdf downloads but has a broken xref table; neither
pdftotext nor Ghostscript could read it), Samsung India single-door defrost page (video only), LG EG/ZA "burning
smell" pages (only a video playlist).

**Target phrases** ("rubber door seal around the edge of the fridge door", "defrost button in the centre of the round
temperature knob", "plastic drip tray under the freezer box", "ventilation grille at the lower back", "adjustable
levelling feet at the bottom front corners") are true for most Indian fridges but not yet grounding-tested on photos.

---

## refrigerator_not_cooling (diy)

| Line | LG | Samsung | Godrej-DC | Haier / ENERGY STAR |
|---|---|---|---|---|
| Safety: never open a back cover (gas pipes) | LG-cool2 (warning: back cover, gas pipe damage, technician only) | — | p.9 (servicing by a qualified technician), p.12 (sealed system) | — |
| 1 Plugged in, switch on | LG-cool2 (power off?) | Samsung-cool (securely plugged) | p.8 (plug connected, fuse intact) | — |
| 2 Light/display off → test socket with another appliance | LG-cool2 (light/display) | Samsung-cool (plug in another device) | p.8 (power supply normal) | — |
| 3 One step colder / about 3 °C | LG-cool (1-2 °C lower; 3 °C default) | Samsung-cool (3 °C) | p.5 (knob to coldest in summer / big load) | Haier-cool (thermostat not at zero); ENERGY STAR 35-38 °F |
| 4 Just switched on / loaded → a few hours | LG-cool (1-2 h; up to 2-3 days in summer) | Samsung-cool (several hours) | p.3 (run ~4 h at max after install) | — |
| 5 Door shuts fully | LG-cool, LG-cool2 | Samsung-cool | p.5 (door closed after every use) | — |
| 6 Clean door seal; torn → replace (caution) | LG-cool (sticky gasket; worn → service) | Samsung-cool (damp cloth, mild detergent) | p.6 (clean gaskets) | ENERGY STAR (airtight; replace if not) |
| 7 Don't overpack | LG-cool (~60 %) | Samsung-cool (overloading blocks vents) | p.8 (not crowded) | — |
| 8 Vents clear | LG-cool, LG-cool2 | Samsung-cool | — | Haier-cool (cold air outlet) |
| 9 Space behind and beside | LG-cool (10 cm), LG-hot (5 cm) | Samsung-cool (2.5 cm back), Samsung-leak (150 mm back, 300 mm sides) | p.3 (6 in both sides) | ENERGY STAR (a few inches) |
| 10 No sun or heat nearby | LG-cool2 (gas stove, balcony) | Samsung-cool (sun, ovens) | p.3 (sun, cooking stove) | ENERGY STAR (oven, dishwasher, sun) |
| 11 Unplug, vacuum lower-back grille, re-plug | LG-cool2 (power off, vacuum the cover, don't remove it) | Samsung-cool (dust at the compressor fan) | — | ENERGY STAR (clean coils per manual) |
| 12 Still warm → technician | LG-cool (service centre) | Samsung-cool (book a repair) | p.8 (before calling service) | — |

- `escalate_if` burning smell / smoke / sparks: Dacor safety section; Godrej p.9 (unplug before repairs). "seal torn":
  LG-cool (worn gasket → service), ENERGY STAR (replace).
- **Dropped:** Samsung Demo Mode "OF OF" (Samsung only, model-specific button combo); LG "move it indoors from a
  balcony / room 5-43 °C" (kept only as "away from heat"); Samsung "power reset for two minutes" (search summary only).
- **Noted, no entry:** hot sides or front of the fridge are normal (LG-hot, Samsung-hot). A question about it gets no
  KB match today; a small "normal behaviour" entry would fix that later.

## refrigerator_water_leak (caution)

| Line | LG | Samsung-leak | Godrej-DC |
|---|---|---|---|
| Safety: water near plug → switch off at the wall | — | ✓ (unplug + breaker for safety) | p.9 (no water near the fridge: electrical leak), p.3 (wires off the floor for water leaks) |
| 1 A few drops are normal condensation | LG-floor (condensation from hot food / open door) | — | p.8 note (droplets on opening the door are normal) |
| 2 Door closes fully | LG-floor | ✓ (door left open) | — |
| 3 Cool hot food first | LG-floor | ✓ | p.7 (hot food cooled to room temperature) |
| 4 Food covered / closed containers | LG-floor (airtight containers; open container spilled) | ✓ (keep food covered with a lid) | — |
| 5 Clear the drain hole on the back wall | LG-drain (drain blocked by ice or debris) | ✓ (drain line blockages) | — |
| 5 caution: no hot water | LG-drain FAQ (hot air or boiling water damages plastic) | — | — |
| 6 Empty / straighten drip tray | LG-dc (chiller tray collects defrost water) | ✓ (chiller tray) | p.5 (evaporation tray) |
| 7 Single door: defrost every few days | LG-dc | Samsung-ice (twice a week) | p.5 (every 3 days; delay → water spills) |
| 8 Still collecting → unplug, technician | LG-drain (drain blocked deep inside → LG support) | ✓ (customer care) | — |

- **Dropped:** "check a nearby sink or water-purifier hose" (LG-floor only; good advice, but one brand); LG-drain's
  "unplug and leave the doors open for 24 hours to melt the drain" (LG only); Samsung's placement spacing (covered by
  the not-cooling entry).
- Severity `caution`: water on the floor near a mains plug.

## refrigerator_ice_build_up (diy)

| Line | LG | Samsung-ice | Godrej-DC |
|---|---|---|---|
| Safety: never chip ice with anything sharp | LG-dc, LG-frost | — | p.5 |
| 1 Doors close fully | LG-frost | ✓ (complete closure) | p.5 |
| 2 Seal clean, not torn | LG-frost (sticky gaskets) | ✓ (gaps or damage) | p.6 (gasket installed properly) |
| 3 Defrost-button fridge: empty the freezer box | LG-drain (move food) | — | p.5 (remove items from the ice compartment) |
| 4 Drip tray in place | LG-dc (chiller tray) | — | p.5 (evaporation tray) |
| 5 Press the defrost button (usually centre of the knob) | LG-dc (centre button) | ✓ (button in the freezer compartment → caution line) | p.1, 5 (button on the temperature knob) |
| 6 Door shut; button pops out, cooling restarts | LG-dc (releases automatically) | ✓ (returns to normal; ~2 h) | p.5 (restarts automatically; don't open the door) |
| 7 Cool hot food; open the door less | LG-frost | ✓ | p.5, p.7 |
| 8 Every few days, before ~0.5 cm | LG-dc (every alternate day / 3 days) | ✓ (twice a week) | p.5, p.11 (every 3 days; ≤ 6 mm) |
| 9 Frost-free keeps icing, or button stuck → technician | LG-drain (still leaking → support) | ✓ (button doesn't return → service) | — |

- `escalate_if` "button stuck": Samsung-ice.
- Frost-free fridges defrost themselves; heavy ice there means a defrost fault (Haier lists "no defrost" codes Ec, Ed,
  Fd, Rd, Hd as service-only), so the guide sends them to a technician after the door and seal checks.
- **Dropped:** Samsung's knob-by-season numbers (1-2 winter, 5-6 summer; Samsung only and the scale differs by brand);
  LG-frost's "warm cloth" and "frost goes in 1-2 weeks after a power cut" (LG only); LG-drain's 24-hour unplug defrost
  for frost-free models (LG only).

## refrigerator_noisy (diy)

| Line | LG | Samsung-noise | Godrej-DC |
|---|---|---|---|
| Safety: unplug before moving or turning feet | LG-cool2 (disconnect power to prevent accidents) | — | p.9 (unplug before cleaning or repairs) |
| 1 Humming, gurgling, cracking, a click are normal | LG-noise (fan hum, refrigerant gurgle, plastic cracking, valve click) | ✓ (bubbling, cracking, buzzing, fluttering) | — |
| 2 New / just moved → louder at first | LG-noise (after purchase, moving, cleaning) | ✓ (buzzing at the start of a cooling cycle) | — |
| 3 Nothing on top | LG-noise (objects on top cause vibration) | — | p.9 (keep nothing on top) |
| 4 Back and sides not touching wall/cabinets | LG-noise (contact with other units amplifies) | ✓ (≥ 2 in from the back wall) | — |
| 5 Firm and level; turn the front feet | LG-noise (level, no sway, levelling screws) | ✓ (door levelling) | p.3 (flat firm surface; front levelling legs) |
| 6 Nothing pressing on the freezer fan | LG-noise (objects obstruct the fan → clattering) | ✓ (frost blocking the fan) | — |
| 7 Wait ~10 min, re-plug, listen | LG-noise (wait ≥ 10 min before reconnecting) | — | — |
| 8 Still grinding/banging → technician | LG-noise (irregular, continuous, louder → inspection) | ✓ (persists → support) | — |

- `escalate_if` grinding noise: LG-noise (abnormal/continuous noise → service); burning smell/smoke/sparks: Dacor.
- **Dropped:** Samsung's ice-maker and dispenser sounds (not on most Indian models); Godrej's "front slightly higher
  than the back" (Godrej only; model-dependent).

## refrigerator_gas_or_compressor_fault (call_technician)

- Sealed system / gas: LG-cool2 (don't open the back: gas pipes, technician only); Godrej-DC p.9, p.12 (refrigerant
  and sealed system handled by professionals, servicing by a qualified technician).
- Burning smell or smoke → unplug now, contact service: Dacor safety section; Godrej-DC p.9 (unplug before repairs).
- Error codes: Haier-codes (no code is user-fixable).

### brand_codes

| Brand | Codes | Meaning (Haier-codes) |
|---|---|---|
| Haier | F1-F9 | Sensor errors → call service |
| Haier | E0 | Communication error |
| Haier | E1, E2, E6, E9 | Fan errors |
| Haier | Ec, Fd | No defrost |

**Codes left out:** Haier Fr and Eh (sensor), Er (ice maker), Ed, Rd, Hd (no defrost): they read as words or
fillers ("er", "Ed", "HD") and would misfire. P0-P4 (door open or switch error): Haier says close the door first, so
they aren't technician-only. No LG, Samsung or Godrej fridge codes were added (no official India code list checked).

### Retrieval notes

- "my fridge is leaking" ties between the water entry and "fridge gas leak" (→ no stage-2 match), so a gas leak is
  never sent into the water guide; "fridge is leaking water" and "fridge gas leak" each resolve.
- "fridge compressor is not working" → technician; "fridge compressor making noise" → noisy.
- "fridge too cold", "fridge smells bad" and "fridge sides are hot" return no match (no entry yet).
