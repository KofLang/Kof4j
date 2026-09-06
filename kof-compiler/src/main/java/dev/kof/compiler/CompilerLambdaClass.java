package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Geração de classes sintéticas para lambdas (nomes, interfaces, mangling).
 */
final class CompilerLambdaClass {

    private CompilerLambdaClass() {}

    static boolean lambdaUsesSuper(Object node) {
        if (node instanceof LambdaExpr le) {
            for (StatementNode st : le.body()) {
                if (lambdaUsesSuper(st)) return true;
            }
            return false;
        }
        if (node instanceof MethodCallExpr mc) {
            if (mc.receiver() instanceof IdentifierExpr rid && "super".equals(rid.name())) return true;
            if (lambdaUsesSuper(mc.receiver())) return true;
            for (ExpressionNode arg : mc.arguments()) if (lambdaUsesSuper(arg)) return true;
            return false;
        }
        if (node instanceof IdentifierExpr ie) return "super".equals(ie.name());
        if (node instanceof FieldAccessExpr fa) return lambdaUsesSuper(fa.receiver());
        if (node instanceof BinaryExpr be) return lambdaUsesSuper(be.left()) || lambdaUsesSuper(be.right());
        if (node instanceof UnaryExpr ue) return lambdaUsesSuper(ue.operand());
        if (node instanceof AssignmentExpr ae) return lambdaUsesSuper(ae.target()) || lambdaUsesSuper(ae.value());
        if (node instanceof VarDeclStmt v) return v.initializer() != null && lambdaUsesSuper(v.initializer());
        if (node instanceof ExpressionStmt es) return es.expression() != null && lambdaUsesSuper(es.expression());
        if (node instanceof ReturnStmt rs) return rs.value() != null && lambdaUsesSuper(rs.value());
        if (node instanceof IfStmt is) return lambdaUsesSuper(is.condition())
                || lambdaUsesSuper(is.thenBranch())
                || (is.elseBranch() != null && lambdaUsesSuper(is.elseBranch()));
        if (node instanceof WhileStmt ws) return lambdaUsesSuper(ws.condition()) || lambdaUsesSuper(ws.body());
        if (node instanceof ForStmt fs) return lambdaUsesSuper(fs.init()) || lambdaUsesSuper(fs.condition())
                || lambdaUsesSuper(fs.update()) || lambdaUsesSuper(fs.body());
        if (node instanceof BlockStmt bs) {
            for (StatementNode st : bs.statements()) if (lambdaUsesSuper(st)) return true;
            return false;
        }
        return false;
    }

    static String lambdaClass(CompilerDriver driver, LambdaExpr le, Type.FunctionType ft,
                           List<IRLocalVariable> captures) {
        return CompilerLambdaClass.lambdaClass(driver, le, ft, captures, false);
    }

    /** @param isTask true for spawn bodies ({@code LambdaTask*}), not for map/filter/UI handlers */
    static String lambdaClass(CompilerDriver driver, LambdaExpr le, Type.FunctionType ft,
                               List<IRLocalVariable> captures, boolean isTask) {
        String existing = driver.lambdaClassNames.get(le);
        if (existing != null) return existing;
        String name = (isTask ? "LambdaTask" : "Lambda") + (driver.lambdaCounter++);
        Type ownerType = new Type.ClassType("", name, List.of());
        // super.metodo() dentro da lambda: captura o this EXTERNO como $outer
        boolean needsOuter = lambdaUsesSuper(le) && driver.currentLoweringOwner != null;
        if (needsOuter) {
            driver.lambdaNeedsOuter.put(le, true);
            driver.lambdaEnclosingOwner.put(name, driver.currentLoweringOwner);
            Type outerType = CompilerTypes.ownerTypeFromInternal(driver.currentLoweringOwner, driver.semanticAnalyzer);
            List<IRLocalVariable> eff = new ArrayList<>();
            eff.add(new IRLocalVariable(0, "$outer", outerType));
            eff.addAll(captures);
            captures = eff;
            driver.lambdaEffectiveCaptures.put(le, eff);
        }
        // lambda retornando lambda (bug 19): preservar a FunctionType (o
        // round-trip por string a destruía) — o className do lambda interno
        // será preenchido após a emissão do corpo.
        Type returnType = ft.returnType() instanceof Type.FunctionType
                ? ft.returnType()
                : CompilerTypes.toType(CompilerTypes.typeToString(ft.returnType()), driver.currentUnit);
        List<FormalParameterNode> params = le.parameters();
        List<Type> paramTypes = new ArrayList<>();
        for (FormalParameterNode p : params) paramTypes.add(CompilerTypes.toType(p.type(), driver.currentUnit));

        List<IRField> fields = new ArrayList<>();
        List<Type> captureTypes = new ArrayList<>();
        for (IRLocalVariable cap : captures) {
            fields.add(new IRField(cap.name(), cap.type(),
                    AccessFlags.PRIVATE | AccessFlags.FINAL, null));
            captureTypes.add(cap.type());
        }

        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        // JVM invoke(): the real parameters arrive physically at slots 1..k
        // (after this). The captures are re-homed to slots AFTER the params:
        // the prologue copies the incoming parameters to their final slots
        // first, then loads each capture field into its slot. This keeps the
        // parameter slots owned by the caller's arguments — no clobbering.
        int localIdx = 1;
        int[] paramSlots = new int[params.size()];
        int paramSlot = 1;
        for (int i = 0; i < params.size(); i++) {
            paramSlots[i] = paramSlot;
            paramSlot += TypeMetrics.isDoubleWidth(paramTypes.get(i)) ? 2 : 1;
        }
        int captureBase = paramSlot;
        int captureSlot = captureBase;
        for (IRLocalVariable cap : captures) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, cap.name(), cap.type()));
            ops.add(new KofStoreLocal(cap.type(), captureSlot));
            locals.add(new IRLocalVariable(captureSlot, cap.name(), cap.type()));
            captureSlot += TypeMetrics.isDoubleWidth(cap.type()) ? 2 : 1;
        }
        localIdx = captureSlot;
        for (int i = 0; i < params.size(); i++) {
            locals.add(new IRLocalVariable(paramSlots[i], params.get(i).name(), paramTypes.get(i)));
        }
        java.util.Set<String> savedMutated = driver.mutatedCapturedNames;
        driver.mutatedCapturedNames = new java.util.HashSet<>();
        // lambda não-void com corpo de expressão única: a expressão É o retorno
        // (ExpressionStmt emitiria POP e mataria o valor antes do areturn)
        java.util.List<StatementNode> bodyStmts = le.body();
        if (!Type.isVoid(returnType) && bodyStmts.size() == 1
                && bodyStmts.get(0) instanceof ExpressionStmt es) {
            bodyStmts = java.util.List.of(new ReturnStmt(
                    es.position() != null ? es.position() : le.position(), es.expression()));
        }
        for (StatementNode stmt : bodyStmts) {
            localIdx = driver.emitStatement(stmt, ops, name, localIdx, locals, returnType);
        }
        driver.mutatedCapturedNames = savedMutated;
        // bug 19: lambda que RETORNA outra lambda — o lambda interno é
        // sintetizado durante a emissão do corpo acima; o className dele só
        // agora está disponível. Atualiza o returnType para o descriptor do
        // invoke casar com o call site (senão NoSuchMethodError).
        if (returnType instanceof Type.FunctionType rtFt && rtFt.className() == null) {
            for (StatementNode stmt : bodyStmts) {
                if (stmt instanceof ReturnStmt rs && rs.value() instanceof LambdaExpr retLam) {
                    String cn = driver.lambdaClassNames.get(retLam);
                    if (cn != null) {
                        returnType = new Type.FunctionType(rtFt.parameterTypes(), rtFt.returnType(), cn);
                        break;
                    }
                }
            }
        }
        KofOperation last = ops.isEmpty() ? null : ops.get(ops.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
            else ops.add(new KofReturn(returnType));
        }
        IRMethod invoke = new IRMethod("invoke", returnType, paramTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);

        List<KofOperation> ctorOps = new ArrayList<>();
        List<IRLocalVariable> ctorLocals = new ArrayList<>();
        ctorLocals.add(new IRLocalVariable(0, "this", ownerType));
        int cidx = 1;
        for (IRLocalVariable cap : captures) {
            ctorOps.add(new KofLoadLocal(ownerType, 0));
            ctorOps.add(new KofLoadLocal(cap.type(), cidx));
            ctorOps.add(new KofStoreField(ownerType, cap.name(), cap.type()));
            ctorLocals.add(new IRLocalVariable(cidx, cap.name(), cap.type()));
            cidx += TypeMetrics.isDoubleWidth(cap.type()) ? 2 : 1;
        }
        ctorOps.add(new KofReturnVoid());
        IRMethod ctor = new IRMethod("<init>", Type.PrimitiveType.VOID, captureTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ctorOps)), ctorLocals);

        IRClass cls = new IRClass(name, "java/lang/Object",
                List.of(CompilerLambdaClass.lambdaInterfaceType(driver, ft).internalName()),
                AccessFlags.PUBLIC | AccessFlags.SUPER, fields,
                List.of(invoke, ctor), List.of(), null, 200 + driver.lambdaCounter);
        driver.syntheticClasses.add(cls);
        driver.lambdaClassNames.put(le, name);
        return name;
    }

    /**
     * Interface sintética por assinatura de função — o dispatch por interface
     * (bug 8) para valores de tipo de função DECLARADO (`f(x)` com
     * `f: (Int) -> Int`): o tipo não carrega className, então o call site
     * invoca via interface que TODAS as lambdas da assinatura implementam.
     */
    static Type.ClassType lambdaInterfaceType(CompilerDriver driver, Type.FunctionType ft) {
        StringBuilder key = new StringBuilder("Function").append(ft.parameterTypes().size());
        for (Type p : ft.parameterTypes()) key.append('_').append(mangleTypeForIface(p));
        key.append('_').append(mangleTypeForIface(ft.returnType()));
        String name = "kof/" + key;
        Type.ClassType cached = driver.functionInterfaces.get(name);
        if (cached != null) return cached;
        Type.ClassType iface = new Type.ClassType("kof", key.toString().replace('/', '_'), List.of());
        // método SAM abstract: invoke(params): ret
        IRMethod invoke = new IRMethod("invoke", ft.returnType(), ft.parameterTypes(),
                AccessFlags.PUBLIC | AccessFlags.ABSTRACT, List.of(),
                List.of(), List.of(new IRLocalVariable(0, "this", iface)));
        IRClass cls = new IRClass(name, "java/lang/Object", List.of(),
                AccessFlags.PUBLIC | AccessFlags.INTERFACE | AccessFlags.ABSTRACT,
                List.of(), List.of(invoke), List.of(), null, 400 + driver.lambdaCounter);
        driver.syntheticClasses.add(cls);
        driver.functionInterfaces.put(name, iface);
        return iface;
    }

    static String mangleTypeForIface(Type t) {
        if (t instanceof Type.PrimitiveType pt) return Type.canonicalPrimitiveName(pt.name());
        if (t instanceof Type.ClassType ct) return "C" + ct.name().replace('.', '_');
        if (t instanceof Type.ArrayType at) return "A" + mangleTypeForIface(at.componentType());
        if (t instanceof Type.NullableType nt) return "N" + mangleTypeForIface(nt.inner());
        return "O";
    }
}