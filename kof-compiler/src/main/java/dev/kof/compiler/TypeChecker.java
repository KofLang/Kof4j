package dev.kof.compiler;

import dev.kof.compiler.jvm.JvmTypeMapper;
import java.util.List;

/**
 * Checagem de tipos extraída do SemanticAnalyzer (REFACTOR-500 fase 6):
 * assignabilidade, conferência de argumentos e tipos de literais. O resultado
 * de operações binárias mora no SemBinaryResultTyper (split §446).
 * Sem estado — diagnostics por parâmetro.
 */
public final class TypeChecker {

    private TypeChecker() {}

    static Type inferLiteralType(LiteralExpr lit) {
        return switch (lit.kind()) {
            case ConcreteLiteralKind.INT -> Type.PrimitiveType.INT;
            case ConcreteLiteralKind.LONG -> Type.PrimitiveType.LONG;
            case ConcreteLiteralKind.FLOAT -> Type.PrimitiveType.FLOAT;
            case ConcreteLiteralKind.DOUBLE -> Type.PrimitiveType.DOUBLE;
            case ConcreteLiteralKind.STRING -> BuiltinTypes.STRING;
            case ConcreteLiteralKind.BOOLEAN -> Type.PrimitiveType.BOOL;
            case ConcreteLiteralKind.CHAR -> Type.PrimitiveType.CHAR;
            case ConcreteLiteralKind.NULL -> Type.UnknownType.UNKNOWN;
            default -> Type.UnknownType.UNKNOWN;
        };
    }

    static boolean isConcurrentHandle(Type t) {
        return t instanceof Type.ClassType ct
                && "kof.concurrent".equals(ct.packageName())
                && "Handle".equals(ct.name());
    }

    static boolean isArithmeticOp(String op) {
        return "+".equals(op) || "-".equals(op) || "*".equals(op)
                || "/".equals(op) || "%".equals(op);
    }

    static boolean isReferenceType(Type t) {
        return t instanceof Type.ClassType || t instanceof Type.FunctionType;
    }

    static void checkArgTypes(DiagnosticCollector diagnostics, String methodName,
                              List<Type> argTypes, List<Type> paramTypes) {
        checkArgTypes(diagnostics, methodName, argTypes, paramTypes, null);
    }

    static void checkArgTypes(DiagnosticCollector diagnostics, String methodName,
                              List<Type> argTypes, List<Type> paramTypes,
                              List<ExpressionNode> argNodes) {
        checkArgTypes(null, diagnostics, methodName, argTypes, paramTypes, argNodes);
    }

    /**
     * #688: a checagem de argumento de CHAMADA usava o `isAssignable` de 2
     * args (estrutural), que aceita quaisquer dois ClassType e ignora os
     * type-args — então `tentaEscrever(Caixa<Cachorro>)` em parâmetro
     * `Caixa<Animal>` (ou `List<Dog>`→`List<Animal>`) compilava e corrompia
     * em runtime. Aqui passamos o `sa` para o `isAssignable` nominal (mesmo
     * caminho de declarações/atribuições): hierarquia nominal + args de
     * genérico (invariante por padrão, out/in por variância). `sa == null`
     * preserva o comportamento antigo (overload legado).
     */
    static void checkArgTypes(SemanticAnalyzer sa, DiagnosticCollector diagnostics, String methodName,
                              List<Type> argTypes, List<Type> paramTypes,
                              List<ExpressionNode> argNodes) {
        if (diagnostics == null || paramTypes.isEmpty() && !argTypes.isEmpty()) return;
        if (argTypes.size() != paramTypes.size()) {
            diagnostics.error(argNodeAt(argNodes, 0),
                    "Wrong number of arguments for '" + methodName + "': expected "
                            + paramTypes.size() + " but got " + argTypes.size(), "SEM013");
            return;
        }
        for (int i = 0; i < argTypes.size(); i++) {
            if (!Type.isUnknown(argTypes.get(i)) && !Type.isUnknown(paramTypes.get(i))
                    && !isAssignable(sa, argTypes.get(i), paramTypes.get(i))) {
                diagnostics.error(argNodeAt(argNodes, i),
                        "Argument " + (i + 1) + " of '" + methodName + "': expected '" + Type.display(paramTypes.get(i))
                                + "' but got '" + Type.display(argTypes.get(i)) + "'", "SEM014");
                return;
            }
        }
    }

    private static ExpressionNode argNodeAt(List<ExpressionNode> argNodes, int i) {
        return argNodes != null && i < argNodes.size() ? argNodes.get(i) : null;
    }

    /**
     * #323 — par formal/arg que o EMIT considera compatível ao ESCOLHER o
     * construtor. UNIAO do predicado do emit (`ExpressionStaticCallLowerer`:
     * `equals || argUnknown || ambos ClassType`) com `isAssignable`: o
     * predicado do emit sozinho perderia `String` → `String?` e boxing;
     * `isAssignable` sozinho daria falso-positivo em classe passada onde o
     * emit espera `FunctionType` (callback/SAM — Supervisor como handler no
     * KofSupWindow, caso real do KofSupervisorE2ETest). So reporta o par que
     * NENHUMA das duas vias aceita — o que fabricaria descritor/stack errado
     * (o VerifyError da issue).
     */
    static boolean emitCtorPairCompatible(Type formal, Type arg) {
        if (formal.equals(arg)) return true;
        if (Type.isUnknown(arg) || Type.isUnknown(formal)) return true;
        if (isAssignable(arg, formal)) return true;
        if (formal instanceof Type.FunctionType) return true;   // SAM/coercao de callback
        if (formal instanceof Type.TypeVariable) return true;   // apagado no emit
        if (formal instanceof Type.ClassType && arg instanceof Type.ClassType) return true;
        return false;
    }

    static boolean ctorAccepts(SymbolTable.ConstructorSymbol c, List<Type> argTypes) {
        return ctorAccepts(c.parameterTypes(), argTypes);
    }

    static boolean ctorAccepts(List<Type> formalTypes, List<Type> argTypes) {
        if (formalTypes.size() != argTypes.size()) return false;
        for (int i = 0; i < argTypes.size(); i++) {
            if (!emitCtorPairCompatible(formalTypes.get(i), argTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * #323 — cheque de TIPO de argumento na construcao (`new X(args)` e a
     * forma implicita `X(args)`). O emit resolve o construtor por aridade e,
     * sem um que aceite os args, fabrica o descritor a partir dos ARGS →
     * <init> fantasma → VerifyError mudo no load (o bug da issue). Reportamos
     * SEM014 exatamente quando NENHUM construtor de mesma aridade passa no
     * PREDICADO DO EMIT (não em `isAssignable`: sobrecarga válida, SAM e
     * classe externa têm de continuar compilando). Aridade sem candidato e
     * SEM023 de quem chama (ja existente).
     */
    static void checkCtorArgTypes(SemanticAnalyzer sa, AstNode node,
            SymbolTable members, String typeName, List<Type> argTypes) {
        if (sa == null || sa.diagnostics() == null) return;
        SymbolTable.Symbol init = members.resolve("<init>");
        java.util.List<SymbolTable.ConstructorSymbol> ctors = new java.util.ArrayList<>();
        if (init instanceof SymbolTable.ConstructorSymbol c) ctors.add(c);
        else if (init instanceof SymbolTable.ConstructorSet set) ctors.addAll(set.constructors());
        boolean hasSameArity = false;
        for (SymbolTable.ConstructorSymbol c : ctors) {
            if (!c.acceptsArgumentCount(argTypes.size())) {
                continue;
            }
            List<Type> effective = c.effectiveParameterTypes(argTypes.size());
            hasSameArity = true;
            if (ctorAccepts(effective, argTypes)) return; // o emit pega este irmao
        }
        if (!hasSameArity) return; // aridade: SEM023 do chamador, nao nosso caso
        // o emit cai no fallback "primeiro de mesma aridade" e inventa o
        // descritor: reporta o primeiro par realmente incompativel.
        List<Type> firstEffective = ctors.stream()
                .filter(c -> c.acceptsArgumentCount(argTypes.size()))
                .findFirst().map(c -> c.effectiveParameterTypes(argTypes.size()))
                .orElse(List.of());
        for (int i = 0; i < argTypes.size(); i++) {
            Type formal = firstEffective.get(i);
            Type arg = argTypes.get(i);
            if (!emitCtorPairCompatible(formal, arg)) {
                sa.diagnostics().error(node,
                        "Argument " + (i + 1) + " of '" + typeName + "': expected "
                                + Type.display(formal) + " but got " + Type.display(arg)
                                + " (no constructor matches the argument types)",
                        "SEM014");
                return;
            }
        }
    }

    /**
     * §370: conformação de TIPO-FUNÇÃO para atribuição (`var f: (T) -> U =
     * <lambda>`, `s.camp = <lambda>`). O SC2 de VarDecl pulava o check
     * whenever um lado era FunctionType, e a escrita em campo não comparava
     * o tipo do campo — o lambda era EMITIDO com a interface da assinatura
     * INFERIDA do corpo (`mangleTypeForIface`, CompilerLambdaClass) e o call
     * site despachava pela DECLARADA do slot: corpo que retorna `Map?`
     * (ex.: `data.get(n)`) em slot `-> Map` compilava verde e explodia em
     * runtime com `IncompatibleClassChangeError` (R6). Agora: lados
     * FunctionType exigem aridade + parâmetros iguais e retorno conforme a
     * MESMA regra de nullabilidade de `isAssignable` (nunca `T?` → `T` sem
     * narrowing); Unknown/TypeVariable permanecem permissivos (invariantes
     * do skip original preservadas).
     */
    static boolean functionTypesConform(Type init, Type declared) {
        if (!(init instanceof Type.FunctionType fi) || !(declared instanceof Type.FunctionType fd)) {
            return true;
        }
        if (fi.parameterTypes().size() != fd.parameterTypes().size()) return false;
        for (int i = 0; i < fi.parameterTypes().size(); i++) {
            if (!isAssignable(fi.parameterTypes().get(i), fd.parameterTypes().get(i))) return false;
        }
        Type a = fi.returnType(), b = fd.returnType();
        boolean an = a instanceof Type.NullableType, bn = b instanceof Type.NullableType;
        Type ai = an ? ((Type.NullableType) a).inner() : a;
        Type bi = bn ? ((Type.NullableType) b).inner() : b;
        if (an && !bn) return false;
        if (Type.isUnknown(ai) || Type.isUnknown(bi)
                || ai instanceof Type.TypeVariable || bi instanceof Type.TypeVariable) return true;
        return ai.equals(bi);
    }

    static String rawTypeOf(Type.ClassType ct) {
        return ct.packageName().isEmpty() ? ct.name() : ct.packageName() + "." + ct.name();
    }

    /**
     * X5.3 (D-TYPE-VARIANCE): compatibilidade dos args de um MESMO raw à luz
     * da variância declaration-site do tipo. Sem variância registrada (tipo
     * builtin/JDK ou declarado sem `out`/`in`) cai na regra §270 invariante
     * (igualdade exata) — compatível com tudo o que já existia. Com variância:
     * `out` aceita `from.arg -> to.arg` (covariante), `in` aceita
     * `to.arg -> from.arg` (contravariante); UNKNOWN/TypeVariable permanecem
     * permissivos como no §270.
     */
    static boolean genericArgsCompatible(SemanticAnalyzer sa, List<Type> from, List<Type> to,
                                         List<String> variance) {
        if (from.isEmpty() || to.isEmpty() || from.size() != to.size()) return true;
        for (int i = 0; i < from.size(); i++) {
            Type a = from.get(i), b = to.get(i);
            // X5.4 (D-X5-SURFACE): projeção no sítio de uso no lado DECLARADO —
            // `List<out Animal>` aceita `List<Dog>`; `List<in Dog>` aceita
            // `List<Animal>` (espelha a variância declaration-site, aplicada
            // por USO). A projeção é apagada na emissão (WildcardType).
            if (b instanceof Type.WildcardType wb) {
                if (wb.bound() == null) continue;
                if (Type.isUnknown(a) || a instanceof Type.TypeVariable
                        || a instanceof Type.WildcardType) return true;
                if (wb.upper() ? !isAssignable(sa, a, wb.bound())
                               : !isAssignable(sa, wb.bound(), a)) {
                    return false;
                }
                continue;
            }
            if (a instanceof Type.WildcardType) return true; // projeção na origem: conservador
            if (Type.isUnknown(a) || Type.isUnknown(b)
                    || a instanceof Type.TypeVariable || b instanceof Type.TypeVariable) {
                return true;
            }
            String v = (variance != null && i < variance.size()) ? variance.get(i) : "";
            switch (v) {
                case "out" -> { if (!isAssignable(sa, a, b)) return false; }
                case "in" -> { if (!isAssignable(sa, b, a)) return false; }
                default -> { if (!a.equals(b)) return false; }
            }
        }
        return true;
    }

    /**
     * §270 (#401): os dois lados têm args CONCRETOS (mesma aridade, nenhum
     * UNKNOWN/TypeVariable) e os args não são iguais? Só então a rejeição é
     * segura — inferência (`listOf()`), raw (`List`) e type-param (`T`)
     * permanecem permissivos como sempre. Recursiva p/ args aninhados
     * (`Map<String, List<Int>>` vs `Map<String, List<String>>`).
     */
    static boolean argsIncompatible(List<Type> from, List<Type> to) {
        if (from.isEmpty() || to.isEmpty() || from.size() != to.size()) return false;
        boolean allEqual = true;
        for (int i = 0; i < from.size(); i++) {
            Type a = from.get(i), b = to.get(i);
            if (Type.isUnknown(a) || Type.isUnknown(b)
                    || a instanceof Type.TypeVariable || b instanceof Type.TypeVariable) {
                return false;
            }
            if (!a.equals(b)) {
                allEqual = false;
            }
        }
        return !allEqual;
    }

    /** O caminho ESTRUTURAL (sem hierarquia nominal) vive em
     *  StructuralAssignability (extraída no commit da face boxing #1089,
     *  gate ≤500); o delegate mantém os callers `TypeChecker.isAssignable(f,t)`. */
    static boolean isAssignable(Type from, Type to) {
        return StructuralAssignability.isAssignable(from, to);
    }

    static boolean isAssignable(SemanticAnalyzer sa, Type from, Type to) {
        // sem analisador não há hierarquia nominal: cai no estrutural legado.
        if (sa == null) return isAssignable(from, to);
        // caminhos não-nominais primeiro (primitivos, nullability, Unknown):
        if (!isReferenceCandidate(from, to)) return isAssignable(from, to);
        if (!(from instanceof Type.ClassType fc) || !(to instanceof Type.ClassType tc)) {
            return isAssignable(from, to);
        }
        // mesmo tipo (já coberto por from.equals, mas barato re-checar via
        // caminho nominal p/ genéricos com args diferentes — conservador)
        String toName = tc.name();
        // Object é raiz: qualquer referência atribui
        if ("Object".equals(toName) && "java.lang".equals(tc.packageName())) return true;
        // §270 (#401, D-POLL-19 18/09): `List<Int>` → `List<String>` era
        // aceito porque o check só comparava o RAW (tipos genéricos nunca
        // consultavam type-args). Invariante nos args quando AMBOS os lados
        // têm args concretos no MESMO raw: rejeita. Conservador onde a
        // semântica é inferência/erasure: args vazios (raw, ou classe →
        // interface do #400), UNKNOWN (`listOf()` vazio/inferido),
        // TypeVariable (`T`) e aridades diferentes ficam permissivos.
        if (rawTypeOf(fc).equals(rawTypeOf(tc))
                && !genericArgsCompatible(sa, fc.typeArguments(), tc.typeArguments(),
                        sa != null ? sa.varianceOf(fc.name()) : null)) {
            return false;
        }
        // tipos builtin (String, List, Map, Set...) têm relações próprias
        // (String → Object via regra acima; List<X> → List<Y> não é nominal)
        if (StructuralAssignability.isBuiltinClassType(fc) || StructuralAssignability.isBuiltinClassType(tc)) {
            return isAssignable(from, to);
        }
        // classes de domínio: BFS nominal
        // §726: os nomes de heritage divergem entre arquivos — um tipo do
        // MESMO arquivo fica SIMPLES ("Greeter") e um resolvido por IMPORT fica
        // QUALIFICADO ("ifc.Greeter"), enquanto `sa.getClass` só resolve por
        // nome SIMPLES. Comparar a entrada contra `toName` (simples) rejeitava
        // o caso cross-file no MESMO pacote (SEM021 falso-positivo, bloqueando
        // qualquer biblioteca interface+implementações). Normaliza AMBOS pelo
        // FQN do símbolo resolvido.
        String fromName = simpleName(fc.name());
        String toFqn = fqnOf(tc);
        if (fqnOf(fc).equals(toFqn)) return true;
        SymbolTable.ClassSymbol node = sa.getClass(fromName);
        if (node == null) return true; // externa/desconhecida — conservador
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        visited.add(fromName);
        if (node.superClass() != null && !"Object".equals(node.superClass())) {
            queue.add(rawTypeName(node.superClass()));
        }
        for (String iface : node.interfaces()) queue.add(rawTypeName(iface));
        while (!queue.isEmpty()) {
            String raw = queue.poll();
            String current = fqnOfString(sa, raw);
            if (current.equals(toFqn)) return true;
            if (!visited.add(current)) continue;
            SymbolTable.ClassSymbol cur = sa.getClass(simpleName(raw));
            if (cur == null) continue; // ancestral externo — para o ramo
            if (cur.superClass() != null && !"Object".equals(cur.superClass())) queue.add(rawTypeName(cur.superClass()));
            for (String iface : cur.interfaces()) queue.add(rawTypeName(iface));
        }
        // from pode ser subtipo declarado com nome qualificado divergente —
        // conservador quando o símbolo de to não existe no módulo
        return sa.getClass(simpleName(toFqn)) == null;
    }

    /**
     * #400: `implements Converter<Int, String>` é guardado no AST COM os
     * type-args (parseTypeRef preserva o texto todo), então o BFS nominal
     * comparava "Converter<Int, String>" com "Converter" e nunca achava a
     * interface → SEM021 falso-positivo bloqueando atribuição legal. A
     * subtipagem nominal compara pela face APAGADA (mesma raiz do JDK:
     * `Converter<Int,String>` apaga p/ `Converter`; checagem FINA dos args
     * entre interfaces =SG-013/#401, fila própria). Precedente strip:
     * MemberResolver:356 (`simpleSuper.substring(0, indexOf("&lt;"))`).
     */
    private static String rawTypeName(String declared) {
        int lt = declared.indexOf('<');
        if (lt < 0) return declared;
        return declared.substring(0, lt).trim();
    }

    /** §726: nome simples (após o último '.') — as entradas de heritage e os
     *  nomes de símbolo convivem em formas simples e qualificadas. */
    private static String simpleName(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    /** §726: FQN de um ClassType (pkg.nome; sem pacote = o nome). */
    private static String fqnOf(Type.ClassType ct) {
        String p = ct.packageName();
        return p == null || p.isEmpty() ? ct.name() : p + "." + ct.name();
    }

    /** §726: FQN de uma entrada de heritage — resolve o símbolo por nome
     *  SIMPLES e usa o pacote dele; sem símbolo (externo), conserva o cru. */
    private static String fqnOfString(SemanticAnalyzer sa, String declared) {
        String bare = rawTypeName(declared);
        SymbolTable.ClassSymbol c = sa.getClass(simpleName(bare));
        if (c != null && c.packageName() != null && !c.packageName().isEmpty()) {
            return c.packageName() + "." + c.name();
        }
        return bare;
    }

    /** Referência → referência (o único caminho que a subtipagem nominal rege). */
    private static boolean isReferenceCandidate(Type from, Type to) {
        return from instanceof Type.ClassType && to instanceof Type.ClassType;
    }

    /**
     * Builtin/stdlib/runtime (java.*, kof.*, pacotes de runtime): relações
     * próprias, não nominais de domínio. Classe de domínio SEM pacote
     * (declarada top-level no módulo) NÃO é builtin — é o caso comum
     * (class Cat ... main() no mesmo arquivo) e DEVE ser checada.
     */
}
