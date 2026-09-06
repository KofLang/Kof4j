package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de declarações de classe/interface/record para IRClass/IRMethod.
 */
final class CompilerClassLowering {

    private CompilerClassLowering() {}

    static IRClass lowerClass(CompilerDriver driver, ClassDeclarationNode cls,
                        String packageName, int typeId) {
        String internalName = driver.toInternalName(packageName, cls.name());
        // usa o superClass QUALIFICADO pelo analyzer ("extends Activity" +
        // import → android/app/Activity); cai pro cru se analyzer ausente
        String superName = null;
        if (driver.semanticAnalyzer != null) {
            SymbolTable.ClassSymbol sym = driver.semanticAnalyzer.getClass(cls.name());
            if (sym != null && sym.superClass() != null && !"Object".equals(sym.superClass())) {
                superName = driver.toInternalName("", sym.superClass());
            }
        }
        if (superName == null) {
            superName = cls.superClass() != null ? driver.toInternalName("", cls.superClass())
                    : "java/lang/Object";
        }
        List<String> ifaces = cls.interfaces().stream().map(n -> CompilerAnnotations.externalOrLocalInternalName(driver, n)).toList();
        int access = driver.computeAccess(cls.modifiers());
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        java.util.Map<String, ExpressionNode> fieldInits = new java.util.LinkedHashMap<>();
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) {
                IRField irField = CompilerClassLowering.lowerField(driver,field, cls.typeParameters());
                fields.add(irField);
                if (field.initializer() != null && irField.initialValue() == null) {
                    fieldInits.put(field.name(), field.initializer());
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
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null,
                typeId, CompilerAnnotations.lowerAnnotations(driver, cls.annotations()));
    }

    static IRClass lowerInterface(CompilerDriver driver, InterfaceDeclarationNode iface,
                            String packageName, int typeId) {
        String internalName = driver.toInternalName(packageName, iface.name());
        List<String> ifaces = iface.interfaces().stream().map(n -> CompilerAnnotations.externalOrLocalInternalName(driver, n)).toList();
        int access = driver.computeAccess(iface.modifiers()) | AccessFlags.ABSTRACT | AccessFlags.INTERFACE;
        List<IRMethod> methods = new ArrayList<>();
        List<IRField> fields = new ArrayList<>();
        for (AstNode member : iface.members()) {
            if (member instanceof MethodDeclarationNode method) methods.add(CompilerClassLowering.lowerMethod(driver,method, internalName, true, List.of()));
            else if (member instanceof FieldDeclarationNode field) fields.add(CompilerClassLowering.lowerField(driver,field, List.of()));
        }
        return new IRClass(internalName, "java/lang/Object", ifaces, access, fields, methods, List.of(), null,
                typeId, CompilerAnnotations.lowerAnnotations(driver, iface.annotations()));
    }

    static IRClass lowerRecord(CompilerDriver driver, RecordDeclarationNode rec,
                       String packageName, int typeId) {
        String internalName = driver.toInternalName(packageName, rec.name());
        String superName = "java/lang/Record";
        List<String> ifaces = rec.interfaces().stream().map(n -> CompilerAnnotations.externalOrLocalInternalName(driver, n)).toList();
        int access = driver.computeAccess(rec.modifiers()) | AccessFlags.FINAL | AccessFlags.PUBLIC;
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        List<String> typeParams = rec.typeParameters() == null ? List.of() : rec.typeParameters();
        for (RecordComponentNode comp : rec.components()) {
            fields.add(new IRField(comp.name(), CompilerTypes.resolveWithTypeParams(comp.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer),
                    AccessFlags.PRIVATE | AccessFlags.FINAL,
                    null, CompilerAnnotations.lowerAnnotations(driver, comp.annotations())));
        }
        methods.add(0, CompilerRecordSupport.generateRecordConstructor(driver, rec, internalName));
        methods.addAll(CompilerRecordSupport.generateRecordDefaultOverloads(driver, rec, internalName));
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, driver.semanticAnalyzer);
        for (RecordComponentNode comp : rec.components()) {
            Type compType = CompilerTypes.resolveWithTypeParams(comp.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
            List<KofOperation> body = new ArrayList<>();
            body.add(new KofLoadLocal(ownerType, 0));
            body.add(new KofLoadField(ownerType, comp.name(), compType));
            body.add(new KofReturn(compType));
            methods.add(new IRMethod(comp.name(), compType, List.of(), AccessFlags.PUBLIC, List.of(),
                    List.of(new IRBasicBlock(0, body)),
                    List.of(new IRLocalVariable(0, "this", ownerType))));
        }
        for (AstNode member : rec.members()) {
            if (member instanceof MethodDeclarationNode method) {
                methods.add(CompilerClassLowering.lowerMethod(driver,method, internalName, false, typeParams));
            } else if (member instanceof ConstructorDeclarationNode ctor) {
                methods.add(CompilerClassLowering.lowerConstructor(driver,ctor, internalName, "java/lang/Record",
                        typeParams, fields, java.util.Map.of()));
            }
        }
        // Native: records não geram toString/equals nos backends (JVM/JS
        // geram nos seus emitters). Sintetiza no IR para paridade — bug 11
        // (native `==` dava undefined reference) e toString imprimia o handle.
        if (driver.target == Target.NATIVE || driver.target == Target.NATIVE_RISCV64
                || driver.target == Target.NATIVE_AARCH64) {
            methods.add(CompilerRecordSupport.buildRecordToStringMethod(driver, internalName, rec, fields, typeParams));
            methods.add(CompilerRecordSupport.buildRecordEqualsMethod(driver, internalName, fields, typeParams));
        }
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null,
                typeId, CompilerAnnotations.lowerAnnotations(driver, rec.annotations()));
    }


    static IRField lowerField(CompilerDriver driver, FieldDeclarationNode field,
                     List<String> typeParams) {
        Type fieldType = CompilerTypes.resolveWithTypeParams(field.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
        Object initVal = null;
        if (field.initializer() instanceof LiteralExpr lit) {
            initVal = switch (lit.kind()) {
                case ConcreteLiteralKind.INT -> driver.parseIntLiteral(lit.value());
                case ConcreteLiteralKind.LONG -> Long.parseLong(driver.stripSuffix(lit.value()));
                case ConcreteLiteralKind.FLOAT -> Float.parseFloat(driver.stripSuffix(lit.value()));
                case ConcreteLiteralKind.DOUBLE -> Double.parseDouble(driver.stripSuffix(lit.value()));
                case ConcreteLiteralKind.STRING -> lit.value();
                case ConcreteLiteralKind.BOOLEAN -> Boolean.parseBoolean(lit.value()) ? 1 : 0;
                default -> null;
            };
        }
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
        try {
            return CompilerClassLowering.lowerMethodInner(driver,method, owner, isInterface, typeParams);
        } finally {
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
        if (isInterface && !method.modifiers().contains("default")) access |= AccessFlags.ABSTRACT;
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
            CompilerCaptureScanner.collectMutatedCaptures(driver, method.body(), localVars);
            for (StatementNode stmt : method.body()) localIdx = driver.emitStatement(stmt, ops, owner, localIdx, localVars, returnType);
            driver.mutatedCapturedNames = savedMutated;
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
                : new KofDebugInfo(new java.util.HashMap<>(driver.currentDebugPositions));
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
        Type superType = CompilerTypes.ownerTypeFromInternal(superName, driver.semanticAnalyzer);
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
        try {
            return CompilerClassLowering.lowerConstructorInner(driver,ctor, owner, superName, typeParams, fields, fieldInits);
        } finally {
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
        if (!delegatesToThis && !hasExplicitSuper && !"java/lang/Object".equals(superName)) {
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
        for (StatementNode stmt : ctor.body()) localIdx = driver.emitStatement(stmt, ops, owner, localIdx, localVars, Type.PrimitiveType.VOID);
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, access, ctor.thrownExceptions(),
                List.of(new IRBasicBlock(0, ops)), localVars, KofDebugInfo.EMPTY,
                CompilerAnnotations.lowerAnnotations(driver, ctor.annotations()), CompilerAnnotations.lowerParameterAnnotations(driver, ctor.parameters()));
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
            if (v instanceof Integer) {
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
            } else if (v instanceof Long) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.LONG, (Long) v));
            } else if (v instanceof String) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, (String) v));
            } else if (v instanceof Double) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.DOUBLE, (Double) v));
            } else if (v instanceof Float) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.FLOAT, (Float) v));
            } else if (v instanceof Boolean) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, ((Boolean) v) ? 1 : 0));
            } else {
                continue;
            }
            ops.add(new KofStoreField(ownerType, field.name(), field.type()));
        }
    }

}