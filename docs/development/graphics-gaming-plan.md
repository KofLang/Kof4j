[English](graphics-gaming-plan.md) | [Português](graphics-gaming-plan.pt_BR.md)

# Graphics, Games and Media — Kof's Intent Surface

last: promoted-from-future-30/09
doing: spike-3.0 (infra+report, no API)
next: slice-3.1 (window/frame/input)
location: docs/development
state: UNDER DEVELOPMENT

**Status:** **UNDER DEVELOPMENT** — promoted 30/09 from `future/` by `D-GRAPHICS-SPIKE` (spike 3.0 = measurement + stack only, no API) under `D-FUTURE-PROMOTION`.
**Owner:** lane UI.
**Normative source:** `DECISIONS.md` §D-GRAPHICS-GAMING + maintainer addenda + §D-GRAPHICS-SPIKE.
**Deps:** R3/FFI-ABI, runtime, capability matrix, stdlib boundary, conformance suite

> **Fundamental rule:** every syntax here is an **intent form**; the definitive
> language form is the maintainer's decision. No keywords/namespaces open from
> this doc. Slice 3.0 is **infra + a measurement report only** — it adds **no
> API** (the decision authorizes the spike, nothing else).

## 0.1 Real state (spike 3.0, measured 30/09)

Measured on `lab` (never by familiarity — `D-GRAPHICS-SPIKE`):

- **JavaFX: 0** — `grep -rins javafx` over `kof-*/src/**`, `pom.xml` and `*.kf`
  is **0**; every hit (237) is documentation/training prose. Enforced now by
  `scripts/check_javafx_absent.sh` (RED-first self-test in
  `scripts/tests/check-javafx-absent-test.sh`).
- **Host candidate libs (x86-64 dev box):** runtime `.so` present for SDL2
  (`2.30.0`), OpenAL (`1.23.1`), FFmpeg libavformat/avcodec (`6.1.1`); **no
  `-dev` headers** (`pkg-config` reports none of sdl3/sdl2/raylib/glfw3/openal/
  libavcodec/ffmpeg). Licenses read from the distro `copyright` files (SDL2 =
  zlib/libpng + permissive, OpenAL = LGPL-2+, libavformat = LGPL-2.1+). The
  spike measurement, not the stack pick.
- **License nuance (measured from the linked `.so`, 30/09):** the distro FFmpeg
  is **GPL-built** — `avcodec_license()` = `GPL version 3 or later`,
  `avformat_license()` = `GPL version 2 or later`, and `--enable-gpl` appears in
  `avcodec_configuration()`. The `copyright` "LGPL-2.1+" is the upstream base,
  **not** the shipped build → a GPL stack choice, if taken, is a licensing
  decision the maintainer owns (the spike only reports it).
- **Headless capability (measured ctypes probe, 30/09):** SDL2 initializes with
  no display — `SDL_Init(VIDEO|AUDIO)` rc=0 under `SDL_VIDEODRIVER=dummy` +
  `SDL_AUDIODRIVER=dummy` (`2.30.0`); OpenAL-Soft opens a null device —
  `alcOpenDevice(NULL)` + context OK under `ALSOFT_DRIVERS=null` (`AL_VERSION =
  1.1 ALSOFT 1.23.1`). raylib/GLFW/miniaudio are **not present** on the host
  (`pkg-config`/`dpkg`), so they stay unmeasured here.
- **Cross (riscv64/aarch64): not measurable yet.** No candidate `.so`/headers
  are in the distro cross sysroot, and the project's cross toolchain
  (`scripts/setup-cross-toolchain.sh`, default `/tmp/kof-cross`) was not set up
  in this environment. **Any picked stack must ship its cross libs in that
  sysroot** — a concrete, testable requirement for slice 3.1, not a promise.
- **R3/FFI substrate present** (this is the dependency the plan §3 names):
  `FfiSignature`, `AbiLayout`, `FfiStructLayout`, `CompilerFfiBinding`,
  `JvmFfiRuntime`, `NativeFfiCall`, `ExternalClasspath`, `KofProcess`
  (see `docs/ffi-abi-structs.md`). Any graphics mechanism is an R3 extension
  first — no parallel FFI.
- **`kof.ui`:** JVM/Native no-op handles, KofJS DOM (`KOFUI-AUDIT`); **`kof.media`:**
  bitmap/WAV/metadata/mic only; playback/streaming/mixer/video absent
  (`MEDIA001`/`MEDIA003`). Both stay honest gaps until a real backend lands.

**Candidate matrix (input to the maintainer's stack pick; `?` = not measured):**

| Candidate | Domain | License (`?` = confirm upstream) | Runs on host | Headless | Cross (riscv64/aarch64) | Axis |
|---|---|---|---|---|---|---|
| SDL3 / SDL2 | window+input+audio | zlib/libpng + permissive (distro `copyright`) | SDL2 `2.30.0` runtime `.so` present, no `-dev` | SDL3 `?` / SDL2 dummy driver **measured OK** | `?` | one lib, many targets |
| raylib | 2D/3D+audio | zlib (`?`) | not present | `?` | `?` | batteries-included 2D |
| GLFW + GL API | window+context | zlib (`?`) | not present | offscreen ctx (`?`) | `?` | thin, GL expertise needed |
| miniaudio | audio | public-domain/MIT-0 (`?`) | not present (header-only, drop-in) | offline mix yes (`?`) | `?` | single-header audio |
| OpenAL-Soft | audio | **LGPL-2+** (distro `copyright`) | runtime `1.23.1` `.so` present, no `-dev` | null backend **measured OK** | `?` | 3D positional audio |
| FFmpeg / Libav | video+codecs | **GPL-built** here (`avcodec_license()` = GPLv3+; `--enable-gpl`); upstream base LGPL-2.1+ | libavcodec/avformat `6.1.1` `.so` present, no `-dev` | codec API (`?`) | `?` | full codec set |

**Recommendation (measurement-driven, not by familiarity):** the plan's JVM rule
(§11: never JavaFX/Swing/AWT/`javax.sound`) plus the R3-first coupling (§3) point
to **one portable multi-target stack for window+input+audio** (SDL3 is the natural
candidate) and **FFmpeg/Libav for video codecs** (never homemade, §10/§14). The
maintainer picks; the spike only removes unknowns and restores the guard.
**Caveat from the license probe:** the FFmpeg face is only "free" if a LGPL
build is vendored — the distro one measured GPL (above), so taking it as-is is a
licensing decision, not merely technical.

**How to finish (slice order, `§15`):** 3.0 (this infra+report) → **3.1**
window/frame/input on JVM/Script/Native/JS + conformance → 3.2 (2D) → 3.3
(audio, offline PCM golden) → 3.4 (video, frame readback) → 3.5 (3D, only if
parity allows) → 3.6 (corpus). Each slice is a complete, tested unit and needs
its **stack choice** recorded as a `D-*` before any API lands (the spike report's
matrix is the input to that decision).

# 0. Objective

Intent surface (backend decides how) for: 2D/3D graphics, windows, game loops,
input, sprites, tilemaps, audio, video playback, media, KofUI integration,
games. Not a graphics language inside Kof.

# 1. Principles

- **Intent before mechanism:** `sprite("player.png").at(100, 80).draw()` (intent) vs `createTexture/bindTexture/beginBatch/drawQuad/swapBuffers` (mechanism, hidden).
- **Platform owns the loop:** user writes `frame { dt -> update(dt); draw() }`; clock/vsync/scheduling/polling/submit/present are backend.
- **No foreign APIs cross:** never `SDL_*`, `gl*`, `canvas.*`, `MediaPlayer`, `javafx.*` — backend tech is not language.

# 2. Measured state (beta-0.5.0)

- **JavaFX:** zero usage (09/21 measurement; hits are launcher-symptom comments). Not a previous implementation; decision: never introduce. No migration.
- **`kof.ui`:** JVM/Native = handle without rendering (`kof_ui_window_new`, empty setters/show); KofJS = functional DOM/webview. Gap code until real: `GFX00x`. No-op must never read as compatibility.
- **`kof.media`:** has bitmap open/save, video metadata, WAV sampling, mic enum/record. Missing: playback, streaming, mixer, video pipeline/playback, Native + JS surfaces (`MEDIA001`/`MEDIA003`). Evolve additively; existing programs keep working.

# 3. R3 dependency

Graphics crosses Kof IR → backend → ABI → runtime → library → OS/device and
needs handles/pointers/buffers/structs/callbacks/arrays/strings/lifecycle/
ownership/error-codes/native-resources. **No parallel FFI** — missing mechanism
becomes an R3 extension first.

# 4. Architecture (5 levels)

```text
Kof App (intent) → Kof Graphics API → Kof Runtime ABI (handles/buffers/events)
→ JVM/Native/JS backends → platform/browser
```

Same intent reaches every target.

# 5. Resources

Platform objects (`Window Sprite Texture Tilemap Mesh Material Camera Sound
Music Video InputDevice`) hiding impl (GL/Vulkan/WebGL/image/native/GPU).
Lifecycle per resource must answer: created/lazy/loaded/available/owner/
release/caching/window-loss. No manual GPU management when the backend can do it.

# 6. Window / loop / clock

- Window (backend-owned, form undecided): `Window("Pong") { frame { dt -> ... } }`
  vs `Scene("Pong") { dt -> ... }`; covers title/size/fullscreen/resize/focus/
  close/DPI/orientation/visibility/input; no OS APIs.
- Loop: backend owns clock/vsync/scheduling/poll/submit/present; program gets
  `dt` (unit/precision/first-frame/long-frame/limit/pause/minimized/focus TBD).
- Virtual clock required (deterministic `dt` streams) for physics/animations/
  input/audio/playback/goldens.

# 7. Input

Snapshot per frame: `keys.down("left")`, `keys.pressed("space")`;
states `down/pressed/released`; `mouse.pos/down/pressed`, `pad.stick/down/pressed`;
backend translates scancodes/X11/Wayland/KeyboardEvent/WinVK (exact semantics TBD).

# 8. 2D

First level. `sprite("player.png").at(120, 80).draw()`; transforms
`at/scale/turn/origin/flip` (API TBD); animation `player.frames("walk")` +
`player.animate()` (platform: atlas/batching/upload/selection).
Tilemaps = map intent (`tilemap("level.png", 16)`); questions: tileset/atlas/
layers/collision/animated/infinite/formats. Rendering: app declares *what*,
backend decides *how* (batching/atlas/command-buffer/order/cache/upload hidden).

# 9. 3D (later)

Minimum: mesh/camera/material/light/transform. `mesh("hero.glb")`,
`camera3d().at().lookAt()`, `draw(scene3d { ... })` — illustrative only.
No own parsers (glTF/OBJ via mature libs; criteria: license/security/coverage/
maintenance/testability/cross-platform). Shaders hidden at first (Kof/SPIR-V/
WGSL/GLSL/HLSL/cross-compile decision deferred, not first slice).

# 10. Audio/video

- Intents: `sound("boom.ogg").play()`, `music("theme.ogg").loop().play()`;
  backend owns decoder/buffer/mixer/output/device/latency/voices (app never
  makes channels/buffers/callbacks/threads). Mixer faces (`volume/pause/resume/
  stop/loop/fade/pan`) each need a cross-target contract. Latency measured
  request→submission→audible per target (value set after spike 3.0/3.3).
  Devices: common capability + honest per-target gaps (browser restrictions).
- Video: `Window("Trailer") { video("intro.mp4").autoplay() }`; no demuxer/
  decoder/codec/queue/hardware-decoder in-app. No own codecs (FFmpeg/Libav/
  native; criteria: license/target/security/maintenance/formats/headless).
- `kof.media` today (bitmap/WAV/metadata/mic) → playback/streaming/mixing/
  video-playback, additively.
- KofUI ≠ competing language (UI apps vs games); share window/input/video/
  images/events infra where equivalent.

# 11. Targets

- **JS:** browser capabilities as backend; `video("intro.mp4")` may lower to an
  HTML element (lowering detail, API stays Kof).
- **WASM:** same intent when the target exists; documented in the WASM/WASI
  plan, never duplicated here.
- **Native:** portable stack, no manual bindings; x86-64/aarch64/riscv64 enter
  the matrix individually.
- **JVM:** never JavaFX/Swing/AWT/javax.sound; route Kof→JVM→R3→portable stack.
- **Script:** same intent semantics, delegates to environment; missing
  capability → `GFX001`/`SND001`/`VID001` (never unknown-method).

# 12. Capability matrix / gaps / parity

- Matrix per operation (`window/sprite/audio/video/3d` × JVM/Native/JS/Script);
  `✓` only after implementation+tests+golden+parity+docs.
- Gap families `GFX00x/INP00x/SND00x/VID00x` (e.g. `GFX001` unavailable,
  `SND001` no playback, `VID001` no playback); final codes enter the normative
  catalog before implementation; never hide.
- Promotion = same observable contract on JVM+Script+Native+JS-Web
  (compile+execute+expected+conformance — compile-only is not enough).

# 13. Observability / conformance

- Pixels testable: render→readback→buffer→hash (never driver/GPU/framebuffer
  screenshots; format/tolerance defined at implementation).
- Audio offline: program→mixer→PCM→hash/reference (volume/mix/order/loop/
  duration/channels, no speakers).
- Conformance paths (`graphics/sprite/basic.kof`, `audio/mix/basic.kof`, …)
  run JVM/Script/Native/JS comparing observables.
- Goldens: frame/input/sprite/transform/audio-mix/media-meta/video-decode
  sequences (virtual clock, deterministic input, offline mixer, frame readback).
- Fuzz transforms/coords/sizes/textures/input/lifecycle/assets/malformed
  media/audio/video/release (untrusted files first-class).
- Security: malformed/overflow/corruption/bombs/decoder/resource/sandbox —
  never own codecs for this reason.

# 14. Stack / licensing / scale

- Candidates (spike 3.0 decides; never by familiarity): graphics SDL3/SDL2/
  raylib/GLFW+API; audio miniaudio/OpenAL-Soft/SDL-audio; video FFmpeg/Libav/
  native. Criteria: license/target-coverage/maintenance/security/headless/
  cross-compile/API-stability/binary-size/startup/performance.
- Licensing analyzed before integration (GPL/LGPL/zlib/MIT/BSD/Apache ×
  distribution/runtime/executable/static/dynamic/Native/JVM/JS); "open source"
  alone approves nothing.
- Thin layer only (`Kof API → thin abstraction → backend`); platform owns
  complexity. Benchmarks (startup/window/scheduling/throughput/upload/draw/
  latency/mix/decode/memory) compare same-semantics only; no unmeasured claims.
- Memory budgets (runtime/texture/audio/video/temps; Native/mobile/WASM/embedded);
  no manual buffers. Transparent cache policy (+ release on demand if contracted).
- Assets (`sprites/sounds/music/video/models`): copy/embed/compress/hash/cache/
  paths/packaging defined later, not first slice. Packaging formats TBD.
- Headless path mandatory (CI/tests/fuzzing/servers/conformance; render buffer,
  no window). `kof test` never needs monitor/GPU/speakers/mic/camera.
- Android: same intent, no `KofAndroidGraphics` split; iOS needs explicit decision.
- Debugging relates Kof source→operation→runtime (no GPU internals at first).
- Diagnostics name Kof code (`Kof graphics error GFX002`, resource, reason,
  `game.kof:42` — never raw `SIGSEGV in libSDL`). Asset errors taxonomy:
  missing/unsupported-format/decode-failure/permission/exhausted (never one
  generic "not found").
- Threading model explicit (main/render/audio/loading) over Kof
  spawn/async/channels/scheduler. Determinism: runtime behavior may vary, test
  behavior must not (no nondeterminism in the conformance harness).

# 15. Phases / promotion / open

- **3.0** spike+infra (stack/R3/FFI/licensing/headless/cross/JavaFX-guard;
  report, no API) → **3.1** window/frame/input (JVM/Script/Native/JS +
  conformance) → **3.2** 2D (sprite/texture/transform/tilemap/draw; golden/
  headless/cross/assets/lifecycle) → **3.3** audio (sound/music/play/pause/
  stop/loop/volume + decoder/mixer/device; offline PCM golden) → **3.4** video
  (`video/play/pause/seek/volume` on defined contract; frame readback) →
  **3.5** 3D only if stack/targets/R3/runtime/conformance allow (else `GFX00x`
  stays valid) → **3.6** corpus (training/learn/docs/conformance/parity).
- Promotion checklist: implementation/runtime/targets/conformance/golden/
  headless/docs/gaps-catalogued/perf/security/licensing/corpus (no "works on
  my machine").
- Open (maintainer decides): R1 (`kof.game` namespace?), scene form (call beats
  new syntax), 3D-after-parity, golden hash contracts, stack pick, input
  snapshot-vs-events, WASM auto-entry, current-media maintain-vs-rebase.
- Non-goals: custom renderer/mixer/codec/demuxer; exposing SDL/GL/WebGL/DOM/
  HTML/CSS/JavaFX/Swing/AWT; per-target APIs; early shader language; wrapper
  facades; partial parity as feature; hidden gaps; unpromoted implementation.
- Golden rule: the developer thinks about **intent**, never the platform
  underneath (`sound("shot.ogg").play()` must not require knowing Linux/
  browser/Android audio). Resulting shape: Kof App over UI/Graphics/Media →
  Kof Runtime Contract → per-target backend → platform.
