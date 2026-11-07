package dev.kof.compiler;

import java.util.List;

/**
 * Whether a lambda body returns a value anywhere ({@code return x}, including
 * {@code return null}), looking through blocks, branches, switches, try and
 * loops but not into nested lambdas, whose returns belong to their own body.
 */
final class LambdaReturns {

    private LambdaReturns() {}

    static boolean hasValueReturn(List<StatementNode> body) {
        if (body == null) return false;
        for (StatementNode s : body) {
            if (hasValueReturn(s)) return true;
        }
        return false;
    }

    private static boolean hasValueReturn(StatementNode s) {
        if (s == null) return false;
        return switch (s) {
            case ReturnStmt rs -> rs.value() != null;
            case BlockStmt bs -> hasValueReturn(bs.statements());
            case IfStmt is -> hasValueReturn(is.thenBranch()) || hasValueReturn(is.elseBranch());
            case SwitchStmt ss -> ss.cases().stream().anyMatch(c -> hasValueReturn(c.body()))
                    || hasValueReturn(ss.defaultBody());
            case TryStmt ts -> hasValueReturn(ts.tryBody()) || hasValueReturn(ts.finallyBody())
                    || ts.catchClauses().stream().anyMatch(c -> hasValueReturn(c.body()));
            case WhileStmt ws -> hasValueReturn(ws.body());
            case DoWhileStmt dws -> hasValueReturn(dws.body());
            case ForStmt fs -> hasValueReturn(fs.body());
            case ForInStmt fis -> hasValueReturn(fis.body());
            default -> false;
        };
    }
}
