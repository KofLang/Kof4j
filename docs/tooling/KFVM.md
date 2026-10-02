[English](KFVM.md) | [Português](KFVM.pt_BR.md)

# kfvm - Kof Version Manager

`kfvm` installs, switches and removes Kof releases on Linux, macOS and Windows. It lives in
`tooling/kfvm`

Full user guide: [`tooling/kfvm/README.md`](../../tooling/kfvm/README.md).

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

## Commands

| Command | Alias | Effect |
| --- | --- | --- |
| `kfvm list [-r, --remote]` | `ls` | installed versions, or the ones on GitHub Releases with `-r` |
| `kfvm install <spec>` | `i` | download, verify (`SHA256SUMS`) and extract a release |
| `kfvm use <spec>` | `u` | point `current` at a version, installing it first if needed |
| `kfvm uninstall <spec>` | `uni` | remove an installed version (local only, never downloads) |

`<spec>` is `lts`/`latest` (newest stable), `nightly` (newest pre-release) or a full or partial
version (`0.4`, `0.4.9`, `v0.5.0-beta+2026.09.25`). A partial version matches on a `.`, `-` or `+`
boundary, so `0.4.1` never matches `0.4.10`. A final release beats its pre-releases.

## Layout

```
~/.local/share/kof/
├── kof-<ver>-<platform>/
└── current -> kof-<ver>-<platform>/
```

This is the same layout as `scripts/install.sh`, so each tool manages the other's installs.
`kfvm use` only moves `current`. On Windows, `current` is a directory junction (no admin rights
needed) and `current\bin` is added to the user `PATH`.

## Installers

`tooling/kfvm/install.sh` (Linux/macOS) and `tooling/kfvm/install.ps1` (Windows) do the following:

1. find a Kof `>= KOF_MIN` (0.5.0), or install one;
2. take the kfvm source from `KFVM_SOURCE`, else from the `tooling/kfvm` of that Kof dist, else
   by sparse-cloning `KFVM_PATH` from `KFVM_REPO@KFVM_REF` (with an archive download as fallback);
3. build a native binary when possible, otherwise `kfvm.jar` plus a launcher in `~/.local/bin`.

| Variable | Default |
| --- | --- |
| `KFVM_HOME` | `~/.local/share/kof` |
| `KFVM_DATA` | `~/.local/share/kfvm` |
| `KFVM_BIN_DIR` | `~/.local/bin` |
| `KFVM_SOURCE` | unset |
| `KFVM_REPO` / `KFVM_REF` / `KFVM_PATH` | `KofLang/Kof4j` / `lab` / `tooling/kfvm` |
| `KOF_MIN` | `0.5.0` |
| `GITHUB_TOKEN` | unset; raises the GitHub API rate limit |

## Tests

```bash
kof test tooling/kfvm/src/kfvm_test.kf
```
