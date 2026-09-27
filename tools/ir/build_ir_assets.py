#!/usr/bin/env python3
"""
Builds the phone's IR remote assets from ir-dataset/ (IRext + Flipper-IRDB).

    python3 tools/ir/build_ir_assets.py            # writes app/src/main/assets/ir/

Output (app/src/main/assets/ir/):
  catalog.json   devices -> brands -> models. TV/projector/fan models map button -> pattern index;
                 AC models name an IRext binary (the phone builds any AC state from it).
  patterns.bin   every fixed-button pattern once: "FXIR", u16 version, u32 count, then per pattern
                 u32 carrier Hz, u16 n, n x u16 microseconds (mark first, ends on a mark). Little-endian.
  ac/*.bin       the IRext AC binaries that decoded cleanly on the laptop.

Needs gcc and git. Two helper programs are built into tools/ir/.build/:
  flipper_encode  Flipper firmware's own IR encoders (fetched, not vendored: GPL) turning Flipper "parsed"
                  codes (protocol/address/command) into timings, round-trip checked with Flipper's decoder.
  irext_check     the vendored IRext decoder (app/src/main/cpp/irext), used to drop AC files that don't decode.
"""
import collections
import hashlib
import json
import os
import re
import shutil
import sqlite3
import struct
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATASET = os.path.join(ROOT, "ir-dataset")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "ir")
BUILD = os.path.join(ROOT, "tools", "ir", ".build")
FLIPPER_FW = os.path.join(BUILD, "flipperzero-firmware")
FLIPPER_FW_URL = "https://github.com/flipperdevices/flipperzero-firmware.git"

MAX_PATTERN_US = 1_900_000  # ConsumerIrManager refuses patterns over 2 s
DEVICES = ("ac", "tv", "projector", "fan")
IREXT_CATEGORY = {"ac": 1, "tv": 2, "fan": 7, "projector": 8}
FLIPPER_FOLDER = {"tv": "TVs", "projector": "Projectors", "fan": "Fans"}  # Flipper ACs: fixed buttons only, skipped

# The closed button sets the app knows (ir/Buttons.kt must match).
KEYS = {
    "tv": ["power", "mute", "vol_up", "vol_down", "ch_up", "ch_down", "input", "menu", "home", "back", "ok",
           "up", "down", "left", "right"] + [str(d) for d in range(10)],
    "projector": ["power", "input", "menu", "ok", "up", "down", "left", "right", "back", "home", "vol_up",
                  "vol_down", "mute", "blank", "freeze"],
    "fan": ["power", "speed", "speed_up", "speed_down", "swing", "timer", "mode"],
}
# Pairing's first test button per device, most preferred first (docs/ir-remote-plan.md §5). TVs test with volume
# up: its on-screen bar is big enough for the camera to notice (ir/ScreenProbe).
TEST_KEYS = {"tv": ["vol_up", "mute", "power"], "projector": ["menu", "power"], "fan": ["speed", "speed_up", "power"]}

SYNONYMS = {
    "power": ["power", "pwr", "power_toggle", "on_off", "onoff", "power_on_off", "standby"],
    "mute": ["mute", "muting", "sound_off"],
    "vol_up": ["vol_up", "vol_plus", "volume_up", "volume_plus", "volup", "vol_increase"],
    "vol_down": ["vol_down", "vol_dn", "vol_minus", "volume_down", "volume_minus", "voldown", "vol_decrease"],
    "ch_up": ["ch_up", "ch_next", "ch_plus", "channel_up", "channel_plus", "chup", "prog_plus", "p_plus"],
    "ch_down": ["ch_down", "ch_prev", "ch_minus", "channel_down", "channel_minus", "chdown", "prog_minus", "p_minus"],
    "input": ["input", "source", "src", "tv_av", "av_tv", "input_source", "inputs"],
    "menu": ["menu", "setup"],
    "home": ["home", "smart_home"],
    "back": ["back", "return", "exit", "esc"],
    "ok": ["ok", "enter", "select", "confirm"],
    "up": ["up", "arrow_up", "nav_up", "cursor_up"],
    "down": ["down", "arrow_down", "nav_down", "cursor_down"],
    "left": ["left", "arrow_left", "nav_left", "cursor_left"],
    "right": ["right", "arrow_right", "nav_right", "cursor_right"],
    "blank": ["blank", "av_mute", "pic_mute", "picture_mute", "hide"],
    "freeze": ["freeze"],
    "speed": ["speed", "fan_speed", "wind_speed", "fan"],
    "speed_up": ["speed_up", "speed_plus", "fan_up", "fanspeed_plus", "fan_speed_plus", "faster", "wind_plus"],
    "speed_down": ["speed_down", "speed_minus", "fan_down", "fanspeed_minus", "fan_speed_minus", "slower", "wind_minus"],
    "swing": ["swing", "osc", "oscillate", "oscillation", "oscilate", "oscillating", "rotate", "rotation", "shake",
              "shake_wind", "turn"],
    "timer": ["timer"],
    "mode": ["mode", "wind_type", "breeze", "natural"],
}
NUMBER_WORDS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]
for d in range(10):
    SYNONYMS[str(d)] = [str(d), "num_%d" % d, "number_%d" % d, "digit_%d" % d, "key_%d" % d, "btn_%d" % d, NUMBER_WORDS[d]]
CANONICAL = {alias: key for key, aliases in SYNONYMS.items() for alias in aliases}

# Brands we expect to demo or that sell widely in India; the build reports which ones have codes.
WATCH_BRANDS = {
    "ac": ["LG", "Samsung", "Voltas", "Daikin", "Blue Star", "Lloyd", "Hitachi", "Carrier", "Godrej", "Whirlpool",
           "Panasonic", "Haier", "O General", "Mitsubishi"],
    "tv": ["LG", "Samsung", "Sony", "Mi", "OnePlus", "TCL", "Vu", "Panasonic", "Philips", "Hisense", "Toshiba"],
    "projector": ["Epson", "BenQ", "Sony", "Optoma", "ViewSonic", "Acer", "Panasonic", "InFocus"],
    "fan": ["Havells", "Crompton", "Atomberg", "Orient", "Usha", "Panasonic"],
}


def norm(name):
    return re.sub(r"[^a-z0-9]+", "", name.lower())


def key_name(raw):
    s = raw.strip().lower()
    if s.endswith("+"):
        s = s[:-1].rstrip(" _") + "_plus"
    elif s.endswith("-"):
        s = s[:-1].rstrip(" _") + "_minus"
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return CANONICAL.get(s)


def carrier_for_irext(protocol):
    p = protocol.lower()
    if "56k" in p:
        return 56000
    if "36k" in p or "rc5" in p or "rc6" in p or p.startswith("philips"):
        return 36000
    return 38000


def clean_pattern(values):
    """µs list → valid ConsumerIrManager pattern (mark first, ends on a mark, all > 0, < 2 s) or None."""
    v = [int(x) for x in values]
    while v and v[0] <= 0:
        v.pop(0)
    if any(x <= 0 for x in v):
        return None
    v = [min(x, 65535) for x in v]
    if len(v) % 2 == 0:
        v = v[:-1]  # trailing space
    if len(v) < 3 or sum(v) > MAX_PATTERN_US:
        return None
    return v


class Patterns:
    def __init__(self):
        self.index = {}
        self.items = []

    def add(self, carrier, values):
        key = (carrier, tuple(values))
        if key not in self.index:
            self.index[key] = len(self.items)
            self.items.append(key)
        return self.index[key]

    def write(self, path):
        with open(path, "wb") as f:
            f.write(b"FXIR" + struct.pack("<HI", 1, len(self.items)))
            for carrier, values in self.items:
                f.write(struct.pack("<IH", carrier, len(values)))
                f.write(struct.pack("<%dH" % len(values), *values))


class Brands:
    def __init__(self, alias_file):
        with open(alias_file) as f:
            table = json.load(f)
        self.display = {}
        self.spoken = {}
        for display, aliases in table.items():
            for a in [display] + aliases["match"]:
                self.display[norm(a)] = display
            self.spoken[display] = aliases.get("spoken", [])

    def resolve(self, raw):
        """Display name for a raw brand name, or None when it has no usable (Latin) name."""
        if not raw or not re.search(r"[A-Za-z]", raw) or re.search(r"[^\x00-\x7f]", raw):
            return None
        key = norm(raw)
        if not key:
            return None
        if key in self.display:
            return self.display[key]
        pretty = raw.replace("_", " ").strip()
        if pretty.isupper() and len(pretty) > 4:
            pretty = pretty.title()
        return pretty


def run(cmd, **kw):
    return subprocess.run(cmd, check=True, **kw)


def build_tools():
    os.makedirs(BUILD, exist_ok=True)
    if not os.path.isdir(os.path.join(FLIPPER_FW, "lib", "infrared")):
        shutil.rmtree(FLIPPER_FW, ignore_errors=True)
        print("Fetching Flipper firmware IR encoders (sparse clone)…")
        run(["git", "clone", "-q", "--depth", "1", "--filter=blob:none", "--sparse", FLIPPER_FW_URL, FLIPPER_FW])
        run(["git", "-C", FLIPPER_FW, "sparse-checkout", "set", "--no-cone", "/lib/infrared/encoder_decoder/"])
    fz = os.path.join(FLIPPER_FW, "lib", "infrared", "encoder_decoder")
    sources = [os.path.join(dp, f) for dp, _, fs in os.walk(fz) for f in fs if f.endswith(".c")]
    here = os.path.dirname(os.path.abspath(__file__))
    flipper_encode = os.path.join(BUILD, "flipper_encode")
    run(["gcc", "-O1", "-std=gnu11", "-w", "-I" + os.path.join(here, "flipper_encode", "stub"), "-I" + fz,
         "-I" + os.path.join(fz, "common"), os.path.join(here, "flipper_encode", "main.c")] + sources +
        ["-o", flipper_encode])
    irext = os.path.join(ROOT, "app", "src", "main", "cpp", "irext")
    irext_check = os.path.join(BUILD, "irext_check")
    run(["gcc", "-O1", "-w", "-DBOARD_PC_DLL", "-I" + os.path.join(irext, "include"),
         os.path.join(here, "irext_check.c")] +
        [os.path.join(irext, f) for f in sorted(os.listdir(irext)) if f.endswith(".c")] + ["-o", irext_check])
    return flipper_encode, irext_check


def encode_flipper(tool, requests):
    """[(protocol, address, command)] → [(carrier, values) or None], one encoder process for all."""
    lines = "".join("%s %x %x\n" % r for r in requests)
    out = subprocess.run([tool], input=lines, capture_output=True, text=True, check=True).stdout.splitlines()
    result = []
    for line in out:
        if line.startswith("ERR"):
            result.append(None)
        else:
            nums = [int(x) for x in line.split()]
            result.append((nums[0], nums[1:]))
    assert len(result) == len(requests)
    return result


def parse_flipper_file(path):
    """Signals in a Flipper .ir file as dicts (name, type, protocol/address/command or frequency/data)."""
    signals, cur = [], {}
    with open(path, errors="ignore") as f:
        for line in f:
            line = line.strip()
            if line.startswith("#") or not line:
                if cur.get("name"):
                    signals.append(cur)
                cur = {}
                continue
            if ":" not in line:
                continue
            k, v = line.split(":", 1)
            cur[k.strip().lower()] = v.strip()
    if cur.get("name"):
        signals.append(cur)
    return signals


def le_hex(field):
    """Flipper stores address/command as little-endian bytes: "04 00 00 00" → 0x04."""
    b = [int(x, 16) for x in field.split()]
    return sum(v << (8 * i) for i, v in enumerate(b))


def main():
    flipper_encode, irext_check = build_tools()
    brands = Brands(os.path.join(os.path.dirname(__file__), "brand_aliases.json"))
    patterns = Patterns()
    # device -> brand -> list of models {"id", "keys"} / {"id", "ac"}
    catalog = {d: collections.defaultdict(list) for d in DEVICES}
    dropped = collections.Counter()

    db = sqlite3.connect(os.path.join(DATASET, "irext", "irext_ac_tv_projector_fan.sqlite"))
    brand_en = dict(db.execute("select id, name_en from brand"))

    # ---- IRext TV / projector / fan: decoded key timings ----
    for device in ("tv", "projector", "fan"):
        cat = IREXT_CATEGORY[device]
        rows = db.execute(
            "select r.id, r.brand_id, r.protocol, d.key_name, d.key_value from remote_index r "
            "join decode_remote d on d.remote_index_id = r.id where r.category_id = ? order by r.id, d.key_number",
            (cat,),
        )
        remotes = collections.OrderedDict()
        for rid, bid, protocol, kname, kvalue in rows:
            remotes.setdefault(rid, (bid, protocol, {}))
            key = key_name(kname)
            if key not in KEYS[device] or key in remotes[rid][2]:
                continue
            values = clean_pattern([x for x in (kvalue or "").split(",") if x.strip()])
            if values is None:
                dropped["irext_bad_pattern"] += 1
                continue
            remotes[rid][2][key] = patterns.add(carrier_for_irext(protocol), values)
        for rid, (bid, protocol, keys) in remotes.items():
            brand = brands.resolve(brand_en.get(bid))
            if brand is None:
                dropped["irext_no_english_brand"] += 1
                continue
            if not any(k in keys for k in TEST_KEYS[device]):
                dropped["irext_no_test_key"] += 1
                continue
            catalog[device][brand].append({"id": "irext:%d" % rid, "keys": keys})

    # ---- Flipper-IRDB TV / projector / fan ----
    for device, folder in FLIPPER_FOLDER.items():
        base = os.path.join(DATASET, "flipper-irdb", folder)
        files = sorted(os.path.join(dp, f) for dp, _, fs in os.walk(base) for f in fs if f.endswith(".ir"))
        parsed_requests, pending = [], []  # pending: (model keys dict, key, request index)
        models = []
        for path in files:
            rel = os.path.relpath(path, base)
            brand = brands.resolve(rel.split(os.sep)[0])
            if brand is None:
                continue
            keys = {}
            for sig in parse_flipper_file(path):
                key = key_name(sig["name"])
                if key not in KEYS[device] or key in keys:
                    continue
                if sig.get("type") == "parsed" and {"protocol", "address", "command"} <= sig.keys():
                    keys[key] = None
                    pending.append((keys, key, len(parsed_requests)))
                    parsed_requests.append((sig["protocol"], le_hex(sig["address"]), le_hex(sig["command"])))
                elif sig.get("type") == "raw" and "data" in sig:
                    values = clean_pattern(sig["data"].split())
                    carrier = int(float(sig.get("frequency", "38000")))
                    if values is None or not 20000 <= carrier <= 60000:
                        dropped["flipper_bad_raw"] += 1
                        continue
                    keys[key] = patterns.add(carrier, values)
            models.append((brand, "flipper:%s/%s" % (folder, rel.replace(os.sep, "/")), keys))
        encoded = encode_flipper(flipper_encode, parsed_requests)
        for keys, key, i in pending:
            enc = encoded[i]
            values = clean_pattern(enc[1]) if enc else None
            if values is None:
                dropped["flipper_encode_failed"] += 1
                del keys[key]
            else:
                keys[key] = patterns.add(enc[0], values)
        for brand, mid, keys in models:
            if not any(k in keys for k in TEST_KEYS[device]):
                dropped["flipper_no_test_key"] += 1
                continue
            catalog[device][brand].append({"id": mid, "keys": keys})

    # ---- IRext ACs: binaries checked with the vendored decoder ----
    bin_dir = os.path.join(DATASET, "irext", "binaries")
    ac_rows = db.execute("select id, brand_id, remote_map from remote_index where category_id = 1 order by id").fetchall()
    paths = sorted({os.path.join(bin_dir, "irda_%s.bin" % rmap) for _, _, rmap in ac_rows})
    paths = [p for p in paths if os.path.exists(p)]
    check = subprocess.run([irext_check], input="\n".join(paths) + "\n", capture_output=True, text=True, check=True)
    ac_ok = {}
    for line in check.stdout.splitlines():
        parts = line.split()
        if parts[0] == "OK":
            ac_ok[parts[1]] = parts[3]  # power-on pattern hash
        else:
            dropped["ac_" + parts[2]] += 1
    ac_files = {}
    for rid, bid, rmap in ac_rows:
        path = os.path.join(bin_dir, "irda_%s.bin" % rmap)
        brand = brands.resolve(brand_en.get(bid))
        if path not in ac_ok or brand is None:
            continue
        name = rmap + ".bin"
        with open(path, "rb") as f:
            digest = hashlib.md5(f.read()).hexdigest()
        if any(m.get("md5") == digest for m in catalog["ac"][brand]):
            continue  # same file listed twice for this brand
        ac_files[name] = path
        catalog["ac"][brand].append({"id": "irext:%d" % rid, "ac": name, "md5": digest, "on": ac_ok[path]})

    # ---- Order models (most common test code first) and drop exact duplicates ----
    out_devices = {}
    stats = {}
    for device in DEVICES:
        def test_code(m):
            if device == "ac":
                return m["on"]
            for k in TEST_KEYS[device]:
                if k in m["keys"]:
                    return (k, m["keys"][k])
            return None

        popularity = collections.Counter(test_code(m) for ms in catalog[device].values() for m in ms)
        brand_list = []
        for brand in sorted(catalog[device], key=lambda b: b.lower()):
            seen, models = set(), []
            for m in catalog[device][brand]:
                sig = m.get("md5") or tuple(sorted(m["keys"].items()))
                if sig in seen:
                    continue
                seen.add(sig)
                models.append(m)
            models.sort(key=lambda m: (-popularity[test_code(m)], -len(m.get("keys", {})),
                                       0 if m["id"].startswith("irext") else 1))
            entry = {"brand": brand, "models": [{k: v for k, v in m.items() if k not in ("md5", "on")} for m in models]}
            if brands.spoken.get(brand):
                entry["spoken"] = brands.spoken[brand]
            brand_list.append(entry)
        # "Try common codes": the most widely shared test codes across all brands, one model each.
        common, used = [], set()
        for code, _ in popularity.most_common():
            if code is None or code in used:
                continue
            for ms in catalog[device].values():
                m = next((m for m in ms if test_code(m) == code), None)
                if m:
                    common.append({k: v for k, v in m.items() if k not in ("md5", "on")})
                    used.add(code)
                    break
            if len(common) >= 12:
                break
        # The brand picker opens on these: the watch list first, then the brands with the most remotes.
        present = {b["brand"] for b in brand_list}
        featured = [b for b in WATCH_BRANDS[device] if b in present]
        for b in sorted(brand_list, key=lambda b: -len(b["models"])):
            if len(featured) >= 16:
                break
            if b["brand"] not in featured and re.fullmatch(r"[A-Za-z][A-Za-z0-9 &+.-]*", b["brand"]):
                featured.append(b["brand"])
        out_devices[device] = {"brands": brand_list, "common": common, "featured": featured}
        stats[device] = (len(brand_list), sum(len(b["models"]) for b in brand_list))

    # ---- Write ----
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(os.path.join(OUT, "ac"))
    used_ac = {m["ac"] for d in out_devices["ac"]["brands"] for m in d["models"]} | \
              {m["ac"] for m in out_devices["ac"]["common"]}
    for name in sorted(used_ac):
        shutil.copy(ac_files[name], os.path.join(OUT, "ac", name))
    patterns.write(os.path.join(OUT, "patterns.bin"))
    version = "irext-20260519+flipper-" + (read_flipper_rev() or "unknown")
    with open(os.path.join(OUT, "catalog.json"), "w") as f:
        json.dump({"version": version, "devices": out_devices}, f, separators=(",", ":"), ensure_ascii=True)

    size = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(OUT) for f in fs)
    print("Wrote %s (%.1f MB): %d patterns, %d AC files, version %s" % (
        os.path.relpath(OUT, ROOT), size / 1e6, len(patterns.items), len(used_ac), version))
    for device in DEVICES:
        print("  %-9s %4d brands %5d models" % (device, *stats[device]))
    print("  dropped:", dict(dropped))
    names = {d: {b["brand"] for b in out_devices[d]["brands"]} for d in DEVICES}
    for device, watch in WATCH_BRANDS.items():
        missing = [b for b in watch if b not in names[device]]
        print("  %-9s watch list missing: %s" % (device, ", ".join(missing) or "none"))


def read_flipper_rev():
    marker = os.path.join(DATASET, "README.md")
    m = re.search(r"commit `([0-9a-f]{8})", open(marker).read()) if os.path.exists(marker) else None
    return m.group(1) if m else None


if __name__ == "__main__":
    sys.exit(main())
