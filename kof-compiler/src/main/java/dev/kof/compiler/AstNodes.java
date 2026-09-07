package dev.kof.compiler;

import java.util.List;

/**
 * @Name ou @Name(key = valor, ...) — metadado de interop anexado a
 * classes, campos, métodos, construtores e parâmetros. Os valores são
 * constantes em compile-time (literal ou array de literais); o compilador
 * os emite no bytecode como RuntimeVisible/InvisibleAnnotations.
 */
/**
 * Par chave=valor de uma annotation. O valor é uma constante em
 * compile-time: String, Integer, Long, Float, Double, Boolean, Character,
 * null ou List&lt;Object&gt; (array de constantes). Um identificador não
 * constante é rejeitado com ANNOT001 — nunca vira silenciosamente outro tipo.
 */
/** Valor Classe.class de annotation (@JsonFormat(using = MyMapper.class)). */
/** Valor enum constante (@Retention(RetentionPolicy.RUNTIME)). */
/**
 * Bloco `test "nome" { ... }` — um caso de teste da suíte estruturada do
 * Kof (G6). O corpo roda isolado; assert falho = teste falho. O compilador
 * conhece os testes em compile-time (nunca reflection).
 */
/**
 * Lifecycle: {@code application { onStart { ... } onShutdown { ... } }} —
 * os blocos são reduzidos pelo compilador a funções sintetizadas chamadas
 * no prólogo/epílogo do main (zero container, zero reflection).
 */
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
/**
 * Um caso de {@link SwitchExpr}: {@code case <value> -> <body>}. O {@code value}
 * é o literal/constante a casar ou um {@link PatternExpr} (pattern matching).
 * O {@code body} é UMA expressão (o valor produzido pelo caso) — não há
 * fall-through nem {@code break} (o switch é uma expressão).
 */
/**
 * Switch como expressão (SYN001): {@code switch (expr) { case A -> b; case T v -> c;
 * default -> d }}. Usável em qualquer posição de expressão ({@code var x = ...},
 * {@code return ...}, aninhado). Forma aditiva — o {@code SwitchStmt} (forma
 * statement com {@code :}) continua válido.
 */
/**
 * Query DSL tipada (ORM001 — nível 3): {@code User.query(db) { where age > 18;
 * orderBy name desc; limit 10 }}. O compilador baixa para
 * {@code db.query<User>(db, "SELECT ... WHERE ... ORDER BY ... LIMIT ?",
 * binds...)} — SQL montado em compile-time, valores como binds preparados
 * (sem string-concat de entrada).
 */
/**
 * SpawnStmt — runs the given call (or block) as a concurrent task.
 * The program waits for spawned tasks before exiting.
 */
/**
 * AssertStmt — assert(condition) or assert(condition, "message").
 * Throws "assertion failed" (or the given message) when the condition
 * is false. The failure exit code powers `kof test`.
 */
