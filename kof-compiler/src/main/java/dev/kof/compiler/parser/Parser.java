package dev.kof.compiler.parser;
import dev.kof.compiler.AnnotationNode;
import dev.kof.compiler.ApplicationDeclarationNode;
import dev.kof.compiler.AstNode;
import dev.kof.compiler.CompilationUnitNode;
import dev.kof.compiler.DiagnosticCollector;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.ExpressionStmt;
import dev.kof.compiler.ExternalFunctionNode;
import dev.kof.compiler.FormalParameterNode;
import dev.kof.compiler.FunctionDeclarationNode;
import dev.kof.compiler.InfraDeclarationNode;
import dev.kof.compiler.MethodCallExpr;
import dev.kof.compiler.ReturnStmt;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.TestDeclarationNode;
import dev.kof.compiler.Token;
import dev.kof.compiler.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser recursivo-descendente (entrada pública) — REFACTOR-500, FASE 7.
 * Mantém o API inalterado ({@code new Parser(tokens, diagnostics, file).parse()})
 * e orquestra os parsers extraídos, todos compartilhando o MESMO
 * {@link ParseContext} (cursor {@code pos} único; nenhum estado duplicado):
 * <ul>
 *   <li>{@link StatementParser} — statements</li>
 *   <li>{@link ExpressionParser} / {@link LambdaParser} — expressões e lambdas</li>
 *   <li>{@link TypeParser} — tipos, parâmetros formais e throws</li>
 *   <li>{@link AnnotationParser} / {@link ClassMemberParser} — annotations e membros</li>
 *   <li>{@link TypeDeclarations} — declarações de tipo (class/interface/
 *       record/enum/entity) — extraído no ratchet do §140 (Parser 513&gt;500)</li>
 * </ul>
 * Permanecem aqui: entrypoint, package/imports/test/application,
 * e os helpers de token compartilhados
 * ({@code splitShiftRight}, {@code isGenericReturnTypeAhead}).
 */
public class Parser {

    private final ParseContext ctx;

    public Parser(List<Token> tokens, DiagnosticCollector diagnostics, String file) {
        this.ctx = new ParseContext(tokens, diagnostics, file);
    }

    public CompilationUnitNode parse() {
        SourcePosition pos0 = ctx.pos();
        String packageName = parsePackage(ctx);
        List<String> imports = parseImports(ctx);
        List<AstNode> declarations = new ArrayList<>();
        while (!ctx.atEnd()) {
            List<AnnotationNode> annos = AnnotationParser.parseAnnotations(ctx);
            if (ctx.check(TokenType.FUN, TokenType.FN, TokenType.FUNC)) {
                rejectFunctionKeyword(ctx);
                continue;
            }
            if (ctx.check(TokenType.IDENTIFIER) && "test".equals(ctx.peek().value()) && ctx.checkNext(TokenType.STRING_LITERAL)) {
                declarations.add(parseTestDeclaration(ctx));
            } else if (ctx.check(TokenType.IDENTIFIER) && "application".equals(ctx.peek().value())
                    && ctx.checkNext(TokenType.LBRACE)) {
                declarations.add(parseApplicationDeclaration(ctx));
            } else if (ctx.check(TokenType.IDENTIFIER) && "infra".equals(ctx.peek().value())
                    && ctx.checkNext(TokenType.STRING_LITERAL)) {
                declarations.add(parseInfraDeclaration(ctx));
            } else if (!annos.isEmpty()
                    && (ctx.check(TokenType.CLASS, TokenType.INTERFACE, TokenType.RECORD, TokenType.ENTITY))) {
                declarations.add(TypeDeclarations.parseTypeDeclaration(ctx, annos));
            } else if (ctx.check(TokenType.EXTERN)) {
                declarations.add(parseExternDeclaration(ctx));
            } else if (ctx.sealedModifierAhead()) {
                // X5.1 (D-X5-SURFACE): `sealed class/record/interface` — keyword
                // contextual; sem este ramo o IDENTIFIER `sealed` cairia no ramo
                // de função (PARSE010).
                declarations.add(TypeDeclarations.parseTypeDeclaration(ctx, annos));
            } else if (ctx.check(TokenType.IDENTIFIER) || ctx.check(TokenType.VOID) || TypeParser.isPrimitiveType(ctx)
                    || ctx.check(TokenType.LPAREN)) {
                declarations.add(parseFunctionDeclaration(ctx, List.of(), annos));
            } else {
                declarations.add(TypeDeclarations.parseTypeDeclaration(ctx, annos));
            }
        }
        return new CompilationUnitNode(pos0, packageName, imports, List.copyOf(declarations));
    }



    /**
     * `test "nome" { ... }` — corpo analisado como bloco de statements;
     * o lowering transforma cada teste numa função void sem argumentos.
     */
    static TestDeclarationNode parseTestDeclaration(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance(); // consome 'test'
        Token nameToken = ctx.expect(TokenType.STRING_LITERAL, "Expected test name string", "PARSE010");
        List<StatementNode> body = StatementParser.parseBlock(ctx);
        return new TestDeclarationNode(p, nameToken.value(), body);
    }

    /**
     * `application { onStart { ... } onShutdown { ... } }` — bloco de
     * lifecycle. Cada bloco nomeado (onStart/onShutdown) é parseado como
     * bloco de statements; o lowering sintetiza funções chamadas no
     * prólogo/epílogo do main.
     */
    static ApplicationDeclarationNode parseApplicationDeclaration(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance(); // consome 'application'
        List<StatementNode> onStart = List.of();
        List<StatementNode> onShutdown = List.of();
        ctx.expect(TokenType.LBRACE, "Expected '{' after application", "PARSE051");
        while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
            if (ctx.check(TokenType.IDENTIFIER)) {
                String blockName = ctx.peek().value();
                if ("onStart".equals(blockName) || "onShutdown".equals(blockName)) {
                    ctx.advance();
                    if (ctx.check(TokenType.LBRACE)) {
                        List<StatementNode> body = StatementParser.parseBlock(ctx);
                        if ("onStart".equals(blockName)) onStart = body;
                        else onShutdown = body;
                    } else {
                        ctx.expect(TokenType.LBRACE, "Expected '{' after " + blockName, "PARSE051");
                    }
                } else {
                    ctx.expect(TokenType.RBRACE, "Expected onStart/onShutdown block in application", "PARSE051");
                }
            } else {
                ctx.expect(TokenType.RBRACE, "Expected onStart/onShutdown block in application", "PARSE051");
            }
        }
        ctx.expect(TokenType.RBRACE, "Expected '}' after application block", "PARSE051");
        return new ApplicationDeclarationNode(p, onStart, onShutdown);
    }

    /**
     * `infra "prod" { resource("fs", "web") ... }` — bloco declarativo do
     * Makealive (linha 3.2, {@code D-MAKEALIVE-SYNTAX} 21/09). É açúcar puro:
     * o lowering vira `design(): Infrastructure`. O corpo só aceita CHAMADAS
     * diretas às faces do host (`resource`/`prop`/`requires`) — qualquer outro
     * statement é erro honesto (R6), nunca ignorado em silêncio.
     */
    static InfraDeclarationNode parseInfraDeclaration(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.advance(); // consome 'infra'
        Token nameToken = ctx.expect(TokenType.STRING_LITERAL, "Expected infra name string", "PARSE010");
        List<StatementNode> body = StatementParser.parseBlock(ctx);
        for (StatementNode st : body) {
            boolean builderCall = st instanceof ExpressionStmt es
                    && es.expression() instanceof MethodCallExpr mc && mc.receiver() == null;
            if (!builderCall) {
                ctx.error("infra body accepts only builder calls (resource/prop/requires)", "PARSE051");
                break;
            }
        }
        return new InfraDeclarationNode(p, nameToken.value(), body);
    }

    static FunctionDeclarationNode parseFunctionDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        SourcePosition p = ctx.pos();
        // Kof NÃO tem keyword de declaração de função. `fun`/`fn`/`func` são
        // RESERVADOS no lexer (tokens FUN/FN/FUNC, SG-001 06/09) — nunca chegam
        // aqui como IDENTIFIER; o dispatch top-level os rejeita (PARSE085).
        String returnType = "void";
        String name;
        if (ctx.check(TokenType.LPAREN)) {
            // Function type as return type: `(Int) -> Int makeDoubler() { ... }` (issue #218)
            returnType = TypeParser.parseTypeRef(ctx);
            name = ctx.expectId("Expected function name", "PARSE010");
        } else if ((ctx.check(TokenType.IDENTIFIER) || ctx.check(TokenType.VOID) || TypeParser.isPrimitiveType(ctx))
                && !ctx.checkNext(TokenType.LPAREN)
                && (isGenericReturnTypeAhead(ctx) || !ctx.checkNext(TokenType.LESS))) {
            returnType = ctx.advance().value();
            if (ctx.check(TokenType.LESS)) {
                returnType = returnType + TypeParser.consumeGenericTypeArgs(ctx);
            }
            // suffixo de tipo no retorno: `String? f()`, `Int[] f()`
            while (ctx.check(TokenType.QUESTION)
                    || (ctx.check(TokenType.LBRACKET) && ctx.checkNext(TokenType.RBRACKET))) {
                if (ctx.check(TokenType.QUESTION)) {
                    ctx.advance();
                    // D-TROOL (19/09): `Bool?` no retorno morre com SEM095;
                    // o tres-estado e `Troolean` (DECISIONS.md).
                    if (TypeParser.isBoolBase(returnType)) {
                        ctx.error("'Bool' has two values; for true/false/unknown use "
                                + "'Troolean' (DECISIONS.md D-TROOL)", "SEM095");
                    }
                    returnType += "?";
                } else {
                    ctx.advance();
                    ctx.advance();
                    returnType += "[]";
                }
            }
            name = ctx.expectId("Expected function name", "PARSE010");
        } else {
            name = ctx.expectId("Expected function name", "PARSE010");
        }
        List<String> typeParams = TypeParser.parseTypeParameters(ctx);
        ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE011");
        List<FormalParameterNode> params = new ArrayList<>();
        if (!ctx.check(TokenType.RPAREN)) {
            params.add(TypeParser.parseFormalParameter(ctx));
            while (ctx.check(TokenType.COMMA)) { ctx.advance(); params.add(TypeParser.parseFormalParameter(ctx)); }
        }
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE012");
        if (ctx.check(TokenType.COLON)) {
            ctx.advance();
            returnType = TypeParser.parseTypeRef(ctx);
        }
        // SG-019: cláusula throw do top-level — antes nem era capturada aqui
        // (só métodos de classe capturavam; o gap dizia "decorativa")
        List<String> thrown = TypeParser.parseThrows(ctx);
        List<StatementNode> body = List.of();
        if (ctx.check(TokenType.LBRACE)) {
            body = StatementParser.parseBlock(ctx);
        } else if (ctx.check(TokenType.EQUAL)) {
            ctx.advance();
            ExpressionNode expr = ExpressionParser.parseExpression(ctx);
            if (ctx.check(TokenType.SEMICOLON)) ctx.advance();
            // pos pré-capturada (bug 75): pós-parse apontaria o token seguinte
            body = List.of(new ReturnStmt(p, expr));
        } else {
            ctx.expectSemicolon();
        }
        return new FunctionDeclarationNode(p, mods, returnType, name, params, thrown, typeParams, body, annos);
    }

    /**
     * FFI (TIER 2.1): {@code extern name(params): ReturnType;} — formaliza a
     * assinatura de uma função externa em compile-time. Não há corpo: o binding
     * é responsabilidade do runtime por target (JVM/Native); JS emite FFI002.
     */
    static ExternalFunctionNode parseExternDeclaration(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.expect(TokenType.EXTERN, "Expected 'extern'", "PARSE090");
        String library = null;
        if (ctx.check(TokenType.STRING_LITERAL)) {
            library = ctx.advance().value();
        }
        String name = ctx.expectId("Expected extern function name", "PARSE091");
        if (ctx.check(TokenType.LESS)) TypeParser.parseTypeParameters(ctx);
        ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE092");
        List<FormalParameterNode> params = new ArrayList<>();
        if (!ctx.check(TokenType.RPAREN)) {
            params.add(TypeParser.parseFormalParameter(ctx));
            while (ctx.check(TokenType.COMMA)) { ctx.advance(); params.add(TypeParser.parseFormalParameter(ctx)); }
        }
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE093");
        String returnType = "void";
        if (ctx.check(TokenType.COLON)) {
            ctx.advance();
            returnType = TypeParser.parseTypeRef(ctx);
        }
        ctx.expectSemicolon();
        return new ExternalFunctionNode(p, library, returnType, name, params);
    }

    /**
     * `fun`/`fn`/`func` são palavras RESERVADAS (SG-001, 06/09) — o lexer as
     * mapeia para tokens FUN/FN/FUNC, então NUNCA são identificador (nem nome
     * de função, variável, parâmetro, campo). Em posição de declaração o
     * parser dá PARSE085 com a forma correta; em outra posição, o
     * `expectId`/`check(IDENTIFIER)` de cada parser já falha com diagnóstico.
     */
    static void rejectFunctionKeyword(ParseContext ctx) {
        String w = ctx.advance().value();
        ctx.error("'" + w + "' is a reserved word (Kof has no function keyword); "
                + "declare as 'Type name(...) { }' or 'name(...): Type { }'", "PARSE085");
    }

    /**
     * True when the current token is a generic return type: IDENTIFIER '<'
     * type args '>' IDENTIFIER '(' — e.g. "List<Int> ints(".
     */
    static boolean isGenericReturnTypeAhead(ParseContext ctx) {
        if (!ctx.checkNext(TokenType.LESS)) return false;
        int depth = 0;
        for (int i = 1; i + 1 < ctx.tokens.size() - ctx.pos; i++) {
            TokenType t = ctx.tokens.get(ctx.pos + i).type();
            if (t == TokenType.LESS) depth++;
            else if (t == TokenType.GREATER || t == TokenType.GREATER_GREATER
                    || t == TokenType.GREATER_GREATER_GREATER) {
                // #617: `List<List<Int>>` — o lexer emite `>>` como UM token
                // (maximal munch); sem contar seu fechamento duplo, esta
                // varredura nunca via depth chegar a 0 e devolvia false —
                // `List<List<Int>> nest()` era mal-interpretado como
                // função chamada "List" com type-params `<List<Int>>`,
                // que também não fecha (mesma classe de bug em
                // parseTypeParameters), cascata de PARSE075/PARSE011/...
                depth -= t == TokenType.GREATER ? 1 : t == TokenType.GREATER_GREATER ? 2 : 3;
                if (depth <= 0) {
                    return ctx.tokens.get(ctx.pos + i + 1).type() == TokenType.IDENTIFIER
                            && ctx.tokens.get(ctx.pos + i + 2).type() == TokenType.LPAREN;
                }
            } else if (t == TokenType.EOF) {
                return false;
            }
        }
        return false;
    }

    static void splitShiftRight(ParseContext ctx) {
        Token cur = ctx.tokens.get(ctx.pos);
        if (cur.type() == TokenType.GREATER_GREATER) {
            ctx.tokens.set(ctx.pos, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column(), cur.offset(), 1));
            ctx.tokens.add(ctx.pos + 1, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column() + 1, cur.offset() + 1, 1));
        } else if (cur.type() == TokenType.GREATER_GREATER_GREATER) {
            ctx.tokens.set(ctx.pos, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column(), cur.offset(), 1));
            ctx.tokens.add(ctx.pos + 1, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column() + 1, cur.offset() + 1, 1));
            ctx.tokens.add(ctx.pos + 2, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column() + 2, cur.offset() + 2, 1));
        }
    }

    static String parsePackage(ParseContext ctx) {
        if (!ctx.check(TokenType.PACKAGE)) return "";
        ctx.advance();
        StringBuilder name = new StringBuilder();
        if (!ctx.check(TokenType.IDENTIFIER)) {
            ctx.error("Expected package name", "PARSE001");
            return "";
        }
        name.append(ctx.advance().value());
        while (ctx.check(TokenType.DOT)) {
            ctx.advance();
            if (ctx.check(TokenType.IDENTIFIER)) {
                name.append('.').append(ctx.advance().value());
            } else {
                ctx.error("Expected package name component", "PARSE002");
                break;
            }
        }
        ctx.expectSemicolon();
        return name.toString();
    }

    static List<String> parseImports(ParseContext ctx) {
        List<String> result = new ArrayList<>();
        while (ctx.check(TokenType.IMPORT)) {
            ctx.advance();
            if (ctx.check(TokenType.STAR)) {
                ctx.advance();
                result.add("*");
            } else {
                StringBuilder path = new StringBuilder();
                if (!ctx.check(TokenType.IDENTIFIER)) {
                    ctx.error("Expected import name", "PARSE004");
                    break;
                }
                path.append(ctx.advance().value());
                while (ctx.check(TokenType.DOT)) {
                    ctx.advance();
                    if (ctx.check(TokenType.STAR)) {
                        ctx.advance();
                        path.append(".*");
                        break;
                    } else if (ctx.check(TokenType.IDENTIFIER)) {
                        path.append('.').append(ctx.advance().value());
                    } else {
                        ctx.error("Expected import path component", "PARSE005");
                        break;
                    }
                }
                result.add(path.toString());
            }
            ctx.expectSemicolon();
        }
        return result;
    }


}
