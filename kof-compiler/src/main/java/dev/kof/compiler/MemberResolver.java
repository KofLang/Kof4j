package dev.kof.compiler;

import java.util.List;

/**
 * Resolução de membros e hierarquia extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Puro — recebe o estado necessário por parâmetro.
 */
public final class MemberResolver {

    private MemberResolver() {}

    /** BFS pela hierarquia (super + interfaces) em busca de um membro. */
    static SymbolTable.Symbol resolveInHierarchy(SemanticAnalyzer sa, String className, String memberName) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(className);
        visited.add(className);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            SymbolTable.ClassSymbol cs = sa.getClass(current);
            if (cs == null) continue;
            SymbolTable.Symbol s = cs.members().resolve(memberName);
            if (s != null) return s;
            enqueueAncestors(cs, visited, queue);
        }
        return null;
    }

    /**
     * #610b: coleta TODOS os métodos com {@code methodName} na hierarquia
     * (classe + supers + interfaces), para seleção por aridade/args. O
     * {@link #resolveInHierarchy} devolve o PRIMEIRO por nome e perde os
     * overloads que vivem em interfaces irmãs — `C().greet("mel")` com
     * `A.greet()`/`B.greet(String)` resolvia para o default de A (aridade
     * errada) e o JVM estourava em VerifyError. Devolve um único símbolo
     * quando só há um candidato, senão um MethodSet (select por aridade).
     */
    static SymbolTable.Symbol resolveMethodsInHierarchy(SemanticAnalyzer sa,
            String className, String methodName) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(className);
        visited.add(className);
        java.util.LinkedHashMap<String, SymbolTable.MethodSymbol> bySig = new java.util.LinkedHashMap<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            SymbolTable.ClassSymbol cs = sa.getClass(current);
            if (cs == null) continue;
            SymbolTable.Symbol s = cs.members().resolve(methodName);
            java.util.List<SymbolTable.MethodSymbol> methods = new java.util.ArrayList<>();
            if (s instanceof SymbolTable.MethodSymbol m) methods.add(m);
            else if (s instanceof SymbolTable.MethodSet set) methods.addAll(set.methods());
            for (SymbolTable.MethodSymbol m : methods) {
                bySig.putIfAbsent(m.parameterTypes().toString(), m);
            }
            enqueueAncestors(cs, visited, queue);
        }
        if (bySig.isEmpty()) return null;
        if (bySig.size() == 1) return bySig.values().iterator().next();
        return new SymbolTable.MethodSet(new java.util.ArrayList<>(bySig.values()));
    }

    /** BFS pela hierarquia buscando campo com prioridade sobre métodos de mesmo nome. */
    static SymbolTable.Symbol resolveFieldInHierarchy(SemanticAnalyzer sa, String className, String fieldName) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(className);
        visited.add(className);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            SymbolTable.ClassSymbol cs = sa.getClass(current);
            if (cs == null) continue;
            SymbolTable.FieldSymbol fs = cs.members().resolveField(fieldName);
            if (fs != null) return fs;
            enqueueAncestors(cs, visited, queue);
        }
        return resolveInHierarchy(sa, className, fieldName);
    }

    /**
     * Super + interfaces na fila do BFS, NORMALIZADOS (simpleOfStored): o
     * nome armazenado pode vir qualificado por import ("foo.bar.Shape"),
     * pontuado-externo ("android.app.Activity") ou com genéricos ("Box<T>")
     * — o registro é por nome simples, e o BFS cru quebrava a cadeia em
     * qualquer uma dessas formas (SEM025/SEM011 falsos em herança
     * cross-package → lowerField perdia o tipo do campo herdado).
     */
    static void enqueueAncestors(SymbolTable.ClassSymbol cs,
                                         java.util.Set<String> visited, java.util.Queue<String> queue) {
        String sup = HierarchyResolver.simpleOfStored(cs.superClass());
        if (sup != null && !sup.isEmpty() && !"Object".equals(sup) && visited.add(sup)) {
            queue.add(sup);
        }
        for (String iface : cs.interfaces()) {
            String simple = HierarchyResolver.simpleOfStored(iface);
            if (simple != null && !simple.isEmpty() && visited.add(simple)) {
                queue.add(simple);
            }
        }
    }

    static boolean isObjectMethod(String name, int argCount) {
        return switch (name) {
            case "hashCode", "toString", "getClass" -> argCount == 0;
            case "equals" -> argCount == 1;
            default -> false;
        };
    }

    static boolean isBuiltinTypeName(String name) {
        return switch (name) {
            // D-TROOL (19/09): `Troolean` e o nome de superficie do bool de
            // tres estados (Nullable(Bool) por baixo) — builtin, nao classe.
            case "String", "string", "Object", "Int", "int", "Long", "long",
                    "Bool", "bool", "boolean", "Boolean", "Char", "char",
                    "Byte", "byte", "Short", "short", "Float", "float",
                    "Double", "double", "Troolean", "troolean",
                    "void", "Void" -> true;
            default -> false;
        };
    }

    /**
     * Nome simples declarado em import vira tipo qualificado
     * ("import android.webkit.WebView" → ClassType("android.webkit","WebView")).
     * Sem isso, tipos de classes externas saem sem pacote e o descritor
     * JVM quebra.
     */
    static Type qualifyViaImports(CompilationUnitNode unit, String name) {
        return qualifyViaImports(unit, name, null);
    }

    /**
     * §134 residual: com um `ExternalClasspath` (--classpath/--deps), o
     * WILDCARD `import a.b.*` passa a qualificar o nome simples quando a
     * classe `a/b/<name>` REALMENTE existe num entry carregado. Sem o cp
     * (targets Native/JS — `externalClasspath == null`) o comportamento é
     * o antigo: wildcard nunca qualifica, cai em SEM011/PKG006. Aditivo:
     * só casa o que existe; nome inexistente segue o fluxo de erro normal.
     */
    static Type qualifyViaImports(CompilationUnitNode unit, String name,
                                  ExternalClasspath external) {
        if (name == null || name.contains(".") || name.contains("<") || name.endsWith("[]")) return null;
        if (unit == null) return null;
        for (String imp : unit.imports()) {
            if (!imp.endsWith("*") && imp.endsWith("." + name)) {
                String pkg = imp.substring(0, imp.lastIndexOf('.'));
                return new Type.ClassType(pkg, name, List.of());
            }
        }
        if (external != null) {
            for (String imp : unit.imports()) {
                if (imp.endsWith(".*")) {
                    String pkg = imp.substring(0, imp.length() - 2);
                    if (external.knows(pkg.replace('.', '/') + "/" + name)) {
                        return new Type.ClassType(pkg, name, List.of());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Nomes qualificados ("android.os.Bundle") precisam do pacote separado
     * do nome simples — senão o descritor JVM sai com pontos
     * (Landroid.os.Bundle;) e a classe não carrega.
     */
    static Type qualifiedType(Type type) {
        if (type instanceof Type.ClassType ct && !ct.name().contains("<")
                && ct.packageName().isEmpty()) {
            int lastDot = ct.name().lastIndexOf('.');
            if (lastDot > 0) {
                return new Type.ClassType(ct.name().substring(0, lastDot),
                        ct.name().substring(lastDot + 1), ct.typeArguments());
            }
        }
        return type;
    }

    /** Resolve um nome de tipo no escopo (type param → import → qualificado). */
    static Type resolveType(SemanticAnalyzer sa, String name, SymbolTable scope) {
        // SG-012: param de lambda sem anotação chega como null — Unknown
        // (a inferência contextual decide; nunca Object silencioso)
        if (name == null) return Type.UnknownType.UNKNOWN;
        SymbolTable.Symbol sym = scope != null ? scope.resolve(name) : null;
        if (sym instanceof SymbolTable.TypeParameterSymbol) return sym.type();
        Type viaImports = qualifyViaImports(sa.unit(), name, sa.externalTypes());
        if (viaImports != null) return viaImports;
        // qualifyDeep: recursa nos type-arguments — `List<NodeUI>` com
        // `import com.dev.NodeUI` precisa do pacote no ARG (senão o receiver
        // do `.get()` fica ClassType("","NodeUI") e o checkcast sai sem pacote
        // → NoClassDefFoundError). Idempotente; não toca builtin/enum/nome local.
        // §179: qualifyDeep mapeia o builtin kof.ui/kof.media quando nada mais
        // resolve o nome (preservando shadowing por import/classe do módulo).
        // §355 (rio da erasure): os type-params do ESCOPO (classe/record/
        // interface/função genérica) entram também nos ARGUMENTOS/COMPONENTES:
        // `List<T>` carregava ClassType("","T") no arg (→ `checkcast T`, #399/
        // #363) e `T[]` carregava o componente fantasma (→ campo `[LT;`, #295).
        Type resolved = CompilerTypes.qualifyDeep(qualifiedType(Type.of(name)), sa.unit(), sa);
        return TypeParams.rewrite(resolved, n ->
                scope != null && scope.resolve(n) instanceof SymbolTable.TypeParameterSymbol tps
                        ? tps.type() : null);
    }

    /**
     * §249: o nome simples declarado NÃO resolve para nenhum tipo conhecido?
     * Um tipo explícito de `VarDeclStmt` (`Foo x`, `s length`) caía em
     * {@code Type.of(name)} = {@code ClassType("", name)} sem diagnóstico algum
     * — o programa compilava e o statement virava lixo em silêncio (R6).
     * Espelha as isenções de SEM011 em {@code SemExpressionTyper} (case
     * IdentifierExpr): builtin, tipo UI/media declarado, coleção nua, classe/
     * record/enum do módulo, classe externa (--classpath), type-param do escopo
     * e import simples.
     * Conservador: só casa NOME SIMPLES (sem `.`/`<`/`?`/`[]`/`(`) — as formas
     * compostas (`List<Int>`, `Int[]`, `String?`, `(Int) -> Int`) são resolvidas
     * por {@code Type.of} e nunca caem aqui.
     */
    static boolean isUnresolvedSimpleType(SemanticAnalyzer sa, String name, SymbolTable scope) {
        if (name == null || name.isEmpty()) return false;
        if (name.contains(".") || name.contains("<") || name.contains("?")
                || name.contains("[") || name.contains("(")) return false;
        if (isBuiltinTypeName(name)) return false;
        // `List xs = listOf(...)`: o Type.of só mapeia a forma parametrizada.
        if (BuiltinTypes.baseTypeName(name) != null) return false;
        // UI/media (`Label`, `ImageData`) — §179.
        if (CompilerTypes.builtinDeclaredType(name) != null) return false;
        if (BuiltinTypes.isEnumName(name)) return false;
        if (scope != null && scope.resolve(name) instanceof SymbolTable.TypeParameterSymbol) return false;
        if (sa.allClasses().containsKey(name)) return false;
        if (CompilerTypes.unitDeclaresType(sa.unit(), name)) return false;
        if (qualifyViaImports(sa.unit(), name, sa.externalTypes()) != null) return false;
        // §268 (A): classe do `java.lang` (`Thread`, `Runnable`, `Object`…) —
        // resolve sem import pelo probe cacheado (mesma regra do toType).
        if (JavaLangProbe.qualifiedOrNull(name) != null) return false;
        return true;
    }

    /**
     * §251: um tipo DECLARADO pode ser composto — {@code List<Foo>}, {@code Foo?},
     * {@code Foo[]}, {@code (Int) -> Foo}. Valida os nomes simples recursivamente
     * contra {@link #isUnresolvedSimpleType}, exceto os type-params de
     * {@code typeParams} (classe/record/função genérica — `T`, `K`, `V`…).
     * Conservador com nomes qualificados (`java.lang.Foo`) e tipos-função: o
     * miolo é validado, o qualificado nunca é acusado.
     */
    static boolean declaredTypeUnresolved(SemanticAnalyzer sa, String declType,
                                          java.util.Set<String> typeParams) {
        if (declType == null) return false;
        String t = declType.trim();
        if (t.isEmpty() || "var".equals(t) || "val".equals(t) || "void".equals(t)) return false;
        // #374: o arrow só vale quando é do TOPO da string. Um ` -> ` aninhado
        // dentro de <...> (List<(Int) -> Int>) NÃO é o conectivo deste tipo —
        // antes caía aqui, o "retorno" virava `Int>` e o SEM011 acusava o tipo
        // inteiro (face do parâmetro/campo; a local usava outro caminho e vivia).
        int topArrow = topLevelFnArrow(t);
        if (topArrow >= 0) {
            int rp = topArrow - 1;
            String ps = t.startsWith("(") && rp > 0 ? t.substring(1, rp) : "";
            String ret = t.substring(topArrow + 4).trim();
            if (!ps.isEmpty()) {
                for (String p : splitTopLevelTypes(ps)) {
                    if (declaredTypeUnresolved(sa, p, typeParams)) return true;
                }
            }
            return declaredTypeUnresolved(sa, ret, typeParams);
        }
        // X5.4 (D-X5-SURFACE): projeção no sítio de uso — `out T`/`in T` como
        // type-argument. Valida o BOUND (a variância não é um tipo).
        if (t.startsWith("out ")) return declaredTypeUnresolved(sa, t.substring(4).trim(), typeParams);
        if (t.startsWith("in ")) return declaredTypeUnresolved(sa, t.substring(3).trim(), typeParams);
        if (t.endsWith("?")) return declaredTypeUnresolved(sa, t.substring(0, t.length() - 1), typeParams);
        if (t.endsWith("[]")) return declaredTypeUnresolved(sa, t.substring(0, t.length() - 2), typeParams);
        int lt = t.indexOf('<');
        if (lt > 0 && t.endsWith(">")) {
            if (declaredTypeUnresolved(sa, t.substring(0, lt), typeParams)) return true;
            String args = t.substring(lt + 1, t.length() - 1);
            for (String a : splitTopLevelTypes(args)) {
                if (declaredTypeUnresolved(sa, a, typeParams)) return true;
            }
            return false;
        }
        if (t.contains(".") || t.contains("(")) return false;
        if (typeParams != null && typeParams.contains(t)) return false;
        return isUnresolvedSimpleType(sa, t, null);
    }

    /** Índice do primeiro ` -> ` em profundidade zero (fora de `<>` e `()`), ou -1 (#374). */
    private static int topLevelFnArrow(String t) {
        int angle = 0, paren = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '<') angle++;
            else if (c == '>') angle--;
            else if (c == '(') paren++;
            else if (c == ')') paren--;
            else if (c == ' ' && angle == 0 && paren == 0 && t.startsWith(" -> ", i)) return i;
        }
        return -1;
    }

    /** Divide `A, B<C, D>, E` em topo-de-nível (respeita o aninhamento de `<...>`). */
    private static List<String> splitTopLevelTypes(String s) {
        List<String> out = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < s.length()) out.add(s.substring(start).trim());
        return out;
    }

    /** Constantes de um enum declarado na unit (vazio se não for enum). */
    static List<String> enumConstantsOf(CompilationUnitNode unit, String name) {
        if (name == null || unit == null) return List.of();
        for (AstNode d : unit.declarations()) {
            if (d instanceof EnumDeclarationNode en && en.name().equals(name)) {
                return en.constants();
            }
        }
        return List.of();
    }

    /** Nome da constante de enum referenciada por uma expressão, ou null. */
    static String enumConstantOfExpr(CompilationUnitNode unit, ExpressionNode e) {
        if (e instanceof FieldAccessExpr fa && fa.receiver() instanceof IdentifierExpr rid) {
            return enumConstantsOf(unit, rid.name()).contains(fa.fieldName()) ? fa.fieldName() : null;
        }
        if (e instanceof LiteralExpr l && l.kind() == ConcreteLiteralKind.STRING) {
            return l.value();
        }
        if (e instanceof IdentifierExpr ie) {
            // não-qualificado: Red quando algum enum declara Red
            if (unit != null) {
                for (AstNode d : unit.declarations()) {
                    if (d instanceof EnumDeclarationNode en && en.constants().contains(ie.name())) {
                        return ie.name();
                    }
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Nome do enum que declara a constante referenciada por {@code e}, ou null.
     * Complementa {@link #enumConstantOfExpr}: aquele devolve só a constante,
     * este o TIPO do enum — necessário para o tipo de `Color.RED` como
     * expressão (o exhaustiveness de switch-expr sobre enum depende dele).
     */
    static String enumNameOfConstant(CompilationUnitNode unit, ExpressionNode e) {
        if (unit == null) return null;
        if (e instanceof FieldAccessExpr fa && fa.receiver() instanceof IdentifierExpr rid) {
            for (AstNode d : unit.declarations()) {
                if (d instanceof EnumDeclarationNode en && en.name().equals(rid.name())
                        && en.constants().contains(fa.fieldName())) {
                    return en.name();
                }
            }
        }
        return null;
    }

    static boolean isBooleanType(Type t) {
        if (t == Type.PrimitiveType.BOOL) return true;
        if (t instanceof Type.ClassType ct) {
            String n = ct.name();
            String p = ct.packageName();
            return ("Boolean".equals(n) || "Bool".equals(n)) && ("java.lang".equals(p) || p.isEmpty());
        }
        return false;
    }

    static boolean isBooleanExhaustive(List<SwitchExprCase> cases) {
        boolean hasTrue = false;
        boolean hasFalse = false;
        for (SwitchExprCase sc : cases) {
            if (sc.value() instanceof LiteralExpr l && l.kind() == ConcreteLiteralKind.BOOLEAN) {
                if ("true".equals(l.value())) hasTrue = true;
                if ("false".equals(l.value())) hasFalse = true;
            }
        }
        return hasTrue && hasFalse;
    }

    /** X5.2: subtipos DIRETOS declarados de um tipo `sealed` (nome simples). */
    static List<String> sealedDirectSubtypes(SemanticAnalyzer sa, String sealedSimple) {
        List<String> subs = new java.util.ArrayList<>();
        for (SymbolTable.ClassSymbol cs : sa.allClasses().values()) {
            if (cs.name().equals(sealedSimple)) continue;
            boolean matches = cs.superClass() != null
                    && sealedSimple.equals(ClassShapeChecks.simpleName(cs.superClass()));
            if (!matches && cs.interfaces() != null) {
                for (String iface : cs.interfaces()) {
                    if (sealedSimple.equals(ClassShapeChecks.simpleName(iface))) {
                        matches = true;
                        break;
                    }
                }
            }
            if (matches) subs.add(cs.name());
        }
        return subs;
    }

    static void checkSwitchExprExhaustiveness(SemanticAnalyzer sa, SwitchExpr se, Type subjectType) {
        // X5.2 (D-X5-SURFACE): sujeito `sealed` — os subtipos DIRETOS são o
        // conjunto fechado; cobrir todos os casos de pattern dispensa o
        // `default`. Faltando caso = SEM081.
        if (subjectType instanceof Type.ClassType sct && sa.isSealedType(sct.name())) {
            java.util.Set<String> covered = new java.util.HashSet<>();
            for (SwitchExprCase sc : se.cases()) {
                if (sc.value() instanceof PatternExpr pe) {
                    covered.add(ClassShapeChecks.simpleName(pe.typeName()));
                }
            }
            List<String> subtypes = sealedDirectSubtypes(sa, sct.name());
            List<String> missing = subtypes.stream().filter(c -> !covered.contains(c)).toList();
            if (!missing.isEmpty()) {
                sa.reportError(se, "switch expression on sealed type '" + sct.name()
                        + "' does not cover: " + String.join(", ", missing)
                        + " (add a default or the missing cases)", "SEM081");
            }
            return;
        }
        if (isBooleanType(subjectType)) {
            if (!isBooleanExhaustive(se.cases())) {
                sa.reportError(se, "switch expression on Boolean does not cover all values (true and false)", "SEM032");
            }
            return;
        }
        // #445: `sa.unit()` é a unidade MESCLADA do módulo — enum de arquivo
        // importado resolve por nome simples; o gate de pacote vazio pulava a
        // exaustividade (SEM032) silenciosamente em switch-expr cross-file.
        if (subjectType instanceof Type.ClassType sct && sa.unit() != null) {
            java.util.Set<String> covered = new java.util.HashSet<>();
            for (SwitchExprCase sc : se.cases()) {
                String cn = enumConstantOfExpr(sa.unit(), sc.value());
                if (cn != null) covered.add(cn);
            }
            List<String> constants = enumConstantsOf(sa.unit(), sct.name());
            List<String> missing = constants.stream().filter(c -> !covered.contains(c)).toList();
            if (!missing.isEmpty()) {
                sa.reportError(se, "switch expression on '" + sct.name()
                        + "' does not cover: " + String.join(", ", missing)
                        + " (add a default or the missing cases)", "SEM032");
            }
        } else {
            sa.reportError(se, "switch expression requires 'default' (or enum exhaustiveness)", "SEM032");
        }
    }

    /**
     * #686 — exaustividade do `switch` como STATEMENT (não-expression). A
     * mesma checagem já existia para a forma-expression (SEM081 sealed,
     * SEM032 Bool/enum), mas a forma-statement não a invocava: um `case`
     * faltando sem `default` era aceito e virava no-op silencioso em runtime.
     * Reusa o MESMO predicado/erros; `hasDefault` distingue `default: }`.
     */
    static void checkSwitchStmtExhaustiveness(SemanticAnalyzer sa, SwitchStmt ss, Type subjectType) {
        if (subjectType instanceof Type.ClassType sct && sa.isSealedType(sct.name())) {
            java.util.Set<String> covered = new java.util.HashSet<>();
            for (SwitchCase sc : ss.cases()) {
                if (sc.value() instanceof PatternExpr pe) {
                    covered.add(ClassShapeChecks.simpleName(pe.typeName()));
                }
            }
            List<String> missing = sealedDirectSubtypes(sa, sct.name()).stream()
                    .filter(c -> !covered.contains(c)).toList();
            if (!(missing.isEmpty() || ss.hasDefault())) {
                sa.reportError(ss, "switch on sealed type '" + sct.name()
                        + "' does not cover: " + String.join(", ", missing)
                        + " (add a default or the missing cases)", "SEM081");
            }
            return;
        }
        if (isBooleanType(subjectType)) {
            boolean hasTrue = false;
            boolean hasFalse = false;
            for (SwitchCase sc : ss.cases()) {
                if (sc.value() instanceof LiteralExpr l && l.kind() == ConcreteLiteralKind.BOOLEAN) {
                    if ("true".equals(l.value())) hasTrue = true;
                    if ("false".equals(l.value())) hasFalse = true;
                }
            }
            if (!(ss.hasDefault() || (hasTrue && hasFalse))) {
                sa.reportError(ss, "switch on Boolean does not cover all values (true and false)", "SEM032");
            }
            return;
        }
        // ENUM não entra aqui: a exaustividade da forma-statement sobre enum já
        // existe no lowering (SEM031, SwitchStmtLowerer) e continua a autoridade
        // — duplicar aqui reportaria SEM032 e, pior, o erro de análise abortaria
        // o lowering (SEM031 sumiria). #686 cobre só sealed + Bool.
    }

    /**
     * SG-015 (#256): classe concreta que estende classe abstrata deve implementar
     * todos os métodos abstratos herdados da cadeia de superclasses.
     */
    static void checkAbstractClassImplementation(SemanticAnalyzer sa, ClassDeclarationNode cls) {
        if (sa.diagnostics() == null || cls.modifiers().contains("abstract")) return;
        String curSuper = cls.superClass();
        java.util.Set<String> checkedMethods = new java.util.HashSet<>();
        while (curSuper != null && !curSuper.isEmpty() && !"Object".equals(curSuper)) {
            String simpleSuper = curSuper.contains("/") ? curSuper.substring(curSuper.lastIndexOf("/") + 1) : curSuper;
            if (simpleSuper.contains("<")) simpleSuper = simpleSuper.substring(0, simpleSuper.indexOf("<"));
            SymbolTable.ClassSymbol superCs = sa.allClasses().get(simpleSuper);
            if (superCs == null) break;
            for (java.util.Map.Entry<String, SymbolTable.Symbol> e : superCs.members().localSymbols().entrySet()) {
                if (!(e.getValue() instanceof SymbolTable.MethodSymbol am)) continue;
                if ((am.accessFlags() & AccessFlags.ABSTRACT) == 0) continue;
                // §242: a chave é a ASSINATURA (nome + tipos de parâmetro), não
                // a aridade — duas sobrecargas de mesma aridade mas tipos
                // diferentes são abstratos DISTINTOS. Sem isto, o dedup
                // colapsava-as e um `Int run(Int)` podia "satisfazer"
                // `abstract Int run(String)` (AbstractMethodError em runtime).
                String methodKey = am.name() + TopLevelOverload.sigTag(am.parameterTypes());
                if (!checkedMethods.add(methodKey)) continue;
                SymbolTable.Symbol local = resolveInHierarchy(sa, cls.name(), am.name());
                boolean implemented = false;
                if (local instanceof SymbolTable.MethodSymbol lm) {
                    implemented = satisfiesAbstract(lm, am);
                } else if (local instanceof SymbolTable.MethodSet set) {
                    for (SymbolTable.MethodSymbol lm : set.methods()) {
                        if (satisfiesAbstract(lm, am)) {
                            implemented = true;
                            break;
                        }
                    }
                }
                if (!implemented) {
                    sa.diagnostics().error(cls,
                            "class '" + cls.name() + "' does not implement abstract method '"
                                    + am.name() + "()' from superclass '" + superCs.name() + "'",
                            "SEM043");
                }
            }
            curSuper = superCs.superClass();
        }
    }

    /**
     * §242: um método concreto satisfaz um abstrato quando NÃO é abstrato e a
     * assinatura casa — mesma aridade E mesmos tipos de parâmetro (o que a JVM
     * exige para o override). Antes o check comparava só a ARIDADE: um
     * `Int run(Int)` "satisfazia" `abstract Int run(String)` e o
     * `AbstractMethodError` voltava em runtime.
     */
    private static boolean satisfiesAbstract(SymbolTable.MethodSymbol concrete,
                                             SymbolTable.MethodSymbol abstractMethod) {
        if ((concrete.accessFlags() & AccessFlags.ABSTRACT) != 0) return false;
        if (concrete.parameterTypes().size() != abstractMethod.parameterTypes().size()) return false;
        return TopLevelOverload.sigTag(concrete.parameterTypes())
                .equals(TopLevelOverload.sigTag(abstractMethod.parameterTypes()));
    }
}
