[English](kof-connector-ecosystem-plan.md) | [Português](kof-connector-ecosystem-plan.pt_BR.md)

# Interoperabilidade Kof — Ecossistema de Connectors

**Status:** EM DESENVOLVIMENTO — promovido `future/` → `docs/development/` por `D-CONNECTORS-GO` (mantenedora 29/09/2026)
**Local:** `docs/development/kof-connector-ecosystem-plan.md`
**Natureza:** arquitetura, contratos, dependências, estratégia de implementação, critérios de promoção
**Fonte normativa:** `DECISIONS.md` §`D-CONNECTORS-GO` (DECIDIDO — promoção autorizada)
**Dependências principais:** R3 / FFI-ABI (`docs/ffi-abi-structs.md`), o caminho de interop JVM
(`ExternalClasspath`/`JdkReflectionResolver`), `kof.process`/`kof.shell`/`kof.ssh`,
KofJS, os backends Native, `kof.toml`/`kofdeps`
**Estado de implementação:** fatias 1–16 POUSADAS em pure-Kof `libs/interop/` (leitor de manifest → `InteropCore`, até `CAbiConnector` = a metade declarativa C-ABI, fatia 16) — ver §9. **Fatia A (gramática `foreign module`) LANDADA 01/10** (`foreign` entra na gramática como açúcar sobre a via FFI existente; `ForeignModuleGrammarE2ETest` 5/5). Restam: a fatia B (tipo de erro de interop) e a transcrição dos tiers de ABI (§9.16 Fatia D) — gated pela regra 6 / `D-CONNECTORS`.

> **Regra fundamental.** Este documento descreve uma direção arquitetural futura. Ele **não**
> altera a linguagem, não adiciona palavras-chave, não cria namespaces e não abre trilha de
> implementação. Toda sintaxe mostrada é uma **forma de intenção**; a forma definitiva pertence
> à mantenedora.
>
> **KOF-first (regra 10).** Nenhuma linguagem, runtime ou ABI externa é oráculo da Kof. O
> ecossistema de connectors é construído a partir do modelo de tipos/ABI da própria Kof;
> pesquisa externa (ELF/Mach-O/PE, CPython C-API, JNI/FFM, `repr(C)`, …) contribui com
> invariantes e trade-offs, nunca com sintaxe ou semântica copiada.
>
> **Kof não é Java/Frankenstein (regras 8/45).** A interoperabilidade acontece **através do
> modelo de tipos e APIs da Kof**. A Kof nunca embute sintaxe estrangeira; a linguagem continua
> sendo Kof.

---

# 0. Objetivo e não-objetivos

## 0.1 Objetivo

A Kof já funciona dentro de sistemas Java por meio de integrações improvisadas. A necessidade
é real:

> **A Kof precisa conseguir entrar em sistemas existentes sem exigir que o sistema inteiro
> seja reescrito em Kof.**

Este plano transforma essas integrações improvisadas em uma **arquitetura oficial,
consistente, documentada e extensível**: um Interop Core mais connectors. Suporta as duas
direções:

```text
Sistema existente (Java, Python, Rust, C, C++, C#, Go, Kotlin, Swift, JavaScript/TypeScript, Ruby, PHP, Lua, Dart, Scala, R, Julia, COBOL, Pascal, Fortran, …)
        │
        ▼                    Kof            (a Kof chama o ecossistema externo)
   Kof Connector   ───────────────▶  função/biblioteca externa
        │
        ▼                    Kof            (o ecossistema externo chama a Kof)
       Kof
```

A interoperabilidade é **bidirecional sempre que tecnicamente possível**.

## 0.2 Não-objetivos

* Não é uma camada de "executa processo e lê stdout" como mecanismo principal (isso já existe
  como `kof.process`; é um connector, não o modelo).
* Não são 30 connectors independentes com regras próprias de marshalling/lifecycle/erro.
* Não é embutir sintaxe estrangeira ("sintaxe Java dentro da Kof", "sintaxe Python dentro da Kof").
* Não é mapear automaticamente toda a dinâmica de uma linguagem externa para o type system da Kof.
* Não é prometer estabilidade de ABI antes de existirem testes de compatibilidade.

---

# 1. Princípio arquitetural — um Interop Core, não connectors isolados

Não criar dezenas de connectors completamente independentes. Criar primeiro a
**infraestrutura comum**, depois cada connector é uma implementação fina dela.

```text
                     Kof Interop Core
                            │
           ┌────────────────┼────────────────┐
          ABI              API              FFI
           └────────────────┼────────────────┘
                            │
                     Connector SPI
                            │
   ┌──────────┬─────────┬───┴────┬──────────┬─────────┐
 Java      Python     Rust      C        C++      … outros
```

O Core é dono, uma única vez, do que nenhum connector pode re-inventar:

* marshal de tipos;
* lifecycle;
* ownership;
* gerenciamento de memória;
* propagação de erros;
* callbacks;
* resolução de símbolos;
* metadados de ABI;
* versionamento;
* diagnósticos.

Um connector implementa apenas o **adapter específico da linguagem** sobre esse substrato.

---

# 2. O que já existe (reusar — nunca duplicar)

A regra 54 exige inventário antes de qualquer código. A tabela abaixo é o ponto de partida
honesto; o plano **evolui esses mecanismos** em vez de construir uma segunda implementação.

| Mecanismo existente | Âncora real | Papel no ecossistema |
|---|---|---|
| FFI `extern` + códigos de assinatura | `docs/ffi-abi-structs.md`; `compiler/FfiSignature.java`; `compiler/CompilerFfiBinding.java` (`FFI001`/`FFI002`) | O substrato de ABI sobre o qual os connectors são construídos |
| Motor de layout ABI | `compiler/AbiLayout.java` (SysV-x86_64 / AAPCS64 / RISCV64 size-align-argclass); `compiler/FfiStructLayout.java` | Convenção de chamada + layout de struct/array — **não** inventar uma segunda ABI |
| Runtime FFI da JVM (FFM) | `compiler/jvm/JvmFfiRuntime.java` (`SymbolLookup.libraryLookup`, downcall/upcall, struct read/write, buffer in/out) | Chamadas externas no lado JVM |
| Runtime FFI Native | `compiler/nat/NativeFfiCall.java`, `NativeFfiCallRiscv.java`; política de link `NativeAssembler.java` / `NativeCrossLink.java` | Chamadas externas no Native (link-by-use, `call sym@PLT`) |
| Ponte FFI do JS | `kof-runtime/.../KofJsFfiBridge.java`, `KofJsFfiMarshal.java` | Chamadas externas no host JS |
| Resolução de classes Java | `compiler/ExternalClasspath.java`, `compiler/JdkReflectionResolver.java`, `JavaLangProbe.java`, `ExternalCtorTyper.java` | O resolvedor em compile-time do connector Java |
| Interop por processo/shell/ssh | `KofProcess.java`, `KofShell.java`, `KofSsh.java`; `runtime/RuntimeProcess.java`, `RuntimeShell.java`; JS `KofJsProcessBridge`; gap honesto `PROC001` | O connector de "processo" (protocolo stdout), não o modelo inteiro |
| Contrato de ABI do runtime | `docs/runtime/RUNTIME_ABI.md`, `ARRAY_MODEL.md`, `STRING_MODEL.md` (payload @24) | Layout de objeto/array/string para handles e buffers |
| GC + alocador | `runtime/RuntimeGc.java` (mark/sweep), `runtime/RuntimeMemory.java` (`kof_alloc`/`kof_free`) | Suporte a ownership/lifetime no Native |
| Sistema de pacotes | `KofProjectConfig.java` (`kof.toml`), `ProjectLocator.java`; `Deps.java`/`DepsRegistry.java` (`kofdeps`) | Manifest do connector + distribuição |
| Seleção de alvo | `Target.java`, `TargetMatrix.java`, `CompilerPipeline.java:193` | Qual connector/runtime é válido por alvo |
| Dispatch da CLI | `kof-cli/.../Main.java:17` (`switch` do comando); precedentes `CmdNew.java`, subcomandos `publish`/`Deps` | Onde `kof connector init` entra |
| Reflexão de interop (X6) | `compiler/CompilerInterop.java`, `docs/type-system-extensions-plan.md`; `training/idioms/interop.md`, `learn/21-java-interoperability.md` | Ponto de entrada existente de reflexão de superfície |
| Motores de script como connector de PROCESSO (X2) | resources `dev/kof/interop-py-host.kf` / `interop-r-host.kf` (`KofPy`/`KofR`: faces tipadas + `callJson`, protocolo de 2 linhas KOFOK/KOFERR, `INTEROP004`/`005`/`006`) | Os conectores scripting de §5.5/§5.6 na forma de processo — LANDED 26/09 (X2); a rota embedding/CPython-C-API/R-C-API segue nao-implementada |

> **Conclusão:** o substrato (ABI, helpers de marshal, runtime por alvo, resolução de classes)
> em grande parte já existe. A peça que falta é a **camada unificadora**: o Core, a Connector
> SPI, o manifest, o versionamento e os contratos de ownership/erro de nível de linguagem.

---

# 3. O Interop Core

## 3.1 Modelo de tipos de interop (ABI oficial)

Definir uma única representação oficial para tipos interoperáveis, compatível com os alvos
que já existem (JVM, C ABI/Native, JS; WASM quando existir). Tipos a cobrir:

| Conceito Kof | Tipo de interop | Observações |
|---|---|---|
| `Int`/`Long`/`Byte`/`Short` | integer | largura/sinal explícitos; SysV/AAPCS/RISCV64 via `AbiLayout` |
| `Float`/`Double` | ponto flutuante | IEEE-754; NaN/Inf preservados (regra 5 cross) |
| `Bool` | boolean | mapeamento para `_Bool`/int por connector |
| `Char` | code point / largura | declarado por connector |
| `String` | view de string | ver §3.3 (UTF-8 / UTF-16 / NUL-terminated / por length) |
| `Buffer` | buffer de bytes | `KofBuffer.java` já modela `Buffer(U8)` |
| `List<T>` | array | ponteiro+tamanho (emprestado ou dono, §3.2) |
| `record`/classe | struct/handle | ver §3.4 |
| enum | inteiro/enum | |
| função | function pointer | ver §3.5 |
| lambda/callback | callback | ver §3.6 |
| recurso opaco | handle opaco | precedente `Handle` boxed `Long` (`KofProcess.java`) |
| nullable | nullable/referência | explícito; nunca silencioso |
| erro | result/error | ver §3.7 |
| objeto da heap externa | object handle | ciente de GC/lifetime |

O modelo **não pode** inventar uma ABI incompatível com os alvos existentes. `FfiSignature`
(códigos de caractere), `FfiStructLayout` (`kof.ffi/struct`, `kof.ffi/array`) e `AbiLayout`
são a representação inicial; novos tipos (handle, result, view de string) a estendem, e toda
extensão é decisão de regra 6.

## 3.2 Ownership e memória (o contrato central)

Para cada travessia, afirmar explicitamente:

```text
quem aloca?   quem libera?   quem possui?   quando pode liberar?
quem pode modificar?   quem mantém referência?
```

Conceitos comuns, independentes de connector:

```text
owned      o receptor deve liberar
borrowed   válido apenas durante a chamada
shared     contado por referência / gerenciado por GC de um lado
opaque     nunca inspecionado; devolvido ao dono
immutable  não pode ser mutado pelo lado externo
mutable    pode ser mutado; contrato declarado
```

Regras:

* Nenhum connector inventa sua própria regra de ownership — o Core a define e o connector
  declara quais conceitos suporta.
* Quando uma linguagem não expressa um conceito (borrows do Rust, ARC do Swift, refcount do
  Python), usar **handles/wrappers** com lifetime explícito e documentado, nunca fallback
  silencioso.
* JVM: alocações FFI confinadas em `Arena` (D6-5) seguem sendo a regra da JVM; Native: o
  GC/alocador da Kof (`RuntimeGc`/`RuntimeMemory`) governa a memória de dono Kof.
* Decisões de ownership/lifetime que mudam a semântica da Kof são **regra 6** — plano, não
  edição silenciosa.

## 3.3 Strings

Interop explícita para strings, com a view mais barata e segura:

```text
Kof String
   ├── view UTF-8          (C, Rust &str, Go string, …)
   ├── view UTF-16         (JVM, JS, C#/CLR, Objective-C/NSString)
   └── buffer nativo       (mutável, NUL-terminated ou por length)
```

* Suportar UTF-8, UTF-16, NUL-terminated, por length, strings imutáveis e buffers mutáveis.
* Evitar cópias desnecessárias quando for seguro; quando uma cópia for necessária, **a camada
  de interop sabe disso** e documenta (§3.10).
* O layout de string da própria Kof é normativo (`docs/runtime/STRING_MODEL.md`); as views são
  mapeadas sobre ele.

## 3.4 Structs e tipos compostos

Permitir representação interoperável sem conversão manual campo a campo:

```text
Kof record User(Int id, String name)
        ↕
C struct  ·  Rust #[repr(C)] struct  ·  Java record/class
C# record  ·  Go struct  ·  Swift struct
```

* A forma real na Kof usa a gramática atual: `record` (imutável) ou `class` mutável com
  `constructor(...)`. **Nada de sintaxe importada.**
* O layout segue `AbiLayout`/`FfiStructLayout`; padding/alinhamento fazem parte do contrato.
* Formas não suportadas (enums Rust com dados, classes C++, interfaces Go) falham com
  diagnóstico claro — nunca layout adivinhado.

## 3.5 Funções, chamadas e o foreign module

Mecanismo oficial para declarar/importar/exportar funções:

```text
função estrangeira → símbolo Kof → chamada Kof
função Kof         → símbolo externo → Java/Python/Rust/C/…
```

Suportar: argumentos, retorno, callbacks, variádicas quando suportado, async quando
suportado, erros, lifecycle. (Nota: FFI variádica hoje não é suportada — `DECISIONS.md`
§`D-R3-3.5`; um connector pode declará-la como gap diagnosticado.)

Introduzir um conceito oficial de **foreign module**, independente de connector específico:

```text
foreign module = biblioteca + símbolos + tipos + funções + ownership + ABI
```

Essa é a abstração que o Core consome e os connectors populam.

## 3.6 Callbacks

Suportar, quando tecnicamente possível, as duas direções:

```text
biblioteca externa → callback → função Kof
Kof                → callback → runtime externo
```

Os mecanismos diferem e **não** são fingidos iguais — uma abstração comum mais adapters
específicos: function pointers C, interfaces JVM (upcalls via FFM), callables Python, funções
`extern "C"` do Rust, delegates C#, funções exportadas Go, closures Swift, funções JS.
Afinidade de thread, reentrância e lifetime fazem parte do contrato de callback.

## 3.7 Erros e exceções

Uma única camada comum capaz de representar:

```text
success · failure · código de erro · mensagem de erro · payload de erro
exceção estrangeira · stack/contexto quando disponível
```

Mapeamentos por connector, sem perder o erro estrangeiro:

```text
exceção Java      → erro de interop Kof
Rust Result::Err  → erro de interop Kof
código de erro C  → erro de interop Kof
```

Um erro estrangeiro nunca deve ser engolido em silêncio (R6).

## 3.8 Carregamento de bibliotecas

Uma abstração reutilizável de carregamento:

```text
.so  ·  .dylib  ·  .dll   (+ bibliotecas estáticas quando aplicável)
```

* JVM/JS: `SymbolLookup.libraryLookup` (já em `JvmFfiRuntime`/`KofJsFfiBridge`).
* Native: link-by-use (`NativeAssembler`/`NativeCrossLink`); `dlopen`/`dlsym` reservados (hoje
  usados só pelo caminho Vulkan `RuntimeVk.java`/`VkChain64Loader.java`).
* O loader é infraestrutura do Core: um caminho, reusado por todos os connectors.

## 3.9 Versionamento e estabilidade de ABI

A interop não pode depender só da versão da linguagem. Rastrear e validar:

```text
versão Kof · versão do Connector · versão da linguagem externa · versão do runtime externo
versão de ABI · ABI da plataforma · versão do compilador
```

* Classificar cada interface como `stable`, `experimental` ou `internal`.
* Não prometer estabilidade de ABI antes de existirem testes de compatibilidade.
* Testes de compatibilidade devem validar nomes de símbolos, convenção de chamada, layout de
  tipos, alinhamento, layout de struct, compatibilidade binária e ownership.
* Combinação não suportada deve produzir **diagnóstico claro** (R6).

## 3.10 Nada de custos escondidos

A API de interop deve deixar visível quando existe: cópia · conversão · alocação · travessia
de runtime · troca de thread · serialização · boxing · interação com GC. Interop conveniente
não pode significar comportamento invisível e imprevisível. Todo custo é documentado e, quando
possível, observável (diagnóstico ou métrica).

---

# 4. Connector SPI e manifest

## 4.1 Connector SPI

Cada connector implementa apenas os adapters que o Core não fornece, atrás de uma interface
de serviço estável (hooks de marshal, lifecycle, mapeamento de erro, ponte de callback,
resolução de símbolo, declaração de ABI). Adicionar um connector **não** pode exigir mudar o
core do compilador; o compilador o descobre pela SPI.

## 4.2 Manifest do connector

Um manifest declarativo, consistente com as convenções existentes da Kof (`kof.toml`,
`kofdeps`):

```text
name
language
version
abi
platforms
runtime
dependencies
capabilities
```

Declara linguagens, versões, alvos, ABI, requisitos de runtime, bibliotecas, tipos
suportados, suporte a callback e modelo de ownership. O formato real deve seguir o formato
existente de projeto/pacote — não um novo inventado aqui.

---

# 5. Catálogo de connectors

Agrupado por **mecanismo**, não por marca. Cada entrada declara sua rota, seus limites
honestos e se é planejado, avaliado ou fora de escopo. Os valores da matriz do §6 são
**descobertos na implementação, nunca supostos**.

## 5.1 Família JVM (um substrato, adapters onde a semântica diverge)

```text
Kof JVM Interop
   ├── Java
   ├── Kotlin
   └── Scala   (+ Groovy, Clojure avaliados)
```

* **Java (`kof-java-connector`) — primeiro connector.** Reusa `ExternalClasspath`,
  `JdkReflectionResolver`, `JvmFfiRuntime`. Suporta classes, métodos, construtores, métodos
  estáticos, campos quando apropriado, interfaces, callbacks, exceções, arrays, primitivos,
  objetos, generics quando há representação segura, bibliotecas JVM e módulos Kof consumidos
  por Java. Avaliar geração de bindings.
* **Kotlin (`kof-kotlin-connector`).** Adapter fino sobre o substrato JVM; manter explícitas
  nullability/extension semantics onde divergirem do Java.
* **Scala (`kof-scala-connector`).** Não reduzir Scala a Java se isso perde semântica
  (objects, traits, collections, tipos específicos de Scala). Adapter, não alias.

## 5.2 Família C ABI (a rota nativa fundamental)

* **C (`kof-c-connector`) — segundo connector, fundamental.** Headers, C ABI, structs,
  pointers, arrays, strings, function pointers, callbacks, bibliotecas compartilhadas/estáticas
  (`.so`/`.a`/`.dll`/`.lib`/`.dylib` por alvo). Avaliar geração header→binding (`kof-c-compiler`
  já emite um subconjunto C e é alvo cross para fixtures).
* **C++ (`kof-cpp-connector`).** Não é "C com classes": name mangling, ABI, namespaces, classes,
  ctors/dtors, templates, exceções, STL, smart pointers. Preferir uma **camada C ABI estável**;
  documentar honestamente os limites de ABI entre toolchains.
* **Rust (`kof-rust-connector`).** `extern`, `repr(C)`, ownership/borrowing, handles opacos,
  `Result`, fronteiras de panic, callbacks. Nunca expor tipos Rust arbitrários como se fossem C ABI.
* **Zig (`kof-zig-connector`).** Próximo de interop C/sistemas; C ABI, funções exportadas,
  structs, pointers, interação com allocator.
* **Go (`kof-go-connector`).** cgo, C ABI, funções exportadas, bibliotecas compartilhadas,
  callbacks, fronteiras goroutine/thread, ownership de memória. Respeitar as regras do runtime
  Go — nunca uma abstração que as viole.
* **Nim / D (`kof-nim-connector`, avaliado).** Mecanismos nativos de interop onde sustentável.

## 5.3 Gerenciado / .NET

* **C# (`kof-csharp-connector`).** .NET/CLR, P/Invoke, interop nativa, objetos gerenciados,
  delegates, exceções, assemblies. Avaliar Kof→.NET e .NET→Kof.
* **F# / VB.NET** avaliados via o mesmo substrato.

## 5.4 Apple

* **Swift (`kof-swift-connector`).** Interop Swift/C, ABI Swift, interop Objective-C, structs,
  classes, closures, ARC, ownership; camada C ABI documentada quando o direto for inseguro.
* **Objective-C (`kof-objectivec-connector`).** Runtime, messaging, `NSObject`, blocks, ARC,
  C ABI, headers — para o ecossistema Apple existente.

## 5.5 Scripting / dinâmicas

* **Python (`kof-python-connector`).** CPython, Python C API, embedding, extension modules,
  bibliotecas nativas, objetos, callables, exceções, buffers. Tratar o lifecycle do runtime
  Python e a GIL explicitamente — Python **não** é uma biblioteca nativa comum. A forma
  **processo** landed 26/09 como o motor X2 `KofPy` (linha do inventário em §2); embedding/C-API
  segue nao-implementada — essa é a forma-objetivo deste item, nao a landed.
* **JavaScript / TypeScript (`kof-javascript-connector` / `kof-typescript-connector`).** O KofJS
  já existe: torná-lo integração oficial (`Kof → KofJS → runtime JS`), não duplicar. Futuro:
  `Kof → KofWasm → host JS`; o mesmo módulo Kof deve rodar em ambos quando semanticamente compatível.
* **Ruby (`kof-ruby-connector`).** Ruby C API, extensões nativas, embedding, objetos, exceções.
* **PHP (`kof-php-connector`).** Extensões PHP, Zend API, FFI, embedding, lifecycle — preferir
  mecanismos estáveis/oficialmente suportados.
* **Lua (`kof-lua-connector`).** Lua C API, userdata, tables, funções, callbacks, embedding —
  especialmente para uso embutido/scripting.
* **Dart (`kof-dart-connector`).** Dart FFI, extensões nativas, isolates, callbacks, memória.

## 5.6 Científico

* **Fortran (`kof-fortran-connector`).** `ISO_C_BINDING`, C ABI, arrays, tipos numéricos,
  convenções de chamada, bibliotecas legadas (HPC/científico).
* **Julia (`kof-julia-connector`).** Julia C API, embedding, bibliotecas nativas, arrays,
  callbacks, objetos. Não mapear automaticamente toda a semântica dinâmica de Julia.
* **R (`kof-r-connector`).** Embedding, extensões nativas, interface C, vectors, data frames,
  callbacks (dados científicos). A forma **processo** landed 26/09 como o motor X2 `KofR`.
* **MATLAB/Octave (`kof-matlab-connector`, `kof-octave-connector`, avaliados).** Interfaces
  nativas, bibliotecas compartilhadas, C ABI, extension APIs onde oficialmente sustentável.

## 5.7 Funcional / específica de runtime

* **Haskell (`kof-haskell-connector`, quando sustentável).** GHC FFI, C ABI, funções exportadas,
  inicialização de runtime, callbacks. Não mapear lazy evaluation para o modelo de execução da Kof.
* **Erlang/Elixir (`kof-erlang-connector`, `kof-elixir-connector`, avaliados).** BEAM NIFs,
  ports, interfaces nativas, message passing, fronteiras de processo. Respeitar a concorrência
  do BEAM — não transformar chamadas Kof em síncronas se isso quebrar as propriedades da plataforma.
* **OCaml** avaliado via sua C ABI.

## 5.8 Legado

* **COBOL (`kof-cobol-connector`).** Um **caso de integração de legado, tratado a sério**:
  C ABI, runtime nativo, convenções de chamada, bindings gerados, bibliotecas compartilhadas,
  o runtime específico do compilador COBOL. Objetivo: um sistema COBOL existente consumir
  funcionalidade Kof — não reescrever COBOL. Records/códigos de erro passam pelo Core.
* **Pascal (`kof-pascal-connector`).** Free Pascal primeiro (baseline open-source), depois
  Object Pascal/Delphi onde tecnicamente possível: C ABI, bibliotecas compartilhadas,
  convenções de chamada, records, pointers, strings.

## 5.9 Extensibilidade

A lista é uma **direção**, não uma exigência de 30 runtimes (regra 55). Primeiro provar a
arquitetura com poucos connectors; cada novo connector é consequência da arquitetura, não uma
nova gambiarra. O catálogo é agrupado para que novas linguagens caiam numa família de mecanismo
existente. Uma matriz de linguagens/mecanismos (JVM · Native/Systems · .NET · Apple · Scripting ·
Científico · Funcional · Legado) rastreia candidatos conforme forem avaliados.

---

# 6. Matriz de capacidades dos connectors

Criar documentação com uma matriz; **descobrir os valores na implementação, nunca preenchê-los
por suposição**:

```text
Linguagem | Kof → Lang | Lang → Kof | ABI   | Callbacks | Structs | Errors | Ownership
Java      | ...        | ...        | JVM   | ...       | ...     | ...    | managed
C         | ...        | ...        | C     | ...       | ...     | ...    | manual
Rust      | ...        | ...        | C     | ...       | limited | Result | explicit
Python    | ...        | ...        | C/API | ...       | objects | exc.   | runtime
COBOL     | ...        | ...        | ABI   | limited   | records | codes  | manual
```

Cada connector implementa apenas o subconjunto que a linguagem permite; capacidades que não
existem são declaradas como gaps honestos (`XXX00x`), nunca fingidas.

---

# 7. Geração de headers / bindings

Avaliar geração automática de bindings:

```text
header C         → gerador de binding Kof → API Kof
metadados Rust/Java/.NET/Python (depois)
```

Não implementar todos os geradores de uma vez. Começar por uma linguagem com interface
**formal e estável** — provavelmente C ABI. Bindings gerados devem ser determinísticos e
cobertos por testes golden; binding gerado que não se pode confiar não é entregue.

---

# 8. CLI — gerador de connector

Avaliar `kof connector init` (ou equivalente na forma existente da CLI):

```text
connector/
    manifest
    bindings
    runtime
    types
    tests
    docs
```

Gera a estrutura inicial de um connector para que a comunidade crie connectors sem alterar o
core do compilador. O lugar é o dispatch existente de `kof-cli` (`Main.java:17`), seguindo o
precedente dos subcomandos `kof new` / `kof deps`.

---

# 9. Fases (incremental)

| Fase | Escopo | Prova de saída |
|---|---|---|
| 1 · Interop Core | modelo ABI, tipos externos, ownership, modelo de erro, handles, chamadas, metadados, testes | Core documentado + testes unitários verdes; nenhum connector necessário |
| 2 · C ABI | connector C, bibliotecas dinâmicas, structs, pointers, callbacks | ida-e-volta Kof↔C nas duas direções |
| 3 · JVM | Java, depois adapters Kotlin/Scala | app Java chama módulo Kof; Kof chama biblioteca Java |
| 4 · Systems | Rust, C++, Zig, Go | cada connector prova uma integração real |
| 5 · Gerenciado/scripting | Python, C#, JS/TS, Ruby, PHP, Lua | ida-e-volta Python↔Kof e C#↔Kof |
| 6 · Científico | Fortran, Julia, R, MATLAB/Octave | ida-e-volta de array/record numérico |
| 7 · Legado | COBOL, Pascal/Delphi | um sistema legado real consome um módulo Kof |
| 8 · Funcional/runtime | Haskell, Erlang/Elixir, OCaml, outros | integração respeitando o runtime externo |

A ordem pode mudar após análise técnica; a fase 1 é pré-requisito de todas as outras.

---


## 9.1 Fatia promovida 1 (29/09/2026) — leitor de manifest de connector

**Estado:** definida; implementação = biblioteca pure-Kof `libs/interop/` sobre o formato
`kof.toml` existente (nunca um formato novo, §4.2), consumida por `ConnectorManifest(path)`:

- `name()` / `language()` / `version()` / `abi()` / `runtime()` → `String` (campo obrigatório
  ausente lança um `CONNECTOR: missing <field>` explícito — R6, nunca silencioso);
- `platforms()` / `dependencies()` / `capabilities()` → `List<String>` (vazio quando ausente);
- `hasCapability(String)` → `Bool`; `describe()` → resumo de uma linha.

**Como terminar:** pousar a biblioteca + um E2E cross-target (`ConnectorManifestE2ETest`) provando a
leitura + o diagnóstico de campo ausente na JVM/Native x86-64/riscv64(qemu)/Script (JS herda a
lacuna `TOML`/`IOJS001`). **Sem mudança no compilador** (library-first, `D-KOF-FIRST-IMPL`). É a
costura declarativa que todo connector compartilha; o Interop Core (Fase 1) e o connector C-ABI
(Fase 2) se apoiam nela.

---

## 9.2 Fatia promovida 2 (29/09/2026) — validação de vocabulário do manifest

**Estado:** landada. `ConnectorManifest.validate()` confere cada token declarado em `capabilities`
contra a suíte cross-language do plano (§10: primitives/strings/arrays/structs/enums/pointers/
callbacks/errors/ownership/threads/async/opaque) e cada token de `ownership` contra o vocabulário do
Core (§3.2: owned/borrowed/shared/opaque/immutable/mutable). Um token desconhecido lança um
`CONNECTOR: unknown capability <x>` / `CONNECTOR: unknown ownership <x>` explícito — um connector
nunca declara em silêncio um conceito que o Core não define (R6). `knownCapabilities()`/`knownOwnership()`
expõem o vocabulário. Sem mudança no compilador.

---


## 9.3 Fatia promovida 3 (29/09/2026) — modelo de tipos de interop

**Estado:** landada. `libs/interop/InteropType.kf` responde em pure Kof à pergunta do plano §3.1:
para um tipo Kof, seu **kind de interop** (integer/float/boolean/char/string/buffer/array/
struct-or-handle/enum/function-pointer/callback/opaque/nullable/result/object-handle). `kindOf`
retorna null para um tipo não-interoperável e `describe` lança `INTEROP: unsupported type <x>` (R6,
nunca um chute); `kinds()`/`isKnownKind` expõem o vocabulário. Sem larguras de ABI de alvo (isso
fica com `AbiLayout`/`FfiSignature`), sem mudança no compilador, e sem IO de arquivo — logo é
neutra de alvo e roda em todos, inclusive JS (sem lacuna).

---


## 9.4 Fatia promovida 4 (29/09/2026) — catálogo de connectors

**Estado:** landada. `libs/interop/ConnectorCatalogue.kf` descobre os manifests `*.toml` de um
diretório (`Directory(base).list()`) e expõe `count`/`names`/`find`/`has`; entradas não-`.toml` são
ignoradas, e um manifest malformado lança seu próprio diagnóstico explícito (nunca um skip
silencioso). Sobre o `Directory` do `kof.io`; JS herda a lacuna `IOJS001`.

**Achado medido (compilador/generics — CORRIGIDO no #697):** uma coleção genérica de um tipo da
biblioteca no MESMO pacote não tipava quando a anotação declarada estava presente:
`List<interop.ConnectorManifest> xs = new List<interop.ConnectorManifest>()` e
`List<ConnectorManifest> xs = listOf<ConnectorManifest>()` davam `SEM021` espúrio ("type mismatch") —
o type-argument degradava para pacote vazio / nome pontuado literal
(`ClassType("", "interop.ConnectorManifest")`) enquanto o outro lado era qualificado pelo pacote.
Causa-raiz: o `SemNewExprTyper` aplicava o `Type::of` (só-texto) aos type-args de um ctor de coleção
builtin, e o tipo declarado do `VarDeclStmt` só passava por `qualifyDeep` quando continha `'.'`.
Ambos agora passam pela qualificação ciente do analisador (#697, issue 697; prova
`SamePackageGenericArgTest` matriz de 6 formas + golden JVM). O catálogo manteve o workaround
`List<String>` durante a lacuna; a API pública `all(): List<ConnectorManifest>` não está mais
bloqueada.

---


## 9.5 Fatia promovida 5 (29/09/2026) — tipos suportados declarados, validados pelo type model

**Estado:** landada. `interop.ConnectorManifest` agora lê o campo opcional `types = [...]` (§4.2
"tipos suportados") e o `validate()` checa cada nome contra o type model de interop do Core
(`interop.InteropType.isKnownKind`, §3.1) — um kind desconhecido lança `CONNECTOR: unknown type <x>`
(R6), nunca aceito às cegas. Isto liga as fatias 1/2 (manifest) com a fatia 3 (type model).

**Prova cross-target:** `ConnectorManifestE2ETest` **10/10** (golden JVM + Script + Native x86-64 + a
lacuna JS `IOJS001` + negativos). A face de validação da fatia 5 é provada em JVM/Script/JS/x86.

**Divergências cross-target medidas (NÃO desta lane — registradas para a lane native):**
- riscv64: o runtime do E2E de manifest dá **SIGSEGV (exit 139)** até para o probe read-only da fatia 1,
  de forma determinística no tip `e7c4abd95` (que já contém o fix §540). O caso riscv64 foi removido do
  `ConnectorManifestE2ETest` em vez de escondido atrás de um assumption; nenhuma alegação riscv64 é feita
  para esta biblioteca até a divergência native ser resolvida.
- erro de link em native x86-64 observado perto (`undefined reference to kof_bm_set` em `kof_alloc`),
  do trabalho §540/§542 do irmão — reportado via `twin.md`, não tocado aqui.

---


## 9.6 Fatia promovida 6 (29/09/2026) — contrato de posse / tempo de vida

**Estado:** landada. `interop.InteropOwnership` é a fonte única do Core para o vocabulário de posse
da §3.2 (owned/borrowed/shared/opaque/immutable/mutable), seu contrato e tempo de vida explícitos
(`contract`/`lifetime`/`describe`); conceito desconhecido lança `INTEROP: unknown ownership <x>`
(R6). `ConnectorManifest.knownOwnership()` agora delega a ela (vocabulário não duplicado). É o
modelo de posse do Core em Kof puro, neutro de alvo (sem IO).

**Prova:** `InteropOwnershipE2ETest` **5/5** (golden JVM + Script + JS + Native x86-64 + o
diagnóstico desconhecido), e `ConnectorManifestE2ETest` **10/10** após a delegação. Sem mudança no
compilador.

---


## 9.7 Fatia promovida 7 (29/09/2026) — custos visíveis de interop

**Estado:** landada. `interop.InteropCost` é o vocabulário do Core para os custos visíveis da §3.10
(copy/conversion/allocation/crossing/thread-switch/serialization/boxing/gc) com `describe`,
`summary` ("none" quando vazio) e `validate`; custo desconhecido lança `INTEROP: unknown cost <x>`
(R6). Torna explícito o custo de uma travessia em vez de invisível, conforme §3.10.

**Prova:** `InteropCostE2ETest` **5/5** (golden JVM + Script + JS + Native x86-64 + o diagnóstico
desconhecido). Kof puro, neutro de alvo, sem mudança no compilador.

---


## 9.8 Fatia promovida 8 (29/09/2026) — views de string de interop

**Estado:** landada. `interop.InteropString` mapeia as views de string da §3.3: a string canônica do
Kof é UTF-8 + NUL (`docs/runtime/STRING_MODEL.md`), hosts UTF-16 (JVM/JS/C#/ObjC) exigem uma
conversão (cópia visível, §3.10) e hosts UTF-8 (C/Rust/Go) não. `viewFor`/`conversionRequired` são
honestos: host desconhecido lança `INTEROP: unknown string host <x>` (R6).

**Prova:** `InteropStringE2ETest` **5/5** (golden JVM + Script + JS + Native x86-64 + o diagnóstico
desconhecido). Kof puro, neutro de alvo, sem mudança no compilador.

---


## 9.9 Fatia promovida 9 (29/09/2026) — descritor de módulo estrangeiro

**Estado:** landada. `interop.ForeignModule` modela a abstração da §3.5 que o Core consome e os
connectors populam: identidade do módulo (nome, library, ABI) + símbolos declarados (nome Kof → nome
estrangeiro + assinatura) + o conceito de posse do módulo, validado por `InteropOwnership` (§3.2). É
Kof puro — sem gramática de `foreign module` (rule 6, §13). Honesto (R6): posse desconhecida lança
via `InteropOwnership`; símbolo não declarado lança `FOREIGN: unknown symbol <x>`.

**Prova:** `ForeignModuleE2ETest` **6/6** (golden JVM + Script + JS + Native x86-64 + os dois
diagnósticos negativos). Sem mudança no compilador; neutro de alvo.

---


## 9.10 Fatia promovida 10 (29/09/2026) — nomes de biblioteca estrangeira

**Estado:** landada. `interop.InteropLibrary` cobre a face do library-loading da §3.8 expressável em
Kof puro: um nome lógico de biblioteca mapeia para o nome concreto de arquivo por formato
(`lib<n>.so` / `lib<n>.dylib` / `<n>.dll`, + estáticas `lib<n>.a` / `<n>.lib`) e para caminhos
candidatos sob raízes dadas. A chave é o PRÓPRIO sufixo de formato (a lista do plano) — nenhum
vocabulário de SO é inventado. Honesto (R6): formato desconhecido lança
`INTEROP: unknown library kind <x>`.

**Prova:** `InteropLibraryE2ETest` **5/5** (golden JVM + Script + JS + Native x86-64 + o diagnóstico
desconhecido). Sem mudança no compilador; neutro de alvo.

---


## 9.11 Fatia promovida 11 (29/09/2026) — tiers de estabilidade e aspectos de compatibilidade ABI

**Estado:** landada. `interop.InteropCompatibility` fornece o MECANISMO da §3.9, não a política: os
tiers de estabilidade (`stable`/`experimental`/`internal`), se um tier promete estabilidade (só
`stable`, e só quando existirem testes de compatibilidade) e os aspectos que um teste de
compatibilidade deve validar (symbol-names/calling-convention/type-layout/alignment/struct-layout/
binary-compat/ownership). A atribuição de tier + a primeira versão ABI estável são **decididas** por
`D-CONNECTORS` (§13); a tabela concreta ainda não foi transcrita (lacuna de documentação). Honesto (R6): tier/aspecto desconhecido lança `INTEROP: unknown stability <x>` /
`INTEROP: unknown compatibility aspect <x>`.

**Prova:** `InteropCompatibilityE2ETest` **6/6** (golden JVM + Script + JS + Native x86-64 + dois
negativos). Sem mudança no compilador; neutro de alvo.

---


## 9.12 Fatia promovida 12 (29/09/2026) — hooks da SPI de connector

**Estado:** landada. `interop.ConnectorSpi` nomeia os hooks de adapter da §4.1 que um connector pode
fornecer (marshalling/lifecycle/error-mapping/callback-bridging/symbol-resolution/abi-declaration) —
a interface de serviço estável atrás da qual o connector implementa só o que o Core não fornece. O
manifesto do connector agora os declara (`spi = [...]`, §4.2) e o `validate()` checa cada um contra o
vocabulário do Core, então adicionar um connector nunca muda o núcleo do compilador. Honesto (R6):
hook desconhecido lança `INTEROP: unknown SPI hook <x>` (avulso) / `CONNECTOR: unknown SPI hook <x>`
(manifesto).

**Prova:** `ConnectorSpiE2ETest` **5/5** + `ConnectorManifestE2ETest` **9/9** (golden JVM + Script +
JS + Native x86-64 + negativos). Sem mudança no compilador; neutro de alvo.

---


## 9.13 Fatia promovida 13 (29/09/2026) — fachada do Interop Core + audit

**Estado:** landada. `interop.InteropCore` compõe a "camada unificadora" da §2: dado um diretório de
manifests de connector, enumera-os (`ConnectorCatalogue`), valida cada um (`ConnectorManifest` cobre
capabilities/ownership/types/SPI) e produz um audit determinístico (nomes ordenados, uma linha por
connector) — o que um `kof connector` de listagem imprimiria. Honesto (R6): connector inválido sai
como `INVALID: <diagnóstico>`, nunca escondido; manifest malformado lança do leitor.

**Prova:** `InteropCoreE2ETest` **4/4** (golden JVM + Script + Native x86-64 + a lacuna JS `IOJS001`).
Sem mudança no compilador.

---


## 9.14 Fatia promovida 14 (29/09/2026) — tier de estabilidade no manifest, validado pelo Core

**Estado:** landada. `ConnectorManifest.stability()` lê o campo opcional `stability` (§3.9) e o
`validate()` o checa contra o vocabulário de tiers do Core (`InteropCompatibility.isKnownTier`) — tier
desconhecido lança `CONNECTOR: unknown stability <x>` (R6). Liga o manifesto ao mecanismo de
compatibilidade; a política de QUAL tier cada interface recebe segue rule-6 (§13).

**Prova:** `ConnectorManifestE2ETest` **10/10** (golden JVM + Script + Native x86-64 + a lacuna JS
`IOJS001` + os negativos, incl. o novo unknown-stability). Sem mudança no compilador.

---


## 9.15 Fatia promovida 15 (29/09/2026) — template de manifest de connector (gerador)

**Estado:** landada. `interop.ConnectorTemplate` fornece a metade pura-Kof do gerador de connector da
§8: dada a identidade e as declarações de um connector, renderiza um manifest `kof.toml` canônico
(§4.2) — o formato existente, nunca um novo. `render()` é neutro de alvo; o round-trip é a prova: o
template escreve o arquivo, `ConnectorManifest` relê e `validate()` passa.

**Prova:** `ConnectorTemplateE2ETest` **5/5** (golden de render em JVM + Script + Native x86-64; um
round-trip real write→read→validate na JVM; a lacuna JS `IOJS001`). Sem mudança no compilador.

---


## 9.17 Fatia promovida 16 (29/09/2026) — connector C-ABI, metade declarativa (Fatia C da §9.16)

**Estado:** landada. `interop.CAbiConnector` é a metade declarativa do segundo connector oficial
(C ABI, `D-CONNECTORS`): compõe as peças do Core num perfil validado — o foreign module
(`ForeignModule`: library+símbolos+ABI+posse), os custos visíveis declarados (`InteropCost`), os tipos
de interop suportados (`InteropType`) e o tier de estabilidade (`InteropCompatibility`) — com
`describe()` e validação. O round-trip de runtime (dlopen/call) espera as fatias de compilador
gramática `foreign module` + tipo de erro (§9.16 A/B). Kof puro, sem mudança no compilador.

**Prova:** `CAbiConnectorE2ETest` **7/7** (golden JVM + Script + JS + Native x86-64 + três negativos:
custo/tipo/símbolo desconhecidos).

---

# 10. Testes

Cada connector deve possuir testes em múltiplos níveis:

* **Unit** — mapeamento de tipos, metadados, geração de bindings, representação de ABI.
* **Integração** — `Kof → externo` e `externo → Kof`.
* **Runtime** — memória, callbacks, exceções/erros, concorrência, lifecycle.
* **Compatibilidade** — versões e combinações de ABI suportadas.
* **Negativos** — ABI incompatível, tipo incompatível, ownership inválido, símbolo inexistente,
  runtime ausente (devem produzir diagnóstico claro, R6).

**Suíte cross-language.** Um conjunto mínimo de operações que todo connector testa quando
suportado:

```text
valores primitivos · strings · arrays · structs · enums · pointers/handles
callbacks · erros · ownership de memória · threads · async · objetos opacos
```

Cada connector implementa o subconjunto que a linguagem permite. A suíte é o corpus golden do
ecossistema; testes de compatibilidade e negativos são portões, não extras.

---


**Suíte Core landada (29/09/2026).** O Interop Core em Kof puro é coberto por 13 classes E2E
cross-target, todas verdes juntas (**72/72**): `ConnectorManifestE2ETest` (10), `ConnectorCatalogueE2ETest`
(4), `InteropCoreE2ETest` (4), `InteropTypeE2ETest` (5), `InteropOwnershipE2ETest` (5),
`InteropStringE2ETest` (5), `InteropCostE2ETest` (5), `InteropLibraryE2ETest` (5),
`InteropCompatibilityE2ETest` (6), `ConnectorSpiE2ETest` (5), `ForeignModuleE2ETest` (6), `ConnectorTemplateE2ETest` (5), `CAbiConnectorE2ETest` (7) — cada uma roda
golden JVM + Script + Native x86-64 (+ JS quando a biblioteca é neutra de alvo; as de IO de arquivo
afirmam a lacuna JS `IOJS001`), mais os diagnósticos negativos.

---

# 11. Critérios de conclusão

A iniciativa é funcional quando:

* existe um Interop Core;
* o modelo de ABI/tipos está documentado;
* ownership está definido;
* erros estão definidos;
* callbacks são suportados quando possível;
* connectors são independentes do core do compilador;
* C ABI funciona;
* JVM interop funciona;
* pelo menos um runtime scripting funciona;
* existem testes bidirecionais;
* a documentação permite criar integrações reais;
* connectors podem ser adicionados sem modificar arbitrariamente o compilador;
* incompatibilidades produzem diagnósticos claros.

E principalmente:

> **um sistema existente consegue incorporar módulos Kof sem virar um projeto Kof inteiro.**

---

# 12. Regras de implementação

Antes de alterar qualquer código (regra 54):

1. analisar a arquitetura atual;
2. localizar os mecanismos existentes de FFI/ABI;
3. localizar o suporte JVM;
4. localizar o KofJS;
5. localizar o Native;
6. localizar o loading de bibliotecas;
7. localizar a representação de tipos;
8. localizar o gerenciamento de memória;
9. localizar o sistema de módulos/pacotes;
10. localizar os mecanismos existentes de linking.

**Não duplicar** mecanismos que já existem. Se uma capacidade já existir parcialmente,
evoluí-la para o Interop Core — nunca criar uma segunda implementação.

**Controle de escopo (regra 55).** A lista de linguagens é direção arquitetural, não exigência
de implementar todos os runtimes agora. Provar a arquitetura com poucos connectors, depois
adicionar linguagens incrementalmente. **Um connector nunca é criado para marcar checkbox** —
só está pronto quando uma integração real pode ser demonstrada (nas duas direções quando a
linguagem permitir).

**Lei da Simplicidade (regra 11).** Tudo que chega à superfície da linguagem deve ser
extremamente simples, curto, idiomático e representante de intenção. Conveniência de interop
não pode importar cerimônia estrangeira para dentro da Kof.

---

# 13. Decisões (regra 6 — a mantenedora decide)

**Resolvidas por `D-CONNECTORS` (mantenedora, `D-FUTURE-BATCH-2809B`, 28/09/2026)** — a frente está
autorizada e suas questões de design travadas no `DECISIONS.md`:

* **D-CONNECTORS** — frente autorizada e ordenada (Interop Core + SPI/manifest + catálogo).
* **Vocabulário de ownership** — fica **interno** (sem superfície de linguagem). → o Core o modela
  em Kof puro (`InteropOwnership`, §9.6); sem mudança de linguagem.
* **Modelo de erro de interop** — **é um tipo da linguagem** (escolha da mantenedora). → exige uma
  fatia de compilador, não só biblioteca.
* **`foreign module`** — **entra na gramática agora** (escolha explícita da mantenedora, contra o
  adiamento recomendado). → exige fatia de compilador; até lá o Core modela o módulo como descritor
  Kof puro (`ForeignModule`, §9.9), sem gramática.
* **Tiers de estabilidade de ABI + primeira versão estável** — **definidos** no `D-CONNECTORS`; o
  Core traz o mecanismo (`InteropCompatibility`, §9.11) e o manifest carrega o tier declarado
  (`ConnectorManifest.stability`, §9.14). Nota honesta: o `D-CONNECTORS` diz que estão
  **definidos**, mas a tabela concreta de tiers e a primeira versão estável **ainda não foram
  transcritas** no `DECISIONS.md` nem aqui — lacuna de documentação a registrar antes da fatia de
  ABI (nunca a inventar por agente).
* **Segundo connector oficial depois do Java** — **C ABI** (Fase 2, §5.2).
* **Roteiro de promoção** — `future/` → `docs/development/` **FEITO 29/09/2026** (`D-CONNECTORS-GO`).

**Falta pousar (fatias de compilador/linguagem, registros rule 6 já existentes):** o tipo de erro de
interop na superfície da linguagem e o construto `foreign module` na gramática — ambos tocam o
compilador/frontend e são fatias separadas, não só biblioteca.

---


## 9.16 Medição — o trabalho autorizado restante, decomposto (29/09/2026)

O `D-CONNECTORS` autoriza mais que o Core em Kof puro: o **tipo de erro** de interop (linguagem), a
**gramática** `foreign module` e o **connector C-ABI** como segundo connector oficial. Esta é uma
fatia medir-antes: nomeia as âncoras reais, divide o trabalho e não implementa nada por si.

**Âncoras reais (verificadas no repositório):** `FfiSignature.java`, `AbiLayout.java`,
`FfiStructLayout.java`, `CompilerFfiBinding.java`, `JvmFfiRuntime.java`, `nat/NativeFfiCall.java`,
`TargetMatrix.java`.

* **Fatia A — gramática `foreign module`** (compilador/frontend). **LANDADA 01/10**
  (`Parser.parseForeignModule`, `ForeignModuleNode`): o bloco
  `foreign module libm { library "libm.so.6"; abi c; ownership borrowed; extern fmod(Double a, Double b): Double; … }`
  é açúcar sobre a via FFI **existente** — desdobra em declarações `extern` normais que herdam a
  `library` do módulo (sem novo motor ABI, regra 54), então o binding/ABI é exatamente a
  `CompilerFfiBinding`. `library` é obrigatória (senão `PARSE097`); `ownership` é validada contra o
  vocabulário do Core (senão `PARSE099`); `foreign`/`module` são keywords contextuais que seguem
  identificadores fora do cabeçalho. Prova RED-first: `ForeignModuleGrammarE2ETest` **5/5** — um
  módulo chamando símbolos reais da libm (`fmod`/`sqrt`/`pow`) na JVM, a sobreposição de library por
  `extern`, os dois diagnósticos honestos e a retrocompat dos identificadores. Toca lexer/parser →
  frontend; sem mudança de ABI/runtime.
* **Fatia B — tipo de erro de interop** (sistema de tipos). A decisão faz do erro de interop um tipo
  da linguagem. Menor passo: o tipo + seu mapeamento para throws/catch existentes; prova = um erro
  estrangeiro surge como esse tipo e nunca é engolido (R6). Toca o sistema de tipos → lane compilador.
* **Fatia C — connector C-ABI, metade declarativa** (library-first, começa já). Compõe as peças do
  Core: `ForeignModule` (library+símbolos+ABI+posse) + `InteropCost` (custos visíveis declarados) +
  `InteropCompatibility` (tier de estabilidade) + `InteropLibrary` (`.so`/`.dylib`/`.dll`/`.a`/`.lib`);
  `describe()`/validação; o round-trip de runtime espera A/B. Kof puro; sem mudança no compilador.
* **Fatia D — tabela de tiers ABI** (documentação). A tabela concreta e a primeira versão estável
  estão decididas no `D-CONNECTORS` mas não transcritas; registrar antes da fatia de ABI.

**Ordem:** D (doc, destrava) → C (library-first, sem compilador) → A → B (fatias de compilador). As
fatias A/B são o primeiro ponto em que esta frente toca o compilador; não são só biblioteca.

---

# 14. Relação com outros planos

* `docs/ffi-abi-structs.md` — o substrato de ABI; este plano o consome, nunca o redefinir.
* `docs/development/graphics-gaming-plan.md` — a exceção nomeada ao R9 (engine própria);
  este plano fornece a camada FFI só para a superfície não-engine.
* `docs/development/future/PLAN-BOOTSTRAP.md` — E4 exige FFI structs ratificados; um Connector
  Core maduro fortalece o caminho do bootstrap.
* `docs/development/future/LEGACY_MIGRATION.md` / `TRANSLATOR.md` / `DECOMPILER.md` — a frente
  de legado compartilha a motivação de integração legada (COBOL/Pascal/Fortran); despriorizada
  separadamente.
* `docs/bugs-and-gaps/ecosystem-coverage.md` §3.15 — linhas de cobertura de interoperabilidade
  que este plano pode eventualmente alimentar.

---

# 15. Fora de escopo / não-promessas

* Sem datas e sem estimativas de esforço — planejar não é fila.
* Nenhuma mudança de linguagem é decidida aqui; toda mudança necessária é registrada como gap
  normal e independente de connector, e decidida pela mantenedora (regra 6).
* Nenhuma promessa de estabilidade de ABI antes de existirem testes de compatibilidade.
* Nenhuma sintaxe estrangeira embutida na Kof; nenhuma "linguagem Frankenstein".
* Nenhuma exposição automática de tipos externos complexos como se fossem ABI estável.
* Nenhum fallback silencioso, stub ou custo escondido (R6/Q7): caminhos não suportados falham
  com diagnóstico claro.
