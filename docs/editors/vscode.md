# VS Code

Integração: extensão local com grammar TextMate + comandos; semântica via
`kof lsp`.

## Instalação automática

```bash
kof editor install vscode
```

Escreve em `~/.vscode/extensions/kof.kof/`:

- `syntaxes/kof.tmLanguage.json` — a grammar oficial (lida da distribuição;
  fallback embutido se ausente — nunca baixa da internet).
- `language-configuration.json` — comentários, colchetes, auto-close.
- `package.json` — registra a linguagem (`*.kf`/`*.kof`, `source.kof`) e os
  comandos `Kof: Build/Run/Test/Check/Format/Serve/Select Target/Open Docs`.

Depois, recarregue o VS Code. Para o LSP, configure o cliente (ex.: extensão
"vscode-languageserver-node" ou similar) com o comando `["kof", "lsp"]`.

## Instalação manual

Copie a pasta da extensão para `~/.vscode/extensions/` e aponte o cliente LSP
para `kof lsp`.

## Troubleshooting

- **Sem highlight:** confirme que a extensão foi carregada (`Developer: Show
  Running Extensions`).
- **Sem diagnostics:** o LSP precisa estar rodando — teste `kof lsp` no
  terminal (deve esperar em stdio, não erro).
