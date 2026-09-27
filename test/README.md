# Test photos

Photos used while testing pointing (copied from `~/3D/fixlens-testdata/`).
`answers/` has the correct boxes drawn in green, so you can compare Fixy's marker with them.

| Folder | Photo | Ask |
|---|---|---|
| `car/` | `car_volt_640.jpg` | "Where do I add engine oil?" · "Where is the coolant tank?" · "Where do I fill the washer fluid?" · "How do I check the engine oil?" (starts the guide) · "How do I fix the wiring?" (should refuse) |
| `washer/` | `washer_ariston_640.jpg` | "How do I choose a wash program?" · "Which button starts the wash?" · "How do I cancel the program?" · "Is the door locked?" |
| `washer/` | `washer_heran_640.jpg` | "What does the display say?" · "How do I turn it on?" · "Which button starts it?" |
| `laptop/` | `s76_gaze20_plain.jpg` | "How do I open this laptop for cleaning?" (13 screws: `s76_gaze20_screws_gt.json`, marked in `answers/`) |
| `laptop/` | `thinkpad_x220_bottom.jpg`, `dell_d830_bottom.jpg` | "Where are the screws for the bottom cover?" |

The `_640` files are the sizes the app uses; the others are the originals.

## Easiest: point the phone at the screen

Open a photo full-screen on the laptop and point FixLens at it, then ask by voice. This tests the whole app: camera,
voice, pointing and tracking.

## Or feed a photo straight to the app (no camera, no tracking)

```bash
adb push test/car/car_volt_640.jpg /sdcard/Android/data/com.fixlens/files/test/
adb shell "am start -n com.fixlens/.app.MainActivity --es ask 'Where do I add engine oil?' \
  --es image /sdcard/Android/data/com.fixlens/files/test/car_volt_640.jpg"
adb logcat -s FixLens
```

Keep the inner single quotes around the question.
