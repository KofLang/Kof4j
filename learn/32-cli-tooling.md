[English](32-cli-tooling.md) | [Português](32-cli-tooling.pt_BR.md)

# 32 — CLI and Tooling

> **Kof 0.5.0-beta — Sep 2026 — targets jvm/native/native.risc/native.arm/js/android + kofc**

The CLI is the central tool of the Kof platform.

## Commands

| Command | What it does |
|---------|--------------|
| `kof build <dir>` | Compiles to JVM (default) |
| `kof build <dir> --target=native` | Compiles to x86-64 ELF |
| `kof build <dir> --target=native.risc` | Compiles to riscv64 ELF |
| `kof build <dir> --target=native.arm` | Compiles to aarch64 ELF |
| `kof build <dir> --target=js` | Compiles to ES Modules |
| `kof build <dir> --target=android` | Generates a Maven project + APK (Phase 1: host Activity in Kof; `mvn verify` / `--apk` with the SDK) |
| `kof run <file.kf> [--target jvm|native|native.risc|native.arm|js] [--jvm-option <opt>]` | Compiles and runs. The file's **directory is the module**: every sibling `.kf` is compiled with it (cross-file top-level functions resolve like one package — `PKG005` duplicate name / `PKG002` duplicate `main()` then apply). Independent files that merely share a directory belong in their own directory; per-file isolation is `kof test` by design (cross-file is `kof build`'s domain) |
| `kof script <file.ks|kf> [--watch] [--target ...]` | Direct KofScript (pure Kof; top-level `var`/`val` → `KofScriptGlobals`) + diagnostics with file:line |
| `kof repl` | Incremental KofScript REPL (type `exit` to quit) |
| `kof c <file.c> [--run] [--output <bin>]` | KofC C subset → native-only x86-64 ELF |
| `kof serve <file.kf>` | HTTP web server (native `web.app()` + legacy `handle()` API) |
| `kof check <file.kf\|dir> [--target <t>] [--json]` | Type-check without emitting code (target-aware gaps, e.g. `AND002` on android) |
| `kof test <file.kf\|dir> [--target jvm|native|js]` | Structured suite `test "nome" { assert(...) }` on the 3 targets + whole programs by exit code; a directory recurses into **named suites** (one per directory) |
| `kof deploy <dir|file.kf> [--target jvm|native|js|android] [--output <dir>] [--name <n>] [--version <v>] [--jvm-option <opt>]` | Packages a self-contained release: artifact (fat jar / ELF 0755 / `Default.mjs` + runtime closure / signed APK) + `RELEASE.md` + `SHA256SUMS` + `.tar.gz`; cross riscv64/aarch64 and `--publish` refuse honestly with `DEP001` |
| `kof bench [paths...] [--target ...] [--iterations N] [--baseline <file>] [--threshold <ratio>] [--json] [--fail-on-regression]` | Benchmark harness (compile, run, validate, metrics, baseline) |
| `kof profile <file.kf> [--target ...] [--methods]` | Execution + metrics (CPU, RSS, GC); `--methods`: in-house method-level **sampling** profiler (own JDK JFR on the JVM, Node `--cpu-prof` on JS) with the hot spots mapped back to the `.kf` line |
| `kof inspect <file.kf> [--json]` | IR statistics: ops before/after optimization |
| `kof decompile <file.class> [--output <file.kf>]` | Structural Kof skeleton from a `.class` |
| `kof translate <file.java> [--output <file.kf>]` | Java subset → Kof source |
| `kof compare <legacy.class\|jar> <file.kf> [--json]` | Differential test legacy vs Kof |
| `kof migrate <file.class\|java> [--output <file.kf>] [--json]` | Migration + traceable report |
| `kof config gen <file.kf\|dir> [--output <arquivo>]` | Generates a `kof.config` template from the `config.*` keys in the code |
| `kof fmt <file.kf\|dir> [-w]` | Real formatter via parser (`KofFormatter`), idempotent — implemented on 31/08 |
| `kof debug [--dap] <file.kf> [--target jvm\|native] [--attach <pid>]` | DAP on the JVM (breakpoints by Kof line, stack trace, real locals — raw JDWP rebuilt against JDK 25, §376); on `native` without the flag delegates to **gdb** over the Kof DWARF (X7-3 `cfa67238`), with `--dap` the editor-facing DAP↔gdb/MI bridge (X7-4 `bda631a7`); `--attach <pid>` REAL on both faces (X7-5 `81401629`: JVM disconnect does NOT kill the debuggee — `KofDebugAttachTest`); `js` refuses honestly (embedded engine has no inspector) |
| `kof new <name>` | Project skeletons by type |
| `kof init` | Initialize a project in the current directory |
| `kof deps <init\|add\|remove\|list\|resolve>` | Package manager (`kofdeps`: Maven `g:a:v` + registry `owner/repo[@ver]` — GitHub Releases) |
| `kof editor <list\|detect\|status\|setup\|install\|uninstall\|update>` | Editor integration (EDI001) |
| `kof info [--json]` | Environment report |
| `kof lsp` | Language Server (stdio, LSP 3.x) |
| `kof install <dir>` | Installs this build as a distribution (launcher + `kof.jar`) |
| `kof version` | Platform version (`<revision>`) |

All commands follow `intention->Kof->frontend->IR->backend->runtime`.

## `kof info`

Official environment diagnostics — for users and support:

```text
Kof 0.5.0-beta
Release channel: beta
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 25.0.4 (embedded)
Compiler: 0.5.0-beta
Runtime: 0.5.0-beta
Stdlib: 0.5.0-beta
Targets: jvm, native, js (alpha)
LSP: available
Editor support: available
Install: /opt/kof
```

(`parseTarget` also accepts `native.risc`/`native.arm`/`android` — the
report summarizes the main runtime targets.)

Structured format: `kof info --json`.

## `kof check`

Runs the complete pipeline (Lexer → Parser → Semantic Analysis) and reports
all errors, without emitting code. It is the same check that the LSP publishes.
`--target <t>` checks against a specific target, so target-specific gaps are
reported without a build (e.g. `kof check app.kf --target android` flags
`web.app()` as `AND002`). With the `--json` flag
(`kof check <file.kf|dir> --json`), it emits the diagnostics in structured JSON
format for automation and continuous integration (CI/CD). Unknown flags are
rejected with exit 1 (never silently ignored).

## `kof script` and `kof c` (0.2.0)

```bash
kof script demo.ks                 # top-level var/val → KofScriptGlobals
kof script demo.ks --watch         # re-executes on save
kof script --repl                  # incremental REPL (exit to quit)
kof c hello.c --run                # native-only C subset (GAS+LD)
kof c hello.c --output ./bin
```

`KofScript` reuses the real frontend (`lexer→parser→AST→IR`) and the chosen backend. **KofScript is pure Kof executed directly — it is not JavaScript**: there is no `let`/`const`/`async`/`fn`. The wrapper's only service is the script model: `var x=5` at the top becomes `class KofScriptGlobals { static Int x=5 }` and loose statements become `main(){…}`.

## `kof fmt` and `kof config gen` (31/08)

```bash
kof fmt src/                  # formats and prints (dry-run)
kof fmt src/ -w               # rewrites the files in-place
kof config gen src/           # generates a kof.config template from the config.* keys
```

- `kof fmt` formats via the real parser (`KofFormatter`) — the result is
  idempotent (running it twice changes nothing).
- `kof config gen` extracts the `config.*` keys from the code and generates a
  `kof.config` template ready for editing (precedence: `KOF_CONFIG` >
  env `KOF_<KEY>` > profile > `kof.config`).

## `kof bench`, `kof profile` and `kof inspect`

- `kof bench [paths...] [--iterations N] [--baseline <file>]
  [--update-baseline <file>] [--threshold <ratio>] [--json]
  [--fail-on-regression]` — compiles, runs, validates the stdout against
  `expected.txt`, measures time (median) and RSS and compares with the baseline
  (`PERFORMANCE REGRESSION` above the threshold; CI uses `--threshold 1.20`).
- `kof profile <file.kf> [--methods]` — execution + metrics (CPU, RSS, GC).
  `--methods` is the method-level **sampling** profiler (8.3 residual, closed
  20/09): the JVM face records `jdk.ExecutionSample` with the JDK's own JFR and
  the JS face runs the emitted module under Node's `--cpu-prof`; the report maps
  the hot spot back to the **Kof function with its `.kf` line** (LineNumberTable /
  `.mjs.map` — never raw bytecode), the sampler's own `jdk.jfr.internal` overhead
  is filtered, and a recording too short to hold a sample is an honest diagnostic.
- `kof inspect <file.kf> [--json]` — IR statistics: ops before/after
  optimization.

## `kof deploy` (X9, 18/09)

Packages a **releasable artifact** from the build pipeline — the unit you ship
is the `.tar.gz` next to the `deploy/` dir, and it is never faked:

```bash
kof deploy ./app --target jvm             # fat jar + RELEASE.md + SHA256SUMS
kof deploy ./app --target native          # x86-64 ELF (mode 0755 in the tar)
kof deploy ./app --target js --name api   # Default.mjs + its runtime closure (runs with bare `node`)
kof deploy ./app --target android         # signed APK (reuses the build --apk pipeline)
# -> deploy/api-<version>.tar.gz          # + sha256 printed on stdout
```

Every release is **self-contained**: the JS face ships the entry together with
the `./kof-runtime*.mjs` modules it imports (§298), so the `node <name>.mjs`
printed in `RELEASE.md` works from a clean directory. `--publish <registry>` is
**refused honestly with `DEP001`** until the maintainer decides the release
registry (plan decision **D2**); same for the cross archs (riscv64/aarch64) —
slices following in `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
(X9). Android refuses `DEP001` only when ANDROID_HOME is missing or its build-tools lack a d8 that reads the artifact's bytecode (the CLI picks the highest installed version; >= 35.0.0 for Java 21)
(honest environment guard, never a fake APK). No exit 0 without a real
artifact, ever (R6).

### Packages are consumed as SOURCE modules (#566)

A release published with `kof deploy --publish` also ships the module's **sources**
(`src/<path>.kf`, each one covered by `SHA256SUMS`; only `.kf`/`.kof`, never `tests/`,
hidden or output directories). A module with no `.kf` at its top level — only a tree of
packages — is a **library**: it is compiled to validate it and the release carries the
sources only (no runnable artifact).

```bash
# producer:  src/mylib/Thing.kf  (package mylib)
kof deploy ./lib --name mylib --version 1.2.0 --publish owner/mylib

# consumer
kof deps add owner/mylib@1.2.0
kof deps resolve                     # installs the VERIFIED sources (SHA256SUMS) in the cache
kof run Main.kf --deps               # `import mylib.Thing` resolves against the installed sources
kof run Main.kf --jvm-option -Xmx512m   # JVM option on the DEVELOPMENT path (repeatable)
kof run Main.kf --jvm-option=-Dstdout.encoding=UTF-8
kof build src --target js --deps     # the SAME sources compile for any target
```

An `import` looks in your module first, then in the official libraries, then in the
installed dependency sources — a dependency never shadows the standard library. Without
`--deps` the import is an honest `PKG006`. Packages published before this change (jar
only) keep working as before (the jar goes on the classpath).

## `kof lsp`

Language Server that consumes the **real compiler frontend**. The editor's
diagnostics are exactly the compiler's — there is no parallel parser.

```bash
kof lsp   # reads stdin, writes stdout (LSP)
```

## Editor support

Editor tooling ships with the distribution:

- `editor/kof.tmLanguage.json` — official TextMate grammar (scope `source.kof`);
- `kof lsp` — semantics and diagnostics in any LSP editor (VS Code,
  IntelliJ via LSP4IJ, Neovim, Helix, Eglot, etc.).

Never duplicate the parser in an editor: consume Kof's tooling. Target separation (`native.risc`/`native.arm`) already appears in `kof info` and in `parseTarget`.

## References

- [docs/tooling/README.md](../docs/tooling/README.md)
- [docs/tooling/EDITOR_SUPPORT.md](../docs/tooling/EDITOR_SUPPORT.md)
- [docs/tooling/LSP.md](../docs/tooling/LSP.md)
