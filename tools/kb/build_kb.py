#!/usr/bin/env python3
"""Merge tools/kb/entries/*.json into the app's KB and the retrieval test set.

    python3 tools/kb/build_kb.py [kb_version]

Writes app/src/main/assets/kb/fixlens_kb.json (read by the app) and tools/kb/test_queries.json (run by KbTest).
Only quick checks here; the full rules are KbRepository.problems(), run by KbTest ("the bundled KB is valid").
How to write entries: tools/kb/AUTHORING.md.
"""
import datetime
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ENTRIES = ROOT / "tools/kb/entries"
KB_OUT = ROOT / "app/src/main/assets/kb/fixlens_kb.json"
QUERIES_OUT = ROOT / "tools/kb/test_queries.json"
MAX_SAY_WORDS = 20


def main() -> int:
    version = sys.argv[1] if len(sys.argv) > 1 else f"{datetime.date.today()}.generic"
    entries, queries, problems = [], [], []
    for path in sorted(ENTRIES.glob("*.json")):
        data = json.loads(path.read_text())
        for e in data["entries"]:
            entries.append(e)
            for s in e.get("steps", []):
                if len(s["say"].split()) > MAX_SAY_WORDS:
                    problems.append(f"{e['id']} step {s['n']}: {len(s['say'].split())} words")
        for q in data.get("test_queries", []):
            queries.append({"q": q["q"], "expect": q["expect"], "from": path.stem})
    ids = [e["id"] for e in entries]
    problems += [f"duplicate id {i}" for i in sorted({i for i in ids if ids.count(i) > 1})]
    problems += [f"test query expects unknown id {q['expect']}: {q['q']}"
                 for q in queries if q["expect"] != "NONE" and q["expect"] not in ids]
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    KB_OUT.write_text(json.dumps({"kb_version": version, "entries": entries}, indent=2, ensure_ascii=False) + "\n")
    QUERIES_OUT.write_text(json.dumps(queries, indent=2, ensure_ascii=False) + "\n")
    by_appliance = {}
    for e in entries:
        by_appliance.setdefault(e["appliance"], []).append(e)
    for appliance, es in by_appliance.items():
        tech = sum(e["severity"] == "call_technician" for e in es)
        print(f"{appliance:16} {len(es):2} entries ({tech} technician-only)")
    print(f"KB {version}: {len(entries)} entries, {len(queries)} test queries -> {KB_OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
