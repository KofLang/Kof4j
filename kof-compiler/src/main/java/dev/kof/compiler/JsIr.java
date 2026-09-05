package dev.kof.compiler;

import java.util.List;

/**
 * JsIr — JavaScript AST for the KofJS backend.
 *
 * This is a backend-specific intermediate representation produced by lowering
 * the Kof IR (never by parsing JavaScript). It is consumed exclusively by the
 * JsEmitter, which renders it as modern ECMAScript (ES2022+, ESM).
 *
 * Kof IR is stack-based; the lowering (JsBackend) converts the stack discipline
 * into this tree-shaped JS AST. The JS AST deliberately stays small: it only
 * models what the Kof IR can express.
 */
final class JsIr {

    private JsIr() {}

    // ── Module ──────────────────────────────────────────────────────

    record JsModule(String name, List<JsClass> classes, List<JsFunction> functions,
                    List<String> runtimeImports, List<String> ioRuntimeImports,
                    List<JsStatement> statements) {
    }

    // ── Classes ─────────────────────────────────────────────────────

    record JsClass(String name, String superName, List<JsField> fields, List<JsFunction> methods) {
    }

    record JsField(String name, String initializer, boolean isStatic) {
    }

    // ── Functions / Methods ─────────────────────────────────────────

    record JsFunction(String name, List<String> parameters, List<JsStatement> body,
                      boolean isStatic, boolean isConstructor, boolean isTopLevel,
                      boolean isAsync, Integer kofLine) {
        JsFunction(String name, List<String> parameters, List<JsStatement> body,
                   boolean isStatic, boolean isConstructor, boolean isTopLevel) {
            this(name, parameters, body, isStatic, isConstructor, isTopLevel, false, null);
        }
    }

    /** Posição de um cabeçalho de função gerado → linha da fonte Kof (source map). */
    record JsFunctionLine(String name, int generatedLine, int kofLine) {
    }

    // ── Statements ──────────────────────────────────────────────────

    sealed interface JsStatement {
    }

    record JsExprStmt(JsExpression expression) implements JsStatement {
    }

    record JsVarDecl(String name, JsExpression initializer, boolean isConst) implements JsStatement {
    }

    record JsAssign(String target, JsExpression value) implements JsStatement {
    }

    record JsReturn(JsExpression value) implements JsStatement {
    }

    record JsBreak() implements JsStatement {
    }

    record JsContinue() implements JsStatement {
    }

    record JsThrow(JsExpression value) implements JsStatement {
    }

    record JsBlock(List<JsStatement> statements) implements JsStatement {
    }

    record JsIf(JsExpression condition, List<JsStatement> thenBranch,
                List<JsStatement> elseBranch) implements JsStatement {
    }

    record JsWhile(JsExpression condition, List<JsStatement> body, boolean isDoWhile) implements JsStatement {
    }

    record JsFor(List<JsStatement> init, JsExpression condition, List<JsStatement> update,
                 List<JsStatement> body) implements JsStatement {
    }

    record JsForIn(String varName, JsExpression collection, List<JsStatement> body) implements JsStatement {
    }

    record JsSwitch(JsExpression subject, List<JsSwitchCase> cases, List<JsStatement> defaultCase) implements JsStatement {
    }

    record JsSwitchCase(JsExpression value, List<JsStatement> body) {
    }

    record JsTry(List<JsStatement> tryBody, List<JsCatchClause> catches,
                 List<JsStatement> finallyBody) implements JsStatement {
    }

    record JsCatchClause(String param, List<JsStatement> body) {
    }

    // ── Expressions ─────────────────────────────────────────────────

    sealed interface JsExpression {
    }

    record JsNumber(String text) implements JsExpression {
    }

    record JsString(String value) implements JsExpression {
    }

    record JsNull() implements JsExpression {
    }

    record JsIdentifier(String name) implements JsExpression {
    }

    record JsThis() implements JsExpression {
    }

    record JsBinary(JsExpression left, String operator, JsExpression right) implements JsExpression {
    }

    record JsUnary(String operator, JsExpression operand) implements JsExpression {
    }

    record JsCall(JsExpression callee, List<JsExpression> arguments) implements JsExpression {
    }

    record JsNew(JsExpression callee, List<JsExpression> arguments) implements JsExpression {
    }

    record JsMember(JsExpression target, String name) implements JsExpression {
    }

    record JsIndex(JsExpression target, JsExpression index) implements JsExpression {
    }

    record JsConditional(JsExpression condition, JsExpression thenExpr, JsExpression elseExpr) implements JsExpression {
    }

    record JsSequence(List<JsExpression> expressions, JsExpression value) implements JsExpression {
    }

    record JsArray(JsExpression size, String fill) implements JsExpression {
    }

    record JsObjectLiteral(List<JsObjectEntry> entries) implements JsExpression {
    }

    record JsObjectEntry(String key, JsExpression value) {
    }

    record JsInstanceOf(JsExpression operand, String typeName) implements JsExpression {
    }

    record JsAssignExpr(String target, JsExpression value) implements JsExpression {
    }

    record JsArrow(List<String> parameters, JsExpression body) implements JsExpression {
    }

    record JsAwait(JsExpression operand) implements JsExpression {
    }
}