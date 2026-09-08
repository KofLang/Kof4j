# Kof

<p align="center">
  <img src="kof.png" alt="Kof Logo" width="200">
</p>

### Uma linguagem. Um compilador. Vários mundos.
se pronuncia coffe

**Menos código. Mais intenção. JVM, nativo, script e web. Tudo partindo da mesma linguagem.**

---

## Mascote

<p align="center">
  <img src="kof_mascot.png" alt="Mascote da Kof — uma civetta" width="300">
</p>

O mascote da Kof é uma **civetta** — também conhecida como **gato do almiscar**,
é um felino que come café. Nada mais adequado para uma linguagem que se pronuncia
*coffe*.

---

## Disclaimer

A linguagem Kof não possui qualquer relação com o jogo The King of Fighters ou com sua franquia.

O nome Kof surgiu como uma referência à palavra "coffee" escrita propositalmente de forma incorreta. A escolha foi feita justamente na tentativa de criar um nome curto, único e facilmente identificável para a linguagem.

Koflang e Kof4J não compactuam com a associação do nome à franquia The King of Fighters. Qualquer semelhança ou associação feita nesse sentido é incidental e não representa a origem, o propósito ou a identidade dos projetos.

Nosso objetivo sempre foi criar uma identidade própria para a linguagem e seus componentes.

---

> Algumas pessoas olham para um problema e escrevem uma biblioteca.
>
> Outras escrevem um framework.
>
> Algumas criam uma ferramenta.
>
> Eu aparentemente olhei para o ecossistema inteiro e pensei:
>
> **"Tá tudo complicado demais. Vou criar uma linguagem."**
>
> E, aparentemente, uma linguagem só também não era suficiente.

Bem-vinda à **Kof**.

---

# O que é Kof?

Kof é uma linguagem de programação **geral e estaticamente tipada**, construída com uma ideia central:

> **Uma única linguagem não deveria obrigar você a escolher um único mundo.**

> 📖 **A especificação formal da linguagem** (gramática, sistema de tipos,
> semântica, status de cada feature) está em
> [`docs/language-reference/`](docs/language-reference/). A arquitetura do
> compilador (implementação) está em
> [`docs/compiler-architecture.md`](docs/compiler-architecture.md). A
> distinção **linguagem ≠ compilador ≠ target** é o eixo desses documentos.

Kof possui seu próprio compilador, lexer, parser, sistema de tipos, análise semântica e representação intermediária (Kof IR). A partir dessa IR, diferentes backends transformam o mesmo programa em diferentes formas de execução:

```text
            Linguagem Kof  (definida pela especificação)
                          │
                    Kof Compiler  (uma implementação)
                          │
                       Kof IR  (máquina de pilha linear, 30 ops)
                          │
          ┌───────────────┼────────────────┐
          │               │                │
       JVM Backend    Native Backend    JS Backend
          │               │                │
          ▼               ▼                ▼
        JVM          Native Binary      ES Modules
       (.class)      (ELF x86_64,       (Node /
                      riscv64/aarch64)    browser)
```

**A linguagem não muda. O target muda.** JVM, Native e JS são *targets de
compilação* da mesma Kof — não dialetos semanticamente diferentes. **KofScript**
(`.ks`, REPL) é um *target de execução direta*: Kof puro consumindo o MESMO
frontend e executado pelo interpretador da IR, sem compilar e sem fork de JVM —
**não é JavaScript** (`let`/`const`/`async`/`fn` não existem). KofC é uma
ferramenta separada (subconjunto C → ELF), não consome a IR Kof — ver
[docs/compiler-architecture.md](docs/compiler-architecture.md) §7.)

---

# Kof não é um transpiler

Kof não funciona assim:

```text
Kof → Java → javac → JVM
```

Funciona assim:

```text
Kof → Kof Compiler → Kof IR → Backend → Target
```

O compilador possui sua própria implementação de:

* lexer
* parser
* AST
* resolução de símbolos
* sistema de tipos
* análise semântica
* IR
* diagnostics
* geração de código

Kof não depende de Java como linguagem intermediária.

---

# Estado Atual

Kof está em desenvolvimento ativo — **0.3.0-beta**.

O compilador possui frontend próprio, type system, Kof IR e **três backends
sobre a IR**, que produzem **seis targets**: JVM (V21 via ASM), Native x86_64
(ELF, sem libc), `native.risc`/`native.arm` (riscv64 real + aarch64 via
tradutor ISA), KofJS (ES Modules) e Android (variante do JVM + empacotamento
APK). **KofScript** (`.ks`, REPL) é um **target de execução direta**: Kof puro
no MESMO frontend, executado pelo interpretador da IR (`KofInterpreter`) sem
emitir bytecode nem fork de JVM. **KofC** (subconjunto C → nativo) é uma
ferramenta separada, não consome a IR Kof — ver
[docs/compiler-architecture.md](docs/compiler-architecture.md) §7.

| Feature | JVM | Native | KofJS |
|---------|-----|--------|-------|
| println, variáveis, aritmética | ✅ | ✅ | ✅ |
| if/else, if-expr, while, for, for-in, switch | ✅ | ✅ | ✅ |
| functions (sem `fun`), lambdas com capturas | ✅ | ✅ | ✅ |
| records, classes, herança, interfaces, virtual dispatch | ✅ | ✅ | ✅ |
| generics (erasure), `Box<T>` com primitivos | ✅ | ✅ | ✅ |
| exceptions (throw "msg", try/catch/finally) | ✅ | ✅ | ✅ |
| null safety `String?` + narrowing | ✅ | ✅ | ✅ |
| pattern matching `case String s` + record destructuring | ✅ | ✅ | ✅ |
| spawn/await (`Handle<T>`, unboxing) | ✅ | ✅ (pthread) | ✅ sequencial |
| strings (concat `+`, `==`, API completa) | ✅ | ✅ | ✅ |
| arrays, `List<T>`/`Map<K,V>`/`Set<T>` + map/filter/reduce | ✅ | ✅ | ✅ |
| enums + switch exaustivo | ✅ | ✅ | ✅ |
| JSON encode/decode (objetos/records/arrays, 3 targets) | ✅ | ✅ | ✅ |
| kof.io (File, Path, Directory) | ✅ | ✅ | ✅ |
| kof.time (`now`/`sleep`/`interval`), kof.cache | ✅ | ✅ | ✅ |
| kof.web (`web.app()`, ws, sse, TLS) | ✅ | WEB002 | WEB001 |
| kof.http client + retry/circuit | ✅ | HTTP002 | ✅ |
| kof.security (passwords, crypto, jwt, secrets, auth) | ✅ | ✅ | ✅ |
| kof.db / kof.orm (SQLite nativo, MySQL WIP, MongoDB) | ✅ | ✅ | DB001/ORM001 |
| kof.config / kof.log | ✅ | ✅ | CONF001/LOG001 |
| kof.ui (Color, Palette, Theme, widgets) | no-op | no-op | ✅ render |

**Concorrência**: `spawn tarefa()` / `val r = spawn f(); await r` — virtual
threads na JVM, `pthread_create` no Native (CONC001 fechado 31/08), sequencial
no JS (CONC003). Ver [docs/concurrency.md](docs/concurrency.md).

**Null safety**: `String?`/`Int?` + `if (x != null)` narrowing nos 3 targets
(fix JVM 02/09). `Map.get` devolve `V?` para valores de referência.

**Testes**: `test "nome" { }` + `assert(cond, "msg")` + `kof test` — 810 testes
(793 kof-compiler + 8 kof-script + 5 kof-c-compiler + 4 kof-cli). Ver
[learn/23-testing.md](learn/23-testing.md).

**Depuração**: `kof debug <file.kf>` — servidor DAP sobre stdio com JDWP cru
(breakpoints por linha Kof, call stack com funções/linhas Kof, continue,
disconnect). Ver [docs/debugging.md](docs/debugging.md).

**Auditoria do ecossistema**: matriz de cobertura da stdlib (inventário,
gaps G1-G12, prioridade e estratégia) em
[docs/ecosystem-coverage.md](docs/ecosystem-coverage.md). Plano de evolução
para plataforma completa: [docs/plan-platform-completion.md](docs/plan-platform-completion.md).

---

# kof.ui — A plataforma de UI

A fundação da UI do Kof: `Color` (RGBA 32-bit), `Palette` (cores nomeadas)
e `Theme` (light/dark com cores semânticas) — mesma semântica em JVM,
Native e JS. A renderização é **KofJS**: widgets → DOM real no webview
nativo (`bin/kof-webview`, WebKitGTK embutido) ou no browser.

Widgets: `Window` (título, bind, show/close, size, theme), `Label` (text,
fontSize, bold, color), `Button` (texto + ação por lambda com capturas),
`Input` (text), containers `Column`/`Row`, `View`+`Style` (background,
padding, radius).

```kof
class App {
    static Int count = 0
}

main() {
    var w = Window("Contador")
    var label = Label("contagem: 0")
    w.bind(label)
    w.bind(Button("+1", () -> {
        App.count = App.count + 1
        label.text = "contagem: " + App.count
    }))
    w.show()
}
```

```bash
kof run contador.kf --target=js   # abre a janela; fechar encerra o programa
```

Ver: [learn/35-kof-ui.md](learn/35-kof-ui.md) e
[learn/37-kofjs.md](learn/37-kofjs.md).

---

# Documentação — onde procurar o quê

| Pasta | Para quem | O que contém |
|-------|-----------|--------------|
| [`docs/`](docs/) | arquitetos, mantenedores, decisões | **Documentação técnica e de projeto**: estado atual (`status.md`, `actual-state.md`), arquitetura (`architecture.md`), segurança (`security.md`), performance (`performance.md`), depuração (`debugging*.md`), roadmap (`roadmap.md`), stdlib (`stdlib/`, `stdlib-web.md`...), targets (`targets/`), distribuição (`distribution/`), ferramentas (`tooling/`), visões futuras (`future/`) e auditorias (`ecosystem-coverage.md`, `complexity-audit.md`) |
| [`learn/`](learn/README.md) | humanos aprendendo Kof | **Trilha de aprendizado em capítulos numerados** (00 Introdução → 37 KofJS): linguagem, classes, funções, lambdas, UI, segurança — cada capítulo um guia prático; `learn/native/` para o alvo nativo |
| [`training/`](training/README.md) | LLMs e ferramentas de IA | **Corpus estruturado otimizado para modelos de linguagem**: fatos por tópico (`language/`), idiomas (`idioms/`), padrões/anti-padrões (`patterns/`, `anti-patterns/`), exemplos compiláveis (`examples/`), referência (`reference/`), migração Java→Kof (`migration/`), tooling e releases |

**Regra prática**: `docs/` diz *como o Kof é* (estado e arquitetura);
`learn/` ensina *como usar o Kof* (passo a passo); `training/` alimenta
*quem gera código Kof* (LLMs).

---

# kof.web — Stack Web Nativa

Aplicações web sem Spring, sem servlet container, sem annotations:

```kof
record User(String name, Int age)

main() {
    var app = web.app()

    app.use {
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }

    app.get("/hello") {
        return "Hello from Kof"
    }

    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }

    app.post("/user") {
        var user = json.decode<User>(body())
        return json.encode(user)
    }

    app.listen(8080)
}
```

```bash
kof serve app.kf
```

Path params, query, headers, body, middleware, JSON tipado e servidor HTTP
embutido no runtime do programa. Ver: [docs/stdlib-web.md](docs/stdlib-web.md).

---

# kof.io — Filesystem

Arquivos, diretórios e caminhos com uma API única em todos os targets:

```kof
var path = Path("data/users.txt")
path.parent().createDirectories()
path.writeText("Mel\nKof\n")
println(path.readText())
println(path.size())
```

```kof
var dir = Directory("data")
dir.createDirectories()
for (var entry in dir.list()) {
    println(entry.name)
}
```

Texto sempre UTF-8; bytes como `Int[]`; ausência como `String?` (`null`) e
`size()` lança em vez de sentinela `-1`. Ver: [learn/34-file-system.md](learn/34-file-system.md) e
[docs/stdlib/IO.md](docs/stdlib/IO.md).

---

# Instalação

Kof é uma **distribuição**: instale e receba compilador, CLI, runtime,
stdlib, tooling, editor support e um OpenJDK embutido. **Nenhuma instalação
externa de Java é necessária** — e não precisa saber a versão para instalar.

1. Baixe o pacote do **seu** sistema em
   [GitHub Releases](https://github.com/KofLang/Kof4j/releases/latest):
   `linux-x86_64.tar.gz` / `macos-arm64.tar.gz` / `windows-x86_64.zip`.
2. Extraia e adicione o `bin` ao `PATH`:

```bash
# Linux
tar -xzf kof-*-linux-x86_64.tar.gz
export PATH="$PWD/$(ls -d kof-*-linux-x86_64 | head -1)/bin:$PATH"

# macOS (Apple Silicon)
tar -xzf kof-*-macos-arm64.tar.gz
export PATH="$PWD/$(ls -d kof-*-macos-arm64 | head -1)/bin:$PATH"

# Windows (PowerShell)
Expand-Archive .\kof-*-windows-x86_64.zip
$DIR = (Get-ChildItem -Directory -Filter "kof-*-windows-x86_64" | Select-Object -First 1).FullName
$env:PATH = "$DIR\bin;$env:PATH"
```

3. Confira:

```bash
kof version   # kof <versão da release>
kof info      # ambiente completo (JVM embutida, Tooling API 21, targets)
```

Ver: [docs/distribution/INSTALL.md](docs/distribution/INSTALL.md) (guia
completo com cada sistema, checksum e solução de problemas) e
[docs/distribution/ARCHITECTURE.md](docs/distribution/ARCHITECTURE.md).

---

# CLI

```bash
kof build <dir> [--target jvm|native|native.risc|native.arm|js|android] [--output <dir>] [--release]
kof run <file.kf> [--target jvm|native|native.risc|native.arm|js] [args...]
kof serve <file.kf> [--port <port>] [--host <host>]
kof check <file.kf|dir>
kof test <file.kf|dir> [--target jvm|native|js]
kof script | repl | c | fmt | config
kof bench | profile | inspect | debug
kof info | lsp | install | version
```

`kof fmt` (formatter idempotente) e `kof config gen` implementados — ver
[docs/tooling/README.md](docs/tooling/README.md).

---

---

# Compilando e instalando a partir do source

**Requisitos:** JDK 21+ (Temurin recomendado — é a Tooling API baseline) e
Maven 3.9+. Para o target `native`: `as`/`ld` (binutils). O target `js` não
exige nada externo (GraalJS embarcado no jar).

```bash
# 1. Compilar tudo (compilador, runtime, CLI com GraalJS embarcado)
mvn clean package -DskipTests

# 2. Rodar a suíte completa (JVM + Native + KofJS E2E)
mvn test

# 3. Usar direto do source (dev build, java do sistema)
mkdir -p lib
cp kof-cli/target/kof-cli-$(cat VERSION).jar lib/kof.jar
bin/kof version
bin/kof info

# 4. Instalar num prefixo (instalação local completa)
bin/kof install ~/.kof
export PATH="$HOME/.kof/bin:$PATH"
kof version

# 5. Empacotar a distribuição oficial (com OpenJDK 21 embutido)
scripts/package.sh --jdk      # gera dist/kof-<versão>-<os>-<arch>.tar.gz
```

O `kof install <dir>` copia o `kof.jar` para `<dir>/lib/` e gera o launcher
`<dir>/bin/kof` (usa o JDK embutido de `<dir>/jdk/` quando presente; senão o
`java` do sistema). O `scripts/package.sh --jdk` baixa o Temurin 21 do
Adoptium e monta o layout completo de distribuição.

Versionamento centralizado em `VERSION` — ver
[docs/distribution/VERSIONING.md](docs/distribution/VERSIONING.md).

**Windows:** use o **Git Bash** para `scripts/package.sh` — o `bash`
genérico do PATH pode resolver para o WSL e gerar uma distribuição Linux
(OBS-005). No Windows, o Python pode estar disponível apenas como o
launcher `py` — o script o descobre automaticamente (`python3`/`python`/
`py -3`).

---

# Arquitetura

```text
Source (.kf)
  ↓ Lexer
  ↓ Parser
  ↓ AST
  ↓ Type System
  ↓ Semantic Analysis
  ↓ Kof IR (backend-agnostic)
  ├── JVM Backend (ASM) → .class
  ├── Native Backend (x86_64 / riscv64 / aarch64) → ELF
  └── JS Backend (GraalJS) → ES Modules
```

---

# Princípios

1. Menos código, mesma capacidade
2. Tipagem forte
3. Intenção acima de cerimônia
4. Um frontend, múltiplos backends
5. Direto para o target
6. Interoperabilidade
7. Sem mágica desnecessária
8. Ferramentas importam

## O "paradigma" da intenção

Kof é **orientada à intenção** — o que não é um paradigma formal, e sim a
orientação a objetos levada ao extremo: o código expressa *o que* quer, e a
plataforma (linguagem + compilador + runtime + stdlib) decide *como*, por
target e por convenção.

```text
intenção → Kof → compilador → backend
```

Você escreve `spawn tarefa()` (não `Thread`), `app.get("/users/:id")` (não
servlet container), `Window`/`Button("+1", () -> ...)` (não WebView/JavaFX),
`json.decode<User>(body)` (não parser manual), `Palette.red` (não
`0xFF0000FF`). Se é essencial para qualquer programa, pertence à plataforma.

Quando um target não consegue realizar a intenção, ele diz isso em
compile-time com um código de gap (`CONC001`, `JSN002`, ...) — nunca
silenciosamente.

Detalhes: [docs/philosophy.md](docs/philosophy.md) · idiomas:
[training/idioms/](training/idioms/) · anti-padrões:
[training/anti-patterns/](training/anti-patterns/).

---

# O que Kof NÃO é

* Java com outra sintaxe.
* Kotlin 2.
* Julia para JVM.
* Um transpiler.
* Um gerador de Java.
* Um interpretador fantasiado de compilador (o compilador é real: bytecode/ELF/ESM; o `KofInterpreter` é um target adicional de execução direta, não um disfarce).

Kof é uma linguagem. Um compilador. Uma IR. Vários backends.

---

# Licença

Kof é software livre distribuído sob a licença **GNU General Public License v3.0**.

Isso se aplica ao código-fonte do compilador, ferramentas e demais componentes do projeto.

**Programas escritos em Kof NÃO são automaticamente GPLv3.**

O autor do programa mantém o direito de escolher a licença do próprio software. Usar o compilador Kof não obriga ninguém a abrir seu código-fonte.

Software proprietário escrito em Kof é permitido, desde que respeite as licenças das dependências que efetivamente incorporar.

Para mais detalhes, consulte [docs/LICENSING.md](docs/LICENSING.md).

---

**Kof**

*Uma linguagem. Um compilador. Vários mundos.*

*Menos cerimônia. Mais intenção.*
