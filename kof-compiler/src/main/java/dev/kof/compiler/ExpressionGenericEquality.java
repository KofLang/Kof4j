package dev.kof.compiler;

import java.util.List;

/**
 * §553 / D-EQ-UNBOUNDED-T (mantenedora 02/10, regra 6): o caminho de
 * {@code ==}/{@code !=} sobre um type variable SEM bound (apaga p/
 * {@code Object}) é igualdade ESTRUTURAL de CONTEÚDO (mesmo contrato de
 * String/record), nunca identidade de referência.
 *
 * <p>Antes os operandos apagavam p/ Object e o ramo final emitia
 * {@code if_acmp} (JVM/Script/Native) enquanto o JS comparava estrutural —
 * 1 fonte, 3 comportamentos; o cache de literais mascarava JVM/Script
 * ({@code eq(1,1)} true) e o Native não tinha cache ({@code eq(200,200)}
 * false). O contrato de tipo CONCRETO é intocado (o chamador só entra aqui
 * quando um dos lados é {@link ExpressionBinaryPredicates#isUnboundedTypeVar}).
 *
 * <p>Pilha ao entrar: [L] (o lado esquerdo já foi emitido pelo laço da cadeia).
 * Deixa um 0/1 (BOOL) no topo representando {@code L == R}; o chamador aplica
 * a dobra do {@code !=} e define {@code accType = BOOL}. Extraído de
 * {@link ExpressionBinaryLowerer} para manter a classe sob o limite de 500
 * (regra ≤500 / check_500).
 */
final class ExpressionGenericEquality {

    private ExpressionGenericEquality() {}

    static int emit(CompilerDriver driver, BinaryExpr be, List<KofOperation> ops,
                    String owner, int localIdx, List<IRLocalVariable> locals) {
        Type objT = new Type.ClassType("java.lang", "Object", List.of());
        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
        if (driver.target == Target.JS) {
            // JS: o helper null-safe de conteúdo já cobre primitivos/strings/
            // records e devolve 1/0 (a dobra do `!=` abaixo funciona nele).
            ops.add(new KofCall(BuiltinTypes.STRING, "kofRecordEq",
                    List.of(objT, objT), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
        } else if (driver.target.isNative()) {
            // Native: sem `Object.equals` no runtime — helper próprio que faz
            // caixa=valor (kof_box_equals), String (kof_string_equals) e
            // equals virtual de record/classe (kof_obj_equals).
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_eq_generic",
                    List.of(objT, objT), Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
        } else {
            // JVM/Script: java.util.Objects.equals — exatamente a semântica
            // null-safe de conteúdo (null==null true, um nulo false, senão
            // a.equals(b)) e o MESMO oráculo que o desugar de record usa.
            ops.add(new KofCall(new Type.ClassType("java.util", "Objects", List.of()),
                    "equals", List.of(objT, objT), Type.PrimitiveType.BOOL, KofCallKind.STATIC));
        }
        if ("!=".equals(be.operator())) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
        }
        return localIdx;
    }
}
