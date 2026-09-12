# Kof Target Reference

**Version:** 0.3.22-beta (Sep 2026) — 810 tests

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
- **GC:** free-list `kof_free_head` first-fit + `kof_gc_collect` mark-sweep conservador (stack+heap scan). Auto-GC desligado (27/08: `.Lgc_tick` = 0) — memória só é devolvida no `munmap` fallback; `kof_free` push onto free-list, não munmap.
- **Ponto flutuante:** FP real em XMM — `vcvtsi2sd`/`mulsd` + dtoa via snprintf (FLT001 fechado 31/08).
- **JSON:** encode/decode completo de objetos/records/arrays (Int/Long/Bool/String/Double) em composição compile-time (JSN001/002/003 fechados 31/08).
- **Concorrência:** `spawn`/`await` via pthread — `pthread_create` + trampoline + `pthread_join` + allocator thread-safe com lock futex (CONC001 fechado 31/08).
- SQLite via link direto `.so`; MySQL wire protocol handshake com auth scramble SHA-1 (`kof_db_mysql_scramble`) implementado 27/08 (query/prepared pendente)

## Native RISC-V / ARM (riscv64 real; aarch64 pendente)

```bash
kof build --target native.risc   # riscv64 ELF via riscv64-linux-gnu-as/ld + qemu
kof build --target native.arm    # aarch64 via aarch64-linux-gnu-as/ld + qemu
```

- Target separation `Target.NATIVE_RISCV64` / `NATIVE_AARCH64` (desde 0.2.6-beta)
- **riscv64 com codegen real (02/09)** — `NATIVE002` parcial (caminho feliz):
  stack machine riscv64 (raw syscalls; runtime em **asm puro**, sem C) +
  `NativeRiscv64E2ETest 4/4` via `qemu-riscv64` (println String/Int, `var`,
  `if/else`, aritmética/comparações Int). Execução com skip condicional se a
  toolchain (`riscv64-linux-gnu-as`/`ld` + qemu) estiver ausente.
- **aarch64**: codegen ainda placeholder (x86_64 via qemu) — `NATIVE002` residual.
- `isNative()` true para os três; `nativeArch()` retorna `x86_64`/`riscv64`/`aarch64`

## Runtime Functions (Native x86_64)

| Function | Purpose |
|----------|---------|
| `kof_alloc(size)` | Heap allocation (free-list first-fit; lock futex thread-safe; mmap se free-list vazia) |
| `kof_free(ptr)` | Push onto `kof_free_head` (reuse, no syscall) |
| `kof_gc_collect()` | Mark-sweep (kof_gc_mark + kof_gc_sweep) |
| `kof_gc_tick()` | Contador de ciclo (auto-GC desligado: `.Lgc_tick` = 0) |
| `kof_spawn_trampoline` | Trampoline da tarefa em pthread (spawn nativo, CONC001) |
| `kof_spawn_handle_new` | Cria handle + `pthread_create` |
| `kof_await` | `pthread_join` do handle (resultado + unboxing) |
| `kof_spawn_join_all` | Join implícito no fim do main |
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
| `kof_list_get(list, idx)` | List get com bounds check (fix 27/08) |
| `kof_net_socket(domain, type, proto)` | Create socket |
| `kof_net_bind(fd, port, addr)` | Bind socket |
| `kof_net_listen(fd, backlog)` | Listen on socket |
| `kof_net_accept(fd)` | Accept connection |
| `kof_net_read(fd, buf, len)` | Read from socket |
| `kof_net_write(fd, buf, len)` | Write to socket |
| `kof_net_close(fd)` | Close socket |
| `kof_db_mysql_scramble(out, seed, len, pass)` | MySQL auth SHA-1 scramble |
| `kof_http_*` | Não disponível (HTTP002) — use JVM/JS |

## Android (target `android`)

```bash
kof build --target=android [--apk]
```

- **Fase 1 (implementada)**: `AndroidProjectWriter` transforma a saída do backend JVM num APK de debug (pipeline Maven aapt2/d8/apksigner; zero Java/Kotlin/Gradle no projeto gerado).
- Host Activity em Kof (`dev/kof/android-host.kf`) compilada pelo próprio frontend; `kof.ui` via WebView (mesma camada KofJS do desktop); interop `android.*` via ExternalClasspath.
- Gaps de target em compile-time: `AND001` (spawn/await — ART sem virtual threads), `AND002` (kof.web), `AND003` (reflexão dinâmica), `AND004` (android.jar ausente).
- Ver `docs/targets/KOFANDROID.md`.

## KofJS (target `js`)

```bash
kof build app.kf --target js --output out/
kof run app.kf --target js
kof script app.ks --target js
```

- ES Modules 2022+ via GraalJS embarcada (KofJsRunner) — sem Node.js
- **kof.http** via `Java.type("java.net.http.HttpClient")` interop (fetch dentro de GraalJS) — 27/08
- Cobertura: classes, records, herança, interfaces (estruturais), lambdas
  (com capturas mutáveis), if-expr, switch, loops (incl. for-in), List/Map/Set com `map/filter/reduce`, `String?`, pattern record destructuring, JSON, kof.io, kof.cache, kof.time/config, try/catch/finally.
- **kof.ui**: widgets (Window/Label/Button/Input, Column/Row, View+Style) com
  renderização em webview nativo (WebKitGTK) ou browser; ações por lambda;
  fechar a janela encerra o programa. JVM/Native: handles no-ops.
- `spawn` no JS é sequencial: statement e expressão cobrem; async real de event-loop = CONC003 parcial.
- Gap codes de target (HTTP002, DB001, WEB001/002/003/004, SCHED001, AND001, SECN00x) reportados via diagnostic em compile-time.

## KofScript (`kof script`)

```bash
kof script app.ks --target jvm|native|js --watch --inspect
kof repl
```

- Top-level `let`/`const` → `KofScriptGlobals` static fields + rewriting (27/08)
- `let x = 5` `const y: Int = 10` → `class KofScriptGlobals { static Int x = 5 }`
- JIT in-memory + cache LRU 64 (evalCache/fileCache)
- Suporta `--watch` (WatchService debounce 200ms) e `--inspect` (IRStatistics)

## KofC (`kof c`)

```bash
kof c app.c -o out/
```

- C subset nativo-only: `int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`, → ELF x86_64 via `as --64` + `ld -e _start`
- Sem target JVM/JS

Ver `docs/targets/KOFJS.md` e `learn/37-kofjs.md`.
