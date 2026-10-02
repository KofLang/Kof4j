[English](debugging-native.md) | [Português](debugging-native.pt_BR.md)

# DEBUGGING_NATIVE.md — Debug no target Native

**Status:** Parcial (17/09) — DWARF line table real no ELF x86-64 (Fase 5 parcial): `NativeBackend`
emite `.file 1 "<fonte.kf>"` + `.loc 1 <linha> 0` quando debug esta habilitado; `objdump --dwarf=decodedline` mostra o arquivo Kof e a linha de cada instrucao
(`NativeDwarfLineInfoTest`). **Fatia 1 da frente 4 (17/09): CU DWARF `.debug_info`/`.debug_abbrev` do Kof com um `DW_TAG_subprogram` por funcao Kof
(`DW_AT_name` = nome-fonte, `low_pc`/`high_pc`, `decl_file`/`decl_line`) —
`NativeDwarf.java` + `NativeDwarfSubprogramTest`; o `as` suprime a CU automatica dele quando o programa emite `.debug_info` explicitamente (verificado no ELF linkado). **Fatia 2 (17/09): cada subprogram agora carrega `DW_AT_frame_base`
(`DW_OP_reg6`/rbp) + `DW_TAG_formal_parameter`/`DW_TAG_variable` filhos com
`DW_AT_location = DW_OP_fbreg` no MESMO slot do prologue (`-(index+1)*8`).
Duas licoes DWARF4 medidas contra gdb real: (1) `DW_AT_high_pc` e offset so na
v4+ (a v3 lia endereco absoluto e o gdb descartava a CU); (2) a ordem do header
v4 e version→abbrev_offset→address_size. Prova E2E: `gdb -batch -ex "b
Box_twice" -ex run -ex "info locals"` imprime `y = 20` (um `var` Kof) e nomeia
os args `this`/`w` — ler valores com tipo exige `DW_AT_type` (fatia 3).**
**Fatia 3 (17/09): DW_AT_type nos params/locals/retorno via DIEs filhos
`DW_TAG_base_type` (Int=signed4, Long=signed8, Double=float8, Float=float4,
Bool=boolean1, Char=unsigned2, Void=tamanho-0/sem-encoding,
classe/array/String = handle opaco de 8 bytes por enquanto). Codigo retirado
dos proprios bytes do `.debug_abbrev` do GCC, nao chutado. gdb le valores
tipados fim-a-fim: `print w` -> `5`, `ptype Box_twice` -> `Int (Opaque, Int)`.**
DAP on native e stepping pousaram 20/09.**
**X7-3/X7-4/X7-5 (20/09):** `kof debug --target native` dirige o gdb real do
alvo sobre o DWARF Kof (breakpoints em `Main.kf:N`, nunca no mangle);
`--break`/`--output` = sessão batch scriptável (gdb `-batch`, `break`/`run`/
`bt`); `kof debug --dap --target native` = ponte DAP↔gdb/MI2 (o editor vê o
`.kf`); `--attach <pid>` = gdb `-p`. Prova: `KofDebugNativeTest` 7/7,
`KofDebugNativeDapTest` 3/3 (stub-gdb no host, gdb real na CI).
**Data:** 20 de setembro de 2026
**Versão:** 0.5.0-beta (7 targets)

---

## 1. Fluxo

```text
Kof Debug Info (IR)
    ↓
símbolos + line tables (DWARF ✅ x86-64 + cross)
    ↓
ELF x86-64
    ↓
debug adapter (frame info Kof)
    ↓
DAP
    ↓
Editor
```

## 2. Fase inicial

- símbolos de função (já existem: `ClassName_methodName`);
- source locations por instrução (Fase 1 da IR);
- line tables ✅ (`.file`/`.loc` GAS → `.debug_line`; verificado com `objdump --dwarf=decodedline`);
- locals com tipos Kof;
- scopes;
- stack frames.

## 3. Depois

- ~~DWARF completo~~ ✅ x86-64 + cross (riscv64/aarch64) — X7-1/X7-2;
- localizações otimizadas de variáveis;
- inspeção de memória nativa.

## 4. Regra

Nunca mostrar assembly como experiência primária — o mapeamento
`assembly → linha Kof` é interno.

## 5. Validando o target Native a partir de um host Windows

O backend Native x86-64 não é apenas uma transformação de código em
memória: ele invoca um toolchain externo para montar e linkar o binário
gerado.

No código atual, `NativeAssembler` utiliza `as` e `ld` e, no caminho Linux
dinâmico, referencia o loader ELF e bibliotecas do sistema, incluindo
`libc` e `libm`.

Por isso, uma execução em Windows puro pode não conseguir validar o mesmo
caminho Native exercitado em Linux. Uma mensagem como:

```text
as not available: ...
```

ou:

```text
ld not available: ...
```

indica primeiro que o processo não encontrou a ferramenta externa
necessária. Não trate essa condição, isoladamente, como prova de
regressão do compilador Kof.

### 5.1 Antes de executar a suíte

Confira a versão Java exigida pelo projeto no `pom.xml`:

```xml
<maven.compiler.release>...</maven.compiler.release>
```

Use no WSL um JDK compatível com esse valor. Não copie uma versão fixa
deste documento: o `pom.xml` é a fonte de verdade.

Confirme também o toolchain Linux:

```bash
java -version
command -v as
command -v ld
as --version
ld --version
```

Quando o teste depender do caminho ELF dinâmico x86-64, também vale
conferir o ambiente esperado pelo linker, por exemplo:

```bash
test -e /lib64/ld-linux-x86-64.so.2 && echo "dynamic linker: ok"
```

A presença exata de ferramentas e bibliotecas depende da distribuição
WSL. Não assuma que toda distro já contém `as`, `ld`, `gcc` ou o JDK
necessário: verifique antes.

### 5.2 Executando a partir do Windows

Para comandos simples, `wsl.exe` pode executar comandos por meio do shell
configurado. Para o fluxo do Kof, no qual frequentemente já usamos
explicitamente `bash -lc` para configurar `JAVA_HOME`, `PATH` e depois
executar Maven, prefira o modo `--exec`/`-e`:

```powershell
wsl -d Ubuntu-24.04 -e bash -lc 'export JAVA_HOME=...; export PATH="$JAVA_HOME/bin:$PATH"; mvn ...'
```

Evite construir esse tipo de script aninhado como:

```powershell
wsl -d Ubuntu-24.04 -- bash -lc 'export JAVA_HOME=...; export PATH="$JAVA_HOME/bin:$PATH"; mvn ...'
```

quando a intenção é que o `bash -lc` fornecido seja a única camada
responsável pela interpretação das variáveis e metacaracteres do script.

#### Por que o Kof recomenda `-e` nesse caso?

Esse comportamento foi investigado no projeto oficial do WSL em
[microsoft/WSL#41598](https://github.com/microsoft/WSL/issues/41598) e
discutido na PR
[microsoft/WSL#41599](https://github.com/microsoft/WSL/pull/41599).

O projeto WSL decidiu manter o comportamento atual e classificou a
questão como `bydesign`. A equipe considera `--` o separador que encerra
a interpretação de opções do `wsl.exe`, enquanto o tipo de shell da
execução é controlado pelas opções próprias de shell/execução.

Portanto, esta orientação do Kof **não descreve um bug do WSL**. É uma
escolha operacional para tornar scripts de build aninhados previsíveis:
se já estamos fornecendo `bash -lc`, `-e` evita depender da interpretação
anterior do shell padrão.

Em termos práticos:

```text
wsl.exe -e bash -lc '<script>'
        │
        └── executar explicitamente bash com os argumentos fornecidos
```

é preferível, para nossos scripts de build, a depender de:

```text
wsl.exe <command line>
        │
        └── shell padrão interpreta a command line
                │
                └── bash -lc interpreta o script interno
```

quando o texto contém `$VAR`, aspas, `;` e outras construções destinadas
ao shell interno.

### 5.3 Git Bash/MSYS

Se `wsl.exe` estiver sendo chamado a partir de Git Bash/MSYS, lembre que o
próprio MSYS pode converter argumentos que se parecem com paths Unix antes
de chamar um executável Windows nativo.

Se essa conversão interferir com paths `/mnt/...`, execute a chamada a
partir de PowerShell/cmd ou desabilite a conversão de path para essa
invocação conforme o ambiente utilizado.

Não trate problemas de conversão MSYS e problemas de parsing do shell WSL
como a mesma causa: são camadas diferentes.

### 5.4 Onde gerar e executar os binários Native

Prefira gerar e executar artefatos Native dentro do filesystem Linux da
distro durante a validação. Isso evita misturar a semântica de permissões
do filesystem Linux com mounts Windows em `/mnt/...`.

Se um binário criado em um mount Windows não puder ser executado, valide
permissões e o tipo de mount antes de concluir que o Kof gerou um
executável inválido.

### 5.5 Ordem de diagnóstico

Quando um teste Native falhar em um host Windows, diagnostique nesta
ordem:

```text
1. O JDK corresponde ao maven.compiler.release atual?
        ↓
2. WSL/distro está disponível?
        ↓
3. as e ld existem dentro da distro?
        ↓
4. loader/bibliotecas exigidos pelo caminho testado existem?
        ↓
5. o comando está sendo executado com a camada de shell esperada?
        ↓
6. o teste isolado Native falha?
        ↓
7. o mesmo caso falha na suíte/gate de conformidade?
        ↓
8. só então tratar como possível regressão do compilador
```

A regra é simples:

> ausência do toolchain não prova defeito no código gerado; uma regressão
> deve continuar reproduzível depois que o ambiente necessário ao target
> estiver disponível.
## `KOF_CROSS_SYSROOT` não-canônico contamina testes cross não relacionados (26/09)

O `NativeCrossLink` aceita o override `KOF_CROSS_SYSROOT` para o sysroot cross
(`--sysroot=` no link + `-L` no qemu). Um sysroot artesanal (ex.: cópia de
`/usr/<arch>-linux-gnu` + debs multiarch do `libsqlite3` extraídos) é
**auto-consistente para os testes que linkam E executam pelo mesmo override**
(ex.: a prova cross §493 do ORM), mas envenena classes não relacionadas: os
harnesses `NativeRiscvGc*/Dtoa/DbWire` de aarch64 que linkam com o override e
executam sob o loader do sistema dão SIGSEGV (139) no qemu.
(`NativeCrossDynamicLinkTest.ldArgsSqliteAddsLsqlite3` travava o ramo de
sysroot padrão (`-lsqlite3`) e falhava quando o override trocava para
`-l:libsqlite3.so.0`; desde a **#699** deriva o arg esperado de
`NativeCrossLink.sqliteLinkArg`, então é agnóstico de host e não é mais
vítima do override.) **Regra:** rodar a suíte reactor completa SEM
`KOF_CROSS_SYSROOT` (o canônico é `/usr/<arch>-linux-gnu`; os testes cross que
exigem sqlite pulam honestos pelo guard `sqliteAvailable`), e usar o override
só em provas cross focadas onde link+exec o compartilham. Para um sysroot
canônico com sqlite: instalar o pacote multiarch real
(`apt install libsqlite3-0:arm64 :riscv64`) — a cópia privada é ferramenta de
uma sessão, não o ambiente do gate de merge.
