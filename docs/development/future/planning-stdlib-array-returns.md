# Planning — retorno Array/objeto na camada de dispatch stdlib (DD-STDLIB-01)

> **Status:** `PROPOSED` (aguarda decisão da mantenedora) · **Gap:** S10c
> (`random.randomBytes` / `random.randomChoice`) · **Lane:** STDLIB ·
> **Criado:** 09/09/2026 · **Bump:** nenhum (aditivo — só abre caminho)

## O problema

O plano S10 lista `randomBytes(n)` (retorna bytes) e `randomChoice(List)`
(retorna elemento) no namespace `random`
(`plan-stdlib-expansion.md:45`). As duas exigem que uma função **stdlib**
retorne um tipo composto pela camada de dispatch (`KofStd.StdCall` →
`KofCall` → runtime dos 5 alvos). Hoje **nenhuma** função stdlib retorna
Array/objeto — o vocabulário da camada é `Int/Bool/String/Void`:

- `KofMath`: Int-only (`kof_math_*` → `(I)I`); FP fica S1b.
- `KofStrings`: STR→STR; `split` não é stdlib (é método de `String`, rota
  própria com `kof_new_array` embutido no lowerer de método).
- `KofUuid`/`KofNet`/`KofEncoding`/`KofValidation`: STR/BOOL.
- `KofRandom` (S10a/b): INT/BOOL/STR.

Arrays NATIVOS existem (`kof_array_alloc`/`get`/`set` no x86; alocador riscv
B-slices; `List<T>` interp com reflection JVM) — o que não existe é o
**plumbing de tipo** `Type.ArrayType` atravessar `KofStd → KofCall →
descritores JVM → JsTypeMapper → ABI asm`. Isso é decisão de design (regra
6): mexe em cinco contratos de alvo, e a forma do retorno (byte[]? List?
String hex?) afeta a API congelada.

## Opções

| Opção | randomBytes | randomChoice | Custo |
|---|---|---|---|
| **A. Tipo-Array no dispatch** | `Bytes -> ByteArray` | `Choice(List) -> Object` | plumbing completo; 5 ABI; `ArrayType` nos descritores |
| **B. Hex String** (recomendada p/ bytes) | `randomBytesHex(n) -> String` | — | zero plumbing; reusa a máquina de String dinâmica do S10b; precedent: `security.randomHex` existe! |
| **C. Sem randomChoice** | — | `l[random.randomInt(l.size)]` em Kof puro | zero — a linguagem já resolve |

## Recomendação

- **`randomChoice` NÃO precisa de runtime**: `list.get(random.randomInt(list.size))`
  é 1 linha Kof idiomatica (a regra da língua: complexidade pertence a quem
  usa, não à plataforma — e `get`+`size` já existem nos 5 alvos). Fechar o
  item do plano como "coberto por idiom", documentar em `learn/39` +
  `training/idioms`. Se a mantenedora quiser o açúcar, vira feature de
  `List` (não de `random`).
- **`randomBytes`**: a versão segura já existe (`security.randomHex(n)` —
  hex string, 5 alvos). Se o plano mantiver a face insegura, o shape
  coerente é idêntico: **Opção B** (`random.bytes` devolve o mesmo shape
  hex? NÃO — bytes binário ≠ hex; abriria divergência de paridade no tipo
  de retorno). **Pergunta à mantenedora:** o valor de `randomBytes` binário
  justifica o plumbing do tipo-Array (Opção A) agora, ou o nome fica
  reservado e `security.randomHex`/`security.randomBytes` cobre o caso de
  uso real (que é SIEMPRE criptográfico — token/salt/chave)?

Nenhum dos dois entra por edição direta da camada atual. `random` fecha
v1 com `randomInt/randomBoolean/randomString` (S10a/b) + idiom p/ choice.

## Impacto se aprovada a Opção A (só para registro)

`KofStd.StdCall.returnType` aceitar `ArrayType` → `JvmRuntimeCallDescriptors`
(case `"[B"`), `JvmRuntimeReturnDescriptors` (`"[B"`), `KofInterpreter`
dispatch, `JsTypeMapper.runtimeJsName` (invariante — só nome), ABI x86
(rdi/len → rax já é o shape de `kof_array_alloc`), riscv/aarch (idem B14).
Estimativa: 1 unidade por alvo (como S1–S10b) + matriz equality com
elemento-walk. NÃO começa sem decisão.
