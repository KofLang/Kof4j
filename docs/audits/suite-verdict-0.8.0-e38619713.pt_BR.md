[English](suite-verdict-0.8.0-e38619713.md) | [Português](suite-verdict-0.8.0-e38619713.pt_BR.md)

# Veredito carimbado da suíte — a candidata da 0.8.0 REFRESCADA em `e38619713`

**Run:** `scripts/safe-suite.sh -o test -Dmaven.test.failure.ignore=true '-Dsurefire.argLine=-Djava.io.tmpdir=/var/tmp/kof-surefire'` (o override de tmpdir só-nos-forks — a lição da run-4), concluída 15/11/2026 ~17:50 local, log `/var/tmp/kof-suite-080f-refresh2.log` (o caminho durável no disco — sem risco de limpeza da tmpfs).

**Carimbo (como impresso pelo safe-suite.sh):**
```
SUITE-SHA: e3861971319280522952de9cc1364fd622ef4089
SUITE-DIRTY: 0
TOTAL: tests=8216 failures=0 errors=0 skipped=1015
[INFO] BUILD SUCCESS
```

**Veredito:** `stability-report.sh` → **STABILITY: GREEN sha=e38619713 tests=8216 failures=0 errors=0 skipped=1015**; `check_lab_stability.sh` → **CUT: GREEN** (rc-gate rc=0 — 0 `1.0-blocks` abertos; gates estruturais TODOS rc=0 — 15 gates incluindo o gate de conformidade `check_conformance_1_0`). **O CARIMBO É A HEAD — zero deriva** (a face bare com HEAD default está VERDE sem o tratamento EXPECT_SHA: a run aconteceu no tip final).

**O QUE ESTE VEREDITO COBRE (o delta desde `3dad1181`, o VERDE anterior):** as fatias de copy-out de arrays FFI (o `kof_ffi_copy_out` do JVM + o payload direto nativo com o opt-in `ownership borrowed` — o default by-value RESTAURADO após o report de regressão da lane issues/tooling), as faces linalg level-2 (dscal/axpy) e level-3 (gemm via cblas_dgemm, o enum CblasRowMajor=101 medido), o gate de conformidade + a lista de 15 gates de estabilidade, o conserto do split copy_in_strings/%1$s (JvmFfiCopyRuntime), as construções 6-arg do FfiNativeCrossGateTest (pegas pelo compile da PRIMEIRA tentativa de refresh — a própria suíte pegou a construção perdida).

**Histórico de runs:** a primeira tentativa de refresh (`/tmp/opencode` limpo + as construções velhas do FfiNativeCrossGateTest) falhou no compile — consertado em `e38619713` e esta run foi direto VERDE. A história anterior de 5 runs (o burst de quota da tmpfs, o binário Cypress, a leitura transiente, a lição do _JAVA_OPTIONS) vive no extrato `3dad1181` (`suite-verdict-0.8.0-3dad1181.md`).

**Regra de re-medição (a permanente):** um corte re-mede no tip final limpo na hora do corte. Se a ponta não moveu desde `e38619713`, ESTE veredito É o veredito de corte.
