package dev.kof.compiler.nat;

import java.util.List;

/**
 * #1134 fatia 2 (16/11, docs/lab): o ALARGAMENTO de sp da saída aarch64 —
 * o fix do push/pop riscv que o issue mediu (o SIGBUS em hardware REAL; o
 * qemu-user não enforcementa o alinhamento). A forma SÓ-ALARGAMENTO (a
 * tentativa-2 medida): cada {@code sub/add sp, sp, #N} com N não múltiplo de
 * 16 alarga para o múltiplo seguinte; o macro {@code pop} inlina com o add
 * alargado. NENHUM offset é ajustado — a máquina de stack acessa os slots em
 * offset 0, e a aritmética de bloco dos call-sites cross já carrega o stride
 * 16 (a fatia 1 do plano). Os pares push/pop adjacentes já viraram
 * {@code mov} no peephole (antes deste passe) e não desalinham.
 */
final class NativeAarch64SpAligner {

    private NativeAarch64SpAligner() {}

    /** O imediato do {@code #N} da linha, ou null se não parseia (o contrato
     *  do passe: linha não-parseável fica byte-idêntica). O {@code matches}
     *  do chamador já garante dígitos — o catch existe para a PROVA estática
     *  (o CodeQL não enxerga o guard), não para engolir um erro real. */
    private static Long parseImm(String s) {
        int h = s.lastIndexOf('#');
        if (h < 0) return null;
        try {
            return Long.parseLong(s.substring(h + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Aplica o alargamento; retorna a contagem de sítios reescritos. */
    static int align(List<String> lines) {
        int rewritten = 0;
        boolean inMacro = false;
        boolean sawPopMacro = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String s = line.strip();
            if (s.isEmpty() || s.startsWith("//") || s.startsWith(".loc")) continue;
            if (s.startsWith(".macro")) {
                inMacro = true;
                if (s.contains("pop")) sawPopMacro = true;
                continue;
            }
            if (s.startsWith(".endm")) { inMacro = false; continue; }
            if (inMacro) continue;   // o corpo do macro pop não é reescrito
            // sub sp, sp, #N  → o próximo múltiplo de 16 (o push/frame alarga)
            if (s.matches("^sub\\s+sp,\\s*sp,\\s*#\\d+$")) {
                Long n = parseImm(s);
                if (n == null) continue;
                long up = (n + 15) & ~15L;
                if (up != n) {
                    lines.set(i, line.replaceFirst("#\\d+", "#" + up));
                    rewritten++;
                }
                continue;
            }
            // add sp, sp, #N  → o próximo múltiplo de 16 (o pop/restore alarga)
            if (s.matches("^add\\s+sp,\\s*sp,\\s*#\\d+$")) {
                Long n = parseImm(s);
                if (n == null) continue;
                long up = (n + 15) & ~15L;
                if (up != n) {
                    lines.set(i, line.replaceFirst("#\\d+", "#" + up));
                    rewritten++;
                }
                continue;
            }
            // o call-site do macro pop: inlina com o ldr no offset 0 (o slot
            // fica em [sp]) + o add alargado (libera os 16 do push alargado)
            if (sawPopMacro && s.matches("^pop\\s+[xw]\\d+$")) {
                String reg = s.substring(3).trim();
                String indent = line.substring(0, line.length() - line.stripLeading().length());
                lines.set(i, indent + "ldr " + reg + ", [sp]");
                lines.add(i + 1, indent + "add sp, sp, #16");
                rewritten++;
                i++;   // a linha inserida não é reprocessada
                continue;
            }
        }
        return rewritten;
    }
}
