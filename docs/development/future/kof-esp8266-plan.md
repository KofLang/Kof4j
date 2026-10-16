[English](kof-esp8266-plan.md) | [Português](kof-esp8266-plan.pt_BR.md)

last: none
doing: none-planned
next: gate-0-architecture-and-feasibility-audit
location: docs/development/future
state: planned

intent: kof-esp8266-embedded-target

**Rule 6 gate:** the ESP8266 target is a NEW compilation target (a new backend
surface + a new toolchain/SDK integration). This document is **plan only, zero
code** — no compiler, runtime, CLI or dependency change is made by it. Promotion
requires a maintainer decision (`DECISIONS.md`, `D-*` id by the maintainer) and
the future-promotion flow (`docs/development/future/README.md` §"When to move",
`AGENTS.md` §"Future promotion"): rewrite with `UNDER DEVELOPMENT`, real state +
how-to-finish, queue in `roadmap.md` §23, point `docs/status.md` at it, claim in
`DOING.md`. **Nothing here is implemented, measured on hardware, or verified.**

**Source:** maintainer request (14/10/2026) — "Kof Future — ESP8266 Embedded
Target". Related: `docs/PLAN-BAREMETAL-BOOT.md` (the HAL seam + the MCU
precedent), `docs/native-multiarch.md` (multi-arch native), `docs/stdlib/PLAN-TREE-SHAKING.md`
§T3 (the embedded/MCU route), `roadmap.md` §23 item 1.6 (native → bare-metal).

---

# `kof.esp8266` — the ESP8266 embedded target (plan, zero code)

## 1. Motivation and intended use cases

The ESP8266 (Espressif, Xtensa L106) is a **32-bit, single-core, Wi-Fi SoC** —
a **compatibility and maker target**: extremely cheap, extremely constrained
(~80 KB of user DRAM, a few MB of flash, no MMU), with a hard lifecycle reality
(the vendor moved its first-class support to the ESP32 family). It is *not* a
systems target and must never be presented as one.

Why it matters to Kof:

- It is the **smallest honest endpoint** for the Kof "one language, many
  targets" claim: if a Kof program can be compiled, linked and **executed on an
  ESP8266**, the claim survives contact with a device that can afford neither a
  JVM nor Node.js nor a hosted OS.
- It is a **credible maker/education target**: cheap hardware, a well-known SoC,
  a large body of existing tutorials against which a Kof workflow can be
  compared honestly.
- It forces the **resource discipline** the platform keeps deferring:
  minimal runtime, explicit unsupported-feature diagnostics, memory/stack
  reporting, a linker-script-sized heap.

Intended use cases (planned, not existing): sensor nodes and telemetry
(GPIO/PWM/UART/I2C/SPI around a Wi-Fi uplink), small network devices (TCP/UDP
client and server), and teaching/experimentation with Kof on real hardware.

## 2. Architectural approach

**Reuse the existing pipeline.** Kof source → the existing frontend/IR → a
target-specific backend or a compatible native path → the Xtensa toolchain →
firmware image. ESP8266-specific functionality stays **isolated behind
target-specific interfaces**; the Kof language surface and IR are preserved.

**The MCU precedent already exists in the repo (measured, not planned).** The
bare-metal front (`docs/PLAN-BAREMETAL-BOOT.md`) already enrolled two MCU
targets as **programmatic machinery** (no CLI surface yet):

| Target enum value | Face | Contract today |
|---|---|---|
| `Target.NATIVE_RISCV32` | B-4 | bare-metal RV32I, `main` with literal `print`/`println` over the `-M virt` UART, poweroff at the test device; out-of-subset ops → `NATIVE002` |
| `Target.NATIVE_MCU_ARM` | B-4.3 | bare-metal Cortex-M3 Thumb-2 (`mps2-an385` CMSDK UART), vector table at `0x0`; concurrency → `CONC003` |

Their tests (`NativeMcuE2ETest`, `NativeMcuArmE2ETest`, `NativeMcuArmGcTest`,
`NativeMcuArmTimeTest`, `NativeMcuGcTest`) are the **proof pattern** the ESP8266
faces must follow. The HAL seam `kof_plat_write` / `kof_plat_exit` /
`kof_plat_alloc` / `kof_plat_time` / `kof_plat_sync` / `kof_plat_thread`
(face B-0 of the same plan) is the **runtime boundary** the ESP8266 runtime must
implement — not a new one.

**Therefore the preferred approach is:** enroll ESP8266 as a **third MCU face**
of the same architecture — a new `Target` value (naming by the implementing
lane), a backend/emitter for the Xtensa L106 subset, and a **`kof_plat_*`
implementation backed by the ESP8266 SDK** instead of a new runtime. A dedicated
independent backend is a **fallback only**, and only if Gate 0 establishes that
the current native machinery cannot reach the target.

**Known uncertainties (unverified — Gate 0 must measure them):**

1. **Xtensa codegen and scheduling.** The L106 has no pipeline interlocks and
   requires load/store hazard handling (`l32r` literal pools, `memw`, register
   windows). Whether the existing native emitter can be extended, or whether an
   assembler-level emission path suffices, is **unknown**.
2. **Toolchain reachability.** The Xtensa toolchain (`xtensa-lx106-elf-*`) and
   the ESP8266 SDK are **not** part of the current build; whether they can be
   provisioned reproducibly (and cross-platform) is a Gate 0 finding, not an
   assumption.
3. **SDK vs bare-metal.** The ESP8266 is never truly "no OS" in practice: the
   SDK provides the Wi-Fi stack and a minimal scheduler. Which SDK flavor is used,
   and how much of it is admitted into the runtime, decides how much of the
   bare-metal purity claim survives — this is an honest-scope question, recorded
   here, not resolved.
4. **Heap and GC.** The bump-pointer heap plus mark-sweep GC of the current
   native runtime may not fit. A pool sized by the linker script (the bare-metal
   plan's approach) is the candidate; whether GC is viable at all on this device
   is a Gate 0/Gate 2 finding.
5. **Flash vs RAM split.** Firmware is executed from flash; a heap/stack layout
   that works on a hosted OS does not automatically map here.

## 3. Planned hardware capabilities (intended scope — NOT existing functionality)

Organized into incremental stages. No stage requires all peripherals; each stage
is independently deliverable and independently provable.

| Stage | Capability | Notes |
|---|---|---|
| **H1** | GPIO input and output | the first provable hardware surface |
| **H2** | Timers and delays | incl. the platform clock behind `kof_plat_time` |
| **H3** | UART serial communication | the first stage that gives a usable console |
| **H4** | PWM (where supported by the selected SDK) | "where supported" is explicit — never claimed unconditionally |
| **H5** | I2C and SPI | the sensor-bus stage |
| **H6** | Wi-Fi station connectivity | the SoC's defining capability; depends on the SDK choice |
| **H7** | TCP and UDP networking | rides `kof.net`'s existing surface where the runtime admits it |
| **H8** | Firmware generation, memory reporting, optional flashing | the developer-workflow stage (`kof build`, artifact inspection) |

## 4. Implementation gates (ordered)

### Gate 0 — Architecture and feasibility audit

Inspect the existing compiler, IR, backend, runtime, CLI and test
infrastructure. Verify Xtensa L106 compatibility and identify toolchain
requirements. Determine which language and stdlib features are viable on the
target. Identify architectural blockers and document the **selected** approach.

Exit criterion: an **evidence-based** implementation design consistent with the
existing Kof architecture (or an honest refusal, if the audit finds the target
out of reach).

### Gate 1 — Target infrastructure

Register the `esp8266` target (a new `Target` value + the dispatch, following
`NATIVE_RISCV32`/`NATIVE_MCU_ARM`). Integrate the selected toolchain and SDK.
Establish startup, linking, memory layout and firmware artifact generation.

Exit criterion: a minimal Kof program compiles and links into a **valid
ESP8266 firmware image**.

### Gate 2 — Minimal embedded runtime

Establish the firmware entry point and initialization sequence. Remove
unnecessary hosted-runtime dependencies. Define the supported language features
and **actionable diagnostics** for unsupported constructs (the `NATIVE002` /
`CONC003` pattern — never a silent fallback or a silent emulation). Add memory
and stack usage reporting where feasible.

Exit criterion: the minimum supported Kof program operates within the target's
memory and runtime constraints.

### Gate 3 — Hardware abstraction

Implement GPIO, UART, timers and PWM where supported (stages H1–H4), followed by
I2C, SPI and Wi-Fi networking (H5–H7). The HAL is exposed through Kof's existing
module and stdlib conventions — no ad-hoc surface.

Exit criterion: documented APIs and reproducible tests for each delivered
capability.

### Gate 4 — Developer workflow

Integrate compilation, artifact inspection, optional flashing and serial
monitoring with the existing CLI conventions (`kof build`/`kof run` and the
target-flag surface in `KofCliSupport`; note the MCU faces have **no CLI flag
today** — adding one is part of this gate). Most ESP8266 boards have **no JTAG**;
the realistic loop is flash + serial monitor, and it must be documented as such.

Exit criterion: a documented end-to-end workflow that **does not make physical
hardware mandatory for ordinary Kof builds**.

### Gate 5 — Verification and release readiness

Add automated target and toolchain tests. Verify firmware generation and memory
constraints. Execute a minimal firmware program on **physical hardware**.
Document verified capabilities, unsupported features and reproducible setup
instructions. Verify that existing Kof targets and tests remain unaffected.

Exit criterion: **physical execution is confirmed** and all acceptance criteria
are satisfied. Compilation alone is **never** acceptance.

## 5. Architectural constraints (explicit)

- Preserve the existing Kof language semantics and IR.
- Do not duplicate the compiler or create an independent language implementation.
- Do not introduce JVM or Node.js runtime dependencies into firmware.
- Keep ESP8266-specific functionality isolated behind target-specific interfaces.
- Keep hardware-dependent tests optional.
- Do not silently emulate unsupported language features.
- **Do not claim hardware support based solely on successful compilation.**
- Keep future ESP32 support in mind **without** expanding the current scope into
  ESP32 implementation (the ESP32 is a different core and toolchain).
- Treat the ESP8266 as a **compatibility and maker target**, accounting for its
  constrained resources and lifecycle status.

## 6. Dependencies and prerequisites

| Dependency | State |
|---|---|
| The pipeline (frontend → IR → backend → toolchain) | EXISTS (measured) |
| The MCU enrollment precedent (`NATIVE_RISCV32`, `NATIVE_MCU_ARM`) | EXISTS (measured) |
| The HAL seam `kof_plat_*` (face B-0) | EXISTS — reuse, do not re-invent |
| The Xtensa toolchain + ESP8266 SDK | **ABSENT** — provisioning is Gate 0/Gate 1 |
| Physical ESP8266 hardware | **ABSENT from this repo's environment** — Gate 5 |
| A maintainer decision to promote this plan | **ABSENT** — rule 6 |

**Cross-cutting rule R12** (`roadmap.md` §23): no future-plan item is an action
on the current state, and new fronts do not open before the SYSTEMS stage closes.
The bare-metal front was opened by an explicit **maintainer override** of R12;
this plan would need the same kind of explicit order to be promoted.

## 7. Related plans and references

- `docs/PLAN-BAREMETAL-BOOT.md` — the HAL seam (`kof_plat_*`), the freestanding
  profile, and the MCU faces **B-4** (`NATIVE_RISCV32`) / **B-4.3**
  (`NATIVE_MCU_ARM`) that this plan reuses as its architectural pattern.
- `docs/native-multiarch.md` — the multi-arch native front
  (x86_64 · riscv64 · aarch64) and its per-arch GC/ABI findings.
- `docs/stdlib/PLAN-TREE-SHAKING.md` §T3 — the recorded embedded/MCU route and
  its three measured blockers (hardcoded `-lc`, direct Linux syscalls, the
  bump+mark-sweep GC), plus the "real embedded = an RTOS/bare-metal backend of
  its own" boundary this plan must pay for.
- `docs/native-multiarch.md` / `Target.java` — the `isNative()` predicate that a
  new MCU target must join.
- `roadmap.md` §23 item **1.6** (native → bare-metal/bootable) — the front that
  produced the HAL seam and the MCU precedent.
- `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` — the platform
  strategy (SYSTEMS stage) whose closure gates new fronts (R12).

## 8. Explicit non-goals

- **Not** an ESP32 implementation (different core, different toolchain —
  out of scope; "keep it in mind" is not "implement it").
- **Not** a Wi-Fi or networking engine written in Kof — the SDK's stack is used
  through interop; no reimplementation.
- **Not** a general RTOS.
- **Not** a bare-metal purity claim: using a vendor SDK is an accepted,
  documented compromise, not a violation to be hidden.
- **Not** a hardware purchase or CI-with-hardware programme (hardware tests are
  optional and local).
- **Not** a rewrite of the compiler, IR or the existing native backends.
- **No** language-surface change, and **no** new syntax.

## 9. Acceptance criteria (what would close the initiative)

Planned functionality and implemented functionality are distinguished everywhere:
this document is the former. The initiative is complete only when:

1. the target compiles and links a minimal Kof program;
2. a valid firmware image is generated;
3. memory constraints and unsupported features are handled **explicitly**
   (actionable diagnostics, never silent emulation);
4. the documented workflow is reproducible;
5. a **physical ESP8266 device executes** the generated firmware;
6. existing targets remain functional (zero regression);
7. the documentation reflects **actual verification results** — no completion
   dates, no implementation status, no test counts and no hardware capabilities
   are to be invented at any point.

## 10. Verification requirements

- Automated target/toolchain tests follow the existing MCU proof pattern.
- Firmware generation and memory constraints are verified by artifact inspection.
- **Physical execution** on hardware is the only proof of the hardware claim.
- Existing suites must stay green on every target after any change (zero
  regression is a standing constraint, not a gate-only check).
- Hardware-dependent tests remain **optional** and must be named as such when
  skipped.