package dev.kof.compiler.parser;

import dev.kof.compiler.BlockStmt;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.LockStmt;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.Token;
import dev.kof.compiler.TokenType;

/**
 * G240 — {@code lock (target) { body }}.
 *
 * <p>Contextual keyword (like {@code using}): the statement form is taken only
 * when {@code lock (} is followed, after the balanced {@code )}, by a
 * {@code {} on the same line. Any other {@code lock(...)} keeps parsing as an
 * ordinary call, so a user function named {@code lock} still works; only the
 * trailing-lambda call {@code lock(x) { ... }} changes meaning (zero corpus
 * usage measured, migration note in the CHANGELOG).</p>
 */
final class LockParser {

    private LockParser() {
    }

    static boolean atLockStatement(ParseContext ctx) {
        if (!ctx.wordIs("lock") || !ctx.checkNext(TokenType.LPAREN)) return false;
        int depth = 0;
        for (int off = 1; ; off++) {
            Token t = ctx.peekOffset(off);
            if (t.type() == TokenType.EOF) return false;
            if (t.type() == TokenType.LPAREN) depth++;
            else if (t.type() == TokenType.RPAREN && --depth == 0) {
                Token next = ctx.peekOffset(off + 1);
                return next.type() == TokenType.LBRACE && next.line() == t.line();
            }
        }
    }

    static StatementNode parseLockStatement(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance(); // 'lock'
        ctx.expect(TokenType.LPAREN, "Expected '(' after 'lock'", "PARSE010");
        ExpressionNode target = ExpressionParser.parseExpression(ctx);
        ctx.expect(TokenType.RPAREN, "Expected ')' to close lock (target)", "PARSE010");
        SourcePosition bp = ctx.pos();
        return new LockStmt(p, target, new BlockStmt(bp, StatementParser.parseBlock(ctx)));
    }
}
