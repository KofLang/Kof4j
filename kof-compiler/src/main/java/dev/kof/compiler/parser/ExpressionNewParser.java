package dev.kof.compiler.parser;

import java.util.ArrayList;
import java.util.List;

import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.NewArrayExpr;
import dev.kof.compiler.NewExpr;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.Token;
import dev.kof.compiler.TokenType;

/** Helper de parsing de new/type-ref (extraído do ExpressionParser, ≤500). */
final class ExpressionNewParser {

    private ExpressionNewParser() {}

    static ExpressionNode parseNewExpression(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance();
        String typeName = ExpressionNewParser.parseNewTypeRef(ctx);
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
            // multidimensional: new T[a][b][...] — dimensões adicionais explícitas
            List<ExpressionNode> moreDims = new ArrayList<>();
            while (ctx.check(TokenType.LBRACKET)) {
                ctx.advance();
                moreDims.add(ExpressionParser.parseExpression(ctx));
                ctx.expect(TokenType.RBRACKET, "Expected ']'", "PARSE046");
            }
            return new NewArrayExpr(p, typeName, size, moreDims);
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

}