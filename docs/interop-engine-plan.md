# X2 — `interop` official engine (Python/R) — implementation plan

last: fatia-4-cross
doing: interop-engine
next: none-concluded
location: docs/interop-engine-plan
state: done
intent: ship-complete-interop-engine
constraint: pr619-maintainer-only
decision: D-COMPLETE-FIRST

Full package only — stubs, facades and "accepted gaps" are not options (rule 11 applies to every face reaching the language surface; `DECISIONS.md` §`D-COMPLETE-FIRST` item 2, maintainer 26/09).

## Contract

Typed bidirectional marshalling (`Int`/`Double`/`Bool`/`String`/`List`/`Map`/`record` ↔ JSON), real process management (spawn, stdin/stdout, timeout, exit, cancel), session state, named `INTEROP00x` errors, E2E per target, corpus synchronized. Born `experimental` (R5).

## Slices

| # | Slice | State | Evidence |
|---|---|---|---|
| 1 | Python engine on JVM | landed 26/09 | targets {JVM, NATIVE x86, JS, SCRIPT}; `InteropPyScriptE2ETest` golden ≡ JVM |
| 2 | R engine `KofR` | landed 27/09 (`996777923`), CI-certified on `9ec0a4eb9` | record round-trip golden byte-identical to Python |
| 3 | timeout / cancel / reuse | landed 27/09 (`d7328c036` + §527 `9b4fa30f5`) | `InteropTimeoutE2ETest` 4/4, `InteropTimeoutScriptE2ETest` 1/1, `InteropRE2ETest` +2 R-gated |
| 4 | Native / JS / Script cross faces | landed 27/09 (lane issues, maintainer order "reinvindique e termine") — timeout007/deadline-reuse/idle-cancel + R-happy E2E riscv64≡aarch64≡JVM under qemu (`InteropTimeoutE2ETest` +3, `InteropRE2ETest` +1 R-gated); 008 stays JVM-only by design (object across spawn is not a Native contract); ANDROID/MCU/RISCV32 stay R7 | `INTEROP005` remains only where honestly unbacked (ANDROID/MCU/RISCV32, R-absent hosts) |
| 5 | Corpus + promotion DoD | landed 27/09, docs-only (`8aa5883c9` matrix, `eae0ac18e`+`06319455f` idioms, `745d1ed0f` learn, `6b70bbaff` coverage) | docs gates green |

## Design (KOF-first, measured)

- Engine written in Kof, not Java (`D-KOF-AS-CLOUD`/`D-BOOTSTRAP` precedent): RPC loop + marshalling + session live in compiler-injected host `dev/kof/interop-py-host.kf`, composed from `kof.process` + `kof.json`.
- Zero new namespaces: `interop` + `kof.interop` already exist (X6, `D-INTEROP-REFLECT`; stdlib-boundary line 68). Codecs are the boundary — only `json.encode`/`json.decode`, no hand parser.
- Surface (rule 11): `var py = KofPy(source)` + `py.callInt/callDouble/callBool/callString(fn, listOf(...))`; result type = method name; args = homogeneous typed Kof list. Record args/results: `py.callJson(fn, args)` + user-side `json.decode<R>(payload)`.
- Model = stateless replay over `python3 -u -c source+prelude spec`; the session IS the source (definitions persist, mutated globals do not). A live-session face needs a named handle type = new compiler surface = rule 6, future slice.
- R channel: the spec travels embedded in the `-e` expression as an escaped R string literal (`kofREscape`, order proven by the golden); source re-applied via `eval(parse(text=s$source))`.
- Wire (internal protocol, not external contract): line 1 `KOFPID <pid>`, line 2 status (`KOFOK`/`KOFERR`/`KOFTIME`/`KOFCANCEL`/`""`), line 3 JSON payload. Deadline lives in the CHILD (python `signal.setitimer` SIGALRM→`TimeoutError`; R `setTimeLimit`); R cancel is parent-named (`tools::signalHandler` cannot emit `KOFCANCEL`). `timeout(Int ms)` default 30000, `0` = no timer.
- Session state = declared cut-out. Targets {JVM, NATIVE x86, JS, SCRIPT} real; `INTEROP005` on riscv64/aarch64 (cross lacks `kof_json_encode_double`/`_long`, JSN001 x86-only, §514) and ANDROID/MCU/RISCV32 (process face unproven, R7).
- Diagnostics: `INTEROP001`/`002` (X6 schema) and `003` (§510) taken; the engine owns `004` (interpreter not found), `005` (target/face not backed, compile-time), `006` (remote failure), `007` (timeout), `008` (cancel).

## Bugs found on the path

- §520 (fatia 2): 4 root causes in record json encode/decode (schema collector swallowed String fields; native decode fed the un-quoted body; `encode_string` left control bytes raw; interpreter missing tag-4 in `encode_list`). Fixed at the root. `List<Record>` DECODE on x86 stays `JSN004` (declared).
- §516: x86 `json.encode(listOf(record))` dumped the raw object pointer; fixed by the fatia-2 encode walker.
- §513 (fatia 1): `json.encode` collapsed Double/Long slots to `encode_int` (x86 list/map walkers); JVM `List<Bool>` cast `Boolean`→`Integer`. `Float` lists stay tag-0. Proof `JsonNativeEncodeFpE2ETest`.
- §527 (fatia 3): void-await left the runtime `Object` on the JVM stack → `VerifyError` at LOAD; fixed at the single lowering site, `VoidAwaitStackFrameE2ETest` 4/4.

## Ecosystem relation

- `kof-connector-ecosystem-plan.md` is NOT pulled into development — opening it needs an explicit `D-CONNECTORS` (rule 6). The X2 engines ARE the process form of the catalogue §5.5 (Python) / §5.6 (R); the embedding/CPython-C-API/R-C-API path remains unbuilt and is not X2. If `D-CONNECTORS` opens, the X2 wire + faces continue as the process connector's adapter; `INTEROP00x`, goldens and tests carry over. Slices 3–5 unaffected.

## Closure

Item 2 CLOSES when every slice ships evidence → `DECISIONS.md` LANDED note, roadmap row ✅, and this doc moves to `docs/` (three-states).

Out-of-lane (registered, untouched): `learn/21-java-interoperability.md` carries a duplicated "Reflection at the boundary" section (lines 83/116, from `29b8af404` X6) — review request to the X6 owner.

**Do NOT touch:** `CompilerDriver.java`/`NativeRuntime.java` beyond the minimal wiring hunks; other lanes' IN PROGRESS (`memory/`, media cross); PR #619 (rule 10).
