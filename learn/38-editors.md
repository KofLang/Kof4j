[English](38-editors.md) | [Português](38-editors.pt_BR.md)

# 38 — Editors: from the installed `kof` to the open `.kof`

> **Kof 0.5.0-beta — `intention->Kof->frontend->IR->backend->runtime`**

Installing Kof is not just having compiler + runtime + CLI + stdlib. It is
opening your editor and already having **highlighting, diagnostics,
autocomplete, hover, rename, formatting** — without searching for "how to
configure Kof in my editor".

## The idea in one sentence

One command detects your editors and installs the official integration for each
one; all of them point to the **same** `kof lsp` — no editor has its own parser.

```
                    kof lsp  (LSP 3.x, stdio)
        ┌───────────────┼───────────────┐
      VS Code        Neovim         IntelliJ
        │              │              │
       Vim           Emacs          others
```

## Step 1 — detect

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

Detection uses PATH, known executables and configuration directories —
it works on Linux, macOS and Windows, without assuming a fixed path. An
unreadable version becomes `unknown`; Kof **never invents** a version.

## Step 2 — setup (with consent)

```bash
kof editor setup
```

`setup` lists the detected editors without integration, shows the
recommendations and **asks before touching your environment**:

```text
Recommended Kof integrations:
  [✓] Visual Studio Code
  [✓] Neovim

Install recommended integrations now? [Y/n]
```

Declined? Nothing changes, and it tells you how to do it later. In an
environment without a console (CI, headless) it neither asks nor installs — it
just points to the command. The installation is **idempotent**: running it
again duplicates nothing.

## Step 3 — one editor at a time

```bash
kof editor install neovim
kof editor install vscode
kof editor install vim
kof editor install emacs
kof editor install geany
kof editor install nano
```

What each one writes (all in your HOME; `uninstall` removes only what Kof
created):

| Editor | Files | What it gives |
|---|---|---|
| VS Code | `~/.vscode/extensions/kof.kof/` | TextMate grammar + `package.json` with the commands `Kof: Build/Run/Test/Check/Format/Serve/Select Target/Open Docs` |
| Neovim | `~/.config/nvim/ftdetect/kof.lua` + `after/ftplugin/kof.lua` | filetype + `vim.lsp.start` pointing to `kof lsp` |
| Vim | `~/.vim/ftdetect/kof.vim` + `after/{syntax,ftplugin,compiler}/kof.vim` | filetype, syntax, indent, `:make` → `kof build` |
| Emacs | `~/.emacs.d/lisp/kof-mode.el` | `kof-mode` + `auto-mode-alist`; LSP via `eglot` |
| Geany | `~/.config/geany/filedefs/filetypes.kof` | filetype, build/run, error parsing |
| Nano | `~/.nano/kof.nanorc` | highlighting proportional to the editor (no LSP — nano is not an IDE) |

IntelliJ: `kof editor install intellij` writes filetype XML (`*.kf`/`*.kof`)
+ External Tools (`kof build/run/test/fmt/check/lsp`) + LSP4IJ README under
`.config/JetBrains/kof/` (step 10, 13/09 — no plugin; the official plugin is a
subproject of its own, issue #1). Extra manual: TextMate bundle + LSP4IJ
(`docs/editors/intellij.md`).

## Step 4 — status

```bash
kof editor status
```

```text
Kof Editor Environment

Kof:
  version: 0.5.0-beta
  compiler: OK
  LSP: OK (kof lsp)
  formatter: OK (kof fmt)
  debugger: PARTIAL (kof debug — DAP in progress)

Editors:
✓ Visual Studio Code
  version: 1.102.3
  path: /usr/bin/code
  integration: available (installed)
...
```

`debugger: PARTIAL` is honest: DAP is still in progress — the flag does not
hide that.

## Step 5 — open and program

Open a `.kf`/`.kof` in a project with `kof.toml` at the root. The LSP resolves
the workspace by walking up to the manifest and uses the project's source roots,
dependencies and targets. Diagnostics appear as you type — they are the
**same** ones as the compiler, never a parallel parser that would diverge.

## What ships in the distribution

```text
kof/
├── editor/kof.tmLanguage.json   # official grammar (no network)
├── bin/kof                      # includes editor + lsp + fmt + debug
└── docs/editors/                # one guide per editor
```

When an editor's marketplace requires an external download, the editor's
official mechanism is used — Kof never downloads code from an arbitrary URL.

## The terminal is sovereign

The integration is convenience on top of the CLI. You can always:

```bash
kof build
kof run
kof test
kof serve
```

## Next step

- Full reference: `docs/editors/overview.md`
- Plan/architecture: `docs/tooling/PLAN-EDITOR-INTEGRATION.md` (EDI001)
- LSP: `docs/tooling/LSP.md`
