# 01 — Instalação

> **Kof 0.3.22-beta — set 2026.** Este guia não depende da versão: os
> comandos funcionam em qualquer release.

## O que é o Kof (e o que você NÃO precisa instalar)

O Kof é uma **distribuição autocontida**. O pacote oficial já traz
compilador, CLI, runtime, standard library, tooling de editor **e um
OpenJDK embutido**.

- **NÃO** instale Java, Maven, Node.js ou nada antes.
- **NÃO** precisa saber qual é a versão para instalar.

## Passo 1 — Baixe o pacote do SEU sistema

Abra <https://github.com/KofLang/Kof4j/releases/latest>. A release mais
recente lista 3 pacotes. Baixe **um** — o do seu sistema:

| Seu sistema | Baixe o arquivo com |
|-------------|---------------------|
| **Linux** (64 bits) | `linux-x86_64.tar.gz` |
| **macOS** (Apple Silicon) | `macos-arm64.tar.gz` |
| **Windows** (64 bits) | `windows-x86_64.zip` |

O arquivo tem ~230 MB. O nome começa com `kof-<versão>-<sistema>` — a
versão muda a cada release; o `kof version` mostra qual é depois.

> Sem certeza de qual é o seu? Rode `uname -m` (Linux/macOS: `x86_64` =
> Intel/AMD, `arm64` = Apple Silicon) ou, no Windows, verifique em
> **Configurações → Sistema → Acerca** (a maioria dos PCs atuais é
> `x64` = Intel/AMD).

## Passo 2 — Extrair e ativar

### Linux

```bash
tar -xzf kof-*-linux-x86_64.tar.gz                       # extrai
DIR=$(ls -d kof-*-linux-x86_64 | head -1)                # acha a pasta
export PATH="$PWD/$DIR/bin:$PATH"                        # ativa
kof version                                              # confere
```

Para valer sempre, adicione ao `~/.bashrc` (ou `~/.zshrc`):

```bash
echo 'export PATH="$HOME/<pasta>/kof-*-linux-x86_64/bin:$PATH"' >> ~/.bashrc
```

### macOS (Apple Silicon)

```bash
tar -xzf kof-*-macos-arm64.tar.gz
DIR=$(ls -d kof-*-macos-arm64 | head -1)
export PATH="$PWD/$DIR/bin:$PATH"
kof version
```

Para valer sempre, adicione ao `~/.zshrc`:

```bash
echo 'export PATH="$HOME/<pasta>/kof-*-macos-arm64/bin:$PATH"' >> ~/.zshrc
```

### Windows (PowerShell)

```powershell
Expand-Archive .\kof-*-windows-x86_64.zip                # extrai
$DIR = (Get-ChildItem -Directory -Filter "kof-*-windows-x86_64" |
        Select-Object -First 1).FullName                 # acha a pasta
$env:PATH = "$DIR\bin;$env:PATH"                          # ativa
kof version                                               # confere
```

Para valer sempre: **Variáveis de Ambiente → PATH → Novo** →
`C:\...\kof-<versão>-windows-x86_64\bin`. Reabra o PowerShell depois.

## Passo 3 — Conferir

```bash
kof version        # ex.: kof 0.3.22-beta
kof info           # ambiente completo (JVM embutida, targets, instalação)
```

Se `kof info` mostrar `JVM: ... (embedded)`, o JDK embutido está em uso —
nada de Java externo foi necessário.

## Seu primeiro programa

Crie `main.kf`:

```kf
main() {
    println("Hello, World!")
}
```

Rode:

```bash
kof run main.kf              # JVM (padrão)
kof run main.kf --target=native   # binário ELF x86-64
kof run main.kf --target=js       # GraalJS embutido
```

Saída:

```
Hello, World!
```

## Targets da plataforma

`kof build`/`run` aceitam `--target`:

| Target | O que gera | Observação |
|--------|-----------|------------|
| `jvm` (padrão) | `.class` | estável |
| `native` | ELF x86-64 | estável; precisa de `as`/`ld` só no source |
| `native.risc` | ELF riscv64 | placeholder (qemu) |
| `native.arm` | ELF aarch64 | placeholder (qemu) |
| `js` | ES Modules | alpha (GraalJS embutido) |
| `android` | projeto Android + APK | fase 1 |

A cadeia `intenção → Kof → IR → backend → runtime` é a mesma para todos —
`--target` só troca o backend.

## Comandos da CLI (resumo)

| Comando | Descrição |
|---------|-----------|
| `kof run <f.kf> [--target ...] [args]` | compila e executa |
| `kof build <dir> [--target ...]` | compila para o target |
| `kof serve <f.kf>` | sobe app web (`web.app()`) |
| `kof test <f.kf\|dir>` | roda testes |
| `kof check <f.kf\|dir>` | type-check sem emitir |
| `kof script <f.kf>` / `kof repl` | execução direta / REPL |
| `kof fmt <f.kf>` | formata |
| `kof info` / `kof version` | ambiente / versão |
| `kof lsp` | Language Server (stdio) |

Detalhes: [32-cli-tooling.md](32-cli-tooling.md).

## Build a partir do código-fonte (contribuidores)

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests
mkdir -p lib && cp kof-cli/target/kof-cli-$(cat VERSION).jar lib/kof.jar
bin/kof version
```

Em builds de desenvolvimento, o launcher usa o `java` do sistema (JDK 21+).
No pacote oficial, o JDK embutido é usado automaticamente.

## Problemas comuns

| Sintoma | Corretivo |
|---------|-----------|
| `kof: command not found` | o `PATH` não está ativo — rode o `export PATH=...` de novo ou reabra o terminal |
| `'kof' não é reconhecido` (Windows) | adicione `...\bin` ao PATH permanente e **reabra** o PowerShell |
| Versão errada | `which kof` (Linux/macOS) / `Get-Command kof` (Windows) — outro `bin` está antes no PATH |

## Referências

- [docs/distribution/INSTALL.md](../docs/distribution/INSTALL.md) — guia completo oficial
- [docs/distribution/ARCHITECTURE.md](../docs/distribution/ARCHITECTURE.md)

## Próximo passo

[Primeiro Programa →](02-first-program.md)
