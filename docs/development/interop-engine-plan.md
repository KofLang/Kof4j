# X2 — `interop` official engine (Python/R) — implementation plan

**Status:** `UNDER DEVELOPMENT` — claimed 26/09 by lane compiler 9092 (same commit
as this doc). **Decision authority:** `D-COMPLETE-FIRST` item 2
(`DECISIONS.md` §`D-COMPLETE-FIRST`, maintainer 26/09) — the FULL package is the
decision; stubs, facades and "accepted gaps" are not options (rule 11 applies to
every face that reaches the language surface).

## Landing log (measured deltas against the table — the table stays as the claim; this is what shipped)

**Fatia 1 LANDED 26/09 (commit `X2-f1`, tests in the same commit).** Deltas
measured while building:
1. **Model = stateless replay over `-c`, not a long-lived `python -`.** Measured:
   `python3 -` on a pipe executes NOTHING before stdin EOF, and the F10 handle has
   no `closeStdin` — the RPC-over-stdin design was physically dead on arrival. The
   engine therefore runs the whole program per call: `process.spawn("python3",
   "-u", "-c", source + prelude, spec)` — the session IS the source (definitions
   persist across calls; mutated globals do not — declared contract, not a hidden
   stub). A live-session face needs a named handle type = new compiler surface =
   rule 6, recorded as a future slice, NOT improvised.
2. **Targets = {JVM, NATIVE x86, JS, SCRIPT}.** SCRIPT was expected to refuse and
   instead runs the REAL engine — the interpreter resolves `kof_process_spawn` by
   reflection in the same generated `KofRuntime` (construction-parity, measured:
   `InteropPyScriptE2ETest` golden ≡ JVM). riscv64/aarch64 refuse `INTEROP005`
   because the cross asm never received `kof_json_encode_double`/`encode_long`
   (JSN001 closed x86-only — catalogued §514, OPEN, owner lane native);
   ANDROID/MCU/RISCV32 refuse until their process face is EXECUTED and proven (R7).
3. **§513 found and fixed at the root in the same commit:** the engine's
   `List<Double>` arg exposed that `json.encode` collapsed raw Double/Long slots
   to `encode_int` (x86 list+map walkers) and that JVM `List<Bool>` cast
   `Boolean`→`Integer`. Proof: `JsonNativeEncodeFpE2ETest` (JVM oracle + JVM≡x86,
   would fail on pre-fix code). `Float` lists stay tag-0 — catalogued in §513.
4. **Surface as shipped (rule 11 gate):** `var py = KofPy(source)` +
   `py.callInt("sq", listOf(5))` / `callDouble` / `callBool` / `callString` —
   the type of the RESULT is the method name, the args are a homogeneous typed
   Kof list; no argv strings, no manual JSON, no reader loops in user code.
   Record args/results = fatia 2 (X6 fold synergy — `json.decode<Record>` scalar
   paths already exist on the JVM side, measured 61-62 of `JsonCompleteE2ETest`).

**Contract (verbatim from the decision):** typed bidirectional marshalling
(`Int`/`Double`/`Bool`/`String`/`List`/`Map`/`record` ↔ JSON), real process
management (spawn, stdin/stdout, timeout, exit, cancel), session state, named
`INTEROP00x` errors, E2E per target, corpus synchronized. Born `experimental`
(R5).

## KOF-first design (measured 26/09, not memory)

- **Zero new namespaces.** The `interop` namespace + HOST_IMPORT `kof.interop`
  already exist (X6, `D-INTEROP-REFLECT`; stdlib-boundary ledger line 68, layer
  `interop experimental`) — the engine EXTENDS it. `scripts/stdlib_boundary.sh`
  stays green with no new row.
- **Engine written in Kof, not Java** (`D-KOF-AS-CLOUD`/`D-BOOTSTRAP` precedent
  `interop-host.kf`): the RPC loop, marshalling and session state live in a
  compiler-injected host (`dev/kof/interop-py-host.kf`), composed from the
  platform that already exists (iron rule 2): `kof.process` (JVM
  `ProcessBuilder`, x86 `RuntimeProcess` pipe2/execvp, cross `Rt` ports —
  measured alive on all executable faces) + `kof.json` (encode/decode, tagged
  maps, sorted-key determinism §106).
- **Codecs are the boundary, never the foundation** (X6 principle, reused):
  scalar/List/Map/record marshalling goes through `json.encode`/`json.decode` —
  no hand-rolled parser (anti-pattern table).
- **Named diagnostics (R6):** free codes measured 26/09 — `INTEROP001`/`002`
  (X6 schema) and `INTEROP003` (§510 non-JVM external static) are taken; the
  engine claims **`INTEROP004`** (interpreter not found at spawn — honest
  runtime error, never a silent empty result), **`INTEROP005`** (target/face
  not backed — compile-time refusal, the §510 gate pattern), **`INTEROP006`**
  (remote-side failure — engine error / traceback surfaced, named). Codes get
  parity-matrix rows + `DomainGapCodesTest` pins as they ship.
- **Surface (rule 11 gate before landing):** the user writes intention —
  `py.call("area", r)` — not mechanism (no argv strings, no manual JSON, no
  reader loops in user code). Exact names are frozen in fatia 1 with the
  training row; the Simplicity Law applies at the surface commit.

## Slices (each a complete vertical cut with proof — never a facade)

| # | Slice | Scope of COMPLETE delivery | Proof |
|---|---|---|---|
| 1 | **Python engine on JVM** | host `.kf` + typer/lowerer wiring; session = long-lived `python3 -u` over `kof.process`; typed args → JSON line on **stdin**, typed result ← JSON line on stdout (RECON first: measure `kof.process` stdin-write surface — if the Kof API lacks it, fatia 1 extends `kof.process` honestly for ALL its targets, it is platform work, not interop work); `INTEROP004` on missing interpreter; `INTEROP006` names the remote failure | E2E with `assumeTrue(python3 present)` (node/qemu precedent); round-trip per type incl. record; idempotency; error edges |
| 2 | **R engine** | same host machinery over `Rscript` (host lacks it → `assumeTrue` guard; CI ubuntu availability measured in-slice) | E2E + source binding via existing `interop.schema` (X6 synergy, zero new reflection) |
| 3 | **timeout / cancel / session state** | named `INTEROP00x` for each; no silent hang (precedent §418 bounded wait) | E2E edges (hang, cancel, reuse) |
| 4 | **Native / JS / Script faces** | MEASURE per face: real port where the platform backs it, otherwise `INTEROP005` compile-time refusal (JVM-first is R7, and §510 proved the honest-refusal face is complete delivery for a target) | per-target goldens or refusal pins |
| 5 | **Corpus + promotion DoD** | `training/idioms/interop.md` EN+PT, `learn/` section, parity-matrix rows, `ecosystem-coverage`, CHANGELOG discipline; R5 stability review | docs-lang/refs gates |

**Closure:** item 2 CLOSES when every slice above has shipped evidence — then
`DECISIONS.md` gets the LANDED note, the roadmap row flips ✅ and this doc moves
to `docs/` (three-states rule).

**Do NOT touch:** `CompilerDriver.java`/`NativeRuntime.java` beyond the minimal
wiring hunks (golden rule); other lanes' IN PROGRESS files (memory/, media
cross); PR #619 (rule 10).
