package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Membros de classe (REFACTOR-500, FASE 7): métodos, campos, construtores
 * e aninhamento de tipos dentro do corpo de classe/interface/record. Move o
 * cursor do {@link ParseContext} — nunca duplica estado.
 */
class ClassMemberParser {

    static AstNode parseClassMember(ParseContext ctx) {
        List<AnnotationNode> annos = AnnotationParser.parseAnnotations(ctx);
        List<String> mods = Parser.parseModifiers(ctx);
        if (ctx.check(TokenType.IDENTIFIER) && ctx.peek().value().equals("constructor") && ctx.checkNext(TokenType.LPAREN)) {
            ConstructorDeclarationNode ctor = parseConstructor(ctx, mods);
            return new ConstructorDeclarationNode(ctor.position(), ctor.modifiers(), ctor.name(),
                    ctor.parameters(), ctor.thrownExceptions(), ctor.body(), annos);
        }
        if (ctx.check(TokenType.IDENTIFIER) && Parser.isFunctionKeyword(ctx.peek().value())
                && ctx.peekAt(1).type() == TokenType.IDENTIFIER
                && ctx.peekAt(2).type() == TokenType.LPAREN) {
            // Mesmo furo do top-level (SG-001): `fun foo()`/`fn bar()` dentro de
            // classe era lido como método com tipo de retorno "fun". Rejeitado.
            ctx.error("Kof não usa '" + ctx.peek().value() + "'; declare como "
                    + "'Tipo nome(...) { }' ou 'nome(...): Tipo { }'", "PARSE085");
            ctx.advance();
        }
        if ((ctx.check(TokenType.IDENTIFIER) || ctx.check(TokenType.AWAIT) || ctx.check(TokenType.SPAWN)) && ctx.checkNext(TokenType.LPAREN)) {
            String name = ctx.advance().value();
            ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE011");
            List<FormalParameterNode> params = new ArrayList<>();
            if (!ctx.check(TokenType.RPAREN)) {
                params.add(TypeParser.parseFormalParameter(ctx));
                while (ctx.check(TokenType.COMMA)) { ctx.advance(); params.add(TypeParser.parseFormalParameter(ctx)); }
            }
            ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE012");
            String returnType = "void";
            if (ctx.check(TokenType.COLON)) {
                ctx.advance();
                returnType = TypeParser.parseTypeRef(ctx);
            }
            List<String> thrown = TypeParser.parseThrows(ctx);
            return finishMethod(ctx, mods, annos, name, params, returnType, thrown);
        }
        if (ctx.check(TokenType.IDENTIFIER, TokenType.BOOL_TYPE, TokenType.BYTE_TYPE, TokenType.SHORT_TYPE,
                TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE, TokenType.DOUBLE_TYPE,
                TokenType.CHAR_TYPE, TokenType.STRING_TYPE, TokenType.VOID)) {
            // Campo ou método com tipo de retorno explícito: `Type name(...)`.
            // parseTypeRef cobre retornos genéricos (`Set<Int>`, `List<String>`,
            // `Map<K,V>`) e nullable (`String?`) — o lookahead antigo de 2 tokens
            // quebrava porque assumia retorno de um token só.
            String type = TypeParser.parseTypeRef(ctx);
            String name = ctx.expectId("Expected member name", "PARSE018");
            if (ctx.check(TokenType.LPAREN)) {
                ctx.advance();
                List<FormalParameterNode> params = new ArrayList<>();
                if (!ctx.check(TokenType.RPAREN)) {
                    params.add(TypeParser.parseFormalParameter(ctx));
                    while (ctx.check(TokenType.COMMA)) { ctx.advance(); params.add(TypeParser.parseFormalParameter(ctx)); }
                }
                ctx.expect(TokenType.RPAREN, "Expected ')' after parameters", "PARSE019");
                String returnType = type;
                if (ctx.check(TokenType.COLON)) {
                    ctx.advance();
                    returnType = TypeParser.parseTypeRef(ctx);
                }
                List<String> thrown = TypeParser.parseThrows(ctx);
                return finishMethod(ctx, mods, annos, name, params, returnType, thrown);
            }
            FieldDeclarationNode f = (FieldDeclarationNode) parseField(ctx, mods, type, name);
            return new FieldDeclarationNode(f.position(), f.modifiers(), f.type(), f.name(),
                    f.initializer(), annos);
        }
        if (ctx.check(TokenType.CLASS, TokenType.INTERFACE, TokenType.RECORD, TokenType.ENTITY)) {
            return Parser.parseTypeDeclaration(ctx, annos);
        }
        if (ctx.check(TokenType.LBRACE)) {
            ConstructorDeclarationNode ctor = parseConstructor(ctx, mods);
            return new ConstructorDeclarationNode(ctor.position(), ctor.modifiers(), ctor.name(),
                    ctor.parameters(), ctor.thrownExceptions(), ctor.body(), annos);
        }
        ctx.error("Unexpected token in class body", "PARSE016");
        ctx.advance();
        return new FieldDeclarationNode(ctx.pos(), mods, "Object", "error", null, annos);
    }

    /** Corpo de método nas três formas: bloco, `= expr` e declaração vazia. */
    static AstNode finishMethod(ParseContext ctx, List<String> mods, List<AnnotationNode> annos, String name,
                                 List<FormalParameterNode> params, String returnType, List<String> thrown) {
        if (ctx.check(TokenType.LBRACE)) {
            List<StatementNode> body = StatementParser.parseBlock(ctx);
            return new MethodDeclarationNode(ctx.pos(), mods, returnType, name, params, thrown, body, annos);
        }
        if (ctx.check(TokenType.EQUAL)) {
            ctx.advance();
            ExpressionNode expr = ExpressionParser.parseExpression(ctx);
            if (ctx.check(TokenType.SEMICOLON)) ctx.advance();
            return new MethodDeclarationNode(ctx.pos(), mods, returnType, name, params, thrown,
                    List.of(new ReturnStmt(ctx.pos(), expr)), annos);
        }
        ctx.expectSemicolon();
        return new MethodDeclarationNode(ctx.pos(), mods, returnType, name, params, thrown, List.of(), annos);
    }

    static FieldDeclarationNode parseField(ParseContext ctx, List<String> mods, String type, String name) {
        ExpressionNode init = null;
        if (ctx.check(TokenType.EQUAL)) {
            ctx.advance();
            init = ExpressionParser.parseExpression(ctx);
        }
        ctx.expectSemicolon();
        return new FieldDeclarationNode(ctx.pos(), mods, type, name, init);
    }

    static ConstructorDeclarationNode parseConstructor(ParseContext ctx, List<String> mods) {
        String name;
        if (ctx.check(TokenType.IDENTIFIER) && ctx.peek().value().equals("constructor")) {
            ctx.advance();
            name = ctx.currentClassName != null ? ctx.currentClassName : "error";
        } else if (ctx.check(TokenType.IDENTIFIER)) {
            name = ctx.advance().value();
        } else {
            name = "error";
            ctx.error("Expected constructor name", "PARSE020");
        }
        List<FormalParameterNode> params = TypeParser.parseFormalParameters(ctx);
        List<String> thrown = TypeParser.parseThrows(ctx);
        List<StatementNode> body = List.of();
        if (ctx.check(TokenType.LBRACE)) {
            body = StatementParser.parseBlock(ctx);
        }
        return new ConstructorDeclarationNode(ctx.pos(), mods, name, params, thrown, body);
    }
}
