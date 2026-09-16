package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Emissão de argumentos com tipos formais, super-bridges e ++/-- (emitIncrement).
 */
public final class CompilerEmission2 {

    private CompilerEmission2() {}

    static String ensureSuperBridge(CompilerDriver driver, String ownerInternal, String superInternal,
                                     String methodName, List<Type> paramTypes, Type returnType) {
        String bridgeName = "kof_super$" + methodName;
        List<IRMethod> bridges = driver.pendingSuperBridges.computeIfAbsent(ownerInternal,
                k -> new ArrayList<>());
        for (IRMethod b : bridges) {
            if (b.name().equals(bridgeName)) return bridgeName;
        }
        Type ownerT = CompilerTypes.ownerTypeFromInternal(ownerInternal, driver.semanticAnalyzer);
        Type superT = CompilerTypes.ownerTypeFromInternal(superInternal, driver.semanticAnalyzer);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerT));
        int idx = 1;
        for (Type pt : paramTypes) {
            locals.add(new IRLocalVariable(idx, "arg" + idx, pt));
            idx += TypeMetrics.isDoubleWidth(pt) ? 2 : 1;
        }
        ops.add(new KofLoadLocal(ownerT, 0));
        int argIdx = 1;
        for (Type pt : paramTypes) {
            ops.add(new KofLoadLocal(pt, argIdx));
            argIdx += TypeMetrics.isDoubleWidth(pt) ? 2 : 1;
        }
        ops.add(new KofCall(superT, methodName, paramTypes, returnType, KofCallKind.SUPER));
        if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
        else ops.add(new KofReturn(returnType));
        bridges.add(new IRMethod(bridgeName, returnType, paramTypes,
                AccessFlags.PUBLIC | AccessFlags.FINAL, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals));
        return bridgeName;
    }

    static int emitArgumentsWithFormalTypes(CompilerDriver driver, List<ExpressionNode> args, List<Type> formalTypes,
                                             List<KofOperation> ops, String owner, int localIdx,
                                             List<IRLocalVariable> locals) {
        for (int i = 0; i < args.size(); i++) {
            Type formal = i < formalTypes.size() ? formalTypes.get(i) : null;
            // SAM conversion: lambda → interface funcional externa
            // (setOnClickListener(v -> ...) com OnClickListener no classpath)
            if (args.get(i) instanceof LambdaExpr le && formal instanceof Type.ClassType ct
                    && !ct.packageName().isEmpty()
                    && driver.externalClasspath.isInterface(ct.internalName())) {
                ExternalClasspath.Sam sam = driver.externalClasspath.resolveSam(ct.internalName());
                if (sam != null) {
                    localIdx = driver.emitSamAdapter(le, ct, sam, ops, owner, localIdx, locals);
                    continue;
                }
            }
            localIdx = ExpressionLowerer.emitExpression(driver, args.get(i), ops, owner, localIdx, locals);
            Type argType = ExpressionTyper.inferExprType(driver, args.get(i), locals);
            if (formal != null && formal instanceof Type.PrimitiveType
                    && argType instanceof Type.PrimitiveType
                    && !BuiltinTypes.isString(formal)) {
                driver.emitWideningIfNeeded(ops, argType, formal);
            }
            if (formal != null && CompilerTypeSupport.erasesToReference(formal) && TypeMetrics.isPrimitiveType(argType)
                    && !BuiltinTypes.isString(formal)
                    && !ExpressionTyper.boxesOwnBranches(driver, args.get(i), locals)) {
                driver.emitErasureBox(ops, argType);
            }
            // D-NULL-INTENT/N1: parâmetro `Nullable(primitivo)` (ex. `Boolean?`)
            // agora é boxed no descritor — um argumento CRU (literal/expr
            // primitiva, ou Map.get() SG-008) precisa boxar antes da chamada.
            // Um argumento GENUÍNO já `Nullable(primitivo)` (ex. outra `T?`)
            // já chega como referência — não boxar de novo (§0).
            if (formal instanceof Type.NullableType fnt && fnt.inner() instanceof Type.PrimitiveType
                    && !ExpressionTyper.boxesOwnBranches(driver, args.get(i), locals)) {
                if (argType instanceof Type.PrimitiveType) {
                    driver.emitWideningIfNeeded(ops, argType, fnt.inner());
                    driver.emitErasureBox(ops, fnt.inner());
                } else if (CompilerComparisons.isCollectionMissSource(driver, args.get(i), locals)) {
                    driver.emitErasureBox(ops, argType instanceof Type.NullableType ant ? ant.inner() : argType);
                }
            }
            if (formal != null && BuiltinTypes.isString(formal)
                    && argType instanceof Type.PrimitiveType pt
                    && "char".equals(Type.canonicalPrimitiveName(pt.name()))) {
                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                        List.of(Type.PrimitiveType.CHAR), BuiltinTypes.STRING, KofCallKind.STATIC));
            }
        }
        return localIdx;
    }

    static int emitIncrement(CompilerDriver driver, UnaryExpr ue, Type operandType, List<KofOperation> ops,
                              String owner, int localIdx, List<IRLocalVariable> locals) {
        boolean prefix = ue.prefix();
        KofBinaryOp op = "++".equals(ue.operator()) ? KofBinaryOp.ADD : KofBinaryOp.SUB;
        ExpressionNode target = ue.operand();
        if (target instanceof IdentifierExpr ie) {
            IRLocalVariable var = driver.findLocalVar(ie.name(), locals);
            if (var != null) {
                // local: [load v, (dup), 1, add, (dup), store v]
                // §168: long/double ocupam 2 slots no JVM e o KofDup2 tem
                // semântica de "2 entradas" no IR do JS (array compound) — o
                // DUP de 1 slot corrompe o frame JVM e o DUP2 quebra o JS.
                // Para tipos largos usa um temp explícito (sem dup), como já
                // fazem os increments de campo/array.
                if (TypeMetrics.isDoubleWidth(var.type())) {
                    int tmp = localIdx;
                    localIdx += 2;
                    locals.add(new IRLocalVariable(tmp, "#inc", var.type()));
                    ops.add(new KofLoadLocal(var.type(), var.index()));
                    ops.add(new KofStoreLocal(var.type(), tmp));
                    ops.add(new KofLoadLocal(var.type(), tmp));
                    CompilerEmissionHelpers.emitIncrementOne(ops, var.type());
                    ops.add(new KofBinary(op, var.type()));
                    ops.add(new KofStoreLocal(var.type(), var.index()));
                    ops.add(new KofLoadLocal(var.type(), prefix ? var.index() : tmp));
                    return localIdx;
                }
                ops.add(new KofLoadLocal(var.type(), var.index()));
                if (!prefix) ops.add(new KofDup());
                CompilerEmissionHelpers.emitIncrementOne(ops, var.type());
                ops.add(new KofBinary(op, var.type()));
                if (prefix) ops.add(new KofDup());
                ops.add(new KofStoreLocal(var.type(), var.index()));
                return localIdx;
            }
            if (!owner.isEmpty() && driver.semanticAnalyzer != null) {
                String className = owner.substring(owner.lastIndexOf('/') + 1);
                SymbolTable.Symbol fieldSym = HierarchyResolver.resolveFieldInHierarchy(className, ie.name(), driver.semanticAnalyzer);
                if (fieldSym instanceof SymbolTable.FieldSymbol fs) {
                    Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
                    if ((fs.accessFlags() & AccessFlags.STATIC) != 0) {
                        return emitStaticFieldIncrement(ownerType, ie.name(), fs.type(), prefix, op, ops, localIdx, locals);
                    }
                    ops.add(new KofLoadLocal(ownerType, 0));
                    localIdx = driver.emitFieldIncrement(ownerType, ie.name(), fs.type(), prefix, op,
                            ops, localIdx, locals);
                    return localIdx;
                }
            }
        }
        if (target instanceof FieldAccessExpr fa) {
            Type recvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
            Type fieldType = Type.UnknownType.UNKNOWN;
            SymbolTable.FieldSymbol fs = null;
            if (recvType instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
                SymbolTable.Symbol sym = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
                if (sym instanceof SymbolTable.FieldSymbol f) {
                    fs = f;
                    fieldType = f.type();
                }
            }
            if (fs != null && (fs.accessFlags() & AccessFlags.STATIC) != 0) {
                return emitStaticFieldIncrement(recvType, fa.fieldName(), fieldType, prefix, op, ops, localIdx, locals);
            }
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            localIdx = driver.emitFieldIncrement(recvType, fa.fieldName(), fieldType, prefix, op,
                    ops, localIdx, locals);
            return localIdx;
        }
        if (target instanceof ArrayAccessExpr aa) {
            localIdx = ExpressionLowerer.emitExpression(driver, aa.receiver(), ops, owner, localIdx, locals);
            Type recvType = ExpressionTyper.inferExprType(driver, aa.receiver(), locals);
            Type elemType = Type.arrayElementType(recvType);
            int arrTmp = localIdx++;
            int idxTmp = localIdx++;
            int valTmp = localIdx;
            localIdx += TypeMetrics.isDoubleWidth(elemType) ? 2 : 1;
            int newTmp = localIdx;
            localIdx += TypeMetrics.isDoubleWidth(elemType) ? 2 : 1;
            locals.add(new IRLocalVariable(arrTmp, "#arr", recvType));
            locals.add(new IRLocalVariable(idxTmp, "#idx", Type.PrimitiveType.INT));
            locals.add(new IRLocalVariable(valTmp, "#val", elemType));
            locals.add(new IRLocalVariable(newTmp, "#new", elemType));
            ops.add(new KofStoreLocal(recvType, arrTmp));
            localIdx = ExpressionLowerer.emitExpression(driver, aa.index(), ops, owner, localIdx, locals);
            ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxTmp));
            // §168: o store de array consome [array, index, valor]; o caminho
            // antigo emitia KofArrayStore com só o valor na pilha (VerifyError
            // no JVM, underflow no JS, core dump no Native). Materializa o
            // índice via temps e mantém o valor velho p/ o pós-fixado.
            ops.add(new KofLoadLocal(recvType, arrTmp));
            ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxTmp));
            ops.add(new KofArrayLoad(elemType));
            ops.add(new KofStoreLocal(elemType, valTmp));
            ops.add(new KofLoadLocal(elemType, valTmp));
            CompilerEmissionHelpers.emitIncrementOne(ops, elemType);
            ops.add(new KofBinary(op, elemType));
            ops.add(new KofStoreLocal(elemType, newTmp));
            ops.add(new KofLoadLocal(recvType, arrTmp));
            ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxTmp));
            ops.add(new KofLoadLocal(elemType, newTmp));
            ops.add(new KofArrayStore(elemType));
            ops.add(new KofLoadLocal(elemType, prefix ? newTmp : valTmp));
            return localIdx;
        }
        // non-assignable operand: evaluate as expression (legacy behavior)
        localIdx = ExpressionLowerer.emitExpression(driver, ue.operand(), ops, owner, localIdx, locals);
        CompilerEmissionHelpers.emitIncrementOne(ops, operandType);
        ops.add(new KofBinary(op, operandType));
        return localIdx;
    }

    private static int emitStaticFieldIncrement(Type ownerType, String fieldName, Type fieldType,
                                                boolean prefix, KofBinaryOp op,
                                                List<KofOperation> ops, int localIdx,
                                                List<IRLocalVariable> locals) {
        int valTmp = localIdx;
        localIdx += TypeMetrics.isDoubleWidth(fieldType) ? 2 : 1;
        int newTmp = localIdx;
        localIdx += TypeMetrics.isDoubleWidth(fieldType) ? 2 : 1;
        locals.add(new IRLocalVariable(valTmp, "#sinc", fieldType));
        locals.add(new IRLocalVariable(newTmp, "#snew", fieldType));
        ops.add(new KofGetStatic(ownerType, fieldName, fieldType));
        ops.add(new KofStoreLocal(fieldType, valTmp));
        ops.add(new KofLoadLocal(fieldType, valTmp));
        CompilerEmissionHelpers.emitIncrementOne(ops, fieldType);
        ops.add(new KofBinary(op, fieldType));
        ops.add(new KofStoreLocal(fieldType, newTmp));
        ops.add(new KofLoadLocal(fieldType, newTmp));
        ops.add(new KofPutStatic(ownerType, fieldName, fieldType));
        ops.add(new KofLoadLocal(fieldType, prefix ? newTmp : valTmp));
        return localIdx;
    }
}