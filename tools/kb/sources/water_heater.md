# Sources: electric and gas water heaters (`water_heater`)

Checked 2026-09-27. Generic entries for Indian storage geysers (and one gas-geyser escalation), combined from official
manuals. "p.N" is the page index in the PDF file, not the printed page number, unless it says "printed".

## Documents used

| Short name | Document | URL | Notes |
|---|---|---|---|
| Racold CDR | Racold (Ariston Group India) Electric Storage Water Heater instruction manual, CDR Dlx Plus / Dlx / Swift / PR / CDP / Buono Pro, dated 24-04-2026 | https://www.racold.com/content/dam/racold/product-manuals/cdr-and-buono-pro-24-04-2026.pdf | Linked from https://www.racold.com/product-manuals. Has a full troubleshooting table (p.8) |
| Racold Eterno | Racold Eterno 2 NXT instruction manual, 16-10-2025 | https://www.racold.com/content/dam/racold/product-manuals/eterno-2nxt-16-10-2025.pdf | Same troubleshooting table as CDR (p.8); lamp meanings p.5 |
| Racold gas | Racold instantaneous gas water heater manual (B/W), 24-04-2026 | https://www.racold.com/content/dam/racold/product-manuals/gas-manual-black-and-white-24-04-2026.pdf | Only ventilation advice is readable (p.6); no troubleshooting table |
| Havells Adonia R | Havells Adonia R 10/15/25 L operating instructions | https://havells.com/media/wysiwyg/Manuals/Adonia_R_IM_Black_and_White.pdf | No troubleshooting table; operation, Do's and Don'ts |
| Havells Monza | Havells Monza Slim i 15/25 L operating instructions (Wi-Fi model) | https://havells.com/media/wysiwyg/Manuals/Monza_Slim_i_IM.pdf | curl got HTTP 406 from havells.com; read through the web-fetch tool's saved copy. Error codes p.20, Do's/Don'ts p.31 |
| Havells Flagro | Havells Flagro instantaneous gas water heater operating instructions | https://havells.com/media/wysiwyg/Manuals/Flagro_IM.pdf | Read the same way as Monza. Gas safety p.9-11 |
| V-Guard Divino | V-Guard Divino / Divino DG storage water heater operating manual | https://cdn.shopify.com/s/files/1/0793/9109/7147/files/DIVINO-ONLINE_UM_1.pdf | Linked from https://vguard.com/pages/water-heater-manual. Troubleshooting p.7 |
| AO Smith Finesse | A. O. Smith India Finesse SDS/HS storage water heater user manual (06-08-2021) | https://www.aosmithindia.com/wp-content/uploads/2021/09/Finesse-SDS_HS_UM_CTC_06-08-2021.pdf | Printed as two A3 sheets (8 A5 pages per sheet, some upside down); cited by **printed** page. Troubleshooting printed p.13, safety-valve drip note printed p.5, knob and lamps printed p.10-11. Read from rendered page images because text extraction scrambles the table |
| PMUY | Pradhan Mantri Ujjwala Yojana (MoPNG), "Consumer Guidance" LPG safety leaflet | https://pmuy.gov.in/docs/Consumer_Guidance.pdf | Public agency (Govt. of India). "If you smell gas" p.3 |
| CPSC 513 | US Consumer Product Safety Commission, Home Electrical Safety Checklist, Pub 513 (2008) | https://www.govinfo.gov/content/pkg/GOVPUB-Y3_C76_3-PURL-LPS108849/pdf/GOVPUB-Y3_C76_3-PURL-LPS108849.pdf | Public agency, public domain. p.5: any shock from an appliance: don't touch it, turn the power off at the breaker, electrician |

### Tried but not usable

- **Bajaj, Crompton**: no official PDF found; only ManualsLib / manuals.plus / Scribd mirrors, which return HTTP 403 here. Not used.
- **Racold Andris** (older racold.com/themes/... link): 404.
- **Other Havells manuals** (Adonia i, Orizzonte, Magnatron): HTTP 406 to scripted downloads; not needed.
- **V-Guard troubleshooting blogs, HomeFixMagic**: aggregator content, not used.

---

## water_heater_no_hot_water (diy)

| Line | Agreeing sources |
|---|---|
| Safety: don't open the cover or touch wiring; only switch, knob, valve | Racold CDR p.7 (mains cord and electrical parts by authorised service only), p.9 (don't tamper thermostat, safety valve); Havells Adonia p.11 (switch off and unplug before the inspection cover is opened, i.e. service work), p.14 (safety devices must not be tampered with); V-Guard p.6 (tampering with thermostat, cut-out, safety valve is hazardous) |
| 1 Wall switch on | V-Guard p.7 ("ensure that the power supply is switched ON"); AO Smith printed p.13 (lamps off: check power supply, switch ON the electrical switch); Racold CDR p.13 (switch on the mains supply, red lamp comes on) |
| 2 Power light should glow | Racold CDR p.3, p.13 and Eterno p.5 (lamps come on at switch-on); AO Smith printed p.10 (power indicator lamp glows); V-Guard p.2 (red lamp = mains on) |
| 3 No light at all: don't reset anything, call a technician | Havells Monza p.31 ("in case both the lamps do not glow, do not start the unit by resetting the thermal cutout", contact customer care); AO Smith printed p.13 (lamps off with power on: thermal cut-out tripped, call customer care); Racold Eterno p.5 (blue lamp off = thermal cut-out operated, contact service); Racold CDR p.4 (cut-out: contact authorised service only) |
| 4 Cold-water inlet valve fully open | Racold CDR p.8 (no hot water: "inlet valve closed", open the inlet valve); V-Guard p.7 ("ensure water supply to the water heater"); Havells Adonia p.12 (always keep the inlet valve open) |
| 5 Temperature knob clockwise to a warmer setting | AO Smith printed p.11 (knob adjusts 25-75 °C clockwise, maximum for most hot water); Racold CDR p.3 (knob "clockwise incremental") and p.8 ("thermostat low setting" as a no-hot-water cause); Havells Adonia p.12 (raising the setting gives hotter water); Racold Eterno p.5 (set the knob to the bathing need) |
| 5 Caution: outside knob only, not past its last mark | V-Guard p.6 (never raise the thermostat beyond the preset); Racold CDR p.9 (don't tamper with thermostat settings) |
| 6 Give it half an hour to an hour | V-Guard p.5 (switch on at least 30 minutes before use) and p.7 ("it may take a few minutes"); Havells Adonia p.12 (switch on at least an hour before use). Worded as a range because the two differ |
| 7 Still no hot water or switch keeps tripping: technician | V-Guard p.7 (no hot water, mains tripping: if it continues, contact service); AO Smith printed p.13 (lamps on but no hot water: call customer care); Racold CDR p.8 (cut-out trip, element) |

- **Dropped:** "reset the cut out" (Racold table p.8) because it is a service-technician action (the cut-out is inside the
  cover, and Havells says not to reset it). The Havells shock-safe plug test/reset button (Adonia p.7, 11) is Havells only.
  Low-voltage checks and descaling (Racold, AO Smith, V-Guard) need a meter or a technician. The indicator colours differ
  by brand (Racold red/orange or red/blue, V-Guard red/green, Havells blue/amber LED ring), so no colour is named.
- **Escalation:** "keeps tripping" from V-Guard p.7; "burning smell", "electric shock", "smoke coming out" route to the
  danger entry below; "water leaking" to AO Smith printed p.13 (leak: switch off, call).

## water_heater_leaking (caution)

| Line | Agreeing sources |
|---|---|
| Safety: switch off at the wall; if that switch is wet, use the main switch | AO Smith printed p.13 (water leakage from product: "switch OFF power immediately"); Havells Adonia p.14 (any abnormality: switch off the main power supply); CPSC 513 p.5 (shock hazard: turn the power off at the circuit breaker) |
| 1 Look where it comes from (valve, joint, tank) | AO Smith printed p.13 (separate rows for piping joints, product leak, safety-valve drip); Racold CDR p.8 ("check from where the leakage is": tank, collar, gasket, heater plate; MFSV) |
| 2 A few drops from the safety valve while heating are normal | AO Smith printed p.5 (safety valve "may drip during the usage of the heater" as heated water expands); Havells Adonia p.7 (the valve lets the expansion of heating water flow out through the drain); Racold CDR p.4, p.8 (MFSV dripping protects the tank from excess pressure; fit a drain pipe); V-Guard p.3 (valve releases water through its outlet above 0.8 MPa) |
| 3 Never block, plug or tighten the valve | AO Smith printed p.5 (never remove or block it to stop dripping); V-Guard p.3 (outlet must never be blocked) and p.6 (sealed screw must not be tampered with); Racold CDR p.9-10 (don't adjust, don't over-tighten); Havells Adonia p.14 |
| 4 Drips all the time, not just heating: pressure may be high, call a plumber | Racold CDR p.10 ("continuous water dripping ... indication of excessive pressure at inlet"); V-Guard p.3, p.7 (inlet pressure above 0.8 MPa or tank too high: regulate; else service); AO Smith printed p.13 (dripping during heating: non-return valve too close to the inlet; else call) |
| 5 Leak from the tank or joints: keep it off, technician | AO Smith printed p.13 ("leakage in tank or other parts: switch OFF power immediately and call"); Racold CDR p.8 (leakage of the unit: repair/replace by service); Havells Adonia p.14 |

- **Dropped / not named:** the actual fix for high pressure, because the brands **disagree**: Racold p.8/10 and V-Guard p.3
  say fit a pressure-reducing valve, AO Smith says move the non-return valve 40 ft away, and Havells p.14 says "never
  install a pressure reducer valve at the inlet". So the step only says "call a plumber". Also dropped: AO Smith's
  "re-connect piping using Teflon tape" (AO Smith only, plumber work) and draining the tank (Havells, V-Guard; not needed
  to stop a leak and involves the drain lever under pressure).
- **Escalation:** "socket is wet" (water near power), "keeps tripping", "burning smell", "electric shock".

## water_heater_electric_danger (call_technician)

- **Shock from taps or the geyser:** V-Guard p.7 ("Shock from water heater: immediately contact the nearest V-Guard
  service centre"); Racold CDR p.8 (mild shock = improper earthing, to be corrected); CPSC 513 p.5 (any shock: don't
  touch the appliance, turn off at the breaker, electrician).
- **Burning smell, sparks, smoke, any abnormality:** Havells Adonia p.14 and Monza p.31 ("in case you observe any
  abnormality of operation, immediately switch OFF the main power supply ... contact the nearest customer care");
  Racold CDR p.8 (unit burnt: short circuit in harness/cord, technician).
- **Water too hot / steam:** AO Smith printed p.13 (water temperature too high: thermostat or cut-out fault, call
  customer care); V-Guard p.2 (cut-out operates at 95 °C only when something has failed).
- **Opening the geyser, wiring, element, resetting the cut-out:** FixLens rule (AUTHORING.md principle 5); Racold CDR p.4,
  p.7; Havells Monza p.31.
- **Meaning** ("switch off at the main switch"): Havells "main power supply", CPSC "at the circuit breaker", so a person
  who may be getting shocks doesn't touch the geyser's own switch.
- **brand_codes:** Havells Monza p.20 lists app/LED error codes: E2 = water reached 90 °C, E5 = dry heating detected (E3/E4
  are sensor faults and were left out). V-Guard p.7 (Divino DG only): E4 = over-temperature error, contact service
  (E1/E2 sensor faults left out). Only the overheating codes are listed so the entry's title stays true.

## water_heater_gas_smell (call_technician)

- **Close the gas, open windows, no switches or flames:** Havells Flagro p.9-10 ("in case of gas leakage, shut off the gas
  supply and open the windows immediately"; ignition and switching electric power on/off prohibited); PMUY p.3 (if you
  smell gas: extinguish all flames, don't light a match, regulator off, open doors and windows, don't operate electrical
  switches, call the distributor).
- **Yellow flame, black smoke, flame not steady, flame on after the tap is closed:** Havells Flagro p.10-11 (stop using,
  turn off the gas valve, contact the service centre or gas dealer).
- **Ventilation:** Racold gas p.6 (install outside the bathroom or keep windows open / exhaust fan on).
- One manufacturer plus one public agency (consensus rule). Gas geysers are technician-only by the task brief; no user
  steps are given.
- Matching note: "gas geyser not working" deliberately ties with the electric entry's "geyser not working", so a gas
  geyser never gets the electric steps (tie = no match, Fixy answers without steps).

## Licence notes

All manuals are © their makers (all rights reserved). Facts and order only; every spoken line is our own wording; no
text or figures are shipped. PMUY and CPSC documents are government publications (CPSC: US public domain).
