[English](AUDIT-BUGS.md) | [Português](AUDIT-BUGS.md)

# AUDIT-BUGS — found / reproduced / fixed / pending (living; updated per cycle)

## Found + fixed THIS operation (so far: the pre-operation week is history in DOING/CHANGELOG)

(none yet — the operation starts with the P0 survey)

## Reproduced + FIXED earlier (the record for the P0 pattern)

- The copy-out regression (the by-value contract broken by my blanket
  copyback) — the issues/tooling lane measured, I fixed at the root (the
  ownership-borrowed opt-in; the by-value default restored) — RED-first
  measured, `FfiArrayE2ETest` 5/5 restored. The lesson recorded: the array
  copy-back is OPT-IN (`ownership borrowed`), never blanket.
- The catch-String null-bind flake (`NativeCatchableRuntimeFaultE2ETest`) —
  root-caused (the message-less fault bound null) and fixed with
  `KofInterpreterFrameCatchStringTest` 5/5 (the issues/tooling lane).
- The stale-red gate that hid 5 days (`check_portukof_tooling`) — the
  #1101 rename never synced the pattern; fixed + wired into the stability
  list (the desync can never hide from the cut verdict again).

## Reproduced + PENDING (the honest pending list, the P0/P1 queue)

| Bug | Reproduced? | The blocker |
|---|---|---|
| #1134 aarch64 SIGBUS (the misaligned sp) | statically (31 misaligned SP sites in the kept asm; the gdb frame pinned in the issue) | arm64 hardware or qemu-user+the SP-alignment assertion — NOT available on this host; the fix = the push/pop redesign in `NativeAarch64Translator` (the 615 `[sp, #N]` offsets). The mandate's question (the support-contract coherence) goes to the P0 cycle: the coherent options are (a) fix, (b) demote the aarch64 target from the supported surface, (c) document the limitation — (b)/(c) are maintainer decisions (the reclassification rule forbids (b) without her). |
| The cross copy-out (riscv64/aarch64) | FFI001 honest (the mirror pack exists in the riscv asm) | the mirror-slice work (the P1 cycle) |
| The pcap capture pointer-heavy | FFI001 honest | the Handle/`pcap_t*` binding slice (the P1 cycle) |
| ws/sse/TLS/path (JS + native) | the honest refusals named | the WEB002/WEB008 slices (the P1 cycles, large) |

## NOT reproducible on this host (the named environment limits)

- #1135 macOS (no Apple hardware), #1134's live crash (no arm64), the
  cross-gcc faces (no cross toolchains here — the CI runs them).
