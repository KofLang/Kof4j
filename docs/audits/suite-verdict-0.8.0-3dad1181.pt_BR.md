[English](suite-verdict-0.8.0-3dad1181.md) | [Português](suite-verdict-0.8.0-3dad1181.pt_BR.md)

# Veredito carimbado da suíte — a candidata da 0.8.0 em `3dad1181`

**Run:** `scripts/safe-suite.sh` (reatores completos, `-Dmaven.test.failure.ignore=true`), concluída 15/11/2026 ~11:00 local, log `/tmp/opencode/safe-suite-0.8.0-tip5.log` (a cópia na tmpfs desde então limpa; ESTE arquivo é o extrato durável).

**Carimbo (como impresso pelo safe-suite.sh):**
```
SUITE-SHA: 3dad118116eefae390e28a42b89f049074fc3b3b
SUITE-DIRTY: 0
TOTAL: tests=8210 failures=0 errors=0 skipped=1014
[INFO] BUILD SUCCESS
```

**Veredito:** `stability-report.sh` → **STABILITY: GREEN sha=3dad1181 tests=8210 failures=0 errors=0 skipped=1014**; `check_lab_stability.sh` → **CUT: GREEN** (rc-gate rc=0 — 0 `1.0-blocks` abertos; gates estruturais TODOS rc=0). O corte da 0.8.0 é o merge da mantenedora.

**Histórico de runs (o ledger honesto, EN+PT no DOING.md 15/11 12:02):** run-1 7267/0F/7E (o burst de quota da tmpfs /tmp — os perfis browser por-teste + os consumidores permanentes bateram o teto de 7.8G); run-2 8210/0F/2E (o binário Cypress 16.1.1 pinado ausente — `cypress install` o restaurou); run-3 8210/0F/1E (uma falha de leitura transiente sob pressão da tmpfs, passou isolada); run-4 4635/1067F — INVÁLIDA pela própria mão da lane (`_JAVA_OPTIONS` contamina o stderr de toda JVM filha: nunca setá-lo numa suíte byte-exata); run-5 VERDE com o `-Dsurefire.argLine=-Djava.io.tmpdir=/var/tmp/kof-surefire` documentado no pom (só os forks).

**Regra de re-medição (a permanente):** um corte re-mede no tip final limpo na hora do corte — este extrato certifica o veredito de 15/11, não a ponta para sempre.
