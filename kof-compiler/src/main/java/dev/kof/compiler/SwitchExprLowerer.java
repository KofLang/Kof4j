package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de switch-expression (emitSwitchExpr/Chain/Binding).
 */
public final class SwitchExprLowerer {

    private SwitchExprLowerer() {}

    static int emitSwitchExpr(CompilerDriver driver, SwitchExpr se, List<KofOperation> ops, String owner,
                               int localIdx, List<IRLocalVariable> locals) {
        Type switchType = ExpressionTyper.inferExprType(driver, se.expression(), locals);
        int switchTmp = localIdx++;
        localIdx = ExpressionLowerer.emitExpression(driver, se.expression(), ops, owner, localIdx, locals);
        ops.add(new KofStoreLocal(switchType, switchTmp));
        locals.add(new IRLocalVariable(switchTmp, "#switchExpr", switchType));
        return emitSwitchChain(driver, se.cases(), 0, se.defaultValue(), switchType, switchTmp,
                ops, owner, localIdx, locals);
    }

    static int emitSwitchChain(CompilerDriver driver, List<SwitchExprCase> cases, int i, ExpressionNode defaultValue,
                                Type switchType, int switchTmp, List<KofOperation> ops, String owner,
                                int localIdx, List<IRLocalVariable> locals) {
        if (i >= cases.size()) {
            if (defaultValue != null) {
                localIdx = ExpressionLowerer.emitExpression(driver, defaultValue, ops, owner, localIdx, locals);
                // #57/§70: corpos com tipos distintos → boxa ramo primitivo
                // in-branch (join só de referências); callers pulam pós-box.
                if (ExpressionTyper.branchTypesDiffer(ExpressionTyper.switchBranchTypes(
                        driver, cases, defaultValue, switchType, locals))) {
                    ExpressionTyper.boxPrimitiveBranch(driver, ops,
                            ExpressionTyper.inferExprType(driver, defaultValue, locals));
                }
                return localIdx;
            }
            ops.add(CompilerTypes.defaultValueOp(switchType));
            return localIdx;
        }
        SwitchExprCase sc = cases.get(i);
        LabelId bodyLabel = LabelId.create();
        LabelId elseLabel = LabelId.create();
        LabelId endLabel = LabelId.create();
        if (sc.value() instanceof PatternExpr pe) {
            Type patType = CompilerTypes.toType(pe.typeName(), driver.currentUnit);
            if (patType instanceof Type.UnknownType) patType = BuiltinTypes.STRING;
            if (TypeMetrics.isPrimitiveType(patType)) {
                // case de PRIMITIVO é ilegal no JVM (instanceof sobre int):
                // diagnóstico em compile-time, nunca VerifyError (bug 37).
                if (driver.currentDiagnostics != null) {
                    SourcePosition pp = pe.position();
                    driver.currentDiagnostics.error(pp != null ? pp.file() : "",
                            pp != null ? pp.line() : 0, pp != null ? pp.column() : 0, 0,
                            "case de tipo primitivo não é suportado em pattern matching "
                                    + "(use um tipo de referência ou o valor direto)",
                            "SEM035");
                }
                return localIdx;
            }
            ops.add(new KofLoadLocal(switchType, switchTmp));
            ops.add(new KofInstanceOf(patType));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofConditionalJump(KofComparison.NE, bodyLabel, elseLabel));
            ops.add(new KofLabel(bodyLabel));
            localIdx = emitPatternBinding(driver, pe, patType, switchType, switchTmp, ops, localIdx, locals);
        } else {
            ops.add(new KofLoadLocal(switchType, switchTmp));
            localIdx = ExpressionLowerer.emitExpression(driver, sc.value(), ops, owner, localIdx, locals);
            Type caseType = ExpressionTyper.inferExprType(driver, sc.value(), locals);
            if (Type.isString(switchType) || CompilerTypes.isEnumType(switchType, driver.currentUnit) || CompilerTypes.isEnumType(caseType, driver.currentUnit)) {
                // igualdade de String/enum é por conteúdo (bug 4 do statement)
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
            } else {
                ops.add(new KofBinary(KofBinaryOp.EQ, switchType));
            }
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofConditionalJump(KofComparison.NE, bodyLabel, elseLabel));
            ops.add(new KofLabel(bodyLabel));
        }
        localIdx = ExpressionLowerer.emitExpression(driver, sc.body(), ops, owner, localIdx, locals);
        // #57/§70: corpos com tipos distintos → boxa corpo primitivo in-branch.
        if (ExpressionTyper.branchTypesDiffer(ExpressionTyper.switchBranchTypes(
                driver, cases, defaultValue, switchType, locals))) {
            ExpressionTyper.boxPrimitiveBranch(driver, ops,
                    ExpressionTyper.inferExprType(driver, sc.body(), locals));
        }
        ops.add(new KofJump(endLabel));
        ops.add(new KofLabel(elseLabel));
        localIdx = emitSwitchChain(driver, cases, i + 1, defaultValue, switchType, switchTmp,
                ops, owner, localIdx, locals);
        ops.add(new KofLabel(endLabel));
        return localIdx;
    }

    /**
     * Prologue de binding de um case pattern de switch-expressão:
     * {@code case T v ->} → {@code v = (T)#switchExpr};
     * {@code case T(var x, var y) ->} → cast p/ {@code #patCast} + um
     * {@code getfield} por componente. No JS os slots são pré-declarados no
     * topo da função, então o {@code store} vira atribuição na sequência do
     * braço (ver parseExpressionFragment).
     */
    static int emitPatternBinding(CompilerDriver driver, PatternExpr pe, Type patType, Type switchType, int switchTmp,
                                   List<KofOperation> ops, int localIdx, List<IRLocalVariable> locals) {
        if (pe.varName() != null) {
            ops.add(new KofLoadLocal(switchType, switchTmp));
            ops.add(new KofCheckCast(patType));
            int varIdx = localIdx++;
            locals.add(new IRLocalVariable(varIdx, pe.varName(), patType));
            ops.add(new KofStoreLocal(patType, varIdx));
            return localIdx;
        }
        int castTmp = localIdx++;
        locals.add(new IRLocalVariable(castTmp, "#patCast", patType));
        ops.add(new KofLoadLocal(switchType, switchTmp));
        ops.add(new KofCheckCast(patType));
        ops.add(new KofStoreLocal(patType, castTmp));
        String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
        for (int fi = 0; fi < pe.fieldVars().size(); fi++) {
            String fieldVar = pe.fieldVars().get(fi);
            Type fieldType = Type.UnknownType.UNKNOWN;
            String fieldName = fieldVar;
            if (driver.currentUnit != null) {
                for (AstNode d : driver.currentUnit.declarations()) {
                    if (d instanceof RecordDeclarationNode rec && rec.name().equals(simple)) {
                        if (fi < rec.components().size()) {
                            fieldType = CompilerTypes.toType(rec.components().get(fi).type(), driver.currentUnit);
                            fieldName = rec.components().get(fi).name();
                        }
                        break;
                    }
                }
            }
            if (fieldType instanceof Type.UnknownType) fieldType = BuiltinTypes.STRING;
            ops.add(new KofLoadLocal(patType, castTmp));
            ops.add(new KofLoadField(patType, fieldName, fieldType));
            int varIdx = localIdx++;
            locals.add(new IRLocalVariable(varIdx, fieldVar, fieldType));
            ops.add(new KofStoreLocal(fieldType, varIdx));
        }
        return localIdx;
    }
}
