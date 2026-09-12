# 34 — Filesystem (kof.io)

> **Kof 0.3.22-beta — `intention->Kof->frontend->IR->backend->runtime` — kof.io + kof.http (JVM+JS)**

`kof.io` é a API oficial de filesystem do Kof. Uma única API para JVM e
Native, Linux, macOS e Windows — sem expor POSIX, `java.nio` ou syscalls.

```kof
var path = Path("data/users.txt")
path.parent().createDirectories()
path.writeText("Mel\nKof\n")
println(path.readText())
println(path.size())
```

## Valores

`File`, `Path` e `Directory` são tipos de `kof.io` que representam um
caminho. As operações são as mesmas para os três — o tipo apenas orienta a
intenção:

```kof
var file = File("hello.txt")
var path = Path("data")
var dir = Directory("data")
```

## Path

Operações de caminho (sem tocar o filesystem):

| Operação | Descrição |
|----------|-----------|
| `resolve(outro)` | junta dois caminhos com o separador da plataforma |
| `parent()` | diretório pai (ou `null`) |
| `fileName()` | nome do último componente |
| `extension()` | extensão (sem o ponto) |
| `normalize()` | resolve `.` e `..` |
| `isAbsolute()` | caminho absoluto? |
| `toAbsolute()` | resolve contra o working directory |

```kof
Path("a/./b/../c").normalize()   // a/c
Path("data").resolve("users")    // data/users (ou data\users no Windows)
```

## File

| Operação | Descrição |
|----------|-----------|
| `exists()` | existe? |
| `isFile()` / `isDirectory()` | tipo |
| `readText()` | conteúdo como texto (UTF-8); `null` se falhar |
| `writeText(s)` / `appendText(s)` | grava / anexa texto (UTF-8) |
| `readBytes()` | conteúdo como `Int[]` (bytes 0-255) |
| `writeBytes(b)` / `appendBytes(b)` | grava / anexa bytes |
| `size()` | tamanho em bytes |
| `delete()` | remove (arquivo ou diretório vazio) |
| `name()` / `path()` | nome do arquivo / caminho |

Formas estáticas equivalentes:

```kof
File.exists("x.txt")
File.readText("x.txt")
File.writeText("x.txt", "conteúdo")
```

## Directory

| Operação | Descrição |
|----------|-----------|
| `exists()` | existe? |
| `create()` | cria (falha se já existe) |
| `createDirectories()` | cria recursivamente |
| `list()` | `List<String>` com os nomes dos itens (ordenado) |
| `delete()` | remove diretório vazio |

```kof
var dir = Directory("data")
dir.createDirectories()
for (var entry in dir.list()) {
    println(entry.name)
}
```

## Bytes

Bytes usam a representação `Int[]` (cada elemento 0-255):

```kof
var b = new Int[4]
b[0] = 65
b[1] = 0
b[2] = 255
File("bin.dat").writeBytes(b)
var data = File("bin.dat").readBytes()
```

## Encoding

`readText`/`writeText`/`appendText` usam **UTF-8** sempre. O encoding do
sistema operacional nunca é usado.

## Erros

- `readText`/`readBytes`: `null` quando o arquivo não pode ser lido.
- Operações booleanas (`writeText`, `delete`, `create`, ...): `true` no
  sucesso, `false` na falha.
- `size`: `-1` quando o arquivo não existe.
- No target **Native**, `readText` de um arquivo inexistente encerra o
  programa com erro (`kof_panic`); no JVM/JS retorna `null`.
  Verifique com `exists()` antes de ler.

## Comportamento por plataforma

- Separadores: `kof.io` usa o separador da plataforma (`/` no Linux/macOS,
  `\` no Windows) — o programa nunca concatena separadores manualmente.
- Case sensitivity: respeita o filesystem.
- O target Native (x86-64 Linux) usa syscalls POSIX; o JVM usa `java.nio`.
  A API é a mesma.

## Referência

- [docs/stdlib/IO.md](../docs/stdlib/IO.md)

## Próximo passo

[Versionamento e Releases →](33-versioning-releases.md)