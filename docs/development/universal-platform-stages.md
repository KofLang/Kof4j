[English](universal-platform-stages.md) | [Português](universal-platform-stages.pt_BR.md)

last: 03/11/2026 — PROMOTED from `docs/development/future/` by the maintainer's direct order ("pega o universal platform de future"); Stage 4.6 (light experiment tracking) LANDED the same cycle with executed proof (`ExperimentsE2ETest` 2/2)
doing: Stages 4–7 are the CURRENT development plan — every PURE-KOF candidate is DONE (4.6 experiments, 4.3's descriptive half, 4.5's writer half, 5.9's detection core, 5.10's audit chain, 7.1's text readers + the VCF follow-up, 7.3's QC pipeline AND its workflow orchestration `libs/bio/Workflow.kf` 05/11 — all with E2E proof); the REMAINING pure-Kof candidates are the WIRING halves of 5.9 (an http request path feeding the sentinel) and 5.10 (the observability metrics export feeding the audit chain) — both surfaces measured present in the compiler; every FFI/Arrow/decision row stays blocked by its real dependency (R3 FFI, Arrow, a maintainer decision or the R12 stage order), recorded row-by-row below
next: the 5.9 wiring half (an `kof.http` request path feeding `Sentinel.addEvent`) is the next pure-Kof candidate — the `kof.http` surface is measured present (get/post/status/retry/insecure); then 5.10's observability→audit export; the FFI/Arrow/decision rows stay blocked — the row-by-row table records which
location: docs/development
state: under-development
owner: 192.168.100.14:0, flatt CLI, no opencode server on this host; port n/a

intent: universal-platform-stages-4-7

**Rule 6 gate — SATISFIED 03/11/2026.** The maintainer ordered directly: "pega o universal platform de future". That order IS the rule-6 decision for this plan (the same pattern as the TLS plan's promotion). The frozen contract it must respect is unchanged: no new core primitive, no new syntax, nothing frozen-semantics.

**Source:** `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (Stages 1–3 + 8, `UNDER DEVELOPMENT` since 17/09 under `D-UNIVERSAL`) split its Stages 4–7 into `future/` per the three-states rule. This document is that half, now promoted back to current work by the maintainer's order.

---

# IMPLEMENTATION-UNIVERSAL-PLATFORM — Stages 4–7 (DATA / SECURITY-expansion / SCIENTIFIC / BIO)

## Status measured at promotion (03/11/2026)

Each row is the plan's own item checked against the tree, not against the plan's prose.

| Item | Plan says | Measured today | Verdict |
|---|---|---|---|
| 4.1 typed `dataframe` | 🔵 needs R3 | no dataframe, no Arrow anywhere in `libs/`/compiler | BLOCKED — needs a new core primitive + Arrow |
| 4.2 Arrow/Parquet via FFI | 🔵 needs R3 | zero Arrow/Parquet code | BLOCKED — FFI dependency (R3) |
| 4.3 statistics via FFI | 🔵 needs R3 | `libs/vision` has histogram/equalization only | PARTIAL — a pure-Kof descriptive-statistics slice is expressible; the FFI distribution/linear-algebra half is not |
| 4.4 `kof.ml` inference | 🔵 needs R3 | nothing | BLOCKED — ONNX/libtorch FFI |
| 4.5 light visualization | 🟡 "kof.ui exists" | `kof.ui` exists as a COMPILER surface; **there is NOW an SVG writer** (`libs/svg/Svg.kf`, landed 03/11) | PARTIAL — the writer half LANDED (`SvgE2ETest` 2/2); data-viz bindings to a charting engine stay 🔵 (FFI/official package) |
| **4.6 experiment tracking** | 🔵 needs 3.4 | `kof.db` + `kof.io` both landed; the `DbLedger` pattern proves rows-over-db | **✅ DONE this cycle** |
| 4.7 pipeline profiling | 🔵 needs 4.1 | depends on the dataframe | BLOCKED by 4.1 |
| 5.1 Secret/KeyHandle | ✅ | landed 21/09 (`D-SECRETS`) | DONE |
| 5.2 `keys.*` | 🔵 needs 5.1 | only the `KeyHandle` surface from 5.1 | BLOCKED — needs a key-store decision (rule 6) |
| 5.3 asymmetric crypto FFI | 🟡 JWT RS/ES on JVM | JWT RS/ES landed; X.509/TLS pending | PARTIAL — the TLS half was landed by the TLS plan; X.509 parsing is FFI/primitive work |
| 5.4–5.6 PQC/hybrid/secure.channel | 🔵 needs R3 + `liboqs` | zero PQC code | BLOCKED — audited `liboqs` FFI (never homemade crypto) |
| 5.7 `libpcap` FFI | 🔵 needs R3 | nothing | BLOCKED — FFI |
| 5.8 forensics | 🔵 needs 5.7 | nothing | BLOCKED by 5.7 |
| 5.9 security automation | 🔵 needs Stage 2 | `kof.http` + `spawn` + `kof.log` exist; **the detection core is NOW in the tree** (`libs/sentinel/Sentinel.kf`, landed 04/11) | PARTIAL — the burst/alert core LANDED (`SentinelE2ETest` 2/2); the http/log wiring faces stay 🔵 follow-ups |
| 5.10 defensive monitoring | 🟡 pieces exist | observability + log + db all landed; **the audit chain is NOW in the tree** (`libs/audit/Audit.kf`, landed 04/11) | PARTIAL — the SHA-256 tamper-evident chain LANDED (`AuditE2ETest` 2/2); the observability export wiring stays 🔵 follow-up |
| 6.1 BLAS/LAPACK FFI | 🔵 needs R3 | nothing | BLOCKED — FFI |
| 6.2 SIMD research | 🔵 native lane | native lane is a LIVE lane | NOT MINE |
| 6.3 GPU | 🟡 Vulkan exists | Vulkan compute landed (`libs/game`); CUDA/OpenCL pending | PARTIAL — CUDA/OpenCL are FFI work |
| **6.5 scoped resources** | 🟡 compiler lane | `using` landed 28/09 (`D-SCOPED-RESOURCES-GO`), plan CONCLUDED | **DONE (elsewhere)** |
| 6.6 MPI | 🔵 needs R3 | nothing | BLOCKED — FFI |
| 6.7 HPC profiling | 🔵 needs 6.1 | nothing | BLOCKED by 6.1 |
| 7.1 `kof-bio` FASTA/FASTQ/VCF/BAM | 🔵 needs R3/R5 | **the FASTA/FASTQ readers are NOW in the tree** (`libs/bio/Bio.kf`, landed 04/11) | PARTIAL — the text half LANDED (`BioE2ETest` 2/2); **the VCF reader is NOW in the tree** (`libs/bio/Vcf.kf`, landed 04/11), BAM 🔵 binary FFI |
| 7.2 BLAST/htslib | 🔵 needs R3 | nothing | BLOCKED — FFI/CLI interop |
| 7.3 genomic pipelines | ✅ | `kof.workflow` landed; **the QC pipeline is NOW in the tree** (`libs/bio/Pipeline.kf`, landed 04/11) AND **its workflow orchestration TOO** (`libs/bio/Workflow.kf`, landed 05/11 — the real DAG with durable TSV stage artifacts) | DONE in pure Kof — `PipelineE2ETest` 2/2 + `PipelineWorkflowE2ETest` 2/2 (the durable ck-persistence hooks stay a host capability the caller may opt into) |
| 7.4 HPC | 🔵 needs 6.1 | nothing | BLOCKED by 6.1 |
| 7.5 lab automation | 🟡 pieces exist | `kof.http` exists; `kof.process` native face is `PROC001` | PARTIAL |

**The measured conclusion:** the plan's own dependency chain (Stages 1–3 → 4 → 6 → 7, and R3/FFI throughout) leaves only a narrow set of items implementable in pure Kof today. 4.6 was one of them and is DONE. The next candidates are 4.3 (descriptive statistics), 4.5 (SVG writer), 5.9, 5.10, 7.1 (text formats) and 7.3 — each expressible as a library over existing primitives, one per cycle.

---

# Stage 4 — DATA (data engineering / science / ML)

**Objective:** an **orchestrated** scientific layer (not reimplemented).
**Dependencies:** Stages 1–3; R3 (FFI); Arrow as the exchange standard.
**NOT to do:** **do not build an ML/NumPy framework in Kof** — Kof provides the
*typed wrapper + pipeline*, the *engine* stays outside.

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 4.1 | Typed `dataframe` (lazy, columnar) | 🔵 | — | R3 |
| 4.2 | **Arrow/Parquet via FFI** (typed wrapper) | 🔵 | — | R3 |
| 4.3 | Statistics/probability (wrapper + FFI) | 🟡 | `192.168.100.14:0` | **the descriptive half LANDED 03/11** — `libs/stats/Stats.kf` (pure Kof): `mean`/`median`/`minimum`/`maximum`/`pvariance` (population)/`stddev` (over `math.sqrt`, MATH001 honest on native cross)/`percentile` (linear interpolation; STATS001 empty, STATS002 out-of-range). Proof: `StatsE2ETest` **2/2** (JVM + Script) RED-first (`libs/stats not found` captured, then two real compiler faces surfaced: the COMP002 frame-crash on the multi-return shape — fixed single-exit — and SEM090's toInt truncation note). The probability-distribution half stays 🔵 (FFI/R3) |
| 4.4 | `kof.ml` — inference via FFI (ONNX/libtorch); orchestrated training | 🔵 | — | R3 |
| 4.5 | Light visualization (SVG/`kof.ui` + FFI) | 🟡 | `192.168.100.14:0` | **the WRITER half LANDED 03/11** — `libs/svg/Svg.kf` (pure Kof string building): `svgDocument`/`svgRect`/`svgLine`/`svgText` (XML escaping of `& < >`, `&` first) + `barChart` (max-scaled, INT-quantized geometry so the output text is byte-identical per target; empty series refuses SVG001). Proof: `SvgE2ETest` **2/2** (JVM + Script) RED-first (`libs/svg not found`), contains-faces on tags/escaping/deterministic geometry. The ENGINE-bindings half stays 🔵 (a charting engine is an FFI/official-package dependency) |
| 4.6 | Experiment tracking (light, over `kof.db`/`kof.io`) | ✅ | `192.168.100.14:0` | **LANDED 03/11** — `libs/experiments/Experiments.kf`: `initExperiments`, `startRun`/`finishRun`, `logMetric`/`logParam`, `metricAt`/`lastMetric`/`metricCount`, `exportRun` (JSON Lines over `kof.io`). Rows over `kof.db`; the reader decodes the raw JSON row with `json.decode<Row>` because the typed `db.query<Rec>` loses a LIB-defined record (`known-bugs` §727). Proof: `ExperimentsE2ETest` **2/2** (JVM + Script), RED-first with 4 real diagnostics (`null` not assignable, `integer` not a type, nullable receivers, `readText` returns `String?`) |
| 4.7 | Tooling: pipeline profiling | 🔵 | — | 4.1 |

---

# Stage 5 — SECURITY (expansion)

**Objective:** from "application security" (already strong) to **platform
security** (network, forensics, defensive) — plus a **modern cryptographic
layer + post-quantum** (vision §4.8.1).
**Absolute rule:** **never** homemade crypto — every new primitive (incl. PQC)
is FFI to an audited lib; identical API across targets; gap = diagnosis
(`SECN00x`/`SECPQ`), never a weak stub.
**Dependencies:** Stages 1–3; R3 (FFI).
**NOT to do:** reimplement audited crypto stacks; offensive work without
legitimate/controlled context; defend *first*.

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 5.1 | S2 — `Secret` type + `KeyHandle` (forced redaction) | ✅ | security lane | R3; **LANDED 21/09 (`D-SECRETS`)**: `secrets-plan.md` closed and moved to `docs/architecture/`, all faces landed (`Secret`, P2 redaction, `KeyHandle`), incremental with proof |
| 5.2 | S3 — `keys.*` (generate/derive/rotate/store) | 🔵 | security lane | 5.1; the store half needs a maintainer decision (rule 6) |
| 5.3 | S4 — asymmetric crypto (RSA/ECC/X.509/TLS) via FFI | 🟡 | security lane | RS/ES JWT on JVM; **TLS landed by `docs/tls-hardening.md`**; X.509 parsing pending |
| 5.4 | S5 — **PQC** hybrid (ML-KEM-768 + ML-DSA-65 + HKDF + AES-256-GCM) via `liboqs` | 🔵 | security lane | R3; gap code `SECPQ` |
| 5.5 | S6 — hybrid KEM+KDF+AEAD | 🔵 | security lane | 5.4 |
| 5.6 | S7 — `secure.channel` (KEM+KDF+AEAD+auth+replay) | 🔵 | security lane | 5.5 |
| 5.7 | `kof.net` / packet parsing (FFI to `libpcap`) | 🔵 | — | R3 |
| 5.8 | Forensics (FFI to parse libs + Kof pipelines) | 🔵 | — | 5.7, Stage 2 |
| 5.9 | Security automation / threat-intel (`kof.http` + `spawn`/`channel` + `kof.log`) | 🟡 | `192.168.100.14:0` | **the DETECTION CORE LANDED 04/11** — `libs/sentinel/Sentinel.kf` (pure Kof, class + record + List): an event ring with window pruning (`addEvent`), per-(source,kind) counting (`count`), the burst rule (`bursting`, count >= limit) and a JSON alert emitter (`alert` returns `String?`; the caller narrows and appends with the existing `kof.io` writers). Defensive only (detect + report, never contact/block). Proof: `SentinelE2ETest` **2/2** (JVM + Script) RED-first (`libs/sentinel not found`), faces on burst/no-burst/other-source/prune/other-kind/alert-JSON. The WIRING faces (an `kof.http` middleware feeding `addEvent`, a log-ingestion pump) stay 🔵 as follow-ups |
| 5.10 | Defensive (monitoring/detection/audit over `kof.observability` + `kof.log` + `kof.db`) | 🟡 | `192.168.100.14:0` | pieces exist (1.6); **the AUDIT CHAIN LANDED 04/11** — `libs/audit/Audit.kf` (pure Kof over the audited `crypto.sha256`): an append-only chain (`digest = sha256(prev|event|at)`, genesis root) + the pure `verifyChain(entries): Bool` (every digest recomputed, every link checked — a deleted/reordered/edited/spliced entry breaks verification). NOT homemade crypto: the digest is the stdlib primitive; the library is the CHAIN. Proof: `AuditE2ETest` **2/2** (JVM + Script) RED-first (`libs/audit not found`), faces on intact-true/broken-link-false/swapped-digest-false. The kof.observability EXPORT wiring stays 🔵 as a follow-up |

---

# Stage 6 — SCIENTIFIC COMPUTING (numeric / HPC)

**Objective:** Kof as a **typed scientific orchestration language** + numeric
zone via FFI.
**Dependencies:** Stages 1–4; R3 (FFI); GC mark-sweep (1.2).
**NOT to do:** reimplement BLAS/LAPACK/NumPy; ownership/borrowing in the core
(the non-GC zone is via FFI to C/Rust).

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 6.1 | Linear algebra via **FFI to BLAS/LAPACK** (wrapper) | 🔵 | — | R3 |
| 6.2 | SIMD/vectorization (Native — research) | 🔵 | native lane | 1.2 — **live lane, not this plan's** |
| 6.3 | GPU — Vulkan via FFI (exists); CUDA/OpenCL via FFI | 🟡 | — | Vulkan compute exists; CUDA/OpenCL pending |
| 6.4 | Data-parallel (research) | 🔵 | — | 6.2 |
| 6.5 | Scoped resources (GPU/files/connections) | ✅ | compiler lane | `docs/scoped-resources-plan.md` (CONCLUDED 28/09 — `using (x = init, closer) { }` landed, `D-SCOPED-RESOURCES-GO`); **D5-B ✅ 19/09**: no new syntax — `close()` + `try/finally` pattern |
| 6.6 | Distributed (FFI to MPI + Kof orchestration) | 🔵 | — | R3, 2.1 |
| 6.7 | Tooling: HPC profiling | 🔵 | — | 6.1 |

---

# Stage 7 — BIOINFORMATICS

**Objective:** a typed platform for **scientific/genomic pipelines**.
**Dependencies:** Stages 2, 4, 6.
**NOT to do:** turn Kof into an exclusive biology language; reimplement
aligners/variant callers.

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 7.1 | `kof-bio` (official package): FASTA/FASTQ/VCF/BAM as typed records | 🟡 | `192.168.100.14:0` | **the TEXT half LANDED 04/11** — `libs/bio/Bio.kf` (pure Kof): typed `FastaRecord(header, sequence)` / `FastqRecord(header, sequence, quality)` parsed from text, `gcContent` (the classic computing face), honest refusals (BIO001 an empty FASTA sequence, BIO002 a quality-length mismatch). Proof: `BioE2ETest` **2/2** (JVM + Script) RED-first (`libs/bio not found`), faces on two-FASTA-records/GC/one-FASTQ-record/both-refusals. **the VCF READER LANDED 04/11** — `libs/bio/Vcf.kf`: typed `VcfRecord(chrom, pos, id, ref, alt, qual)` from the 8-column rows, `##`/`#CHROM` skipped, a missing QUAL (`.`) is `Double?` null, <8 columns refuses BIO003; proof `VcfE2ETest` **2/2** JVM+Script; the tab from the `"\t"` escape — the `(char) n` Java cast is REFUSED (PARSE041); BAM 🔵 (binary FFI) |
| 7.2 | Alignment/variants via **FFI/CLI** (BLAST/htslib — do not reimplement) | 🔵 | — | 7.1, R3 |
| 7.3 | Genomic pipelines (Stage 2 `workflow` model + checkpointing) | ✅ | `192.168.100.14:0` | **the QC PIPELINE LANDED 04/11** — `libs/bio/Pipeline.kf` (pure Kof): `sequenceStats(records)` (per-record header/length/gc), `qcFilter(stats, minLen, minGc)` (the AND-threshold step) and `qcReport(stats)` (count/meanLength/meanGc; empty refuses QC001) — the stats → filter → report shape over the 7.1 readers. Proof: `PipelineE2ETest` **2/2** (JVM + Script) RED-first (SEM015 undefined functions; a Kof lesson: the import is PER-FILE — `import bio.Pipeline`, not just `bio.Bio`). **the WORKFLOW ORCHESTRATION LANDED 05/11** — `libs/bio/Workflow.kf`: `qcWorkflow(workDir, records, minLen, minGc)` builds the QC pipeline as a REAL `kof.workflow` DAG (`job`/`after`/`run` → `KofWfReport`) whose stages carry their data through durable TSV artifacts in the caller's work directory (the measured carrier: `File.writeText`/`readText` + `split` + `String.toInt/toDouble` — no JSON round-trip of a lib record, which the typed decoders lose per §727); an empty record list refuses QC001 at BUILD time, an EMPTY-FILTERED outcome is a legitimate `count=0` result (the orchestration layer records it; the pipeline layer's qcReport keeps refusing to fabricate), and a body throw (BIO001 on an empty sequence) lands the job in `failed` with its dependents `skipped` — the host's run() catches body throws. Proof: `PipelineWorkflowE2ETest` **2/2** (JVM + Script) RED-first (PKG006 `import 'bio.Workflow' not found`); the lib-file `import kof.workflow` injection measured WORKING (no double-injection clash with the caller's import). The pure-Kof surface of the row is DONE (the durable ck-persistence hooks stay a host capability the caller may opt into) |
| 7.4 | HPC (Stage 6) | 🔵 | — | 6.1 |
| 7.5 | Lab automation (`kof.http` REST + `kof.process` via FFI) | 🟡 | — | pieces exist |

---

## How to finish

1. Take the next CANDIDATE row (measured above), one per cycle.
2. Implement it as a pure-Kof library under `libs/<domain>/` — never a new
   compiler primitive, never a stub, never homemade crypto.
3. RED-first proof: an `*E2ETest` that compiles + runs the library on JVM and
   Script (and Native where the surface allows), with the failure captured
   before the code exists.
4. Docs EN+PT in the same commit; update the row above to `✅` with the proof.
5. Any item that needs a new core primitive, an FFI dependency (R3), Arrow, or
   a maintainer decision stays 🔵 with the reason in this table.