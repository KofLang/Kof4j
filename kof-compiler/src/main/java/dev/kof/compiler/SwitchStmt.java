package dev.kof.compiler;

import java.util.List;
/** #686: `hasDefault` distingue `default: }` (corpo vazio, mas presente) de
 *  ausência total de `default` — a exaustividade do `switch` STATEMENT
 *  (sealed/Bool) depende disso. */
public record SwitchStmt(SourcePosition position, ExpressionNode expression,
                  List<SwitchCase> cases, List<StatementNode> defaultBody,
                  boolean hasDefault) implements StatementNode {
}
