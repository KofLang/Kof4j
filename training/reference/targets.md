[English](targets.md) | [Português](targets.pt_BR.md)

# Kof Target Reference

**Version:** 0.5.0-beta (Sep 2026) — 3225 tests

## JVM Target

```bash
kof build --target=jvm
kof run --target=jvm
kof script app.ks --target jvm
```

- Generates `.class` files (ASM → Java 21)
- Uses JVM's GC and memory management
- Uses `java.lang.String` for strings
- Uses JVM arrays for arrays
- Uses `INVOKEVIRTUAL` for virtual dispatch
- Uses `INVOKEINTERFACE` for interface calls
- `kof.http` via `java.net.http.HttpClient`
- `KofScript` JIT in-memory via URLClassLoader

## Native Target

```bash
kof build --target=native
kof run --target=native
kof c app.c            # KofC C subset → ELF x86_64
```

- Generates x86-64 Linux ELF binary
- Uses Linux syscalls (no libc)
- Uses KofString for strings
- Uses KofArray for arrays
- Uses vtable for virtual dispatch
- Uses mmap for memory allocation
- **GC:** `kof_free_head` free-list first-fit + conservative `kof_gc_collect` mark-sweep (stack+heap scan). Auto-GC off (27/08: `.Lgc_tick` = 0) — memory is only returned on the `munmap` fallback; `kof_free` pushes onto the free-list, not munmap.
- **Floating point:** real FP in XMM — `vcvtsi2sd`/`mulsd` + dtoa via snprintf (FLT001 closed 31/08).
- **JSON:** complete encode/decode of objects/records/arrays (Int/Long/Bool/String/Double) in compile-time composition (JSN001/002/003 closed 31/08).
- **Concurrency:** `spawn`/`await` via pthread — `pthread_create` + trampoline + `pthread_join` + thread-safe allocator with futex lock (CONC001 closed 31/08).
- SQLite via direct `.so` link; MySQL wire protocol handshake with SHA-1 auth scramble (`kof_db_mysql_scramble`) implemented 27/08 (query/prepared pending)

## Native profiles (`--profile host|freestanding`)

```bash
kof build app/ --target native --profile freestanding
```

- `host` (default) — the standard x86-64 ELF with the full runtime.
- `freestanding` — no libc: the runtime provides the `kof_plat_*` HAL seams
  (print/exit/random/alloc) itself; real bare-metal profiles (`bios`/`mbr`,
  `uefi`, `uefi-ring`) live in the compiler (`NativeProfile`) behind the
  B-series front and boot a real payload through SeaBIOS under qemu — not yet
  a shipped CLI flag.
- The C FFI surface (`extern`) is documented in `training/idioms/interop.md`
  and the learner path in `learn/40-low-level.md`.

## Native RISC-V / ARM (real riscv64; aarch64 pending)

```bash
kof build --target native.risc   # riscv64 ELF via riscv64-linux-gnu-as/ld + qemu
kof build --target native.arm    # aarch64 via aarch64-linux-gnu-as/ld + qemu
```

- Target separation `Target.NATIVE_RISCV64` / `NATIVE_AARCH64` (since 0.2.6-beta)
- **riscv64 with real codegen (02/09)** — `NATIVE002` partial (happy path):
  riscv64 stack machine (raw syscalls; runtime in **pure asm**, no C) +
  `NativeRiscv64E2ETest 4/4` via `qemu-riscv64` (println String/Int, `var`,
  `if/else`, Int arithmetic/comparisons). Execution with a conditional skip if
  the toolchain (`riscv64-linux-gnu-as`/`ld` + qemu) is absent.
- **aarch64**: codegen still placeholder (x86_64 via qemu) — residual `NATIVE002`.
- `isNative()` true for all three; `nativeArch()` returns `x86_64`/`riscv64`/`aarch64`

## Runtime Functions (Native x86_64)

| Function | Purpose |
|----------|---------|
| `kof_alloc(size)` | Heap allocation (free-list first-fit; thread-safe futex lock; mmap if the free-list is empty) |
| `kof_free(ptr)` | Push onto `kof_free_head` (reuse, no syscall) |
| `kof_gc_collect()` | Mark-sweep (kof_gc_mark + kof_gc_sweep) |
| `kof_gc_tick()` | Cycle counter (auto-GC off: `.Lgc_tick` = 0) |
| `kof_spawn_trampoline` | Task trampoline in pthread (native spawn, CONC001) |
| `kof_spawn_handle_new` | Creates handle + `pthread_create` |
| `kof_await` | `pthread_join` of the handle (result + unboxing) |
| `kof_spawn_join_all` | Implicit join at end of main |
| `kof_panic(msg)` | Fatal error |
| `kof_print(ptr)` | Print string |
| `kof_println(ptr)` | Print string + newline |
| `kof_print_int(val)` | Print integer |
| `kof_string_from_literal(data, len)` | Create KofString |
| `kof_string_length(str)` | Get string length |
| `kof_string_concat(s1, s2)` | Concatenate strings |
| `kof_string_equals(s1, s2)` | Compare strings |
| `kof_string_char_at(str, idx)` | Get char at index |
| `kof_string_substring(str, start, end)` | Substring |
| `kof_string_contains(str, sub)` | Check contains |
| `kof_string_starts_with(str, prefix)` | Check starts with |
| `kof_string_ends_with(str, suffix)` | Check ends with |
| `kof_array_alloc(len, elem_size)` | Create array |
| `kof_array_length(arr)` | Get array length |
| `kof_array_get(arr, idx)` | Get array element |
| `kof_array_set(arr, idx, val)` | Set array element |
| `kof_init_object(ptr, type_id, vtable)` | Initialize object header |
| `kof_list_get(list, idx)` | List get with bounds check (fix 27/08) |
| `kof_net_socket(domain, type, proto)` | Create socket |
| `kof_plat_net_bind(fd, addr, len)` | Bind socket |
| `kof_plat_net_listen(fd, backlog)` | Listen on socket |
| `kof_plat_net_accept(fd, addr, lenp)` | Accept connection |
| `kof_net_read(fd, buf, len)` | Read from socket |
| `kof_net_write(fd, buf, len)` | Write to socket |
| `kof_plat_close(fd)` | Close fd |
| `kof_db_mysql_scramble(out, seed, len, pass)` | MySQL auth SHA-1 scramble |
| `kof_http_*` | Not available (HTTP002) — use JVM/JS |

## Android (target `android`)

```bash
kof build --target=android [--apk]
```

- **Phase 1 (implemented)**: `AndroidProjectWriter` turns the JVM backend output into a debug APK (Maven pipeline aapt2/d8/apksigner; zero Java/Kotlin/Gradle in the generated project).
- Host Activity in Kof (`dev/kof/android-host.kf`) compiled by the frontend itself; `kof.ui` via WebView (same KofJS layer as the desktop); `android.*` interop via ExternalClasspath.
- Target gaps at compile time: `AND001` (spawn/await — ART without virtual threads), `AND002` (kof.web), `AND003` (dynamic reflection), `AND004` (android.jar absent).
- See `docs/targets/KOFANDROID.md`.

## KofJS (target `js`)

```bash
kof build app.kf --target js --output out/
kof run app.kf --target js
kof script app.ks --target js
```

- ES Modules 2022+ via embedded GraalJS (KofJsRunner) — no Node.js
- **kof.http** via `Java.type("java.net.http.HttpClient")` interop (fetch inside GraalJS) — 27/08
- Coverage: classes, records, inheritance, interfaces (structural), lambdas
  (with mutable captures), if-expr, switch, loops (incl. for-in), List/Map/Set with `map/filter/reduce`, `String?`, pattern record destructuring, JSON, kof.io, kof.cache, kof.time/config, try/catch/finally.
- **kof.ui**: widgets (Window/Label/Button/Input, Column/Row, View+Style) with
  rendering in a native webview (WebKitGTK) or browser; actions by lambda;
  closing the window terminates the program. JVM/Native: no-op handles.
- `spawn` on JS is event-loop async: statement and expression are covered; CONC003 closed 03/09 (real `async`/`await`/`Promise`; `cancelled()` always `0` is the known limitation).
- Target gap codes (HTTP002, DB001, WEB001/002/003/004, SCHED001, AND001, SECN00x) reported via diagnostic at compile time.

## KofScript (`kof script`)

```bash
kof script app.ks --target jvm|native|js --watch --inspect
kof repl
```

- Top-level `var`/`val` → `KofScriptGlobals` static fields + rewriting
- `var x = 5` `val y: Int = 10` → `class KofScriptGlobals { static Int x = 5 }` (no `let`/`const` — JS sugar removed `183cb048`; the annotated form only "works" via parser hole §263)
- JIT in-memory + 64-entry LRU cache (evalCache/fileCache)
- Supports `--watch` (WatchService 200ms debounce) and `--inspect` (IRStatistics)

## KofC (`kof c`)

```bash
kof c app.c -o out/
```

- Native-only C subset: `int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`, → ELF x86_64 via `as --64` + `ld -e _start`
- No JVM/JS target

See `docs/targets/KOFJS.md` and `learn/37-kofjs.md`.
