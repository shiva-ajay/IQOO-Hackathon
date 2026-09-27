# Sources: washing machines (`washing_machine`)

Checked 2026-09-27. These are generic entries for front-load and top-load washers, built from several makers' official
sources under the consensus rule. For the Samsung and IFB manuals the PDF page number and the printed page number are
the same, so "p.N" works for both. All steps are written in our own words, and no manual text or figures are shipped.

## Documents used

| Short name | Document | URL | Notes |
|---|---|---|---|
| Samsung manual | Samsung WW4000T front-load user manual (WW70T4020EE/TL, DC68-04288A-01, 64 pp.), Samsung India download centre | https://org.downloadcenter.samsung.com/downloadfile/ContentsFile.aspx?CDSite=UNI_IN&OriginYN=N&ModelType=N&ModelName=WW70T4020EE%2FTL&CttFileID=8370780&CDCttType=UM&VPath=UM%2F202111%2F20211122181531843%2FWW4000T-MD_LED_DC68-04288A-01_EN.pdf | Safety pp.8-10, 12; install pp.18, 21; child lock p.42; drum clean p.43; emergency drain p.45; mesh filter p.47; debris filter p.48; drawer p.49; troubleshooting pp.51-54; information codes pp.55-57 |
| Samsung IN 4E/5E | Samsung India, "How to resolve 4E or 5E codes on your Samsung washing machine" (updated 20 Jun 2025) | https://www.samsung.com/in/support/home-appliances/how-to-resolve-4e-or-5e-codes-on-your-samsung-washing-machine/ | 4E = 4C, 5E = 5C; "allow about one hour for the water to cool"; says the debris filter is on front-loaders only. Only the front-load tab showed up as text |
| Samsung US codes | Samsung US, "Samsung washing machine information and error codes" (TSG10000997) | https://www.samsung.com/us/support/troubleshoot/TSG10000997/ | Groups codes by fault: no drain (nd, 5E, SE, 5C), not filling (4C, nF, 4E), door (dE, dC…), unbalanced (Ub, U6, UE…), overflow (OE/OC), and "all other errors" (restart, then service) |
| LG error list | LG U.A.E. help library, "LG Front Load Washer - Error Code List" | https://www.lg.com/ae/support/product-help/CT20076046-20153792986245 | This page renders as text. OE, IE, UE, CL, LE, tcL, dE/dE1/dE2, CE, Sud, Cd, FE, PE, PF, tE, dHE, HE, SE, AE |
| LG drain filter | LG USA help library, "LG Washer – How to Clean the Drain Pump Filter" | https://www.lg.com/us/support/help-library/lg-washer-how-to-clean-the-drain-pump-filter--20150206838321 | Shallow pan, drain hose plug, "have a towel handy", anticlockwise, clockwise until it stops. Curl got 403, so it was read with WebFetch |
| LG power | LG USA help library, "Why won't my LG washing machine power on?" | https://www.lg.com/us/support/help-library/lg-washer-why-wont-my-lg-washing-machine-power-on--20150791852235DRC | Plug straight into the wall with no extension cord, check the breaker, test the outlet with a lamp, then electrician or service. Read with WebFetch |
| LG UK door | LG UK help library, "[LG front load washing machine door] The door does not open" | https://www.lg.com/uk/support/product-support/troubleshoot/help-library/cs-CT00008362-20153082709866/ | Locked while running, hot, or with water inside; pause and wait for the click; spin-only to drain. Read from the page's JSON-LD articleBody |
| LG CA door | LG Canada help library, "Troubleshooting an LG Front Load Washing Machine Locked Door" | https://www.lg.com/ca_en/support/product-support/troubleshoot/help-library/cs-CT32012082-20152946712429/ | No-spin drain, wait for it to cool, unplug 3-5 min, power cut keeps it locked. JSON-LD articleBody |
| Bosch E18 | Bosch India, "What to do if washing machine shows E18 in display?" | https://www.bosch-home.in/service/get-support/e18-in-display | E18 = F18 = d02. Close the tap, switch off and unplug, let the water cool (scald warning), service flap, emergency drain hose, pump cover, impeller must turn, test with the drain programme, drain hose kinks |
| Bosch codes | Bosch India washing machine error pages (E16/F16, E17/F17, E19/F19, E20/F20, E23/F23, E25/F25, E26/F26, F27, E28/F28) | https://www.bosch-home.in/service/get-support/washing-machine-errors (each code links from here, e.g. `/service/get-support/error-e17-or-f17-washing-machine`) | E16 door open: close it. E17 water supply: open the tap. E19-E28: "can't be rectified by itself", call service. E20: reset on/off first |
| IFB manual | IFB Senator Plus / Executive / Executive Plus front-load user manual, UF421PKMAN752 rev. H (79 pp.) | https://imagestore.ifbhub.com/Adobe/Manuals/bis/FL_752_H_Digital.pdf | Safety pp.12-16; transit bolts and levelling pp.21-22; child lock p.39; emergency door release p.40; drawer p.58; mesh filter p.59; drain filter p.60; tub clean p.61; troubleshooting pp.64-71; display codes pp.73-76 |
| Whirlpool FL codes | Whirlpool product help (US), "Error Codes in Front Load Washers" | https://producthelp.whirlpool.com/Laundry/Washers/Product_Info/Washer_Product_Assistance/Error_Codes_in_Front_Load_Washers | LOC/LC, F5 E2, F7 E1, F8 E1/LO FL, F9 E1, Sud, "other F# E#: unplug 5 min, then service" |
| Whirlpool TL codes | Whirlpool product help (US), "Error Codes in Top Load HE Washers" | https://producthelp.whirlpool.com/Laundry/Washers/Product_Info/Washer_Product_Assistance/Error_Codes_in_Top_Load_HE_Washers | drn, LdL, LdU, LF/Lo FL/F8 E1, lid, OFb, OL, PF. The top-load side of several steps comes from here |
| Whirlpool care | Whirlpool product help (US): "Removing Odors or Smells - Front Load Washer", "Cleaning the Door Seal - Front Load Washer", "Cleaning the Drain Pump Filter" | https://producthelp.whirlpool.com/Laundry/Washers/Front_Load_Washers/Wash_Performance_or_Clothing_Results/Cleaning_and_Maintenance/Removing_Odors_or_Smells_-_Front_Load_Washer (and the `Cleaning_the_Door_Seal_-_Front_Load_Washer`, `Cleaning_the_Drain_Pump_Filter` pages in the same folder) | Leave the door open, Clean Washer cycle monthly, clean the dispenser, use the recommended amount of HE detergent, wipe the seal, flat container plus a cotton cloth under the drain filter |
| ApplianceDB | ApplianceDB public sample, `error_codes.csv` + `repair_procedures.csv` | https://github.com/ApplianceDB/ApplianceDB-public | ODbL 1.0. Used only to check which codes mean which fault (`component` / `cause_category`), never as the source of a code or step (see the licence notes) |

### Tried but not usable

- **Whirlpool India:** no official Whirlpool India manual or error-code page could be found. The Whirlpool codes come from
  Whirlpool's US product-help site (official, but for US models), so Indian Whirlpool models may use other codes.
- **LG India help library:** the pages are rendered by JavaScript. LG's U.A.E., UK, Canada and US pages were used instead.
- **LG U.A.E. drain-filter page** (`CT20076046-20153920233575`): renders by JavaScript. The US page has the same procedure.
- **Whirlpool blog** (`whirlpool.com/blog/...smells-in-front-load-washer`): blocked (HTTP 403). The product-help pages covered it.
- **ApplianceDB Bosch rows** come from an aggregator (`appliancecodehub.com`), so they were not used. The Bosch codes
  come from Bosch India's own pages.

---

## washing_machine_not_draining (caution)

| Line | Agreeing sources |
|---|---|
| Safety: switch off and unplug | Samsung manual p.48 (debris filter step 1) and p.45; Bosch E18 ("turn off… pull out the main plug"); IFB p.13 (disconnect before cleaning) |
| Safety: after a hot wash, let the water cool about an hour | Samsung IN 4E/5E ("approximately one hour"); Samsung manual p.10 (drained water is hot); Bosch E18 ("allow the washing water to cool down", scald warning); IFB p.40 ("let the wash water cool down if it is hot") |
| 1 Drain hose not bent, squashed or blocked | Samsung manual p.52, p.55 (5C); LG error list OE step 1; IFB p.67, p.75 (dPEr); Bosch E18 §2; Whirlpool FL codes F9 E1 |
| 2 Hose end placed in the drain as the manual shows | Samsung manual p.55 (5C "positioned correctly, depending on the connection type"; LC "end not on the floor"); IFB p.67 (end no more than 1 m above the floor); Whirlpool F9 E1 (standpipe 99-244 cm). **The heights differ by brand**, so the step says "the way your manual shows" |
| 3 Front-loader: open the small flap at the bottom front | Samsung manual p.45, p.48 (filter cover); IFB p.60 (bottom door, "with a coin or a key"); Bosch E18 (service flap); LG drain filter (lower-front access panel); Whirlpool care (drawer or panel at the base). Front-loaders only: Samsung IN 4E/5E ("Front Load only") |
| 4 Wide, shallow tray and a towel | Samsung manual p.45 ("spacious container"); IFB p.60 (shallow container); Bosch E18 (suitable container); LG drain filter (shallow pan, "have a towel handy"); Whirlpool care (broad, flat container, cotton cloth) |
| 5 Drain tube: pull out, uncap, drain; caution about the amount of water | Samsung manual p.45 (tube cap; "the water may be more than expected"); IFB p.60 (drain hose + plug); Bosch E18 (drain hose, sealing cap); LG drain filter; Whirlpool care ("empty the container, repeat") |
| 6 Cap the tube, unscrew the filter anticlockwise; a little more water comes out | Samsung manual p.48 (knob to the left); IFB p.60 (anti-clockwise); LG drain filter (counter-clockwise, towel); Whirlpool care (counterclockwise, cloth); Bosch E18 ("remaining water may leak out") |
| 7 Clear lint, coins and hair, rinse the filter | Samsung manual p.48 (soft brush); IFB p.60 (coins, pins, hooks); LG drain filter (lost items, lint, wash by hand); Whirlpool care (lint by hand, rinse) |
| 8 Opening clear, propeller can turn | Samsung manual p.48 ("drain pump propeller… unclogged"); Bosch E18 ("the impeller in the drain pump must be able to rotate", clean the housing) |
| 9 Refit firmly clockwise, close the flap; caution: loose filter leaks | Samsung manual p.48 (knob to the right; caution: not closed properly → leak); IFB p.60, p.66 (tighten; loose filter → leakage); LG drain filter (clockwise until it stops); Whirlpool care; Bosch E18 (screw back firmly) |
| 10 Test with spin-only / drain cycle, then technician | Samsung IN 4E/5E (start a cycle; if it doesn't drain, request service); LG error list OE (spin-only test; "may require a repair service"); Bosch E18 (drain programme, check for leaks; "defective pump"); IFB p.67 ("contact IFB Care"); Samsung manual p.55 |

**Brand codes**

| Brand | Codes | Where |
|---|---|---|
| LG | OE | LG error list |
| Samsung | 5C, 5E, SE, nd, LC, LC1 | 5C, LC, LC1: Samsung manual p.55 (LC/LC1 = check the drain hose). 5E: Samsung IN 4E/5E. SE, nd: Samsung US codes ("No drain error") |
| Bosch | E18, F18, d02 | Bosch E18 ("F18 and d02 faults are the identical ones") |
| IFB | dPEr | IFB p.75 |
| Whirlpool | F9E1, drn | Whirlpool FL codes (F9 E1), TL codes (drn). US models |

**Dropped:** LG's "listen for the pump hum" test (LG only, and it tests the pump rather than being a fix). Excess suds
as a cause (LG OE, Whirlpool F9 E1): the detergent advice is in `washing_machine_bad_smell`. Bosch's "pour 1 litre of
water into compartment II" (Bosch only). Frozen hoses (Samsung, LG; not relevant in India). Samsung's "check the
level" (Samsung India only for this symptom; levelling is in the shaking entry). Top-loaders mostly have no
user-reachable drain filter (Samsung IN 4E/5E, Whirlpool TL codes has none), so step 3 tells them to skip to the last
step. `escalate_if` leaves out "water on the floor", because some spilling is normal while draining the filter.

## washing_machine_not_filling (diy)

| Line | Agreeing sources |
|---|---|
| Safety: switch off and unplug before touching the hoses | Samsung manual p.47 (mesh filter step 1); IFB p.59 ("turn OFF taps and disconnect the power supply") |
| 1 Tap fully on | Samsung manual p.51, p.55 (4C); Samsung IN 4E/5E; LG error list IE; IFB p.65, p.74 (tAP); Bosch E17 ("open the tap"); Whirlpool FL/TL codes (F8 E1, LF) |
| 2 Household supply on; wait if it's off | Samsung manual p.51, p.55 (sufficient water pressure); LG error list IE (water is cut off); IFB p.74 ("operations will restart when water supply is available"); Whirlpool F8 E1 (household supply turned on) |
| 3 Straighten kinks in the inlet hose | Samsung manual p.51; Samsung IN 4E/5E; LG error list IE; IFB p.65, p.74; Whirlpool F8 E1 |
| 4 Door or lid shut | Samsung manual p.51 ("make sure the door is properly closed" under no water supply); IFB p.65 (door not securely closed → water not supplied); Whirlpool TL codes (lid open → cycle resets and drains) |
| 5 Tap off, unscrew the inlet hose at the back; caution: cloth | Samsung manual p.47 (close tap, disconnect, cover with a cloth); Samsung IN 4E/5E; IFB p.59; LG error list IE (turn off the faucet first) |
| 6 Pull the mesh filter with pliers, rinse it | Samsung manual p.47 (pliers, from the inlet valve); Samsung IN 4E/5E (pliers, from the hose end); IFB p.59 (mesh at the tap end + sieve in the valve, flat-nosed pliers); LG error list IE (fingers or pincers). The location differs by model, which the caution says |
| 7 Refit, reconnect, open the tap, check for drips | Samsung manual p.47; Samsung IN 4E/5E ("make sure the connections are watertight"); IFB p.59 (check watertight); LG error list IE (reassemble the filter before reconnecting) |
| 8 Retry, then technician | Samsung manual p.55; LG error list IE ("malfunction in the water supply valve"); Bosch E17 (service); IFB p.73 (codes not fixed → IFB Care); Whirlpool FL codes |
| escalate_if: damaged / cracked hose | IFB p.66 ("inlet hose is leaking → contact IFB Care"), p.74 (replace a damaged hose); LG error list AE (inlet hose cuts) |

**Brand codes:** Samsung 4C (manual p.55), 4E (Samsung IN 4E/5E), nF (Samsung US codes); LG IE (LG error list); Bosch
E17, F17 (Bosch codes); Whirlpool F8E1, LF, LoFL (Whirlpool FL/TL codes, US).
**Left out:** IFB `tAP`. It is the everyday word "tap", which the validator rejects, so the phrases "showing tap" and
"tap error" are in `symptoms` instead. Samsung 4C2 and Whirlpool HC (hot and cold hoses swapped) aren't relevant for
cold-only Indian installs (IFB p.12: don't connect to hot water).
**Dropped:** anti-flood hoses (Whirlpool only); frozen taps (Samsung, LG; not relevant in India); Samsung's "dry the
mesh filter in the shade" (one source; the Samsung India page just rinses it); Samsung US drain-hose insertion depth
(US only).

## washing_machine_door_problem (caution)

One entry for "won't open" and "won't lock/close" together. The words users say for the two overlap ("door is locked,
can't open"), so two entries tied in retrieval. Each branch step starts with "If…" or "Once…".

| Line | Agreeing sources |
|---|---|
| Safety: never force it; water and clothes can be very hot | Samsung manual p.9 (don't force the door during high-temperature washing: burns); IFB p.73 (dLEr: "do not forcibly open"), p.40 (let the water cool); LG UK door / LG CA door (locked while hot) |
| 1 Press Start/Pause, wait for the drum to stop | Samsung manual p.52 (press Start/Pause to stop); IFB p.64 (pause the program), p.16 (drum stationary); LG UK door (pause); LG CA door |
| 2 Wait a few minutes for the click; caution: a hot wash or power cut takes longer | Samsung manual p.52 (opens 3 min after stopping or power off); IFB p.73 (dLEr: switch off, wait 2 min); LG UK door (clicking sound; hot course → wait); LG CA door (3-5 min; a power cut keeps it locked); IFB p.40 |
| 3 Child Lock off (often two buttons, about 3 s) | IFB p.64 (Child Lock active → disable), p.39 (Spin + Temp 2 s; door locked till the end); Samsung manual p.42 (Temp + Spin 3 s); LG error list CL (3 s, "may be a combination of two buttons"); Whirlpool FL codes (LOC) |
| 4 Water inside → drain or spin-only cycle | Samsung manual p.52 (door won't open with water; lock light goes off after draining); LG UK door (Spin Only); LG CA door (No Spin drain); IFB p.64 (high water level) |
| 5 Clothes caught in the seal or under the lid | Samsung manual p.55 (dC); LG error list dE1/dE2 (between the door and the rubber gasket); Samsung US codes; Whirlpool FL codes F5 E2; Whirlpool TL codes LdL (items under the lid) |
| 6 Hook and catch bent or broken → technician | LG error list dE2 ("latch… bent or broken"); Samsung US codes (damage to the door or latch → service) |
| 7 Push shut until it clicks | Samsung manual p.55 (properly closed); LG error list dE (open and re-close); Bosch codes E16 ("close the door"); Whirlpool F5 E2 (closing completely); IFB p.73 ("door": close properly) |
| 8 Switch off at the wall a few minutes, retry | LG error list dE (power off/unplug reset); LG CA door (unplug 3-5 min); IFB p.73 (switch off, wait 2 min); Samsung manual p.56 (DC1: off and restart); Whirlpool FL codes (unplug 5 min) |
| 9 Still stuck → technician, never force | Samsung manual p.56; LG error list dE ("service will be required"); IFB p.73; Bosch codes E16 |

**Brand codes:** Samsung dC, dC1 (manual pp.55-56), dE (Samsung US codes); LG dE, dE1, dE2 (LG error list); Bosch
E16, F16 (Bosch codes); IFB dLEr (p.73); Whirlpool F5E2, LdL (US).
**Left out:** Samsung dE2 (Samsung UK lists it as a power-switch fault, per ApplianceDB). IFB `door` is an ordinary word,
so "showing door" is in `symptoms` instead. IFB E1/E2/E3 (clothes trapped: their fix drains first and then rearranges,
which doesn't fit this order, and "E1" is too generic). Whirlpool LdU (objects on the lid; one source).
**Dropped:** emergency door-release levers and manual draining through the filter (IFB p.40, LG CA). They differ by
model and are for power cuts, so users are pointed to the manual or a technician. LG's "hold Start/Pause 5 s while
unplugged" (LG only). LG ThinQ "Keep remote start on" (LG only). Whirlpool: detergent build-up around the lid lock (one source).

## washing_machine_shaking_unbalanced (diy)

| Line | Agreeing sources |
|---|---|
| Safety: pause and wait for the drum to stop | IFB p.16 (drum stationary before removing clothes); Samsung manual p.9 (don't reach in or open by force while it's operating) |
| 1 Spread the clothes evenly, untangle | Samsung manual p.52 (redistribute), Samsung US codes (untangle and rearrange); LG error list UE; IFB p.67, p.76 (UnbL); Whirlpool TL codes OFb |
| 2 One heavy item → add a few towels | Samsung manual p.52 (a single bathrobe or jeans → Ub); LG error list UE (small loads, single bulky items); IFB p.68, p.76 (add a few clothes); Whirlpool TL codes (balance a single item with towels) |
| 3 Packed tight → take some out | IFB p.68 (overloaded → reduce a few items); Whirlpool TL codes OL (remove several items); LG error list LE (overloaded) |
| 4 Close and restart the spin | Samsung US codes (close the door, restart); LG error list UE (restart the cycle); Whirlpool TL codes (close the lid, press Start/Pause) |
| 5 Machine doesn't rock; adjust the feet | Samsung manual p.21 (levelling feet, check it isn't rocking, tighten the nuts), p.51; IFB p.13, p.22 (all four feet levelled and locked), p.67 (not levelled → vibration); Whirlpool TL codes (levelling video) |
| 6 Solid, level floor, no carpet | Samsung manual p.18 ("solid, level surface without carpeting"), p.51; IFB p.13 (stable level surface, remove carpets) |
| 7 New machine: transit bolts removed | Samsung manual p.18, p.51 (shipping bolts); IFB p.12, p.14, p.21 (transit bolts); Whirlpool FL codes F7 E1 (shipping bolts removed) |
| 8 Still shaking hard → technician | Samsung manual p.54 ("if a problem persists"); IFB p.67 ("contact IFB Care") |

**Brand codes:** Samsung Ub (manual p.52), U6, UE (Samsung US codes); LG UE; IFB UnbL (p.76); Whirlpool OFb, OL (US).
**Dropped:** "not touching another object" (Samsung only); "filter partly clogged causes vibration" (IFB only); the
Bulky/Sheets cycle (Whirlpool only); IFB's "replace a vibrating trolley" (IFB only). Normal motor or pump noise and noise
from coins (Samsung p.51, IFB p.71) are explanations, not steps.

## washing_machine_wont_start (diy)

| Line | Agreeing sources |
|---|---|
| Safety: dry hands on the plug and switches | Samsung manual pp.8-9 (don't touch the plug with wet hands); IFB p.15 (not with damp hands) |
| 1 Plugged straight into a wall socket, no extension cord, switch on | Samsung manual p.51 (plugged in); Samsung US codes (not on an extension cord); LG power (directly into the wall, no extension cord); IFB p.64 (power switch on, plug in securely), p.15 (avoid extension cables) |
| 2 Test the socket with a lamp or charger | LG power (plug in a lamp or fan); Samsung manual p.53 ("plug the power cord into a live electrical outlet") |
| 3 Check the MCB or fuse box; caution: trips again → electrician | Samsung manual p.51, p.53 (check the fuse or reset the breaker); LG power (breaker; electrician if the outlet has no power); IFB p.76 (electrician for supply problems) |
| 4 Door or lid shut until it clicks | Samsung manual p.51; IFB p.65 |
| 5 Water tap on | Samsung manual p.51; IFB p.65 |
| 6 Child Lock (buttons for about 3 s) | Samsung manual p.51, p.42; LG error list CL; IFB p.39; Whirlpool FL codes LOC |
| 7 Choose a program, press Start/Pause | Samsung manual p.51; IFB p.64 (no program selected), p.65 (Start/Pause not pressed). Not "press firmly": IFB p.70 says press the buttons softly |
| 8 Not in a delay start or a soak pause | IFB p.65 (Pause/Soak or Rinse Hold), p.73 (dLAY); Samsung manual p.53 ("there may be a pause or soak period") |
| 9 Still dead → technician | Samsung manual p.54; IFB p.64; LG power ("contact LG's repair service") |

**Brand codes:** LG CL, PF (LG error list); Samsung UC (manual p.56, low voltage / power), PF and CL (Samsung US codes);
Whirlpool LOC, PF (FL/TL codes, US).
**Left out:** IFB h260 / l175 (high or low mains voltage; the machine resumes by itself, and IFB says call an electrician
if it keeps happening). Whirlpool `LC` (Samsung uses LC for the drain hose).
**Dropped:** GFCI outlet reset (US outlets); LG's hard reset (holding Power 5 s; LG only); cleaning a film off touch
panels (LG only); "clicking before it fills is normal" (Samsung only).

## washing_machine_bad_smell (diy)

| Line | Agreeing sources |
|---|---|
| Safety: switch off and unplug before cleaning | IFB p.13 ("before cleaning your machine… remove the plug"); Samsung manual p.48, p.47 (unplug before filter cleaning) |
| 1 Empty the drum; the cleaning program runs empty | IFB p.61 ("empty the drum before running"); LG error list tcL ("empty the tub"); Whirlpool FL codes rL (items detected during Clean Washer) |
| 2 Front-loader: wipe the rubber seal and its folds; caution: pick out hair, coins | Samsung manual p.54 ("clean the door seal"), p.43 (drum clean also cleans the gasket); Whirlpool care, door seal (inspect, pull back the seal, wipe with a damp cloth, check for foreign objects) |
| 3 Drawer out, rinse under warm water with a soft brush | Samsung manual p.49 (flowing water, soft brush, release lever); IFB p.58 (warm water, siphon); Whirlpool care, odours (remove and clean the drawer per the manual) |
| 4 Clean the drawer slot, refit | Samsung manual p.49 (clean the recess with a bottle brush); IFB p.58 (clean the dispenser housing before reinstalling) |
| 5 Run drum clean / tub clean, empty | Samsung manual p.43 (DRUM CLEAN, every 40 washes); LG error list tcL (Tub Clean monthly); IFB p.61 (Tub Clean monthly / every 40 cycles); Whirlpool care, odours (Clean Washer monthly). The names differ, hence the caution |
| 6 Only the detergent amount on the pack | Samsung manual p.54 (excess suds cause odours), p.52 (reduce detergent); LG error list tcL and Sud (reduce detergent); IFB p.66 (excessive detergent); Whirlpool care, odours (no more than the recommended amount) |
| 7 Leave the door slightly open after each wash | Samsung manual p.12 ("to prevent odours and mould, leave the door open"), p.54 (dry the interior); Whirlpool care, odours ("leave the door open between loads") |
| 8 Still smells → technician | Samsung manual p.54 ("if a problem persists, contact… service"); IFB p.66 |

**Brand codes:** LG tcL (LG error list: "time to run the tub clean cycle"). Note: TCL is also on the app's brand list;
"LG … tcL" works because LG comes first in that list.
**Dropped:** bleach solutions and branded washer cleaners (Whirlpool, LG): chemical handling differs by product, so the
steps stay with the machine's own program. Also dropped: "remove wet clothes promptly" (Whirlpool only), "leave the
detergent drawer open" (Samsung only), and cleaning the debris filter for smells (Samsung India only).

## washing_machine_technician_fault (call_technician)

No steps. The `meaning` (switch it off at the wall with dry hands, turn off its tap) comes from Samsung manual p.8: if
it's flooded, turn off the water and power supplies; if there's a strange noise, burning smell or smoke, unplug it and
contact service. Also Samsung p.8 and IFB p.15: never touch the plug with wet hands.

| Brand | Codes | Where / meaning |
|---|---|---|
| Samsung | 3C, HC, 1C, 8C, AC6, OC | Manual pp.55-56: motor, high-temperature heating, water-level sensor, MEMS sensor, main/inverter board communication, overflow. Each says "if the code remains, contact service" |
| LG | CE, PE, tE, SE, FE, dHE | LG error list: motor over-current, water-level sensor, heating/thermistor, sensor, overfill (faulty water valve), dry function. Each ends in "repair service" |
| Bosch | E19/F19, E20/F20, E23/F23, E25/F25, E26/F26, E27/F27, E28/F28 | Bosch codes: heating time exceeded, unexpected heating, Aquastop (leak), turbidity sensor, pressure sensors, flow sensor. "Can't be rectified by itself" |

**Left out:** Samsung `AC` and LG/Samsung `HE` read as ordinary words (the "AC" appliance, "he"). LG `LE` (motor locked:
LG lists overload and foreign objects as fixes, so it isn't technician-only). Samsung `3E` (motor or voltage depending on
the model). Samsung `OE`/`0E` (overflow on Samsung but drain on LG; only Samsung's `OC` is listed). IFB OFEr, PrS, LPr,
HPr, COnn (IFB doesn't say what they mean). Whirlpool "other F# E#" (can't be listed as a pattern).
**Deliberately more conservative than the brands:** Samsung, LG and Bosch (E20) all say to restart once before calling
service. A generic entry can't know the model, so it escalates straight away.
**Aliases** "burning smell", "smoke", "sparks", "strange noise", "water on the floor" / "flooded": Samsung manual p.8.
"Electric shock": Samsung pp.8-9 and IFB p.15 (shock warnings).

---

## Cross-check with ApplianceDB (grouping only)

ApplianceDB's `cause_category` / `component` agree with the grouping above:

| ApplianceDB category | Rows (brand code) | Our entry |
|---|---|---|
| `drainage` / drain pump | Samsung 5C; LG OE; Whirlpool F9E1, DRN | not_draining |
| `water_supply` / water inlet valve | Samsung 4C; LG IE; Whirlpool F8E1 | not_filling |
| `door_lock` | Samsung DC; LG DE; Whirlpool F5E2, LDL | door_problem |
| `unbalanced` / drum suspension | Samsung UB; LG UE; Whirlpool OFB | shaking_unbalanced |
| `motor`, `heating`, `temperature`, `sensor`, `communication` | Samsung 3C, HC, 1C, 8C, AC; LG CE, PE, TE | technician_fault |
| `informational` / control lock, `power` | Whirlpool LOC; LG/Whirlpool PF; Samsung UC | wont_start |

Differences found: ApplianceDB's Samsung UK rows give **DE2 = power switch** and **E3 = overflow/suds**, so Samsung dE2 is
not listed and nothing here uses E3. Its Bosch rows come from an aggregator, so they weren't used.

## Licence notes

- **Manuals and help pages** (Samsung, LG, IFB, Bosch, Whirlpool) are all rights reserved. Only facts and their order
  are used. Every `say`, `safety` and `meaning` line is our own wording (≤ 20 words), cited per entry. No PDF text or
  figures go in the APK.
- **ApplianceDB** is ODbL 1.0 and was used only to cross-check which fault a code means. No rows or fields were copied
  into the KB. If rows are ever imported, the KB's code table needs ODbL attribution ("ApplianceDB", link above) and
  share-alike.
- iFixit, Stack Exchange and aggregator blogs were not used for any step.

## Not verified yet

- Grounding: the `target` phrases haven't been tested on real photos (bottom filter flap, drain tube, inlet connector, feet).
- ASR: the spoken forms of letter codes ("d p e r", "u n b l", "o f b", "t c l") haven't been tried with Moonshine.
- Top-loaders: no top-load manual was read. The top-load wording comes from Whirlpool's top-load code page, plus the
  Samsung India page's note that the debris filter is front-load only.
- Retrieval: the test queries pass in a Python copy of `Retriever` stages 1-2 plus `MemoryRules.kbContext` (30/30).
  `build_kb.py` and `KbTest` weren't run, because they write outside this category's files.
- Gap: "leaking" matches no entry. Leaks have their own DIY checks (Samsung p.53, IFB p.66: tighten the hose
  connections and the drain filter), so a leak entry would be the next one to write.
