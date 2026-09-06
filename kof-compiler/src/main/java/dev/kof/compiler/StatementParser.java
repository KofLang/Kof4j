package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Statements (REFACTOR-500, FASE 7): bloco, return, if, while, do-while,
 * for, throw, try, switch (statement e expressão), var decl. Move o cursor
 * do {@link ParseContext} — nunca duplica estado.
 */
class StatementParser {

    static List<StatementNode> parseBlock(ParseContext ctx) {
        ctx.expect(TokenType.LBRACE, "Expected '{'", "PARSE024");
        List<StatementNode> stmts = new ArrayList<>();
        while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
            stmts.add(StatementParser.parseStatement(ctx));
        }
        ctx.expect(TokenType.RBRACE, "Expected '}'", "PARSE025");
        return stmts;
    }

    static StatementNode parseStatement(ParseContext ctx) {
        if (ctx.check(TokenType.LBRACE)) {
            return new BlockStmt(ctx.pos(), StatementParser.parseBlock(ctx));
        }
        if (ctx.check(TokenType.RETURN)) {
            return StatementParser.parseReturn(ctx);
        }
        if (ctx.check(TokenType.IF)) {
            return StatementParser.parseIfStatement(ctx);
        }
        if (ctx.check(TokenType.WHILE)) {
            return StatementParser.parseWhileStatement(ctx);
        }
        if (ctx.check(TokenType.DO)) {
            return StatementParser.parseDoWhileStatement(ctx);
        }
        if (ctx.check(TokenType.FOR)) {
            return StatementParser.parseForStatement(ctx);
        }
        if (ctx.check(TokenType.THROW)) {
            return StatementParser.parseThrowStatement(ctx);
        }
        if (ctx.check(TokenType.SPAWN)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ExpressionNode expr = ExpressionParser.parseExpression(ctx);
            ctx.expectSemicolon();
            return new SpawnStmt(p, expr);
        }
        if (ctx.check(TokenType.ASSERT)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE011");
            ExpressionNode condition = ExpressionParser.parseExpression(ctx);
            String message = null;
            if (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                ExpressionNode msgExpr = ExpressionParser.parseExpression(ctx);
                if (msgExpr instanceof LiteralExpr lit && lit.kind() == ConcreteLiteralKind.STRING) {
                    message = lit.value();
                }
            }
            ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
            ctx.expectSemicolon();
            return new AssertStmt(p, condition, message);
        }
        if (ctx.check(TokenType.TRY)) {
            return StatementParser.parseTryStatement(ctx);
        }
        if (ctx.check(TokenType.SWITCH)) {
            return StatementParser.parseSwitchStatement(ctx);
        }
        if (ctx.check(TokenType.BREAK)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ctx.expectSemicolon();
            return new BreakStmt(p);
        }
        if (ctx.check(TokenType.CONTINUE)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ctx.expectSemicolon();
            return new ContinueStmt(p);
        }
        if (ctx.check(TokenType.VAR, TokenType.VAL)) {
            return StatementParser.parseVarDecl(ctx);
        }
        if ((ctx.check(TokenType.IDENTIFIER) || ctx.check(TokenType.VOID) || TypeParser.isPrimitiveType(ctx))
                && StatementParser.lookaheadTypedVarDecl(ctx)) {
            // `Type name = ...` incluindo nullable (`String? s`) e arrays
            // (`Int[] arr`) — a detecção antiga via ctx.checkNext(IDENTIFIER)
            // quebrava em `?`/`[`/`<` depois do tipo.
            return StatementParser.parseVarDecl(ctx);
        }
        if (ctx.check(TokenType.SEMICOLON)) {
            ctx.advance();
            return new ExpressionStmt(ctx.pos(), null);
        }
        ExpressionNode expr = ExpressionParser.parseExpression(ctx);
        ctx.expectSemicolon();
        return new ExpressionStmt(ctx.pos(), expr);
    }

    static StatementNode parseReturn(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        if (ctx.check(TokenType.SEMICOLON)) {
            ctx.advance();
            return new ReturnStmt(p, null);
        }
        if (ctx.check(TokenType.RBRACE) || ctx.atEnd()) {
            // bare return: `return` followed by the end of the block
            return new ReturnStmt(p, null);
        }
        ExpressionNode value = ExpressionParser.parseExpression(ctx);
        ctx.expectSemicolon();
        return new ReturnStmt(p, value);
    }

    static StatementNode parseIfStatement(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        ctx.expect(TokenType.LPAREN, "Expected '(' after 'if'", "PARSE028");
        ExpressionNode cond = ExpressionParser.parseExpression(ctx);
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE029");
        StatementNode thenB = StatementParser.parseStatement(ctx);
        StatementNode elseB = null;
        if (ctx.check(TokenType.ELSE)) {
            ctx.advance();
            elseB = StatementParser.parseStatement(ctx);
        }
        return new IfStmt(p, cond, thenB, elseB);
    }

    static StatementNode parseWhileStatement(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        ctx.expect(TokenType.LPAREN, "Expected '(' after 'while'", "PARSE030");
        ExpressionNode cond = ExpressionParser.parseExpression(ctx);
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE031");
        StatementNode body = StatementParser.parseStatement(ctx);
        return new WhileStmt(p, cond, body);
    }

    static StatementNode parseDoWhileStatement(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        StatementNode body = StatementParser.parseStatement(ctx);
        ctx.expect(TokenType.WHILE, "Expected 'while' after 'do'", "PARSE060");
        ctx.expect(TokenType.LPAREN, "Expected '(' after 'while'", "PARSE061");
        ExpressionNode cond = ExpressionParser.parseExpression(ctx);
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE062");
        ctx.expectSemicolon();
        return new DoWhileStmt(p, cond, body);
    }

    static StatementNode parseForStatement(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        ctx.expect(TokenType.LPAREN, "Expected '(' after 'for'", "PARSE032");
        if (ctx.check(TokenType.VAR, TokenType.VAL) && ctx.checkNext(TokenType.IDENTIFIER)
                && ctx.pos + 2 < ctx.tokens.size() && ctx.tokens.get(ctx.pos + 2).is(TokenType.IDENTIFIER)
                && "in".equals(ctx.tokens.get(ctx.pos + 2).value())) {
            ctx.advance();
            String varName = ctx.advance().value();
            ctx.advance();
            ExpressionNode collection = ExpressionParser.parseExpression(ctx);
            ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE035");
            StatementNode body = StatementParser.parseStatement(ctx);
            return new ForInStmt(p, varName, collection, body);
        }
        StatementNode init;
        if (ctx.check(TokenType.SEMICOLON)) {
            ctx.advance();
            init = new ExpressionStmt(p, null);
        } else if (ctx.check(TokenType.VAR, TokenType.VAL) ||
                (TypeParser.isPrimitiveType(ctx) && ctx.checkNext(TokenType.IDENTIFIER))) {
            init = StatementParser.parseVarDecl(ctx);
        } else {
            ExpressionNode initExpr = ExpressionParser.parseExpression(ctx);
            ctx.expectSemicolon();
            init = new ExpressionStmt(p, initExpr);
        }
        ExpressionNode cond = null;
        if (!ctx.check(TokenType.SEMICOLON)) {
            cond = ExpressionParser.parseExpression(ctx);
        }
        ctx.expectSemicolon();
        ExpressionNode update = null;
        if (!ctx.check(TokenType.RPAREN)) {
            update = ExpressionParser.parseExpression(ctx);
        }
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE035");
        StatementNode body = StatementParser.parseStatement(ctx);
        return new ForStmt(p, init, cond, update, body);
    }

    static StatementNode parseThrowStatement(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        ExpressionNode expr = ExpressionParser.parseExpression(ctx);
        ctx.expectSemicolon();
        return new ThrowStmt(p, expr);
    }

    static StatementNode parseTryStatement(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        List<StatementNode> tryBody = StatementParser.parseBlock(ctx);
        List<CatchClause> catchClauses = new ArrayList<>();
        while (ctx.check(TokenType.CATCH)) {
            SourcePosition cp = ctx.pos();
            ctx.advance();
            ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE050");
            String excType = TypeParser.parseTypeRef(ctx);
            String excName = ctx.expectId("Expected exception name", "PARSE051");
            ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE052");
            List<StatementNode> catchBody = StatementParser.parseBlock(ctx);
            catchClauses.add(new CatchClause(cp, excType, excName, catchBody));
        }
        List<StatementNode> finallyBody = List.of();
        if (ctx.check(TokenType.FINALLY)) {
            ctx.advance();
            finallyBody = StatementParser.parseBlock(ctx);
        }
        return new TryStmt(p, tryBody, catchClauses, finallyBody);
    }

    static StatementNode parseSwitchStatement(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        ctx.expect(TokenType.LPAREN, "Expected '(' after 'switch'", "PARSE070");
        ExpressionNode expr = ExpressionParser.parseExpression(ctx);
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE071");
        ctx.expect(TokenType.LBRACE, "Expected '{'", "PARSE072");
        List<SwitchCase> cases = new ArrayList<>();
        List<StatementNode> defaultBody = List.of();
        while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
            if (ctx.check(TokenType.CASE)) {
                SourcePosition cp = ctx.pos();
                ctx.advance();
                ExpressionNode value = StatementParser.parseSwitchCasePatternOrValue(ctx, cp);
                ctx.expect(TokenType.COLON, "Expected ':' (switch statement) ou '->' (switch expressão)", "PARSE073");
                List<StatementNode> caseBody = new ArrayList<>();
                while (!ctx.check(TokenType.CASE) && !ctx.check(TokenType.DEFAULT) && !ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                    caseBody.add(StatementParser.parseStatement(ctx));
                }
                cases.add(new SwitchCase(cp, value, caseBody));
            } else if (ctx.check(TokenType.DEFAULT)) {
                ctx.advance();
                ctx.expect(TokenType.COLON, "Expected ':'", "PARSE074");
                defaultBody = new ArrayList<>();
                while (!ctx.check(TokenType.CASE) && !ctx.check(TokenType.DEFAULT) && !ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                    defaultBody.add(StatementParser.parseStatement(ctx));
                }
            } else {
                ctx.advance();
            }
        }
        ctx.expect(TokenType.RBRACE, "Expected '}'", "PARSE075");
        return new SwitchStmt(p, expr, cases, defaultBody);
    }

    /**
     * Valor ou pattern de um case de switch (compartilhado pela forma statement
     * {@code case X:} e pela forma expressão {@code case X ->}):
     * <ul>
     *   <li>{@code case Type var} — pattern simples ({@link PatternExpr})</li>
     *   <li>{@code case Type(var1, var2)} — pattern de destructuring</li>
     *   <li>senão, uma expressão comum (literal, constante, {@code x instanceof T})</li>
     * </ul>
     */
    static ExpressionNode parseSwitchCasePatternOrValue(ParseContext ctx, SourcePosition cp) {
        // pattern matching: case Type var  /  case Type(var1, var2)
        if (ctx.check(TokenType.IDENTIFIER) && ctx.pos + 2 < ctx.tokens.size()
                && ctx.tokens.get(ctx.pos + 1).type() == TokenType.IDENTIFIER
                && (ctx.tokens.get(ctx.pos + 2).type() == TokenType.COLON
                        || ctx.tokens.get(ctx.pos + 2).type() == TokenType.ARROW)) {
            String typeName = ctx.advance().value();
            String varName = ctx.advance().value();
            return new PatternExpr(cp, typeName, varName, java.util.List.of());
        }
        if (ctx.check(TokenType.IDENTIFIER) && ctx.pos + 1 < ctx.tokens.size()
                && ctx.tokens.get(ctx.pos + 1).type() == TokenType.LPAREN) {
            // Try destructuring: case Type(var1, var2)
            String typeName = ctx.tokens.get(ctx.pos).value();
            int depth = 0;
            int rparenPos = -1;
            for (int k = ctx.pos + 1; k < ctx.tokens.size() && k < ctx.pos + 20; k++) {
                TokenType tt = ctx.tokens.get(k).type();
                if (tt == TokenType.LPAREN) depth++;
                else if (tt == TokenType.RPAREN) {
                    depth--;
                    if (depth == 0) { rparenPos = k; break; }
                }
            }
            if (rparenPos != -1 && rparenPos + 1 < ctx.tokens.size()
                    && (ctx.tokens.get(rparenPos + 1).type() == TokenType.COLON
                            || ctx.tokens.get(rparenPos + 1).type() == TokenType.ARROW)) {
                java.util.List<String> fieldVars = new java.util.ArrayList<>();
                for (int q = ctx.pos + 2; q < rparenPos; q++) {
                    if (ctx.tokens.get(q).type() == TokenType.IDENTIFIER) {
                        String v = ctx.tokens.get(q).value();
                        if (("var".equals(v) || "val".equals(v)) && q + 1 < rparenPos
                                && ctx.tokens.get(q + 1).type() == TokenType.IDENTIFIER) {
                            fieldVars.add(ctx.tokens.get(q + 1).value());
                            q++;
                        } else {
                            fieldVars.add(v);
                        }
                    }
                }
                ctx.advance(); // Type
                ctx.advance(); // LPAREN
                while (!ctx.check(TokenType.RPAREN) && !ctx.atEnd()) ctx.advance();
                if (ctx.check(TokenType.RPAREN)) ctx.advance();
                return new PatternExpr(cp, typeName, null, java.util.List.copyOf(fieldVars));
            }
            return ExpressionParser.parseExpression(ctx);
        }
        return ExpressionParser.parseExpression(ctx);
    }

    static StatementNode parseVarDecl(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        String type = "var";
        if (ctx.check(TokenType.VAR, TokenType.VAL)) {
            ctx.advance();
        } else {
            type = TypeParser.parseTypeRef(ctx);
        }
        String name = ctx.expectId("Expected variable name", "PARSE037");
        if (ctx.check(TokenType.COLON)) {
            // var name: Type = value — explicit type annotation
            ctx.advance();
            type = TypeParser.parseTypeRef(ctx);
        }
        ExpressionNode init = null;
        if (ctx.check(TokenType.EQUAL)) {
            ctx.advance();
            init = ExpressionParser.parseExpression(ctx);
        }
        ctx.expectSemicolon();
        return new VarDeclStmt(p, type, name, init);
    }

    /**
     * Lookahead de declaração tipada em statement: `Type name = ...` —
     * cobre nullable (`String? s`), arrays (`Int[] arr`) e genéricos
     * (`List<Int> xs`). A detecção antiga (ctx.checkNext(IDENTIFIER)) quebrava
     * quando `?`/`[`/`<` vinha logo após o tipo.
     */
    static boolean lookaheadTypedVarDecl(ParseContext ctx) {
        if (ctx.pos >= ctx.tokens.size()) return false;
        TokenType t0 = ctx.tokens.get(ctx.pos).type();
        if (t0 != TokenType.IDENTIFIER && t0 != TokenType.VOID && !TypeParser.isPrimitiveTypeToken(t0)) {
            return false;
        }
        int i = ctx.pos + 1;
        while (i + 1 < ctx.tokens.size() && ctx.tokens.get(i).is(TokenType.DOT)
                && ctx.tokens.get(i + 1).is(TokenType.IDENTIFIER)) {
            i += 2;
        }
        if (i < ctx.tokens.size() && ctx.tokens.get(i).is(TokenType.LESS)) {
            int depth = 0;
            while (i < ctx.tokens.size()) {
                TokenType tt = ctx.tokens.get(i).type();
                if (tt == TokenType.LESS) {
                    depth++;
                    i++;
                } else if (tt == TokenType.GREATER) {
                    depth--;
                    i++;
                    if (depth <= 0) break;
                } else if (tt == TokenType.GREATER_GREATER) {
                    depth -= 2;
                    i++;
                    if (depth <= 0) break;
                } else {
                    i++;
                }
            }
        }
        while (i < ctx.tokens.size()) {
            TokenType tt = ctx.tokens.get(i).type();
            if (tt == TokenType.QUESTION) {
                i++;
            } else if (tt == TokenType.LBRACKET && i + 1 < ctx.tokens.size()
                    && ctx.tokens.get(i + 1).is(TokenType.RBRACKET)) {
                i += 2;
            } else {
                break;
            }
        }
        return i < ctx.tokens.size() && ctx.tokens.get(i).is(TokenType.IDENTIFIER);
    }
}
