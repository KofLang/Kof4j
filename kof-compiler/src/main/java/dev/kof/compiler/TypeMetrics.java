package dev.kof.compiler;
import dev.kof.compiler.jvm.JvmBackend;
import dev.kof.compiler.nat.NativeBackend;

import java.util.List;

/**
 * Métricas e utilitários puros sobre {@link Type} — sem estado de compilação.
 * Classe COMPARTILHADA (contrato DRY do PLAN-SOLID-500): usada por
 * agente-idiomatic (CompilerDriver/NativeBackend) e por fixes-for-kofagent
 * (JvmBackend/SemanticAnalyzer). NUNCA duplicar estes predicados.
 */
public final class TypeMetrics {

    private TypeMetrics() {}

    static boolean isPrimitiveType(Type type) {
        if (type instanceof Type.NullableType nt) return isPrimitiveType(nt.inner());
        return type instanceof Type.PrimitiveType pt && !"void".equals(pt.name());
    }

    /**
     * §295(b): `Nullable(primitivo)` GENUÍNO (excluindo void) — CRÚ, sem o
     * peek de {@link #isPrimitiveType} que desembrulha o wrapper. Os gates de
     * box ESCRITOR (VarDecl/assign/compound/increment) precisam dos dois:
     * slot = isNullablePrimitive (referência física desde o Commit B) e valor
     * = PrimitiveType cru (um valor já Nullable chega boxed — re-boxar é o
     * NoSuchMethodError do §294-2a).
     */
    static boolean isNullablePrimitive(Type type) {
        return type instanceof Type.NullableType nt
                && nt.inner() instanceof Type.PrimitiveType pt && !Type.isVoid(pt);
    }

    static boolean isCharType(Type type) {
        if (type instanceof Type.NullableType nt) return isCharType(nt.inner());
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
        if (t instanceof Type.NullableType nt) return isNumeric(nt.inner());
        if (!(t instanceof Type.PrimitiveType pt)) return false;
        String name = Type.canonicalPrimitiveName(pt.name());
        return switch (name) {
            case "int", "long", "float", "double", "byte", "short", "char" -> true;
            default -> false;
        };
    }

    static String primitiveName(Type t) {
        if (t instanceof Type.NullableType nt) return primitiveName(nt.inner());
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
        // #720/§561 (D-KOF-BYTE-ARITH, maintainer 02/10): byte/short/char
        // promote to int in arithmetic (docs/language-reference/type-system.md
        // §3.2: double > float > long > int). Returning the LEFT operand here
        // kept `Byte + Byte` typed Byte, so the JVM boxed the un-narrowed
        // result and crashed in Byte.valueOf (AIOOBE) while JS/Native returned
        // the Int. int is the only remaining sub-int-dominant result.
        return Type.PrimitiveType.INT;
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
        // D-NULL-INTENT (supersede §125 opção A, DECISIONS.md 15/09):
        // Nullable(primitivo) agora é referência de verdade (boxed) — 1 slot,
        // como qualquer objeto. `Long?`/`Double?` NÃO são categoria-2; só o
        // primitivo NÃO-nullable (`Long`/`Double` cru) continua 2 slots.
        if (type instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType pt0
                && !Type.isVoid(pt0)) {
            return false;
        }
        if (type instanceof Type.NullableType nt) return isDoubleWidth(nt.inner());
        if (type instanceof Type.PrimitiveType pt) {
            return "long".equals(pt.name()) || "Long".equals(pt.name())
                    || "double".equals(pt.name()) || "Double".equals(pt.name());
        }
        return false;
    }

    public static Type boxedTypeFor(Type primitive) {
        // §519/#632 (UIW050): handle de kof.ui/midia APAGA para int no JVM —
        // nas bordas de referencia (Object slot, valueOf, `.equals` do I6) ele
        // e boxed como Integer, exatamente como o primitivo cru que espelha.
        if (primitive instanceof Type.NullableType nh) return boxedTypeFor(nh.inner());
        if (primitive instanceof Type.ClassType && (KofUi.isUiType(primitive) || KofMedia.isHandleType(primitive)))
            return new Type.ClassType("java.lang", "Integer", List.of());
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