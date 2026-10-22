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
            case "float" -> ((Number) x).floatValue() == ((Number) y).floatValue();
            case "double" -> ((Number) x).doubleValue() == ((Number) y).doubleValue();
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
        // §367: o ProcessResult do runtime gerado NAO e KofObj — sem este
        // ramo, o Script vazava "dev.kof.runtime.KofRuntime$ProcessResult@<hash>"
        // (Object.toString cru). A classe e carregada por URLClassLoader
        // proprio (KofInterpreter.loadRuntimeClass), entao o teste e por
        // nome; campos lidos por reflexao (public finals). Conteudo identico
        // ao toString do template JVM (paridade 4-alvos).
        if (v.getClass().getName().endsWith(".KofRuntime$ProcessResult")) {
            try {
                java.lang.reflect.Field ec = v.getClass().getField("exitCode");
                java.lang.reflect.Field so = v.getClass().getField("stdout");
                java.lang.reflect.Field se = v.getClass().getField("stderr");
                return "ProcessResult[exitCode=" + ec.getInt(v)
                        + ", stdout=" + trimTrailingNewline((String) so.get(v))
                        + ", stderr=" + trimTrailingNewline((String) se.get(v)) + "]";
            } catch (ReflectiveOperationException e) {
                return String.valueOf(v);
            }
        }
        if (v instanceof KofInterpreter.KofObj ko) {
            String own = userToString(ko);
            if (own != null) return own;
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
            // §749: um ENUM e uma classe REAL (§211) com o campo `kofName`
            // (CompilerEnumLowering) e um toString sintetico que o devolve —
            // o JVM/JS/nativo imprimem o NOME. O interpretador caia no
            // `Nome@hash` (a representacao interna vazava). Le o campo.
            if (ko.fields.containsKey(CompilerEnumLowering.NAME_FIELD)) {
                return String.valueOf(ko.fields.get(CompilerEnumLowering.NAME_FIELD));
            }
            return KofInterpreterValues.simpleOf(ko.internalName()) + "@"
                    + Integer.toHexString(System.identityHashCode(ko));
        }
        if (v instanceof Integer[] arr) return java.util.Arrays.toString(arr);
        return String.valueOf(v);
    }

    private static String userToString(KofInterpreter.KofObj ko) {
        if (ko.interp == null) return null;
        IRMethod m = ko.interp.members.findKofMethod(ko.clazz, "toString", 0, java.util.List.of());
        if (m == null) return null;
        try {
            return String.valueOf(ko.interp.invokeKof(ko.clazz, m, new Object[]{ko}, true));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static String trimTrailingNewline(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) end--;
        return s.substring(0, end);
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
