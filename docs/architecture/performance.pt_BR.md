[English](performance.md) | [Português](performance.pt_BR.md)

# KOF — Performance, Benchmarks, Segurança de Recursos, Diretrizes

last: none
doing: none-normative
next: none
location: docs/architecture
state: normative

**Versão:** 0.5.0-beta (37 benchmarks; `kof bench` + `benchmark.yml` limiar 1.20)
Princípios permanentes, não sugestões: type system, IR, backends, runtime, stdlib e tooling obedecem.

# 1. Núcleo

- Remover complexidade do código sem movê-la para o runtime: expressividade + type safety + baixo overhead/consumo + performance + interop.
- Abstrações desnecessárias em runtime somem na compilação. Regra: **gerar a representação semanticamente mais eficiente por constructo.**
- Superioridade: Kof idiomático ≥ Java idiomático; **nunca reproduzir overhead de Java** por conveniência de implementação (boxing/allocation/iterator/temporário/reflection/indireção/dispatch/wrapper) quando provavelmente eliminável.

# 2. Alvos

- **JVM:** bytecode HotSpot excelente (tipos concretos, acesso direto, sem boxing/allocs/reflection inúteis, loops eficientes, StackMapTable correta). `for (x in values)` busca o loop direto, nunca Iterator+box automático.
- **Native:** mais agressivo (startup/memória/allocs/chamadas/syscalls/cache/stack/binários enxutos). Estado 0.5.0-beta: free-list `kof_free_head`, FP em XMM, dtoa via `snprintf`, `spawn` em pthreads. Pode superar equivalentes onde o controle permitir.
- **JS:** ECMAScript natural e eficiente (sem wrappers/temporários/closures/boxing/dispatch-indireto/runtime gigante); mapeamento direto de constructo.
- **Script:** mesmo frontend/type-system/IR, sem segundo compilador; startup rápido, baixa latência, runtime mínimo. Script ≠ lento.

# 3. Regras zero

- **Overhead:** toda feature responde seu custo de runtime; overhead escondido proibido.
- **Boxing:** `Int/Long/Bool/Char` ficam primitivos (`ILOAD/ISTORE/IADD`, não alloc→unbox→op→box); type system + IR preservam a informação.
- **Reflection:** só interop/frameworks/metadata/APIs explícitas; `user.name` → `GETFIELD`, nunca reflect→lookup→invoke.
- **Dispatch:** forma mais direta conhecida (`INVOKESTATIC/SPECIAL/VIRTUAL/INTERFACE`); sem dispatch dinâmico artificial (todos os alvos).
- **Sumir:** loops/ranges/lambdas/closures/pipelines/matching/properties/interpolação/collections/generics/sugar somem salvo necessários ("ainda precisa existir pós-compile?" → eliminar).
- **Type system como otimizador:** conhecer tipo/subtipo/mutabilidade/escape/dispatch/nulabilidade/especialização em compile time.
- **IR:** permite fold/DCE/inalcançável/CFG-simplify/propagação/branch/allocation/escape/inline/specialize/scalar/loop/dispatch (futuros sem bloqueio arquitetural).
- **Escape:** identifica escopos sem escape para representação futura eficiente (JVM: análise→bytecode→HotSpot; Native: representação direta).
- **Segurança memória/stack/recursos:** sem leaks/double-free/use-after-free/corrupção/temps ilimitados (esp. Native; ownership claro, sem runtime de `malloc()` esquecidos); nunca recursão artificial (loops seguem loops; análise de cauda, sem frames artificiais; **nenhum stack overflow causado pelo compilador**); files/fds/sockets/threads/locks/handles/buffers/processos com lifecycles previsíveis (normal/return/exception/break/continue); backends nunca introduzem bugs de recurso (documentar+testar+limitar, nunca esconder).

# 4. Benchmarks

- Parte da arquitetura (nunca feeling): árvore `benchmarks/{micro,algorithms,collections,pipelines,strings,math,objects,inheritance,interfaces,generics,json,io,concurrency,startup,memory,stress,applications}`; cada um com input/esperado/implementação/harness/métricas/baseline.
- Micro: aritmética/bitwise/compare/branch/loop/calls/virtual/interface/field/array/alloc/generics/boxing/strings/exceptions/lambda/closure/collections. Algoritmos: sort/search/hash/graph/tree/matrix/parse/serialize/hash/compress/JSON/IO (programas semanticamente equivalentes).
- Memória: heap/RSS/taxa-alloc/objetos/GC/temps/fds/threads (só tempo nunca decide; 10% mais rápido × 5x memória não é melhor).
- Stress (`benchmarks/stress/`): CPU prolongada, milhões de allocs, volumes de coleção/string (insert/lookup/remove/iterate, concat/split/replace/search/parse), tempestades `spawn` (+`await` futuro), volumes IO, tempestades throw/catch/finally, HTTP req/s+p50/p95/p99+CPU/RAM.
- Long-run: crescimento limitado de memória/recursos, throughput/latência estáveis (detecção de leak/degradação).
- Regressão: baseline por versão (0.2.6-beta golden: sort 42ms/json 17ms/startup 38ms/12MB/37 benches); CI sinaliza `PERFORMANCE REGRESSION` após limiares (estatística, sem falsos positivos; regressões significativas investigadas).
- Pipeline CI: compile→run→validate→collect→compare→report (`benchmark.yml`; informativo em PRs, bloqueante após limite significativo).
- Multi-alvo: JVM/Native/JS/Script compartilham frontend/type-system/IR; benchmarks comparam Java vs Kof-por-alvo perguntando onde/porquê (nunca marketing); toda regressão relevante vira investigação.
- Migração Java→Kof não preserva overhead incidental: usa info de tipo/fluxo/ownership/semântica para mesma semântica + menos código/abstrações/allocs/melhor repr. Equivalência é **semântica**, nunca implementação interna (sem Iterator preservado por equivalência estrutural).

# 5. Debug / profile

- Perfis: debug (mapeamento/linhas/locais/metadata/traces/observabilidade) vs release (otimizado, metadata só se preciso); debugging nunca força overhead no programa final.
- Debugging de runtime (planejado): Editor → Protocolo de Debug → Adapter → interface por alvo (JVM mecanismos existentes; Native metadata/protocolo próprios; JS DevTools/Node); programa compilado, breakpoints/step/locais/threads/watch/attach.
- `kof bench`/`profile`/`inspect`: startup/throughput/latência/CPU/memória/allocs/GC. JVM: JFR `kof profile --methods` com linhas Kof ✅. Native: gap honesto (precisa `perf_event`, recusa nomeando o sysctl quando fechado) ✅. JS: Node `--cpu-prof` + `.mjs.map` para linhas Kof ✅.

# 6. Stdlib / IO / concorrência

- Stdlib (`core/collections/io/time/json/concurrent`) desenhada para allocs/cache/syscalls/boxing/dispatch/memória/throughput/latência; API simples, eficiente por dentro.
- IO: APIs consistentes cross-platform (backend escolhe impl, nunca segunda API por tecnologia).
- Concorrência: baixo overhead, segura, previsível, sem leaks/threads abandonadas; `spawn processarFila()` sobre cerimônia Thread/Runnable/Executor — esconde cerimônia, nunca custo absurdo.
- Exceções sob carga (latência/alloc/stack/aninhadas/finally/propagação), sem corrupção de estado; unwinding nativo preserva correção/limpeza/stack/recursos.

# 7. Portão de features (15 perguntas)

Feature nova responde: código mais simples? verificável estaticamente? some em compile? custo runtime? allocs? boxing? reflection? dispatch indireto? memória? risco leak/stack? benchmark? stress? alvos aplicáveis? debugger enxerga? Resposta ruim → revisar arquitetura.
DoD: Parser→suporte Script + testes unit/E2E/docs/benchmark/stress/memória/recursos + metadata debug, preservando correção+performance+segurança de recursos/stack+debugabilidade.

# 8. Regra de otimização

`intenção → semântica → análise estática → eliminar abstrações → representação mínima → código eficiente` (nunca intenção→boilerplate→objetos→wrappers→reflection→runtime-gigante). Final: complexidade removida dos DOIS lados (programador: menos código/cerimônia + safety/intenção; máquina: menos overhead/alloc/boxing/dispatch/memória). Filosofia nunca é "rápido o bastante", e sim **"se dá para melhorar, melhora"** — JVM busca melhor-que-Java, Native explora controle total, JS idiomático, Script mínimo, todos correção-primeiro com zero overhead desnecessário aceito. Performance, segurança de memória/recursos/stack, observabilidade e debugabilidade SÃO qualidade, não acabamento.
