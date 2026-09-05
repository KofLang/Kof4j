package dev.kof.compiler;

import java.util.List;

/**
 * Métricas e utilitários puros sobre {@link Type} — sem estado de compilação.
 * Classe COMPARTILHADA (contrato DRY do PLAN-SOLID-500): usada por
 * agente-idiomatic (CompilerDriver/NativeBackend) e por fixes-for-kofagent
 * (JvmBackend/SemanticAnalyzer). NUNCA duplicar estes predicados.
 */
final class TypeMetrics {

    private TypeMetrics() {}

    static boolean isPrimitiveType(Type type) {
        return type instanceof Type.PrimitiveType pt && !"void".equals(pt.name());
    }

    static boolean isCharType(Type type) {
        return type instanceof Type.PrimitiveType pt
                && ("char".equals(pt.name()) || "Char".equals(pt.name()));
    }

    static KofBinaryOp mapArithmeticOp(String op) {
        return switch (op) {
            case "+" -> KofBinaryOp.ADD;
            case "-" -> KofBinaryOp.SUB;
            case "*" -> KofBinaryOp.MUL;
            case "/" -> KofBinaryOp.DIV;
            case "%" -> KofBinaryOp.MOD;
            case "==" -> KofBinaryOp.EQ;
            case "!=" -> KofBinaryOp.NE;
            case "<" -> KofBinaryOp.LT;
            case "<=" -> KofBinaryOp.LE;
            case ">" -> KofBinaryOp.GT;
            case ">=" -> KofBinaryOp.GE;
            default -> KofBinaryOp.ADD;
        };
    }

    static boolean isNumeric(Type t) {
        if (!(t instanceof Type.PrimitiveType pt)) return false;
        String name = Type.canonicalPrimitiveName(pt.name());
        return switch (name) {
            case "int", "long", "float", "double", "byte", "short", "char" -> true;
            default -> false;
        };
    }

    static String primitiveName(Type t) {
        if (t instanceof Type.PrimitiveType pt) {
            return Type.canonicalPrimitiveName(pt.name());
        }
        return "";
    }

    static Type commonNumericType(Type a, Type b) {
        String an = primitiveName(a);
        String bn = primitiveName(b);
        if (an.equals("double") || an.equals("Double") || bn.equals("double") || bn.equals("Double")) {
            return Type.PrimitiveType.DOUBLE;
        }
        if (an.equals("float") || an.equals("Float") || bn.equals("float") || bn.equals("Float")) {
            return Type.PrimitiveType.FLOAT;
        }
        if (an.equals("long") || an.equals("Long") || bn.equals("long") || bn.equals("Long")) {
            return Type.PrimitiveType.LONG;
        }
        return a instanceof Type.PrimitiveType ? a : Type.PrimitiveType.INT;
    }

    /** Compatibilidade largura para fallback de resolução de construtor. */
    static int primWidth(Type.PrimitiveType pt) {
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

    static boolean isComparisonOp(String op) {
        return ">".equals(op) || "<".equals(op) || ">=".equals(op) || "<=".equals(op)
                || "==".equals(op) || "!=".equals(op);
    }

    static boolean isFloatingPoint(Type type) {
        return type instanceof Type.PrimitiveType pt
                && ("float".equals(pt.name()) || "double".equals(pt.name()));
    }

    static boolean isInteger(Type t) {
        if (!(t instanceof Type.PrimitiveType pt)) return false;
        String name = Type.canonicalPrimitiveName(pt.name());
        return "int".equals(name) || "long".equals(name)
                || "byte".equals(name) || "short".equals(name) || "char".equals(name);
    }

    static boolean isDoubleWidth(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return "long".equals(pt.name()) || "Long".equals(pt.name())
                    || "double".equals(pt.name()) || "Double".equals(pt.name());
        }
        return false;
    }

    static Type boxedTypeFor(Type primitive) {
        if (primitive instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "char", "Char" -> new Type.ClassType("java.lang", "Integer", List.of());
                case "long", "Long" -> new Type.ClassType("java.lang", "Long", List.of());
                case "float", "Float" -> new Type.ClassType("java.lang", "Float", List.of());
                case "double", "Double" -> new Type.ClassType("java.lang", "Double", List.of());
                case "boolean", "bool", "Bool" -> new Type.ClassType("java.lang", "Boolean", List.of());
                case "byte", "Byte" -> new Type.ClassType("java.lang", "Byte", List.of());
                case "short", "Short" -> new Type.ClassType("java.lang", "Short", List.of());
                default -> Type.UnknownType.UNKNOWN;
            };
        }
        return Type.UnknownType.UNKNOWN;
    }
}