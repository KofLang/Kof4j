# KOF — PERFORMANCE, BENCHMARKS, RESOURCE SAFETY E GUIDELINES ARQUITETURAIS

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes; 37 benchmarks; `kof bench` + `benchmark.yml` threshold 1.20)

> Este documento define princípios arquiteturais permanentes do Kof.
>
> Não são sugestões.
> Não são features opcionais.
> Não são objetivos cosméticos de marketing.
>
> Type system, IR, backends, runtime, standard library e tooling devem respeitar estas regras.

---

# 1. PRINCÍPIO FUNDAMENTAL

Kof foi projetado para remover complexidade do código sem transferir essa complexidade para runtime.

A linguagem deve buscar simultaneamente:

```text
alta expressividade
+
forte segurança de tipos
+
baixo overhead
+
baixo consumo de recursos
+
alta performance
+
excelente interoperabilidade
```

A abstração existe para beneficiar o programador.

Quando uma abstração não é necessária em runtime, ela deve desaparecer durante a compilação.

A regra fundamental é:

> **Kof deve tentar gerar a representação mais eficiente semanticamente possível para cada construção.**

---

# 2. JAVA NÃO É O TETO DE PERFORMANCE

Java é uma referência de:

* ecossistema;
* interoperabilidade;
* semântica de plataforma;
* bibliotecas;
* JVM;
* compatibilidade.

Java **não é o limite de performance do Kof**.

A comparação conceitual é:

```text
Java
 ↓
javac
 ↓
JVM bytecode
 ↓
HotSpot/JIT
```

contra:

```text
Kof
 ↓
Lexer
 ↓
Parser
 ↓
AST
 ↓
Semantic Analysis
 ↓
Type System
 ↓
Kof IR
 ↓
JVM Backend
 ↓
JVM bytecode
 ↓
HotSpot/JIT
```

O Kof possui conhecimento semântico do programa antes da geração do bytecode.

Esse conhecimento deve ser usado.

---

# 3. REGRA DE SUPERIORIDADE DE PERFORMANCE

Quando existir código Java e código Kof semanticamente equivalentes:

```text
Java idiomático
vs
Kof idiomático
```

o Kof deve procurar gerar uma representação:

```text
igual ou melhor
```

em relação ao Java.

Mais importante:

> **Nunca reproduzir overhead do Java simplesmente porque uma implementação Java tradicional faz dessa maneira.**

Se o compilador puder provar que determinada abstração pode ser eliminada sem alterar a semântica, ela deve ser eliminada.

Exemplos:

```text
boxing
allocation
iterator
temporary object
reflection
indirection
dispatch
wrapper
```

não devem existir simplesmente por conveniência da implementação do compilador.

---

# 4. KOF/JVM

A JVM não deve ser tratada como desculpa para gerar bytecode medíocre.

O backend JVM deve buscar bytecode excelente para o HotSpot.

Priorizar:

* tipos concretos;
* acesso direto;
* métodos simples;
* dispatch previsível;
* ausência de boxing desnecessário;
* ausência de allocations desnecessárias;
* ausência de reflection desnecessária;
* loops eficientes;
* control flow simples;
* metadata correta;
* StackMapTable correta;
* estruturas amigáveis ao JIT.

Exemplo:

```kof
var total = 0

for (x in values) {
    total = total + x
}
```

Se `values` permitir, o compilador deve buscar uma representação equivalente a um loop direto.

Não deve gerar automaticamente:

```text
Iterator allocation
↓
hasNext()
↓
next()
↓
boxing
↓
temporary objects
```

apenas porque esse seria um caminho fácil de implementar.

---

# 5. KOF/NATIVE

No Native o Kof possui controle ainda maior sobre a execução.

Portanto, a expectativa de eficiência deve ser ainda mais agressiva.

> **Estado (0.2.6-beta, 31/08):** alocação via free-list `kof_free_head`
> (reuso `mmap` — reduz o custo de mmap por alocação); FP em XMM
> (`vcvtsi2sd`/`mulsd`) em vez de fallback em int; dtoa via `snprintf`;
> `spawn` em threads reais (`pthread`) — sobrecarga de contexto documentada
> nos benchmarks de concorrência.

Priorizar:

* baixo startup;
* baixo consumo de memória;
* baixo número de allocations;
* baixo overhead de chamadas;
* baixo overhead de abstrações;
* binaries enxutos quando possível;
* syscalls eficientes;
* uso eficiente de cache;
* loops eficientes;
* stack usage previsível;
* gerenciamento de memória eficiente;
* ausência de runtime desnecessariamente grande.

A meta arquitetural é que Kof/Native seja capaz de superar implementações equivalentes sempre que o controle adicional do backend permitir.

---

# 6. KOF/JS

O backend JS deve gerar JavaScript natural e eficiente.

Evitar:

* wrappers desnecessários;
* objetos temporários;
* closures artificiais;
* boxing;
* dispatch indireto;
* runtime gigantesco;
* abstrações que poderiam ser eliminadas.

Quando uma construção Kof puder ser traduzida diretamente para uma construção eficiente do ECMAScript, preferir a representação direta.

---

# 7. KOF/SCRIPT

Kof/Script deve continuar sendo baseado na infraestrutura existente do compilador.

Não criar um segundo compilador apenas para script.

O modo Script deve priorizar:

* startup rápido;
* baixa latência;
* baixo consumo;
* execução previsível;
* integração simples;
* runtime mínimo;
* reutilização do frontend;
* reutilização do type system;
* reutilização da IR.

Script não significa lento.

---

# 8. ZERO OVERHEAD DESNECESSÁRIO

Toda feature nova deve responder:

```text
Qual é o custo dessa abstração em runtime?
```

Se a resposta for:

```text
nenhum
```

porque ela desaparece durante a compilação:

ótimo.

Se houver custo:

```text
qual?
por quê?
é realmente necessário?
pode ser eliminado?
```

A implementação não deve esconder overhead.

---

# 9. ZERO BOXING DESNECESSÁRIO

Valores primitivos devem permanecer primitivos quando semanticamente possível.

Exemplo:

```kof
Int
Long
Bool
Char
```

não devem ser automaticamente transformados em objetos.

Preferir:

```text
ILOAD
ISTORE
IADD
```

a:

```text
allocate Integer
↓
unbox
↓
operation
↓
box
```

quando o boxing não for necessário.

O type system e a IR devem preservar informação suficiente para o backend tomar essa decisão.

---

# 10. ZERO REFLECTION DESNECESSÁRIA

Reflection é necessária para:

* interoperabilidade;
* frameworks;
* metadata;
* APIs explicitamente reflexivas.

Mas não deve ser usada para operações normais que possam ser resolvidas estaticamente.

Exemplo:

```kof
user.name
```

deve preferir:

```text
GETFIELD
```

ou equivalente.

Não:

```text
reflection
↓
field lookup
↓
invoke
```

sem necessidade.

---

# 11. ZERO DISPATCH DESNECESSÁRIO

Se o compilador conhece o alvo de uma chamada, deve utilizar a forma mais direta possível.

Por exemplo:

```text
INVOKESTATIC
INVOKESPECIAL
INVOKEVIRTUAL
INVOKEINTERFACE
```

devem ser escolhidos de acordo com a semântica real.

Não criar dispatch dinâmico artificial.

O mesmo princípio vale para Native e JS.

---

# 12. ABSTRAÇÕES DEVEM DESAPARECER

Construções de alto nível não devem necessariamente existir em runtime.

Isso vale para:

* loops;
* ranges;
* lambdas;
* closures;
* pipelines;
* pattern matching;
* properties;
* interpolação;
* collections;
* generics;
* extension-like syntax;
* syntactic sugar;
* constructors compactos;
* outras abstrações futuras.

Pergunta obrigatória:

> Essa abstração ainda precisa existir depois da compilação?

Se não:

```text
eliminar.
```

---

# 13. TYPE SYSTEM COMO FERRAMENTA DE PERFORMANCE

O type system não existe apenas para detectar erros.

Ele também fornece informação para otimização.

O compilador deve saber:

```text
tipo
subtipo
mutabilidade
escape
dispatch
nullability futura
generic specialization futura
```

quando essas informações estiverem disponíveis.

Quanto mais informação semanticamente segura existir em compile-time, menos trabalho precisa ser realizado em runtime.

---

# 14. IR ORIENTADA À OTIMIZAÇÃO

A Kof IR deve permitir:

* constant folding;
* dead code elimination;
* unreachable code elimination;
* control-flow simplification;
* type propagation;
* branch simplification;
* allocation analysis;
* escape analysis futura;
* inlining futuro;
* specialization futura;
* scalar replacement futura;
* loop optimization;
* dispatch optimization.

Não é necessário implementar todas essas otimizações imediatamente.

Mas a arquitetura da IR **não pode impedir sua implementação futura**.

---

# 15. ESCAPE ANALYSIS

O compilador deve ser arquitetado para identificar objetos que não escapam do escopo.

Exemplo:

```kof
class Point(
    Int x,
    Int y
)

distance(Point(10, 20))
```

Se o objeto não escapar e sua materialização não for semanticamente necessária, o compilador deve futuramente poder representar seus valores de forma mais eficiente.

No JVM:

```text
Kof analysis
↓
bytecode otimizado
↓
HotSpot
↓
further optimization
```

No Native:

```text
Kof analysis
↓
direct machine representation
```

---

# 16. MEMORY SAFETY

Performance sem segurança de memória não é sucesso.

Kof deve buscar impedir:

* memory leaks;
* double free;
* use-after-free;
* acesso inválido;
* corrupção de memória;
* crescimento ilimitado de estruturas temporárias.

Especialmente no Native.

O gerenciamento de memória deve possuir ownership/lifetime suficientemente claros para permitir evolução futura sem transformar o runtime em uma coleção de `malloc()` esquecidos.

---

# 17. STACK SAFETY

O compilador nunca deve introduzir recursão artificial.

Loops devem continuar sendo loops.

```kof
while (...) {
    ...
}
```

deve ser compilado como controle iterativo.

Não transformar isso em chamadas recursivas.

Para recursão legítima:

* analisar tail position;
* permitir tail-call optimization quando possível;
* evitar frames artificiais;
* detectar padrões perigosos quando possível.

Objetivo:

> **Nenhum stack overflow causado artificialmente pelo compilador ou runtime.**

---

# 18. RESOURCE SAFETY

Os mesmos princípios devem valer para:

```text
files
file descriptors
sockets
threads
locks
native handles
buffers
processes
```

Recursos devem possuir lifecycle previsível.

A evolução da linguagem deve permitir estruturas que garantam cleanup em:

```text
normal completion
return
exception
break
continue
```

quando semanticamente aplicável.

---

# 19. BENCHMARKS SÃO PARTE DA ARQUITETURA

Performance não pode ser avaliada por sensação.

Criar:

```text
benchmarks/
├── micro/
├── algorithms/
├── collections/
├── strings/
├── math/
├── objects/
├── inheritance/
├── interfaces/
├── generics/
├── json/
├── io/
├── concurrency/
├── startup/
├── memory/
├── stress/
└── applications/
```

Cada benchmark deve ter:

```text
input
expected output
implementation
harness
metrics
baseline
```

---

# 20. BENCHMARKS DE MICROPERFORMANCE

Medir:

* integer arithmetic;
* long arithmetic;
* floating point;
* bitwise;
* comparisons;
* branches;
* loops;
* function calls;
* static calls;
* virtual calls;
* interface calls;
* field access;
* array access;
* allocation;
* generics;
* boxing/unboxing;
* strings;
* exceptions;
* lambdas;
* closures;
* collections.

---

# 21. BENCHMARKS DE ALGORITMOS

Criar casos reais para:

* sorting;
* binary search;
* hash lookup;
* graph traversal;
* tree traversal;
* matrix multiplication;
* parsing;
* serialization;
* hashing;
* compression;
* JSON;
* IO.

Os programas devem ser semanticamente equivalentes entre implementações.

---

# 22. BENCHMARKS DE MEMÓRIA

Medir:

```text
heap
peak RSS
allocation rate
object count
GC activity
temporary allocations
file descriptors
threads
```

Não basta medir tempo.

Um programa que termina 10% mais rápido consumindo 5x mais memória não é automaticamente melhor.

---

# 23. STRESS TESTS

Criar:

```text
benchmarks/stress/
```

Testar:

### CPU

Execuções prolongadas.

### Memory

Milhões de allocations.

### Collections

Grandes volumes de:

```text
insert
lookup
remove
iteration
```

### Strings

Grandes volumes de:

```text
concat
split
replace
search
parse
```

### Concurrency

Grandes quantidades de:

```kof
spawn
```

e futuramente:

```kof
await
```

### IO

Grandes volumes de:

```text
open
read
write
close
```

### Exceptions

Grandes volumes de:

```text
throw
catch
finally
```

### HTTP

Alto volume de requests.

Medir:

```text
requests/sec
p50
p95
p99
CPU
memory
```

---

# 24. LONG-RUN TESTS

Criar testes que mantenham aplicações executando por longos períodos.

O objetivo é verificar:

```text
bounded memory growth
bounded resource usage
stable throughput
stable latency
```

Detectar:

* memory leaks;
* descriptor leaks;
* thread leaks;
* crescimento inesperado do heap;
* degradação progressiva;
* crescimento de latência.

---

# 25. PERFORMANCE REGRESSION

Cada versão deve possuir baseline.

Exemplo (0.2.6-beta, 27/08/2026 — `mvn test` 810, golden 16/16):

```text
Kof 0.2.6-beta

sort:       42 ms
json:       17 ms
startup:    38 ms
memory:     12 MB
benchmarks: 37 em 17 categorias (kof bench PASS, baseline jvm/native/js)
```

Se uma alteração produzir:

```text
sort: 61 ms
```

o CI deve sinalizar:

```text
PERFORMANCE REGRESSION
```

Utilizar thresholds e análise estatística para evitar falsos positivos.

Regressões significativas precisam ser investigadas.

---

# 26. CI DE BENCHMARK

Criar:

```text
.github/workflows/benchmark.yml
```

Pipeline:

```text
compile
 ↓
run
 ↓
validate output
 ↓
collect metrics
 ↓
compare baseline
 ↓
report regression
```

Benchmarks podem ser informativos em PRs e bloqueantes quando uma regressão ultrapassar um limite significativo.

---

# 27. PARIDADE MULTI-TARGET

Os targets:

```text
Kof/JVM
Kof/Native
Kof/JS
Kof/Script
```

devem compartilhar:

```text
frontend
type system
semantic analysis
IR
```

quando possível.

O que deve mudar são as necessidades específicas de cada backend/runtime.

---

# 28. BENCHMARK MULTI-TARGET

Benchmarks relevantes devem comparar:

```text
Java
Kof/JVM
Kof/Native
Kof/JS
Kof/Script
```

O objetivo não é produzir marketing.

O objetivo é responder:

```text
onde Kof é mais rápido?
onde é mais lento?
por quê?
qual componente é responsável?
```

Toda regressão relevante deve virar investigação técnica.

---

# 29. JAVA → KOF

Um dos objetivos estratégicos do Kof é permitir migração de sistemas Java.

Ao portar:

```text
Java
 ↓
Kof
```

o resultado não deve simplesmente preservar o overhead incidental do código Java original.

O compilador deve aproveitar:

```text
type information
control-flow information
ownership/lifetime information
semantic information
```

para produzir:

```text
mesma semântica
+
menos código
+
menos abstrações runtime
+
menos allocations
+
melhor representação
```

---

# 30. PORTABILIDADE NÃO SIGNIFICA PRESERVAR INEFICIÊNCIA

Se o Java original possui:

```java
new Iterator(...)
```

mas a semântica Kof permite um loop direto:

```kof
for (x in values) {
    process(x)
}
```

o compilador não deve preservar o iterator apenas para manter uma equivalência estrutural.

A equivalência necessária é:

```text
semântica
```

não:

```text
implementação interna
```

---

# 31. DEBUG E PERFORMANCE

Devem existir perfis distintos:

```text
debug
release
```

Debug deve possuir:

* source mapping;
* line information;
* local variables;
* metadata;
* stack traces;
* observabilidade.

Release deve possuir:

* otimizações;
* menor overhead;
* metadata apenas quando necessária.

A existência de debugging não pode obrigar o programa final a carregar overhead desnecessário.

---

# 32. RUNTIME DEBUGGING

Planejar uma infraestrutura de debugging runtime própria do Kof.

O runtime deve futuramente permitir:

```text
breakpoint
step into
step over
step out
continue
pause
stack trace
locals
fields
threads/tasks
exceptions
watch expressions
```

Sem transformar o programa em um interpretador.

O programa continua sendo compilado.

O debugger conversa com o processo em execução.

Arquitetura conceitual:

```text
Kof Editor
    │
    │ Debug Protocol
    ▼
Kof Debug Adapter
    │
    ▼
Kof Runtime Debug Interface
    │
    ├── JVM
    │
    ├── Native
    │
    └── JS
```

Na JVM, integrar com mecanismos existentes quando possível.

No Native, criar uma camada própria de debug metadata/protocol.

No JS, integrar com DevTools/Node quando possível.

---

# 33. DEBUGGING NO KOF EDITOR

O Kof Editor deve futuramente conseguir:

```text
abrir projeto
 ↓
build
 ↓
run
 ↓
debug
```

com:

* breakpoints no código Kof;
* execução linha a linha;
* inspeção de variáveis;
* stack trace;
* avaliação de expressões;
* threads/tasks;
* exception breakpoints;
* console;
* restart;
* attach.

O editor não deve conhecer detalhes internos do backend.

Ele deve falar com uma interface comum:

```text
Kof Debug Protocol
```

e o backend/runtime fornece a implementação específica.

---

# 34. PROFILING

Planejar futuramente:

```text
kof bench
kof profile
kof inspect
```

Exemplo:

```text
kof bench app.kf
```

deve apresentar:

```text
startup
throughput
latency
CPU
memory
allocations
GC
```

`kof profile` deve futuramente integrar ferramentas adequadas:

### JVM

* JFR;
* async-profiler;
* JVM tooling.

### Native

* perf;
* sampling profiler;
* ferramentas nativas.

### JS

* Node profiler;
* V8/DevTools.

O objetivo é permitir descobrir **por que** o Kof está lento, e não apenas saber que está lento.

---

# 35. STANDARD LIBRARY TAMBÉM PRECISA SER RÁPIDA

Não adianta o compilador ser rápido e a stdlib ser uma âncora.

APIs como:

```text
kof.core
kof.collections
kof.io
kof.time
kof.json
kof.concurrent
```

devem ser desenhadas considerando:

* allocations;
* cache locality;
* syscall count;
* boxing;
* dispatch;
* memory usage;
* throughput;
* latency.

A API pública pode ser simples.

A implementação interna deve ser eficiente.

---

# 36. IO

IO deve possuir abstrações multiplataforma sem esconder custos importantes.

Targets:

```text
Kof/JVM
Kof/Native
Kof/JS
Kof/Script
```

devem possuir APIs consistentes.

O backend escolhe a implementação adequada.

Não criar uma API diferente apenas porque cada plataforma possui internamente uma tecnologia diferente.

---

# 37. CONCORRÊNCIA

A concorrência deve priorizar:

* baixo overhead;
* segurança;
* previsibilidade;
* ausência de vazamentos;
* lifecycle correto;
* ausência de threads abandonadas.

A linguagem não deve expor diretamente detalhes da implementação quando não forem necessários.

Exemplo:

```kof
spawn processarFila()
```

é preferível a obrigar o programador a manipular:

```text
Thread
Runnable
ExecutorService
Future
```

A abstração deve esconder a cerimônia, não esconder um custo absurdo.

---

# 38. EXCEPTIONS

Exceptions devem ser testadas sob carga.

Medir:

```text
throw/catch latency
allocation
stack usage
nested exceptions
finally
propagation
```

Exceptions não devem produzir corrupção de estado.

No Native, a evolução do mecanismo de unwinding deve preservar:

```text
correctness
cleanup
stack integrity
resource safety
```

---

# 39. O COMPILADOR NÃO PODE INTRODUZIR BUGS DE RECURSO

Nenhum backend pode introduzir:

```text
memory leak
double free
use-after-free
descriptor leak
thread leak
stack corruption
```

como consequência de uma construção normal da linguagem.

Se uma feature não consegue garantir isso ainda:

```text
documentar
testar
limitar
```

mas não esconder a limitação.

---

# 40. DEFINITION OF DONE

Uma feature não está pronta apenas porque:

```text
compila
```

Quando aplicável, ela precisa possuir:

```text
Parser
Semantic Analysis
Type System
IR
JVM Backend
Native Backend
JS Backend
Script support
Unit Tests
E2E Tests
Documentation
Benchmark
Stress Test
Memory Test
Resource Test
Debug metadata
```

E precisa preservar:

```text
correctness
+
performance
+
resource safety
+
stack safety
+
debuggability
```

---

# 41. REGRA PARA NOVAS FEATURES

Toda nova feature deve responder:

### 1. O código fica mais simples para o programador?

### 2. A semântica continua estaticamente verificável?

### 3. A abstração pode desaparecer em compile-time?

### 4. Qual é o custo de runtime?

### 5. Quantas allocations são introduzidas?

### 6. Existe boxing?

### 7. Existe reflection?

### 8. Existe dispatch indireto?

### 9. Existe overhead de memória?

### 10. Existe risco de memory leak?

### 11. Existe risco de stack overflow?

### 12. Existe benchmark?

### 13. Existe teste de stress?

### 14. Funciona nos targets aplicáveis?

### 15. O debugger consegue representar corretamente a execução?

Se a resposta for ruim:

```text
revisar a arquitetura.
```

---

# 42. REGRA DE OTIMIZAÇÃO

O compilador deve sempre procurar a seguinte transformação:

```text
intenção do programador
        ↓
semântica
        ↓
análise estática
        ↓
eliminação de abstrações
        ↓
representação mínima necessária
        ↓
código eficiente
```

Não:

```text
intenção
 ↓
boilerplate escondido
 ↓
objetos
 ↓
wrappers
 ↓
reflection
 ↓
runtime gigante
 ↓
resultado
```

---

# 43. REGRA FINAL

Kof deve remover complexidade dos dois lados.

Para o programador:

```text
alta intenção
↓
pouco código
↓
alta produtividade
```

Para a máquina:

```text
alta abstração
↓
análise estática
↓
otimização
↓
baixa representação
↓
baixo overhead
```

O objetivo é:

```text
                    KOF
                     │
          ┌──────────┴──────────┐
          │                     │
      PROGRAMADOR            MÁQUINA
          │                     │
    menos código          menos overhead
    menos ceremony       menos allocation
    type safety          menos boxing
    intenção             menos dispatch
    simplicidade         menos memória
          │                     │
          └──────────┬──────────┘
                     │
                     ▼
              ALTA PERFORMANCE
```

A filosofia do Kof não deve ser:

> "É rápido o suficiente."

Deve ser:

> **"Se conseguimos fazer melhor, fazemos melhor."**

Java continua sendo uma referência de interoperabilidade e uma plataforma extremamente poderosa.

Mas Kof não deve copiar suas limitações acidentais.

Kof deve usar o conhecimento que possui em compile-time para produzir uma representação melhor.

No JVM:

> **buscar ser mais eficiente que o Java equivalente sempre que tecnicamente possível.**

No Native:

> **buscar explorar todo o controle do compilador para atingir eficiência ainda maior.**

No JS:

> **gerar JavaScript eficiente e idiomático.**

No Script:

> **manter startup e overhead mínimos sem criar um segundo compilador.**

Em todos os targets:

> **correção primeiro, mas nunca aceitar overhead desnecessário como requisito arquitetural.**

Performance, segurança de memória, segurança de recursos, stack safety, observabilidade e debuggabilidade fazem parte da definição de qualidade da linguagem.

Não são acabamento.

São Kof.