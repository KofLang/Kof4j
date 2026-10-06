## Deterministic native regressions introduced by §612 (`1d9415d00`) — found by the 0.6.0 cut-readiness audit (06/10)

Bisect evidence on `lab` (JAVA 25, x86-64 host + qemu cross, same 5-class battery):
- parent `36f72695e`: **147 run / 0F / 0E**
- `1d9415d00` (§612): **147 run / 5F** — all five faces reproduce deterministically
- tip `fd0ff7cb9`: **5F** (unchanged since §612)

The five faces (from the §612 battery run):
1. `KofDbE2ETest.nativeSqliteRoundtrip` — JSON of a native row: expected `{"id":7,"name":"Nativa"}` but was `{"id":"","name":"Nativa"}` — int→string value-of now yields the EMPTY string on native.
2. `Av1CoeffsE2ETest.av1CoeffsOnNativeAarch64` — qemu SIGSEGV (exit 139).
3. `Vp8RasterE2ETest.vp8RasterOnNativeRiscv64` — "Runtime error: array index out of bounds" (exit 1) — value corruption on cross.
4. `KofOrmE2ETest.crossNativeF1aDeleteAllCountMatchX86Oracle` — D-DB-GAPS contract broken (drop -> count 0 / deleteAll false oracle mismatched).
5. `ArtifactSizeTest.helloX86NativeSizeWithinBaseline` — hello went `<110` to **126 syms**: `wrapperValueOfBoxFn` now emits `kof_box_*` at every numeric-wrapper `valueOf` site, so runtime slices the S-3 prune used to drop are back (the §612 change widened box emission).

Suspected common root (from the §612 diff in `NativeOpHelpers.wrapperValueOfBoxFn` + `NativeX86ValueOf` + `NativeRiscvCrossOps`): the MAGIC-box return for numeric/boolean `valueOf` replaced the JVM-style wrapper value for consumers that treat `valueOf` as STRINGIFY sugar (`"" + i`, JSON of an int column) — on native the stringifier now receives a MAGIC box handle instead of the digits, printing empty/garbage or reading it as an index (the AV1/VP8 cross failures). This is exactly the dispatch the §612 author flagged between stringify and box — the box branch now wins where stringify owned the value.

## Consequence
#772 is REOPENED: the fix shipped with a zero-regression violation (AGENTS quality-gate). `lab` at `fd0ff7cb9` is NOT stable for the 0.6.0 cut (`D-LAB-STABILITY`): suite-report SLIPS (5 deterministic native faces).

## DoD (owning lane: native-backend / §612 author, lane security/connectors precedent)
Re-scope the §612 dispatch so the stringify sugar keeps stringifying digits and only the narrowed-int-arg-to-primitive-consumer gets the MAGIC box — pin all five battery faces + the §612 fix faces green, cross riscv64/aarch64 included.
