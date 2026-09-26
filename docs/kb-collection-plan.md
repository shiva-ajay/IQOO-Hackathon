# FixLens — Knowledge Base Collection Plan

Goal: build `app/src/main/assets/kb/fixlens_kb.json`, a **small, verified, spoken-friendly**
knowledge base that Fixy reads from word for word. Schema and retrieval rules: CLAUDE.md §7.
Research date: 2026-09-26.

---

## 1. Principles (these decide everything below)

1. **Curate; don't bulk-import.** Steps are spoken to the user exactly as written (CLAUDE.md §7).
   So every line has to be checked by a human. Datasets are **sources to read and cross-check**.
   They are not files to dump into the app.
2. **Official source first.** Use the manufacturer's owner's manual or the brand's own support page.
   Use a second source only to cross-check. Never use an aggregator blog as the only source.
3. **Exact codes, per brand.** Error codes differ between brands, and sometimes between models of
   the same brand. One washer brand and one AC brand. No "universal" code tables.
4. **User-serviceable only.** Anything involving mains wiring, refrigerant, sealed systems, PCBs,
   airbags, fuel lines or brakes beyond a fluid-level check gets `call_technician`.
5. **Written for the ear.** Each step is one action in ≤ 20 words, with no tables, no parentheses
   and no model numbers read aloud.
6. **Traceable.** Every entry records its source (URL or manual + page), licence, and the date it was checked.

Why not a vector index over all of iFixit / all manuals: see CLAUDE.md §7. Exact codes, safety,
and 10–40 entries make exact lookup + aliases the right tool. A bigger corpus is post-hackathon.

---

## 2. Scope tiers

| Tier | Domain | Entries | When | Why |
|---|---|---|---|---|
| **A (demo)** | Car engine bay (generic, checked against the **actual demo car's** manual) | 7–8 | Now | Live parking-lot demo |
| **A (demo)** | Front-load washer, **one brand** | 7–9 (codes + 1–2 routine) | Now | Judges'-table demo on the A3 printout |
| B (stretch) | Two-wheeler (one scooter + one motorcycle, Indian brands) | 5–6 | Only after M4 works end to end | Shows range; many Indian users |
| B (stretch) | Split AC, **one brand** | 5–7 | Only after M4 works | Pitch mentions AC |
| C (post-hackathon) | Fridge, geyser, water purifier, inverter, router | — | Grand Finale roadmap | CLAUDE.md §13 / STACK.md §9 |

CLAUDE.md §3 keeps the demo narrow on purpose. Only start a Tier B entry once its grounding targets
have been checked on real photos (M0/M1 method). An entry that the VLM can't point at is worse than no entry.

---

## 3. Sources by domain

### 3.1 Car (engine bay)

| Source | What it gives | Licence / use | Use for |
|---|---|---|---|
| **Owner's manual of the demo car** (e.g. Maruti Suzuki PDFs hosted on Maruti's own storage; the Maruti Suzuki Rewards app has the digital manual) | Oil, coolant, washer fluid, brake fluid, battery, air filter checks, with the car's own warnings | Copyright of OEM. Paraphrase the facts into short steps and cite the page | **Primary source** for every car entry |
| **OBDex** (GitHub `foerbsnavi/OBDex`) | 9,533 generic OBD-II codes with causes, symptoms, DIY feasibility and difficulty | **CC0** (public domain), JSON/YAML | The "check engine light" entry: meaning, and why it's `call_technician` (reading codes needs a scanner) |
| `mytrile/obd-trouble-codes` | Code + description CSV/JSON/SQLite | Check the repo licence | Cross-check only |
| **NHTSA datasets and APIs** (recalls, complaints, investigations, manufacturer communications) | Real failure reports and safety escalations | **US public domain**, free API, no auth | Ideas for `escalate_if` symptoms (e.g. "burning smell", "smoke"). US fleet, so it's context only |
| **iFixit**, "Car and Truck" category (and API v2.0) | Step-by-step guides with photos | **CC BY-NC-SA 3.0**. iFixit's ToS forbids LLM *training* on its content (we don't train). Non-commercial use with attribution is OK for the hackathon; commercial use needs a licence | Cross-check wording and ideas |
| **MyFixit dataset** (`rub-ksv/MyFixit-Dataset`) | 31,601 iFixit manuals as JSON-lines, incl. **Car and Truck (761), Vehicle (374), Appliance (1,333), Household (1,710)** | iFixit-derived (CC BY-NC-SA applies); the README states no separate licence | Offline browsing / search for coverage ideas. Not copied verbatim |
| ManualsLib / carmanualsonline | Mirrors of owner's manuals | Third-party mirror | Only if the OEM PDF can't be found; still cite the OEM manual |

### 3.2 Two-wheeler (Tier B)

| Source | Use |
|---|---|
| **Official owner's manuals** from Honda 2Wheelers India, Hero MotoCorp, TVS, Bajaj, Royal Enfield (usually PDFs on the brand site or in the brand's app; ManualsLib mirrors e.g. Activa, Splendor+) | **Primary.** Oil level window/dipstick, chain slack + lube, tyre pressure (from the manual's label), brake-fluid window, battery terminals, fuses |
| iFixit / MyFixit "Vehicle" | Cross-check only |

### 3.3 Washing machine (Tier A: pick ONE brand)

| Brand | Official source | Notes |
|---|---|---|
| **LG** | LG help library "Front Load Washer Error Code List" (lg.com, US + regional pages) and LG India (lg.com/in) help library | Codes: **OE** (drain), **IE** (water inlet), **UE** (unbalance), **dE/dE1/dE2** (door), **LE** (motor/overload), plus technician-only ones (e.g. PE, FE). Well-documented and very common in India |
| **Samsung** | Samsung India support page "How to resolve 4C or 5C error codes" + other regional support pages | **4C/4E** (water supply), **5C/5E** (drain), dE (door), UB (unbalance) |
| IFB / Whirlpool / Bosch | Brand support pages + the model's manual | Only if the A3 printout is of one of these |

⚠ **Pitch consistency:** `docs/pitch/*.md` uses **"E4"** as the washer example. That isn't an LG code,
and on Samsung it's written "4E". **Pick the brand first, then use one of its real codes in the pitch
and on the printout** (e.g. LG "OE" drain error with the drain-pump filter as the marker target).

### 3.4 Split AC (Tier B: pick ONE brand)

| Brand | Official source | Notes |
|---|---|---|
| **LG** | LG India help library "[LG Air Conditioner] Guide to Error Codes", "How to read error codes", CH05 page | CH-prefix codes. CH05 = indoor–outdoor communication → power-cycle 5 min, then technician |
| **Daikin** | Daikin India "Error Codes" page (daikinindia.com) | A-/E-/U-series. U = refrigerant/pressure → technician |
| Voltas / Blue Star | Brand pages + model manual | Codes vary by model; confirm against the manual |

User-serviceable AC entries are limited to these: filter cleaning, remote/batteries/mode, power reset,
blocked indoor drain (caution), and outdoor unit airflow. **Gas, compressor, PCB and outdoor electrics are always `call_technician`.**

### 3.5 Other datasets checked

| Dataset | Verdict |
|---|---|
| Hugging Face `dipenbhuva/home-diy-repair-qa` | Unverified Q&A. **Don't use as KB content.** Useful as a source of **realistic user questions** to test retrieval stage 2 (aliases) |
| HF `Shekswess/technical-manuals`, `Jaya1995/Maintenance` | Not our domains / unverified. Skip |
| Roboflow Universe engine-bay datasets (classes like battery, coolant reservoir, oil dipstick, oil filler cap, washer fluid reservoir, fuse block) | **Not KB content**, but useful **test images** for grounding (M0/M1) when the real car isn't at hand. Licences vary per dataset, so check each |

---

## 4. Entry list (Tier A draft; the checker confirms each against the source)

### Car engine bay (`appliance: car_engine_bay`, `brand: generic`, checked against the demo car's manual)
| id | severity | grounding targets (descriptive phrases) |
|---|---|---|
| `car_check_engine_oil` | diy | "yellow ring handle of the engine oil dipstick", "engine oil filler cap with the oil can symbol" |
| `car_check_coolant` | caution (engine must be cold) | "translucent coolant reservoir with MIN and MAX marks" |
| `car_top_up_washer_fluid` | diy | "cap of the windshield washer fluid reservoir with the wiper symbol" |
| `car_check_battery_terminals` | caution | "positive terminal of the car battery with the red cover" |
| `car_check_air_filter` | diy | "clips on the side of the air filter box" |
| `car_check_brake_fluid_level` | caution (level check only) | "brake fluid reservoir cap" |
| `car_check_engine_light` | call_technician (needs OBD scanner; OBDex for meaning) | none |
| `car_electrical_wiring` | call_technician (the deliberate refusal demo, CLAUDE.md §3) | none |

### Washer (example if LG front-load is chosen)
| id | code | severity | main target |
|---|---|---|---|
| `lg_fl_oe_drain` | OE | diy | "small drain pump filter cover at the bottom front" |
| `lg_fl_ie_inlet` | IE | diy | "water inlet hose at the back of the machine" / tap |
| `lg_fl_ue_unbalance` | UE | diy | "door" (redistribute load) |
| `lg_fl_de_door` | dE / dE1 / dE2 | diy | "door edge and rubber gasket" |
| `lg_fl_le_motor` | LE | caution → escalate if it repeats | "door" |
| `lg_fl_pe_pressure` | PE | call_technician | none |
| `lg_fl_fe_overflow` | FE | call_technician | none |
| `washer_clean_drain_filter` | — (routine) | diy | "drain pump filter cover" |

For each code, add `code_aliases` for how speech-to-text or OCR will actually produce it:
"OE" → `0E`, `O E`, `oh e`, `zero e`. "dE" → `DE`, `d e`. "4C" → `4 C`, `four c`.

---

## 5. Workflow per entry (~10–15 min each)

1. **Pick** the entry from §4 and open the **primary source**. Note the URL / manual page + today's date.
2. **Extract** the meaning, the safety precautions and the user-level steps. Stop where the source says
   "contact service". Everything after that point is `escalate_if` / technician.
3. **Cross-check** with a second source (another official regional page, iFixit, OBDex). If they
   disagree, follow the official source or leave the step out.
4. **Write** the entry in the CLAUDE.md §7 schema:
   - `safety`: ≥ 1 line (unplug / engine off and cool / tap closed).
   - `steps[].say`: one action, ≤ 20 words, plain English, no invented values.
   - `steps[].target`: a **descriptive grounding phrase** (colour, shape, position, symbol), or `null`.
   - `aliases` / `symptoms`: 5–10 real phrasings (use the HF Q&A set and your own speech for ideas).
   - `escalate_if`: the danger signs from the source (burning smell, smoke, water leak near power, etc.).
   - `source`: `"<Brand> <doc title>, <URL or manual p.N>, checked 2026-09-26, licence <x>"`.
5. **Read it aloud.** If it sounds wrong spoken, rewrite it.
6. **Grounding check:** for each non-null target, run it on 2–3 real photos (the M0/M1 tool) and
   rewrite phrases the VLM misses.
7. **Validate** with `tools/kb/validate_kb.py` (below). Commit.

---

## 6. Tooling to add (small)

- `tools/kb/validate_kb.py`. It checks:
  - JSON is valid
  - ids are unique
  - `appliance|brand|normalizedCode` keys are unique after normalization (same rules as the app: uppercase, no spaces, O≡0)
  - severity is valid
  - ≥ 1 safety line unless `call_technician`
  - steps are numbered 1..n
  - each `say` is ≤ 20 words
  - `source` is present
  - no `call_technician` entry has repair steps

  The app's `KbRepository` does the same checks at startup (CLAUDE.md §7). The script just catches errors earlier.
- `tools/kb/test_queries.jsonl`: ~40 realistic questions (spoken style, including ASR-garbled codes) →
  expected entry id or `NONE`. Use it to test retrieval stages 1–2 on the laptop, and later in unit tests.
- `kb_version` at the top of the JSON (e.g. `"2026-09-26.1"`), logged with each answer.

---

## 7. Time budget (most of it can be done during Red Light)

| Task | Time |
|---|---|
| Decide washer brand + car model; collect the official pages / manual PDFs | 20 min |
| 8 car entries | 1.5 h |
| 8 washer entries | 1.5 h |
| Validator + 40 test queries | 30 min |
| Grounding check of all targets on photos | 30 min |
| **Tier A total** | **~4 h** (can be split across Red Light slots) |
| Tier B (two-wheeler + AC), optional | +2–2.5 h |

---

## 8. Licensing and attribution (for the pitch Q&A too)

- **OEM manuals / support pages:** we store short paraphrased steps plus a citation, not copied pages.
  Each entry shows its source on screen ("Source: LG support, OE error").
- **OBDex:** CC0, free to use as-is.
- **NHTSA:** US public domain.
- **iFixit / MyFixit:** CC BY-NC-SA 3.0. The hackathon is non-commercial, so give attribution and share
  derived entries under the same licence. **Commercial use later needs an iFixit licence.** Their ToS
  also forbids LLM training on the content, and we don't train.
- **Pitch line:** "Every step is traceable to an official manual or support page, and checked by a
  human before it goes in."

---

## 9. Decisions needed from you

1. **Washer brand** (it must match the A3 printout and the pitch's example code). Suggested: **LG front-load**,
   because it has the best official documentation and the "OE → drain filter" demo is visual.
2. **Demo car model** (so car entries are checked against *its* manual; e.g. the car you'll use in the parking lot).
3. **Tier B:** in scope for the demo, or pitch roadmap only? Suggested: roadmap only, unless M4 is done early.

---

## Sources

- [MyFixit dataset (GitHub)](https://github.com/rub-ksv/MyFixit-Dataset) · [MyFixit paper (LREC 2020)](https://aclanthology.org/2020.lrec-1.260/)
- [iFixit content licensing](https://www.ifixit.com/Info/Licensing) · [iFixit API v2.0 docs](https://www.ifixit.com/api/2.0/doc/Wikis) · [iFixit repair API announcement](https://www.ifixit.com/News/3981/the-worlds-first-repair-api)
- [OBDex (CC0 OBD-II codes)](https://github.com/foerbsnavi/OBDex) · [mytrile/obd-trouble-codes](https://github.com/mytrile/obd-trouble-codes) · [MechanicDB public sample](https://github.com/MechanicDB/MechanicDB-public)
- [NHTSA datasets and APIs](https://www.nhtsa.gov/nhtsa-datasets-and-apis) · [NHTSA data](https://www.nhtsa.gov/data)
- [Maruti Suzuki WagonR owner's manual (official storage)](https://marutistoragenew.blob.core.windows.net/msilintiwebpdf/WagonR-99011M69R12-74W.pdf) · [ManualsLib Maruti Suzuki Alto](https://www.manualslib.com/manual/541447/Maruti-Suzuki-Alto.html)
- [ManualsLib Honda Activa](https://www.manualslib.com/manual/1212498/Honda-Activa-2009.html) · [ManualsLib Hero Splendor+](https://www.manualslib.com/manual/1955922/Hero-SplendorPlus.html) · [Honda Powersports owner's manuals](https://powersports.honda.com/downloads/owners-manuals)
- [LG front-load washer error code list](https://www.lg.com/us/support/help-library/lg-front-load-washer-error-code-list--20153792986252) · [LG OE error](https://www.lg.com/us/support/help-library/lg-washer-what-is-a-front-load-washing-machine-oe-error-code--1337714738535) · [LG dE/dE1/dE2](https://www.lg.com/us/support/help-library/front-load-washer-what-are-de-de1-and-de2-error-codes--1400508168566) · [LG India top-load error codes](https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT52006627-20154403824705/)
- [Samsung India 4C/5C](https://www.samsung.com/in/support/home-appliances/how-to-resolve-4e-or-5e-codes-on-your-samsung-washing-machine/) · [Samsung UK 4E/4C](https://www.samsung.com/uk/support/home-appliances/the-4e-error-is-displayed-on-the-panel-of-my-washing-machine-what-can-i-do/) · [Samsung US error codes](https://www.samsung.com/us/support/troubleshoot/TSG10000997/)
- [LG India AC error code guide](https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT52006833-20154846171880/) · [LG India AC CH05](https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT00022939-20153280864489/) · [LG India how to read AC error codes](https://www.lg.com/in/support/product-support/troubleshoot/help-library/cs-CT00022939-20155117728312/) · [Daikin India error codes](https://daikinindia.com/product-services/product-services/error-codes)
- [HF home-diy-repair-qa](https://huggingface.co/datasets/dipenbhuva/home-diy-repair-qa)
- [Roboflow engine bay search](https://universe.roboflow.com/search?p=0&q=class%3Acar+engine+bay) · [Engine Parts Detector dataset](https://universe.roboflow.com/final-year-project-5vbtb/engine-parts-detector)
