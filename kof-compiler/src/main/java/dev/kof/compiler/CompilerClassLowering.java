package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de declarações de classe/interface/record para IRClass/IRMethod.
 */
public final class CompilerClassLowering {

    private CompilerClassLowering() {}

    static IRClass lowerClass(CompilerDriver driver, ClassDeclarationNode cls,
                        String packageName, int typeId) {
        String internalName = driver.toInternalName(packageName, cls.name());
        // super registry-first (§308/§354): o nome armazenado pode ser SIMPLES
        // ("Shape" — wildcard não qualifica) com a classe em package, e emitir
        // o cru dava NoClassDefFoundError: Shape; externo pontuado segue o caminho antigo.
        String superName = HierarchyResolver.canonicalSuperOf(
                driver.semanticAnalyzer, cls.name());
        if (superName == null) {
            String rawSuper = eraseTypeArgs(cls.superClass());
            superName = rawSuper != null ? driver.toInternalName("", rawSuper)
                    : "java/lang/Object";
        }
        var sup = ClassSuperResolution.resolve(driver, superName, cls.interfaces().stream().map(n -> CompilerAnnotations.externalOrLocalInternalName(driver, HeritageQualifier.qualify(driver.currentUnit, driver.semanticAnalyzer, eraseTypeArgs(n)))).toList()); superName = sup.superName(); List<String> ifaces = sup.interfaces();
        int access = driver.computeAccess(cls.modifiers());
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        java.util.Map<String, ExpressionNode> fieldInits = new java.util.LinkedHashMap<>();
        // #133 (issue do colaborador, §186): inicializadores de campos
        // ESTÁTICOS com expressão não-constante não têm lugar no atributo
        // ConstantValue do class file — precisam de um <clinit>. O código
        // antigo jogava TODOs os inicializadores em `fieldInits` (que só o
        // <init> de instância executa, como putfield) — o <clinit> jamais
        // existiu: `static Int[] shared = new Int[3]` ficava null/undefined,
        // `static Int x = compute()` ficava 0, silenciosamente (viola R6).
        // Em putfield num campo static era AINDA pior: bytecode inválido
        // (VerifyError) assim que a classe fosse instanciada. Separa por
        // STATIC e sintetiza o <clinit> na ordem de declaração.
        java.util.Map<String, ExpressionNode> staticFieldInits = new java.util.LinkedHashMap<>();
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) {
                IRField irField = CompilerClassLowering.lowerField(driver,field, cls.typeParameters());
                fields.add(irField);
                if (field.initializer() != null && irField.initialValue() == null) {
                    // UIW052/#133: inicializador NÃO-constante. Um campo
                    // ESTÁTICO não pode ser atribuído no <init> de instância
                    // (virava `this.x = ...` → PUTFIELD num campo estático =
                    // IncompatibleClassChangeError no JVM; no-op no Native).
                    // Vai para o <clinit> sintetizado abaixo; só o campo de
                    // instância vai para fieldInits (run no <init>).
                    if ((irField.accessFlags() & AccessFlags.STATIC) != 0) {
                        staticFieldInits.put(field.name(), field.initializer());
                    } else {
                        fieldInits.put(field.name(), field.initializer());
                    }
                }
            } else if (member instanceof MethodDeclarationNode method) {
                methods.add(CompilerClassLowering.lowerMethod(driver,method, internalName, false, cls.typeParameters()));
            } else if (member instanceof ConstructorDeclarationNode ctor) {
                methods.add(CompilerClassLowering.lowerConstructor(driver,ctor, internalName, superName, cls.typeParameters(), fields, fieldInits));
                methods.addAll(CompilerClassLowering.lowerConstructorDefaults(driver,ctor, internalName, superName,
                        cls.typeParameters(), fields, fieldInits));
            }
        }
        if (!methods.stream().anyMatch(m -> m.name().equals("<init>"))) {
            methods.add(0, CompilerClassLowering.generateDefaultConstructor(driver,internalName, superName, fields, fieldInits));
        }
        if (!staticFieldInits.isEmpty()) {
            methods.add(CompilerClassLowering.generateStaticInitializer(driver, internalName, superName, fields, staticFieldInits));
        }
        // bug 104b-i: classe não-record chamada com `.equals()` precisa de
        // símbolo no Native — o JVM resolve no Object.equals herdado, o
        // backend não tem java.lang.Object. Sintetiza identidade (oracle JVM).
        if ((driver.target == Target.NATIVE || driver.target == Target.NATIVE_RISCV64
                || driver.target == Target.NATIVE_AARCH64)
                && "java/lang/Object".equals(superName)
                && !methods.stream().anyMatch(m -> "equals".equals(m.name()))) {
            methods.add(CompilerRecordSupport.buildClassIdentityEqualsMethod(driver, internalName));
        }
        if (driver.target == Target.JVM || driver.target.isNative()) {
            // §356: bridges também para as INTERFACES implementadas (a
            // chamada via interface usa o descritor apagado do pai).
            // §483: no Native estes bridges TAMBÉM são necessários — o call site
            // boxa o primitivo contra o param apagado da interface. O slot da
            // vtable é resolvido por ÍNDICE a partir da interface erasure, então
            // o bridge precisa vir ANTES do método concreto para ocupar esse slot.
            List<IRMethod> bridges = CompilerRecordSupport.generateCovariantReturnBridges(
                    driver, internalName, superName, ifaces, methods);
            if (driver.target.isNative()) {
                methods.addAll(0, bridges);
            } else {
                methods.addAll(bridges);
            }
        }
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null,
                typeId, CompilerAnnotations.lowerAnnotations(driver, cls.annotations()));
    }



    // #125: o parser aceita os modificadores de mecanismo da gramática
    // (synchronized/volatile/transient/native), mas computeAccess os descarta
    // — e o programa compilava em silêncio com SEM o efeito pedido (falsa
    // sensação de segurança: contador "synchronized" sem monitor). O memory
    // model RATIFICADO (concurrency-memory-model.md §5) os declara non-goals
    // na superfície — a abstração é Channel/spawn. Decisão de design é
    // intocável (regra 6), o que muda aqui é R6: nunca silencioso. O warning
    // não-fatal conserva retrocompat (código que compila hoje continua) e
    // aponta o substituto.
    private static final java.util.Set<String> MECHANISM_MODIFIERS =
            java.util.Set.of("synchronized", "volatile", "transient", "native");

    static void warnMechanismModifiers(CompilerDriver driver, List<String> modifiers,
                                       SourcePosition pos) {
        if (driver.currentDiagnostics == null) return;
        for (String mod : modifiers) {
            if (MECHANISM_MODIFIERS.contains(mod)) {
                driver.currentDiagnostics.warning(
                        pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0,
                        pos != null ? pos.column() : 0, 0,
                        "modificador '" + mod + "' has no effect in Kof (a non-goal of the "
                                + "memory model — concurrency-memory-model.md §5); use a "
                                + "language abstraction: spawn/await/Channel for concurrency"
                                + (pos != null ? " (line " + pos.line() + ")" : ""),
                        "SEM091");
            }
        }
    }

    static IRField lowerField(CompilerDriver driver, FieldDeclarationNode field,
                     List<String> typeParams) {
        Type fieldType = CompilerTypes.resolveWithTypeParams(field.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
        Object initVal = FieldConstantFolder.coerceFieldConstant(
                FieldConstantFolder.foldConstantExpr(driver, field.initializer()), fieldType);
        warnMechanismModifiers(driver, field.modifiers(), field.position());
        return new IRField(field.name(), fieldType, driver.computeAccess(field.modifiers()), initVal,
                CompilerAnnotations.lowerAnnotations(driver, field.annotations()));
    }

    /**
     * Converte annotations do AST para a IR: nome resolvido para o formato
     * interno JVM e valores já constantes (o parser só aceita literais).
     */

    /**
     * Nome interno JVM de uma interface declarada: simples vinda de import
     * ("import android.view.OnClickListener") qualifica; senão, classe local.
     */

    /**
     * Dobra valores de annotation: refs de Classe.class e Enum.CONST viram
     * constantes resolvidas; enum só passa se o classpath provar a classe.
     */

    /**
     * Resolve o nome da annotation para o formato interno JVM. Nomes
     * qualificados vão direto; simples usam imports do arquivo; os de
     * java.lang são embutidos; senão assume-se a própria classe local.
     */

    static IRMethod lowerMethod(CompilerDriver driver, MethodDeclarationNode method,
                        String owner, boolean isInterface, List<String> typeParams) {
        String prevOwner = driver.currentLoweringOwner;
        driver.currentLoweringOwner = owner;
        java.util.List<String> prevTps = driver.currentTypeParams;
        driver.currentTypeParams = typeParams;
        try {
            return CompilerClassLowering.lowerMethodInner(driver,method, owner, isInterface, typeParams);
        } finally {
            driver.currentTypeParams = prevTps;
            driver.currentLoweringOwner = prevOwner;
        }
    }

    static IRMethod lowerMethodInner(CompilerDriver driver, MethodDeclarationNode method,
                            String owner, boolean isInterface, List<String> typeParams) {
        Type returnType = CompilerTypes.resolveWithTypeParams(method.returnType(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
        List<Type> paramTypes = method.parameters().stream()
                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer)).toList();
        if (Type.isVoid(returnType) && method.body() != null && !method.body().isEmpty()
                && method.body().getLast() instanceof ReturnStmt ret && ret.value() != null) {
            List<IRLocalVariable> tmpLocals = new ArrayList<>();
            int tmpIdx = 1;
            for (FormalParameterNode p : method.parameters()) {
                tmpLocals.add(new IRLocalVariable(tmpIdx, p.name(), CompilerTypes.resolveWithTypeParams(p.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer)));
                tmpIdx++;
            }
            Type inferred = ExpressionTyper.inferExprType(driver, ret.value(), tmpLocals);
            if (inferred instanceof Type.UnknownType && driver.semanticAnalyzer != null) {
                Type semanticRt = driver.semanticAnalyzer.resolvedMethodReturnType(method);
                if (semanticRt != null && !(semanticRt instanceof Type.UnknownType) && !Type.isVoid(semanticRt)) {
                    inferred = semanticRt;
                }
            }
            if (!(inferred instanceof Type.UnknownType)) {
                returnType = inferred;
            }
        }
        int access = driver.computeAccess(method.modifiers());
        warnMechanismModifiers(driver, method.modifiers(), method.position());
        // #213: método de interface COM corpo é default (não-abstract); só o
        // método sem corpo vira abstract. Antes o corpo era baixado na IR mas
        // a flag ABSTRACT fazia o backend pular o Code attribute.
        boolean hasBody = method.body() != null && !method.body().isEmpty();
        if (isInterface && !method.modifiers().contains("default") && !method.modifiers().contains("static")
                && !hasBody) {
            access |= AccessFlags.ABSTRACT;
        }
        List<IRBasicBlock> body = List.of();
        List<IRLocalVariable> locals = List.of();
        if (method.body() != null && !method.body().isEmpty() && !driver.isAbstractMethod(method)) {
            List<KofOperation> ops = new ArrayList<>();
            List<IRLocalVariable> localVars = new ArrayList<>();
            Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
            // método ESTÁTICO: sem driver, params começam no slot 0
            boolean isStaticMethod = (access & AccessFlags.STATIC) != 0;
            if (!isStaticMethod) {
                localVars.add(new IRLocalVariable(0, "this", ownerType));
            }
            int localIdx = isStaticMethod ? 0 : 1;
            for (FormalParameterNode param : method.parameters()) {
                Type paramType = CompilerTypes.resolveWithTypeParams(param.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
                localVars.add(new IRLocalVariable(localIdx, param.name(), paramType));
                localIdx += TypeMetrics.isDoubleWidth(paramType) ? 2 : 1;
            }
            java.util.Set<String> savedMutated = driver.mutatedCapturedNames;
            driver.mutatedCapturedNames = new java.util.HashSet<>();
            java.util.Deque<CompilerDriverState.FinallyFrame> savedFrames = driver.finallyFrames;
            driver.finallyFrames.clear(); // DD-01: frame do finally externo não vaza p/ dentro
            driver.tryDepth = 0; // §551: profundidade de try é por método
            CompilerCaptureScanner.collectMutatedCaptures(driver, method.body(), localVars);
            for (StatementNode stmt : method.body()) localIdx = driver.emitStatement(stmt, ops, owner, localIdx, localVars, returnType);
            driver.mutatedCapturedNames = savedMutated;
            driver.finallyFrames.addAll(savedFrames); // DD-01: restaura
            KofOperation lastOp = ops.isEmpty() ? null : ops.get(ops.size() - 1);
            if (lastOp == null || !(lastOp instanceof KofReturn || lastOp instanceof KofReturnVoid)) {
                if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
                else ops.add(new KofReturn(returnType));
            }
            body = List.of(new IRBasicBlock(0, ops));
            locals = localVars;
        } else if (!isInterface && !driver.isAbstractMethod(method)) {
            // corpo vazio em classe concreta: sem Code attribute o JVM rejeita
            // a classe (Absent Code attribute) — emite corpo com return default
            List<KofOperation> ops = new ArrayList<>(List.of(Type.isVoid(returnType)
                    ? new KofReturnVoid() : new KofReturn(returnType)));
            body = List.of(new IRBasicBlock(0, ops));
        }
        KofDebugInfo debugInfo = driver.currentDebugPositions.isEmpty()
                ? KofDebugInfo.EMPTY
                // GitHub #66 / bug 75: a cópia NÃO pode ser HashMap — ops são RECORDS e
// duas instâncias com o MESMO VALOR (ex.: 2 KofGetStatic do System.out em
// 2 prints) colidem por equals/hashCode: 1 entry sobrescreve o outro e
// AMBAS as ops herdam a MESMA posição (o print seguinte "vencia" o anterior
// — LNT apontando o statement seguinte). A cópia é por IDENTIDADE.
                : new KofDebugInfo(new java.util.IdentityHashMap<>(driver.currentDebugPositions));
        driver.currentDebugPositions.clear();
        return new IRMethod(method.name(), returnType, paramTypes, access, method.thrownExceptions(),
                body, locals, debugInfo,
                CompilerAnnotations.lowerAnnotations(driver, method.annotations()), CompilerAnnotations.lowerParameterAnnotations(driver, method.parameters()));
    }

    /** Annotations por parâmetro, alinhadas à ordem de parameterTypes. */

    /**
     * Default parameter values on constructors: for each trailing default, a
     * wrapper <init> with fewer parameters evaluates the default expressions
     * and delegates to the canonical constructor — the same semantics as
     * lowerFunctionDefaults for functions.
     */
    static List<IRMethod> lowerConstructorDefaults(CompilerDriver driver, ConstructorDeclarationNode ctor,
                                     String owner,
                                                    String superName, List<String> typeParams,
                                                    List<IRField> fields,
                                                    java.util.Map<String, ExpressionNode> fieldInits) {
        List<IRMethod> wrappers = new ArrayList<>();
        List<FormalParameterNode> params = ctor.parameters();
        if (params.isEmpty() || params.stream().noneMatch(p -> p.defaultExpression() != null)) {
            return wrappers;
        }
        int n = params.size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (params.get(i).defaultExpression() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return wrappers;
        List<Type> canonicalTypes = new ArrayList<>();
        for (FormalParameterNode p : params) canonicalTypes.add(CompilerTypes.resolveWithTypeParams(p.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer));
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = canonicalTypes.subList(0, paramCount);
            List<IRLocalVariable> locals = new ArrayList<>();
            locals.add(new IRLocalVariable(0, "this", ownerType));
            List<KofOperation> ops = new ArrayList<>();
            // No super()/field inits here: the canonical <init> performs
            // them; the wrapper only supplies the default arguments.
            ops.add(new KofLoadLocal(ownerType, 0));
            int localIdx = 1;
            for (int i = 0; i < paramCount; i++) {
                locals.add(new IRLocalVariable(localIdx, params.get(i).name(), paramTypes.get(i)));
                ops.add(new KofLoadLocal(paramTypes.get(i), localIdx));
                localIdx++;
            }
            for (int i = paramCount; i < n; i++) {
                localIdx = ExpressionLowerer.emitExpression(driver, params.get(i).defaultExpression(), ops, owner,
                        localIdx, locals);
            }
            ops.add(new KofCall(ownerType, "<init>", canonicalTypes, Type.PrimitiveType.VOID,
                    KofCallKind.CONSTRUCTOR));
            ops.add(new KofReturnVoid());
            wrappers.add(new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes,
                    driver.computeAccess(ctor.modifiers()), ctor.thrownExceptions(),
                    List.of(new IRBasicBlock(0, ops)), locals));
        }
        return wrappers;
    }

    static IRMethod lowerConstructor(CompilerDriver driver, ConstructorDeclarationNode ctor,
                        String owner, String superName,
                                      List<String> typeParams, List<IRField> fields,
                                      java.util.Map<String, ExpressionNode> fieldInits) {
        String prevOwner = driver.currentLoweringOwner;
        driver.currentLoweringOwner = owner;
        java.util.List<String> prevTps = driver.currentTypeParams;
        driver.currentTypeParams = typeParams == null ? java.util.List.of() : typeParams;
        try {
            return CompilerClassLowering.lowerConstructorInner(driver,ctor, owner, superName, typeParams, fields, fieldInits);
        } finally {
            driver.currentTypeParams = prevTps;
            driver.currentLoweringOwner = prevOwner;
        }
    }

    static IRMethod lowerConstructorInner(CompilerDriver driver, ConstructorDeclarationNode ctor,
                            String owner, String superName,
                                      List<String> typeParams, List<IRField> fields,
                                      java.util.Map<String, ExpressionNode> fieldInits) {
        List<Type> paramTypes = ctor.parameters().stream()
                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer)).toList();
        int access = driver.computeAccess(ctor.modifiers());
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> localVars = new ArrayList<>();
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
        Type superType = CompilerTypes.ownerTypeFromInternal(superName, driver.semanticAnalyzer);
        localVars.add(new IRLocalVariable(0, "this", ownerType));
        boolean delegatesToThis = !ctor.body().isEmpty() &&
                ctor.body().getFirst() instanceof ExpressionStmt es &&
                es.expression() instanceof MethodCallExpr mc &&
                "this".equals(mc.methodName());
        boolean hasExplicitSuper = !ctor.body().isEmpty() &&
                ctor.body().getFirst() instanceof ExpressionStmt es &&
                es.expression() instanceof MethodCallExpr mc &&
                "super".equals(mc.methodName());
        // driver(...): o construtor alvo executa super() e os inicializadores
        // #53 (metades JS/Native/script): o <init> sintético de Record só
        // existe no verificador JVM (precedente: generateRecordConstructor
        // gateia o super em isJvmTarget). Em JS a classe de record não tem
        // pai (SyntaxError 'super unexpected'); em Native não há
        // java_lang_Record_init (undefined reference no link); no interpretador
        // o Record não é super de nada. Suprimir fora do JVM.
        boolean recordSuperOnlyJvm = "java/lang/Record".equals(superName) && !driver.isJvmTarget();
        if (!delegatesToThis && !hasExplicitSuper && !"java/lang/Object".equals(superName)
                && !recordSuperOnlyJvm) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        if (!delegatesToThis) {
            CompilerClassLowering.emitFieldInitializers(driver,ops, ownerType, fields);
        }
        int localIdx = 1;
        for (FormalParameterNode param : ctor.parameters()) {
            Type paramType = CompilerTypes.resolveWithTypeParams(param.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
            localVars.add(new IRLocalVariable(localIdx, param.name(), paramType));
            localIdx += TypeMetrics.isDoubleWidth(paramType) ? 2 : 1;
        }
        for (var entry : fieldInits.entrySet()) {
            if (delegatesToThis) break;
            Type fieldType = fields.stream().filter(f -> f.name().equals(entry.getKey())).findFirst()
                    .map(f -> f.type()).orElse(Type.UnknownType.UNKNOWN);
            ops.add(new KofLoadLocal(ownerType, 0));
            localIdx = ExpressionLowerer.emitExpression(driver, entry.getValue(), ops, owner, localIdx, localVars);
            ops.add(new KofStoreField(ownerType, entry.getKey(), fieldType));
        }
        driver.tryDepth = 0; // §551: profundidade de try é por método
        for (StatementNode stmt : ctor.body()) localIdx = driver.emitStatement(stmt, ops, owner, localIdx, localVars, Type.PrimitiveType.VOID);
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, access, ctor.thrownExceptions(),
                List.of(new IRBasicBlock(0, ops)), localVars, KofDebugInfo.EMPTY,
                CompilerAnnotations.lowerAnnotations(driver, ctor.annotations()), CompilerAnnotations.lowerParameterAnnotations(driver, ctor.parameters()));
    }

    /**
     * #133 (§186): sintetiza o <clinit> da classe — coloca os inicializadores
     * ESTÁTICOS não-constantes (expressões, chamadas, `new`) num método
     * estático sem receiver, na ordem de declaração, usando KofPutStatic (o
     * mesmo caminho que `C.f = v` no código-fonte usa nos 4 backends). O
     * interpretador já roda <clinit> via ensureInit; JVM/Native/KofJS ganham
     * o suporte correspondente nos backends.
     */
    static IRMethod generateStaticInitializer(CompilerDriver driver, String owner, String superName,
                                              List<IRField> fields,
                                              java.util.Map<String, ExpressionNode> staticFieldInits) {
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        int localIdx = 0;
        String prevOwner = driver.currentLoweringOwner;
        driver.currentLoweringOwner = owner;
        try {
            for (var entry : staticFieldInits.entrySet()) {
                Type fieldType = fields.stream().filter(f -> f.name().equals(entry.getKey())).findFirst()
                        .map(f -> f.type()).orElse(Type.UnknownType.UNKNOWN);
                localIdx = ExpressionLowerer.emitExpression(driver, entry.getValue(), ops, owner, localIdx, locals);
                Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
                ops.add(new KofPutStatic(ownerType, entry.getKey(), fieldType));
            }
        } finally {
            driver.currentLoweringOwner = prevOwner;
        }
        ops.add(new KofReturnVoid());
        return new IRMethod("<clinit>", Type.PrimitiveType.VOID, List.of(),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    static IRMethod generateDefaultConstructor(CompilerDriver driver, String owner, String superName,
                                          List<IRField> fields,
                                                 java.util.Map<String, ExpressionNode> fieldInits) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
        Type superType = CompilerTypes.ownerTypeFromInternal(superName, driver.semanticAnalyzer);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        if (!"java/lang/Object".equals(superName)) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        CompilerClassLowering.emitFieldInitializers(driver,ops, ownerType, fields);
        int localIdx = 1;
        for (var entry : fieldInits.entrySet()) {
            Type fieldType = fields.stream().filter(f -> f.name().equals(entry.getKey())).findFirst()
                    .map(f -> f.type()).orElse(Type.UnknownType.UNKNOWN);
            ops.add(new KofLoadLocal(ownerType, 0));
            localIdx = ExpressionLowerer.emitExpression(driver, entry.getValue(), ops, owner, localIdx, locals);
            ops.add(new KofStoreField(ownerType, entry.getKey(), fieldType));
        }
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, List.of(), AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * Field initializers must run in the constructor (after super(), before
     * the body) — instance fields with a default value are assigned there.
     * Never silently ignore an initializer.
     */
    static void emitFieldInitializers(CompilerDriver driver, List<KofOperation> ops,
                                Type ownerType, List<IRField> fields) {
        for (IRField field : fields) {
            if (field.initialValue() == null || (field.accessFlags() & AccessFlags.STATIC) != 0) continue;
            ops.add(new KofLoadLocal(ownerType, 0));
            Object v = field.initialValue();
            String fieldName = field.type() instanceof Type.PrimitiveType pt
                    ? Type.canonicalPrimitiveName(pt.name()) : "";
            switch (v) {
                case Integer _ -> {
                    int iv = (Integer) v;
                    if ("long".equals(fieldName)) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.LONG, (long) iv));
                    } else if ("double".equals(fieldName)) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.DOUBLE, (double) iv));
                    } else if ("float".equals(fieldName)) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.FLOAT, (float) iv));
                    } else {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, iv));
                    }
                }
                case Long _ -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.LONG, (Long) v));
                }
                case String _ -> {
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, (String) v));
                }
                case Double _ -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.DOUBLE, (Double) v));
                }
                case Float _ -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.FLOAT, (Float) v));
                }
                case Boolean _ -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, ((Boolean) v) ? 1 : 0));
                }
                case null, default -> {  // null cai aqui (como no if-else: instanceof null == false)
                    continue;
                }
            }
            ops.add(new KofStoreField(ownerType, field.name(), field.type()));
        }
    }


    /**
     * #160: `Mapper<String>` → `Mapper` — o superinterface/super-classe no
     * class file é o nome APAGADO (JVMS: signature é atributo à parte); o
     * parser preserva os type-args no type-ref (lição §155), e o emit de
     * interfaces não apagava → `NoClassDefFoundError: Mapper<String>`.
     */
    static String eraseTypeArgs(String typeName) {
        if (typeName == null) return null;
        int lt = typeName.indexOf('<');
        return lt < 0 ? typeName : typeName.substring(0, lt).trim();
    }

}
