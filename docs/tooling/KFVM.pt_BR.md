[English](KFVM.md) | [Português](KFVM.pt_BR.md)

# kfvm - Kof Version Manager

O `kfvm` instala, alterna e remove releases do Kof no Linux, macOS e Windows.

Guia completo de uso: [`tooling/kfvm/README.pt_BR.md`](../../tooling/kfvm/README.pt_BR.md).

# Como instalo?

### Linux | Macos

```bash
curl -fsSL https://raw.githubusercontent.com/KofLang/Kof4j/lab/tooling/kfvm/install.sh | sh
```

### Windows

```powershell
irm https://raw.githubusercontent.com/KofLang/Kof4j/lab/tooling/kfvm/install.ps1 | iex
```

Or from `cmd`:

```bat
powershell -c "irm https://raw.githubusercontent.com/KofLang/Kof4j/lab/tooling/kfvm/install.ps1 | iex"
```

## Comandos

| Comando | Alias | Efeito |
| --- | --- | --- |
| `kfvm list [-r, --remote]` | `ls` | versões instaladas, ou as do GitHub Releases com `-r` |
| `kfvm install <spec>` | `i` | baixa, verifica (`SHA256SUMS`) e extrai uma release |
| `kfvm use <spec>` | `u` | aponta `current` para uma versão, instalando antes se preciso |
| `kfvm uninstall <spec>` | `uni` | remove uma versão instalada (só local, nunca baixa) |

`<spec>` é `lts`/`latest` (estável mais nova), `nightly` (pré-release mais nova) ou uma versão
completa ou parcial (`0.4`, `0.4.9`, `v0.5.0-beta+2026.09.25`). Uma versão parcial corresponde
numa fronteira `.`, `-` ou `+`, então `0.4.1` nunca corresponde a `0.4.10`. A release final vence
as suas pré-releases.

## Layout

```
~/.local/share/kof/
├── kof-<ver>-<platform>/
└── current -> kof-<ver>-<platform>/
```

É o mesmo layout do `scripts/install.sh`, então cada ferramenta gerencia as instalações da outra.
`kfvm use` só move o `current`. No Windows, `current` é uma directory junction (sem precisar de
admin) e `current\bin` é adicionado ao `PATH` do usuário.

## Installers

O `tooling/kfvm/install.sh` (Linux/macOS) e o `tooling/kfvm/install.ps1` (Windows) fazem o seguinte:

1. encontram um Kof `>= KOF_MIN` (0.5.0), ou instalam um;
2. pegam o código do kfvm de `KFVM_SOURCE`, senão do `tooling/kfvm` dessa dist do Kof, senão
   fazem sparse-clone de `KFVM_PATH` em `KFVM_REPO@KFVM_REF` (com download do arquivo como fallback);
3. geram um binário nativo quando possível, senão `kfvm.jar` mais um launcher em `~/.local/bin`.

| Variável | Padrão |
| --- | --- |
| `KFVM_HOME` | `~/.local/share/kof` |
| `KFVM_DATA` | `~/.local/share/kfvm` |
| `KFVM_BIN_DIR` | `~/.local/bin` |
| `KFVM_SOURCE` | não definida |
| `KFVM_REPO` / `KFVM_REF` / `KFVM_PATH` | `KofLang/Kof4j` / `lab` / `tooling/kfvm` |
| `KOF_MIN` | `0.5.0` |
| `GITHUB_TOKEN` | não definida; aumenta o rate limit da API do GitHub |

## Testes

```bash
kof test tooling/kfvm/src/kfvm_test.kf
```
