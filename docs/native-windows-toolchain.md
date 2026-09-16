# NATIVE_WINDOWS_TOOLCHAIN.md — rodar a suíte Native num dev host Windows

## O problema

O backend Native da Kof (`NativeAssembler.java`) gera assembly x86_64 e
invoca `as`/`ld` de verdade, linkando contra ELF Linux real:

```java
runCommand(new String[]{"as", "-o", objFile, asmFile}, "as");
...
"ld", "-o", binFile, objFile, "-dynamic-linker", "/lib64/ld-linux-x86-64.so.2", "-lc"
```

Isso **não** é um assembler/linker genérico — é especificamente Linux
(`ld-linux-x86-64.so.2`, `libc.so.6`). Um `as`/`ld` do MinGW (Windows)
gera objetos PE/COFF e não entende essas flags: mesmo instalando um
assembler qualquer no Windows, o link falha ou produz um binário errado.

**Sintoma sem toolchain nenhum:** todo teste Native/`kof-c-compiler` falha
com `as not available: Cannot run program "as"` (COMP001) — isso é 100%
esperado e não indica bug no compilador; só falta o toolchain.

## A solução: WSL (não instalar nada no Windows)

Se o Windows já tem uma distro WSL (`wsl -l -v`), ela já traz `as`/`ld`/`gcc`
reais (verifique com `wsl -d <distro> -e which as ld gcc`). Falta só um JDK
dentro da distro para rodar o Maven — não precisa `apt`/`sudo`: baixe um
tarball portátil do Temurin e extraia na home do usuário:

```bash
# dentro da distro (wsl -d <distro>):
curl -sL -o /tmp/jdk25.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p ~/tools && tar -xzf /tmp/jdk25.tar.gz -C ~/tools && rm /tmp/jdk25.tar.gz
```

> **Use a versão que o `pom.xml` exige** (`maven.compiler.release`). Em 09/13
> era 21; hoje é **25**. Um JDK fora dessa versão não roda a suíte —
> `release version N not supported` no build ou `UnsupportedClassVersionError`
> nos testes E2E que dão spawn de `java`.

Reaproveite o `.m2` do Windows (evita rebaixar todas as dependências):

```bash
mvn -Dmaven.repo.local=/mnt/c/Users/<user>/.m2/repository ...
```

## Armadilha: `wsl.exe` sem `-e` reinterpreta o comando por um shell extra

`wsl -d <distro> -- <comando>` **não** executa `<comando>` diretamente: sem
`-e`/`--exec`, o `wsl.exe` junta tudo numa string e manda para
`$SHELL -c "<string>"` dentro da distro — uma segunda passada de shell por
cima do que você digitou. Isso reavalia `$VAR`/aspas/`;` do SEU comando
antes que ele chegue a rodar, e faz variáveis recém-atribuídas **sumirem em
silêncio** (sem erro nenhum) sempre que o comando é algo como
`bash -lc '<script>'`:

```bash
# ❌ QUEBRADO — X vira vazio, sem erro nenhum
wsl -d Ubuntu-24.04 -- bash -lc 'export JAVA_HOME=~/tools/jdk-25...; mvn ...'

# ✅ CORRETO — -e evita a passada extra de shell
wsl -d Ubuntu-24.04 -e bash -lc 'export JAVA_HOME=~/tools/jdk-25...; mvn ...'

# ✅ TAMBÉM CORRETO — script real em arquivo, nenhuma ambiguidade de shell
wsl -d Ubuntu-24.04 -e bash /mnt/d/kof/run-native-tests.sh
```

Prefira a segunda forma (script em arquivo) para qualquer coisa com mais de
uma linha — mais fácil de revisar e nunca sofre com quoting aninhado.
Relatado e documentado a montante em
[microsoft/WSL#41598](https://github.com/microsoft/WSL/issues/41598) (o
`wsl --help` chama isso de "pass as-is", o que é enganoso).

Se estiver invocando `wsl.exe` a partir do Git Bash/MSYS (não PowerShell/
cmd), também exporte `MSYS_NO_PATHCONV=1` — do contrário o MSYS reescreve
argumentos que parecem paths Unix (`/mnt/d/...`) para paths Windows antes de
chamar o `wsl.exe` (que é um executável nativo, não MSYS-aware).

## Duas armadilhas menores, medidas

**Saída do build Native nasce sem permissão de execução em `/mnt/`.** Escreva
o `--output` dentro do filesystem Linux (`~/...`), não em `/mnt/d`: via 9p o
binário gerado pode não nascer executável.

**Processo em background não sobrevive ao fim da sessão do `wsl.exe`.** Um
`nohup ... &` dentro de `wsl -e bash -lc` morre quando o `wsl.exe` que o
lançou retorna. Para builds longos, mantenha o `wsl.exe` em primeiro plano
(ou use `setsid`).

## Prova de que funciona

Com o toolchain acima:

| Verificação | Resultado |
|---|---|
| Suíte completa do `kof-compiler` (1315 testes) | **100%** (0 falhas, 0 erros, 115 skips) — 13/09, KOF-SBD-001 |
| `ConformanceMatrixTest` (gate de paridade, 4 alvos) | **11/11 verde** — 16/09, N1 |
| Bounds-check Native manual | acesso inválido a array aborta com `exit 1` / `"Runtime error: array index out of bounds"` |

O `ConformanceMatrixTest` é o caso mais ilustrativo da diferença: no Windows
ele dá **11/11 falhas** (todas `as not available`); no WSL, **11/11 verde**.
As duas medições juntas são o que separa falha ambiental de falha de código.
