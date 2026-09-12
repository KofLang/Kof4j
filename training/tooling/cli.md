# CLI e Tooling

Fatos sobre a CLI oficial do Kof. Use para responder perguntas sobre
comandos, tooling e editor support.

**Version:** 0.3.22-beta (Sep 2026) — 810 tests

## Comandos oficiais (18)

| Comando | Comportamento |
|---------|---------------|
| `kof build <dir> [--target jvm\|native\|native.risc\|native.arm\|js\|android] [--output <dir>] [--release] [--apk]` | Compila |
| `kof run <file.kf\|dir> [--target jvm\|native\|native.risc\|native.arm\|js\|android] [args...]` | Compila e executa |
| `kof serve <file.kf> [--port <port>] [--host <host>]` | Web server HTTP básico. `--port`/`--host` valem só no modo legacy (`handle`); app kof-native (`app.listen`) define a própria porta e a CLI avisa (#35.3) |
| `kof check <file.kf\|dir>` | Type-check sem emitir código |
| `kof test <file.kf\|dir> [--target jvm\|native\|js]` | Suíte estruturada `test "nome" { }`: PASS/FAIL por teste; arquivos sem testes rodam inteiros (PASS = exit 0) |
| `kof script <file.ks> [--target jvm\|native\|js] [--watch] [--inspect] [args...]` | KofScript: JIT com top-level `let` → KofScriptGlobals, repl, cache 64 LRU |
| `kof repl` | Alias para `kof script` interativo |
| `kof c <file.c> [-o outDir]` | KofCcompiler: C subset nativo-only → ELF x86_64 |
| `kof fmt <file.kf\|dir>` | Formatter via parser real (`KofFormatter`), idempotente |
| `kof init <nome>` | Inicializa projeto (`main.kf` + `tests/`) |
| `kof config gen <file.kf\|dir> [--target jvm\|native\|js] [--output <arquivo>]` | Gera template `kof.config` a partir das chaves `config.*` do código |
| `kof info [--json]` | Relatório do ambiente (inclui native.risc/arm, kofc) |
| `kof lsp` | Language Server (stdio, LSP 3.x) — hover/completion + .ks preprocess |
| `kof editor <list\|detect\|status\|setup\|install\|uninstall\|update>` | Integração de editores (EDI001): detecta VS Code/Vim/Neovim/IntelliJ/Geany/Nano/Emacs e instala a integração oficial (grammar + `kof lsp`), com consentimento. `install <editor>` escreve só no HOME; `uninstall` remove só o que o Kof escreveu. Docs: `docs/editors/` |
| `kof version` | Versão da plataforma (0.3.22-beta) |
| `kof bench [...]` | Benchmark harness com baselines |
| `kof debug <file.kf>` | DAP MVP no JVM |

`kof fmt` (parser real, idempotente) e `kof config gen` são implementados
(0.3.22-beta). Não existe comando `kof doctor` — o diagnóstico oficial é
`kof info`.

## KofScript

```bash
kof script app.ks --target jvm --watch --inspect
let x = 5
// top-level let/const → KofScriptGlobals static fields
```

## KofCcompiler

```bash
kof c app.c
# int globals, void funcs, if/while, *(int*), & → ELF x86_64 via as/ld
```

## Tooling API Level

- O baseline de API Java do tooling é 21.
- O Kof não exige Java anterior a 21 para seu tooling.
- Versões posteriores (ex.: 25, Virtual Threads) podem ser usadas
  internamente sem virar requisito.
- O pacote oficial carrega sua própria JVM (Temurin 21).

## Editor support

- O suporte de editores viaja com a distribuição.
- Grammar oficial: `editor/kof.tmLanguage.json` (scope `source.kof`),
  consumível por VS Code, IntelliJ (TextMate) e highlighters compatíveis.
- Semântica e diagnostics: `kof lsp` — qualquer editor LSP (VS Code,
  IntelliJ via LSP4IJ, Neovim, Helix, Eglot).
- **Regra: nunca duplicar o parser em um editor.** O editor consome o
  tooling do Kof; o LSP consome o frontend real do compilador.
- LSP agora suporta `.ks` (KofScript) com preprocess `let` → `var` e wrap `main()`.

## LSP

- `kof lsp` implementa LSP 3.x sobre stdio (framing Content-Length, JSON-RPC 2.0).
- Mensagens: initialize, initialized, shutdown, exit, didOpen, didChange,
  publishDiagnostics, hover, completion.
- Diagnostics são produzidos pelo CompilerDriver real — os mesmos códigos e
  mensagens de `kof check`/`kof build` (inclui `a.b.C` import fix 27/08).
- Sync de documentos: completa (change: 1).
- Sem parser paralelo: editor e compilador sempre concordam.

## `kof info`

Informa: versão do Kof (0.3.22-beta), versão do compiler/runtime/stdlib, tooling API level, target/arquitetura, SO, JVM embutida, versão da
JVM, targets disponíveis (jvm, native, native.risc, native.arm, js, kofc) e localização da instalação.
Legível por humanos; `--json` para formato estruturado.

## Regras importantes

- Preservar `kof build`, `kof install` (compatibilidade), `kof run`, `kof serve`, `kof script`, `kof c`.
- Não criar `kof doctor` — o comando de diagnóstico é `kof info`.
- Formatter, config gen e test runner são consumidos do frontend oficial, sem
  implementações paralelas.
