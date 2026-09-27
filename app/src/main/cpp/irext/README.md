# IRext decoder (vendored)

The AC decoder from [irext/core](https://github.com/irext/core) `decoder/src` at commit
`628b3b563ad5a837a1bf595f1848517364fbe036` (2026-08-11), MIT license (see `LICENSE`). Unmodified; only the
sample JNI (`ir_decode_jni.*`) and the test program were left out. FixLens calls it through
`../fixlens_ir.cpp`. It turns an AC remote's binary file (`assets/ir/ac/*.bin`) plus a wanted state
(power, mode, temperature, fan, swing) into the IR timing list for that state.

The decoder keeps one open remote in global state: only call it from the single `fixlens-ir` thread.
