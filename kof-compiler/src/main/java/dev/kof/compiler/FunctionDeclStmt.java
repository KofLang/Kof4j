package dev.kof.compiler;

import java.util.List;

/**
 * SG-011: função aninhada (`Int dobro(Int x) { ... }` dentro de função).
 * O parser a captura como FunctionDeclStmt; o desugar faz HOISTING para
 * top-level com nome qualificado `outer__inner` e reescreve as chamadas
 * `inner(...)` do escopo da outer. Semântica (decisão do maintainer):
 * a inner é definida antes do corpo da outer executar; a outer chama
 * a inner e aguarda o retorno.
 */
public record FunctionDeclStmt(SourcePosition position,
                               FunctionDeclarationNode function) implements StatementNode {
}
