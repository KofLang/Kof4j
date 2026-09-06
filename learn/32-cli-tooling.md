# 32 — CLI e Tooling

> **Kof 0.2.6-beta — 02 set 2026 — 810 testes — targets jvm/native/native.risc/native.arm/js/android + kofc**

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
| `kof run <file.kf> [--target jvm|native|native.risc|native.arm|js]` | Compila e executa |
| `kof script <file.ks|kf> [--watch] [--target ...]` | KofScript direto (Kof puro; `var`/`val` de topo → `KofScriptGlobals`) + diagnostics com file:line |
| `kof repl` | REPL incremental KofScript (type `exit` to quit) |
| `kof c <file.c> [--run] [--output <bin>]` | KofC C subset → ELF x86-64 nativo-only |
| `kof serve <file.kf>` | Web server HTTP (`web.app()` nativo + API legada `handle()`) |
| `kof check <file.kf\|dir>` | Type-check sem emitir código |
| `kof test <file.kf\|dir> [--target jvm|native|js]` | Suíte estruturada `test "nome" { assert(...) }` nos 3 targets + programas inteiros por exit code |
| `kof bench [paths...] [--target ...] [--iterations N] [--baseline <file>] [--threshold <ratio>] [--json] [--fail-on-regression]` | Benchmark harness (compile, run, validate, métricas, baseline) |
| `kof profile <file.kf> [--target ...]` | Execução + métricas (CPU, RSS, GC) |
| `kof inspect <file.kf> [--json]` | Estatísticas da IR: ops antes/depois da otimização |
| `kof config gen <file.kf\|dir> [--output <arquivo>]` | Gera template `kof.config` a partir das chaves `config.*` do código |
| `kof fmt <file.kf\|dir> [-w]` | Formatador real via parser (`KofFormatter`), idempotente — implementado em 31/08 |
| `kof debug <file.kf> [--target jvm]` | DAP MVP (breakpoints por linha Kof, stack trace) |
| `kof info [--json]` | Relatório do ambiente |
| `kof lsp` | Language Server (stdio, LSP 3.x) |
| `kof install <dir>` | Instala este build como distribuição (launcher + `kof.jar`) |
| `kof version` | Versão da plataforma (`0.2.6-beta`) |

Todos os comandos seguem `intention->Kof->frontend->IR->backend->runtime`.

## `kof info`

Diagnóstico oficial do ambiente — para usuários e suporte:

```text
Kof 0.2.6-beta
Release channel: beta
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 25.0.4 (embedded)
Compiler: 0.2.6-beta
Runtime: 0.2.6-beta
Stdlib: 0.2.6-beta
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
- `kof profile <file.kf>` — execução + métricas (CPU, RSS, GC).
- `kof inspect <file.kf> [--json]` — estatísticas da IR: ops antes/depois
  da otimização.

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
