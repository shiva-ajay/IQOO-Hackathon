"""Grounding test cases: test photo, target phrase, spoken question, and a hand-marked ground-truth box
(pixels of the original 640-px photo in /home/shiva-ajay/3D/fixlens-testdata/m0/provisional)."""

CASES = [
    # (image, target phrase, question, gt box x1,y1,x2,y2)
    ("washer_ariston_640.jpg", "program selector dial", "How do I choose a wash program?", (224, 158, 354, 310)),
    ("washer_ariston_640.jpg", "start/pause button", "Which button starts the wash?", (482, 243, 531, 279)),
    ("washer_ariston_640.jpg", "ARISTON brand logo", "What brand is this machine?", (432, 38, 594, 72)),
    ("washer_ariston_640.jpg", "cancel button marked with an X", "How do I cancel the program?", (482, 187, 531, 223)),
    ("washer_ariston_640.jpg", "door lock indicator light with the padlock symbol", "Is the door locked?", (455, 102, 501, 128)),
    ("car_volt_640.jpg", "yellow engine oil filler cap", "Where do I add engine oil?", (184, 254, 216, 284)),
    ("car_volt_640.jpg", "white coolant reservoir with the cap", "Where is the coolant tank?", (332, 104, 404, 152)),
    ("car_volt_640.jpg", "windshield washer fluid cap", "Where do I fill the washer fluid?", (553, 285, 592, 320)),
    ("washer_heran_640.jpg", "digital display showing the number", "What does the display say?", (262, 147, 292, 172)),
    ("washer_heran_640.jpg", "red power button", "How do I turn it on?", (388, 143, 420, 174)),
    ("washer_heran_640.jpg", "start/pause button", "Which button starts it?", (388, 178, 420, 207)),
]

# Synthetic: one red circle and one blue square on a noisy gray background, at known places, in the two keyframe
# shapes the app produces (portrait camera crop, landscape photo). Built by run_ground_test.make_synthetic().
SYN_SHAPES = {"syn_land": (448, 352), "syn_port": (192, 448)}
SYN_LAYOUT = {
    # name: (circle centre fx,fy, square centre fx,fy) as fractions of width/height
    "a": ((0.20, 0.30), (0.78, 0.72)),
    "b": ((0.80, 0.25), (0.25, 0.75)),
    "c": ((0.50, 0.80), (0.50, 0.20)),
}
SYN_RADIUS = 0.07  # of the short side
