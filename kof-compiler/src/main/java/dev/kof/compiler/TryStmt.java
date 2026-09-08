package dev.kof.compiler;

import java.util.List;
public record TryStmt(SourcePosition position, List<StatementNode> tryBody,
               List<CatchClause> catchClauses, List<StatementNode> finallyBody) implements StatementNode {
}
