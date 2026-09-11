package dev.kof.compiler;
import dev.kof.compiler.jvm.JvmRecordEmitter;

import java.util.Objects;

/**
 * toString/equals/hashCode de objetos Kof — espelha exatamente o que o
 * {@code JvmRecordEmitter} gera no caminho compilado (record:
 * {@code Nome[a=1, b=2]}, equals por conteúdo de campos, hashCode 31*h+f).
 */
public final class KofInterpreterObjects {

    static final Object NOT_HANDLED = KofInterpreterValues.NOT_HANDLED;

    Object kofObjectMethod(String name, Object recv, Object[] args, IRClass owner) {
        if (!(recv instanceof KofInterpreter.KofObj ko)) return NOT_HANDLED;
        switch (name) {
            case "toString":
                return kofToString(ko);
            case "equals": {
                // bug 104a: só record tem equals de conteúdo (oracle JVM);
                // classe não-record é identidade (Thing(5).equals(Thing(5))
                // = false no JVM — antes o Script dava true).
                Object other = args[0];
                return objectEquals(ko, other) ? 1 : 0;
            }
            case "hashCode":
                return objectHash(ko);
            default:
                return NOT_HANDLED;
        }
    }

    static boolean objectEquals(KofInterpreter.KofObj ko, Object other) {
        if (other == ko) return true;
        if (!ko.isRecord()) return false;
        if (!(other instanceof KofInterpreter.KofObj ok)) return false;
        if (!ok.isRecord() || !ok.clazz.name().equals(ko.clazz.name())) return false;
        for (IRField f : ko.clazz.fields()) {
            Object x = ko.fields.get(f.name());
            Object y = ok.fields.get(f.name());
            if (!fieldEquals(f.type(), x, y)) return false;
        }
        return true;
    }

    static int objectHash(KofInterpreter.KofObj ko) {
        if (!ko.isRecord()) return System.identityHashCode(ko);
        int h = 1;
        for (IRField f : ko.clazz.fields()) {
            h = 31 * h + fieldHash(f.type(), ko.fields.get(f.name()));
        }
        return h;
    }

    static String objectToString(KofInterpreter.KofObj ko) {
        return kofToString(ko);
    }

    private static boolean fieldEquals(Type t, Object x, Object y) {
        if (t instanceof Type.PrimitiveType pt) {
            return numEq(pt, x, y);
        }
        return Objects.equals(x, y);
    }

    private static boolean numEq(Type.PrimitiveType pt, Object x, Object y) {
        if (x == null || y == null) return Objects.equals(x, y);
        return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "long" -> ((Number) x).longValue() == ((Number) y).longValue();
            case "float" -> Float.compare(((Number) x).floatValue(), ((Number) y).floatValue()) == 0;
            case "double" -> Double.compare(((Number) x).doubleValue(), ((Number) y).doubleValue()) == 0;
            default -> ((Number) x).intValue() == ((Number) y).intValue();
        };
    }

    private static int fieldHash(Type t, Object v) {
        if (v == null) return 0;
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "long" -> Long.hashCode(((Number) v).longValue());
                case "float" -> Float.floatToIntBits(((Number) v).floatValue());
                case "double" -> Double.hashCode(((Number) v).doubleValue());
                default -> ((Number) v).intValue();
            };
        }
        return Objects.hashCode(v);
    }

    static String kofToString(Object v) {
        if (v == null) return "null";
        if (v instanceof KofInterpreter.KofObj ko) {
            if (ko.isRecord()) {
                String simple = KofInterpreterValues.simpleOf(ko.internalName());
                StringBuilder sb = new StringBuilder(simple).append('[');
                boolean first = true;
                for (IRField f : ko.clazz.fields()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append(f.name()).append('=').append(appendValue(f.type(), ko.fields.get(f.name())));
                }
                return sb.append(']').toString();
            }
            return KofInterpreterValues.simpleOf(ko.internalName()) + "@"
                    + Integer.toHexString(System.identityHashCode(ko));
        }
        if (v instanceof Integer[] arr) return java.util.Arrays.toString(arr);
        return String.valueOf(v);
    }

    private static String appendValue(Type t, Object v) {
        if (v == null) return "null";
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "bool" -> ((Number) v).intValue() != 0 ? "true" : "false";
                case "char" -> String.valueOf((char) ((Number) v).intValue());
                default -> String.valueOf(v);
            };
        }
        return kofToString(v);
    }
}
