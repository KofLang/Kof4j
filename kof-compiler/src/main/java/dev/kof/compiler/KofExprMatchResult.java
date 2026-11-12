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

    private final CompilerDriver driver;
    private final Type self;
    private final String entry;
    private final List<String> captures;
    private final int slots;
    private final Type recordType;

    private KofExprMatchResult(CompilerDriver driver, Type self, String entry,
                               List<String> captures, int slots, Type recordType) {
        this.driver = driver;
        this.self = self;
        this.entry = entry;
        this.captures = captures;
        this.slots = slots;
        this.recordType = recordType;
    }

    /** As classes de um padrão: o matcher e (se houver capturas) o record. */
    static List<IRClass> lower(CompilerDriver driver, ExprDeclarationNode ex, String packageName, int typeId) {
        String internalName = driver.toInternalName(packageName, ex.name());
        Type self = new Type.ClassType(packageName, ex.name(), List.of());
        List<String> captures = KofExprShape.captureNames(ex.body());
        int slots = KofExprShape.quantifierCount(ex.body());
        CompilerExprLowering lowering = new CompilerExprLowering(driver, self, slots, captures);
        if (!lowering.validate(ex.body())) return List.of();
        String accept = lowering.acceptState();
        String entry = lowering.compileUnits(KofExprShape.unitsOf(ex.body()), accept);
        boolean capturing = !captures.isEmpty();
        Type recordType = capturing
                ? new Type.ClassType(packageName, ex.name() + "Match", List.of()) : null;
        KofExprMatchResult surface = new KofExprMatchResult(driver, self, entry, captures, slots, recordType);

        List<IRClass> classes = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        methods.addAll(lowering.states);
        methods.add(surface.matches());
        methods.add(surface.match());
        methods.add(surface.captures());
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
        List<Type> params = pushEntryArgs(ops, SLOT_KEYS);
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
        List<Type> entryParams = pushEntryArgs(ops, SLOT_KEYS);
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
        List<Type> entryParams = pushEntryArgs(ops, SLOT_KEYS);
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

    // ---------------------------------------------------------------- apoio

    /**
     * Cria o canal de chaves (quando há capturas) e empilha os argumentos do
     * estado de entrada: (s, 0, contadores ZERO, ARRAY, inícios ZERO).
     * @return os tipos dos parâmetros do estado de entrada.
     */
    private List<Type> pushEntryArgs(List<KofOperation> ops, int keysSlot) {
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(KofLoadLiteral.ofInt(0));
        List<Type> params = new ArrayList<>(List.of(STR, INT));
        for (int i = 0; i < slots; i++) {
            ops.add(KofLoadLiteral.ofInt(0));
            params.add(INT);
        }
        if (!captures.isEmpty()) {
            // new String[M] — o canal escrito pelos estados da cadeia
            ops.add(KofLoadLiteral.ofInt(captures.size()));
            ops.add(new KofNewArray(STR));
            ops.add(new KofStoreLocal(new Type.ArrayType(STR), keysSlot));
            ops.add(new KofLoadLocal(new Type.ArrayType(STR), keysSlot));
            params.add(new Type.ArrayType(STR));
        }
        for (int i = 0; i < captures.size(); i++) {
            ops.add(KofLoadLiteral.ofInt(0));
            params.add(INT);
        }
        return params;
    }

    private static IRLocalVariable keysLocal() {
        return new IRLocalVariable(SLOT_KEYS, "keys", new Type.ArrayType(STR));
    }

    /** Os parâmetros do construtor do record: um String? por captura. */
    private List<Type> ctorParams() {
        List<Type> params = new ArrayList<>();
        for (int i = 0; i < captures.size(); i++) params.add(new Type.NullableType(STR));
        return params;
    }
}