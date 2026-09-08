# Geany

Integração: filetype com syntax, indentação e build/run commands que delegam
à CLI.

## Instalação automática

```bash
kof editor install geany
```

Escreve `~/.config/geany/filedefs/filetypes.kof`:

- comentários `//` e `/* */`, extensão `.kf`, nome "Kof";
- `[build]`: `compiler=kof build`, `execute=kof run`;
- `[error_messages]`: regex que casa `arquivo:linha:coluna: error/warning: msg`
  — os diagnósticos do compilador aparecem clicáveis na barra de mensagens.

## Uso

- `F8` compila (`kof build`), `F5` executa (`kof run`).
- Erros/warnings navegam até a linha no editor (error parsing).

## Troubleshooting

- Se o filetype não aparecer: `Document → Set Filetype → Kof`.
- Ajuste o caminho do `kof` no arquivo `.conf` se ele não estiver no PATH.
