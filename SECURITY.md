[English](SECURITY.md) | [Português](SECURITY.pt_BR.md)

# Security Policy — Kof4j

> **EN:** a security vulnerability **never** goes into a public issue/PR.
> Report it through the private channel below. **EN summary at the bottom.**

## How to report (private channel)

1. **Preferred — GitHub Private vulnerability reporting:**
   [Report a vulnerability](https://github.com/KofLang/Kof4j/security/advisories/new)
   (`Security` → `Advisories` → `Report a vulnerability`). Only the maintainer
   and designated collaborators see the content.
2. What to include: version (`cat VERSION`, currently `0.5.0-beta`), affected
   target (`jvm`/`native`/`native.risc`/`native.arm`/`js`), **minimal repro**
   (minimal `.kf` + `kof run|build|serve` command), actual vs expected output,
   estimated impact (RCE, auth bypass, secret leak, DoS…). PoC is welcome;
   weaponized exploit, not.
3. **Do not open a public issue, a public PR, or comment in an open thread**
   before the coordinated fix — that exposes users.

## Scope

**In scope** (maintained in this repo, GPLv3):

- Compiler and backends (`kof-compiler`: JVM/Native/JS, KofScript, KofC),
  CLI (`kof-cli`), native runtime (`kof-runtime/`, `native/`).
- Stdlib with a security surface: `kof.security` (passwords/PBKDF2,
  crypto AES-GCM/ChaCha20, JWT HS256, secrets, `auth.*`, CSRF/CORS/headers,
  rateLimit/sessions/API keys — see `docs/stdlib/security.md`), `kof.web`
  (serve/engine, TLS), `kof.http` (client, retry/circuit), `kof.db`/`kof.orm`
  (SQL binds, migrations), `kof.config` (env/files), distribution
  (`scripts/package.sh`, `bin/kof`, workflows in `.github/`).
- Declared dependencies (`pom.xml`, GitHub Actions) and images/services
  used in CI.

**Out of scope:** programs written *in* Kof by third parties (user code),
deploy/infra of those who use Kof, social engineering, phishing, volumetric
DoS without a PoC of amplification in our code, and any association with the
The King of Fighters franchise (see disclaimer in `README.md` — it is not
a security problem).

## Response commitment (best-effort, open source)

- **Triage within 5 business days**, by the maintainer
  ([@aminadojava](https://github.com/aminadojava), CODEOWNERS).
- Severity by real impact (remote execution > auth bypass >
  secret leak > local DoS > hardening). Reports on `kof.security`
  (crypto/auth) have top priority — the repo rule is **crypto never
  homemade, secure default, failure with diagnostic** (`SECN00x`), and the fix
  follows the same pattern.
- Fix on the active branch (`lab`, per `D-BRANCH-PIPELINE`) with a
  **regression test in the same
  commit** (repo quality gate), advisory published in
  [Security advisories](https://github.com/KofLang/Kof4j/security/advisories)
  after the patch, with credit to the reporter (unless anonymity is requested).
- Coordinated disclosure: we ask for **up to 90 days** between the private
  report and public disclosure; the advisory goes out together with the release
  containing the fix.

## Safe harbor

Good-faith research on this repo is welcome: we will not take action
against those who follow this policy (scope respected, no third-party data
exfiltration, no service degradation, no disclosure before the fix).
Aggressive automated testing against `github.com/KofLang/*` outside your
own fork/clone is not research — it is abuse.

## Hardening already in effect in this repo

- Secrets per environment (`KOF_JWT_SECRET`, `KOF_<KEY>`, `kof.config`),
  never hardcoded; `*.env` is in `.gitignore`.
- `kof.security`: PBKDF2-HMAC-SHA256 600k, AES-GCM/ChaCha20 failing on
  tamper, JWT HS256 with `alg` pinned (no algorithm confusion),
  constant-time comparison, secret redaction in logs.
- Versioned formats (`pbkdf2$…`, `aesgcm$…`, `chacha20$…`) — details in
  `docs/stdlib/security.md`.
- CI with CodeQL (`.github/workflows/codeql.yml`),
  secret scanning with Gitleaks
  (`.github/workflows/secret-scan.yml`) and Dependabot
  (`.github/dependabot.yml`).

---

### EN summary

**Do not open a public issue for security vulnerabilities.** Report privately
via [Report a vulnerability](https://github.com/KofLang/Kof4j/security/advisories/new)
including version, affected target, minimal `.kf` repro and impact. Triage
within 5 business days; coordinated disclosure (up to 90 days); advisory
published with the fixing release. Good-faith research within scope is
welcome. Scope and details above; security architecture in
`docs/stdlib/security.md`.
