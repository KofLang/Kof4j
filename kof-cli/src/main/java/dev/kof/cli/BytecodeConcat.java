package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Recovery de String concat via {@code invokedynamic makeConcatWithConstants}
 * (Java 9+). O {@code ClassFileParser} reescreve as entradas tag-18 do CP p/
 * {@code "CONCAT:<receita>"} (atributo BootstrapMethods, JVMS 4.7.23); os
 * decoders ({@link BytecodeDecoder}/{@link BytecodeStatements}) consultam
 * aqui p/ aplicar a receita à pilha simbólica.
 *
 * <p>Receita: {@code \u0001} = placeholder de um argumento da pilha (na ordem
 * em que foram empurrados); texto literal vira parte {@code "..."};
 * {@code \u0002} = static-arg bootstrap (não carregamos) → recusar. O resultado
 * é uma expressão {@code a + "x" + b} — concat de String, que Kof resolve para
 * qualquer tipo (Int/String/Bool/Long/Double viram texto com {@code +}).
 */
final class BytecodeConcat {

    private BytecodeConcat() {
    }

    /** CP[idx] (tag 18) resolvido p/ "CONCAT:<receita>"? null se não for concat. */
    static String recipe(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (!e.startsWith("CONCAT:")) return null;
        return e.substring("CONCAT:".length());
    }

    /**
     * Aplica a receita consumindo um arg p/ cada {@code \u0001}. Retorna a
     * expressão {@code +} ou null (static-arg \u0002 / pilha curta) → o decoder
     * recusa o corpo → stub honesto.
     */
    static String apply(Deque<String> stack, String recipe) {
        int placeholders = 0;
        for (int i = 0; i < recipe.length(); i++) {
            char c = recipe.charAt(i);
            if (c == 1) placeholders++;
            else if (c == 2) return null;  // static-arg bootstrap — não carregamos
        }
        if (stack.size() < placeholders) return null;
        List<String> args = new ArrayList<>();
        for (int i = 0; i < placeholders; i++) args.add(0, stack.pop());  // ordem original
        List<String> parts = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int arg = 0;
        for (int i = 0; i < recipe.length(); i++) {
            char c = recipe.charAt(i);
            if (c == 1) {
                if (lit.length() > 0) { parts.add("\"" + escape(lit.toString()) + "\""); lit.setLength(0); }
                parts.add(args.get(arg++));
            } else {
                lit.append(c);
            }
        }
        if (lit.length() > 0) parts.add("\"" + escape(lit.toString()) + "\"");
        if (parts.isEmpty()) return "\"\"";
        return String.join(" + ", parts);
    }

    /** Escapa o texto p/ literal String Kof (aspas, barra, controles). */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
