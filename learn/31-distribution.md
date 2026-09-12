# 31 — Distribuição

> **Kof 0.3.22-beta — set 2026 — targets jvm/native/native.risc/native.arm/js/android + kofc**

## Kof é uma plataforma, não apenas um JAR

A partir do 0.2.x-beta, o Kof se comporta como uma linguagem distribuível:

```text
Kof 0.3.22-beta
        ├── Compiler
        ├── CLI (build/run/serve/check/test/script/repl/c/fmt/config gen/bench/
        │    profile/inspect/debug/info/lsp/install/version)
        ├── Runtime (JVM + Native free-list + JS + KofScript + KofC)
        ├── Standard Library (kof.io, kof.http, kof.db, kof.orm, kof.security,
        │    kof.web, kof.cache, kof.scheduler, kof.config, kof.mq, kof.log...)
        ├── Tooling
        ├── Language Server / editor support
        ├── Embedded OpenJDK
        └── documentação
```

O usuário instala o Kof e recebe tudo o que precisa — **sem instalar Java
separadamente**. A cadeia `intention->Kof->frontend->IR->backend->runtime` é a mesma para todos os targets.

## Estrutura do pacote

```text
kof/
├── bin/
│   ├── kof              # launcher (Unix)
│   ├── kof.bat          # launcher (Windows)
│   └── kof-webview      # webview nativo Linux (WebKitGTK embutido) —
│                        #   usado por `kof run --target=js` para kof.ui
├── lib/
│   └── kof.jar      # CLI + compiler + tooling (autocontido)
├── jdk/             # OpenJDK embutido (pacote oficial)
├── tooling/         # definições consumidas por editores
├── editor/          # grammar TextMate oficial
├── docs/
└── VERSION          # `revision` (fonte única)
```

O `kof-webview` é compilado por `scripts/build-webview.sh` (Linux, requer
`libwebkit2gtk-4.1`); sem ele, `kof run --target=js` abre no browser do
sistema. `kof script` e `kof c` não precisam de webview.

## JDK embutido

O Kof distribui seu próprio OpenJDK (Temurin 21 — alinhado ao Tooling API
Level). O launcher `bin/kof`:

1. localiza o JDK embutido em `jdk/`;
2. se existir, usa-o (sem depender de `JAVA_HOME`/`PATH`);
3. em builds de desenvolvimento, cai para `java` do sistema.

Verificação:

```bash
kof info
# Kof 0.3.22-beta
# Targets: jvm, native, js (alpha)
# JVM: Eclipse Temurin 21.0.x (embedded)
```

## Tooling API Level

O baseline de API Java do tooling é **21**:

- versões posteriores do OpenJDK podem ser usadas internamente quando
  apropriado (ex.: Virtual Threads com Java 25), sem virar requisito;
- o pacote oficial carrega sua própria JVM.

## Multi-target preservado (Target separation 0.2.0)

A distribuição não muda a arquitetura da linguagem:

```text
Kof Source → Frontend → Kof IR → JVM | Native (x86-64 / riscv64 / aarch64) | KofJS | KofScript | KofC
```

`Target` enum: `JVM`, `NATIVE`, `NATIVE_RISCV64`, `NATIVE_AARCH64`, `JS`, `ANDROID`. `parseTarget` aceita `native.risc`/`native.riscv64` e `native.arm`/`native.aarch64` como aliases.

A linguagem é a mesma; o backend muda. Para nativo, o programador nunca
escreve `malloc`, `free` ou gerencia memória manualmente — o compilador/
runtime absorvem isso com **free-list GC** (`kof_free_head`, reuso via `mmap`; mark-sweep pendente, memória devolvida só no `munmap` fallback).

## Instalação

Baixe o pacote do **seu** sistema em
[GitHub Releases](https://github.com/KofLang/Kof4j/releases/latest)
(`linux-x86_64.tar.gz` / `macos-arm64.tar.gz` / `windows-x86_64.zip`).
O nome muda a cada release — use o globo `*` para não depender da versão:

```bash
tar -xzf kof-*-linux-x86_64.tar.gz
export PATH="$PWD/$(ls -d kof-*-linux-x86_64 | head -1)/bin:$PATH"
kof info
kof script --repl   # testa KofScript
kof c --help        # testa KofC
```

Verificar integridade: `sha256sum -c SHA256SUMS`.
Guia completo por sistema: [INSTALL.md](../docs/distribution/INSTALL.md).

## Referências

- [docs/distribution/ARCHITECTURE.md](../docs/distribution/ARCHITECTURE.md)
- [docs/distribution/INSTALL.md](../docs/distribution/INSTALL.md)
