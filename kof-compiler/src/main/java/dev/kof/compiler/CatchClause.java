package dev.kof.compiler;

import java.util.List;
public record CatchClause(SourcePosition position, String exceptionType, String exceptionName,
                   List<StatementNode> body) implements AstNode {
}
