package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * §814 — the JVM generic {@code Signature} attribute (JVMS 4.7.9.1), read just
 * far enough to carry a class's type arguments up its hierarchy. A fluent
 * builder that inherits {@code T region(String)} from
 * {@code Builder<T extends Builder<T, C>, C>} through
 * {@code S3Builder extends Builder<S3Builder, S3>} returns {@code S3Builder},
 * not the erased {@code Builder}; without the binding the chain lost its type
 * at the first inherited call ({@code build()} was {@code Object}).
 *
 * <p>Type variables the walk never bound (a method's own {@code <T>}, the
 * receiver's own parameters) stay erased: the caller keeps the descriptor.
 */
final class GenericSignatures {

    private GenericSignatures() {}

    /** The formal type parameter names of a class signature, in order. */
    static List<String> formalNames(String classSig) {
        List<String> names = new ArrayList<>();
        if (classSig == null || !classSig.startsWith("<")) return names;
        int depth = 0;
        int start = 1;
        for (int i = 1; i < classSig.length(); i++) {
            char c = classSig.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') {
                if (depth == 0) break;
                depth--;
            } else if (c == ':' && depth == 0 && start >= 0) {
                // an empty name is the `:` of an interface bound after a class bound
                if (i > start) names.add(classSig.substring(start, i));
                start = -1;
            } else if (c == ';' && depth == 0) {
                start = i + 1;
            }
        }
        return names;
    }

    /** The super class and super interfaces of a class signature, each with
     *  its type arguments as descriptors erased under {@code bindings}. */
    static List<Map.Entry<String, List<String>>> supertypes(String classSig, Map<String, String> bindings) {
        List<Map.Entry<String, List<String>>> out = new ArrayList<>();
        if (classSig == null) return out;
        int i = skipFormals(classSig);
        while (i < classSig.length() && classSig.charAt(i) == 'L') {
            int end = endOfType(classSig, i);
            String type = classSig.substring(i, end);
            out.add(Map.entry(internalName(type), typeArguments(type, bindings)));
            i = end;
        }
        return out;
    }

    /** The return type of a method signature erased under {@code bindings};
     *  null when it mentions a variable the bindings do not know. */
    static String returnDescriptor(String methodSig, Map<String, String> bindings) {
        if (methodSig == null) return null;
        int close = methodSig.indexOf(')', skipFormals(methodSig));
        if (close < 0) return null;
        String ret = methodSig.substring(close + 1);
        int caret = ret.indexOf('^');
        return erase(caret >= 0 ? ret.substring(0, caret) : ret, bindings);
    }

    /** A type signature as a descriptor; null for an unbound type variable. */
    static String erase(String sig, Map<String, String> bindings) {
        if (sig.isEmpty()) return null;
        return switch (sig.charAt(0)) {
            case 'T' -> bindings.get(sig.substring(1, sig.indexOf(';')));
            case '[' -> {
                String elem = erase(sig.substring(1), bindings);
                yield elem == null ? null : "[" + elem;
            }
            case 'L' -> "L" + internalName(sig.substring(0, endOfType(sig, 0))) + ";";
            default -> sig.substring(0, 1);
        };
    }

    private static List<String> typeArguments(String type, Map<String, String> bindings) {
        List<String> args = new ArrayList<>();
        int open = -1;
        int depth = 0;
        for (int i = 1; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<' && depth++ == 0) open = i;
            else if (c == '>') depth--;
            else if (c == '.' && depth == 0) open = -1; // only the last segment's arguments
        }
        if (open < 0) return args;
        int i = open + 1;
        while (i < type.length() && type.charAt(i) != '>') {
            char c = type.charAt(i);
            if (c == '*') {
                args.add("Ljava/lang/Object;");
                i++;
                continue;
            }
            int s = (c == '+' || c == '-') ? i + 1 : i;
            int end = endOfType(type, s);
            String erased = c == '-' ? null : erase(type.substring(s, end), bindings);
            args.add(erased != null ? erased : "Ljava/lang/Object;");
            i = end;
        }
        return args;
    }

    /** {@code Lpkg/Outer<..>.Inner<..>;} → {@code pkg/Outer$Inner}. */
    private static String internalName(String type) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 1; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (depth == 0 && c != ';') sb.append(c == '.' ? '$' : c);
        }
        return sb.toString();
    }

    /** Index just past the type signature that starts at {@code i}. */
    private static int endOfType(String s, int i) {
        char c = s.charAt(i);
        if (c == '[') return endOfType(s, i + 1);
        if (c == 'T') return s.indexOf(';', i) + 1;
        if (c != 'L') return i + 1;
        int depth = 0;
        for (int j = i + 1; j < s.length(); j++) {
            char d = s.charAt(j);
            if (d == '<') depth++;
            else if (d == '>') depth--;
            else if (d == ';' && depth == 0) return j + 1;
        }
        return s.length();
    }

    private static int skipFormals(String sig) {
        if (!sig.startsWith("<")) return 0;
        int depth = 0;
        for (int i = 0; i < sig.length(); i++) {
            char c = sig.charAt(i);
            if (c == '<') depth++;
            else if (c == '>' && --depth == 0) return i + 1;
        }
        return sig.length();
    }
}
