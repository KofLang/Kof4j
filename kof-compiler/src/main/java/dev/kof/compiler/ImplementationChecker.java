package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REFACTOR-500 (espelha {@link DeclaredTypeChecker}): verificadores de
 * CONTRATO de classe — implementacao de interfaces (SG-015/SEM043 + as
 * faces #322/#311: deferral em abstratas e obrigacao transitiva pelo pai
 * abstrato) e compatibilidade de retorno em override (#326/SEM059) —
 * extraidos do SemanticAnalyzer para manter o arquivo quente longe do
 * limite de 500 linhas (regra 7; o gate CI trava >= 600).
 *
 * <p>Puro read-only sobre o estado ja resolvido do analyzer; toda a
 * mutacao continua no dono. Roda DEPOIS do fixpoint de corpo de
 * `analyzeClass` porque o retorno pode ser refinado na analise de corpo.
 */
final class ImplementationChecker {

    private ImplementationChecker() {}

    static void checkInterfaceImplementation(SemanticAnalyzer sa,
            ClassDeclarationNode cls, SymbolTable classScope) {
        DiagnosticCollector diagnostics = sa.diagnostics();
        if (diagnostics == null) return;
        Map<String, SymbolTable.ClassSymbol> knownClasses = sa.allClasses();
        java.util.Set<String> interfaceNames = sa.interfaceNames();
        java.util.Set<String> abstractClasses = sa.abstractClasses();
        // #322: classe ABSTRATA que implementa uma interface pode DEIXAR
        // métodos da interface sem implementação (o contrato é o mesmo do
        // Java/JLS 8.4.8.1): quem deve implementá-los é a subclasse CONCRETA.
        // A obrigação de NÃO ficar em silêncio é transitiva: para classe
        // concreta, cobramos também as interfaces herdadas dos pais
        // ABSTRATOS (senão o deferral viraria um AbstractMethodError mudo —
        // mesma classe de bug da #311, vetada pela regra 6 do freeze/R6).
        boolean clsAbstract = cls.modifiers().contains("abstract");
        List<ClassDeclIfaces> obligations = new ArrayList<>();
        obligations.add(new ClassDeclIfaces(cls.interfaces(), null));
        if (!clsAbstract) {
            // sobe a cadeia de super-ABSTRATOS (mesma normalização do
            // checkAbstractClassImplementation: nome simples, sem generics)
            String curSuper = cls.superClass();
            java.util.Set<String> seen = new java.util.HashSet<>();
            seen.add(cls.name());
            while (curSuper != null && !curSuper.isEmpty() && !"Object".equals(curSuper)) {
                String simple = curSuper.contains("/")
                        ? curSuper.substring(curSuper.lastIndexOf("/") + 1) : curSuper;
                if (simple.contains("<")) simple = simple.substring(0, simple.indexOf("<"));
                if (!seen.add(simple)) break;
                SymbolTable.ClassSymbol superSym = knownClasses.get(simple);
                if (superSym == null) break;
                if (!abstractClasses.contains(simple)) break;
                obligations.add(new ClassDeclIfaces(superSym.interfaces(), simple));
                curSuper = superSym.superClass();
            }
        }
        for (ClassDeclIfaces ob : obligations) {
            for (String ifaceName : ob.ifaces()) {
                SymbolTable.ClassSymbol ifaceSym = knownClasses.get(ifaceName);
                if (ifaceSym == null || !interfaceNames.contains(ifaceName)) continue;
                for (Map.Entry<String, SymbolTable.Symbol> e
                        : ifaceSym.members().localSymbols().entrySet()) {
                    if (!(e.getValue() instanceof SymbolTable.MethodSymbol im)) continue;
                    // #213: métodos default (com corpo) já têm implementação na
                    // interface — a classe implementadora não precisa declará-los.
                    if ((im.accessFlags() & AccessFlags.ABSTRACT) == 0) continue;
                    SymbolTable.Symbol local = clsAbstract
                            ? classScope.resolve(im.name())
                            : MemberResolver.resolveInHierarchy(sa, cls.name(), im.name());
                    // #322: só um método CONCRETO satisfaz a obrigação. O
                    // walk transitivo via resolveInHierarchy pode devolver a
                    // PRÓPRIA declaração abstrata da interface (ou um abstract
                    // de um super abstrato) — isso NÃO implementa nada.
                    boolean implemented = false;
                    if (local instanceof SymbolTable.MethodSymbol cm) {
                        if ((cm.accessFlags() & AccessFlags.ABSTRACT) == 0) {
                            implemented = true;
                            if (cm.parameterTypes().size() != im.parameterTypes().size()) {
                                diagnostics.error(cls,
                                        "method '" + im.name() + "' of interface '" + ifaceName
                                                + "' expects " + im.parameterTypes().size()
                                                + " parameter(s) but implementation has "
                                                + cm.parameterTypes().size(),
                                        "SEM043");
                            }
                        }
                    } else if (local instanceof SymbolTable.MethodSet ms) {
                        for (SymbolTable.MethodSymbol cm : ms.methods()) {
                            if ((cm.accessFlags() & AccessFlags.ABSTRACT) == 0) {
                                implemented = true;
                                break;
                            }
                        }
                    }
                    if (!implemented && !clsAbstract) {
                        diagnostics.error(cls,
                                "class '" + cls.name() + "' does not implement method '" + im.name()
                                        + "' of interface '" + ifaceName + "'"
                                        + (ob.via() != null ? " inherited via '" + ob.via() + "'" : ""),
                                "SEM043");
                    }
                }
            }
        }
    }

    /** #322: par (interfaces-declaradas, pai-abstrato-que-as-declarou). */
    private record ClassDeclIfaces(List<String> ifaces, String via) {}

    /**
     * #603: duas interfaces SEM relação de ancestralidade entre si, ambas
     * declarando um `default` de MESMO nome+aridade — a classe que as
     * implementa e não sobrescreve o método fica ambígua (qual default
     * vence?). O checker de #322/SEM043 acima passa batido por design
     * (`if (!ABSTRACT) continue` — default já tem corpo, "não precisa
     * declarar"), então esse diamond nunca era pego em compile-time: o JVM
     * só recusa no LOAD da classe, `IncompatibleClassChangeError: Conflicting
     * default methods` (mesmo contrato do javac, JLS 9.4.1.3 — mas javac
     * pega isso no CHECK, nunca deixa a classe carregar). Rejeitar cedo,
     * como o resto do freeze exige (R6: nunca silencioso até o load).
     */
    static void checkDefaultMethodDiamond(SemanticAnalyzer sa,
            ClassDeclarationNode cls, SymbolTable classScope) {
        DiagnosticCollector diagnostics = sa.diagnostics();
        if (diagnostics == null) return;
        Map<String, SymbolTable.ClassSymbol> knownClasses = sa.allClasses();
        java.util.Set<String> interfaceNames = sa.interfaceNames();
        List<String> directIfaces = cls.interfaces();
        for (int i = 0; i < directIfaces.size(); i++) {
            for (int j = i + 1; j < directIfaces.size(); j++) {
                String nameA = bareName(directIfaces.get(i));
                String nameB = bareName(directIfaces.get(j));
                if (nameA.equals(nameB)) continue;
                SymbolTable.ClassSymbol symA = knownClasses.get(nameA);
                SymbolTable.ClassSymbol symB = knownClasses.get(nameB);
                if (symA == null || symB == null) continue;
                if (!interfaceNames.contains(nameA) || !interfaceNames.contains(nameB)) continue;
                // Relacionadas (uma extends a outra) — o default mais
                // específico vence, sem ambiguidade (JLS 9.4.1.3 caso 1).
                if (isInterfaceAncestor(knownClasses, nameA, nameB)
                        || isInterfaceAncestor(knownClasses, nameB, nameA)) continue;
                for (Map.Entry<String, SymbolTable.Symbol> e : symA.members().localSymbols().entrySet()) {
                    if (!(e.getValue() instanceof SymbolTable.MethodSymbol dmA)) continue;
                    if ((dmA.accessFlags() & AccessFlags.ABSTRACT) != 0) continue; // só default
                    SymbolTable.Symbol other = symB.members().resolve(dmA.name());
                    List<SymbolTable.MethodSymbol> candidatesB = new ArrayList<>();
                    if (other instanceof SymbolTable.MethodSymbol ms) candidatesB.add(ms);
                    else if (other instanceof SymbolTable.MethodSet mset) candidatesB.addAll(mset.methods());
                    for (SymbolTable.MethodSymbol dmB : candidatesB) {
                        if ((dmB.accessFlags() & AccessFlags.ABSTRACT) != 0) continue; // só default
                        if (dmA.parameterTypes().size() != dmB.parameterTypes().size()) continue;
                        if (classOwnMethodOverrides(classScope, dmA.name(), dmA.parameterTypes().size())) continue;
                        diagnostics.error(cls,
                                "class '" + cls.name() + "' inherits unrelated default methods '"
                                        + nameA + "." + dmA.name() + "' and '" + nameB + "." + dmB.name()
                                        + "' with the same signature — override '" + dmA.name()
                                        + "' explicitly to resolve the conflict",
                                "SEM101");
                    }
                }
            }
        }
    }

    private static String bareName(String ifaceRef) {
        String s = ifaceRef.contains("/") ? ifaceRef.substring(ifaceRef.lastIndexOf('/') + 1) : ifaceRef;
        return s.contains("<") ? s.substring(0, s.indexOf('<')) : s;
    }

    private static boolean isInterfaceAncestor(Map<String, SymbolTable.ClassSymbol> knownClasses,
            String ancestor, String start) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            String cur = stack.pop();
            if (!visited.add(cur)) continue;
            SymbolTable.ClassSymbol sym = knownClasses.get(cur);
            if (sym == null) continue;
            for (String parent : sym.interfaces()) {
                String simple = bareName(parent);
                if (simple.equals(ancestor)) return true;
                stack.push(simple);
            }
        }
        return false;
    }

    private static boolean classOwnMethodOverrides(SymbolTable classScope, String name, int arity) {
        SymbolTable.Symbol local = classScope.localSymbols().get(name);
        List<SymbolTable.MethodSymbol> candidates = new ArrayList<>();
        if (local instanceof SymbolTable.MethodSymbol ms) candidates.add(ms);
        else if (local instanceof SymbolTable.MethodSet mset) candidates.addAll(mset.methods());
        for (SymbolTable.MethodSymbol cm : candidates) {
            if ((cm.accessFlags() & AccessFlags.ABSTRACT) != 0) continue;
            if (cm.parameterTypes().size() == arity) return true;
        }
        return false;
    }

    /**
     * #326: overriding method deve ter retorno COMPATIVEL com o do
     * sobrescrito (JLS 8.4.8.3 / JVM invokevdispatch). Retorno COVARIANTE
     * (subtipo) e legal — gerado como bridge por
     * `generateCovariantReturnBridges` (#248). Retorno INCOMPATIVEL (nem
     * igual, nem atribuivel ao do pai) e a causa do bug: o emit gera o metodo
     * com o descritor do FILHO, a JVM nao ve override, e a chamada via
     * referencia do pai faz dispatch ao pai (saida errada, silenciosa). Re-
     * jeitar em compile-time (R6), apontando o metodo, o pai e os dois tipos.
     */
    static void checkOverrideReturnCompatibility(SemanticAnalyzer sa,
            ClassDeclarationNode cls) {
        DiagnosticCollector diagnostics = sa.diagnostics();
        if (diagnostics == null) return;
        String curSuper = cls.superClass();
        if (curSuper == null || curSuper.isEmpty() || "Object".equals(curSuper)) return;
        SymbolTable classScope = sa.classMemberScopes().get(cls.name());
        if (classScope == null) return;
        Map<String, SymbolTable.ClassSymbol> knownClasses = sa.allClasses();
        java.util.Set<String> visited = new java.util.HashSet<>();
        visited.add(cls.name());
        while (curSuper != null && !curSuper.isEmpty() && !"Object".equals(curSuper)) {
            String simple = curSuper.contains("/")
                    ? curSuper.substring(curSuper.lastIndexOf("/") + 1) : curSuper;
            if (simple.contains("<")) simple = simple.substring(0, simple.indexOf("<"));
            if (!visited.add(simple)) break;
            SymbolTable.ClassSymbol superSym = knownClasses.get(simple);
            if (superSym == null) break;
            for (Map.Entry<String, SymbolTable.Symbol> e
                    : classScope.localSymbols().entrySet()) {
                List<SymbolTable.MethodSymbol> childMethods = new ArrayList<>();
                if (e.getValue() instanceof SymbolTable.MethodSymbol c) childMethods.add(c);
                else if (e.getValue() instanceof SymbolTable.MethodSet cs) childMethods.addAll(cs.methods());
                for (SymbolTable.MethodSymbol child : childMethods) {
                    if ((child.accessFlags() & (AccessFlags.STATIC | AccessFlags.PRIVATE)) != 0) continue;
                    SymbolTable.Symbol parent = superSym.members().resolve(child.name());
                    List<SymbolTable.MethodSymbol> parents = new ArrayList<>();
                    if (parent instanceof SymbolTable.MethodSymbol p) parents.add(p);
                    else if (parent instanceof SymbolTable.MethodSet pset) parents.addAll(pset.methods());
                    for (SymbolTable.MethodSymbol pm : parents) {
                        if ((pm.accessFlags() & (AccessFlags.STATIC | AccessFlags.PRIVATE)) != 0) continue;
                        if (pm.parameterTypes().size() != child.parameterTypes().size()) continue;
                        boolean paramsMatch = true;
                        for (int i = 0; i < child.parameterTypes().size(); i++) {
                            if (!child.parameterTypes().get(i).equals(pm.parameterTypes().get(i))) {
                                paramsMatch = false;
                                break;
                            }
                        }
                        if (!paramsMatch) continue;
                        Type parentRet = pm.returnType();
                        Type childRet = child.returnType();
                        if (childRet.equals(parentRet)) continue;
                        if (TypeChecker.isAssignable(sa, childRet, parentRet)) continue;
                        diagnostics.error(cls,
                                "method '" + child.name() + "' in class '" + cls.name()
                                        + "' overrides '" + simple + "' but return type " + childRet
                                        + " is not compatible with the overridden return type " + parentRet,
                                "SEM059");
                    }
                }
            }
            curSuper = superSym.superClass();
        }
    }
}
