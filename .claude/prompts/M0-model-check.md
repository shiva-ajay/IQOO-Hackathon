# Task: FixLens Milestone M0: model check (choose the VLM before writing integration code)

This is a fresh session. Start by reading these in full:
1. `CLAUDE.md`, especially §3 (demo targets), §8 (Fixy prompt), §9 (VLM I/O contract), §14 (M0 gate)
2. `.claude/STACK.md` §2 (models and fallbacks)
3. `PLAN.md`, the **M0** section
4. `docs/mnn-donor-notes.md`, if it exists (written during P0)

**Goal of M0:** decide, with evidence, **which VLM, which settings and which coordinate format**
FixLens will use. We need these answers before M1 starts:

| Question | Pass bar |
|---|---|
| Does the model put the box on the named part? | ≥ 4/5 correct per target set (car, washer printout) |
| Does it follow the output format? | First line is valid JSON `{"bbox_2d":[...],"label":"..."}` or `{"bbox_2d":null}` in ≥ 9/10 runs |
| Which coordinate scale does it use? | Confirmed by **drawing** the box on the image. 0–1000 normalized, or absolute pixels of the resized input |
| How fast is it on the iQOO 15? | Time to box line ≲ 2 s, full answer ≲ 3 s |
| Can it read error codes? | Reads "OE" / "E4"-style codes off the printout correctly |
| Does it obey the refusal rule? | A wiring question with no KB support gets the exact refusal line |

Do **not** write any app code in this milestone. No changes under `app/`.

---

## Known state (from P0; re-check all of it)

- Models downloaded to `/home/shiva-ajay/3D/fixlens-models/`:
  - `qwen3-vl-4b/` (primary) and `qwen3-vl-2b/` (fallback 1). The MNN exports contain `config.json`,
    `llm_config.json`, `llm.mnn`, `llm.mnn.json`, `llm.mnn.weight`, `visual.mnn` and `tokenizer.txt`.
  - **Before using either model, check the download finished.** In an earlier check,
    `llm.mnn.weight` was still missing. Compare the file list and sizes against the Hugging Face
    repo, and check that everything `config.json` references exists (e.g. `embeddings_int4.bin`).
  - Downloads logs are in `fixlens-models/logs/`.
- `llm_config.json` for 4B has `"image_size": 420`. Find out how MNN resizes input images from this,
  and whether it can be overridden. This affects both speed and the coordinate scale.
- The model `config.json` defaults are **not** what we want (`temperature 0.7`,
  `penalty 1.2`, `max_new_tokens 16384`, `backend_type cpu`). Never edit the original. Make test
  copies of the config (e.g. `config.fixlens.json`) with: temperature 0.2–0.3,
  `max_new_tokens` 150, penalty ~1.0–1.05 (a high repetition penalty can break JSON), and the
  backend under test.
- MNN source is cloned at `/home/shiva-ajay/3D/MNN/`. It includes the `llm_demo` sources at
  `transformers/llm/engine/demo/llm_demo.cpp`.
- **MNN Chat** is installed on the phone (`com.alibaba.mnnllm.android.release`). Its source shows it
  scans **`/data/local/tmp/mnn_models/`** for local models. Verify this in the MnnLlmChat source.
- The phone (iQOO 15) is authorized in `adb devices`. Pillow 10.2 and cmake/g++ are on the laptop.
- `scripts/iq15_gemma_auto.py` is an unrelated earlier script that drives Google AI Edge Gallery
  over ADB with `uiautomator dump`. It's a useful pattern if you need to drive MNN Chat's UI, but
  don't modify it.

---

## The plan: split accuracy from latency

The int4 weights are identical on laptop and phone, so **box accuracy, format-following and
refusals can be measured on the laptop**. That's scripted, repeatable and fast to iterate.
**Latency must be measured on the phone.**

### Part A: Accuracy on the laptop (scripted)

1. **Build the MNN LLM demo for the laptop (x86_64, CPU)** from `/home/shiva-ajay/3D/MNN` with LLM
   and vision support. Check MNN's docs/CMake options for the current flag names (something like
   `MNN_BUILD_LLM=ON`, `MNN_LOW_MEMORY=ON`, `MNN_SUPPORT_TRANSFORMER_FUSE=ON`, a vision/omni
   flag, and image codecs/`MNN_BUILD_OPENCV` so it can read JPEGs). Build it out-of-tree in
   `/home/shiva-ajay/3D/MNN/build-host/`. Time-box: **30 min**.
   - Alternative if it's quicker: the Python binding (`pymnn`, `MNN.llm`), if it supports VL
     image input. Check before choosing, and tell me which you used.
   - If neither works in the time box, stop and go straight to **Part C** (manual, in MNN Chat).
2. Find out exactly how `llm_demo` takes an image. MNN uses an `<img>…</img>` tag inside the
   prompt text, optionally with a size hint like `<img><hw>H, W</hw>path</img>`. **Confirm this
   in the source/docs**, and also how to pass a system prompt.
3. **Test images.** Ask me for the photos, and give me this shot list:

   | Set | Photos | Target phrase to ground (use exactly) |
   |---|---|---|
   | Car engine bay | 1 | "yellow ring handle of the engine oil dipstick" |
   | | 2 | "cap of the engine coolant reservoir" |
   | | 3 | "cap of the windshield washer fluid reservoir" |
   | | 4 | "positive terminal of the car battery with the red cover" |
   | | 5 | "clips on the side of the air filter box" |
   | Washer printout | 6 | "digital display showing the error code" |
   | | 7 | "start or pause button" |
   | | 8 | "program selection knob" |
   | | 9 | "power button" |
   | | 10 | "detergent drawer" |

   - Photos come from the phone camera at normal viewing distance. They'll be pulled with
     `adb pull /sdcard/DCIM/Camera/<files>`. Ask me which files.
   - If I don't have the car or the A3 printout yet, a photo of any front-load washer panel
     (on a laptop screen or printed) is OK for the washer set. Flag that the result is provisional.
   - Store them **outside the repo** in `/home/shiva-ajay/3D/fixlens-testdata/m0/`, since car photos
     may show number plates. Keep a `manifest.json` there: file → set → target phrase →
     (optional) the expected error code.
4. **Prompt.** Use the real FixLens prompt shape, not a bare question:
   - The system prompt is the Fixy prompt from CLAUDE.md §8, filled with a small realistic
     `<kb_entry>` for that target (e.g. `car_check_engine_oil`) and `<state>current_step=1 of 3</state>`.
   - The user message is the image + the grounding request from CLAUDE.md §9, verbatim.
   - Keep these prompt files in the repo under `tools/m0/prompts/`. We'll reuse them in M1.
5. **Extra tests (a few runs each):**
   - **OCR:** on the display photo, ask "What error code is shown on the display?" Check it
     against the code I tell you is printed.
   - **Refusal:** with the `car_check_engine_oil` KB entry loaded, ask "How do I rewire the
     headlight?" The reply must be the exact refusal line from CLAUDE.md §8, with no invented steps.
   - **Persona:** ask "Who are you?" It must answer as Fixy and never mention Qwen or an AI model.
6. **Runner + drawer script** in `tools/m0/` (Python, stdlib + Pillow only; that's laptop tooling,
   not an app dependency):
   - `run_m0.py`: for each image × model × settings variant, run the model, capture the raw output
     and wall time, parse the first line with the same tolerance rules as CLAUDE.md §9 (strip
     whitespace and markdown fences), and save everything to
     `fixlens-testdata/m0/results/<model>/<variant>/<image>.json`.
   - `draw_boxes.py`: draw the box on the original photo **twice**, once assuming 0–1000
     normalized and once assuming pixels of the resized input (work out the resize MNN actually
     applied). Save both side by side. The one that lands on the part tells us the scale.
   - Before trusting either, **draw a synthetic known box** (e.g. the centre quarter of the
     image) through the same mapping code to prove the drawer itself is correct.
7. **Show me the drawn images** (read them yourself too) and score each one: correct / partly
   correct (overlaps the part but too big or small) / wrong / null. I have the final say on
   borderline scores.
8. **Variants to compare (keep the matrix small):**
   - 4B vs 2B
   - temperature 0.2 vs greedy
   - image size default (420) vs ~640 long side, only if MNN allows it and 420 fails on small
     parts like the dipstick handle

### Part B: Latency on the phone (MNN Chat)

1. Push the model(s) to the directory MNN Chat scans:
   ```bash
   adb shell mkdir -p /data/local/tmp/mnn_models
   adb push /home/shiva-ajay/3D/fixlens-models/qwen3-vl-4b /data/local/tmp/mnn_models/Qwen3-VL-4B-Instruct-MNN
   ```
   Verify the exact folder-naming rules and whether MNN Chat needs its own `config.json` tweaks
   (check the MnnLlmChat source). Also push the photos to `/sdcard/Pictures/fixlens-m0/`.
2. Give me a short, exact click-path for MNN Chat: open the local model, set the backend
   (**try OpenCL/GPU and CPU**), set the sampler/temperature if the app allows, attach a photo,
   paste the prompt. Put the ready-to-paste prompt text in `tools/m0/prompts/phone_prompt.txt`
   and copy it to the phone clipboard if that's possible.
3. For **at least 3 photos per model per backend**, record: model load time, time to first token
   / box line, total time, and the prefill/decode tok/s MNN Chat shows. If practical, read these
   and the output text via `uiautomator dump`. Otherwise I'll read them off the screen and tell you.
4. Thermal: run 10 questions back to back on the best config and note whether the speed drops.
5. Also note RAM: `adb shell dumpsys meminfo com.alibaba.mnnllm.android.release` while the model
   is loaded.

### Part C: Fallback if the laptop build fails

Do all tests from Part A manually in MNN Chat on the phone. Copy the raw outputs from the phone
(uiautomator dump, or I paste them to you). Then use `draw_boxes.py` on the laptop to draw and score.

---

## Decision rules (from CLAUDE.md §14 and PLAN.md M0)

| Result | Decision |
|---|---|
| 4B passes accuracy + format, latency ≲ 3 s | **Use 4B.** Record the coordinate scale |
| 4B accurate but too slow / throttles | **Use 2B**, if 2B also passes accuracy |
| Qwen3-VL boxes poor on our targets | Try **Qwen2.5-VL-3B MNN int4** (absolute-pixel coords). Ask me before downloading |
| Nothing in MNN works | Recommend the emergency path: **Gemma 3n / MediaPipe**. The phone already has Google AI Edge Gallery installed for a quick sanity check |

If the results are borderline, don't decide on your own. Show me the numbers and your recommendation.

---

## Deliverables

1. `docs/m0-model-check.md`, containing:
   - the environment (MNN commit hash, how it was built, the model repos + sizes)
   - the results table per image (model, variant, correct?, JSON ok?, coords, time)
   - phone latency table (model × backend) + thermal + RAM
   - OCR / refusal / persona results
   - **the decision:** model, backend, temperature/sampler, max tokens, image size, **coordinate
     scale for `BoxMapper`**, and any prompt changes that improved format-following
   - problems and gotchas for M1 (e.g. how the image is passed to the native API, resize behaviour)
2. `tools/m0/` scripts + prompt files (in the repo). Photos and results stay out of the repo.
3. `CLAUDE.md` §15: tick `M0 model check`, and add 2–4 short notes (chosen model, coord scale,
   measured latency, backend).
4. `PLAN.md`: mark M0 done, and note any change to M1 that follows from the decision.
5. A git commit of docs + tools (show me `git status` and the message first). If P0's first commit
   doesn't exist yet, tell me and don't commit on top of an empty history without asking.
6. A short final report to me: the decision in one line, the evidence in 3–5 lines, anything I
   still need to do on the phone, and the first concrete step of M1.

## Ground rules

- Anything needing a tap on the phone, a photo or `sudo`: give me the exact steps in one batch.
  Don't trickle requests.
- Don't modify the original model folders or the MNN Chat app. Work on copies of configs.
- No new app dependencies. Laptop-side tooling is Python stdlib + Pillow only, unless you ask first.
- Don't guess the coordinate scale or tag syntax. Verify both in source code or by drawing.
- Report honestly. If a test was skipped or a number is an estimate, say so.
