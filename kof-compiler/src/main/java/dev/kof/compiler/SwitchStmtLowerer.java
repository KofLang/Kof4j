package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de switch-statement (case SwitchStmt do StatementLowerer).
 */
public final class SwitchStmtLowerer {

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
// #445: o driver.lower() roda sobre a unidade MESCLADA do módulo
// (parseAndMerge), então enumConstantsOf resolve enum de arquivo
// importado por nome simples; exigir pacote vazio aqui era o modelo
// pré-D-ENUM207 e fazia switch cross-file cair no ramo numérico
// (SUB → isub sobre referências = VerifyError mascarado por JavaFX).
if (switchType instanceof Type.ClassType sct
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
                "switch on '" + sct.name() + "' does not cover: "
                        + String.join(", ", missing)
                        + " (add a default or the missing cases)",
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
            if (TypeMetrics.isPrimitiveType(patType)) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition pp = pe.position();
                    driver.currentDiagnostics.error(pp != null ? pp.file() : "",
                            pp != null ? pp.line() : 0, pp != null ? pp.column() : 0, 0,
                            "case of primitive type is not supported in pattern matching "
                                    + "(use a reference type or the value directly)",
                            "SEM035");
                }
                return localIdx;
            }
            ops.add(new KofLoadLocal(switchType, switchTmp));
            ops.add(new KofInstanceOf(patType));
            // SG-014: guarda — depois do instanceof, o corpo é só alcançado
            // se a guarda avaliar true (a var é bound no início do corpo;
            // a guarda referencia a var pelo ACCESO por cast implícito)
            if (pe.guard() != null) {
                localIdx = emitGuard(driver, pe, patType, switchTmp, ops, owner, localIdx, locals);
            }
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofConditionalJump(KofComparison.EQ, nextTest, bodyLabels.get(i)));
        } else {
            ops.add(new KofLoadLocal(switchType, switchTmp));
            localIdx = ExpressionLowerer.emitExpression(driver, sc.value(), ops, owner, localIdx, locals);
            // #473: o valor do case é tipado por si (um literal `1` é Int)
            // enquanto o subject pode ser largo (`switch (x: Long)`). Sem a
            // promoção, `KofBinary(EQ, long)` emitia LCMP sobre [long, int] e
            // o frame calculation do ASM estourava (NegativeArraySizeException
            // em COMPUTE_FRAMES) — mesmo mecanismo do widening de `long == int`.
            Type caseValType = ExpressionTyper.inferExprType(driver, sc.value(), locals);
            driver.emitWideningIfNeeded(ops, caseValType, switchType);
            ops.add(new KofBinary(KofBinaryOp.EQ, switchType));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofConditionalJump(KofComparison.EQ, nextTest, bodyLabels.get(i)));
        }
    }
    // #588: defaultLabelPat IS endLabelPat when the default body is empty -
    // marking it here too (as well as at the method's true end below) gives
    // endLabelPat two KofLabel sites for one LabelId, so a KofJump(endLabelPat)
    // resolves to this EARLIER site instead of the true end - a self-jump.
    if (defaultLabelPat != endLabelPat) {
        ops.add(new KofLabel(defaultLabelPat));
    }
    if (!ss.defaultBody().isEmpty()) {
        localIdx = driver.emitStatement(new BlockStmt(ss.defaultBody().get(0).position(), ss.defaultBody()), ops, owner, localIdx, locals, returnType);
        ops.add(new KofJump(endLabelPat));
    }
    for (int i = 0; i < ss.cases().size(); i++) {
        SwitchCase sc = ss.cases().get(i);
        ops.add(new KofLabel(bodyLabels.get(i)));
        if (sc.value() instanceof PatternExpr pe) {
            Type patType = CompilerTypes.toType(pe.typeName(), driver.currentUnit);
            if (patType instanceof Type.UnknownType) patType = BuiltinTypes.STRING;
            if (TypeMetrics.isPrimitiveType(patType)) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition pp = pe.position();
                    driver.currentDiagnostics.error(pp != null ? pp.file() : "",
                            pp != null ? pp.line() : 0, pp != null ? pp.column() : 0, 0,
                            "case of primitive type is not supported in pattern matching "
                                    + "(use a reference type or the value directly)",
                            "SEM035");
                }
                return localIdx;
            }
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
                    int varIdx = localIdx;
                    localIdx += TypeMetrics.isDoubleWidth(fieldType) ? 2 : 1;
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
    // #473: promove o valor do case ao tipo do subject (literal Int em
    // `switch (Long)` precisa de I2L antes do KofBinary/EQ largo).
    Type caseValType = ExpressionTyper.inferExprType(driver, sc.value(), locals);
    driver.emitWideningIfNeeded(ops, caseValType, switchType);
    if (enumSwitch) {
        // D-ENUM207: as constantes são INSTÂNCIAS (singletons), a igualdade
        // é por IDENTIDADE (if_acmp) — não mais kof_string_equals sobre o
        // nome (que agora forçaria cast de Dir→String = CCE).
        ops.add(new KofBinary(KofBinaryOp.EQ, switchType));
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
    } else if (isWideOrFloating(switchType)) {
        // #471-regression (lane 499-505): o ramo SUB do stmt sobreviveu ao
        // "EQ não-SUB" de 2238bd0a só na face pattern/expression — sobre
        // LONG/DOUBLE o lsub/dsub deixa categoria 2 na pilha e o jump
        // int-if_icmpeq = VerifyError (medido: if_icmpeq @ long_2nd).
        // Igualdade larga/FP usa o MESMO KofBinary(EQ, switchType) da face
        // pattern (LCMP/FCMP/DCMP + IFEQ no backend; NaN nunca casa), e o
        // bool 0/1 resultante compara com 0 como INT — shape válido nos
        // 4 alvos (JVM/Script/JS/Native roteiam pelo mesmo IR).
        ops.add(new KofBinary(KofBinaryOp.EQ, switchType));
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

    /** SG-014: emite a guarda do pattern no teste (var referenciada via cast no subject). */
    private static int emitGuard(CompilerDriver driver, PatternExpr pe, Type patType,
                                 int switchTmp, List<KofOperation> ops, String owner,
                                 int localIdx, List<IRLocalVariable> locals) {
        // a guarda referencia a var do pattern; para emitir, bound temporário:
        // cast do subject num tmp #guardCast e a var disponível como local
        int castIdx = localIdx++;
        locals.add(new IRLocalVariable(castIdx, "#guardCast", patType));
        ops.add(new KofLoadLocal(switchTypeOf(locals, switchTmp, patType), switchTmp));
        ops.add(new KofCheckCast(patType));
        ops.add(new KofStoreLocal(patType, castIdx));
        List<IRLocalVariable> guardLocals = new ArrayList<>(locals);
        guardLocals.add(new IRLocalVariable(castIdx, pe.varName() != null ? pe.varName() : "#patCast", patType));
        return ExpressionLowerer.emitExpression(driver, pe.guard(), ops, owner, localIdx, guardLocals);
    }

    private static Type switchTypeOf(List<IRLocalVariable> locals, int switchTmp, Type fallback) {
        for (IRLocalVariable lv : locals) {
            if (lv.index() == switchTmp) return lv.type();
        }
        return fallback;
    }

    private static boolean isWideOrFloating(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (!(inner instanceof Type.PrimitiveType p)) return false;
        String n = Type.canonicalPrimitiveName(p.name());
        return "long".equals(n) || "float".equals(n) || "double".equals(n);
    }
}
