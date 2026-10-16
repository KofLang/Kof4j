package dev.kof.compiler;

import java.util.List;

/**
 * Declaração de local com boxing de representação (braço VarDecl do
 * `StatementLowerer`, extraído no split ≤500 do §307 — 18/09): erasure-box
 * do bug 15/#57 e o gate cru §295(b) do slot `Nullable(primitivo)`. Estrutura-only;
 * comportamento idêntico (freeze rule 3).
 */
final class StatementLowererLocalBoxing {

    private StatementLowererLocalBoxing() {}

    static int emitLocalDeclaration(CompilerDriver driver, VarDeclStmt vds,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
        // §179: usa a resolução semântica (qualifyDeep) — sem ela o tipo
        // declarado kof.ui/kof.media saía ClassType("", "Label") e o
        // store local virava `astore` sobre handle `int` (VerifyError).
        Type varType = CompilerTypes.toType(vds.type(), driver.currentUnit, driver.semanticAnalyzer);
        // D-NULL-INTENT (#278): o fold §125-ext (`Int? v = if (c) x
        // else null` -> ramo null vira default) só continua vivo no
        // Native (fase 2, DECISIONS.md — representação antiga
        // preservada lá). JVM/Script/JS deixam o ramo null real; o
        ExpressionNode vdInit = vds.initializer();
        // nullable de REFERÊNCIA é constraint de compile-time: o
        // storage é o inner (a referência já é nullable por si só).
        // Nullable(primitivo) é DIFERENTE desde o #278: o storage
        // PRECISA continuar Nullable (boxed — Commit B já ensina
        // storeVarOpcode/loadVarOpcode/isDoubleWidth a despachar
        // ASTORE/ALOAD/1-slot para ele) — desembrulhar aqui devolvia
        // ao slot bruto e perdia a distinção null/default.
        if (varType instanceof Type.NullableType nt
                && !(nt.inner() instanceof Type.PrimitiveType)) {
            varType = nt.inner();
        }
        if (driver.mutatedCapturedNames.contains(vds.name())) {
            return CapturedVarBox.emit(driver, vds, vdInit, ops, owner, localIdx, locals);
        }
        if (vdInit != null) {
            Type initType = ExpressionTyper.inferExprType(driver, vdInit, locals);
            if (Type.isVoid(initType)) {
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(vds.position() != null ? vds.position().file() : "",
                            vds.position() != null ? vds.position().line() : 0,
                            vds.position() != null ? vds.position().column() : 0, 0,
                            "assignment to '" + vds.name() + "' received a void value — the"
                                    + " call does not return a value",
                            "SEM033");
                }
                return localIdx;
            }
            localIdx = ExpressionLowerer.emitExpression(driver, vdInit, ops, owner, localIdx, locals);
            if ("var".equals(vds.type()) || "val".equals(vds.type())) {
                varType = ExpressionTyper.inferExprType(driver, vdInit, locals);
                // JVM-01 (audit 0.6.0, §671): an INFERRED lambda-typed local must
                // be typed as the synthetic INTERFACE, not the concrete LambdaN
                // class of the first lambda — otherwise a second lambda of the
                // same signature (Lambda1) stored into the Lambda0-typed slot is
                // a VerifyError. Declared types keep the else branch (bug 8).
                if (varType instanceof Type.FunctionType vft && vft.className() != null
                        && !(vft.returnType() instanceof Type.FunctionType)) {
                    varType = new Type.FunctionType(vft.parameterTypes(), vft.returnType(), null);
                }
                // spawn-expr: pina Handle<T> com T do corpo (a inferência genérica pode ter perdido o typeArgument)
                if (vdInit instanceof MethodCallExpr sm && "__kof_spawn_expr".equals(sm.methodName())
                        && varType instanceof Type.ClassType hct && "kof.concurrent".equals(hct.packageName())
                        && (hct.typeArguments().isEmpty() || hct.typeArguments().get(0) instanceof Type.UnknownType)) {
                    // #141: usa inferLambdaBodyType (mesmo chokepoint do MethodCallTyper/lowerer), NÃO inferExprType direto —
                    // no corpo-bloco de expressão única este dava VOID e Handle<Void> poluía o local p/ o `await`.
                    ExpressionNode spawnBody = sm.arguments().get(0);
                    Type t = spawnBody instanceof LambdaExpr sle
                            ? ExpressionTyper.inferLambdaBodyType(driver, sle, locals)
                            : ExpressionTyper.inferExprType(driver, spawnBody, locals);
                    varType = new Type.ClassType("kof.concurrent", "Handle", List.of(t));
                }
            } else {
                Type initT = ExpressionTyper.inferExprType(driver, vdInit, locals);
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
                    // #845: `Int j = i` com `i: Int?` estreitado — abre a caixa
                    // pela origem antes do widening (o slot de i segue boxed)
                    initT = CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops, initT, varType);
                    driver.emitWideningIfNeeded(ops, initT, varType);
                    // §696: estreita o store num slot Byte/Short (o widening
                    // acima é NO-OP para esses destinos)
                    CompilerEmissionHelpers.emitStoreNarrow(driver, ops, initT, varType);
                    // §736: um `T?` (Nullable(primitivo)) usado como valor de
                    // um local PRIMITIVO CRU chega FISICAMENTE boxed — o slot
                    // de origem é boxed desde D-NULL-INTENT — mas o slot de
                    // destino é o primitivo crú, então sem o unbox o `istore`
                    // recebe um Integer (VerifyError "Integer not assignable
                    // to integer"). Espelho EXATO do caminho de ARGUMENTO
                    // (#770, CompilerEmission2). Cobre o estreitamento por
                    // `if (x != null)` e os produtores erased (Map.get) e o
                    // getter de campo de record nullable.
                    if (varType instanceof Type.PrimitiveType
                            && initT instanceof Type.NullableType unboxNt
                            && unboxNt.inner() instanceof Type.PrimitiveType) {
                        driver.emitErasureUnboxSoft(ops, varType);
                    }
                }
            }
        }
        // bug 15: `Object n = 42` — primitivo atribuído a referência:
        // boxa no JVM (JS/Native já são untyped). Sem isso o store de
        // int num slot Object invalidava o bytecode.
        // (#57: IfExpr/switch heterogêneo já boxeou in-branch → pular)
        // §295(b): o slot de um Nullable(primitivo) local é boxed desde
        // o Commit B (storeVarOpcode→ASTORE, 1 slot) mas o gate de box
        // só conhecia erasesToReference — FALSE p/ NullableType — e
        // `Int? v = 5` saía `iconst_5; astore_1` (VerifyError no LOAD
        // da classe, rosto "JavaFX ausente"). Espelha o gate cru do
        // return (D-NULL-INTENT, ReturnValueLowerer): boxa só
        // primitivo CRÚ — init já Nullable (`Int? g = m.get(...)`)
        // chega fisicamente boxed; re-box = §294-2a, nunca. No Native
        // o varType já foi desembrulhado acima (representação antiga).
        boolean nullablePrimSlot = TypeMetrics.isNullablePrimitive(varType);
        Type vdBoxT = vdInit != null
                ? ExpressionTyper.inferExprType(driver, vdInit, locals) : null;
        // #259: o box do slot Nullable(primitivo) usa o inner DECLARADO, não
        // o tipo do initializer — o widening acima já converteu o valor da
        // pilha para o destino, e boxar pelo tipo de ORIGEM empilhava um
        // valor de um tipo e chamava o boxer de outro:
        //   `Double? d = 2`   -> i2d (double)  + Integer.valueOf(int)  VerifyError
        //   `Float?  x = 2.5` -> f   (float)   + Double.valueOf(double) frame crash
        //   `Long?   l = 2`   -> i2l (long)    + Integer.valueOf(int)  VerifyError
        // O gate de re-box continua no tipo de ORIGEM (vdBoxT cru): init já
        // Nullable (`Int? g = m.get(...)`) chega fisicamente boxed — §294-2a.
        Type slotBoxT = nullablePrimSlot && varType instanceof Type.NullableType nts
                && nts.inner() instanceof Type.PrimitiveType ? nts.inner() : vdBoxT;
        if (driver.erasesToReference(varType)
                && vdInit != null
                && TypeMetrics.isPrimitiveType(vdBoxT)
                && !ExpressionTyper.boxesOwnBranches(driver, vdInit, locals)) {
            driver.emitErasureBox(ops, vdBoxT);
        } else if (nullablePrimSlot
                && vdBoxT instanceof Type.PrimitiveType ipt
                && !Type.isVoid(ipt)
                && !ExpressionTyper.boxesOwnBranches(driver, vdInit, locals)) {
            driver.emitErasureBox(ops, slotBoxT);
        }
        // declaração sem inicializador: default (0 primitivo / null
        // referência) — antes o store saía de pilha vazia (frame crash).
        // §295(b): slot boxed de Nullable(primitivo) é referência de
        // verdade (Commit B) → default null, espelhando `String? s;`
        // (unassigned ≠ 0). Native não chega aqui com o wrapper.
        if (vdInit == null) {
            // audit 0.6.0 JVM-02 (ledger §664): slot de FunctionType é
            // referência (classe sintética da lambda) — default null, não
            // iconst_0 (iconst_0; astore_1 = VerifyError no load).
            ops.add(driver.erasesToReference(varType) || nullablePrimSlot
                    || varType instanceof Type.FunctionType
                    ? new KofLoadLiteral(varType, null)
                    : new KofLoadLiteral(varType, 0));
        }
        ops.add(new KofStoreLocal(varType, localIdx));
        locals.add(new IRLocalVariable(localIdx, vds.name(), varType));
        return localIdx + (TypeMetrics.isDoubleWidth(varType) ? 2 : 1);
    }
}
