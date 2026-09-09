# Suporte a Editores

O suporte de editores para Kof é distribuído com a própria linguagem:

- **Grammar TextMate** — `editor/kof.tmLanguage.json` (scope `source.kof`)
- **Language Server** — `kof lsp` (LSP 3.x sobre stdio)
- **Diagnostics** — os mesmos do compilador, via LSP ou `kof check`

Nenhum editor precisa de um parser próprio. O editor consome o tooling do Kof.

> **Instalação automática:** `kof editor setup` detecta seus editores e
> instala as integrações recomendadas (com consentimento). Documentação por
> editor em [`docs/editors/`](../editors/overview.md). Infra e plano:
> `docs/development/plan-editor-integration.md` (EDI001).

---

## VS Code

Crie uma extensão local apontando para a grammar oficial:

```json
// .vscode/extensions.json (ou extension/package.json)
{
  "contributes": {
    "languages": [{
      "id": "kof",
      "aliases": ["Kof", "kof"],
      "extensions": [".kf"],
      "configuration": "./language-configuration.json"
    }],
    "grammars": [{
      "language": "kof",
      "scopeName": "source.kof",
      "path": "./syntaxes/kof.tmLanguage.json"
    }]
  }
}
```

Para diagnostics em tempo de edição, configure o `kof lsp` como servidor de
linguagem (ex.: via extensão de cliente LSP genérica ou `vscode-languageserver-node`):

```json
{
  "command": ["kof", "lsp"]
}
```

## IntelliJ

- O IntelliJ consome grammars TextMate em `Settings → Editor → TextMate Bundles`.
- Para experiência completa, use um plugin LSP (ex.: LSP4IJ) apontando para
  `kof lsp`.

## Neovim

```lua
-- grammar via vim/helix-style TextMate é suportada por treesitter? Não —
-- para syntax highlighting use o plugin nvim-treesitter com um parser
-- dedicado OU o LSP para semântica.
vim.lsp.start({
  name = "kof",
  cmd = { "kof", "lsp" },
  root_dir = vim.fs.root(0, { "VERSION", ".git" }),
})
```

O caminho recomendado para Neovim é o LSP: highlights semânticos e
diagnostics vêm do frontend oficial, sem duplicar o parser.

## Editores LSP genéricos (Helix, Kakoune, Emacs Eglot, etc.)

Configure o comando `kof lsp` como language server para `source.kof`.

---

## Por que grammar + LSP e não um parser por editor?

Porque duplicar o parser em cada editor garante divergência: o editor
"aceitaria" código que o compilador rejeita e vice-versa. Com o LSP
consumindo o frontend real, o editor vê exatamente o que o compilador vê.

---

## O que viaja na distribuição

```text
kof/
├── tooling/           # este documento + convenções
├── editor/
│   └── kof.tmLanguage.json
└── bin/kof            # inclui o comando `lsp`
```