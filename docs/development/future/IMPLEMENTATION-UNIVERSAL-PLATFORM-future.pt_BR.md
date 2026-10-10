[English](IMPLEMENTATION-UNIVERSAL-PLATFORM-future.md) | [Português](IMPLEMENTATION-UNIVERSAL-PLATFORM-future.pt_BR.md)

# IMPLEMENTATION-UNIVERSAL-PLATFORM — Fases Futuras (apenas plano, sem código)

> Este documento contém as Fases 4–7 do plano de plataforma universal. Pela regra dos três estados (AGENTS.md), estas são **apenas plano, sem código** e pertencem a `development/future/`. Não são trabalho de desenvolvimento atual.

# Estágio 4 — DATA (data engineering / science / ML)

**Objetivo:** uma camada científica **orquestrada** (não reimplementada).
**Dependências:** Estágios 1–3; R3 (FFI); Arrow como padrão de troca.
**NÃO fazer:** **não construir um framework de ML/NumPy em Kof** — Kof fornece
o *wrapper tipado + pipeline*, o *motor* fica fora.

| # | Item | Status | Dono | Depende de |
|---|------|--------|------|------------|
| 4.1 | `dataframe` tipado (lazy, colunar) | 🔵 | — | R3 |
| 4.2 | **Arrow/Parquet via FFI** (wrapper tipado) | 🔵 | — | R3 |
| 4.3 | Estatística/probabilidade (wrapper + FFI) | 🔵 | — | R3 |
| 4.4 | `kof.ml` — inferência via FFI (ONNX/libtorch); treino orquestrado | 🔵 | — | R3 |
| 4.5 | Visualização leve (SVG/`kof.ui` + FFI) | 🟡 | — | `kof.ui` existe; bindings de data-viz pendentes |
| 4.6 | Rastreamento de experimentos (leve, sobre `kof.db`/`kof.io`) | 🔵 | — | 3.4 |
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

