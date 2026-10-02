[English](README.md) | [Português](README.pt_BR.md)

# kfvm — Kof Version Manager

Instale, alterne e remova versões do toolchain do [Kof](https://github.com/KofLang/Kof4j) pela linha de comando, no Linux, macOS e Windows.

```console
$ kfvm i lts        # instala a release estável mais recente
$ kfvm i 0.4        # instala a 0.4.x mais nova (0.4.10-beta)
$ kfvm u 0.4.8      # torna essa a versão ativa, instalando antes se preciso
$ kof version
```

O kfvm faz parte do tooling do Kof e fica em `tooling/kfvm` no repositório Kof4j. Ele baixa as releases oficiais do Kof publicadas no [GitHub Releases](https://github.com/KofLang/Kof4j/releases). É uma ferramenta JVM: roda sobre o JDK embutido de uma versão do Kof instalada.

## Por quê

O Kof é distribuído como um pacote autocontido (compilador, CLI, runtime, stdlib e um JDK embutido), e cada release fica na sua própria pasta. Alternar entre elas na mão significa baixar tarballs, extrair um ao lado do outro e reapontar o `PATH`. O kfvm faz isso por você, o que ajuda quando você precisa:

- verificar se um bug se reproduz em várias releases antes de reportá-lo;
- testar uma pré-release nightly sem perder sua instalação estável;
- voltar para uma versão anterior quando uma atualização quebra algo.

## Instalação

### Linux e macOS

```bash
curl -fsSL https://raw.githubusercontent.com/KofLang/Kof4j/lab/tooling/kfvm/install.sh | sh
```

O script compila o kfvm a partir do código-fonte. Se não encontrar um Kof 0.5.0 ou mais novo, ele primeiro instala um em `~/.local/share/kof`. Ele usa o código do kfvm que vem nessa distribuição do Kof (`tooling/kfvm`) quando existe, e caso contrário baixa `tooling/kfvm` do repositório Kof4j. Quando o Kof consegue compilar o kfvm para um binário nativo, esse binário é instalado em `~/.local/bin/kfvm`. Caso contrário, o kfvm é instalado como `~/.local/share/kfvm/kfvm.jar` mais um launcher em `~/.local/bin/kfvm`, que roda sobre o JDK embutido de uma versão do Kof instalada. Se `~/.local/bin` não estiver no seu `PATH`, o script o adiciona à configuração do seu shell.

### Windows

No PowerShell:

```powershell
irm https://raw.githubusercontent.com/KofLang/Kof4j/lab/tooling/kfvm/install.ps1 | iex
```

Ou pelo `cmd`:

```bat
powershell -c "irm https://raw.githubusercontent.com/KofLang/Kof4j/lab/tooling/kfvm/install.ps1 | iex"
```

Requer Windows 10 (1803 ou mais novo) ou Windows 11, que já vêm com `curl.exe` e `tar.exe`. O Kof publica builds para Windows apenas em x86_64.

O `install.ps1` faz o mesmo que o `install.sh`, com o mesmo layout dentro de `%USERPROFILE%`: as versões do Kof vão para `%USERPROFILE%\.local\share\kof`, o kfvm é instalado como `%USERPROFILE%\.local\share\kfvm\kfvm.jar` mais o launcher `%USERPROFILE%\.local\bin\kfvm.cmd`, e `%USERPROFILE%\.local\bin` é adicionado ao `PATH` do usuário. Abra um novo terminal depois de instalar.

Quando o kfvm ativa uma versão do Kof (`kfvm i` ou `kfvm u`), ele adiciona `%USERPROFILE%\.local\share\kof\current\bin` ao `PATH` do usuário, então `kof` funciona em qualquer terminal novo.

### Instalação manual

```bash
git clone https://github.com/KofLang/Kof4j
cd Kof4j
KFVM_SOURCE=tooling/kfvm sh tooling/kfvm/install.sh
```

No Windows:

```powershell
git clone https://github.com/KofLang/Kof4j
cd Kof4j
$env:KFVM_SOURCE = 'tooling\kfvm'; powershell -ExecutionPolicy Bypass -File tooling\kfvm\install.ps1
```

## Uso

```bash
kfvm -h
```

```
kfvm ls,  list [-r, --remote]          list installed versions (or available ones)
kfvm i,   install <ver|lts|nightly>    install a version
kfvm u,   use <ver|lts|nightly>        switch the active version (installs it if needed)
kfvm uni, uninstall <ver|lts|nightly>  remove an installed version
```

Todo comando tem um alias curto, então `kfvm install 0.4.10` e `kfvm i 0.4.10` são a mesma coisa.

### Listando versões

```bash
kfvm ls           # versões instaladas nesta máquina
kfvm ls -r        # versões disponíveis no GitHub Releases
```

### Instalando

```bash
kfvm i lts        # release estável mais recente (o mesmo que latest)
kfvm i nightly    # pré-release mais recente
kfvm i 0.4        # release 0.4.x mais nova
kfvm i 0.4.10     # uma versão específica
```

### Alternando

```bash
kfvm u 0.4.10
kof version
```

Se a versão ainda não estiver instalada, `kfvm u` baixa e instala primeiro. Se o GitHub não puder ser acessado (offline, ou com rate limit sem um `GITHUB_TOKEN`), ele usa as versões já instaladas.

### Removendo

```bash
kfvm uni 0.4.10
```

No Windows, o kfvm roda sobre o JDK da versão ativa do Kof e arquivos em uso não podem ser apagados, então troque para outra versão (`kfvm u <ver>`) antes de remover a ativa.

## Especificadores de versão

`kfvm i`, `kfvm u` e `kfvm uni` aceitam os mesmos especificadores. `kfvm i` e `kfvm u` os procuram nas releases publicadas no GitHub (`kfvm u` pula a busca quando você passa o nome completo de uma versão instalada, e usa as versões instaladas quando o GitHub não pode ser acessado). `kfvm uni` olha apenas as versões instaladas nesta máquina e nunca baixa nada.

| Especificador | Significado |
|---|---|
| `lts`, `latest` | A release estável mais nova. |
| `nightly` | A pré-release mais nova. |
| `<ver>` | Uma versão completa ou parcial, resolvida como descrito abaixo. Um `v` no início é ignorado (`v0.4` é `0.4`). |

Uma versão que corresponde exatamente a uma release seleciona essa release. Caso contrário, seleciona a release mais nova que começa com ela, onde a correspondência precisa terminar em `.`, `-` ou `+`, então `0.4.1` nunca corresponde a `0.4.10`. Você pode omitir o número minor ou patch, o sufixo `-beta` e o build `+data`. Quando uma release final e suas pré-releases correspondem, a release final vence (`0.4.9` é mais nova que `0.4.9-beta`).

Com estas releases publicadas:

```
0.5.0-beta+2026.09.26
0.5.0-beta+2026.09.25
0.4.10-beta
0.4.9-beta
...
```

| Você digita | kfvm escolhe | Por quê |
|---|---|---|
| `lts` | `0.4.10-beta` | a release estável mais nova (os builds 0.5.0 são pré-releases) |
| `nightly` | `0.5.0-beta+2026.09.26` | a pré-release mais nova |
| `0.4` | `0.4.10-beta` | a 0.4.x mais nova; não existe release 0.4 sem `-beta` |
| `0.4.9` | `0.4.9-beta` | a única release 0.4.9 |
| `0.4.9` | `0.4.9` | se uma `0.4.9` final for publicada, ela corresponde exatamente |
| `0.5` | `0.5.0-beta+2026.09.26` | o build 0.5.x mais novo |
| `0.5.0-beta+2026.09.25` | `0.5.0-beta+2026.09.25` | correspondência exata, mesmo existindo um build mais novo |

## Como funciona

Cada versão é extraída na sua própria pasta dentro do diretório de instalação, e um symlink `current` aponta para a ativa:

```
~/.local/share/kof/
├── kof-0.4.10-beta-macos-arm64/
├── kof-0.5.0-beta-macos-arm64/
└── current -> kof-0.5.0-beta-macos-arm64/
```

`kfvm use` só move o link `current`. Nada é copiado ou recompilado, então a troca é instantânea, e cada versão mantém seu próprio JDK embutido e sua biblioteca padrão.

É o mesmo layout usado pelo instalador oficial do Kof (`scripts/install.sh`), então o kfvm consegue gerenciar versões instaladas por ele, e vice-versa.

No Windows o layout fica em `%USERPROFILE%\.local\share\kof`, as versões terminam em `-windows-x86_64` e `current` é uma directory junction, que não precisa de permissão de administrador nem do Modo de Desenvolvedor. O launcher `kof` de cada versão é `bin\kof.bat`.

## Rodando uma versão específica sem alternar

Toda versão instalada pode ser chamada diretamente pelo caminho, o que é útil para comparar o comportamento entre releases:

```bash
~/.local/share/kof/kof-0.4.10-beta-macos-arm64/bin/kof fmt main.kf | diff main.kf -
~/.local/share/kof/kof-0.5.0-beta-macos-arm64/bin/kof fmt main.kf | diff main.kf -
```

## Desinstalando o kfvm

Remova as versões do Kof que você não precisa mais com `kfvm uni`, depois apague `~/.local/bin/kfvm`, `~/.local/share/kfvm` e a linha do `PATH` na configuração do seu shell. Para remover também todas as versões do Kof, apague o diretório de instalação:

```bash
rm -rf ~/.local/share/kof
```

No Windows, apague `%USERPROFILE%\.local\bin\kfvm.cmd` e `%USERPROFILE%\.local\share\kfvm`, e remova `%USERPROFILE%\.local\bin` e `%USERPROFILE%\.local\share\kof\current\bin` do `PATH` do usuário. Para remover também todas as versões do Kof:

```powershell
Remove-Item -Recurse -Force "$env:USERPROFILE\.local\share\kof"
```

## Licença

Copyright (C) 2026 Emerson A. Tieppo Jr.

O kfvm é software livre, distribuído sob a [GNU General Public License v3.0](LICENSE), a mesma licença do Kof.
