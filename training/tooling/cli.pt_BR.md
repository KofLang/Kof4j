[English](cli.md) | [Português](cli.pt_BR.md)

# CLI e Tooling

Fatos sobre a CLI oficial do Kof. Use para responder perguntas sobre
comandos, tooling e editor support.

**Version:** 0.5.0-beta (Sep 2026) — 3225 testes

## Comandos oficiais (26)

| Comando | Comportamento |
|---------|---------------|
| `kof build <dir\|file.kf> [--target jvm\|native\|native.risc\|native.arm\|js\|android] [--output <dir>] [--release] [--apk]` | Compila |
| `kof run <file.kf\|dir> [--target jvm\|native\|native.risc\|native.arm\|js\|android] [args...]` | Compila e executa |
| `kof serve <file.kf> [--port <port>] [--host <host>]` | Web server HTTP básico. `--port`/`--host` valem só no modo legacy (`handle`); app kof-native (`app.listen`) define a própria porta e a CLI avisa (#35.3) |
| `kof check <file.kf\|dir> [--target <t>]` | Type-check sem emitir código (gaps por alvo) |
| `kof test <file.kf\|dir> [--target jvm\|native\|js] [--timeout <sec>] [--tag <tag>]` | Suíte estruturada `test "nome", "tag" { }`: PASS/FAIL por teste; `--tag` filtra o catálogo em compile-time (mesmo harness em todo alvo); funções `setup`/`teardown` pulam (nomeado) ou finalizam testes; arquivos sem testes rodam inteiros (PASS = exit 0); um dir desce em **suítes nomeadas** (uma por diretório) |
| `kof script <file.ks> [--target jvm\|native\|js] [--watch] [--inspect] [args...]` | KofScript: JIT com top-level `var`/`val` → KofScriptGlobals, repl, cache 64 LRU |
| `kof repl` | Alias para `kof script` interativo |
| `kof c <file.c> [-o outDir]` | KofCcompiler: C subset nativo-only → ELF x86_64 |
| `kof fmt <file.kf\|dir>` | Formatter via parser real (`KofFormatter`), idempotente |
| `kof init <nome>` | Inicializa projeto (`main.kf` + `tests/`) |
| `kof config gen <file.kf\|dir> [--target jvm\|native\|js] [--output <arquivo>]` | Gera template `kof.config` a partir das chaves `config.*` do código |
| `kof info [--json]` | Relatório do ambiente (inclui native.risc/arm, kofc) |
| `kof lsp` | Language Server (stdio, LSP 3.x) — hover/completion + .ks preprocess |
| `kof editor <list\|detect\|status\|setup\|install\|uninstall\|update>` | Integração de editores (EDI001): detecta VS Code/Vim/Neovim/IntelliJ/Geany/Nano/Emacs e instala a integração oficial (grammar + `kof lsp`), com consentimento. `install <editor>` escreve só no HOME; `uninstall` remove só o que o Kof escreveu. Docs: `docs/editors/` |
| `kof version` | Versão da plataforma (0.5.0-beta) |
| `kof bench [...]` | Benchmark harness com baselines |
| `kof debug <file.kf>` | DAP MVP no JVM |
| `kof profile <file.kf> [--target ...]` | Execução + métricas (CPU, RSS, GC) |
| `kof inspect <file.kf> [--json]` | Estatísticas de IR: ops antes/depois da otimização |
| `kof decompile <file.class> [--output <file.kf>]` | Esqueleto estrutural Kof de um `.class` |
| `kof translate <file.java> [--output <file.kf>]` | Subset Java → código Kof |
| `kof compare <legacy.class\|jar> <file.kf> [--json]` | Teste diferencial legado vs Kof |
| `kof migrate <file.class\|java> [--output <file.kf>] [--json]` | Migração + relatório rastreável |
| `kof new <name>` | Esqueletos de projeto por tipo |
| `kof deps <init\|add\|remove\|list\|resolve>` | Gerenciador de pacotes (`kofdeps`: Maven `g:a:v` + registry `owner/repo[@ver]` — GitHub Releases) |
| `kof install <dir>` | Instala este build como distribuição (launcher + `kof.jar`) |

`kof fmt` (parser real, idempotente) e `kof config gen` são implementados
(0.5.0-beta). Não existe comando `kof doctor` — o diagnóstico oficial é
`kof info`.

## KofScript

```bash
kof script app.ks --target jvm --watch --inspect
var x = 5
// top-level var/val → KofScriptGlobals static fields
```

## KofCcompiler

```bash
kof c app.c
# int globals, void funcs, if/while, *(int*), & → ELF x86_64 via as/ld
```

## Tooling API Level

- O baseline de API Java do tooling é 21.
- O Kof não exige Java anterior a 21 para seu tooling.
- O toolchain do repo exige JDK 25 (D-BASELINE, 14/09): compilar o repo e
  rodar a CLI são JDK 25. O **API level** do tooling segue 21
  (`KofVersion.TOOLING_API`); programas Kof seguem JVM 21+.
- O pacote oficial carrega sua própria JVM (Temurin 25).

## Editor support

- O suporte de editores viaja com a distribuição.
- Grammar oficial: `editor/kof.tmLanguage.json` (scope `source.kof`),
  consumível por VS Code, IntelliJ (TextMate) e highlighters compatíveis.
- Semântica e diagnostics: `kof lsp` — qualquer editor LSP (VS Code,
  IntelliJ via LSP4IJ, Neovim, Helix, Eglot).
- **Regra: nunca duplicar o parser em um editor.** O editor consome o
  tooling do Kof; o LSP consome o frontend real do compilador.
- LSP agora suporta `.ks` (KofScript) pelo mesmo frontend real — top-level `var`/`val`, sem dialeto JS.

## LSP

- `kof lsp` implementa LSP 3.x sobre stdio (framing Content-Length, JSON-RPC 2.0).
- Mensagens: initialize, initialized, shutdown, exit, didOpen, didChange,
  publishDiagnostics, hover, completion.
- Diagnostics são produzidos pelo CompilerDriver real — os mesmos códigos e
  mensagens de `kof check`/`kof build` (inclui `a.b.C` import fix 27/08).
- Sync de documentos: completa (change: 1).
- Sem parser paralelo: editor e compilador sempre concordam.

## `kof info`

Informa: versão do Kof (0.5.0-beta), versão do compiler/runtime/stdlib, tooling API level, target/arquitetura, SO, JVM embutida, versão da
JVM, targets disponíveis (jvm, native, native.risc, native.arm, js, kofc) e localização da instalação.
Legível por humanos; `--json` para formato estruturado.

## Regras importantes

- Preservar `kof build`, `kof install` (compatibilidade), `kof run`, `kof serve`, `kof script`, `kof c`.
- Não criar `kof doctor` — o comando de diagnóstico é `kof info`.
- Formatter, config gen e test runner são consumidos do frontend oficial, sem
  implementações paralelas.
