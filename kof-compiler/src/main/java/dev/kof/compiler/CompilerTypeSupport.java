package dev.kof.compiler;

import java.util.List;

/**
 * Helpers de tipo e layout: json/fp support, nomes internos, access flags.
 */
public final class CompilerTypeSupport {

    private CompilerTypeSupport() {}

    static Type listOfElementType(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (!mc.arguments().isEmpty()) {
            Type first = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
            if (first instanceof Type.FunctionType ft) {
                // §156: lista heterogênea de lambdas com a MESMA assinatura —
                // o tipo do elemento carregava o className da PRIMEIRA lambda
                // concreta (Lambda0) e o `kof_list_get` fazia checkcast p/ ela
                // (CCE quando o elemento era Lambda1). Todas as lambdas da
                // assinatura implementam a MESMA interface SAM sintética — o
                // elemento desce sem className (dispatch por interface, bug 8).
                // Só unifica quando TODOS os args são FunctionType da mesma
                // assinatura (params+retorno); senão mantém o primeiro (SEM056
                // barra a poluição heterogênea de verdade no literal).
                if (sameLambdaSignature(driver, mc, locals, ft)) {
                    return new Type.FunctionType(ft.parameterTypes(), ft.returnType());
                }
            } else if (first instanceof Type.ClassType) {
                // #360: espelho do BuiltinCallTyper — a lista heterogênea por
                // subtipes relaciona-se ao ancestral comum (checkcast sai p/
                // ele); sem ancestral comum (ou com primitivo no meio) o
                // first-wins de hoje fica intacto (r1).
                Type.ClassType elem = (Type.ClassType) first;
                for (int i = 1; i < mc.arguments().size(); i++) {
                    Type t = ExpressionTyper.inferExprType(driver, mc.arguments().get(i), locals);
                    if (!(t instanceof Type.ClassType tc)) continue;
                    elem = (Type.ClassType) HierarchyResolver.widenToCommonSupertype(
                            driver.semanticAnalyzer, elem, tc);
                }
                return elem;
            }
            return first;
        }
        if (!mc.typeArguments().isEmpty()) {
            return CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
        }
        return Type.UnknownType.UNKNOWN;
    }

    private static boolean sameLambdaSignature(CompilerDriver driver, MethodCallExpr mc,
            List<IRLocalVariable> locals, Type.FunctionType first) {
        for (int i = 1; i < mc.arguments().size(); i++) {
            Type t = ExpressionTyper.inferExprType(driver, mc.arguments().get(i), locals);
            if (!(t instanceof Type.FunctionType ft)) return false;
            if (!ft.parameterTypes().equals(first.parameterTypes())) return false;
            if (!ft.returnType().equals(first.returnType())) return false;
        }
        return true;
    }

    static boolean ctorCompatible(CompilerDriver driver, Type formal, Type arg) {
        if (formal == null || arg == null) return true;
        if (Type.isUnknown(formal) || Type.isUnknown(arg)) return true;
        if (formal.equals(arg)) return true;
        if (formal instanceof Type.PrimitiveType fp && arg instanceof Type.PrimitiveType ap) {
            return TypeMetrics.primWidth(ap) <= TypeMetrics.primWidth(fp);
        }
        if (formal instanceof Type.ClassType fc && arg instanceof Type.ClassType ac
                && driver.semanticAnalyzer != null) {
            java.util.Set<String> visited = new java.util.HashSet<>();
            java.util.Queue<String> queue = new java.util.LinkedList<>();
            queue.add(ac.name());
            visited.add(ac.name());
            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current.equals(fc.name())) return true;
                SymbolTable.ClassSymbol cur = driver.semanticAnalyzer.getClass(current);
                if (cur == null) continue;
                if (cur.superClass() != null && !cur.superClass().equals("java/lang/Object")
                        && visited.add(cur.superClass())) queue.add(cur.superClass());
                for (String i : cur.interfaces()) {
                    if (visited.add(i)) queue.add(i);
                }
            }
        }
        return true;
    }

    static boolean erasesToReference(Type t) {
        return t instanceof Type.TypeVariable || t instanceof Type.ClassType
                || t instanceof Type.ArrayType || t instanceof Type.UnknownType;
    }

    static boolean syntheticExists(CompilerDriver driver, String name) {
        for (IRClass c : driver.syntheticClasses) {
            if (c.name().equals(name)) return true;
        }
        return false;
    }

    static boolean fpSupportedOnNative(@SuppressWarnings("unused") CompilerDriver driver,
            @SuppressWarnings("unused") Type type,
            @SuppressWarnings("unused") SourcePosition pos) {
        // Native float/double now supported via XMM (was FLT001) — KofJS always was
        return true;
    }

    static boolean jsonSupported(CompilerDriver driver, Type type, boolean isDecode) {
        Type check = BuiltinTypes.isList(type) ? CompilerTypeSupport.listElementType(driver,type) : type;
        if (check instanceof Type.PrimitiveType pt && ("float".equals(pt.name()) || "double".equals(pt.name()))) {
            // JSN001 fechado: encode/decode float/double no Native
            // (kof_json_encode_double + kof_string_to_double, FP XMM).
            return true;
        }
        if (isDecode && type instanceof Type.ArrayType) {
            // JSN003 fechado: int/long/bool/string[] tem decoders nativos.
            // JSN001: float/double[] também decodifica no Native.
            return true;
        }
        // §516 (26/09): List/Map de record no ENCODE nativo anda pelo walker
        // de runtime (kof_json_encode_object), que so existe se a tabela de
        // schema do coletor existir — as regras do gate sao as do COLETOR
        // (int/char/byte/short/long/bool/string + classe aninhada com tabela;
        // float/double SEM tabela -> falsy "null" seria o bug R6). O gate da
        // dobra escalar (fieldOk/nativeObjJsonFieldsOk) permanece intacto:
        // la float/double funcionam via kof_double_to_string.
        // Decode de List<Record> segue o caminho proprio JSN004 do lowerer.
        Type walkerElem = null;
        if (!isDecode && driver.target.isNative()) {
            if (BuiltinTypes.isList(type)) walkerElem = listElementType(driver, type);
            else if (BuiltinTypes.isMap(type)) walkerElem = BuiltinTypes.mapValue(type);
        }
        if (walkerElem instanceof Type.ClassType wct && !BuiltinTypes.isString(walkerElem)) {
            if (BuiltinTypes.isList(walkerElem) || BuiltinTypes.isMap(walkerElem)
                    || BuiltinTypes.isSet(walkerElem)) {
                // §516 (R6): List/Map/Set de List/Map — o walker nativo e v1
                // flat; a classe colecao nao tem tabela de schema, e sem este
                // gate o elemento virava o "null" falso do notfound. O gate de
                // CAMPO interno (abaixo) ja cobria record; este cobre a colecao
                // como ELEMENTO direto. JVM/Script seguem normais.
                driver.currentDiagnostics.error("", 0, 0, 0,
                        "json: nested List/Map/Set element is not supported by the Native JSON walker"
                                + " (v1 flat, §516) — use the JVM/JS target or wrap the inner"
                                + " collection in a record with supported fields",
                        "JSN002");
                return false;
            }
            String wcn = wct.packageName().isEmpty() ? wct.name()
                    : wct.packageName() + "." + wct.name();
            return nativeRecordWalkerOk(driver, wcn, new java.util.HashSet<>());
        }
        if (check instanceof Type.ClassType && driver.target.isNative() && !BuiltinTypes.isList(type)
                && !BuiltinTypes.isString(type)) {
            // JSN002 fechado para classes cujos campos sao todos suportados
            // pelo walker nativo (primitivos, string e objetos aninhados).
            String cn = check instanceof Type.ClassType ct
                    ? (ct.packageName().isEmpty() ? ct.name()
                      : ct.packageName() + "." + ct.name())
                    : "";
            if (!driver.nativeObjJsonFieldsOk(cn, new java.util.HashSet<>(), null)) {
                return false;
            }
        }
        return true;
    }

    static boolean nativeRecordWalkerOk(CompilerDriver driver, String className,
                                        java.util.Set<String> visiting) {
        if (visiting.contains(className)) return true; // ciclo: aceita no nivel externo
        visiting.add(className);
        for (String[] f : driver.classFieldsOrdered(className)) {
            Type t = CompilerTypes.toType(f[1], driver.currentUnit);
            boolean ok;
            if (t instanceof Type.PrimitiveType pt) {
                ok = switch (Type.canonicalPrimitiveName(pt.name())) {
                    case "int", "char", "byte", "short", "long", "bool" -> true;
                    default -> false; // float/double: sem tabela de schema
                };
            } else if (BuiltinTypes.isString(t)) {
                ok = true;
            } else if (t instanceof Type.ClassType ct
                    && !BuiltinTypes.isList(t) && !BuiltinTypes.isMap(t) && !BuiltinTypes.isSet(t)) {
                String cn2 = ct.packageName().isEmpty() ? ct.name()
                        : ct.packageName() + "." + ct.name();
                ok = !driver.classFieldsOrdered(cn2).isEmpty()
                        && nativeRecordWalkerOk(driver, cn2, visiting);
            } else {
                ok = false; // colecoes/campo exotico: walker nao tabela
            }
            if (!ok) {
                driver.currentDiagnostics.error("", 0, 0, 0,
                        "json: class " + className + " field " + f[0]
                                + " (type " + f[1] + ") has no Native JSON schema"
                                + " (record lists/maps encode int/long/bool/string/nested-record;",
                        "JSN002");
                return false;
            }
        }
        return true;
    }

    static boolean fieldOk(CompilerDriver driver, String typeName, String className, java.util.Set<String> visiting) {
        Type t = CompilerTypes.toType(typeName, driver.currentUnit);
        // #1181 (a fatia 1 — o campo NULLABLE): `String?` no record do decode
        // nativo era recusado com o JSN002 do ENCODER (a mensagem falava do
        // encoder para um problema do DECODER). O nullable é um wrapper: o
        // campo existe ou é ausente — o decoder nativo (o find_value + o
        // escalar + o ctor) aceita o VALOR OU O AUSENTE; o nullable só muda o
        // default do campo ausente (null). A decisão do gate: o inner decide.
        if (t instanceof Type.NullableType nt) {
            return fieldOk(driver, nt.inner() instanceof Type.ClassType ic
                    ? (ic.packageName().isEmpty() ? ic.name()
                      : ic.packageName() + "." + ic.name())
                    : typeName, className, visiting);
        }
        if (t instanceof Type.PrimitiveType) return true;
        if (BuiltinTypes.isString(t)) return true;
        // #1181 (a fatia 3 — o record ANINHADO): AINDA RECUADO (§1238): o
        // emit do ctor aninhado no paths nativo não consome a pilha como o
        // flat (o pai lê o filho errado: o id vem null e o acesso SEGV). O
        // gate mantém a recusa HONESTA até a fatia do emit sair completa.
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error("", 0, 0, 0,
                    "json: class " + className + " has field of type " + typeName
                            + " not supported by the Native JSON encoder yet"
                            + " (use int, long, bool or string fields; nested objects coming soon)",
                    "JSN002");
        }
        return false;
    }

    static Type listElementType(CompilerDriver driver, Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    static String toInternalName(String packageName, String simpleName) {
        if (simpleName.contains("/")) return simpleName;
        if (simpleName.contains(".")) return simpleName.replace('.', '/');
        if (packageName.isEmpty()) return simpleName;
        return packageName.replace('.', '/') + "/" + simpleName;
    }

    static int computeAccess(List<String> modifiers) {
        int access = 0;
        boolean hasVisibility = false;
        for (String mod : modifiers) {
            access |= switch (mod) {
                case "public" -> AccessFlags.PUBLIC;
                case "private" -> AccessFlags.PRIVATE;
                case "protected" -> AccessFlags.PROTECTED;
                case "static" -> AccessFlags.STATIC;
                case "final" -> AccessFlags.FINAL;
                case "abstract" -> AccessFlags.ABSTRACT;
                default -> 0;
            };
            if ("public".equals(mod) || "private".equals(mod) || "protected".equals(mod)) {
                hasVisibility = true;
            }
        }
        if (!hasVisibility) access |= AccessFlags.PUBLIC;
        return access;
    }

    static int parseIntLiteral(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            // no suffix stripping: hex digits may end in a..f
            try {
                return (int) Long.parseUnsignedLong(value.substring(2), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer literal: " + value, e);
            }
        }
        try {
            return Integer.parseInt(CompilerTypeSupport.stripSuffix(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer literal: " + value, e);
        }
    }

    static long parseLongLiteral(String value) {
        String stripped = CompilerTypeSupport.stripSuffix(value);
        if (stripped.startsWith("0x") || stripped.startsWith("0X")) {
            try {
                return Long.parseUnsignedLong(stripped.substring(2), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid long literal: " + value, e);
            }
        }
        try {
            return Long.parseLong(stripped);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long literal: " + value, e);
        }
    }

    static float parseFloatLiteral(String value) {
        try {
            return Float.parseFloat(CompilerTypeSupport.stripSuffix(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid float literal: " + value, e);
        }
    }

    static double parseDoubleLiteral(String value) {
        try {
            return Double.parseDouble(CompilerTypeSupport.stripSuffix(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid double literal: " + value, e);
        }
    }

    static String stripSuffix(String value) {
        if (value.endsWith("l") || value.endsWith("L") ||
            value.endsWith("f") || value.endsWith("F") ||
            value.endsWith("d") || value.endsWith("D")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}