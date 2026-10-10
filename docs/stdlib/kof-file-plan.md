[English](kof-file-plan.md) | [Português](kof-file-plan.pt_BR.md)

# Strategic plan — `kof.file`

> **State (28/09): CONCLUDED — promoted `future/` → `docs/development/` by `D-KOF-FILE-GO` + `D-FUTURE-PROMOTION` (maintainer), implemented library-first (`D-KOF-FIRST-IMPL`), and moved to `docs/stdlib/` (3-state rule).** Promoted scope landed: **Phase 1** already exists as `kof.io`; **Phase 2 streaming** (`FileStream`/`TextStream`/`CsvReader`+`CsvWriter`/`JsonLinesReader`+`JsonLinesWriter`/`XmlReader` incl. namespaces); **Phase 3 configuration** (`Ini`/`Toml`/`Yaml`) — all pure-Kof `libs/file/`, goldens on JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001`; §537/§538 catalogued. **Deferred (NOT current work):** Phase 4 documents (Markdown/HTML/PDF), Phase 5 binaries/containers (images/archives) and Phase 6 evolution — heavy codecs are official packages (R1/R9); they need a new `D-FUTURE-PROMOTION` to open.** Re-scoped on promotion: **Phase 1 (File/Path/Text/Binary) already exists as `kof.io`** (`docs/stdlib/IO.md` — `File`/`Path`/`Directory` + `readText`/`writeText`/`appendText`/`readBytes`/`writeBytes`/`readRange`/`size`/`delete`/`copyTo`/`moveTo`/`modifiedTime`/`isSymlink`); the only open Phase-1 gap is **Streaming**. **Slice 1 LANDED 28/09:** pure-Kof library `libs/file/` (`FileStream` chunked reader over `kof.io.readRange` + `copyStream` constant-memory copy), JVM-proven by `FileLibraryE2ETest` 2/2 — no new syntax, no compiler change (library-first, `D-KOF-FIRST-IMPL`). **Slice 2 LANDED 28/09:** measured on JVM **+ Native x86-64 + riscv64/aarch64 under qemu + Script** against the same golden; **JS`readRange`/`copyTo`/`moveTo`/`modifiedTime`/`isSymlink` are an honest compile-time gap `IOJS001`** (the GraalJS runtime exports no binding — before, the program died at runtime with `SyntaxError`) — `FileLibraryE2ETest` 7/7. **Slice 2.1 LANDED 28/09:** `TextStream` — streaming **UTF-8 text/line reader** over the same chunk model, decoding multi-byte sequences split across chunk boundaries (a sequence never emitted as broken bytes; an unterminated one at EOF → U+FFFD), accepting `\n` and `\r\n`, memory bounded by one chunk plus the current line; golden measured on JVM + Native x86-64 + riscv64 (qemu) + Script, plus a JVM/Script astral (non-BMP) round-trip — **non-BMP is a measured Native divergence** (§537, UTF-8 storage has no WTF-8 surrogate representation). **Slice 2.2 LANDED 28/09:** `CsvReader` — streaming **CSV/TSV** records (RFC 4180-style: quoted fields with embedded delimiters/newlines, `""` escapes, `\n`/`\r\n`, TSV via tab delimiter) as a character-level state machine over `TextStream`, bounded by one chunk plus the current record; golden measured on JVM + Native x86-64 + riscv64 (qemu) + Script — `CsvReaderE2ETest` 15/15 (reader and writer). `CsvWriter` (slice 2.2, same commit) completes the pair: the first `writeRow` truncates then the rest append, quoting a field only when it holds the delimiter/quote/newline/CR (quotes doubled), and the reader round-trips the writer's output. **Slice 2.3 LANDED 28/09:** `JsonLinesReader`/`JsonLinesWriter` (`libs/file/JsonLines.kf`) — the `kof.json` ↔ stream seam: one JSON document per non-blank line over `TextStream`, the caller decoding with a concrete `json.decode<T>`; the reader stays untyped because `json.decode<T>` over an OPEN type parameter is the compiler defect **§538** (compiles clean, emits `checkcast T`/`kof_json_decode_T`, failure masked as the JavaFX launcher message). Golden measured on JVM + Native x86-64 + riscv64 (qemu) + Script (records decode on JVM; riscv64 decodes arrays — `kof_json_find_value` is absent from the cross runtime), JS `IOJS001` — `JsonLinesE2ETest` 8/8. **Slice 2.4 LANDED 28/09:** `XmlReader` (`libs/file/Xml.kf`) — a non-validating pull-style streaming XML reader over `TextStream` (`next(): XmlEvent?` with kinds `start`/`end`/`empty`/`text`, attributes, predefined + numeric entities, CDATA, comments, DOCTYPE with internal subset, validated element stack); documented subset (namespace prefixes preserved verbatim, DOCTYPE entities not resolved, CDATA not decoded), malformed input throws. Golden measured on JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001` — `XmlReaderE2ETest` 8/8. **Slice 2.5 LANDED 28/09:** namespace resolution — scoped `xmlns`/`xmlns:prefix` declarations, `localName`/`namespaceUri`/`attributeNamespaces` on `XmlEvent` (`name` stays verbatim, additive over 2.4), predefined `xml`/`xmlns` enforced, unbound prefix throws; golden + two error cases on JVM + Native x86-64/riscv64 + Script — `XmlReaderE2ETest` 14/14. **Slice 3.1 LANDED 28/09:** `Ini` (`libs/file/Ini.kf`) — Phase 3 config reader: sections/global keys, `=`/`:` separators, full-line `;`/`#` comments, quoted values, last-wins duplicates, and explicit errors for malformed input; ordered entry list queried by `get`/`has`/`keysOf`. Golden measured on JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001` — `IniReaderE2ETest` 7/7. **Slice 3.2 LANDED 28/09:** `Toml` (`libs/file/Toml.kf`) — Phase 3 config reader: comments, dotted/quoted keys, `[table]` headers, basic/literal strings with escapes, integer (`_` separators)/float/boolean and single-line scalar arrays; tables are flattened to a canonical dotted key (no nested maps) with typed accessors `getString`/`getInt`/`getDouble`/`getBool`/`getArray`/`kindOf`; duplicate/malformed lines and the unsupported forms (`[[array of tables]]`, inline tables, nested arrays) throw explicit `String`s. Golden measured on JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001` — `TomlReaderE2ETest` 11/11. **Slice 3.3 LANDED 28/09:** `Yaml` (`libs/file/Yaml.kf`) — Phase 3 config reader for block mappings and block sequences of scalars: comments, space-indent nesting, plain/single/double-quoted scalars with escapes, boolean/null (`null`/`~`)/integer/float, indexed sequences (`key.0`…), flattened to a canonical dotted key with typed accessors; duplicate/tab-indent/malformed lines and the unsupported forms (block scalars, flow collections, sequences of mappings, nested sequences, anchors/tags) throw explicit `String`s. Golden measured on JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001` — `YamlReaderE2ETest` 11/11. **Phase 3 configuration (INI + TOML + YAML) is complete.** R1 boundary gate — heavy codecs (PDF, images, archives) belong to the **official-packages** layer, not the base stdlib (`scripts/check_stdlib_boundary.sh` + `scripts/stdlib_boundary.txt`); interop-first R9 — ZXing/PDFBox/imageio/JCA, never reimplement. The `let` in the examples below is a fake idiom
> (`training/anti-patterns/fake-idioms.md`); real syntax is `var`/`val`.

## Real state (28/09)

| Face | State | Where |
|------|-------|-------|
| File / Path / Text / Binary | **EXISTS as `kof.io`** (not `kof.file`) | `docs/stdlib/IO.md`; `KofIo.java`; `JvmRuntimeIo.java` |
| Streaming (chunked read + constant-memory copy) | **SLICE 1 LANDED** (JVM-proven) | `libs/file/FileStream.kf`; `FileLibraryE2ETest` 2/2 |
| Streaming on Native | **SLICE 2 MEASURED — golden on x86-64 + riscv64/aarch64** (qemu) | `FileLibraryE2ETest.streamsOnNative*` |
| Streaming on JS | **SLICE 2 — honest compile-time gap `IOJS001`** (no JS host binding; was a runtime `SyntaxError`) | `ExpressionBuiltinInstanceCalls.lowerIo`; `DomainGapCodesTest.ioReadRangeOnJsIsIojs001` |
| Streaming on Script | **SLICE 2 MEASURED — golden** (interpreter has the real `readRange`) | `FileLibraryE2ETest.streamsOnScript` |
| Streaming text (UTF-8 lines) | **SLICE 2.1 LANDED** (JVM + Native x86-64/riscv64 + Script; astral = Non-BMP Native divergence §537) | `libs/file/TextStream.kf`; `FileLibraryE2ETest.textStream*` |
| CSV / TSV (streaming records) | **SLICE 2.2 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Csv.kf`; `CsvReaderE2ETest` 15/15 (reader + writer) |
| JSON bridge | **SLICE 2.3 LANDED** as JSON Lines/NDJSON (`libs/file/JsonLines.kf`); `kof.json` `encode`/`decode` reused, typed flow blocked by §538 | `JsonLinesE2ETest` 8/8 |
| XML (streaming pull + namespaces) | **SLICES 2.4/2.5 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Xml.kf`; `XmlReaderE2ETest` 14/14 |
| INI (config) | **SLICE 3.1 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Ini.kf`; `IniReaderE2ETest` 7/7 |
| TOML (config) | **SLICE 3.2 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Toml.kf`; `TomlReaderE2ETest` 11/11 |
| YAML (config) | **SLICE 3.3 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Yaml.kf`; `YamlReaderE2ETest` 11/11 |
| documents / archives | **DEFERRED** — not promoted; heavy codecs = official packages (R1/R9) or a new `D-FUTURE-PROMOTION` | — |

## How it was finished (concluded 28/09)

1. Streaming slice 2 — **LANDED 28/09**: `FileStream` golden on JVM + Native x86-64 + riscv64/aarch64 (qemu) + Script; JS refuses at compile time with `IOJS001` (host has no partial-read primitive — never a whole-file fallback).
2. Streaming text slice 2.1 — **LANDED 28/09**: `TextStream` UTF-8 line reader over `FileStream` (cross-chunk multi-byte reassembly, `\n`/`\r\n`, U+FFFD on unterminated tail).
3. CSV/TSV slice 2.2 — **LANDED 28/09**: `CsvReader` character-level record parser over `TextStream` (quotes with embedded delimiters/newlines, `""` escapes, TSV tab) + `CsvWriter` inverse encoder (first write truncates, then appends; quotes only when needed) round-tripping through the reader. **Slice 2.3 LANDED 28/09:** `JsonLinesReader`/`JsonLinesWriter` — the `kof.json` ↔ stream seam (one concrete-typed document per non-blank line); typed streaming inside the library is blocked by §538, so the seam is untyped by design. **Slice 2.4 LANDED 28/09:** `XmlReader` — non-validating pull reader (documented subset: elements/attributes/text/empty, predefined + numeric entities, CDATA, comments, DOCTYPE, validated stack; namespace prefixes preserved verbatim), malformed input throws. **Slice 2.5 LANDED 28/09:** namespace resolution (scoped declarations, `localName`/`namespaceUri`/`attributeNamespaces`, unbound-prefix error; `name` verbatim, additive). **Slice 3.1 LANDED 28/09:** `Ini` — Phase 3 config reader (sections/global, `=`/`:` separators, `;`/`#` comments, quoted values, last-wins duplicates, explicit malformed-line errors), all-target golden. **Slice 3.2 LANDED 28/09:** `Toml` — Phase 3 config reader (comments, dotted/quoted keys, `[table]` headers, strings/escapes, integer/float/boolean, single-line scalar arrays; tables flattened to a canonical dotted key with typed accessors; explicit diagnostics for duplicate/malformed and unsupported forms), all-target golden + error cases. **Slice 3.3 LANDED 28/09:** `Yaml` — block mappings + scalar sequences (space indent, plain/quoted scalars, escapes, boolean/null, integer/float, indexed sequences; flattened dotted keys with typed accessors; explicit diagnostics). **Phase 3 configuration complete (INI + TOML + YAML).** Concluded on 28/09; documents (Markdown/HTML/PDF) and binaries/containers are deferred (official packages R1/R9 or a new promotion).
4. Deferred — documents (Markdown/HTML/PDF), binaries/containers and evolution; each needs its own promoted slice; heavy codecs as official packages (R1, R9).
5. Every slice: RED-first test + docs (`docs/stdlib/IO.md`) + zero stdlib regression.


## Objective

Create the native module `kof.file`, responsible for giving Kof a unified API for **creating, reading, writing, transforming, streaming and manipulating files and data/document formats**.

`kof.file` must evolve into a fundamental layer of the Kof stdlib, able to work with text files, structured data, documents, binary files, streams and formats widely used by real applications.

The premise is:

> Kof must be able to work natively with real data and files without forcing the developer to manually assemble a collection of external libraries for each common format.

---

# FUNDAMENTAL RULE — DO NOT INVENT SYNTAX

Before implementing anything:

1. Read the current Kof grammar.
2. Read real examples that exist in the repository.
3. Identify the currently supported syntax for: variables,
   functions, types, imports, loops, lambdas, error handling,
   resources, streams, objects, collections.
4. Use exclusively valid Kof constructs.

### IMPORTANT

Kof uses `var`.

Do not use:

```text
let
const
```

Do not import syntax from JavaScript, Kotlin, TypeScript or any other
language just because it is familiar.

Do not invent APIs or syntactic constructs to illustrate the
implementation.

All examples in this plan are **conceptual** and must be adapted to the
real Kof syntax before being incorporated into documentation or tests.

The goal is to implement **Kof**, not to turn Kof into a JavaScript
variant.

---

# 1. Principles

## 1.1 Unified API

Different formats must follow common concepts when a real shareable
abstraction exists.

Conceptually, the API may allow operations like (instance style — no static
`File.…` form, `D-FILE-STATIC`):

```kof
var file = File("users.json")
var data = file.readText()
```

and later interpret the data:

```kof
var json = data.json()
```

The syntax above must be validated against the current grammar before
being used in real code.

The final API must follow the patterns that already exist in the
stdlib.

---

# 2. A file is not a format

Separate clearly:

```text
filesystem
    ↓
file
    ↓
bytes / text / stream
    ↓
format
```

JSON, XML, CSV, PDF and other formats must not be conceptually coupled
to the filesystem.

The same content must be obtainable from: file, memory, stdin, HTTP,
socket, database, stream, another resource.

Avoid APIs excessively coupled to specific files per format when a
data/stream abstraction can be reused.

---

# 3. Architecture

Design `kof.file` in layers.

```text
kof.file
│
├── File        (open/create/read/write/append/copy/move/delete/metadata)
├── Path
├── Stream      (input/output/reader/writer)
├── Text
├── Binary
├── Data        (JSON/XML/CSV/other structured formats)
└── Document    (PDF/HTML/Markdown/others)
```

The final structure must respect the REAL architecture of Kof.

Do not create this tree literally if the project already has a better
organization.

---

# 4. Basic filesystem operations

Provide fundamental operations: open, create, read, write, append,
copy, move, delete, existence check, size, metadata, timestamps, type
check, directories (work/list/create/remove), paths.

Conceptual example, using correct Kof syntax (instance style — there is no static
`File.…` form, `D-FILE-STATIC`):

```kof
var file = File("data.txt")
var content = file.readText()
```

The real implementation must follow the APIs and conventions that
already exist.

When Kof's resource-management model allows, prefer automatic resource
management over requiring a manual `close()`.

---

# 5. Path

Create an adequate abstraction for paths: separators, absolute/relative
paths, normalization, resolution, parent, filename, extension,
existence, directory, file, symlink when supported.

Do not manipulate critical paths with manual string concatenation when
that can produce incorrect cross-platform behavior.

---

# 6. Text

Explicit support for text files: UTF-8, other encodings when needed,
conversion, incremental read/write, newline, BOM, invalid content.

Do not silently assume every textual file is UTF-8 if that can produce
data corruption.

---

# 7. Binary

Provide abstractions for binary data.

Conceptually (instance style — no static `File.…` form, `D-FILE-STATIC`):

```kof
var bytes = File("image.bin").readBytes()
```

and:

```kof
File("output.bin").writeBytes(bytes)
```

The API must allow working with: bytes, buffers, streams, offsets,
ranges, partial reads, partial writes.

---

# 8. Streaming

Streaming is an architectural requirement.

`kof.file` must not assume any file can be loaded entirely into
memory.

It must be possible to work incrementally with: large files, large
CSVs, streaming JSON when applicable, streaming XML, binary files,
logs, datasets, pipelines.

The final API must use the iteration and streaming model that already
exists in Kof.

Do not invent new syntax just to represent streaming.

---

# 9. JSON

Add native JSON support: parse, serialize, read, write, objects,
arrays, strings, numbers, booleans, null, validation, pretty printing,
streaming when applicable.

Access must respect Kof's existing type and collection system.

Do not copy JavaScript APIs as if JSON were a JS object.

> **Real state:** `kof.json` (`json.encode`/`json.decode`) already
> covers the core of this section. The future work is the seam with the
> File/Stream model, not a second parser.

---

# 10. XML

Add XML support: parse, serialize, elements, attributes, text,
namespaces, read, write, queries, streaming.

Evaluate XPath only with architectural justification.

Do not create a gigantic abstraction just to reproduce an external
library.

---

# 11. CSV / TSV

Add support for tabular formats. CSV must correctly handle:
delimiters, quotes, escapes, newline, encoding, header, columns, empty
fields, values containing delimiters, large files, streaming.

TSV must reuse the same infrastructure when possible.

The API must allow incremental processing.

---

# 12. YAML / TOML / INI

Progressively add support for configuration and data formats: YAML,
TOML, INI.

Each format must have: parse, serialize when it makes sense,
validation, clear errors.

Avoid creating incompatible data structures without need.

---

# 13. Markdown / HTML

**Markdown** — evaluate: read, parsing, structured representation,
generation.

**HTML** — evaluate: parsing, document tree, elements, attributes,
text, serialization. HTML must be treatable as a structured document,
not just a string.

> **Philosophy note (rule 9):** HTML here is handled as
> **data/document** (parse/serialize), never as template markup embedded
> in `kof.ui` user code.

---

# 14. PDF

Add PDF support progressively.

First phase: open, validate, metadata, page count, page access, text
extraction, simple PDF creation when technically viable.

Later: images, fonts, tables, positioning, forms, annotations, advanced
generation.

Do not implement the whole PDF standard from scratch if a mature
library can be used.

The external library must stay isolated behind the Kof API.

---

# 15. Images

Evaluate progressive support for: PNG, JPEG, GIF, WebP, BMP.

Possible operations: open, metadata, dimensions, read, write,
conversion, pixels when appropriate.

Do not turn `kof.file` into a graphics library.

The responsibility remains file and data manipulation.

> **Responsibility split:** pixel decoding and processing belong to
> `kof.image` (see [`../development/image-vision-plan.md`](../development/image-vision-plan.md)); `kof.file` delivers
> bytes/stream/loaded `Image`.

---

# 16. Compressed files

Evaluate support for: ZIP, GZIP, TAR.

Operations: open, list, extract, create, add, remove, streaming when
possible.

Consider security against path traversal inside archives.

---

# 17. Binary formats

The `kof.file` infrastructure must allow working with arbitrary binary
formats.

Provide primitives for: bytes, integers, floats, endianness, offsets,
buffers, streams, binary structures.

This lets future Kof libraries implement specific formats without
depending on an external library for every protocol.

---

# 18. Format detection

Evaluate an API able to identify formats when there is enough evidence:
extension, MIME type, magic bytes, content.

Do not rely on the extension alone.

When ambiguous, represent the uncertainty correctly.

---

# 19. MIME types

Add MIME-type infrastructure (`application/json`, `application/pdf`,
`text/csv`, `application/xml`, `image/png`, ...).

Allow future integration with: HTTP, upload, download, filesystem,
documents, binary content.

---

# 20. HTTP integration

Allow data obtained via HTTP to be consumed by the same `kof.file`
APIs.

Desired architecture:

```text
HTTP response → bytes/stream → JSON / XML / CSV / PDF / etc.
File → stream → HTTP upload
```

Do not create a circular dependency between HTTP and `kof.file`.

> **Real state:** `kof.http` already exists; the integration is a bridge
> layer (`Response → bytes/stream`), not a new module.

---

# 21. Database integration

The architecture must allow pipelines like Database → Data → format,
and File → Data → Database, reusing the existing data abstractions
instead of creating converters per combination.

> **Real state:** `kof.db` already exists; same bridging principle.

---

# 22. Security

Consider: path traversal, symlinks, permissions, missing files,
concurrency, race conditions, special files, limited resources, giant
files, malformed content, decompression bombs, malicious archives.

Do not assume content received by the program is trusted.

Errors must be explicit.

---

# 23. Targets

The public API must be shared whenever there is an equivalent
implementation.

Priority: JVM, Native, JS/WASM when applicable.

Do not fake cross-platform support. When a feature is
platform-specific: isolate the implementation, document the limitation,
return the appropriate error when unavailable (R6 — gap `XXX00x`, never
silence).

---

# 24. Performance

Consider: streaming, reusable buffers, partial reads, incremental
writes, zero-copy when possible, mmap when appropriate, backpressure,
parallel processing when it makes sense.

Do not optimize on speculation.

Add benchmarks to measure the relevant decisions.

---

# 25. Tests

Create tests per layer.

* **Filesystem:** create, read, write, append, copy, move, delete, directories, metadata.
* **Text:** UTF-8, Unicode, encoding, newline, BOM, empty files, invalid content.
* **Binary:** bytes, offsets, buffers, endianness, streams.
* **JSON:** parse, serialize, types, Unicode, errors, round-trip.
* **XML:** parse, namespaces, attributes, serialization, invalid XML, round-trip.
* **CSV:** header, quoted fields, delimiters, newline, Unicode, empty fields, large files.
* **PDF:** open, metadata, pages, text, invalid files.
* **Archives:** create, extract, corrupted files, malicious paths.

Also create integration tests:

```text
File → Format parser → Data → Format serializer → File
```

---

# 26. Dependencies

Do not implement complex standards from scratch just to avoid
dependencies.

Also do not add a gigantic library to solve one simple operation.

For each dependency evaluate: maturity, license, size, security,
maintenance, target compatibility, performance, future
replaceability.

Kof's public API must remain independent of the internally used
library.

---

# 27. Incremental implementation

Do not implement all formats simultaneously.

* **Phase 1 — core:** File, Path, Text, Binary, Stream.
* **Phase 2 — structured data:** JSON, CSV, XML.
* **Phase 3 — configuration:** YAML, TOML, INI.
* **Phase 4 — documents:** Markdown, HTML, PDF.
* **Phase 5 — binaries and containers:** Images, Archives, other formats.
* **Phase 6 — evolution:** advanced streaming, mmap, performance, HTTP integration, DB integration.

The order may change if Kof's current architecture justifies another
sequence.

---

# 28. Architectural rule

`kof.file` must not become a dumping ground of wrappers over external
libraries.

Every new feature must answer:

> What common abstraction does this feature introduce or reuse?

Abstract when real similarity exists.

Do not create artificial abstractions just to make the architecture
look pretty.

---

# 29. Language-compatibility rule

Before adding any API or example:

1. Consult the current grammar.
2. Consult real examples in the project.
3. Consult existing stdlib APIs.
4. Reuse existing conventions.
5. Do not invent syntax.
6. Do not import JavaScript patterns.
7. Do not import Kotlin patterns.
8. Do not import Python patterns.
9. Do not create a "file DSL" without need.

`kof.file` must look like a natural extension of Kof.

---

# 30. Completion criteria

The first version does not need to support all formats.

The architecture, however, must allow adding new formats without
rewriting the core.

* [x] File functional (`kof.io`)
* [x] Path functional (`kof.io`)
* [x] Text functional (`kof.io` + `TextStream`)
* [x] Binary functional (`kof.io` + `FileStream`)
* [x] Stream functional (`FileStream`/`TextStream`)
* [x] JSON (`kof.json` + JSON Lines bridge)
* [x] CSV (`CsvReader`/`CsvWriter`)
* [x] XML (`XmlReader`, namespaces)
* [x] config (INI/TOML/YAML)
* [x] tests (per-slice E2E, all targets)
* [ ] benchmarks
* [x] documentation (`docs/stdlib/IO.md`)
* [x] error handling (explicit diagnostics)
* [ ] security (archive path-traversal — not applicable until archives)
* [x] target integration (JVM/Native x86-64/riscv64/Script; JS `IOJS001`)
* [x] no regression in the existing stdlib

---

# Final rule

The goal is not to create a collection of wrappers.

The goal is to create a **Kof-owned abstraction for files, data and
documents**, using external libraries only when technically
justifiable.

The Kof developer must be able to think:

```text
"I need to work with this file"
```

and solve it through `kof.file`, without needing to know the internal
implementation used by the target.

The complexity stays behind the Kof API.

**Kof must know how to work with files. Kof must know how to work with
data. Kof must know how to work with documents.**

And all of it must be built incrementally, keeping the language, the
stdlib and the existing base stable.
