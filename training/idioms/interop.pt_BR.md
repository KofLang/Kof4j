[English](interop.md) | [Português](interop.pt_BR.md)

# Idiomas — Interop (tipos JVM e FFI C)

**Status:** parcial (whitelist) · **Introduzido:** 0.3.x (TIER 2.1) · **Atualizado:** 21/09 (formas D6 struct/array/out-buffer LIGAM na JVM — record por valor in/out, `T[]` escalar→`ptr` copy-in, `Buffer(U8)` INOUT; ver a frente FFI em `IMPLEMENTATION-UNIVERSAL-PLATFORM.pt_BR.md` 3.8b)

## O que é

Duas superfícies, uma regra: a plataforma já existe — não a reconstrua.
**(a)** JVM: qualquer tipo Java no classpath por nome qualificado. **(b)** FFI C:
`extern "<lib>" f(T): R` prende uma função nativa (JVM via `java.lang.foreign`).

## API real (medida no compilador — 0.5.0-beta)

```kof
// (a) interop JVM — nome qualificado, sem wrapper
var now = java.time.Instant.now()
println(now.toString())

// (b) FFI C — o JVM liga QUALQUER assinatura ESCALAR (R3 generalizado 18/09):
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double   // ok
extern "/lib/x86_64-linux-gnu/libc.so.6" atoi(String s): Int    // ok (String->Int)
extern "/lib/x86_64-linux-gnu/libm.so.6" fmod(Double a, Double b): Double  // ok — 1.5 medido
extern "/lib/x86_64-linux-gnu/libm.so.6" ldexp(Double x, Int e): Double    // ok — 12.0 medido (misto)
extern "/lib/x86_64-linux-gnu/libc.so.6" puts(String s): void     // ok — void liga
extern "/lib/x86_64-linux-gnu/libc.so.6" getenv(String n): String // ok — "mel" medido
// O NOME da funcao Kof e o simbolo C (sem alias) — kof_fmod falhou no lookup, fmod funciona.
// Tipos nao-escalares -> FFI001 em tempo de compilacao (excecao JVM, 3.8b ✅ 20-21/09: um `record`
//   por valor arg/retorno e um `T[]` escalar->`ptr` LIGAM — FfiStructE2ETest 10/10, FfiArrayE2ETest 5/5).
// runner JS  -> MESMA ABI escalar via KofJsFfiBridge (F2/F3 ✅ 18/09; FfiE2ETest 16/16) E as formas D6
//   record/array/out-buffer (R54/R55/R57/R58/R59 ✅ 21/09, JVM==JS); browser -> erro honesto de runtime
//   (R7, sem host); uma forma genuinamente nao-suportada (String[]/List/Handle) -> FFI002
// Native (x86-64/riscv64/aarch64) -> MESMA ABI escalar binda DIRETA desde #431 20/09 (§61 FECHADO, §369):
//   sem dlopen — link-by-use da library() + call sym@PLT; String<->char* = payload UTF-8 no offset 24
//   (NULL->NULL); >=9 args da mesma classe derramam; retorno String = copia na fronteira (buffer C nunca free'd);
//   o glibc riscv64/aarch64 passa E devolve FP em fa0..fa7 (MEDIDO sob qemu — NAO ft0);
//   o stdio da C e flushado no exit; struct/array/callback/sem-library -> FFI001 na linha da declaracao
//   Argumentos numericos seguem a regra COMUM de conversao do Kof (#549/§370 FIXED 20/09): `f(Float x)`
//     aceita `f(4.0 as Float)`, `f(4.0)` (Double->Float) e `f(4)` (Int->Float) com o MESMO
//     resultado na JVM, no Native e no host JS; `sqrt(9)` (slot Double) e `labs(i)` idem.
//     O que o Kof nao converte (String/Bool em slot numerico, Double->Int estreitando) e SEM014
//     no call-site — nunca bits reinterpretados pela classe do slot.

// (c) CALLBACKS (C2 ✅ JVM + paridade JS C3 ✅, 18/09): uma função Kof entregue
// ao C como ponteiro de função. Parâmetro tipo-função + lambda no call site;
// só ABI de callback PRIMITIVA + arg `String` (síncrono, não-escapante):
extern "libcallback.so" kof_cb_add(Int a, Int b, (Int, Int) -> Int cb): Int
// call site — a lambda vira o ponteiro de função C (Linker.upcallStub):
kof_cb_add(20, 22, (x: Int, y: Int) -> x + y)   // 42 medido
kof_cb_mixed(3, 2.5, (i: Int, d: Double) -> i * d)  // ABI escalar mista ok
kof_cb_slen("hello", (x: String) -> x.length())  // char* -> arg String (C3.4)
// callback JS no host runner -> MESMA ABI (C3.2/C3.4 ✅ 18/09,
// jvmAndJsCallbacksMatchByteForByte 42/42/6.0/7.5; stringCallbackArgsBindAndMatchJvmJs 5/104/2026); browser -> degrade honesto
// em runtime (R7, sem host); struct/ponteiro-no-callback, retorno `String` e
// callback-como-retorno -> FFI001/FFI002 — nunca um stub silencioso.

// (d) STRUCT / ARRAY / OUT-BUFFER (D6, JVM — 3.8b fatias 1–4, 20–21/09):
//   um `record` de campos escalares atravessa POR VALOR (arg e retorno), um
//   `T[]` escalar atravessa como `ptr` com COPY-IN por chamada (somente-leitura),
//   e um out-buffer e o tipo nominal `Buffer(U8)` atravessando INOUT (copia-para-
//   dentro / chamada / copia-de-volta). Um buffer NUNCA e um reuso de
//   `String`/`Byte[]` — tipos ABI distintos (comprimento, direcao, mutabilidade).
record Pt(Int x, Int y)
extern "libshapes.so" mkpt(Int x, Int y): Pt          // record por valor (retorno; registrador ou sret)
extern "libshapes.so" ptlen(Pt p): Int                // record por valor (argumento)
extern "libshapes.so" sumn(Int[] xs, Int n): Int      // array escalar -> ptr (copy-in; o C nunca devolve)
extern "libshapes.so" fill(Buffer(U8) b, Int n): Int  // out-buffer INOUT (copy-back apos a chamada)
// call site:
var xs = new Int[3]                                   // `new Int[n]` e a surface de array (nao `[...]`)
var b = buffer.alloc(4)                               // Buffer(U8) — vida automatica (sem malloc/free)
fill(b, 4)                                            // o C escreve no buffer
println(b.bytes())                                    // clone Byte[] (le de volta)
println(ptlen(Pt(1, 2)))                              // 2 (record passado por valor)
// Native: extern com record/array/buffer = FFI001 (gap honesto, R6 — a ABI de
// struct/sret do Native é o 3.7). O JS binda as MESMAS formas D6 desde
// R54/R55/R57/R58/R59 (byte-for-byte JVM==JS).
// A ABI escalar binda em todo target; estas formas D6 sao JVM-first (R7).
```

## Reflexão na fronteira — `interop.schema(R)` (X6, `D-INTEROP-REFLECT`)

Uma visão **somente-leitura** da estrutura de um `record`, disponível **apenas
na fronteira de interop** (schemas Arrow/Parquet/ML), para os dados externos se
ligarem a records Kof sem mappers escritos à mão. É um **intrínseco de
compile-time** — zero reflexão em runtime, logo a mesma saída em todo target (sem
gap `REF001`). Ativada por `import kof.interop`; resolve para uma `List<Field>`
imutável com o `record Field(String name, String type)` fornecido pelo
compilador, na ordem de declaração.

```kof
import kof.interop

record Order(String id, Double amount, Long qty)

main() {
    for (var f in interop.schema(Order)) {
        println(f.name() + ":" + f.type())   // id:String, amount:Double, qty:Long
    }
}
```

Uso inválido é diagnóstico honesto (R6), nunca silêncio: membro desconhecido de
`interop` → `INTEROP002`; aridade errada, valor, classe ou enum (não um
`record`) → `INTEROP001`. Um `entity` conta como record. **Não** é fundação da
linguagem: sem metaprogramação em runtime, sem dispatch dinâmico, sem caminho de
escrita.

## RUIM → BOM

| ❌ RUIM | ✅ BOM | Por quê |
|---|---|---|
| ligar um simbolo sob outro nome Kof (`kof_fmod`) | o NOME e o simbolo C (sem alias, medido 18/09) — ligar `fmod`, envolver numa fn Kof para nome amigavel | multi-arg/`void`/retorno `String` ja ligam desde R3 18/09 — NAO emita bytecode na mao para furar o compilador |
| assumir que o caminho da lib é checado em compile | trate lib/símbolo ausente como falha `kof_ffi_*` de **runtime** | o caminho resolve em runtime (`SymbolLookup`), não em compile |
| guardar o ponteiro do callback para chamar DEPOIS (atexit/signal/async) | mantenha callbacks síncronos e não-escapantes | escapantes exigem política de vida/GC-rooting (R12) — ficam `FFI001`, nunca stub pendurado |
| reimplementar sin/cos/strcmp em Kof | prenda a lib do sistema (qualquer forma escalar desde 18/09) | complexidade é da plataforma (regra de ferro 2) |
| assumir que `library()` significa o mesmo em todo target | no JVM/JS e o caminho do dlopen; no **Native** resolve **por basename em LINK-time pelo sysroot** (`libc.so.6` → `-l:libc.so.6`; caminho absoluto do HOST e arch-errado no cross) | o Native nao tem FFM: um `extern` casado e um `call sym@PLT` + link-by-use (#431 20/09, §369) |
| reusar `Byte[]`/`String` para um out-buffer da C | declare o tipo nominal **`Buffer(U8)`** no `extern` e crie com `buffer.alloc(n)` (D-R3-BUFFER/D6-3) | um out-buffer e mutavel e bidirecional (copy-in + copy-back); `T[]` e copy-in somente-leitura e `String`/`char*` e somente-leitura — tipos ABI distintos |
| emitir bytecode na mao para um call de struct/array/out-buffer | declare o `record`/`new T[n]`/`Buffer(U8)` no `extern`; o compilador classifica a ABI (`AbiLayout`) | complexidade e do compilador (regra de ferro 2); uma ABI na mao vira bug silencioso no proximo target |
| escrever um mapper/schema por record a mao (nomes + tipos duplicados numa string) | derive de `interop.schema(R)` na fronteira | o compilador ja conhece a estrutura do record — zero reflexao em runtime, saida identica nos 4 alvos |
| criar `process.spawn("python3","-c",...)` na mão + JSON manual por chamada | `import kof.interop` + `var py = KofPy(fonte)` + `py.callInt("sq", listOf(5))` | o motor é stdlib (fatia 1 X2 26/09): o tipo do resultado é o NOME do método, os args são uma lista Kof tipada homogênea; as linhas do RPC, o quoting do spec e o nomear de erros (`INTEROP004`/`INTEROP006`) pertencem à plataforma — a sessão é a FONTE (definições persistem; mutações de globals não), e a face recusa com `INTEROP005` onde o runtime de processo não é provado (cross §514, ANDROID/MCU) |

## Veja também

`docs/language-reference/syntax.md` (§FFI com C), `grammar.md`
(`extern-declaration`), `modules.md` §6; gaps `FFI001`/`FFI002`;
R3 landado: JVM escalar arbitrario (aridade/void/retorno String, 18/09) + paridade JS host (3.6.F2/F3 ✅ 18/09) + **callbacks ligam na JVM E no host runner JS, paridade byte-for-byte (C2 ✅ + C3.2/C3.3/C3.4 ✅ 18/09 — callbacks primitivos + com argumento `String`; `JvmFfiCallbackE2ETest` incl. `jvmAndJsCallbacksMatchByteForByte` e `stringCallbackArgsBindAndMatchJvmJs`)** + **formas D6 struct/array/out-buffer na JVM (3.8b fatias 1–4 ✅ 20–21/09: record por valor in/out, `T[]` escalar→`ptr` copy-in, `Buffer(U8)` INOUT; `FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5, `BufferE2ETest` 4/4, `BufferFfiE2ETest` 4/4)** + **as MESMAS formas D6 no alvo JS (bridge 3.8b ✅ 21/09 — R54 record como arg, R55 `T[]` escalar→ptr copy-in, R57 namespace `kof.buffer`, R58 `Buffer(U8)` INOUT, R59 retorno de record por valor; byte-for-byte JVM==JS: `FfiStructE2ETest#structReturnByValueJsParity`, `FfiArrayE2ETest#arrayParamByValueJsParity`, `BufferE2ETest#allocAndBytesJsParity`, `BufferFfiE2ETest#bufferInoutCopyInCopyBackJsParity`)** + **a ABI escalar do Native bina nos 3 archs (fatias 1–2 ✅ 20/09 — §369, §61 FECHADO: `FfiNativeE2ETest` 16/16 x86-64 + `FfiNativeCrossE2ETest` 6/6 riscv64×aarch64 byte-identicos sob qemu)**; **decididos 21/09:** variadics = nenhum (`D-R3-3.5`), `Handle` opaco + `Buffer(U8,INOUT)` (`D-R3-3.3` — Buffer landou, `Handle` aguarda a frente RAII). Restantes (cross-lane/posterior): struct/sret no Native (3.7), callbacks/upcalls no Native (sem mecanismo — `FFI001`), tempos de vida do `Handle` (`future/scoped-resources-plan.md`).
