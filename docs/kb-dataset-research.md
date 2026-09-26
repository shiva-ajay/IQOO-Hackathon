# FixLens — KB Dataset Research (diagnostics round)

Research date: 2026-09-27. Extends [kb-collection-plan.md](kb-collection-plan.md) (sources, licences, entry list).
Question asked: is there a dataset that gives us diagnostic flows (symptom → checks → cause), alternative checks,
edge cases and a final "not serviceable, contact a technician", for our two demo targets?

---

## 0. Scope decision (2026-09-27): generic, not brand- or model-specific

The KB covers **appliance and vehicle categories** (washing machine, AC, fridge, TV, bike, car…), not one brand or car.
The brand manuals below stay as **sources**; an entry is written from what **several** of them agree on.

- **Entries are symptom-first** ("washer won't drain", "AC not cooling", "bike won't start"), because symptoms are the
  same across brands and codes aren't.
- **Consensus rule:** a step goes in only if ≥ 2 official manuals (different brands) give it, or one manual plus a
  public-agency guide. Anything that differs by model becomes "check your manual" or escalation. Cite every source.
- **Error codes become a secondary way in:** a generic entry lists the brands' codes that mean the same fault
  (washer drain: LG OE, Samsung 5C/5E, Bosch E18, IFB dPEr). Code + known brand → that entry; a code that means
  different things on different brands → Fixy asks the brand (or the VLM reads the logo). ApplianceDB's `component` /
  `cause_category` fields do most of this grouping.
- **Targets are generic descriptions** that hold for most models ("small flap at the bottom front corner of the
  washing machine", "the removable filter behind the front panel of the indoor AC unit").
- **Retrieval needs the category.** "Not cooling" is AC or fridge; "won't start" is car or bike. The category comes from
  the user's words, the session, or one VLM side request on the keyframe ("which of: washing machine, AC, …?").
- **More conservative safety:** generic advice can't know the model, so it escalates earlier than a brand manual would.

---

## 1. Verdict

**No public dataset has verified diagnostic trees for front-load washers or engine-bay checks.** Every candidate is
one of: repair/replacement only (MyFixit), unverified crowd text (Stack Exchange, iFixit, Reddit), synthetic
(several Hugging Face sets), or code → meaning with 1–2 lines of fix (ApplianceDB, OBDex).

The closest thing to a verified diagnostic flow is the **manufacturer's own troubleshooting table**: each code lists
its checks in order and says where the user stops and service starts. So the KB stays hand-built from official
manuals, with open datasets used as seeds and cross-checks.

---

## 2. Primary sources (official, text-extractable, India market)

### Washer: Samsung WW70T4020EE (recommended)
- User manual (Samsung India download centre):
  https://org.downloadcenter.samsung.com/downloadfile/ContentsFile.aspx?CDSite=UNI_IN&OriginYN=N&ModelType=N&ModelName=WW70T4020EE%2FTL&CttFileID=8370780&CDCttType=UM&VPath=UM%2F202111%2F20211122181531843%2FWW4000T-MD_LED_DC68-04288A-01_EN.pdf
- "Information codes" table, pp. 55–57: code → ordered actions → "if the problem remains, contact service".
  Plus step-by-step **debris filter** and **emergency drain** procedures with safety lines (unplug; drained water is hot).
- Companion page: https://www.samsung.com/in/support/home-appliances/how-to-resolve-4e-or-5e-codes-on-your-samsung-washing-machine/
  ("allow approximately one hour for the water to cool").
- Pitch note: the same manual describes Samsung **Smart Check** (their app reads the display code with the camera).
  Judges may know it. Our difference: offline, voice, points at the part, tracks it, and walks you through the fix.

| id (proposed) | Code | Severity | Diagnostic flow from the manual (ordered checks) | Main target |
|---|---|---|---|---|
| `samsung_fl_4c_water_supply` | 4C | diy | Tap open → hose not kinked → clean the inlet mesh filter → still on: service | tap / inlet hose at the back |
| `samsung_fl_5c_drain` | 5C | diy | Drain hose position/kinks → clean the debris filter (emergency drain first) → still on: service | debris filter cover, bottom front |
| `samsung_fl_dc_door` | dC | diy | Close the door firmly → clothes caught in the door → still on: service | door edge and gasket |
| `samsung_fl_ub_unbalanced` | Ub | diy | Redistribute the load → too few or too bulky items → level the machine | door / drum |
| `samsung_fl_lc_drain_hose` | LC / LC1 | diy | Drain hose end not on the floor → correct height → still on: service | drain hose at the back |
| `samsung_fl_oc_overflow` | OC | caution | Restart once → still on: service (`escalate_if`: water on the floor) | none |
| `samsung_fl_3c_motor` | 3C | call_technician | none | none |
| `samsung_fl_hc_heater` | HC | call_technician | none | none |

(Also in the manual as call-service codes, if more are needed: UC voltage, 1C water level sensor, AC/AC6, 8C, DC1/DC3.)

### Washer alternatives
| Brand | Source | For | Against |
|---|---|---|---|
| **IFB** Senator Plus | https://imagestore.ifbhub.com/Adobe/Manuals/bis/FL_752_H_Digital.pdf (pp. 73–76; Serena: `.../FL_780_D.pdf`) | Indian brand story, full text, filter location ("bottom door, open with a coin") | Word codes `dPEr`, `tAP`, `UnbL` are hard to say and hard for Moonshine to transcribe |
| **LG** | UAE help library (the only one that renders as text): https://www.lg.com/ae/support/product-help/CT20076046-20153792986245 | "OE" is the best-known code; market leader | India page is JS-only; the manual shows codes as images, so there's no India document to check the codes against |
| **Bosch** | https://www.bosch-home.in/service/get-support/e18-in-display | Very good E18 drain procedure with hot-water warnings | Only one code page; no India manual PDF found |
| Whirlpool | — | — | No official India source found |

### Car: Maruti Suzuki Swift (2024+), owner's manual 99011M75T08-74W (recommended primary)
- https://marutisuzuki.scene7.com/is/content/maruti/Swift_99011M75T08-74Wpdf (535 pp.; the older blob-storage links now return 403).
  The same feed has Dzire (`Dzire-99011M55U06-74Wpdf`) and Brezza (`Brezza_Petrolpdf`) for cross-checks.
- Ch. 9 Inspection and maintenance: oil level p. 9-8, coolant 9-9/9-10, air cleaner 9-11, brake fluid 9-13,
  battery + corrosion cleaning 9-20, engine-bay fuses 9-22, washer fluid 9-40. Each has its WARNING boxes and says
  which jobs are "authorized workshop only".
- **Ch. 4 warning lights (pp. 4-36 to 4-41)**: already in "what to do / when to stop" form. This is the diagnostic
  layer we were missing. Oil pressure: pull off, stop the engine, check the level; if the level is fine, get it
  inspected before driving. Charge light: workshop. Coolant temp: blinking = hot, steady = overheating.
  Check-engine blinking: stop at once. Airbag / power steering: workshop.
- **Ch. 10 "Engine overheating" (p. 10-17)**: a numbered diagnostic procedure (AC off, park, idle, check the
  reservoir, look for leaks) with its own warnings (no bonnet while steaming, no radiator cap while hot, fans).

### Car: supporting manuals
| Manual | URL | Use it for |
|---|---|---|
| Honda City 5th gen (2022) | https://www.hondacarindia.com/ownersmanual/webom/eng/city%20-%205th%20generation/city%205th%20generation%20-%202022.pdf | **Target phrases.** The under-bonnet diagram (p. 263) names colours: orange dipstick, blue washer cap, black brake-fluid cap. "Handling the unexpected" splits every warning light into *what to do now / after parking*, the best template for safety + `escalate_if` |
| Hyundai Creta (2019) | https://www.hyundai.com/content/dam/hyundai/in/en/data/connect-to-service/owners-manual/creta.pdf | Second opinion where brands differ. Hyundai checks oil warm-then-off, Maruti cold or 5 min after stopping |
| Tata Nexon (2022) | https://tmlcars.tatamotors.com/images/service/owners/owners-manual/pdf/nexon/nexon-owner-manual-2022.pdf | Unverified (the connection timed out) |

**The demo car decides the target phrases.** Dipstick and cap colours differ by maker (Maruti yellow ring, Honda
orange). Identify the parking-lot car first and pull that model's manual.

---

## 3. Open datasets: what each is good for

| Dataset | What it is | Licence | Use now | Use later |
|---|---|---|---|---|
| **ApplianceDB** ([GitHub](https://github.com/ApplianceDB/ApplianceDB-public), [HF](https://huggingface.co/datasets/Ichlibitiche/appliancedb-error-codes-repair-database)) | 438 codes (250 washer, 13 brands; Samsung 42, LG 14), 288 ranked fixes, `severity`, `diy_difficulty` (incl. `professional_only`), `source_url` per row. US/UK only | **ODbL 1.0** (attribution + share-alike) | Cross-check meaning and severity; `professional_only` → `call_technician` | Structured code table for more brands |
| **Stack Exchange** mechanics (28.5k Q) + diy (93k Q), [archive.org dump](https://archive.org/details/stackexchange) | Real diagnostic reasoning and edge cases ("oil level rising = coolant in oil"). Tags: coolant 578, oil 809, battery 1,434, washing-machine 635 | CC BY-SA (the post-2024 official dump carries a no-LLM-training agreement; archive.org copies predate it) | Mine top-voted accepted answers for **edge cases and `escalate_if` wording**; question titles → `aliases` | Best open corpus for hybrid retrieval and eval |
| **iFixit troubleshooting pages** (e.g. [Samsung 4E/4C](https://www.ifixit.com/Guide/How+to+Troubleshoot+the+4E+or+4C+Error+Code+on+a+Samsung+Front+Load+Washing+Machine/207459)) | Ordered check sequences, crowd-edited | CC BY-NC-SA 3.0; **ToS bans ML training** | Human reference to paraphrase, with attribution | Not for fine-tuning without a licence |
| MyFixit | 31.6k iFixit repair/replacement guides | CC BY-NC-SA | No (no diagnostics; see earlier review) | Retrieval only, not training |
| OBDex | 9,533 OBD-II codes, CC0 | CC0 | Only for a "check engine light → technician" entry | Scanner add-on |
| RepairBench ([arXiv 2606.03331](https://arxiv.org/html/2606.03331)) | 991 expert-verified repair QAs with diagnosis + safety (phones/PCs) | No public release found | No (wrong domain) | Copy its 4-part scoring (correctness, completeness, practicality, **safety**) for our eval |
| HoloAssist, IndustReal, Ego-Exo4D | Egocentric procedural video with mistakes | CDLA-P-2.0 / Apache-2.0 / signed licence | No | Step and mistake grounding, fine-tuning |
| Roboflow engine-bay sets | Images labelled dipstick, oil cap, coolant reservoir, battery | Varies per project | Test photos for grounding checks | — |
| **Avoid:** `dipenbhuva/home-diy-repair-qa`, `CJJones/Vehicle_Diagnostics_*` (synthetic), `sanjayram-a/ifixit-repair-chatml` (no licence, breaks iFixit ToS), Falcon-98 washer codes (unverified, no licence) | | | | |

---

## 4. How the diagnostic behaviour fits our schema

The flows in the manuals are short and linear ("check A → check B → still on: service"), so the current schema can
already hold them:
- **Ordered checks** → `steps[]`, cheapest and safest first, each with a `target`.
- **"Still showing the code?"** → a final step that says so, plus `escalate_if` phrases ("code comes back",
  "water on the floor", "burning smell") that jump to `Escalate`.
- **Edge cases / other ways to check** → `caution` on the step, or extra `escalate_if` lines (mined from Stack Exchange).
- **Not serviceable** → `severity: call_technician` (3C, HC, airbag light, wiring).

For follow-up questions ("is there another way to check?") to use the KB, the current entry must be in the prompt as
`<kb_entry>` (CLAUDE.md §8). Today it isn't, so follow-ups come from the VLM's own knowledge.

---

## 5. Licensing (short)

All OEM manuals are "all rights reserved". Approach for the non-commercial demo: keep the facts and order, write each
`say` line in our own words (≤ 20 words), cite manual + page in `source`, and never ship PDF text or figures in the
APK. ODbL (ApplianceDB) and CC BY-SA (Stack Exchange) need attribution and share-alike. This is a judgement call,
not legal advice.

---

## 6. Generic category plan (proposed)

Sources per category: the manuals of 2–3 market leaders in India (consensus rule, §0), public-agency guides for
maintenance and safety (US DOE Energy Saver, ENERGY STAR, CPSC: public domain), iFixit category troubleshooting pages
and Stack Exchange for edge cases (paraphrase only). Sources still to be collected per category: not yet researched.

| Category | Generic entries (symptom-first) | Always `call_technician` |
|---|---|---|
| Washing machine | won't drain · won't fill · door won't open/lock · shakes (unbalanced) · won't start · bad smell | motor, heater, PCB, water on the floor near power |
| Split AC | not cooling (filter, mode/temp, outdoor unit blocked) · water dripping indoors · remote not working · bad smell | gas/refrigerant, compressor, outdoor electrics |
| Refrigerator | not cooling (setting, door seal, space behind) · water inside · ice build-up · noisy | gas, compressor, sealed system |
| TV | no power · sound but no picture · no signal (input, cables, set-top box) · remote not working | opening the back, anything inside |
| Geyser | no hot water (switch, setting) | leaks, gas geysers, wiring |
| Car (engine bay) | oil · coolant · washer fluid · battery terminals · air filter · warning lights (oil, temperature, battery, check-engine) | wiring, airbags, brakes beyond a level check |
| Bike / scooter | oil level (window or dipstick) · chain slack and lube · won't start (kill switch, side stand, fuel, choke) · battery terminals | brakes beyond a level check, electrics |

~6–8 categories × 3–5 entries ≈ 30–40 entries. At ~15 min each plus a grounding check on real photos, that is
~8–10 h, so the two live demos (car, washer printout) are done in depth first and the rest get 2–3 entries each.

## 7. Decisions needed

1. Which categories are in (table above), and how many entries per category.
2. Whether error codes stay in as a secondary way in (a `brand_codes` field and "which brand is it?"), or entries are
   symptom-only for now.
3. How the category is known: user's words + session only, or also a VLM side request on the keyframe.
4. Whether to add `<kb_entry>` to the prompt during a guide (a small change; costs a KV-cache rebuild at guide start).
5. CLAUDE.md §3 ("keep it narrow", brand-specific washer) and §7 need updating to match; also the pitch's "E4".
