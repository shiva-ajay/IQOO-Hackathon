# FixLens demo: photos, what to say, what the judges should see

Three short demos, each proving a different part of FixLens. Open the photos full-screen on the laptop, point the
phone at the screen (about 30–40 cm away, laptop brightness high), hold the mic and speak. Everything runs on the phone
in airplane mode.

| # | Demo | Time | What it proves |
|---|---|---|---|
| 1 | Car engine oil check | ~2 min | Verified steps from the KB (not made up), safety first, pointing + tracking, the how-to animations, the camera auto-check |
| 2 | Washer won't drain (error code) | ~1 min | Error codes from any brand map to one verified fix; turn-direction animations on the filter |
| 3 | "I can't fix this" | ~30 s | Fixy refuses dangerous jobs, stops a guide at a danger sign, and shows technicians to call |

Before you start: open FixLens and start a **new session** (so earlier chats don't get in the way), wait for the
greeting. Always name the appliance in your **first** question ("my car…", "my washing machine…"): that tells Fixy
which part of the KB to use.

How a guide works: Fixy reads each step out word for word from the KB and marks the part. Say **"done"** (or
"next") to go on, **"back"** to go back, **"repeat"** to hear it again, **"stop"** to end. Safety steps must be
confirmed with "done".

---

## Demo 1: car engine oil check

Folder `1_car_oil/`. The concept: the owner has never checked their oil. Fixy walks them through it one step at a
time, points at each part on the real engine and shows how to handle it (pull, push, turn, pour).

| Step | Photo on the laptop | You say | Judges see and hear |
|---|---|---|---|
| Start | `01_engine_bay` | **"How do I check the engine oil in my car?"** | Guide starts: "Let's check your engine oil on the dipstick… Park on level ground and switch the engine off." Card top right: **Engine off** (key turning back). Banner: *Safety first 1 of 2* |
| Safety 2 | `01_engine_bay` | "Done" | "Wait about five minutes for the oil to settle…" |
| Step 1 | `01_engine_bay` | "Done" | "Open the bonnet and prop it up with its support rod." |
| Step 2 | `02_dipstick_handle` | "Next" | "Find the dipstick…" The marker flies to the **yellow/orange dipstick handle** and stays locked on it; move the phone a little to show the tracking |
| Step 3 | `03_dipstick_pulled` | "Next" | "Pull the dipstick all the way out…" Card: **Pull out**; amber chevrons stream upward beside the dipstick |
| Step 4 | `02_dipstick_handle` | "Next" | "Push it back in fully…" Card: **Push back in**; chevrons stream downward |
| Step 5 | `04_dipstick_level` | "Next" | "The oil should be between the two marks near the tip." Card: **Between the marks** (level rises into the green band, tick) |
| Step 6 | `05_filler_cap` | "Next" | "…unscrew the oil filler cap." Card: **Turn anticlockwise**; two amber arrows circle the cap on screen |
| Auto-check | switch to `06_filler_open` and wait ~6 s | nothing | Fixy looks, sees the cap is off and moves on by itself: "I can see that's done" style advance to step 7. **Keep `05` up until you want to show this**, or it will skip ahead |
| Step 7 | `07_pouring_oil` (or `06`) | (auto) or "Next" | "Slowly pour in a little of the oil…" Card: **Pour a little**; drops fall into the opening |
| Step 8 | `05_filler_cap` | "Next" | "Screw the filler cap back on tightly…" Card: **Turn clockwise**; arrows circle clockwise |
| Step 9 | `02_dipstick_handle` | "Next" | "…push the dipstick back in fully." Then "Done": "That was the last step. Nicely done!" |

Good extras during this demo:
- Ask a follow-up in the middle of a step: **"Which one is the dipstick?"** Fixy answers and points again.
- Say **"back"** once to show you can go back a step.
- A second, shorter car guide: say "stop", show `08_coolant_tank` and ask **"Where do I add coolant in my car?"**
  (starts the coolant guide; its safety line says the engine must be cold).

Note: `04_dipstick_level` shows the two marks clearly but no oil on the stick; that's fine for the level animation.

## Demo 2: washing machine won't drain

Folder `2_washer_drain/`. The concept: the washer shows an error code and the user has no manual. Every brand has
its own code for the same fault (LG **OE**, Samsung **5C**, Bosch **E18**, IFB **dPEr**); FixLens knows they all
mean "not draining" and gives one verified fix.

| Step | Photo | You say | Judges see and hear |
|---|---|---|---|
| Start | `01_front_loader` | **"My LG washing machine shows OE."** (no photo of the display needed) | Found by the error code: "Let's find out why the water isn't draining. Switch the machine off and unplug it." Card: **Unplug first** |
| Safety 2 | `01_front_loader` | "Done" | "If it just ran a hot wash, let the water cool for about an hour first." |
| Steps 1–2 | `01_front_loader` | "Done", "Next" | Check the drain hose and where it ends (behind the machine: nothing to point at in the photo, just say "next") |
| Step 3 | `02_flap_closed` | "Next" | "On a front-loader, open the small flap at the bottom front." Marker on the **flap** |
| Steps 4–5 | `03_filter_visible` | "Next", "Next" | Tray and towel; then "Pull out the small drain tube…" Marker on the **drain tube** |
| Step 6 | `03_filter_visible` | "Next" | "…slowly unscrew the round filter anticlockwise." Card: **Turn anticlockwise**; arrows circle the **filter cap** |
| Steps 7–8 | `04_filter_removed` | "Next", "Next" | Clean the filter; check the opening is clear. Marker on the **filter hole** |
| Step 9 | `03_filter_visible` | "Next" | "Screw the filter back in firmly, clockwise…" Card: **Turn clockwise** |
| Step 10 | `01_front_loader` | "Next" | "Plug it in and run a spin-only cycle. If it still won't drain, please call a technician." |

Try the same with another brand to make the point: **"My Samsung washer shows 5C"** starts the same guide.

## Demo 3: "I can't fix this" (Fixy says call a mechanic)

Folder `3_cant_fix/`. The concept: FixLens knows its limits. For dangerous faults it gives **no repair steps**, says
why, shows a red **Call a technician** card and a row of **technicians near you** (sample cards, no network).

Pick one or two of these:

| Photo | You say | Judges see and hear |
|---|---|---|
| During demo 1, any photo | **"There's a burning smell."** (or "I see smoke") | The oil guide **stops at once**: "That's a warning sign: burning smell. This one is a job for a technician. Please don't try it yourself." Red card + *Technicians near you: Car mechanic* |
| `01_burnt_wiring_car` | **"The wires in my car engine look burnt. Can I fix them?"** | "Wiring and electrical faults can hurt you or damage the car, so they need a technician." Red card, car mechanics row |
| `02_smoke_engine` | **"Smoke is coming from my car engine."** | "Smoke or a burning smell isn't safe to check yourself. Stop somewhere safe, switch the engine off, step away and call a mechanic." |
| `03_burnt_plug_socket` | **"My washing machine plug is melted."** | "That's a fault inside the machine. With dry hands, switch it off at the wall and turn off its tap." Washing machine technicians row |
| `04_swollen_battery` (a phone battery; the KB has laptops, not phones) | **"My laptop battery is swollen."** | "Please stop using the laptop and unplug the charger. Don't press, puncture, or try to remove a swollen battery." |

The technician cards are **sample data** (made-up names and example prices), labelled "Sample" on screen. In the
pitch: "a booking partner plugs in here".

---

## If something goes wrong

- **No guide starts** (Fixy just answers): you probably didn't name the appliance. Say it again with "my car" or
  "my washing machine".
- **The marker lands on the wrong part:** move the phone so the part is bigger and centred, or ask "Where is the
  dipstick?" again. Screen glare also hurts: tilt the phone slightly.
- **The guide skips a step by itself:** that's the auto-check deciding the step is done (step 6 of the oil check
  looks for the cap being off). Say "back".
- **Drive it from the laptop instead of voice** (same result, handy for rehearsing):
  ```bash
  adb shell "am start -n com.fixlens/.app.MainActivity --ez new true"          # new session
  adb shell "am start -n com.fixlens/.app.MainActivity --es ask 'how do I check the engine oil in my car'"
  adb shell "am start -n com.fixlens/.app.MainActivity --es ask 'next'"
  ```

## Photo credits

All photos are freely licensed (CC BY / CC BY-SA / public domain), from Wikimedia Commons and Flickr. Authors,
licences and source links are in each folder's `CREDITS.md`. Some are cropped.
