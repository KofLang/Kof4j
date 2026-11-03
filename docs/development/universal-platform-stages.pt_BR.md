[English](universal-platform-stages.md) | [Português](universal-platform-stages.pt_BR.md)

last: 03/11/2026 — PROMOVIDO de `docs/development/future/` pela ordem direta da mantenedora ("pega o universal platform de future"); a Fase 4.6 (registro leve de experimentos) POUSOU no mesmo ciclo com prova executada (`ExperimentsE2ETest` 2/2)
doing: as Fases 4–7 são o plano de desenvolvimento CORRENTE — 4.6 FEITA (`libs/experiments/`), a metade descritiva da 4.3 FEITA (`libs/stats/Stats.kf`: mean/median/minimum/maximum/pvariance/stddev/percentile, `StatsE2ETest` 2/2 JVM+Script); os itens restantes estão medidos contra as suas dependências reais abaixo
next: o próximo CANDIDATO — 4.5 (escritor SVG), 5.9, 5.10, 7.1 (formatos textuais), 7.3; todo o resto segue bloqueado pelo R3 (FFI), Arrow ou pela ordem de estágios do R12
location: docs/development
state: under-development
dona: 192.168.100.14:0, flatt CLI, sem servidor opencode neste host; porta n/a

intent: universal-platform-stages-4-7

**Gate da regra 6 — SATISFEITO 03/11/2026.** A mantenedora ordenou diretamente: "pega o universal platform de future". Essa ordem É a decisão da regra 6 para este plano (o mesmo padrão da promoção do plano TLS). O contrato congelado que ele precisa respeitar segue intacto: nenhum primitivo novo de núcleo, nenhuma sintaxe nova, nada de semântica congelada.

**Origem:** `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (Fases 1–3 + 8, `EM DESENVOLVIMENTO` desde 17/09 sob `D-UNIVERSAL`) separou as Fases 4–7 para `future/` pela regra dos três estados. Este documento é essa metade, agora promovida de volta ao trabalho corrente pela ordem da mantenedora.

---

# IMPLEMENTATION-UNIVERSAL-PLATFORM — Fases 4–7 (DATA / SECURITY-expansão / CIENTÍFICO / BIO)

## Status medido na promoção (03/11/2026)

Cada linha é o item do próprio plano verificado contra a árvore, não contra a prosa do plano.

| Item | O plano diz | Medido hoje | Veredito |
|---|---|---|---|
| 4.1 `dataframe` tipado | 🔵 precisa R3 | nenhum dataframe, nenhum Arrow em `libs/`/compilador | BLOQUEADO — primitivo novo de núcleo + Arrow |
| 4.2 Arrow/Parquet via FFI | 🔵 precisa R3 | zero código Arrow/Parquet | BLOQUEADO — dependência FFI (R3) |
| 4.3 estatística via FFI | 🔵 precisa R3 | `libs/vision` só tem histograma/equalização | PARCIAL — uma fatia de estatística descritiva em Kof puro é expressável; a metade FFI (distribuições/álgebra linear) não |
| 4.4 inferência `kof.ml` | 🔵 precisa R3 | nada | BLOQUEADO — FFI ONNX/libtorch |
| 4.5 visualização leve | 🟡 "kof.ui existe" | `kof.ui` existe como superfície do COMPILADOR (`CompilerUiEmitter`); NÃO há escritor SVG nem `libs/ui` | PARCIAL — um escritor SVG sobre `Raster` é expressível em Kof puro; bindings de gráficos a um engine não |
| **4.6 registro de experimentos** | 🔵 precisa 3.4 | `kof.db` + `kof.io` pousaram; o padrão `DbLedger` prova linhas-sobre-db | **✅ FEITO neste ciclo** |
| 4.7 profiling de pipeline | 🔵 precisa 4.1 | depende do dataframe | BLOQUEADO pela 4.1 |
| 5.1 Secret/KeyHandle | ✅ | pousou 21/09 (`D-SECRETS`) | FEITO |
| 5.2 `keys.*` | 🔵 precisa 5.1 | só a superfície `KeyHandle` da 5.1 | BLOQUEADO — precisa de decisão sobre o key-store (regra 6) |
| 5.3 cripto assimétrica FFI | 🟡 JWT RS/ES na JVM | JWT RS/ES pousou; X.509/TLS pendentes | PARCIAL — a metade TLS POUSOU pelo plano TLS; o parse de X.509 é trabalho de FFI/primitivo |
| 5.4–5.6 PQC/híbrido/secure.channel | 🔵 precisa R3 + `liboqs` | zero código PQC | BLOQUEADO — FFI de `liboqs` auditado (nunca cripto caseira) |
| 5.7 FFI `libpcap` | 🔵 precisa R3 | nada | BLOQUEADO — FFI |
| 5.8 forense | 🔵 precisa 5.7 | nada | BLOQUEADO pela 5.7 |
| 5.9 automação de segurança | 🔵 precisa Fase 2 | `kof.http` + `spawn` + `kof.log` existem | CANDIDATO — um pipeline de threat-intel/alerta em Kof puro é expressível |
| 5.10 monitoração defensiva | 🟡 peças existem | observabilidade + log + db pousados | CANDIDATO — uma fatia de auditoria/detecção em Kof puro é expressível |
| 6.1 BLAS/LAPACK FFI | 🔵 precisa R3 | nada | BLOQUEADO — FFI |
| 6.2 pesquisa SIMD | 🔵 lane nativa | a lane nativa é uma lane VIVA | NÃO É MINHA |
| 6.3 GPU | 🟡 Vulkan existe | compute Vulkan pousou (`libs/game`); CUDA/OpenCL pendentes | PARCIAL — CUDA/OpenCL são trabalho de FFI |
| **6.5 recursos escopados** | 🟡 lane compilador | `using` pousou 28/09 (`D-SCOPED-RESOURCES-GO`), plano CONCLUÍDO | **FEITO (em outro lugar)** |
| 6.6 MPI | 🔵 precisa R3 | nada | BLOQUEADO — FFI |
| 6.7 profiling HPC | 🔵 precisa 6.1 | nada | BLOQUEADO pela 6.1 |
| 7.1 `kof-bio` FASTA/FASTQ/VCF/BAM | 🔵 precisa R3/R5 | nada | CANDIDATO — FASTA/FASTQ/VCF são parsers TEXTUAIS tipados expressíveis em Kof puro; BAM precisa FFI binária |
| 7.2 BLAST/htslib | 🔵 precisa R3 | nada | BLOQUEADO — FFI/CLI |
| 7.3 pipelines genômicos | 🔵 precisa 2.1 | `kof.workflow` pousou | CANDIDATO — pipeline em Kof puro sobre os parsers da 7.1 |
| 7.4 HPC | 🔵 precisa 6.1 | nada | BLOQUEADO pela 6.1 |
| 7.5 automação de laboratório | 🟡 peças existem | `kof.http` existe; a face nativa de `kof.process` é `PROC001` | PARCIAL |

**A conclusão medida:** a própria cadeia de dependências do plano (Fases 1–3 → 4 → 6 → 7, e R3/FFI por toda parte) deixa só um conjunto estreito de itens implementáveis em Kof puro hoje. A 4.6 era um deles e está FEITA. Os próximos candidatos são 4.3 (estatística descritiva), 4.5 (escritor SVG), 5.9, 5.10, 7.1 (formatos textuais) e 7.3 — cada um expressível como biblioteca sobre primitivos existentes, um por ciclo.

---

## Como terminar

1. Pegue a próxima linha CANDIDATO (medida acima), uma por ciclo.
2. Implemente como biblioteca Kof pura em `libs/<domínio>/` — nunca um primitivo novo de compilador, nunca um stub, nunca cripto caseira.
3. Prova RED-first: um `*E2ETest` que compila + roda a biblioteca na JVM e no Script (e no Nativo onde a superfície permitir), com a falha capturada antes de o código existir.
4. Docs EN+PT no mesmo commit; atualize a linha acima para `✅` com a prova.
5. Qualquer item que precise de primitivo novo de núcleo, dependência FFI (R3), Arrow ou decisão da mantenedora fica 🔵 com o motivo nesta tabela.

---

# Estágio 4 — DATA (data engineering / science / ML)

**Objetivo:** uma camada científica **orquestrada** (não reimplementada).
**Dependências:** Estágios 1–3; R3 (FFI); Arrow como padrão de troca.
**NÃO fazer:** **não construir um framework de ML/NumPy em Kof** — Kof fornece
o *wrapper tipado + pipeline*, o *motor* fica fora.

| # | Item | Status | Dono | Depende de |
|---|------|--------|------|------------|
| 4.1 | `dataframe` tipado (lazy, colunar) | 🔵 | — | R3 |
| 4.2 | **Arrow/Parquet via FFI** (wrapper tipado) | 🔵 | — | R3 |
| 4.3 | Estatística/probabilidade via FFI | 🟡 | `192.168.100.14:0` | **a metade descritiva POUSOU 03/11** — `libs/stats/Stats.kf` (Kof puro): `mean`/`median`/`minimum`/`maximum`/`pvariance` (populacional)/`stddev` (sobre `math.sqrt`, MATH001 honesto no cross nativo)/`percentile` (interpolação linear; STATS001 vazio, STATS002 fora de faixa). Prova: `StatsE2ETest` **2/2** (JVM + Script) RED-first (`libs/stats not found` capturado, depois duas faces reais do compilador: o crash de frame COMP002 na forma multi-return — consertado com saída única — e a nota de truncamento SEM090). A metade de distribuições de probabilidade segue 🔵 (FFI/R3) |
| 4.4 | `kof.ml` — inferência via FFI (ONNX/libtorch); treino orquestrado | 🔵 | — | R3 |
| 4.5 | Visualização leve (SVG/`kof.ui` + FFI) | 🟡 | — | `kof.ui` existe; bindings de data-viz pendentes |
| 4.6 | Rastreamento de experimentos (leve, sobre `kof.db`/`kof.io`) | ✅ | `192.168.100.14:0` | **POUSOU 03/11** — `libs/experiments/Experiments.kf`: `initExperiments`, `startRun`/`finishRun`, `logMetric`/`logParam`, `metricAt`/`lastMetric`/`metricCount`, `exportRun` (JSON Lines sobre `kof.io`). Linhas sobre `kof.db`; o leitor decodifica a linha JSON crua com `json.decode<Row>` porque o `db.query<Rec>` tipado perde um record definido em LIB (`known-bugs` §727). Prova: `ExperimentsE2ETest` **2/2** (JVM + Script), RED-first com 4 diagnósticos reais (`null` não atribuível, `integer` não é tipo, receivers nuláveis, `readText` devolve `String?`) |
| 4.7 | Tooling: profiling de pipeline | 🔵 | — | 4.1 |

---

# Estágio 5 — SECURITY (expansão)

**Objetivo:** de "segurança de aplicação" (já forte) para **segurança de
plataforma** (rede, forense, defensiva) — mais uma **camada criptográfica
moderna + pós-quântica** (visão §4.8.1).
**Regra absoluta:** **nunca** cripto caseira — toda primitiva nova (incl. PQC) é
FFI para lib auditada; API idêntica entre alvos; gap = diagnóstico
(`SECN00x`/`SECPQ`), nunca stub fraco.
**Dependências:** Estágios 1–3; R3 (FFI).
**NÃO fazer:** reimplementar stacks de cripto auditadas; trabalho ofensivo sem
contexto legítimo/controlado; defender *primeiro*.

| # | Item | Status | Dono | Depende de |
|---|------|--------|------|------------|
| 5.1 | S2 — tipo `Secret` + `KeyHandle` (redação forçada) | ✅ | lane security | R3; **POUSADO 21/09 (`D-SECRETS`)**: `secrets-plan.md` fechado e movido para `docs/architecture/`, todas as faces pousadas (`Secret`, redação P2, `KeyHandle`), incremental com prova |
| 5.2 | S3 — `keys.*` (generate/derive/rotate/store) | 🔵 | lane security | 5.1 |
| 5.3 | S4 — cripto assimétrica (RSA/ECC/X.509/TLS) via FFI | 🟡 | lane security | JWT RS/ES já no JVM; X.509/TLS pendentes |
| 5.4 | S5 — **PQC** híbrido (ML-KEM-768 + ML-DSA-65 + HKDF + AES-256-GCM) via `liboqs` | 🔵 | lane security | R3; código de gap `SECPQ` |
| 5.5 | S6 — KEM+KDF+AEAD híbrido | 🔵 | lane security | 5.4 |
| 5.6 | S7 — `secure.channel` (KEM+KDF+AEAD+auth+replay) | 🔵 | lane security | 5.5 |
| 5.7 | `kof.net` / parsing de pacotes (FFI para `libpcap`) | 🔵 | — | R3 |
| 5.8 | Forense (FFI para libs de parsing + pipelines Kof) | 🔵 | — | 5.7, Estágio 2 |
| 5.9 | Automação de segurança / threat-intel (`kof.http` + `spawn`/`channel` + `kof.log`) | 🔵 | — | Estágio 2 |
| 5.10 | Defensiva (monitoramento/detecção/auditoria sobre `kof.observability` + `kof.log` + `kof.db`) | 🟡 | — | peças existem (1.6) |

---

# Estágio 6 — SCIENTIFIC COMPUTING (numérico / HPC)

**Objetivo:** Kof como **linguagem de orquestração científica tipada** + zona
numérica via FFI.
**Dependências:** Estágios 1–4; R3 (FFI); GC mark-sweep (1.2).
**NÃO fazer:** reimplementar BLAS/LAPACK/NumPy; ownership/borrowing no core
(a zona não-GC é via FFI para C/Rust).

| # | Item | Status | Dono | Depende de |
|---|------|--------|------|------------|
| 6.1 | Álgebra linear via **FFI para BLAS/LAPACK** (wrapper) | 🔵 | — | R3 |
| 6.2 | SIMD/vectorização (Native — pesquisa) | 🔵 | lane native | 1.2 |
| 6.3 | GPU — Vulkan via FFI (existe); CUDA/OpenCL via FFI | 🟡 | — | Vulkan compute existe; CUDA/OpenCL pendentes |
| 6.4 | Data-parallel (pesquisa) | 🔵 | — | 6.2 |
| 6.5 | Scoped resources (GPU/arquivos/conexões) | 🟡 | lane compiler | `docs/scoped-resources-plan.md` (CONCLUÍDO 28/09 — `using (x = init, closer) { }` landou, `D-SCOPED-RESOURCES-GO`); **D5-B ✅ 19/09**: sem sintaxe nova — padrão `close()` + `try/finally` |
| 6.6 | Distribuído (FFI para MPI + orquestração Kof) | 🔵 | — | R3, 2.1 |
| 6.7 | Tooling: profiling HPC | 🔵 | — | 6.1 |

---

# Estágio 7 — BIOINFORMATICS

**Objetivo:** uma plataforma tipada para **pipelines científicos/genômicos**.
**Dependências:** Estágios 2, 4, 6.
**NÃO fazer:** transformar o Kof numa linguagem exclusiva de biologia;
reimplementar aligners/variant callers.

| # | Item | Status | Dono | Depende de |
|---|------|--------|------|------------|
| 7.1 | `kof-bio` (pacote oficial): formatos FASTA/FASTQ/VCF/BAM como records tipados | 🔵 | — | R3, R5 |
| 7.2 | Alinhamento/variantes via **FFI/CLI** (BLAST/htslib — não reimplementar) | 🔵 | — | 7.1, R3 |
| 7.3 | Pipelines genômicos (modelo `workflow` do Estágio 2 + checkpointing) | 🔵 | — | 2.1 |
| 7.4 | HPC (Estágio 6) | 🔵 | — | 6.1 |
| 7.5 | Automação de laboratório (`kof.http` REST + `kof.process` via FFI) | 🟡 | — | peças existem |

---

