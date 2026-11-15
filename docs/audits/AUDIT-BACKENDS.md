[English](AUDIT-BACKENDS.md) | [Português](AUDIT-BACKENDS.md)

# AUDIT-BACKENDS — the parity matrix per target (living; the deep audits per P0/P1 cycle)

The suite is the differential engine (the golden battery 90 cases × 4 targets
348/0F + the ConformanceMatrixTest 105 cases × 4 + the E2E families). The
per-target state at this baseline:

| Target | Compiles/Runs | The contract notes |
|---|---|---|
| JVM | GREEN (the suite) | the FFM FFI face (the copy-out opt-in landed) |
| Native x86-64 | GREEN (the suite + the real-hardware ESP8266-style runs) | the direct-payload copy-out (borrowed) landed; the free-list+bump heap (objects do not move) |
| Native riscv64/aarch64 | the cross faces skip HERE (no cross-gcc; the CI runs them) | the array copy-out = FFI001 honest (the mirror pack is the follow-up); #1134 = the SIGBUS blocker (aarch64, real hw) |
| Script (IR) | GREEN | the FFI face = the honest refusal (D-SCRIPT-EXTERN-REFUSE) |
| KofJS | GREEN (the browser family 60/0F/2 webkit skips) | the ws/sse/TLS/path residuals named (#1158) |
| ESP8266 | EXECUTED ON REAL HARDWARE (the gate 5; the UART console byte-exact) | the gates 0-4; H4-H7 named out pending the SDK choice |
| Android | the JVM-pipeline packaging (the SDK needed for the APK E2E) | the language semantics = the JVM matrix |
| KofC | own gate (D-1.0-EDGES) | out of this host's quick audits |
| WASM/WASI | the scalar subset emitting (TIER 15.2) + the WASI faces through F | the 15.4 flip named (#1155) |

The known semantic divergences: NONE open in the shared surface (the
conformance matrix 607 DONE cells + the residuals named with refs — the
`check_conformance_1_0` gate locks it). The divergences UNDER INVESTIGATION
(the mandate §5 list): the split edge cases (§738/§740 pinned by the golden),
the native generics (§717 representation decision pending — the
maintainer's), the Maps growth (the B-42 free-list), the ABI alignment (the
HFA/struct faces measured).
