package dev.kof.compiler;

/**
 * BuiltinUiCallTyper — face `kof.ui` do typer de chamadas builtin sem
 * receiver (construtores implicitos Color/Window/Label/Button/Input/
 * Column/Row/View/Style/Link/Image/Canvas/Icon/Font/Component). Extraido
 * de BuiltinCallTyper no gate §344 (classe tinha cruzado 600): as regras
 * UI eram a cauda do `infer` e continuam delegadas no MESMO ponto da
 * cadeia — ordem exata preservada (freeze regra 3, refactor sem mudanca
 * de comportamento).
 */
final class BuiltinUiCallTyper {

    private BuiltinUiCallTyper() {}

    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        if (mc.receiver() == null && "Color".equals(mc.methodName())
                && (mc.arguments().size() == 1 || mc.arguments().size() == 3)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.COLOR;
        }
        if (mc.receiver() == null && "Window".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.WINDOW;
        }
        if (mc.receiver() == null && "Label".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.LABEL;
        }
        if (mc.receiver() == null && "Button".equals(mc.methodName())
                && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.BUTTON;
        }
        if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.INPUT;
        }
        if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
                && (mc.arguments().isEmpty() || mc.arguments().size() == 1)) {
            return "Column".equals(mc.methodName()) ? KofUi.COLUMN : KofUi.ROW;
        }
        if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.VIEW;
        }
        if (mc.receiver() == null && KofUi.isConstructor(mc.methodName())
                && !mc.arguments().isEmpty() && mc.arguments().size() <= 3) {
            Type ct = KofUi.constructorType(mc.methodName());
            if (KofUi.isLayoutType(ct) || KofUi.isStore(ct)) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return ct;
            }
        }
        if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.STYLE;
        }
        if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 1) {
            // D-UI-STYLE (UI007): declarative form — parse/validate in the
            // compiler (Q4) with a typed whitelist (Q3). SEM076 (unknown
            // property) / SEM077 (malformed) / SEM078 (invalid value); the
            // lowering re-parses only for the normalized text.
            ExpressionNode arg = mc.arguments().get(0);
            SemExpressionTyper.inferType(sa, arg, scope);
            KofStyleParser.report(sa.diagnostics(), mc.position(),
                    KofStyleParser.literalString(arg));
            return KofUi.STYLE;
        }
        if (mc.receiver() == null && "Link".equals(mc.methodName()) && mc.arguments().size() == 2) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.LINK;
        }
        if (mc.receiver() == null && "Image".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.IMAGE;
        }
        if (mc.receiver() == null && "Canvas".equals(mc.methodName()) && mc.arguments().size() == 2) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.CANVAS;
        }
        if (mc.receiver() == null && "Icon".equals(mc.methodName())
                && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            String badIcon = KofUiIcons.unknownLiteral(mc.arguments().get(0));
            if (badIcon != null && sa.diagnostics() != null) {
                sa.diagnostics().error(mc, KofUiIcons.unknownMessage(badIcon), "SEM079");
            }
            return KofUi.ICON;
        }
        if (mc.receiver() == null && "Font".equals(mc.methodName())
                && (mc.arguments().size() == 2 || mc.arguments().size() == 3)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.FONT;
        }
        if (mc.receiver() == null && "Component".equals(mc.methodName())
                && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.COMPONENT;
        }
        return null;
    }
}
