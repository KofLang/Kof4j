package dev.kof.compiler;

import java.util.List;

sealed interface AstNode {
    SourcePosition position();
}

/**
 * @Name ou @Name(key = valor, ...) — metadado de interop anexado a
 * classes, campos, métodos, construtores e parâmetros. Os valores são
 * constantes em compile-time (literal ou array de literais); o compilador
 * os emite no bytecode como RuntimeVisible/InvisibleAnnotations.
 */
record AnnotationNode(SourcePosition position, String name,
                      List<AnnotationPair> pairs) implements AstNode {

    /** Valor único na forma curta @Name("x"): par com chave null. */
    boolean singleValue() {
        return pairs.size() == 1 && pairs.get(0).key() == null;
    }
}

/**
 * Par chave=valor de uma annotation. O valor é uma constante em
 * compile-time: String, Integer, Long, Float, Double, Boolean, Character,
 * null ou List&lt;Object&gt; (array de constantes). Um identificador não
 * constante é rejeitado com ANNOT001 — nunca vira silenciosamente outro tipo.
 */
record AnnotationPair(String key, Object value) {
}

/** Valor Classe.class de annotation (@JsonFormat(using = MyMapper.class)). */
record AnnotationClassRef(String typeName) {
}

/** Valor enum constante (@Retention(RetentionPolicy.RUNTIME)). */
record AnnotationEnumRef(String qualifiedConstant) {
}

record CompilationUnitNode(SourcePosition position, String packageName, List<String> imports,
                           List<? extends AstNode> declarations) implements AstNode {
}

record FunctionDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                               String name, List<FormalParameterNode> parameters,
                               List<String> thrownExceptions, List<String> typeParameters,
                               List<StatementNode> body, List<AnnotationNode> annotations) implements AstNode {

    public FunctionDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                                   String name, List<FormalParameterNode> parameters,
                                   List<String> thrownExceptions, List<String> typeParameters,
                                   List<StatementNode> body) {
        this(position, modifiers, returnType, name, parameters, thrownExceptions, typeParameters,
                body, List.of());
    }
}

/**
 * Declaração FFI {@code extern ["lib.so"] name(params): ReturnType;} —
 * assinatura de uma função externa (`.so`/lib nativa) formalizada em
 * compile-time (R3). O corpo vive fora do Kof; a declaração só assina o
 * contrato. {@code library} é o nome da lib (ex.: {@code libc.so.6}); quando
 * omitida, a resolução é no processo (loader lookup). O binding real é
 * lowering/runtime por target (TIER 2.1.4+); enquanto não existe, o lowering
 * emite o gap honesto FFI001 em vez de gerar bytecode quebrado.
 */
record ExternalFunctionNode(SourcePosition position, String library, String returnType,
                            String name, List<FormalParameterNode> parameters) implements AstNode {
}

/**
 * Bloco `test "nome" { ... }` — um caso de teste da suíte estruturada do
 * Kof (G6). O corpo roda isolado; assert falho = teste falho. O compilador
 * conhece os testes em compile-time (nunca reflection).
 */
record TestDeclarationNode(SourcePosition position, String name,
                           List<StatementNode> body) implements AstNode {
}

/**
 * Lifecycle: {@code application { onStart { ... } onShutdown { ... } }} —
 * os blocos são reduzidos pelo compilador a funções sintetizadas chamadas
 * no prólogo/epílogo do main (zero container, zero reflection).
 */
record ApplicationDeclarationNode(SourcePosition position,
                                  List<StatementNode> onStart,
                                  List<StatementNode> onShutdown) implements AstNode {
}

sealed interface TypeDeclarationNode extends AstNode {
    String name();
    List<String> modifiers();
}

record ClassDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                            String superClass, List<String> interfaces, List<String> typeParameters,
                            List<? extends AstNode> members, List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public ClassDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                String superClass, List<String> interfaces, List<String> typeParameters,
                                List<? extends AstNode> members) {
        this(position, name, modifiers, superClass, interfaces, typeParameters, members, List.of());
    }
}

record EnumDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                            List<String> constants,
                            List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public EnumDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                               List<String> constants) {
        this(position, name, modifiers, constants, List.of());
    }
}

record InterfaceDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                List<String> interfaces,
                                List<? extends AstNode> members,
                                List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public InterfaceDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                    List<String> interfaces, List<? extends AstNode> members) {
        this(position, name, modifiers, interfaces, members, List.of());
    }
}

 record RecordDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                             String superClass, List<String> interfaces,
                             List<String> typeParameters,
                             List<RecordComponentNode> components,
                             List<? extends AstNode> members,
                             List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public RecordDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                 String superClass, List<String> interfaces,
                                 List<RecordComponentNode> components,
                                 List<? extends AstNode> members) {
        this(position, name, modifiers, superClass, interfaces, List.of(), components, members, List.of());
    }

    public RecordDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                 String superClass, List<String> interfaces,
                                 List<RecordComponentNode> components,
                                 List<? extends AstNode> members,
                                 List<AnnotationNode> annotations) {
        this(position, name, modifiers, superClass, interfaces, List.of(), components, members, annotations);
    }
}

record RecordComponentNode(SourcePosition position, List<String> modifiers, String type, String name,
                            ExpressionNode initializer, List<AnnotationNode> annotations) implements AstNode {

    public RecordComponentNode(SourcePosition position, List<String> modifiers, String type, String name,
                               ExpressionNode initializer) {
        this(position, modifiers, type, name, initializer, List.of());
    }
}

/**
 * entity User {
 *     id: Long generated
 *     name: String
 *     email: String unique
 *     age: Int
 * }
 *
 * A entity é um record gerado pelo compilador + um schema registrado para
 * o ORM (kof.orm): o compilador conhece os campos, os tipos e as
 * constraints em compile-time — nunca reflection para descobrir schema.
 */
record EntityDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                             List<EntityFieldNode> fields,
                             List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public EntityDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                 List<EntityFieldNode> fields) {
        this(position, name, modifiers, fields, List.of());
    }
}

record EntityFieldNode(SourcePosition position, String type, String name,
                       boolean generated, boolean unique) implements AstNode {
}

sealed interface MemberNode extends AstNode {
}

record FieldDeclarationNode(SourcePosition position, List<String> modifiers, String type,
                            String name, ExpressionNode initializer,
                            List<AnnotationNode> annotations) implements MemberNode {

    public FieldDeclarationNode(SourcePosition position, List<String> modifiers, String type,
                                String name, ExpressionNode initializer) {
        this(position, modifiers, type, name, initializer, List.of());
    }
}

record MethodDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                             String name, List<FormalParameterNode> parameters,
                             List<String> thrownExceptions, List<StatementNode> body,
                             List<AnnotationNode> annotations) implements MemberNode {

    public MethodDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                                 String name, List<FormalParameterNode> parameters,
                                 List<String> thrownExceptions, List<StatementNode> body) {
        this(position, modifiers, returnType, name, parameters, thrownExceptions, body, List.of());
    }
}

record ConstructorDeclarationNode(SourcePosition position, List<String> modifiers,
                                  String name, List<FormalParameterNode> parameters,
                                  List<String> thrownExceptions,
                                  List<StatementNode> body,
                                  List<AnnotationNode> annotations) implements MemberNode {

    public ConstructorDeclarationNode(SourcePosition position, List<String> modifiers,
                                      String name, List<FormalParameterNode> parameters,
                                      List<String> thrownExceptions,
                                      List<StatementNode> body) {
        this(position, modifiers, name, parameters, thrownExceptions, body, List.of());
    }
}

record FormalParameterNode(SourcePosition position, List<String> modifiers, String type,
                           String name, ExpressionNode defaultExpression,
                           List<AnnotationNode> annotations) implements AstNode {

    public FormalParameterNode(SourcePosition position, List<String> modifiers, String type,
                               String name, ExpressionNode defaultExpression) {
        this(position, modifiers, type, name, defaultExpression, List.of());
    }

    public FormalParameterNode(SourcePosition position, List<String> modifiers, String type,
                               String name) {
        this(position, modifiers, type, name, null, List.of());
    }
}

sealed interface ExpressionNode extends AstNode {
}

record IdentifierExpr(SourcePosition position, String name) implements ExpressionNode {
}

record PatternExpr(SourcePosition position, String typeName, String varName, java.util.List<String> fieldVars) implements ExpressionNode {
    public PatternExpr(SourcePosition position, String typeName, String varName) {
        this(position, typeName, varName, java.util.List.of());
    }
}

sealed interface LiteralKind {
}

enum ConcreteLiteralKind implements LiteralKind {
    INT, LONG, FLOAT, DOUBLE, STRING, CHAR, BOOLEAN, NULL
}

record LiteralExpr(SourcePosition position, LiteralKind kind, String value) implements ExpressionNode {
}

record BinaryExpr(SourcePosition position, String operator, ExpressionNode left,
                  ExpressionNode right) implements ExpressionNode {
}

record UnaryExpr(SourcePosition position, String operator, ExpressionNode operand,
                 boolean prefix) implements ExpressionNode {
}

record AssignmentExpr(SourcePosition position, ExpressionNode target,
                      String operator, ExpressionNode value) implements ExpressionNode {
}

record MethodCallExpr(SourcePosition position, ExpressionNode receiver,
                      String methodName, List<String> typeArguments,
                      List<ExpressionNode> arguments) implements ExpressionNode {
}

record NewExpr(SourcePosition position, String typeName, List<String> typeArguments,
               List<ExpressionNode> arguments) implements ExpressionNode {
}

record NewArrayExpr(SourcePosition position, String elementType, ExpressionNode size) implements ExpressionNode {
}

record ArrayAccessExpr(SourcePosition position, ExpressionNode receiver, ExpressionNode index) implements ExpressionNode {
}

record FieldAccessExpr(SourcePosition position, ExpressionNode receiver,
                       String fieldName) implements ExpressionNode {
}

record IfExpr(SourcePosition position, ExpressionNode condition,
              ExpressionNode thenExpr, ExpressionNode elseExpr) implements ExpressionNode {
}

record LambdaExpr(SourcePosition position, List<FormalParameterNode> parameters,
                  List<StatementNode> body) implements ExpressionNode {
}

/**
 * Query DSL tipada (ORM001 — nível 3): {@code User.query(db) { where age > 18;
 * orderBy name desc; limit 10 }}. O compilador baixa para
 * {@code db.query<User>(db, "SELECT ... WHERE ... ORDER BY ... LIMIT ?",
 * binds...)} — SQL montado em compile-time, valores como binds preparados
 * (sem string-concat de entrada).
 */
record QueryDslExpr(SourcePosition position, String entityType,
                    ExpressionNode dbArg,
                    List<ExpressionNode> whereClauses,
                    List<ExpressionNode> orderByFields,
                    List<String> orderByDirs,
                    ExpressionNode limit) implements ExpressionNode {
}

sealed interface StatementNode extends AstNode {
}

record ExpressionStmt(SourcePosition position, ExpressionNode expression) implements StatementNode {
}

record ReturnStmt(SourcePosition position, ExpressionNode value) implements StatementNode {
}

record BlockStmt(SourcePosition position, List<StatementNode> statements) implements StatementNode {
}

record IfStmt(SourcePosition position, ExpressionNode condition,
              StatementNode thenBranch, StatementNode elseBranch) implements StatementNode {
}

record WhileStmt(SourcePosition position, ExpressionNode condition,
                 StatementNode body) implements StatementNode {
}

record ForStmt(SourcePosition position, StatementNode init, ExpressionNode condition,
               ExpressionNode update, StatementNode body) implements StatementNode {
}

record ForInStmt(SourcePosition position, String varName, ExpressionNode collection,
                 StatementNode body) implements StatementNode {
}

record VarDeclStmt(SourcePosition position, String type, String name,
                   ExpressionNode initializer) implements StatementNode {
}

record ThrowStmt(SourcePosition position, ExpressionNode expression) implements StatementNode {
}

/**
 * SpawnStmt — runs the given call (or block) as a concurrent task.
 * The program waits for spawned tasks before exiting.
 */
record SpawnStmt(SourcePosition position, ExpressionNode expression) implements StatementNode {
}

/**
 * AssertStmt — assert(condition) or assert(condition, "message").
 * Throws "assertion failed" (or the given message) when the condition
 * is false. The failure exit code powers `kof test`.
 */
record AssertStmt(SourcePosition position, ExpressionNode condition, String message) implements StatementNode {
}

record BreakStmt(SourcePosition position) implements StatementNode {
}

record ContinueStmt(SourcePosition position) implements StatementNode {
}

record SwitchCase(SourcePosition position, ExpressionNode value, List<StatementNode> body) implements AstNode {
}

record SwitchStmt(SourcePosition position, ExpressionNode expression,
                  List<SwitchCase> cases, List<StatementNode> defaultBody) implements StatementNode {
}

record CatchClause(SourcePosition position, String exceptionType, String exceptionName,
                   List<StatementNode> body) implements AstNode {
}

record TryStmt(SourcePosition position, List<StatementNode> tryBody,
               List<CatchClause> catchClauses, List<StatementNode> finallyBody) implements StatementNode {
}

record DoWhileStmt(SourcePosition position, ExpressionNode condition,
                   StatementNode body) implements StatementNode {
}
