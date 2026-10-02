[English](README.md) | [Português](README.pt_BR.md)

# Kof Tooling — consumido por editores e ferramentas

Este diretório viaja dentro da distribuição oficial do Kof (`tooling/`).

O tooling do Kof é parte da plataforma: syntax definition, language server,
formatter e diagnostics são distribuídos com a linguagem e consomem o mesmo
frontend do compilador (Lexer → Parser → Symbol Table → Type System →
Diagnostics).

## O que existe

| Componente | Onde | Uso |
|-----------|------|-----|
| Grammar TextMate | `editor/kof.tmLanguage.json` (scope `source.kof`) | VS Code, IntelliJ, highlighters TextMate |
| Language Server | `kof lsp` (LSP 3.x, stdio) | qualquer editor com cliente LSP |
| Type-check | `kof check` | CLI |
| Diagnóstico de ambiente | `kof info [--json]` | CLI / suporte |
| Version manager | `tooling/kfvm` (`kfvm ls\|i\|u\|uni`, só JVM) | instalar e alternar versões do Kof |

## Arquivos de referência

- `editor/kof.tmLanguage.json` — grammar oficial reutilizável.
- Documentação completa: `docs/tooling/` na raiz do repositório.

## Como um editor consome

1. Grammar: aponte para `editor/kof.tmLanguage.json` com scope `source.kof`.
2. Semântica: configure `kof lsp` como language server.

Nunca duplicar o parser da linguagem em um editor — o editor consome o
tooling do Kof.