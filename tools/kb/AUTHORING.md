# Writing FixLens KB entries

The app reads `app/src/main/assets/kb/fixlens_kb.json`. It is **generated** by `tools/kb/build_kb.py` from
`tools/kb/entries/<category>.json` (one file per category). Every entry has its sources written up in
`tools/kb/sources/<category>.md`. Background: [docs/kb-dataset-research.md](../../docs/kb-dataset-research.md).

## Principles

1. **Generic, not brand- or model-specific.** Entries are per appliance category and written from what the
   manufacturers agree on. `brand` is `"generic"`.
2. **Consensus rule.** A step goes in only if ≥ 2 official manufacturer sources (different brands) give it, or one
   official source plus a public-agency guide (US DOE / ENERGY STAR / CPSC / NHTSA / BEE India). Anything that
   differs by model becomes "check your manual" wording or an escalation. Aggregator blogs, iFixit and Stack
   Exchange are only for edge cases and wording ideas, never the only source of a step.
3. **Symptom-first.** Entries are what the user notices ("won't drain", "not cooling"). Error codes are a second
   way in (`brand_codes`).
4. **Diagnostic order.** Steps go cheapest and safest first (setting, switch, door, tap, filter…). The **last step**
   of a diagnostic entry says when to stop: "If it still …, please call a technician."
5. **User-serviceable only.** Mains wiring, gas/refrigerant, sealed systems, PCBs, compressors, motors, heaters,
   airbags, brakes beyond a level check, microwave internals, opening a TV/geyser → `call_technician`, no steps.
6. **Written for the ear.** Everything in `meaning`, `safety[]` and `steps[].say` is **spoken word for word**.
   Plain English, contractions, no parentheses, no model numbers, no tables. `say` ≤ 20 words, one action.
   `meaning` ≤ 20 words and starts naturally ("Let's check why the water isn't draining.").
7. **Our own words.** Manuals are copyrighted: keep the facts and order, rewrite the sentences, cite the source.

## Schema

```json
{
  "id": "washing_machine_not_draining",
  "appliance": "washing_machine",
  "brand": "generic",
  "error_code": null,
  "code_aliases": [],
  "brand_codes": [
    {"brand": "LG", "codes": ["OE"], "spoken": ["oh e", "zero e"]},
    {"brand": "Samsung", "codes": ["5C", "5E", "SE"], "spoken": ["five c", "five e"]}
  ],
  "title": "Washing machine not draining",
  "meaning": "Let's find out why the water isn't draining.",
  "symptoms": ["water left in the drum", "clothes still wet", "drain error"],
  "aliases": ["not draining", "won't drain", "water not going out", "washing machine not draining"],
  "severity": "diy",
  "escalate_if": ["water on the floor", "burning smell", "still showing"],
  "safety": ["Switch the machine off and unplug it."],
  "steps": [
    {"n": 1, "say": "Check the drain hose at the back isn't bent or squashed.",
     "target": "the grey drain hose at the back of the washing machine", "verify": null, "caution": null}
  ],
  "source": "Samsung WW70T4020EE manual p.55 + LG help library OE page + Bosch E18 page; checked 2026-09-27"
}
```

| Field | Rules |
|---|---|
| `id` | `<appliance>_<snake_case_symptom>`, unique. Keep existing ids when revising an entry. |
| `appliance` | One of: `washing_machine`, `air_conditioner`, `refrigerator`, `television`, `laptop`, `water_heater`, `water_purifier`, `microwave`, `inverter`, `car`, `bike`. |
| `brand` | `"generic"`. |
| `error_code`, `code_aliases` | `null` / `[]` for generic entries (kept for old brand-specific entries). |
| `brand_codes` | Optional. Per brand, the codes that mean **this** fault, from that brand's official source. `spoken` = how a person says it ("five c", "oh e"). A code already covered by letter/digit normalization ("5 C", "O E", "0E") needs no spoken form. Leave out codes that are ordinary words ("AC", "TV", "ON", "NO"). Never list a code for a brand unless you saw it in that brand's source. |
| `symptoms`, `aliases` | 5–10 short phrases a user would really say. See matching below. |
| `severity` | `diy` \| `caution` (hot, sharp, heavy, water near power, acid) \| `call_technician` (no steps, no safety needed). |
| `escalate_if` | 2–5 danger signs or "it didn't work" signals, **2–3 meaningful words each** ("burning smell", "sparks", "water on the floor", "still showing", "gas smell"). |
| `safety` | ≥ 1 line unless `call_technician`. 1–2 lines; each must be confirmed with "done" by the user, so keep it to what matters. |
| `steps[].say` | ≤ 20 words, one action. |
| `steps[].target` | What the VLM must box, as a **descriptive phrase true on most models**: colour, shape, position, symbol ("the small square flap at the bottom front corner of the washing machine"). `null` if there's nothing to point at. |
| `steps[].verify` | Optional: something the camera would clearly see when the step is done ("the filter cover is open"). Usually `null`. |
| `steps[].caution` | Optional short warning shown on the step card. |
| `source` | Short citation(s) + "checked 2026-09-27". Full notes go in `sources/<category>.md`. |
| `steps[].anim` | Optional small animation (how-to card + cue on the part): `{"kind": "turn", "dir": "ccw"}`. Kinds: `turn` (needs `dir` `ccw`/`cw`, and the step's words must say unscrew/anticlockwise or tighten/screw on/clockwise), `pull` / `push` (optional `dir` up/down/left/right: the way the part moves on screen), `level`, `pour`. `null` = reviewed, no animation. Propose with `tools/kb/suggest_anims.py`, then review. |
| `safety_anim` | Optional, one per safety line by position: `unplug`, `switch_off`, `engine_off`, or `null`. |

## How matching works (write aliases for this)

1. **Appliance first.** The app works out the appliance from the user's words ("fridge", "AC", "scooter") or the
   session, and only searches that appliance's entries. So aliases don't need the appliance name, but one or two
   that include it help when the appliance is unknown.
2. **Codes (stage 1).** A code in the text matches `brand_codes`. If the brand is known (said or remembered) it
   must be that brand's code; if not, the code must point to only one entry.
3. **Keywords (stage 2).** Text and phrases are lowercased, split on anything not a–z/0–9, stop words dropped
   ("a the my is it how what can do …"), crudely stemmed ("draining" → "drain", "lights" → "light"), and
   negations unified ("won't", "doesn't", "isn't", "can't", "no" → "not"). An entry's score is its best phrase
   (aliases + symptoms + title): the share of that phrase's words found in the text. Accepted if ≥ 0.6 **and**
   ≥ 0.2 ahead of the next entry. So: short, specific phrases; give each entry words the others don't have;
   avoid phrases two entries of the same appliance would share.
4. **Escalation during a guide.** Words of 2 letters or fewer are ignored; ≥ 75 % of the rest must be in what the
   user says.

## Deliverables per category

- `tools/kb/entries/<category>.json`:
  `{"category": "<name>", "entries": [ ... ], "test_queries": [{"q": "my washer won't drain", "expect": "<id>"}, {"q": "...", "expect": "NONE"}]}`
  (KbTest looks each one up as in a session about this category, unless the question names another appliance)
  with 3–4 test queries per entry (spoken style, including one code query per entry that has `brand_codes`) and
  2 that must return `NONE` (out of scope for this category).
- `tools/kb/sources/<category>.md`: per entry, the sources (URL, document, page/section), which sources agree on
  each step, what you dropped and why, licence notes.
