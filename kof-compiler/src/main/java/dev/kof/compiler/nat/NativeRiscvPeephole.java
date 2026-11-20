package dev.kof.compiler.nat;

import java.util.List;

/**
 * §7 RISC-V 64 of {@code docs/assembly-optimization-plan.md}
 * ("lower-cost instruction selection"): a text peephole over the riscv64 output.
 * The riscv stack machine pushes a value with {@code addi sp, sp, -8} +
 * {@code sd X, 0(sp)} and pops it with the {@code pop Y} assembler macro. When a
 * push is IMMEDIATELY followed by a pop, the triple is exactly {@code Y = X} with
 * the stack pointer unchanged:
 *
 * <pre>
 *   addi sp, sp, -8          mv Y, X      (or: nothing, when X == Y)
 *   sd   X, 0(sp)      ==&gt;
 *   pop  Y
 * </pre>
 *
 * <p>Only {@code .loc}/blank debug lines may sit between the three. This is
 * unconditionally safe: the net effect of the triple is {@code Y = X} with sp
 * restored; the stack pointer is unchanged either way. Because the aarch64
 * output is produced by translating this very riscv64 text, folding here
 * benefits BOTH cross targets.
 *
 * <p>The fold scan restarts from index 0 after each fold (leftmost-first, the
 * historical order, so the emitted text stays byte-identical). What changed is
 * the PER-LINE cost: the recognisers are allocation-free (the previous version
 * ran {@code String.strip()} plus two {@link java.util.regex.Matcher}s on every
 * line of every pass, which on the ~700 KB pruned runtime — thousands of folds
 * over tens of thousands of lines — dominated the riscv cross-compile). The
 * removals collapse to one {@code subList.clear()} per fold.
 */
final class NativeRiscvPeephole {

    private NativeRiscvPeephole() {
    }

    /** Collapses {@code addi sp,sp,-8} + {@code sd X,0(sp)} + {@code pop Y} into
     *  {@code mv Y, X} (or nothing when X == Y). Returns the number folded. */
    /** Drops {@code mv X, X} self-moves (a no-op). Returns the number dropped. */
    static int dropSelfMoves(List<String> lines) {
        int dropped = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String t = lines.get(i).strip();
            if (t.startsWith("mv ")) {
                String[] a = t.substring(3).split(",");
                if (a.length == 2 && a[0].strip().equals(a[1].strip())) {
                    lines.remove(i);
                    dropped++;
                }
            }
        }
        return dropped;
    }

    static int collapsePushPopPairs(List<String> lines) {
        final int n = lines.size();
        final boolean[] dead = new boolean[n];
        int folded = 0;
        int i = 0;
        while (i < n) {
            if (dead[i] || !isStackPush(lines.get(i))) {
                i++;
                continue;
            }
            int st = i + 1;
            while (st < n && dead[st]) st++;
            if (st >= n) break;
            String x = storeReg(lines.get(st));
            if (x == null) {
                i++;
                continue;
            }
            int k = st + 1;
            while (k < n && (dead[k] || isDebug(lines.get(k)))) k++;
            if (k >= n) {
                i++;
                continue;
            }
            String y = popReg(lines.get(k));
            if (y == null) {
                i++;
                continue;
            }
            if (x.equals(y)) {
                for (int z = i; z <= k; z++) dead[z] = true;
            } else {
                lines.set(i, leadingWhitespace(lines.get(k)) + "mv " + y + ", " + x);
                for (int z = st; z <= k; z++) dead[z] = true;
            }
            folded++;
            // Leftmost-first, exactly as the historical scan-from-zero: a fold
            // only removes lines at/after its own start, so no earlier position
            // becomes foldable — except the one candidate formed by the last
            // non-debug line before the removal (m - 1), which a self-move
            // removal can unblock.
            int m = i - 1;
            while (m >= 0 && (dead[m] || isDebug(lines.get(m)))) m--;
            i = m < 1 ? 0 : m - 1;
        }
        if (folded == 0) return 0;
        int w = 0;
        for (int r = 0; r < n; r++) {
            if (!dead[r]) lines.set(w++, lines.get(r));
        }
        lines.subList(w, n).clear();
        return folded;
    }

    /**
     * {@code ^\s*addi sp, sp, -8$} without the allocation of {@code strip()}.
     * (The old code stripped every line of every pass; that allocation, times
     * folds times lines, is what made the AV1 riscv faces cost minutes.)
     */
    static boolean isStackPush(String line) {
        int s = 0;
        int e = line.length();
        while (s < e && isWs(line.charAt(s))) s++;
        while (e > s && isWs(line.charAt(e - 1))) e--;
        return e - s == 15 && line.regionMatches(s, "addi sp, sp, -8", 0, 15);
    }

    /** The register of {@code ^\s*sd\s+(\S+),\s*0\(sp\)$}, or null. */
    static String storeReg(String line) {
        int len = line.length();
        int s = 0;
        while (s < len && isWs(line.charAt(s))) s++;
        if (!line.startsWith("sd", s)) return null;
        int p = s + 2;
        if (p >= len || !isWs(line.charAt(p))) return null;
        while (p < len && isWs(line.charAt(p))) p++;
        if (!line.endsWith("0(sp)")) return null;
        int q = len - 5;
        while (q > p && isWs(line.charAt(q - 1))) q--;
        if (q <= p || line.charAt(q - 1) != ',') return null;
        int regEnd = q - 1;
        if (regEnd <= p) return null;
        for (int z = p; z < regEnd; z++) {
            if (isWs(line.charAt(z))) return null;
        }
        return line.substring(p, regEnd);
    }

    /** The register of {@code ^\s*pop\s+(\S+)$}, or null. */
    static String popReg(String line) {
        int len = line.length();
        int s = 0;
        while (s < len && isWs(line.charAt(s))) s++;
        if (!line.startsWith("pop", s)) return null;
        int p = s + 3;
        if (p >= len || !isWs(line.charAt(p))) return null;
        while (p < len && isWs(line.charAt(p))) p++;
        if (p >= len) return null;
        for (int z = p; z < len; z++) {
            if (isWs(line.charAt(z))) return null;
        }
        return line.substring(p);
    }

    private static String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && isWs(line.charAt(i))) i++;
        return line.substring(0, i);
    }

    private static boolean isWs(char c) {
        return Character.isWhitespace(c);
    }

    private static boolean isDebug(String line) {
        int s = 0;
        int e = line.length();
        while (s < e && isWs(line.charAt(s))) s++;
        while (e > s && isWs(line.charAt(e - 1))) e--;
        if (s == e) return true;
        return (e - s >= 5 && line.regionMatches(s, ".loc ", 0, 5))
                || (e - s >= 6 && line.regionMatches(s, ".file ", 0, 6));
    }
}
