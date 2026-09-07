package dev.kof.compiler;

import java.util.List;
public record QueryDslExpr(SourcePosition position, String entityType,
                    ExpressionNode dbArg,
                    List<ExpressionNode> whereClauses,
                    List<ExpressionNode> orderByFields,
                    List<String> orderByDirs,
                    ExpressionNode limit) implements ExpressionNode {
}
