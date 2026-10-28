[English](wasm-wasi-plan.md) | [Português](wasm-wasi-plan.pt_BR.md)

**Dono:** ⏹ **ORFAO 15/10 (auditoria) — o claim `192.168.15.101:9092` esta abandonado: o ultimo trabalho no plano e 09/10 e todo commit dessa lane desde 10/10 e trabalho de release-protocol/native-bug, NAO deste plano; a unidade 15.4 ordenada pela mantenedora (o flip do frontend-padrao) NAO esta no codigo (medido, `AGENTS.md` `last:`). Por `D-PLAN-ONE-OWNER` a frente e LIBERADA para SEM DONO — qualquer lane a reivindica no DOING primeiro. Historico: promovido por `D-WEB-WASI-DEFAULT-0710` (GATE 0.6.0, #776); 15.1+15.2+15.3-fatia1+15.3b+15.3c-fatiaA+15.3c-fatiaB+15.3d-inc1+15.3d-inc2-A/B/C1+C2/E pousadas por essa lane.**

# WebAssembly (WASM) + WASI — especificação de implementação (EM DESENVOLVIMENTO: emissão pousada, flip 15.4 pendente)

last: JOIN-4 POUSADO 28/10 pela lane flatt CLI `192.168.15.117:0` — o ternary de PROFUNDIDADE 4 do concat nullable cruza a máquina de estados do WASI, o render do `Map<String, Char>` COMPLETO (a sonda MC1 10 faces = o render COMPLETO; `mapStringCharFullRenderMatchesTheJvmOracle` na WasmWasiMapValueE2ETest 10/10; `Wasm*` 97/97). AS LEIS: o strConcat do join = a PRIMEIRA op da região do join ([strConcat, valueOf, println] medido) — o guard do swap = a profundidade da sim 0 (o load do join-entry = o +1 FORA da sim); a dança-4 = [rhs, cópia, V, esquerda(string)] (os quatro stores + os dois reloads; os guards = o V-caixa, a esquerda-"string", os cmp não-wide); o CROSS = o fill nos DOIS estados (o KofPop do ELSE = o descarte do slot — a lei da rodada 48); o SWAP = o `a` do strConcat = o MAIS FUNDO = a esquerda (a lei da ORDEM — sem ele todo concat imprimia REVERSO); o leftPending = consumido no PRIMEIRO strConcat com a profundidade da sim 0 (os mains multi-ternary = os flags por statement). O MAP = A FATIA PRIMITIVA COMPLETA: Map<String, Int|String|Double|Bool|Char> + Map<Int, String> byte-idênticos em todo lugar. O estado Schubfach/HETBOX/LIST-1..7/MAP-1..3/SET-1..3/ARRAY-1..3/MATH-D/LIBM-1..3/TAN/ATAN/ASIN/ACOS/ATAN2 abaixo não muda.
doing: 15.3 continua — **fatia F face 1 POUSADA 22/10** (`String ==`/`!=` via o `kof.strEq` existente; a chamada `kof_string_equals` do operador agora tem destino no WASI); fatia F face 2 (campos `Double` no `toString` + paridade `Double.toString`) é a PRÓXIMA face e é GRANDE — exige um dtoa Schubfach emitido como bytecode wasm; fatia G: coleções (`kof.list/map/set` no bump heap); flip 15.4 por último.
next: runtime GC-handle (records/colecoes) → 15.4 flip do frontend-padrão POR ÚLTIMO (só com paridade total; o corte segue gated por #776 via `D-LAB-STABILITY`).
location: docs/wasm-wasi-plan.pt_BR.md
state: EM DESENVOLVIMENTO

> **✅ POUSADO 28/10 — o ternary de PROFUNDIDADE 4 do concat nullable cruza a máquina de estados do WASI; o render do `Map<String, Char>` COMPLETO (o print E o concat byte-idênticos; lane `192.168.15.117:0`, rodada agendada flatt CLI).** A MEDIÇÃO QUE QUEBROU O TRAVAMENTO (o rastro de regiões com os nomes das ops): o strConcat do join = a **PRIMEIRA op da região do join** (`[strConcat, valueOf, println]` medido) — o guard do swap era a profundidade da sim 1 e NUNCA disparava (a profundidade da sim no strConcat = **0** — o load do join-entry = o +1 FORA da sim) — a esquerda nunca carregava, o strConcat despachava o tmp do join-entry e batia na PILHA VAZIA. O FIX = UM GUARD: a profundidade da sim 0. O MECANISMO COMPLETO: a dança-4 ([rhs, cópia, V, esquerda(string)] — os quatro stores incl a ESQUERDA = o novo leftIdx cruzando TODOS os braços até o strConcat do join + os dois reloads; os guards = o V-caixa, a esquerda-"string", os cmp não-wide), o self-serve do valueOf(char) (o carrega da caixa do braço + o UNBOX antes do strChar), o CROSS (o VALOR do braço para o join — o fill nos DOIS estados pela lei da rodada 48: o KofPop do ELSE = o descarte do slot, o load do join-entry o ALIMENTA) e o SWAP do strConcat (a pilha do join = [direita]; o swap = [direita → o scratch, get esquerda, get scratch] = [esquerda, direita] — a lei da ORDEM: o `a` do strConcat = o MAIS FUNDO = a esquerda). O leftPending = consumido no PRIMEIRO strConcat com a profundidade da sim 0 (os mains multi-ternary = os flags por statement, verificado nos QUATRO ternaries da MC3). Provado byte-idêntico: a sonda MC1 (10 faces — o render COMPLETO), as MC2/MC3/MC5, a regressão de 15 sondas (145+ faces); a face nova `mapStringCharFullRenderMatchesTheJvmOracle` (WasmWasiMapValueE2ETest 10/10); a bateria `Wasm*` **97/97**; Q2 rc=0; os gates do escopo rc=0. O MAP = A FATIA PRIMITIVA COMPLETA: Map<String, Int|String|Double|Bool|Char> + Map<Int, String> byte-idênticos em todo lugar.

> **✅ POUSADO 28/10 — o ternary de PRINT do desugar nullable agora cruza a máquina de estados do WASI; o render do `Map<String, Char>` byte-idêntico (lane `192.168.15.117:0`, rodada agendada flatt CLI).** A forma medida: o desugar EMITE `KofDup` da caixa do operando (o null-check consome a CÓPIA, o braço THEN o ORIGINAL) e o cmp nasce DENTRO do salto condicional — o dup do WASI era o no-op fora dos rastros de coleção → o cmp consumia a única cópia e o strChar despachava sobre a PILHA VAZIA. A maquinaria: (1) o KofDup = REAL para os rastros de caixa (o tee+get no dupIdx; a lei do record-ctor intocada); (2) a DANÇA no salto (a nova `WasmJoinCode.storeUnderForCondJump` — a sim detecta [under, lhs, rhs] = a profundidade 3, os dois operandos do cmp + o under vão para os scratches, os operandos voltam e o emitCondJump compara; a profundidade > 3 = o concat = a recusa honesta nomeada); (3) o CROSS (o valor do braço cruza para o join pelo scratch join; o valueOf(char) self-serves o carrega da caixa + o UNBOX antes do strChar). As leis da sim pagas: o map-put = VOID no pushedShape, o println pops = 1 (o receptor elidido), as coleções = INSTANCE. O split (check_500): WasmLowering 711 → 586, a maquinaria da fronteira de estado = a nova WasmJoinCode (173). O VEREDICTO DO CONCAT: [esq, V, cópia, rhs] = a PROFUNDIDADE 4 = a recusa honesta (o slice registrado seguinte). Provado byte-idêntico nas sondas MC2/MC5 (7 faces) + a regressão de 13 sondas (128 faces); a face nova `mapStringCharRenderMatchesTheJvmOracle` (WasmWasiMapValueE2ETest 9/9); a bateria `Wasm*` **96/96**; Q2 rc=0; os gates do escopo rc=0. PRÓXIMO: o ternary de profundidade 4 (o concat = a esquerda cruzando para o strConcat do join) completa o render do Char?.

> **✅ POUSADO 28/10 — a ARMAZENAGEM do `Map<String, Char>` avalia no WASI byte-idêntica; o RENDER honestamente recusado na fronteira do ternary medida (lane `192.168.15.117:0`, rodada agendada flatt CLI).** O valor Char anda RAW no slot (o put = o mapPut CRU — o MESMO slot do Int) e o get/remove reusam os runtimes da fatia Int tal-como-estão (o boxInt é idêntico — o 0 = null); as pernas `nullablechar` (o trio + o rastro de local + os rastros + a sim) + os gates alargam para {int, string, double, bool, char}. O VEREDICTO DO RENDER: o print/concat do Char? = o TERNARY do desugar cujo operando nasce ANTES do null-check — a máquina de estados não o carrega (o print = [valueOf, jump]; o concat = [left, V, cond] = a PROFUNDIDADE 3, DOIS valores vivos sob a condição) — a maquinaria do if-expression = o slice registrado seguinte. A DEFESA Q7: a tentativa do under único chegou a um módulo inválido no concat de profundidade 3 (REVERTIDA); as pernas sozinhas deixavam o ternary compilar para strChar sobre a pilha vazia (o gate restaurado via `WasmOperandShape.depthAt` — o diagnóstico honesto nomeado, SEM artefato); as rotas mortas do render REVERTIDAS (a lei da rodada 38). Provado byte-idêntico em 5 faces de armazenagem (MC4); a sonda MC3 = a recusa honesta; a regressão das 11 sondas toda byte-idêntica (123 faces); a face nova `mapStringCharStorageMatchesTheJvmOracle` (WasmWasiMapValueE2ETest 8/8); a bateria `Wasm*` **95/95**; Q2 rc=0; os gates do escopo rc=0. PRÓXIMO: a maquinaria do if-expression (os valores vivos do ternary pelos estados) destrava o render do Char? e os if-expressions heterogêneos.

> **✅ POUSADO 28/10 — o `Map<String, Bool>` agora avalia no WASI, byte-idêntico ao JVM (lane `192.168.15.117:0`, rodada agendada flatt CLI).** O valor Bool anda na CAIXA §284 (`kof.boxBool` do qword; o 0 = null — o 0 = false nunca pode ser a sentinela): o put leva o extend NO CALL SITE (`0xad` + o mapPut cru, o padrão do Double), o get/remove devolvem a caixa (os clones nomeados `kof.mapGetBool`/`kof.mapRemoveBool` com o `0xa7 wrap_i64` antes do boxBool — o qword wrapa para o i32 que o boxBool pede), as pernas `nullablebool` completas (o trio typeName/mapType/printableScalar + o localNullableTrace + o case do print + o ramo do valueOf + o rastro do remove + o isBox da sim). O BISECT PAGOU: o Boolean.valueOf do desugar do println chega no ramo printlnFollows (que VENCE o ramo Boolean do valueOf) e o else dele apagava o rastro pelo param APAGADO — o writeBool despachava sobre o handle da caixa (o memory fault `0x6c6c756e` = os bytes "null") — o re-arme nas DUAS cadeias; os ramos do valueOf caíram primeiro na cadeia ERRADA (o valueOf do concat = UM POR OPERANDO); e a edição do gate do put separou o braço dblVal SEM o void-drop — a cadeia do mapOf dessincronizou e MD1/MD2/MX1 REGREDIRAM (as re-sondas de regressão pegaram; o drop restaurado). Provado byte-idêntico em 34 faces de sonda (MB1/MB2/MB3/MB4) + a regressão de 89 faces (10 sondas); a face nova `mapStringBoolMatchesTheJvmOracle` (WasmWasiMapValueE2ETest 7/7); a bateria `Wasm*` **94/94**; Q2 rc=0; os gates do escopo rc=0. Larguras de map restantes: `Map<String, Char>` (a última primitiva — o render do Char? precisa da rota strChar, não o intToStr do boxToStr — a ambiguidade da tag-0) atrás da própria medição.

> **✅ POUSADO 28/10 — a ARITMÉTICA MISTAS das caixas do map Double agora avalia no WASI, byte-idêntica ao JVM (lane `192.168.15.117:0`, rodada agendada flatt CLI).** A sonda MX1 mediu o módulo inválido primeiro; a causa raiz = a dança conhecia só o rastro do op2. O fix = a NOVA `WasmOperandShape` (a simulação FORWARD da pilha de traços sobre os ops da região devolvendo [op1, op2]) + as TRÊS danças: caixa+caixa (a lei MAP-D), escalar+caixa (o único load do op2), caixa+escalar (o stash pelo scratch vf64 sempre alocado). A lei de segurança: um erro da simulação = módulo inválido no validate, nunca byte errado silencioso; as formas não-cobertas recusam honesta. Provado byte-idêntico em 5 faces de sonda (as três variantes); as re-sondas MD1/MD2/MK1..MK4 todas byte-idênticas (57 faces); a face nova `mapDoubleMixedArithmeticMatchesTheJvmOracle` (WasmWasiMapValueE2ETest 6/6); a bateria `Wasm*` **93/93**; Q2 rc=0; os gates do escopo rc=0. A frente da aritmética dos valores do map está FECHADA: caixa+caixa, escalar+caixa, caixa+escalar dançam todas.

> **✅ POUSADO 28/10 — a extração `keys()`/`values()` sobre a chave Int agora avalia no WASI, byte-idêntica ao JVM (lane `192.168.15.117:0`, rodada agendada flatt CLI).** A recusa honesta da rodada 41 mediu primeiro; a leitura do `WasmMapExtract` mostrou a extração AGNÓSTICA por construção (os slots 16B copiam RAW — `LOAD64`→`STORE64`): as chaves Int = os i64 cru (a fatia da lista Int), as chaves String = os handles estendidos, os values = os handles do valor. O fix = UM levantamento de gate (zero runtime novo): os MESMOS `kof.mapKeys`/`kof.mapValues` servem as duas larguras de chave; as faces LIVRES DE ORDEM (a lei MAP-1). Provado byte-idêntico em 7 faces de sonda livres de ordem + a regressão MK1/MK2/MK3 (35 faces); a face nova `mapIntStringKeysValuesMatchTheJvmOracle` (WasmWasiMapValueE2ETest 5/5); a bateria `Wasm*` **92/92**; Q2 rc=0; os gates do escopo rc=0. Frentes de map restantes: a aritmética MISTA (o rastro do op1 da dança) atrás da própria medição.

> **✅ POUSADO 28/10 — o `Map<Int, String>` agora avalia no WASI, byte-idêntico ao JVM (lane `192.168.15.117:0`, rodada agendada flatt CLI).** A chave anda RAW no slot i64 (o Int do Kof = o i64 — sem extensão strLit) e o valor String estende no MESMO slot do putStr: `WasmMapIntRuntime` (os quatro clones nomeados `kof.mapPutKi`/`kof.mapGetKi`/`kof.mapRemoveKi`/`kof.mapContainsKi` — a busca `[cellkey LOAD64; get key; i64.eq]`, o wrap+strEq fora, o param da chave TYPE_I64) registrados pelo all(). As rotas: os gates alargam para a chave ∈ {string, int} (o KI + valor int/double RECUSA, uma linha nomeando as quatro fatias); o get/remove mantêm o rastro do valor Str (`mapval` / `nullablestring` — o 0 = null passthrough via boxToStr); o `keys()/values()` sobre chave Int RECUSA HONESTA (a chave raw-i64 não é o handle String — a extração é fatia própria). Provado byte-idêntico em 35 faces de sonda (as sondas MK1/MK2/MK3: a cadeia de 3 pares, os hits/misses, os concat, o sobrescreve, os removes declarado+void, o contains depois); a face nova `mapIntStringMatchesTheJvmOracle` (WasmWasiMapValueE2ETest 4/4); a bateria `Wasm*` **91/91**; Q2 rc=0; os gates do escopo rc=0. Formas de map restantes: a aritmética MISTA (o rastro do op1 da dança) e a extração da chave Int, cada um atrás da própria medição.

> **✅ POUSADO 28/10 — o `Map<String, Double>` agora avalia no WASI, byte-idêntico ao JVM (lane `192.168.15.117:0`, rodada agendada flatt CLI).** O valor Double anda na CAIXA §284 (`kof.boxDouble` dos bits; o handle i32, o 0 = null — os 0-bits = 0.0 nunca podem ser a sentinela de null): o put leva o reinterpret NO CALL SITE (a f64 → o i64 dos bits do `kof.mapPut`), o get/remove devolvem a CAIXA (`kof.mapGetDbl`/`kof.mapRemoveDbl` — os clones nomeados, a lei do dedupe byName). As leis do rastro: o nome `nullabledouble` (o trio typeName/scan/mapType) + o mapa de rastro de LOCAL no Ctx (o `KofLoadLocal.type()` ERASA o nullable — o slot o viu; o load consulta `m.localVariables()`) + o RE-ARME no ramo valueOf do `printlnFollows` (o desugar `String.valueOf(Double.valueOf(x))` reescrevia o rastro pelo param PRIMITIVO apagado — o MESMO padrão do re-arme do mapval) + as rotas do print/valueOf via `kof.boxToStr` (o null = `null`). A dança da ARITMÉTICA: os ops f64 sobre operandos-caixa desboxam via `f64.load 16` (o op2 no scratch vf64 sempre alocado, depois o op1) — o null trapa = a paridade do NPE do JVM; a forma MISTA caixa+escalar recusa no validate. O split check_500: `WasmMapDblRuntime` (os dois clones Dbl) com o all() delegando (WasmMapRuntime 772 → 567). Provado byte-idêntico em 15 faces de sonda (os hits/misses, os concat `v=null`/`w=0.5`/`d=null`, o put/sobrescreve, os removes declarado+void, a aritmética `b+a` = 11.25); a face nova `mapStringDoubleMatchesTheJvmOracle` (WasmWasiMapValueE2ETest 3/3); a bateria `Wasm*` **90/90** (duas vezes — antes e depois do split); Q2 rc=0; os gates do escopo rc=0. Formas de map restantes: `Map<Int, String>` atrás da própria medição; a aritmética MISTA (o rastro do op1) = a próxima fatia.

> **✅ POUSADO 28/10 — o `Map<String, String>` agora avalia no WASI, byte-idêntico ao JVM (lane `192.168.15.117:0`, rodada agendada flatt CLI).** A célula do map mantém o MESMO layout ([chave][valor i64]) — o valor String viaja ESTENDIDO no slot (`kof.mapPutStr`), o get/remove devolvem o valor CRU (`kof.mapGetStr`/`kof.mapRemoveStr`) com o passthrough do `kof.boxToStr` renderizando tanto o String cru quanto o `null` no 0. As leis do rastro: as rotas do nome `nullableString` (o typeName/o gate do scan/o local mapType — as peças da rodada 38, vivas de novo), o if do print com o check do null = VOID com o write DENTRO dos bracos (um if result-typed com o write depois = 'values remaining at end of block' no ELSE), o ramo do null no valueOf do concat = RESULT-typed (0x7f — o handle precisa cruzar o join), e o remove VOID consumido pelo KofPop do STATEMENT (o ramo do map do hasReturnValue — um drop próprio fazia o duplo pop). O bissepto também pegou os três clones mantendo os NOMES DE EXPORT velhos — o dedupe byName os descartava em silêncio ('chamada a funcao fora do subset' no ENCODER). O split do check_500: `WasmMapStrRuntime` (os três Str + os helpers) com o all() delegando. Provado byte-idêntico em 24 faces de sonda; a face nova `mapStringStringMatchesTheJvmOracle` (WasmWasiMapValueE2ETest 2/2); a bateria `Wasm*` **89/89**; Q2 rc=0; os gates do escopo rc=0. Formas do map restantes: Map<String,Double>/Map<Int,String> atrás das próprias medições.

> **✅ POUSADO 28/10 — o CONCAT dos valores nullable do map.get agora avalia no WASI, byte-idêntico ao JVM (`"v=" + m.get("z")` = `v=null`) (lane `192.168.15.117:0`, rodada agendada flatt CLI).** Medido primeiro: as superfícies nullable sondadas — o `println(m.get(...))` simples já era byte-idêntico (a rota mapval→boxToStr do print), o literal null direto = o contrato da linguagem SEM048, e a lacuna medida = a cadeia valueOf do CONCAT (o valor do map.get = o HANDLE da caixa, o valueOf recusava com o nome NullableType completo). A correção: o ramo String.valueOf trata o `mapval` — o handle da caixa vai ao kof.boxToStr (o null = a literal `null`, o mesmo render do print) e o mapval re-arma (a cadeia tem dois passes). A disciplina Q7: a rodada PRIMEIRO também roteou o print do String? (o nome nullableString + o gate do scan + o despacho com o check do null) — mas o gate da construção das larguras de valor do map (`Map<int, string> fora da fatia Map<String, Int>`) fica na frente, a rota era inalcançável hoje = código morto — REVERTIDO, com o mapeamento registrado para a fatia das formas do map. Provado byte-idêntico nas sondas M3/M7 (os prints + o concat com os valores null e acertados); a face nova `mapNullableValueConcatMatchesTheJvmOracle` no split SNULL `WasmWasiMapValueE2ETest` (o ratchet do test-hygiene forçou o split do WasmWasiListE2ETest); a bateria `Wasm*` **88/88**; Q2 rc=0; os gates do escopo rc=0.

> **✅ POUSADO 28/10 — `new Long[n]` e `new Double[n]` agora alocam, gravam, leem e medem length no bump heap do WASI, byte-idêntico ao JVM (lane `192.168.15.117:0`, rodada agendada flatt CLI).** A fatia wide: os slots de 8 bytes ([count i32][slots 8xN]) com o stride 8 no store/load, o MESMO bounds-trap D-WASM-03, e os slots zerados do bump = 0L/0.0 (o zero-init da JVM). O store guarda o long no scratch I64 e o double no scratch F64 — que agora aloca SEMPRE (o alocar-condicional o deixava em −1 para programas sem records, uma classe de módulo-inválido). O load: o long sai i64 cru, o double f64. Provado byte-idêntico em 14 faces de sonda (os ±stores, o zero-init, os slots Double, os lengths, a aritmética dos elems carregados); a face nova `wideArraysMatchTheJvmOracle` na `WasmWasiArrayE2ETest` 6/6; a bateria `Wasm*` **87/87**; Q2 rc=0; os gates do escopo rc=0. A COBERTURA DO ARRAY-HEAP: Int/String/Bool/Char/Long/Double — as larguras primitivas COMPLETAS. A caça Q4 também registrou um defeito PRÉ-EXISTENTE do backend JVM (o store com índice Long aritmético derruba o ASM COMPUTE_FRAMES — a classe do long-arg-first) para a lane JVM.

> **✅ CORRIGIDO 28/10 — o `println(!false)` não emite mais um módulo WASI INVÁLIDO: o fold constante do NOT perdeu o tipo bool (lane `192.168.15.117:0`, rodada agendada flatt CLI).** Medido primeiro: as faces do List add/set já são todas byte-idênticas e a igualdade de record aninhada está fine — o bissepto isolou o módulo inválido (`wasi ok=true`, o módulo falha a validação, "expected i32, found i64" no func do print) em `println(!f)` num var Bool. Causa raiz: o fold NOT do OptimizerConstantFold vê o Integer 0/1 do desugar do Boolean e emite `ofInt` — um literal i64 onde o caminho do print bool do WASI despacha i32; o programa compila "clean" e só o HOST recusa = a classe módulo-inválido que o Q7 proíbe. A correção: o fold NOT consulta o operandType do próprio KofUnary — o operando bool dobra para `ofBool(i == 0)`, o Int mantém o ofInt (o desugar do int sem mudança). Provado byte-idêntico ao oráculo JVM no combo medido do repro (a eq de record plana + ! bool + - int juntos) e nos vizinhos (!true, -e); a face nova `recordEqualityWithNotAndNegMatchesTheJvmOracle` na `WasmWasiRecordE2ETest` 8/8; a bateria `Wasm*` **86/86**; Q2 rc=0; os gates do escopo rc=0. A recusa da chamada de função do usuário (a fronteira do subset escalar 15.2) está registrada como a fronteira honesta, não um bug.

> **✅ POUSADO 28/10 — o `String.replace(Char, Char)` agora avalia no WASI, byte-idêntico ao JVM (lane `192.168.15.117:0`, rodada agendada flatt CLI).** O novo runtime `kof.strReplaceChar(h, from, to)` codifica os DOIS chars em UTF-8 via o novo `kof.utf8Enc` (1/2/3/4 bytes pela fórmula padrão; o runtime `kof.strChar` é o subset ASCII DOCUMENTADO — um byte cru — e não serve o contrato completo) e delega ao `kof.strReplace` (a mesma maquinaria byte da forma (String, String)). O UTF-8 auto-sincronizado = a agulha de um char casa SÓ em fronteira de char — o MESMO conjunto de casamentos do scan de code units da JVM; um replace com mudança de tamanho (a→é = `bénéné`) reconstrói pelo alloc/append existentes. As leis byte do bissepto: o 0x49 = i32.lt_u (o i64.lt_s = 0x53), o `kof.strAppendByte` RETORNA o handle (um `drop` depois de cada append) e os args do call ficam com o PRIMEIRO param MAIS FUNDO. O medido-e-fechado: a nota das collections estava stale (os elems/iteração do Set/Map todos byte-idênticos já); `indexOf(Char)`/`lastIndexOf(Char)` = SEM051 nos DOIS alvos (o contrato da linguagem — não é lacuna do WASI). A face de recusa stale (`unimplementedOverloadsRefuseInsteadOfEmittingAnInvalidModule`) saiu da forma do replace na MESMA commit (a recusa do startsWith(str, fromIndex) fica). Provado byte-idêntico em 16 faces de sonda; a face nova `stringReplaceCharMatchesTheJvmOracle` (WasmWasiStringSearchE2ETest 10/10); a bateria `Wasm*` **85/85**; Q2 rc=0; os gates do escopo rc=0.

> **✅ POUSADO 28/10 — records com campos Double agora toString byte-idêntico no WASI, fechando a fatia F face 2 — e o veredicto do pow está MEDIDO: morto como paridade byte (lane `192.168.15.117:0`, rodada agendada flatt CLI).** Medido primeiro: os caminhos do concat e do String.valueOf(double) JÁ eram byte-idênticos (a sonda F2b — o scan usesDoubleValueOf e o kof.doubleToStr pousaram com o Schubfach); o que faltava = o toString de record com campos Double (o WASM002 honesto no WasmRecordCode). A correção roteia o campo f64 pelo MESMO `kof.doubleToStr` dos caminhos print/concat/caixa (o Schubfach completo, delegando ao kof.schubFull) e o scan ganha o predicado `recordDoubles` (qualquer layout de record com campo f64) alimentando o MESMO gancho da família Schubfach do println/concat. Provado byte-idêntico ao oráculo JVM: `Point[x=0.5, y=-2.25]`, os campos ±0.0 (`Point[x=0.0, y=-0.0]`), o concat e o print explícito do campo; a face nova `recordToStringWithDoubleFieldsMatchesTheJvmOracle` na `WasmWasiRecordE2ETest` 7/7; a bateria `Wasm*` **84/84**; Q2 rc=0; os gates do escopo rc=0. O VEREDICTO DO POW (a segunda medição da rodada): `Math.pow == StrictMath.pow` deriva **3/30 por 1 ulp** (2^-0.5, 123.456^2.5, 1.5^300 — o Math.pow da JVM é o caminho intrínseco, não o fdlibm puro) — o pow segue RECUSADO no WASI como o cos, nunca um port com drift.

> **✅ POUSADO 28/10 — o `math.atan2` agora avalia no WASI, byte-idêntico ao JVM — a família trigonométrica inversa está COMPLETA (lane `192.168.15.117:0`, rodada agendada flatt CLI).** O `__ieee754_atan2` do fdlibm (o `FdLibm.Atan2` do JDK — tiny/pi_o_4/pi_o_2/pi_lo verbatim) como `WasmAtan2Kernel`, REUSANDO o func `kof_math_atan` registrado via CALL para o atan(y) (o x == 1.0 exato) e o atan(|y/x|) (o caminho principal) — o primeiro reuso intra-módulo dos kernels libm. A ordem dos casos do fdlibm completa: o NaN (`x + y`), o x == 1.0, o m do quadrante, o y = ±0 (±0 / ±π+tiny), o x = ±0 (±π/2 ∓ tiny), o x INF (a tabela ±INF×±INF: ±π/4 e ±3π/4; a tabela do x finito ±0/±π), o y INF (±π/2 ∓ tiny), o guard da diferença dos expoentes k = (iy−ix)>>20 (o k > 60 → π/2+½·pi_lo e o hx<0 && k<−60 → 0.0) e o switch final do m (z / −z / π−(z−pi_lo) / (z−pi_lo)−π). As DUAS leis novas que o bissepto pagou: a ordem dos params = a assinatura do fdlibm = a ordem do call do IR (o func nascido (x, y) espelhou TODA a saída) e a decl dos locals = SÓ os NÃO-param (os params ocupam os índices baixos — a lei da rodada 31 refinada); o m lê os BITS de sinal (shr_s 63), nunca o compare f64 (o −0.0 tem bit, não compare). Provado byte-idêntico em 31 faces de sonda (os quadrantes ±0, os quatro quadrantes de sinal, as tabelas INF, os k-extremos 1e300/1e-8, as duas faces NaN); a face nova `mathAtan2FullRangeMatchesTheJvmOracle` (30 valores) na `WasmWasiTrigE2ETest` 8/8; a bateria `Wasm*` **83/83**; Q2 rc=0; os gates do escopo rc=0. A TRIG DO WASI: sin / tan / atan / asin / acos / atan2 TODOS byte-idênticos; o cos segue recusado (o veredicto medido 1-ulp).

> **✅ POUSADO 28/10 — o `math.acos` agora avalia no WASI, byte-idêntico ao JVM em todo o domínio [-1, 1] mais o NaN — a primeira fatia libm com ZERO voltas de bissepto (lane `192.168.15.117:0`, rodada agendada flatt CLI).** O `__ieee754_acos` do fdlibm (o `FdLibm.Acos` do JDK — os MESMOS pS0..pS5/qS1..qS4 do asin + as correções df/c próprias do fdlibm; o wrapper π/2 − asin continua morto, o veredicto medido 8/18) como `WasmAcosKernel`: TRÊS ramos carregando o sinal neles — o caminho pequeno (|x| < 0.5 com o x ASSINADO: `pio2_hi − (x − (pio2_lo − x·r))`; |x| <= 2^-57 → a CONSTANTE `pio2_hi + pio2_lo`), o x < −0.5 (`z = (1+x)/2`, `w = r·s − pio2_lo`, `π − 2(s+w)`) e o x > 0.5 (`z = (1−x)/2`, o zero `__LO(df,0)`, `c = (z − df²)/(s + df)`, `w = r·s + c`, `2(df + w)`); o ±1.0 exato → 0.0 / `π + 2·pio2_lo`; |x| > 1 → o NaN `(x−x)/(x−x)`, bit-idêntico. As leis da rodada 31 pré-aplicadas pagaram inteiras: a decl dos locals casou exatamente com o mapa de slots (8 f64, nenhum i32) e o módulo validou E casou com o oráculo JVM na PRIMEIRA run. Provado byte-idêntico em 21 faces de sonda (±0.0, 1e-300/1e-8, a borda do ramo no 0.5 nos dois sinais, ±0.999, ±1.0 = 0 e π, NaN); a face nova `mathAcosFullRangeMatchesTheJvmOracle` (21 valores) na `WasmWasiTrigE2ETest` 7/7; a bateria `Wasm*` **82/82**; Q2 rc=0; os gates do escopo rc=0. Resta da trig inversa: só o atan2.

> **✅ POUSADO 28/10 — o `math.asin` agora avalia no WASI, byte-idêntico ao JVM em todo o domínio [-1, 1] mais o NaN canônico (lane `192.168.15.117:0`, rodada agendada flatt CLI).** O `__ieee754_asin` do fdlibm (o `FdLibm.Asin` do JDK — pio2_hi/lo, pio4_hi, pS0..pS5, qS1..qS4 em hex verbatim do src.zip) como `WasmAsinKernel`: o MESMO par de polinômios p/q nos dois trilhos — o pequeno (|x| < 0.5, t = x² no x ASSINADO, o sinal viajando em `x + x·w`, o `return x` precoce de |x| < 2^-27 para o ±0.0 bit-exato) e o grande (t = (1−|x|)/2, s = √t nativo do WASM, o zero do word baixo `__LO(s,0)` via a dança reinterpret/AND/reinterpret i64, os dois retornos: `pio2_hi − (2(s + s·w) − pio2_lo)` acima do threshold 0x1.F3333p-1 e a decomposição pio4_hi/df abaixo); o ±1.0 exato → `x·pio2_hi + x·pio2_lo`; |x| > 1 → o NaN `(x−x)/(x−x)` do fdlibm, bit-idêntico. O veredicto medido do wrapper: acos = π/2 − asin está MORTO (8/18 amostras derivam 1 ulp) — o acos terá a própria fatia. Provado byte-idêntico ao oráculo JVM em 24 faces de sonda (±0.0, 1e-300/1e-8, a borda do ramo no 0.5, o threshold EXATO 0.9749999046325684, ±0.975, ±1.0 = ±π/2, NaN); a face nova `mathAsinFullRangeMatchesTheJvmOracle` (24 valores) na `WasmWasiTrigE2ETest` 6/6; a bateria `Wasm*` **81/81**; Q2 rc=0; os gates do escopo rc=0.

> **✅ POUSADO 28/10 — o `math.atan` agora avalia no WASI, byte-idêntico ao JVM em TODO o domínio double — a primeira fatia libm sem fronteira de trap (lane `192.168.15.117:0`, rodada agendada flatt CLI).** O `__kernel_atan` do fdlibm (o `FdLibm.Atan` do JDK — atanhi[4]/atanlo[4]/aT[11] em hex verbatim do src.zip) como `WasmAtanKernel`, autocontido (o atan NÃO precisa de rem_pio2): o return precoce do NaN `x+x`; a árvore de redução de 4 ramos (|x| < 0.4375 mantém o x ASSINADO; 0.6875/1.1875/2.4375 subdividem o lado grande com as transformações (2x−1)/(2+x), (x−1)/(x+1), (x−1.5)/(1+1.5x), −1/x — todo limiar com hi-word de mantissa 0, o compare double É o compare hi-word do fdlibm); as duas cadeias de Horner fundidas (s1 sobre aT[0,2,4,6,8,10] escalado por z, s2 sobre aT[1,3,5,7,9] escalado por w); o retorno pequeno `x − x·(s1+s2)`; o retorno grande `h − ((x·(s1+s2) − l) − x)` com a árvore do id gravando atanhi/atanlo em locais e o sinal como um `f64.neg` final. O `return x` precoce para |x| < 2^-29 preserva o ±0.0 bit-exato (a dança do inexact do fdlibm sempre devolve lá). Provado byte-idêntico ao oráculo JVM em 38 faces de sonda (±0.0, 1e-300/1e-8, as quatro bordas EXATAS, ±1.0 = π/4, 1.5, 2.4375, 33.0, 1e15, ±1e300 = π/2); a face nova `mathAtanFullRangeMatchesTheJvmOracle` (27 valores) na `WasmWasiTrigE2ETest` 5/5; a bateria `Wasm*` **80/80**; Q2 rc=0; os gates do escopo rc=0. A fundação do drift medida: atan 34/34 — e asin/acos/atan2 TODOS GO (as fatias da família seguem).

> **✅ POUSADO 28/10 — o `math.tan` agora avalia no WASI, byte-idêntico ao JVM em todo o domínio ±π/4 (lane `192.168.15.117:0`, rodada agendada flatt CLI).** O `__kernel_tan` do fdlibm (o `FdLibm.Tan` do JDK — os coeficientes T[0..12] e π/4/π/4lo em hex verbatim do src.zip) como `WasmTanKernel`: o guard |x| ≤ π/4, o local do sinal (sg = ±1, x<0), o ramo da reflexão 0x1.59428p−1 (xw = (π/4 − xw) + π/4lo, o `ix >= 0x3FE59428` do fdlibm — o >= NA borda, provado pela face do limiar exato), o Horner de dois trilhos grau-27 (r = a cadeia T[1,3,5,7,9,11], v = z·(a cadeia T[2,4,6,8,10,12])), e o retorno iy=1: o caminho pequeno `w = xw + r`, o refletido `1 − 2·(xw − (w²/(w+1) − r))`, multiplicado pelo sinal no fim. Além de π/4 o guard trapa honestamente (o caminho grande do rem_pio2 é o MATH-LIBM-4). Os três defeitos que o bissepto validador+oráculo pegou, cada um pinado nos bytes emitidos: (1) a v-chain emitida ao contrário deixou o T12 na pilha ("values remaining on stack at end of block"); (2) o sub da reflexão tinha os operandos invertidos (xw−π/4 — o tan ímpar inverteu o sinal; o ±π/4 mascarou, as duas ordens dão 0.0); (3) o ramo do valor testava o xw REFLETIDO (sempre pequeno → sempre o retorno do caminho pequeno) — corrigido com o flag do |x| original num local i32 novo de zero-init, a forma wasm do `ix` intocado do fdlibm. Provado byte-idêntico ao oráculo JVM em 31 faces no total (a T1 12 + a T4 19, incluindo ±0.7853981633974482 um ulp abaixo de π/4 e o 0x1.59428p−1 exato); as faces novas `mathTanFullRangeMatchesTheJvmOracle` (17 valores) + `tanBeyondQuarterPiTrapsHonestly` (wasmtime rc=134) na `WasmWasiTrigE2ETest` 4/4; a bateria `Wasm*` **79/79**; Q2 rc=0; os gates do escopo rc=0 (a dívida do check_500 CAIU: WasmLowering 633→565).

> **✅ POUSADO 27/10 — `Char` agora vira string no WASI: `"val: " + c` e `String.valueOf(c)` (lane `192.168.100.23:0`, rodada agendada do flatt CLI).** A função de runtime JÁ existia — o `kof.strChar` atendia os campos Char do toString de record desde a 15.3d inc2 C — o gancho do valueOf e o registro condicional eram o que faltava. O lowering mapeia `String.valueOf(char)` para `kof.strChar` (o Char viaja na pilha como i64, o runtime embrulha internamente), o scan ganha `usesCharValueOf` (qualquer `valueOf` com parâmetro Char) e o registro adiciona `kof.strChar` quando records são usados OU um valueOf de char aparece. Provado byte-idêntico ao oráculo JVM: o print direto, o concat, o valueOf explícito e o loop cotidiano de construção de string char a char (charAt + concat + comparação de char na condição). A face nova `charToStringAndConcatMatchesTheJvmOracle`; bateria `Wasm*` **53/53**; o lote de regressão WASI (bool/if-expr/nullable) segue verde.

> **✅ CORRIGIDO 27/10 — o runtime WASI emitia um módulo com EXPORT DUPLICADO quando um programa combinava métodos de busca com métodos de fatia (lane `192.168.100.23:0`, rodada agendada do flatt CLI).** O `kof.strByteOff` é dependência comum de `indexOf`/`lastIndexOf`/`contains` E de `charAt`/`substring` — os dois blocos de registro o adicionavam, então um programa usando `s.contains(x)` junto com `s.substring(i)` produzia um módulo INVÁLIDO ("duplicate export name kof.strByteOff"). Correção: os guardas de dependência se acumulam numa ÚNICA condição (`needsByteOff`) e, sistematicamente, o backend agora faz DEDUPE das funções de runtime por nome (mantendo a primeira ocorrência) antes da serialização — fechando a classe inteira de pares de registro (compareTo/replace/length compartilham strCharAt/strLen/strMatchAt/strAlloc/strAppendByte da mesma forma). Provado: `s.length` + `contains` + `indexOf` + `substring` + `charAt` + `startsWith` + `endsWith` numa ÚNICA fonte agora valida e é byte-idêntico ao oráculo JVM; a face nova `combinedStringSearchAndSliceMatchesTheJvmOracle`; bateria `Wasm*` **52/52**. Locais de array em `_start` seguem a recusa honesta documentada (a fatia de heap de arrays).

> **✅ POUSADO 27/10 — a familia DOUBLE de aritmetica pura do `math` agora avalia no WASI (lane `192.168.100.23:0`, rodada agendada do flatt CLI).** `math.sqrt` e a INSTRUCAO nativa `f64.sqrt` do WASM (sem libm); `toRadians`/`toDegrees` reproduzem a ORDEM de operacoes do `Math.toRadians/ToDegrees` da JVM exatamente (`v/180*PI` e `v*180/PI` — zero-drift bit a bit); `lerp(a,b,t) = a+(b-a)*t` e `percentage(part,total) = part/total*100` seguem a ordem do runtime nativo. Os casos fixam resultados integrais porque o print fracionario de Double segue atras do port Schubfach (a armadilha honesta do runtime, documentada em 22/10 — agora a fatia principal da frente); sin/cos/tan/asin/acos/atan/atan2/pow seguem recusados honestamente (libm — fatia propria). Provado byte-identico ao oraculo JVM em 9 casos (sqrt sobre literais e uma expressao aritmetica, as conversoes de angulo em π/2 e π, lerp em 0.4/0.5, percentage 200/400). A face nova `mathDoubleArithmeticFamilyMatchesTheJvmOracle`; bateria `Wasm*` **51/51**.

> **✅ POUSADO 26/10 — a familia INT do `math` agora avalia no WASI (lane `192.168.100.23:0`, rodada agendada do flatt CLI).** `math.abs/sign/min/max/clamp` mais os predicados `isEven/isOdd/isPositive/isNegative/isZero` — aritmetica i64 pura no runtime WASI (SEM host imports: servem ao WASI e ao WASM igualmente). O abs e branchless (`(x ^ (x >>s 63)) - (x >>s 63)` — MIN_VALUE wrapa exatamente como o `Math.abs(int)` da JVM), o sign e `(x >>s 63) | ((0-x) >>s 63 & 1)`, min/max/clamp usam o select com os operandos na ordem do WASM ([val1, val2, cond] — o val1 e' o escolhido quando a condicao vale), os predicados comparam signed e saem como bools i32. A whitelist do scan admite os intrinsecos `kof_math_*` e o lowering os mapeia direto para os nomes do runtime, com o rastro de largura atualizado por resultado (Int = i64 largo, predicados = bool i32) e o print consumindo-o (a terceira face de rastro vazado: `println(isZero(0))` fazia o literal 0 do statement seguinte sair como i32). Provado byte-identico ao oraculo JVM em 16 casos (negativos, zero, clamp fora da faixa dos dois lados e dentro). A face nova `mathIntFamilyMatchesTheJvmOracle`; bateria `Wasm*` **50/50**; a regressao flow/IfExpr **48/48**. sqrt/pow/trig seguem recusados honestamente (libm — fatia propria).

> **✅ POUSADO 26/10 — `String.toUpperCase`/`toLowerCase` agora avaliam no WASI (lane `192.168.100.23:0`, rodada agendada do flatt CLI).** O case fold Unicode com a MESMA semantica do backend nativo — D-STR-UNICODE (27/09, linha 11): fold SIMPLES por code unit (`Character.toUpperCase/toLowerCase` por code point), os mapeamentos BMP (0x80..0xFFFF, ~1,2k entradas) numa tabela embarcada gerada do proprio JDK, ASCII dobrado inline, code points astrais (UTF-8 de 4 bytes) passando intocados. O runtime WASI ganha `kof.strCaseUp`/`kof.strCaseLo` (o mapeador por posicao: decode UTF-8, busca binaria na tabela, passagem astral) e `kof.strUpper`/`kof.strLower` (duas passadas: o total exato de bytes — mapeamentos simples PODEM mudar o comprimento, ex. U+0130 → i — depois o preenchimento via `strAlloc`/`strAppendByte`); as tabelas vão no cursor de dados (o heap começa depois delas). Provado byte-identico ao oraculo JVM em ASCII, Latin acentuado (café→CAFÉ, naïve), grego (ΣΟΣ↔σος), alfanumérico misto e passagem astral (✅). As excecoes de full-case-mapping (ß→SS, İ→i̇, sigma final) ficam FORA do escopo ratificado por-code-unit — o WASI se comporta exatamente como o NATIVO nelas (a frente nao diverge entre backends). A face nova `stringCaseFoldMatchesTheJvmOracle` (no `WasmWasiFlowE2ETest`); bateria `Wasm*` **49/49**; a regressao de flow/IfExpr **48/48**.

> **✅ POUSADO 26/10 — o JOIN do `if`-EXPRESSION agora avalia no WASI (lane `192.168.100.23:0`, rodada agendada do flatt CLI).** A mesma máquina de temporário único das cadeias lógicas: quando os dois ramos convergem no MESMO tipo (`bts.get(0).equals(bts.get(1))`, ou tipos físicos iguais), cada ramo GRAVA o resultado num temporário (`$kif`) e o join o CARREGA, então nenhum valor atravessa fronteira de estado; o alvo JS mantém o caminho próprio de pilha (o parser dele dobra o shape sem stores — a mesma sensibilidade medida no troolean em 19/09). Sondado: `var m = if (flag) 1 else 2`, ramos String/Bool, if-expressions aninhadas e o ramo não tomado — todos iguais byte a byte ao oráculo JVM; a face nova `WasmWasiE2ETest#ifExpressionJoinMatchesTheJvmOracle` fixa isso (wasm-tools validate + wasmtime vs o JVM); bateria `Wasm*` **48/48**; lote de regressão de condições/IfExpr **46/46** (incl. `IfExprNarrowedPrimitiveJoinE2ETest`, que pegou a face JS na primeira execução — corrigida pelo gate). **Divergência RESTANTE (registrada, próxima fatia):** ramos heterogêneos (`if (c) 1 else "x"`, join boxed) ainda recusam no WASI — o temporário precisa do tipo LUB boxed. **NOTA DE AMBIENTE:** os bumps do dependabot que pousaram em 26/10 (surefire 3.6.1, mariadb-java-client 4.0.0) NÃO estão no `~/.m2` offline deste host, então execuções locais offline exigem um pin temporário do pom (3.6.0 / 3.5.10); os poms foram restaurados antes deste commit — uma execução `mvn` online (ou os artefatos dos bumps) é necessária para o fluxo `mvn -o` normal.

> **✅ POUSADO 26/10 — as CADEIAS LÓGICAS (`&&`/`||`) agora avaliam no WASI (lane `192.168.100.23:0`, rodada agendada do flatt CLI).** A fatia anterior corrigiu a largura dos zeros sintéticos; esta corrige o JOIN: o resultado da cadeia atravessava uma fronteira de estado na pilha, e a máquina de estados não transporta a pilha entre estados — então `x > 0 && y > 0`, `while (i < n && j < m)`, `x == 1 || x == 2` e `b1 && b2` emitiam módulos inválidos (a recusa honesta WASM002 da fatia anterior era uma rede, não a funcionalidade). Correção: o lowering regular da cadeia (`ExpressionBinaryLowerer`) agora grava o resultado de cada arco num **local temporário** e o join o CARREGA — a mesma máquina de temporário único que o caminho troolean (`Bool?`) já usava — então toda fronteira de bloco começa com a pilha vazia e a máquina de estados do WASI é satisfeita; os backends JVM/nativo/JS recebem o mesmo IR, com comportamento idêntico (o alvo JS mantém o caminho próprio de cadeia). O curto-circuito é PRESERVADO e provado em execução: `false && 1/z == 1` e `true || 1/z == 1` com `z = 0` não armadilham porque o lado direito não é avaliado. Prova: 11 formas iguais byte a byte ao oráculo JVM (`&&`/`||` de bools, `&&`/`||` de comparações, `while` composto, precedência mista `false && true || true`, cadeia como valor, cadeias aninhadas de três com `!`); a face nova `WasmWasiE2ETest#logicalChainsMatchTheJvmOracle` (wasm-tools validate + execução wasmtime vs o JVM); bateria `Wasm*` **47/47**; lote de condições/loops **48/48**; a rede de honestidade `refuseLiveStack` permanece para os joins entre blocos restantes (o join da EXPRESSÃO `if` é a próxima fatia — a mesma máquina de temporário se aplica). A suíte completa do módulo mostra só a falha pré-existente `FfiNativeE2ETest` (verificada por stash).

> **✅ CORRIGIDO 26/10 — o defeito de condição sobre BOOL-VARIÁVEL (o bug medido `AUDIT-BOOL-IF` de 22/10; lane `192.168.100.23:0`, rodada agendada do flatt CLI).** O defeito era uma família com uma raiz: valores bool são **i32** no WASI enquanto os zeros sintéticos da redução de truthiness eram tipados **INT** (i64). Três faces medidas, três correções: (1) **o zero tipado** — o `emitTruthinessJump` agora tipa o zero sintético pela CONDIÇÃO (`BOOL` → literal i32), os caminhos do do-while/for passam pelo mesmo helper, e a cadeia `&&`/`||` tipa seus 0/1 sintéticos pela expressão (`b1 && b2` não despacha mais larguras diferentes); (2) **o estado de largura vazado** — o `ctx.lastPush` sobrevivia entre statements (`var flag = true` + `var i = 0` fazia o `0` herdar "bool" e emitir i32.const num slot i64 — a segunda face de módulo inválido) — o reset agora é por BLOCO com limpeza em todo consumidor (store, salto condicional); (3) **valores vivos entre blocos** — a cadeia `&&` carrega o operando direito através de uma fronteira de estado e a máquina de estados não transporta a pilha, o que emitia um MÓDULO INVÁLIDO — o subconjunto agora recusa honestamente (`WASM002`, sem artefato, Q7). Prova: as faces novas do `WasmWasiE2ETest` — `boolVariableConditionsMatchTheJvmOracle` (if/else, if, while(flag), do-while com comparação bool, `!d`, e uma chamada que retorna BOOL como condição — wasm-tools validate + igual byte a byte ao oráculo JVM) e `andOverBoolVariablesIsAnHonestRefusal` (WASM002 + sem artefato); bateria `Wasm*` **45/45**; o lote de condições/loops **48/48** (`IfExpr*`, `JsLoop*`, `KofJsLogicalShortCircuit`, `NullableBool*`, `JsDoWhile*`). Suíte completa do módulo: as únicas falhas são do `FfiNativeE2ETest` (2 faces, exit 139 nativo) — **verificadas pré-existentes por stash** (falham sem esta mudança; FFI/nativo, fora do escopo desta correção).

> **Estado (07/10): EM DESENVOLVIMENTO — promovido por ordem direta da**
> **mantenedora** (`D-WEB-WASI-DEFAULT-0710`); **zero código no dia da promoção.**
> O plano é **GATE do corte 0.6.0** (issue #776): o alvo web do Kof passa a ser
> **WASI por padrão**, o frontend desktop igualmente; o alvo JS/`kofjs`
> **continua existindo** quando explicitamente especificado; a **superfície da
> linguagem não muda**; **paridade total de comportamento** e **zero regressão**
> são obrigatórios. O plano está ABERTO e sem dono — qualquer lane livre o
> reivindica no DOING primeiro (`D-PLAN-ONE-OWNER`). Cada afirmação abaixo
> permanece marcada **CURRENT** (medido no código), **PLANNED** (proposto aqui)
> ou **TBD / DECISION REQUIRED** (marcadores históricos — o bloco 28/09
> `D-WASM-01..09` já resolveu as questões técnicas).
> por pedido explícito da mantenedora ("documente a implementação futura de
> WASM/WASI; NÃO implemente"). Não é fila de execução (regra três-estados +
> R12). Esta spec **não muda nada**: nenhum `Target`, backend, runtime ou
> semântica de linguagem.

> **Atualização de decisão (28/09/2026):** as questões técnicas que esta spec
> deixou TBD/DECISION REQUIRED foram **resolvidas** pela mantenedora em
> `DECISIONS.md` §`D-WASM-GO` (`D-WASM-01..09`: backend direto, `Int`=i64,
> unwinding por string lançada, handles+tabela de handles, GC mark-sweep do
> design nativo, env de closure explícito, WASI preview1, wasmtime primeiro,
> concorrência cooperativa). A spec, porém, JÁ FOI PROMOVIDA (07/10 — este doc
> vive em `docs/`, não em `future/`) e as unidades de emissão pousaram: MEDIDO
> 21/10, o backend `wasm/` tem 15 arquivos e `kof build --target=wasi --output X`
> realmente gera `Default/Main.wasm`; só o flip de frontend **15.4** segue
> pendente. Não leia os marcadores TBD inline como decisões ainda abertas.
>
## Como ler este documento

Toda afirmação carrega exatamente uma marca:

| Marca | Significado |
|---|---|
| **CURRENT** | estado real do código hoje (arquivo citado) |
| **PLANNED** | feature futura, explicitamente *não implementada* |
| **TBD** | decisão aberta — ninguém decidiu; não trate como certa |
| **DECISION REQUIRED** | portão regra 6 — a mantenedora decide antes de implementar |

Nunca escreva "Kof suporta WASM": não suporta (CURRENT: `--target wasm` é
**rejeitado** com o gap honesto `WASM001`).

---

## 1. Objetivo da feature (PLANNED)

Adicionar **WASM** e **WASI** como targets oficiais do Kof:

```
Kof source → compilador Kof → IR Kof → backend WASM → module.wasm
```

O desenvolvedor escreve Kof e escolhe WebAssembly sem escrever JavaScript,
C ou linguagem intermediária. Kof **não** vira transpiler: WASM é
especificado como **backend de primeira classe**, par de JVM / Native / JS
/ Script (CURRENT enum: `Target.java` — `JVM, NATIVE, NATIVE_RISCV64,
NATIVE_AARCH64, JS, ANDROID, SCRIPT`; sem `WASM`).

## 2. WASM × WASI — camadas diferentes

```
WASM = o formato de módulo; execução controlada por um host (browser, runtime)
WASI = WASM + interface de sistema (fs, stdio, clocks, random, …) conforme a
       especificação WASI — camada SOBRE o módulo, não "WASM de servidor"
```

```
Kof → módulo WASM → Host                      (browser: glue JS + Web APIs)
Kof → módulo WASM → interface WASI → host/runtime WASI
```

## 3. Arquitetura futura

```
                          Kof
                            │
                   Frontend + IR (CURRENT: parser → SemanticAnalyzer →
                   lowerers; IR compartilhada já provada pelo Target.SCRIPT,
                   que interpreta a IR via CompilerPipeline.interpret)
                            │
     ┌──────────┬───────────┼────────────┬───────────┬──────────┐
     │          │           │            │           │          │
    JVM      Native         JS          WASM      Script     Android
 (ASM bytecode)(asm x86-64)(dev.kof.   ┌────┴────┐  (IR      (ASM→APK,
                       compiler/js)  Browser    WASI         §330)
                                     host      module
```

**Pontos de extensão identificados no código real** (investigar antes de
implementar — não assumir mais deste diagrama do que está escrito aqui):

- `Target.java` — enum + `isNative()`/`nativeArch()` (PLANNED: `WASM`,
  `WASI` — **TBD: uma entrada por host ou `WASM` + flag de host**;
  DECISION REQUIRED).
- `TargetMatrix.java` — **CURRENT**: mapeia os pedidos `wasm`/`kofwasm`/
  `kofwebasm`/`kofwebassembly`/`webassembly` **e as grafias WASI** `wasm32`/
  `wasm32-wasi`/`wasi`/`wasi-preview1`/`wasip1`/`kofwasi` ao gap honesto
  `WASM001`, nunca silencioso. **Fatia 15.1 (LANDED 07/10, lane
  compiler/JVM/native `192.168.15.30:9092`):** os dois caminhos CLI
  (`--frontend=wasi`/`kof.toml` via `TargetMatrix.parse`, legado
  `--target=wasi` via `KofCliSupport.parseTarget`) recusam com `WASM001`
  nomeado — nunca "unknown target" (R6) — e a mensagem aponta para o plano
  promovido + issue #776 + `D-WEB-WASI-DEFAULT-0710`. **CORRIGIDO 21/10:** essa
  recusa vale para o papel de FRONTEND; como backend de BUILD, `--target=wasi`
  parou de recusar na unidade 15.3 e hoje emite `Main.wasm` (medido; roda sob
  `node:wasi`, ver `backend-parity.md`). Histórico: (antes o obsoleto
  "Phase 6 of the platform plan"). Plumbing aditivo: **ainda sem valor no enum
  `Target`** (adjacente a superfície congelada, regra 6) e sem codegen. Prova
  RED-first: `TargetMatrixTest.wasiSpellingsAreHonestGap` +
  `SelectTargetsTest.legacyWasiTargetFlagIsHonestGap`.
- **Fatia 15.2 POUSADA (07/10, lane `192.168.15.101:9092`):** primeiro backend
  emissor do `wasm` — `dev/kof/compiler/wasm/WasmBackend` grava um módulo
  WebAssembly binário direto (seções `WasmBinary`, encodings `WasmInstr`)
  exportando toda função estática top-level ESCALAR (`Int/Long/Double/Bool/
  Char`; `Int=i64` por D-WASM-02) de `Default/Main`; fluxo de controle desce
  do grafo de blocos do IR para um dispatcher `loop $dispatch` + `$pc` (com
  profundidade de `br` explícita — fallthrough apenas-para-frente foi medido
  e rejeitado); chamadas diretas resolvem por nome na serialização. A borda
  do subset é recusa HONESTA, nunca skip silencioso: `CompilerPipeline` pega
  `WasmUnsupportedException` → `WASM002` nomeando plano + unidade + #776 e
  NÃO grava artefatos (Q7). `main`/IO/strings/coleções/records = 15.3+
  (D-WASM-03..06). Emissão WAT segue PLANNED (a linha `--emit=wat` precede a
  fatia; o produto da 15.2 é o binário). Prova: `WasmScalarE2ETest` 3/3 —
  emissão+validação via `wasm-tools` 1.261.0, execução via `wasmtime`
  v49.0.2 (`scripts/provision-wasmtime.sh`, checksums pinados, host-gated por
  `assumeTrue`), paridade com oráculo JVM de `add`, `collatz(27)=111`,
  `fib(10)=55`; o caso de recusa prova módulo parcial INEXISTENTE.
  `WasmTargetGateE2ETest` reppinado na verdade nova (wasm É backend; WASI
  segue `WASM001`; programa fora do subset → `WASM002`).

**Fatia 15.3-stdout POUSADA (07/10, lane `192.168.15.101:9092`):**
`Target.WASI` e agora um backend EMISSOR WASI-preview1: `main` compila para
`_start` exportado (modo comando), e todo `println` escalar escreve pelo
import `wasi_snapshot_preview1.fd_write`; `Int`/`Long` imprimem pelo helper
`kof.writeInt` (itoa emitido), `Bool` pelo `kof.writeBool`. Programas fora da
fatia (literais de string, `args`, records/colecoes) recusam `WASM002`
nomeando este plano + #776, sem escrever artefatos (Q7; provado por
`WasmWasiE2ETest` + `WasmTargetGateE2ETest`). Prova: `WasmWasiE2ETest` 3/3 —
modulo com imports preview1 + export `_start`, validado com `wasm-tools`
1.261.0, executado sob `wasmtime` v49.0.2 com stdout EXATAMENTE igual ao
oracle JVM do mesmo fonte (ints negativos/zero/resultado de chamada, bools),
exit 0; `WasmScalarE2ETest` 3/3 (15.2) e `WasmTargetGateE2ETest` 5/5
reppinado (WASI E backend; o gap de frontend fica `WASM001` ate 15.4) seguem
verdes; `kof deploy --target wasi` recusa com honestidade `WASM001` (emite
modulo, mas sem archive/host de deploy ainda — `SelectTargetsTest`/
`CmdDeployTest` verdes sem mudanca). `println(String)`, records, colecoes,
`args` e o runtime de GC handles pousam nas PROXIMAS fatias da 15.3
(regra GC-handle do plano).

**Fatia 15.3b POUSADA (07/10, lane `192.168.15.101:9092`):** `println(String)` de
LITERAL agora emite: os bytes vao para um DATA segment do wasm (pool com base em
1024, `+1` byte reservado por string para o newline, alinhado em 4), a interceptacao
do println empilha `(addr,len)` e o helper emitido `kof.writeString` escreve via
`fd_write` com o `\n` final. Prova: `WasmWasiE2ETest` 3/3 — main mista escalar+string
imprime EXATAMENTE o stdout do oracle JVM (`oi`, `hello kof` adicionados) sob
wasmtime v49.0.2, modulo valida com `wasm-tools`; strings fora da fatia (concat
`var s = "a" + "b"`, `println(args)`) recusam `WASM002` nomeando plano + #776 sem
artefatos (Q7); `WasmTargetGateE2ETest` 5/5 reppinado (o caso de recusa saiu de
literal — agora EMITIDO — para `args`); `WasmScalarE2ETest` 3/3 segue verde.
VARIAVEIS/concat/records/colecoes de string exigem o heap (shape de handle-table
dos §8/§9 do plano) — 15.3c+ (`args`) e depois as fatias de GC. NOTA 07/10: a suite
de 4 modulos e `4877 run / 3 F` — JavaFX ambiental (documentada) +
`Av1CoeffsE2ETest` aarch64/riscv64 = regressao EXTERNA catalogada como
`known-bugs` **§625** (bisect cai em `a2f69d2f7` do shift cross do §620; reproduzida
no tip remoto `45d839322` SEM nenhum codigo da lane WASI; lane dona
`192.168.15.30:9092`; NAO tocada por esta lane — regra de colisao) — **CORRIGIDA
08/10** por aquela lane (prologue cross/x86 zera slots acima de `paramSlotMax`, `PrologueSlotInitTest`).


- **Fatia 15.3c-fatiaA POUSADA (08/10, lane `192.168.15.101:9092`):** VARIAVEIS e
  CONCATENACAO de String agora emitem — um valor `String` vira um HANDLE i32 num
  bump heap (`global 0`, i32 mutavel inicializado em `16384`; layout `[len][bytes]\n`,
  o `+1` do newline reservado na emissao para o `println` nao realocar). Tres helpers
  emitidos: `kof.strLit(addr,len)` copia um literal do DATA segment para um bloco novo
  do heap e devolve o handle; `kof.strConcat(a,b)` aloca `la+lb`, copia os dois payloads
  (`copyLoop` — `block`+`loop` com `br_if`/`br`, `i32.load8_u` align 0, byte store com
  `addr` empilhado antes do `value`) e devolve o handle concatenado; `kof.writeStr(handle)`
  escreve `[h]+1` bytes via `fd_write` (reusado para cada `println` de string nao-literal).
  A interceptacao do println empilha `(addr,len)` para literal e chama `kof.strLit` para
  `kof_string_concat`/variaveis `String`, entao `var s = "a" + "b"; println(s)` e
  `println("x" + "y" + "z")` imprimem `ab`/`xyz`. Prova: `WasmWasiE2ETest` 3/3 — a main
  mista escalar+string+concat imprime EXATAMENTE o stdout do oracle JVM
  (`3/-42/0/55/true/false/14/oi/hello kof/ab/xyz`) sob wasmtime v49.0.2, modulo valida com
  `wasm-tools`, exit 0; `WasmTargetGateE2ETest` 5/5 + `WasmScalarE2ETest` 3/3 +
  `TargetMatrixTest` 10/10 verdes. Na epoca `args` ainda recusava; pousou no MESMO DIA
  como 15.3c-fatiaB abaixo (records/colecoes ficam; flip 15.4 POR ULTIMO). MEDIDO 08/10: a suite completa dos 4 modulos no tip `898bc50ab` + esta fatia = 8F + 2 flakes, TODOS externos/ambientais e stash-prova independentes da lane WASI: §625 `Av1CoeffsE2ETest` (2F, CORRIGIDA 08/10 pela lane `.30:9092` — zera slots stale do prologue) + §627 `KofTestingE2ETest` assert-float (2F, CORRIGIDO 08/10 pela lane `.30:9092` — `EQ/NE` do interpretador + `feq.s` cross) + §628 `JvmLauncherDiagnosticE2ETest` (3F deterministas no tip LIMPO — o `ExternalArgTighten` do §554 quebra as fixtures de pipe, dona compilador/interop) + JavaFX ambiental (1F) + flakes de carga `InteropTimeoutE2ETest` (2F, VERDES isolados). Fila viva 3->5 (5->3 apos as correcoes §625+§627).


- - **Fatia 15.3c-fatiaB POUSADA (08/10, lane `192.168.15.101:9092`):** `args` — um
  programa que toca `args` ganha prologo `kof.readArgs` no `_start`: WASI-preview1
  `args_sizes_get(&argc@48,&argv_buf_size@52)`, bump-aloca a TABELA de ponteiros
  (`argc*4`) e o BUFFER NUL (`sz`) no `global 0`, chama `args_get` e converte cada
  `argv[i+1]` (argv[0] = nome do programa, DESCARTADO p/ paridade JVM) num handle
  KofString `[len][bytes]\n` no heap (strlen + `copyInto`), devolvendo handle de
  array `[count][handle...]`. `args.length` reduz a `i32.load` de `[arr]` +
  `i64.extend_i32_s` (Int = i64, D-WASM-02); `args[i]` reduz a `i32.wrap_i64` do
  indice com bounds-check EXPLICITO (`count <= idx` via `i32.le_u` -> `unreachable`)
  — fora de limites e trap DETERMINISTICO, NUNCA leitura de lixo (modelo de excecao
  D-WASM-03 segue TBD); `println(args[i])` e `args[i] + "x"` usam o runtime de String
  da 15.3c-fatiaA (`kof.writeStr`/`kof.strConcat`). Prova: `WasmWasiE2ETest` 5/5 sob
  wasmtime v49.0.2 — `argsLengthIndexAndConcatMatchTheJvmOracle` compila a MESMA
  fonte para JVM e WASI, roda as duas com `alpha beta` e exige stdout byte-a-byte
  identico (`2/alpha/beta/alpha-beta`); `emptyArgsLengthMatchesJvmAndOobIndexTraps`
  pinta `args.length == 0` sem argv e exit NAO-ZERO do wasmtime para `args[7]`
  (`unreachable` explicito, exit 134 medido); face de recusa re-pinnada: `println(args)`
  (formato de array) e `for (a in args)` seguem `WASM002` nomeando plano + #776 sem
  artefatos. Higiene de gate: o split cobrado pelo `check_500` pousou COM esta fatia —
  `WasmBackend` 629 -> 204 (so assembly do modulo), lowering/dispatcher/Ctx extraidos
  para `WasmLowering` (447), `kof.readArgs` + `copyInto` vivem em `WasmArgsRuntime`
  (192), `WasmStdoutRuntime` 503 -> 329; puro movimento de codigo, bateria WASI verde
  depois. MEDIDO 08/10 (arvore pos-split + pos-correcao §625): suite completa 4 modulos = kof-compiler 4898 exec/5F + kof-cli 593/3F — §627 2F + §628 3F = 5F deterministas EXTERNAS (outras lanes) + JavaFX-amb 1F + flakes de carga InteropTimeout 2F = 3F ambientais; §625 VERDE apos a correcao `.30` do prologo (`6572e6367`, Av1Coeffs 6/6 re-medido aqui); RingPrivilege verde nesta passada; ZERO falhas WASI, bateria 23/23 + PrologueSlotInit 2/2.

**Fatia 15.3d-incremento1 POUSADA (08/10, lane `192.168.15.101:9092`):** ALOCACAO de RECORD + ACESSO a campo Int/Long — um valor de record e um bloco no bump heap cujo layout e o `ClassLayout.build(IRClass)` (MESMA fonte do Native: cabeca + slots de 8 bytes), o receptor e um handle `i32` no slot local (`lowerMethod` mapeia um local/param de tipo record para `i32`); `WasmLowering` ganha `KofNewObject` (bump do `global 0` por `totalSize`, guarda o handle num scratch `obj` e o empilha), `KofDup` (no-op — o handle que o construtor deixou na pilha de valores do wasm e reusado), o `<init>` de record (chamada `CONSTRUCTOR`: desempilha os valores dos argumentos na ordem reversa dos campos num scratch `v` i64 e faz `i64.store` em `obj + fieldOffset`) e `KofLoadField` (handle `i32` do receptor + `fieldOffset` -> `i64.load`). `WasmInstr.Mem` ganha os opcodes i64 de memoria reais `LOAD64=0x29`/`STORE64=0x37` (align 3) — o menor mecanismo, nao um hack por funcao.** **Honestidade de escopo (Q7/R6):** so campos `Int`/`Long` (i64 na pilha) sao construidos; um record com campo `String`/`Bool`/`Char`/`Double`/record-aninhado, `println(record)` (`toString`), `record == record` (`equals`) e record-em-`+` exigem lowering de metodo de INSTANCIA (ainda ausente) e seguem recusando `WASM002` nomeando plano + #776 SEM artefatos — nunca um modulo silenciosamente invalido. Prova: `WasmWasiE2ETest` 7/7 sob wasmtime v49.0.2 — `recordAllocationAndIntFieldAccessMatchTheJvmOracle` compila `record Point(Int x, Int y) { var p = Point(1,2); println(p.x); println(p.y) }` para JVM + WASI e afirma stdout byte-igual (`1/2`); `recordToStringAndEqualityStillRefuseHonestly` pina as tres faces ainda abertas (toString / igualdade / record com campo String) para `WASM002` + sem-artefatos. PROXIMO (incremento 2): as faces `toString`/`equals`/concat de record pousam com o lowering de metodo de instancia; depois colecoes; depois 15.4 flip POR ULTIMO.
**Fatia 15.3d-incremento2-FATIA-A POUSADA (09/10, lane `192.168.15.101:9092`):** `String.valueOf(Int|Long)` como VALOR (variavel / dentro de `+`) — `WasmStdoutRuntime.kofIntToStr` formata os digitos na MESMA janela de scratch que o `kof.writeInt` ja provado verde usa, depois bump-aloca um handle `[len][digits]` no heap com o padrao do `kof.strLit` (`copyLoop`, bloco +5) e devolve o handle; o `WasmLowering` roteia o valueOf de dono `String` que NAO e imediatamente `println` para `kof.intToStr` (Int|Long), trata receptor String como IDENTIDADE (o desugar do `+` da JVM reaplica `valueOf` sobre um handle existente — medido no `_start` emitido), e recusa o resto `WASM002` SEM artefatos (Q7); o `WasmBackend` registra o helper junto do runtime de strings. Prova: `WasmWasiE2ETest` 8/8 sob wasmtime v49.0.2 — `stringValuesOfVariablesAndConcatMatchTheJvmOracle` (mesma fonte JVM+WASI, byte-igual `3 / -42 / 7! / x100`); bateria WASI 26/26. PROXIMO: fatia B = larguras de campo de record (Bool/Char/handle-String = slots i32, Double = f64), fatia C = `toString`/`equals`/concat-de-record inline via `kof.strEq`.
**Fatia 15.3d-incremento2-FATIA-B POUSADA (09/10, lane `192.168.15.101:9092`):** LARGURAS DE CAMPO DE RECORD + paridade de impressao char/bool — `WasmStdoutRuntime.kofWriteChar` agora recebe o i64 que o `emitLiteral` 15.2 REALMENTE empilha para `Char` e faz wrap p/ um local i32 (o modulo antigo de `println(char)` era INVALIDO — um falso-verde latente agora pinado por `charAndBoolPrintMatchTheJvmOracle`); `WasmLowering` despacha os stores do `<init>` de record e as leituras `KofLoadField` por largura: Int/Long/Char = i64 (`Mem.STORE64/LOAD64`), Bool/handle-String = i32 (`Mem.STORE/LOAD`), Double = f64 (`Mem.STORE_F64/LOAD_F64` = 0x39/0x2b align 3, novo em `WasmInstr.Mem`) com scratch por funcao `v32`/`vf64`; `operandTypeBefore` responde o tipo do campo para o `println(campo)` registrar o escritor certo; `isStringField` reconhece campos `String` de record; `usesStringOps` registra o runtime de strings em programas com literais/handles de String sem nenhuma string impressa (so `Pair("ab", 7)` codificava uma chamada `kof.strLit` nao resolvida); campos record-aninhados seguem recusando `WASM002` SEM artefatos (a recusa `record-string-field` virada verde; `record-nested-field` pinada). Prova: `WasmWasiE2ETest` 10/10 (`charAndBoolPrintMatchTheJvmOracle`, `recordFieldWidthsMatchTheJvmOracle` byte-iguais ao oraculo JVM sob wasmtime v49.0.2); bateria WASI 28/28. PROXIMO: fatia C = `toString`/`equals`/concat-de-record inline via `kof.strEq`.
**Slice 15.3d-increment2-SLICE-C2 POUSADA (09/10, lane `192.168.15.101:9092`):** RECORD `==`/`!=` CONTENT EQUALITY — the frontend `RecordEqualityLowerer` WASI arm desugars to the SAME opaque `kofRecordEq(L,R)` call the JS arm already uses (the plan`s own precedent: "one single function call is an opaque expression for all backends"; the loop/`$pc` linearization never gets the cross-block merge it could not model, so it never HAS to). `WasmRecordCode.emitEquals` folds the fields INLINE in one straight-line block — accumulator locals `eqAcc`(handle L)/`eqEq`(handle R)/`eqAnd`(AND-fold) + `eqCmp`/`eqDbl` scratch, every `if` void-bodied so no value ever crosses a label; null semantics = `java.util.Objects.equals` (both null→1, one null→0, else field-wise content); widths: Int/Long/Char `i64.eq`, Bool `i32.eq`, String fields via NEW `kof.strEq` (length check + byte-scan loop over the bump-heap blocks), Double via `i64.reinterpret_f64`+`i64.eq` = JVM `Double.equals` bits (+0.0/-0.0/NaN parity); `!=` (EQ-vs-0 desugar) and the null-0 literal are intercepted as `i32` when the producer is the fold; `record == null` (reference branch of the desugar) = handle-vs-0 `i32.eq/ne`; the explicit `p.equals(q)` still refuses `WASM002` — instance-method dispatch is a separate slice. Bugs the byte-parity caught (Q0/Q5, fixed same commit): wasm `br` to a LOOP label is a CONTINUE — the strEq scan exits at depth 2 (the enclosing `if`), not 1 (infinite loop measured); the fold accumulator doubling as the L handle got clobbered by the seed — roles split `eqAcc`/`eqAnd`; `i32.and` opcode (0x71, an earlier 0x7c was `i64.add`). Gate hygiene: `WasmPrintCode` extracted from `WasmLowering` (622->593, `check_500` rc=0). Prova: `WasmWasiE2ETest` **12/12** under wasmtime v49.0.2 — `recordEqualityMatchesTheJvmOracle` mesma fonte JVM+WASI byte-igual em `Point`/`Pair`(String content-not-identity)/`Flag`(Bool/Char)/`Dbl`(bits)/`== null`/`!= null`/`!=`/`!`; `record-equality` refusal FLIPPED GREEN; refusal test renamed+re-pinned to `record-explicit-equals` + `record-nested-field`. WASI battery **30/30**. PROXIMO: nested-record fields + `Double`/`Double.toString` in record `toString`; then collections; then 15.4 flip LAST.

**Fatia 15.3d-incremento2-FATIA-C1 POUSADA (09/10, lane `192.168.15.101:9092`):** `toString`/PRINT/CONCAT DE RECORD INLINE — o novo `wasm/WasmRecordCode.emitToString` expande o metodo de instancia sintetizado NO CALL SITE (o backend ainda nao tem dispatch virtual — a mesma decisao de shape de `CompilerRecordSupport`/`RecordEqualityLowerer`, o oraculo JVM): `Name[f1=v1, f2=v2]` montado com handles no bump heap (`kof.strLit`/`kof.strConcat`), Int/Long via `kof.intToStr`, Bool via o novo `kof.strBool`, Char via o novo `kof.strChar`, campo String reusa o proprio handle; o `WasmLowering` roteia `println(record)`, `record.toString()`, `String.valueOf(record)` e record-dentro-de-`+` por la (tipo do operando via `operandTypeBefore` sobre `ctx.flat`), `KofCheckCast` vira no-op e valores wide guardados em slots `Object` fazem wrap `i64->i32`; o `WasmBackend` pula no closure os `toString`/`equals` de dono-record, admite `recOk` nos tipos de println e registra o runtime de strings + heap quando `usesRecords`. **Honestidade de escopo (Q7/R6):** `record == record` segue recusando `WASM002` SEM artefatos — medido: o desugar do `==` da JVM faz o merge da comparacao de campo ATRAVES de fluxo de controle entre labels (ternario + literais `i64` num merge `i32`), que a linearizacao loop/`$pc` nao modela; o scaffolding `emitEquals`/`kof.strEq` nunca ligado foi APAGADO (codigo morto e stub, Q7 — volta na fatia C2 COM o modelo de merge). Higiene de gate: `WasmTypeOracle` extraido do `WasmLowering` (726 -> 569 tolerado; responsabilidade oraculo/inferencia), `WasmStdoutRuntime` 477 abaixo do alvo. Prova: `WasmWasiE2ETest` **11/11** sob wasmtime v49.0.2 — `recordToStringAndConcatMatchTheJvmOracle` (mesma fonte JVM+WASI byte-igual `Point[x=1, y=2]` / `v=Point[x=1, y=2]` / `Pair[a=ab, n=7]` / `Flag[c=k, ok=true]`), `recordToStringAndEqualityStillRefuseHonestly` repinada para as duas faces abertas (igualdade + campos aninhados), `charAndBoolPrintMatchTheJvmOracle` segue verde (um wrap errado de println adicionado durante o trabalho foi pego por ela). Bateria WASI **29/29**; `check_500` rc=0. PROXIMO: fatia C2 = `==` de record com o modelo de merge cross-block; depois campos record-aninhados + Double; depois colecoes; depois o flip 15.4 POR ULTIMO.

**Emenda (07/10, lane `192.168.15.101:9092`, 15.1-COMPLETA):** o passo do
  enum NÃO ficou adiado — o roadmap TIER 15 define a própria 15.1 como
  enum+encanamento (ordem da mantenedora `D-WEB-WASI-DEFAULT-0710`, que
  promoveu este plano). `Target.WASM`/`Target.WASI` agora EXISTEM: as formas
  canônicas `wasm`/`wasi` PARSEIAM como alvos reais ponta a ponta, e a recusa
  honesta de emissão (WASM001, nomeando este plano + unidade 15.2 + #776, sem
  escrever artefatos, nunca fallback JVM) vive em
  `TargetMatrix.validate`/`CompilerPipeline.lowerAndEmit`
  (`WasmTargetGateE2ETest` 5/5). Os solecismos longos mantêm o gap de string
  da `.30` verbatim; os dois testes da `.30` foram re-pinados para esta
  verdade (cobertura de solecismos preservada). O código do gap está
  reservado para este futuro (§14).
- `dev.kof.compiler.js` (`JsBackend`, `JsArtifactWriter`, …) — precedente
  estrutural para um pacote `dev.kof.compiler.wasm`.
- CLI: `dev.kof.cli` mapeia `"jvm"|"native"|"js"` para `Target` (ex.:
  `BenchDiscovery.java`); braço `wasm`/`wasi` é PLANNED (§23).
- Sistema de capabilities da stdlib — CURRENT: gates por `Target`
  (`supportedOn`) + códigos de gap `XXX00x` + matriz
  `docs/backend-parity.md` (§14).

## 4. Decisão arquitetural principal (REQUISITO)

```
Kof → WASM deve ser compilação DIRETA pela infraestrutura interna do
compilador (parser → typer → lowerer → emissor WASM), produzindo o binário
.wasm ele mesmo.
```

Rejeitado como arquitetura principal: `Kof → JavaScript → WASM` (amarra o
produto a toolchain JS e herda semântica JS) e `Kof → C → WASM`
(mediatizado por clang; perde tipos/IR do Kof, duplica a história da
toolchain C). O que viabiliza o backend próprio: o **front-half**
(lexer/parser/AST/`SemanticAnalyzer`/lowerers já target-neutrais — prova:
`SCRIPT` roda na IR compartilhada sem backend) e os **contratos de shape de
runtime** (GC, strings, dispatch) já pagos em JVM+Native+JS. **TBD**: se o
emissor consome a IR de lowering atual direto ou uma IR intermediária
orientada a WASM — decidir na Fase 0 (§31).

## 5. Target registry (15.1+15.2 POUSADA 07/10 — enum + matriz + parse da CLI + emissão ESCALAR)LANEJADA)

```
CURRENT (real hoje):
  kof compile/build/run → jvm | native (native.risc/arm cross) | js | script | --android
  kof --target wasm → EMITE o subset escalar desde 15.2 (fora dele: WASM002 honesto)
  kof --target wasi → EMITE desde a unidade 15.3 (medido 21/10: `kof build --output` gera Default/Main.wasm)

MEDIDO 21/10 (antes esta secao listava tudo como PLANNED):
  kof build --target=wasm  → LANDADO (emite o subset escalar)
  kof build --target=wasi  → LANDADO (emite Main.wasm)
  kof check --target=wasm  → LANDADO ("checked 1 file(s) — no errors")
  kof run   --target=wasm|wasi → RECUSADO com causa nomeada (Q7: nao ha host;
                                 use `kof build` + um runtime WASI)
```

Formato da flag (`--target=x` vs uso atual) medido no `dev.kof.cli` na
implementação — **TBD**.

## 6. Backend WASM (spec futura)

O emissor deve produzir módulo core-WASM válido cobrindo ao menos:
`Type · Function · Table · Memory · Global · Export · Import · Code · Data`.

Mapeamento `conceito Kof → IR → WASM`:

| Conceito Kof | Representação WASM | Status |
|---|---|---|
| função | função WASM (type + code) | PLANNED |
| local | local WASM | PLANNED |
| control flow (`if`/`while`/`for`/`switch`) | `block`/`loop`/`if`/`br`/`br_table` | PLANNED |
| `Int` | `i64` | **RESOLVIDO 28/09** — `D-WASM-GO`/`D-WASM-02`: `Int`=i64 (bate com o golden JVM/Native) |
| `Float`/`Double` | `f32`/`f64` | PLANNED |
| referência (objeto/record/String) | handles + tabela de handles | **RESOLVIDO 28/09** — `D-WASM-GO`/`D-WASM-04` |
| chamada ao host | `import` + adaptador JS/WASI | PLANNED |

Mapeamento que dependa de decisão aberta fica **TBD** — não inventar.

## 7. Runtime WASM

Runtime mínimo avaliado contra o runtime Nativo existente (allocator,
memória, strings, arrays, objetos, erros de runtime/panic, GC). Perguntas
abertas da Fase 0 (todas **TBD**): o que é reutilizável do Native
(source-level, não binário); ABI necessária (a ABI Native atual é
SysV-shaped x86-64; WASM é nova); representação de objetos Kof (a história
do slot de tamanho de lista tem seu próprio §252); administração de
memória (§8).

## 8. Memória linear

```
objeto Kof → runtime Kof → linear memory WASM
```

Pontos de investigação (nenhuma estratégia decidida): allocator
(bump-then-GC?), alignment, object layout, representação de ponteiro
(`i32` offset vs `i64` vs `ref` gerenciado), growth (`memory.grow`), free,
integração com GC, root tracking. Nada está comprometido — afirmar hoje
seria ficção.

## 9. Garbage collector

CURRENT do GC Kof: **JVM** delega ao GC da JVM; **Native** tem decomposição
mark-sweep **em andamento** no roadmap (G-0 riscv ✅ `356f33b9`; G-1..G-5
pendentes — `docs/development/roadmap.md`); auto-GC foi desabilitado após
um hang (nota do roadmap). WASM deve se acoplar a **esse** design, não
forkar o seu.

Dependências documentadas: safe points, root maps, referências de objeto,
ciclos alocação/coleção, metadados de runtime.

> **Invariantes: o backend WASM NÃO pode criar uma segunda semântica de
> gerenciamento de memória independente do Kof.** Se o GC atual não está
> pronto, isso é dependência explícita (§30), não motivo para inventar
> outro GC.

## 10. Strings

A representação futura de `String` em WASM deve **seguir a spec Kof
existente** (o que um `String` observa — length, indexação, comparação,
concatenação — é definido pela linguagem, não pelo backend). Cobrir:
decisão UTF-8 vs UTF-16 de armazenamento (**TBD — checar a semântica de
strings em `docs/` primeiro; não escolher arbitrariamente**), meaning de
length, allocation, concatenação, comparação (conteúdo `==`, congelada),
indexing/slicing, custo de conversão no host.

## 11. Records, objetos e classes

Mapeamento futuro a investigar: records (imutáveis — layout estático),
classes (campos mutáveis), interfaces, herança, **dispatch virtual**
(vtable? switch-on-type-tag? — **TBD**; o backend Native já escolheu o
esquema dele — estudá-lo). A semântica Kof deve ser preservada; abstrações
Kof **não** se traduzem automaticamente em abstrações JavaScript
(regras 8/9: Kof não é JS).

## 12. Closures

Implementação futura cobre: capture, environment, lifetime, allocation,
referência de função (calls indiretas via `table` WASM ou convenção
closure-record — **TBD**), closures aninhadas. **Seção de riscos**: closure
é o feature mais sensível a layout de memória — depende de §8/§9/§11
decididos em conjunto; a semântica de capture por referência deve bater
com os outros quatro targets (congelada — Freeze §6).

## 13. WASI

Superfície de integração: `stdin`, `stdout`, `stderr`, `arguments`,
`environ`, `clock`, `random`, `filesystem`; demais capabilities conforme a
versão escolhida.

```
Versão WASI: TBD — re-verificar o estado da spec upstream na hora de
implementar (era preview1 vs 0.2/component-model); nenhuma versão é
assumida aqui.
```

## 14. Capabilities

Integra com o sistema **CURRENT** (`supportedOn` por target + códigos
`XXX00x` + matriz `docs/backend-parity.md`). `WASM001` já existe no
`TargetMatrix` como rejeição honesta; a implementação precisará de uma
família de códigos por API não suportada (R6). Matriz futura — valores só
onde há decisão real (`✓ ~ - ?`):

```
                     JVM    Native  JS    WASM  WASI
strings/records      ✓      ✓       ✓      ?     ?
filesystem (kof.io)  ✓      ✓       -      -     ~
browser APIs         -      -       ✓      ~     -
clock                ✓      ✓       ✓      ?     ?
random               ✓      ✓       ✓      ?     ?
process (kof.process)✓      PROC001 ✓*     ?     ?
networking           ✓      ~       ?      -     ?
```

(`-` não aplicável / `~` parcial / `?` TBD — nada inventado; linha JS
`process` reflete `PROC001` em spawn/pipeline.)

> **Regra arquitetural (existente, R6): API indisponível num target deve
> produzir diagnóstico explícito quando conhecível em compile time. Sem
> silent stubs (Q7).**

## 15. Browser WASM

```
Kof → módulo WASM → host Browser → Web APIs (glue JS)
```

Distinto de KofJS: **KofJS** = target JavaScript (emissor em
`dev.kof.compiler.js`); **KofWASM** = target WebAssembly + host JS fino.
Bridge a documentar: DOM, eventos, `fetch`, storage, canvas, Web APIs —
cada uma é host integration, e cada gap voltado ao Kof ganha código (§14).
(Interage com `qrcode-wasm-plan.md` — plano de features QR+web — e com
`D-UI-SCOPE`: mudanças de regra do kof.ui pertencem a KofJS hoje e ao Kof
WebASM quando `Target.WASM` existir, nunca JVM/Native.)

## 16. Interop JavaScript

Separar **WASM core** (sem dependência de JS para rodar lógica Kof) de
**host integration específica de browser** (glue JS exigido pelo
browser). JS é *capability/host integration* quando necessário — nunca
dependência obrigatória para todo código Kof WASM. Mecanismo (gluegen?
wasm-bindgen-like? adaptador à mão?) — **TBD / DECISION REQUIRED**.

## 17. KofUI

```
KofUI
 ├── KofJS   (target UI web CURRENT)
 └── KofWASM (segundo target UI futuro)
```

Intenção: uma API conceitual de UI, hosts diferentes. **Não** duplicar a
API. Dividir: **abstração compartilhada** (intenta declarada — layout,
widgets, eventos) vs **implementação backend/host** (chamadas DOM, bridge
wasm). Regra 9 vale: código Kof declara intent; HTML/CSS nunca vaza para
código de usuário — WASM não é exceção.

## 18. WASI para aplicações (PLANNED)

CLI, servidores, edge, sandbox, plugins, embedded — futuros **possíveis**,
não garantias iniciais. WASI permite rodar Kof compilado fora do browser
em ambientes compatíveis; o que entra na primeira leva é escopo Fase 4+.

## 19. Host e runtime

`.wasm` é um **módulo**; rodar exige sempre um **host/runtime**. Registrar
para decisão (tudo **TBD / DECISION REQUIRED**):

```
runtime oficial de desenvolvimento: TBD
WASI runtime(s) suportados:         TBD (avaliar wasmtime/wasmer-like na
                                    hora; nenhuma ferramenta externa é
                                    adotada por esta doc)
descoberta de runtime (kof run):    TBD — diagnóstico honesto se ausente (R6),
                                    nunca fallback silencioso (Freeze §6)
```

> Kof não se acopla permanentemente a um runtime externo específico sem
> decisão arquitetural (R9 vale para *bibliotecas*, não para virar refém
> de um runtime).

## 20. Concorrência

Semântica congelada do Kof: `spawn`/`await` (Freeze §6). Análise futura:

- browser WASM: event loop single-threaded do host → mapeamento de `spawn`
  (**TBD**; worker threads = host-gated);
- `wasi-threads` / SharedArrayBuffer+atomics: não são WASM baseline —
  **TBD**, capability-gated;
- channels/scheduler sobre execução cooperativa;
- o modelo pthread de Native/JVM **não** pode ser assumido portável.

## 21. Networking

Documentar em separado, sem API falsa:

```
Browser WASM → fetch/WebSocket via bridge do host (glue JS)   PLANNED/TBD
WASI         → wasi-sockets quando spec+host suportarem        TBD
Native       → estado real atual (gaps na matriz)              CURRENT
JVM          → kof.http via JVM                                CURRENT
```

Registrar gaps explicitamente (R6) em vez de criar APIs que fingem andar.

## 22. Modules / imports

`import foo` continua sendo Kof independentemente do target — **nenhum
sistema de módulos exclusivo de WASM**. Questões futuras: link de múltiplos
módulos vs módulo único + `import`/`export`; fronteiras de módulo; o que
vira export WASM; dead-code elimination; empacotamento de dependências;
implicações do component-model (**TBD**).

## 23. CLI e build system

O build futuro deve tratar `wasm`/`wasi` como qualquer target: seleção,
output (`module.wasm` + glue do host — forma TBD), resolução de runtime,
linking, configuração de host, flags de capability. Marcar todo comando
como **proposta** (§5). O registro `koffing`/`deps` (CURRENT 1.5.x) ganha
empacotamento wasm-aware quando o ecossistema precisar — **TBD**.

## 24. WAT / debugging

Ferramentas futuras (PLANNED, sem promessa):

```bash
kof build --target=wasm --emit=wat   # proposta — forma texto p/ debug do backend
```

Pipeline de debug source-level (`Kof → WASM → debug info → devtools`):
suporte a DWARF/custom-name-section em WASM é **TBD**; o precedente de
source map V3 do backend JS (landado 01/09) é o modelo a estudar.

## 25. Conformance

Ligado à suíte futura de conformance (`docs/bugs-and-gaps/conformance-matrix.md`
é a matriz living CURRENT; layout `tests/conformance/{language,semantics,
stdlib,jvm,native,js,wasm,wasi}` é proposta):

> Validar **equivalência semântica** entre targets quando as
> **capabilities são equivalentes**. Nunca exigir binários idênticos.

## 26. Estratégia de testes

```
Compiler tests    : AST → IR → WASM (goldens de .wasm/.wat)
Validation tests  : módulo gerado passa num validator wasm
Runtime tests     : executa → stdout/exit esperados (golden medido — Q3)
WASI tests        : roda sob o host WASI escolhido → pins de comportamento
Cross-target      : mesmo programa → JVM/Native/JS/WASM(/WASI) quando as
                    capabilities permitem; divergência = gap diagnosticado
                    ou bug, nunca silêncio
```

Guardas honestas (`assumeTrue` + toolchain ausente ≠ red — §"Verification
loop").

## 27. Fuzzing

Fuzz/property testing é **obrigatório** para o backend WASM — gerar um
`.wasm` que *existe* não é gerar um `.wasm` *semanticamente correto*.
Superfícies prioritárias: control flow, **stack typing** (o validator WASM
é o adversário), assinaturas de função, locals, closures, ops de memória,
strings, object layout. Alimenta o mesmo programa de correctness já usado
no Native (família `ArrayBoundsDeepStressTest`).

## 28. Performance

Benchmarks futuros (lista, **sem promessa**): startup, custo de chamada,
aritmética inteira e float, loops, arrays, strings, alocação, JSON.
Comparar depois JVM / Native / JS / WASM / WASI com a tool de bench
existente (`BenchDiscovery` é CURRENT).

## 29. Segurança

Documentar: sandbox, capabilities, fronteiras do host, isolamento de
memória, acesso a filesystem, acesso a rede — WASI em especial.

> Meta de design: código Kof compilado para WASI não obtém acesso
> arbitrário ao SO senão através das capabilities declaradas do host.
> (R11: defesa primeiro — nada security-sensitive é caseiro neste backend.)

## 30. Dependências / pré-requisitos

Só o que a análise do código justifica:

| Dependência | Por quê | Status |
|---|---|---|
| GC Native concluído (roadmap G-1..G-5) | invariante §9 — uma semântica de memória | aberto |
| Estabilidade da IR | emissor WASM é consumidor novo da IR de lowering | SCRIPT já consome — risco baixo |
| `TargetMatrix`/capabilities | estender, não reinventar | CURRENT |
| Decisão D-UI-SCOPE | UI = KofJS/KofWebASM apenas | ratificada `95002c50` |
| Suíte de conformance | portão de claims de paridade | em evolução |
| Split da abstração KofUI (§17) | compartilhado vs host impl. | **TBD** |

## 31. Fases futuras

**Estado MEDIDO 21/10** (o cabeçalho `doing:` do plano acompanha as fatias): as
fases 1 e 4 estão PARCIALMENTE pousadas — `wasm/` tem 15 arquivos-fonte,
`kof build --target=wasm|wasi` emitem módulos reais, e o módulo RODA sob um host
WASI (`WasmWasiNodeHostTest` 2/2, sem skips). O subset é só-escalar:
records/coleções/exceções/lambdas são recusados com `WASM002`, que é a fronteira
que a tabela abaixo descreve como fases 2–3. O **flip de frontend 15.4** (a
precondição da última linha) segue pendente, então este plano NÃO está concluído.

Cada fase: objetivo · dependências · componentes · testes · critérios.

```
Fase 0  Arquitetura / ABI   — decidir forma da IR (§4), memória (§8), versão
        WASI (§13), runtime (§19); componentes: esta doc + entradas em
        DECISIONS.md; testes: nenhum (design); conclui quando cada TBD acima
        tiver decisão da mantenedora.
Fase 1  Backend WASM mínimo — código straight-line + funções + control flow;
        validation tests.
Fase 2  Runtime + memória   — allocator/layout conforme Fase 0; goldens de
        runtime num host.
Fase 3  Paridade de linguagem — records/classes/dispatch/closures/strings;
        goldens cross-target; fuzzing LIGADO.
Fase 4  WASI                — versão + host da Fase 0; linhas de capabilities
        da stdlib; gaps honestos no resto.
Fase 5  Host Browser        — glue (§16), bridge DOM/eventos (§15).
Fase 6  Integração KofUI    — segundo alvo do D-UI-SCOPE navega.
Fase 7  Conformance         — suíte §25 verde sobre as capabilities declaradas.
```

## 32. Release gates (para o flip 15.4 e um corte — o doc JÁ foi promovido em 07/10)

**Medido 21/10:** esta doc saiu de `docs/development/future/` para `docs/` em
07/10, portanto os gates abaixo não governam mais a mudança — governam o
**flip de frontend 15.4** e qualquer corte que dependa de WASI. Estado medível
aqui: backend real **CUMPRIDO** (15 arquivos-fonte em `wasm/`; `kof build
--target=wasi --output X` gera `Default/Main.wasm`); validator **NÃO VERIFICADO**
(`wasm-tools` ausente neste ambiente); colunas WASM/WASI da matriz
**NÃO FEITAS** (`backend-parity.md` ainda tem só `| Feature | JVM | Native |
KofJS | Notes |`). Gerar `.wasm` **não** é suficiente:

```
[ ] backend real (sem roundtrip JS/C — requisito §4)
[ ] módulos válidos (validator-clean)  [ ] runtime ok  [ ] esquema de memória
[ ] strings  [ ] paridade de linguagem (4 targets existentes ≡ WASM onde há
    capability)
[ ] WASI     [ ] linhas da matriz de capabilities honestas (sem silent stub
    — R6/Q7)
[ ] testes de execução + conformance verdes  [ ] docs (learn/, training/idioms)
    atualizadas
```

## 33. Relação com o roadmap

- A fila transversal de `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
  lista **WASM** como capacidade (série X) — esta doc é a spec profunda
  dela; a *ordem* obedece **R12** (estágio SYSTEMS fecha primeiro).
- `roadmap.md` §23 é o único plano ordenado — promoção de `future/` exige
  decisão da mantenedora registrada em `DECISIONS.md` (regra 6), que então
  cria a fila no §23.
- Dependências reais upstream: GC Native (G-1..G-5), maturidade da spec
  WASM/WASI (§13), host escolhido (§19) — sem lista artificial de features.

## 34. Registro de decisões

```
Decisão: backend direto (sem roundtrip JS/C)      Status: REQUISITO (esta doc,
        registrado 19/09; implementação ainda futura)
Decisão: código de gap WASM001 reservado          Status: CURRENT (TargetMatrix)
Decisão: D-UI-SCOPE (UI = KofJS/KofWebASM)        Status: DECIDIDA (95002c50)
Decisão: versão WASI                              Status: TBD
Decisão: forma do enum (WASM+WASI vs WASM+flag)   Status: TBD — DECISION REQUIRED
Decisão: representação ponteiro/GC-ref            Status: TBD — DECISION REQUIRED
Decisão: runtime oficial / hosts suportados       Status: TBD — DECISION REQUIRED
Decisão: mecanismo de glue JS                     Status: TBD
Decisão: modelo de concorrência em WASM           Status: TBD — spawn/await
        congelados (Freeze §6) o restringem (regra 6)
```

## 35. Organização dos arquivos

A tarefa de implementação propunha pasta `wasm-wasi/`; a **convenção do
repositório vence** (§35 da própria tarefa): `docs/development/future/` usa
pares planos EN + PT — este único par é o mínimo que mantém as 38 seções
endereçáveis. Se a feature for promovida, a divisão em `wasm-backend.md` /
`runtime.md` / `wasi.md` etc. acontece então, em `docs/`.

## 36. Documentos relacionados

- `qrcode-wasm-plan.md` / `.pt_BR.md` — estratégia de **features**
  QR + KofWasm (esta doc é o counterpart **backend** mais profundo).
- `DECOMPILER.md`, `TRANSLATOR.md` (nesta pasta) — a direção *reversa*
  (WASM → Kof), independente desta.
- `docs/backend-parity.md` — a matriz living que esta spec estende no
  futuro (nunca editar linhas de WASM antes de existir código — regra
  CURRENT: `WASM001` permanece a rejeição honesta).
- `docs/native-multiarch.md`, `docs/architecture/compiler-architecture.md`
  — precedentes de backend (cross-arch Native = o padrão de plumbing que
  entradas de Target seguem).

**Nenhuma implementação realizada. Apenas documentação.**
