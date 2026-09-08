package dev.kof.compiler;

import java.util.List;
public record FieldAccessExpr(SourcePosition position, ExpressionNode receiver,
                       String fieldName) implements ExpressionNode {
}
