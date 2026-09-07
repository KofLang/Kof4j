package dev.kof.compiler;

import java.util.List;
public record LiteralExpr(SourcePosition position, LiteralKind kind, String value) implements ExpressionNode {
}
