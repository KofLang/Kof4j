[English](README.md) | [Português](README.pt_BR.md)

# kfvm — Kof Version Manager

Install, switch between and remove versions of the [Kof](https://github.com/KofLang/Kof4j) toolchain from the command line, on Linux, macOS and Windows.

```console
$ kfvm i lts        # install the latest stable release
$ kfvm i 0.4        # install the newest 0.4.x (0.4.10-beta)
$ kfvm u 0.4.8      # make it the active one, installing it first if needed
$ kof version
```

kfvm is part of the Kof tooling and lives in `tooling/kfvm` of the Kof4j repository. It downloads the official Kof releases published on [GitHub Releases](https://github.com/KofLang/Kof4j/releases). It is a JVM tool: it runs on the embedded JDK of an installed Kof version.

## Why

Kof ships as a self-contained distribution (compiler, CLI, runtime, stdlib and an embedded JDK), and each release lives in its own folder. Switching between them by hand means downloading tarballs, extracting them side by side and repointing your `PATH`. kfvm does that for you, which is handy when you need to:

- check whether a bug reproduces across releases before reporting it;
- try a nightly pre-release without losing your stable setup;
- go back to an older version when an upgrade breaks something.

## Installation

### Linux and macOS

```bash
curl -fsSL https://raw.githubusercontent.com/KofLang/Kof4j/lab/tooling/kfvm/install.sh | sh
```

The script builds kfvm from source. If no Kof 0.5.0 or newer is found, it first installs one into `~/.local/share/kof`. It uses the kfvm source shipped in that Kof distribution (`tooling/kfvm`) when there is one, and otherwise downloads `tooling/kfvm` from the Kof4j repository. When Kof can compile kfvm into a native binary, that binary is installed as `~/.local/bin/kfvm`. Otherwise kfvm is installed as `~/.local/share/kfvm/kfvm.jar` plus a launcher in `~/.local/bin/kfvm`, which runs on the embedded JDK of an installed Kof version. If `~/.local/bin` is not in your `PATH`, the script adds it to your shell config.

### Windows

In PowerShell:

```powershell
irm https://raw.githubusercontent.com/KofLang/Kof4j/lab/tooling/kfvm/install.ps1 | iex
```

Or from `cmd`:

```bat
powershell -c "irm https://raw.githubusercontent.com/KofLang/Kof4j/lab/tooling/kfvm/install.ps1 | iex"
```

Requires Windows 10 (1803 or newer) or Windows 11, which ship `curl.exe` and `tar.exe`. Kof publishes Windows builds for x86_64 only.

`install.ps1` does the same as `install.sh`, with the same layout under `%USERPROFILE%`: Kof versions go to `%USERPROFILE%\.local\share\kof`, kfvm is installed as `%USERPROFILE%\.local\share\kfvm\kfvm.jar` plus the launcher `%USERPROFILE%\.local\bin\kfvm.cmd`, and `%USERPROFILE%\.local\bin` is added to your user `PATH`. Open a new terminal after installing.

When kfvm activates a Kof version (`kfvm i` or `kfvm u`), it adds `%USERPROFILE%\.local\share\kof\current\bin` to your user `PATH`, so `kof` works in any new terminal.

### Install manually

```bash
git clone https://github.com/KofLang/Kof4j
cd Kof4j
KFVM_SOURCE=tooling/kfvm sh tooling/kfvm/install.sh
```

On Windows:

```powershell
git clone https://github.com/KofLang/Kof4j
cd Kof4j
$env:KFVM_SOURCE = 'tooling\kfvm'; powershell -ExecutionPolicy Bypass -File tooling\kfvm\install.ps1
```

## Usage

```bash
kfvm -h
```

```
kfvm ls,  list [-r, --remote]          list installed versions (or available ones)
kfvm i,   install <ver|lts|nightly>    install a version
kfvm u,   use <ver|lts|nightly>        switch the active version (installs it if needed)
kfvm uni, uninstall <ver|lts|nightly>  remove an installed version
```

Every command has a short alias, so `kfvm install 0.4.10` and `kfvm i 0.4.10` are the same thing.

### Listing versions

```bash
kfvm ls           # versions installed on this machine
kfvm ls -r        # versions available on GitHub Releases
```

### Installing

```bash
kfvm i lts        # latest stable release (same as latest)
kfvm i nightly    # latest pre-release
kfvm i 0.4        # newest 0.4.x release
kfvm i 0.4.10     # a specific version
```

### Switching

```bash
kfvm u 0.4.10
kof version
```

If the version is not installed yet, `kfvm u` downloads and installs it first. If GitHub cannot be reached (offline, or rate-limited without a `GITHUB_TOKEN`), it falls back to the versions already installed.

### Removing

```bash
kfvm uni 0.4.10
```

On Windows, kfvm runs on the JDK of the active Kof version and files in use cannot be deleted, so switch to another version (`kfvm u <ver>`) before removing the active one.

## Version specifiers

`kfvm i`, `kfvm u` and `kfvm uni` accept the same specifiers. `kfvm i` and `kfvm u` look them up in the releases published on GitHub (`kfvm u` skips the lookup when you pass the full name of an installed version, and falls back to the installed versions when GitHub cannot be reached). `kfvm uni` only looks at the versions installed on this machine and never downloads anything.

| Specifier | Meaning |
|---|---|
| `lts`, `latest` | The newest stable release. |
| `nightly` | The newest pre-release. |
| `<ver>` | A full or partial version, resolved as described below. A leading `v` is ignored (`v0.4` is `0.4`). |

A version that matches a release exactly selects that release. Otherwise it selects the newest release that starts with it, where the match has to end at a `.`, `-` or `+`, so `0.4.1` never matches `0.4.10`. You can leave out the minor or patch number, the `-beta` suffix and the `+date` build. When a final release and its pre-releases match, the final release wins (`0.4.9` is newer than `0.4.9-beta`).

With these releases published:

```
0.5.0-beta+2026.09.26
0.5.0-beta+2026.09.25
0.4.10-beta
0.4.9-beta
...
```

| You type | kfvm picks | Why |
|---|---|---|
| `lts` | `0.4.10-beta` | the newest stable release (the 0.5.0 builds are pre-releases) |
| `nightly` | `0.5.0-beta+2026.09.26` | the newest pre-release |
| `0.4` | `0.4.10-beta` | the newest 0.4.x; there is no 0.4 release without `-beta` |
| `0.4.9` | `0.4.9-beta` | the only 0.4.9 release |
| `0.4.9` | `0.4.9` | if a final `0.4.9` is published, it matches exactly |
| `0.5` | `0.5.0-beta+2026.09.26` | the newest 0.5.x build |
| `0.5.0-beta+2026.09.25` | `0.5.0-beta+2026.09.25` | an exact match, even though a newer build exists |

## How it works

Each version is extracted into its own folder under the install directory, and a `current` symlink points to the active one:

```
~/.local/share/kof/
├── kof-0.4.10-beta-macos-arm64/
├── kof-0.5.0-beta-macos-arm64/
└── current -> kof-0.5.0-beta-macos-arm64/
```

`kfvm use` only moves the `current` link. Nothing is copied or rebuilt, so switching is instant, and each version keeps its own embedded JDK and standard library.

This is the same layout used by Kof's official installer (`scripts/install.sh`), so kfvm can manage versions that were installed with it, and vice versa.

On Windows the layout lives under `%USERPROFILE%\.local\share\kof`, versions end in `-windows-x86_64`, and `current` is a directory junction, which needs neither administrator rights nor Developer Mode. The `kof` launcher of each version is `bin\kof.bat`.

## Running a specific version without switching

Every installed version can be called directly by its path, which is useful for comparing behavior between releases:

```bash
~/.local/share/kof/kof-0.4.10-beta-macos-arm64/bin/kof fmt main.kf | diff main.kf -
~/.local/share/kof/kof-0.5.0-beta-macos-arm64/bin/kof fmt main.kf | diff main.kf -
```

## Uninstalling kfvm

Remove the Kof versions you no longer need with `kfvm uni`, then delete `~/.local/bin/kfvm`, `~/.local/share/kfvm` and the `PATH` line from your shell config. To remove every Kof version as well, delete the install directory:

```bash
rm -rf ~/.local/share/kof
```

On Windows, delete `%USERPROFILE%\.local\bin\kfvm.cmd` and `%USERPROFILE%\.local\share\kfvm`, and remove `%USERPROFILE%\.local\bin` and `%USERPROFILE%\.local\share\kof\current\bin` from your user `PATH`. To remove every Kof version as well:

```powershell
Remove-Item -Recurse -Force "$env:USERPROFILE\.local\share\kof"
```

## License

Copyright (C) 2026 Emerson A. Tieppo Jr.

kfvm is free software, released under the [GNU General Public License v3.0](LICENSE), the same license as Kof.
