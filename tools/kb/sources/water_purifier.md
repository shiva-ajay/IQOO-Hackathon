# Sources: RO / UV water purifiers (`water_purifier`)

Checked 2026-09-27. Generic entries for Indian point-of-use purifiers (RO and RO+UV), combined from official manuals.
"p.N" is the page index in the PDF file unless it says "printed".

## Documents used

| Short name | Document | URL | Notes |
|---|---|---|---|
| AO Smith Z5 | A. O. Smith India Z5 RO water purifier user manual | https://www.aosmithindia.com/wp-content/uploads/2022/01/Z5_User-Manual.pdf | A3 sheets; cited by **printed** page. Troubleshooting table printed p.11-12 (read from a rendered image). The X9 manual (https://www.aosmithindia.com/wp-content/uploads/2023/11/X9_UM_CTC_12-12-2023_2-1.pdf) has the same table |
| Aquaguard NXT | Eureka Forbes, Sure from Aquaguard Delight NXT RO+UV+TA user manual | https://www.eurekaforbes.com/media/downloadfiles/usermanual/NXT_RO+UV+TA.pdf | Tips and precautions p.7, "A quick checklist" (troubleshooting) p.8 |
| EF blog | Eureka Forbes blog, "How to troubleshoot common problems in commercial water purifiers" | https://www.eurekaforbes.com/blog/how-to-troubleshoot-common-problems-in-commercial-water-purifiers.html | Official brand site, but about commercial units; used only as the second source for "look for visible loose fittings" |
| Livpure Pep | Livpure Pep series RO / RO+UV user manual | https://5.imimg.com/data5/IJ/BA/MY-12968843/livpure-pep-ro-water-purifier-7-litres.pdf | The Livpure document, hosted by an IndiaMART seller (no livpure.com PDF found). FAQ p.16-17, troubleshooting table p.17 |
| KENT Grand | KENT Grand / Grand-B Mineral RO instruction handbook | https://www.kent.co.in/pdf/kent-grand-11119.pdf | Don't self-service p.3, ball valve p.5, maintenance p.6, automatic operation p.12 |
| KENT Maxx | KENT Maxx UV purifier instruction handbook (27-11-2025) | https://www.kent.co.in/pdf/kent-maxx-user-manual.pdf | UV (not RO) model; used for the filter-change alarm (p.5) and power LED (p.6) only |
| Pureit Marvella | HUL Pureit Marvella instruction manual (2010), archive.org copy | https://archive.org/details/manualzilla-id-7343392 | Older non-RO tap-connected Pureit. FAQ 10 (leak) and FAQ 11 (stored water 2 days). Used only as a supporting source |
| CPSC 513 | US CPSC Home Electrical Safety Checklist, Pub 513 | https://www.govinfo.gov/content/pkg/GOVPUB-Y3_C76_3-PURL-LPS108849/pdf/GOVPUB-Y3_C76_3-PURL-LPS108849.pdf | p.5: power off at the breaker when an appliance is a shock risk (wet socket wording) |

### Tried but not usable

- **Pureit RO manuals**: the pureitwater.com PDF link (`/IN/uploads/product/manual/pureit-classic-ro-uv-manual.pdf`) now
  returns the site's home page; ManualsLib and Scribd copies return 403. Only the older Marvella manual was readable.
- **KENT Pearl, Prime Plus, Superb, Elite** (kent.co.in/pdf/...): return an HTML page, not the PDF.
- **Aquaguard Crest** (eurekaforbes.com): no troubleshooting section. **Aquaguard Royale** (Croma-hosted): host did not
  resolve. **Aqua Guard RO (UAE, archive.org)**: not the India product; not used.
- manuals.plus / ManualsLib / device.report: 403. Not used.

---

## water_purifier_no_water (diy)

| Line | Agreeing sources |
|---|---|
| Safety: don't open the cover or pull out filters | KENT Grand p.3 ("do not try to service the purifier on your own"); Livpure p.16 (FAQ 7: not recommended to open it); Aquaguard NXT p.7 (installation, service and cartridge changes by an authorised technician only) |
| 1 Switched on, power light glowing | Livpure p.16 (FAQ 6 "no water in the storage tank": check water supply, check power supply); KENT Maxx p.6 (red LED = power on); AO Smith Z5 printed p.12 (the ON/power LED is how faults are shown) |
| 2 Feed tap has water, overhead tank not empty | AO Smith Z5 printed p.11 ("check whether there is water supply in the tap", else plumber); Aquaguard NXT p.8 (water in the overhead tank? main supply tap closed?); Livpure p.16 (check water supply) |
| 3 Inlet valve fully open | AO Smith Z5 printed p.11 (open the tap/ball valve, both for less and no water); Aquaguard NXT p.8 ("is the tap water valve closed?"); Livpure p.17 (ball valve open fully); KENT Grand p.5 (open the SS ball valve) |
| 4 Low pressure or heavy use makes it slow; give the tank time | Aquaguard NXT p.8 (tap pressure too low: less purified water); KENT Grand p.12 (won't start below 0.3 kg/cm²; stops when the tank is full and restarts as it's used); AO Smith Z5 printed p.12 (low-pressure alarm: check feed water); Livpure p.15-16 (FAQ 2: purifying time depends on input pressure and filters) |
| 5 Filter-change light on or blinking: book a service | AO Smith Z5 printed p.11-12 (filter change LED: call customer care for membrane/filter replacement); KENT Maxx p.5 and Grand p.6 (filter change alarm: service technician replaces the filters); Aquaguard NXT p.8 (clogged filters: authorised technician) |
| 6 Still little or no water: technician | AO Smith Z5 printed p.11 ("none of the above: call customer care"); Livpure p.16; Aquaguard NXT p.8 |

- **Dropped:** bent or blocked reject-water tube (AO Smith only); flow restrictor (Aquaguard only); the ball-valve handle
  direction (KENT only: "handle parallel"); exact refill times (differ by model: Livpure 12 L/h, Pureit 30 min for 4.5 L).
  LED patterns (tank-full blinking = overflow error etc.) are model-specific, so only "filter change light" is named.
- **Escalation:** "water leaking" (see the leak entry), "burning smell", "smoke coming out" (electrical fault).

## water_purifier_bad_taste (diy)

| Line | Agreeing sources |
|---|---|
| Safety: don't drink it while it tastes or smells wrong | Aquaguard NXT p.7 (medicinal taste or smell: discard the tank and let it refill); Pureit Marvella FAQ 11 (discard water stored over 2 days); Livpure p.17 (discard water not used within 48 h). Worded as a precaution: nobody should drink water the manuals say to throw away |
| Safety: don't open it or change filters yourself | KENT Grand p.3; Livpure p.16; Aquaguard NXT p.7 |
| 1 Stored over two days: drain it through the tap | Livpure p.17 (use within 48 h; tastes unusual → drain the stored water); Aquaguard NXT p.7 (drain the tank if unused for over 48 hours) and p.8; Pureit Marvella FAQ 11 (2 days); AO Smith Z5 printed p.11 (drain the stored water through the faucet) |
| 2 Let it refill with fresh water, taste again | Livpure p.17 ("fill fresh water"); Aquaguard NXT p.7 ("let it refill again"); Pureit Marvella FAQ 11 |
| 3 Filter-change light on: book a service | AO Smith Z5 printed p.11-12; Aquaguard NXT p.8 ("is it time to change the filter?" → technician); Livpure p.17 (filters choked → service); KENT Grand p.6 |
| 4 Still odd: don't drink it, technician | AO Smith Z5 printed p.11 (membrane/filters or changed raw water: call customer care); Livpure p.17; Aquaguard NXT p.8 |

- **Dropped:** cleaning or disinfecting the storage tank (Aquaguard p.7 and KENT p.6 describe it, but Aquaguard says the
  disinfection is done by the technician and it needs the cover open); TDS-controller adjustment (KENT only, a screw
  inside).

## water_purifier_leaking (caution)

| Line | Agreeing sources |
|---|---|
| Safety: close the inlet valve | Aquaguard NXT p.8 (water leaking: "close the tap water valve"); Pureit Marvella FAQ 10 (switch off the water inlet first); EF blog (turn off the inlet valve immediately) |
| Safety: switch off and unplug; wet socket → main switch | Aquaguard NXT p.8 ("unplug the power cord"); Livpure p.17 (FAQ 8: switch off the purifier); CPSC 513 p.5 (breaker, for the wet-socket case) |
| 1 Wipe dry, look for a loose pipe or tap joint outside | Livpure p.17 (FAQ 8 "check for any visual improper fitment like tap or connection to purifier"); EF blog (wipe down the area, identify visible loose fittings) |
| 2 Don't open the cover or pull pipes apart | KENT Grand p.3; Livpure p.16; EF blog (internal leaks must not be handled without training) |
| 3 Leave it off, valve closed, call a technician | Aquaguard NXT p.8; Livpure p.17 (register a service request); Pureit Marvella FAQ 10 |

- **Dropped:** pushing a loose push-fit pipe back in (Livpure installation p.12 only); the AO Smith "tank full LED
  blinking = overflow error" code (model-specific; its answer is also "switch off and call").
- **Escalation:** "socket is wet", "burning smell", "sparks from plug".

## Licence notes

All manuals are © their makers (all rights reserved); facts and order only, our own wording, nothing shipped in the APK.
The Livpure PDF is a seller-hosted copy of the maker's manual and the Pureit one an archive.org copy; both are cited as
the maker's document. CPSC Pub 513 is US public domain.
