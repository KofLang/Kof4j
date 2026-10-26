package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de statements (emitStatementInner + switch-expr) do CompilerDriver.
 * Recebe o driver como host para os helpers compartilhados (emitExpression etc.).
 */
public final class StatementLowerer {

    private StatementLowerer() {}

    static int emitStatementInner(CompilerDriver driver, StatementNode stmt, List<KofOperation> ops, String owner, int localIdx,
                                   List<IRLocalVariable> locals, Type returnType) {
        return switch (stmt) {
            case ReturnStmt ret -> {
                // DD-01 (bug 45): return dentro de try/finally NÃO retorna
                // direto — store no slot do frame + jump p/ o epílogo que roda
                // o finally antes de retornar (training/idioms/errors.md:
                // "finally roda no caminho normal, no capturado e na propagação").
                if (!driver.finallyFrames.isEmpty()) {
                    CompilerDriverState.FinallyFrame f = driver.finallyFrames.peek();
                    // §551 residual (native catch-return): o frame é empilhado
                    // ANTES do corpo do try, mas `tryDepth` DENTRO do catch é
                    // `tryDepthSelf - 1` (o handler deste try já foi desempilhado
                    // por `kof_throw_string` ao capturar). O unlink deve espelhar
                    // exatamente os handlers VIVOS no ponto do return, não a
                    // profundidade de entrada do frame: um return no try precisa
                    // do unlink do próprio handler; um return no catch NÃO pode
                    // desempilhar o handler já consumido (double-pop corrompia a
                    // cadeia → SIGSEGV no próximo throw). O epílogo do finally
                    // não desvincula mais — o return site já o fez.
                    for (int i = 0; i < driver.tryDepth; i++) ops.add(new KofExcUnlink());
                    if (ret.value() != null) {
                        localIdx = ReturnValueLowerer.emitCoerced(driver, ret, returnType, ops, owner, localIdx, locals);
                        ops.add(new KofStoreLocal(returnType, f.slotValor()));
                    } else if (!Type.isVoid(returnType)) {
                        ops.add(CompilerTypes.defaultValueOp(returnType));
                        ops.add(new KofStoreLocal(returnType, f.slotValor()));
                    }
                    ops.add(new KofJump(f.returnFinallyLabel()));
                    yield localIdx;
                }
                // §551: return de dentro de região(is) try — desvincula o
                // handler nativo de cada região atravessada antes de sair da
                // função (sem isto o frame fica pendurado e o próximo throw
                // da cadeia dá UAF). O caso com finally ativo é tratado no
                // epílogo (frame empilhado), não aqui.
                for (int i = 0; i < driver.tryDepth; i++) ops.add(new KofExcUnlink());
                if (ret.value() != null) {
                    localIdx = ReturnValueLowerer.emitCoerced(driver, ret, returnType, ops, owner, localIdx, locals);
                    ops.add(new KofReturn(returnType));
                } else if (Type.isVoid(returnType)) {
                    ops.add(new KofReturnVoid());
                } else {
                    ops.add(CompilerTypes.defaultValueOp(returnType));
                    ops.add(new KofReturn(returnType));
                }
                yield localIdx;
            }
            case BreakStmt _ -> {
                if (!driver.breakLabels.isEmpty()) {
                    // §551: sair de N regiões try leva o handler nativo junto —
                    // desvincula da mais interna primeiro (delta = profundidade).
                    int targetDepth = driver.breakDepths.peek();
                    int delta = driver.tryDepth - targetDepth;
                    for (int i = 0; i < delta; i++) ops.add(new KofExcUnlink());
                    // §617 face A: `finally` sempre executa — um break que SAI
                    // de regiões try/finally precisa rodar o corpo do finally de
                    // cada frame atravessado (da mais interna p/ a mais externa)
                    // ANTES de saltar. JS não recebe o corpo inline (o
                    // try/finally nativo do JS já o executa no break).
                    if (driver.target != Target.JS) {
                        localIdx = emitExitedFinallyBodies(driver, ops, owner, localIdx, locals,
                                targetDepth, returnType);
                    }
                    ops.add(new KofJump(driver.breakLabels.peek()));
                }
                yield localIdx;
            }
            case ContinueStmt _ -> {
                if (!driver.continueLabels.isEmpty()) {
                    int targetDepth = driver.continueDepths.peek();
                    int delta = driver.tryDepth - targetDepth;
                    for (int i = 0; i < delta; i++) ops.add(new KofExcUnlink());
                    // §617 face A: idem break — o finally de cada frame
                    // atravessado roda antes do salto.
                    if (driver.target != Target.JS) {
                        localIdx = emitExitedFinallyBodies(driver, ops, owner, localIdx, locals,
                                targetDepth, returnType);
                    }
                    ops.add(new KofJump(driver.continueLabels.peek()));
                }
                yield localIdx;
            }
            case ExpressionStmt es -> {
                if (es.expression() != null) {
                    localIdx = ExpressionLowerer.emitExpression(driver, es.expression(), ops, owner, localIdx, locals);
                    if (driver.hasReturnValue(es.expression(), locals)) {
                        // SG-020/bug 79: descarte de valor de categoria-2 (Long/
                        // Double) exige POP2 — POP sobre long deixa o 2º slot na
                        // pilha e o verificador rejeita (Bad type on operand
                        // stack: long_2nd). Statement de await de Long era o
                        // caso canônico.
                        Type discardT = ExpressionTyper.inferExprType(driver, es.expression(), locals);
                        ops.add(TypeMetrics.isDoubleWidth(discardT)
                                ? new KofPop2() : new KofPop());
                    }
                }
                yield localIdx;
            }
            case VarDeclStmt vds -> StatementLowererLocalBoxing.emitLocalDeclaration(driver, vds, ops, owner, localIdx, locals);
            case BlockStmt block -> {
                int startSize = locals.size();
                int idx = localIdx;
                for (StatementNode s : block.statements()) {
                    idx = driver.emitStatement(s, ops, owner, idx, locals, returnType);
                }
                for (int i = startSize; i < locals.size(); i++) {
                    IRLocalVariable lv = locals.get(i);
                    if (!lv.name().startsWith("#")) {
                        locals.set(i, new IRLocalVariable(lv.index(), "#scopedVar$" + lv.name(), lv.type()));
                    }
                }
                yield idx;
            }
            case IfStmt ifStmt -> {
                LabelId elseLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId thenLabel = LabelId.create();
                ops.add(new KofStatementIf(thenLabel)); // §267
                if (ifStmt.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                    localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()), driver.comparisonOperandType(bin, locals), thenLabel, elseLabel));
                } else {
                    localIdx = CompilerComparisons.emitTruthinessJump(driver, ifStmt.condition(),
                            ops, owner, localIdx, locals, thenLabel, elseLabel);
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
                    localIdx = CompilerComparisons.emitTruthinessJump(driver, ws.condition(),
                            ops, owner, localIdx, locals, bodyLabel, endLabel);
                }
                ops.add(new KofLabel(bodyLabel));
                driver.breakLabels.push(endLabel);
                driver.continueLabels.push(startLabel);
                driver.breakDepths.push(driver.tryDepth);
                driver.continueDepths.push(driver.tryDepth);
                localIdx = driver.emitStatement(ws.body(), ops, owner, localIdx, locals, returnType);
                driver.breakLabels.pop();
                driver.continueLabels.pop();
                driver.breakDepths.pop();
                driver.continueDepths.pop();
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
                driver.breakDepths.push(driver.tryDepth);
                driver.continueDepths.push(driver.tryDepth);
                localIdx = driver.emitStatement(dws.body(), ops, owner, localIdx, locals, returnType);
                driver.breakLabels.pop();
                driver.continueLabels.pop();
                driver.breakDepths.pop();
                driver.continueDepths.pop();
                if (dws.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                    localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()), driver.comparisonOperandType(bin, locals), startLabel, endLabel));
                } else {
                    // Fase E (24/10, #776): roteado pelo helper central — o
                    // zero leva o TIPO da condicao (BOOL do WASI = i32).
                    localIdx = CompilerComparisons.emitTruthinessJump(driver, dws.condition(),
                            ops, owner, localIdx, locals, startLabel, endLabel);
                }
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case ForStmt fs -> {
                LabelId startLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId continueLabel = LabelId.create();
                LabelId bodyLabel = LabelId.create();
                int initLocalEntryIdx = locals.size();
                if (fs.init() != null) localIdx = driver.emitStatement(fs.init(), ops, owner, localIdx, locals, returnType);
                int initLocalEndIdx = locals.size();
                ops.add(new KofLabel(startLabel));
                if (fs.condition() != null) {
                    if (fs.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                        localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                        ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()), driver.comparisonOperandType(bin, locals), bodyLabel, endLabel));
                    } else {
                        // Fase E (24/10, #776): helper central — zero tipado.
                        localIdx = CompilerComparisons.emitTruthinessJump(driver, fs.condition(),
                                ops, owner, localIdx, locals, bodyLabel, endLabel);
                    }
                }
                ops.add(new KofLabel(bodyLabel));
                driver.breakLabels.push(endLabel);
                driver.continueLabels.push(continueLabel);
                driver.breakDepths.push(driver.tryDepth);
                driver.continueDepths.push(driver.tryDepth);
                localIdx = driver.emitStatement(fs.body(), ops, owner, localIdx, locals, returnType);
                driver.breakLabels.pop();
                driver.continueLabels.pop();
                driver.breakDepths.pop();
                driver.continueDepths.pop();
                ops.add(new KofContinueLabel(continueLabel, startLabel)); // §266
                ops.add(new KofLabel(continueLabel));
                if (fs.update() != null) {
                    if (fs.update() instanceof UnaryExpr ue
                            && ("++".equals(ue.operator()) || "--".equals(ue.operator()))
                            && ue.operand() instanceof IdentifierExpr id
                            && driver.findLocalVar(id.name(), locals) != null
                            && !driver.boxFactory.isBoxType(driver.findLocalVar(id.name(), locals).type())) {
                        IRLocalVariable var = driver.findLocalVar(id.name(), locals);
                        if (var != null) {
                            ops.add(new KofLoadLocal(var.type(), var.index()));
                            CompilerEmissionHelpers.emitIncrementStep(ops,
                                    "++".equals(ue.operator()) ? KofBinaryOp.ADD : KofBinaryOp.SUB, var.type());
                            ops.add(new KofStoreLocal(var.type(), var.index()));
                        }
                    } else {
                        localIdx = ExpressionLowerer.emitExpression(driver, fs.update(), ops, owner, localIdx, locals);
                        // KofPop2 width-aware (mesma regra do ExpressionStmt,
                        // SG-020/bug 79): update de 2 slots (ex.:
                        // `for (i; i<n; time.now())`) exigia POP2 — POP
                        // deixava o 2º slot na pilha (VerifyError/COMP002).
                        if (driver.hasReturnValue(fs.update(), locals)) {
                            Type updT = ExpressionTyper.inferExprType(driver, fs.update(), locals);
                            ops.add(TypeMetrics.isDoubleWidth(updT)
                                    ? new KofPop2() : new KofPop());
                        }
                    }
                }
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                // #182: libera SOMENTE os nomes declarados pelo init (ex.: `var i`).
                // Varrer ate locals.size() renomeava tbem as variaveis do CORPO
                // (ja empilhadas durante o loop) para o sentinel '#forInitVar',
                // que o backend JS trata como temp nao-declaravel (isCompilerTemp)
                // -> o `let` sumia e as leituras davam ReferenceError (#201).
                if (fs.init() != null) {
                    for (int li = initLocalEntryIdx; li < initLocalEndIdx; li++) {
                        IRLocalVariable lv = locals.get(li);
                        locals.set(li, new IRLocalVariable(lv.index(), "#forInitVar", lv.type()));
                    }
                }
                yield localIdx;
            }
            case ForInStmt fis -> {
                LabelId startLabel = LabelId.create();
                LabelId bodyLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId continueLabel = LabelId.create();
                Type collType = ExpressionTyper.inferExprType(driver, fis.collection(), locals);
                // #882/#879: a CLASSIFICAÇÃO do receptor olha o tipo SEM a anulabilidade
                // — o local ESTREITADO (`if (l != null)`) segue `Nullable(...)` no IR.
                Type collBase = Type.withoutNullable(collType);
                Type elemType = Type.UnknownType.UNKNOWN;
                boolean isList = BuiltinTypes.isList(collBase);
                boolean isString = BuiltinTypes.isString(collBase);
                if (isList) elemType = driver.listElementType(collBase);
                else if (isString) {
                    // EVOLUÇÃO (`for (var c in s)`, a diretiva da mantenedora
                    // 13/10): a variável do loop é Char (a unidade do charAt);
                    // o desugar emite os MESMOS KofCall INSTANCE length/charAt
                    // que os métodos de string emitem — os 4 alvos de graça.
                    elemType = Type.PrimitiveType.CHAR;
                }
                // #879: array — o tipo do local ESTREITADO (`if (b != null)`) é
                // `Nullable(ArrayType)`, então o elemento vem de `arrayElementType`
                // (que atravessa a anulabilidade), não de um `instanceof ArrayType`
                // cru: sem isto o `KofArrayLoad` do elemento saía como referência
                // (`aaload` sobre `[I`) — VerifyError na JVM, SIGSEGV nos nativos.
                else elemType = Type.arrayElementType(collType);
                int collIdx = localIdx++;
                int idxIdx = localIdx++;
                int varIdx = localIdx++;
                locals.add(new IRLocalVariable(collIdx, "#coll", collType));
                locals.add(new IRLocalVariable(idxIdx, "#idx", Type.PrimitiveType.INT));
                int varLocalEntryIdx = locals.size();
                locals.add(new IRLocalVariable(varIdx, fis.varName(), elemType));
                localIdx = ExpressionLowerer.emitExpression(driver, fis.collection(), ops, owner, localIdx, locals);
                ops.add(new KofStoreLocal(collType, collIdx));
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofLabel(startLabel));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofLoadLocal(collType, collIdx));
                if (isList) {
                    ops.add(new KofCall(collBase, "kof_list_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
                } else if (isString) {
                    ops.add(new KofCall(collBase, "length", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
                } else {
                    ops.add(new KofArrayLength());
                }
                ops.add(new KofConditionalJump(KofComparison.LT, bodyLabel, endLabel));
                ops.add(new KofLabel(bodyLabel));
                ops.add(new KofLoadLocal(collType, collIdx));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxIdx));
                if (isList) {
                    ops.add(new KofCall(collBase, "kof_list_get", List.of(Type.PrimitiveType.INT), elemType, KofCallKind.INSTANCE));
                } else if (isString) {
                    ops.add(new KofCall(collBase, "charAt", List.of(Type.PrimitiveType.INT), elemType, KofCallKind.INSTANCE));
                } else {
                    ops.add(new KofArrayLoad(elemType));
                }
                ops.add(new KofStoreLocal(elemType, varIdx));
                driver.breakLabels.push(endLabel);
                driver.continueLabels.push(continueLabel);
                driver.breakDepths.push(driver.tryDepth);
                driver.continueDepths.push(driver.tryDepth);
                localIdx = driver.emitStatement(fis.body(), ops, owner, localIdx, locals, returnType);
                driver.breakLabels.pop();
                driver.continueLabels.pop();
                driver.breakDepths.pop();
                driver.continueDepths.pop();
                ops.add(new KofContinueLabel(continueLabel, startLabel)); // §266
                ops.add(new KofLabel(continueLabel));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                ops.add(new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT));
                ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                // Issue #182: remove a variável do loop de locals para não sombrear
                // a variável externa homônima nas leituras posteriores ao loop.
                // Preserva o slot varIdx nos metadados renomeando para "#forInVar"
                // para que o backend JS / Native mantenha o mapeamento do slot.
                locals.set(varLocalEntryIdx, new IRLocalVariable(varIdx, "#forInVar", elemType));
                yield localIdx;
            }
            case ThrowStmt ts -> {
                localIdx = ExpressionLowerer.emitExpression(driver, ts.expression(), ops, owner, localIdx, locals);
                Type excType = ExpressionTyper.inferExprType(driver, ts.expression(), locals);
                // ANDROID reusa o JvmBackend e o mesmo contrato de excecao
                // String->RuntimeException do JVM (issue #777 hunt): sem isto o
                // `athrow` recebia a String crua -> VerifyError em toda app
                // Android com `throw`/`assert`.
                if (BuiltinTypes.isString(excType)
                        && (driver.target == Target.JVM || driver.target == Target.ANDROID)) {
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
                localIdx = ExpressionLowerer.emitExpression(driver, asrt.condition(), ops, owner, localIdx, locals);
                LabelId okLabel = LabelId.create();
                LabelId failLabel = LabelId.create();
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                ops.add(new KofConditionalJump(KofComparison.EQ, failLabel, okLabel));
                ops.add(new KofLabel(failLabel));
                String message = asrt.message() != null ? asrt.message() : "assertion failed";
                if (driver.target == Target.JVM || driver.target == Target.ANDROID) {
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
                        CompilerCaptures.pushCapture(driver, ops, cap);
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
                    CompilerCaptures.pushCapture(driver, ops, cap);
                }
                ops.add(new KofCall(taskType, "<init>", captureTypes,
                        Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                        "kof_spawn", List.of(taskType), Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                yield localIdx;
            }
            case TryStmt ts ->
                    StatementLowererTry.emitTryStmt(driver, ts, ops, owner, localIdx, locals, returnType);
            case SwitchStmt ss -> {
                localIdx = SwitchStmtLowerer.lowerSwitchStmt(driver, ss, ops, owner, localIdx, locals, returnType);
                yield localIdx;
            }
            default -> localIdx;
        };
    }

    /**
     * §617 face A: emite o corpo do `finally` de cada frame try/finally que o
     * `break`/`continue` ATRAVESSA (frames com {@code tryDepthSelf > targetDepth}),
     * da mais interna para a mais externa, ANTES do salto. Cada frame é retirado
     * da pilha enquanto seu corpo é emitido (um `return` dentro do finally
     * enxerga só os frames externos — como no epílogo real) e restaurado depois.
     * O contrato é normativo (`statements.md`: "`finally` sempre executa").
     */
    private static int emitExitedFinallyBodies(CompilerDriver driver, List<KofOperation> ops, String owner,
                                               int localIdx, List<IRLocalVariable> locals, int targetDepth,
                                               Type returnType) {
        List<CompilerDriverState.FinallyFrame> exited = new ArrayList<>();
        for (CompilerDriverState.FinallyFrame f : driver.finallyFrames) {
            if (f.tryDepthSelf() > targetDepth) {
                exited.add(f);
            } else {
                break;
            }
        }
        if (exited.isEmpty()) return localIdx;
        for (CompilerDriverState.FinallyFrame f : exited) {
            driver.finallyFrames.pop();
            for (StatementNode s : f.finallyBody()) {
                localIdx = driver.emitStatement(s, ops, owner, localIdx, locals, returnType);
            }
        }
        for (int i = exited.size() - 1; i >= 0; i--) {
            driver.finallyFrames.push(exited.get(i));
        }
        return localIdx;
    }
}
