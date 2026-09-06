package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de switch-statement (case SwitchStmt do StatementLowerer).
 */
final class SwitchStmtLowerer {

    private SwitchStmtLowerer() {}

    static int lowerSwitchStmt(CompilerDriver driver, SwitchStmt ss, List<KofOperation> ops,
                                 String owner, int localIdx, List<IRLocalVariable> locals, Type returnType) {
LabelId endLabel = LabelId.create();
LabelId defaultLabel = LabelId.create();
Type switchType = ExpressionTyper.inferExprType(driver, ss.expression(), locals);
// ── exaustividade: switch sobre enum precisa cobrir todas as
// constantes ou ter default (nunca cair silenciosamente)
boolean enumSwitch = false;
java.util.List<String> missing = java.util.List.of();
if (switchType instanceof Type.ClassType sct && sct.packageName().isEmpty()
        && !CompilerTypes.enumConstantsOf(sct.name(), driver.currentUnit).isEmpty()) {
    enumSwitch = true;
    java.util.Set<String> covered = new java.util.HashSet<>();
    for (SwitchCase sc : ss.cases()) {
        String cn = CompilerTypes.enumConstantOfExpr(sc.value(), driver.currentUnit);
        if (cn != null) covered.add(cn);
    }
    missing = CompilerTypes.enumConstantsOf(sct.name(), driver.currentUnit).stream()
            .filter(c -> !covered.contains(c)).toList();
    if (!missing.isEmpty() && ss.defaultBody().isEmpty()
            && driver.currentDiagnostics != null) {
        driver.currentDiagnostics.error(ss.position() != null ? ss.position().file() : "",
                ss.position() != null ? ss.position().line() : 0,
                ss.position() != null ? ss.position().column() : 0, 0,
                "switch sobre '" + sct.name() + "' não cobre: "
                        + String.join(", ", missing)
                        + " (adicione default ou os casos faltantes)",
                "SEM031");
    }
}
int switchTmp = localIdx++;
localIdx = ExpressionLowerer.emitExpression(driver, ss.expression(), ops, owner, localIdx, locals);
ops.add(new KofStoreLocal(switchType, switchTmp));
locals.add(new IRLocalVariable(switchTmp, "#switch", switchType));
boolean hasPattern = ss.cases().stream().anyMatch(sc -> sc.value() instanceof PatternExpr);
if (hasPattern) {
    // Pattern switch lowered as if-else chain (no switch subject needed beyond #switch)
    List<LabelId> bodyLabels = new ArrayList<>();
    List<LabelId> nextTestLabels = new ArrayList<>();
    for (int i = 0; i < ss.cases().size(); i++) {
        bodyLabels.add(LabelId.create());
        nextTestLabels.add(LabelId.create());
    }
    LabelId endLabelPat = LabelId.create();
    LabelId defaultLabelPat = ss.defaultBody().isEmpty() ? endLabelPat : LabelId.create();
    for (int i = 0; i < ss.cases().size(); i++) {
        if (i > 0) ops.add(new KofLabel(nextTestLabels.get(i)));
        SwitchCase sc = ss.cases().get(i);
        LabelId nextTest = i + 1 < ss.cases().size() ? nextTestLabels.get(i + 1) : defaultLabelPat;
        if (sc.value() instanceof PatternExpr pe) {
            Type patType = CompilerTypes.toType(pe.typeName(), driver.currentUnit);
            if (patType instanceof Type.UnknownType) patType = BuiltinTypes.STRING;
            ops.add(new KofLoadLocal(switchType, switchTmp));
            ops.add(new KofInstanceOf(patType));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofConditionalJump(KofComparison.EQ, nextTest, bodyLabels.get(i)));
        } else {
            ops.add(new KofLoadLocal(switchType, switchTmp));
            localIdx = ExpressionLowerer.emitExpression(driver, sc.value(), ops, owner, localIdx, locals);
            ops.add(new KofBinary(KofBinaryOp.EQ, switchType));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofConditionalJump(KofComparison.EQ, nextTest, bodyLabels.get(i)));
        }
    }
    ops.add(new KofLabel(defaultLabelPat));
    if (!ss.defaultBody().isEmpty()) {
        localIdx = driver.emitStatement(new BlockStmt(ss.defaultBody().get(0).position(), ss.defaultBody()), ops, owner, localIdx, locals, returnType);
    }
    ops.add(new KofJump(endLabelPat));
    for (int i = 0; i < ss.cases().size(); i++) {
        SwitchCase sc = ss.cases().get(i);
        ops.add(new KofLabel(bodyLabels.get(i)));
        if (sc.value() instanceof PatternExpr pe) {
            Type patType = CompilerTypes.toType(pe.typeName(), driver.currentUnit);
            if (patType instanceof Type.UnknownType) patType = BuiltinTypes.STRING;
            ops.add(new KofLoadLocal(switchType, switchTmp));
            ops.add(new KofCheckCast(patType));
            if (pe.varName() != null) {
                int varIdx = localIdx++;
                locals.add(new IRLocalVariable(varIdx, pe.varName(), patType));
                ops.add(new KofStoreLocal(patType, varIdx));
            } else if (!pe.fieldVars().isEmpty()) {
                int castTmp = localIdx++;
                locals.add(new IRLocalVariable(castTmp, "#patCast", patType));
                ops.add(new KofStoreLocal(patType, castTmp));
                java.util.List<String> fieldNames = pe.fieldVars();
                for (int fi = 0; fi < fieldNames.size(); fi++) {
                    String fieldVar = fieldNames.get(fi);
                    Type fieldType = Type.UnknownType.UNKNOWN;
                    for (AstNode d : driver.currentUnit.declarations()) {
                        if (d instanceof RecordDeclarationNode rec && rec.name().equals(pe.typeName())) {
                            if (fi < rec.components().size()) {
                                fieldType = CompilerTypes.toType(rec.components().get(fi).type(), driver.currentUnit);
                            }
                            break;
                        }
                    }
                    if (fieldType instanceof Type.UnknownType) fieldType = BuiltinTypes.STRING;
                    ops.add(new KofLoadLocal(patType, castTmp));
                    Type fieldOwner = patType;
                    String fieldName = null;
                    for (AstNode d : driver.currentUnit.declarations()) {
                        if (d instanceof RecordDeclarationNode rec && rec.name().equals(pe.typeName())) {
                            if (fi < rec.components().size()) fieldName = rec.components().get(fi).name();
                            break;
                        }
                    }
                    if (fieldName == null) fieldName = fieldVar;
                    ops.add(new KofLoadField(fieldOwner, fieldName, fieldType));
                    int varIdx = localIdx++;
                    locals.add(new IRLocalVariable(varIdx, fieldVar, fieldType));
                    ops.add(new KofStoreLocal(fieldType, varIdx));
                }
            }
        }
        localIdx = driver.emitStatement(new BlockStmt(sc.position(), sc.body()), ops, owner, localIdx, locals, returnType);
        ops.add(new KofJump(endLabelPat));
    }
    ops.add(new KofLabel(endLabelPat));
    return localIdx;
}
List<LabelId> testLabels = new ArrayList<>();
List<LabelId> bodyLabels = new ArrayList<>();
for (int i = 0; i < ss.cases().size(); i++) {
    testLabels.add(LabelId.create());
    bodyLabels.add(LabelId.create());
}
for (int i = 0; i < ss.cases().size(); i++) {
    if (i > 0) ops.add(new KofLabel(testLabels.get(i)));
    SwitchCase sc = ss.cases().get(i);
    ops.add(new KofLoadLocal(switchType, switchTmp));
    localIdx = ExpressionLowerer.emitExpression(driver, sc.value(), ops, owner, localIdx, locals);
    if (enumSwitch) {
        // comparação por conteúdo (o valor do enum é o nome)
        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        ops.add(new KofConditionalJump(KofComparison.NE, bodyLabels.get(i),
                i + 1 < ss.cases().size() ? testLabels.get(i + 1) : defaultLabel));
    } else if (Type.isString(switchType)) {
        // bug 4: switch de String usava SUB (switchValue - case)
        // → String - String gerava bytecode inválido no JVM.
        // Igualdade de String é por conteúdo (kof_string_equals).
        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        ops.add(new KofConditionalJump(KofComparison.NE, bodyLabels.get(i),
                i + 1 < ss.cases().size() ? testLabels.get(i + 1) : defaultLabel));
    } else {
        ops.add(new KofBinary(KofBinaryOp.SUB, switchType));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        ops.add(new KofConditionalJump(KofComparison.EQ, bodyLabels.get(i),
                i + 1 < ss.cases().size() ? testLabels.get(i + 1) : defaultLabel));
    }
}
for (int i = 0; i < ss.cases().size(); i++) {
    SwitchCase sc = ss.cases().get(i);
    ops.add(new KofLabel(bodyLabels.get(i)));
    localIdx = driver.emitStatement(new BlockStmt(sc.position(), sc.body()), ops, owner, localIdx, locals, returnType);
    ops.add(new KofJump(endLabel));
}
ops.add(new KofLabel(defaultLabel));
if (!ss.defaultBody().isEmpty()) {
    localIdx = driver.emitStatement(new BlockStmt(ss.defaultBody().get(0).position(), ss.defaultBody()), ops, owner, localIdx, locals, returnType);
}
ops.add(new KofLabel(endLabel));
        return localIdx;
    }
}