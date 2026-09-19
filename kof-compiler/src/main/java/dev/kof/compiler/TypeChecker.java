package dev.kof.compiler;

import java.util.List;

/**
 * Checagem de tipos extraída do SemanticAnalyzer (REFACTOR-500 fase 6):
 * assignabilidade, conferência de argumentos, tipos de resultado de
 * operações binárias e literais. Sem estado — diagnostics por parâmetro.
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

    /** String OU Nullable(String) — para o guard de ordem em String (bug 98). */
    private static boolean isMaybeString(Type t) {
        if (t instanceof Type.NullableType nt) return BuiltinTypes.isString(nt.inner());
        return BuiltinTypes.isString(t);
    }

    /** Enum OU Nullable(enum) — D-ENUM207 (a comparação enum×String é SEM062). */
    private static boolean isEnumRef(Type t) {
        if (t instanceof Type.NullableType nt) return isEnumRef(nt.inner());
        return BuiltinTypes.isEnumType(t);
    }

    static Type inferBinaryResultType(DiagnosticCollector diagnostics, String operator, Type left, Type right) {
        // bug 98 (paridade absoluta JVM=JS=X86=ARM=RISC, opção B da mantenedora):
        // `<`/`<=`/`>`/`>=` entre Strings — a ordem era UNspecified no reference
        // (expressions.md:56-58) e cada target dava lixo DIFERENTE: JVM tudo
        // false (if_acmp em referência), Native comparava PONTEIRO (ordem de
        // alocação), Script dava lexicográfico — paridade quebrada em silêncio
        // (R6). REJEITAR em compile-time com SEM053 (o MESMO erro nos 5 alvos:
        // este typer é o frontend único) apontando para o idiom do corpus —
        // `s.compareTo(t) < 0` (ordem lexicográfica, paridade §97). `==`/`!=`
        // (conteúdo, congelado) e `+` (concat) NÃO são afetados.
        if (("<".equals(operator) || "<=".equals(operator) || ">".equals(operator)
                || ">=".equals(operator))
                && (isMaybeString(left) || isMaybeString(right))) {
            if (diagnostics != null) {
                String rel = switch (operator) {
                    case "<" -> "< 0";
                    case "<=" -> "<= 0";
                    case ">" -> "> 0";
                    default -> ">= 0";
                };
                diagnostics.error("", 0, 0, 0,
                        "Kof has no operator '" + operator + "' for String "
                                + "(lexicographic order is Unspecified — it diverges per target); "
                                + "use: s.compareTo(t) " + rel,
                        "SEM053");
            }
            return Type.UnknownType.UNKNOWN;
        }
        // §211 / D-ENUM207: um valor de enum NÃO é uma String. `Dir.N == "N"`
        // compilava e devolvia `true` (o valor era `ldc "N"`), quebrando a
        // identidade que a issue #207 exige. A mantenedora ratificou o erro de
        // tipo (DECISIONS D-ENUM207). Rejeitado no frontend COMPARTILHADO —
        // o mesmo SEM062 nos 4 alvos (sem divergência, freeze regra 5).
        if (("==".equals(operator) || "!=".equals(operator))
                && (isEnumRef(left) && isMaybeString(right) || isMaybeString(left) && isEnumRef(right))) {
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot compare an enum value to a String: an enum constant is not "
                                + "a String (D-ENUM207). Compare two enum values, or use "
                                + ".name() explicitly to get the name",
                        "SEM062");
            }
            return Type.PrimitiveType.BOOL;
        }
        if ("==".equals(operator) || "!=".equals(operator) || "<".equals(operator) ||
                ">".equals(operator) || "<=".equals(operator) || ">=".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }

        if ("&&".equals(operator) || "||".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("instanceof".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("as".equals(operator)) {
            return right;
        }
        if ("!".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if (Type.isString(left) || Type.isString(right)) {
            if ("+".equals(operator)) {
                return BuiltinTypes.STRING;
            }
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot apply '" + operator + "' to String and " + right, "SEM001");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.PrimitiveType lp && right instanceof Type.PrimitiveType rp) {
            if ("int".equals(lp.name())) {
                if ("long".equals(rp.name()) || "Long".equals(rp.name())) return Type.PrimitiveType.LONG;
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.INT;
            }
            if ("long".equals(lp.name()) || "Long".equals(lp.name())) {
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.LONG;
            }
            if ("float".equals(lp.name()) || "Float".equals(lp.name())) {
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.FLOAT;
            }
            if ("double".equals(lp.name()) || "Double".equals(lp.name())) {
                return Type.PrimitiveType.DOUBLE;
            }
                if ("bool".equals(lp.name()) || "bool".equals(rp.name())) {
                    if ("+".equals(operator) || "-".equals(operator) || "*".equals(operator) ||
                            "/".equals(operator) || "%".equals(operator)) {
                        if (diagnostics != null) {
                            diagnostics.error("", 0, 0, 0,
                                    "Cannot apply '" + operator + "' to boolean types. Use == or != for comparison.", "SEM002");
                        }
                        return Type.UnknownType.UNKNOWN;
                    }
                }
            // #484: Char arithmetic (+, -, *, /, %) promotes to Int on the JVM
            // (char is always widened to int before arithmetic opcodes).
            // Without this, `Char + Int` and `Char - Char` returned Char and
            // println printed the code-point as a character instead of a number.
            if (isArithmeticOp(operator)
                    && ("char".equals(lp.name()) || "Char".equals(lp.name())
                        || "char".equals(rp.name()) || "Char".equals(rp.name()))) {
                return Type.PrimitiveType.INT;
            }
            return left;
        }
        if (left instanceof Type.ArrayType || right instanceof Type.ArrayType) {
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.UnknownType || right instanceof Type.UnknownType) {
            return Type.UnknownType.UNKNOWN;
        }
        // Aritmética sobre tipo referência (ex.: param de lambda sem anotação
        // → Object) não tem opcode: o emit cairia em IADD sobre referência e a
        // JVM rejeitaria o bytecode (VerifyError). Diagnóstico explícito, nunca
        // fallback silencioso (R6). String + já foi tratado acima.
        if (isArithmeticOp(operator) && (isReferenceType(left) || isReferenceType(right))) {
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot apply '" + operator + "' to non-numeric type "
                                + (isReferenceType(left) ? left : right)
                                + " (declare the parameter type, e.g. (x: Int) -> ...)",
                        "SEM001");
            }
            return Type.UnknownType.UNKNOWN;
        }
        return left;
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
        if (diagnostics == null || paramTypes.isEmpty() && !argTypes.isEmpty()) return;
        if (argTypes.size() != paramTypes.size()) {
            diagnostics.error("", 0, 0, 0,
                    "Wrong number of arguments for '" + methodName + "': expected "
                            + paramTypes.size() + " but got " + argTypes.size(), "SEM013");
            return;
        }
        for (int i = 0; i < argTypes.size(); i++) {
            if (!Type.isUnknown(argTypes.get(i)) && !Type.isUnknown(paramTypes.get(i))
                    && !isAssignable(argTypes.get(i), paramTypes.get(i))) {
                diagnostics.error("", 0, 0, 0,
                        "Argument " + (i + 1) + " of '" + methodName + "': expected '" + Type.display(paramTypes.get(i))
                                + "' but got '" + Type.display(argTypes.get(i)) + "'", "SEM014");
                return;
            }
        }
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
        if (c.parameterTypes().size() != argTypes.size()) return false;
        for (int i = 0; i < argTypes.size(); i++) {
            if (!emitCtorPairCompatible(c.parameterTypes().get(i), argTypes.get(i))) {
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
    static void checkCtorArgTypes(SemanticAnalyzer sa,
            SymbolTable members, String typeName, List<Type> argTypes) {
        if (sa == null || sa.diagnostics() == null) return;
        SymbolTable.Symbol init = members.resolve("<init>");
        java.util.List<SymbolTable.ConstructorSymbol> ctors = new java.util.ArrayList<>();
        if (init instanceof SymbolTable.ConstructorSymbol c) ctors.add(c);
        else if (init instanceof SymbolTable.ConstructorSet set) ctors.addAll(set.constructors());
        boolean hasSameArity = false;
        for (SymbolTable.ConstructorSymbol c : ctors) {
            if (c.parameterTypes().size() != argTypes.size()) continue;
            hasSameArity = true;
            if (ctorAccepts(c, argTypes)) return; // o emit pega este irmao
        }
        if (!hasSameArity) return; // aridade: SEM023 do chamador, nao nosso caso
        // o emit caí no fallback "primeiro de mesma aridade" e inventa o
        // descritor: reporta o primeiro par realmente incompatível.
        SymbolTable.ConstructorSymbol firstArity = ctors.stream()
                .filter(c -> c.parameterTypes().size() == argTypes.size())
                .findFirst().orElse(null);
        for (int i = 0; i < argTypes.size(); i++) {
            Type formal = firstArity.parameterTypes().get(i);
            Type arg = argTypes.get(i);
            if (!emitCtorPairCompatible(formal, arg)) {
                sa.diagnostics().error("", 0, 0, 0,
                        "Argument " + (i + 1) + " of '" + typeName + "': expected "
                                + formal + " but got " + arg
                                + " (no constructor matches the argument types)",
                        "SEM014");
                return;
            }
        }
    }

    static boolean isAssignable(Type from, Type to) {
        if (from == null || to == null) return true;
        if (Type.isUnknown(from) || Type.isUnknown(to)) return true;
        if (from instanceof Type.TypeVariable || to instanceof Type.TypeVariable) return true;
        if (from instanceof Type.NullableType fn) {
            if (to instanceof Type.NullableType tn) return isAssignable(fn.inner(), tn.inner());
            return false;
        }
        if (to instanceof Type.NullableType tn) {
            // from nunca é NullableType aqui: o branch acima sempre retorna
            // nesse caso (CodeQL contradictory-type-checks — ramo morto removido).
            return isAssignable(from, tn.inner());
        }
        if (from.equals(to)) return true;
        if (from instanceof Type.PrimitiveType fp && to instanceof Type.PrimitiveType tp) {
            if ("bool".equals(Type.canonicalPrimitiveName(fp.name())) || "bool".equals(Type.canonicalPrimitiveName(tp.name()))) {
                return "bool".equals(Type.canonicalPrimitiveName(fp.name())) && "bool".equals(Type.canonicalPrimitiveName(tp.name()));
            }
            // double → float: o lowering emite D2F; sem isso literais
            // decimais (1000.0) não atribuem a campos Float
            if ("double".equals(fp.name()) && "float".equals(tp.name())) return true;
            int fw = primitiveWidth(fp);
            int tw = primitiveWidth(tp);
            return fw <= tw;
        }
        if (from instanceof Type.FunctionType && to instanceof Type.ClassType) {
            // lambda → interface funcional externa (SAM conversion): a
            // compatibilidade real (aridade/tipos) é validada na emissão
            return true;
        }
        if (from instanceof Type.PrimitiveType && to instanceof Type.ClassType ct
                && "Object".equals(ct.name()) && "java.lang".equals(ct.packageName())) {
            // bug 15: primitivo → Object (auto-boxing no emit) — `Object n = 42`
            return true;
        }
        if (from instanceof Type.PrimitiveType fp
                && "double".equals(fp.name())
                && to instanceof Type.PrimitiveType tp
                && "float".equals(tp.name())) {
            // double → float: o lowering emite D2F; sem isso literais
            // decimais (1000.0) não atribuem a campos Float
            return true;
        }
        if (to instanceof Type.ClassType) {
            return from instanceof Type.ClassType;
        }
        return false;
    }

    /**
     * SG-009: subtipagem NOMINAL — `A a = <não-relacionado>` é erro
     * compile-time (antes só o checkcast do emit salvava, em runtime).
     * Caminha superClass/interfaces via BFS (MemberResolver usa o mesmo
     * padrão). Conservador (true) quando a hierarquia é desconhecida:
     * tipo externo (imports Android/JDK), builtin (String/List/Map — o
     * BuiltinTypes resolve), ou classe não declarada no módulo — restringir
     * esses quebraria interop legítima (regra 6: nunca quebrar o que funciona).
     */
    static boolean isAssignable(SemanticAnalyzer sa, Type from, Type to) {
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
        // tipos builtin (String, List, Map, Set...) têm relações próprias
        // (String → Object via regra acima; List<X> → List<Y> não é nominal)
        if (isBuiltinClassType(fc) || isBuiltinClassType(tc)) {
            return isAssignable(from, to);
        }
        // classes de domínio: BFS nominal
        String fromName = fc.name();
        if (fromName.equals(toName)) return true;
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
            String current = queue.poll();
            if (current.equals(toName)) return true;
            if (!visited.add(current)) continue;
            SymbolTable.ClassSymbol cur = sa.getClass(current);
            if (cur == null) continue; // ancestral externo — para o ramo
            if (cur.superClass() != null && !"Object".equals(cur.superClass())) queue.add(rawTypeName(cur.superClass()));
            for (String iface : cur.interfaces()) queue.add(rawTypeName(iface));
        }
        // from pode ser subtipo declarado com nome qualificado divergente —
        // conservador quando o símbolo de to não existe no módulo
        return sa.getClass(toName) == null;
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
    private static boolean isBuiltinClassType(Type.ClassType ct) {
        String pkg = ct.packageName();
        return "kof".equals(pkg) || "java".equals(pkg)
                || "java.lang".equals(pkg) || "java.util".equals(pkg)
                || "kof.concurrent".equals(pkg) || "dev.kof".equals(pkg)
                || pkg.startsWith("java.") || pkg.startsWith("dev.kof.")
                || pkg.startsWith("kof.");
    }

    static int primitiveWidth(Type.PrimitiveType pt) {
        return switch (pt.name()) {
            case "bool", "Bool" -> 0;
            case "char", "Char" -> 1;
            case "int", "Int", "byte", "short" -> 2;
            case "long", "Long" -> 3;
            case "float", "Float" -> 4;
            case "double", "Double" -> 5;
            default -> 2;
        };
    }
}
