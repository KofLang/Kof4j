package dev.kof.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * §251 — um tipo DECLARADO (retorno, parâmetro, campo, componente de record,
 * campo de entity) nunca era validado: um nome inexistente compilava em
 * silêncio e o descritor JVM referenciava um tipo que não existe — a classe
 * nem carregava (`NoClassDefFoundError`); para campo/local o valor era
 * simplesmente ignorado. R6 (nunca silencioso).
 *
 * <p>O predicado compartilhado é {@link MemberResolver#declaredTypeUnresolved},
 * que isenta builtins, coleções nuas, {@code kof.ui}/{@code kof.media} (§179),
 * enums, classes/records/interfaces do módulo, tipos externos via
 * {@code --classpath}, imports simples e os TYPE-PARAMS do escopo (classe/record/
 * função genérica — `class Box&lt;T&gt; { T value }`). A face LOCAL
 * ({@code VarDeclStmt}) é fechada no {@code StatementAnalyzer} (§249).
 *
 * <p>DEDICADO (regra 7) para manter os arquivos quentes
 * ({@code SemanticAnalyzer}) longe do limite de 500 linhas.
 */
final class DeclaredTypeChecker {

    private DeclaredTypeChecker() {}

    static void check(SemanticAnalyzer sa) {
        DiagnosticCollector dc = sa.diagnostics();
        if (dc == null || sa.unit() == null) return;
        for (AstNode decl : sa.unit().declarations()) {
            switch (decl) {
                case FunctionDeclarationNode f -> {
                    Set<String> tps = new HashSet<>(TypeParams.names(f.typeParameters())); // §355: nome limpo
                    checkBounds(dc, sa, f.typeParameters(), "function '" + f.name() + "'");
                    report(dc, sa, f.returnType(), tps,
                            "return type of function '" + f.name() + "'");
                    for (FormalParameterNode p : f.parameters()) {
                        report(dc, sa, p.type(), tps,
                                "parameter '" + p.name() + "' of function '" + f.name() + "'");
                    }
                }
                case ClassDeclarationNode c -> {
                    checkBounds(dc, sa, c.typeParameters(), "class '" + c.name() + "'");
                    checkHeritage(sa, dc, c.superClass(), c.interfaces(), c.typeParameters(), c.name());
                    checkMembers(sa, dc, c.typeParameters(), c.members());
                }
                case RecordDeclarationNode r -> {
                    checkBounds(dc, sa, r.typeParameters(), "record '" + r.name() + "'");
                    checkHeritage(sa, dc, r.superClass(), r.interfaces(), r.typeParameters(), r.name());
                    Set<String> tps = new HashSet<>(TypeParams.names(r.typeParameters())); // §355
                    for (RecordComponentNode comp : r.components()) {
                        report(dc, sa, comp.type(), tps,
                                "component '" + comp.name() + "' of record '" + r.name() + "'");
                    }
                    checkMembers(sa, dc, r.typeParameters(), r.members());
                }
                case InterfaceDeclarationNode i -> {
                    checkBounds(dc, sa, i.typeParameters(), "interface '" + i.name() + "'");
                    checkHeritage(sa, dc, null, i.interfaces(), i.typeParameters(), i.name());
                    checkMembers(sa, dc, i.typeParameters(), i.members());
                }
                case EntityDeclarationNode e -> {
                    for (EntityFieldNode f : e.fields()) {
                        report(dc, sa, f.type(), Set.of(),
                                "field '" + f.name() + "' of entity '" + e.name() + "'");
                    }
                }
                default -> { }
            }
        }
    }

    private static void checkMembers(SemanticAnalyzer sa, DiagnosticCollector dc,
                                     List<String> classTypeParams,
                                     List<? extends AstNode> members) {
        Set<String> tps = new HashSet<>(TypeParams.names(classTypeParams)); // §355
        for (AstNode member : members) {
            switch (member) {
                case FieldDeclarationNode field -> report(dc, sa, field.type(), tps,
                        "field '" + field.name() + "'");
                case MethodDeclarationNode method -> {
                    report(dc, sa, method.returnType(), tps,
                            "return type of method '" + method.name() + "'");
                    for (FormalParameterNode p : method.parameters()) {
                        report(dc, sa, p.type(), tps,
                                "parameter '" + p.name() + "' of method '" + method.name() + "'");
                    }
                }
                case ConstructorDeclarationNode ctor -> {
                    for (FormalParameterNode p : ctor.parameters()) {
                        report(dc, sa, p.type(), tps,
                                "parameter '" + p.name() + "' of constructor '" + ctor.name() + "'");
                    }
                }
                default -> { }
            }
        }
    }

    private static void report(DiagnosticCollector dc, SemanticAnalyzer sa, String declType,
                               Set<String> typeParams, String where) {
        if (declType == null) return;
        if (MemberResolver.declaredTypeUnresolved(sa, declType, typeParams)) {
            dc.error("", 0, 0, 0,
                    "Undefined variable or type: '" + declType.trim() + "' in " + where
                            + " — declare the type or fix the name (R6: undefined declared types"
                            + " must not compile)",
                    "SEM011");
            return;
        }
        reportCompositeTypeParam(dc, sa, declType, typeParams, where);
    }

    /**
     * §716: valida o BOUND de cada type-parameter genérico. O bound vira o
     * descritor da ERASURE do type-var; um bound inexistente (`T: Foobar`) ou
     * um não-tipo (`T: Any`) compilava em silêncio e a classe emitida
     * referenciava uma classe inexistente -> `NoClassDefFoundError` no load do
     * JVM (R6/Q7: nunca silencioso). Reusa o MESMO predicado dos tipos
     * declarados (§251/SEM011), isentando os type-params do próprio escopo.
     */
    private static void checkBounds(DiagnosticCollector dc, SemanticAnalyzer sa,
                                    List<String> typeParameters, String where) {
        if (typeParameters == null || typeParameters.isEmpty()) return;
        Set<String> names = new HashSet<>(TypeParams.names(typeParameters));
        for (String entry : typeParameters) {
            String bound = TypeParams.boundText(entry);
            if (bound == null || bound.isEmpty()) continue;
            if (MemberResolver.declaredTypeUnresolved(sa, bound, names)) {
                dc.error("", 0, 0, 0,
                        "Undefined variable or type: '" + bound.trim() + "' as the bound of type-parameter '"
                                + TypeParams.name(entry) + "' in " + where
                                + " — declare the bound type or fix the name (R6: an undefined bound must"
                                + " not compile)",
                        "SEM011");
            }
        }
    }

    /**
     * §288 (#396, D-RULE6-BATCH opção (b) — rejeição interina SEM085): um
     * tipo DECLARADO cuja FUNÇÃO carrega um type-param do dono (`(T) -> T`,
     * `(Int) -> T`, `List<(T) -> T>`…) compila hoje para uma interface
     * sintética APOGADA (`Function1_O_O`) enquanto a lambda do call site é
     * sintetizada contra a assinatura CONCRETA (`Function1_int_int`) — o
     * load morre em `IncompatibleClassChangeError` (medido no tip). A via
     * completa (lambda contextualizada na assinatura apagada + box/unbox no
     * corpo) é o trabalho de ABI de erasure da linha 1.0; até lá a forma é
     * rejeitada no compile (R6: nunca crash no load). As formas SEM função
     * com type-param (`T value`, `Pipeline<T>`, `List<T>`) seguem legais —
     * o rio da erasure as cobre (§355/§356/§357).
     */
    private static void reportCompositeTypeParam(DiagnosticCollector dc, SemanticAnalyzer sa,
                                                 String declType, Set<String> typeParams,
                                                 String where) {
        if (typeParams == null || typeParams.isEmpty()) return;
        String t = declType.trim();
        if (t.isEmpty() || "var".equals(t) || "val".equals(t) || "void".equals(t)) return;
        Type resolved = CompilerTypes.resolveWithTypeParams(
                t, java.util.List.copyOf(typeParams), sa.unit(), sa);
        if (fnTypeUsesOwnerTypeParam(resolved, typeParams)) {
            dc.error("", 0, 0, 0,
                    "function type with a type-parameter of the owner in '" + t + "' in " + where
                            + " — generic function types are not lowered yet (erasure ABI is 1.0-line);"
                            + " the form is rejected instead of compiling to a load crash (SEM085)",
                    "SEM085");
        }
    }

    /** Um type-param do dono aparece DENTRO de um tipo-função (param/retorno,
     *  em qualquer profundidade — `List<(T) -> T>` conta)? */
    private static boolean fnTypeUsesOwnerTypeParam(Type t, Set<String> tps) {
        if (t == null || tps.isEmpty()) return false;
        if (t instanceof Type.FunctionType ft) {
            for (Type p : ft.parameterTypes()) if (containsOwnerTypeParam(p, tps)) return true;
            return containsOwnerTypeParam(ft.returnType(), tps);
        }
        if (t instanceof Type.ClassType ct) {
            for (Type a : ct.typeArguments()) if (fnTypeUsesOwnerTypeParam(a, tps)) return true;
            return false;
        }
        if (t instanceof Type.ArrayType at) return fnTypeUsesOwnerTypeParam(at.componentType(), tps);
        if (t instanceof Type.NullableType nt) return fnTypeUsesOwnerTypeParam(nt.inner(), tps);
        if (t instanceof Type.WildcardType wt) return fnTypeUsesOwnerTypeParam(wt.bound(), tps);
        return false;
    }

    /** Um type-param do dono (TypeVariable ou leaf cru `ClassType("","T")`)
     *  ocorre em qualquer posição de {@code t}? */
    private static boolean containsOwnerTypeParam(Type t, Set<String> tps) {
        if (t == null || tps.isEmpty()) return false;
        if (t instanceof Type.TypeVariable tv) return tps.contains(tv.name());
        if (t instanceof Type.ClassType ct) {
            if (ct.packageName().isEmpty() && tps.contains(ct.name())) return true;
            for (Type a : ct.typeArguments()) if (containsOwnerTypeParam(a, tps)) return true;
            return false;
        }
        if (t instanceof Type.ArrayType at) return containsOwnerTypeParam(at.componentType(), tps);
        if (t instanceof Type.NullableType nt) return containsOwnerTypeParam(nt.inner(), tps);
        if (t instanceof Type.FunctionType ft) {
            for (Type p : ft.parameterTypes()) if (containsOwnerTypeParam(p, tps)) return true;
            return containsOwnerTypeParam(ft.returnType(), tps);
        }
        if (t instanceof Type.WildcardType wt) return containsOwnerTypeParam(wt.bound(), tps);
        return false;
    }

    /**
     * §268 (D-RULE6-BATCH opção A): `extends`/`implements` por nome simples que
     * NADA resolve (sem import, sem módulo, fora do `java.lang` — o probe
     * cacheado cobre Thread/Runnable/… ) vira SEM087 no compile. Antes o nome
     * cru virava super_class inválido e a classe morria no load
     * (`NoClassDefFoundError: Zebra`/`IOException`), silenciosamente.
     */
    private static void checkHeritage(SemanticAnalyzer sa, DiagnosticCollector dc,
                                      String superDecl, List<String> interfaces,
                                      List<String> classTypeParams, String typeName) {
        Set<String> tps = new HashSet<>(TypeParams.names(classTypeParams));
        reportHeritage(dc, sa, superDecl, tps, "superclass of '" + typeName + "'");
        if (interfaces == null) return;
        for (String iface : interfaces) {
            reportHeritage(dc, sa, iface, tps, "interface of '" + typeName + "'");
        }
    }

    private static void reportHeritage(DiagnosticCollector dc, SemanticAnalyzer sa, String declType,
                                       Set<String> typeParams, String where) {
        if (declType == null) return;
        if (MemberResolver.declaredTypeUnresolved(sa, declType, typeParams)) {
            dc.error("", 0, 0, 0,
                    "Undefined superclass or interface: '" + declType.trim() + "' in " + where
                            + " — import the type or fix the name (R6: a raw super fails at load)",
                    "SEM087");
        }
    }
}
