package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de print/println no emitExpression.
 */
public final class ExpressionPrintLowerer {

    private ExpressionPrintLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
// #495: println() with no args prints a blank line via System.out.println()
if ("println".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    ops.add(new KofGetStatic(
            new Type.ClassType("java.lang", "System", List.of()),
            "out", new Type.ClassType("java.io", "PrintStream", List.of())));
    ops.add(new KofCall(
            new Type.ClassType("java.io", "PrintStream", List.of()),
            "println", List.of(), Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
    return localIdx;
}
if (("print".equals(mc.methodName()) || "println".equals(mc.methodName())) && mc.arguments().size() == 1) {
    Type printedType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if (Type.isVoid(printedType)) {
        // void não é um valor: println(f()) com f void empilhava
        // nada e o backend dava pop de lixo (segfault Native /
        // VerifyError JVM). Diagnóstico limpo em vez disso.
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    mc.methodName() + "(...) received a void value — the call does"
                            + " not return a value (add a 'return' or don't use it as an argument)",
                    "SEM033");
        }
        return localIdx;
    }
    if (!driver.fpSupportedOnNative(printedType, mc.position())) {
        return localIdx;
    }
    // §205 (Native): `println(if/switch com ramos de tipos distintos)` — o
    // join #183 dá `Object`, e o print de um `Object` não despacha no Native
    // (sem boxed-ABI polimórfico = §104b-ii). Em vez de gate (quebra a
    // paridade cross-target, freeze regra 5), rebaixamos a impressão POR
    // RAMO: a condição é avaliada UMA vez e só o ramo tomado imprime, cada
    // um pelo SEU tipo estático — o dispatch valueOf/println de primitivo e
    // String já existe e é testado em x86, riscv64 e aarch64 (aarch64 herda
    // via tradutor). Byte-identical ao JVM, sem ABI novo. Só dispara para o
    // print DIRETO do if/switch heterogêneo; via-var/as-Object ficam para
    // §104b-ii (a caixa real precisa de boxed print).
    if (driver.target.isNative() && BuiltinTypes.isObject(printedType)) {
        int direct = lowerHeterogeneousDirect(driver, mc, ops, owner, localIdx, locals);
        if (direct >= 0) return direct;
    }
    ops.add(new KofGetStatic(
            new Type.ClassType("java.lang", "System", List.of()),
            "out", new Type.ClassType("java.io", "PrintStream", List.of())));
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    Type argType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    // (#57: IfExpr/switch heterogêneo já boxeou in-branch → pular o box)
    if (CompilerTypes.isEnumType(argType, driver.currentUnit)) {
        // D-ENUM207: enum é instância real; imprime o NOME via toString()
        // da própria classe (paridade JVM/Script/JS/Native — o intérprete e
        // o JS não stringificam um objeto custom por valueOf(Object)).
        ops.add(new KofCall(argType, "toString", List.of(), BuiltinTypes.STRING,
                KofCallKind.INSTANCE));
    } else if (TypeMetrics.isPrimitiveType(argType)
            && !ExpressionTyper.boxesOwnBranches(driver, mc.arguments().get(0), locals)) {
        // D-PRINT (#168, maintainer 15/09): `println(Char)` imprime o
        // CARÁTER ("A"), nunca o code point ("65"), com ou sem aspas — a
        // antiga face numérica de §216 face 2 está SUPERSEDED (regra 4:
        // remove saída silenciosamente errada). Vale para os 4 alvos: o
        // `valueOf(C)` é o MESMO overload que `String.valueOf(c)` já usa e
        // que o `.toString()` de char usa (§27). O valor na pilha de um
        // `char` é int-width em todos os alvos (literal, `charAt`, storage
        // de coleção §104b-ii — que segue boxado como Integer, NÃO tocado):
        // reinterpretar como C é seguro. `Nullable(char)` desembrulha p/ a
        // checagem (get de Map devolve Nullable).
        Type charCheck = argType instanceof Type.NullableType nt ? nt.inner() : argType;
        boolean isCharPrimitive = charCheck instanceof Type.PrimitiveType p
                && "char".equals(Type.canonicalPrimitiveName(p.name()));
        if (isCharPrimitive) {
            // D-NULL-INTENT (#278): `argType` Nullable(Char) chega
            // FISICAMENTE boxed (Integer — return/local/Map.get do #278);
            // sem desempacotar, `String.valueOf(C)` receberia a referência
            // Integer onde espera um valor int-width (VerifyError). Um
            // `Char` CRU (não-nullable) já está int-width na pilha —
            // nenhum unbox extra.
            if (argType instanceof Type.NullableType) {
                CompilerEmissionHelpers.emitErasureUnboxSoft(driver, ops, Type.PrimitiveType.INT);
            }
            ops.add(new KofCall(
                    BuiltinTypes.STRING,
                    "valueOf", List.of(Type.PrimitiveType.CHAR),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        } else if (driver.target.isNative()) {
            // O dispatch nativo do valueOf decide pelo tipo do
            // parâmetro — aqui mapeia char→Int para imprimir o
            // codepoint sem quebrar String.valueOf(char).
            // §104b-ii (face print): o storage de coleção devolve
            // Nullable(char) (get de Map) — o INNER é que decide o
            // dispatch; sem desembrulhar, char-em-coleção caía no
            // ramo char_to_string ("a") ou, Unknown, em nada
            // (raw int → println_string → SIGSEGV).
            Type nativeArg = argType;
            ops.add(new KofCall(
                    BuiltinTypes.STRING,
                    "valueOf", List.of(nativeArg),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        } else if (driver.target == Target.JS
                && TypeMetrics.isFloatingPoint(
                        argType instanceof Type.NullableType nt ? nt.inner() : argType)) {
            // §264 (JS): Double/Float no JS sao Numbers crus — sem box; o
            // valueOf recebe o tipo REAL p/ o emissor formatar no contrato
            // do JDK ("4.0"/"1.0E7", nao "4"). Nullable: o get de Map devolve
            // Double? — o valueOf(J) do JS faz null-guard antes de formatar.
            ops.add(new KofCall(
                    BuiltinTypes.STRING,
                    "valueOf", List.of(argType),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        } else {
            // D-NULL-INTENT: no JVM, argType já Nullable(primitivo) chega
            // FISICAMENTE boxed (return/local/Map do #278) — boxPrimitive de
            // novo chamaria Integer/Boolean.valueOf sobre uma referência já
            // boxed (VerifyError). Só pula o box no JVM quando já é
            // Nullable; Script/JS mantêm o box incondicional de sempre — o
            // interpretador representa `Bool` em COLEÇÃO como Integer 1/0
            // (KofInterpreterCollections.unbox) e depende do
            // `Boolean.valueOf` aqui para reformatar de volta antes do
            // `String.valueOf`, senão imprime "1"/"0" em vez de
            // "true"/"false" (achado ao medir boolInCollectionsPrintsLikeJvm).
            boolean skipBox = driver.target == Target.JVM && argType instanceof Type.NullableType;
            if (!skipBox) {
                TypeEmitter.boxPrimitive(ops, argType);
            }
            ops.add(new KofCall(
                    BuiltinTypes.STRING,
                    "valueOf", List.of(Type.UnknownType.UNKNOWN),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        }
    } else {
        // o tipo REAL do arg só vai para o valueOf NATIVO/JS (para
        // despachar toString de records e formatar coleções). JVM usa
        // Object (String.valueOf(Object) chama toString; valueOf de um
        // ClassType específico não existe no JVM).
        ops.add(new KofCall(
                BuiltinTypes.STRING,
                "valueOf", List.of((driver.target.isNative() || driver.target == Target.JS)
                        && !Type.isString(argType) ? argType
                        : Type.UnknownType.UNKNOWN),
                BuiltinTypes.STRING, KofCallKind.STATIC));
    }
    ops.add(new KofCall(
            new Type.ClassType("java.io", "PrintStream", List.of()),
            mc.methodName(), List.of(BuiltinTypes.STRING),
            Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
        return localIdx;
    }
        return -1;
    }

    /**
     * §205 slice 1 (Native): `println`/`print` DIRETO de um if/switch com
     * ramos de tipos distintos (join #183 = `Object`, sem dispatch boxed).
     * Rebaixa como impressão POR RAMO: condição avaliada UMA vez e cada
     * ramo imprime o SEU valor pelo SEU tipo estático — reusa os dispatchs
     * valueOf/println de primitivo/String/record já testados em x86, e o
     * MESMO IR vale para riscv64/aarch64 (regra 5, sem ABI novo). Retorna
     * -1 se o caso não é o padrão suportado (cai no caminho antigo).
     */
    static int lowerHeterogeneousDirect(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
        ExpressionNode arg = mc.arguments().get(0);
        if (arg instanceof IfExpr ie && ie.elseExpr() != null) {
            List<Type> bts = ExpressionTyper.ifBranchTypes(driver, ie, locals);
            if (bts.size() == 2 && ExpressionTyper.branchTypesDiffer(bts)
                    && staticallyPrintable(bts.get(0)) && staticallyPrintable(bts.get(1))) {
                LabelId thenLabel = LabelId.create();
                LabelId elseLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                if (ie.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                    localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()),
                            driver.comparisonOperandType(bin, locals), thenLabel, elseLabel));
                } else {
                    localIdx = CompilerComparisons.emitTruthinessJump(driver, ie.condition(),
                            ops, owner, localIdx, locals, thenLabel, elseLabel);
                }
                ops.add(new KofLabel(thenLabel));
                localIdx = emitPrintBranch(driver, mc, ie.thenExpr(), ops, owner, localIdx, locals);
                ops.add(new KofJump(endLabel));
                ops.add(new KofLabel(elseLabel));
                localIdx = emitPrintBranch(driver, mc, ie.elseExpr(), ops, owner, localIdx, locals);
                ops.add(new KofLabel(endLabel));
                return localIdx;
            }
        }
        if (arg instanceof SwitchExpr se && se.defaultValue() != null && !se.cases().isEmpty()
                && se.cases().stream().allMatch(c -> !(c.value() instanceof PatternExpr))
                && ExpressionTyper.branchTypesDiffer(ExpressionTyper.switchBranchTypes(
                        driver, se.cases(), se.defaultValue(),
                        ExpressionTyper.inferExprType(driver, se, locals), locals))
                && staticallyPrintable(ExpressionTyper.inferExprType(driver, se.defaultValue(), locals))
                && se.cases().stream().allMatch(c -> staticallyPrintable(
                        ExpressionTyper.inferExprType(driver, c.body(), locals)))) {
            // chain if-else espelhando SwitchExprLowerer.emitSwitchChain
            // (subject em um temp local, avaliado UMA vez), com bodies
            // impressos por ramo em vez de load+box. Sem guards/patterns.
            Type switchType = ExpressionTyper.inferExprType(driver, se.expression(), locals);
            int switchTmp = localIdx++;
            localIdx = ExpressionLowerer.emitExpression(driver, se.expression(),
                    ops, owner, localIdx, locals);
            ops.add(new KofStoreLocal(switchType, switchTmp));
            locals.add(new IRLocalVariable(switchTmp, "#heteroSwitch", switchType));
            LabelId endLabel = LabelId.create();
            return emitSwitchPrintChain(driver, mc, se, se.cases(), 0, switchType, switchTmp,
                    endLabel, ops, owner, localIdx, locals);
        }
        return -1;
    }

    /** Caso sem match (fim da chain) → imprime o default e fecha o end. */
    private static int emitSwitchPrintChain(CompilerDriver driver, MethodCallExpr mc,
            SwitchExpr se, List<SwitchExprCase> cases, int i, Type switchType, int switchTmp,
            LabelId endLabel, List<KofOperation> ops, String owner, int localIdx,
            List<IRLocalVariable> locals) {
        if (i >= cases.size()) {
            localIdx = emitPrintBranch(driver, mc, se.defaultValue(), ops, owner, localIdx, locals);
            ops.add(new KofLabel(endLabel));
            return localIdx;
        }
        SwitchExprCase sc = cases.get(i);
        LabelId bodyLabel = LabelId.create();
        LabelId elseLabel = LabelId.create();
        ops.add(new KofLoadLocal(switchType, switchTmp));
        localIdx = ExpressionLowerer.emitExpression(driver, sc.value(), ops, owner, localIdx, locals);
        if (Type.isString(switchType)) {
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
        } else {
            // D-ENUM207: enum = instâncias → identidade (if_acmp).
            ops.add(new KofBinary(KofBinaryOp.EQ, switchType));
        }
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        ops.add(new KofConditionalJump(KofComparison.NE, bodyLabel, elseLabel));
        ops.add(new KofLabel(bodyLabel));
        localIdx = emitPrintBranch(driver, mc, sc.body(), ops, owner, localIdx, locals);
        ops.add(new KofJump(endLabel));
        ops.add(new KofLabel(elseLabel));
        return emitSwitchPrintChain(driver, mc, se, cases, i + 1, switchType, switchTmp,
                endLabel, ops, owner, localIdx, locals);
    }

    /** Cada ramo imprime com o SEU tipo — o mesmo trio que o caminho normal
     *  (receiver; valor; valueOf(tipoReal); println(string)). */
    private static int emitPrintBranch(CompilerDriver driver, MethodCallExpr mc,
            ExpressionNode branch, List<KofOperation> ops, String owner, int localIdx,
            List<IRLocalVariable> locals) {
        Type branchType = ExpressionTyper.inferExprType(driver, branch, locals);
        ops.add(new KofGetStatic(
                new Type.ClassType("java.lang", "System", List.of()),
                "out", new Type.ClassType("java.io", "PrintStream", List.of())));
        localIdx = ExpressionLowerer.emitExpression(driver, branch, ops, owner, localIdx, locals);
        Type nativeArg = branchType;
        if (TypeMetrics.isPrimitiveType(branchType)) {
            Type inner = branchType instanceof Type.NullableType nt ? nt.inner() : branchType;
            if (inner instanceof Type.PrimitiveType p
                    && "char".equals(Type.canonicalPrimitiveName(p.name()))) {
                nativeArg = Type.PrimitiveType.INT;
            }
        } else if (Type.isString(branchType)) {
            nativeArg = Type.UnknownType.UNKNOWN;
        }
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf", List.of(nativeArg),
                BuiltinTypes.STRING, KofCallKind.STATIC));
        ops.add(new KofCall(
                new Type.ClassType("java.io", "PrintStream", List.of()),
                mc.methodName(), List.of(BuiltinTypes.STRING),
                Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
        return localIdx;
    }

    /** Tipo com dispatch NATIVO de valueOf/println garantido (primitivo,
     *  String, record/classe com vtable, coleções com tag; NÃO `Object`,
     *  Unknown, tipo de variável). Nullable desembrulha (dispatch pelo
     *  INNER — mesma regra do bug 87). */
    private static boolean staticallyPrintable(Type t) {
        if (t instanceof Type.NullableType nt) t = nt.inner();
        if (TypeMetrics.isPrimitiveType(t)) return true;
        if (t instanceof Type.ClassType ct) return !"Object".equals(ct.name());
        return false;
    }
}