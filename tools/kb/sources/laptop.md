# Sources: laptop

Checked 2026-09-27. Every step below was written from pages and PDFs that were opened and read on that date
(copies of the downloads are in the session scratchpad, not in the repo). Wording is our own.

## Source list

| Key | Document | Where |
|---|---|---|
| **DELL-SM** | Dell Inspiron 15 3520 Service Manual (Dell, PDF dated 2023-05; 110 pp.): "Before working inside your computer", "Safety instructions", "Removing / Installing the base cover", "Rechargeable Li-ion battery precautions", "Handling swollen rechargeable Li-ion batteries" | Online: https://www.dell.com/support/manuals/en-us/inspiron-15-3520-laptop/inspiron_3520_sm/removing-the-base-cover?guid=guid-3b13fc8d-6fd3-446a-aa70-8395745ef949&lang=en-us · PDF read from a mirror: https://scuba.cs.uchicago.edu/summer2023/inspiron-3520-sm-en-us.pdf (Author: Dell; same text as the online manual) |
| **HP-MSG** | HP 15 Laptop PC Maintenance and Service Guide, L58964-001 (2019): Safety warning notice (p. iii), ESD, "Preparation for disassembly" (p. 27), "Bottom cover" (p. 28) | https://h10032.www1.hp.com/ctg/Manual/c08289069.pdf |
| **LEN-HMM** | Lenovo Hardware Maintenance Manual, IdeaPad Gaming 3 (15"/16", 7), 1st ed. Feb 2022: general guidelines, DANGER note, "Remove the lower case", "Remove the battery pack", warranty exclusions | Lenovo PDF, read from iFixit's document mirror: https://documents.cdn.ifixit.com/j4dYRLQaxyBl2xhu.pdf |
| **LEN-UG** | Lenovo IdeaPad Slim 3 Series User Guide: charging light, rechargeable battery pack / conservation mode, ventilation slots, CRU table | https://www.adorama.com/col/productManuals/LE82XQ001GUS.pdf (Lenovo PDF hosted by a retailer) |
| **LEN-HEAT** | Lenovo "Heat and product ventilation" safety notice | https://download.lenovo.com/pccbbs/pubs/p330_tiny/html_en/en/Heat_and_product_ventilation_(topic)_T0000763247.html |
| **LEN-FAN** | Lenovo glossary, "Laptop Fan" | https://www.lenovo.com/us/en/glossary/laptop-fan/ |
| **DELL-BLOG** | Dell, "How to Clean a Laptop Fan Safely: Step-by-Step Guide" | https://www.dell.com/en-us/blog/how-to-clean-a-laptop-fan-safely-step-by-step-guide/ |
| **DELL-HEAT** | Dell, "How to Prevent Overheating: Tips for Cooling Your PC or Laptop" | https://www.dell.com/support/contents/en-us/article/product-support/self-support-knowledgebase/battery-and-power/fan |
| **DELL-AC** | Dell, "Laptop Battery Not Charging: Resolve AC Adapter Issues" | https://www.dell.com/support/contents/en-us/article/product-support/self-support-knowledgebase/battery-and-power/ac-adapter |
| **DELL-BAT** | Dell KB 000123069, "How to Troubleshoot Dell Laptop Battery Issues" (charging LED, Primarily AC Use / custom charge thresholds) | https://www.dell.com/support/kbdoc/en-us/000123069/how-to-troubleshoot-dell-laptop-battery-issues |
| **HP-CLEAN** | HP Tech Takes, "How To Clean Your Laptop" | https://www.hp.com/us-en/tech-takes/laptops/maintenance/how-to-clean-your-laptop.html |
| **HP-HEAT** | HP Tech Takes, "How to Fix Overheating Issues in HP Laptops" | https://www.hp.com/us-en/tech-takes/laptops/troubleshooting/how-to-fix-overheating-issues-in-hp-laptops.html |
| **HP-CHG** | HP Tech Takes, "Laptop Not Charging? How to Fix It: 10 Easy Solutions" | https://www.hp.com/us-en/tech-takes/laptops/troubleshooting/how-to-fix-laptop-that-wont-charge.html |
| **ASUS-CHG** | ASUS FAQ 1012793, battery not supplying power / charging | https://www.asus.com/us/support/faq/1012793/ |
| **ASUS-HEAT** | ASUS FAQ 1015064, overheating and fan issues | https://www.asus.com/us/support/faq/1015064/ |
| **MS-SURF** | Microsoft Support, "Caring for your Surface battery" (expanded batteries) | https://support.microsoft.com/en-us/surface/battery/caring-for-your-surface-battery |

Not reachable from here: support.hp.com documents (timeouts; e.g. "Reduce heat inside the laptop", "Swelling or
deformation of notebook battery"), support.lenovo.com solutions (403, e.g. HT103159 "stops at 60%"). The HP Tech Takes
pages and Lenovo PDFs above stand in for them.

---

## laptop_open_bottom_cover (caution), revised from the DRAFT

Kept the id, title, meaning and the demo query ("how do I open this laptop to clean it"). Changes from the draft:
added a warranty / manual check, "keep track of each screw", the battery-cable disconnect and reconnect (both in the
Dell and Lenovo procedures), compressed-air cautions, the loose-screw check; dropped "soft cloth" (not in any source;
Dell asks for a flat, dry, clean surface) and "never a metal tool" (sources only say *plastic* tool). Moved
"laptop overheating / fan is loud" to the new `laptop_overheating` entry, where official advice starts outside the case.

| Line | Agreeing sources |
|---|---|
| Safety 1: shut down fully, unplug, remove cables and cards | DELL-SM "Before working inside" (shut down, disconnect from outlets, disconnect peripherals, remove media cards), HP-MSG "Preparation for disassembly" (turn off, not Hibernation; unplug; disconnect external devices), LEN-HMM DANGER ("shut down the computer and unplug all power cords") |
| Safety 2: clean, flat, dry table; touch bare metal for static | DELL-SM ("work surface is flat, dry, and clean"; "ground yourself by touching an unpainted metal surface"), LEN-HMM ("establish personal grounding by touching a ground point"), HP-MSG (be discharged of static electricity; ESD section) |
| 1. Check the manual; opening can void the warranty | HP-MSG ch. 5 ("should be accessed only by an authorized service provider. Accessing these parts can… void the warranty"), HP-CLEAN ("consult your laptop's manual… Some models may void the warranty if opened"), DELL-SM ("Damage due to servicing that is not authorized by Dell is not covered by your warranty"), LEN-UG (only power cord and adapter are CRUs on IdeaPad Slim 3; other parts by authorized technicians), LEN-HMM ("Do not try to service any computer unless you have been trained") |
| 2. Lid closed, laptop upside down on the table | LEN-HMM ("Place the computer upside down on a flat surface"), DELL-SM figures, HP-MSG figures |
| 3. Remove every screw, track where each goes | DELL-SM (six screws + two captive screws loosened → caution), HP-MSG (screws of two sizes; "Make special note of each screw size and location"; rubber feet peeled first → caution), LEN-HMM (ten screws, three sizes), DELL-BLOG ("keeping track of where each screw belongs") |
| 4. Pry loose with a plastic tool around the edge | DELL-SM ("Using a plastic scribe, pry open the base cover"), HP-MSG ("Insert a plastic tool between the bottom cover and the computer chassis… flex and lift"), LEN-HMM ("Pry up the latches"; pry tool in the tool list). Caution: LEN-HMM warranty exclusions ("Plastic parts, latches… cracked or broken by excessive force") |
| 5. Lift the cover off | DELL-SM, HP-MSG, LEN-HMM |
| 6. Unplug the battery cable by its connector | DELL-SM step 5 ("Disconnect the battery cable from the system board") + safety ("pull it by its connector or its pull tab, not the cable itself"), LEN-HMM battery pack step ("Use your fingernail to pull the connector… Do not pull the cable"). Caution (swollen → stop): DELL-SM ("Swollen batteries should not be used"), MS-SURF |
| 7. Short puffs of compressed air on fan and vents | DELL-BLOG ("Spray compressed air in short, controlled bursts across the fan blades and copper heat sinks"), HP-CLEAN ("short bursts of compressed air… clean the fan blades with compressed air or a soft brush"), LEN-FAN (compressed air). Caution: hold the fan still (DELL-BLOG), can upright (DELL-BLOG, DELL-HEAT), no vacuum (DELL-BLOG, DELL-HEAT) |
| 8. Battery cable back in | DELL-SM "Installing the base cover" step 1 ("Connect the battery cable to the connector on the system board"); LEN-HMM / HP-MSG ("reverse the procedure") |
| 9. Cover on, every screw back | DELL-SM (snap into place, tighten captive screws, replace screws), HP-MSG ("Reverse this procedure"), LEN-HMM ("carefully retain and reuse all screws"). Caution: LEN-HMM ("do not turn on the computer until… none are loose inside… shaking the computer gently and listening for rattling") |

Dropped: Dell's "press and hold power 5 s to drain flea power" (Dell only), Lenovo "remove the deco cover" and
Dell "remove the SD card" first (model-specific; covered by safety 1 "remove cards"), BIOS "disable built-in battery"
/ ship mode (differs per brand, not in the manuals read). iFixit not needed.

## laptop_not_charging (diy)

| Line | Agreeing sources |
|---|---|
| Safety: damaged or buzzing charger → unplug, don't use | HP-CHG (damaged cables are a fire / shock hazard; replace), DELL-AC ("A buzzing sound may indicate electrical issues. Discontinue use immediately"), ASUS-CHG (damaged adapter → service centre) |
| 1. Unplug from wall and laptop | HP-CHG ("Unplug your laptop charger from both the wall outlet and your laptop"), ASUS-CHG (check looseness at cord plug, outlet side and device side) |
| 2. Check for fraying, kinks, bent pins | HP-CHG (fraying, kinks, discoloration), DELL-AC (frayed cables, bent pins), ASUS-CHG (signs of damage). Caution: HP-CHG, ASUS-CHG |
| 3. Dust / lint in the charging port | DELL-AC ("Inspect the charging port… for any debris, dust, or lint"), HP-CHG ("Clean connection points") |
| 4. Firmly back in; a socket you know works | HP-CHG (reconnect firmly; test the socket with another device), DELL-AC (working outlet; different outlet), ASUS-CHG (try different outlets). Caution (only some USB-C ports charge): HP-CHG |
| 5. Charging light comes on | DELL-BAT (charging LED "usually near the power port"; "No light: Power is not reaching the laptop"), LEN-UG ("Charging light… Off: not connected to ac power") |
| 6. Stops at 60/80% → battery-saving setting | LEN-UG (conservation mode keeps charge at 75-80%), ASUS-CHG (Battery Health Charging limits to 60% or 80%), DELL-BAT (Primarily AC Use limits charge to 50-80%; custom thresholds). Caution (just short of full is normal): ASUS-CHG (not charging above 95% is "a normal condition for battery protection"), LEN-UG (recharges only below 94%) |
| 7. Still not charging → technician | DELL-AC, HP-CHG, ASUS-CHG (contact support / service centre) |

Dropped: hard reset / EC reset (durations and methods differ: Dell 15-20 s, ASUS model-specific), battery-driver
reinstall in Device Manager, BIOS updates, maker diagnostics (software, not something Fixy can point at), "try another
charger" (only Dell says so, with a matching-wattage condition).

## laptop_overheating (caution)

| Line | Agreeing sources |
|---|---|
| Safety: too hot to hold → shut down, unplug, cool | HP-HEAT (get help if "too hot to hold… constant shutting down"), LEN-FAN (fan stops → "shut down your laptop immediately"), HP-MSG safety notice (heat-related injuries) |
| 1. Hard flat table, not bed / sofa / lap | HP-MSG ("Use the device only on a hard, flat surface… pillows or rugs or clothing"), LEN-HEAT ("bed, sofa, carpet"; not on your lap), ASUS-HEAT ("firm, flat surfaces… avoid sofas/beds"), HP-HEAT, LEN-FAN |
| 2. Nothing covering the vents | HP-MSG, LEN-UG ("Do not block the ventilation slots"), ASUS-HEAT, DELL-HEAT |
| 3. Close unused programs; Task Manager | HP-HEAT ("close background programs"), ASUS-HEAT (Task Manager CPU/memory usage) |
| 4. Shut down and unplug before cleaning vents | DELL-HEAT ("Turn off your computer and unplug"), HP-CLEAN ("Power off and unplug"), ASUS-HEAT ("turn off the device and disconnect the power cord") |
| 5. Short puffs of compressed air into the vents | DELL-HEAT, HP-CLEAN, ASUS-HEAT (canned air at a distance), LEN-FAN. Caution: can upright (DELL-HEAT), no vacuum (DELL-HEAT), no air compressor (ASUS-HEAT) |
| 6. Still hot → fan may need inside cleaning → technician | ASUS-HEAT (excessive dust needing deep cleaning → service centre), HP-HEAT (constant overheating / shutdowns → HP support), LEN-FAN (seek professional help), DELL-HEAT (contact support) |

Dropped: power-plan / fan-mode changes (menu names differ: Windows power mode, MyASUS, Dell Power Manager), BIOS and
driver updates, cooling pads (optional accessory), thermal paste (LEN-FAN mentions it; technician work).
Inside cleaning is the `laptop_open_bottom_cover` entry; the last step sends people to a technician because HP, Lenovo
and ASUS treat opening the case as authorized-service work.

## laptop_swollen_battery (call_technician)

Spoken `meaning` + the technician line; safety lines are for follow-up questions only (no steps).

| Line | Agreeing sources |
|---|---|
| Stop using, unplug the charger, don't charge it | DELL-SM ("discontinue the use of the laptop… disconnecting the AC adapter"; "Swollen batteries should not be used"), MS-SURF ("we recommend you stop using the device"), HP-CHG ("Stop charging immediately if the battery appears swollen or bloated") |
| Don't press, bend, puncture, pry; keep from heat; don't remove it | DELL-SM ("Do not crush… penetrate… apply pressure… bend… use tools to pry"; "do not try to free it"; "Do not expose the battery to high temperatures"), MS-SURF ("prevent putting pressure on or risk puncturing the battery cell") |
| Technician / maker service for replacement | DELL-SM (contact Dell support; authorized technician), MS-SURF (replacement request), LEN-UG (built-in battery replaced only by an authorized technician) |

Symptom aliases (bulging case, lifting touchpad, laptop rocking) are how people notice swelling; Dell notes damage
to the enclosure, and HP's (unreachable) support page is widely quoted for touchpad/keyboard deformation. They are
retrieval phrases, not instructions.

## Licence notes

OEM manuals and support pages are "all rights reserved"; facts and order kept, sentences rewritten (≤ 20 words),
cited here. Two PDFs were read from mirrors (Dell manual on a university site, Lenovo HMM on iFixit's document CDN);
both are the manufacturers' own documents, and iFixit's own guides were not used.
