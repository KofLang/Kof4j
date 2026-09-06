package dev.kof.compiler;

import java.util.Set;

/**
 * JsTypeMapper — helpers puros de nome/tipo do backend JS:
 * sanitização de identificadores, mapeamento de tipos Kof → JS e
 * literais. Stateless por design (REFACTOR-500 FASE 4).
 */
final class JsTypeMapper {

    private JsTypeMapper() {
    }

    /** Palavras reservadas do JS que não podem ser identificadores. */
        static final Set<String> RESERVED = Set.of(
            "class", "function", "var", "let", "const", "return", "if", "else", "while", "do",
            "for", "switch", "case", "default", "break", "continue", "new", "delete", "typeof",
            "instanceof", "in", "try", "catch", "finally", "throw", "this", "super", "null",
            "true", "false", "void", "static", "extends", "import", "export", "yield", "await",
            "async", "of", "arguments", "eval");

    static Type listElementType(Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    static String capitalizeUiFn(String name) {
        String rest = name.startsWith("kof_") ? name.substring(4) : name;
        StringBuilder sb = new StringBuilder("kof");
        boolean cap = true;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '_') {
                cap = true;
                continue;
            }
            sb.append(cap ? Character.toUpperCase(c) : c);
            cap = false;
        }
        return sb.toString();
    }

    static boolean isIntFamily(Type type) {
        if (!(type instanceof Type.PrimitiveType pt)) return false;
        return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "int", "byte", "short", "char" -> true;
            default -> false;
        };
    }

    // && / || booleanos → && / || JS (que short-circuitam nativamente);
    // & / | bitwise → & / | (avalia os dois lados). O operador lógico e o
    // bitwise caem no MESMO KofBinaryOp.AND/OR — o operandType (bool vs int)
    // é o que os distingue. Antes: && virava & (bitwise) no JS → sem
    // short-circuit (efeitos colaterais do lado de não deviam ser avaliados).
    static boolean isBoolOperand(Type type) {
        return type instanceof Type.PrimitiveType pt
                && "bool".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    static boolean isLongType(Type type) {
        if (!(type instanceof Type.PrimitiveType pt)) return false;
        return "long".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    static String literalText(Object value) {
        if (value instanceof Float f) return Float.toString(f);
        if (value instanceof Double d) return Double.toString(d);
        if (value instanceof Boolean b) return b ? "1" : "0";
        if (value instanceof String s) return jsStringLiteral(s);
        return String.valueOf(value);
    }

    static String jsStringLiteral(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    static String arrayFill(Type elementType) {
        if (elementType instanceof Type.PrimitiveType) return "0";
        return "null";
    }

    static String runtimeJsName(String kofName) {
        StringBuilder sb = new StringBuilder("kof");
        boolean upper = false;
        for (int i = 3; i < kofName.length(); i++) {
            char c = kofName.charAt(i);
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    static String ownerInternalName(Type type) {
        if (type instanceof Type.ClassType ct) return ct.internalName();
        return "";
    }

    static String classPackage(Type type) {
        if (type instanceof Type.ClassType ct) return ct.packageName();
        return "";
    }

    static String className(Type type) {
        if (type instanceof Type.ClassType ct) return ct.name();
        return "";
    }

    static String jsClassName(String internalName) {
        if (internalName == null || internalName.isEmpty()) return "Object";
        return sanitizeName(internalName.replace('/', '_'));
    }

    static String sanitizeName(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '$') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String result = sb.toString();
        if (result.isEmpty() || Character.isDigit(result.charAt(0))) {
            result = "_" + result;
        }
        if (RESERVED.contains(result)) {
            result = "_" + result;
        }
        return result;
    }

    static JsIr.JsExpression defaultForType(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "bool" -> new JsIr.JsNumber("0");
                default -> new JsIr.JsNumber("0");
            };
        }
        return new JsIr.JsNull();
    }

}