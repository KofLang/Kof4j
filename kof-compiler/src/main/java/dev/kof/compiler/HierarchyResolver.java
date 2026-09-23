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
        if (isNamedAncestor(start.superClass())) {
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
                if (isNamedAncestor(cs.superClass())) {
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

    // #596: "Record" (implicit JVM superclass of every record) isn't
    // user-declared - queueing it as a widening candidate resolves to an
    // unqualified, unloadable ClassType instead of a shared interface.
    private static boolean isNamedAncestor(String superClass) {
        return superClass != null && !"Object".equals(superClass) && !"Record".equals(superClass);
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
}