# Sources: air conditioner (`appliance: "air_conditioner"`)

Checked 2026-09-27. Entries: [../entries/air_conditioner.json](../entries/air_conditioner.json). Rules: [../AUTHORING.md](../AUTHORING.md).
Split ACs first; window ACs are covered where the same step applies (filter behind the front grille, the back of the
unit outside).

## Documents

| Short name | Document | URL | Notes |
|---|---|---|---|
| **LG-cool** | LG India help library: "LG Air Conditioner: less Cooling or No Cold Air" (2026-05-21) | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT52006833-20153301492448/ | Pages are JS-rendered. The article text is in the page's JSON-LD `articleBody` (read with curl) |
| **LG-cool2** | LG India: "LG Air Conditioner [Ceiling AC] Not Cooling" | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT52006833-20153263842746/ | Mode, target temperature, louver, outdoor room ventilation, filter, doors |
| **LG-win** | LG Levant: "How to fix an LG Window Air Conditioner not blowing cold air" | https://www.lg.com/levant_en/support/product-help/CT20158041-20155184083530 | Window AC: cool mode 18 °C for 30 min, front cover + filter, outdoor side, sunshade |
| **LG-filter** | LG India: "How to Clean the Filter in LG Split Air Conditioner" (2026-09-08) | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT32003422-20152544608551/ | Every 2 weeks; power off + unplug; sturdy ladder; running water + soft brush; dry in shade |
| **LG-leak** | LG India: "Check This If Water Leaks or Drips from Your LG Split Air Conditioner" (2026-07-02) | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT20150063-20153183858492/ | Clogged drain pipe, open windows/doors, dirty filter → icing → overflow |
| **LG-remote** | LG India: "[LG Air Conditioner] Remote Control Not Working" (2026-06-18; same text as cs-CT00022939-20154664029676) | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT52006833-20154838066416/ | Batteries, phone-camera IR test (not iPhones), breaker, receiver covered, fluorescent/neon lamps |
| **LG-smell** | LG India: "[LG Air Conditioner Smell] The air conditioner suddenly smells awful" | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT00022939-20153255241375/ | ≤ 20 °C, fan mode ~5 min after cooling, wash filter, dry in shade; persists → disassemble and wash inside |
| **LG-codes** | LG India: "[LG Air Conditioner] Guide to Error Codes" (2026-03-30) + CH05 page + "How to read error codes" | https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT52006833-20154846171880/ · https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT00022939-20153280864489/ · https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT00022939-20155117728312/ | CH-prefixed codes; power-cycle 5 min, then service |
| **Daikin-IN** | Daikin India "Troubleshooting" page | https://daikinindia.com/product-services/product-services/troubleshooting | Split/multi-split checklist "before calling for servicing" |
| **Daikin-codes** | Daikin India "Error Codes" page | https://daikinindia.com/product-services/product-services/error-codes | Same table as Daikin-OM p.44 |
| **Daikin-OM** | Daikin room AC operation manual CTXM/FTXM (R32), 3PEN393186-10J | https://www.daikin.eu/content/dam/document-library/operation-manuals/ac/split/CTXM-M_FTXM-M_3PEN393186-10J_Operation%20manuals_English.pdf | EU print, used because the Daikin India manual (GTKW) is image-only. Pages are the printed ones: safety p.2-3, remote p.3/10, filter p.36, troubleshooting p.40-43, codes p.44 |
| **Samsung-cool** | Samsung India "Samsung Air Conditioner: How to ensure optimum cooling" | https://www.samsung.com/in/support/home-appliances/samsung-air-conditioner-how-to-ensure-optimum-cooling/ | Cool mode lower temp, filter every 2 weeks, outdoor unit in shade, curtains |
| **Samsung-filter** | Samsung India "How to clean filter of Samsung air conditioner" + window AC filter page | https://www.samsung.com/in/support/home-appliances/ac-filter-cleaning/ · https://www.samsung.com/in/support/home-appliances/how-to-clean-air-filter-of-my-window-air-conditioner/ | Running water, dry in a ventilated place; window AC: open front grille, remove, wash, dry |
| **Samsung-leak** | Samsung Gulf "How to fix water leaks from a Samsung AC indoor unit" + Samsung Saudi water-leakage page | https://www.samsung.com/ae/support/home-appliances/how-to-fix-water-leakage-in-your-samsung-ac-indoor-unit/ · https://www.samsung.com/sa_en/support/home-appliances/what-to-do-if-there-is-water-leakage-on-the-samsung-ac-indoor-unit/ | Unplug + breaker; drain pipe slope; wash filter; close windows/doors; collect water in a container. The Samsung India page only gives 24 °C / dry mode / fan speed |
| **Samsung-remote** | Samsung India "What to do if the Samsung AC remote is not working" | https://www.samsung.com/in/support/home-appliances/when-the-remote-control-is-not-working/ | Reinsert batteries after 30 s, polarity, replace; camera test; obstacles; bright/neon lights |
| **Samsung-smell** | Samsung Singapore "What to do when Samsung air conditioner emits a bad smell" | https://www.samsung.com/sg/support/home-appliances/what-to-do-when-samsung-air-conditioner-emits-a-bad-smell/ | The Samsung India page with the same title is video-only (no text) |
| **Samsung-gas** | Samsung India "What to do if there is gas leakage smelled from the air conditioning system" | https://www.samsung.com/in/support/home-appliances/what-to-do-if-there-is-gas-leakage-smelled-from-the-air-conditioning-system/ | Refrigerant has no smell; open windows, stop using electrics, check the gas (LPG) pipes |
| **Panasonic-OM** | Panasonic CS-C18HKD/CU-C18HKD operating instructions | https://aircon.cis.panasonic.com/wp-content/uploads/cs-c18hkd_cu-c18hkd_cs-c24hkd_cu-c24hkd.pdf | CIS-region print (Panasonic India site has no text manual). Safety p.1, parts p.3, care p.6, troubleshooting p.7 |
| **DOE** | US DOE "Energy Saver 101: Home Cooling" infographic | https://www.energy.gov/sites/prod/files/HomeCooling101-final.pdf | Public domain. Clean/replace filters; clear debris and leaves from the outdoor unit; low/leaking refrigerant → trained technician. (The energy.gov "air-conditioner-maintenance" pages now 404) |

Local copies (HTML, PDF, text) are in the session scratchpad `kb/` (not in the repo).

**Consensus rule used:** every `say` and safety line is backed by ≥ 2 brands, or 1 brand + DOE. A `caution` may come
from one source when it only adds care. Where brands differ (filter removal, how long to dry the inside), the line is
worded so it is true for all of them.

**Licence:** manufacturer pages and manuals are "all rights reserved"; facts and order only, every line in our own
words, nothing from them ships in the APK. DOE material is public domain. Not legal advice.

**Not reachable / not used:** Voltas (only third-party copies on Scribd / manuals.plus), Blue Star (the
bluestarindia.com PDF link served an HTML page), Daikin India GTKW manual (image-only PDF, no text layer).

**Target phrases** ("front cover panel of the indoor AC unit", "plastic mesh air filter", "small dark signal receiver
window", "outdoor AC unit with the round fan grille", "thin plastic drain pipe coming out of the wall") hold for most
wall-mounted splits but are not yet grounding-tested on photos.

---

## air_conditioner_not_cooling (diy)

| Line | LG | Daikin | Samsung | Panasonic | DOE |
|---|---|---|---|---|---|
| Safety: sturdy stool/ladder | LG-filter | Daikin-OM p.35 (robust stool) | — | — | — |
| 1 Cool mode, not fan/dry/auto | LG-cool, LG-cool2, LG-win | Daikin-IN, Daikin-OM p.41 (mode set) | Samsung-cool | — | — |
| 2 Temp below room, fan high | LG-cool (target 18 °C, F5), LG-cool2 | Daikin-IN, Daikin-OM p.41 (temp; airflow rate) | Samsung-cool (lower temp) | p.7 set temperature correctly | — |
| 3 Doors/windows shut, curtains | LG-cool (curtains), LG-cool2 (doors) | Daikin-IN, Daikin-OM p.41 | Samsung-cool (curtains) | p.7 | window coverings |
| 4 Nothing blocks the indoor unit | — | Daikin-IN (furniture below/next), Daikin-OM p.41 (inlet/outlet) | — | p.7 obstruction at inlet/outlet | — |
| 5 Wait 30 min, then power off at the wall | LG-cool (30 min); LG-filter (unplug) | Daikin-OM p.3 (breaker/cord before cleaning) | Samsung-smell (unplug/breaker) | p.6 unplug before cleaning | — |
| 6 Open front cover / window grille | LG-filter, LG-win | Daikin-OM p.35-36 | Samsung-filter (panel; window: grille) | p.6 front panel | — |
| 7-9 Filters out, rinse or vacuum, dry in shade | LG-filter, LG-cool (brush/vacuum; ≤ 40 °C; neutral detergent) | Daikin-OM p.36 (water or vacuum; lukewarm; shade) | Samsung-filter (running water; ventilated place) | p.6 (≤ 40 °C; shade, not sunlight) | clean filters |
| 10 Back in, switch on, ~3 min delay | LG-filter | Daikin-OM p.40 (wait ~3 min) | Samsung-filter | p.7 delay protects compressor | — |
| 11 Outdoor unit / back of window AC unblocked | LG-cool, LG-win | Daikin-IN, Daikin-OM p.3, 41 | — | p.7 | clear debris and leaves |
| 12 Still not cooling → technician (gas/repair) | LG-codes (CH38 low refrigerant → installer/LG) | Daikin-OM p.2 (refrigerant leak → dealer) | Samsung-cool (contact support) | — | refrigerant → trained technician |

- Step 11 caution (don't touch fins): Daikin-OM p.3, Panasonic p.1/6.
- `escalate_if`: burning smell, smoke, power cord hot, breaker keeps tripping (Daikin-OM p.43 "call the service shop
  immediately"; Panasonic p.6 "non serviceable criteria"); sparks (electrical danger sign, same list).
- **Dropped:** LG's "VIR" boost mode and 6 vane angles (LG only); LG's "spray water on the outdoor unit" (LG only,
  and water near outdoor electrics); sunshade over the outdoor unit (LG + Samsung, but an installation change, not a
  quick check); room-size/tonnage check (Samsung only); "set 18 °C" (LG only, spoken as "a few degrees below").
- Aliases: "clean the AC filter" routes here because this is the entry with the full filter procedure.

## air_conditioner_water_dripping (caution)

| Line | LG | Samsung | Daikin | Panasonic |
|---|---|---|---|---|
| Safety: AC off + wall switch/breaker | LG-filter | Samsung-leak (unplug + breaker) | Daikin-IN (stop, unplug/breaker) | p.6 turn off and unplug |
| 1 Outdoor-unit dripping is normal | — | — | Daikin-OM p.40 (condensation on outdoor piping) | p.7 (outdoor unit emits water) |
| 2 Clear the area, bowl/towel | — | Samsung-leak SA (collect water in a container) | Daikin-OM p.3 (nothing moisture-sensitive below) | — |
| 3-5 Filters out, rinse, dry, back | LG-leak (dirty filter → icing → overflow) | Samsung-leak (wash with running water, dry) | Daikin-OM p.3 (filter dirt can cause dripping) | — |
| 6-7 Drain pipe slopes down, not blocked/squashed | LG-leak (clogged drain pipe) | Samsung-leak (drain slope) | Daikin-OM p.3 (arrange drain hose for smooth drainage) | p.1 (drain pipe connected properly) |
| 8 Doors/windows closed | LG-leak | Samsung-leak Gulf | — | — |
| 9 Still dripping → off + technician | LG-leak (on-site support) | Samsung-leak (service) | Daikin-OM p.43, Daikin-IN (water leak → contact) | p.6 (water leaks from indoor unit → dealer) |

- Daikin and Panasonic list any indoor leak as "call service"; LG and Samsung give the checks above first. Severity
  is `caution` (water near mains power) and the last step always ends in a technician.
- **Dropped:** Samsung India's 24 °C / dry mode 3-4 h / fan speed advice (Samsung only); Samsung Gulf's "fan mode for
  30 minutes after cleaning" (Samsung only); "drain hose end not under water" (only seen in a search summary, not on a
  page I could open); LG CH04 drain code (see codes below).

## air_conditioner_remote_not_working (diy)

| Line | LG-remote | Samsung-remote | Daikin | Panasonic |
|---|---|---|---|---|
| Safety: batteries away from young children | — | — | Daikin-OM p.3 (keep out of children's reach) | p.1 (children may swallow the batteries) |
| 1 Blank/faint screen → weak batteries | ✓ | — | Daikin-OM p.10, 42 (display fades) | p.7 (display dim) |
| 2 Reinsert matching + and − | — | ✓ (after 30 s, polarity) | — | p.1, 7 (insert correctly, polarity) |
| 3 Replace both, same type | ✓ | ✓ | Daikin-OM p.10 (both, same type), Daikin-IN | p.1, 7 |
| 4 Point at indoor unit, nothing in between | — | ✓ (obstacles) | Daikin-OM p.3 (aim; curtain blocks; ≤ ~7 m) | — |
| 5 Receiver window clean and uncovered | ✓ | — | Daikin-OM p.10 (dust on receiver; wipe) | p.7 (receiver not obstructed) |
| 6 Tube/neon lights off | ✓ (tri-phosphor fluorescent, neon) | ✓ (3-wave, neon) | Daikin-OM p.10 (fluorescent lamp) | p.7 (fluorescent lights) |
| 7-8 Phone-camera IR test; no light → remote faulty | ✓ (not iPhones) | ✓ | — | — |
| 9 Remote OK → check breaker/wall switch | ✓ (breaker marked A/C) | — | Daikin-IN, Daikin-OM p.41 (breaker, fuse) | p.7 (breaker tripped) |
| 10 On/off button on the indoor unit | — | — | Daikin-OM p.3 (indoor ON/OFF switch when remote is missing), Daikin-IN | p.3 (Auto OFF/ON button when remote misplaced) |
| 11 Still won't start → technician | ✓ (AC should be inspected) | ✓ (support) | Daikin-OM p.43 | p.6 (switches not working) |

- `escalate_if` "remote got wet": Panasonic p.6 (water entered the remote → dealer). Breaker keeps tripping: Daikin-OM
  p.43, Panasonic p.6.
- **Dropped:** LG's non-LG/universal remote and multi-unit "ALL" selection notes (LG only); Daikin's "move another
  appliance the remote operates" (Daikin only). The on/off button's position differs by model, so the step card says
  to check the manual.
- `ac not turning on` routes here: Daikin-IN/Daikin-OM "does not operate" and Panasonic "unit does not work" are the
  same checks (batteries, breaker/fuse, power).

## air_conditioner_bad_smell (diy)

| Line | LG-smell | Samsung | Daikin | Panasonic |
|---|---|---|---|---|
| Safety: off + wall switch/breaker | LG-filter | Samsung-smell (unplug or breaker) | Daikin-OM p.3 | p.6 |
| Safety: sturdy stool | LG-filter | — | Daikin-OM p.35 | — |
| 1-4 Filters out, wash, dry in shade, back in | ✓ (wash with water, dry in shade until the smell goes) | Samsung-smell, Samsung-filter | — | — |
| 5 Fan-only mode to dry the inside | ✓ (fan ~5 min after cooling) | Samsung-smell (fan only 3-4 h after cleaning) | — | — |
| 6 Smells nearby (bin, shoes, damp clothes) | — | Samsung-smell (drain hose near diaper bin, shoe shelf, laundry; outside smells drawn in) | Daikin-OM p.42, Daikin-IN (room, furniture, cigarette smells absorbed) | p.7 (damp smell from wall, carpet, furniture, clothing) |
| 7 Still smells → technician deep clean | ✓ (disassemble and wash internal parts) | Samsung India (yearly wet service, search summary only) | Daikin-OM p.42 (have the indoor unit washed by a technician) | — |

- Step 5 duration: LG says ~5 min after each cooling run, Samsung 3-4 h after cleaning; "a few hours" follows Samsung
  because it comes right after cleaning.
- `escalate_if` burning/electrical smell, smoke, sparks: Daikin-OM p.2, 43; Panasonic p.1. A burning smell is routed
  to the technician entry at retrieval (see aliases), not to this guide.
- **Dropped:** LG's "set 20 °C or lower" (LG only); LG Auto Drying feature (model-specific); Daikin streamer/ozone note.

## air_conditioner_gas_or_compressor_fault (call_technician)

- Gas: DOE (low or leaking refrigerant → trained technician); Daikin-OM p.2 (refrigerant leak: consult dealer, don't
  use until repaired); LG-codes CH38 (low refrigerant → installer/LG); Samsung-gas (refrigerant has no smell, so a gas
  smell is usually LPG: ventilate, no electrics).
- Burning smell, abnormal sound, hot power cord, breaker tripping often: Daikin-OM p.43 "call the service shop
  immediately"; Panasonic p.1, 6. Compressor/outdoor electrics: DOE (compressor and fan controls → professional).
- `meaning` (switch off, don't use until checked): Daikin-OM p.2, 43; Panasonic p.1.

### brand_codes

| Brand | Code | Meaning (source) |
|---|---|---|
| LG | CH05, CH53 | Indoor-outdoor communication (LG-codes, CH05 page) |
| LG | CH10 / E6 | Indoor fan fault (LG-codes) |
| LG | CH38 / F4 | Low refrigerant (LG-codes). F4 is also shown for CH90/CH91 on some models: also a technician job |
| LG | CH54 | Reverse phase after electrical work (LG-codes) |
| LG | CH66 | Communication or piping between units (LG-codes) |
| LG | CH67 / EF | Outdoor fan fault (LG-codes) |
| LG | CH90, CH91 | Test-run protection, piping to be checked (LG-codes) |
| LG | CH93 | Communication, outdoor unit not powered (LG-codes) |
| Daikin | U0 | Refrigerant shortage (Daikin-codes, Daikin-OM p.44) |
| Daikin | U2 | Voltage drop / overvoltage |
| Daikin | U4 | Indoor-outdoor transmission failure |
| Daikin | UA | Indoor-outdoor combination fault |
| Daikin | E1 | Circuit board fault |
| Daikin | E5 | OL (compressor overload) started |
| Daikin | E6 | Compressor start-up fault |
| Daikin | E7 | DC fan motor fault |
| Daikin | E8 | Overcurrent input |
| Daikin | F6 | High pressure control in cooling |
| Daikin | L5 | Output overcurrent |

LG's official first step for most of these is a 5-minute power cycle, then service. The entry skips the reset and goes
straight to a technician (conservative; `call_technician` entries have no steps).

**Codes left out:**
- LG CH04 / FL (drain / condensate tank full): DIY on window and portable units (drain the cap), reset then service on
  ceiling units. Doesn't fit the wall-split water entry's steps.
- LG CH07 (mixed heating/cooling modes): DIY, set all units to the same mode.
- LG CH61 and its variants P4, P6, P7, P8, CH34 (outdoor unit overheating from poor ventilation): DIY ventilation
  checks then a reset; no matching guide yet.
- LG E0 (communication on some models): the same display shows "EO" while kW Manager runs, so it would misfire.
- Daikin 00 (normal), A1 and A6 (spoken "a 1" / "a 6" would match everyday speech like "a 6 year old AC"), A5, C4, C9,
  H0, H6, H8, H9, J3, J6, P4 (sensor and board faults; still technician jobs, but not gas/compressor/outdoor
  electrics), F3, L3, L4, EA.

### Retrieval notes

- "my AC is leaking" and "the AC smells" deliberately tie (→ no stage-2 match) so that "AC gas leak" and "burning
  smell from the AC" always reach this technician entry rather than the water or smell guides.
- "not cooling" with no appliance named ties with `refrigerator_not_cooling` (as intended); "AC not cooling" wins.
