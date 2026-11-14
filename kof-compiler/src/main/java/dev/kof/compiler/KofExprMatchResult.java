package dev.kof.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Kof.expr fatia 5 (`D-KOF-EXPR`) — a SUPERFÍCIE PÚBLICA de um padrão: as três
 * operações estáticas e, para padrões COM capturas, o record gerado da Opção T
 * (§6 do plano).
 *
 * <p>{@code matches(String): Bool} — sujeito inteiro; {@code match(String):
 * nameMatch? / String?} — o resultado tipado quando há capturas (o record com
 * um campo por captura, todos {@code String?}: uma captura fora do caminho
 * casado é null), o sujeito caso contrário; {@code captures(String):
 * List<String>?} — os valores na ordem de declaração.
 *
 * <p><b>O canal das chaves é um ARRAY compartilhado</b>: os métodos públicos
 * criam {@code new String[captures]}, passam-nhe como PARÂMETRO ao estado de
 * entrada e lêem-no depois do sucesso — as capturas são escritas pelos estados
 * DENTRO da cadeia ({@code keys[i] = substring(start, pos)} no retorno de cada
 * corpo), que não têm como devolvê-las por outros canais. O record é emitido
 * pelo lowering de record EXISTENTE (o precedente entity: um
 * {@link RecordDeclarationNode} sintético), então toString/equals e o acesso a
 * campo são os mesmos de qualquer record Kof. Extraído de
 * {@code CompilerExprLowering} pelo ratchet de 500 linhas.
 */
final class KofExprMatchResult {

    static final Type STR = BuiltinTypes.STRING;
    static final Type INT = Type.PrimitiveType.INT;
    static final Type BOOL = Type.PrimitiveType.BOOL;
    static final int SLOT_S = 0;
    static final int SLOT_POS = 1;
    static final int SLOT_KEYS = 2;
    static final int SLOT_END = 1;
    /**
     * O orçamento do §12 (v1 documentado, MEDIDO POR EXECUTOR). O matcher v1
     * é CPS recursivo — a profundidade é O(sujeito), ~3 frames por caractere
     * — e a pilha segura DIFERE: JVM 2,000 (4,000 estoura); o interpretador
     * Script ~200 (400 estoura o frame dele, medido). O driver.interpreting é
     * conhecido no tempo de lower, então o valor e a mensagem são CONSTANTES
     * da compilação. O motor iterativo (profundidade O(1)) é o item restante
     * da fatia 8.
     */
    static final int JVM_BUDGET = 2_000;
    static final int SCRIPT_BUDGET = 200;
    /** Locais dos métodos públicos de busca (find/findAll/replace). */
    static final int SLOT_SEARCH_A = 3;
    static final int SLOT_SEARCH_B = 4;

    final CompilerDriver driver;
    final Type self;
    final String entry;
    final String entryFree;
    final List<String> captures;
    final int slots;
    final Type recordType;

    private KofExprMatchResult(CompilerDriver driver, Type self, String entry, String entryFree,
                               List<String> captures, int slots, Type recordType) {
        this.driver = driver;
        this.self = self;
        this.entry = entry;
        this.entryFree = entryFree;
        this.captures = captures;
        this.slots = slots;
        this.recordType = recordType;
    }

    /** As classes de um padrão: o matcher e (se houver capturas) o record. */
    static List<IRClass> lower(CompilerDriver driver, ExprDeclarationNode ex, String packageName, int typeId) {
        // Fatia 7 MEDIDA: o gate é da DECLARAÇÃO (não só dos atoms de classe) —
        // o MATCHER inteiro baixa em formas que o reconstrutor JS não aceita
        // (um KofJump solto lê como break/continue; KofExprEither estoura
        // COMP002 num padrão só-de-literal).
        //
        // Fatia 6 (D-KOF-EXPR): o NATIVO x86-64 ENTROU — o backend nativo é um
        // emissor 1:1 op→instrução e TODOS os ops que a cadeia de estados emite
        // estão declarados em NativeOpSupport (labels/jumps/cjumps/calls/
        // arrays/records), então a MESMA cadeia compilada roda no nativo sem
        // nenhum trabalho de parser (a diferença do JS). O runtime traz os
        // predicados de classe (RuntimeExpr) e a leitura de ponto de código.
        // Fatia 7 (14/11): o gate JS FECHOU — o estado de laço reestruturado
        // em três estados de if único + o predicado de classe reparado
        // (kofExprInRange lia a tabela como array-de-chars) fizeram o corpus
        // §3.4 inteiro responder byte-idêntico ao oráculo JVM no artefato
        // node (as 7 faces obrigatórias + os quantificadores/capturas/busca).
        String internalName = driver.toInternalName(packageName, ex.name());
        Type self = new Type.ClassType(packageName, ex.name(), List.of());
        CompilerExprLowering lowering = new CompilerExprLowering(driver, self, 0, List.of());
        // a composição por referência (o §3.4 ipv4): a árvore é RESOLVIDA
        // primeiro — as referências viram os corpos dos referidos IN LÍNEA —
        // e TUDO depois (capturas, contagem de quantificadores, validação,
        // emissão) opera sobre a árvore expandida. Sem isso o contador do
        // quantificador inlinado cai num slot não declarado (medido:
        // VerifyError Bad local variable type em ipv4.kx3).
        ExprNode body = KofExprReferences.resolveReferences(driver, lowering, ex.body(),
                new ArrayDeque<>(List.of(ex.name())));
        if (body == null) return List.of();
        List<String> captures = KofExprShape.captureNames(body);
        int slots = KofExprShape.quantifierCount(body);
        lowering.resetSlotsTo(slots, captures);
        if (!lowering.validate(body)) return List.of();
        String accept = lowering.acceptState();
        String freeAccept = lowering.freeAcceptState();
        String entry = lowering.compileUnits(KofExprShape.unitsOf(body), accept);
        // fatia 8: a SEGUNDA cadeia — as mesmas unidades terminando no aceite
        // livre (as buscas definem a ocorrência pelo [start, end)). O
        // alocador de slots RESETA: a mesma árvore tem de mapear os mesmos
        // quantificadores para os MESMOS slots nas duas cadeias.
        lowering.resetSlots();
        String entryFree = lowering.compileUnits(KofExprShape.unitsOf(body), freeAccept);
        boolean capturing = !captures.isEmpty();
        Type recordType = capturing
                ? new Type.ClassType(packageName, ex.name() + "Match", List.of()) : null;
        KofExprMatchResult surface = new KofExprMatchResult(driver, self, entry, entryFree,
                captures, slots, recordType);
        KofExprSearch search = new KofExprSearch(surface);

        List<IRClass> classes = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        methods.addAll(lowering.states);
        methods.add(surface.matches());
        methods.add(surface.match());
        methods.add(surface.captures());
        methods.add(search.find());
        methods.add(search.findAll());
        methods.add(search.replace());
        int access = driver.computeAccess(ex.modifiers()) | AccessFlags.FINAL;
        classes.add(new IRClass(internalName, "java/lang/Object", List.of(), access,
                List.of(), methods, List.of(), null, typeId,
                CompilerAnnotations.lowerAnnotations(driver, ex.annotations())));
        if (capturing) {
            classes.add(surface.recordClass(ex, packageName, typeId + 1000));
        }
        return classes;
    }

    /** O record da Opção T: um componente String? por captura, pela via entity. */
    private IRClass recordClass(ExprDeclarationNode ex, String packageName, int typeId) {
        List<RecordComponentNode> components = new ArrayList<>();
        for (String cap : captures) {
            components.add(new RecordComponentNode(ex.position(), List.of(), "String?", cap, null));
        }
        return CompilerIfaceRecordLowering.lowerRecord(driver, new RecordDeclarationNode(
                        ex.position(), ex.name() + "Match", List.of(), null, List.of(),
                        components, List.of()),
                packageName, typeId);
    }

    // ---------------------------------------------------------------- operações

    /** {@code matches(String): Bool} — cria o canal de chaves e roda a cadeia. */
    private IRMethod matches() {
        LabelId pass = LabelId.create();
        LabelId fail = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        emitKeysArray(ops, SLOT_KEYS);
        List<Type> params = pushChainArgs(ops, SLOT_KEYS, -1);
        ops.add(new KofCall(self, entry, params, INT, KofCallKind.STATIC));
        ops.add(new KofStoreLocal(INT, SLOT_END));
        ops.add(new KofLoadLocal(INT, SLOT_END));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofConditionalJump(KofComparison.NE, INT, pass, fail));
        ops.add(new KofLabel(pass));
        ops.add(KofLoadLiteral.ofBool(true));
        ops.add(new KofReturn(BOOL));
        ops.add(new KofLabel(fail));
        ops.add(KofLoadLiteral.ofBool(false));
        ops.add(new KofReturn(BOOL));
        List<IRLocalVariable> locals = new ArrayList<>(List.of(
                new IRLocalVariable(SLOT_S, "s", STR),
                new IRLocalVariable(SLOT_END, "end", INT)));
        if (!captures.isEmpty()) locals.add(keysLocal());
        return new IRMethod("matches", BOOL, List.of(STR),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /** {@code match(String)} — o record tipado (ou o sujeito) quando casa; null quando não. */
    private IRMethod match() {
        LabelId absent = LabelId.create();
        LabelId present = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        // roda a cadeia com o SEU canal: o array do matches é de outro frame
        emitKeysArray(ops, SLOT_KEYS);
        List<Type> entryParams = pushChainArgs(ops, SLOT_KEYS, -1);
        ops.add(new KofCall(self, entry, entryParams, INT, KofCallKind.STATIC));
        ops.add(new KofStoreLocal(INT, SLOT_END));
        ops.add(new KofLoadLocal(INT, SLOT_END));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofConditionalJump(KofComparison.NE, INT, present, absent));
        ops.add(new KofLabel(present));
        if (recordType != null) {
            // o record da Opção T: um campo String? por captura, lido do canal
            ops.add(new KofNewObject(recordType, ctorParams()));
            ops.add(new KofDup());
            for (int i = 0; i < captures.size(); i++) {
                ops.add(new KofLoadLocal(new Type.ArrayType(STR), SLOT_KEYS));
                ops.add(KofLoadLiteral.ofInt(i));
                ops.add(new KofArrayLoad(STR));
            }
            ops.add(new KofCall(recordType, "<init>", ctorParams(),
                    Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            ops.add(new KofReturn(recordType));
        } else {
            ops.add(new KofLoadLocal(STR, SLOT_S));
            ops.add(new KofReturn(STR));
        }
        ops.add(new KofLabel(absent));
        ops.add(KofLoadLiteral.ofNull());
        if (recordType != null) {
            ops.add(new KofReturn(recordType));
        } else {
            ops.add(new KofReturn(STR));
        }
        // fatia JS: o slot END (1) é gravado/lido aqui — o backend JS exige a
        // declaração do local (a JVM tolera: a tabela de locals é debug-info).
        List<IRLocalVariable> locals = new ArrayList<>(List.of(
                new IRLocalVariable(SLOT_S, "s", STR),
                new IRLocalVariable(SLOT_END, "end", INT)));
        if (!captures.isEmpty()) locals.add(keysLocal());
        return new IRMethod("match", recordType != null ? new Type.NullableType(recordType)
                        : new Type.NullableType(STR),
                List.of(STR), AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /** {@code captures(String): List<String>?} — os valores na ordem de declaração. */
    private IRMethod captures() {
        Type listType = new Type.ClassType("kof", "List", List.of(STR));
        LabelId absent = LabelId.create();
        LabelId present = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        // roda a cadeia com o SEU canal (o array do matches é de outro frame)
        emitKeysArray(ops, SLOT_KEYS);
        List<Type> entryParams = pushChainArgs(ops, SLOT_KEYS, -1);
        ops.add(new KofCall(self, entry, entryParams, INT, KofCallKind.STATIC));
        ops.add(new KofStoreLocal(INT, SLOT_END));
        ops.add(new KofLoadLocal(INT, SLOT_END));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofConditionalJump(KofComparison.NE, INT, present, absent));
        ops.add(new KofLabel(present));
        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
        ops.add(new KofStoreLocal(listType, SLOT_END));
        for (int i = 0; i < captures.size(); i++) {
            ops.add(new KofLoadLocal(listType, SLOT_END));
            ops.add(new KofLoadLocal(new Type.ArrayType(STR), SLOT_KEYS));
            ops.add(KofLoadLiteral.ofInt(i));
            ops.add(new KofArrayLoad(STR));
            ops.add(new KofCall(listType, "kof_list_add", List.of(STR),
                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
        }
        ops.add(new KofLoadLocal(listType, SLOT_END));
        ops.add(new KofReturn(listType));
        ops.add(new KofLabel(absent));
        ops.add(KofLoadLiteral.ofNull());
        ops.add(new KofReturn(listType));
        List<IRLocalVariable> locals = new ArrayList<>(List.of(
                new IRLocalVariable(SLOT_S, "s", STR),
                new IRLocalVariable(SLOT_END, "list", listType)));
        if (!captures.isEmpty()) locals.add(keysLocal());
        return new IRMethod("captures", new Type.NullableType(listType), List.of(STR),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    // ---------------------------------------------------------------- busca (fatia 8, §6/§7)

    /**
     * O orçamento do §12 (v1 documentado, MEDIDO): 2,000 caracteres por
     * sujeito. O matcher v1 é CPS recursivo — a profundidade é O(sujeito),
     * ~3 frames por caractere — e 4,000 estouram a pilha padrão da JVM
     * (medido: 1,000 e 2,000 passam, 4,000 estoura). O motor iterativo
     * (profundidade O(1)) é o item restante da fatia 8.
     */
    private static final int SUBJECT_BUDGET = 2_000;

    void emitBudget(List<KofOperation> ops) {
        LabelId ok = LabelId.create();
        LabelId bad = LabelId.create();
        // §267: o marcador de if de STATEMENT (antes da condição) — sem ele o
        // dispatcher JS tenta a dobra em ternário e o ramo do throw se perde
        // (medido: `if (len > 2000) { } else { … }` com o throw sumido e o
        // find devolvendo undefined num sujeito de 2100 caracteres).
        ops.add(new KofStatementIf(bad));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
        ops.add(KofLoadLiteral.ofInt(driver.interpreting ? SCRIPT_BUDGET : JVM_BUDGET));
        ops.add(new KofConditionalJump(KofComparison.GT, INT, bad, ok));
        ops.add(new KofLabel(bad));
        String bound = driver.interpreting
                ? "200 characters on the Script interpreter (frame-bound tighter than the JVM)"
                : "2,000 characters on the JVM";
        String message = "EXPR-E2: the Kof.expr subject is above the documented "
                + "budget of " + bound + " (the recursive v1 matcher is stack-bound)";
        if (driver.target.isNative() || driver.target == Target.JS) {
            // O nativo não tem java.lang.RuntimeException, e no JS o construtor
            // java_lang_RuntimeException não existe (medido: ReferenceError —
            // a mensagem EXPR-E2 se perdia). O `throw` de Kof é uma STRING em
            // ambos (o mesmo caminho de `throw "msg"`): o texto do orçamento
            // viaja na pilha e chega ao programa.
            ops.add(KofLoadLiteral.ofString(message));
            ops.add(new KofThrow());
        } else {
            Type rte = new Type.ClassType("java.lang", "RuntimeException", List.of());
            ops.add(new KofNewObject(rte, List.of(STR)));
            ops.add(new KofDup());
            ops.add(KofLoadLiteral.ofString(message));
            ops.add(new KofCall(rte, "<init>", List.of(STR), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            ops.add(new KofThrow());
        }
        ops.add(new KofLabel(ok));
    }

    /**
     * Cria o canal de chaves e empilha os argumentos do estado de entrada
     * começando em {@code startPosLocal} (0 para os casamentos de sujeito
     * inteiro; o índice corrente para as buscas).
     */
    List<Type> pushChainArgs(List<KofOperation> ops, int keysSlot, int startPosLocal) {
        ops.add(new KofLoadLocal(STR, SLOT_S));
        if (startPosLocal >= 0) {
            ops.add(new KofLoadLocal(INT, startPosLocal));
        } else {
            ops.add(KofLoadLiteral.ofInt(0));
        }
        List<Type> params = new ArrayList<>(List.of(STR, INT));
        for (int i = 0; i < slots; i++) {
            ops.add(KofLoadLiteral.ofInt(0));
            params.add(INT);
        }
        if (!captures.isEmpty()) {
            ops.add(new KofLoadLocal(new Type.ArrayType(STR), keysSlot));
            params.add(new Type.ArrayType(STR));
        }
        for (int i = 0; i < captures.size(); i++) {
            ops.add(KofLoadLiteral.ofInt(0));
            params.add(INT);
        }
        return params;
    }

    void emitKeysArray(List<KofOperation> ops, int keysSlot) {
        if (captures.isEmpty()) return;
        ops.add(KofLoadLiteral.ofInt(captures.size()));
        ops.add(new KofNewArray(STR));
        ops.add(new KofStoreLocal(new Type.ArrayType(STR), keysSlot));
    }

    /** O elemento de uma ocorrência: o trecho casado (sem captura) ou o record tipado (com captura). */
    void emitOccurrence(List<KofOperation> ops, Type elemType, int keysSlot, int startSlot, int endSlot) {
        if (!captures.isEmpty()) {
            ops.add(new KofNewObject(elemType, ctorParams()));
            ops.add(new KofDup());
            for (int i = 0; i < captures.size(); i++) {
                ops.add(new KofLoadLocal(new Type.ArrayType(STR), keysSlot));
                ops.add(KofLoadLiteral.ofInt(i));
                ops.add(new KofArrayLoad(STR));
            }
            ops.add(new KofCall(elemType, "<init>", ctorParams(),
                    Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            return;
        }
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofLoadLocal(INT, startSlot));
        ops.add(new KofLoadLocal(INT, endSlot));
        ops.add(new KofCall(STR, "substring", List.of(INT, INT), STR, KofCallKind.INSTANCE));
    }

    private static IRLocalVariable keysLocal() {
        return new IRLocalVariable(SLOT_KEYS, "keys", new Type.ArrayType(STR));
    }

    /** Os locais comuns (s + extras) + o canal de chaves quando há capturas. */
    List<IRLocalVariable> localsFor(IRLocalVariable... extra) {
        List<IRLocalVariable> locals = new ArrayList<>(List.of(
                new IRLocalVariable(SLOT_S, "s", STR)));
        if (!captures.isEmpty()) locals.add(keysLocal());
        for (IRLocalVariable v : extra) locals.add(v);
        return locals;
    }

    /** Os parâmetros do construtor do record: um String? por captura. */
    private List<Type> ctorParams() {
        List<Type> params = new ArrayList<>();
        for (int i = 0; i < captures.size(); i++) params.add(new Type.NullableType(STR));
        return params;
    }
}