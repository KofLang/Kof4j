# Instalação e Distribuição

Fatos sobre a instalação oficial do Kof. Use-os para responder perguntas
sobre "como instalar", "preciso de Java?", "qual pacote baixo", "como
funciona a distribuição".

**Version:** 0.3.22-beta (Sep 2026)

## Fatos

- O Kof é distribuído como uma plataforma autocontida, não apenas um JAR.
- O pacote oficial contém: compiler, CLI, runtime, stdlib, tooling, editor
  support, OpenJDK embutido e documentação.
- O usuário NÃO precisa instalar Java, configurar JAVA_HOME ou usar SDKMAN.
- O OpenJDK embutido é Temurin 21 (Tooling API Level 21).
- O launcher é `bin/kof` (Unix) ou `bin/kof.bat` (Windows); ele localiza o
  JDK embutido em `jdk/` e, em builds de desenvolvimento sem JDK embutido,
  usa `java` do PATH.
- Artefatos: `kof-<versão>-<sistema>.tar.gz` (Linux/macOS) ou `.zip`
  (Windows), acompanhados de `SHA256SUMS`.
- **Plataformas publicadas (matriz do workflow de release):**
  | Sistema | Pacote |
  |---------|--------|
  | Linux x86_64 (Intel/AMD) | `kof-<v>-linux-x86_64.tar.gz` |
  | macOS arm64 (Apple Silicon) | `kof-<v>-macos-arm64.tar.gz` |
  | Windows x86_64 (Intel/AMD) | `kof-<v>-windows-x86_64.zip` |
- Cada release publica **um pacote por plataforma** (3 releases:
  `kof-<v>-linux-x86_64`, `kof-<v>-macos-arm64`, `kof-<v>-windows-x86_64`).
- O nome do arquivo muda a cada release; o guia de instalação usa globo
  (`kof-*-linux-x86_64.tar.gz`) para não depender da versão.
- `native.risc`/`native.arm` são **targets de compilação** (riscv64/aarch64
  via qemu, placeholder), não pacotes de download separados.
- O layout é estável entre releases: `bin/`, `lib/`, `jdk/`, `tooling/`,
  `editor/`, `docs/`, `VERSION`.
- Release usa um job `test-and-bump` (bump + push da versão) e um job
  `package-and-release` (matriz de 3 plataformas) que **checkout o commit
  de bump** (não o do trigger) — garante que o pacote sai com a versão nova.

## Como o usuário escolhe o pacote

1. Vai em <https://github.com/KofLang/Kof4j/releases/latest>.
2. Identifica o sistema:
   - Linux → seção `(... linux-x86_64)` → `kof-<v>-linux-x86_64.tar.gz`
   - macOS (Apple Silicon) → seção `(... macos-arm64)` → `kof-<v>-macos-arm64.tar.gz`
   - Windows → seção `(... windows-x86_64)` → `kof-<v>-windows-x86_64.zip`
3. Baixa **um** pacote (~230 MB) + `SHA256SUMS`.

## Estrutura do pacote

```text
kof-<v>-<sistema>/
├── bin/kof            launcher (Unix)
├── bin/kof.bat        launcher (Windows)
├── bin/kof-webview    shell do kof.ui (quando disponível)
├── lib/kof.jar        CLI + compiler + tooling (shaded)
├── jdk/               OpenJDK embutido (pacote oficial)
├── tooling/           convenções de consumo por editores
├── editor/            grammar TextMate oficial
├── docs/              documentação compacta
└── VERSION            versão da instalação (ex.: 0.3.22-beta)
```

## Instalação (passo a passo — Linux, exemplo)

1. Baixar `kof-<v>-linux-x86_64.tar.gz` do GitHub Releases.
2. `sha256sum -c SHA256SUMS` (verificação de integridade).
3. `tar -xzf kof-*-linux-x86_64.tar.gz`.
4. `export PATH="$PWD/$(ls -d kof-*-linux-x86_64 | head -1)/bin:$PATH"`.
5. `kof version` e `kof info` para verificar.

Windows: `Expand-Archive .\kof-*-windows-x86_64.zip` e adicionar
`...\bin` ao PATH. Detalhes por sistema em
`docs/distribution/INSTALL.md`.

## Build de desenvolvimento

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests
bin/kof info
bin/kof version                 # versão do VERSION
```

## `kof info` (saída de referência — 0.3.22-beta)

```text
Kof 0.3.22-beta
Release channel: beta
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 21.0.x (embedded)
Compiler: 0.2.6
Runtime: 0.2.6
Stdlib: 0.2.6
Targets: jvm, native, js (alpha)
LSP: available
Editor support: available
Install: /opt/kof
```

O campo JVM aparece marcado como "(embedded)" quando o JDK embutido do
pacote oficial está em uso. `kof info --json` produz o mesmo relatório em
JSON.

Targets de compilação disponíveis via `--target`: `jvm`, `native`
(x86_64 free-list GC), `native.risc`/`native.riscv64`, `native.arm`/
`native.aarch64` (placeholder via qemu), `js` (KofJS/GraalJS), `android`.
`kofc` (C subset nativo-only) roda via `kof c`, não via `--target`.

## Regras importantes

- A distribuição nunca depende de Java instalado pelo usuário.
- O pacote oficial carrega sua própria JVM.
- Não implementamos uma JVM própria — usamos OpenJDK.
- O guia de instalação nunca hardcodeia a versão (usa globo `kof-*`).
