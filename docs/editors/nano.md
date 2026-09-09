# Nano

Integração **proporcional ao editor**: só syntax highlighting e reconhecimento
de arquivo. Nano não é IDE — semântica (diagnostics/LSP) não se aplica aqui;
para isso use um editor LSP.

## Instalação automática

```bash
kof editor install nano
```

Escreve `~/.nano/kof.nanorc` com cores para palavras-chave, tipos, constantes,
strings e comentários.

## Configuração

Garanta que o nano lê o diretório de syntax (Linux: `nano` já procura
`~/.nano/*.nanorc`; em outras plataformas, adicione ao seu `~/.nanorc`):

```
include "~/.nano/kof.nanorc"
```

## Uso

```bash
nano Arquivo.kf
```

Compile/rode pela CLI (`kof build` / `kof run`) — o nano é só edição.
