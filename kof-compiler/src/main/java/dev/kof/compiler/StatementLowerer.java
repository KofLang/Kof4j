package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de statements (emitStatementInner + switch-expr) do CompilerDriver.
 * Recebe o driver como host para os helpers compartilhados (emitExpression etc.).
 */
final class StatementLowerer {

    private StatementLowerer() {}

    static int emitStatementInner(CompilerDriver driver, StatementNode stmt, List<KofOperation> ops, String owner, int localIdx,
                                   List<IRLocalVariable> locals, Type returnType) {
        return switch (stmt) {
            case ReturnStmt ret -> {
                if (ret.value() != null) {
                    localIdx = driver.emitExpression(ret.value(), ops, owner, localIdx, locals);
                    driver.emitWideningIfNeeded(ops, driver.inferExprType(ret.value(), locals), returnType);
                    ops.add(new KofReturn(returnType));
                } else if (Type.isVoid(returnType)) {
                    ops.add(new KofReturnVoid());
                } else {
                    ops.add(CompilerTypes.defaultValueOp(returnType));
                    ops.add(new KofReturn(returnType));
                }
                yield localIdx;
            }
            case BreakStmt ignored -> {
                if (!driver.breakLabels.isEmpty()) ops.add(new KofJump(driver.breakLabels.peek()));
                yield localIdx;
            }
            case ContinueStmt ignored -> {
                if (!driver.continueLabels.isEmpty()) ops.add(new KofJump(driver.continueLabels.peek()));
                yield localIdx;
            }
            case ExpressionStmt es -> {
                if (es.expression() != null) {
                    localIdx = driver.emitExpression(es.expression(), ops, owner, localIdx, locals);
                    if (driver.hasReturnValue(es.expression(), locals)) ops.add(new KofPop());
                }
                yield localIdx;
            }
            case VarDeclStmt vds -> {
                Type varType = CompilerTypes.toType(vds.type(), driver.currentUnit);
                // nullable é constraint de compile-time: o storage é o inner
                // (a referência já pode ser null na JVM/Native/JS)
                if (varType instanceof Type.NullableType nt) {
                    varType = nt.inner();
                }
                if (driver.mutatedCapturedNames.contains(vds.name())) {
                    Type initType = vds.initializer() == null ? Type.PrimitiveType.INT
                            : driver.inferExprType(vds.initializer(), locals);
                    String boxName = driver.boxFactory.createBoxClass(initType, driver.syntheticClasses, driver.lambdaCounter);
                    Type boxType = new Type.ClassType("", boxName, List.of());
                    ops.add(new KofNewObject(boxType, List.of()));
                    ops.add(new KofDup());
                    ops.add(new KofCall(boxType, "<init>", List.of(),
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                    ops.add(new KofDup());
                    if (vds.initializer() != null) {
                        localIdx = driver.emitExpression(vds.initializer(), ops, owner, localIdx, locals);
                    } else {
                        ops.add(new KofLoadLiteral(initType, 0));
                    }
                    ops.add(new KofStoreField(boxType, "value", initType));
                    ops.add(new KofStoreLocal(boxType, localIdx));
                    locals.add(new IRLocalVariable(localIdx, vds.name(), boxType));
                    yield localIdx + 1;
                }
                if (vds.initializer() != null) {
                    Type initType = driver.inferExprType(vds.initializer(), locals);
                    if (Type.isVoid(initType)) {
                        if (driver.currentDiagnostics != null) {
                            driver.currentDiagnostics.error(vds.position() != null ? vds.position().file() : "",
                                    vds.position() != null ? vds.position().line() : 0,
                                    vds.position() != null ? vds.position().column() : 0, 0,
                                    "a atribuição a '" + vds.name() + "' recebeu um valor void — a"
                                            + " chamada não retorna valor",
                                    "SEM033");
                        }
                        yield localIdx;
                    }
                    localIdx = driver.emitExpression(vds.initializer(), ops, owner, localIdx, locals);
                    if ("var".equals(vds.type()) || "val".equals(vds.type())) {
                        varType = driver.inferExprType(vds.initializer(), locals);
                        // spawn-expr: pina Handle<T> com T do corpo (a inferência
                        // genérica pode ter perdido o typeArgument)
                        if (vds.initializer() instanceof MethodCallExpr sm
                                && "__kof_spawn_expr".equals(sm.methodName())
                                && varType instanceof Type.ClassType hct
                                && "kof.concurrent".equals(hct.packageName())
                                && (hct.typeArguments().isEmpty()
                                    || hct.typeArguments().get(0) instanceof Type.UnknownType)) {
                            varType = new Type.ClassType("kof.concurrent", "Handle",
                                    List.of(driver.inferExprType(sm.arguments().get(0), locals)));
                        }
                    } else {
                        Type initT = driver.inferExprType(vds.initializer(), locals);
                        // bug 8: `var s: (Int) -> Int = (x: Int) -> x * 2` — o
                        // tipo declarado é FunctionType sem className, mas o
                        // valor real é a classe sintética da lambda. Preservar
                        // o className do initializer para o call site invocar
                        // via invokevirtual (owner = classe da lambda) em vez
                        // de SEM032 (dispatch por interface ainda não existe).
                        if (varType instanceof Type.FunctionType dft
                                && initT instanceof Type.FunctionType ift
                                && ift.className() != null
                                && dft.parameterTypes().equals(ift.parameterTypes())
                                && dft.returnType().equals(ift.returnType())) {
                            varType = ift;
                        } else {
                            driver.emitWideningIfNeeded(ops, initT, varType);
                        }
                    }
                }
                // bug 15: `Object n = 42` — primitivo atribuído a referência:
                // boxa no JVM (JS/Native já são untyped). Sem isso o store de
                // int num slot Object invalidava o bytecode.
                if (driver.erasesToReference(varType)
                        && vds.initializer() != null
                        && TypeMetrics.isPrimitiveType(driver.inferExprType(vds.initializer(), locals))) {
                    driver.emitErasureBox(ops, driver.inferExprType(vds.initializer(), locals));
                }
                // declaração sem inicializador: default (0 primitivo / null
                // referência) — antes o store saía de pilha vazia (frame crash)
                if (vds.initializer() == null) {
                    ops.add(driver.erasesToReference(varType)
                            ? new KofLoadLiteral(varType, null)
                            : new KofLoadLiteral(varType, 0));
                }
                ops.add(new KofStoreLocal(varType, localIdx));
                locals.add(new IRLocalVariable(localIdx, vds.name(), varType));
                yield localIdx + (TypeMetrics.isDoubleWidth(varType) ? 2 : 1);
            }
            case BlockStmt block -> {
                int idx = localIdx;
                for (StatementNode s : block.statements()) {
                    idx = driver.emitStatement(s, ops, owner, idx, locals, returnType);
                }
                yield idx;
            }
            case IfStmt ifStmt -> {
                LabelId elseLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId thenLabel = LabelId.create();
                if (ifStmt.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                    localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()), driver.comparisonOperandType(bin, locals), thenLabel, elseLabel));
                } else {
                    localIdx = driver.emitExpression(ifStmt.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.NE, thenLabel, elseLabel));
                }
                ops.add(new KofLabel(thenLabel));
                localIdx = driver.emitStatement(ifStmt.thenBranch(), ops, owner, localIdx, locals, returnType);
                ops.add(new KofJump(endLabel));
                ops.add(new KofLabel(elseLabel));
                if (ifStmt.elseBranch() != null) {
                    localIdx = driver.emitStatement(ifStmt.elseBranch(), ops, owner, localIdx, locals, returnType);
                }
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case WhileStmt ws -> {
                LabelId startLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId bodyLabel = LabelId.create();
                ops.add(new KofLabel(startLabel));
                if (ws.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                    localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()), driver.comparisonOperandType(bin, locals), bodyLabel, endLabel));
                } else {
                    localIdx = driver.emitExpression(ws.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.NE, bodyLabel, endLabel));
                }
                ops.add(new KofLabel(bodyLabel));
                driver.breakLabels.push(endLabel);
                driver.continueLabels.push(startLabel);
                localIdx = driver.emitStatement(ws.body(), ops, owner, localIdx, locals, returnType);
                driver.breakLabels.pop();
                driver.continueLabels.pop();
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case DoWhileStmt dws -> {
                LabelId startLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                ops.add(new KofLabel(startLabel));
                driver.breakLabels.push(endLabel);
                driver.continueLabels.push(startLabel);
                localIdx = driver.emitStatement(dws.body(), ops, owner, localIdx, locals, returnType);
                driver.breakLabels.pop();
                driver.continueLabels.pop();
                if (dws.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                    localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()), driver.comparisonOperandType(bin, locals), startLabel, endLabel));
                } else {
                    localIdx = driver.emitExpression(dws.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.NE, startLabel, endLabel));
                }
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case ForStmt fs -> {
                LabelId startLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId continueLabel = LabelId.create();
                LabelId bodyLabel = LabelId.create();
                if (fs.init() != null) localIdx = driver.emitStatement(fs.init(), ops, owner, localIdx, locals, returnType);
                ops.add(new KofLabel(startLabel));
                if (fs.condition() != null) {
                    if (fs.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                        localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                        ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()), driver.comparisonOperandType(bin, locals), bodyLabel, endLabel));
                    } else {
                        localIdx = driver.emitExpression(fs.condition(), ops, owner, localIdx, locals);
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        ops.add(new KofConditionalJump(KofComparison.NE, bodyLabel, endLabel));
                    }
                }
                ops.add(new KofLabel(bodyLabel));
                driver.breakLabels.push(endLabel);
                driver.continueLabels.push(continueLabel);
                localIdx = driver.emitStatement(fs.body(), ops, owner, localIdx, locals, returnType);
                driver.breakLabels.pop();
                driver.continueLabels.pop();
                ops.add(new KofLabel(continueLabel));
                if (fs.update() != null) {
                    if (fs.update() instanceof UnaryExpr ue && "++".equals(ue.operator()) && ue.operand() instanceof IdentifierExpr id) {
                        IRLocalVariable var = driver.findLocalVar(id.name(), locals);
                        if (var != null) {
                            ops.add(new KofLoadLocal(var.type(), var.index()));
                            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                            ops.add(new KofBinary(KofBinaryOp.ADD, var.type()));
                            ops.add(new KofStoreLocal(var.type(), var.index()));
                        }
                    } else if (fs.update() instanceof UnaryExpr ue2 && "--".equals(ue2.operator()) && ue2.operand() instanceof IdentifierExpr id2) {
                        IRLocalVariable var2 = driver.findLocalVar(id2.name(), locals);
                        if (var2 != null) {
                            ops.add(new KofLoadLocal(var2.type(), var2.index()));
                            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                            ops.add(new KofBinary(KofBinaryOp.SUB, var2.type()));
                            ops.add(new KofStoreLocal(var2.type(), var2.index()));
                        }
                    } else {
                        localIdx = driver.emitExpression(fs.update(), ops, owner, localIdx, locals);
                        if (driver.hasReturnValue(fs.update(), locals)) ops.add(new KofPop());
                    }
                }
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case ForInStmt fis -> {
                LabelId startLabel = LabelId.create();
                LabelId bodyLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId continueLabel = LabelId.create();
                Type collType = driver.inferExprType(fis.collection(), locals);
                Type elemType = Type.UnknownType.UNKNOWN;
                boolean isList = BuiltinTypes.isList(collType);
                if (isList) elemType = driver.listElementType(collType);
                else if (collType instanceof Type.ArrayType at) elemType = at.componentType();
                int collIdx = localIdx++;
                int idxIdx = localIdx++;
                int varIdx = localIdx++;
                locals.add(new IRLocalVariable(collIdx, "#coll", collType));
                locals.add(new IRLocalVariable(idxIdx, "#idx", Type.PrimitiveType.INT));
                locals.add(new IRLocalVariable(varIdx, fis.varName(), elemType));
                localIdx = driver.emitExpression(fis.collection(), ops, owner, localIdx, locals);
                ops.add(new KofStoreLocal(collType, collIdx));
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofLabel(startLabel));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofLoadLocal(collType, collIdx));
                if (isList) {
                    ops.add(new KofCall(collType, "kof_list_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
                } else {
                    ops.add(new KofArrayLength());
                }
                ops.add(new KofConditionalJump(KofComparison.LT, bodyLabel, endLabel));
                ops.add(new KofLabel(bodyLabel));
                ops.add(new KofLoadLocal(collType, collIdx));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxIdx));
                if (isList) {
                    ops.add(new KofCall(collType, "kof_list_get", List.of(Type.PrimitiveType.INT), elemType, KofCallKind.INSTANCE));
                } else {
                    ops.add(new KofArrayLoad(elemType));
                }
                ops.add(new KofStoreLocal(elemType, varIdx));
                driver.breakLabels.push(endLabel);
                driver.continueLabels.push(continueLabel);
                localIdx = driver.emitStatement(fis.body(), ops, owner, localIdx, locals, returnType);
                driver.breakLabels.pop();
                driver.continueLabels.pop();
                ops.add(new KofLabel(continueLabel));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                ops.add(new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT));
                ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case ThrowStmt ts -> {
                localIdx = driver.emitExpression(ts.expression(), ops, owner, localIdx, locals);
                Type excType = driver.inferExprType(ts.expression(), locals);
                if (BuiltinTypes.isString(excType) && driver.target == Target.JVM) {
                    int tmp = localIdx++;
                    locals.add(new IRLocalVariable(tmp, "#exc", BuiltinTypes.STRING));
                    ops.add(new KofStoreLocal(BuiltinTypes.STRING, tmp));
                    Type runtimeExc = new Type.ClassType("java.lang", "RuntimeException", List.of());
                    ops.add(new KofNewObject(runtimeExc, List.of(BuiltinTypes.STRING)));
                    ops.add(new KofDup());
                    ops.add(new KofLoadLocal(BuiltinTypes.STRING, tmp));
                    ops.add(new KofCall(runtimeExc, "<init>", List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                }
                ops.add(new KofThrow());
                yield localIdx;
            }
            case AssertStmt asrt -> {
                localIdx = driver.emitExpression(asrt.condition(), ops, owner, localIdx, locals);
                LabelId okLabel = LabelId.create();
                LabelId failLabel = LabelId.create();
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                ops.add(new KofConditionalJump(KofComparison.EQ, failLabel, okLabel));
                ops.add(new KofLabel(failLabel));
                String message = asrt.message() != null ? asrt.message() : "assertion failed";
                if (driver.target == Target.JVM) {
                    int tmp = localIdx++;
                    locals.add(new IRLocalVariable(tmp, "#exc", BuiltinTypes.STRING));
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, message));
                    ops.add(new KofStoreLocal(BuiltinTypes.STRING, tmp));
                    Type runtimeExc = new Type.ClassType("java.lang", "RuntimeException", List.of());
                    ops.add(new KofNewObject(runtimeExc, List.of(BuiltinTypes.STRING)));
                    ops.add(new KofDup());
                    ops.add(new KofLoadLocal(BuiltinTypes.STRING, tmp));
                    ops.add(new KofCall(runtimeExc, "<init>", List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                } else {
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, message));
                }
                ops.add(new KofThrow());
                ops.add(new KofLabel(okLabel));
                yield localIdx;
            }
            case SpawnStmt ss -> {
                if (driver.target.isNative()) {
                    // CONC001 fechado: pthread_create no runtime nativo
                    LambdaExpr leN = ss.expression() instanceof LambdaExpr l1 ? l1
                            : new LambdaExpr(ss.position(), List.of(),
                                    List.of(new ExpressionStmt(ss.position(), ss.expression())));
                    Type.FunctionType ftN = new Type.FunctionType(List.of(), Type.PrimitiveType.VOID, null);
                    List<IRLocalVariable> capN = driver.collectCaptures(leN, locals);
                    List<IRLocalVariable> effN = driver.lambdaEffectiveCaptures.get(leN);
                    if (effN != null) capN = effN;
                    String lambdaClassN = driver.lambdaClass(leN, ftN, capN, true);
                    Type taskTypeN = new Type.ClassType("", lambdaClassN, List.of());
                    List<Type> capTypesN = new ArrayList<>();
                    for (IRLocalVariable cap : capN) capTypesN.add(cap.type());
                    ops.add(new KofNewObject(taskTypeN, capTypesN));
                    ops.add(new KofDup());
                    for (IRLocalVariable cap : capN) {
                        ops.add(new KofLoadLocal(cap.type(), cap.index()));
                    }
                    ops.add(new KofCall(taskTypeN, "<init>", capTypesN,
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                    ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_spawn", List.of(taskTypeN), Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                LambdaExpr le;
                if (ss.expression() instanceof LambdaExpr le0) {
                    le = le0;
                } else {
                    le = new LambdaExpr(ss.position(), List.of(),
                            List.of(new ExpressionStmt(ss.position(), ss.expression())));
                }
                Type.FunctionType ft = new Type.FunctionType(List.of(), Type.PrimitiveType.VOID, null);
                // capturas: spawn { println(x + 1) } deve empilhar x no construtor
                // (antes: List.of() → x resolvia para `this` → VerifyError)
                List<IRLocalVariable> captures = driver.collectCaptures(le, locals);
                List<IRLocalVariable> effective = driver.lambdaEffectiveCaptures.get(le);
                if (effective != null) captures = effective;
                String lambdaClass = driver.lambdaClass(le, ft, captures, true);
                Type taskType = new Type.ClassType("", lambdaClass, List.of());
                List<Type> captureTypes = new ArrayList<>();
                for (IRLocalVariable cap : captures) captureTypes.add(cap.type());
                ops.add(new KofNewObject(taskType, captureTypes));
                ops.add(new KofDup());
                for (IRLocalVariable cap : captures) {
                    ops.add(new KofLoadLocal(cap.type(), cap.index()));
                }
                ops.add(new KofCall(taskType, "<init>", captureTypes,
                        Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                        "kof_spawn", List.of(taskType), Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                yield localIdx;
            }
            case TryStmt ts -> {
                LabelId tryStart = LabelId.create();
                LabelId tryEnd = LabelId.create();
                LabelId doneLabel = LabelId.create();
                boolean hasFinally = !ts.finallyBody().isEmpty();
                LabelId finallyLabel = LabelId.create();
                LabelId rethrowLabel = hasFinally ? LabelId.create() : doneLabel;
                LabelId catchAllLabel = LabelId.create();
                LabelId primaryHandler = LabelId.create();
                boolean hasCatch = !ts.catchClauses().isEmpty();
                String primaryExcType = hasCatch ? ts.catchClauses().getFirst().exceptionType() : "Throwable";
                int primaryExcLocal = localIdx++;
                if (hasCatch) {
                    locals.add(new IRLocalVariable(primaryExcLocal, ts.catchClauses().getFirst().exceptionName(),
                            CompilerTypes.toType(primaryExcType, driver.currentUnit)));
                } else if (hasFinally) {
                    locals.add(new IRLocalVariable(primaryExcLocal, "#excTmp",
                            new Type.ClassType("java.lang", "Throwable", List.of())));
                }
                ops.add(new KofTryStart(tryStart, tryEnd,
                        hasCatch ? primaryHandler : catchAllLabel, primaryExcType, primaryExcLocal));
                for (StatementNode s : ts.tryBody()) {
                    localIdx = driver.emitStatement(s, ops, owner, localIdx, locals, returnType);
                }
                ops.add(new KofJump(finallyLabel));
                ops.add(new KofLabel(tryEnd));
                for (int ci = 0; ci < ts.catchClauses().size(); ci++) {
                    CatchClause cc = ts.catchClauses().get(ci);
                    LabelId handlerLabel = ci == 0 ? primaryHandler : LabelId.create();
                    int excIdx = ci == 0 ? primaryExcLocal : localIdx++;
                    if (ci > 0) {
                        locals.add(new IRLocalVariable(excIdx, cc.exceptionName(), CompilerTypes.toType(cc.exceptionType(), driver.currentUnit)));
                    }
                    ops.add(new KofCatchStart(handlerLabel, cc.exceptionType(), excIdx));
                    localIdx = driver.emitStatement(new BlockStmt(cc.position(), cc.body()), ops, owner, localIdx, locals, returnType);
                    ops.add(new KofJump(finallyLabel));
                }
                if (hasFinally) {
                    int excTmp = hasCatch ? localIdx++ : primaryExcLocal;
                    if (hasCatch) {
                        locals.add(new IRLocalVariable(excTmp, "#excTmp",
                                new Type.ClassType("java.lang", "Throwable", List.of())));
                    }
                    ops.add(new KofCatchStart(catchAllLabel, "Throwable", excTmp));
                    ops.add(new KofJump(rethrowLabel));
                    ops.add(new KofTryEnd());
                    ops.add(new KofLabel(finallyLabel));
                    for (StatementNode s : ts.finallyBody()) {
                        localIdx = driver.emitStatement(s, ops, owner, localIdx, locals, returnType);
                    }
                    ops.add(new KofJump(doneLabel));
                    ops.add(new KofLabel(rethrowLabel));
                    for (StatementNode s : ts.finallyBody()) {
                        localIdx = driver.emitStatement(s, ops, owner, localIdx, locals, returnType);
                    }
                    ops.add(new KofLoadLocal(new Type.ClassType("java.lang", "Throwable", List.of()), excTmp));
                    ops.add(new KofThrow());
                } else {
                    ops.add(new KofTryEnd());
                    ops.add(new KofLabel(finallyLabel));
                }
                ops.add(new KofLabel(doneLabel));
                yield localIdx;
            }
            case SwitchStmt ss -> {
                localIdx = SwitchStmtLowerer.lowerSwitchStmt(driver, ss, ops, owner, localIdx, locals, returnType);
                yield localIdx;
            }
            default -> localIdx;
        };
    }
}
