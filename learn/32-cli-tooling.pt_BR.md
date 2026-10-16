[English](32-cli-tooling.md) | [Português](32-cli-tooling.pt_BR.md)

# 32 — CLI e Tooling

> **Kof 0.5.0-beta — set 2026 — targets jvm/native/native.risc/native.arm/js/android + kofc**

A CLI é a ferramenta central da plataforma Kof.

## Comandos

| Comando | O que faz |
|---------|-----------|
| `kof build <dir>` | Compila para JVM (padrão) |
| `kof build <dir> --target=native` | Compila para ELF x86-64 |
| `kof build <dir> --target=native.risc` | Compila para ELF riscv64 |
| `kof build <dir> --target=native.arm` | Compila para ELF aarch64 |
| `kof build <dir> --target=js` | Compila para ES Modules |
| `kof build <dir> --target=android` | Gera projeto Maven + APK (Fase 1: host Activity em Kof; `mvn verify` / `--apk` com o SDK) |
| `kof run <file.kf> [--target jvm|native|native.risc|native.arm|js] [--jvm-option <opt>]` | Compila e executa. O **diretório do arquivo é o módulo**: todo irmão `.kf` é compilado junto (funções de topo cross-file resolvem como um pacote — então `PKG005` nome duplicado / `PKG002` `main()` duplicado se aplicam). Arquivos independentes que só dividem o diretório pertencem a diretórios próprios; o isolamento por arquivo é o `kof test` por design (cross-file é domínio do `kof build`) |
| `kof script <file.ks|kf> [--watch] [--target ...]` | KofScript direto (Kof puro; `var`/`val` de topo → `KofScriptGlobals`) + diagnostics com file:line |
| `kof repl` | REPL incremental KofScript (type `exit` to quit) |
| `kof c <file.c> [--run] [--output <bin>]` | KofC C subset → ELF x86-64 nativo-only |
| `kof serve <file.kf>` | Web server HTTP (`web.app()` nativo + API legada `handle()`) |
| `kof check <file.kf\|dir> [--target <t>] [--json]` | Type-check sem emitir código (gaps por alvo, ex.: `AND002` no android) |
| `kof test <file.kf\|dir> [--target jvm|native|js]` | Suíte estruturada `test "nome" { assert(...) }` nos 3 targets + programas inteiros por exit code; um diretório desce em **suítes nomeadas** (uma por diretório) |
| `kof deploy <dir|file.kf> [--target jvm|native|js|android] [--output <dir>] [--name <n>] [--version <v>] [--jvm-option <opt>]` | Empacota uma release autocontida: artefato (fat jar / ELF 0755 / `Default.mjs` + fecho do runtime / APK assinado) + `RELEASE.md` + `SHA256SUMS` + `.tar.gz`; cross riscv64/aarch64 e `--publish` recusam honesto com `DEP001` |
| `kof bench [paths...] [--target ...] [--iterations N] [--baseline <file>] [--threshold <ratio>] [--json] [--fail-on-regression]` | Benchmark harness (compile, run, validate, métricas, baseline) |
| `kof profile <file.kf> [--target ...] [--methods]` | Execução + métricas (CPU, RSS, GC); `--methods`: profiler de **amostragem** method-level próprio (JFR da própria JDK no JVM, `--cpu-prof` do Node no JS) com hot spots mapeados de volta à linha `.kf` |
| `kof inspect <file.kf> [--json]` | Estatísticas da IR: ops antes/depois da otimização |
| `kof decompile <file.class> [--output <file.kf>]` | Esqueleto Kof estrutural de um `.class` |
| `kof translate <file.java> [--output <file.kf>]` | Subset Java → fonte Kof |
| `kof compare <legacy.class\|jar> <file.kf> [--json]` | Teste diferencial legacy vs Kof |
| `kof migrate <file.class\|java> [--output <file.kf>] [--json]` | Migração + relatório rastreável |
| `kof config gen <file.kf\|dir> [--output <arquivo>]` | Gera template `kof.config` a partir das chaves `config.*` do código |
| `kof fmt <file.kf\|dir> [-w]` | Formatador real via parser (`KofFormatter`), idempotente — implementado em 31/08 |
| `kof debug [--dap] <file.kf> [--target jvm|native] [--attach <pid>]` | DAP no JVM (breakpoints por linha Kof, stack trace, locals reais — JDWP cru reconstruído contra o JDK 25, §376); no `native`: sem flag delega ao **gdb** sobre o DWARF Kof (X7-3 `cfa67238`), com `--dap` atende editores pela ponte DAP↔gdb/MI (X7-4 `bda631a7`); `--attach <pid>` REAL nas duas faces (X7-5 `81401629`: no JVM o disconnect NÃO mata o debuggee — `KofDebugAttachTest`); `js` recusa honesto (engine embutido sem inspector) |
| `kof new <name>` | Esqueletos de projeto por tipo |
| `kof init` | Inicializa um projeto no diretório atual |
| `kof deps <init\|add\|remove\|list\|resolve>` | Gerenciador de pacotes (`kofdeps`: Maven `g:a:v` + registry `owner/repo[@ver]` — GitHub Releases) |
| `kof editor <list\|detect\|status\|setup\|install\|uninstall\|update>` | Integração com editores (EDI001) |
| `kof info [--json]` | Relatório do ambiente |
| `kof lsp` | Language Server (stdio, LSP 3.x) |
| `kof install <dir>` | Instala este build como distribuição (launcher + `kof.jar`) |
| `kof version` | Versão da plataforma (`<revision>`) |

Todos os comandos seguem `intention->Kof->frontend->IR->backend->runtime`.

## `kof info`

Diagnóstico oficial do ambiente — para usuários e suporte:

```text
Kof 0.5.0-beta
Release channel: beta
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 25.0.4 (embedded)
Compiler: 0.5.0-beta
Runtime: 0.5.0-beta
Stdlib: 0.5.0-beta
Targets: jvm, native, js (alpha)
LSP: available
Editor support: available
Install: /opt/kof
```

(`parseTarget` aceita também `native.risc`/`native.arm`/`android` — o
relatório resume os targets de runtime principais.)

Formato estruturado: `kof info --json`.

## `kof check`

Executa o pipeline completo (Lexer → Parser → Análise Semântica) e reporta
todos os erros, sem emitir código. É a mesma checagem que o LSP publica.
`--target <t>` checa contra um alvo específico, então gaps específicos do
alvo são reportados sem build (ex.: `kof check app.kf --target android` acusa
`web.app()` como `AND002`). Flags desconhecidas são recusadas com exit 1
(nunca ignoradas em silêncio). Com a flag `--json` (`kof check <file.kf|dir> --json`), emite os diagnósticos
em formato JSON estruturado para automação e integração contínua (CI/CD).

## `kof script` e `kof c` (0.2.0)

```bash
kof script demo.ks                 # var/val no topo → KofScriptGlobals
kof script demo.ks --watch         # re-executa ao salvar
kof script --repl                  # REPL incremental (exit para sair)
kof c hello.c --run                # C subset nativo-only (GAS+LD)
kof c hello.c --output ./bin
```

`KofScript` reaproveita o frontend real (`lexer→parser→AST→IR`) e o backend escolhido. **KofScript é Kof puro executado direto — não é JavaScript**: não há `let`/`const`/`async`/`fn`. O único serviço do wrapper é o modelo de script: `var x=5` no topo vira `class KofScriptGlobals { static Int x=5 }` e statements soltos viram `main(){…}`.

## `kof fmt` e `kof config gen` (31/08)

```bash
kof fmt src/                  # formata e imprime (dry-run)
kof fmt src/ -w               # reescreve os arquivos in-place
kof config gen src/           # gera template kof.config a partir das chaves config.*
```

- `kof fmt` formata via parser real (`KofFormatter`) — o resultado é
  idempotente (rodar duas vezes não muda nada).
- `kof config gen` extrai as chaves `config.*` do código e gera um
  template `kof.config` pronto para edição (precedência: `KOF_CONFIG` >
  env `KOF_<KEY>` > perfil > `kof.config`).

## `kof bench`, `kof profile` e `kof inspect`

- `kof bench [paths...] [--iterations N] [--baseline <file>]
  [--update-baseline <file>] [--threshold <ratio>] [--json]
  [--fail-on-regression]` — compila, executa, valida o stdout contra
  `expected.txt`, mede tempo (mediana) e RSS e compara com o baseline
  (`PERFORMANCE REGRESSION` acima do threshold; CI usa `--threshold 1.20`).
- `kof profile <file.kf> [--methods]` — execução + métricas (CPU, RSS, GC).
  `--methods` é o profiler de **amostragem** method-level (residual 8.3, fechado
  20/09): a face JVM grava `jdk.ExecutionSample` com o JFR da própria JDK e a face
  JS roda o módulo emitido sob `--cpu-prof` do Node; o relatório mapeia o hot spot
  de volta à **função Kof com a linha `.kf`** (LineNumberTable / `.mjs.map` — nunca
  bytecode cru), o overhead do próprio sampler `jdk.jfr.internal` é filtrado e uma
  gravação curta demais para conter amostra é diagnóstico honesto.
- `kof inspect <file.kf> [--json]` — estatísticas da IR: ops antes/depois
  da otimização.

## `kof deploy` (X9, 18/09)

Empacota um **artefato publicável** a partir do pipeline do build — a unidade
que você distribui é o `.tar.gz` ao lado do diretório `deploy/`, e ele nunca é
fingido:

```bash
kof deploy ./app --target jvm             # fat jar + RELEASE.md + SHA256SUMS
kof deploy ./app --target native          # ELF x86-64 (mode 0755 no tar)
kof deploy ./app --target js --name api   # Default.mjs + fecho de runtime (roda com `node` puro)
kof deploy ./app --target android         # APK assinado (reusa o pipeline --apk do build)
# -> deploy/api-<version>.tar.gz          # + sha256 impresso no stdout
```

Toda release é **autocontida**: a face JS embarca o entry junto com os módulos
`./kof-runtime*.mjs` que ele importa (§298), então o `node <name>.mjs` impresso
no `RELEASE.md` funciona num diretório limpo. `--publish <registry>` é
**recusado honesto com `DEP001`** até a mantenedora decidir o registry de
release (decisão **D2** do plano); o mesmo vale para os cross riscv64/aarch64 —
fatias seguintes em `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
(X9). Android recusa `DEP001` apenas quando falta ANDROID_HOME ou as build-tools não têm um d8 que leia o bytecode do artefato (a CLI escolhe a maior versão instalada; >= 35.0.0 para Java 21)
(guarda honesta de ambiente, nunca um APK fake). Nunca exit 0 sem artefato
real, nunca fake-publish (R6).

### Pacotes são consumidos como módulos-FONTE (#566)

Uma release publicada com `kof deploy --publish` também leva as **fontes** do módulo
(`src/<caminho>.kf`, cada uma coberta pelo `SHA256SUMS`; só `.kf`/`.kof`, nunca `tests/`,
diretórios ocultos ou de saída). Um módulo sem `.kf` no topo — só uma árvore de pacotes —
é uma **biblioteca**: é compilada para validar e a release leva só as fontes (sem
artefato executável).

```bash
# produtor:  src/mylib/Thing.kf  (package mylib)
kof deploy ./lib --name mylib --version 1.2.0 --publish owner/mylib

# consumidor
kof deps add owner/mylib@1.2.0
kof deps resolve                     # instala as fontes VERIFICADAS (SHA256SUMS) no cache
kof run Main.kf --deps               # `import mylib.Thing` resolve contra as fontes instaladas
kof run Main.kf --jvm-option -Xmx512m   # opcao da JVM no caminho de DESENVOLVIMENTO (repetivel)
kof run Main.kf --jvm-option=-Dstdout.encoding=UTF-8
kof build src --target js --deps     # as MESMAS fontes compilam para qualquer alvo
```

O `import` procura no seu módulo primeiro, depois nas bibliotecas oficiais, depois nas
fontes das dependências instaladas — uma dependência nunca sombreia a biblioteca padrão.
Sem `--deps` o import é um `PKG006` honesto. Pacotes publicados antes desta mudança (só
jar) continuam funcionando como antes (o jar vai ao classpath).

## `kof lsp`

Language Server que consome o **frontend real do compilador**. Os
diagnósticos do editor são exatamente os do compilador — não existe parser
paralelo.

```bash
kof lsp   # lê stdin, escreve stdout (LSP)
```

## Editor support

O tooling de editores viaja com a distribuição:

- `editor/kof.tmLanguage.json` — grammar TextMate oficial (scope `source.kof`);
- `kof lsp` — semântica e diagnostics em qualquer editor LSP (VS Code,
  IntelliJ via LSP4IJ, Neovim, Helix, Eglot, etc.).

Nunca duplique o parser em um editor: consuma o tooling do Kof. Target separation (`native.risc`/`native.arm`) já aparece no `kof info` e no `parseTarget`.

## Referências

- [docs/tooling/README.md](../docs/tooling/README.md)
- [docs/tooling/EDITOR_SUPPORT.md](../docs/tooling/EDITOR_SUPPORT.md)
- [docs/tooling/LSP.md](../docs/tooling/LSP.md)
