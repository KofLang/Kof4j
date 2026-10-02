[English](INSTALL.md) | [Português](INSTALL.pt_BR.md)

# Installing Kof

Official installation guide from the artifacts published on **GitHub
Releases**. Follow the step-by-step for **your system** and you are done.

> **Current version:** 0.5.0-beta (see `VERSION` at the repo root). This guide **does not depend on the
> version**: the commands work on any release, current or future.
> You do not need to know which version it is to install.

## Automated install (recommended — Linux/macOS)

The fastest way: run the installer directly via cURL, as provided by the installation script
in the repository (URL on the released `main` branch; measured 30/09 — the old `beta-0.4.0`
branch no longer exists and `main` now carries `scripts/install.sh`):

```bash
# install a specific version with environment variables
# and no args
curl -fsSL https://raw.githubusercontent.com/KofLang/Kof4j/main/scripts/install.sh \
  | KOF_INSTALL_VERSION=0.5.0-beta bash
```

or

```bash
# install a specific version with args
curl -fsSL https://raw.githubusercontent.com/KofLang/Kof4j/main/scripts/install.sh \
  | bash -s -- --version 0.5.0-beta --yes
```

What it does automatically:

1. Detects your platform (`linux-x86_64` / `macos-arm64`).
2. Resolves the latest release for your platform (or the `--version` you pin).
3. Downloads `kof-<version>-<platform>.tar.gz` and `SHA256SUMS`.
4. Verifies the SHA-256 checksum.
5. Extracts to `~/.local/share/kof/kof-<version>-<platform>` and keeps a
   `current` symlink.
6. Adds the absolute PATH line
   (`export PATH="<prefix>/current/bin:$PATH"`, where `<prefix>` defaults to
   `~/.local/share/kof`) to your `~/.zshrc` / `~/.bashrc` **idempotently**
   (it never duplicates the line).
7. Prints the `kof version` output as proof.

Options: `--prefix <dir>` (default `~/.local/share/kof`), `--yes` (no
prompts), `--no-modify-shell` (print the PATH line instead), `--uninstall`
(remove the install and the PATH lines), `--help`.

> The installer downloads the **official release binary** — no Java/Maven
> needed. Windows still follows the manual steps below.

---

## 0. What you need (and what you do NOT need)

- **Need:** a computer with Linux, macOS or Windows. Nothing more.
- **Do NOT need:** Java, JDK, Maven, Node.js or any other tool.
  The Kof package already comes with the **embedded OpenJDK**.

Kof is a **self-contained distribution**: compiler + CLI + runtime +
standard library + editor tooling + embedded JDK, all in a single file.

---

## 1. Choose the file for YOUR system

Each release publishes one package **per platform**. Download **only one** — the
one for yours:

| Your system | Package (extension) | Where it is on the page |
|-------------|-------------------|----------------------|
| **Linux** (Intel/AMD, 64-bit) | `.tar.gz` with `linux-x86_64` | in the release section `(... linux-x86_64)` |
| **macOS** (Apple Silicon M1/M2/M3…) | `.tar.gz` with `macos-arm64` | in the release section `(... macos-arm64)` |
| **Windows** (Intel/AMD, 64-bit) | `.zip` with `windows-x86_64` | in the release section `(... windows-x86_64)` |

**How to download:**

1. Open <https://github.com/KofLang/Kof4j/releases> (or
   <https://github.com/KofLang/Kof4j/releases/latest>).
2. Look at the **Latest** release (the most recent one). It lists 3 packages — one
   for each platform — with their attached files.
3. In the section for **your** system, click the `.tar.gz` (Linux/macOS)
   or `.zip` (Windows) file. It is about 230 MB.

> **File name:** the name changes every release
> (`kof-<version>-<system>.tar.gz`). Download the package file for your
> platform; do not worry about the version number — `kof version`
> shows the real version afterwards.

---

## 2. (Optional, recommended) Check the integrity

Each release brings a `SHA256SUMS` file with the code for each package.
Download it together and confirm that the downloaded file was not corrupted:

```bash
# Linux / macOS
sha256sum -c SHA256SUMS
# Windows (PowerShell)
Get-FileHash kof-*-windows-x86_64.zip -Algorithm SHA256
```

If `OK` appears (Linux/macOS) or the same hash listed in `SHA256SUMS`
(Windows), everything is fine. You can skip this step if you prefer.

---

## 3. Install

### 🐧 Linux

Open a terminal in the folder where you downloaded the file:

```bash
# 1) extract (the * catches the version, no matter which one)
tar -xzf kof-*-linux-x86_64.tar.gz

# 2) find the extracted folder and put it in PATH (this terminal)
DIR=$(ls -d kof-*-linux-x86_64 | head -1)
export PATH="$PWD/$DIR/bin:$PATH"

# 3) done!
kof version
```

For the PATH to apply **in all future terminals**, copy the `export` line
to the end of your `~/.bashrc` or `~/.zshrc` (replacing `$PWD/$DIR` with the
real absolute path of the folder):

```bash
echo 'export PATH="$HOME/<folder>/kof-*-linux-x86_64/bin:$PATH"' >> ~/.bashrc
```

> **Security note:** Kof runs as a normal user. If you want to
> install it in a fixed place, move the folder to `~/.local/share/` or
> `/opt/` and point `PATH` there.

### 🍎 macOS (Apple Silicon)

```bash
# 1) extract
tar -xzf kof-*-macos-arm64.tar.gz

# 2) PATH (this terminal)
DIR=$(ls -d kof-*-macos-arm64 | head -1)
export PATH="$PWD/$DIR/bin:$PATH"

# 3) done!
kof version
```

For a permanent PATH, add it to `~/.zshrc` (macOS default):

```bash
echo 'export PATH="$HOME/<folder>/kof-*-macos-arm64/bin:$PATH"' >> ~/.zshrc
```

> If macOS warns about the file, just click **Open** once in the
> Privacy Settings — the package is **not Apple-signed** (no codesign in the
> pipeline today). Trust anchors on the `SHA256SUMS` digest verified above;
> build provenance attested by the official workflow is the ratified contract
> `DECISIONS.md` §D-ARTIFACT-TRUST (enforcement queue landing), never a
> present-tense claim.

### 🪟 Windows

Open **PowerShell** in the folder where you downloaded the `.zip`:

```powershell
# 1) extract
Expand-Archive .\kof-*-windows-x86_64.zip

# 2) find the folder and put it in PATH (this session)
$DIR = (Get-ChildItem -Directory -Filter "kof-*-windows-x86_64" | Select-Object -First 1).FullName
$env:PATH = "$DIR\bin;$env:PATH"

# 3) done!
kof version
```

For a **permanent** PATH (all sessions), add the `...\bin` folder
to the Windows **System Variables → PATH**:

1. `Win + R` → `sysdm.cpl` → **Advanced** tab → **Environment Variables**.
2. Under **System variables**, edit `Path` → **New** → paste
   `C:\...\kof-<version>-windows-x86_64\bin`.
3. OK, OK. Reopen PowerShell and run `kof version`.

---

## 4. Verify it worked

```bash
kof version
```

Expected output (the number is that of your release):

```
kof 0.5.0-beta
```

Full environment report:

```bash
kof info
```

Expected output (summary):

```
Kof 0.5.0-beta
Release channel: beta
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 25.x (embedded)
Compiler: 0.2.6
Runtime: 0.2.6
Stdlib: 0.2.6
Targets: jvm, native, js (alpha)
LSP: available
Editor support: available
Install: /caminho/onde/esta/kof-...-linux-x86_64
```

If `kof info` shows `JVM: ... (embedded)`, the embedded JDK is in use —
**no external Java installation was needed**.

---

## 5. What you received

```
kof-<version>-<system>/
├── bin/
│   ├── kof            # launcher (Linux/macOS)
│   ├── kof.bat        # launcher (Windows)
│   └── kof-webview    # kof.ui shell (when available)
├── lib/
│   └── kof.jar        # compiler + runtime + stdlib + GraalJS
├── jdk/               # embedded OpenJDK 25 (official release)
├── editor/            # grammar + editor support
├── tooling/           # reusable language definitions
├── docs/              # embedded documentation
└── VERSION            # the exact version of this installation
```

Main commands already available (details in
[learn/32-cli-tooling.md](../../learn/32-cli-tooling.md)):

| Command | What it does |
|---------|-----------|
| `kof run app.kf` | compiles and runs (JVM by default) |
| `kof build <dir|file.kf> [--target ...]` | compiles to jvm / native / native.risc / native.arm / js / android |
| `kof serve app.kf` | starts a `web.app()` app |
| `kof test <dir>` | runs the test suite |
| `kof deploy <dir|file.kf> [--target jvm|native|native.riscv64|native.aarch64|js|android[,..]|all] [--publish [<owner/repo>]]` | packages a self-contained release: artifact + `RELEASE.md` + `SHA256SUMS` + `.tar.gz` (JS ships its runtime closure, §298); comma-list/`all` deploys the SAME source to several targets with one `.deploy-manifest.json` per run (8.4); `--publish` uploads the artifact(s) to GitHub Releases (`GH_TOKEN`, D2-A); cross riscv64/aarch64 package the cross ELF too (X9 fatia 6; without the cross toolchain an honest failure names the tool — `KOF_CROSS_PREFIX` overrides the `riscv64-`/`aarch64-` tool prefix) |
| `kof check <dir>` | type-check without emitting code |
| `kof script <f.kf>` / `kof repl` | direct execution / REPL |
| `kof fmt <f.kf>` | formats the code |
| `kof info` / `kof version` | environment / version |

---

## 6. Update

1. Download the package for the new release (same step 1).
2. Extract it alongside (not over it).
3. Point `PATH` to the new folder and run `kof version`.
4. Delete the old version folder whenever you want.

The layout is stable between releases — there is no "migrate" step.

---

## 7. Troubleshooting

| Symptom | Probable cause | Fix |
|---------|----------------|-----------|
| `kof: command not found` (Linux/macOS) | `PATH` not updated in this terminal | Reopen the terminal, or run the `export PATH=...` from step 3 again |
| `'kof' is not recognized` (Windows) | `bin` outside the PATH | Follow step 2.5 (permanent PATH) and **reopen** PowerShell |
| `kf: distribution incomplete` | incomplete extracted folder | Re-download and extract again; check the checksum (step 2) |
| Old version when running `kof version` | there is another `bin` earlier in `PATH` | `which kof` (Linux/macOS) / `Get-Command kof` (Windows) and fix the PATH order |
| `tar: Unknown option` | wrong `tar` used on Windows | On Windows use `Expand-Archive` (PowerShell) |
| macOS security block | Gatekeeper warning | Privacy Settings → allow the app once |

---

## 8. Build from source (developers)

Only for those who want to contribute or test `main`:

**Prerequisites:** JDK 25 (Temurin) and Maven 3.9+. For the `native`
target: binutils (`as`/`ld`).

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests

# use straight from source (uses the system java, not the embedded one)
mkdir -p lib
cp kof-cli/target/kof-cli-$(cat VERSION).jar lib/kof.jar
bin/kof version
bin/kof info

# package the official distribution (downloads the embedded JDK)
scripts/package.sh --jdk
```

Versioning and packaging: [VERSIONING.md](VERSIONING.md) and
[PACKAGING.md](PACKAGING.md).
