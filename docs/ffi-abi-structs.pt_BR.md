# ABI de struct/array na FFI — spec D6-A (CONCLUÍDA 23/09 — todas as fatias pousadas, promovida para `docs/`)

[English](ffi-abi-structs.md) | [Português](ffi-abi-structs.pt_BR.md)

**Status:** **CONCLUÍDO — D6 DECIDIDO (20/09) + todas as fatias pousadas (23/09) — movido para `docs/` pela regra dos três estados.** `docs/development/DECISIONS.md`
§D-FFI-STRUCT. D6-1 = B (`D-FFI-STRUCT-B`, 21/09: novo `struct` mutável por
referência; `record`s ficam por valor read-only, `Buffer(U8)` já cobre o
out-buffer) · D6-2 = só `new T[n]` · D6-3 = `Buffer(U8, INOUT)`, sem sintaxe
nova · D6-4 = implementar o sret completo · D6-5 = arena confinada por downcall.
**D6-1 B aprovado spec-first 21/09 (`D-FFI-STRUCT-B`)**: a superfície `struct`
mutável é desenhada aqui (§4/§6) e revisada antes de qualquer diff de
parser/typer (regra 11). O texto-proposta da §4 fica pelo raciocínio medido.
Este documento segue SOMENTE DESIGN.
**Execução após aprovação:** compiler lane (linha 3.8 do tracker) + native
lane (3.7).
**Pousou 20/09 (fatia sem decisão):** 3.8a `AbiLayout` — o substrato de
layout/classificação, com golden medido nas três ABIs (§6.1). O binding
(3.8b/3.7) procede sob as decisões D6 acima.
**Pousou 20/09 (3.8b fatia 1):** `record` Kof→struct C **por valor como
argumento** no JVM (token `@`; `FfiStructE2ETest` 6/6).
**Pousou 21/09 (3.8b fatia 2):** `record` Kof **devolvido por valor** no JVM
(registrador e sret; nome binário codificado em `@`+`:`, reconstrução pelo
construtor canônico; `FfiStructE2ETest` 10/10). Struct no Native = 3.7 (fatia 1
pousou 21/09 — ver abaixo).
**Pousou 21/09 (3.8b bridge JS · D6-1):** o runner JS agora binda um `record`
**por valor como ARGUMENTO** — token `@<n><chars>` carrega o layout dos campos
no fio (o host não reflete `RecordComponent` de um objeto GraalJS), o record
expõe `__kof_ffi_fields()` na ordem de declaração e o `KofJsFfiMarshal` empacota
o `StructLayout` (mesmos offsets/padding de cauda do JVM) na arena da chamada
(D6-5). Prova: `structParamByValueJsParity` — `sumpoint(Point(3,4))=7`,
`scale(Point(2,3),2.0)=10.0` e `parammix(ParamMix(3,2.5,4))=9.5` (layout j/d/i)
byte-a-byte JVM==JS. **Retorno** de struct no JS e `Buffer` no JS seguem
`FFI002`.
**Pousou 21/09 (3.8b fatia 3 · D6-2):** array escalar **`T[]`→`ptr` C** no JVM,
**copy-in por chamada** (token `p`+char do elemento; `new Int[n]` atravessa como
`int*`). O array Java não é pinado nem aliasado — o callee não escreve de volta
(isso é o out-buffer da D6-3, `Buffer(U8, INOUT)`). `String[]` (array de
ponteiros) segue FFI001; Native mantém seu gap code. `FfiArrayE2ETest` 5/5.
**Pousou 21/09 (3.8b fatia 3 bridge JS · D6-2):** o runner JS agora binda array
escalar **`T[]`→`ptr` C** com a mesma semântica de **copy-in por chamada** — o
`KofJsFfiMarshal.packArray` lê o array JS do guest e copia os elementos para a
arena da chamada (`p`+char do elemento; o C não escreve de volta). Prova:
`arrayParamByValueJsParity` (`sumn(Int[1,2,3])=6`, `sumd(Double[1.5,2.5])=4.0` e
`fill` prova não-aliasamento: `11/11/5`) byte-a-byte JVM==JS.
**Pousou 21/09 (3.8b fatia 4 bridge JS · D6-3):** o runner JS agora binda
`Buffer(U8)` como param INOUT também — o `KofJsFfiMarshal.packBuffer` copia os
bytes do `Uint8Array` do guest para a arena da chamada, o token `B` vira
`ADDRESS`, e o copy-back após o downcall devolve o resultado do C ao buffer do
guest (paridade com `kof_ffi_buffer_in`/`_out`). Prova:
`bufferInoutCopyInCopyBackJsParity` (`20/[10, 10]/40/[20, 20]`, o +10
acumulando entre chamadas) byte-a-byte JVM==JS.
**Pousou 21/09 (3.8b fatia 2 bridge JS · retorno de struct):** o runner JS agora
binda o **retorno** de struct também — o token de retorno é `@<n><chars>`
(layout no fio), o bridge materializa o struct por valor na arena da chamada (o
Linker recebe a arena como `SegmentAllocator` à frente) e lê os campos num
`Object[]`, e o `__kof_ffi_from` estático do record reconstrói a instância pelo
construtor canônico (coagindo `Long`→`BigInt` etc.; paridade com o
`kof_ffi_read_struct` reflexivo). Prova: `structReturnByValueJsParity`
(`Point`/`Big`/`Mix`/`ParamMix` — caminhos registrador e sret, mais um campo
`Long`) byte-a-byte JVM==JS. **Toda a superfície de param + retorno do JS está
pronta.**
**Pousou 29/09 (#651 fatia A2 · D6-3 · `Buffer(U8)` INOUT nativo x86-64):** o
extern nativo x86-64 agora binda `Buffer(U8)` como parâmetro INOUT. Diferente da
arena copy-in/copy-back do JVM/JS, o Buffer Kof nativo já é memória contígua,
então o emissor passa o endereço do payload (`obj+24`) direto para o C — a
escrita do C **é** o copy-back (um registrador INTEGER, como um `char*`). Prova:
`BufferFfiE2ETest#bufferInoutCopyInCopyBackNativeParity` com `.so` real
(`20/[10, 10]/40/[20, 20]`, acumulando +10 a cada chamada) byte-a-byte
JVM==Native; `InteropIdiomsCompileTest#nativeShapeExamplesBindOnX86` prova que as
formas documentadas deixam de emitir `FFI001`. Cross riscv64/aarch64 segue
`FFI001` (fatia B).
**Pousou 21/09 (3.7 fatia 1 · struct param nativo, caminho de registradores):** o
backend x86-64 SysV agora binda um struct `record` de campos escalares **por
valor como argumento** — o `FfiStructLayout` classifica via `AbiLayout` e o
call-site monta cada eightbyte direto no registrador de destino (INTEGER via
shift/or dos slots de 8 bytes do objeto Kof; SSE via `movq`/`movd`), sem spill
de scratch. O gate (`CompilerPipeline.nativeExternBound`) mantém o resto
honesto em `FFI001`: structs que vão à memória (SysV MEMORY / > 16 B), um
eightbyte SSE com mais de um campo, ou um struct que não cabe nos registradores
restantes. Prova: `FfiStructE2ETest`
`structParamByValueNativeRegisterPath` (`Point`/`MixIF` int+float no mesmo
eightbyte/`Time` long+double) byte-a-byte JVM==Native + `FfiStructLayoutTest`
3/3 (classificação, sem toolchain C).
**Pousou 21/09 (3.7 fatia 2a · retorno de struct nativo, caminho de
registradores):** o backend x86-64 SysV agora binda um `record` **devolvido por
valor** quando cabe nos registradores (≤ 16 B): o call-site salva os eightbytes
de retorno (`rax`/`rdx` + `xmm0`/`xmm1`) na pilha, aloca+inicializa o objeto Kof
e copia cada campo do seu eightbyte (shift + extensão pela largura) para o slot
de 8 bytes do objeto. Prova: `FfiStructE2ETest`
`structReturnByValueNativeRegisterPath` (`Point` = 1 eightbyte INTEGER, `Big` =
2 eightbytes INTEGER, `Mix` = SSE+INTEGER) byte-a-byte JVM==Native.
**Pousou 21/09 (3.7 fatia 2b · retorno de struct nativo, sret > 16 B):** o
backend x86-64 SysV agora binda o caminho **sret** (SysV MEMORY, `> 16 B`,
ponteiro escondido — D6-4): o caller passa o ponteiro escondido em `%rdi` e o
callee preenche o buffer. O call-site aloca o objeto Kof **antes** de popar os
args (a alocação é um call C e clobberaria os registradores de arg caller-saved;
os args ainda estão na pilha de operandos acima do frame do `kof_alloc`), põe o
buffer cru de retorno na pilha, passa seu endereço em `%rdi` e copia cada campo
do offset C direto para o slot Kof (largura natural do escalar). O ponteiro
escondido consome um registrador INTEGER, então `x86Bindable(paramTypes, 1)`
desloca os params (`rdi`→`rsi`…). Prova: `FfiStructE2ETest`
`structReturnSretNative` (`ParamMix` = `Long,Double,Int` = 20 B) byte-a-byte
JVM==Native; `FfiStructLayoutTest.sretReturnAndReservedIntRegister` (quando o
registrador reservado empurra um struct param para fora dos regs, a chamada não
é bindável).
**Pousou 22/09 (3.7 fatia 3 · retorno de struct INTEGER no cross, register path):**
os emissores riscv64 LP64 / aarch64 AAPCS64 agora bindam um `record` **devolvido
por valor** quando todo campo é classe INTEGER e o struct é ≤ 16 B (provado com
a `div` da libc → `div_t { int quot; int rem; }`): o call-site salva os words de
retorno (`a0`/`a1`; `x0`/`x1` no AAPCS64), aloca+inicializa o objeto Kof
(`kof_alloc`/`kof_init_object`) e extrai cada campo do seu word pela largura
natural. Float/HFA, > 16 B e o caminho de **parâmetro** struct no cross seguem
`FFI001` honesto (R6) — o lado do param também é bloqueado por ferramenta aqui
(sem compilador C cross para uma `.so` de fixture, §365). Prova:
`FfiNativeCrossE2ETest` `riscv64StructReturnViaLibcDiv`/`aarch64StructReturnViaLibcDiv`/
`crossStructReturnAgreesBetweenArchs` (qemu, golden `3\n1` = o oráculo JVM) +
`FfiStructLayoutTest.crossIntReturnIsBindableOnlyForIntegerRegisterPath`.
`T[]`/`Buffer` e as demais formas de struct riscv64/aarch64 seguem 3.7 (FFI001).
**Pousou 22/09 (3.7 fatia 4 · struct PARAM no cross, register path INTEGER):** os
emissores riscv64/aarch64 agora bindam um struct `record` de campos escalares
INTEGER **por valor como argumento** — o gate `nativeExternBound` aceita params
struct no cross só via `FfiStructLayout.crossIntRegisterOnly`/`crossBindable`, e
o `NativeFfiCall.emitRiscv` empacota cada eightbyte nos registradores inteiros
(`a0`/`a1`; `x0`/`x1` no AAPCS64 via tradutor). Float/HFA, > 16 B (BYREF/MEMORY)
seguem `FFI001` honesto (R6). Prova: `FfiCrossStructParamE2ETest` 5/5 (fixture
`.o` montada com o `as` cross, chamada sob qemu nas 2 archs, golden `42/2/6` +
campo negativo + 3×int=12 B/2 words + mix struct+escalar, mais 2 rejeições de
gate sem toolchain).

## 1. O que existe hoje (medido 19/09, não lembrado)

`extern name[("lib")] (params): Ret` vira um token de assinatura
(`FfiSignature.java`): `i`=Int, `j`=Long, `f`=Float, `d`=Double, `b`=Boolean,
`S`=String (`char*`), `v`=retorno void; `@`=record por valor (fatias 1–2),
`p<elem>`=array escalar `T[]`→`ptr` com copy-in (fatia 3); parâmetro callback é
o token aninhado `(<ret><params>)`. O que o mapa não cobre é **gap honesto em
tempo de compilação**: `FFI001` (JVM/Native não bindável) / `FFI002` (JS) —
`CompilerPipeline.java:225-236`, R6 (nunca stub silencioso).

| Superfície | JVM | Native | JS |
|---|---|---|---|
| downcall escalar | ✅ `kof_ffi` FFM (`JvmFfiRuntime.java:142+`) | ✅ **`call sym@PLT` direto em x86-64/riscv64/aarch64** (#431 fatias 1–2, 20/09, §369 — link-by-use, sem `dlopen`) | ✅ bridge do host `KofJsFfiBridge` (browser degrada honesto, R7) |
| callbacks/upcalls (3.4) | ✅ `Linker.upcallStub` | ❌ `FFI001` (sem mecanismo) | ✅ host |
| String = `char*` | ✅ entrada + saída | ✅ entrada (payload off 24) + saída (cópia na fronteira) | ✅ |
| **struct (record, campos escalares)** | ✅ **por valor entrada + retorno** (token `@`, 3.8b fatias 1–2, 20–21/09) | ◐ **por valor param + retorno, caminho de registradores *e* sret x86-64** (3.7 fatias 1–2b, 21/09); **retorno** INTEGER ≤ 16 B no cross binda (fatia 3, 22/09) e **retorno memory-path/sret** binda (30/09 face 3, `D-MEM-FFI-CROSS-FULL`); `T[]`/`String[]`/`Buffer` no cross bindam (faces 1–2 + `#651` B); **param** struct >16 B binda (30/09 face 3, BYREF ponteiro `a0`/`x0`, `FfiCrossStructParamE2ETest`); float/HFA em riscv64/aarch64 → `FFI001` (3.7) | ✅ **por valor ENTRADA + RETORNO** (IN: `@<n><chars>` + `__kof_ffi_fields`; OUT: retorno `@<n><chars>` + `__kof_ffi_from`, bridges 21/09) |
| **array escalar `T[]`→`ptr`** | ✅ **copy-in por chamada** (token `p<elem>`, 3.8b fatia 3, 21/09; sem write-back) | ✅ **copy-in** para `Long[]`/`Double[]`/`Int[]`/`Float[]`/`Bool[]` — x86-64 (3.7 passos 1–2, 22/09) E cross riscv64/aarch64 (30/09, face 1 de `D-MEM-FFI-CROSS-FULL`, `kof_ffi_pack_array`); `FfiNativeArrayE2ETest` (JVM==riscv64==aarch64); `String[]` (array de ponteiros) segue `FFI001` | ✅ **copy-in por chamada** (`packArray` bridge, 21/09; sem write-back) |
| **out-buffer `Buffer(U8)` INOUT** | ✅ **copy-in / chamada / copy-back** (token `B` + `buffer.alloc`/`Buffer.bytes()`, D6-3, 21/09) | ✅ **ponteiro do payload `obj+24` (a escrita do C é o copy-back)** no x86-64 (#651 fatia A2, 29/09); cross riscv64/aarch64 → `FFI001` | ✅ **copy-in / chamada / copy-back** (token `B` + `packBuffer`/copy-back após o downcall, bridge 21/09) |
| array não-escalar / opaco (ex. `String[]`/`List<T>`/`Handle`) | ❌ FFI001 | ❌ FFI001 | ❌ FFI002 |

Mapeamento escalar JVM→FFM (medido): `i→JAVA_INT, j→JAVA_LONG, f→JAVA_FLOAT,
d→JAVA_DOUBLE, b→JAVA_BOOLEAN, S→ADDRESS`; token não-escalar cai em
`ADDRESS` só no caminho de callback (`JvmFfiRuntime.java:88-106,144-145`).

**Verruga medida (CORRIGIDA 21/09 — 3.8b fatia 2, D6-5):** a política de arena
do downcall agora é **confinada por chamada, fechada no `finally`** nos helpers
escalares (`kof_ffi_i`/`kof_ffi_si`/`kof_ffi_dd`) e no `kof_ffi`. Antes, `i`/`dd`
usavam `Arena.global()` (o handle do `libraryLookup` nunca era liberado →
vazamento por chamada) e `si` abria arena confinada sem fechar. Prova:
`FfiE2ETest` 17/17 (`scalarHelpersRepeatStableUnderConfinedArena`: 300× cada
helper, idempotente).

## 2. Por que "struct" é mais duro do que parece (o custo real)

O lado JVM é quase grátis: o FFM já entende
`MemorySegment`/`StructLayout` e a classificação é feita pela própria
implementação SysV do JDK. **O custo se concentra no backend asm Native**,
que precisa implementar a classificação de struct por ABI manualmente
(§3) — por isso o tracker separa 3.8 (layout+JVM) de 3.7 (native), e por
isso esta spec vem antes de qualquer código.

## 3. Regras de layout e chamada por ABI (referências normativas)

Alinhamento natural (`alignof` do campo), tamanho arredondado para cima até
`alignof` do struct, padding final incluído; sem `#pragma pack` na v1.

| ABI | Regra de passo por valor (resumo) |
|---|---|
| x86-64 SysV | classifica cada *eightbyte*: INTEGER / SSE / SSEUP / NO_CLASS (≤ 8 campos no total); ≤ 16 B classe INTEGER → duas int regs (`rdi…`), ≤ 16 B SSE → XMM; maior que isso → **memória** (stack), cópia alocada pelo chamador |
| aarch64 AAPCS64 | teste HFA (≤ 4 floats homogêneos); senão ≤ 16 B → core regs `x0…` (classe eightword), > 16 B → stack; registro `w` para a metade alta quando misto |
| riscv64 LP64D | **MEDIDO 20/09 (corrige a prosa do rascunho "empacotados em doublewords a0…a7"):** struct ≤ 16 B com **≤ 2 campos** é *flattened* — campos de ponto flutuante em `fa0/fa1`, campos inteiros empacotados em `a0/a1` (`Time(Long,Double)`→`a0`+`fa0`; `{Float,Int}`→`fa0`+`a0`); com **3+ campos** empacota em doublewords inteiros (`{Int,Int,Int}`→`a0,a1`); > 16 B → **referência** (ponteiro para cópia do chamador), classe `Byref`. O `riscv64-linux-gnu-gcc` 13.3 do host é LP64D (hard-float), por isso o empacotamento soft-float do rascunho não batia |

Três exemplos resolvidos que os testes de implementação devem reproduzir bit a bit:

| Forma Kof | Forma C | size | align | classes SysV |
|---|---|---|---|---|
| `Point2(Int x, Int y)` | `struct{int,int}` | 8 | 4 | INTEGER (1 eightbyte) |
| `Mixed(Bool b, Int n, Float f)` | `struct{_Bool,int,float}` | 12 | 4 | padding após `b`; eightbyte 0 (b+n) = INTEGER, eightbyte 1 (f) = **SSE** — MEDIDO (GCC 13.3, x86-64 `-O0 -S`): primeiro eightbyte em `%rdi`, `f` em `%xmm0`; offsets n=4, f=8 (corrigido 20/09: o rascunho dizia INTEGER+INTEGER) |
| `Time(Long s, Double d)` | `struct{int64_t,double}` | 16 | 8 | INTEGER + SSE (SysV; MEDIDO: `s`→`%rdi`, `d`→`%xmm0`), 2 eightwords (aarch64). O Kof não tem `Int64` — o inteiro de 64 bits é `Long` (corrigido 20/09) |

## 4. Decisões de design — **DECIDIDO** (D-FFI-STRUCT, mantenedora 20/09/2026; rule 6)

> **Decidido:** D6-1 = **B** (`D-FFI-STRUCT-B`, 21/09: novo `struct` mutável
> por referência; `record`s ficam por valor read-only; `Buffer(U8)` cobre o
> out-buffer) · D6-2 = **só `new T[n]`** binda a `ptr` · D6-3 =
> **`Buffer(U8, INOUT)` sem sintaxe nova** · D6-4 = **implementar o sret
> completo** · D6-5 = **arena confinada por downcall**. Autoridade:
> `docs/development/DECISIONS.md` §D-FFI-STRUCT. O texto-proposta abaixo fica
> pelo raciocínio medido.

- **D6-1 · qual valor Kof mapeia para struct C?**
  A) `record` (estrutural, imutável, zero-ceremonia — default recomendado);
  B) um novo `struct` mutável (necessário para buffers *in/out*);
  C) ambos: records = by-value read-only, `struct` = by-ref.
  **Superseded:** a decisão foi escrita — `D-FFI-STRUCT` (20/09) e depois
  `D-FFI-STRUCT-B` (21/09) fixaram **D6-1 = B** (o `struct` mutável por
  referência; `record`s ficam por valor read-only).
- **D6-2 · mapeamento de array.** `List<Int>` é boxed (`ArrayList` no JVM) —
  bindar significa copiar para memória nativa a cada chamada. Proposta:
  arrays primitivos (`new Int[n]`, que já existem) bindam como `ptr` (sem
  parâmetro de comprimento implícito — quem decide é a API C); `List<T>`
  permanece FFI001 até um benchmark de unboxing provar o contrário.
  **✅ fatia 3 POUSOU 21/09 (JVM, copy-in por chamada, token `p<elem>`) — a
  proposta acima, exatamente; `List<T>` segue não bindado.**
- **D6-3 · parâmetros out.** Sem sintaxe nova na v1: out-buffer =
  `new Byte[n]` cruzando como tipo ABI PRÓPRIO — `Buffer(U8, INOUT)`, copia-para-dentro /
  chamada / copia-de-volta — **nunca o token `S`** (corrigido 20/09: `S` = `String` = `char*`
  UTF-8 terminado em NUL, somente-leitura; um buffer difere em mutabilidade, comprimento,
  direção e tempo de vida, então não pode reusar `S`; `CString`, `Buffer`, `Pointer`,
  `OpaqueHandle` e `Struct` são tipos ABI distintos mesmo quando todos viram um endereço
  num registrador). O comprimento segue argumento explícito do C.
  Campos ponteiro-em-struct ficam fora (handles opacos são 3.3, decisão
  separada).
  **Pousou 21/09 (D-R3-BUFFER/D-R3-HANDLE-LIFETIME):** a grafia nominal é
  **`Buffer(U8)`** (não um reuso de `Byte[]`), criado com
  **`buffer.alloc(Int) : Buffer(U8)`** — o programador nunca aloca/libera
  (vida gerenciada pela linguagem; `Handle` segue a mesma regra automática).
  Fatias
  (JVM + superfície Native x86-64, #651 fatia A1): `buffer.alloc` + `Buffer.bytes() : Byte[]` (`BufferE2ETest` paridade JVM/JS/x86)
  e `Buffer(U8)` como parâmetro INOUT de `extern` — **copy-in / chamada /
  copy-back** (`BufferFfiE2ETest` 4/4 com shim C real: as escritas acumulam
  entre chamadas, provando que o copy-in lê e o copy-back escreve). A superfície
  Native x86-64 de namespace/print pousou no #651 fatia A1 (28/09) e o token FFI
  `B` no Native pousou na fatia A2 (29/09) — o emissor passa `obj+24` (payload)
  direto no x86-64, provado por
  `BufferFfiE2ETest#bufferInoutCopyInCopyBackNativeParity`; o cross
  riscv64/aarch64 segue honesto até a fatia B (R6-SCOPE: incremental).
- **D6-4 · retorno by-value > 16 B.** SysV hidden-pointer (sret) /
  AAPCS64 hidden-x8 / LP64 referência — o Linker do *JVM* esconde isso; o
  backend *asm* precisa implementar sret explicitamente. Alerta: é o maior
  custo single da native lane; as fatias em §6 o isolam.
- **D6-5 · ownership de String/arena (conserta a verruga do §1).**
  Proposta: arena confined por downcall, fechada após a chamada; `char*`
  retornado é **copiado e nunca possuído** (String Kof é imutável — o
  ponteiro C não pode sobreviver à chamada, salvo API C que documenta
  transferência de posse, que é a história do `free()` de 3.3).

## 5. Não-objetivos (v1)

bitfields; unions anônimas; `#pragma pack`/`alignas`; `long double`
(x87 80-bit — código de gap próprio se um dia); `wchar_t`/UTF-16;
callbacks struct-typed (fn-ptr aninhado em struct); variadics (3.5 — `D-R3-3.5` ✅ decidido 21/09: **sem variadics gerais**, gap documentado); name mangling C++; regras de COMDAT/seção. Cada item permanece
FFI001/002 honesto até decidido — nada de binding parcial silencioso.

## 6. Divisão de trabalho (após aprovação — não é esta lane)

1. **3.8a** engine de layout: `AbiLayout` (size/align/classes por triple) no
   compiler, dados puros + golden tests contra os três exemplos resolvidos
   (§3). **✅ POUSOU 20/09** — `AbiLayout.java` + `AbiLayoutTest` (14 shapes ×
   3 ABIs, golden medido com GCC 13.3 em x86-64/aarch64/riscv64 e reprovado ao
   vivo com `_Static_assert` contra os compiladores reais). Não binda nada e
   não decide nada de D6-1..D6-5; é o substrato compartilhado que 3.8b/3.7
   consomem.
2. **3.8b** binding JVM: records→`StructLayout` no `kof_ffi` (FFM faz a
   classificação); política de arena D6-5. **✅ fatia 1 (param por valor,
   20/09) + fatia 2 (retorno por valor: registrador + sret, 21/09) + fatia 3
   (D6-2: `T[]` escalar→`ptr`, copy-in por chamada, 21/09) + fatia 4 (D6-3:
   out-buffer `Buffer(U8)` — `buffer.alloc`/`Buffer.bytes()` + `extern` INOUT
   copy-in/copy-back, 21/09) POUSARAM** —
   só o subconjunto de campos escalares; `struct` mutável (D6-1 B) é superfície
   nova da linguagem sob a Lei da Simplicidade (regra 11), decisão separada.
   Os bridges no JS pousaram 21/09 para struct **param** (pack no host, D6-5),
   array escalar **`T[]`→`ptr` copy-in** (D6-2, `packArray`), `Buffer(U8)`
   INOUT (D6-3, `packBuffer` + copy-back) e o **retorno** de struct
   (`__kof_ffi_from`). A superfície de FFI do JS (param + retorno) está completa;
   o trabalho D6 restante é o Native cross (3.7 fatia B) — as faces x86-64 de
   struct/array/`Buffer(U8)` pousaram (3.7 D6-2 + #651 fatia A2).
3. **3.7** asm native: classificação manual por target. **✅ fatias 1–2b
   POUSARAM 21/09 (struct param + retorno x86-64, caminho de registradores *e*
   sret > 16 B — `FfiStructLayout` + pack/materialização no call-site, golden
   JVM==Native) + fatia 3 POUSOU 22/09 (RETURN de struct INTEGER ≤ 16 B no
   cross, riscv64/aarch64, golden qemu)** + **fatia 4 POUSOU 22/09 (PARAM struct
   no cross, register path INTEGER)**; restante: float/HFA/> 16 B no cross,
   `T[]`/`Buffer(U8)` no cross (`FFI001` honesto, R6). O token FFI `Buffer(U8)`
   `B` no x86-64 **pousou no #651 fatia A2 (29/09)** — ponteiro do payload
   (`obj+24`) direto, a escrita do C é o copy-back
   (`BufferFfiE2ETest#bufferInoutCopyInCopyBackNativeParity`).
4. **JS**: ✅ **COMPLETO 21/09** — struct param + retorno, array escalar
   copy-in, `Buffer(U8)` INOUT (bridges `structParamByValueJsParity`,
   `structReturnByValueJsParity`, `arrayParamByValueJsParity`,
   `bufferInoutCopyInCopyBackJsParity` — todos byte-a-byte JVM==JS).
5. **DoD (R5) ✅ 23/09**: matriz E2E golden por target medida
   (`FfiStructE2ETest` 12/12, `FfiNativeCrossE2ETest` 10/10,
   `FfiCrossStructParamE2ETest` 5/5, `FfiNativeArrayE2ETest` 2/2,
   `FfiArrayE2ETest` 5/5, `BufferFfiE2ETest` 5/5 (Buffer INOUT x86-64, A2 29/09),
   `FfiStructLayoutTest` 5/5,
   `FfiE2ETest` 17/17 — **61/61 verde 29/09**); FFI00x inalterado para
   tudo que não for coberto; `training/idioms/interop.md` carrega as formas
   D6 (record por valor, `T[]`→`ptr`, `Buffer(U8)` INOUT). **Este doc está
   CONCLUÍDO — promover para `docs/` pela regra dos três estados.**

## 7. Native 3.7 restante — todo de implementação (lane FFI/kof-c, reivindicada 22/09)

A próxima fatia é maior que uma sessão, decomposta para que cada passo seja um
vertical completo (nenhum caminho meio-ligado, R6):

> **LANDADO 22/09 (3.7 passos 1–2 · `T[]`→`ptr` no x86-64, D6-2):** `Long[]`→
> `long*`, `Double[]`→`double*`, `Int[]`→`int*`, `Float[]`→`float*` e
> `Bool[]`→`bool*` bindam com **copy-in por chamada** — a largura de slot do
> elemento iguala a largura C (8/4/1 B), então o pack é um `memcpy` de
> `len*elemSize`. `FfiStructLayout.arrayPtrType`/`isArrayPtr`/`arrayPtrElem`, gate
> `CompilerPipeline.nativeExternBound` (só x86), marker de lowering em
> `ExpressionMethodCallLowerer`, pré-passo de pack no `emitX86` + helper
> `kof_ffi_pack_array` (tamanho do elemento em `%rsi`) atrás de
> `NativeBackend.ffiUsesArray`. Corrige um crash latente do JVM achado no caminho
> (R6): `bool[]` não é suportado por `MemorySegment.copy`, então `kof_ffi_copy_in`
> converte para `byte[]` 0/1 antes. Prova: `FfiNativeArrayE2ETest` 2/2 (shim `.so`
> do gcc, golden byte-a-byte igual ao oráculo JVM, bordas vazio/negativo/`Bool[]`)
> — passo 3 abaixo restante.

1. **✅ FEITO (22/09) — `T[]`→`ptr` C no x86-64, copy-in por chamada (D6-2).**
   Classes de elemento cuja largura de slot Kof iguala a largura C copiam com
   `memcpy` simples: **`Long[]`/`Double[]` (8 B), `Int[]`/`Float[]` (4 B),
   `Bool[]` (1 B)**. `String[]` (array de ponteiros) segue `FFI001` neste corte.
   - gate `CompilerPipeline.nativeExternBound`: aceitar param array no x86
     quando `FfiSignature.arrayElemChar` não é nulo.
   - lowering `ExpressionMethodCallLowerer` (branch nativo): adicionar um tipo
     de param sintético `kof.ffi`/`array`(`elem`) em vez de null; pular a
     coerção escalar para ele.
   - emissor `NativeFfiCall.emitX86`: empacotar cada arg array num buffer novo
     de `kof_alloc` via o helper de runtime `kof_ffi_pack_array` (emitido como
     `emitX86CstrHelper`, atrás de uma flag `ffiUsesArray` do backend) e passar
     o buffer como um registrador INTEGER; só copy-in — escritas da C são
     descartadas, exatamente como no JVM (paridade, regra 5).
   - prova: um shim `.so` compilado com gcc (`long*`/`double*`/...) ligado ao
     binário nativo, golden medido contra o oráculo JVM do mesmo programa
     (`FfiNativeArrayE2ETest`), + pin de gate `String[]`/array cross→`FFI001`.
2. **✅ FEITO (22/09) — pack de `Int[]`/`Float[]`/`Bool[]`.** Não foi preciso loop
   de estreitamento: a largura do elemento Kof já é igual à largura C
   (`Int`/`Float` 4 B, `Bool` 1 B), então o mesmo helper copia `len*elemSize`
   com o tamanho passado em `%rsi` pelo call-site. O gate aceita todos os chars
   de elemento escalar no x86-64 (`String[]` e cross seguem `FFI001`).
3. **`String[]`→`char**` e `Buffer(U8)` nativos** — `Buffer` precisa do tipo
   nominal + runtime no Native primeiro (hoje só JVM/JS); `String[]` é array de
   ponteiros (distinto do copy-in escalar). Ambos seguem `FFI001` até o seu
   próprio corte.
