# Conformance Matrix — Feature × Target (Fase 9, plano de plataforma)

> **Criado:** 07/09/2026 · **Dono:** lane KOFSCRIPT (fixes-for-kofagent)
> **Plano:** `docs/development/future/PLATFORM-PLAN.md` Fase 9 ·
> **Roadmap-audit:** linha 12 "Conformance Suite — NOT STARTED (BackendParityTest é proxy)" + fila P4.
>
> **Regra:** cada célula é travada por teste em `ConformanceMatrixTest`
> (kof-compiler/src/test). **DONE** = os 4 targets concordam com a saída
> esperada. **PARTIAL** = divergência com bug registrado em
> `docs/development/known-bugs.md` (ref na célula). **UNSUPPORTED** = gap
> honesto (R6: diagnóstico, nunca stub silencioso).
>
> Targets: **JVM** (bytecode compilado), **Native** (x86_64 ELF; riscv64/
> aarch64 via qemu = follow-up), **Script** (interpretador de IR — o alvo
> de execução direta), **KofJS** (GraalJS). Casos determinísticos apenas
> (sem tempo real/concorrência — esses têm testes próprios: SpawnE2ETest,
> KofTimeE2ETest, KofMqE2ETest).

## Matriz (lote 1 — linguagem core)

| Feature | Saída esperada | JVM | Native | Script | KofJS | Caso (ConformanceMatrixTest) |
|---|---|---|---|---|---|---|
| aritmética int + overflow | `-2147483648` / `-1` / `1` | DONE | DONE | DONE | DONE | `arith` |
| long div/mod | `3333333333` / `4` | DONE | DONE | DONE | DONE | `longdiv` |
| cast `d as Int` / `L as Int` / `66 as Char` | `9` / `70000` / `66` | DONE | DONE | DONE | DONE | `cast` |
| float println | `0.3333333333333333` / `5.0` / `3.5` | DONE | PARTIAL (bug 44: 6 casas + `5`) | DONE | PARTIAL (doc: `5` vs `5.0`) | `floatprint` |
| string unicode length/charAt | `4` / `233` / `café!` | DONE | PARTIAL (bug 43: UTF-8 5/195) | DONE | DONE | `unicode` |
| string ops split/toLowerCase/trim | `4` / `hello world` / `x\|` | DONE | DONE | DONE | DONE | `strops` |
| map put/get/size | `1` / `2` | DONE | DONE | DONE | DONE | `map` |
| list empty/isEmpty/contains | `true` / `0` / `false` | DONE | DONE | DONE | DONE | `emptylist` |
| `null == null` / `!=` | `true` / `false` | DONE | DONE | DONE | DONE | `nulleq` |
| if-expr curto-circuito null | `iguais` / `nao-ne` | DONE | DONE | DONE | DONE | `nulleqshortcut` |
| set dedup/contains | `3` / `true` / `false` | DONE | DONE | DONE | DONE | `setdedup` |
| if-expression aninhada | `small` | DONE | DONE | DONE | DONE | `nestedif` |
| switch-expression `case ->` | `three` | DONE | DONE | DONE | DONE | `switchexpr` |
| for-in + break/continue | `4` | DONE | DONE | DONE | DONE | `breakcont` |
| record `==` conteúdo + toString + accessor | `true` / `P[x=1, y=2]` / `1` | DONE | DONE | DONE | DONE | `record` |
| record `hashCode()` igual | `true` | DONE | PARTIAL (bug 42: ld P_hashCode) | DONE | PARTIAL (bug 42: TypeError) | `recordhash` |
| lambda filter/map/reduce | `90` | DONE | DONE | DONE | DONE | `lambdachain` |
| lambda captura mutável | `3` | DONE | DONE | DONE | DONE | `lambdacapture` |
| array 2D/length | `60` / `3` | DONE | DONE | DONE | DONE | `array2d` |
| campo estático + bump | `1` / `2` / `2` | DONE | PARTIAL (bug 41: stub vazio, lixo) | DONE | DONE | `staticfield` |
| campo estático `+=` | `2` / `4` / `4` | DONE | PARTIAL (bug 41) | DONE | DONE | `staticpluseq` |
| concat string+num (ordem) | `n=42` / `3x` / `x12` | DONE | DONE | DONE | DONE | `concat` |
| lógica booleana + comparação | `false` / `true` / `false` / `true` | DONE | DONE | DONE | DONE | `boollogic` |
| bitwise & \|\| ^ << >> | `2` / `7` / `5` / `16` / `64` | DONE | DONE | DONE | DONE | `bitwise` |
| recursão profunda (fact 10) | `3628800` | DONE | DONE | DONE | DONE | `recursion` |
| list add/set/remove | `99` / `4` / `2` / `3` | DONE | DONE | DONE | DONE | `listops` |
| map keys() + iteração | `6` | DONE | DONE | DONE | DONE | `mapiter` |

## Matriz (lote 2 — erros/null/JSON)

| Feature | Saída esperada | JVM | Native | Script | KofJS | Caso |
|---|---|---|---|---|---|---|
| try/catch throw-as-String | `caught:not found: x` / `after` | DONE | DONE | DONE | DONE | `trycatch` |
| try/catch/finally (sem throw) | `in` / `fin` / `after` | DONE | DONE | DONE | DONE | `trycatchfin` |
| throw propagando p/ catch externo | `got:kaboom` | DONE | DONE | DONE | DONE | `throwprop` |
| try aninhado | `caught-inner:inner` / `end` | DONE | DONE | DONE | PARTIAL (bug 49: COMP002) | `nestedtry` |
| null-safety narrowing (`!= null`) | `val=1` / `null-ok` | DONE | DONE | DONE | DONE | `nullnarrow` |
| json.encode int/string/bool | `42` / `"oi"` / `true` | DONE | DONE | DONE | DONE | `jsonenc-int` |
| json.encode lista | `[1,2,3]` | DONE | DONE | DONE | DONE | `jsonenc-list` |
| json.encode record | `{"x":1,"y":2}` | DONE | DONE | DONE | DONE | `jsonenc-record` |
| json.decode int/string/bool | `7` / `oi` / `true` | DONE | DONE | DONE | DONE | `jsondec-int` |
| json.decode lista de primitivo | `3` / `2` | DONE | DONE | DONE | DONE | `jsondec-list` |
| json.decode record | `1` / `2` | DONE | DONE | DONE (fix 07/09) | DONE | `jsondec-record` |
| json.decode lista de record | `2` / `2` | DONE | PARTIAL (bug 48: não compila) | PARTIAL (bug 48: exit 1 R6) | DONE | `jsondec-recordlist` |

> **Fix 07/09 (lane interpreter):** `json.decode<Record>` no interpretador
> dava exit 1 + stderr só `Point` (R6) — o método gerado `kof_json_decode_Point`
> faz `Class.forName`, mas no interpretador a classe Kof é `KofObj`. Corrigido
> em `KofInterpreterRuntime.decodeKofValue` (espelha `encodeKof`). O caso
> `jsondec-record` passou de PARTIAL(Script) → DONE.

## Notas de método

- **Oráculo:** a saída esperada é a do **comportamento documentado**
  (corpus `training/`/`learn/` + `docs/backend-parity.md`), não "o que o
  JVM imprime" — onde o JVM diverge do documento, é bug do JVM, não
  do target.
- **Native x86_64** é o "runtime de referência" do Native; riscv64/aarch64
  herdam via `translateRiscvToAarch64` e são cobertos por
  `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` (20/20 cada) — o follow-up
  desta matriz é estender os casos aqui para qemu.
- **Script = JVM no interpretador** (`runFile(f, Target.JVM)`/`SCRIPT`):
  por construção roda a MESMA IR otimizada do frontend — divergência
  Script×JVM-compilado é bug do interpretador OU do lowering (ambos
  têm gate de paridade próprio: `KofInterpreterParityTest` 16/16).
- **Riscar célula** = editar a matriz + commit; o teste é a prova, a
  matriz é o índice (R6: o teste falha antes da doc divergir).
