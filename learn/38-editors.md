# 38 — Editores: do `kof` instalado ao `.kof` aberto

> **Kof 0.3.0-beta — `intention->Kof->frontend->IR->backend->runtime`**

Instalar o Kof não é só ter compilador + runtime + CLI + stdlib. É abrir o
seu editor e já ter **highlighting, diagnostics, autocomplete, hover, rename,
formatação** — sem pesquisar "como configurar Kof no meu editor".

## A ideia em uma frase

Um comando detecta seus editores e instala a integração oficial de cada um;
todos apontam para o **mesmo** `kof lsp` — nenhum editor tem parser próprio.

```
                    kof lsp  (LSP 3.x, stdio)
        ┌───────────────┼───────────────┐
      VS Code        Neovim         IntelliJ
        │              │              │
       Vim           Emacs          outros
```

## Passo 1 — detectar

```bash
kof editor detect
```

```text
Kof Editor Integration

Detected editors:
  ✓ Visual Studio Code
  ✗ Vim
  ✓ Neovim
  ...

Available integrations:
  ✓ Kof for VS Code
  ✓ Kof for Neovim
  ...

Use:

    kof editor setup

to install recommended integrations.
```

A detecção usa PATH, executáveis conhecidos e diretórios de configuração —
funciona em Linux, macOS e Windows, sem assumir caminho fixo. Versão ilegível
vira `unknown`; o Kof **nunca inventa** versão.

## Passo 2 — setup (com consentimento)

```bash
kof editor setup
```

O `setup` lista os editores detectados sem integração, mostra as recomendações
e **pergunta antes de tocar no seu ambiente**:

```text
Recommended Kof integrations:
  [✓] Visual Studio Code
  [✓] Neovim

Install recommended integrations now? [Y/n]
```

Recusou? Nada muda, e ele diz como fazer depois. Em ambiente sem console
(CI, headless) ele não pergunta nem instala — só aponta o comando. A
instalação é **idempotente**: rodar de novo não duplica nada.

## Passo 3 — um editor por vez

```bash
kof editor install neovim
kof editor install vscode
kof editor install vim
kof editor install emacs
kof editor install geany
kof editor install nano
```

O que cada um escreve (tudo no seu HOME; `uninstall` remove só o que o Kof
criou):

| Editor | Arquivos | O que dá |
|---|---|---|
| VS Code | `~/.vscode/extensions/kof.kof/` | grammar TextMate + `package.json` com os comandos `Kof: Build/Run/Test/Check/Format/Serve/Select Target/Open Docs` |
| Neovim | `~/.config/nvim/ftdetect/kof.lua` + `after/ftplugin/kof.lua` | filetype + `vim.lsp.start` apontando para `kof lsp` |
| Vim | `~/.vim/ftdetect/kof.vim` + `after/{syntax,ftplugin,compiler}/kof.vim` | filetype, syntax, indent, `:make` → `kof build` |
| Emacs | `~/.emacs.d/lisp/kof-mode.el` | `kof-mode` + `auto-mode-alist`; LSP via `eglot` |
| Geany | `~/.config/geany/filedefs/filetypes.kof` | filetype, build/run, parsing de erros |
| Nano | `~/.nano/kof.nanorc` | highlighting proporcional ao editor (sem LSP — nano não é IDE) |

IntelliJ: o provider detecta, mas o plugin oficial é subprojeto próprio
(issue #1). Hoje: TextMate bundle + LSP4IJ apontando para `kof lsp`
(`docs/editors/intellij.md`).

## Passo 4 — status

```bash
kof editor status
```

```text
Kof Editor Environment

Kof:
  version: 0.3.0-beta
  compiler: OK
  LSP: OK (kof lsp)
  formatter: OK (kof fmt)
  debugger: PARTIAL (kof debug — DAP em evolução)

Editors:
✓ Visual Studio Code
  version: 1.102.3
  path: /usr/bin/code
  integration: available (installed)
...
```

`debugger: PARTIAL` é honesto: o DAP ainda está em evolução — a flag não
esconde isso.

## Passo 5 — abrir e programar

Abra um `.kf`/`.kof` num projeto com `kof.toml` na raiz. O LSP resolve o
workspace subindo até o manifesto e usa os source roots, dependências e
targets do projeto. Diagnostics aparecem enquanto você digita — são os
**mesmos** do compilador, nunca um parser paralelo que divergiria.

## O que viaja na distribuição

```text
kof/
├── editor/kof.tmLanguage.json   # grammar oficial (sem rede)
├── bin/kof                      # inclui editor + lsp + fmt + debug
└── docs/editors/                # um guia por editor
```

Quando a marketplace de um editor exigir download externo, usa-se o mecanismo
oficial do editor — o Kof nunca baixa código de URL arbitrária.

## Terminal é soberano

A integração é conveniência sobre a CLI. Você sempre pode:

```bash
kof build
kof run
kof test
kof serve
```

## Próximo passo

- Referência completa: `docs/editors/overview.md`
- Plano/arquitetura: `docs/development/plan-editor-integration.md` (EDI001)
- LSP: `docs/tooling/LSP.md`
