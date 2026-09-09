# Emacs

Integração: `kof-mode` (highlight por palavras-chave) + LSP via `eglot`.
Nenhum parser próprio.

## Instalação automática

```bash
kof editor install emacs
```

Escreve `~/.emacs.d/lisp/kof-mode.el` — define `kof-mode` (prog-mode),
syntax table (comentários `//` e `/* */`, strings), `font-lock` por
palavra-chave e `auto-mode-alist` para `*.kf`/`*.kof`.

## Configuração

```elisp
(add-to-list 'load-path "~/.emacs.d/lisp")
(require 'kof-mode)
(with-eval-after-load 'eglot
  (add-to-list 'eglot-server-programs '(kof-mode . ("kof" "lsp"))))
```

Abrir um `.kof` ativa `kof-mode`; `M-x eglot` conecta ao `kof lsp`
(diagnostics, completion, hover, rename, references).

## Troubleshooting

- `M-: (require 'kof-mode)` deve carregar sem erro.
- `M-x eglot` na buffer `.kof` deve mostrar conexão com o servidor.
