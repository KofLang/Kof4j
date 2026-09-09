# Neovim

Integração: filetype + LSP nativo (`vim.lsp`) apontando para `kof lsp`.
Nenhum parser próprio — semântica vem do LSP oficial.

## Instalação automática

```bash
kof editor install neovim
```

Escreve:

- `~/.config/nvim/ftdetect/kof.lua` — reconhece `*.kf`/`*.kof` como filetype `kof`.
- `~/.config/nvim/after/ftplugin/kof.lua` — comenta/indent básicos +
  `vim.lsp.start({ cmd = { "kof", "lsp" } })` com `root_dir` resolvido por
  `kof.toml`/`.git`.

## Instalação manual

```lua
vim.filetype.add({ extension = { kf = 'kof', kof = 'kof' } })
vim.api.nvim_create_autocmd('FileType', {
  pattern = 'kof',
  callback = function()
    vim.lsp.start({
      name = 'kof',
      cmd = { 'kof', 'lsp' },
      root_dir = vim.fs.dirname(vim.fs.find({ 'kof.toml', '.git' },
        { upward = true })[1]) or vim.fn.getcwd(),
    })
  end,
})
```

## Uso

- `gd` go-to-definition, `gr` references, `<leader>rn` rename, `K` hover —
  tudo via LSP.
- Formatação: `kof fmt <arquivo>` (ou `:silent !kof fmt %`).

## Troubleshooting

- `:LspInfo` deve mostrar `kof` ativo. Se não, confira `which kof`.
