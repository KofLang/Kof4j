package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
 * </ul>
 * Permanecem aqui: entrypoint, package/imports/test/application,
 * declarações de tipo, e os helpers de token compartilhados
 * ({@code splitShiftRight}, {@code isGenericReturnTypeAhead}).
 */
class Parser {

    static final Set<String> PRIMITIVE_TYPE_NAMES = Set.of(
            "bool", "byte", "short", "int", "long", "float", "double", "char", "string", "void"
    );

    private final ParseContext ctx;

    Parser(List<Token> tokens, DiagnosticCollector diagnostics, String file) {
        this.ctx = new ParseContext(tokens, diagnostics, file);
    }

    CompilationUnitNode parse() {
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
            } else if (!annos.isEmpty()

                    && (ctx.check(TokenType.CLASS, TokenType.INTERFACE, TokenType.RECORD, TokenType.ENTITY))) {
                declarations.add(parseTypeDeclaration(ctx, annos));
            } else if (ctx.check(TokenType.EXTERN)) {
                declarations.add(parseExternDeclaration(ctx));
            } else if (ctx.check(TokenType.IDENTIFIER) || ctx.check(TokenType.VOID) || TypeParser.isPrimitiveType(ctx)) {
                declarations.add(parseFunctionDeclaration(ctx, List.of(), annos));
            } else {
                declarations.add(parseTypeDeclaration(ctx, annos));
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


    static FunctionDeclarationNode parseFunctionDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        SourcePosition p = ctx.pos();

        // Kof NÃO tem keyword de declaração de função. `fun`/`fn`/`func` são
        // RESERVADOS no lexer (tokens FUN/FN/FUNC, SG-001 06/09) — nunca chegam
        // aqui como IDENTIFIER; o dispatch top-level os rejeita (PARSE085).

        String returnType = "void";
        String name;
        if ((ctx.check(TokenType.IDENTIFIER) || ctx.check(TokenType.VOID) || TypeParser.isPrimitiveType(ctx))
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
        List<String> thrown = new ArrayList<>();
        List<StatementNode> body = List.of();
        if (ctx.check(TokenType.LBRACE)) {
            body = StatementParser.parseBlock(ctx);
        } else if (ctx.check(TokenType.EQUAL)) {
            ctx.advance();
            ExpressionNode expr = ExpressionParser.parseExpression(ctx);
            if (ctx.check(TokenType.SEMICOLON)) ctx.advance();
            body = List.of(new ReturnStmt(ctx.pos(), expr));
        } else {
            ctx.expectSemicolon();
        }
        return new FunctionDeclarationNode(p, mods, returnType, name, params, thrown, typeParams, body, annos);
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
        ctx.error("'" + w + "' é palavra reservada (Kof não tem keyword de "
                + "função); declare como 'Tipo nome(...) { }' ou 'nome(...): Tipo { }'", "PARSE085");
    }

    /**
     * FFI: {@code extern name(params): ReturnType;} (opcionalmente com
     * especificador de biblioteca: {@code extern "libm" sqrt(Double): Double;}).
     * Formaliza a assinatura de uma função externa em compile-time (R3,
     * TIER 2.1). Não há corpo: o binding é do runtime por target.
     */
    static ExternalFunctionNode parseExternDeclaration(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        ctx.expect(TokenType.EXTERN, "Expected 'extern'", "PARSE090");
        String library = null;
        if (ctx.check(TokenType.STRING_LITERAL)) {
            library = ctx.advance().value();
        }
        String name = ctx.expectId("Expected extern function name", "PARSE091");
        TypeParser.parseTypeParameters(ctx);
        ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE092");
        List<FormalParameterNode> params = new ArrayList<>();
        if (!ctx.check(TokenType.RPAREN)) {
            params.add(TypeParser.parseFormalParameter(ctx));
            while (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                params.add(TypeParser.parseFormalParameter(ctx));
            }
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
     * True when the current token is a generic return type: IDENTIFIER '<'
     * type args '>' IDENTIFIER '(' — e.g. "List<Int> ints(".
     */
    static boolean isGenericReturnTypeAhead(ParseContext ctx) {
        if (!ctx.checkNext(TokenType.LESS)) return false;
        int depth = 0;
        for (int i = 1; i + 1 < ctx.tokens.size() - ctx.pos; i++) {
            TokenType t = ctx.tokens.get(ctx.pos + i).type();
            if (t == TokenType.LESS) depth++;
            else if (t == TokenType.GREATER) {
                depth--;
                if (depth == 0) {
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

    static AstNode parseTypeDeclaration(ParseContext ctx, List<AnnotationNode> annos) {
        List<String> mods = parseModifiers(ctx);
        if (ctx.check(TokenType.CLASS)) return parseClassDeclaration(ctx, mods, annos);
        if (ctx.check(TokenType.INTERFACE)) return parseInterfaceDeclaration(ctx, mods, annos);
        if (ctx.check(TokenType.RECORD)) return parseRecordDeclaration(ctx, mods, annos);
        if (ctx.check(TokenType.ENUM)) return parseEnumDeclaration(ctx, mods, annos);
        if (ctx.check(TokenType.ENTITY)) return parseEntityDeclaration(ctx, mods, annos);
        ctx.error("Expected type declaration", "PARSE007");
        ctx.advance();
        return new ClassDeclarationNode(ctx.pos(), "error", List.of(), null, List.of(), List.of(), List.of(), annos);
    }

    static List<String> parseModifiers(ParseContext ctx) {
        List<String> mods = new ArrayList<>();
        while (ctx.check(TokenType.PUBLIC, TokenType.PRIVATE, TokenType.PROTECTED, TokenType.STATIC,
                TokenType.FINAL, TokenType.ABSTRACT, TokenType.TRANSIENT, TokenType.VOLATILE,
                TokenType.SYNCHRONIZED, TokenType.NATIVE, TokenType.DEFAULT, TokenType.OVERRIDE)) {
            mods.add(ctx.advance().value());
        }
        return mods;
    }

    /** enum Name { A, B, C } — constantes apenas (MVP P1). */
    static AstNode parseEnumDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.expect(TokenType.ENUM, "Expected 'enum'", "PARSE030");
        String name = ctx.expectId("Expected enum name", "PARSE031");
        java.util.List<String> constants = new ArrayList<>();
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            while (!ctx.check(TokenType.RBRACE) && !ctx.check(TokenType.EOF)) {
                if (ctx.check(TokenType.IDENTIFIER)) {
                    constants.add(ctx.advance().value());
                } else {
                    ctx.error("Expected enum constant", "PARSE032");
                    ctx.advance();
                }
                if (ctx.check(TokenType.COMMA)) ctx.advance();
            }
            ctx.expect(TokenType.RBRACE, "Expected '}' after enum body", "PARSE033");
        }
        return new EnumDeclarationNode(ctx.pos(), name, mods, constants, annos);
    }

    static AstNode parseClassDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.advance();
        String name = ctx.expectId("Expected class name", "PARSE008");
        ctx.currentClassName = name;
        List<String> typeParams = TypeParser.parseTypeParameters(ctx);
        String superClass = null;
        if (ctx.check(TokenType.EXTENDS)) {
            ctx.advance();
            superClass = TypeParser.parseTypeRef(ctx);
        }
        List<String> ifaces = parseImplementedInterfaces(ctx);

        if (ctx.check(TokenType.LPAREN)) {
            return parseRecordBody(ctx, name, mods, superClass, ifaces, typeParams);
        }
        List<AstNode> members = new ArrayList<>();
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                members.add(ClassMemberParser.parseClassMember(ctx));
            }
            ctx.expect(TokenType.RBRACE, "Expected '}' after class body", "PARSE009");
        }
        return new ClassDeclarationNode(ctx.pos(), name, mods, superClass, ifaces, typeParams,
                List.copyOf(members), annos);
    }

    static InterfaceDeclarationNode parseInterfaceDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.advance();
        String name = ctx.expectId("Expected interface name", "PARSE010");
        List<String> ifaces = new ArrayList<>();
        if (ctx.check(TokenType.EXTENDS)) {
            ctx.advance();
            ifaces.add(TypeParser.parseTypeRef(ctx));
            while (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                ifaces.add(TypeParser.parseTypeRef(ctx));
            }
        }
        List<AstNode> members = new ArrayList<>();
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                members.add(ClassMemberParser.parseClassMember(ctx));
            }
            ctx.expect(TokenType.RBRACE, "Expected '}' after interface body", "PARSE011");
        }
        return new InterfaceDeclarationNode(ctx.pos(), name, mods, ifaces, List.copyOf(members), annos);
    }

    static EntityDeclarationNode parseEntityDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.advance(); // entity
        String name = ctx.expectId("Expected entity name", "PARSE024");
        ctx.entityNames.add(name);
        List<EntityFieldNode> fields = new ArrayList<>();
        ctx.expect(TokenType.LBRACE, "Expected '{' after entity name", "PARSE024");
        while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
            SourcePosition fieldPos = ctx.pos();
            String fieldName = ctx.expectId("Expected field name in entity", "PARSE024");
            ctx.expect(TokenType.COLON, "Expected ':' after field name", "PARSE024");
            String fieldType = TypeParser.parseTypeRef(ctx);
            boolean generated = false;
            boolean unique = false;
            while (ctx.check(TokenType.GENERATED, TokenType.UNIQUE)) {
                if (ctx.check(TokenType.GENERATED)) generated = true;
                if (ctx.check(TokenType.UNIQUE)) unique = true;
                ctx.advance();
            }
            fields.add(new EntityFieldNode(fieldPos, fieldType, fieldName, generated, unique));
        }
        ctx.expect(TokenType.RBRACE, "Expected '}' after entity body", "PARSE024");
        return new EntityDeclarationNode(ctx.pos(), name, mods, fields, annos);
    }

    static RecordDeclarationNode parseRecordDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.advance();
        String name = ctx.expectId("Expected record name", "PARSE012");
        List<String> typeParams = TypeParser.parseTypeParameters(ctx);
        String superClass = null;
        if (ctx.check(TokenType.EXTENDS)) {
            ctx.advance();
            superClass = TypeParser.parseTypeRef(ctx);
        }
        List<String> ifaces = parseImplementedInterfaces(ctx);
        RecordDeclarationNode rec = parseRecordBody(ctx, name, mods, superClass, ifaces, typeParams);
        return new RecordDeclarationNode(rec.position(), rec.name(), rec.modifiers(), rec.superClass(),
                rec.interfaces(), typeParams, rec.components(), rec.members(), annos);
    }

    static RecordDeclarationNode parseRecordBody(ParseContext ctx, String name, List<String> mods, String superClass,
                                                  List<String> ifaces, List<String> typeParams) {
        List<RecordComponentNode> components = new ArrayList<>();
        if (ctx.check(TokenType.LPAREN)) {
            ctx.advance();
            if (!ctx.check(TokenType.RPAREN)) {
                components.add(parseRecordComponent(ctx));
                while (ctx.check(TokenType.COMMA)) {
                    ctx.advance();
                    components.add(parseRecordComponent(ctx));
                }
            }
            ctx.expect(TokenType.RPAREN, "Expected ')' after record components", "PARSE013");
        }
        List<AstNode> members = new ArrayList<>();
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                members.add(ClassMemberParser.parseClassMember(ctx));
            }
            ctx.expect(TokenType.RBRACE, "Expected '}' after record body", "PARSE014");
        }
        return new RecordDeclarationNode(ctx.pos(), name, mods, superClass, ifaces,
                typeParams, List.copyOf(components), List.copyOf(members), List.of());
    }

    static List<String> parseImplementedInterfaces(ParseContext ctx) {
        List<String> ifaces = new ArrayList<>();
        if (ctx.check(TokenType.IMPLEMENTS)) {
            ctx.advance();
            ifaces.add(TypeParser.parseTypeRef(ctx));
            while (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                ifaces.add(TypeParser.parseTypeRef(ctx));
            }
        }
        return ifaces;
    }

    static RecordComponentNode parseRecordComponent(ParseContext ctx) {
        List<AnnotationNode> annos = AnnotationParser.parseAnnotations(ctx);
        List<String> mods = parseModifiers(ctx);
        String type = TypeParser.parseTypeRef(ctx);
        String name = ctx.expectId("Expected component name", "PARSE015");
        ExpressionNode init = null;
        if (ctx.check(TokenType.EQUAL)) {
            ctx.advance();
            init = ExpressionParser.parseExpression(ctx);
        }
        return new RecordComponentNode(ctx.pos(), mods, type, name, init, annos);
    }

}
