[English](34-file-system.md) | [Português](34-file-system.pt_BR.md)

# 34 — Filesystem (kof.io)

> **Kof 0.5.0-beta — `intention->Kof->frontend->IR->backend->runtime` — kof.io + kof.http (JVM+JS)**

`kof.io` is Kof's official filesystem API. A single API for JVM and
Native, Linux, macOS and Windows — without exposing POSIX, `java.nio` or syscalls.

```kof
var path = Path("data/users.txt")
path.parent().createDirectories()
path.writeText("Mel\nKof\n")
println(path.readText())
println(path.size())
```

## Values

`File`, `Path` and `Directory` are `kof.io` types that represent a
path. The operations are the same for all three — the type only guides the
intention:

```kof
var file = File("hello.txt")
var path = Path("data")
var dir = Directory("data")
```

## Path

Path operations (without touching the filesystem):

| Operation | Description |
|----------|-----------|
| `resolve(outro)` | joins two paths with the platform separator |
| `parent()` | parent directory (or `null`) |
| `fileName()` | name of the last component |
| `extension()` | extension (without the dot) |
| `normalize()` | resolves `.` and `..` |
| `isAbsolute()` | absolute path? |
| `toAbsolute()` | resolves against the working directory |

```kof
Path("a/./b/../c").normalize()   // a/c
Path("data").resolve("users")    // data/users (or data\users on Windows)
```

## File

| Operation | Description |
|----------|-----------|
| `exists()` | exists? |
| `isFile()` / `isDirectory()` | type |
| `readText()` | content as text (UTF-8); `null` if it fails |
| `writeText(s)` / `appendText(s)` | writes / appends text (UTF-8) |
| `readBytes()` | content as `Int[]` (bytes 0-255) |
| `writeBytes(b)` / `appendBytes(b)` | writes / appends bytes |
| `size()` | size in bytes |
| `delete()` | removes (file or directory — non-empty directories delete recursively on every target since 08/10) |
| `name()` / `path()` | file name / path |

There are no static `File.…` forms — always construct the handle first:

```kof
var f = File("x.txt")
if (f.exists()) { println(f.readText()) }
f.writeText("conteúdo")
```

## Directory

| Operation | Description |
|----------|-----------|
| `exists()` | exists? |
| `create()` | creates (fails if it already exists) |
| `createDirectories()` | creates recursively |
| `list()` | `List<String>` with the item names (sorted) |
| `delete()` | removes directory, recursively when non-empty (all targets since 08/10) |

```kof
var dir = Directory("data")
dir.createDirectories()
for (var entry in dir.list()) {
    println(entry.name)
}
```

## Bytes

Bytes use the `Int[]` representation (each element 0-255):

```kof
var b = new Int[4]
b[0] = 65
b[1] = 0
b[2] = 255
File("bin.dat").writeBytes(b)
var data = File("bin.dat").readBytes()
```

## Encoding

`readText`/`writeText`/`appendText` always use **UTF-8**. The operating
system encoding is never used.

## Errors

- `readText`/`readBytes`: `null` when the file cannot be read.
- Boolean operations (`writeText`, `delete`, `create`, ...): `true` on
  success, `false` on failure.
- `size`: `-1` when the file does not exist.
- On the **Native** target, `readText` of a nonexistent file terminates the
  program with an error (`kof_panic`); on the JVM/JS it returns `null`.
  Check with `exists()` before reading.

## Behavior by platform

- Separators: `kof.io` uses the platform separator (`/` on Linux/macOS,
  `\` on Windows) — the program never concatenates separators manually.
- Case sensitivity: respects the filesystem.
- The Native target (x86-64 Linux) uses POSIX syscalls; the JVM uses `java.nio`.
  The API is the same.

## Reference

- [docs/stdlib/IO.md](../docs/stdlib/IO.md)

## Next step

[Versioning and Releases →](33-versioning-releases.md)
