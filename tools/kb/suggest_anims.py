#!/usr/bin/env python3
"""Propose step animations (`anim`, `safety_anim`) from the words of each step. A person reviews them.

    python3 tools/kb/suggest_anims.py            # print proposals for untagged steps
    python3 tools/kb/suggest_anims.py --write    # write them into tools/kb/entries/*.json

Only unambiguous wording is tagged: a direction is set only where the step says it ("unscrew", "anticlockwise",
"screw it back on", "tighten"), and pull/push only for dipsticks (the one part whose on-screen direction is known).
KbRepository.problems() checks every tag against the step's words again. Rules: docs/step-animations-plan.md.
"""
import json
import pathlib
import re
import sys

ENTRIES = pathlib.Path(__file__).resolve().parent / "entries"

OPEN = re.compile(r"unscrew|anti-?clockwise|counter-?clockwise|loosen")
CLOSE = re.compile(r"(?<!anti)(?<!anti-)(?<!counter)(?<!counter-)clockwise|tighten|\bscrew (\w+ )*(back )?(on|in)\b")
LEVEL = re.compile(r"between (the )?(two |lower and upper |upper and lower |max and min |min and max )?(marks|min|max|lower|upper)")
POUR = re.compile(r"\bpour\b|top up (slowly )?with|add (a little|the (oil|coolant))|slowly add coolant")
UNPLUG = re.compile(r"\bunplug")
ENGINE_OFF = re.compile(r"engine (is )?off|(switch|turn) (the )?engine off|(switch|turn) off the engine|ignition off|(switch|turn) the ignition off")
WALL_OFF = re.compile(r"off at the wall|wall switch|off at the main|(switch|turn) off .*(breaker|wall)")


def step_anim(say: str, target: str | None) -> dict | None:
    t = say.lower()
    dip = target is not None and "dipstick" in target.lower()
    if OPEN.search(t) and not CLOSE.search(t):
        return {"kind": "turn", "dir": "ccw"}
    if CLOSE.search(t) and not OPEN.search(t) and target:
        return {"kind": "turn", "dir": "cw"}
    if dip and re.search(r"\bpush\b", t):
        return {"kind": "push"}
    if dip and re.search(r"\bpull\b", t):
        return {"kind": "pull"}
    if LEVEL.search(t):
        return {"kind": "level"}
    if POUR.search(t):
        return {"kind": "pour"}
    return None


def safety_anim(line: str) -> dict | None:
    t = line.lower()
    if UNPLUG.search(t):
        return {"kind": "unplug"}
    if ENGINE_OFF.search(t):
        return {"kind": "engine_off"}
    if WALL_OFF.search(t):
        return {"kind": "switch_off"}
    return None


def main() -> int:
    write = "--write" in sys.argv
    for path in sorted(ENTRIES.glob("*.json")):
        data = json.loads(path.read_text())
        changed = False
        for e in data["entries"]:
            if e.get("safety") and "safety_anim" not in e:
                tags = [safety_anim(s) for s in e["safety"]]
                if any(tags):
                    while tags and tags[-1] is None:
                        tags.pop()
                    for s, a in zip(e["safety"], tags):
                        if a:
                            print(f"{e['id']:40} safety  {a['kind']:10}   {s}")
                    e["safety_anim"] = tags
                    changed = True
            for s in e.get("steps", []):
                if "anim" in s:
                    continue
                a = step_anim(s["say"], s.get("target"))
                if a:
                    print(f"{e['id']:40} step {s['n']:<2} {a['kind']:6} {a.get('dir', ''):4} {s['say']}")
                    s["anim"] = a
                    changed = True
        if write and changed:
            path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    if not write:
        print("\n(dry run: nothing written; add --write)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
