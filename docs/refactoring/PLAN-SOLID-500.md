Versão: 1.0 · Data: 04/09/2026 · Branch: beta-0.3.0
Regra de ferro: comportamento é lei — zero regressão em cada fase (suíte completa verde como gate de merge).
1. Objetivo
Reestruturar TODAS as classes do projeto para:
1. ≤500 linhas por classe (regra absoluta, já consta no AGENTS.md).
2. Single Responsibility (SRP) — cada classe tem UM motivo para mudar.
3. DRY — extrair duplicação (dispatch de chamadas, mangle, layout, type-mapper) para um único lugar.
4. KISS — divisão por DOMÍNIO/INTENÇÃO, não por mecanismo; nada de camadas desnecessárias.
Restrição imposta pelo usuário: "cada função ter sua classe única e single responsibility" — interpretada
pragmaticamente como cada responsabilidade coesa → uma classe (funções que são um domínio inteiro,
ex. emitCall, viram uma classe XCallEmitter; funções isoladas e coesas permanecem métodos da classe
de seu domínio). Não vamos criar 1 classe por função trivial (isso violaria KISS/DRY).
2. Inventário atual (20 classes acima do limite)
Classe
NativeRuntime
CompilerDriver
NativeBackend
JsBackend
JvmRuntime
SemanticAnalyzer
Parser
JvmBackend
Main (cli)
JvmStringRuntime
JvmVkRuntime
JvmWebRuntime
JvmMediaRuntime
NativeHttpRuntime
Bench
Optimizer
KofScript
NativeWebRuntime
KofJsRunner
JdwpClient
Total alvo: ~120 classes (todas ≤500). NativeRuntime sozinho é 35× o limite — é o maior gerador de ASM em
string do projeto e o primeiro a ser atacado (domínios bem isolados, risco baixo).
3. Princípios de extração (aplicar em TODA extração)
3.1 Regra de ouro (do AGENTS.md)
Refactor preserva semântica. Mexe em estrutura, NUNCA em comportamento. Prova: mesma suíte + golden E2E
por target. Se o refactor muda output observável, é bug do refactor — corrige ou reverte.
3.2 Padrão de extração de método → classe (passo a passo)
1. Identifique um domínio coeso (ex: todas as emitString* do NativeRuntime).
2. Crie a classe RuntimeStringOps com os métodos static movidos (sem tocar no corpo).
3. No caller, troque emitStringX(sb) por RuntimeStringOps.emitStringX(sb).
4. Remova o método da classe original.
5. Compile + rode a suíte do módulo (mvn -o -pl kof-compiler -am test).
6. Rode a suíte completa (mvn -o test) — gate de merge.
7. Commit isolado por extração (uma responsabilidade por commit).
3.3 Contexto compartilhado
Classes de lowering/emissão usam muito estado. Extração NUNCA duplica estado:
- Preferir static puro (recebe StringBuilder/contexto por parâmetro) — NativeRuntime, JvmRuntime.
- Quando for instância, extrair um record XContext(...) (ex: LoweringContext, EmitContext) e passá-lo
por parâmetro. Proibido campos estáticos globais para estado de compilação.
3.4 DRY: pontos únicos a extrair primeiro (benefício imediato)
- sanitizeName/mangle de nomes → NativeNameMangler (usado em NativeBackend + NativeRuntime).
- typeToString/toType/descritores → TypeMapper (usado em JvmBackend, JsBackend, CompilerDriver, NativeBackend).
- isDoubleWidth/isDoubleWidthSlot → TypeMetrics.
- lookup de método/field em hierarquia → já centralizado no SemanticAnalyzer; manter.
3.5 KISS: proibições
- NÃO criar 1 classe por método trivial.
- NÃO criar interfaces/abstrações "por precaução" (YAGNI).
- NÃO criar camadas Service/Repository/Controller (anti-pattern do Kof — o repo é Java, mas o espírito vale).
- NÃO introduzir injeção de dependência/framework.
4. Fases de execução (ordem por risco: menor → maior)
Cada fase é um PR/commit isolado com a suíte completa verde. Fases podem ser executadas em paralelo por
agentes diferentes, desde que nunca dois agentes toquem a mesma classe gigante (regra do DOING.md).
FASE 1 — NativeRuntime (17 726 → ~37 classes) — RISCO BAIXO
Gerador de ASM em string, métodos static, domínios isolados por prefixo emit*. Extração mecânica.
Nova classe
RuntimeCore
RuntimePrint
RuntimeStringConv
RuntimeStringOps
RuntimeStringParse
RuntimeStringLayout
RuntimeList
RuntimeArray
RuntimeJson
RuntimeConcurrency
RuntimeChannel
RuntimeScheduler
RuntimeMq
RuntimeGc
RuntimeDbSqlite
RuntimeDbPrepared
RuntimeLog
RuntimeConfig
RuntimeTime
RuntimeCache
RuntimeIoFile
RuntimeIoTime
RuntimeNet
RuntimeSecurity
RuntimeUi
RuntimeVk
… (subdivisões conforme necessário)
Gate: suíte completa + golden E2E Native (todos os E2E já cobrem os domínios).
FASE 2 — CompilerDriver (8 870 → ~18 classes) — RISCO ALTO (estado compartilhado)
O coração do compilador. A extração de emitExpression (4k+) e emitStatement (2k+) é o passo mais delicado:
esses métodos usam dezenas de campos da instância. Estratégia: extrair um LoweringContext imutável
(unit, target, semanticAnalyzer, diagnostics, module state) e mover os emitters para classes estáticas.
Nova classe
CompilerPipeline
CompilerImports
CompilerDesugar
CompilerTypes
LambdaLowerer
LambdaState
SuperBridgeBuilder
BoxClassFactory
StatementLowerer
ExpressionLowerer
CallEmitter
UiEmitter
ArgumentsEmitter
QueryDslLowerer
StringMethodRegistry
AndroidHostBuilder
CompilerMain
Estratégia de migração por pedaços (evita 1 commit gigante):
1. Extrair StringMethodRegistry, CompilerTypes, BoxClassFactory (sem estado — trivial).
2. Extrair CompilerImports, CompilerDesugar (sem estado de lowering).
3. Extrair LambdaLowerer + LambdaState (estado isolado dos lambdas).
4. Extrair SuperBridgeBuilder, QueryDslLowerer, UiEmitter, ArgumentsEmitter.
5. Último: extrair StatementLowerer/ExpressionLowerer com o LoweringContext — maior commit, feito
em sub-commits (por tipo de nó), cada um com suíte verde.
Gate: suíte completa + golden E2E por target (JVM/JS/Native).
FASE 3 — NativeBackend (6 813 → ~14 classes) — RISCO MÉDIO
Emissão de ASM. Subdivide por responsabilidade da emissão.
Nova classe
NativeBackend
NativeOperationEmitter
NativeCallEmitter
NativeStringData
NativeVtableBuilder
NativeLayout
NativeJsonSchema
NativeRiscv64
NativeAarch64
NativeNameMangler
FASE 4 — JsBackend (6 064 → ~12 classes) — RISCO MÉDIO
Espelha o NativeBackend para JS.
Nova classe
JsBackend
JsClassEmitter
JsMethodEmitter
JsOperationEmitter
JsCallEmitter
JsExpressionEmitter
JsStatementEmitter
JsRuntimeEmitter
JsSourceMap
JsTypeMapper
FASE 5 — JvmRuntime (2 526 → ~5 classes) — RISCO BAIXO
Gera source Java em string. Mesmo padrão do NativeRuntime.
Nova classe
JvmRuntime
JvmRuntimeSource
JvmRuntimeDecoders
JvmRuntimeCallDescriptors
FASE 6 — SemanticAnalyzer (2 293 → ~5 classes) — RISCO ALTO (muita interdependência)
Nova classe
SemanticAnalyzer
SymbolTableBuilder
TypeChecker
ExpressionTyper
MemberResolver
FASE 7 — Parser (1 975 → ~4 classes) — RISCO MÉDIO
Nova classe
Parser
StatementParser
ExpressionParser
TypeParser
FASE 8 — Demais 500–1400 linhas (~14 classes)
Executável em paralelo (nenhum conflito de arquivo).
Classe
JvmBackend
Main (cli)
JvmStringRuntime
JvmVkRuntime
JvmWebRuntime
JvmMediaRuntime
NativeHttpRuntime
Bench
Optimizer
KofScript
NativeWebRuntime
KofJsRunner
JdwpClient
FASE 9 — VARREDURA FINAL (DRY/KISS)
1. Grep por duplicação: emitCall blocks repetidos entre backends → extrair helpers comuns
(CallAbi, RegisterAllocator) SE o ganho compensar (evitar over-engineering).
2. rg por classes com import.*\* (limpar).
3. Verificação automática de tamanho: script scripts/check_500.sh que falha se qualquer classe >500.
4. Atualizar AGENTS.md/DOING.md com a regra e o plano.
5. Riscos e mitigação
Risco
emitExpression (4k+) refactor quebra semântica
Estado compartilhado vira bagunça
Conflito entre agentes nas classes gigantes
Duplicação de lógica entre backends (DRY)
Refactor "documentado em volta" de bug
Perda de contexto (por que o código é assim)
6. DoD (Definition of Done) por fase
- Todas as novas classes ≤500 linhas.
- Nenhuma classe tocada ficou >500 linhas.
- mvn -o compile -q limpo.
- mvn -o test completo verde (913 testes hoje; pode crescer).
- Golden E2E por target (JVM/JS/Native) sem mudança de output.
- Zero campos estáticos globais novos para estado de compilação.
- Sem duplicação nova (grep por padrão extraído).
- docs/refactoring/ atualizado (se descobriu divisão melhor, registra).
7. Ordem sugerida de execução (prioridade)
1. Fase 1 (NativeRuntime) — maior classe, menor risco, maior ganho imediato.
2. Fase 2 passo 1–4 (CompilerDriver partes fáceis) — desbloqueia o caminho.
3. Fase 5 + Fase 8 (JvmRuntime e 500–1400) — paralelizáveis.
4. Fase 3 + Fase 4 (backends) — depois que TypeMapper/NameMangler existirem (DRY).
5. Fase 6 + Fase 7 (SemanticAnalyzer + Parser).
6. Fase 2 passo 5 (emitExpression/emitStatement — o último, mais arriscado).
7. Fase 9 (varredura final + script de gate).
8. Status de execução
Fase
1
2
3
4
5
6
7
8
9
</parameter>
</invoke>
▣  B