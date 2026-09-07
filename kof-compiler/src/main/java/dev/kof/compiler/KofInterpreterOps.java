package dev.kof.compiler;

import java.lang.reflect.Array;

/**
 * Aritmética, comparação, unary, instanceof e arrays do interpretador —
 * semântica JVM exata do emitter ({@code JvmOpEmitter.emitBinary}/
 * {@code emitConditionalJump}). Estado-free além do acesso às classes Kof
 * para {@code instanceof}.
 */
final class KofInterpreterOps {

    private final KofInterpreter interp;

    KofInterpreterOps(KofInterpreter interp) {
        this.interp = interp;
    }

    Object binary(KofBinary kb, Object a, Object b) {
        Type t = kb.operandType();
        if (kb.op() == KofBinaryOp.EQ || kb.op() == KofBinaryOp.NE) {
            // referência: igual ao IF_ACMPEQ/IF_ACMPNE do bytecode (identidade)
            boolean eq = KofInterpreterValues.isRefType(t) ? a == b
                    : KofInterpreterValues.numEq(a, b);
            boolean want = kb.op() == KofBinaryOp.EQ;
            return (eq == want) ? 1 : 0;
        }
        if (KofInterpreterValues.isRefType(t) && (kb.op() == KofBinaryOp.LT
                || kb.op() == KofBinaryOp.GT || kb.op() == KofBinaryOp.LE
                || kb.op() == KofBinaryOp.GE)) {
            int c = KofInterpreterValues.compareRefs(a, b);
            return KofInterpreterValues.cmpResult(kb.op(), c);
        }
        if (KofInterpreterValues.isFloatType(t)) {
            double x = ((Number) a).doubleValue(), y = ((Number) b).doubleValue();
            return switch (kb.op()) {
                case ADD -> (float) (x + y);
                case SUB -> (float) (x - y);
                case MUL -> (float) (x * y);
                case DIV -> (float) (x / y);
                case MOD -> (float) (x % y);
                default -> KofInterpreterValues.cmpResult(kb.op(),
                        Float.compare((float) x, (float) y));
            };
        }
        if (KofInterpreterValues.isDoubleType(t)) {
            double x = ((Number) a).doubleValue(), y = ((Number) b).doubleValue();
            return switch (kb.op()) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                case DIV -> x / y;
                case MOD -> x % y;
                default -> KofInterpreterValues.cmpResult(kb.op(), Double.compare(x, y));
            };
        }
        if (KofInterpreterValues.isLongType(t)) {
            long x = ((Number) a).longValue(), y = ((Number) b).longValue();
            return switch (kb.op()) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                case DIV -> x / y;
                case MOD -> x % y;
                case AND -> x & y;
                case OR -> x | y;
                case XOR -> x ^ y;
                case SHL -> x << y;
                case SHR -> x >> y;
                case USHR -> x >>> y;
                default -> KofInterpreterValues.cmpResult(kb.op(), Long.compare(x, y));
            };
        }
        int x = KofInterpreter.unboxInt(a), y = KofInterpreter.unboxInt(b);
        return switch (kb.op()) {
            case ADD -> x + y;
            case SUB -> x - y;
            case MUL -> x * y;
            case DIV -> x / y;
            case MOD -> x % y;
            case AND -> x & y;
            case OR -> x | y;
            case XOR -> x ^ y;
            case SHL -> x << y;
            case SHR -> x >> y;
            case USHR -> x >>> y;
            default -> KofInterpreterValues.cmpResult(kb.op(), Integer.compare(x, y));
        };
    }

    boolean compare(KofComparison cmp, Type t, Object a, Object b) {
        if (KofInterpreterValues.isRefType(t)) {
            int c = (cmp == KofComparison.EQ || cmp == KofComparison.NE)
                    ? (java.util.Objects.equals(a, b) ? 0 : 1)
                    : KofInterpreterValues.compareRefs(a, b);
            return switch (cmp) {
                case EQ -> c == 0;
                case NE -> c != 0;
                case LT -> c < 0;
                case LE -> c <= 0;
                case GT -> c > 0;
                case GE -> c >= 0;
            };
        }
        if (KofInterpreterValues.isLongType(t)) {
            long x = ((Number) a).longValue(), y = ((Number) b).longValue();
            return switch (cmp) {
                case EQ -> x == y; case NE -> x != y; case LT -> x < y;
                case LE -> x <= y; case GT -> x > y; case GE -> x >= y;
            };
        }
        if (KofInterpreterValues.isFloatType(t) || KofInterpreterValues.isDoubleType(t)) {
            double x = ((Number) a).doubleValue(), y = ((Number) b).doubleValue();
            int c = Double.compare(x, y);
            return switch (cmp) {
                case EQ -> c == 0; case NE -> c != 0; case LT -> c < 0;
                case LE -> c <= 0; case GT -> c > 0; case GE -> c >= 0;
            };
        }
        int x = KofInterpreter.unboxInt(a), y = KofInterpreter.unboxInt(b);
        return switch (cmp) {
            case EQ -> x == y; case NE -> x != y; case LT -> x < y;
            case LE -> x <= y; case GT -> x > y; case GE -> x >= y;
        };
    }

    Object unary(KofUnary ku, Object v) {
        Type t = ku.operandType();
        return switch (ku.op()) {
            case NEG -> KofInterpreterValues.isLongType(t) ? -((Number) v).longValue()
                    : KofInterpreterValues.isFloatType(t) ? -((Number) v).floatValue()
                    : KofInterpreterValues.isDoubleType(t) ? -((Number) v).doubleValue()
                    : -KofInterpreter.unboxInt(v);
            case NOT -> KofInterpreter.unboxInt(v) == 0 ? 1 : 0;
            case I2L -> ((Number) v).longValue();
            case I2F -> ((Number) v).floatValue();
            case I2D -> ((Number) v).doubleValue();
            case I2C -> (char) KofInterpreter.unboxInt(v);
            case L2I -> ((Number) v).intValue();
            case L2F -> ((Number) v).floatValue();
            case L2D -> ((Number) v).doubleValue();
            case F2D -> ((Number) v).doubleValue();
            case D2F -> ((Number) v).floatValue();
            case D2I -> ((Number) v).intValue();
            case F2I -> ((Number) v).intValue();
            case D2L -> ((Number) v).longValue();
            case F2L -> ((Number) v).longValue();
        };
    }

    boolean instanceOf(Type type, Object v) {
        if (v == null) return false;
        if (type instanceof Type.ClassType ct) {
            if (v instanceof KofInterpreter.KofObj ko) {
                IRClass c = interp.kofClassOrNull(type);
                if (c != null) {
                    IRClass cur = ko.clazz;
                    while (cur != null) {
                        if (cur.name().equals(c.name())) return true;
                        for (String i : cur.interfaces()) {
                            if (i.equals(ct.internalName())) return true;
                        }
                        cur = interp.classByInternal(cur.superName());
                    }
                    return false;
                }
                return false;
            }
            try {
                return KofInterpreterValues.classForType(ct).isInstance(v);
            } catch (Throwable e) {
                return false;
            }
        }
        if (type instanceof Type.ArrayType) return v.getClass().isArray();
        return false;
    }

    Object newArray(Type elementType, int size) {
        if (elementType instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "long" -> new long[size];
                case "double" -> new double[size];
                case "float" -> new float[size];
                case "char" -> new char[size];
                case "bool" -> new boolean[size];
                default -> new int[size];
            };
        }
        Class<?> comp = Object.class;
        if (elementType instanceof Type.ClassType ct) {
            try {
                comp = KofInterpreterValues.classForType(ct);
            } catch (Throwable ignored) {
            }
        }
        return Array.newInstance(comp, size);
    }

    Object arrayLoad(KofArrayLoad al, Object arr, int idx) {
        Object v = Array.get(arr, idx);
        Type t = al.elementType();
        if (t instanceof Type.PrimitiveType pt && Type.canonicalPrimitiveName(pt.name()).equals("bool")) {
            return ((Boolean) v) ? 1 : 0;
        }
        return v;
    }

    void arrayStore(KofArrayStore as, Object arr, int idx, Object v) {
        Type t = as.elementType();
        if (t instanceof Type.PrimitiveType pt && Type.canonicalPrimitiveName(pt.name()).equals("bool")) {
            Array.set(arr, idx, KofInterpreter.unboxInt(v) != 0);
            return;
        }
        Array.set(arr, idx, v);
    }
}
