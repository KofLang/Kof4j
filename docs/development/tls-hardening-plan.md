[English](tls-hardening-plan.md) | [Português](tls-hardening-plan.pt_BR.md)

last: 02/11/2026 — PROMOTED from `docs/development/future/` by maintainer order ("pega tls do future pra fazer, é urgente"); slice 0 (inventory spike) MEASURED at the tip
doing: slices 0-3 LANDED with executed proof (KofWebTlsTest 19/19: inventory + SNI + hot-reload + verifying client); slice 4 (ACME DNS-01 client) is the next slice
next: slice 4 — ACME DNS-01 client (JVM, external CA): Pebble in CI, ES256 JWS reuse from the OAuth runtime, key storage delegated to the operator
location: docs/development
state: under-development
owner: 192.168.100.14:0, flatt CLI, no opencode server on this host; port n/a

intent: kof-tls-hardening-beyond-dev-self-signed

**Rule 6 gate — SATISFIED 02/11/2026.** TLS touches a **frozen-semantics
surface** (the `web.app()` contract + the `kof.http` trust behavior, both
user-visible and security-critical). The maintainer ordered the promotion
directly ("pega tls do future pra fazer, é urgente"), which is the rule-6
decision for this plan; the frozen contract it must respect is `D-SEC.6`
(additive only — the 1-arg/3-arg `listenSecure` surface never changes). The
future-promotion flow was executed: rewritten `UNDER DEVELOPMENT`, queued in
`roadmap.md` §23, `docs/status.md` points at it, claimed in `DOING.md`.

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

### Slice 0 — MEASURED at promotion (02/11/2026, lane `192.168.100.14:0`)

Re-measured at the tip before any code (the promotion entry gate):

| Claim | Measured |
|---|---|
| `KofWebTlsTest` test count | **7** `@Test` (the plan's §2 table is right; the 5-vs-7 doc drift is real) |
| SNI selection | **absent** — 0 files match `SNIServerName`/`SNIMatcher`/`setServerNames` |
| Certificate hot-reload | **absent** — 0 files match `reloadTls`/`reloadTLS` |
| ACME | **absent** — the single `acme` hit is `NativeBackend.java:493` (a JNI name-mangling comment, unrelated) |
| `listenSecure` lowering | `KofWeb.java:163` (`case "listenSecure"`), gap at `:217` (`WEB002`) |
| JVM server impl | `JvmRuntimeWebServer.java:43` (self-signed), `:78` (own PEM), `:130` PKCS12, `:135` `SunX509`, `:156` keytool |
| Trust-all client | `JvmWebHttpRuntime.java:112` (`KOF_HTTP_CLIENT_INSECURE`), `:117` empty trust manager, `:123` `setEndpointIdentificationAlgorithm("")`, `:137` selection |
| Doc drift | `docs/stdlib/http.md:325` and `docs/status.md:783` both say **5** tests; the file has **7** |

Line numbers drifted slightly from the plan's §2 (e.g. the trust-all block is
`:112-137`, not `:112-132`); the substance is unchanged. Slice 1 may start.

### Slice 1 — LANDED 02/11/2026 (lane `192.168.100.14:0`, flatt CLI)

SNI certificate selection on the JVM, additive over `D-SEC.6`:

- **Kof surface:** `web.secureHost(host, certPem, keyPem)` builds a
  compile-time-only descriptor; `app.listenSecure(port, listOf(secureHost(...), ...))`
  (the 2-arg overload) serves the list. The 1-arg/3-arg surfaces are unchanged.
- **Lowering:** the list literal is flattened into three parallel
  `String[]` (hosts/certs/keys) and emitted as
  `kof_web_listen_secure_sni(appId, port, hosts[], certs[], keys[])`;
  a non-literal list is refused with `WEB007` (the certificate set must be
  explicit at the call site). Native/JS keep the honest `WEB002`.
- **Runtime:** a PLAIN `ServerSocket`; per connection the first TLS record is
  read, the SNI hostname is parsed from the ClientHello, and the handshake
  runs on an `SSLEngine` over that host's `SSLContext` (one PEM pair each).
  Unknown SNI → `HTTP 421` + `WEB007` on stderr, NO certificate (never the
  default). No SNI at all → the FIRST configured host (documented default).
- **Why SSLEngine:** the JDK's server `SSLSocket` path does NOT expose the
  ClientHello SNI to a KeyManager (`chooseServerAlias` has no SNI; only the
  engine's `chooseEngineServerAlias` does), and `createSocket(Socket,...)`
  ignores the passed Socket's streams — the handshake must be engine-based.
- **Proof (executed):** `KofWebTlsTest` **11/11** (the 7 original + 4 new:
  `sniSelectsTheCertificatePerHostname` (fingerprints differ per host),
  `sniUnknownHostnameIsRefused` (loud refusal), `sniNoHostnameGetsTheDocumentedDefault`
  (first cert), `sniGapOnNative` (WEB002)); `mvn -o -pl kof-compiler -am compile` rc=0;
  the TLS template split (check_500: `JvmRuntimeWebServer` 744→257 + new
  `JvmRuntimeTlsServer` 506, both below 600).

Debug war stories kept OUT of the code but recorded here for the next slice:
the emit bugs were a String-loaded int literal (`KofLoadLiteral.ofInt` fix) and
`KofNewArray`+`KofArrayStore` (not `kof_array_*` calls); the runtime bug was a
`PushbackInputStream.read(b,off,len)` that drains the pushback and then BLOCKS
for the remainder — the replay must be a `SequenceInputStream(ByteArrayInputStream,
rawStream)` so the first `fillNet` cannot deadlock (the peer only sends its next
flight after our ServerHello).

### Slice 2 — LANDED 03/11/2026 (lane `192.168.100.14:0`, flatt CLI)

Certificate hot-reload without restart, additive over `D-SEC.6`:

- **Kof surface:** `app.reloadTls(certPem, keyPem)` re-arms a single-cert
  server with a NEW PEM pair; `app.reloadTls(listOf(secureHost(...), ...))`
  replaces the WHOLE SNI map. No reload on the 1-arg dev server (dev
  convenience, not production).
- **Atomicity:** the per-app TLS state (context map + default host) lives in
  an `AtomicReference` (`TLS_HOLDERS`); the swap is one reference write —
  in-flight connections keep the engine built at their handshake, new
  handshakes read the new state. The 3-arg PEM listen now goes through the
  SAME engine path (plain `ServerSocket` + per-connection handshake), which is
  what makes the swap possible at all.
- **Failure honesty:** a reload that cannot build the new context throws
  `WEB007` (catchable as a Kof String — `catch (String e)`) and the OLD state
  keeps serving; never a half-loaded state.
- **Proof (executed):** `KofWebTlsTest` **14/14** — the 11 original +
  `reloadSwapsTheCertificateAtomically` (in-flight connection opened BEFORE
  the reload still completes while NEW connections present B's fingerprint),
  `reloadFailureKeepsServingAndReports` (the route catches `WEB007` and the
  fingerprint stays A), `sniReloadSwapsTheWholeMap` (post-slice-1 map behavior
  pinned); `mvn -o -pl kof-compiler -am compile` rc=0; the PortuKof method
  aliases regenerated for the new dispatcher arm (13 WEB_APP members).

### Slice 3 — LANDED 04/11/2026 (lane `192.168.100.14:0`, flatt CLI)

The verifying HTTP client, JVM-first:

- **Strict default:** `kof.http` https:// requests now validate the chain
  against the platform trust store WITH hostname verification
  (`HttpClient.newHttpClient()` does both). The trust-all client stays, but
  only behind the opt-in (existing tests use it explicitly).
- **The opt-in:** `http.insecure()` (0 args, JVM face) switches https:// back
  to the trust-all client and warns once on stderr
  (`TLS-TRUST-ALL: ... test/dev only`). On JS/Native the opt-in is an honest
  compile-time refusal (HTTP002): the JS fetch stack verifies by default and
  the Native https face refuses — neither has a bypass surface.
- **Named diagnostics:** a strict rejection is catchable as a Kof String:
  `TLS-VERIFY: certificate rejected for <url> — <cause> (use http.insecure()
  to opt into trust-all for test/dev)`.
- **Proof (executed):** `KofWebTlsTest` **19/19** — the 14 previous +
  `strictClientRejectsSelfSigned` (TLS-VERIFY), `strictClientAcceptsChainedCaCert`
  (a REAL test CA via keytool: genkeypair(bc) → certreq → gencert(-rfc) →
  import into the server keystore as the client trust store, `-Djavax.net.ssl.trustStore=...`),
  `strictClientRejectsHostnameMismatch`, `insecureOptInWarnsAndTrustsSelfSigned`,
  `insecureGapOnNative` (HTTP002); `KofHttpE2ETest` 8/8 (the http:// face
  untouched); `mvn -o -pl kof-compiler -am compile` rc=0.

keytool war stories (for the next slice's docs): keytool prompts for the
keystore password when it is not given (a hung child, not an error — always
pass `-storepass`); `-ext bc=ca` is INVALID (the valid spelling is `-ext bc`);
`-gencert -outfile` writes DER unless `-rfc`; the emitted PEM is in the
SYSTEM charset (decode with `Charset.defaultCharset()`, never
`Files.readString`).

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
