# Sources: television

Checked 2026-09-27. Every step below was written from pages and PDFs that were opened and read on that date
(copies of the downloads are in the session scratchpad, not in the repo). Wording is our own; facts and order
come from the sources.

## Source list

| Key | Document | Where |
|---|---|---|
| **SS-IN** | Samsung India, "What to do when the Samsung TV is not turning ON?" | https://www.samsung.com/in/support/tv-audio-video/what-to-do-when-the-samsung-tv-is-not-turning-on/ |
| **SS-UG** | Samsung 2025 TV Simple User Guide, BN68-20833E-00 (safety, "The TV won't turn on", "The remote control does not work", anti-tip) | https://downloadcenter.samsung.com/content/EM/202505/20250510034710001/BN68-20833E-00_SUG_Y25%20TV%20ALL_ZA_ENG_250224.0.pdf |
| **SS-BLK** | Samsung US, "Samsung TV has no picture or shows a black screen" (TSG10002233) | https://www.samsung.com/us/support/troubleshoot/TSG10002233/ |
| **SS-AE** | Samsung Gulf, "Samsung TV powers on but displays a black screen" | https://www.samsung.com/ae/support/tv-audio-video/samsung-tv-powers-on-but-displays-a-black-screen/ |
| **SS-HDMI** | Samsung, "My TV doesn't detect a device connected via HDMI" | https://www.samsung.com/latin_en/support/tv-audio-video/my-tv-does-not-detect-a-device-connected-via-hdmi/ |
| **SS-RC** | Samsung US, "Samsung remote is not working" (TSG10002214) | https://www.samsung.com/us/support/troubleshoot/TSG10002214/ |
| **SS-POFF** | Samsung UK, "How to turn off Samsung TV screen & keep audio on" | https://www.samsung.com/uk/support/tv-audio-video/how-do-i-turn-off-my-samsung-tv-picture-but-not-the-sound/ |
| **LG-SG** | LG Singapore help library, "If Your LG TV Won't Turn On" (updated 24/08/2026; article body + FAQ) | https://www.lg.com/sg/support/product-support/troubleshoot/help-library/cs-CT52001362-20155409997795/ |
| **LG-UK** | LG UK help library, "LG TV has sound but no picture" (2026-04-23) | https://www.lg.com/uk/support/product-support/troubleshoot/help-library/cs-CT00008333-20155142696330/ |
| **LG-OM** | LG LED TV Owner's Manual, Safety and Reference (MFL71040005, 2019), pp. 2-5 safety, p. 19 troubleshooting | https://gscs-b2c.lge.com/open/downloadFile?fileId=iMWHbfAcRINaCa3xIwfgcw |
| **TCL-PWR** | TCL, "How to Troubleshoot a TV That Will Not Power On" | https://support.tcl.com/en_US/common-questions-TVs/how-to-troubleshoot-a-tv-that-will-not-power-on |
| **TCL-NPS** | TCL, "My TV Is On, but There Is No Picture or Sound" | https://support.tcl.com/en_US/common-questions-TVs/my-tv-is-on-but-there-is-no-picture-or-sound |
| **TCL-ROKU** | TCL, "Roku TV Has Power but no Video" (flashlight test, when to contact support, unplug-now signs) | https://support.tcl.com/en_US/roku-tv-has-power-but-no-video |
| **TCL-FIRE** | TCL, "Troubleshooting Common Screen Issues on a TCL Fire TV" (No Signal section) | https://support.tcl.com/en_US/troubleshooting/troubleshooting-common-screen-issues-on-a-tcl-fire-tv |
| **TCL-RC** | TCL, "How to Troubleshoot Your TCL Android TV Remote Control" | https://support.tcl.com/en_US/us-androidtv-troubleshooting/how-to-troubleshoot-your-tcl-android-tv-remote-control |
| **SONY-PWR** | Sony TV Help Guide, "The TV does not turn on." | https://helpguide.sony.net/tv/faep1/v1/en/08-08_03.html |
| **SONY-RC** | Sony TV Help Guide, "The remote control does not operate." | https://helpguide.sony.net/tv/faep1/v1/en/08-07_01.html |
| **SONY-PIC** | Sony i-Manual, Troubleshooting > Picture ("No picture from connected equipment", antenna) | https://helpguide.sony.net/gbmig/ADL81001/egb/1a/tspicture_restaep_1a_2btdr.html |
| **SONY-POFF** | Sony Help Guides: Eco > Power Saving "[Picture Off]" and Power > "Picture off" ("press any key on the remote") | https://helpguide.sony.net/gbmig/14HA2671/v1/uen/c_eco_backlight.html , https://helpguide.sony.net/tv/iaepm1/v1/en/3_4_power_settings.html |

Not reachable from here (HTTP 403 / timeouts): sony.co.in / sony.co.uk support articles, LG US help library,
Xiaomi (mi.com) support answers. Nothing in the entries depends on them.

---

## television_no_power (diy)

| Line | Agreeing sources |
|---|---|
| Safety: dry hands, pull by the plug not the cord | SS-UG ("always pull on the power cord's plug… Do not touch the power cord with wet hands"), LG-OM ("grasp the plug… do not touch the TV with wet hands") |
| 1. Look at the standby light (on / off / blinking) | SS-IN (red standby light; on / off / blinking branches), LG-SG step 1 (light at the bottom of the TV), TCL-PWR (look for standby lights), SS-UG (sensor glowing solid red) |
| 2. Press the power button on the TV itself | SS-IN step 2, LG-SG step 5, TCL-PWR step 4, SONY-PWR step 2, SS-UG. Location differs (under logo / bottom / back / side) → caution says so. "If it turns on, it's the remote": SS-IN, LG-SG, SONY-PWR |
| 3. Cord firmly in at the TV and the wall | LG-SG step 2 (back of TV and wall outlet), TCL-PWR (both ends), SS-UG. Damaged cord → don't use: SS-IN, LG-OM, TCL-PWR (caution) |
| 4. Test the socket with a lamp / charger | SS-IN (lamp), LG-SG step 4, TCL-PWR, LG-OM p.19. Power strip → plug straight into the wall (caution): SS-IN (no surge protector), TCL-PWR |
| 5. Unplug, wait two minutes, plug in, press power | SS-IN 30 s, TCL-PWR ≥ 60 s, SONY-PWR 2 min, LG-SG (reconnect). Two minutes satisfies all three. Caution "up to twenty seconds to start": SONY-PWR hint (10-20 s) |
| 6. Light stays off or blinks → technician | SS-IN (no light after a working outlet → service; blinking → service), TCL-PWR (no lights; standby light flashing → contact), LG-SG step 6 |
| escalate_if | light blinking/flashing (SS-IN, TCL-PWR), burning smell / sparks (TCL-PWR, LG-OM), cord damaged (SS-IN, LG-OM) |

Dropped: TCL's "hold the TV's power button 15 s while unplugged" (one brand); Samsung One Connect box checks
(model-specific); LG "enable the power indicator in settings" (model-specific); circuit breaker reset (LG only, and
it's household wiring).

## television_sound_no_picture (diy)

| Line | Agreeing sources |
|---|---|
| Safety 1: never open the back, dangerous voltage | LG-OM ("DO NOT REMOVE COVER (OR BACK). NO USER-SERVICEABLE PARTS INSIDE"), SS-UG (same + "Only a qualified technician should open this apparatus") |
| Safety 2: switch TV and box off before touching cables | LG-UK ("Before reconnecting any cables, turn off the TV and the connected device"), TCL-NPS (turn off TV and device, unplug both) |
| 1. Press Home/Menu; a menu means the screen works | LG-UK step 1 + LG-SG FAQ (volume indicator), SS-BLK step 1 (Home/Menu), TCL-NPS / TCL-ROKU (Home), SS-AE. Caution (picture-off setting wakes on a button): SONY-POFF ("press any key"), SS-POFF ("any button except volume and power") |
| 2. Input / Source → the box's input | SS-BLK, TCL-NPS, TCL-ROKU step 3, SONY-PIC |
| 3. Box / device switched on | SS-BLK, TCL-ROKU step 4, SONY-PIC ("Turn the connected equipment on"), LG-UK |
| 4. HDMI out at both ends, back in firmly (devices off) | LG-UK step 2, SS-BLK, TCL-ROKU step 4, TCL-NPS. Caution (other cable / port): LG-UK, TCL-ROKU, SS-HDMI |
| 5. Unplug TV and box for a minute | TCL-NPS (60 s), TCL-ROKU step 6, SS-BLK (power cycle), LG-UK step 3 (set-top box power reset) |
| 6. No menu ever / still black → technician | LG-UK ("If the volume indicator does not appear… contact LG service"), SS-BLK step 5, TCL-ROKU ("produces sound but never displays a picture") |

Dropped: **flashlight / backlight test.** TCL-ROKU gives a careful version ("darken the room, torch at an angle,
don't press it on the screen; a faint image may mean a backlight fault and may need service"), and TCL-FIRE repeats it
as a tip. The LG US "No Video/No Screen" article that also mentions it could not be opened (403), and Samsung's
official pages don't have it (only its community forum). One official brand → not a step. Its outcome is "call a
technician" either way, which step 6 already says. Also dropped: HDMI cable self-test menus and MyASUS-style
diagnostics (menu paths differ per brand), set-top box resolution button (LG CA, one brand).

## television_no_signal (diy)

| Line | Agreeing sources |
|---|---|
| Safety: TV and box off before touching cables | LG-UK, TCL-NPS |
| 1. Input / Source → the device's input | SS-HDMI step 1, SS-BLK ("cable box on HDMI 1 → set source to HDMI 1", used for the caution), TCL-FIRE No Signal, SONY-PIC, LG-SG FAQ |
| 2. Box / device switched on | SS-HDMI step 2, SS-BLK, SONY-PIC, LG-UK |
| 3. HDMI out at both ends, back in firmly | SS-HDMI step 3, TCL-FIRE ("unplug/replug both ends"), LG-UK step 2. Caution (another port / cable, then pick that input): SS-HDMI steps 5-6, TCL-FIRE, LG-UK |
| 4. Antenna / cable wire firmly in | SONY-PIC ("Check the antenna (aerial)/cable connection"), TCL-NPS (reconnect coaxial / antenna cables), LG-UK step 4 (antenna line) |
| 5. Box off at the wall 30 s, back on | SS-HDMI step 4 (unplug TV and devices "for at least 10 seconds"), SS-BLK (power-cycle the device), LG-UK step 3 (box off, wait 5 s). 30 s satisfies both. "Give it a minute to start": LG CA note on cable boxes booting (caution only) |
| 6. Still no signal → provider or technician | Samsung (contact the set-top box provider), LG-UK (ask the cable provider / property office to inspect the line; else LG service), TCL |

No `brand_codes`: "No Signal" is a message, not a code.

## television_remote_not_working (diy)

| Line | Agreeing sources |
|---|---|
| Safety: batteries away from children, don't mix old and new | LG-OM ("Store the accessories (battery, etc.) out of the reach of children"; "Do not mix new batteries with old batteries"), SS-UG ("Store the accessories (remote control, batteries…) out of reach of children"; replace only with the same type) |
| 1. Press power on the TV itself | SONY-RC, SS-RC step 5, TCL-RC step 6, LG-SG step 5 |
| 2. Clear the space to the sensor | SONY-RC ("Keep the remote control sensor area clear"), SS-RC step 2, TCL-RC step 2 (soundbar example → caution), LG-OM p.19 |
| 3. Batteries out, hold power 8 s, fit new ones | SS-RC ("remove the batteries, then hold Power for eight seconds"), SONY-RC ("Remove the batteries, press power three seconds, install new batteries"), TCL-RC, LG-SG. 8 s covers both. Polarity caution: SONY-RC, TCL-RC, SS-UG, LG-OM |
| 4. Point straight at the TV's bottom front | SS-RC (point directly at the logo), SONY-RC (sensor at the front), TCL-RC (point at the front), SS-UG (sensor at the bottom) |
| 5. Phone-camera test: flashing light at the tip | LG-SG step 3 ("point it at an Android phone's camera… flashing light"; "may not work on iPhones" → caution), SS-RC (phone camera, coloured light), TCL-RC (flashing purple/white/pink light at the tip) |
| 6. No light → new remote; light but TV ignores it → technician | SS-RC (no IR light with fresh batteries → replacement), TCL-RC ("the remote may need to be replaced"), LG-SG step 5 ("remote control or the remote sensor") |

Dropped: Bluetooth / Smart Remote pairing (button combos differ per brand), Sony's fluorescent-light interference
(one brand), USB-C / solar remote charging (Samsung only).

Demo note: step 5 uses FixLens's own camera; an IR LED often shows as a purple dot on phone cameras.

## television_damage_or_burning_smell (call_technician)

Spoken `meaning` + the technician line; no steps.

| Line | Agreeing sources |
|---|---|
| Don't open the back; dangerous voltage | LG-OM and SS-UG caution labels ("DO NOT REMOVE COVER (OR BACK)… REFER SERVICING TO QUALIFIED SERVICE PERSONNEL"; "dangerous voltage") |
| Smoke / burning smell / sparks → unplug | TCL-ROKU ("Turn off and unplug the TV immediately if you notice smoke, sparks, a burning smell, excessive heat, a damaged power cord, cracked or physically damaged screen components"), TCL-PWR, LG-OM ("If you smell smoke or other odors… unplug the product immediately and contact customer service"; also water inside, product damaged), SS-UG ("unusual sounds or smells → unplug it immediately and contact… service") |
| Lines on screen → service | TCL-ROKU ("The screen flashes, shows lines, or displays only part of an image" → contact support). Samsung and LG first run a built-in picture test; the menu path differs by brand and year, so we escalate directly (more conservative) |
| "Replace the backlight" / "open the TV" aliases | Principle 5 in AUTHORING.md: opening a TV is technician-only |

## Safety context not used as steps

- CPSC Anchor It (https://www.cpsc.gov/Safety-Education/Safety-Education-Centers/AnchorItgov) and SS-UG / LG-OM: anchor
  TVs, don't let children climb, keep remotes off TV stands. Not a fault, so not an entry; worth a Fixy follow-up.
- LG-OM: a "cracking" noise when the TV warms or cools is plastic contracting and is normal.

## Licence notes

All OEM pages and manuals are "all rights reserved". We keep facts and order, write our own sentences (≤ 20 words),
and cite. Nothing is copied into the APK beyond our paraphrase. CPSC material is US-government public domain.
