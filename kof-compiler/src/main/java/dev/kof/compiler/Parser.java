package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class Parser {

    private static final Set<String> PRIMITIVE_TYPE_NAMES = Set.of(
            "bool", "byte", "short", "int", "long", "float", "double", "char", "string", "void"
    );

    private final List<Token> tokens;
    private final DiagnosticCollector diagnostics;
    private final String file;
    private int pos;
    private String currentClassName;

    /** Nomes de entidades declaradas — para o Query DSL {@code Entity.query(db) { }}. */
    private final java.util.Set<String> entityNames = new java.util.HashSet<>();

    Parser(List<Token> tokens, DiagnosticCollector diagnostics, String file) {
        this.tokens = tokens;
        this.diagnostics = diagnostics;
        this.file = file;
    }

    CompilationUnitNode parse() {
        SourcePosition pos0 = pos();
        String packageName = parsePackage();
        List<String> imports = parseImports();
        List<AstNode> declarations = new ArrayList<>();
        while (!atEnd()) {
            List<AnnotationNode> annos = parseAnnotations();
            if (check(TokenType.IDENTIFIER) && "test".equals(peek().value()) && checkNext(TokenType.STRING_LITERAL)) {
                declarations.add(parseTestDeclaration());
            } else if (check(TokenType.IDENTIFIER) && "application".equals(peek().value())
                    && checkNext(TokenType.LBRACE)) {
                declarations.add(parseApplicationDeclaration());
            } else if (!annos.isEmpty()
                    && (check(TokenType.CLASS, TokenType.INTERFACE, TokenType.RECORD, TokenType.ENTITY))) {
                declarations.add(parseTypeDeclaration(annos));
            } else if (check(TokenType.EXTERN)) {
                declarations.add(parseExternDeclaration());
            } else if (check(TokenType.IDENTIFIER) || check(TokenType.VOID) || isPrimitiveType()) {
                declarations.add(parseFunctionDeclaration(List.of(), annos));
            } else {
                declarations.add(parseTypeDeclaration(annos));
            }
        }
        return new CompilationUnitNode(pos0, packageName, imports, List.copyOf(declarations));
    }

    /**
     * @Name, @pkg.Name e @Name(valor | key = valor, ...) — valores são
     * constantes em compile-time (literais ou arrays {a, b}). Annotations
     * são metadados de interop: parseadas aqui, preservadas na IR e
     * emitidas no bytecode pelo backend; nunca substituem APIs idiomáticas.
     */
    private List<AnnotationNode> parseAnnotations() {
        List<AnnotationNode> out = new ArrayList<>();
        while (check(TokenType.AT)) {
            SourcePosition p = pos();
            advance();
            StringBuilder name = new StringBuilder(expectId("Expected annotation name", "PARSE080"));
            while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
                advance();
                name.append('.').append(advance().value());
            }
            List<AnnotationPair> pairs = new ArrayList<>();
            if (check(TokenType.LPAREN)) {
                advance();
                if (!check(TokenType.RPAREN)) {
                    do {
                        String key = null;
                        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.EQUAL)) {
                            key = advance().value();
                            advance();
                        }
                        Object value = parseAnnotationValue();
                        if (value instanceof ParseError) continue;
                        pairs.add(new AnnotationPair(key, value));
                    } while (check(TokenType.COMMA) && !advance().is(TokenType.EOF));
                }
                expect(TokenType.RPAREN, "Expected ')' after annotation arguments", "PARSE081");
            }
            out.add(new AnnotationNode(p, name.toString(), List.copyOf(pairs)));
        }
        return out;
    }

    /** Marcador interno: valor de annotation inválido (já diagnosticado). */
    private static final class ParseError {
        static final ParseError INSTANCE = new ParseError();
    }

    private boolean isParseError(Object v) {
        return v == ParseError.INSTANCE;
    }

    private boolean isNumericKind(Class<?> k) {
        return k == Integer.class || k == Long.class || k == Float.class || k == Double.class;
    }

    /**
     * Valor constante de annotation: literal, array {v1, v2} de literais
     * ou identificador simples. Identificadores não-constantes produzem
     * ANNOT001 (nunca um valor silenciosamente errado).
     */
    private Object parseAnnotationValue() {
        if (check(TokenType.LBRACE)) {
            advance();
            List<Object> items = new ArrayList<>();
            Class<?> elementKind = null;
            boolean mixed = false;
            if (!check(TokenType.RBRACE)) {
                do {
                    Object v = parseAnnotationValue();
                    if (!isParseError(v)) {
                        // JVM exige tipo único no array: {1, "a"} é bytecode
                        // inválido — diagnosticado aqui, não emitido
                        Class<?> kind = v != null ? v.getClass() : null;
                        if (kind == Integer.class && elementKind == null) kind = Integer.class;
                        if (elementKind == null) {
                            elementKind = kind;
                        } else if (kind != null && !kind.equals(elementKind)
                                && !(isNumericKind(kind) && isNumericKind(elementKind))) {
                            mixed = true;
                        }
                        items.add(v);
                    }
                } while (check(TokenType.COMMA) && !advance().is(TokenType.EOF));
            }
            expect(TokenType.RBRACE, "Expected '}' after annotation array", "PARSE082");
            if (mixed) {
                error("Annotation array values must have the same type", "ANNOT002");
                return ParseError.INSTANCE;
            }
            return items;
        }
        if (check(TokenType.STRING_LITERAL) || check(TokenType.INT_LITERAL) || check(TokenType.LONG_LITERAL)
                || check(TokenType.FLOAT_LITERAL) || check(TokenType.DOUBLE_LITERAL)
                || check(TokenType.BOOLEAN_LITERAL) || check(TokenType.CHAR_LITERAL)
                || check(TokenType.NULL_LITERAL)) {
            Token t = advance();
            try {
                return switch (t.type()) {
                    case STRING_LITERAL -> t.value();
                    case INT_LITERAL -> Integer.parseInt(t.value());
                    case LONG_LITERAL -> Long.parseLong(t.value().replaceAll("[lL]$", ""));
                    case FLOAT_LITERAL -> Float.parseFloat(t.value().replaceAll("[fF]$", ""));
                    case DOUBLE_LITERAL -> Double.parseDouble(t.value().replaceAll("[dD]$", ""));
                    case BOOLEAN_LITERAL -> Boolean.parseBoolean(t.value());
                    case CHAR_LITERAL -> t.value().charAt(0);
                    default -> null;
                };
            } catch (NumberFormatException e) {
                error("Invalid numeric literal in annotation", "PARSE083");
                return ParseError.INSTANCE;
            }
        }
        // negativos
        if (check(TokenType.MINUS) && (checkNext(TokenType.INT_LITERAL) || checkNext(TokenType.LONG_LITERAL)
                || checkNext(TokenType.FLOAT_LITERAL) || checkNext(TokenType.DOUBLE_LITERAL))) {
            advance();
            Token t = advance();
            try {
                return switch (t.type()) {
                    case INT_LITERAL -> Integer.parseInt("-" + t.value());
                    case LONG_LITERAL -> Long.parseLong("-" + t.value().replaceAll("[lL]$", ""));
                    case FLOAT_LITERAL -> Float.parseFloat("-" + t.value().replaceAll("[fF]$", ""));
                    default -> Double.parseDouble("-" + t.value().replaceAll("[dD]$", ""));
                };
            } catch (NumberFormatException e) {
                error("Invalid numeric literal in annotation", "PARSE083");
                return ParseError.INSTANCE;
            }
        }
        if (check(TokenType.IDENTIFIER)) {
            // Nome qualificado: Classe.class (valor Class) ou Enum.CONST
            StringBuilder name = new StringBuilder(advance().value());
            while (check(TokenType.DOT) && (checkNext(TokenType.IDENTIFIER) || isTypeKeywordAtNext())) {
                advance();
                name.append('.').append(advance().value());
            }
            if (check(TokenType.DOT) && peek().value().equals("class")) {
                advance();
                advance();
                return new AnnotationClassRef(name.toString());
            }
            if (name.indexOf(".") >= 0) {
                return new AnnotationEnumRef(name.toString());
            }
            error("Annotation values must be compile-time constants ('" + name
                    + "' is not supported yet)", "ANNOT001");
            return ParseError.INSTANCE;
        }
        error("Expected annotation value", "PARSE084");
        advance();
        return ParseError.INSTANCE;
    }

    /**
     * `test "nome" { ... }` — corpo analisado como bloco de statements;
     * o lowering transforma cada teste numa função void sem argumentos.
     */
    private TestDeclarationNode parseTestDeclaration() {
        SourcePosition p = pos();
        advance(); // consome 'test'
        Token nameToken = expect(TokenType.STRING_LITERAL, "Expected test name string", "PARSE010");
        List<StatementNode> body = parseBlock();
        return new TestDeclarationNode(p, nameToken.value(), body);
    }

    /**
     * `application { onStart { ... } onShutdown { ... } }` — bloco de
     * lifecycle. Cada bloco nomeado (onStart/onShutdown) é parseado como
     * bloco de statements; o lowering sintetiza funções chamadas no
     * prólogo/epílogo do main.
     */
    private ApplicationDeclarationNode parseApplicationDeclaration() {
        SourcePosition p = pos();
        advance(); // consome 'application'
        List<StatementNode> onStart = List.of();
        List<StatementNode> onShutdown = List.of();
        expect(TokenType.LBRACE, "Expected '{' after application", "PARSE051");
        while (!check(TokenType.RBRACE) && !atEnd()) {
            if (check(TokenType.IDENTIFIER)) {
                String blockName = peek().value();
                if ("onStart".equals(blockName) || "onShutdown".equals(blockName)) {
                    advance();
                    if (check(TokenType.LBRACE)) {
                        List<StatementNode> body = parseBlock();
                        if ("onStart".equals(blockName)) onStart = body;
                        else onShutdown = body;
                    } else {
                        expect(TokenType.LBRACE, "Expected '{' after " + blockName, "PARSE051");
                    }
                } else {
                    expect(TokenType.RBRACE, "Expected onStart/onShutdown block in application", "PARSE051");
                }
            } else {
                expect(TokenType.RBRACE, "Expected onStart/onShutdown block in application", "PARSE051");
            }
        }
        expect(TokenType.RBRACE, "Expected '}' after application block", "PARSE051");
        return new ApplicationDeclarationNode(p, onStart, onShutdown);
    }

    private FunctionDeclarationNode parseFunctionDeclaration(List<String> mods) {
        return parseFunctionDeclaration(mods, List.of());
    }

    /**
     * FFI: {@code extern name(params): ReturnType;} — formaliza a assinatura de
     * uma função externa em compile-time (R3, TIER 2.1.1/2.1.2). Não há corpo:
     * o binding é responsabilidade do runtime por target (nunca gerado aqui).
     */
    private ExternalFunctionNode parseExternDeclaration() {
        SourcePosition p = pos();
        expect(TokenType.EXTERN, "Expected 'extern'", "PARSE090");
        String library = null;
        if (check(TokenType.STRING_LITERAL)) {
            library = advance().value();
        }
        String name = expectId("Expected extern function name", "PARSE091");
        if (check(TokenType.LESS)) parseTypeParameters();
        expect(TokenType.LPAREN, "Expected '('", "PARSE092");
        List<FormalParameterNode> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(parseFormalParameter());
            while (check(TokenType.COMMA)) { advance(); params.add(parseFormalParameter()); }
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE093");
        String returnType = "void";
        if (check(TokenType.COLON)) {
            advance();
            returnType = parseTypeRef();
        }
        expectSemicolon();
        return new ExternalFunctionNode(p, library, returnType, name, params);
    }

    private FunctionDeclarationNode parseFunctionDeclaration(List<String> mods, List<AnnotationNode> annos) {
        SourcePosition p = pos();
        // "fn" é o keyword de declaração da linguagem (fn main()); sem isso o
        // parser tratava "fn" como tipo de retorno e o JvmBackend gerava o
        // método main([String;)Lfn; → a JVM rejeita (JavaFX launcher error).
        if (check(TokenType.IDENTIFIER) && "fn".equals(peek().value())) {
            advance();
        }
        String returnType = "void";
        String name;
        if ((check(TokenType.IDENTIFIER) || check(TokenType.VOID) || isPrimitiveType())
                && !checkNext(TokenType.LPAREN)
                && (isGenericReturnTypeAhead() || !checkNext(TokenType.LESS))) {
            returnType = advance().value();
            if (check(TokenType.LESS)) {
                returnType = returnType + consumeGenericTypeArgs();
            }
            // suffixo de tipo no retorno: `String? f()`, `Int[] f()`
            while (check(TokenType.QUESTION)
                    || (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET))) {
                if (check(TokenType.QUESTION)) {
                    advance();
                    returnType += "?";
                } else {
                    advance();
                    advance();
                    returnType += "[]";
                }
            }
            name = expectId("Expected function name", "PARSE010");
        } else {
            name = expectId("Expected function name", "PARSE010");
        }
        List<String> typeParams = parseTypeParameters();
        expect(TokenType.LPAREN, "Expected '('", "PARSE011");
        List<FormalParameterNode> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(parseFormalParameter());
            while (check(TokenType.COMMA)) { advance(); params.add(parseFormalParameter()); }
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE012");
        if (check(TokenType.COLON)) {
            advance();
            returnType = parseTypeRef();
        }
        List<String> thrown = new ArrayList<>();
        List<StatementNode> body = List.of();
        if (check(TokenType.LBRACE)) {
            body = parseBlock();
        } else if (check(TokenType.EQUAL)) {
            advance();
            ExpressionNode expr = parseExpression();
            if (check(TokenType.SEMICOLON)) advance();
            body = List.of(new ReturnStmt(pos(), expr));
        } else {
            expectSemicolon();
        }
        return new FunctionDeclarationNode(p, mods, returnType, name, params, thrown, typeParams, body, annos);
    }

    /**
     * True when the current token is a generic return type: IDENTIFIER '<'
     * type args '>' IDENTIFIER '(' — e.g. "List<Int> ints(".
     */
    private boolean isGenericReturnTypeAhead() {
        if (!checkNext(TokenType.LESS)) return false;
        int depth = 0;
        for (int i = 1; i + 1 < tokens.size() - pos; i++) {
            TokenType t = tokens.get(pos + i).type();
            if (t == TokenType.LESS) depth++;
            else if (t == TokenType.GREATER) {
                depth--;
                if (depth == 0) {
                    return tokens.get(pos + i + 1).type() == TokenType.IDENTIFIER
                            && tokens.get(pos + i + 2).type() == TokenType.LPAREN;
                }
            } else if (t == TokenType.EOF) {
                return false;
            }
        }
        return false;
    }

    /**
     * Query DSL tipada (ORM001): {@code Entity.query(db) { where age > 18;
     * orderBy name desc; limit 10 }}. Consome o bloco {@code { ... }} e
     * devolve um {@link QueryDslExpr}; o lowering (CompilerDriver) gera
     * {@code db.query<Entity>(db, "SELECT ...", binds...)}.
     */
    private ExpressionNode parseQueryDsl(SourcePosition pos, String entity, ExpressionNode dbArg) {
        expect(TokenType.LBRACE, "Expected '{'", "PARSE024");
        List<ExpressionNode> wheres = new ArrayList<>();
        List<ExpressionNode> orderFields = new ArrayList<>();
        List<String> orderDirs = new ArrayList<>();
        ExpressionNode limit = null;
        while (!check(TokenType.RBRACE) && !atEnd()) {
            if (check(TokenType.IDENTIFIER) && "where".equals(peek().value())) {
                advance();
                wheres.add(parseExpression());
                expectSemicolon();
            } else if (check(TokenType.IDENTIFIER) && "orderBy".equals(peek().value())) {
                advance();
                orderFields.add(parseIdentifierOrLiteral());
                String dir = "asc";
                if (check(TokenType.IDENTIFIER) && ("desc".equals(peek().value())
                        || "asc".equals(peek().value()))) {
                    dir = advance().value();
                }
                orderDirs.add(dir);
                expectSemicolon();
            } else if (check(TokenType.IDENTIFIER) && "limit".equals(peek().value())) {
                advance();
                limit = parseExpression();
                expectSemicolon();
            } else {
                error("Expected 'where', 'orderBy' or 'limit' in query block", "PARSE090");
                advance();
            }
        }
        expect(TokenType.RBRACE, "Expected '}' after query block", "PARSE025");
        return new QueryDslExpr(pos, entity, dbArg, wheres, orderFields, orderDirs, limit);
    }

    /** Identificador ou literal (para o campo do `orderBy`). */
    private ExpressionNode parseIdentifierOrLiteral() {
        if (check(TokenType.IDENTIFIER)) {
            return new IdentifierExpr(pos(), advance().value());
        }
        return parsePrimary();
    }

    /**
     * Consumes the generic type arguments starting at the current LESS token
     * and returns their source text, e.g. "<Int, String>".
     */
    private String consumeGenericTypeArgs() {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (!atEnd()) {
            Token t = advance();
            sb.append(t.value());
            if (t.type() == TokenType.LESS) depth++;
            else if (t.type() == TokenType.GREATER) {
                depth--;
                if (depth == 0) break;
            }
        }
        return sb.toString();
    }

    private List<String> parseTypeParameters() {
        List<String> typeParams = new ArrayList<>();
        if (check(TokenType.LESS)) {
            advance();
            while (!check(TokenType.GREATER) && !atEnd()) {
                if (check(TokenType.IDENTIFIER)) {
                    typeParams.add(advance().value());
                } else {
                    advance();
                }
                if (check(TokenType.COMMA)) advance();
            }
            expect(TokenType.GREATER, "Expected '>' after type parameters", "PARSE075");
        }
        return typeParams;
    }

    private String parsePackage() {
        if (!check(TokenType.PACKAGE)) return "";
        advance();
        StringBuilder name = new StringBuilder();
        if (!check(TokenType.IDENTIFIER)) {
            error("Expected package name", "PARSE001");
            return "";
        }
        name.append(advance().value());
        while (check(TokenType.DOT)) {
            advance();
            if (check(TokenType.IDENTIFIER)) {
                name.append('.').append(advance().value());
            } else {
                error("Expected package name component", "PARSE002");
                break;
            }
        }
        expectSemicolon();
        return name.toString();
    }

    private List<String> parseImports() {
        List<String> result = new ArrayList<>();
        while (check(TokenType.IMPORT)) {
            advance();
            if (check(TokenType.STAR)) {
                advance();
                result.add("*");
            } else {
                StringBuilder path = new StringBuilder();
                if (!check(TokenType.IDENTIFIER)) {
                    error("Expected import name", "PARSE004");
                    break;
                }
                path.append(advance().value());
                while (check(TokenType.DOT)) {
                    advance();
                    if (check(TokenType.STAR)) {
                        advance();
                        path.append(".*");
                        break;
                    } else if (check(TokenType.IDENTIFIER)) {
                        path.append('.').append(advance().value());
                    } else {
                        error("Expected import path component", "PARSE005");
                        break;
                    }
                }
                result.add(path.toString());
            }
            expectSemicolon();
        }
        return result;
    }

    private AstNode parseTypeDeclaration() {
        return parseTypeDeclaration(List.of());
    }

    private AstNode parseTypeDeclaration(List<AnnotationNode> annos) {
        List<String> mods = parseModifiers();
        if (check(TokenType.CLASS)) return parseClassDeclaration(mods, annos);
        if (check(TokenType.INTERFACE)) return parseInterfaceDeclaration(mods, annos);
        if (check(TokenType.RECORD)) return parseRecordDeclaration(mods, annos);
        if (check(TokenType.ENUM)) return parseEnumDeclaration(mods, annos);
        if (check(TokenType.ENTITY)) return parseEntityDeclaration(mods, annos);
        error("Expected type declaration", "PARSE007");
        advance();
        return new ClassDeclarationNode(pos(), "error", List.of(), null, List.of(), List.of(), List.of(), annos);
    }

    private List<String> parseModifiers() {
        List<String> mods = new ArrayList<>();
        while (check(TokenType.PUBLIC, TokenType.PRIVATE, TokenType.PROTECTED, TokenType.STATIC,
                TokenType.FINAL, TokenType.ABSTRACT, TokenType.TRANSIENT, TokenType.VOLATILE,
                TokenType.SYNCHRONIZED, TokenType.NATIVE, TokenType.DEFAULT, TokenType.OVERRIDE)) {
            mods.add(advance().value());
        }
        return mods;
    }

    /** enum Name { A, B, C } — constantes apenas (MVP P1). */
    private AstNode parseEnumDeclaration(List<String> mods, List<AnnotationNode> annos) {
        expect(TokenType.ENUM, "Expected 'enum'", "PARSE030");
        String name = expectId("Expected enum name", "PARSE031");
        java.util.List<String> constants = new ArrayList<>();
        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                if (check(TokenType.IDENTIFIER)) {
                    constants.add(advance().value());
                } else {
                    error("Expected enum constant", "PARSE032");
                    advance();
                }
                if (check(TokenType.COMMA)) advance();
            }
            expect(TokenType.RBRACE, "Expected '}' after enum body", "PARSE033");
        }
        return new EnumDeclarationNode(pos(), name, mods, constants, annos);
    }

    private AstNode parseClassDeclaration(List<String> mods) {
        return parseClassDeclaration(mods, List.of());
    }

    private AstNode parseClassDeclaration(List<String> mods, List<AnnotationNode> annos) {
        advance();
        String name = expectId("Expected class name", "PARSE008");
        currentClassName = name;
        List<String> typeParams = parseTypeParameters();
        String superClass = null;
        if (check(TokenType.EXTENDS)) {
            advance();
            superClass = parseTypeRef();
        }
        List<String> ifaces = parseImplementedInterfaces();

        if (check(TokenType.LPAREN)) {
            return parseRecordBody(name, mods, superClass, ifaces, typeParams);
        }
        List<AstNode> members = new ArrayList<>();
        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE) && !atEnd()) {
                members.add(parseClassMember());
            }
            expect(TokenType.RBRACE, "Expected '}' after class body", "PARSE009");
        }
        return new ClassDeclarationNode(pos(), name, mods, superClass, ifaces, typeParams,
                List.copyOf(members), annos);
    }

    private InterfaceDeclarationNode parseInterfaceDeclaration(List<String> mods) {
        return parseInterfaceDeclaration(mods, List.of());
    }

    private InterfaceDeclarationNode parseInterfaceDeclaration(List<String> mods, List<AnnotationNode> annos) {
        advance();
        String name = expectId("Expected interface name", "PARSE010");
        List<String> ifaces = new ArrayList<>();
        if (check(TokenType.EXTENDS)) {
            advance();
            ifaces.add(parseTypeRef());
            while (check(TokenType.COMMA)) {
                advance();
                ifaces.add(parseTypeRef());
            }
        }
        List<AstNode> members = new ArrayList<>();
        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE) && !atEnd()) {
                members.add(parseClassMember());
            }
            expect(TokenType.RBRACE, "Expected '}' after interface body", "PARSE011");
        }
        return new InterfaceDeclarationNode(pos(), name, mods, ifaces, List.copyOf(members), annos);
    }

    /**
     * entity Name {
     *     id: Long generated
     *     name: String
     *     email: String unique
     * }
     */
    private EntityDeclarationNode parseEntityDeclaration(List<String> mods) {
        return parseEntityDeclaration(mods, List.of());
    }

    private EntityDeclarationNode parseEntityDeclaration(List<String> mods, List<AnnotationNode> annos) {
        advance(); // entity
        String name = expectId("Expected entity name", "PARSE024");
        entityNames.add(name);
        List<EntityFieldNode> fields = new ArrayList<>();
        expect(TokenType.LBRACE, "Expected '{' after entity name", "PARSE024");
        while (!check(TokenType.RBRACE) && !atEnd()) {
            SourcePosition fieldPos = pos();
            String fieldName = expectId("Expected field name in entity", "PARSE024");
            expect(TokenType.COLON, "Expected ':' after field name", "PARSE024");
            String fieldType = parseTypeRef();
            boolean generated = false;
            boolean unique = false;
            while (check(TokenType.GENERATED, TokenType.UNIQUE)) {
                if (check(TokenType.GENERATED)) generated = true;
                if (check(TokenType.UNIQUE)) unique = true;
                advance();
            }
            fields.add(new EntityFieldNode(fieldPos, fieldType, fieldName, generated, unique));
        }
        expect(TokenType.RBRACE, "Expected '}' after entity body", "PARSE024");
        return new EntityDeclarationNode(pos(), name, mods, fields, annos);
    }

    private RecordDeclarationNode parseRecordDeclaration(List<String> mods) {
        return parseRecordDeclaration(mods, List.of());
    }

    private RecordDeclarationNode parseRecordDeclaration(List<String> mods, List<AnnotationNode> annos) {
        advance();
        String name = expectId("Expected record name", "PARSE012");
        List<String> typeParams = parseTypeParameters();
        String superClass = null;
        if (check(TokenType.EXTENDS)) {
            advance();
            superClass = parseTypeRef();
        }
        List<String> ifaces = parseImplementedInterfaces();
        RecordDeclarationNode rec = parseRecordBody(name, mods, superClass, ifaces, typeParams);
        return new RecordDeclarationNode(rec.position(), rec.name(), rec.modifiers(), rec.superClass(),
                rec.interfaces(), typeParams, rec.components(), rec.members(), annos);
    }

    private RecordDeclarationNode parseRecordBody(String name, List<String> mods, String superClass,
                                                  List<String> ifaces, List<String> typeParams) {
        List<RecordComponentNode> components = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            advance();
            if (!check(TokenType.RPAREN)) {
                components.add(parseRecordComponent());
                while (check(TokenType.COMMA)) {
                    advance();
                    components.add(parseRecordComponent());
                }
            }
            expect(TokenType.RPAREN, "Expected ')' after record components", "PARSE013");
        }
        List<AstNode> members = new ArrayList<>();
        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE) && !atEnd()) {
                members.add(parseClassMember());
            }
            expect(TokenType.RBRACE, "Expected '}' after record body", "PARSE014");
        }
        return new RecordDeclarationNode(pos(), name, mods, superClass, ifaces,
                typeParams, List.copyOf(components), List.copyOf(members), List.of());
    }

    private List<String> parseImplementedInterfaces() {
        List<String> ifaces = new ArrayList<>();
        if (check(TokenType.IMPLEMENTS)) {
            advance();
            ifaces.add(parseTypeRef());
            while (check(TokenType.COMMA)) {
                advance();
                ifaces.add(parseTypeRef());
            }
        }
        return ifaces;
    }

    private RecordComponentNode parseRecordComponent() {
        List<AnnotationNode> annos = parseAnnotations();
        List<String> mods = parseModifiers();
        String type = parseTypeRef();
        String name = expectId("Expected component name", "PARSE015");
        ExpressionNode init = null;
        if (check(TokenType.EQUAL)) {
            advance();
            init = parseExpression();
        }
        return new RecordComponentNode(pos(), mods, type, name, init, annos);
    }

    private AstNode parseClassMember() {
        List<AnnotationNode> annos = parseAnnotations();
        List<String> mods = parseModifiers();
        if (check(TokenType.IDENTIFIER) && peek().value().equals("constructor") && checkNext(TokenType.LPAREN)) {
            ConstructorDeclarationNode ctor = parseConstructor(mods);
            return new ConstructorDeclarationNode(ctor.position(), ctor.modifiers(), ctor.name(),
                    ctor.parameters(), ctor.thrownExceptions(), ctor.body(), annos);
        }
        if ((check(TokenType.IDENTIFIER) || check(TokenType.AWAIT) || check(TokenType.SPAWN)) && checkNext(TokenType.LPAREN)) {
            String name = advance().value();
            expect(TokenType.LPAREN, "Expected '('", "PARSE011");
            List<FormalParameterNode> params = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                params.add(parseFormalParameter());
                while (check(TokenType.COMMA)) { advance(); params.add(parseFormalParameter()); }
            }
            expect(TokenType.RPAREN, "Expected ')'", "PARSE012");
            String returnType = "void";
            if (check(TokenType.COLON)) {
                advance();
                returnType = parseTypeRef();
            }
            List<String> thrown = parseThrows();
            return finishMethod(mods, annos, name, params, returnType, thrown);
        }
        if (check(TokenType.IDENTIFIER, TokenType.BOOL_TYPE, TokenType.BYTE_TYPE, TokenType.SHORT_TYPE,
                TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE, TokenType.DOUBLE_TYPE,
                TokenType.CHAR_TYPE, TokenType.STRING_TYPE, TokenType.VOID)) {
            // Campo ou método com tipo de retorno explícito: `Type name(...)`.
            // parseTypeRef cobre retornos genéricos (`Set<Int>`, `List<String>`,
            // `Map<K,V>`) e nullable (`String?`) — o lookahead antigo de 2 tokens
            // quebrava porque assumia retorno de um token só.
            String type = parseTypeRef();
            String name = expectId("Expected member name", "PARSE018");
            if (check(TokenType.LPAREN)) {
                advance();
                List<FormalParameterNode> params = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    params.add(parseFormalParameter());
                    while (check(TokenType.COMMA)) { advance(); params.add(parseFormalParameter()); }
                }
                expect(TokenType.RPAREN, "Expected ')' after parameters", "PARSE019");
                String returnType = type;
                if (check(TokenType.COLON)) {
                    advance();
                    returnType = parseTypeRef();
                }
                List<String> thrown = parseThrows();
                return finishMethod(mods, annos, name, params, returnType, thrown);
            }
            FieldDeclarationNode f = (FieldDeclarationNode) parseField(mods, type, name);
            return new FieldDeclarationNode(f.position(), f.modifiers(), f.type(), f.name(),
                    f.initializer(), annos);
        }
        if (check(TokenType.CLASS, TokenType.INTERFACE, TokenType.RECORD, TokenType.ENTITY)) {
            return parseTypeDeclaration(annos);
        }
        if (check(TokenType.LBRACE)) {
            ConstructorDeclarationNode ctor = parseConstructor(mods);
            return new ConstructorDeclarationNode(ctor.position(), ctor.modifiers(), ctor.name(),
                    ctor.parameters(), ctor.thrownExceptions(), ctor.body(), annos);
        }
        error("Unexpected token in class body", "PARSE016");
        advance();
        return new FieldDeclarationNode(pos(), mods, "Object", "error", null, annos);
    }

    /** Corpo de método nas três formas: bloco, `= expr` e declaração vazia. */
    private AstNode finishMethod(List<String> mods, List<AnnotationNode> annos, String name,
                                 List<FormalParameterNode> params, String returnType, List<String> thrown) {
        if (check(TokenType.LBRACE)) {
            List<StatementNode> body = parseBlock();
            return new MethodDeclarationNode(pos(), mods, returnType, name, params, thrown, body, annos);
        }
        if (check(TokenType.EQUAL)) {
            advance();
            ExpressionNode expr = parseExpression();
            if (check(TokenType.SEMICOLON)) advance();
            return new MethodDeclarationNode(pos(), mods, returnType, name, params, thrown,
                    List.of(new ReturnStmt(pos(), expr)), annos);
        }
        expectSemicolon();
        return new MethodDeclarationNode(pos(), mods, returnType, name, params, thrown, List.of(), annos);
    }

    private FieldDeclarationNode parseField(List<String> mods, String type, String name) {
        ExpressionNode init = null;
        if (check(TokenType.EQUAL)) {
            advance();
            init = parseExpression();
        }
        expectSemicolon();
        return new FieldDeclarationNode(pos(), mods, type, name, init);
    }

    private ConstructorDeclarationNode parseConstructor(List<String> mods) {
        String name;
        if (check(TokenType.IDENTIFIER) && peek().value().equals("constructor")) {
            advance();
            name = currentClassName != null ? currentClassName : "error";
        } else if (check(TokenType.IDENTIFIER)) {
            name = advance().value();
        } else {
            name = "error";
            error("Expected constructor name", "PARSE020");
        }
        List<FormalParameterNode> params = parseFormalParameters();
        List<String> thrown = parseThrows();
        List<StatementNode> body = List.of();
        if (check(TokenType.LBRACE)) {
            body = parseBlock();
        }
        return new ConstructorDeclarationNode(pos(), mods, name, params, thrown, body);
    }

    private List<FormalParameterNode> parseFormalParameters() {
        expect(TokenType.LPAREN, "Expected '('", "PARSE021");
        List<FormalParameterNode> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(parseFormalParameter());
            while (check(TokenType.COMMA)) {
                advance();
                params.add(parseFormalParameter());
            }
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE022");
        return params;
    }

    private FormalParameterNode parseFormalParameter() {
        List<AnnotationNode> annos = parseAnnotations();
        List<String> mods = parseModifiers();
        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.COLON)) {
            // name: Type — annotation form (idiomatic for main(args: List<String>))
            String name = advance().value();
            advance();
            String type = parseTypeRef();
            ExpressionNode defaultValue = null;
            if (check(TokenType.EQUAL)) {
                advance();
                defaultValue = parseExpression();
            }
            return new FormalParameterNode(pos(), mods, type, name, defaultValue, annos);
        }
        String type = parseTypeRef();
        String name = expectId("Expected parameter name", "PARSE023");
        ExpressionNode defaultValue = null;
        if (check(TokenType.EQUAL)) {
            advance();
            defaultValue = parseExpression();
        }
        return new FormalParameterNode(pos(), mods, type, name, defaultValue, annos);
    }

    private List<String> parseThrows() {
        List<String> thrown = new ArrayList<>();
        if (check(TokenType.THROW)) {
            advance();
            thrown.add(parseTypeRef());
            while (check(TokenType.COMMA)) {
                advance();
                thrown.add(parseTypeRef());
            }
        }
        return thrown;
    }

    private List<StatementNode> parseBlock() {
        expect(TokenType.LBRACE, "Expected '{'", "PARSE024");
        List<StatementNode> stmts = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !atEnd()) {
            stmts.add(parseStatement());
        }
        expect(TokenType.RBRACE, "Expected '}'", "PARSE025");
        return stmts;
    }

    private StatementNode parseStatement() {
        if (check(TokenType.LBRACE)) {
            return new BlockStmt(pos(), parseBlock());
        }
        if (check(TokenType.RETURN)) {
            return parseReturn();
        }
        if (check(TokenType.IF)) {
            return parseIfStatement();
        }
        if (check(TokenType.WHILE)) {
            return parseWhileStatement();
        }
        if (check(TokenType.DO)) {
            return parseDoWhileStatement();
        }
        if (check(TokenType.FOR)) {
            return parseForStatement();
        }
        if (check(TokenType.THROW)) {
            return parseThrowStatement();
        }
        if (check(TokenType.SPAWN)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode expr = parseExpression();
            expectSemicolon();
            return new SpawnStmt(p, expr);
        }
        if (check(TokenType.ASSERT)) {
            SourcePosition p = pos();
            advance();
            expect(TokenType.LPAREN, "Expected '('", "PARSE011");
            ExpressionNode condition = parseExpression();
            String message = null;
            if (check(TokenType.COMMA)) {
                advance();
                ExpressionNode msgExpr = parseExpression();
                if (msgExpr instanceof LiteralExpr lit && lit.kind() == ConcreteLiteralKind.STRING) {
                    message = lit.value();
                }
            }
            expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
            expectSemicolon();
            return new AssertStmt(p, condition, message);
        }
        if (check(TokenType.TRY)) {
            return parseTryStatement();
        }
        if (check(TokenType.SWITCH)) {
            return parseSwitchStatement();
        }
        if (check(TokenType.BREAK)) {
            SourcePosition p = pos();
            advance();
            expectSemicolon();
            return new BreakStmt(p);
        }
        if (check(TokenType.CONTINUE)) {
            SourcePosition p = pos();
            advance();
            expectSemicolon();
            return new ContinueStmt(p);
        }
        if (check(TokenType.VAR, TokenType.VAL)) {
            return parseVarDecl();
        }
        if ((check(TokenType.IDENTIFIER) || check(TokenType.VOID) || isPrimitiveType())
                && lookaheadTypedVarDecl()) {
            // `Type name = ...` incluindo nullable (`String? s`) e arrays
            // (`Int[] arr`) — a detecção antiga via checkNext(IDENTIFIER)
            // quebrava em `?`/`[`/`<` depois do tipo.
            return parseVarDecl();
        }
        if (check(TokenType.SEMICOLON)) {
            advance();
            return new ExpressionStmt(pos(), null);
        }
        ExpressionNode expr = parseExpression();
        expectSemicolon();
        return new ExpressionStmt(pos(), expr);
    }

    private StatementNode parseReturn() {
        SourcePosition p = pos();
        advance();
        if (check(TokenType.SEMICOLON)) {
            advance();
            return new ReturnStmt(p, null);
        }
        if (check(TokenType.RBRACE) || atEnd()) {
            // bare return: `return` followed by the end of the block
            return new ReturnStmt(p, null);
        }
        ExpressionNode value = parseExpression();
        expectSemicolon();
        return new ReturnStmt(p, value);
    }

    private StatementNode parseIfStatement() {
        SourcePosition p = pos();
        advance();
        expect(TokenType.LPAREN, "Expected '(' after 'if'", "PARSE028");
        ExpressionNode cond = parseExpression();
        expect(TokenType.RPAREN, "Expected ')'", "PARSE029");
        StatementNode thenB = parseStatement();
        StatementNode elseB = null;
        if (check(TokenType.ELSE)) {
            advance();
            elseB = parseStatement();
        }
        return new IfStmt(p, cond, thenB, elseB);
    }

    private StatementNode parseWhileStatement() {
        SourcePosition p = pos();
        advance();
        expect(TokenType.LPAREN, "Expected '(' after 'while'", "PARSE030");
        ExpressionNode cond = parseExpression();
        expect(TokenType.RPAREN, "Expected ')'", "PARSE031");
        StatementNode body = parseStatement();
        return new WhileStmt(p, cond, body);
    }

    private StatementNode parseDoWhileStatement() {
        SourcePosition p = pos();
        advance();
        StatementNode body = parseStatement();
        expect(TokenType.WHILE, "Expected 'while' after 'do'", "PARSE060");
        expect(TokenType.LPAREN, "Expected '(' after 'while'", "PARSE061");
        ExpressionNode cond = parseExpression();
        expect(TokenType.RPAREN, "Expected ')'", "PARSE062");
        expectSemicolon();
        return new DoWhileStmt(p, cond, body);
    }

    private StatementNode parseForStatement() {
        SourcePosition p = pos();
        advance();
        expect(TokenType.LPAREN, "Expected '(' after 'for'", "PARSE032");
        if (check(TokenType.VAR, TokenType.VAL) && checkNext(TokenType.IDENTIFIER)
                && pos + 2 < tokens.size() && tokens.get(pos + 2).is(TokenType.IDENTIFIER)
                && "in".equals(tokens.get(pos + 2).value())) {
            advance();
            String varName = advance().value();
            advance();
            ExpressionNode collection = parseExpression();
            expect(TokenType.RPAREN, "Expected ')'", "PARSE035");
            StatementNode body = parseStatement();
            return new ForInStmt(p, varName, collection, body);
        }
        StatementNode init;
        if (check(TokenType.SEMICOLON)) {
            advance();
            init = new ExpressionStmt(p, null);
        } else if (check(TokenType.VAR, TokenType.VAL) ||
                (isPrimitiveType() && checkNext(TokenType.IDENTIFIER))) {
            init = parseVarDecl();
        } else {
            ExpressionNode initExpr = parseExpression();
            expectSemicolon();
            init = new ExpressionStmt(p, initExpr);
        }
        ExpressionNode cond = null;
        if (!check(TokenType.SEMICOLON)) {
            cond = parseExpression();
        }
        expectSemicolon();
        ExpressionNode update = null;
        if (!check(TokenType.RPAREN)) {
            update = parseExpression();
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE035");
        StatementNode body = parseStatement();
        return new ForStmt(p, init, cond, update, body);
    }

    private StatementNode parseThrowStatement() {
        SourcePosition p = pos();
        advance();
        ExpressionNode expr = parseExpression();
        expectSemicolon();
        return new ThrowStmt(p, expr);
    }

    private StatementNode parseTryStatement() {
        SourcePosition p = pos();
        advance();
        List<StatementNode> tryBody = parseBlock();
        List<CatchClause> catchClauses = new ArrayList<>();
        while (check(TokenType.CATCH)) {
            SourcePosition cp = pos();
            advance();
            expect(TokenType.LPAREN, "Expected '('", "PARSE050");
            String excType = parseTypeRef();
            String excName = expectId("Expected exception name", "PARSE051");
            expect(TokenType.RPAREN, "Expected ')'", "PARSE052");
            List<StatementNode> catchBody = parseBlock();
            catchClauses.add(new CatchClause(cp, excType, excName, catchBody));
        }
        List<StatementNode> finallyBody = List.of();
        if (check(TokenType.FINALLY)) {
            advance();
            finallyBody = parseBlock();
        }
        return new TryStmt(p, tryBody, catchClauses, finallyBody);
    }

    private StatementNode parseSwitchStatement() {
        SourcePosition p = pos();
        advance();
        expect(TokenType.LPAREN, "Expected '(' after 'switch'", "PARSE070");
        ExpressionNode expr = parseExpression();
        expect(TokenType.RPAREN, "Expected ')'", "PARSE071");
        expect(TokenType.LBRACE, "Expected '{'", "PARSE072");
        List<SwitchCase> cases = new ArrayList<>();
        List<StatementNode> defaultBody = List.of();
        while (!check(TokenType.RBRACE) && !atEnd()) {
            if (check(TokenType.CASE)) {
                SourcePosition cp = pos();
                advance();
                ExpressionNode value = parseSwitchCasePatternOrValue(cp);
                expect(TokenType.COLON, "Expected ':' (switch statement) ou '->' (switch expressão)", "PARSE073");
                List<StatementNode> caseBody = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE) && !atEnd()) {
                    caseBody.add(parseStatement());
                }
                cases.add(new SwitchCase(cp, value, caseBody));
            } else if (check(TokenType.DEFAULT)) {
                advance();
                expect(TokenType.COLON, "Expected ':'", "PARSE074");
                defaultBody = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE) && !atEnd()) {
                    defaultBody.add(parseStatement());
                }
            } else {
                advance();
            }
        }
        expect(TokenType.RBRACE, "Expected '}'", "PARSE075");
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
    private ExpressionNode parseSwitchCasePatternOrValue(SourcePosition cp) {
        // pattern matching: case Type var  /  case Type(var1, var2)
        if (check(TokenType.IDENTIFIER) && pos + 2 < tokens.size()
                && tokens.get(pos + 1).type() == TokenType.IDENTIFIER
                && (tokens.get(pos + 2).type() == TokenType.COLON
                        || tokens.get(pos + 2).type() == TokenType.ARROW)) {
            String typeName = advance().value();
            String varName = advance().value();
            return new PatternExpr(cp, typeName, varName, java.util.List.of());
        }
        if (check(TokenType.IDENTIFIER) && pos + 1 < tokens.size()
                && tokens.get(pos + 1).type() == TokenType.LPAREN) {
            // Try destructuring: case Type(var1, var2)
            String typeName = tokens.get(pos).value();
            int depth = 0;
            int rparenPos = -1;
            for (int k = pos + 1; k < tokens.size() && k < pos + 20; k++) {
                TokenType tt = tokens.get(k).type();
                if (tt == TokenType.LPAREN) depth++;
                else if (tt == TokenType.RPAREN) {
                    depth--;
                    if (depth == 0) { rparenPos = k; break; }
                }
            }
            if (rparenPos != -1 && rparenPos + 1 < tokens.size()
                    && (tokens.get(rparenPos + 1).type() == TokenType.COLON
                            || tokens.get(rparenPos + 1).type() == TokenType.ARROW)) {
                java.util.List<String> fieldVars = new java.util.ArrayList<>();
                for (int q = pos + 2; q < rparenPos; q++) {
                    if (tokens.get(q).type() == TokenType.IDENTIFIER) {
                        String v = tokens.get(q).value();
                        if (("var".equals(v) || "val".equals(v)) && q + 1 < rparenPos
                                && tokens.get(q + 1).type() == TokenType.IDENTIFIER) {
                            fieldVars.add(tokens.get(q + 1).value());
                            q++;
                        } else {
                            fieldVars.add(v);
                        }
                    }
                }
                advance(); // Type
                advance(); // LPAREN
                while (!check(TokenType.RPAREN) && !atEnd()) advance();
                if (check(TokenType.RPAREN)) advance();
                return new PatternExpr(cp, typeName, null, java.util.List.copyOf(fieldVars));
            }
            return parseExpression();
        }
        return parseExpression();
    }

    /**
     * Switch como expressão (SYN001): {@code switch (e) { case A -> b; case T v -> c;
     * default -> d }}. Forma aditiva — os cases usam {@code ->} e o corpo de cada
     * caso é UMA expressão (o valor do caso). O {@code default} é obrigatório
     * (validado no analyzer, SEM032) — uma expressão não pode "cair" sem valor.
     */
    private ExpressionNode parseSwitchExpression() {
        SourcePosition p = pos();
        advance(); // switch
        expect(TokenType.LPAREN, "Expected '(' after 'switch'", "PARSE070");
        ExpressionNode expr = parseExpression();
        expect(TokenType.RPAREN, "Expected ')'", "PARSE071");
        expect(TokenType.LBRACE, "Expected '{'", "PARSE072");
        List<SwitchExprCase> cases = new ArrayList<>();
        ExpressionNode defaultValue = null;
        while (!check(TokenType.RBRACE) && !atEnd()) {
            if (check(TokenType.CASE)) {
                SourcePosition cp = pos();
                advance();
                ExpressionNode value = parseSwitchCasePatternOrValue(cp);
                expect(TokenType.ARROW,
                        "Switch expressão exige '->' (a forma statement usa ':')", "PARSE076");
                ExpressionNode body = parseExpression();
                cases.add(new SwitchExprCase(cp, value, body));
            } else if (check(TokenType.DEFAULT)) {
                advance();
                expect(TokenType.ARROW, "Expected '->' after 'default'", "PARSE077");
                defaultValue = parseExpression();
            } else {
                error("Esperava 'case' ou 'default' em switch expressão", "PARSE078");
                advance();
            }
        }
        expect(TokenType.RBRACE, "Expected '}'", "PARSE075");
        return new SwitchExpr(p, expr, cases, defaultValue);
    }

    private StatementNode parseVarDecl() {
        SourcePosition p = pos();
        String type = "var";
        if (check(TokenType.VAR, TokenType.VAL)) {
            advance();
        } else {
            type = parseTypeRef();
        }
        String name = expectId("Expected variable name", "PARSE037");
        if (check(TokenType.COLON)) {
            // var name: Type = value — explicit type annotation
            advance();
            type = parseTypeRef();
        }
        ExpressionNode init = null;
        if (check(TokenType.EQUAL)) {
            advance();
            init = parseExpression();
        }
        expectSemicolon();
        return new VarDeclStmt(p, type, name, init);
    }

    /**
     * Lookahead de declaração tipada em statement: `Type name = ...` —
     * cobre nullable (`String? s`), arrays (`Int[] arr`) e genéricos
     * (`List<Int> xs`). A detecção antiga (checkNext(IDENTIFIER)) quebrava
     * quando `?`/`[`/`<` vinha logo após o tipo.
     */
    private boolean lookaheadTypedVarDecl() {
        if (pos >= tokens.size()) return false;
        TokenType t0 = tokens.get(pos).type();
        if (t0 != TokenType.IDENTIFIER && t0 != TokenType.VOID && !isPrimitiveTypeToken(t0)) {
            return false;
        }
        int i = pos + 1;
        while (i + 1 < tokens.size() && tokens.get(i).is(TokenType.DOT)
                && tokens.get(i + 1).is(TokenType.IDENTIFIER)) {
            i += 2;
        }
        if (i < tokens.size() && tokens.get(i).is(TokenType.LESS)) {
            int depth = 0;
            while (i < tokens.size()) {
                TokenType tt = tokens.get(i).type();
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
        while (i < tokens.size()) {
            TokenType tt = tokens.get(i).type();
            if (tt == TokenType.QUESTION) {
                i++;
            } else if (tt == TokenType.LBRACKET && i + 1 < tokens.size()
                    && tokens.get(i + 1).is(TokenType.RBRACKET)) {
                i += 2;
            } else {
                break;
            }
        }
        return i < tokens.size() && tokens.get(i).is(TokenType.IDENTIFIER);
    }

    private ExpressionNode parseExpression() {
        if (check(TokenType.SWITCH)) {
            return parseSwitchExpression();
        }
        return parseAssignment();
    }

    private ExpressionNode parseAssignment() {
        ExpressionNode left = parseBinary(0);
        if (check(TokenType.EQUAL, TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL,
                TokenType.STAR_EQUAL, TokenType.SLASH_EQUAL, TokenType.PERCENT_EQUAL,
                TokenType.AMP_EQUAL, TokenType.PIPE_EQUAL, TokenType.CARET_EQUAL,
                TokenType.LESS_LESS_EQUAL, TokenType.GREATER_GREATER_EQUAL,
                TokenType.GREATER_GREATER_GREATER_EQUAL)) {
            String op = advance().value();
            ExpressionNode right = parseAssignment();
            return new AssignmentExpr(pos(), left, op, right);
        }
        return left;
    }

    private ExpressionNode parseBinary(int minPrec) {
        ExpressionNode left = parseUnary();
        while (isBinaryOp() && precedence(peek().value()) >= minPrec) {
            String op = advance().value();
            int prec = precedence(op);
            ExpressionNode right = parseBinary(prec + 1);
            left = new BinaryExpr(pos(), op, left, right);
        }
        return left;
    }

    private int precedence(String op) {
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

    private boolean isBinaryOp() {
        return switch (peek().type()) {
            case PLUS, MINUS, STAR, SLASH, PERCENT,
                 EQUAL_EQUAL, BANG_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
                 AMP_AMP, PIPE_PIPE, AMP, PIPE, CARET,
                 LESS_LESS, GREATER_GREATER, GREATER_GREATER_GREATER,
                 INSTANCEOF, AS -> true;
            default -> false;
        };
    }

    private ExpressionNode parseUnary() {
        // spawn <expr> / await <expr> em posição de expressão — baixados como
        // chamadas sintéticas __kof_spawn_expr / __kof_await (sem AST novo)
        if (check(TokenType.SPAWN)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode e = parseUnary();
            return new MethodCallExpr(p, null, "__kof_spawn_expr", List.of(), List.of(e));
        }
        if (check(TokenType.AWAIT)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode e = parseUnary();
            return new MethodCallExpr(p, null, "__kof_await", List.of(), List.of(e));
        }
        if (check(TokenType.BANG)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode operand = parseUnary();
            return new UnaryExpr(p, "!", operand, true);
        }
        if (check(TokenType.MINUS)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode operand = parseUnary();
            return new UnaryExpr(p, "-", operand, true);
        }
        if (check(TokenType.PLUS_PLUS)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode operand = parseUnary();
            return new UnaryExpr(p, "++", operand, true);
        }
        if (check(TokenType.MINUS_MINUS)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode operand = parseUnary();
            return new UnaryExpr(p, "--", operand, true);
        }
        return parsePostfix();
    }

    private ExpressionNode parsePostfix() {
        ExpressionNode expr = parsePrimary();
        while (true) {
            if (check(TokenType.LBRACE) && expr instanceof IdentifierExpr ie) {
                // trailing lambda call: identifier { ... } (transaction { ... })
                expr = new MethodCallExpr(pos(), null, ie.name(), List.of(),
                        List.of(new LambdaExpr(pos(), List.of(), parseBlock())));
            } else if (check(TokenType.DOT)) {
                advance();
                String field;
                if (check(TokenType.IDENTIFIER) || check(TokenType.AWAIT) || check(TokenType.SPAWN)) {
                    field = advance().value();
                } else if (isTypeKeywordField()) {
                    // type keywords are valid method names after a dot
                    // (config.int, user.toString, ...)
                    field = advance().value();
                } else {
                    field = expectId("Expected field name", "PARSE039");
                }
                if (check(TokenType.LBRACE)) {
                    // trailing lambda call: receiver.method { ... } — the
                    // block is the final argument of the method call.
                    // With explicit parameters (receiver.method { s -> ... }
                    // or { s: Int -> ... }) the block is a typed lambda: the
                    // remaining statements up to '}' form the lambda body.
                    if (looksLikeLambdaBlockParams()) {
                        List<FormalParameterNode> params = parseLambdaBlockParams();
                        expect(TokenType.ARROW, "Expected '->'", "PARSE042");
                        // the opening '{' was consumed by the parameter list;
                        // the lambda body is the statement list up to '}'
                        List<StatementNode> body = new ArrayList<>();
                        while (!check(TokenType.RBRACE) && !atEnd()) {
                            body.add(parseStatement());
                        }
                        expect(TokenType.RBRACE, "Expected '}'", "PARSE025");
                        expr = new MethodCallExpr(pos(), expr, field, List.of(),
                                List.of(new LambdaExpr(pos(), params, body)));
                    } else {
                        expr = new MethodCallExpr(pos(), expr, field, List.of(),
                                List.of(new LambdaExpr(pos(), List.of(), parseBlock())));
                    }
                } else {
                    expr = new FieldAccessExpr(pos(), expr, field);
                }
            } else if (check(TokenType.LBRACKET)) {
                SourcePosition p = pos();
                advance();
                ExpressionNode index = parseExpression();
                expect(TokenType.RBRACKET, "Expected ']'", "PARSE045");
                expr = new ArrayAccessExpr(p, expr, index);
            } else if (check(TokenType.LPAREN)) {
                List<ExpressionNode> args = parseArguments();
                if (check(TokenType.LBRACE)) {
                    // Query DSL tipada: `Entity.query(db) { where ...; }` — o `{`
                    // é o token atual; parseQueryDsl consome o bloco.
                    if (expr instanceof FieldAccessExpr fa && "query".equals(fa.fieldName())
                            && fa.receiver() instanceof IdentifierExpr qr
                            && entityNames.contains(qr.name()) && args.size() == 1) {
                        return parseQueryDsl(fa.position(), qr.name(), args.get(0));
                    }
                    args.add(new LambdaExpr(pos(), List.of(), parseBlock()));
                }
                if (expr instanceof IdentifierExpr ie) {
                    expr = new MethodCallExpr(pos(), null, ie.name(), List.of(), args);
                } else if (expr instanceof FieldAccessExpr fa) {
                    expr = new MethodCallExpr(pos(), fa.receiver(), fa.fieldName(), List.of(), args);
                } else {
                    expr = new MethodCallExpr(pos(), expr, "", List.of(), args);
                }
            } else if (check(TokenType.LESS) && (expr instanceof IdentifierExpr || expr instanceof FieldAccessExpr)
                    && looksLikeGenericCall()) {
                List<String> typeArgs = parseCallTypeArguments();
                List<ExpressionNode> args = parseArguments();
                if (check(TokenType.LBRACE)) {
                    args.add(new LambdaExpr(pos(), List.of(), parseBlock()));
                }
                if (expr instanceof IdentifierExpr ie3) {
                    expr = new MethodCallExpr(pos(), null, ie3.name(), typeArgs, args);
                } else if (expr instanceof FieldAccessExpr fa2) {
                    expr = new MethodCallExpr(pos(), fa2.receiver(), fa2.fieldName(), typeArgs, args);
                }
            } else if (check(TokenType.PLUS_PLUS)
                    && (expr instanceof IdentifierExpr || expr instanceof FieldAccessExpr
                    || expr instanceof ArrayAccessExpr)) {
                advance();
                expr = new UnaryExpr(pos(), "++", expr, false);
            } else if (check(TokenType.MINUS_MINUS)
                    && (expr instanceof IdentifierExpr || expr instanceof FieldAccessExpr
                    || expr instanceof ArrayAccessExpr)) {
                advance();
                expr = new UnaryExpr(pos(), "--", expr, false);
            } else {
                break;
            }
        }
        return expr;
    }

    private ExpressionNode parsePrimary() {
        if (check(TokenType.INT_LITERAL, TokenType.LONG_LITERAL, TokenType.FLOAT_LITERAL,
                TokenType.DOUBLE_LITERAL, TokenType.STRING_LITERAL, TokenType.CHAR_LITERAL,
                TokenType.BOOLEAN_LITERAL, TokenType.NULL_LITERAL)) {
            Token t = advance();
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
                    error("numeric literal out of range: " + t.value(), "PARSE084");
                    return new LiteralExpr(pos(), ConcreteLiteralKind.NULL, "0");
                }
            }
            return new LiteralExpr(pos(), kind, t.value());
        }
        if (check(TokenType.THIS)) {
            Token t = advance();
            return new IdentifierExpr(pos(), t.value());
        }
        if (check(TokenType.SUPER)) {
            Token t = advance();
            return new IdentifierExpr(pos(), t.value());
        }
        if (check(TokenType.IDENTIFIER)) {
            return new IdentifierExpr(pos(), advance().value());
        }
        if (check(TokenType.NEW)) {
            return parseNewExpression();
        }
        if (check(TokenType.LPAREN)) {
            if (looksLikeLambdaParams()) {
                List<FormalParameterNode> params = parseLambdaParams();
                expect(TokenType.ARROW, "Expected '->'", "PARSE042");
                return new LambdaExpr(pos(), params, parseLambdaBody());
            }
            advance();
            ExpressionNode expr = parseExpression();
            expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
            return expr;
        }
        if (check(TokenType.IF)) {
            SourcePosition p = pos();
            advance();
            expect(TokenType.LPAREN, "Expected '(' after if", "PARSE043");
            ExpressionNode condition = parseExpression();
            expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
            ExpressionNode thenExpr = parseExpression();
            expect(TokenType.ELSE, "Expected 'else'", "PARSE044");
            ExpressionNode elseExpr = parseExpression();
            return new IfExpr(p, condition, thenExpr, elseExpr);
        }
        if (check(TokenType.LBRACE)) {
            List<FormalParameterNode> params = new ArrayList<>();
            List<StatementNode> body = parseBlock();
            return new LambdaExpr(pos(), params, body);
        }
        error("Unexpected token in expression: " + peek().value(), "PARSE041");
        advance();
        return new IdentifierExpr(pos(), "error");
    }

    private boolean looksLikeLambdaParams() {
        // `(x: T) -> ...` — IDENTIFIER COLON no início é inequívoco: uma
        // expressão entre parênteses não começa com 'id :'. Cobre também
        // `(s: (Int) -> Int) -> ...` (o ARROW do tipo de função seria
        // confundido com o delimitador da lambda no scan abaixo).
        if (checkNext(TokenType.IDENTIFIER)
                && pos + 2 < tokens.size()
                && tokens.get(pos + 2).type() == TokenType.COLON) {
            return true;
        }
        int i = pos + 1;
        int depth = 0;
        while (i < tokens.size()) {
            TokenType t = tokens.get(i).type();
            if (t == TokenType.LPAREN) {
                depth++;
            } else if (t == TokenType.RPAREN) {
                if (depth == 0) {
                    return i + 1 < tokens.size() && tokens.get(i + 1).type() == TokenType.ARROW;
                }
                depth--;
            } else if (t == TokenType.ARROW) {
                // ARROW no depth 0 é o delimitador da lambda; dentro de parens
                // aninhados (ex.: `(x) -> ((y) -> ...)`) faz parte de outro
                // lambda — ignorar.
                if (depth == 0) return false;
            }
            i++;
        }
        return false;
    }

    /**
     * Trailing-lambda block with explicit parameters:
     * {@code method { s -> ... }}, {@code method { s: Int -> ... }} or
     * {@code method { a, b -> ... }}. A plain block ({@code method { ... }})
     * returns false — statements start with keywords/identifiers, and an
     * identifier followed by `->` or `,` or `: type ->` is a parameter list.
     */
    private boolean looksLikeLambdaBlockParams() {
        int i = pos + 1; // first token inside the block
        if (i >= tokens.size()) return false;
        int look = 0;
        // scan a small window: ident (: type)? (, ident (: type)?)* ->
        while (look < 8 && i + look < tokens.size()) {
            TokenType t = tokens.get(i + look).type();
            if (t == TokenType.ARROW) {
                return look > 0;
            }
            if (t == TokenType.IDENTIFIER || isPrimitiveTypeToken(t)) {
                look++;
                // optional ": Type"
                if (i + look < tokens.size() && tokens.get(i + look).type() == TokenType.COLON) {
                    look++;
                    // type reference: identifier or primitive type keyword
                    if (i + look < tokens.size()
                            && (tokens.get(i + look).type() == TokenType.IDENTIFIER
                                    || isPrimitiveTypeToken(tokens.get(i + look).type()))) {
                        look++;
                    } else {
                        return false;
                    }
                }
                if (i + look < tokens.size() && tokens.get(i + look).type() == TokenType.COMMA) {
                    look++;
                    continue;
                }
                continue;
            }
            return false;
        }
        return false;
    }

    /** Parses {@code { s: Int -> ... }} parameter list (the LBRACE is current). */
    private List<FormalParameterNode> parseLambdaBlockParams() {
        expect(TokenType.LBRACE, "Expected '{'", "PARSE013");
        List<FormalParameterNode> params = new ArrayList<>();
        params.add(parseLambdaParameter());
        while (check(TokenType.COMMA)) {
            advance();
            params.add(parseLambdaParameter());
        }
        return params;
    }

    private List<FormalParameterNode> parseLambdaParams() {
        List<FormalParameterNode> params = new ArrayList<>();
        expect(TokenType.LPAREN, "Expected '('", "PARSE011");
        if (!check(TokenType.RPAREN)) {
            params.add(parseLambdaParameter());
            while (check(TokenType.COMMA)) {
                advance();
                params.add(parseLambdaParameter());
            }
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE012");
        return params;
    }

    private FormalParameterNode parseLambdaParameter() {
        SourcePosition p = pos();
        String name = expectId("Expected parameter name", "PARSE010");
        String type = "Object";
        if (check(TokenType.COLON)) {
            advance();
            type = parseTypeRef();
        }
        return new FormalParameterNode(p, List.of(), type, name);
    }

    private List<StatementNode> parseLambdaBody() {
        if (check(TokenType.LBRACE)) {
            return parseBlock();
        }
        ExpressionNode expr = parseExpression();
        if (check(TokenType.SEMICOLON)) advance();
        return List.of(new ReturnStmt(pos(), expr));
    }

    private ExpressionNode parseNewExpression() {
        SourcePosition p = pos();
        advance();
        String typeName = parseNewTypeRef();
        List<String> typeArgs = List.of();
        if (check(TokenType.LESS)) {
            advance();
            typeArgs = new ArrayList<>();
            while (!check(TokenType.GREATER) && !atEnd()) {
                if (check(TokenType.IDENTIFIER) || isPrimitiveType()) {
                    typeArgs.add(parseTypeRef());
                } else {
                    advance();
                }
                if (check(TokenType.COMMA)) advance();
            }
            expect(TokenType.GREATER, "Expected '>' after type arguments", "PARSE076");
        }
        if (check(TokenType.LBRACKET)) {
            advance();
            ExpressionNode size = parseExpression();
            expect(TokenType.RBRACKET, "Expected ']'", "PARSE046");
            return new NewArrayExpr(p, typeName, size);
        }
        List<ExpressionNode> args = parseArguments();
        return new NewExpr(p, typeName, typeArgs, args);
    }

    private String parseNewTypeRef() {
        if (check(TokenType.VOID)) {
            advance();
            return "void";
        }
        if (isPrimitiveType()) {
            return advance().value();
        }
        if (check(TokenType.IDENTIFIER)) {
            String name = peek().value();
            if (PRIMITIVE_TYPE_NAMES.contains(name.toLowerCase()) || PRIMITIVE_TYPE_NAMES.contains(name)) {
                advance();
                return name;
            }
            StringBuilder type = new StringBuilder();
            type.append(advance().value());
            while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
                advance();
                type.append('.').append(advance().value());
            }
            return type.toString();
        }
        error("Expected type", "PARSE044");
        return "Object";
    }


    private boolean looksLikeGenericCall() {
        if (!check(TokenType.LESS)) return false;
        int i = pos + 1;
        int depth = 1;
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            switch (t.type()) {
                case LESS -> depth++;
                case GREATER -> {
                    depth--;
                    if (depth == 0) return i + 1 < tokens.size() && tokens.get(i + 1).type() == TokenType.LPAREN;
                }
                case GREATER_GREATER -> {
                    depth -= 2;
                    if (depth == 0) return i + 1 < tokens.size() && tokens.get(i + 1).type() == TokenType.LPAREN;
                }
                case GREATER_GREATER_GREATER -> {
                    depth -= 3;
                    if (depth == 0) return i + 1 < tokens.size() && tokens.get(i + 1).type() == TokenType.LPAREN;
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

    private List<String> parseCallTypeArguments() {
        List<String> typeArgs = new ArrayList<>();
        splitShiftRight();
        expect(TokenType.LESS, "Expected '<'", "PARSE078");
        while (!check(TokenType.GREATER) && !atEnd()) {
            splitShiftRight();
            if (check(TokenType.IDENTIFIER) || isPrimitiveType()) {
                String typeRef = parseTypeRef();
                while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
                    advance();
                    advance();
                    typeRef += "[]";
                }
                typeArgs.add(typeRef);
            } else {
                advance();
            }
            if (check(TokenType.COMMA)) advance();
        }
        splitShiftRight();
        expect(TokenType.GREATER, "Expected '>'", "PARSE079");
        return typeArgs;
    }

    private void splitShiftRight() {
        Token cur = tokens.get(pos);
        if (cur.type() == TokenType.GREATER_GREATER) {
            tokens.set(pos, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column(), cur.offset(), 1));
            tokens.add(pos + 1, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column() + 1, cur.offset() + 1, 1));
        } else if (cur.type() == TokenType.GREATER_GREATER_GREATER) {
            tokens.set(pos, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column(), cur.offset(), 1));
            tokens.add(pos + 1, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column() + 1, cur.offset() + 1, 1));
            tokens.add(pos + 2, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column() + 2, cur.offset() + 2, 1));
        }
    }

    private boolean isPrimitiveTypeAtNext() {
        if (pos + 1 >= tokens.size()) return false;
        Token n = tokens.get(pos + 1);
        return switch (n.type()) {
            case INT_TYPE, LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE, BOOL_TYPE, BYTE_TYPE,
                    SHORT_TYPE, CHAR_TYPE, STRING_TYPE -> true;
            default -> false;
        };
    }

    private List<ExpressionNode> parseArguments() {
        expect(TokenType.LPAREN, "Expected '('", "PARSE042");
        List<ExpressionNode> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            args.add(parseExpression());
            while (check(TokenType.COMMA)) {
                advance();
                args.add(parseExpression());
            }
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE043");
        return args;
    }

    private String parseTypeRef() {
        StringBuilder type = new StringBuilder();
        if (check(TokenType.VOID)) {
            advance();
            return "void";
        }
        // tipo de função: (Int) -> Int ou (Int, String) -> Bool (bug 8)
        if (check(TokenType.LPAREN)) {
            return parseFunctionTypeRef();
        }
        if (isPrimitiveType()) {
            return advance().value();
        }
        if (check(TokenType.IDENTIFIER)) {
            type.append(advance().value());
            while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
                advance();
                type.append('.').append(advance().value());
            }
        } else {
            error("Expected type", "PARSE044");
            return "Object";
        }
        if (check(TokenType.LESS)) {
            StringBuilder args = new StringBuilder("<");
            int depth = 0;
            boolean first = true;
            do {
                splitShiftRight();
                boolean isClose = check(TokenType.GREATER);
                if (check(TokenType.LESS)) depth++;
                else if (isClose) depth--;
                if (!first && !isClose) args.append(tokens.get(pos).value());
                else if (!first && isClose && depth > 0) args.append(tokens.get(pos).value());
                first = false;
                advance();
            } while (depth > 0 && !atEnd());
            args.append(">");
            type.append(args);
        }
        while (check(TokenType.LBRACKET)) {
            advance();
            expect(TokenType.RBRACKET, "Expected ']'", "PARSE045");
            type.append("[]");
        }
        while (check(TokenType.QUESTION)) {
            advance();
            type.append("?");
        }
        return type.toString();
    }

    private boolean isPrimitiveType() {
        return check(TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE,
                TokenType.DOUBLE_TYPE, TokenType.BOOL_TYPE, TokenType.BYTE_TYPE,
                TokenType.SHORT_TYPE, TokenType.CHAR_TYPE, TokenType.STRING_TYPE);
    }

    /**
     * Tipo de função: `(Int) -> Int`, `(Int, String) -> Bool`. Bug 8 — antes
     * não parseava como tipo (param de lambda, arg genérico, declaração).
     */
    private String parseFunctionTypeRef() {
        StringBuilder sb = new StringBuilder("(");
        expect(TokenType.LPAREN, "Expected '('", "PARSE040");
        boolean first = true;
        while (!check(TokenType.RPAREN) && !atEnd()) {
            if (!first) {
                if (check(TokenType.COMMA)) advance();
                sb.append(", ");
            }
            sb.append(parseTypeRef());
            first = false;
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
        sb.append(")");
        expect(TokenType.ARROW, "Expected '->'", "PARSE042");
        sb.append(" -> ").append(parseTypeRef());
        return sb.toString();
    }

    private static boolean isPrimitiveTypeToken(TokenType t) {
        return t == TokenType.INT_TYPE || t == TokenType.LONG_TYPE
                || t == TokenType.FLOAT_TYPE || t == TokenType.DOUBLE_TYPE
                || t == TokenType.BOOL_TYPE || t == TokenType.BYTE_TYPE
                || t == TokenType.SHORT_TYPE || t == TokenType.CHAR_TYPE
                || t == TokenType.STRING_TYPE;
    }

    private boolean check(TokenType... types) {
        if (atEnd()) return false;
        TokenType cur = peek().type();
        for (TokenType t : types) {
            if (cur == t) return true;
        }
        return false;
    }

    private boolean checkNext(TokenType type) {
        int next = pos + 1;
        return next < tokens.size() && tokens.get(next).type() == type;
    }

    private boolean atEnd() {
        return pos >= tokens.size() || peek().type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(Math.min(pos, tokens.size() - 1));
    }

    private Token advance() {
        if (!atEnd()) pos++;
        return tokens.get(pos - 1);
    }

    private Token expect(TokenType type, String message, String code) {
        if (check(type)) return advance();
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
        return peek();
    }

    private void expectSemicolon() {
        if (check(TokenType.SEMICOLON)) {
            advance();
        }
    }

    private String expectId(String message, String code) {
        if (check(TokenType.IDENTIFIER)) return advance().value();
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
        return "error";
    }

    /** Type keywords are valid as field/method names after a dot (config.int). */
    private boolean isTypeKeywordField() {
        return check(TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE,
                TokenType.DOUBLE_TYPE, TokenType.BOOL_TYPE, TokenType.BYTE_TYPE,
                TokenType.SHORT_TYPE, TokenType.CHAR_TYPE, TokenType.STRING_TYPE,
                TokenType.RECORD);
    }

    private boolean isTypeKeywordAtNext() {
        if (pos + 1 >= tokens.size()) return false;
        return switch (tokens.get(pos + 1).type()) {
            case INT_TYPE, LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE, BOOL_TYPE,
                    BYTE_TYPE, SHORT_TYPE, CHAR_TYPE, STRING_TYPE -> true;
            default -> false;
        };
    }

    private SourcePosition pos() {
        Token t = peek();
        return new SourcePosition(file, t.line(), t.column(), t.offset(), t.length());
    }

    private void error(String message, String code) {
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
    }
}
