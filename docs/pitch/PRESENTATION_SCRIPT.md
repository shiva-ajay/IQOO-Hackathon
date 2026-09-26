# FixLens: Round 1 presentation script

Deck: [FixLens-Round1.pptx](FixLens-Round1.pptx) (6 slides; the script is also in each slide's speaker notes).

**Full version:** about 7 minutes at a relaxed pace. If you have only 3 to 4 minutes, use the
[short version](#short-version-3-minutes) at the end.

How to read this:
- **On screen** is what the jury sees.
- **Say** is the script. Say it in your own words. It doesn't have to be word for word.
- **Tip** is a delivery note.

---

## Slide 1: Title (about 40 seconds)

**On screen:** the FixLens logo, "Point. Ask. Fix.", and Fixy waving inside the orange marker.

**Say:**
> Hi everyone. I'm [your name], and I'm building this solo.
>
> My project is called FixLens. The idea fits in three words: point, ask, fix.
>
> You point your phone camera at something that's broken, like a washing machine or a car's
> engine, and you ask your question out loud. A small assistant called Fixy answers you by
> voice. It also puts a marker on the exact part you need to touch.
>
> And the important part: all of this runs on the phone itself. No internet, no cloud.

**Tip:** Smile and slow down on "point, ask, fix". It's your hook, and you'll repeat it at the end.

---

## Slide 2: The problem (about 1 minute)

**On screen:** a washing-machine display showing "E4", three pain points, and the insight box.

**Say:**
> Let me start with a situation most of us have been in.
>
> Your washing machine stops, and all it shows is "E4". That's it. Just a code and a
> blinking light.
>
> So what do you do? The manual is long gone. You call the helpline and wait in a queue. Or
> you book a technician, which costs money and often takes a few days.
>
> And here's the funny thing: these machines usually break in places with bad signal. Utility
> rooms, basements, terraces, parking lots. So even searching online or using a cloud AI app
> is hard right there.
>
> But the real insight is this: most of these fixes are actually simple. A clogged filter. A
> loose cover. Low fluid. People can do it themselves. They just don't know where to look.
>
> That's the gap FixLens fills.

**Tip:** Ask the room "Has this happened to anyone?" and pause for a second. It gets nods and
makes the problem feel real.

---

## Slide 3: The solution, meet Fixy (about 1 min 30 sec)

**On screen:** a phone showing a washing-machine panel with the orange marker on the drain
filter and a short conversation, plus Fixy's five abilities.

**Say:**
> So meet Fixy. Think of Fixy as a patient friend who knows repairs and is standing right
> next to you.
>
> Here's what Fixy does. Five things.
>
> One, it **sees**. It looks through the camera and reads the error code or looks at the engine.
>
> Two, it **listens**. You just talk to it. Your hands stay free, because they're usually
> busy or dirty.
>
> Three, and this is the key part, it **points**. It draws a marker on the exact part, like
> this drain filter here. And when you move the phone, the marker stays stuck to that part.
>
> Four, it **talks you through** the fix one step at a time. You say "done", and it moves to
> the next step.
>
> And five, it **knows its limits**. If it doesn't have verified guidance, it doesn't guess.
> It says so. And for anything dangerous, it tells you to call a technician.
>
> All of this works in airplane mode.
>
> Now, a quick word on what's new here. Offline, on-device AI is expected at this hackathon
> now. What makes FixLens different is the full loop: you have a live voice conversation, the
> AI finds the exact part in the camera view, and the marker follows it in real time.

**Tip:** Point at the phone mockup when you say "like this drain filter here". The last
paragraph is your novelty claim, so say it clearly and don't rush it.

---

## Slide 4: Architecture (about 2 minutes)

**On screen:** the "two speeds" diagram. Dark boxes are the slow brain, orange boxes are the
fast eyes.

**Say:**
> Now, how does it work? There's one big technical problem to solve.
>
> The AI model that understands images is smart, but slow. It takes one to three seconds to
> answer. But the camera gives us thirty frames every second. If the marker only updated
> every few seconds, it would jump around and feel broken.
>
> So I split the app into two speeds. A slow brain and fast eyes.
>
> The **slow brain** is the dark boxes. When you ask a question, your voice is turned into
> text right on the phone. The orchestrator finds the right repair entry in our knowledge
> base. Then it grabs the current camera frame and sends everything to the vision model,
> Qwen3-VL, running on the phone. The model answers in two parts. First, a box around the
> part. Then one or two short sentences that Fixy speaks out loud.
>
> The **fast eyes** are the orange boxes. The box from the brain goes to a tracker. The
> tracker follows that part in every single frame, thirty times a second, using optical flow
> from OpenCV.
>
> There's one clever trick here. By the time the brain answers, the phone has already moved
> a bit. So the app keeps the last three seconds of frames. The tracker starts from the old
> frame the brain looked at and quickly catches up to the live view. So the marker lands in
> the right place even though the answer was late.
>
> And if the tracker ever loses the part, it quietly asks the brain again, "where is it
> now?", and locks back on.
>
> The brain runs only when you ask something. Never on every frame. That keeps the phone fast
> and cool.

**Tip:** This slide scores "technical depth". Trace the arrows with your hand: voice → orchestrator
→ model → box → tracker → marker. The "catch up from the old frame" trick is the most
impressive detail, so slow down there.

---

## Slide 5: Trust by design (about 1 min 20 sec)

**On screen:** four trust cards, speed targets, the open-source stack, and Fixy with a caution sign.

**Say:**
> Now, when you're giving repair advice, being trustworthy matters more than being clever. So
> I built trust in from the start.
>
> First, **offline and private**. The app doesn't even have internet permission. Your camera
> and your voice never leave the phone. You can check that in the app's permissions yourself.
>
> Second, **verified steps only**. The AI is never allowed to make up repair steps. Every
> step comes word for word from a checked knowledge base. Error codes are looked up exactly,
> so "E4" never gets confused with "E5".
>
> Third, **safety first**. Before any repair step, Fixy gives you the safety steps, like
> "unplug the machine" or "turn the engine off and let it cool". You have to say "done"
> before it moves on.
>
> And fourth, it **knows when to stop**. If you ask about something risky, like car wiring,
> Fixy won't guess. It tells you to call a technician.
>
> On speed, these are my targets on the iQOO 15: about two to three seconds from your
> question to Fixy's first word, thirty frames per second for the marker, and zero bytes
> sent to any server. Everything is built on open-source models and tools.

**Tip:** Say "these are my targets". Don't present them as measured results until you've
measured them on the phone.

---

## Slide 6: Demo and what comes next (about 50 seconds)

**On screen:** the two demo targets, four roadmap items, and "Point. Ask. Fix."

**Say:**
> For the final demo, I'll show two things.
>
> First, a real car in the parking lot. Fixy will help check the oil, coolant, washer fluid
> and battery. Then I'll ask it about wiring on purpose, and you'll see it refuse and suggest
> a mechanic. That's by design.
>
> Second, a washing-machine control panel. We read an error code, and Fixy guides us step by
> step to the exact part.
>
> After this hackathon, the plan is Telugu and Hindi, a "Hey Fixy" wake word, more appliances
> like fridges, water purifiers and inverters, and a version for junior field technicians
> who can't upload videos from customers' homes.
>
> So that's FixLens. Point, ask, fix. No internet, no manual, no waiting.
>
> Thank you. I'm happy to take questions.

**Tip:** End by looking at the jury, not the screen. Stop talking after "Thank you".

---

## Short version (3 minutes)

Use this if the time limit is tight. Same slides, fewer words.

1. **Title (15 s):** "I'm [name], building solo. FixLens: point your phone at something broken,
   ask out loud, and Fixy answers and points at the exact part. It all runs on the phone, with no internet."
2. **Problem (30 s):** "Your washer shows 'E4'. There's no manual, the helpline has a queue, and a
   technician costs money and days. It usually breaks where the signal is bad. And most fixes
   are simple. People just don't know where to look."
3. **Solution (40 s):** "Fixy sees, listens, points, talks you through it step by step, and knows
   its limits. It works in airplane mode. What's new is the loop: live voice, the AI finding the
   exact part, and a marker that stays locked on it as you move."
4. **Architecture (50 s):** "The vision model is smart but slow, one to three seconds. The camera
   is thirty frames a second. So there are two speeds: the slow brain answers once per question
   with a box and a sentence, and the fast eyes track that box every frame. The app buffers three
   seconds of frames, so the tracker catches up from the frame the brain saw. If the part is lost,
   it re-asks."
5. **Trust (30 s):** "No internet permission. Steps come word for word from a verified knowledge
   base. Safety steps come first. Anything risky, like wiring, goes to a technician."
6. **Close (15 s):** "Demo: a real car and a washer panel. Next: Telugu and Hindi, a wake word, more
   appliances. Point, ask, fix. Thank you."

---

## Likely questions and simple answers

**Asked in Round 1 — "How often would you actually use this in real life?"** and
**"What if Gemini brings an offline model like this — what's the difference?"**
See [ROUND2_PITCH.md](ROUND2_PITCH.md) for the full simple-English answers and a memorized
soundbite for each. Short version: the fire-extinguisher comparison (not daily, but essential
the one time you need it, plus a daily tool for maintenance staff) and the "the model is just
the engine, FixLens is the whole car" comparison (verified steps + live tracking + safety
rules is the product, and it's designed so a better model can be swapped in later).

**"Why not just use ChatGPT or Google Lens?"**
They need the internet, and they don't track the part live or guide you step by step. Also,
a general chatbot can make up repair steps. Fixy only uses steps from a verified knowledge
base, and it refuses when it doesn't know.

**"What if the AI points at the wrong part?"**
The step text comes from the knowledge base, so the instruction itself is always correct.
If the marker drifts or loses the part, the app re-asks the model with a fresh frame. And you
can always say "repeat" or ask "where exactly?".

**"Why a JSON knowledge base and not vector search (RAG)?"**
Error codes are exact identifiers. Vector search can return a neighbouring code, like E5 for
E4, and for repair advice that's a safety problem. With a small, checked set of entries, exact
lookup is more accurate and easier to explain. Hybrid search is on the post-hackathon roadmap.

**"How does it run such a big model on a phone?"**
Qwen3-VL-4B is compressed to 4-bit (about 3 GB) and runs on Alibaba's open-source MNN engine,
using the Snapdragon 8 Elite Gen 5. It loads once at startup and only runs when you ask
something, never on every frame. There's a smaller 2B model as a fallback if speed or heat
becomes a problem.

**"How do you prove it's offline?"**
The app doesn't request the internet permission at all, so Android won't let it go online. We
demo it in airplane mode.

**"Where are you right now in the build?"**
Answer honestly. As of today: the Android project and build toolchain are set up, the models
(Qwen3-VL 4B and 2B, the Moonshine speech models and Silero VAD) are downloaded, the MNN
engine has been studied and its native library built, and the logo and Fixy are designed.
Next is the on-phone model check, then camera → model → box on screen, then the tracker,
voice, and the guided knowledge-base flow. Update this answer on the day.

**"Who is this for?"**
Households with an error code and no manual, building-maintenance staff, and later, junior
field technicians who can't upload video from customers' homes.

**"Can it do any appliance?"**
Not yet, on purpose. For the demo it covers a car engine bay (user-serviceable checks only)
and one washing-machine brand's common error codes. Adding an appliance means adding verified
entries to the knowledge base, not retraining the model.

---

## Before you present

- [ ] Fill in [your name] in the slide 1 script, then practise the whole thing once out loud with a timer.
- [ ] Open the deck in presenter view so the speaker notes show on your screen.
- [ ] Update the "Where are you in the build?" answer with what's working on the day.
- [ ] If anything is working on the phone, show it in 20 seconds after slide 4. A live box on
      screen beats any slide.
