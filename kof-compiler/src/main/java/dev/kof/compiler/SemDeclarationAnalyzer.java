package dev.kof.compiler;

import java.util.List;

/**
 * REFACTOR-500 (split do SemanticAnalyzer, 19/09 — gate ≤500): análise de
 * DECLARAÇÕES de tipo (entity/record/interface) e de FUNÇÕES top-level.
 * Movimento behavior-free (freeze regra 3): o estado continua no
 * {@link SemanticAnalyzer} (getters read-only + mutadores da fase 6);
 * esta classe só ORQUESTRA a análise. Mesma família de satellites:
 * {@link StatementAnalyzer}, {@link SemExpressionTyper},
 * {@link SymbolTableBuilder}, {@link ImplementationChecker}.
 */
final class SemDeclarationAnalyzer {

    private SemDeclarationAnalyzer() {
    }

    static void analyzeEntity(SemanticAnalyzer sa, EntityDeclarationNode ent) {
        List<RecordComponentNode> components = new java.util.ArrayList<>();
        for (EntityFieldNode f : ent.fields()) {
            components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
        }
        analyzeRecord(sa, new RecordDeclarationNode(ent.position(), ent.name(), ent.modifiers(),
                null, List.of(), components, List.of()));
    }

    static void analyzeRecord(SemanticAnalyzer sa, RecordDeclarationNode rec) {
        String prevClass = sa.currentClassName();
        sa.setCurrentClassName(rec.name());
        SymbolTable classScope = sa.classMemberScopes().get(rec.name());
        if (classScope == null) {
            SymbolTableBuilder.defineRecordMembers(sa, rec);
            classScope = sa.classMemberScopes().get(rec.name());
        }
        SymbolTable prevScope = sa.currentScope();
        sa.setCurrentScope(classScope);
        for (RecordComponentNode comp : rec.components()) {
            if (comp.initializer() != null) {
                sa.inferType(comp.initializer(), classScope);
            }
        }
        for (AstNode member : rec.members()) {
            if (member instanceof FieldDeclarationNode field && field.initializer() != null) {
                sa.inferType(field.initializer(), classScope);
            }
        }
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            sa.beginExpressionTypeGroup();
            for (AstNode member : rec.members()) {
                if (member instanceof MethodDeclarationNode method) {
                    SymbolTable.MethodSymbol ms = sa.methodSymbols().get(method);
                    Type before = ms != null ? ms.returnType() : null;
                    sa.analyzeMethodBody(method);
                    Type after = ms != null ? ms.returnType() : null;
                    if (before != null && after != null && !before.equals(after)) {
                        changed = true;
                    }
                }
            }
            if (!changed) {
                sa.discardExpressionTypeGroup();
                break;
            }
            sa.clearExpressionTypes();
        }
        sa.setCurrentScope(prevScope);
        sa.setCurrentClassName(prevClass);
    }

    static void analyzeInterface(SemanticAnalyzer sa, InterfaceDeclarationNode iface) {
        String prevClass = sa.currentClassName();
        sa.setCurrentClassName(iface.name());
        SymbolTable classScope = sa.classMemberScopes().get(iface.name());
        if (classScope == null) {
            SymbolTableBuilder.defineInterfaceMembers(sa, iface);
            classScope = sa.classMemberScopes().get(iface.name());
        }
        SymbolTable prevScope = sa.currentScope();
        sa.setCurrentScope(classScope);
        // #321 — `interface J extends Base` onde Base é CLASSE: o JVM escreve
        // o supertype na interface como super_class → IncompatibleClassChangeError
        // no load, silencioso no compile (R6/Q7). Interfaces só estendem
        // interfaces; o alvo precisa existir E ser interface (nome
        // desconhecido: SEM011 de resolveType cuida — nao duplicar aqui).
        for (String parent : iface.interfaces()) {
            if (sa.diagnostics() == null) break;
            String base = parent.contains("<") ? parent.substring(0, parent.indexOf('<')).trim() : parent;
            if (sa.knowsClass(base) && !sa.interfaceNames().contains(base)) {
                sa.reportError(iface, "interface '" + iface.name() + "' cannot extend class '"
                        + base + "' (interfaces may only extend interfaces)", "SEM064");
            }
        }
        // X5.1 (D-X5-SURFACE): interface que estende interface `sealed` só na
        // mesma unidade de compilação (SEM080).
        SealedTypeChecks.checkSubtype(sa, iface.position(), iface.name(), null, iface.interfaces());
        // X5.3 (D-TYPE-VARIANCE): restrição de posição de `out`/`in`.
        VarianceChecks.checkInterface(sa, iface);
        // #213: corpos de métodos default de interface precisam ser analisados
        // (resolução de `greet(name)` como this.greet, tipos de retorno) — antes
        // eram ignorados e a chamada nua virava função hoisted.
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            sa.beginExpressionTypeGroup();
            for (AstNode member : iface.members()) {
                if (member instanceof MethodDeclarationNode method
                        && method.body() != null && !method.body().isEmpty()) {
                    SymbolTable.MethodSymbol ms = sa.methodSymbols().get(method);
                    Type before = ms != null ? ms.returnType() : null;
                    sa.analyzeMethodBody(method);
                    Type after = ms != null ? ms.returnType() : null;
                    if (before != null && after != null && !before.equals(after)) changed = true;
                }
            }
            if (!changed) {
                sa.discardExpressionTypeGroup();
                break;
            }
            sa.clearExpressionTypes();
        }
        sa.setCurrentScope(prevScope);
        sa.setCurrentClassName(prevClass);
    }

    /**
     * SG-019 (SEM045): a cláusula `throw X, Y` não é checked-exception (exceções
     * são Strings em Kof), mas os nomes devem ser TIPOS conhecidos — classe do
     * módulo, interface, ou externa via import. Antes era capturado pelo parser
     * e nunca validado (decorativo).
     */
    static void checkThrowsClause(SemanticAnalyzer sa, List<String> thrown, String owner) {
        if (sa.diagnostics() == null) return;
        for (String name : thrown) {
            if ("String".equals(name) || Type.isPrimitive(Type.of(name))) continue;
            if (sa.knowsClass(name) || sa.interfaceNames().contains(name)) continue;
            Type viaImports = MemberResolver.qualifyViaImports(sa.unit(), name);
            if (viaImports != null) continue;
            sa.diagnostics().error("", 0, 0, 0,
                    "throw clause of " + owner + " references unknown type '" + name + "'",
                    "SEM045");
        }
    }

    static void analyzeFunction(SemanticAnalyzer sa, FunctionDeclarationNode func) {
        // SG-018 (SEM044): o entry point é SÓ `main()` — sem tipo de retorno,
        // sem modifiers (o IR já emite public static void — CompilerFunctionLowering).
        if ("main".equals(func.name())) {
            String rt = func.returnType();
            boolean badReturnType = rt != null && !"void".equals(rt)
                    && !"var".equals(rt) && !"val".equals(rt);
            if (!func.modifiers().isEmpty() && sa.diagnostics() != null) {
                sa.diagnostics().error(func.position().file(), func.position().line(),
                        func.position().column(), 0,
                        "main() must be declared without modifiers: 'main() { ... }' (found "
                                + func.modifiers() + ")",
                        "SEM044");
            }
            if (badReturnType && sa.diagnostics() != null) {
                sa.diagnostics().error(func.position().file(), func.position().line(),
                        func.position().column(), 0,
                        "main() must have no return type: 'main() { ... }' (found '"
                                + rt + " main(...)')",
                        "SEM044");
            }
        }
        String prevFunction = sa.currentFunctionName();
        sa.setCurrentFunctionName(func.name());
        checkThrowsClause(sa, func.thrownExceptions(), "function '" + func.name() + "'");
        SymbolTable funcScope = sa.currentScope().enterScope();
        for (String tp : func.typeParameters()) {
            funcScope.define(TypeParams.symbol(tp, sa)); // §355
        }
        Type returnType = sa.resolveType(func.returnType(), funcScope);
        // #333 (medido no tip): funcao top-level declarada VOID ou SEM TIPO com
        // `return <valor>` = FunctionLowering emite descriptor inferido (()I)
        // enquanto o symbol/call-site esta ()V → NoSuchMethodError silencioso em
        // runtime (R6). `main() { return 5 }` idem (JVM exige main()V). Metodos
        // ficam de fora: la a reinferencia bug-26 atualiza os DOIS lados (§130).
        boolean funcValueReturnRejected = Type.isVoid(returnType) || Type.isUnknown(returnType);
        int idx = 0;
        for (FormalParameterNode param : func.parameters()) {
            // §355/#368: parâmetro do tipo de um type-param (`item: T` em
            // `process<T>(item: T)`) virava ClassType("","T") fantasma — o
            // dono do invoke saía `""` (ClassFormatError). Resolve primeiro
            // como type-variable (com bound), senão o Type.of de sempre.
            Type paramType = TypeParams.variable(param.type(), func.typeParameters(),
                    sa.unit(), sa);
            if (paramType == null) paramType = Type.of(param.type());
            funcScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        SymbolTable prevScope = sa.currentScope();
        sa.setCurrentScope(funcScope);
        // bug 26: função top-level com tipo não-void pode terminar sem return
        ReturnPathAnalyzer.check(sa, func.body(), returnType, func.position(),
                "function '" + func.name() + "'");
        boolean prevExplicitVoid = sa.currentExplicitVoid;
        sa.currentExplicitVoid = funcValueReturnRejected;
        StatementAnalyzer.analyzeBody(sa, func.body(), funcScope, returnType);
        sa.currentExplicitVoid = prevExplicitVoid;
        sa.setCurrentScope(prevScope);
        sa.setCurrentFunctionName(prevFunction);
    }
}
