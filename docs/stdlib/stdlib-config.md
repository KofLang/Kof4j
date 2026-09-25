[English](stdlib-config.md) | [Português](stdlib-config.pt_BR.md)

# stdlib config — Kof Native Configuration

**Last updated:** September 12, 2026
**Version:** 0.5.0-beta
**Status:** implemented (Phase 3 of the Spring independence plan) — 3 targets (JVM / Native own asm `/proc/self/environ` + free-list / JS `kof_platform`) + `required`/`${key}` interpolation/`kof config gen` (30/08)

---

## 1. Philosophy

> Configuration is a capability of the language, not of a framework.

`kof.config` resolves configuration values with explicit precedence,
compile-time typing and zero external dependency (no Spring
Environment/PropertySource, no dotenv).

## 2. API

```kof
var port  = config.int("server.port", 8080)         // Int with default
var url   = config.str("database.url", "jdbc:h2:mem") // String with default
var debug = config.bool("app.debug", false)         // Bool with default
var big   = config.long("app.timeoutMillis", 30000) // Long with default

var raw   = config.get("server.port")               // String or null
var has   = config.has("server.port")               // Bool
var home  = config.env("HOME")                      // direct environment variable
var need  = config.required("db.url")               // fails at startup if missing
```

### 2.1 `${key}` interpolation (P2 — implemented, 30/08)

Values can reference other keys of the config itself:

```text
# kof.config
db.host = localhost
db.port = 5432
db.url  = jdbc:pg://${db.host}:${db.port}/app
```

- Recursive resolution (reference to reference works), limit of 16 levels.
- **Cycle** (`a=${b}`, `b=${a}`) → value **left as the literal** (`a` is
  `${b}`), never a crash or infinite loop.
- Referenced key **nonexistent** → literal unchanged.
- It works the same for values coming from a file **and** from env
  (`KOF_<KEY>`), in the 3 targets (JVM: `JvmConfigRuntime`; Native: asm
  `kof_config_interpolate`; JS: `kofConfigInterpolate`).

`config.int/bool/long/str` never fail: missing or invalid value → default.

## 3. Sources and precedence

1. **Explicit file** — `KOF_CONFIG` points to a `key=value` file
   (comments with `#`). Highest precedence.
2. **Environment variable** — `KOF_<KEY>` with `.`/`-` → `_` and uppercase:
   `server.port` → `KOF_SERVER_PORT`.
3. **Profile file** — `kof.<KOF_PROFILE>.config` in the working
   directory (e.g.: `kof.prod.config`).
4. **Default file** — `kof.config` in the working directory.

```bash
KOF_CONFIG=/etc/app/config.properties kof run app.kf
KOF_PROFILE=prod kof run app.kf            # uses kof.prod.config
KOF_SERVER_PORT=9000 kof run app.kf        # env by convention
```

## 4. Example with the web stack

```kof
main() {
    var port = config.int("server.port", 8080)
    var app = web.app()
    app.get("/") {
        return "listening on " + port
    }
    app.listen(port)
}
```

## 5. Targets (0.5.0-beta)

| Target | Status | Notes |
|--------|--------|-------|
| JVM | ✅ complete | `KofRuntime` generated |
| Native x86_64 | ✅ complete (own asm, 27/08) | `/proc/self/environ` scan, trim, comments, free-list `kof_free_head`, interpolation `kof_config_interpolate` |
| Native riscv64/aarch64 | ✅ complete (own asm, 26/09) | `NativeRiscvAsmConfig1/2/3` — `/proc/self/environ` scan, file `key=value` find, lookup `KOF_CONFIG` → env `KOF_<KEY>` → profile `kof.<KOF_PROFILE>.config`/`kof.config`, `${key}` interpolation, typed wrappers; the `CONF001` gate was removed (`KofConfig.supportedOn` true; D-FULL-PARITY-050 row 9); proof `KofConfigCrossTest` |
| JS | ✅ complete | `kof_platform` (`kofConfigLookup`/`kofConfigStr/Int/Bool/Long/Required` + `kofConfigInterpolate`) |

## 6. Tests

`KofConfigE2ETest` — 11 E2E tests (0.5.0-beta): env by convention, defaults,
explicit file, profiles, default file in the working directory, `env()`,
full precedence, `required` (present in all targets + fast fail
if missing) and `${key}` interpolation (JVM/Native/JS).

## 7. Architecture

```
Kof source (.kf) → KofConfig (compile-time table)
   → SemanticAnalyzer (types) → CompilerDriver (KofCall kof_config_*)
   → dev.kof.runtime.KofRuntime (generated): lookup with precedence + parsing
```

The compiler knows every call at compile-time; the runtime is never
discovered by reflection.

## 8. Where we stand vs. the gold standard (Spring/Quarkus) — honest audit

**Last review:** re-synced 17/09/2026 (0.5.0-beta; base audit 30/08/2026)

| Capability | kof.config today | Spring Boot | Status |
|------------|-----------------|-------------|--------|
| Config file | `kof.config` (key=value) | `application.properties` | ✅ equivalent |
| Profiles | `KOF_PROFILE` → `kof.prod.config` | `spring.profiles.active` | ✅ equivalent |
| Env by convention | `server.port` → `KOF_SERVER_PORT` | `SERVER_PORT` (relaxed binding) | ✅ equivalent |
| Typed with default | `config.int/str/bool/long` | `@Value` / `@ConfigurationProperties` | ✅ equivalent |
| Fail early (required) | `config.required(key)` fails at startup | fails at boot | ✅ equivalent (P1, 30/08) |
| Typed declarative config | ❌ (P3 planned) | ❌ (runtime reflection) | 🎯 planned advantage |
| Interpolation | `${key}` in the 3 targets (P2, 30/08) | `${key}` | ✅ equivalent |
| Key discovery | `kof config gen` generates a template from the code's keys | Actuator `/env` | ✅ equivalent (P3, 30/08) |
| Secrets | separate (`kof.security.secrets.get`, env-only) | `Environment` mixes everything | ✅ Kof is safer |

### 8.1 Design decisions (firm)

1. **The file is called `kof.config`** — not `application.properties` nor
   `application.kof`. Consistency with `kof.log`, `kof.cache`, `kof.db`:
   everything in Kof lives in the `kof.*` namespace. The "application.kof" of the
   initial discussion is already served by the right name.
2. **A secret NEVER goes in the file.** `kof.config` is committable to git;
   secrets live in env (`secrets.get`) — config/secret separation is
   security, not convenience. 12-factor standard; better than the common
   practice of mixing in the same file.
3. **Never reflection.** Precedence is implemented directly (generated JVM,
   native asm, `kof_platform` in JS). No PropertySource, no reflection.

### 8.2 Roadmap (in order of value)

**P1 — ✅ `config.required(key)` — IMPLEMENTED (30/08).**
```kof
var url = config.required("database.url")   // clear startup error if missing
```
Eliminates the whole class of deploy bugs ("it ran on my machine").
JVM: `IllegalStateException` naming the key + precedence consulted;
Native: asm panic; JS: throw. Tests: `requiredKeyPresentAllTargets`,
`requiredKeyMissingFailsFast`.

**P2 — ✅ `${key}` interpolation — IMPLEMENTED (30/08, see §2.1).**
Recursive lookup with cycle detection (cycle → literal, never crash).
JVM + Native (asm `kof_config_interpolate`) + JS. Works for values from
file and from env. Tests: `interpolationResolvesReferences` (JVM),
extended interpolation in `nativeAndJsRunConfig` (Native + JS).
Bonus: exposed and fixed a latent bug in the `kof_config_bool` asm
(rsi was never set before `.Lcb_ci_match`; it worked by chance).

**P3 — ✅ `kof config gen` — IMPLEMENTED (30/08).**
The compiler knows all literal keys (compile-time dispatch, no
reflection). Subcommand:

```bash
kof config gen src/                    # prints the template to stdout
kof config gen src/ --output kof.config  # writes the file
kof config gen app.kf --target native    # any target (analysis only)
```

Template rules: a key with a default becomes a **comment** (the program already
has a value; uncomment to override); `required`/`get` without a default
become an **active line** fill-or-fail; a computed key (not literal)
does not appear — nothing is inferred at runtime. Repeated keys are deduped
by (method, key, default). Tests: `ConfigGenTest` (3 cases).

> **~~Known gap (COMP002, pre-existing)~~ — closed 31/08:** the real
> cause was missing JVM descriptors for web-context functions
> (`kof_web_ws_message` falling into the default `(String)->Object` with 0 args —
> stack underflow in the ASM `COMPUTE_FRAMES`). `config.*` with a
> non-literal key compiles and runs.

**P3 — Typed declarative config (the KOF_VS_SPRING §2 vision).**
```kof
config App {
    port    = 8080
    db.url  = "jdbc:h2:mem"
    debug   = false
}
```
A block in the language; the compiler validates keys/types at compile-time,
emits the `AppConfig` class and knows ALL the keys. A typo in
config becomes a compilation error — nothing on the market does that (Spring resolves
at runtime by reflection; Quarkus uses annotations + APT).
Depends on: named-block parser, codegen. Own phase, large.

**P4 — ✅ JS: CONF001 closed (30/08).** `config.*` works in JS via
`kof_platform` (`kofConfigLookup` reads `kof.config`/env; `kof_platform` exposes
`getenv`); `KofConfig.supportedOn` now returns `true` for all targets.

### 8.3 What we will NOT do

- Automatic config reload (hot reload): high runtime complexity,
  low value in containerized environments (the pod restarts).
- Secrets in a file (even encrypted): env is already the universal contract
  (Kubernetes, systemd, CI). No inventing a vault format.
- YAML/TOML: the `key=value` format with `#` covers 100% of real app
  config cases; YAML brings a dependency and error surface (indentation)
  with no benefit. If structure is ever needed, P3 (declarative block)
  solves it with typing, not with indentation.
