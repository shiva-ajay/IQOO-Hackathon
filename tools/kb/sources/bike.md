# Sources: two-wheelers (`bike`)

Checked 2026-09-27. Generic entries for Indian motorcycles and scooters, combined from official owner's manuals.
"PDF p.N" means the page index in the PDF file, not the printed page number.

## Documents used

| Short name | Document | URL | Notes |
|---|---|---|---|
| Honda Activa 6G | Honda Motorcycle & Scooter India, Activa 6G (K0PA) Owner's Manual, English (2019) | https://s3-ap-southeast-1.amazonaws.com/assetsin.izmocars.com/userfiles/104202/Manual/Manual%20pdf/Activa_6G__K0PA__English.pdf | Official Honda PDF, hosted by authorised dealer Maximus Honda (linked from https://www.maximushonda.com/om-en-in.htm). honda2wheelersindia.com/resources/owners-manual only serves PDFs through a form, so there was no direct link to use. Scooter, drum brakes, CBS |
| Honda Shine | Honda Shine BS-VI (CBF125) Owner's Manual, English (2019/2020) | https://s3-ap-southeast-1.amazonaws.com/assetsin.izmocars.com/userfiles/104202/Manual/Manual%20pdf/Shine_BS_VI_Eng.pdf | Same dealer host as above. Motorcycle, enclosed chain case, disc (III ID) variant |
| TVS Jupiter | TVS Jupiter Owner's Manual, Revision 9, 6 Dec 2023 | https://www.tvsmotor.com/-/media/Feature/User-Manual-New-Folder/07-12-2023/TVS-Jupiter---SMW.pdf | Scooter, fuel-injected |
| TVS Jupiter 125 | TVS Jupiter 125 DT SXC Owner's Manual, Revision 2, 6 Jan 2026 | https://www.tvsmotor.com/-/media/Feature/Owners/UserManual2026/TVS-Jupiter-125-DT-SXC.pdf | Scooter |
| TVS Apache | TVS Apache RTR 160 2V Owner's Manual, Revision 6, Apr 2022 | https://www.tvsmotor.com/-/media/Feature/Owners/User-Manual-11-Apr-22/TVS-Apache-RTR-160-2V.pdf | Motorcycle, disc front and rear, ABS |
| Bajaj Pulsar 150 | Bajaj Pulsar 150 BS VI Owner's Manual, Doc. 71120250 Rev. 00, Oct 2025 | https://cdn.bajajauto.com/-/media/assets/bajajauto/customer-service/owners-manual/owners-manual-pdf/owner-manual-pdf-2026/pulsar-150_dh72.pdf | Motorcycle |
| Bajaj Pulsar N160 | Bajaj Pulsar N160 Owner's Manual, Doc. 71120183 Rev. 02, Mar 2023 | https://cdn.bajajauto.com/-/media/Assets/bajajauto/customer-service/owners-manual/Owners-Manual-Pdf/Pulsar-N160.pdf | Motorcycle, sealed chain, disc front and rear |
| Bajaj Pulsar 150/180 (2018) | Bajaj Pulsar 150/180 Owner's Manual, Doc. 71112294 (2018) | https://www.bajajauto.com/pdf/pulsar_150_180.pdf | Older carburetted model: fuel tap, manual choke |
| RE Classic 350 | Royal Enfield Classic 350 Owner's Manual (domestic) | https://www.royalenfield.com/content/dam/royal-enfield/ownersManual/Classic350_Owners_Manual_Domestic.pdf | Carburetted UCE model: choke, fuel tap |
| Hero HF Deluxe schedule | Hero MotoCorp HF Deluxe FI maintenance schedule, 3 pages | https://www.heromotocorp.com/content/dam/hero-aem-website/service-journey-assets/maintainence-schedule/HF_Delux.pdf | Supporting source only: its notes on oil top-up and chain service were used |

### Tried but not usable

- **Hero MotoCorp Splendor+ / HF Deluxe full owner's manuals** (`heromotocorp.com/content/dam/hero-aem-website/in/service-owner-manual/*.pdf`): the site returns HTTP 403 to scripted downloads, and the PDFs are over the 10 MB web-fetch limit. Only the 3-page HF Deluxe maintenance schedule could be read.
- **Suzuki Access 125**: `suzukimotorcycle.co.in/User-Manual` returns 403. The only CDN PDF found (`cdn.suzukimotorcycle.co.in/public-live/user-manual/Access-125-UZ125-NR-Ride-connect-2025.pdf`) is the Ride Connect app supplement and has no maintenance content. Suzuki was not used.
- **ManualsLib** (the fallback mirror) returns 403 here as well. No ManualsLib content was used.

Every step below has at least two different makers agreeing (Honda, TVS, Bajaj, Royal Enfield), so losing Hero and Suzuki did not leave any step with only one source.

---

## bike_engine_oil_check (diy)

| Line | Agreeing sources |
|---|---|
| Safety: centre stand on firm, level ground | Honda Activa PDF p.63, Honda Shine p.56, TVS Jupiter p.58, Bajaj Pulsar 150 p.28, RE p.27 |
| Safety: warm a cold engine, switch off, wait | Honda Activa p.63 and Shine p.56 (idle 3-5 min if cold, off, wait 2-3 min); RE p.27 (warm up a few minutes, switch off) and p.17 (run 2 minutes before checking). Worded as "a few minutes" because the numbers differ |
| 1 Window or filler cap, low on the right of the engine | Honda Activa parts location p.19 and Shine parts location (right-side view); TVS Jupiter "gauge oil level" in the right-side parts view p.26; Bajaj Pulsar 150 p.28 (marks on "Cover RH") |
| 2 Window: oil between the upper and lower marks | Bajaj Pulsar 150 p.28 and N160 p.32 (oil level gauge window); RE p.27 (middle of the window, between MAX and MIN) |
| 3 Unscrew the filler cap, wipe the dipstick | Honda Activa p.63, Shine p.56; TVS Jupiter p.58, Jupiter 125 p.86 |
| 3 Caution: engine and exhaust hot | Honda Shine p.34 (let engine, muffler cool); TVS Jupiter 125 p.96 (exhaust hot after a run); Bajaj Pulsar 150 p.34 |
| 4 Rest it in without screwing, pull out again | Honda ("insert until it seats, but don't screw it in"); TVS ("do not thread in", then take it out) |
| 5 Read between the upper and lower marks | Honda Activa p.63, Shine p.56; TVS Jupiter p.58 |
| 6 At or near the lower mark: add the manual's grade | Honda Activa p.64 ("below or near the lower level mark"); Hero HF Deluxe schedule note 3 ("at or near the lower level mark"); TVS p.58; Bajaj p.28; RE p.27 |
| 6 Caution: don't overfill | Honda Activa p.64; TVS Jupiter p.58-59 ("do not fill excess oil") |
| 7 Refit the cap firmly, wipe spills | Honda Activa p.64 ("securely reinstall", "wipe up any spills"); TVS Jupiter p.58-59 ("wipe out the oil traces", refit the gauge) |

- **Dropped:** exact wait and warm-up times, and oil grades and capacities, because they differ by model (TVS SAE 10W30, Bajaj 20W50 or 10W40, Honda's own recommended oil). The target says "your manual's grade" instead. Also dropped: the TVS scooter transmission-oil check (TVS only), and Honda's "don't mix brands or grades" (Honda only).
- **Escalation:** "oil leak" comes from Honda Shine (check for leaks) and TVS (excess oil can leak). The Bajaj N160 low-oil-pressure lamp (p.13) has no owner procedure, so "oil light stays on" escalates. "burning smell" and "smoke from engine" follow the FixLens safety policy.

## bike_chain_slack_lube (diy, motorcycles only)

| Line | Agreeing sources |
|---|---|
| Safety: engine off, key out, neutral | Honda Shine p.73 (neutral, stop engine) and p.34 (remove key); TVS Apache p.75 (neutral, engine off) |
| Safety: centre stand, level ground | Honda Shine p.73; TVS Apache p.75; Bajaj N160 p.36 ("main stand") |
| 1 Find the chain. Caution: may be covered | Chain on all motorcycles. The fully enclosed case with an inspection cap is Honda Shine only (p.73), so the caution just says "your manual shows how" |
| 2 Move the chain up and down halfway along | Honda Shine p.73 (move up and down with your finger) and p.76 (midway between the sprockets); TVS Apache p.76 figure |
| 3 Within the slack range in the manual | The ranges **differ**: Honda Shine 20-30 mm, Bajaj 20-30 mm, RE 25-30 mm, TVS Apache 20-25 mm max. The step names no number |
| 4 Turn the wheel: smooth, no tight spots | Honda Shine p.73 (turn the rear wheel, chain moves smoothly; uneven slack means kinked or binding links, see the dealer); TVS Apache p.75 (turn the rear tyre) |
| 5 Wipe clean while turning the wheel | Honda Shine p.44 (dry cloth, while turning the rear wheel); Bajaj N160 p.36 (lint-free cloth); TVS Apache p.75 (cleaner and soft brush while turning); RE p.27 |
| 6 Lube while turning, wipe off the extra | RE p.27 (lube while turning, wipe off excess); Bajaj N160 p.36 (spray while turning the wheel); TVS Apache p.76; Honda Shine p.44 (no excess) |
| 6 Caution: the manual's lube, keep it off tyres and brakes | Honda Shine p.44 (keep lube off brakes and tyres). Lube types differ (O-ring chain lube, SAE 80/90 gear oil, brand sprays), so the caution says "your manual names" |
| 7 Out of range or kinked: a mechanic adjusts it | Honda Shine p.75 (adjusting needs special tools, have the dealer do it); TVS Apache p.76 (contact the dealer); Hero HF Deluxe schedule note 5 (workshop chain service) |

- **Disagreement:** Bajaj N160 p.35 measures slack "in 1st gear or both wheels touching ground", while Honda and TVS use neutral on the centre stand. We follow Honda and TVS, because steps 4-6 need the wheel to turn by hand, and every maker's lube method turns the wheel.
- **Dropped:** the slack numbers; Honda's "don't ride if slack exceeds 50 mm" (Honda only); cleaning a non-O-ring chain with kerosene (Bajaj Pulsar 150 only); and the adjustment procedure. RE and Bajaj describe owner adjustment, but Honda, TVS and Hero send it to the dealer, and the scope brief says to send it to a mechanic.

## bike_wont_start (diy, ends in "call a mechanic")

| Line | Agreeing sources |
|---|---|
| Safety: firm, level ground | Honda and TVS parking guidance ("firm, level surface"). "Off the road" is FixLens wording |
| 1 Engine stop switch set to run | Honda Shine p.85 (troubleshooting: switch in Run) and p.23; Bajaj N160 p.27 (kill switch); RE p.39 (stop switch off: switch it on). Some scooters have no stop switch, hence "if there's" |
| 2 Side stand fully up | TVS Jupiter 125 p.33, Jupiter p.16, Apache p.32 (won't start with the stand down); Bajaj N160 p.29 (stand down and in gear: self start disabled). Honda doesn't mention a cut-off, hence "many bikes" |
| 3 Neutral, or hold the clutch | Honda Shine p.27; Bajaj N160 p.27, Pulsar 150/180 (2018) p.18; RE p.20 |
| 4 Scooter: squeeze a brake lever while pressing start | Honda Activa p.29 (starter only works with the brake lever squeezed); TVS Jupiter p.43, Jupiter 125 p.74 (apply either brake) |
| 5 Throttle closed, start for 5 s at most | Honda Activa p.28 and Shine p.26 (throttle closed; 5 s, then wait 10 s); Bajaj 2018 p.18 (5 s max, wait 15 s); Bajaj N160 p.27; TVS (engine won't start with the throttle open). The caution says "ten to fifteen seconds" to cover both waits |
| 6 Enough petrol; fuel tap on or reserve | Honda Activa p.78 and Shine p.85 (check for petrol); TVS Jupiter 125 p.74; RE p.20 and p.39; Bajaj 2018 p.18 (fuel tap ON/RES) |
| 7 Choke on older bikes when cold | RE p.20 (below 20 °C) and p.16; Bajaj 2018 p.18 (cold engine; choke off once warm). Fuel-injected models have no choke, hence "older bikes with a choke" |
| 8 Dim lights or dead starter: kick start | RE p.39 (dim lights or horn: weak battery); TVS Jupiter p.29 (battery very low: starter won't work, use the kick starter); Honda Activa p.29-30 and Shine p.27-28 kick-start method |
| 8 Caution: kick from the top; never while running | Honda Activa p.28 (don't kick while the engine runs; kick from the top of the stroke); RE p.20 (powerful kick) |
| 9 Warning light stays on or still no start: mechanic | Honda Activa p.78 and Shine p.85 (MIL on: dealer; problem continues: dealer); TVS Jupiter 125 p.33 (MIL: dealer); RE p.39 (not fixed: service centre) |

- **Dropped:** Honda's hard-start routine (throttle fully open for 5 s; Honda only), fuse checks (locations and ratings differ, and it's electrical, so it goes to a mechanic), RE's carburettor idle and flooding fixes (RE only), Bajaj's auto crank-cut explanation, and TVS intelliGO.
- **Escalation:** "petrol smell" and "fuel leak" come from Honda Shine p.30 (petrol is highly flammable) and Bajaj N160 p.26 (no fuel leaks, daily check). "engine warning light" is the MIL above. "burning smell" follows the FixLens policy.

## bike_tyre_pressure_check (diy)

| Line | Agreeing sources |
|---|---|
| Safety: firm, level ground, engine off, key out | Honda Shine p.34 (maintenance rules) |
| 1 Check while the tyres are cold | Honda Activa p.51 and Shine p.45 ("always check when cold"); TVS Jupiter 125 p.92 (cold); TVS Apache p.74 (within 1 km) |
| 2 Front and rear pressures from the manual | Honda Shine p.45 (see specifications); TVS Jupiter 125 p.92 (table, solo and with pillion); Bajaj Pulsar 150 p.29 ("as per specifications"). The values differ, so none are given |
| 3 Pressure gauge on the valve | Honda Activa p.51 and Shine p.45 (use an air pressure gauge). The valve-cap wording is generic |
| 4 Inflate to the manual's value | TVS Jupiter 125 p.92; Bajaj p.29; Honda |
| 5 Look for cuts, cracks, bulges, nails | Honda Activa p.51 and Shine p.45 (cuts, slits, cracks, nails, bumps, bulges); RE p.28 (cracks and cuts) |
| 6 Tread wear marks: replace | Honda Shine p.46 (wear indicators visible: replace); TVS Jupiter 125 p.92 (TWI mark); RE p.28 (minimum tread depth) |
| 7 Puncture or damage: tyre repair shop | Honda Activa p.80 (puncture repair needs tools, have the dealer do it); TVS Jupiter 125 p.92 and Apache (a tyre dealer or shop that knows tubeless); Bajaj Pulsar 150 p.29 (tubeless tyre repair shop) |
| 7 Caution: ride slowly, only to the nearest shop | Honda Activa p.80 (after a temporary repair ride slowly, 50 km/h max); Bajaj p.29 (the slow leak lets you ride to the nearest puncture shop) |

- **Dropped:** all pressure numbers, the 50 km/h figure, RE's tread depths, Bajaj's "plug or filler method only, no patch" (the shop's job), Honda's emergency repair kit (not every rider has one), and tyre rotation direction.
- **Note:** there's no tyre-pressure sticker in any of these manuals, so the steps point to the manual, not to a label.

## bike_brake_fluid_check (caution: level and lever-feel check only)

| Line | Agreeing sources |
|---|---|
| Safety: upright, level ground, handlebar straight | Honda Shine p.60 (upright, firm level surface); Bajaj Pulsar 150 p.30 and N160 p.37 (handlebar straight); TVS Apache p.68 (reservoir parallel to the ground) |
| 1 Front reservoir window on the right handlebar | Bajaj Pulsar 150 p.30 ("near RH control switch", inspection window); Bajaj N160 p.37; TVS Apache p.68 (right side of the handlebar, view glass) |
| 1 Caution: drum brakes have no window | Honda Activa p.67 (drum: lever free play only); TVS Jupiter 125 p.89 (drum-brake model note) |
| 2 Fluid above the lower or MIN mark | Honda Shine p.60 (above LWR); TVS Apache p.68 (above LOWER); Bajaj p.30 (above MIN); RE p.17 (above MIN) |
| 2 Caution: don't open; fluid damages paint | Honda Shine p.43 (add fluid only in an emergency; it damages plastic and paint) |
| 3 Rear disc: its reservoir on the right side | Honda Shine p.60 (rear reservoir, between the LOWER and UPPER marks); TVS Apache p.69 (behind the right frame cover); Bajaj N160 p.37 (behind the right pillion holder bracket) |
| 4 Levers and pedal firm, not spongy | TVS Apache p.68-69 and Jupiter 125 p.89 (spongy or weak: dealer); Honda Shine p.60 (too much lever travel: check pads, else a leak, see the dealer); RE p.17 (brake levers work smoothly, with free play) |
| 5 Lever travel within the manual's range | Honda Activa p.67 (10-20 mm); TVS Jupiter 125 p.89 (10-15 mm). Numbers differ, so none are given |
| 6 Low fluid or spongy: don't ride, mechanic | Honda Shine p.60; TVS Apache p.68-69; TVS Jupiter 125 p.89 |

- **Disagreement:** Bajaj (Pulsar 150 p.30, N160 p.37) lets owners top up with DOT 4 from a sealed container. Honda Shine p.43 says add fluid only in an emergency and then see the dealer, and TVS has the dealer top up. With AUTHORING principle 5 (brakes beyond a level check go to a technician), there's no top-up step.
- **Dropped:** brake-pad wear checks (Honda and TVS show them), drum lever free-play adjustment (Honda Activa and TVS show it), and fluid grades (DOT 3 or 4 varies). All of these are beyond a level check.

## bike_battery_terminals (caution)

| Line | Agreeing sources |
|---|---|
| Safety: ignition off, key out | Honda Activa p.55 and Shine p.49 (ignition OFF before touching the battery); Honda Shine p.34 (remove key); TVS Jupiter p.56 (ignition off for electrical work) |
| Safety: no flames or sparks, acid, glasses | Honda Activa p.10 (keep flame and spark away; electrolyte burns) and p.48 (wear safety glasses); TVS Jupiter p.55 (explosive gases, keep away from heat) |
| 1 Open the battery cover, where the manual says | Location **varies**: Honda Shine right side cover (p.49), Honda Activa lid under the floor mat (p.55-56), Bajaj 2018 left side cover (p.25), TVS Jupiter 125 behind the front panel, RE locked battery box (p.33) |
| 2 Cables tight, no white crust | Honda Activa p.46-48 (clean if dirty or corroded; white deposit) and p.78 (loose connection or corrosion); TVS Jupiter p.55 (check cables and clamps for loose connections); RE p.33 (clean, corrosion-free terminals) |
| 3 Minus cable off first | Honda Activa p.55, Honda Shine p.49; Bajaj Pulsar 150 p.31 |
| 4 Clean the terminals; wire brush for heavy crust | Honda Activa p.48 (clean; wire brush or sandpaper for heavy corrosion); TVS Jupiter p.55 (clean terminals and connectors) |
| 5 Plus cable on first, tighten both | Honda Activa p.55 and Shine p.49 (positive first; nuts tight); Bajaj Pulsar 150 p.31 (positive first) |
| 6 Petroleum jelly on the terminals | RE p.33 and its maintenance schedule; Bajaj Pulsar 150 p.48 |
| 7 Still weak: charge it or see a mechanic | Bajaj Pulsar 150 p.31 (discharged: charge it at once); Bajaj N160 (discharged: authorised workshop); TVS Jupiter p.55 (recharge below 12.4 V); Honda Activa p.48 (dealer advises on replacement) |

- **Disagreement:** Honda Activa p.48 washes light corrosion with warm water after removing the battery. TVS Jupiter 125 p.83 warns that any water getting into a VRLA battery ruins it. We use a dry cloth or wire brush with the battery left in place, and add the caution "Keep water out of the battery."
- **Disagreement:** RE p.33's reassembly text lists "-VE and +VE" in that order. Honda and Bajaj both say positive first, so we follow them.
- **Dropped:** taking the battery out (fitting varies; Honda only for cleaning), distilled-water top-up (old non-sealed batteries only, Bajaj 2018), the 12.4 V figure (needs a meter), and storage advice.

## bike_brakes_fuel_wiring_danger (call_technician)

No steps. The user is told to stop riding and call a technician.

- **Brakes spongy, weak or failing:** TVS Apache p.68-69 and Jupiter 125 p.89 ("spongy or ineffective": dealer); Honda Shine p.60 (probable leak: dealer).
- **Fuel leak or petrol smell:** Honda Shine p.30 (petrol is highly flammable and explosive; keep heat, sparks and flame away); Bajaj N160 p.26 (daily check: no fuel leaks).
- **Wiring, fuses, shorts:** TVS Jupiter p.56 (short circuit or overload blows fuses; if it blows again, dealer); RE p.39 (if the fuse blows again, service centre).
- **Smoke or burning smell:** FixLens policy. AUTHORING.md principle 5 and kb-collection-plan §1.4 say wiring, fuel lines and brakes beyond a level check are technician-only.

---

## Grounding targets

Targets are written to hold for most Indian models: oil window or cap low on the right of the engine, brake reservoir on the right handlebar, side stand on the left. They have **not** yet been tested on real bike photos (kb-collection-plan §2 wants that before a Tier B entry ships). Parts whose position varies by model (battery, rear brake reservoir, fuel tap, choke) have `target: null` or a looser phrase, plus a "your manual shows where" caution.

## Licence

The owner's manuals are copyright of their makers (Honda Motorcycle & Scooter India, TVS Motor Company, Bajaj Auto, Royal Enfield, Hero MotoCorp). Only facts and step order were used. Every line was rewritten in our own words, and no text or images were copied. Each entry cites its manuals in `source`.

---

## Reminder intervals (`remind`, checked 2026-09-27)

After a routine check's guide is finished, the app schedules a local reminder `after_days` later (app `alerts/`).
Distances are converted at about 25 km a day.

| Entry | after_days | Sources |
|---|---|---|
| bike_chain_slack_lube | 14 | Lube every 500 km (~20 days): TVS Apache p.75, Bajaj N160 p.36, Bajaj 2018 p.28; Royal Enfield p.24 every 1,000 km |
| bike_battery_terminals | 90 | Royal Enfield p.24 every service, 3,000 km or 3 months; Bajaj Pulsar 150 p.41 every service, ~5,000 km |

**No reminder:** `bike_engine_oil_check`, `bike_tyre_pressure_check`, `bike_brake_fluid_check`. The manuals make them
daily pre-ride checks (Bajaj p.23/26, Honda Activa p.45 / Shine p.38, TVS Apache p.47); a daily notification would be
noise, and a looser interval (weekly tyre gauge check) has only one brand (TVS Apache p.52, Jupiter 125 p.76).
