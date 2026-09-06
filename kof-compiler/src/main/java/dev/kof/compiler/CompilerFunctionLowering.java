package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de funções top-level para IRMethod (incluindo defaults).
 */
final class CompilerFunctionLowering {

    private CompilerFunctionLowering() {}

    static IRMethod lowerFunction(CompilerDriver driver, FunctionDeclarationNode func) {
        String prevOwner = driver.currentLoweringOwner;
        driver.currentLoweringOwner = mainClassInternalName();
        try {
            return lowerFunctionInner(driver, func);
        } finally {
            driver.currentLoweringOwner = prevOwner;
        }
    }

    private static String mainClassInternalName() {
        return "Default/Main";
    }

    static IRMethod lowerFunctionInner(CompilerDriver driver, FunctionDeclarationNode func) {
        Type returnType = CompilerTypes.resolveWithTypeParams(func.returnType(), func.typeParameters(), driver.currentUnit, driver.semanticAnalyzer);
        if (Type.isVoid(returnType)) {
            // inferência de retorno: percorre o corpo acumulando locais
            // (params + var decls) até achar um ReturnStmt com valor
            List<IRLocalVariable> tmpLocals = new ArrayList<>();
            int tmpIdx = 0;
            for (FormalParameterNode p : func.parameters()) {
                Type pt = CompilerTypes.resolveWithTypeParams(p.type(), func.typeParameters(), driver.currentUnit, driver.semanticAnalyzer);
                tmpLocals.add(new IRLocalVariable(tmpIdx, p.name(), pt));
                tmpIdx += TypeMetrics.isDoubleWidth(pt) ? 2 : 1;
            }
            for (StatementNode stmt : func.body()) {
                if (stmt instanceof VarDeclStmt vds && vds.initializer() != null) {
                    Type vt = vds.type() != null && !"var".equals(vds.type())
                            ? CompilerTypes.toType(vds.type(), driver.currentUnit)
                            : ExpressionTyper.inferExprType(driver, vds.initializer(), tmpLocals);
                    tmpLocals.add(new IRLocalVariable(tmpIdx, vds.name(), vt));
                    tmpIdx += TypeMetrics.isDoubleWidth(vt) ? 2 : 1;
                }
                if (stmt instanceof ReturnStmt ret && ret.value() != null) {
                    Type inferred = ExpressionTyper.inferExprType(driver, ret.value(), tmpLocals);
                    if (!(inferred instanceof Type.UnknownType) && !Type.isVoid(inferred)) {
                        returnType = inferred;
                    }
                    break;
                }
                if (stmt instanceof ExpressionStmt es && es.expression() instanceof MethodCallExpr) {
                    break; // void call termina a busca
                }
            }
        }
        List<Type> paramTypes = func.parameters().stream()
                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), func.typeParameters(), driver.currentUnit, driver.semanticAnalyzer)).toList();
        boolean mainArgsList = "main".equals(func.name()) && func.parameters().size() == 1
                && "args".equals(func.parameters().get(0).name())
                && BuiltinTypes.isList(paramTypes.get(0));
        boolean isMain = "main".equals(func.name())
                && (paramTypes.isEmpty() || mainArgsList);
        if (isMain) {
            paramTypes = List.of(new Type.ArrayType(BuiltinTypes.STRING));
        }
        boolean prevMain = driver.loweringMain;
        driver.loweringMain = isMain;
        boolean prevMainArgsList = driver.mainArgsListField;
        driver.mainArgsListField = mainArgsList;
        int access = AccessFlags.PUBLIC | AccessFlags.STATIC;
        List<IRLocalVariable> locals = new ArrayList<>();
        List<KofOperation> body = new ArrayList<>();
        int localIdx = 0;
        if (isMain) {
            if (mainArgsList) {
                // args: List<String> — convert the injected String[] once
                // at method entry (JVM); Native/JS start with an empty list.
                if (driver.target == Target.JVM) {
                    body.add(new KofLoadLocal(new Type.ArrayType(BuiltinTypes.STRING), 0));
                    body.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_args_list", List.of(new Type.ArrayType(BuiltinTypes.STRING)),
                            BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    body.add(new KofStoreLocal(BuiltinTypes.LIST, 1));
                } else if (driver.target == Target.JS) {
                    body.add(new KofCall(BuiltinTypes.LIST, "kof_args", List.of(),
                            BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    body.add(new KofStoreLocal(BuiltinTypes.LIST, 1));
                } else {
                    body.add(new KofCall(BuiltinTypes.LIST, "kof_list_new", List.of(),
                            BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    body.add(new KofStoreLocal(BuiltinTypes.LIST, 1));
                }
            }
            localIdx = 1;
        }
        for (FormalParameterNode p : func.parameters()) {
            Type paramType = CompilerTypes.resolveWithTypeParams(p.type(), func.typeParameters(), driver.currentUnit, driver.semanticAnalyzer);
            locals.add(new IRLocalVariable(localIdx, p.name(), paramType));
            localIdx += TypeMetrics.isDoubleWidth(paramType) ? 2 : 1;
        }
        java.util.Set<String> savedMutated = driver.mutatedCapturedNames;
        driver.mutatedCapturedNames = new java.util.HashSet<>();
        CompilerCaptureScanner.collectMutatedCaptures(driver, func.body(), locals);
        for (StatementNode stmt : func.body()) {
            localIdx = driver.emitStatement(stmt, body, "", localIdx, locals, returnType);
        }
        driver.mutatedCapturedNames = savedMutated;
        KofOperation last = body.isEmpty() ? null : body.get(body.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(returnType)) body.add(new KofReturnVoid());
            else body.add(new KofReturn(returnType));
        }
        KofDebugInfo debugInfo = driver.currentDebugPositions.isEmpty()
                ? KofDebugInfo.EMPTY
                : new KofDebugInfo(new java.util.HashMap<>(driver.currentDebugPositions));
        driver.currentDebugPositions.clear();
        driver.loweringMain = prevMain;
        driver.mainArgsListField = prevMainArgsList;
        return new IRMethod(func.name(), returnType, paramTypes, access, func.thrownExceptions(),
                List.of(new IRBasicBlock(0, body)), locals, debugInfo,
                CompilerAnnotations.lowerAnnotations(driver, func.annotations()), CompilerAnnotations.lowerParameterAnnotations(driver, func.parameters()));
    }

    /**
     * Default parameter values: for each trailing default, a wrapper with the
     * same name and fewer parameters is generated. The wrapper evaluates the
     * default expressions and delegates to the canonical function — pure
     * compile-time semantics, no runtime machinery.
     */
    static List<IRMethod> lowerFunctionDefaults(CompilerDriver driver, FunctionDeclarationNode func) {
        List<IRMethod> wrappers = new ArrayList<>();
        List<FormalParameterNode> params = func.parameters();
        if (params.isEmpty() || params.stream().noneMatch(p -> p.defaultExpression() != null)) {
            return wrappers;
        }
        if ("main".equals(func.name())) return wrappers;
        int n = params.size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (params.get(i).defaultExpression() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return wrappers;
        List<Type> canonicalTypes = params.stream()
                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), func.typeParameters(), driver.currentUnit, driver.semanticAnalyzer)).toList();
        Type returnType = CompilerTypes.resolveWithTypeParams(func.returnType(), func.typeParameters(), driver.currentUnit, driver.semanticAnalyzer);
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = canonicalTypes.subList(0, paramCount);
            List<IRLocalVariable> locals = new ArrayList<>();
            List<KofOperation> ops = new ArrayList<>();
            int localIdx = 0;
            for (int i = 0; i < paramCount; i++) {
                locals.add(new IRLocalVariable(localIdx, params.get(i).name(), paramTypes.get(i)));
                ops.add(new KofLoadLocal(paramTypes.get(i), localIdx));
                localIdx++;
            }
            for (int i = paramCount; i < n; i++) {
                localIdx = ExpressionLowerer.emitExpression(driver, params.get(i).defaultExpression(), ops, "",
                        localIdx, locals);
            }
            ops.add(new KofCall(CompilerTypes.mainClassType(driver.currentModule), func.name(), canonicalTypes,
                    returnType, KofCallKind.FUNCTION));
            ops.add(new KofReturn(returnType));
            wrappers.add(new IRMethod(func.name(), returnType, paramTypes,
                    AccessFlags.PUBLIC | AccessFlags.STATIC, func.thrownExceptions(),
                    List.of(new IRBasicBlock(0, ops)), locals));
        }
        return wrappers;
    }

}