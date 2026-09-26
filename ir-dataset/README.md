# IR dataset (AC, TV, projector, fan)

Infrared remote codes for the "Fixy takes the remote" feature. Only appliances that actually have an IR
receiver are included: **AC** (split, window, cassette), **TV**, **projector** and **fan**. Fridges, washing
machines and cars have no IR receiver, so FixLens covers them with the camera + KB instead. Robot vacuums and
air purifiers were left out on purpose (thin coverage, mostly app-controlled).

Nothing here is used by the app yet. A build step will later turn it into a compact `app/src/main/assets/ir/`.

| Source | License | Snapshot | AC | TV | Projector | Fan |
|---|---|---|---|---|---|---|
| [IRext database](https://github.com/irext/database) | MIT | `irext_db_20260519` + `irext-binaries_20260519` | 1547 remotes / 246 brands | 1760 / 238 | 195 / 75 | 230 / 71 |
| [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) | CC0 | commit `d126fb1b` (2026-04-11) | 158 files | 397 files | 126 files | 155 files |

## `irext/`

- `irext_ac_tv_projector_fan.sqlite`: the IRext tables filtered to categories 1 (AC), 2 (TV), 7 (fan), 8 (projector):
  `category`, `brand`, `remote_index`, `decode_remote`, `key_mapping`. Schema unchanged from upstream.
- `remotes.csv`: one row per remote (`remote_index_id, category, brand_en, brand_zh, protocol, binary_file,
  has_decoded_keys`). Easiest place to browse. 36 rows have no English brand name upstream.
- `binaries/irda_<remote_map>.bin`: IRext's per-remote binary files (2649 unique). One remote (id 13242) has
  no binary upstream.
- `LICENSE`: IRext MIT license.

**TV, projector, fan: ready to send.** `decode_remote` already holds each key as a raw timing list in µs
(mark, space, mark, …), e.g. `key_name='power'`, `key_value='4500,4500,560,560,…,'` (trailing comma).
Keys include `power, mute, vol+, vol-, up, down, left, right, ok, back, input, menu, home, 0-9`.
1694 of 1760 TV remotes, all projectors and 227 of 230 fans have decoded keys. A brand has several remotes
(different protocols); pairing tries them in turn.

```sql
-- all power codes for Samsung TVs
SELECT r.id, r.protocol, d.key_value FROM decode_remote d
JOIN remote_index r ON r.id = d.remote_index_id
JOIN brand b ON b.id = r.brand_id
WHERE r.category_id = 2 AND b.name_en = 'SAMSUNG' AND d.key_name = 'power';
```

**AC: needs the decoder.** AC remotes send the whole state (power, mode, temperature, fan, swing) in every
frame, so there are no fixed key codes. The `.bin` file for the remote plus IRext's C decoder
([irext/core](https://github.com/irext/core), `ir_decode`) produce the timing list for any state. The carrier is
not stored; 38 kHz is the usual value.

## `flipper-irdb/`

`ACs/`, `TVs/`, `Projectors/`, `Fans/` as `<Brand>/<file>.ir`, plus the CC0 `LICENSE`. Flipper `.ir` text format:

```
name: Power
type: parsed
protocol: NECext        # NEC, NECext, Samsung32, RC5, RC6, SIRC, SIRC15, SIRC20, Kaseikyo, RCA, …
address: 04 00 00 00
command: 08 00 00 00
#
name: Off
type: raw
frequency: 38000
duty_cycle: 0.330000
data: 3302 1612 440 1210 …   # µs, mark first
```

`parsed` entries need a small encoder per protocol; `raw` entries can be sent as-is. AC files are fixed button
captures (most common: `Off`, `POWER`, `TEMP+`, `TEMP-`, `Mode`, `Fan`), not full state control; use IRext
for ACs. Protocols across the four folders, most used first: NEC, NECext, Samsung32, RC5, SIRC, Kaseikyo,
SIRC15, RC6, RCA.

## Sending on Android

`ConsumerIrManager.transmit(carrierHz, IntArray)` with the µs list (mark first, even count preferred,
total < 2 s). Needs the `android.permission.TRANSMIT_IR` permission (not yet added; STACK.md §5 allows only
CAMERA and RECORD_AUDIO).

## How this was built

1. IRext: Git LFS files `db/irext_db_20260519_sqlite3.db` and `binaries/irext-binaries_20260519.zip` from
   github.com/irext/database, filtered to categories 1, 2, 7, 8.
2. Flipper-IRDB: `git clone --depth 1 --filter=blob:none --sparse`, then
   `git sparse-checkout set --no-cone /ACs/ /TVs/ /Projectors/ /Fans/ /LICENSE`.
