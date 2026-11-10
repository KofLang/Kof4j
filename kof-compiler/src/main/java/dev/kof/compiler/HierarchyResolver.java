package dev.kof.compiler;

/**
 * Resolução de hierarquia de classes via SemanticAnalyzer (helpers do
 * CompilerDriver). Puro — recebe o analyzer por parâmetro.
 */
public final class HierarchyResolver {

    private HierarchyResolver() {}

    static SymbolTable.Symbol resolveFromSemantic(String name, SemanticAnalyzer semanticAnalyzer) {
        if (semanticAnalyzer == null) return null;
        for (var entry : semanticAnalyzer.allClasses().entrySet()) {
            SymbolTable.ClassSymbol cs = entry.getValue();
            SymbolTable.Symbol s = cs.members().resolve(name);
            if (s != null) return s;
        }
        return null;
    }

    /**
     * §782: a simple name read where there is no {@code this} (a STATIC
     * method) is looked up in the class being lowered first, like the
     * emitter does ({@code ExpressionLowerer}); only then the module-wide
     * scan. Without it {@code seen.set(x)} in a static method of
     * {@code ResponseHeaders} could be typed by an unrelated
     * {@code List<String> seen} field of another class (HashMap order),
     * failing compilation with SEM055.
     */
    static SymbolTable.Symbol resolveInOwnerOrModule(String name, String owner,
                                                     SemanticAnalyzer sa) {
        if (sa == null) return null;
        if (owner != null && !owner.isEmpty()) {
            SymbolTable.ClassSymbol cs = sa.findQualifiedClass(owner.replace('/', '.'));
            if (cs == null) cs = sa.getClass(owner.substring(owner.lastIndexOf('/') + 1));
            if (cs != null) {
                SymbolTable.Symbol f = MemberResolver.resolveFieldInHierarchy(sa, cs.name(), name);
                if (f != null) return f;
            }
        }
        return resolveFromSemantic(name, sa);
    }

    static SymbolTable.Symbol resolveFieldInHierarchy(String className, String fieldName,
                                                      SemanticAnalyzer semanticAnalyzer) {
        if (semanticAnalyzer == null) return null;
        return MemberResolver.resolveFieldInHierarchy(semanticAnalyzer, className, fieldName);
    }

    static String findSuperClass(String internalName, SemanticAnalyzer semanticAnalyzer) {
        if (semanticAnalyzer == null) return null;
        String simpleName = internalName.substring(internalName.lastIndexOf('/') + 1);
        SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(simpleName);
        if (cs == null) return null;
        String superName = cs.superClass();
        if (superName == null || superName.isEmpty()) return null;
        String bare = stripGenerics(superName);
        String simple = simpleOfStored(bare);
        if (simple.isEmpty() || "Object".equals(simple)) return null;
        SymbolTable.ClassSymbol superCs = semanticAnalyzer.getClass(simple);
        if (superCs != null) return superCs.internalName();
        return bare.replace('.', '/');
    }

    /**
     * Nome como ARMAZENADO na symbol table de super/interface: o
     * SymbolTableBuilder qualifica via import explícito ("foo.bar.Shape"),
     * wildcards deixam o nome simples ("Shape"), genéricos chegam grudados
     * ("Box<T>") e classes externas vêm pontuadas ("android.app.Activity").
     * O registro (knownClasses) é CHAVEADO pelo nome simples — qualquer BFS
     * pela hierarquia precisa normalizar antes de consultar. Simples
     * ("foo.bar.Shape<T>" → "Shape"); null só para null.
     */
    static String simpleOfStored(String stored) {
        if (stored == null) return null;
        String bare = stripGenerics(stored);
        int cut = Math.max(bare.lastIndexOf('.'), bare.lastIndexOf('/'));
        return cut >= 0 ? bare.substring(cut + 1) : bare;
    }

    /**
     * §779: internal name of a declared {@code implements} entry (also an
     * interface's own {@code extends}), registry-first like the super (§308):
     * a module interface declared in a package resolves to its qualified
     * internal name ({@code kernel/p/PIface}); emitting the bare simple name
     * gave {@code NoClassDefFoundError: PIface} at load. Anything the module
     * does not declare keeps the import / {@code java.lang} resolution.
     */
    static String canonicalInterfaceInternal(CompilerDriver driver, String declared) {
        SemanticAnalyzer sa = driver.semanticAnalyzer;
        SymbolTable.ClassSymbol cs = sa == null ? null : sa.getClass(simpleOfStored(declared));
        if (cs != null) return cs.internalName();
        return CompilerAnnotations.externalOrLocalInternalName(driver, HeritageQualifier.qualify(
                driver.currentUnit, sa, CompilerClassLowering.eraseTypeArgs(declared)));
    }

    /**
     * Internal name canônico de um nome de super/interface armazenado:
     * registro local primeiro (módulo mesclado é a verdade — §308), senão a
     * forma pontuada→barras do próprio nome. Null para null/`Object`.
     */
    static String canonicalSuperInternal(String stored, SemanticAnalyzer sa) {
        if (stored == null || stored.isEmpty()) return null;
        String bare = stripGenerics(stored);
        String simple = simpleOfStored(bare);
        if (simple.isEmpty()) return null;
        if (sa != null) {
            SymbolTable.ClassSymbol cs = sa.getClass(simple);
            if (cs != null) return cs.internalName();
        }
        // §268 (A): `extends Object` explícito gravava o super cru ("Object" →
        // internal name inválido, CNFE no load). O sentinela Object vira o
        // java/lang/Object real; um `class Object` do módulo já venceu acima.
        if ("Object".equals(simple)) return "java/lang/Object";
        return bare.replace('.', '/');
    }

    /** Registry-first: internal name canônico da SUPERCLASSE declarada da
     *  classe simples `simpleName` (null se ausente/Object/nada conhecido). */
    static String canonicalSuperOf(SemanticAnalyzer sa, String simpleName) {
        if (sa == null || simpleName == null) return null;
        SymbolTable.ClassSymbol cs = sa.getClass(simpleName);
        return cs == null ? null : canonicalSuperInternal(cs.superClass(), sa);
    }

    /**
     * Tipo da SUPERCLASSE da classe `internalName` (registry-first). Null
     * quando não há superclasse conhecida (Object / ausente). Fonte única do
     * typing de `super` (READ, WRITE e análise semântica) — sem ela o
     * receiver `super` chegava UNKNOWN ao lowerField e o valor do campo
     * herdado era vertido num temporário Object.
     */
    static Type superTypeOf(SemanticAnalyzer sa, String internalName) {
        String sup = findSuperClass(internalName, sa);
        if (sup == null) return null;
        return CompilerTypes.ownerTypeFromInternal(sup.replace('.', '/'), sa);
    }

    /**
     * True quando a cadeia de superclasses a partir de internalName é
     * inteiramente conhecida pelo SemanticAnalyzer (nenhuma classe externa
     * no caminho). Só nesse caso "método não resolvido" prova inexistência.
     */
    static boolean hierarchyFullyKnown(String internalName, SemanticAnalyzer semanticAnalyzer) {
        if (semanticAnalyzer == null) return false;
        String cur = internalName;
        int hops = 0;
        while (cur != null && !"java/lang/Object".equals(cur) && hops++ < 32) {
            String simple = cur.substring(cur.lastIndexOf('/') + 1);
            SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(simple);
            if (cs == null) return false;
            String sup = cs.superClass();
            if (sup == null || sup.isEmpty() || "Object".equals(sup)) return true;
            if (sup.contains(".")) {
                cur = sup.replace('.', '/');
            } else {
                SymbolTable.ClassSymbol supCs = semanticAnalyzer.getClass(sup);
                cur = supCs != null ? supCs.internalName() : sup;
            }
        }
        return true;
    }

    static String superSimpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    /**
     * Calcula o tipo comum mais específico (LCA) entre dois tipos para if-expression / branches.
     */
    static Type commonSupertype(SemanticAnalyzer sa, Type t1, Type t2) {
        if (t1 == null || t1 instanceof Type.UnknownType) return t2;
        if (t2 == null || t2 instanceof Type.UnknownType) return t1;
        if (t1.equals(t2)) return t1;
        if (t1 instanceof Type.ClassType ct1 && t2 instanceof Type.ClassType ct2) {
            if (sa != null) {
                if (TypeChecker.isAssignable(sa, ct1, ct2)) return ct2;
                if (TypeChecker.isAssignable(sa, ct2, ct1)) return ct1;
                // #360: o ancestral comum pode ser uma INTERFACE — a cadeia
                // antiga só subia SUPERCLASSES de ct1 e perdia o caso
                // Dog/Cat->Animal (interfaces). Fecha o fecho (supers +
                // interfaces, BFS = do mais específico) de cada lado e devolve
                // o primeiro ancestral que o OUTRO lado também satisfaz.
                Type a = firstCommonAncestor(sa, ct1, ct2);
                if (a != null) return a;
                Type b = firstCommonAncestor(sa, ct2, ct1);
                if (b != null) return b;
            }
        }
        return new Type.ClassType("java.lang", "Object", java.util.List.of());
    }

    /** #360 — BFS pelo fecho de `base` (sem o próprio base); primeiro nó ao
     *  qual `other` é atribuível vence. Null = sem ancestral comum nomeado. */
    private static Type firstCommonAncestor(SemanticAnalyzer sa,
            Type.ClassType base, Type.ClassType other) {
        String from = base.name();
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        visited.add(from);
        SymbolTable.ClassSymbol start = sa.getClass(from);
        if (start == null) return null;
        if (start.superClass() != null && !isImplicitAncestor(start.superClass())) {
            queue.add(simpleOfStored(start.superClass()));
        }
        for (String iface : start.interfaces()) queue.add(simpleOfStored(iface));
        int hops = 0;
        while (!queue.isEmpty() && hops++ < 64) {
            String cur = queue.poll();
            if (cur.isEmpty() || !visited.add(cur)) continue;
            Type curType = ancestorType(sa, cur);
            if (TypeChecker.isAssignable(sa, other, curType)) return curType;
            SymbolTable.ClassSymbol cs = sa.getClass(cur);
            if (cs != null) {
                if (cs.superClass() != null && !isImplicitAncestor(cs.superClass())) {
                    queue.add(simpleOfStored(cs.superClass()));
                }
                for (String iface : cs.interfaces()) queue.add(simpleOfStored(iface));
            }
        }
        return null;
    }

    private static Type ancestorType(SemanticAnalyzer sa, String simpleName) {
        SymbolTable.ClassSymbol cs = sa.getClass(simpleName);
        return cs != null ? cs.type() : new Type.ClassType("", simpleName, java.util.List.of());
    }

    /** Ancestrais implícitos do JVM que não são tipos Kof e não entram no
     *  widening: `Object` (todo tipo) e `Record` (estrutural de `record`,
     *  §596) — enfileirá-los ANTES das interfaces fazia o BFS resolver o
     *  ancestral comum como o nome nu `Record` (checkcast `Record` →
     *  NoClassDefFoundError) em vez da interface compartilhada. */
    private static boolean isImplicitAncestor(String stored) {
        return "Object".equals(stored) || "Record".equals(stored);
    }

    private static String stripGenerics(String declared) {
        int lt = declared.indexOf('<');
        return lt < 0 ? declared : declared.substring(0, lt).trim();
    }

    /**
     * #360 — widening só quando existe um ancestral comum REAL (interface ou
     * superclasse nomeada); sem ele (Dog+String) devolve o elemento atual —
     * mantém o first-wins de hoje, r1: nada que roda hoje deixa de rodar.
     */
    static Type widenToCommonSupertype(SemanticAnalyzer sa, Type.ClassType elem, Type.ClassType next) {
        if (elem.equals(next)) return elem;
        if (sa == null) return elem;
        Type c = commonSupertype(sa, elem, next);
        if (c instanceof Type.ClassType cc
                && !("java.lang".equals(cc.packageName()) && "Object".equals(cc.name()))) {
            return cc;
        }
        return elem;
    }

    /**
     * #819 (1.0-blocks): join de tipos de ramos (switch-expression) pelo supertipo
     * comum REAL. Devolve o supertipo nomeado quando existe um (dobra
     * {@link #commonSupertype} pelos ramos); null quando NÃO há ancestral comum
     * nomeado (primitivos heterogêneos, ramos sem parentesco) — o caller mantém o
     * fallback honesto (java.lang.Object / first-wins). Sem este passo o switch
     * caía em Object e o `areturn` do método (declarando o supertipo) recebia
     * Object → VerifyError no load.
     */
    static Type joinBranchTypes(SemanticAnalyzer sa, java.util.List<Type> branches) {
        if (sa == null || branches == null || branches.isEmpty()) return null;
        Type joined = branches.get(0);
        for (int i = 1; i < branches.size(); i++) {
            joined = commonSupertype(sa, joined, branches.get(i));
        }
        if (joined instanceof Type.ClassType cc
                && !("java.lang".equals(cc.packageName()) && "Object".equals(cc.name()))) {
            return cc;
        }
        return null;
    }

    /**
     * §284-map: `T` + ramo literal `null` → `T?` (nunca toca nullable/objeto).
     * (Movido de ExpressionTyper para manter a classe abaixo do teto de 500/600.)
     */
    static Type nullableIfNullBranch(Type joined, ExpressionNode... branches) {
        if (!(joined instanceof Type.PrimitiveType pt) || Type.isVoid(pt)) return joined;
        for (ExpressionNode b : branches) {
            if (b instanceof LiteralExpr lit && lit.kind() == ConcreteLiteralKind.NULL) {
                return new Type.NullableType(pt);
            }
        }
        return joined;
    }
}