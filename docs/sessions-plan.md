# Sessions + conversation memory — plan

Status (2026-09-26): **S0–S5 implemented and verified on the iQOO 15.** Results:
- S0: KV append works after patching MNN (decode mrope positions ignored the KV offset;
  `tools/mnn-patches/omni-multiturn-positions.patch`). Text follow-up prefill 0.5–0.7 s; `eraseHistory` rollback works.
- In the app: first word ~3 s (was ~5 s, the system prompt is now prefilled when the session opens);
  follow-ups recall earlier turns; a reopened session is prewarmed from notes + last 2 turns (~2.6 s, before the first question).
- Auto-title ("Washing Machine Drain Issue") costs ~1 s as a rolled-back side request.
- Debug hook for testing without speaking: `adb shell am start -n com.fixlens/.app.MainActivity --es ask "..."`
  (`--ez new true` opens a new session, `--ez mic false` mutes).

## 1. What we are building

Today the app opens straight into the camera, and every question is answered on its own (Fixy
forgets the previous turn). The new flow:

```
App opens ──► Sessions screen (models load in the background here)
                 │  [ + New repair ]        ← opens the camera in a new, empty session
                 │  ┌──────────────────────────────┐
                 │  │ [thumb] Washing machine · E4 │  ← tap = resume with full memory
                 │  │ "It says drain problem…"  2m │
                 │  └──────────────────────────────┘
                 ▼
           Session screen (camera + conversation card, same as today)
                 - every question/answer is saved into the session
                 - Fixy remembers earlier turns of THIS session
                 - after the first answer, Fixy names the session ("Refrigerator repair")
                 - back arrow → Sessions screen
```

## 2. What the engine can do (checked in `/home/shiva-ajay/3D/MNN` source)

| Finding | Where | Why it matters |
|---|---|---|
| `max_all_tokens` defaults to **2048** | `llmconfig.hpp:152` | That is the whole conversation budget. We must raise it (plan: 4096). |
| `reuse_kv: true` keeps the KV cache between `response()` calls | `llm.cpp:904-915` | Past turns are not recomputed. Only the new turn costs prefill time. |
| `getCurrentHistory()` / `eraseHistory(begin, end)` | `llm.hpp:179-180` | We can mark the KV length before a turn and roll back a cancelled turn or a side request (title). |
| `prompt_cache` + `response(ChatMessages)` diffs the full prompt text against the last one | `llm.cpp:1209-1330` | This is how MNN Chat does multi-turn. **But** it re-tokenizes the full prompt, and for Qwen3-VL that re-runs the vision encoder on **every image in the history** (`omni.cpp` has no image-embedding cache). It also looks likely to hand the first image's embedding to the new image in the delta path. **Don't use it with images.** |
| `setPrefixCacheFile()` | `llm.hpp:181` | Optional: save the system-prompt KV to disk so a new session skips that prefill. |
| KV memory for 4B: 36 layers × 8 KV heads × 128 dim × K+V × 2 bytes ≈ **147 KB/token** | `llm_config.json` | 4096 tokens ≈ 600 MB. Fine on 16 GB. |

**Cost model (measured today):**
- Prefill runs at ~60–100 tokens/s on the CPU.
- Every 100 extra prompt tokens adds **~1–1.5 s** before the first word.
- One 448-px keyframe is about 150 image tokens, plus ~1.3 s of vision encoding.

So the context strategy is really about latency: **the model should never re-read what it has
already read.**

## 3. Context engineering: the approach

Principles we apply (from the well-known write-ups: Anthropic's *Effective context engineering
for AI agents*, Manus's *Context engineering for AI agents*, and the *Lost in the middle* paper):

1. **The KV-cache hit rate is the key metric.**
   - Keep the prefix stable and the context append-only.
   - Never edit earlier turns, or you pay for a full re-prefill.
2. **Keep structured notes outside the model**, a small "session memory" that we own. Use it to
   rebuild a lost or overflowing context in a few hundred tokens, instead of replaying everything.
3. **Compact when full.** Summarise the old turns into the notes, keep the last few verbatim,
   and drop old images.
4. **Put the important content at the end.** The latest image and question go last; the facts
   Fixy must respect (KB entry, step) sit right after the system prompt.
5. **Keep the context minimal and high-signal.** A 4B model gets worse with long noisy
   history, not better.

### 3.1 Two layers of memory per session

**A) Transcript (source of truth, saved to disk).** Every turn is kept:
```json
{ "n": 3, "question": "what does E4 mean", "answer": "E4 means the water isn't draining…",
  "keyframe": "kf_003.jpg", "ts": 1790000000, "cancelled": false, "ms": 6120 }
```

**B) Session memory (small structured notes; also saved; goes into the prompt):**
```json
{ "appliance": "front_load_washer", "brand": "IFB", "error_code": "E4",
  "symptoms": ["water not draining"], "checked": ["drain filter"],
  "kb_entry": "washer_ifb_e4", "step": 2, "safety_confirmed": true }
```
- It's filled by **cheap deterministic rules**, no VLM call:
  - regex for error codes (`E\d+`, `OE`, `F\d+`);
  - keyword lists for appliance and brand;
  - the KB lookup (M4) for `kb_entry` and `step`.
- It's rendered into the prompt as 3–6 short lines (~40–80 tokens).

### 3.2 How a turn reaches the model: "live KV" plus "rebuild from notes"

```
KV cache (one MNN instance, one active session at a time)
┌────────────┬──────────────────┬───────────┬───────────┬───────────┬───── ─ ─
│ system     │ session memory   │ turn 1    │ turn 2    │ turn 3    │ new turn
│ Fixy rules │ (+ KB entry, M4) │ img+Q+A   │ img+Q+A   │ img+Q+A   │ img+Q → A
└────────────┴──────────────────┴───────────┴───────────┴───────────┴───── ─ ─
  prefilled once per session         appended, never recomputed       only this is prefilled
```

- **Normal turn:**
  - Append only `<|im_start|>user <img>kf</img> question<|im_end|><|im_start|>assistant` and
    generate.
  - The cost is the same as today (~150 image + ~20 text tokens), so **memory is free.**
- **Cancelled turn (barge-in):**
  - Save `mark = getCurrentHistory()` before the turn.
  - On cancel, call `eraseHistory(mark, 0)` so a half-answer never pollutes the context.
- **Session opened or resumed** (the KV holds another session, or nothing): **rebuild**.
  - Prefill system + session memory + the **last 2 turns as text only** (no old images) + the
    new turn.
  - That is roughly 150 extra tokens, a one-off ~1.5–2 s.
  - It can be hidden: start it the moment the session opens, while the camera warms up and
    before the user speaks.
- **Near the limit** (history > ~3300 of 4096 tokens):
  - Update the session memory, then rebuild the same way as a resume.
  - Old images are dropped here. Their facts live on in the notes.
- **The KB entry changes** (M4, the user moves to a different problem): rebuild, so the new
  `<kb_entry>` sits near the top where the model weights it most.

One MNN model instance serves every session. Only the *active* session lives in the KV cache;
the others live on disk as transcript + memory and are rebuilt on open. We never keep two
3 GB models loaded.

### 3.3 Prompt layout for a rebuild

```
<|im_start|>system
You are Fixy … (persona rules, unchanged)
Earlier pictures may show a different view. Answer about the LATEST picture unless the user asks about an earlier one.
<session>
appliance: front-load washer (IFB) · error: E4 · symptoms: water not draining
already checked: drain filter
</session>
<kb_entry …>…</kb_entry>            ← M4, when an entry is active
<|im_end|>
<|im_start|>user
what does E4 mean<|im_end|>          ← last 2 turns, text only
<|im_start|>assistant
E4 means the water isn't draining…<|im_end|>
<|im_start|>user
<img>kf_004.jpg</img>is this the filter?<|im_end|>
<|im_start|>assistant
```

## 4. Auto-naming a session

1. **Instantly** (on the first final question): pick a keyword title so the list never shows
   "New session":
   - fridge / refrigerator → "Refrigerator repair";
   - car / engine / oil / coolant → "Car engine check";
   - washer / washing machine or an error code → "Washing machine · E4".
2. **After the first answer, when the VLM is idle:** ask the VLM for a 2–4 word title.
   - The request uses only the first question + answer text, with no image (~60 tokens,
     ~1–1.5 s).
   - It runs as a **side request**: mark the KV, generate, then `eraseHistory(mark)` so it never
     enters the conversation.
   - If the user asks a new question meanwhile, the title job is cancelled and retried later.
3. The title is locked after that, unless the user renames it (long-press) or turn 3 detects a
   different appliance.

## 5. Storage (no new dependencies)

- Stored in app-private storage (`filesDir/sessions/<id>/`, deleted with the app):
  - `session.json`: id, title, titleSource (keyword/model/user), created, updated, memory,
    turns[];
  - `kf_001.jpg`, … (the keyframes, which also serve as list thumbnails).
- Written with `kotlinx.serialization`, which is already in the stack. Written atomically
  (temp file + rename) after each turn.
- A new `SessionRepository` (in `app/` or a new `session/` package):
  - `list()`, `create()`, `load(id)`, `appendTurn()`, `updateMemory()`, `rename()`, `delete()`;
  - IO on `Dispatchers.IO`.
- Room was considered and rejected: it would be a new dependency, and JSON is enough for a
  handful of sessions.
- The pitch point: **sessions and photos never leave the phone.**

## 6. App structure changes

| Change | Detail |
|---|---|
| `FixyEngine` (app-scoped, created in `FixLensApp`) | Owns `VlmEngine` + `SpeechInput`. It starts loading at app launch and exposes `ready: StateFlow`. It is no longer tied to the camera screen, so the models load while the user is on the list. |
| `ConversationContext` (new, `guide/`) | Owns the KV state machine: which session is in the KV, marks, the token count, and rebuild vs. append. It builds the prompts from §3.3. |
| `SessionMemory` + rules (new, `guide/`) | Extracts appliance, brand, code and symptoms from each turn. Later M4 plugs the KB `kb_entry`/`step` in here. |
| `VlmEngine` / `fixlens_llm.cpp` | Add `reuse_kv: true` and `max_all_tokens: 4096` to the config. Add `nativeHistoryLen`, `nativeErase(begin)`, `nativeReset`, and a raw-prompt generate that does **not** apply the chat template (we write the template ourselves so append and rebuild stay byte-identical). |
| Screens | A `Screen` state (`Sessions` / `Session(id)`) in the ViewModel. `BackHandler` on the session screen. No navigation library (it would be a new dependency). |
| `SessionsScreen` (new) | Logo, a model-ready chip ("Fixy is getting ready…" becomes "Ready · on-device"), and a big amber **New repair** button. Below it, session cards (thumbnail, title, last line, relative time, turn count), with long-press to rename or delete. The empty state shows Fixy the drone. |
| `CameraScreen` | Adds a back arrow, the session title in the top bar (animates when auto-renamed), and a pull-up **history sheet** listing earlier turns in this session. |

## 7. Build order (each step runs on the phone)

| # | Step | Done when | Est. |
|---|---|---|---|
| S0 | **Spike: KV append on the phone** with Qwen3-VL. Raw-prompt append, 3 turns with a different image each, then `eraseHistory` rollback. | Turn 2's prefill ≈ turn 1's (not bigger). "What did I ask before?" is answered correctly. The answer after a rollback ignores the erased turn. Grounding is no worse than now. | 45 min |
| S1 | `SessionRepository` + JSON models + atomic save | Create, append, reload and delete work. Survives an app kill. | 30 min |
| S2 | App-scoped `FixyEngine`, the Sessions screen, the New-repair → camera flow, and back | The app opens on the list, models load in the background, and a new session opens the camera. | 1 h |
| S3 | `ConversationContext`: append, cancel rollback, rebuild on resume, compaction | A follow-up in the same session uses earlier turns. A resumed session remembers. Cancelled turns leave no trace. | 1 h |
| S4 | Session memory rules + keyword title + VLM side-request title | The title appears after the first answer. The memory JSON fills in. | 45 min |
| S5 | History sheet, rename/delete, polish | — | 45 min |

**Fallback if S0 fails** (the mrope positions break after an append, or `eraseHistory` misbehaves
with images):
- Use **stateless turns with a text-only history**: every turn re-prefills system + memory +
  last 2 turns as text + the current image.
- It's simpler and safe, but each turn costs ~1.5–2 s more.
- Everything else in this plan (sessions, storage, titles, memory notes) stays the same.

## 8. Risks

- **Qwen3-VL 3D (mrope) positions across appended multi-image turns.** This is the unknown S0
  must answer, since grounding is already off on this engine build.
- **Latency.** Memory must not slow down turns; the plan only adds cost on resume or compaction.
- **A 4B model over-weights old turns** (it answers about the previous picture). Mitigate with
  the system-prompt rule, last-2-turns-only on rebuild, and the image placed last.
- **Time.** This is roughly 4.5 h against ~15 h of remaining build time, and it isn't on the
  M1–M4 critical path. Suggestion: do S0–S3 now (they make the demo feel like a real product),
  and leave S5 polish for later.
