package dev.kof.compiler.parser;

import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.TokenType;
import dev.kof.compiler.UsingStmt;

import java.util.List;

/**
 * D-SCOPED-RESOURCES-GO slice 1 — {@code using (name = init, closer) { body }}.
 *
 * <p>Contextual keyword (mirrors {@code test}/{@code application} in
 * {@link Parser}): only {@code using} followed by {@code (} takes this branch;
 * any other use of the identifier keeps parsing as before (zero corpus usage
 * measured). The closer is mandatory — a missing closer is a parse error,
 * never a silently non-closing program (R6). No trailing semicolon (like
 * {@code try}/{@code if}: the block closes the statement).</p>
 */
final class UsingParser {

    private UsingParser() {
    }

    static StatementNode parseUsingStatement(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance(); // 'using'
        ctx.expect(TokenType.LPAREN, "Expected '(' after 'using'", "PARSE010");
        String name = ctx.expectId("Expected resource name in using (name = init, closer)", "PARSE010");
        ctx.expect(TokenType.EQUAL, "Expected '=' after the resource name in using (name = init, closer)",
                "PARSE010");
        ExpressionNode init = ExpressionParser.parseExpression(ctx);
        if (!ctx.check(TokenType.COMMA)) {
            ctx.error("using requires a closer expression: using (name = init, closer) { body }",
                    "PARSE010");
        }
        ctx.advance(); // ','
        ExpressionNode closer = ExpressionParser.parseExpression(ctx);
        ctx.expect(TokenType.RPAREN, "Expected ')' to close using (name = init, closer)", "PARSE010");
        List<StatementNode> body = StatementParser.parseBlock(ctx);
        return new UsingStmt(p, name, init, closer, body);
    }
}
