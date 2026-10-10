[English](graphics-gaming-plan.md) | [Português](graphics-gaming-plan.pt_BR.md)

# Graphics, Games and Media — Kof's Intent Surface

**Owner:** `192.168.15.15:9092` — lane security/connectors, graphics/gaming front; re-claimed 05/10 (the spike-3.0 `192.168.15.30:9093` claims were runner/tooling, historical).

last: slice-3.4c LANDED 09/10 (the poke primitive — `buffer.poke8/32/64`, the write counterpart of peek: raw form (addr, value) + Buffer form (b, off, value) with the same bounds trap; a Long value slot accepts Int via the ordinary conversion; `BufferPokeE2ETest` 12/12 0 skips on all four native targets; the video flow now calls `av_packet_free`/`av_frame_free` through poke-boxed out-params — `freed=true/true` in `FfmpegFrameReadbackE2ETest` 3/3; the game battery 75/0F)
doing: (none — the pure-surface mission is complete; the FFmpeg LGPL backend probe/readback + the SDL3 ABI are landed)
next: the backend faces stay the documented boundary (see the state line); a new plan for backend work (renderer/mesh-loader/decode-queue) starts from docs/development on a maintainer order
location: docs
state: PURE SURFACE COMPLETE + VALIDATED (the backend faces — view matrix, mesh loading, shading, present, decode queue — stay the documented boundary)

**Status:** **UNDER DEVELOPMENT** — promoted 30/09 from `future/` by `D-GRAPHICS-SPIKE` (spike 3.0 = measurement + stack only, no API) under `D-FUTURE-PROMOTION`.
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
  (`2.30.0`), OpenAL (`1.23.1`), FFmpeg libavformat/avcodec (`6.1.1`); **delta
  02/10: `-dev` headers are NOW present for SDL2 (`libsdl2-dev 2.30.0` — full
  `SDL.h`/audio/gamecontroller/haptic/events) and FFmpeg (`libavcodec-dev` /
  `libavformat-dev` / `libavutil-dev` / `libavfilter-dev` 6.1.1)**; still no
  `-dev` for sdl3/raylib/glfw3/openal/miniaudio (`pkg-config` finds none of
  them; no headers under `/usr/include`). Bonus runtime-only: SDL_ttf `2.0.11`
  (SDL1.2-era, no `-dev`). Licenses read from the distro `copyright` files
  (SDL2 = zlib/libpng + permissive, OpenAL = LGPL-2+, libavformat = LGPL-2.1+).
  The spike measurement, not the stack pick — the pick itself is `G1` decided
  (**SDL3**) in `D-MAINT-BATCH-0510` (05/10); see the matrix below.
- **License nuance (measured from the linked `.so`, 30/09):** the distro FFmpeg
  is **GPL-built** — `avcodec_license()` = `GPL version 3 or later`,
  `avformat_license()` = `GPL version 2 or later`, and `--enable-gpl` appears in
  `avcodec_configuration()`. The `copyright` "LGPL-2.1+" is the upstream base,
  **not** the shipped build → a GPL stack choice, if taken, is a licensing
  decision the maintainer owns (the spike only reports it). **Confirmed 02/10
  at C level** (gcc + `pkg-config`, host scratch probes, not committed):
  `avcodec_license()`/`avformat_license()` return the same GPL strings and
  `--enable-gpl` is in the configuration.
- **Headless capability (measured ctypes probe, 30/09):** SDL2 initializes with
  no display — `SDL_Init(VIDEO|AUDIO)` rc=0 under `SDL_VIDEODRIVER=dummy` +
  `SDL_AUDIODRIVER=dummy` (`2.30.0`); OpenAL-Soft opens a null device —
  `alcOpenDevice(NULL)` + context OK under `ALSOFT_DRIVERS=null` (`AL_VERSION =
  1.1 ALSOFT 1.23.1`). raylib/GLFW/miniaudio are **not present** on the host
  (`pkg-config`/`dpkg`), so they stay unmeasured here. **Hardened 02/10 with a
  C probe** (gcc + `sdl2-config`, host scratch, not committed):
  `SDL_GetVersion` = 2.30.0 and `SDL_Init(VIDEO|AUDIO)` rc=0 under the dummy
  drivers — compile+link+init, stronger than the ctypes probe. OpenAL unchanged
  (runtime-only, no headers to compile against).
- **SDL3 delta (measured 05/10, G1):** SDL3 is not installed on the host, but the
  `SDL3-devel` + `libSDL3-0` RPMs from the Tumbleweed repo were extracted into a
  local prefix and measured: `gcc probe.c -lSDL3` compiles+links, and
  `SDL_Init(VIDEO)` + `SDL_CreateWindow` + `SDL_GetWindowTitle` +
  `SDL_DestroyWindow` + `SDL_Quit` run under `SDL_VIDEODRIVER=dummy`
  (`driver=dummy`, `title=kof`, rc=0). The **Kof FFI binding of the same shape was
  measured too** — the first time SDL3 is driven from Kof — on JVM and Native
  x86-64 (headless), printing `init=true / driver=dummy / title=kof`. This closes
  the SDL3 `?` in the matrix below. It also surfaced and fixed the parity blocker
  `known-bugs` §606 (a stateful C library lost its globals between `extern` calls
  on JVM/JS because the lookup arena was closed per call); the stack cannot be
  used until that fix. Cross (riscv64/aarch64) stays `?`: the picked stack must
  ship its libs+headers in the cross sysroot.
- **Cross (riscv64/aarch64): not measurable yet.** No candidate `.so`/headers
  are in the distro cross sysroot, and the project's cross toolchain
  (`scripts/setup-cross-toolchain.sh`, default `/tmp/kof-cross`) was not set up
  in this environment (**still absent 02/10** — `/tmp/kof-cross` does not
  exist). **Any picked stack must ship its cross libs in that
  sysroot** — a concrete, testable requirement for slice 3.1, not a promise.
- **R3/FFI substrate present** (this is the dependency the plan §3 names):
  `FfiSignature`, `AbiLayout`, `FfiStructLayout`, `CompilerFfiBinding`,
  `JvmFfiRuntime`, `NativeFfiCall`, `ExternalClasspath`, `KofProcess`
  (see `docs/ffi-abi-structs.md`). Any graphics mechanism is an R3 extension
  first — no parallel FFI.
- **`kof.ui`:** JVM/Native no-op handles, KofJS DOM (`KOFUI-AUDIT`); **`kof.media`:**
  bitmap/WAV/metadata/mic only; playback/streaming/mixer/video absent
  (`MEDIA001`/`MEDIA003`). Both stay honest gaps until a real backend lands.

**Candidate matrix (`G1` decided — SDL3 — `D-MAINT-BATCH-0510`; the matrix is now the vendoring/ABI input for slice 3.1, not an open pick; `?` = not measured):**

| Candidate | Domain | License (`?` = confirm upstream) | Runs on host | Headless | Cross (riscv64/aarch64) | Axis |
|---|---|---|---|---|---|---|
| SDL3 / SDL2 | window+input+audio | zlib/libpng + permissive (distro `copyright`) | SDL2 `2.30.0` runtime `.so` **+ `-dev` (02/10)**; SDL3 `3.4.16` RPMs extracted to a local prefix **05/10**; C compile+link OK for both | SDL3 + SDL2 dummy driver **measured OK**; **Kof FFI drives SDL3 init/window/title/quit on JVM+Native (05/10)** | `?` | one lib, many targets |
| raylib | 2D/3D+audio | zlib (`?`) | not present | `?` | `?` | batteries-included 2D |
| GLFW + GL API | window+context | zlib (`?`) | not present | offscreen ctx (`?`) | `?` | thin, GL expertise needed |
| miniaudio | audio | public-domain/MIT-0 (`?`) | not present (header-only, drop-in) | offline mix yes (`?`) | `?` | single-header audio |
| OpenAL-Soft | audio | **LGPL-2+** (distro `copyright`) | runtime `1.23.1` `.so` present, no `-dev` | null backend **measured OK** | `?` | 3D positional audio |
| FFmpeg / Libav | video+codecs | **GPL-built** here (`avcodec_license()` = GPLv3+; `--enable-gpl`); upstream base LGPL-2.1+ | libavcodec/avformat `6.1.1` `.so` **+ `-dev` (02/10)**; C compile+link OK, GPL confirmed at C level | codec API (`?`) | `?` | full codec set |

**Recommendation (measurement-driven, not by familiarity):** the plan's JVM rule
(§11: never JavaFX/Swing/AWT/`javax.sound`) plus the R3-first coupling (§3) point
to **one portable multi-target stack for window+input+audio** (SDL3 is the natural
candidate) and **FFmpeg/Libav for video codecs** (never homemade, §10/§14).
**Decided:** `G1` is **SDL3** (`D-MAINT-BATCH-0510`, 05/10); the spike removed the
unknowns and restored the guard — the matrix below is now the vendoring/ABI input,
not an open pick.
**Caveat from the license probe:** the FFmpeg face is only "free" if a LGPL
build is vendored — the distro one measured GPL (above), so taking it as-is is a
licensing decision, not merely technical.

**How to finish (slice order, `§15`):** 3.0 (this infra+report) → **3.1**
window/frame/input on JVM/Script/Native/JS + conformance → 3.2 (2D) → 3.3
(audio, offline PCM golden) → 3.4 (video, frame readback) → 3.5 (3D, only if
parity allows) → 3.6 (corpus). Each slice is a complete, tested unit and uses the
**`G1` decision (SDL3)** recorded in `D-MAINT-BATCH-0510` before any API lands (the
spike report's matrix is the vendoring/ABI input).

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

- Window (backend-owned, form DECIDED — `D-GRAPHICS-WINDOW-FORM`): `Window("Pong") { frame { dt -> ... } }`
  (the `Scene("Pong") { dt -> ... }` alternative was rejected); covers title/size/fullscreen/resize/focus/
  close/DPI/orientation/visibility/input; no OS APIs.
- Loop: backend owns clock/vsync/scheduling/poll/submit/present; program gets
  `dt` (unit DECIDED — `D-GRAPHICS-WINDOW-FORM`: Int milliseconds, first frame `0`).
  The remaining loop semantics are DECIDED too (`D-MAINT-BATCH-0610`, 06/10):
  **long-frame = clamp of `dt`** (the real delta bounded by a configured ceiling —
  spiral-of-death guard, never an unbounded `dt`); **limit = vsync on/off only**
  (no frame-rate cap); **pause = explicit `pause()`/`resume()`, `minimized`
  suspends the render, losing focus does NOT pause**.
- Virtual clock required (deterministic `dt` streams) for physics/animations/
  input/audio/playback/goldens.
- Pure host LANDED 07/10 (`libs/game/Window.kf`, slice 3.1): `Window("Pong")`
  + `clock(source)` + `dtClampMillis(n)` (A1) + `vsync(on)` (A2) +
  `pause()`/`resume()`/`minimize()`/`restore()`/`blur()`/`focus()` (A3) +
  `frame { dt: Int, self: Window -> ... }` over the composed `Clock`; the
  2-arg body (window passed as `self`, never captured) sidesteps what was
  `known-bugs` §620 (capturing lambda + args = garbage first arg on cross),
  ✅ FIXED 07/10 by the native-backend lane, and is green on every target
  (`GameWindowE2ETest` 8/8). Building it fixed `known-bugs` §619
  (uninitialized field + `(` member misparse, `ClassMemberParseE2ETest` 4/4).
- Pump binding LANDED 07/10 (slice 3.1 remainder, test-only over the vendored
  stack — no new Kof API): `Sdl3PumpE2ETest` **5/5** drives a real headless
  SDL3 window (`dummy` driver) through drain (`SDL_PollEvent` into a 128-byte
  `Buffer(U8)`, the `SDL_Event` size) + push + pacing (`SDL_Delay`/
  `SDL_GetTicks`) + two virtual-`Clock` frames with `Keys` snapshots, golden
  `init=true/push=true/poll=0/paced=true/frames=2/quit=true` on JVM + Native
  x86-64 + riscv64 + aarch64 under qemu. Measured boundaries: SDL drops a
  pushed zero (type-0) event (`poll=0` pinned); real backend events exist
  (e.g. `0x404 MOUSE_ADDED` at creation) but counts vary by environment, so
  the drain counts silently; scancode-carrying synthesis awaits a `Buffer`
  byte-write surface (today alloc+read only — documented frontier, not a
  silent gap).

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
- Slice 3.2a LANDED 07/10 (pure intent, no rendering): `libs/game/Sprite.kf`
  (`sprite()` factory + `at/scale/turn/origin/flip/show/hide`, `worldPointX/Y`
  = `pos + R·S·F·(p − origin)`, `frames()/animate(dtMs, frameMs)` over a
  caller-supplied delta, `draw(queue)`) + `libs/game/Draw.kf` (`DrawCmd`
  record + ordered `DrawList`: `draw/clear/size/commandAt`, invisible draws
  record nothing) + `libs/game/Trig.kf` (pure-Kof `trigSin`/`trigCos`,
  Taylor through x^13 — `math.sin`/`math.cos` had no Native symbols,
  `known-bugs` §621, ✅ FIXED 07/10 by the native-backend lane with an honest
  `MATH001` gate, so the lib still uses zero backend trig). Cross-file same-
  package refs need an explicit `import` (measured: `import game.Draw` /
  `import game.Trig` inside `Sprite.kf`, the `Window.kf` → `game.Clock`
  precedent). Proof: `GameSpriteE2ETest` **14/14** (transform + animation +
  draw goldens on JVM + Script + Native x86-64 + JS; transform golden also
  riscv64 + aarch64 under qemu; milli-unit goldens, never raw `Double`s).
  Next: tilemap intent (3.2b).
- Slice 3.2b LANDED 07/10 (pure intent, no rendering): `libs/game/Tilemap.kf`
  (`tilemap()` factory + unbounded sparse grid: `tileAt`/`setTile`/
  `clearTile`/`hasTile`/`count`/`clear`, `worldX`/`worldY` pixel origins;
  negative id clears, unset reads `-1`, `tileSize <= 0` throws). Building it
  confirmed two `List` API facts the compiler states explicitly (`[]`
  assignment is arrays-only → `l.set(i, v)` per `SEM054`; removal is
  `l.remove(i)`, no `removeAt`) — language knowledge, no bug. Proof:
  `GameTilemapE2ETest` **6/6** (all-integer golden on JVM + Script + Native
  x86-64 + JS + riscv64 + aarch64 under qemu).

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
- Slice 3.4a LANDED 07/10 (pure playback intent, no decoder): `libs/game/
  Video.kf` (`video()` factory + `play/pause/stop/seek/volume/loop/mute`,
  `tick(dtMs)` over caller-supplied timestamps, `position/frameIndex/
  finished`; meaningless metadata throws at construction, `seek` clamps,
  volume clamps into [0,1], end-of-stream stops (or wraps on loop)).
  Proof: `GameVideoE2ETest` **6/6** (all-integer golden on JVM + Script +
  Native x86-64 + JS + riscv64 + aarch64 under qemu). Decoder and frame
  readback stay backend work (FFmpeg LGPL decision F, maintainer).
- `kof.media` today (bitmap/WAV/metadata/mic) → playback/streaming/mixing/
  video-playback, additively.
- KofUI ≠ competing language (UI apps vs games); share window/input/video/
  images/events infra where equivalent.
- Slice 3.3a LANDED 07/10 (pure offline mixer, no audible output):
  `libs/game/Audio.kf` (`Sound` record + `Mixer`: `play(sound, startMs)`,
  `render()` to 16-bit PCM `Int[]`, overlapping voices sum and clamp to
  [-32768, 32767], each loop restarts the phase, `rate <= 0` throws).
  Samples synthesize from `game.Trig` only (zero backend trig — the Native
  runtime had no `sin`/`cos` symbols, `known-bugs` §621, ✅ FIXED 07/10).
  Building it
  bisected and catalogued `known-bugs` §622 (a 2nd/nested conditional
  assignment to the same Double local was lost on cross; the 15-line
  `twoIfLit` reproducer + else/while/return/nested variants), ✅ FIXED 07/10
  by the native-backend lane (the cross conditional-jump compared Double bit
  patterns with signed-integer branches), and ships the
  branch-free workaround (`roundTo` range reduction in `Trig.trigNorm`,
  still valid and faster).
  Proof: `GameAudioE2ETest` **6/6** (exact-integer golden on JVM + Script +
  Native x86-64 + JS + riscv64 + aarch64 under qemu). Audible
  decoder/playback/device faces stay backend work.
- Ledger close-out (measured 07/10, no gate change): `kof.game` needs NO
  ledger row — the R1 namespace question (`kof.game` vs `package game`) is
  maintainer-open, `HARD_DENY game` already encodes official-package-only,
  `scripts/package.sh` ships all of `libs/` generically, and imports
  resolve off the filesystem. Inventing a `kof.game` row would assert an
  undecided namespace. Next: live-audio backend faces + 3.4 (video).
- Slice 3.3b LANDED 07/10 (SDL3 audio ABI, test-only over the vendored
  stack — no audible output asserted, CI has no speakers): `Sdl3AudioE2ETest`
  **5/5** inits the audio subsystem, opens the default playback device,
  reads back the negotiated `SDL_AudioSpec` (S16 stereo 44100 Hz, 1024
  buffer frames — identical on all four targets), pauses/resumes, closes
  and quits, golden `init/open/fmt/format/channels/freq/frames/pause/
  resume/quit=true` on JVM + Native x86-64 + riscv64 + aarch64 under qemu.
  Measured Kof/FFI facts: `SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK` is passed as
  `0 - 1` (Kof has no unsigned literals; same low 32 bits); the nullable
  `spec` cannot spell `NULL` (`SEM048`), but a zeroed 12-byte buffer is
  accepted and negotiates the dummy defaults here. Audible
  decoder/playback/streaming faces stay later work (FFmpeg LGPL decision F
  for codecs, maintainer).
- Slice 3.3c LANDED 07/10 (SDL3 audio stream submit path, test-only — the
  device stays paused so no background thread consumes the queue): the
  negotiated device spec is reused as the stream spec (a zeroed spec is
  rejected for streams: `src_spec->format is invalid`), then 16 `'A'` bytes
  round-trip as exactly 32 available/returned bytes — the stream converts
  to SDL's internal F32 mixer format regardless (`src=S16/2ch/44100`,
  `dst=F32/2ch/44100` read back; `'A'` = S16 `0x4141` → F32 `0,130,2,63`
  repeating, exact IEEE pinned). Measured boundaries: same-`Buffer` twice
  in one call trips the honest `MEM020` borrow guard (fill two buffers
  instead); a `\0`-in-source silent cycle was measured and dropped — NUL
  bytes marshal fine on JVM but break the native assembler (raw control
  bytes in `.s`), and Kof has no runtime NUL-string constructor
  (`known-bugs` §623, native-backend lane). Proof: `Sdl3AudioStreamE2ETest`
  **5/5** on JVM + Native x86-64 + riscv64 + aarch64 under qemu.
- Slice 3.3d LANDED 07/10 (WAV encoder, pure Kof — the offline mixer's PCM
  made shippable): `libs/game/Wav.kf` (`encodeWav(samples, rate, channels)`
  writes PCM16 WAVE images (44-byte RIFF header from `charAt` codes, never
  magic numbers; mono/stereo only and `rate <= 0` throw; samples clamp) and
  reads them back field-exact (`wavSampleCount`/`wavRate`/`wavChannels`/
  `wavSampleAt`). Unlocked by measuring that `Byte[]` indexed write plus
  `as Byte` (mod-256 wrap) work on every target — the same surface a future
  stream-submit bridge needs. Proof: `GameWavE2ETest` **6/6** (exact header
  + roundtrip golden, incl. ±32768 extremes, on JVM + Script + Native
  x86-64 + JS + riscv64 + aarch64 under qemu).

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

- **Slice 3.1 — started 05/10 (lane security/connectors `192.168.15.15:9092`):** the pure, backend-independent half of the §6/§7 contract landed first — `libs/game/Clock.kf` (namespace `kof.game`) owns the frame bookkeeping and `dt` over caller-supplied monotonic timestamps (the "virtual clock" the plan requires for deterministic goldens), so it calls no window/audio/video API and is honest on every target today. Semantics frozen by `GameClockE2ETest` **4/4** on JVM + Script + Native x86-64 + JS (frame 0 `dt=0`; later frames diff the previous timestamp; `stop()` ends `hasNext()`). `libs/game/Keys.kf` adds the §7 per-frame input snapshot: `beginFrame(held)` diffs the current and previous key sets and derives `down`/`pressed`/`released`, so the backend only translates events and the transitions are deterministic on every target (`GameInputE2ETest` **4/4**, JVM + Script + Native x86-64 + JS, byte-identical golden). `libs/game/Mouse.kf` completes the pointer half of the same §7 snapshot: `beginFrame(x, y, buttons)` keeps the pointer position and the previous/current button sets and derives `x`/`y`, the per-frame movement `dx`/`dy` (0 on the first frame, mirroring the clock's first-frame `dt=0`) and `down`/`pressed`/`released`, so the backend only translates pointer events (`GameMouseE2ETest` **4/4**, JVM + Script + Native x86-64 + JS, byte-identical golden). `libs/game/Pad.kf` closes the §7 input surface with the gamepad snapshot: `beginFrame(leftX, leftY, rightX, rightY, buttons)` records the stick axes raw (normalizing a device range is backend policy) and diffs the button set for `down`/`pressed`/`released` (`GamePadE2ETest` **4/4**, JVM + Script + Native x86-64 + JS, byte-identical golden). Building the clock surfaced and fixed a frontend defect (`known-bugs` §603: the synthetic SAM `invoke` descriptor used inferred argument types). The G1 SDL3 measurement then closed the stack unknown: the `SDL3-devel`/`libSDL3-0` `3.4.16` RPMs were extracted to a local prefix, a C probe compiled+linked+ran headless (`SDL_VIDEODRIVER=dummy`), and the same shape was driven from **Kof** through `extern` on JVM + Native x86-64 (`SDL_Init`/`SDL_CreateWindow`/`SDL_GetWindowTitle`/`SDL_DestroyWindow`/`SDL_Quit` → `init=true / driver=dummy / title=kof`). That first Kof-side use surfaced a cross-target parity blocker — a stateful C library lost its globals between `extern` calls on JVM/JS because the library lookup was loaded into the per-call arena and closed — now fixed (`known-bugs` §606, `FfiLibraryStateE2ETest` 3/3 JVM+JS+Native, RED-first 2/3 pre-fix). The pure surface was then verified on the cross backends too: `GameCrossE2ETest` **3/3** drives one virtual frame through all four modules together — frame 0 `dt=0`, `left` pressed, pointer (100, 50) with no movement, neutral pad; frame 1 `dt=16`, `space` pressed with `left` held, pointer (140, 70) moved +40/+20 with `left` pressed, left stick (0.5, −0.25) with `a` pressed — and asserts the same golden on the JVM oracle and on **riscv64 + aarch64** under qemu (the per-module suites cover x86-64 + JS + Script; axes in milli-units). The window form is then DECIDED (`D-GRAPHICS-WINDOW-FORM`): `Window("…") { frame { dt -> … } }` with `dt` an `Int` of milliseconds (first frame `0`) — preparing it measured and fixed a parser gap (`known-bugs` §611: a trailing lambda with an explicit parameter parsed only after a `.` receiver; `f { dt: Int -> … }` and `f(1) { dt: Int -> … }` now parse too, `TrailingLambdaParamsE2ETest` 6/6), so the literal nested form's only remaining need is an implicit receiver (Kof has none: SEM015/SEM025), written today as `{ w: Window -> w.frame { dt: Int -> … } }`. The stack then shipped cross-target: new `scripts/provision-cross-sdl3.sh` vendors SDL3 `3.4.16` + its measured runtime closure (audio/X11/wayland/drm/…) + GLIBC 2.44 from the openSUSE Ports RPMs into `$KOF_CROSS_SYSROOT/usr/<arch>-linux-gnu/{include,lib}` (no root; the aarch64 SDL3 needs `GLIBC_2.43`, the sysroot libc was 2.41 — the forward-compatible Ports glibc closes it), and the raw SDL3 ABI is now proven end-to-end headless (`SDL_VIDEODRIVER=dummy`) on **all four targets** — `Sdl3FfiCrossE2ETest` **5/5**: JVM + Native x86-64 + riscv64 + aarch64 under qemu, identical golden `init=true / driver=dummy / title=kof` (`SDL_Init`/`SDL_CreateWindow`/`SDL_GetWindowTitle`/`SDL_DestroyWindow`/`SDL_Quit`), with the cross pair pinned to agree (rule 5). Next: design the backend window (`Window("…") { frame { dt -> … } }`) over the measured binding — the loop long-frame/limit/pause/minimized/focus semantics remain the maintainer's call (`D-GRAPHICS-WINDOW-FORM`).
- **3.0** spike+infra (stack/R3/FFI/licensing/headless/cross/JavaFX-guard;
  report, no API) → **3.1** window/frame/input (JVM/Script/Native/JS +
  conformance) → **3.2** 2D (sprite/texture/transform/tilemap/draw; golden/
  headless/cross/assets/lifecycle) → **3.3** audio (sound/music/play/pause/
  stop/loop/volume + decoder/mixer/device; offline PCM golden) → **3.4** video
  (`video/play/pause/seek/volume` on defined contract; frame readback) →
  **3.5** 3D only if stack/targets/R3/runtime/conformance allow (else `GFX00x`
  stays valid) → **3.6** corpus (training/learn/docs/conformance/parity).
- Slice 3.6a LANDED 07/10 (corpus, docs-only — no compiler/library change):
  `training/idioms/game.md` (+PT: canonical forms for all 12 modules with
  the measured constraints — virtual time, 2-arg frame body, milli-unit
  goldens, per-file imports, no backend trig) + `learn/42-games.md` (+PT:
  "your first game loop" tutorial composing Clock/Keys/Sprite/Draw/Window).
  Proof: all 20 snippets extracted and compiled clean on JVM (9 EN + 9 PT
  idioms + 2 tutorials), both tutorial programs run (`drawn=3`);
  executing them caught and fixed 2 doc bugs pre-commit (an `Int` lambda
  for a `() -> Long` clock source — runtime `IncompatibleClassChangeError`
  — and a raw-`Double` print in the sprite example). Next: 3.6b+ (decoder
  faces, backend docs) as backends land.
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
