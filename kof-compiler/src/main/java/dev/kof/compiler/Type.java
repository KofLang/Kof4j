package dev.kof.compiler;

import java.util.List;
import java.util.Map;

public sealed interface Type {
    record PrimitiveType(String name, int sort) implements Type {
        public static final PrimitiveType BOOL = new PrimitiveType("bool", 1);
        public static final PrimitiveType BYTE = new PrimitiveType("byte", 5);
        public static final PrimitiveType SHORT = new PrimitiveType("short", 9);
        public static final PrimitiveType INT = new PrimitiveType("int", 10);
        public static final PrimitiveType LONG = new PrimitiveType("long", 11);
        public static final PrimitiveType FLOAT = new PrimitiveType("float", 6);
        public static final PrimitiveType DOUBLE = new PrimitiveType("double", 7);
        public static final PrimitiveType CHAR = new PrimitiveType("char", 2);
        public static final PrimitiveType VOID = new PrimitiveType("void", 0);
    }

    record ClassType(String packageName, String name, List<Type> typeArguments) implements Type {
        public String internalName() {
            if (packageName.isEmpty()) return name;
            return packageName.replace('.', '/') + "/" + name;
        }
    }

    record TypeVariable(String name) implements Type {
    }

    record FunctionType(List<Type> parameterTypes, Type returnType, String className) implements Type {
        FunctionType(List<Type> parameterTypes, Type returnType) {
            this(parameterTypes, returnType, null);
        }
    }

    record ArrayType(Type componentType) implements Type {
    }

    record WildcardType(Type bound, boolean upper) implements Type {
    }

    record UnknownType() implements Type {
        public static final UnknownType UNKNOWN = new UnknownType();
    }

    record NullableType(Type inner) implements Type {
    }

    static Type of(String name) {
        if (name == null) return UnknownType.UNKNOWN;
        // tipo de função: "(Int) -> Int" (bug 8)
        if (name.startsWith("(") && name.contains(" -> ")) {
            int rp = name.indexOf(')');
            int arrow = name.indexOf(" -> ");
            String paramsStr = rp > 1 ? name.substring(1, rp) : "";
            String retStr = name.substring(arrow + 4);
            List<Type> params = paramsStr.isEmpty() ? List.of()
                    : java.util.Arrays.stream(paramsStr.split(","))
                            .map(String::trim).map(Type::of).toList();
            return new FunctionType(params, Type.of(retStr));
        }
        if (name.endsWith("?")) {
            Type inner = of(name.substring(0, name.length() - 1));
            return new NullableType(inner);
        }
        if (name.endsWith("[]")) {
            Type component = of(name.substring(0, name.length() - 2));
            return new ArrayType(component);
        }
        if (name != null && name.contains("<")) {
            int lt = name.indexOf('<');
            String base = name.substring(0, lt);
            String argsStr = name.substring(lt + 1, name.lastIndexOf('>'));
            List<Type> args = java.util.Arrays.stream(argsStr.split(","))
                    .map(String::trim).map(Type::of).toList();
            if ("List".equals(base) || "ArrayList".equals(base)) return new ClassType("kof", "List", args);
            if ("Map".equals(base) || "HashMap".equals(base)) return new ClassType("kof", "Map", args);
            if ("Set".equals(base) || "HashSet".equals(base)) return new ClassType("kof", "Set", args);
            if ("Channel".equals(base)) return new ClassType("kof.concurrent", "Channel", args);
            if ("Handle".equals(base)) return new ClassType("kof.concurrent", "Handle", args);
            return new ClassType("", base, args);
        }
        return switch (name) {
            case "bool", "boolean", "Bool", "Boolean" -> PrimitiveType.BOOL;
            case "byte", "Byte" -> PrimitiveType.BYTE;
            case "short", "Short" -> PrimitiveType.SHORT;
            case "int", "Int" -> PrimitiveType.INT;
            case "long", "Long" -> PrimitiveType.LONG;
            case "float", "Float" -> PrimitiveType.FLOAT;
            case "double", "Double" -> PrimitiveType.DOUBLE;
            case "char", "Char" -> PrimitiveType.CHAR;
            case "void", "Void" -> PrimitiveType.VOID;
            case "string", "String" -> BuiltinTypes.STRING;
            case "Object" -> new ClassType("java.lang", "Object", List.of());
            default -> new ClassType("", name, List.of());
        };
    }

    static boolean isPrimitive(Type type) {
        return type instanceof PrimitiveType && !(type instanceof PrimitiveType p && "void".equals(p.name()));
    }

    static boolean isVoid(Type type) {
        return type instanceof PrimitiveType p && "void".equals(p.name());
    }

    static boolean isUnknown(Type type) {
        return type instanceof UnknownType;
    }

    static boolean isString(Type type) {
        return BuiltinTypes.isString(type);
    }

    static boolean isArray(Type type) {
        return type instanceof ArrayType;
    }

    // Tipos cuja divisão/resto por zero lança ArithmeticException no JVM
    // (float/double produzem Infinity/NaN e não são inteiros aqui).
    static boolean isInteger(Type type) {
        if (!(type instanceof PrimitiveType pt)) return false;
        return switch (canonicalPrimitiveName(pt.name())) {
            case "int", "long", "byte", "short", "char" -> true;
            default -> false;
        };
    }

    static String canonicalPrimitiveName(String name) {
        return switch (name) {
            case "bool", "boolean", "Bool", "Boolean" -> "bool";
            case "byte", "Byte" -> "byte";
            case "short", "Short" -> "short";
            case "int", "Int" -> "int";
            case "long", "Long" -> "long";
            case "float", "Float" -> "float";
            case "double", "Double" -> "double";
            case "char", "Char" -> "char";
            case "void", "Void" -> "void";
            default -> name;
        };
    }

    static String canonicalName(String name) {
        String canonical = canonicalPrimitiveName(name);
        if (!canonical.equals(name)) return canonical;
        if ("string".equals(name)) return "String";
        if ("list".equals(name) || "arraylist".equals(name)) return "List";
        if ("map".equals(name) || "hashmap".equals(name)) return "Map";
        if ("set".equals(name) || "hashset".equals(name)) return "Set";
        return name;
    }

    static Type arrayElementType(Type type) {
        if (type instanceof ArrayType at) return at.componentType();
        return UnknownType.UNKNOWN;
    }

    static Type fromJvmDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) return UnknownType.UNKNOWN;
        return parseJvmDescriptor(desc, 0).type();
    }

    /**
     * Parses ONE type starting at {@code pos} and reports the position just
     * after it. Used by the class-file parser to walk a method descriptor's
     * parameter list without assuming every field is one char (a bug that
     * mis-parsed 2+ object/long/double params).
     */
    public static ParseResult parseJvmDescriptorAt(String desc, int pos) {
        return parseJvmDescriptor(desc, pos);
    }

    static String describe(Type type) {
        return switch (type) {
            case PrimitiveType p -> p.name();
            case ClassType c -> c.name()
                    + (c.typeArguments().isEmpty() ? ""
                        : c.typeArguments().stream().map(Type::describe)
                            .collect(java.util.stream.Collectors.joining(", ", "<", ">")));
            case ArrayType a -> describe(a.componentType()) + "[]";
            case NullableType n -> describe(n.inner()) + "?";
            case UnknownType u -> "unknown";
            case TypeVariable v -> v.name();
            case FunctionType f -> "function";
            case WildcardType w -> "?";
        };
    }

    private static ParseResult parseJvmDescriptor(String desc, int pos) {
        if (pos >= desc.length()) return new ParseResult(UnknownType.UNKNOWN, pos);

        char c = desc.charAt(pos);

        return switch (c) {
            case 'B' -> new ParseResult(PrimitiveType.BYTE, pos + 1);
            case 'C' -> new ParseResult(PrimitiveType.CHAR, pos + 1);
            case 'D' -> new ParseResult(PrimitiveType.DOUBLE, pos + 1);
            case 'F' -> new ParseResult(PrimitiveType.FLOAT, pos + 1);
            case 'I' -> new ParseResult(PrimitiveType.INT, pos + 1);
            case 'J' -> new ParseResult(PrimitiveType.LONG, pos + 1);
            case 'S' -> new ParseResult(PrimitiveType.SHORT, pos + 1);
            case 'V' -> new ParseResult(PrimitiveType.VOID, pos + 1);
            case 'Z' -> new ParseResult(PrimitiveType.BOOL, pos + 1);
            case '[' -> {
                ParseResult inner = parseJvmDescriptor(desc, pos + 1);
                yield new ParseResult(new ArrayType(inner.type()), inner.pos());
            }
            case 'L' -> {
                int end = desc.indexOf(';', pos);
                if (end == -1) throw new IllegalArgumentException("Malformed descriptor: " + desc);
                String className = desc.substring(pos + 1, end);
                yield new ParseResult(parseClassName(className), end + 1);
            }
            default -> new ParseResult(UnknownType.UNKNOWN, pos + 1);
        };
    }

    private static Type parseClassName(String name) {
        if (name.isEmpty()) return UnknownType.UNKNOWN;
        String[] parts = name.split("/");
        String simpleName = parts[parts.length - 1];
        List<Type> args = new java.util.ArrayList<>();
        if (simpleName.contains("<")) {
            int lt = simpleName.indexOf('<');
            String base = simpleName.substring(0, lt);
            String argsStr = simpleName.substring(lt + 1, simpleName.lastIndexOf('>'));
            for (String arg : argsStr.split(",")) {
                arg = arg.trim();
                if (!arg.isEmpty() && !arg.contains(";")) {
                    args.add(parseJvmDescriptor("L" + arg + ";", 0).type());
                } else if (arg.endsWith(";")) {
                    args.add(parseJvmDescriptor(arg, 0).type());
                }
            }
            return new ClassType("", base, args);
        }
        return new ClassType("", simpleName, List.of());
    }

    public record ParseResult(Type type, int pos) {}

    // ── Fase D (Type Recovery): assinatura JVM com genéricos (JVMS 4.7.9.1) ──
    // O descriptor apaga genéricos (erasure); só o atributo Signature os
    // preserva. parseClassName trata "Foo<Bar>" textual, mas o Signature real
    // é aninhado (Lx/List<Ljava/lang/Integer;>;) — exige parser recursivo.

    /** Parses a single field/class type signature (e.g. "Ljava/util/List<...>;"). */
    public static Type fromJvmSignature(String sig) {
        if (sig == null || sig.isEmpty()) return UnknownType.UNKNOWN;
        return parseSignatureType(sig, 0).type();
    }

    public record SignatureParseResult(Type returnType, List<Type> parameterTypes) {}

    /** Parses a full method signature "(TT;Ljava/lang/String;)Ljava/util/List<...>;". */
    public static SignatureParseResult parseMethodSignature(String sig) {
        if (sig == null || sig.isEmpty() || sig.charAt(0) != '(') {
            return new SignatureParseResult(UnknownType.UNKNOWN, List.of());
        }
        int pos = 1;
        List<Type> params = new java.util.ArrayList<>();
        while (pos < sig.length() && sig.charAt(pos) != ')') {
            ParseResult pr = parseSignatureType(sig, pos);
            params.add(pr.type());
            pos = pr.pos();
        }
        if (pos < sig.length() && sig.charAt(pos) == ')') pos++;
        Type ret = pos < sig.length() ? parseSignatureType(sig, pos).type() : PrimitiveType.VOID;
        return new SignatureParseResult(ret, params);
    }

    private static ParseResult parseSignatureType(String sig, int pos) {
        if (pos >= sig.length()) return new ParseResult(UnknownType.UNKNOWN, pos);
        char c = sig.charAt(pos);
        return switch (c) {
            case 'B' -> new ParseResult(PrimitiveType.BYTE, pos + 1);
            case 'C' -> new ParseResult(PrimitiveType.CHAR, pos + 1);
            case 'D' -> new ParseResult(PrimitiveType.DOUBLE, pos + 1);
            case 'F' -> new ParseResult(PrimitiveType.FLOAT, pos + 1);
            case 'I' -> new ParseResult(PrimitiveType.INT, pos + 1);
            case 'J' -> new ParseResult(PrimitiveType.LONG, pos + 1);
            case 'S' -> new ParseResult(PrimitiveType.SHORT, pos + 1);
            case 'Z' -> new ParseResult(PrimitiveType.BOOL, pos + 1);
            case 'V' -> new ParseResult(PrimitiveType.VOID, pos + 1);
            case '[' -> {
                ParseResult inner = parseSignatureType(sig, pos + 1);
                yield new ParseResult(new ArrayType(inner.type()), inner.pos());
            }
            case 'T' -> {
                int end = sig.indexOf(';', pos);
                if (end == -1) throw new IllegalArgumentException("Malformed signature: " + sig);
                yield new ParseResult(new TypeVariable(sig.substring(pos + 1, end)), end + 1);
            }
            case '*' -> new ParseResult(new WildcardType(null, false), pos + 1);
            case '+' -> {
                ParseResult bound = parseSignatureType(sig, pos + 1);
                yield new ParseResult(new WildcardType(bound.type(), true), bound.pos());
            }
            case '-' -> {
                ParseResult bound = parseSignatureType(sig, pos + 1);
                yield new ParseResult(new WildcardType(bound.type(), false), bound.pos());
            }
            case 'L' -> parseClassSignature(sig, pos);
            default -> new ParseResult(UnknownType.UNKNOWN, pos + 1);
        };
    }

    private static ParseResult parseClassSignature(String sig, int pos) {
        // 'L' PackageSpecifier SimpleClassTypeSignature {ClassTypeSignatureSuffix} ';'
        // Ex.: Ljava/util/Map<Ljava/lang/String;Ljava/util/List<Ljava/lang/Integer;>;>;
        StringBuilder name = new StringBuilder();
        List<Type> args = new java.util.ArrayList<>();
        int i = pos + 1;
        while (i < sig.length() && sig.charAt(i) != ';') {
            char c = sig.charAt(i);
            if (c == '<') {
                TypeArgsResult ta = parseTypeArguments(sig, i + 1);
                args.addAll(ta.args());
                i = ta.pos();
            } else if (c == '.') {
                // ClassTypeSignatureSuffix: .Inner<...> — o simples nome vira Inner
                i++;
            } else {
                name.append(c);
                i++;
            }
        }
        if (i < sig.length() && sig.charAt(i) == ';') i++;
        String full = name.toString();
        // Package usa '/', aninhamento usa '.' — o nome simples é o último
        // segmento de ambos (Map$Entry / Map.Entry → "Entry").
        String[] parts = full.split("[/.]");
        String simple = parts[parts.length - 1];
        String pkg = parts.length > 1
                ? String.join(".", java.util.Arrays.copyOf(parts, parts.length - 1)) : "";
        return new ParseResult(new ClassType(pkg, simple, args), i);
    }

    record TypeArgsResult(List<Type> args, int pos) {}

    private static TypeArgsResult parseTypeArguments(String sig, int pos) {
        List<Type> collected = new java.util.ArrayList<>();
        int i = pos;
        while (i < sig.length() && sig.charAt(i) != '>') {
            ParseResult pr = parseSignatureType(sig, i);
            collected.add(pr.type());
            i = pr.pos();
        }
        if (i < sig.length() && sig.charAt(i) == '>') i++;
        return new TypeArgsResult(collected, i);
    }
}
