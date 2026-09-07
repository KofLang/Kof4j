package dev.kof.compiler;

import java.util.Objects;

/**
 * Primitivos compartilhados do interpretador: normalização de retorno de
 * reflexão, predicados de tipo e coerções puras. Sem estado — usado por
 * {@link KofInterpreterOps}, {@link KofInterpreterRuntime},
 * {@link KofInterpreterConcurrency} e {@link KofInterpreterObjects}.
 */
final class KofInterpreterValues {

    private KofInterpreterValues() {}

    /** Sentinela: "não tratado por este colaborador" (dispatch continua). */
    static final Object NOT_HANDLED = new Object();

    /**
     * Normaliza retorno de reflexão para a representação da pilha JVM:
     * primitivo bool → Integer 0/1, primitivo char → Integer; tipos boxed
     * (Boolean/Character) ficam como objeto. Guiado pelo tipo de retorno da
     * IR — senão Boolean.valueOf(v) viraria 1 e println imprimiria "1".
     */
    static Object normalizeReturn(Object r, Type ret) {
        if (r == null) return null;
        if (ret instanceof Type.PrimitiveType pt) {
            if (Type.canonicalPrimitiveName(pt.name()).equals("bool") && r instanceof Boolean b) {
                return b ? 1 : 0;
            }
            if (Type.canonicalPrimitiveName(pt.name()).equals("char") && r instanceof Character c) {
                return (int) c;
            }
        }
        return r;
    }

    static Object normalizeReturn(Object r) {
        if (r instanceof Boolean b) return b ? 1 : 0;
        if (r instanceof Character c) return (int) c;
        return r;
    }

    static Class<?> classForType(Type.ClassType ct) throws ClassNotFoundException {
        String name = ct.packageName().isEmpty() ? ct.name()
                : ct.packageName() + "." + ct.name();
        return Class.forName(name);
    }

    static String pkgOf(String internal) {
        int sl = internal.lastIndexOf('/');
        return sl < 0 ? "" : internal.substring(0, sl).replace('/', '.');
    }

    static String simpleOf(String internal) {
        int sl = internal.lastIndexOf('/');
        return sl < 0 ? internal : internal.substring(sl + 1);
    }

    static boolean isRefType(Type t) {
        return t instanceof Type.ClassType || t instanceof Type.ArrayType
                || t instanceof Type.TypeVariable
                || (t instanceof Type.NullableType nt && !(nt.inner() instanceof Type.PrimitiveType));
    }

    static boolean isLongType(Type t) {
        return JvmOpCollections.isPrimitiveOf(t, "long");
    }

    static boolean isFloatType(Type t) {
        return JvmOpCollections.isPrimitiveOf(t, "float");
    }

    static boolean isDoubleType(Type t) {
        return JvmOpCollections.isPrimitiveOf(t, "double");
    }

    static int cmpResult(KofBinaryOp op, int c) {
        return switch (op) {
            case LT -> c < 0 ? 1 : 0;
            case LE -> c <= 0 ? 1 : 0;
            case GT -> c > 0 ? 1 : 0;
            case GE -> c >= 0 ? 1 : 0;
            case EQ -> c == 0 ? 1 : 0;
            case NE -> c != 0 ? 1 : 0;
            default -> 0;
        };
    }

    static boolean numEq(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            if (a instanceof Long || b instanceof Long) return x.longValue() == y.longValue();
            if (a instanceof Double || b instanceof Double || a instanceof Float || b instanceof Float) {
                return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
            }
            return x.intValue() == y.intValue();
        }
        return Objects.equals(a, b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static int compareRefs(Object a, Object b) {
        if (a instanceof Comparable ca && b instanceof Comparable cb) {
            return ((Comparable) ca).compareTo(cb);
        }
        throw new ClassCastException("not comparable");
    }

    /** Coerção de primitivo guiada pelo tipo da IR (unbox para slot primitivo). */
    static Object coerceFor(Type type, Object v) {
        if (v == null || type == null) return v;
        if (type instanceof Type.NullableType nt) return coerceFor(nt.inner(), v);
        if (!(type instanceof Type.PrimitiveType pt)) return v;
        return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "long" -> v instanceof Number n ? n.longValue() : v;
            case "double" -> v instanceof Number n ? n.doubleValue() : v;
            case "float" -> v instanceof Number n ? n.floatValue() : v;
            case "int", "char", "bool", "byte", "short" -> v instanceof Number n ? n.intValue() : v;
            default -> v;
        };
    }
}
