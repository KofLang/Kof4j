[English](io.md) | [Português](io.pt_BR.md)

# kof.io — filesystem API

Fatos sobre a API oficial de filesystem do Kof. Use para responder perguntas
sobre ler/escrever arquivos, trabalhar com paths e listar diretórios.

## Modelo

- `File`, `Path` e `Directory` são tipos de `kof.io`.
- Os três representam um caminho; as operações são as mesmas para os três.
- O programador nunca vê POSIX, `java.nio`, syscalls ou separadores por
  plataforma: `kof.io` resolve isso no backend (JVM → `java.nio.file`;
  Native → syscalls POSIX no Linux x86-64).
- **`readLine()` (top-level, stdin)** → `String?` (02/09): `null` no EOF em
  JVM e Native (antes o Native devolvia `""`). Trate com `if (line != null)`.

## Path

| Operação | Resultado |
|----------|-----------|
| `Path("a").resolve("b")` | `a/b` (separador da plataforma) |
| `Path("a/b").parent()` | `a` (ou null) |
| `Path("a/b.txt").fileName()` | `b.txt` |
| `Path("a/b.txt").extension()` | `txt` |
| `Path("a/./b/../c").normalize()` | `a/c` |
| `Path("a").isAbsolute()` | `false` |
| `Path("a").toAbsolute().isAbsolute()` | `true` |

`normalize()` resolve `.` e `..`; um caminho relativo vazio vira `.`.

## File

| Operação | Comportamento |
|----------|---------------|
| `File("x").exists()` | Bool |
| `File("x").isFile()` / `.isDirectory()` | Bool |
| `File("x").readText()` | `String?` — `null` se falhar (JVM e Native) |
| `File("x").writeText(s)` / `.appendText(s)` | Bool |
| `File("x").readBytes()` | `Int[]` (0-255); `null` se falhar |
| `File("x").writeBytes(b)` / `.appendBytes(b)` | Bool |
| Params de bytes e impressão de um `Int[]` (§388) | Os params de bytes pedem **array primitivo** — `writeBytes(listOf(65,66))` é erro em tempo de compilação (`SEM099`, §388-A); imprimir o array inteiro usa o formato de container §107: `println(f.readBytes())` → `[65, 66]` (voto da mantenedora 21/09, §388-B) |
| `File("x").size()` | Long; **lança exceção** se o arquivo não existe (02/09 — sem sentinela `-1`) |
| `File("x").delete()` | Bool |
| `File("x").mkdir()` / `.mkdirs()` | aliases POSIX de `create()` / `createDirectories()`: criam o diretório / todo o caminho (Bool) |

**Membro desconhecido é erro em tempo de compilação (`SEM102`, #617):** um método que não está nas tabelas acima num valor `File`/`Directory`/`Path` não compila, com `'File' has no method 'x()'` (hint: `Directory(path).createDirectories()` para criar diretório). Antes do guard, a chamada compilava em silêncio e não fazia nada em runtime — as tabelas de membros aqui SÃO o contrato.
| `File("x").name()` / `.path()` | String |

Não existem **formas estáticas `File.…`**: `File.exists(p)`, `File.readText(p)`,
`File.writeText(p, s)`, `File.delete(p)`, `File.size(p)` são rejeitados pelo typer
com `SEM011 Undefined variable or type: 'File'` (`D-FILE-STATIC`, mantenedora 02/10 —
a face `kof.io` fica só no estilo instância; o braço inalcançável `KofIo.staticMethod("File")`
NÃO é ligado). Sempre construa o handle primeiro: `File("x").exists()`,
`File("x").readText()`, `File("x").writeText(s)`, `File("x").delete()`, `File("x").size()`.

## Directory

| Operação | Comportamento |
|----------|---------------|
| `Directory("d").exists()` | Bool |
| `Directory("d").create()` | cria; falha se já existe |
| `Directory("d").createDirectories()` | cria recursivamente |
| `Directory("d").mkdir()` / `.mkdirs()` | aliases POSIX de `create()` / `createDirectories()` |
| `Directory("d").list()` | `List<String>` dos nomes (ordenado) |
| `Directory("d").delete()` | remove diretório vazio |

Iteração:

```kof
for (var entry in dir.list()) {
    println(entry.name)
}
```

`entry.name` e `entry.path` retornam a própria string do entry.

## Exemplos

```kof
var path = Path("data/users.txt")
path.parent().createDirectories()
path.writeText("Mel\nKof\n")
println(path.readText())
println(path.size())
```

```kof
var file = File("binary.dat")
var bytes = new Int[4]
bytes[0] = 65
bytes[1] = 0
bytes[2] = 255
bytes[3] = 66
file.writeBytes(bytes)
println(file.readBytes().length)
```

## Encoding

- `readText`/`writeText`/`appendText` usam UTF-8 sempre.
- O encoding default do sistema nunca é usado.

## Erros

- **Ausência como valor (02/09):** `readText()`/`readFile()` devolvem `String?`
  (`null` quando o arquivo não existe) — em JVM **e** Native (o Native antes
  encerrava com erro; agora devolve `null` como o JVM).
- `size()` **lança** exceção recuperável (`catch (String e)`) para arquivo
  inexistente — o `-1` sentinela foi removido (era anti-pattern do corpus).
- Operações booleanas retornam `true`/`false`.

## Limitações atuais (0.5.0-beta)

- Native: Linux x86_64 (syscalls POSIX) + riscv64/aarch64 placeholder via qemu; GC free-list aplica-se a buffers de arquivo.
- Symlinks, timestamps e permissões são futuros.
- Não há API de streams (`Reader`/`Writer`); operações são inteiras.