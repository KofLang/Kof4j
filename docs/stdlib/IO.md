[English](IO.md) | [Português](IO.pt_BR.md)

# kof.io — Filesystem API

`kof.io` is the official Kof filesystem API: files, directories and
paths with a single semantics on the JVM and Native targets.

## Types

`File`, `Path` and `Directory` represent a path (the path string).
All `kof.io` operations work on the three types — the type only
guides the intent.

## Path

| Operation | Example | Result (Linux/macOS) |
|----------|---------|--------------------------|
| `resolve` | `Path("data").resolve("users.txt")` | `data/users.txt` |
| `parent` | `Path("data/users.txt").parent()` | `data` |
| `fileName` | `Path("data/users.txt").fileName()` | `users.txt` |
| `extension` | `Path("data/users.txt").extension()` | `txt` |
| `normalize` | `Path("a/./b/../c").normalize()` | `a/c` |
| `isAbsolute` | `Path("/x").isAbsolute()` | `true` |
| `toAbsolute` | `Path("x").toAbsolute()` | absolute path |

On Windows the separator is `\`; Kof code never concatenates separators.

## File

| Operation | Description |
|----------|-----------|
| `exists()` | Bool |
| `isFile()` / `isDirectory()` | Bool |
| `readText()` | `String?` — `null` on failure (JVM and Native) |
| `writeText(s)` / `appendText(s)` | Bool, UTF-8 |
| `readBytes()` | `Int[]` (0-255), `null` on failure |
| `writeBytes(b)` / `appendBytes(b)` | Bool |
| `size()` | Long; throws an exception if the file does not exist (02/09 — no `-1` sentinel) |
| `delete()` | Bool (file or empty directory) |
| `name()` / `path()` | String |
| `copyTo(destination)` | Bool — JVM + Native (x86-64/riscv64/aarch64, parity row 13). Copies bytes + basic attributes. No-overwrite by default (returns `false`, does not touch either file, if `destination` already exists); does not create the parent directory of `destination` implicitly — the caller must ensure it exists |
| `moveTo(destination)` | Bool — JVM + Native. Filesystem-primitive rename/move, no-overwrite by default (same contract as `copyTo`). Not a safe transaction: callers that need a hash-verified move should keep doing copy → verify → delete, same as before this method existed |
| `modifiedTime()` | Long — JVM + Native. Last-modified time in epoch milliseconds; throws an exception if the file does not exist (same contract as `size()`, no sentinel) |
| `isSymlink()` | Bool — JVM + Native. `true` when the path itself is a symbolic link (the link is never followed implicitly by this check) |

There is **no static form** for any `kof.io` face: `File.exists(p)`,
`File.readText(p)`, `File.readRange(p, o, n)` are rejected by the typer with
`SEM011 Undefined variable or type: 'File'` on **every** target (measured
28/09) — use the instance form `File(p).exists()`, `File(p).readRange(0, 4)`.
(The compiler's `KofIo.staticMethod` table is unreachable because the typer
never resolves the type as a static receiver; `copyTo`/`moveTo`/`modifiedTime`/
`isSymlink` are instance-only.)

## Directory

| Operation | Description |
|----------|-----------|
| `exists()` | Bool |
| `create()` | creates; fails if it already exists |
| `createDirectories()` | creates recursively |
| `list()` | `List<String>` of names, sorted |
| `delete()` | removes an empty directory |

```kof
var dir = Directory("data")
dir.createDirectories()
for (var entry in dir.list()) {
    println(entry.name)
}
```

`entry.name` and `entry.path` return the entry itself.

## Complete example

```kof
var path = Path("data/users.txt")
path.parent().createDirectories()
path.writeText("Mel\nKof\n")
var text = path.readText()
println(text)
println(path.size())
```

## Errors and encoding

- Text: UTF-8 always.
- Absence as a value (02/09): `readText()`/`readFile()` return `String?`
  (`null` for a nonexistent file) on JVM and Native; `size()` throws a
  recoverable exception (`catch (String e)`) — the `-1` sentinel was removed.
- Booleans: `true`/`false`. `size()` throws an exception when the file does not exist (without `-1`).

## Streaming (`libs/file`)

`kof.io` also exposes `readRange(offset, len)` (incremental read). The
official pure-Kof library `libs/file` builds streaming on top of it —
`D-KOF-FILE-GO`; no new syntax, no compiler change. Slice 2 measured the
byte reader on every target, and slice 2.1 added `TextStream` (UTF-8 lines)
on the same model (`FileLibraryE2ETest` 13/13).

```kof
import file.FileStream

main() {
    var stream = FileStream("large.log", 4096)   // chunk size
    var chunk = stream.readChunk()
    while (chunk != null) {
        // process a fixed-size byte chunk; memory stays bounded
        chunk = stream.readChunk()
    }
    println(stream.position())
}
```

| Operation | Description |
|----------|-------------|
| `FileStream(path[, chunkSize])` | chunked byte reader (default 8192) |
| `readChunk()` | `Int[]?` — next chunk, `null` at end of file |
| `done()` | `Bool` |
| `position()` | `Long` bytes consumed |
| `copyStream(source, destination, chunkSize)` | `Long` bytes copied, constant memory |

Targets: JVM, Native (x86-64/riscv64/aarch64) and Script run the real
`readRange` (all measured against the same golden). JS has no host binding
for `readRange` (nor `copyTo`/`moveTo`/`modifiedTime`/`isSymlink`): calling
it refuses at compile time with `IOJS001` (`D-KOF-FILE-GO`), never a silent
whole-file fallback nor a runtime `SyntaxError`.

### Text streaming (`TextStream`, slice 2.1)

`TextStream` reads a file as **UTF-8 lines** over the same chunk model —
use it for logs, CSV, JSONL, datasets. A multi-byte sequence split across
two chunks is reassembled in Kof (never emitted as broken bytes); an
unterminated one at EOF becomes U+FFFD. `\n` and `\r\n` are both accepted
(`\r` is stripped); memory stays bounded by one chunk plus the current
line.

```kof
import file.TextStream

main() {
    var stream = TextStream("large.csv", 8192)   // chunk size
    var line = stream.nextLine()
    while (line != null) {
        // process one line without loading the whole file
        line = stream.nextLine()
    }
}
```

| Operation | Description |
|----------|-------------|
| `TextStream(path[, chunkSize])` | streaming UTF-8 line reader (default 8192) |
| `nextLine()` | `String?` — next line without terminator, `null` at end of file |
| `done()` | `Bool` — end of file reached and no pending text |
| `position()` | `Long` bytes consumed |

Exact for the BMP on JVM, Native and Script. **Non-BMP (astral) text is a
measured Native divergence**: Native stores strings as UTF-8 and has no
WTF-8 representation for a surrogate pair (`§537`); it is exact on JVM and
Script.

### CSV / TSV (`CsvReader` / `CsvWriter`, slice 2.2)

`CsvReader` streams **records** over `TextStream` character by character, so a
quoted field may contain the delimiter and newlines, records split on `\n`/
`\r\n`, and memory stays bounded by one chunk plus the current record. TSV
reuses the same reader with a tab delimiter. `CsvWriter` is its inverse: the
first `writeRow` truncates the file, the rest append, and a field is quoted
only when it contains the delimiter, a quote, a newline or a carriage return
(embedded quotes are doubled). Reader and writer round-trip the same records.

```kof
import file.Csv

main() {
    var reader = CsvReader("data.csv", ',', 8192)   // delimiter, chunk size
    var row = reader.nextRow()
    while (row != null) {
        // row.get(0) is the first field of the record
        row = reader.nextRow()
    }

    var writer = CsvWriter("out.csv", ',')
    writer.writeRow(listOf("id", "name"))
    writer.writeRow(listOf("1", "Doe, John"))       // quoted automatically
}
```

| Operation | Description |
|----------|-------------|
| `CsvReader(path[, delimiter[, chunkSize]])` | streaming record reader (default delimiter `,`, chunk 8192) |
| `nextRow()` | `List<String>?` — next record's fields, `null` at end of file |
| `CsvWriter(path[, delimiter])` | streaming record writer (default delimiter `,`; first write truncates) |
| `writeRow(cells)` | encode and append one record terminated by `\n` |

A `"` opens a quoted field only at the start of a field; inside it `""` is one
literal quote. A blank line is a record with one empty field; a trailing
newline does not add a record. The first record is not special — treat it as a
header by reading it first. Targets match `TextStream` (JVM, Native x86-64/
riscv64, Script; JS `IOJS001`).

### JSON Lines / NDJSON (`JsonLinesReader` / `JsonLinesWriter`, slice 2.3)

The seam between `kof.json` and the streaming model: each non-blank line is one
JSON document, read through `TextStream` (memory bounded by one chunk plus the
current line). `json.encode` never emits a raw newline, so line framing is
exact. The reader is deliberately **untyped** — the caller decodes each
document with `json.decode<T>` at a concrete `T`; decoding with an open type
parameter is the compiler defect `§538`.

```kof
import file.JsonLines

record Pt(Int x, Int y)

main() {
    var reader = JsonLinesReader("events.jsonl", 8192)
    var doc = reader.nextJson()
    while (doc != null) {
        if (doc != null) {
            var p = json.decode<Pt>(doc)
        }
        doc = reader.nextJson()
    }

    var writer = JsonLinesWriter("out.jsonl")
    writer.writeJson(json.encode(Pt(1, 2)))   // one document per line
}
```

| Operation | Description |
|----------|-------------|
| `JsonLinesReader(path[, chunkSize])` | streaming reader of non-blank lines (default chunk 8192) |
| `nextJson()` | `String?` — next non-blank JSON document text, `null` at end of file |
| `JsonLinesWriter(path)` | streaming writer (the first write truncates, the rest append) |
| `writeJson(json)` | append one already-encoded JSON document as a line |

Blank (empty or whitespace-only) lines are skipped. Typed record decode is
supported on JVM, Native x86-64 and Script; on riscv64 `json.decode<record>`
lacks the cross `kof_json_find_value` binding (`NATIVE002`-stdlib), so decode
arrays there. Targets match `TextStream` (JS `IOJS001`).

### XML (`XmlReader`, slice 2.4)

A non-validating, pull-style streaming XML reader over `TextStream` (bounded
memory): each `next()` returns one significant event — `kind` is `start`,
`end`, `empty` or `text`; `name`/`text`/`attributes` carry the payload, and it
returns `null` at end of document. Malformed input throws a `String`.

```kof
import file.Xml

main() {
    var reader = XmlReader("doc.xml", 8192)
    var ev = reader.next()
    while (ev != null) {
        if (ev != null && ev.kind == "start" && ev.name == "book") {
            // ev.attributes.get("id") is the id, if present
        }
        ev = reader.next()
    }
}
```

| Operation | Description |
|----------|-------------|
| `XmlReader(path[, chunkSize])` | streaming pull reader (default chunk 8192) |
| `next()` | `XmlEvent?` (`kind` `start`/`end`/`empty`/`text`, `name` verbatim, `localName`, `namespaceUri`, `text`, `attributes`, `attributeNamespaces`), `null` at EOF |

Documented subset: elements/attributes/text/empty elements; the XML declaration,
processing instructions and comments are skipped; a DOCTYPE (with an optional
internal subset) is skipped but its entities are **not** resolved; CDATA becomes
a text event and is **not** entity-decoded; the predefined entities and numeric
`&#D;`/`&#xH;` are resolved (any other entity throws); whitespace-only text is
skipped; the element stack is validated (mismatched end tag or EOF throws).
Namespace resolution (slice 2.5): `xmlns`/`xmlns:prefix` declarations are scoped
to the element subtree; `localName`/`namespaceUri` are resolved on element events
and `attributeNamespaces` maps each attribute to its URI (unprefixed attributes
have no namespace); `name` stays verbatim, so it is additive over 2.4. An unbound
prefix throws; the predefined `xml`/`xmlns` bindings are enforced. Targets match
`TextStream` (JVM, Native x86-64/riscv64, Script; JS `IOJS001`).

### INI (`Ini`, slice 3.1)

An INI configuration reader over `TextStream`: entries are kept in an ordered
list (config-sized), and `get(section, key)` / `has(section, key)` /
`keysOf(section)` query them. Section `""` is the global scope.

```kof
import file.Ini

main() {
    var ini = Ini("app.ini")
    var host = ini.get("db", "host")   // String?, null when absent
    if (host != null) {
        println(host)
    }
}
```

| Operation | Description |
|----------|-------------|
| `Ini(path[, chunkSize])` | parse an INI file (default chunk 8192) |
| `get(section, key)` | `String?` — value, or `null` (section `""` = global) |
| `has(section, key)` | `Bool` |
| `keysOf(section)` | `List<String>` — keys in first-appearance order |

Documented subset: `[section]` headers (keys before the first are global);
`key = value` and `key: value`, trimmed; a value wrapped in matching single or
double quotes is unquoted; full-line comments start with `;` or `#` (inline
comments are data); a duplicate `section`+`key` keeps the **last** value; a
malformed header or a line without a separator throws a `String`. Targets match
`TextStream` (JVM, Native x86-64/riscv64, Script; JS `IOJS001`).

### TOML (`Toml`, slice 3.2)

A streaming TOML configuration reader over `TextStream`. Tables and dotted keys
are flattened into a canonical dotted key — no nested maps — and typed
accessors parse on demand.

```kof
import file.Toml

main() {
    var t = Toml("app.toml")
    var name = t.getString("service.name")    // String?, null when absent
    var port = t.getInt("service.port", 8080) // Int, fallback when absent
    var tags = t.getArray("service.tags")     // List<String>?, null when absent
}
```

| Operation | Description |
|----------|-------------|
| `Toml(path[, chunkSize])` | parse a TOML file (default chunk 8192) |
| `has(key)` | `Bool` |
| `kindOf(key)` | `String?` — `string`/`integer`/`float`/`boolean`/`array` |
| `getString(key)` | `String?` — decoded string value |
| `getArray(key)` | `List<String>?` — array elements |
| `getRaw(key)` | `String?` — canonical scalar text |
| `getInt(key, fallback)` / `getDouble(key, fallback)` / `getBool(key, fallback)` | typed value or `fallback` |
| `keys()` | `List<String>` — declared keys in first-appearance order |

Documented subset: comments `#` (outside strings) and blank lines; `key = value`
with bare or quoted keys and dotted keys; `[table]`/`[a.b]` headers set the
prefix for following keys; values are basic `"..."` (escapes
`\b \t \n \f \r \" \\ \uXXXX \UXXXXXXXX`), literal `'...'`, boolean, integer
(sign, `_` separators), float (`.`/`e`) and single-line arrays of scalars. A
duplicate key or a malformed line throws a `String`. Not supported, with an
explicit diagnostic: `[[array of tables]]`, multi-line arrays, nested
arrays/inline tables and date/time values. Targets match `TextStream` (JVM,
Native x86-64/riscv64, Script; JS `IOJS001`).

### YAML (`Yaml`, slice 3.3)

A streaming YAML configuration reader over `TextStream` for block mappings and
block sequences of scalars. Mappings are flattened into a canonical dotted key
and sequence items are indexed, so a document is queried without nested maps.

```kof
import file.Yaml

main() {
    var y = Yaml("app.yaml")
    var name = y.getString("service.name")     // String?, null when absent
    var port = y.getInt("service.port", 8080)  // Int, fallback when absent
    var tags = y.getArray("service.tags")      // List<String>?, null when absent
}
```

| Operation | Description |
|----------|-------------|
| `Yaml(path[, chunkSize])` | parse a YAML file (default chunk 8192) |
| `has(key)` | `Bool` |
| `kindOf(key)` | `String?` — `string`/`integer`/`float`/`boolean`/`null` |
| `getString(key)` | `String?` — decoded string value |
| `getArray(key)` | `List<String>?` — scalar sequence at `key` (items `key.0`…) |
| `getRaw(key)` | `String?` — canonical scalar text |
| `getInt(key, fallback)` / `getDouble(key, fallback)` / `getBool(key, fallback)` | typed value or `fallback` |
| `keys()` | `List<String>` — mapping paths and indexed items in file order |

Documented subset: comments `#` (at line start or after whitespace, outside
quotes) and blank lines; block mappings `key: value` nested by a deeper space
indent; block sequences of scalars `- value` (indexed under the parent key);
scalars plain, single-quoted (`''` = `'`), double-quoted (escapes
`\0 \a \b \t \n \v \f \r \e \" \\ \uXXXX \UXXXXXXXX`), boolean, null
(`null`/`~`/empty, case-insensitive), integer and float. A key with an empty
value is a section header; use `null`/`~` for an explicit null. A duplicate
key, tab indentation or a malformed line throws a `String`. Not supported, with
an explicit diagnostic: block scalars (`|`/`>`), flow collections (`[]`/`{}`),
sequences of mappings, nested sequences, anchors/aliases/tags and multi-line
scalars. Targets match `TextStream` (JVM, Native x86-64/riscv64, Script; JS
`IOJS001`).

## Reference

- [learn/34-file-system.md](../../learn/34-file-system.md)
- Tests: `kof-compiler/src/test/java/dev/kof/compiler/IoE2ETest.java`
- Streaming: `libs/file/FileStream.kf`, `libs/file/TextStream.kf`, `libs/file/Csv.kf`, `libs/file/JsonLines.kf`, `libs/file/Xml.kf`, `libs/file/Ini.kf`, `libs/file/Toml.kf`, `libs/file/Yaml.kf`, `FileLibraryE2ETest.java`, `CsvReaderE2ETest.java`, `JsonLinesE2ETest.java`, `XmlReaderE2ETest.java`, `IniReaderE2ETest.java`, `TomlReaderE2ETest.java`, `YamlReaderE2ETest.java`
