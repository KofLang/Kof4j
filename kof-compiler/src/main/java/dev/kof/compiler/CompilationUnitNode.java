package dev.kof.compiler;

import java.util.List;
public record CompilationUnitNode(SourcePosition position, String packageName, List<String> imports,
                           List<? extends AstNode> declarations) implements AstNode {
}
