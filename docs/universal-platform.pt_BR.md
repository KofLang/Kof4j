[English](universal-platform.md) | [Português](universal-platform.pt_BR.md)

last: 05/11/2026 — CONCLUÍDO EM KOF PURO e PROMOVIDO para `docs/universal-platform.md` (+EN) pela autorização da mantenedora; a ÚLTIMA fatia (o middleware HTTP da 5.9 `libs/sentinel/Http.kf`) pousou 2/2 JVM+Script depois do conserto de emissor da #1080 (a tipagem do lambda pelo tipo declarado, do PR #1092) e da ponte do interpretador `kof_web_use`; cada pouso que este plano produziu carrega sua E2E
doing: as Fases 4–7 são o plano de desenvolvimento CORRENTE — TODA face pure-Kof das Fases 4–7 está FEITA com prova E2E (4.6 experiments, a metade descritiva da 4.3, a metade escritora da 4.5, o núcleo de detecção da 5.9 + a bomba de ingestão de log `libs/sentinel/LogPump.kf` + a FIAÇÃO POR MIDDLEWARE HTTP `libs/sentinel/Http.kf` 05/11 (destravada pelo conserto de emissor da #1080 + a ponte `kof_web_use` do interpretador), a cadeia de auditoria da 5.10 + o seu export de observability `libs/audit/Observability.kf`, os leitores textuais da 7.1 + o follow-up VCF, o pipeline de QC da 7.3 + a sua orquestração workflow `libs/bio/Workflow.kf`); toda linha restante precisa de R3 (FFI), Arrow, uma decisão da mantenedora ou a ordem de estágios do R12 (registrada linha a linha abaixo). **O PLANO ESTÁ CONCLUÍDO EM KOF PURO E PROMOVIDO para `docs/universal-platform.md` 05/11 pela autorização da mantenedora ("eu autorizo a concluir tudo que ta aberto. finaliza todos os planos e gaps")**
next: nada em Kof puro — as linhas restantes do plano promovido são os follow-ups registrados (R3/Arrow/decisões/a ordem de estágios do R12); o re-gatilho age sobre uma designação da mantenedora de uma linha FFI (libpcap+Vulkan estão instalados neste host), uma decisão, ou trabalho novo da triagem
location: docs
state: implemented
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
| 4.5 visualização leve | 🟡 "kof.ui existe" | `kof.ui` existe como superfície do COMPILADOR; **AGORA há escritor SVG** (`libs/svg/Svg.kf`, pousou 03/11) | PARCIAL — a metade escritora POUSOU (`SvgE2ETest` 2/2); bindings de gráficos a um engine seguem 🔵 (FFI/pacote oficial) |
| **4.6 registro de experimentos** | 🔵 precisa 3.4 | `kof.db` + `kof.io` pousaram; o padrão `DbLedger` prova linhas-sobre-db | **✅ FEITO neste ciclo** |
| 4.7 profiling de pipeline | 🔵 precisa 4.1 | depende do dataframe | BLOQUEADO pela 4.1 |
| 5.1 Secret/KeyHandle | ✅ | pousou 21/09 (`D-SECRETS`) | FEITO |
| 5.2 `keys.*` | 🔵 precisa 5.1 | só a superfície `KeyHandle` da 5.1 | BLOQUEADO — precisa de decisão sobre o key-store (regra 6) |
| 5.3 cripto assimétrica FFI | 🟡 JWT RS/ES na JVM | JWT RS/ES pousou; X.509/TLS pendentes | PARCIAL — a metade TLS POUSOU pelo plano TLS; o parse de X.509 é trabalho de FFI/primitivo |
| 5.4–5.6 PQC/híbrido/secure.channel | 🔵 precisa R3 + `liboqs` | zero código PQC | BLOQUEADO — FFI de `liboqs` auditado (nunca cripto caseira) |
| 5.7 FFI `libpcap` | 🔵 precisa R3 | nada | BLOQUEADO — FFI |
| 5.8 forense | 🔵 precisa 5.7 | nada | BLOQUEADO pela 5.7 |
| 5.9 automação de segurança | 🔵 precisa Fase 2 | `kof.http` + `spawn` + `kof.log` existem; **o núcleo de detecção está AGORA na árvore** (`libs/sentinel/Sentinel.kf`, pousou 04/11) | PARCIAL — o núcleo de explosão/alerta POUSOU (`SentinelE2ETest` 2/2); **a FIAÇÃO POR MIDDLEWARE HTTP POUSOU 05/11** — `libs/sentinel/Http.kf`: `sentinelHttp(sent)` devolve o middleware observador `() -> String?` (um `addEvent(path(), "request", time.now())` por requisição, devolve null — nunca bloqueia, nunca muta); prova `SentinelHttpE2ETest` **2/2** (JVM + Script) depois do conserto de emissor da #1080 (a tipagem do lambda pelo tipo declarado, do PR #1092) + a ponte do interpretador estendida a `kof_web_use` (o middleware ficava um KofObj cru e o dispatch dava 500) → a bomba de log (`LogPumpE2ETest` 2/2) |
| 5.10 monitoração defensiva | 🟡 peças existem | observabilidade + log + db pousados; **a cadeia de auditoria está AGORA na árvore** (`libs/audit/Audit.kf`, pousou 04/11) | FEITA em Kof puro — a cadeia à prova de adulteração SHA-256 POUSOU (`AuditE2ETest` 2/2) E o export de observability TAMBÉM (`libs/audit/Observability.kf`, pousou 05/11 — `AuditObservabilityE2ETest` 2/2; o snapshot de métricas entra na história à prova de adulteração) |
| 6.1 BLAS/LAPACK FFI | 🔵 precisa R3 | nada | BLOQUEADO — FFI |
| 6.2 pesquisa SIMD | 🔵 lane nativa | a lane nativa é uma lane VIVA | NÃO É MINHA |
| 6.3 GPU | 🟡 Vulkan existe | compute Vulkan pousou (`libs/game`); CUDA/OpenCL pendentes | PARCIAL — CUDA/OpenCL são trabalho de FFI |
| **6.5 recursos escopados** | 🟡 lane compilador | `using` pousou 28/09 (`D-SCOPED-RESOURCES-GO`), plano CONCLUÍDO | **FEITO (em outro lugar)** |
| 6.6 MPI | 🔵 precisa R3 | nada | BLOQUEADO — FFI |
| 6.7 profiling HPC | 🔵 precisa 6.1 | nada | BLOQUEADO pela 6.1 |
| 7.1 `kof-bio` FASTA/FASTQ/VCF/BAM | 🔵 precisa R3/R5 | **os leitores FASTA/FASTQ estão AGORA na árvore** (`libs/bio/Bio.kf`, pousou 04/11) | PARCIAL — a metade textual POUSOU (`BioE2ETest` 2/2); **o leitor VCF está AGORA na árvore** (`libs/bio/Vcf.kf`, pousou 04/11), BAM 🔵 FFI binária |
| 7.2 BLAST/htslib | 🔵 precisa R3 | nada | BLOQUEADO — FFI/CLI |
| 7.3 pipelines genômicos | ✅ | `kof.workflow` pousou; **o pipeline de QC está AGORA na árvore** (`libs/bio/Pipeline.kf`, pousou 04/11) E **a orquestração workflow TAMBÉM** (`libs/bio/Workflow.kf`, pousou 05/11 — a DAG real com artefatos TSV duráveis por stage) | FEITA em Kof puro — `PipelineE2ETest` 2/2 + `PipelineWorkflowE2ETest` 2/2 (os hooks de ck-persistência durável seguem capacidade do host, opt-in do chamador) |
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
| 4.5 | Visualização leve (SVG/`kof.ui` + FFI) | 🟡 | `192.168.100.14:0` | **a metade ESCRITORA POUSOU 03/11** — `libs/svg/Svg.kf` (construção de String em Kof puro): `svgDocument`/`svgRect`/`svgLine`/`svgText` (escape XML de `& < >`, `&` primeiro) + `barChart` (escala pelo máximo, geometria QUANTIZADA em Int para o texto ser byte-idêntico por alvo; série vazia recusa SVG001). Prova: `SvgE2ETest` **2/2** (JVM + Script) RED-first (`libs/svg not found`). A metade de bindings de ENGINE segue 🔵 (um engine de gráficos é dependência de FFI/pacote oficial) |
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
| 5.9 | Automação de segurança / threat-intel (`kof.http` + `spawn`/`channel` + `kof.log`) | 🟡 | `192.168.100.14:0` | **o NÚCLEO DE DETECÇÃO POUSOU 04/11** — `libs/sentinel/Sentinel.kf` (Kof puro, classe + record + List): um anel de eventos com poda por janela (`addEvent`), contagem por (fonte, tipo) (`count`), a regra de explosão (`bursting`, contagem >= limite) e um emissor de alerta JSON (`alert` devolve `String?`; o chamador estreita e anexa com os escritores `kof.io` existentes). Defensivo apenas (detectar + reportar, nunca contactar/bloquear). Prova: `SentinelE2ETest` **2/2** (JVM + Script) RED-first (`libs/sentinel not found`), faces de explosão/não-explosão/outra-fonte/poda/outro-tipo/alerta-JSON. **a FIAÇÃO POR MIDDLEWARE HTTP POUSOU 05/11** — `libs/sentinel/Http.kf`: `sentinelHttp(sent)` devolve o middleware observador `() -> String?` (um `addEvent(path(), "request", time.now())` por requisição, devolve null — nunca bloqueia, nunca muta); prova `SentinelHttpE2ETest` **2/2** (JVM + Script) depois do conserto de emissor da #1080 (a tipagem do lambda pelo tipo declarado, do PR #1092) + a ponte do interpretador estendida a `kof_web_use` (o middleware ficava um KofObj cru e o dispatch dava 500) → a bomba de log (`LogPumpE2ETest` 2/2) |
| 5.10 | Defensiva (monitoramento/detecção/auditoria sobre `kof.observability` + `kof.log` + `kof.db`) | 🟡 | `192.168.100.14:0` | peças existem (1.6); **a CADEIA DE AUDITORIA POUSOU 04/11** — `libs/audit/Audit.kf` (Kof puro sobre o `crypto.sha256` auditado): uma cadeia append-only (`digest = sha256(prev|event|at)`, raiz genesis) + o `verifyChain(entries): Bool` puro (cada digest recomputado, cada elo checado — uma entrada deletada/reordenada/editada/emendada quebra a verificação). NÃO é cripto caseira: o digest é o primitivo da stdlib; a biblioteca É a CADEIA. Prova: `AuditE2ETest` **2/2** (JVM + Script) RED-first (`libs/audit not found`), faces de íntegra-true/elo-quebrado-false/digest-trocado-false. **o EXPORT DE OBSERVABILITY POUSOU 05/11** — `libs/audit/Observability.kf`: `metricsToEntries(text, startSeq, prevDigest, atMillis)` (a face pura de parse+vínculo — cada linha de amostra Prometheus de um snapshot `observability.metrics()` vira UMA entrada cujo evento carrega `obs:<nome>=<valor>` VERBATIM, sem conversão numérica; linhas `#` e vazias puladas; amostra malformada recusa OBS001) + `exportMetrics(trail, atMillis)` (a face escritora — anexa o snapshot inteiro a um AuditTrail vivo, devolve a contagem). O estado das métricas entra na história à prova de adulteração: o `verifyChain` vale sobre o trail exportado e uma entrada EDITADA o quebra. Prova: `AuditObservabilityE2ETest` **2/2** (JVM + Script) RED-first (PKG006 `import 'audit.Observability' not found`) |

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
| 7.1 | `kof-bio` (pacote oficial): FASTA/FASTQ/VCF/BAM como records tipados | 🟡 | `192.168.100.14:0` | **a metade TEXTUAL POUSOU 04/11** — `libs/bio/Bio.kf` (Kof puro): `FastaRecord(header, sequence)` / `FastqRecord(header, sequence, quality)` tipados parseados de texto, `gcContent` (a face clássica de computação), recusas honestas (BIO001 sequência FASTA vazia, BIO002 comprimento de qualidade divergente). Prova: `BioE2ETest` **2/2** (JVM + Script) RED-first (`libs/bio not found`), faces de dois-registros-FASTA/GC/um-registro-FASTQ/ambas-as-recusas. **o LEITOR VCF POUSOU 04/11** — `libs/bio/Vcf.kf`: `VcfRecord(chrom, pos, id, ref, alt, qual)` tipado a partir das linhas de 8 colunas, `##`/`#CHROM` pulados, um QUAL ausente (`.`) é `Double?` null, <8 colunas recusa BIO003; prova `VcfE2ETest` **2/2** JVM+Script; o tab pelo escape `"\t"` — o cast Java `(char) n` é RECUSADO (PARSE041); BAM 🔵 (FFI binária) |
| 7.2 | Alinhamento/variantes via **FFI/CLI** (BLAST/htslib — não reimplementar) | 🔵 | — | 7.1, R3 |
| 7.3 | Pipelines genômicos (modelo `workflow` da Fase 2 + checkpointing) | ✅ | `192.168.100.14:0` | **o PIPELINE DE QC POUSOU 04/11** — `libs/bio/Pipeline.kf` (Kof puro): `sequenceStats(records)` (header/comprimento/gc por registro), `qcFilter(stats, minLen, minGc)` (o passo com os dois limiares em E) e `qcReport(stats)` (contagem/meanLength/meanGc; vazio recusa QC001) — a forma stats → filtro → relatório sobre os leitores da 7.1. Prova: `PipelineE2ETest` **2/2** (JVM + Script) RED-first (SEM015 funções indefinidas; uma lição de Kof: o import é POR ARQUIVO — `import bio.Pipeline`, não só `bio.Bio`). **a ORQUESTRAÇÃO WORKFLOW POUSOU 05/11** — `libs/bio/Workflow.kf`: `qcWorkflow(workDir, records, minLen, minGc)` constrói o pipeline QC como uma DAG `kof.workflow` REAL (`job`/`after`/`run` → `KofWfReport`) cujos stages carregam os dados por artefatos TSV duráveis no diretório de trabalho do chamador (o carrier medido: `File.writeText`/`readText` + `split` + `String.toInt/toDouble` — sem round-trip JSON de record de lib, que os decoders tipados perdem por §727); lista de records vazia recusa QC001 no BUILD, desfecho de FILTRO-VAZIO é resultado legítimo `count=0` (a camada de orquestração registra; o qcReport da camada pipeline segue recusando fabricar), e um throw no corpo (BIO001 em sequência vazia) põe o job em `failed` com os dependentes `skipped` — o run() do host captura throws de corpo. Prova: `PipelineWorkflowE2ETest` **2/2** (JVM + Script) RED-first (PKG006 `import 'bio.Workflow' not found`); a injeção do `import kof.workflow` por arquivo de LIB medida FUNCIONANDO (sem conflito de injeção dupla com o import do chamador). A superfície pure-Kof da linha está FEITA (os hooks de ck-persistência durável seguem capacidade do host, opt-in do chamador) |
| 7.4 | HPC (Estágio 6) | 🔵 | — | 6.1 |
| 7.5 | Automação de laboratório (`kof.http` REST + `kof.process` via FFI) | 🟡 | — | peças existem |

---

