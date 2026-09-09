package dev.kof.compiler;

import java.util.List;

/**
 * Checagem de caminho de retorno (bug 26): função/método com tipo NÃO-void
 * cujo corpo pode terminar sem `return`/`throw` emite SEM036 em
 * compile-time. Antes disso o backend emitia `ireturn`/`areturn` com pilha
 * vazia → VerifyError em runtime (disfarçado de "JavaFX launcher" no JVM,
 * `expression stack underflow` no JS, `NoSuchElementException` no
 * interpretador). R6: diagnóstico honesto, nunca bytecode inválido.
 *
 * Conservador por design (zero regressão): só acusa quando um caminho óbvio
 * cai no fim do corpo — corpo vazio, último statement não-terminal, ou `if`
 * sem `else` no fim. Switch/try/loops não são analisados fundo (podem
 * retornar em todos os caminhos; falsos positivos quebrariam código que
 * compila hoje — retrocompatibilidade).
 */
public final class ReturnPathAnalyzer {

    private ReturnPathAnalyzer() {}

    static void check(SemanticAnalyzer sa, List<StatementNode> body, Type returnType,
                      SourcePosition pos, String what) {
        if (sa.diagnostics() == null) return;
        if (returnType == null || Type.isVoid(returnType) || Type.isUnknown(returnType)) return;
        boolean reachesEnd = body == null || body.isEmpty()
                || reachesEnd(body.get(body.size() - 1));
        if (!reachesEnd) return;
        sa.diagnostics().error(pos != null ? pos.file() : "",
                pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                what + " declara retorno '" + returnType + "' mas pode terminar sem return/throw",
                "SEM036");
    }

    /** true se o statement pode completar normalmente (cai no fim do corpo). */
    static boolean reachesEnd(StatementNode stmt) {
        return switch (stmt) {
            case ReturnStmt r -> false;
            case ThrowStmt t -> false;
            case BlockStmt b -> b.statements().isEmpty()
                    || reachesEnd(b.statements().get(b.statements().size() - 1));
            // if SEM else: o caminho else-implícito cai no fim (true sempre);
            // com else: só se AMBOS os ramos caírem no fim.
            case IfStmt i -> i.elseBranch() == null
                    || (reachesEnd(i.thenBranch()) && reachesEnd(i.elseBranch()));
            // loops podem ser infinitos com return dentro (while(true){return}
            // funciona hoje); try/switch têm muitos caminhos — conservador.
            case WhileStmt w -> false;
            case DoWhileStmt d -> false;
            case ForStmt f -> false;
            case ForInStmt fi -> false;
            case TryStmt t -> false;
            case SwitchStmt s -> false;
            default -> true; // declarações, atribuições, chamadas: caem no fim
        };
    }
}
