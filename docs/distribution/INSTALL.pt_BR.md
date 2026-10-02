[English](INSTALL.md) | [Português](INSTALL.pt_BR.md)

# Instalação do Kof

Guia oficial de instalação a partir dos artefatos publicados no **GitHub
Releases**. Siga o passo a passo do **seu sistema** e pronto.

> **Versão atual:** 0.5.0-beta (ver `VERSION` na raiz do repo). Este guia **não depende da
> versão**: os comandos funcionam em qualquer release, atual ou futura.
> Você não precisa saber qual é a versão para instalar.

## Instalação automatizada (recomendado — Linux/macOS)

O jeito mais rápido: rode o instalador direto do cURL disponibilizado
pelo script de instalação contido no repositório (URL na branch `main` de release;
medido 30/09 — a antiga branch `beta-0.4.0` não existe mais e o `main` agora carrega
`scripts/install.sh`):

```bash
# instale uma versão específica com variáveis de ambiente
# e sem argumentos
curl -fsSL https://raw.githubusercontent.com/KofLang/Kof4j/main/scripts/install.sh \
  | KOF_INSTALL_VERSION=0.5.0-beta bash
```

ou

```bash
# instale uma versão específica com argumentos
curl -fsSL https://raw.githubusercontent.com/KofLang/Kof4j/main/scripts/install.sh \
  | bash -s -- --version 0.5.0-beta --yes
```

O que ele faz automaticamente:

1. Detecta sua plataforma (`linux-x86_64` / `macos-arm64`).
2. Resolve a release mais recente para a sua plataforma (ou o `--version`
   que você fixar).
3. Baixa `kof-<version>-<platform>.tar.gz` e o `SHA256SUMS`.
4. Verifica o checksum SHA-256.
5. Extrai para `~/.local/share/kof/kof-<version>-<platform>` e mantém um
   symlink `current`.
6. Adiciona a linha absoluta do PATH
   (`export PATH="<prefix>/current/bin:$PATH"`, onde `<prefix>` por padrão é
   `~/.local/share/kof`) no seu `~/.zshrc` / `~/.bashrc` **de forma
   idempotente** (nunca duplica a linha).
7. Exibe o `kof version` como prova.

Opções: `--prefix <dir>` (padrão `~/.local/share/kof`), `--yes` (sem
perguntas), `--no-modify-shell` (só imprime a linha do PATH), `--uninstall`
(remove a instalação e as linhas do PATH), `--help`.

> O instalador baixa o **binário oficial da release** — não precisa de
> Java/Maven. O Windows continua seguindo os passos manuais abaixo.

---

## 0. O que você precisa (e o que você NÃO precisa)

- **Precisa:** um computador com Linux, macOS ou Windows. Nada mais.
- **NÃO precisa:** Java, JDK, Maven, Node.js ou qualquer outra ferramenta.
  O pacote do Kof já vem com o **OpenJDK embutido**.

O Kof é uma **distribuição autocontida**: compilador + CLI + runtime +
standard library + tooling de editor + JDK embutido, tudo num arquivo só.

---

## 1. Escolha o arquivo do SEU sistema

Cada release publica um pacote **por plataforma**. Baixe **apenas um** — o
da sua:

| Seu sistema | Pacote (extensão) | Onde está na página |
|-------------|-------------------|----------------------|
| **Linux** (Intel/AMD, 64 bits) | `.tar.gz` com `linux-x86_64` | na seção da release `(... linux-x86_64)` |
| **macOS** (Apple Silicon M1/M2/M3…) | `.tar.gz` com `macos-arm64` | na seção da release `(... macos-arm64)` |
| **Windows** (Intel/AMD, 64 bits) | `.zip` com `windows-x86_64` | na seção da release `(... windows-x86_64)` |

**Como baixar:**

1. Abra <https://github.com/KofLang/Kof4j/releases> (ou
   <https://github.com/KofLang/Kof4j/releases/latest>).
2. Veja a release **Latest** (a mais recente). Ela lista 3 pacotes — um
   para cada plataforma — com seus arquivos anexos.
3. Na seção do **seu** sistema, clique no arquivo `.tar.gz` (Linux/macOS)
   ou `.zip` (Windows). Ele tem cerca de 230 MB.

> **Nome do arquivo:** o nome muda a cada release
> (`kof-<versão>-<sistema>.tar.gz`). Baixe o arquivo de pacote da sua
> plataforma; não se preocupe com o número da versão — o `kof version`
> mostra a versão real depois.

---

## 2. (Opcional, recomendado) Conferir a integridade

Cada release traz um arquivo `SHA256SUMS` com o código de cada pacote.
Baixe-o junto e confirme que o arquivo baixado não foi corrompido:

```bash
# Linux / macOS
sha256sum -c SHA256SUMS
# Windows (PowerShell)
Get-FileHash kof-*-windows-x86_64.zip -Algorithm SHA256
```

Se aparecer `OK` (Linux/macOS) ou o mesmo hash listado no `SHA256SUMS`
(Windows), está tudo certo. Pode pular esta etapa se preferir.

---

## 3. Instalar

### 🐧 Linux

Abra um terminal na pasta onde você baixou o arquivo:

```bash
# 1) extrair (o * pega a versão, não importa qual seja)
tar -xzf kof-*-linux-x86_64.tar.gz

# 2) achar a pasta extraída e colocar no PATH (este terminal)
DIR=$(ls -d kof-*-linux-x86_64 | head -1)
export PATH="$PWD/$DIR/bin:$PATH"

# 3) pronto!
kof version
```

Para o PATH valer **em todos os terminais futuros**, copie a linha `export`
para o final do seu `~/.bashrc` ou `~/.zshrc` (trocando `$PWD/$DIR` pelo
caminho absoluto real da pasta):

```bash
echo 'export PATH="$HOME/<pasta>/kof-*-linux-x86_64/bin:$PATH"' >> ~/.bashrc
```

> **Nota de segurança:** o Kof roda como usuário normal. Se você quiser
> instalá-lo em lugar fixo, mova a pasta para `~/.local/share/` ou
> `/opt/` e aponte o `PATH` para lá.

### 🍎 macOS (Apple Silicon)

```bash
# 1) extrair
tar -xzf kof-*-macos-arm64.tar.gz

# 2) PATH (este terminal)
DIR=$(ls -d kof-*-macos-arm64 | head -1)
export PATH="$PWD/$DIR/bin:$PATH"

# 3) pronto!
kof version
```

Para o PATH permanente, adicione ao `~/.zshrc` (padrão do macOS):

```bash
echo 'export PATH="$HOME/<pasta>/kof-*-macos-arm64/bin:$PATH"' >> ~/.zshrc
```

> Se o macOS avisar sobre o arquivo, é só clicar em **Abrir** uma vez nas
> Configurações de Privacidade — o pacote **não é assinado pela Apple** (a
> pipeline não faz codesign hoje). A confiança ancora no digest `SHA256SUMS`
> verificado acima; proveniência de build atestada pelo workflow oficial é o
> contrato ratificado `DECISIONS.md` §D-ARTIFACT-TRUST (fila de execução em
> pouso), nunca uma afirmação em tempo presente.

### 🪟 Windows

Abra o **PowerShell** na pasta onde você baixou o `.zip`:

```powershell
# 1) extrair
Expand-Archive .\kof-*-windows-x86_64.zip

# 2) achar a pasta e colocar no PATH (esta sessão)
$DIR = (Get-ChildItem -Directory -Filter "kof-*-windows-x86_64" | Select-Object -First 1).FullName
$env:PATH = "$DIR\bin;$env:PATH"

# 3) pronto!
kof version
```

Para o PATH **permanente** (todas as sessões), adicione a pasta `...\bin`
às **Variáveis de Sistema → PATH** do Windows:

1. `Win + R` → `sysdm.cpl` → aba **Avançado** → **Variáveis de Ambiente**.
2. Em **Variáveis do sistema**, edite `Path` → **Novo** → cole
   `C:\...\kof-<versão>-windows-x86_64\bin`.
3. OK, OK. Reabra o PowerShell e rode `kof version`.

---

## 4. Verificar se deu certo

```bash
kof version
```

Saída esperada (o número é o da sua release):

```
kof 0.5.0-beta
```

Relatório completo do ambiente:

```bash
kof info
```

Saída esperada (resumo):

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

Se `kof info` mostrar `JVM: ... (embedded)`, o JDK embutido está em uso —
**nenhuma instalação externa de Java foi necessária**.

---

## 5. O que você recebeu

```
kof-<versão>-<sistema>/
├── bin/
│   ├── kof            # launcher (Linux/macOS)
│   ├── kof.bat        # launcher (Windows)
│   └── kof-webview    # shell do kof.ui (quando disponível)
├── lib/
│   └── kof.jar        # compilador + runtime + stdlib + GraalJS
├── jdk/               # OpenJDK 25 embutido (release oficial)
├── editor/            # grammar + suportes de editor
├── tooling/           # definições reutilizáveis da linguagem
├── docs/              # documentação embarcada
└── VERSION            # a versão exata desta instalação
```

Principais comandos já disponíveis (detalhes em
[learn/32-cli-tooling.md](../../learn/32-cli-tooling.md)):

| Comando | O que faz |
|---------|-----------|
| `kof run app.kf` | compila e executa (JVM por padrão) |
| `kof build <dir|file.kf> [--target ...]` | compila para jvm / native / native.risc / native.arm / js / android |
| `kof serve app.kf` | sobe um app `web.app()` |
| `kof test <dir>` | roda a suíte de testes |
| `kof deploy <dir|file.kf> [--target jvm|native|native.riscv64|native.aarch64|js|android[,..]|all] [--publish [<owner/repo>]]` | empacota uma release autocontida: artefato + `RELEASE.md` + `SHA256SUMS` + `.tar.gz` (JS embarca o fecho do runtime, §298); lista vírgula/`all` faz o multi-target da MESMA fonte com um `.deploy-manifest.json` por execução (8.4); `--publish` sobe o(s) artefato(s) ao GitHub Releases (`GH_TOKEN`, D2-A); cross riscv64/aarch64 também empacota o ELF cross (X9 fatia 6; sem a toolchain cross a falha honesta nomeia a ferramenta — `KOF_CROSS_PREFIX` sobrepõe o prefixo das ferramentas `riscv64-`/`aarch64-`) |
| `kof check <dir>` | type-check sem emitir código |
| `kof script <f.kf>` / `kof repl` | execução direta / REPL |
| `kof fmt <f.kf>` | formata o código |
| `kof info` / `kof version` | ambiente / versão |

---

## 6. Atualizar

1. Baixe o pacote da nova release (mesmo passo 1).
2. Extraia ao lado (não por cima).
3. Aponte o `PATH` para a pasta nova e rode `kof version`.
4. Apague a pasta da versão antiga quando quiser.

O layout é estável entre releases — não há etapa de "migrar".

---

## 7. Solução de problemas

| Sintoma | Causa provável | Corretivo |
|---------|----------------|-----------|
| `kof: command not found` (Linux/macOS) | `PATH` não atualizado neste terminal | Reabra o terminal, ou rode o `export PATH=...` do passo 3 de novo |
| `'kof' não é reconhecido` (Windows) | `bin` fora do PATH | Siga o passo 2.5 (PATH permanente) e **reabra** o PowerShell |
| `kf: distribution incomplete` | pasta extraída incompleta | Re-baixe e extraia de novo; confira o checksum (passo 2) |
| Versão antiga ao rodar `kof version` | há outro `bin` antes no `PATH` | `which kof` (Linux/macOS) / `Get-Command kof` (Windows) e corrija a ordem do PATH |
| `tar: Unknown option` | usou `tar` errado no Windows | No Windows use o `Expand-Archive` (PowerShell) |
| Bloqueio de segurança no macOS | aviso do Gatekeeper | Configurações de Privacidade → permitir a app uma vez |

---

## 8. Build a partir do código-fonte (desenvolvedores)

Só para quem quer contribuir ou testar o `main`:

**Pré-requisitos:** JDK 25 (Temurin) e Maven 3.9+. Para o target
`native`: binutils (`as`/`ld`).

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests

# usar direto do source (usa o java do sistema, não o embutido)
mkdir -p lib
cp kof-cli/target/kof-cli-$(cat VERSION).jar lib/kof.jar
bin/kof version
bin/kof info

# empacotar a distribuição oficial (baixa o JDK embutido)
scripts/package.sh --jdk
```

Versionamento e empacotamento: [VERSIONING.md](VERSIONING.md) e
[PACKAGING.md](PACKAGING.md).
