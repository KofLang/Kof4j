package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Expressões (REFACTOR-500, FASE 7): assignment, binary, unary, postfix,
 * primary, lambdas, new/call, switch-expressão e Query DSL. Move o cursor
 * do {@link ParseContext} — nunca duplica estado.
 */
class ExpressionParser {

    static ExpressionNode parseExpression(ParseContext ctx) {
        if (ctx.check(TokenType.SWITCH)) {
            return ExpressionParser.parseSwitchExpression(ctx);
        }
        return ExpressionParser.parseAssignment(ctx);
    }

    static ExpressionNode parseAssignment(ParseContext ctx) {
        ExpressionNode left = ExpressionParser.parseBinary(ctx, 0);
        if (ctx.check(TokenType.EQUAL, TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL,
                TokenType.STAR_EQUAL, TokenType.SLASH_EQUAL, TokenType.PERCENT_EQUAL,
                TokenType.AMP_EQUAL, TokenType.PIPE_EQUAL, TokenType.CARET_EQUAL,
                TokenType.LESS_LESS_EQUAL, TokenType.GREATER_GREATER_EQUAL,
                TokenType.GREATER_GREATER_GREATER_EQUAL)) {
            String op = ctx.advance().value();
            ExpressionNode right = ExpressionParser.parseAssignment(ctx);
            return new AssignmentExpr(ctx.pos(), left, op, right);
        }
        return left;
    }

    static ExpressionNode parseBinary(ParseContext ctx, int minPrec) {
        ExpressionNode left = ExpressionParser.parseUnary(ctx);
        while (isBinaryOp(ctx) && precedence(ctx, ctx.peek().value()) >= minPrec) {
            String op = ctx.advance().value();
            int prec = precedence(ctx, op);
            ExpressionNode right = ExpressionParser.parseBinary(ctx, prec + 1);
            left = new BinaryExpr(ctx.pos(), op, left, right);
        }
        return left;
    }

    static int precedence(ParseContext ctx, String op) {
        return switch (op) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|", "^" -> 3;
            case "&" -> 4;
            case "==", "!=", "<", "<=", ">", ">=", "instanceof", "as" -> 5;
            case "<<", ">>", ">>>" -> 6;
            case "+", "-" -> 7;
            case "*", "/", "%" -> 8;
            default -> -1;
        };
    }

    static boolean isBinaryOp(ParseContext ctx) {
        return switch (ctx.peek().type()) {
            case PLUS, MINUS, STAR, SLASH, PERCENT,
                 EQUAL_EQUAL, BANG_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
                 AMP_AMP, PIPE_PIPE, AMP, PIPE, CARET,
                 LESS_LESS, GREATER_GREATER, GREATER_GREATER_GREATER,
                 INSTANCEOF, AS -> true;
            default -> false;
        };
    }

    static ExpressionNode parseUnary(ParseContext ctx) {
        // spawn <expr> / await <expr> em posição de expressão — baixados como
        // chamadas sintéticas __kof_spawn_expr / __kof_await (sem AST novo)
        if (ctx.check(TokenType.SPAWN)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ExpressionNode e = ExpressionParser.parseUnary(ctx);
            return new MethodCallExpr(p, null, "__kof_spawn_expr", List.of(), List.of(e));
        }
        if (ctx.check(TokenType.AWAIT)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ExpressionNode e = ExpressionParser.parseUnary(ctx);
            return new MethodCallExpr(p, null, "__kof_await", List.of(), List.of(e));
        }
        if (ctx.check(TokenType.BANG)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ExpressionNode operand = ExpressionParser.parseUnary(ctx);
            return new UnaryExpr(p, "!", operand, true);
        }
        if (ctx.check(TokenType.MINUS)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ExpressionNode operand = ExpressionParser.parseUnary(ctx);
            return new UnaryExpr(p, "-", operand, true);
        }
        if (ctx.check(TokenType.PLUS_PLUS)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ExpressionNode operand = ExpressionParser.parseUnary(ctx);
            return new UnaryExpr(p, "++", operand, true);
        }
        if (ctx.check(TokenType.MINUS_MINUS)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ExpressionNode operand = ExpressionParser.parseUnary(ctx);
            return new UnaryExpr(p, "--", operand, true);
        }
        return ExpressionParser.parsePostfix(ctx);
    }

    static ExpressionNode parsePostfix(ParseContext ctx) {
        ExpressionNode expr = ExpressionParser.parsePrimary(ctx);
        while (true) {
            if (ctx.check(TokenType.LBRACE) && expr instanceof IdentifierExpr ie) {
                // trailing lambda call: identifier { ... } (transaction { ... })
                expr = new MethodCallExpr(ctx.pos(), null, ie.name(), List.of(),
                        List.of(new LambdaExpr(ctx.pos(), List.of(), StatementParser.parseBlock(ctx))));
            } else if (ctx.check(TokenType.DOT)) {
                ctx.advance();
                String field;
                if (ctx.check(TokenType.IDENTIFIER) || ctx.check(TokenType.AWAIT) || ctx.check(TokenType.SPAWN)) {
                    field = ctx.advance().value();
                } else if (ctx.isTypeKeywordField()) {
                    // type keywords are valid method names after a dot
                    // (config.int, user.toString, ...)
                    field = ctx.advance().value();
                } else {
                    field = ctx.expectId("Expected field name", "PARSE039");
                }
                if (ctx.check(TokenType.LBRACE)) {
                    // trailing lambda call: receiver.method { ... } — the
                    // block is the final argument of the method call.
                    // With explicit parameters (receiver.method { s -> ... }
                    // or { s: Int -> ... }) the block is a typed lambda: the
                    // remaining statements up to '}' form the lambda body.
                    if (LambdaParser.looksLikeLambdaBlockParams(ctx)) {
                        List<FormalParameterNode> params = LambdaParser.parseLambdaBlockParams(ctx);
                        ctx.expect(TokenType.ARROW, "Expected '->'", "PARSE042");
                        // the opening '{' was consumed by the parameter list;
                        // the lambda body is the statement list up to '}'
                        List<StatementNode> body = new ArrayList<>();
                        while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                            body.add(StatementParser.parseStatement(ctx));
                        }
                        ctx.expect(TokenType.RBRACE, "Expected '}'", "PARSE025");
                        expr = new MethodCallExpr(ctx.pos(), expr, field, List.of(),
                                List.of(new LambdaExpr(ctx.pos(), params, body)));
                    } else {
                        expr = new MethodCallExpr(ctx.pos(), expr, field, List.of(),
                                List.of(new LambdaExpr(ctx.pos(), List.of(), StatementParser.parseBlock(ctx))));
                    }
                } else {
                    expr = new FieldAccessExpr(ctx.pos(), expr, field);
                }
            } else if (ctx.check(TokenType.LBRACKET)) {
                SourcePosition p = ctx.pos();
                ctx.advance();
                ExpressionNode index = ExpressionParser.parseExpression(ctx);
                ctx.expect(TokenType.RBRACKET, "Expected ']'", "PARSE045");
                expr = new ArrayAccessExpr(p, expr, index);
            } else if (ctx.check(TokenType.LPAREN)) {
                List<ExpressionNode> args = ExpressionParser.parseArguments(ctx);
                if (ctx.check(TokenType.LBRACE)) {
                    // Query DSL tipada: `Entity.query(db) { where ...; }` — o `{`
                    // é o token atual; parseQueryDsl consome o bloco.
                    if (expr instanceof FieldAccessExpr fa && "query".equals(fa.fieldName())
                            && fa.receiver() instanceof IdentifierExpr qr
                            && ctx.entityNames.contains(qr.name()) && args.size() == 1) {
                        return ExpressionParser.parseQueryDsl(ctx, fa.position(), qr.name(), args.get(0));
                    }
                    args.add(new LambdaExpr(ctx.pos(), List.of(), StatementParser.parseBlock(ctx)));
                }
                if (expr instanceof IdentifierExpr ie) {
                    expr = new MethodCallExpr(ctx.pos(), null, ie.name(), List.of(), args);
                } else if (expr instanceof FieldAccessExpr fa) {
                    expr = new MethodCallExpr(ctx.pos(), fa.receiver(), fa.fieldName(), List.of(), args);
                } else {
                    expr = new MethodCallExpr(ctx.pos(), expr, "", List.of(), args);
                }
            } else if (ctx.check(TokenType.LESS) && (expr instanceof IdentifierExpr || expr instanceof FieldAccessExpr)
                    && ExpressionParser.looksLikeGenericCall(ctx)) {
                List<String> typeArgs = ExpressionParser.parseCallTypeArguments(ctx);
                List<ExpressionNode> args = ExpressionParser.parseArguments(ctx);
                if (ctx.check(TokenType.LBRACE)) {
                    args.add(new LambdaExpr(ctx.pos(), List.of(), StatementParser.parseBlock(ctx)));
                }
                if (expr instanceof IdentifierExpr ie3) {
                    expr = new MethodCallExpr(ctx.pos(), null, ie3.name(), typeArgs, args);
                } else if (expr instanceof FieldAccessExpr fa2) {
                    expr = new MethodCallExpr(ctx.pos(), fa2.receiver(), fa2.fieldName(), typeArgs, args);
                }
            } else if (ctx.check(TokenType.PLUS_PLUS)
                    && (expr instanceof IdentifierExpr || expr instanceof FieldAccessExpr
                    || expr instanceof ArrayAccessExpr)) {
                ctx.advance();
                expr = new UnaryExpr(ctx.pos(), "++", expr, false);
            } else if (ctx.check(TokenType.MINUS_MINUS)
                    && (expr instanceof IdentifierExpr || expr instanceof FieldAccessExpr
                    || expr instanceof ArrayAccessExpr)) {
                ctx.advance();
                expr = new UnaryExpr(ctx.pos(), "--", expr, false);
            } else {
                break;
            }
        }
        return expr;
    }

    static ExpressionNode parsePrimary(ParseContext ctx) {
        if (ctx.check(TokenType.INT_LITERAL, TokenType.LONG_LITERAL, TokenType.FLOAT_LITERAL,
                TokenType.DOUBLE_LITERAL, TokenType.STRING_LITERAL, TokenType.CHAR_LITERAL,
                TokenType.BOOLEAN_LITERAL, TokenType.NULL_LITERAL)) {
            Token t = ctx.advance();
            LiteralKind kind = switch (t.type()) {
                case INT_LITERAL -> ConcreteLiteralKind.INT;
                case LONG_LITERAL -> ConcreteLiteralKind.LONG;
                case FLOAT_LITERAL -> ConcreteLiteralKind.FLOAT;
                case DOUBLE_LITERAL -> ConcreteLiteralKind.DOUBLE;
                case STRING_LITERAL -> ConcreteLiteralKind.STRING;
                case CHAR_LITERAL -> ConcreteLiteralKind.CHAR;
                case BOOLEAN_LITERAL -> ConcreteLiteralKind.BOOLEAN;
                case NULL_LITERAL -> ConcreteLiteralKind.NULL;
                default -> ConcreteLiteralKind.NULL;
            };
            // faixa de literais numéricos: Long fora do range crashava o
            // emit (NumberFormatException crua em CompilerDriver.emitExpression);
            // aqui vira diagnóstico limpo (bug 25).
            if (t.type() == TokenType.LONG_LITERAL) {
                try {
                    Long.parseLong(t.value().replaceAll("[lL]$", ""));
                } catch (NumberFormatException e) {
                    ctx.error("numeric literal out of range: " + t.value(), "PARSE084");
                    return new LiteralExpr(ctx.pos(), ConcreteLiteralKind.NULL, "0");
                }
            }
            return new LiteralExpr(ctx.pos(), kind, t.value());
        }
        if (ctx.check(TokenType.THIS)) {
            Token t = ctx.advance();
            return new IdentifierExpr(ctx.pos(), t.value());
        }
        if (ctx.check(TokenType.SUPER)) {
            Token t = ctx.advance();
            return new IdentifierExpr(ctx.pos(), t.value());
        }
        if (ctx.check(TokenType.IDENTIFIER)) {
            return new IdentifierExpr(ctx.pos(), ctx.advance().value());
        }
        if (ctx.check(TokenType.NEW)) {
            return ExpressionParser.parseNewExpression(ctx);
        }
        if (ctx.check(TokenType.LPAREN)) {
            if (LambdaParser.looksLikeLambdaParams(ctx)) {
                List<FormalParameterNode> params = LambdaParser.parseLambdaParams(ctx);
                ctx.expect(TokenType.ARROW, "Expected '->'", "PARSE042");
                return new LambdaExpr(ctx.pos(), params, LambdaParser.parseLambdaBody(ctx));
            }
            ctx.advance();
            ExpressionNode expr = ExpressionParser.parseExpression(ctx);
            ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
            return expr;
        }
        if (ctx.check(TokenType.IF)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            ctx.expect(TokenType.LPAREN, "Expected '(' after if", "PARSE043");
            ExpressionNode condition = ExpressionParser.parseExpression(ctx);
            ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
            ExpressionNode thenExpr = ExpressionParser.parseExpression(ctx);
            ctx.expect(TokenType.ELSE, "Expected 'else'", "PARSE044");
            ExpressionNode elseExpr = ExpressionParser.parseExpression(ctx);
            return new IfExpr(p, condition, thenExpr, elseExpr);
        }
        if (ctx.check(TokenType.LBRACE)) {
            List<FormalParameterNode> params = new ArrayList<>();
            List<StatementNode> body = StatementParser.parseBlock(ctx);
            return new LambdaExpr(ctx.pos(), params, body);
        }
        ctx.error("Unexpected token in expression: " + ctx.peek().value(), "PARSE041");
        ctx.advance();
        return new IdentifierExpr(ctx.pos(), "error");
    }


    static ExpressionNode parseNewExpression(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        String typeName = ExpressionParser.parseNewTypeRef(ctx);
        List<String> typeArgs = List.of();
        if (ctx.check(TokenType.LESS)) {
            ctx.advance();
            typeArgs = new ArrayList<>();
            while (!ctx.check(TokenType.GREATER) && !ctx.atEnd()) {
                if (ctx.check(TokenType.IDENTIFIER) || TypeParser.isPrimitiveType(ctx)) {
                    typeArgs.add(TypeParser.parseTypeRef(ctx));
                } else {
                    ctx.advance();
                }
                if (ctx.check(TokenType.COMMA)) ctx.advance();
            }
            ctx.expect(TokenType.GREATER, "Expected '>' after type arguments", "PARSE076");
        }
        if (ctx.check(TokenType.LBRACKET)) {
            ctx.advance();
            ExpressionNode size = ExpressionParser.parseExpression(ctx);
            ctx.expect(TokenType.RBRACKET, "Expected ']'", "PARSE046");
            return new NewArrayExpr(p, typeName, size);
        }
        List<ExpressionNode> args = ExpressionParser.parseArguments(ctx);
        return new NewExpr(p, typeName, typeArgs, args);
    }

    static String parseNewTypeRef(ParseContext ctx) {
        if (ctx.check(TokenType.VOID)) {
            ctx.advance();
            return "void";
        }
        if (TypeParser.isPrimitiveType(ctx)) {
            return ctx.advance().value();
        }
        if (ctx.check(TokenType.IDENTIFIER)) {
            String name = ctx.peek().value();
            if (Parser.PRIMITIVE_TYPE_NAMES.contains(name.toLowerCase()) || Parser.PRIMITIVE_TYPE_NAMES.contains(name)) {
                ctx.advance();
                return name;
            }
            StringBuilder type = new StringBuilder();
            type.append(ctx.advance().value());
            while (ctx.check(TokenType.DOT) && ctx.checkNext(TokenType.IDENTIFIER)) {
                ctx.advance();
                type.append('.').append(ctx.advance().value());
            }
            return type.toString();
        }
        ctx.error("Expected type", "PARSE044");
        return "Object";
    }

    static boolean looksLikeGenericCall(ParseContext ctx) {
        if (!ctx.check(TokenType.LESS)) return false;
        int i = ctx.pos + 1;
        int depth = 1;
        while (i < ctx.tokens.size()) {
            Token t = ctx.tokens.get(i);
            switch (t.type()) {
                case LESS -> depth++;
                case GREATER -> {
                    depth--;
                    if (depth == 0) return i + 1 < ctx.tokens.size() && ctx.tokens.get(i + 1).type() == TokenType.LPAREN;
                }
                case GREATER_GREATER -> {
                    depth -= 2;
                    if (depth == 0) return i + 1 < ctx.tokens.size() && ctx.tokens.get(i + 1).type() == TokenType.LPAREN;
                }
                case GREATER_GREATER_GREATER -> {
                    depth -= 3;
                    if (depth == 0) return i + 1 < ctx.tokens.size() && ctx.tokens.get(i + 1).type() == TokenType.LPAREN;
                }
                case IDENTIFIER, INT_TYPE, LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE, BOOL_TYPE,
                        BYTE_TYPE, SHORT_TYPE, CHAR_TYPE, STRING_TYPE, DOT, COMMA,
                        LBRACKET, RBRACKET, QUESTION, LPAREN, RPAREN, ARROW -> { }
                default -> {
                    return false;
                }
            }
            i++;
        }
        return false;
    }

    static List<String> parseCallTypeArguments(ParseContext ctx) {
        List<String> typeArgs = new ArrayList<>();
        Parser.splitShiftRight(ctx);
        ctx.expect(TokenType.LESS, "Expected '<'", "PARSE078");
        while (!ctx.check(TokenType.GREATER) && !ctx.atEnd()) {
            Parser.splitShiftRight(ctx);
            if (ctx.check(TokenType.IDENTIFIER) || TypeParser.isPrimitiveType(ctx)) {
                String typeRef = TypeParser.parseTypeRef(ctx);
                while (ctx.check(TokenType.LBRACKET) && ctx.checkNext(TokenType.RBRACKET)) {
                    ctx.advance();
                    ctx.advance();
                    typeRef += "[]";
                }
                typeArgs.add(typeRef);
            } else {
                ctx.advance();
            }
            if (ctx.check(TokenType.COMMA)) ctx.advance();
        }
        Parser.splitShiftRight(ctx);
        ctx.expect(TokenType.GREATER, "Expected '>'", "PARSE079");
        return typeArgs;
    }


    /**
     * Switch como expressão (SYN001): {@code switch (e) { case A -> b; case T v -> c;
     * default -> d }}. Forma aditiva — os cases usam {@code ->} e o corpo de cada
     * caso é UMA expressão (o valor do caso). O {@code default} é obrigatório
     * (validado no analyzer, SEM032) — uma expressão não pode "cair" sem valor.
     */
    static ExpressionNode parseSwitchExpression(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance(); // switch
        ctx.expect(TokenType.LPAREN, "Expected '(' after 'switch'", "PARSE070");
        ExpressionNode expr = ExpressionParser.parseExpression(ctx);
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE071");
        ctx.expect(TokenType.LBRACE, "Expected '{'", "PARSE072");
        List<SwitchExprCase> cases = new ArrayList<>();
        ExpressionNode defaultValue = null;
        while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
            if (ctx.check(TokenType.CASE)) {
                SourcePosition cp = ctx.pos();
                ctx.advance();
                ExpressionNode value = StatementParser.parseSwitchCasePatternOrValue(ctx, cp);
                ctx.expect(TokenType.ARROW,
                        "Switch expressão exige '->' (a forma statement usa ':')", "PARSE076");
                ExpressionNode body = ExpressionParser.parseExpression(ctx);
                cases.add(new SwitchExprCase(cp, value, body));
            } else if (ctx.check(TokenType.DEFAULT)) {
                ctx.advance();
                ctx.expect(TokenType.ARROW, "Expected '->' after 'default'", "PARSE077");
                defaultValue = ExpressionParser.parseExpression(ctx);
            } else {
                ctx.error("Esperava 'case' ou 'default' em switch expressão", "PARSE078");
                ctx.advance();
            }
        }
        ctx.expect(TokenType.RBRACE, "Expected '}'", "PARSE075");
        return new SwitchExpr(p, expr, cases, defaultValue);
    }

    static List<ExpressionNode> parseArguments(ParseContext ctx) {
        ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE042");
        List<ExpressionNode> args = new ArrayList<>();
        if (!ctx.check(TokenType.RPAREN)) {
            args.add(ExpressionParser.parseExpression(ctx));
            while (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                args.add(ExpressionParser.parseExpression(ctx));
            }
        }
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE043");
        return args;
    }

    /**
     * Query DSL tipada (ORM001): {@code Entity.query(db) { where age > 18;
     * orderBy name desc; limit 10 }}. Consome o bloco {@code { ... }} e
     * devolve um {@link QueryDslExpr}; o lowering (CompilerDriver) gera
     * {@code db.query<Entity>(db, "SELECT ...", binds...)}.
     */
    static ExpressionNode parseQueryDsl(ParseContext ctx, SourcePosition pos, String entity, ExpressionNode dbArg) {
        ctx.expect(TokenType.LBRACE, "Expected '{'", "PARSE024");
        List<ExpressionNode> wheres = new ArrayList<>();
        List<ExpressionNode> orderFields = new ArrayList<>();
        List<String> orderDirs = new ArrayList<>();
        ExpressionNode limit = null;
        while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
            if (ctx.check(TokenType.IDENTIFIER) && "where".equals(ctx.peek().value())) {
                ctx.advance();
                wheres.add(ExpressionParser.parseExpression(ctx));
                ctx.expectSemicolon();
            } else if (ctx.check(TokenType.IDENTIFIER) && "orderBy".equals(ctx.peek().value())) {
                ctx.advance();
                orderFields.add(ExpressionParser.parseIdentifierOrLiteral(ctx));
                String dir = "asc";
                if (ctx.check(TokenType.IDENTIFIER) && ("desc".equals(ctx.peek().value())
                        || "asc".equals(ctx.peek().value()))) {
                    dir = ctx.advance().value();
                }
                orderDirs.add(dir);
                ctx.expectSemicolon();
            } else if (ctx.check(TokenType.IDENTIFIER) && "limit".equals(ctx.peek().value())) {
                ctx.advance();
                limit = ExpressionParser.parseExpression(ctx);
                ctx.expectSemicolon();
            } else {
                ctx.error("Expected 'where', 'orderBy' or 'limit' in query block", "PARSE090");
                ctx.advance();
            }
        }
        ctx.expect(TokenType.RBRACE, "Expected '}' after query block", "PARSE025");
        return new QueryDslExpr(pos, entity, dbArg, wheres, orderFields, orderDirs, limit);
    }

    /** Identificador ou literal (para o campo do `orderBy`). */
    static ExpressionNode parseIdentifierOrLiteral(ParseContext ctx) {
        if (ctx.check(TokenType.IDENTIFIER)) {
            return new IdentifierExpr(ctx.pos(), ctx.advance().value());
        }
        return ExpressionParser.parsePrimary(ctx);
    }
}
