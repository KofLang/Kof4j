[English](SECURITY.md) | [Português](SECURITY.pt_BR.md)

# Security Policy — Kof4j

> **PT-BR:** vulnerabilidade de segurança **nunca** vai em issue/PR pública.
> Reporte pelo canal privado abaixo. **EN summary at the bottom.**

## Como reportar (canal privado)

1. **Preferencial — Private vulnerability reporting do GitHub:**
   [Report a vulnerability](https://github.com/KofLang/Kof4j/security/advisories/new)
   (`Security` → `Advisories` → `Report a vulnerability`). Só a mantenedora
   e colaboradores designados veem o conteúdo.
2. O que incluir: versão (`cat VERSION`, hoje `0.5.0-beta`), target afetado
   (`jvm`/`native`/`native.risc`/`native.arm`/`js`), **menor repro**
   (`.kf` mínimo + comando `kof run|build|serve`), output real vs esperado,
   impacto estimado (RCE, bypass de auth, vazamento de segredo, DoS…).
   PoC é bem-vindo; exploit weaponizado, não.
3. **Não abra issue pública, PR pública, nem comente em thread aberta**
   antes do fix coordenado — isso expõe os usuários.

## Escopo

**No escopo** (mantido neste repo, GPLv3):

- Compilador e backends (`kof-compiler`: JVM/Native/JS, KofScript, KofC),
  CLI (`kof-cli`), runtime nativo (`kof-runtime/`, `native/`).
- Stdlib com superfície de segurança: `kof.security` (passwords/PBKDF2,
  crypto AES-GCM/ChaCha20, JWT HS256, secrets, `auth.*`, CSRF/CORS/headers,
  rateLimit/sessions/API keys — ver `docs/stdlib/security.md`), `kof.web`
  (serve/engine, TLS), `kof.http` (client, retry/circuit), `kof.db`/`kof.orm`
  (SQL binds, migrations), `kof.config` (env/arquivos), distribuição
  (`scripts/package.sh`, `bin/kof`, workflows em `.github/`).
- Dependências declaradas (`pom.xml`, GitHub Actions) e imagens/serviços
  usados no CI.

**Fora de escopo:** programas escritos *em* Kof por terceiros (código do
usuário), deploy/infra de quem usa o Kof, engenharia social, phishing, DoS
volumétrico sem PoC de amplificação no nosso código, e qualquer associação
com a franquia The King of Fighters (ver disclaimer no `README.md` — não é
um problema de segurança).

## Compromisso de resposta (best-effort, open source)

- **Triagem em até 5 dias úteis**, pela mantenedora
  ([@aminadojava](https://github.com/aminadojava), CODEOWNERS).
- Severidade pelo impacto real (execução remota > bypass de auth >
  vazamento de segredo > DoS local > hardening). Reportes de `kof.security`
  (cripto/auth) têm prioridade máxima — a regra do repo é **cripto nunca
  caseira, default seguro, falha com diagnóstico** (`SECN00x`), e o fix segue
  o mesmo padrão.
- Correção na branch ativa (`lab`, conforme `D-BRANCH-PIPELINE`) com **teste de
  regressão no mesmo
  commit** (portão de qualidade do repo), advisory publicado em
  [Security advisories](https://github.com/KofLang/Kof4j/security/advisories)
  após o patch, com crédito ao reporter (salvo pedido de anonimato).
- Divulgação coordenada: pedimos **até 90 dias** entre o reporte privado e
  a divulgação pública; o advisory sai junto com a release que contém o fix.

## Safe harbor

Pesquisa de boa-fé sobre este repo é bem-vinda: não tomaremos medidas
contra quem seguir esta policy (escopo respeitado, sem exfiltração de dados
de terceiros, sem degradação de serviço, sem divulgação antes do fix).
Testes automatizados agressivos contra `github.com/KofLang/*` fora do seu
próprio fork/clone não são pesquisa — são abuso.

## Hardening que já vale neste repo

- Segredos por ambiente (`KOF_JWT_SECRET`, `KOF_<KEY>`, `kof.config`),
  nunca hardcoded; `*.env` está no `.gitignore`.
- `kof.security`: PBKDF2-HMAC-SHA256 600k, AES-GCM/ChaCha20 com falha em
  tamper, JWT HS256 com `alg` fixado (sem confusão de algoritmo),
  comparação em tempo constante, redact de segredos em logs.
- Formatos versionados (`pbkdf2$…`, `aesgcm$…`, `chacha20$…`) — detalhe em
  `docs/stdlib/security.md`.
- CI com CodeQL (`.github/workflows/codeql.yml`),
  varredura de segredos com Gitleaks
  (`.github/workflows/secret-scan.yml`) e Dependabot
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
