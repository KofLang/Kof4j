[English](PLAN-TLS-HARDENING.md) | [Português](PLAN-TLS-HARDENING.pt_BR.md)

last: none
doing: none — plan only, zero code
next: spike-0-inventory (SNI/hot-reload/ACME-state audit) — only if a maintainer orders promotion per `D-FUTURE-PROMOTION`
location: docs/development/future
state: planned

intent: kof-tls-hardening-beyond-dev-self-signed

**Rule 6 gate:** TLS touches a **frozen-semantics surface** (the `web.app()`
contract + the `kof.http` trust behavior, both user-visible and
security-critical). This document is **plan only, zero code** — no compiler,
runtime, CLI or dependency change is made by it. Promotion requires a
maintainer decision (`D-TLS-HARDEN` or an extension of `D-SEC.6`) and the
future-promotion flow (`docs/development/future/README.md` §"When to move",
`AGENTS.md` §"Future promotion"): rewrite with `UNDER DEVELOPMENT`, real state
+ how-to-finish, queue in `roadmap.md` §23, point `docs/status.md` at it,
claim in `DOING.md`.

**Source:** external downstream need (the KofCloud edge serves multi-tenant
HTTPS and today cannot terminate TLS inside the Kof process for raw sockets;
`kof-cloud/docs/12-tls.md`, `L2`), plus the measured gaps inside Kof's own
TLS surface (below).

---

# TLS hardening — from dev self-signed to production TLS (plan, zero code)

## 1. Motivation

Kof can serve HTTPS today — on exactly one target, with exactly one
certificate model, and a client that trusts everything. That is the honest
state:

- **Server, JVM only:** `app.listenSecure(port)` generates a throwaway
  self-signed certificate (dev convenience); `app.listenSecure(port,
  certPem, keyPem)` serves with an operator-supplied PKCS#8 PEM pair
  (production). Both emit `WEB002` on Native/JS — an honest refusal, never
  a silent downgrade to plaintext.
- **Client, JVM:** `kof.http` against `https://` URLs uses a **trust-all**
  `HttpClient` (`KOF_HTTP_CLIENT_INSECURE`): any certificate is accepted,
  hostname verification is disabled. Correct for self-signed test servers;
  wrong as a default for production traffic.
- **What does not exist anywhere:** SNI-based certificate selection (one
  process serving many hostnames over TLS), certificate hot-reload without
  restart, and any ACME/Let's Encrypt automation.

The use case is not hypothetical: any Kof program that terminates TLS for
more than one hostname — a reverse proxy, a multi-tenant edge, a SaaS router
— needs (1) and (2) below. The plan keeps what exists working and adds the
missing production faces in slices, each independently testable.

## 2. Measured baseline (what exists today — file-anchored)

| Surface | State today | Where |
|---|---|---|
| `listenSecure` lowering (1-arg dev, 3-arg PEM) | **landed**: `case "listenSecure"` yields `kof_web_listen_secure` (1 `Int` arg) or `kof_web_listen_secure_pem` (1 `Int` + 2 `String` args); anything else yields `null` (typer refuses) | `kof-compiler/.../KofWeb.java:163-176` |
| Non-JVM gap | **honest**: both TLS calls map to `WEB002` in `gapCode()` (`KofWeb.java:217`) | `KofWeb.java:217` |
| JVM server impl (self-signed) | **landed**: `kof_web_listen_secure(appId, port)` — `SSLServerSocketFactory`, bind `0.0.0.0`, `setNeedClientAuth(false)`, virtual-thread accept loop | `jvm/JvmRuntimeWebServer.java:43-70` |
| JVM server impl (own PEM) | **landed**: `kof_web_listen_secure_pem(appId, port, certPem, keyPem)` — X.509 cert + PKCS#8 key (RSA/EC/DSA tried in order), PKCS12 in-memory keystore (`"changeit"`), `SunX509` | `jvm/JvmRuntimeWebServer.java:78-106`, context at `:108-140` |
| Self-signed issuance | **dev-only**: `keytool -genkeypair` (RSA-2048, 365d, `CN=localhost`, `SAN=IP:127.0.0.1,DNS:localhost`), temp JKS file deleted in `finally` | `jvm/JvmRuntimeWebServer.java:153-175` |
| HTTP client trust | **trust-all**: `KOF_HTTP_CLIENT_INSECURE` — empty-issuer `X509TrustManager`, `setEndpointIdentificationAlgorithm("")`; used for **every** `https://` request | `jvm/JvmWebHttpRuntime.java:112-132`, selection at `:136-137` |
| Tests | **7/7 measured** (`KofWebTlsTest`): hello route, headers, `kof.http`-over-TLS, Native gap, JS gap, own-cert serves HTTPS, own-cert Native gap | `KofWebTlsTest.java` (`@Test` × 7) |
| SNI selection | **absent** — zero hits for `SNIServerName`/`SNIMatcher`/`setServerNames` in compiler+runtime sources | (nothing to measure) |
| Certificate hot-reload | **absent** — the `SSLContext` is built once per `listenSecure` call; no reload/rotation/watch path | (nothing to measure) |
| ACME / Let's Encrypt | **absent** — zero hits outside unrelated identifiers | (nothing to measure) |
| Production warning | `listen`/`listenSecure` under `KOF_ENV=production` without `app.security()` warns on `stderr` | `stdlib-web.md:165-166`, `JvmWebSecurityRuntime.java:268-273` |
| Frozen contract | `D-SEC.6`: the 3-arg API, PKCS#8 key requirement, JVM-first, "self-signed is dev convenience, not production" | `docs/development/DECISIONS.md:441-466` |

**Known doc drift (recorded, not fixed here):** `docs/stdlib/http.md:325`
and `docs/status.md:781` still claim `KofWebTlsTest` has 5 tests; the file
has 7 `@Test` methods (the two own-cert faces landed later). Fixing the
count belongs to the promotion slice (docs update, not this plan).

## 3. Design principles (frozen for all slices)

1. **No silent downgrade, ever.** A TLS failure (bad PEM, expired cert,
   unknown SNI hostname) refuses loudly with a named diagnostic — never
   falls back to plaintext, never serves the wrong certificate.
2. **The existing 1-arg/3-arg surface does not change.** New capability
   arrives as new functions/opts, never by reinterpreting what
   `listenSecure(port)` or `listenSecure(port, certPem, keyPem)` mean
   (`D-SEC.6` stays the contract).
3. **Trust-all stays a test tool, never the production default.** Any slice
   that introduces real verification must make the insecure client
   opt-in and loud (env-gated, warned), not remove it (tests depend on it).
4. **JVM first, gaps stay honest.** Native/JS keep reporting `WEB002`
   until a slice genuinely lands there — no stub that pretends TLS works.
5. **Never homemade crypto.** Every new primitive (ACME JWS needs ES256 —
   already in the tree for OAuth; anything else) reuses audited JDK/JCA
   providers, exactly as the current `SunX509`/PKCS12 path does.

## 4. Slices (ordered — each independently testable)

**Slice 0 — inventory spike (audit, zero code).** Re-measure §2 at
promotion time (line numbers drift): confirm the 7 tests still green,
confirm zero SNI/reload/ACME, confirm the doc-drift count, and write the
measured state into the promoted plan. This is the promotion's entry gate —
no code before it.

**Slice 1 — SNI certificate selection (JVM).** One process, many hostnames:
the server holds a hostname→(`certPem`, `keyPem`) map (built from explicit
API arguments, never from directory scanning) and selects the certificate
from the ClientHello SNI. Unknown SNI hostname → handshake refusal with a
named diagnostic (principle 1), never the default cert. RED-first proof:
two hostnames on one port serve their own certs (verified by fingerprint),
a third hostname is refused, and the no-SNI client gets the documented
default behavior — all over real `SSLSocket`s in `KofWebTlsTest` style.

**Slice 2 — certificate hot-reload without restart (JVM).** Re-read the PEM
pair (SNI map or single-cert) on demand — explicit `app.reloadTls()` call
and/or file-watch — and swap the `SSLContext` atomically; in-flight
connections keep the old context, new handshakes use the new one.
Reload failure keeps serving the old certificate and reports (never serves
half-loaded state). RED-first proof: serve cert A → reload cert B →
new connections present B's fingerprint while an old connection still
completes; a corrupt replacement file changes nothing and reports.

**Slice 3 — verifying HTTP client (JVM).** A strict `kof.http` path that
validates the chain against the platform trust store **with** hostname
verification, alongside the existing trust-all client (kept for tests).
The strict path is the default for new code; trust-all requires an
explicit opt-in that warns. RED-first proof: strict client accepts a
properly-chained test CA cert, rejects self-signed, and rejects hostname
mismatch — each with a named diagnostic, none silent.

**Slice 4 — ACME DNS-01 client (JVM, external CA).** Automate issuance for
the PEM pair Slice 1 consumes: DNS-01 challenge flow (the edge is
multi-tenant, HTTP-01 cannot route per-hostname pre-verification),
`ES256` JWS (already in-tree per the OAuth runtime — reuse, do not add a
signer), key storage delegated to the operator's secret store (never
persisted in plaintext by Kof). Explicit non-goal: no Kof-run DNS server,
no HTTP-01 listener magic. RED-first proof: issuance against Pebble
(the standard ACME test server) in CI, then the issued pair serves via
Slice 1.

**Slice 5 — Native/JS parity (only if a lane owns it).** TLS per target
after the JVM contract is frozen and proven: Native via the platform TLS
of each arch (never a bundled crypto reimplementation), JS via the host
`tls` module surface. Each target keeps `WEB002` until its slice lands —
parity is per-slice (SNI/reload/verify/ACME land independently per
target), never a flag-day.

## 5. Explicit non-goals

- No change to the `D-SEC.6` 1-arg/3-arg contract (additive only).
- No `mTLS`/client-certificate auth in this plan (server-side TLS only;
  `setNeedClientAuth(false)` stays).
- No TLS 1.3-only policy, no cipher-suite DSL — JDK defaults, documented.
- No certificate transparency monitoring, no OCSP stapling management
  (JDK handles stapling where configured; the plan does not add knobs).
- No ACME HTTP-01/TLS-ALPN-01 (DNS-01 only — the multi-tenant reason).
- No removal of the trust-all test client (tests depend on it; §3.3).
- No dates, no effort estimates (planning ≠ queue).

## 6. Acceptance (what would close the initiative)

Each slice closes on its RED-first proof (real sockets, real certs, real
refusals — §4). The initiative closes when Slices 0–4 are green on JVM
with `KofWebTlsTest`-style E2E coverage, the doc-drift count is fixed,
`backend-parity.md` reflects per-slice per-target state, and no new
silent-downgrade path exists (audited: every TLS failure in the new code
has a named diagnostic). Slice 5 closes per target, independently.

## 7. References

- `kof-compiler/src/main/java/dev/kof/compiler/KofWeb.java:163-176`
  (lowering), `:217` (WEB002 gap).
- `kof-compiler/src/main/java/dev/kof/compiler/jvm/JvmRuntimeWebServer.java:43-70`
  (self-signed), `:78-140` (own PEM), `:153-175` (keytool issuance).
- `kof-compiler/src/main/java/dev/kof/compiler/jvm/JvmWebHttpRuntime.java:112-137`
  (trust-all client).
- `kof-compiler/src/test/java/dev/kof/compiler/KofWebTlsTest.java` (7 tests).
- `docs/development/DECISIONS.md:441-466` (D-SEC.6, the frozen contract).
- `docs/stdlib/stdlib-web.md:165-166,205-217` (production warning, API table).
- `docs/stdlib/http.md:315-325` (G12 section; note the 5-vs-7 test-count drift).
- Downstream need: `kof-cloud/docs/12-tls.md`, `L2` (no TLS on raw sockets).
