package dev.kof.compiler;

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

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final int SLOT_S = 0;
    private static final int SLOT_POS = 1;
    private static final int SLOT_KEYS = 2;
    private static final int SLOT_END = 1;
    /** Locais dos métodos públicos de busca (find/findAll/replace). */
    private static final int SLOT_SEARCH_A = 3;
    private static final int SLOT_SEARCH_B = 4;

    private final CompilerDriver driver;
    private final Type self;
    private final String entry;
    private final String entryFree;
    private final List<String> captures;
    private final int slots;
    private final Type recordType;

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
        // COMP002 num padrão só-de-literal). O NATIVO entra com a fatia 6, a
        // frente da lane nativa viva.
        Target t = driver.target;
        if (!driver.interpreting && (t == Target.JS || t == Target.NATIVE)) {
            SourcePosition p = ex.position();
            String where = t == Target.JS ? "JS" : "Native";
            String why = t == Target.JS
                    ? "the state chain lowers to raw jumps/labels the JS structured-flow parser does not reconstruct yet"
                    : "the native runtime predicates enter with slice 6";
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0,
                        p != null ? p.length() : 0,
                        "the Kof.expr matcher is not on the " + where + " target yet: " + why
                                + " (EXPR004)", "EXPR004");
            }
            return List.of();
        }
        String internalName = driver.toInternalName(packageName, ex.name());
        Type self = new Type.ClassType(packageName, ex.name(), List.of());
        List<String> captures = KofExprShape.captureNames(ex.body());
        int slots = KofExprShape.quantifierCount(ex.body());
        CompilerExprLowering lowering = new CompilerExprLowering(driver, self, slots, captures);
        if (!lowering.validate(ex.body())) return List.of();
        String accept = lowering.acceptState();
        String freeAccept = lowering.freeAcceptState();
        String entry = lowering.compileUnits(KofExprShape.unitsOf(ex.body()), accept);
        // fatia 8: a SEGUNDA cadeia — as mesmas unidades terminando no aceite
        // livre (as buscas definem a ocorrência pelo [start, end)). O
        // alocador de slots RESETA: a mesma árvore tem de mapear os mesmos
        // quantificadores para os MESMOS slots nas duas cadeias.
        lowering.resetSlots();
        String entryFree = lowering.compileUnits(KofExprShape.unitsOf(ex.body()), freeAccept);
        boolean capturing = !captures.isEmpty();
        Type recordType = capturing
                ? new Type.ClassType(packageName, ex.name() + "Match", List.of()) : null;
        KofExprMatchResult surface = new KofExprMatchResult(driver, self, entry, entryFree,
                captures, slots, recordType);

        List<IRClass> classes = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        methods.addAll(lowering.states);
        methods.add(surface.matches());
        methods.add(surface.match());
        methods.add(surface.captures());
        methods.add(surface.find());
        methods.add(surface.findAll());
        methods.add(surface.replace());
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
        ops.add(new KofConditionalJump(KofComparison.EQ, INT, absent, present));
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
        List<IRLocalVariable> locals = new ArrayList<>(List.of(
                new IRLocalVariable(SLOT_S, "s", STR)));
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
        ops.add(new KofConditionalJump(KofComparison.EQ, INT, absent, present));
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

    private void emitBudget(List<KofOperation> ops) {
        LabelId ok = LabelId.create();
        LabelId bad = LabelId.create();
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
        ops.add(KofLoadLiteral.ofInt(SUBJECT_BUDGET));
        ops.add(new KofConditionalJump(KofComparison.LE, INT, ok, bad));
        ops.add(new KofLabel(bad));
        Type rte = new Type.ClassType("java.lang", "RuntimeException", List.of());
        ops.add(new KofNewObject(rte, List.of(STR)));
        ops.add(new KofDup());
        ops.add(KofLoadLiteral.ofString("EXPR-E2: the Kof.expr subject is above the documented "
                + "budget of 2,000 characters (the recursive v1 matcher is stack-bound)"));
        ops.add(new KofCall(rte, "<init>", List.of(STR), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        ops.add(new KofThrow());
        ops.add(new KofLabel(ok));
    }

    /**
     * Cria o canal de chaves e empilha os argumentos do estado de entrada
     * começando em {@code startPosLocal} (0 para os casamentos de sujeito
     * inteiro; o índice corrente para as buscas).
     */
    private List<Type> pushChainArgs(List<KofOperation> ops, int keysSlot, int startPosLocal) {
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

    private void emitKeysArray(List<KofOperation> ops, int keysSlot) {
        if (captures.isEmpty()) return;
        ops.add(KofLoadLiteral.ofInt(captures.size()));
        ops.add(new KofNewArray(STR));
        ops.add(new KofStoreLocal(new Type.ArrayType(STR), keysSlot));
    }

    /** O elemento de uma ocorrência: o trecho casado (sem captura) ou o record tipado (com captura). */
    private void emitOccurrence(List<KofOperation> ops, Type elemType, int keysSlot, int startSlot, int endSlot) {
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

    /** {@code find(String): Int} — o primeiro índice de início de ocorrência, -1 quando ausente. */
    private IRMethod find() {
        LabelId loop = LabelId.create();
        LabelId tryOne = LabelId.create();
        LabelId hit = LabelId.create();
        LabelId miss = LabelId.create();
        LabelId fail = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        emitBudget(ops);
        emitKeysArray(ops, SLOT_KEYS);
        ops.add(KofLoadLiteral.ofInt(0));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofLabel(loop));
        // start > len(s)? → fail
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
        ops.add(new KofConditionalJump(KofComparison.GT, INT, fail, tryOne));
        ops.add(new KofLabel(tryOne));
        List<Type> params = pushChainArgs(ops, SLOT_KEYS, SLOT_SEARCH_A);
        ops.add(new KofCall(self, entryFree, params, INT, KofCallKind.STATIC));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_B));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_B));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofConditionalJump(KofComparison.NE, INT, hit, miss));
        ops.add(new KofLabel(hit));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofReturn(INT));
        ops.add(new KofLabel(miss));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(KofLoadLiteral.ofInt(1));
        ops.add(new KofBinary(KofBinaryOp.ADD, INT));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofJump(loop));
        ops.add(new KofLabel(fail));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofReturn(INT));
        List<IRLocalVariable> locals = localsFor(
                new IRLocalVariable(SLOT_SEARCH_A, "start", INT),
                new IRLocalVariable(SLOT_SEARCH_B, "end", INT));
        return new IRMethod("find", INT, List.of(STR),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * {@code findAll(String)} — as ocorrências em ordem: os TRECHOS casados
     * (List<String>) num padrão sem captura, o record tipado
     * (List<nameMatch>) num padrão com captura; null quando nenhuma. Um
     * casamento vazio avança 1 (sem laço infinito).
     */
    private IRMethod findAll() {
        Type elemType = captures.isEmpty() ? STR : recordType;
        Type listType = new Type.ClassType("kof", "List", List.of(elemType));
        LabelId loop = LabelId.create();
        LabelId tryOne = LabelId.create();
        LabelId hit = LabelId.create();
        LabelId miss = LabelId.create();
        LabelId emptyAdv = LabelId.create();
        LabelId nonEmpty = LabelId.create();
        LabelId done = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        emitBudget(ops);
        emitKeysArray(ops, SLOT_KEYS);
        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
        ops.add(new KofStoreLocal(listType, 1));
        ops.add(KofLoadLiteral.ofInt(0));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofLabel(loop));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
        ops.add(new KofConditionalJump(KofComparison.GT, INT, done, tryOne));
        ops.add(new KofLabel(tryOne));
        List<Type> params = pushChainArgs(ops, SLOT_KEYS, SLOT_SEARCH_A);
        ops.add(new KofCall(self, entryFree, params, INT, KofCallKind.STATIC));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_B));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_B));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofConditionalJump(KofComparison.EQ, INT, miss, hit));
        ops.add(new KofLabel(hit));
        ops.add(new KofLoadLocal(listType, 1));
        emitOccurrence(ops, elemType, SLOT_KEYS, SLOT_SEARCH_A, SLOT_SEARCH_B);
        ops.add(new KofCall(listType, "kof_list_add", List.of(elemType),
                Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
        // avanço: casamento vazio anda 1; casamento normal salta além de si
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_B));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofConditionalJump(KofComparison.GT, INT, nonEmpty, emptyAdv));
        ops.add(new KofLabel(emptyAdv));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(KofLoadLiteral.ofInt(1));
        ops.add(new KofBinary(KofBinaryOp.ADD, INT));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofJump(loop));
        ops.add(new KofLabel(nonEmpty));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_B));
        ops.add(KofLoadLiteral.ofInt(1));
        ops.add(new KofBinary(KofBinaryOp.ADD, INT));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofJump(loop));
        ops.add(new KofLabel(miss));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(KofLoadLiteral.ofInt(1));
        ops.add(new KofBinary(KofBinaryOp.ADD, INT));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofJump(loop));
        ops.add(new KofLabel(done));
        ops.add(new KofLoadLocal(listType, 1));
        ops.add(new KofReturn(listType));
        List<IRLocalVariable> locals = localsFor(
                new IRLocalVariable(1, "list", listType),
                new IRLocalVariable(SLOT_SEARCH_A, "start", INT),
                new IRLocalVariable(SLOT_SEARCH_B, "end", INT));
        return new IRMethod("findAll", new Type.NullableType(listType), List.of(STR),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * {@code replace(String, String): String} — substituição LITERAL (§7):
     * cada ocorrência vira o texto de reposição; o sujeito sem casamento
     * volta inalterado. Locais: 1 = resultado, 2 = reposição, A = início,
     * B = fim, 5 = copyFrom, 6 = resultado não — 1 e 6 são o MESMO resultado:
     * layout final: 1 = reposição, A = início, B = fim/copyFrom, 5 = resultado.
     */
    private IRMethod replace() {
        LabelId loop = LabelId.create();
        LabelId tryOne = LabelId.create();
        LabelId hit = LabelId.create();
        LabelId miss = LabelId.create();
        LabelId emptyAdv = LabelId.create();
        LabelId nonEmpty = LabelId.create();
        LabelId tail = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        emitBudget(ops);
        emitKeysArray(ops, SLOT_KEYS);
        ops.add(KofLoadLiteral.ofString(""));
        ops.add(new KofStoreLocal(STR, 5));
        ops.add(KofLoadLiteral.ofInt(0));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(KofLoadLiteral.ofInt(0));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_B));
        ops.add(KofLoadLiteral.ofInt(0));
        ops.add(new KofStoreLocal(INT, 6));
        ops.add(new KofLabel(loop));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
        ops.add(new KofConditionalJump(KofComparison.GT, INT, tail, tryOne));
        ops.add(new KofLabel(tryOne));
        List<Type> params = pushChainArgs(ops, SLOT_KEYS, SLOT_SEARCH_A);
        ops.add(new KofCall(self, entryFree, params, INT, KofCallKind.STATIC));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_B));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_B));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofConditionalJump(KofComparison.EQ, INT, miss, hit));
        ops.add(new KofLabel(hit));
        // result(5) = result(5) + s.substring(copyFrom(B), start(A)) + replacement(2)
        ops.add(new KofLoadLocal(STR, 5));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofLoadLocal(INT, 6));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofCall(STR, "substring", List.of(INT, INT), STR, KofCallKind.INSTANCE));
        ops.add(new KofCall(STR, "kof_string_concat", List.of(STR, STR), STR, KofCallKind.FUNCTION));
        ops.add(new KofLoadLocal(STR, 1));
        ops.add(new KofCall(STR, "kof_string_concat", List.of(STR, STR), STR, KofCallKind.FUNCTION));
        ops.add(new KofStoreLocal(STR, 5));
        // copyFrom(6) = end da ocorrência
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_B));
        ops.add(new KofStoreLocal(INT, 6));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_B));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofConditionalJump(KofComparison.GT, INT, nonEmpty, emptyAdv));
        ops.add(new KofLabel(emptyAdv));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(KofLoadLiteral.ofInt(1));
        ops.add(new KofBinary(KofBinaryOp.ADD, INT));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofJump(loop));
        ops.add(new KofLabel(nonEmpty));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_B));
        ops.add(KofLoadLiteral.ofInt(1));
        ops.add(new KofBinary(KofBinaryOp.ADD, INT));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofJump(loop));
        ops.add(new KofLabel(miss));
        ops.add(new KofLoadLocal(INT, SLOT_SEARCH_A));
        ops.add(KofLoadLiteral.ofInt(1));
        ops.add(new KofBinary(KofBinaryOp.ADD, INT));
        ops.add(new KofStoreLocal(INT, SLOT_SEARCH_A));
        ops.add(new KofJump(loop));
        ops.add(new KofLabel(tail));
        // result(5) = result(5) + s.substring(copyFrom(6), len)
        ops.add(new KofLoadLocal(STR, 5));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofLoadLocal(INT, 6));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
        ops.add(new KofCall(STR, "substring", List.of(INT, INT), STR, KofCallKind.INSTANCE));
        ops.add(new KofCall(STR, "kof_string_concat", List.of(STR, STR), STR, KofCallKind.FUNCTION));
        ops.add(new KofReturn(STR));
        List<IRLocalVariable> locals = localsFor(
                new IRLocalVariable(1, "replacement", STR),
                new IRLocalVariable(SLOT_SEARCH_A, "start", INT),
                new IRLocalVariable(SLOT_SEARCH_B, "end", INT),
                new IRLocalVariable(5, "result", STR),
                new IRLocalVariable(6, "copyFrom", INT));
        return new IRMethod("replace", STR, List.of(STR, STR),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    private static IRLocalVariable keysLocal() {
        return new IRLocalVariable(SLOT_KEYS, "keys", new Type.ArrayType(STR));
    }

    /** Os locais comuns (s + extras) + o canal de chaves quando há capturas. */
    private List<IRLocalVariable> localsFor(IRLocalVariable... extra) {
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