# Sources: home inverters / home UPS with a battery (`inverter`)

Checked 2026-09-27. Generic entries for Indian home inverters (home UPS) on lead-acid (tubular or flat plate) or sealed
batteries. "p.N" is the page index in the PDF file.

## Documents used

| Short name | Document | URL | Notes |
|---|---|---|---|
| Microtek HD | Microtek Heavy Duty VTurbo New pure sinewave UPS (1400-2350) user manual, issue 05, 24/03/2025 | https://cms.microtek.in/upload/product/HeavyDuty-Manual-SW---Microtek-1759465643435.pdf | Microtek's CMS. LEDs, power button, buzzer table p.3; troubleshooting table p.4; safety p.6; Do's and Don'ts, vacation p.7 |
| Livfast Flash | Livfast Smart Flash sine wave home UPS user manual | https://www.livfast.in/wp-content/uploads/2025/01/Smart-Flash-Inverter-Manual_Livfast.pdf | Livfast is a SAR Group brand (the same group as Livguard); counted as one brand with Livguard, not two. Safety p.2; power switch p.3; display messages p.5; back panel breaker p.8; troubleshooting p.9; beep table p.10 |
| Livguard FAQ | Livguard LGS1100i product page, FAQ "How do I maintain my inverter" | https://www.livguard.com/product/lgs1100i | "Don't overload it"; "if you've got a lead-acid battery, check the water level now and then" |
| Luminous water | Luminous blog, "Inverter Battery Water: Everything You Need to Know" (02 Sep 2026) | https://www.luminousindia.com/blogs/inverter-battery-water-everything-you-need-know | Official Luminous site. Float indicators (green/red), distilled water only, pour slowly, stop at the mark, close caps and wipe spills, no sparks, maintenance-free batteries need no water |
| Luminous care | Luminous blog, "Inverter battery maintenance guide and tips for longer life" | https://www.luminousindia.com/blogs/inverter-battery-maintenance-guide-tips-longer-life | Don't overload; leakage, swelling, cracks, heavy corrosion, reduced backup → qualified technician; "do not open or repair the battery yourself" |
| Genus | Genus Innovation (inverter and battery maker), "Steps to top-up water in inverter battery: dos and don'ts" | https://shop.genusinnovation.com/blogs/invertor-ups/steps-to-top-up-water-in-inverter-battery-dos-and-don-ts | Distilled water only; up to the Max level; gloves; "never top-up when the inverter is either charging or is on"; don't overfill; ventilated area |
| Exide tips | Exide Industries, battery maintenance tips (infrastructure / stationary batteries) | https://www.exideindustries.com/products/infrastructure-batteries/battery-tips.aspx | Supporting only: no open flame near cells, loose cables spark and can explode, bulging containers, plastic vessels and protective wear for acid |
| CPSC 513 | US CPSC Home Electrical Safety Checklist, Pub 513 | https://www.govinfo.gov/content/pkg/GOVPUB-Y3_C76_3-PURL-LPS108849/pdf/GOVPUB-Y3_C76_3-PURL-LPS108849.pdf | p.3: odd smells, sparks or smoke from an appliance = unsafe electrical condition |

### Tried but not usable

- **Luminous user manuals** (Zelio, Eco Volt, Optimus): luminousindia.com only offers catalogues; the manuals are on
  manuals.plus, device.report, ManualsLib and Scribd, which return 403 here. A dealer-hosted "luminous-inverter.pdf" is a
  catalogue, not a manual. Luminous's own blogs were used instead.
- **Microtek manual index** (microtek.in/user-manual): rendered by JavaScript; only the Heavy Duty manual was reachable.
- **V-Guard, Su-Kam, Exide home-UPS manuals**: no official PDF found (brochures only). Exide Technologies' US/EU
  "Tubular LMX" manuals are a different company and industrial batteries; not used.
- **Livguard user manuals**: only on manuals.plus (403). Livguard's own product FAQ was used.

---

## inverter_no_backup (caution)

| Line | Agreeing sources |
|---|---|
| Safety: don't open the inverter; high voltage inside even when off | Microtek HD p.6 ("do not open the UPS, there are dangerous high voltages inside even when the power is OFF"); Livfast p.2 (same, "even when power switch is off") |
| 1 Front switch on; plug firmly in a working socket | Livfast p.3 ("if switch is off the UPS will not work in event of mains failure") and p.9 (dead wall socket; loose line cord → plug it in properly); Microtek HD p.4 (mains normal but UPS on battery: dead wall socket, loose AC input; UPS does not operate: check the mains connections) and p.3 (front power button switches the output on and off). The switch-off cause is stated outright only by Livfast; Microtek describes the same button |
| 2 Breaker on the back tripped: switch it back on once | Microtek HD p.4 ("check MCB at the rear. If it is in Off position then set it to On ... If trips again, call auth. service personnel"); Livfast p.9 (shift the MCB knob up to ON) and p.8 (the thermal breaker knob pops out on overload: reduce the load, press it in) |
| 2 Caution: trips again → leave it, technician | Microtek HD p.4 |
| 3 In a power cut, switch off heavy appliances; load drains the battery | Microtek HD p.4 ("the load is more. Reduce the load"); Livfast p.5 (battery-low warning: reduce the connected load to get more backup) and p.9; Luminous care (running more than the inverter is designed for strains the backup); Livguard FAQ ("don't overload it") |
| 4 After a long cut, let it recharge on mains for several hours | Microtek HD p.4 ("charge the battery with mains, min. for 8-12 hours"); Livfast p.9 ("recharge the battery after mains restoration"). Said as "several hours" because only Microtek gives a number |
| 5 Water-level indicators: switch the inverter off and unplug first | Genus ("never top-up when the inverter is either charging or is on"); Microtek HD p.6 (switch off the UPS and disconnect mains when disconnecting the battery) and p.7 (unplug and switch off before touching); Livfast p.2 |
| 5 Caution: maintenance-free batteries need no water | Luminous water ("inverter battery water is required only in specific lead-acid batteries"); Luminous care ("maintenance-free batteries do not need regular water top-ups") |
| 6 If low, top up slowly with distilled water, only to the max mark | Luminous water (check the float indicators, red = top up; distilled water only, not tap or packaged drinking water; pour slowly into each cell; stop at the mark, don't overfill); Genus (distilled water only; pour slowly to the Max level; don't overfill); Microtek HD p.4 (short backup: "check battery water") and p.3 (battery water level port); Livguard FAQ (check the water level) |
| 6 Caution: gloves, acid burns, no tap water, no flames | Genus (wear gloves); Microtek HD p.6 and Livfast p.2 ("be sure not to come in contact with battery acid", "don't allow sparks near the battery"); Luminous water (don't check near sparks); Exide tips (no open flame near cells, protective wear) |
| 7 Close the caps, wipe spills, plug back in and switch on | Luminous water (close caps firmly, wipe spills); Genus (close the vent plugs clockwise) |
| 8 Still short: battery may be worn out, technician | Microtek HD p.4 ("if still the backup time is less, get the battery checked up by auth. service personnel"); Luminous care (reduced backup duration is a sign for a qualified technician) |

- **Dropped:** charging-current and battery-type selector switches (Microtek p.3; a wrong setting hurts the battery, so it's
  installer work); ECO/UPS mode (differs by model); specific gravity checks (Exide, needs a hydrometer); "check the
  socket with a lamp" (Livfast only); naming heavy appliances (no manual lists which ones).
- **Escalation:** "burning smell", "battery swelling", "acid leaking", "sparks at battery" (→ danger entry), "keeps
  tripping" (Microtek p.4).

## inverter_beeping_overload (diy)

| Line | Agreeing sources |
|---|---|
| Safety: don't open the inverter | Microtek HD p.6; Livfast p.2 |
| 1 Display or lights show overload or battery low | Microtek HD p.3 (Battery Low LED, UPS Overload LED, buzzer table); Livfast p.5 (Over Load Warning, Battery Low Warning messages) and p.10 (beep patterns) |
| 2 Overload: switch off heavy appliances until the warning stops | Microtek HD p.4 (overload: "reduce the load and reset the UPS"); Livfast p.5 ("reduce the connected load as per inverter capacity") and p.9 |
| 3 Reset: front switch off and on | Microtek HD p.4 ("reset the UPS") and p.3 (power button on/off); Livfast p.3 (the power switch "will also work as a reset in the event of overload, low battery shut down and protections") and p.9 ("reset the UPS from front switch") |
| 4 Battery low in a power cut: switch off what you don't need; it recharges when power returns | Microtek HD p.4 (low battery: recharge after mains restoration); Livfast p.5 (reduce load for more backup; the warning clears when mains returns) |
| 5 Keeps beeping with little load, fault or short circuit: technician | Microtek HD p.4 (short circuit: remove the load and reset, if still short → service; other faults: reset, "if still alarm buzzing, call auth. service personnel"); Livfast p.5 (short circuit: check wiring and load; fault) |

- **Dropped:** Luminous "OL" display code (only seen in a search snippet of a manuals.plus page that couldn't be opened),
  so no `brand_codes`. Over-temperature and fan checks (Microtek only). Wi-Fi/app alerts (model-specific).
- **Escalation:** "still beeping", "short circuit", "burning smell", "battery swelling".

## inverter_battery_danger (call_technician)

- **Burning smell, smoke, sparks:** CPSC 513 p.3 (odd smells, sparks or smoke = unsafe electrical condition); Microtek HD
  p.6 (no sparks near the battery; emergency: switch off the front panel switch and disconnect the mains cord and battery
  wires); Livfast p.2 (same emergency procedure; "remove at least one battery terminal" is left to the technician).
- **Swelling, bulging, acid leak, cracks, heavy corrosion:** Luminous care ("if there is leakage, swelling, or visible
  damage, get professional help"; "do not open the battery or try to repair it yourself"; heavy corrosion → qualified
  technician); Microtek HD p.6 and Livfast p.2 (no contact with battery acid); Exide tips (bulging containers; sparks from
  loose cables can cause explosion).
- **Opening the inverter, wiring, replacing the battery:** Microtek HD p.6 (high voltage inside) and p.4 (wrong battery
  polarity can blow the fuse and cause fire; installation by a knowledgeable person); Livfast p.2 and p.9 (installation by
  a qualified technician).
- **Meaning:** don't touch the battery; if safe, switch off and unplug; technician. The battery terminal is not
  disconnected by the user because of acid and spark risk.

## Licence notes

Manuals, blogs and product pages are © their makers (all rights reserved). Facts and order only, our own wording,
nothing shipped in the APK. CPSC Pub 513 is US public domain.
