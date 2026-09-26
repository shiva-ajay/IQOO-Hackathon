# Fixy's persona and memory across sessions

Goal: Fixy should feel like a handy friend who remembers you ("Good evening! Last time we looked at your
washing machine (error OE) yesterday. Is it working fine now?"), not a repair robot that starts from zero
every time. Everything stays on the phone, and nothing Fixy "remembers" can be made up.

## 1. Three layers of memory

```
            ┌────────────────────────────── system prompt (rebuilt on session open) ─────────────────────┐
 Persona ──►│ SYSTEM: casual tone, answer what was asked, safety + no-invention rules                    │
            │ <session>        this repair's notes         ← MemoryRules (per turn, regex, no VLM)       │
            │ <past_repairs>   the repair the greeting asked after, first reply only  ← Recall           │
            └────────────────────────────────────────────────────────────────────────────────────────────┘
            assistant: <greeting>  (before the first question only) ← Recall.greeting (templates, no VLM)
            user/assistant: last 2 turns as text, then the live turn (+ picture unless it's small talk)
```

| Layer | What | Where it lives | Built by |
|---|---|---|---|
| Live turns | This session's conversation | MNN KV cache | ConversationContext (docs/sessions-plan.md §3) |
| Session notes | appliance, brand, error code, symptoms, **outcome** | `session.json` → `memory` | `guide/MemoryRules.kt` |
| Past repair | the one the greeting asked after | computed on open, dropped after the first reply | `guide/Recall.kt` |

A past-repairs line:
```
- "Washer not draining" (washing machine, LG, error OE; not draining), 3 days ago, outcome unknown. Last advice: "Clean the drain filter at the bottom front"
```
**Scope (2026-09-27):** a new session sees only the repair its greeting asked after, and only for the user's
first reply. Earlier, up to 4 past repairs stayed in the prompt for the whole session, and the 4B model mixed them
into unrelated answers (car oil advice while looking at a laptop). Once the greeting is answered:
- the cache is rebuilt without `<past_repairs>` and without the greeting (a background prewarm, before the title
  request, so the title isn't about the old device either);
- that first turn is left out of replays if it was only an answer about the earlier repair (`MemoryRules.openedWithFollowUp`);
- its notes come from `MemoryRules.afterFollowUp`: the old device, brand, code and symptoms aren't copied into the
  new session, only something new ("yes it's fixed, but now my laptop won't turn on" → Laptop).

The cost: mid-session questions about an older repair ("what did we do last time?") aren't answered from memory.

## 2. Persona (`guide/FixyPrompts.kt` SYSTEM) and question kinds

- Casual, like a friend looking through the camera with you; at most 2 short spoken sentences with contractions.
- Answer exactly what was asked. No pivot to repairs, no reply that always ends with a question or an offer to help.
- Only talk about what's in the latest picture or what the user brought up. The prompt names no device examples
  (it used to list oil, coolant and washer fluid, and the model repeated them everywhere).
- "What do you do?": one generic sentence (cars, home appliances, gadgets; points at the part to check).
- Rules unchanged: safety first; wiring, gas and sealed or mains-powered parts go to a technician; never invent parts,
  values, codes **or past events**.

Each question is sorted by `guide/Intent.kt` (word rules, no VLM call); anything unclear is Repair:

| Kind | Examples | Picture | Per-turn instruction | Filler |
|---|---|---|---|---|
| Chat | hi, thanks, who are you, what do you do, yes | no (faster) | `CHAT_TURN`: one casual sentence, no repair advice | no |
| Look | what do you see, what's this, describe this | yes | `LOOK_TURN`: say what's there, no repair steps | yes |
| Repair | where is…, how do I…, error codes, "won't start" | yes | the pointing request (JSON first, then the reply) | yes |

## 3. The greeting (templated, never generated)

`Recall.greeting(past)` picks one of four shapes (time of day: morning, afternoon, evening, or "Hi there"):

| History | Greeting | Asks after |
|---|---|---|
| none | "Hi, I'm Fixy! Point me at what's broken and tell me what's happening." (CLAUDE.md §8) | – |
| latest repair < 14 days, not known fixed | "Good evening! Last time we looked at your washing machine (error OE) yesterday. Is it working fine now? And what are we fixing today?" | that session |
| latest repair known fixed | "Good evening, welcome back! Glad your car is sorted. What are we fixing today?" | – |
| latest repair older | "Good evening, welcome back! What are we fixing today?" | – |

Templates rather than the VLM because the greeting must be instant (it shows the moment the session opens), and
the facts in it come straight from saved notes, so it can't misremember. The greeting is stored on the session
(`greeting`, `followUpOf`) and replayed to the model as its first assistant message, so a reply of "yes, it's fine
now" makes sense to the model.

## 4. Closing the loop: outcomes

`SessionMemory.outcome` is `Unknown | Fixed | NotFixed`.
- **Within a session:** each question is scanned (`MemoryRules.outcome`): "it's working fine now" → Fixed, "still
  not draining" → NotFixed (the not-fixed patterns are checked first).
- **From the next session:** if the greeting asked after session X, the user's first reply is scanned with
  `followUpOutcome` (also accepts a plain "yes" or "no") and marks X, not the new session. X's "last updated" time
  is left alone, so it keeps its place in the list.

So the next greeting knows to say "Glad your car is sorted" instead of asking again.

## 5. Limits and next steps

- Outcome and device detection are keyword rules: cheap and predictable, but they miss unusual phrasing. The
  title fallback ("Last time we looked at "Laptop screen issue"") covers unknown devices.
- A reopened session shows its last answer, not a "welcome back" line. It could get one from the same templates.
- The greeting is text only until TTS lands (M3); it should be spoken instantly on open.
- Post-hackathon: a model-written one-line summary per session (as a rolled-back side request, like the title)
  would give richer past-repair lines than the last answer's first sentence.
