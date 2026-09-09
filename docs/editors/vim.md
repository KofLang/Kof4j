# Vim

Integração: filetype + syntax + compiler, idiomáticos de Vim. Semântica via
LSP (com um plugin de cliente LSP, ex.: `vim-lsp`).

## Instalação automática

```bash
kof editor install vim
```

Escreve:

- `~/.vim/ftdetect/kof.vim` — `*.kf`/`*.kof` → filetype `kof`.
- `~/.vim/after/syntax/kof.vim` — highlight por palavras-chave/tipos.
- `~/.vim/after/ftplugin/kof.vim` — `commentstring`, `expandtab`, `shiftwidth`.
- `~/.vim/after/compiler/kof.vim` — `:make` delega a `kof build`;
  `errorformat` casa `arquivo:linha:coluna: msg` (os diagnósticos do Kof).

## Uso

```vim
:setfiletype kof
:make            " kof build
```

Para LSP, registre `kof lsp` no seu cliente (ex.: vim-lsp):

```vim
if executable('kof')
  au User lsp_setup call lsp#register_server({
    \ 'name': 'kof',
    \ 'cmd': {server_info->['kof','lsp']},
    \ 'allowlist': ['kof'],
    \ })
endif
```

## Troubleshooting

- Sem highlight: `:syntax list kofKeyword` deve listar os padrões.
