[English](graphics-sdl3-backend.md) | [Português](graphics-sdl3-backend.pt_BR.md)

# SDL3 graphics BACKEND (the native window + the software WASI face)

last: OPENED 22/11 on the maintainer's order ("1 autorizo, 2 pode focar só no x86-64 por hora, nativo + wasi é o caminho; alem disso depois quem quiser que porte pra arm/risc") — this is the new backend plan `docs/graphics-gaming.md` requires. STATUS: UNDER DEVELOPMENT
doing: slice 1b LANDED 22/11 — `present(DrawList)` resolves each `DrawCmd.image` through the backend's texture cache (`registerTexture`/`textureFor`), drawing the TEXTURE where one is registered and the solid rect otherwise; the image upload rides the exported `SDL_UpdateTexture` (a `Buffer(U8)` is only legal in an `extern` signature) and the destination rect is built by `floatBits(Int)` (an `SDL_FRect` is four floats)
next: a `kof.game` sample app using all four measured faces (window + sprite + keyboard + mouse) so a person has a starting point; then the arm/risc port (maintainer: whoever wants it), then docs/ promotion
location: repository
state: active

constraint:

* Kof-first: the backend is a Kof file over `extern`; no new core primitive
* NO KofJS anywhere in this plan (maintainer, 22/11) — the JS target is out of scope
* honest cross-target: every face either RUNS or refuses with its own code (`FFI001` on WASI for `extern`; the software face replaces it there)
* the pure surface (`Window`, `Keys`, `Mouse`, `Draw`, `Sprite`, `Clock`) is FROZEN — the backend plugs in, it does not redefine them
* `D-GRAPHICS-GAMING` stays the owner of the stack decision (SDL3, `G1`)

decision:

* target order: NATIVE x86-64 first; WASI gets the SOFTWARE face; aarch64/riscv64 are a PORT, not this plan
* the backend is `kof.game` surface over SDL3; the app never writes `extern`
* WASI has NO window (preview1): its face renders to a PPM the program names, and that is the honest contract

---

## §1 What is MEASURED today (22/11, this host)

| fact | measured by | result |
|---|---|---|
| `extern` binds SDL3 on native x86-64 | a real program | `janela=true`, `titulo=Enois`, 60 frames, `dt=16ms` |
| the renderer is real | `SDL_RenderReadPixels` + `SDL_ReadSurfacePixel` | pixels **equal** the written colour: `(10,200,90,255)` |
| `extern` refuses on WASI | the compiler | `FFI001` — honest, never a silent fallback |
| WASI can draw in SOFTWARE | PPM writer | 32×16 P6 PPM, pixels exact per ImageMagick |
| the pure loop runs on every target | `GameWindowE2ETest` | green |
| WASI scalar conversions | `WasmWasiCastE2ETest` | now green (§1180) |

---

## §2 Slice 1 — the native face

`libs/game/Sdl3Backend.kf` (`package game`):

```
var b = Sdl3Backend("Enois", 800, 600)   // window + renderer, throws "SDL3: <err>" on failure
var w = Window("Enois")
w.frame { dt: Int, self: Window ->
    val keys = b.pollKeys()              // the SDL event pump → List<String> (held, this frame)
    if (b.quitRequested()) { self.stop() }
    b.clear(10, 20, 30)                  // background
    b.present(queue)                     // DrawList → the renderer
    queue.clear()
}
b.close()
```

What slice 1 must prove:

1. the window opens and reports its title;
2. `present(DrawList)` draws every `DrawCmd` (solid rect by default; an image path is slice 2);
3. `pumpEvents()`/`heldKeys()` report the keys held in the frame and `pushKey` injects a deterministic one — the frozen `Keys` sees pressed/down/released exactly;
4. `quitRequested()` turns a window-close into `self.stop()`;
5. **pixel readback** — draw a known colour, read it back, assert equality (the same measurement that proved the FFI);
6. **slice 1b** — `present(DrawList)` resolves `DrawCmd.image` through the texture cache, and the pixels prove the texture arrived.

The proof is `Sdl3BackendE2ETest` (4 tests), headless (`SDL_VIDEODRIVER=dummy`) on the host plus the readback.

---

## §3 Slice 2 — the software WASI face

`libs/game/SoftwareBackend.kf` (`package game`, uses only `kof.io`):

```
var b = SoftwareBackend(320, 200, "frame.ppm")
b.clear(0, 0, 0)
b.present(queue)          // rects painted into an Int[] framebuffer
b.write()                 // P6 PPM to the path
```

This is the honest WASI contract: no window exists, so the frame is a FILE. It works today because `writeText` + `as Char` are both green (measured in §1).

---

## §4 How to finish

* slice 1: `Sdl3Backend` + `Sdl3BackendE2ETest` (readback + input), doc line in `docs/graphics-gaming.md`
* slice 2: `SoftwareBackend` + `SoftwareBackendE2ETest` (PPM header + pixels), WASI-run green
* promote to `docs/` only when both faces are green and the plan's DoD is met
* the arm/risc port is explicitly NOT this plan (maintainer: "depois quem quiser que porte")

## §5 Out of scope

* KofJS rendering (maintainer order, 22/11)
* audio (`kof.game.Audio` has its own SDL3 slice, already landed)
* 3D (`Scene3d`/`Mesh` present path comes after the 2D `DrawList`)

---

## §6 The WASI boundary, MEASURED (22/11) — slice 2 is blocked on the subset, not on this plan

The maintainer asked for WASI compatibility. The measurement says WHERE the wall is, and it is not the backend:

| shape | WASI | evidence |
|---|---|---|
| a **plain `_start`** that draws and writes a PPM | ✅ runs | 8×4 PPM, pixels exact per ImageMagick |
| a record + `listOf<Cmd>()` + a `while` over it, ALL inside `main` | ✅ runs | 2 rects painted, clipping correct, PPM exact |
| the same drawing moved into a **function** (`static`, instance, or top-level) | ❌ `WASM002` | `chamada 'ppm' nao resolvida no subset escalar da unidade 15.2` |
| an `Int[]` or `List<T>` as a **function parameter** | ❌ `WASM002` | the same refusal |
| `new Int[n]` / `listOf<T>()` **inside a method** | ❌ `WASM002` | `new Int[n] fora de '_start'` / `kof.listNew` |
| `Sdl3Backend` (the native face) imported | ❌ `FFI001` | honest: no `extern` on WASI |

THE LAW AS OF TODAY: **the WASI subset 15.2 accepts the body of `_start` and nothing more.** A drawing routine extracted into any function — which is what a library face IS — is refused. So a `SoftwareBackend` that other programs import cannot exist on WASI yet; what CAN exist is a program whose `main` draws directly, and that is proven above.

WHAT THIS PLAN OWES, therefore, for slice 2: it is **blocked on units 15.3+** (the scalar subset's function/collection/IO admission that `docs/wasm-wasi-plan.md` already schedules), NOT on a backend design. The maintainer's WASI requirement is met at the *program* level today (the measured PPM) and will be met at the *library* level when the subset admits calls — the port is a `SoftwareBackend` over the SAME `PaintCmd` law measured in §6's second row.
