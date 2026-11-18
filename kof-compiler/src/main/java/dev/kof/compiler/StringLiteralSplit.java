package dev.kof.compiler;

/**
 * §742 / `D-SPLIT-LITERAL` — o roteamento do split LITERAL (separado do
 * lowerer para a regra ≤500 do AGENTS.md: a classelowers estava em 595 linhas
 * e a adição cruzou a linha vermelha).
 *
 * <p>O `String.split` Kof tem separador LITERAL (nunca regex): o caminho
 * compilado-JVM roteia pelo runtime `kof_string_split_literal[_limit]`
 * (o `split(Pattern.quote(sep), limit)` do JDK — as regras de
 * vazios-trailing/limit do §111/§655 ficam byte-idênticas). As faces JS
 * (`kofSplit`) e Native (`kof_string_split*`) JÁ eram literais e seguem os
 * caminhos delas.
 */
final class StringLiteralSplit {

    private StringLiteralSplit() {}

    /** O nome do runtime do split literal, ou o `runtimeMethod` recebido
     *  (o Registry) quando a chamada não é o split. */
    static String route(CompilerDriver driver, MethodCallExpr mc, Type recvType,
                        String runtimeMethod) {
        if (driver.target != Target.JVM) return runtimeMethod;
        if (!"split".equals(mc.methodName())) return runtimeMethod;
        if (!BuiltinTypes.isString(recvType)) return runtimeMethod;
        // a variante 2-arg (limit) tem runtime PRÓPRIO (o nome único do
        // registry cobre só a 1-arg).
        return mc.arguments().size() == 2
                ? "kof_string_split_literal_limit" : "kof_string_split_literal";
    }
}
