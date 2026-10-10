[English](README.md) | [Português](README.pt_BR.md)

# Kof Tooling — consumed by editors and tools

This directory ships inside the official Kof distribution (`tooling/`).

Kof's tooling is part of the platform: syntax definition, language server,
formatter and diagnostics are distributed with the language and consume the same
compiler frontend (Lexer → Parser → Symbol Table → Type System →
Diagnostics).

## What exists

| Component | Where | Use |
|-----------|------|-----|
| Grammar TextMate | `editor/kof.tmLanguage.json` (scope `source.kof`) | VS Code, IntelliJ, TextMate highlighters |
| Language Server | `kof lsp` (LSP 3.x, stdio) | any editor with an LSP client |
| Type-check | `kof check` | CLI |
| Environment diagnostics | `kof info [--json]` | CLI / support |
| Version manager | `tooling/kfvm` (`kfvm ls\|i\|u\|uni`, JVM-only) | install and switch Kof versions |

## Reference files

- `editor/kof.tmLanguage.json` — official reusable grammar.
- Full documentation: `docs/tooling/` at the repository root.

## How an editor consumes it

1. Grammar: point to `editor/kof.tmLanguage.json` with scope `source.kof`.
2. Semantics: configure `kof lsp` as the language server.

Never duplicate the language parser in an editor — the editor consumes Kof's
tooling.
