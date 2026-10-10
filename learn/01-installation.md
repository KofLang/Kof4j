[English](01-installation.md) | [Português](01-installation.pt_BR.md)

# 01 — Installation

> **Kof 0.5.0-beta — Sep 2026.** This guide does not depend on the version: the
> commands work on any release.

## What Kof is (and what you do NOT need to install)

Kof is a **self-contained distribution**. The official package already brings
the compiler, CLI, runtime, standard library, editor tooling **and an
embedded OpenJDK**.

- **DO NOT** install Java, Maven, Node.js or anything beforehand.
- You do **NOT** need to know the version to install.

## Step 1 — Download the package for YOUR system

> **Easiest (Linux/macOS):** the installer detects your platform and the
> newest release automatically — there is no version to type:
>
> ```bash
> curl -fsSL https://raw.githubusercontent.com/KofLang/Kof4j/main/scripts/install.sh | bash
> ```
>
> Pin a version only if you want a specific one:
> `... | KOF_INSTALL_VERSION=<version> bash`. Manual steps below.

Kof publishes **one release per platform**, so the GitHub "Latest" label
points at a single platform. Open <https://github.com/KofLang/Kof4j/releases>
and, in the newest release whose name ends with **your** platform, download
the package:

| Your system | Download the file with |
|-------------|---------------------|
| **Linux** (64-bit) | `linux-x86_64.tar.gz` |
| **macOS** (Apple Silicon) | `macos-arm64.tar.gz` |
| **macOS** (Intel) | `macos-x86_64.tar.gz` |
| **Windows** (64-bit) | `windows-x86_64.zip` |

The file is ~230 MB. The name starts with `kof-<version>-<system>` — the
version changes with each release; `kof version` shows which one it is afterward.

> Not sure which one is yours? Run `uname -m` (Linux/macOS: `x86_64` =
> Intel/AMD, `arm64` = Apple Silicon) or, on Windows, check in
> **Settings → System → About** (most current PCs are
> `x64` = Intel/AMD).

## Step 2 — Extract and activate

### Linux

```bash
tar -xzf kof-*-linux-x86_64.tar.gz                       # extracts
DIR=$(ls -d kof-*-linux-x86_64 | head -1)                # finds the folder
export PATH="$PWD/$DIR/bin:$PATH"                        # activates
kof version                                              # checks
```

To make it permanent, add it to `~/.bashrc` (or `~/.zshrc`):

```bash
echo 'export PATH="$HOME/<folder>/kof-*-linux-x86_64/bin:$PATH"' >> ~/.bashrc
```

### macOS (Apple Silicon)

```bash
tar -xzf kof-*-macos-arm64.tar.gz
DIR=$(ls -d kof-*-macos-arm64 | head -1)
export PATH="$PWD/$DIR/bin:$PATH"
kof version
```

To make it permanent, add it to `~/.zshrc`:

```bash
echo 'export PATH="$HOME/<folder>/kof-*-macos-arm64/bin:$PATH"' >> ~/.zshrc
```

### macOS (Intel)

```bash
tar -xzf kof-*-macos-x86_64.tar.gz
DIR=$(ls -d kof-*-macos-x86_64 | head -1)
export PATH="$PWD/$DIR/bin:$PATH"
kof version
```

To make it permanent, add it to `~/.zshrc`:

```bash
echo 'export PATH="$HOME/<folder>/kof-*-macos-x86_64/bin:$PATH"' >> ~/.zshrc
```

### Windows (PowerShell)

```powershell
Expand-Archive .\kof-*-windows-x86_64.zip                # extracts
$DIR = (Get-ChildItem -Directory -Filter "kof-*-windows-x86_64" |
        Select-Object -First 1).FullName                 # finds the folder
$env:PATH = "$DIR\bin;$env:PATH"                          # activates
kof version                                               # checks
```

To make it permanent: **Environment Variables → PATH → New** →
`C:\...\kof-<version>-windows-x86_64\bin`. Reopen PowerShell afterward.

## Step 3 — Check

```bash
kof version        # e.g.: kof <version>
kof info           # full environment (embedded JVM, targets, installation)
```

If `kof info` shows `JVM: ... (embedded)`, the embedded JDK is in use —
no external Java was needed.

## Your first program

Create `main.kf`:

```kf
main() {
    println("Hello, World!")
}
```

Run:

```bash
kof run main.kf              # JVM (default)
kof run main.kf --target=native   # ELF x86-64 binary
kof run main.kf --target=js       # embedded GraalJS
```

Output:

```
Hello, World!
```

## Platform targets

`kof build`/`run` accept `--target`:

| Target | What it generates | Note |
|--------|-----------|------------|
| `jvm` (default) | `.class` | stable |
| `native` | ELF x86-64 | stable; needs `as`/`ld` only in the source |
| `native.risc` | ELF riscv64 | placeholder (qemu) |
| `native.arm` | ELF aarch64 | placeholder (qemu) |
| `js` | ES Modules | alpha (embedded GraalJS) |
| `android` | Android project + APK | phase 1 |

The chain `intention → Kof → IR → backend → runtime` is the same for all —
`--target` only swaps the backend.

## CLI commands (summary)

| Command | Description |
|---------|-----------|
| `kof run <f.kf> [--target ...] [args]` | compiles and executes |
| `kof build <dir|file.kf> [--target ...]` | compiles to the target |
| `kof serve <f.kf>` | starts a web app (`web.app()`) |
| `kof test <f.kf\|dir>` | runs tests |
| `kof check <f.kf\|dir> [--target <t>]` | type-check without emitting (target-aware gaps) |
| `kof script <f.kf>` / `kof repl` | direct execution / REPL |
| `kof fmt <f.kf>` | formats |
| `kof info` / `kof version` | environment / version |
| `kof lsp` | Language Server (stdio) |

Details: [32-cli-tooling.md](32-cli-tooling.md).

## Build from source (contributors)

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests
mkdir -p lib && cp kof-cli/target/kof-cli-$(cat VERSION).jar lib/kof.jar
bin/kof version
```

In development builds, the launcher uses the system `java` (JDK 25+ — the repo toolchain baseline; the compiled Kof *program* still targets JVM 21+).
In the official package, the embedded JDK is used automatically.

## Common problems

| Symptom | Fix |
|---------|-----------|
| `kof: command not found` | `PATH` is not active — run the `export PATH=...` again or reopen the terminal |
| `'kof' is not recognized` (Windows) | add `...\bin` to the permanent PATH and **reopen** PowerShell |
| Wrong version | `which kof` (Linux/macOS) / `Get-Command kof` (Windows) — another `bin` is earlier in PATH |

## References

- [docs/distribution/INSTALL.md](../docs/distribution/INSTALL.md) — complete official guide
- [docs/distribution/ARCHITECTURE.md](../docs/distribution/ARCHITECTURE.md)

## Next step

[First Program →](02-first-program.md)
