package dev.kof.compiler;
import dev.kof.compiler.jvm.JvmOpCollections;

import java.util.Objects;

/**
 * Primitivos compartilhados do interpretador: normalização de retorno de
 * reflexão, predicados de tipo e coerções puras. Sem estado — usado por
 * {@link KofInterpreterOps}, {@link KofInterpreterRuntime},
 * {@link KofInterpreterConcurrency} e {@link KofInterpreterObjects}.
 */
public final class KofInterpreterValues {

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
                || t instanceof Type.TypeVariable;
    }

    /** Wrapper {@code java.lang.Boolean} do slot Nullable(Bool) (box do §306). */
    static boolean isBoolWrapperType(Type t) {
        return t instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                && "Boolean".equals(ct.name());
    }

    /** Primitivo bool (destino do unbox do §306). */
    static boolean isBoolPrimitiveType(Type t) {
        return t instanceof Type.PrimitiveType pt && "bool".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    /**
     * ==/!= tolerante a null: Nullable(primitivo) (get de Map sem hit) compara
     * via Objects.equals (espelha o JVM: primitivo boxado vs null → acmp),
     * nunca unboxInt(null) (SG-008/bug 87).
     */
    static boolean eqAllowsNull(Type t) {
        return t instanceof Type.NullableType;
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

    /**
     * §625: comparação IEEE 754 de ponto flutuante (JVM {@code dcmpl/dcmpg}/
     * {@code fcmpl/fcmpg}, JS). Diferente de {@link #cmpResult} sobre
     * {@code Double.compare}/{@code Float.compare} (que ORDENAM NaN como
     * maior), NaN é FALSO em toda comparação ordenada e verdadeiro só em
     * {@code !=}. Usado pelo interpretador (alvo Script) para não divergir da
     * JVM/JS.
     */
    static int fpCmpResult(KofBinaryOp op, double x, double y) {
        return switch (op) {
            case LT -> x < y ? 1 : 0;
            case LE -> x <= y ? 1 : 0;
            case GT -> x > y ? 1 : 0;
            case GE -> x >= y ? 1 : 0;
            case EQ -> x == y ? 1 : 0;
            case NE -> x != y ? 1 : 0;
            default -> 0;
        };
    }

    static boolean numEq(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            if (a instanceof Long || b instanceof Long) return x.longValue() == y.longValue();
            if (a instanceof Double || b instanceof Double || a instanceof Float || b instanceof Float) {
                return x.doubleValue() == y.doubleValue();
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
            // §185: char[]/boolean[] rejeitam Integer no Array.set
            // ("argument type mismatch") — a coerção tem que produzir o tipo
            // REAL do slot (Character/Boolean), não um int homônimo.
            case "char" -> v instanceof Character c ? c
                    : v instanceof Number n ? (char) n.intValue() : v;
            case "bool" -> v instanceof Boolean b ? b
                    : v instanceof Number n ? n.intValue() != 0 : v;
            case "int" -> v instanceof Number n ? n.intValue() : v;
            // D-KOF-NET fatia 5 (02/10): byte/short nao podem agrupar com int —
            // Array.set em byte[]/short[] so ALARGA; um Integer no slot morria
            // "argument type mismatch" (o agrupado so parecia verde enquanto
            // newArray materializava byte[] como int[] — o bug gemeo abaixo).
            case "byte" -> v instanceof Number n ? n.byteValue() : v;
            case "short" -> v instanceof Number n ? n.shortValue() : v;
            default -> v;
        };
    }

    /**
     * Monta o array de locais de um frame a partir dos argumentos compactos:
     * `this` no slot 0 (quando `hasThis`) e cada parâmetro no slot REAL da IR
     * — `Double`/`Long` ocupam DOIS slots, igual ao bytecode JVM
     * ({@link TypeMetrics#isDoubleWidth}). Copiar `args` contíguo colocava o
     * 2º parâmetro largo no índice errado (lido como `null`) — §163.
     *
     * @param minSize tamanho já exigido pelos locais que a IR toca
     */
    static Object[] bindLocals(IRMethod m, Object[] args, boolean hasThis, int minSize) {
        int size = minSize;
        int paramExtent = hasThis ? 1 : 0;
        for (Type pt : m.parameterTypes()) paramExtent += TypeMetrics.isDoubleWidth(pt) ? 2 : 1;
        if (paramExtent > size) size = paramExtent;
        Object[] locals = new Object[size];
        int argi = 0;
        int slot = 0;
        if (hasThis && argi < args.length) locals[slot++] = args[argi++];
        for (Type pt : m.parameterTypes()) {
            if (argi >= args.length) break;
            locals[slot] = args[argi++];
            slot += TypeMetrics.isDoubleWidth(pt) ? 2 : 1;
        }
        for (; argi < args.length; argi++) locals[slot++] = args[argi];
        return locals;
    }
}
