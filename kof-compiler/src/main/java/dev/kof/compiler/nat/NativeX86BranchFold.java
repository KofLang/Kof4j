package dev.kof.compiler.nat;

import java.util.List;

/**
 * §7 x86_64 branch/flag folds for the x86-64 peephole, extracted from
 * {@link NativeX86Peephole} to keep it under the 500-line gate: drop a
 * jump-to-next, invert a conditional jump over a jump, the frame teardown to
 * {@code leave}, {@code cmp $0} to {@code test}, and a redundant flag test after
 * an and/or/xor/test. All are exact by construction.
 */
final class NativeX86BranchFold {

    private NativeX86BranchFold() {}

    /**
     * §7 x86_64 ("lower-cost instruction selection"): an unconditional
     * {@code jmp L} immediately followed by the label {@code L:} jumps to the very
     * next instruction — the jump is a no-op and is dropped. Only {@code .loc}
     * debug lines may sit between them.
     */
    static int dropJumpToNext(List<String> lines) {
        int dropped = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String j = lines.get(i).trim();
                if (!j.startsWith("jmp ")) continue;
                String target = j.substring("jmp ".length()).trim();
                int k = i + 1;
                while (k < lines.size() && lines.get(k).trim().startsWith(".loc ")) k++;
                if (k >= lines.size()) continue;
                if (!lines.get(k).trim().equals(target + ":")) continue;
                lines.remove(i);
                dropped++;
                changed = true;
                break;
            }
        }
        return dropped;
    }

    /** The inverse of an x86 condition code ({@code je}<->{@code jne}, {@code jl}<->{@code jge},
     *  {@code jle}<->{@code jg}, {@code jb}<->{@code jae}, {@code jbe}<->{@code ja}). */
    private static final java.util.Map<String, String> INVERT = java.util.Map.ofEntries(
            java.util.Map.entry("je", "jne"), java.util.Map.entry("jne", "je"),
            java.util.Map.entry("jz", "jnz"), java.util.Map.entry("jnz", "jz"),
            java.util.Map.entry("jl", "jge"), java.util.Map.entry("jge", "jl"),
            java.util.Map.entry("jle", "jg"), java.util.Map.entry("jg", "jle"),
            java.util.Map.entry("jb", "jae"), java.util.Map.entry("jae", "jb"),
            java.util.Map.entry("jbe", "ja"), java.util.Map.entry("ja", "jbe"),
            java.util.Map.entry("js", "jns"), java.util.Map.entry("jns", "js"),
            java.util.Map.entry("jp", "jnp"), java.util.Map.entry("jnp", "jp"));

    /**
     * §7 x86_64 ("lower-cost instruction selection"): {@code jcc X} + {@code jmp Y}
     * where {@code X} is the label IMMEDIATELY after the {@code jmp} means "if cc go
     * to X (fall through), else to Y" — which is exactly {@code j!cc Y}. Invert the
     * condition and drop the {@code jmp}: one instruction fewer per site. Only when
     * {@code X != Y} (otherwise {@code jcc X} is itself a jump-to-next, handled by
     * {@link #dropJumpToNext}). Only {@code .loc}/blank lines may separate the three.
     */
    static int invertJumpOverJump(List<String> lines) {
        int folded = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 2 < lines.size(); i++) {
                String jcc = lines.get(i).trim();
                int sp = jcc.indexOf(' ');
                if (sp < 0) continue;
                String mnem = jcc.substring(0, sp);
                String inv = INVERT.get(mnem);
                if (inv == null) continue;
                String xLabel = jcc.substring(sp + 1).trim();
                if (xLabel.startsWith("*")) continue; // indirect: skip
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                String jmp = lines.get(j).trim();
                if (!jmp.startsWith("jmp ") || jmp.substring(4).trim().startsWith("*")) continue;
                String yLabel = jmp.substring("jmp ".length()).trim();
                if (xLabel.equals(yLabel)) continue;
                int k = j + 1;
                while (k < lines.size() && lines.get(k).trim().startsWith(".loc ")) k++;
                if (k >= lines.size()) continue;
                if (!lines.get(k).trim().equals(xLabel + ":")) continue;
                String indent = lines.get(i).substring(0, lines.get(i).length() - lines.get(i).stripLeading().length());
                lines.set(i, indent + inv + " " + yLabel);
                lines.remove(j);
                folded++;
                changed = true;
                break;
            }
        }
        return folded;
    }

    static int epilogueToLeave(List<String> lines) {
        int folded = 0;
        for (int i = 0; i + 1 < lines.size(); i++) {
            if (!lines.get(i).trim().equals("movq %rbp, %rsp")) continue;
            if (!lines.get(i + 1).trim().equals("popq %rbp")) continue;
            lines.set(i, "    leave");
            lines.remove(i + 1);
            folded++;
        }
        return folded;
    }

    static int cmpZeroToTest(List<String> lines) {
        int folded = 0;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            int sp = t.indexOf(' ');
            if (sp < 0) continue;
            String mnem = t.substring(0, sp);
            boolean wide;
            if (mnem.equals("cmpl")) wide = false;
            else if (mnem.equals("cmpq")) wide = true;
            else continue;
            String rest = t.substring(sp + 1);
            int comma = rest.indexOf(',');
            if (comma < 0) continue;
            String src = rest.substring(0, comma).trim();
            String dst = rest.substring(comma + 1).trim();
            if (!src.equals("$0") || !dst.startsWith("%")) continue;
            // A 64-bit register name is a key of REG32 (it maps to its 32-bit name).
            if (wide != NativeX86Peephole.REG32.containsKey(dst)) continue; // width must match the register
            String test = wide ? "testq" : "testl";
            lines.set(i, "    " + test + " " + dst + ", " + dst);
            folded++;
        }
        return folded;
    }

    /**
     * §7 x86_64 ("efficient use of flags"): {@code and/or/xor/test <src>, %R}
     * immediately followed by {@code testl/testq %R, %R} (or {@code cmpl $0, %R}) is
     * redundant — the AND/OR/XOR/TEST already set SF/ZF/PF from the SAME register and
     * clear CF/OF exactly as {@code test %R,%R} does, so the following flag-only test
     * is a no-op and is dropped. ONLY and/or/xor/test qualify: {@code add}/{@code sub}
 * set CF (carry/borrow) differently from {@code test} (CF=0), so they are NOT
 * folded (a following {@code jb}/{@code jae} would change meaning).
     */
    static int dropRedundantFlagTest(List<String> lines) {
        int folded = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String prev = lines.get(i).trim();
                int sp = prev.indexOf(' ');
                if (sp < 0) continue;
                String mnem = prev.substring(0, sp);
                boolean wide;
                if (mnem.equals("andl") || mnem.equals("orl") || mnem.equals("xorl")
                        || mnem.equals("testl")) {
                    wide = false;
                } else if (mnem.equals("andq") || mnem.equals("orq") || mnem.equals("xorq")
                        || mnem.equals("testq")) {
                    wide = true;
                } else {
                    continue;
                }
                // The flag-producing op must write the FULL register 32/64 bits, so the
                // width name is the register itself (e.g. `andl $mask, %eax` / `%r8d`).
                String rest = prev.substring(sp + 1);
                int comma = rest.indexOf(',');
                if (comma < 0) continue;
                String dst = rest.substring(comma + 1).trim();
                if (!dst.startsWith("%")) continue;
                if (wide != NativeX86Peephole.REG32.containsKey(dst)) continue;
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                String t = lines.get(j).trim();
                if (isSelfFlagTest(t, dst)) {
                    lines.remove(j);
                    folded++;
                    changed = true;
                    break;
                }
            }
        }
        return folded;
    }

    /** True when {@code t} is a flag-only test of {@code reg} alone
     *  ({@code testl %reg,%reg} or {@code cmpl $0, %reg}). */
    private static boolean isSelfFlagTest(String t, String reg) {
        if (t.equals("testl " + reg + ", " + reg) || t.equals("testq " + reg + ", " + reg)) {
            return true;
        }
        return t.equals("cmpl $0, " + reg) || t.equals("cmpq $0, " + reg);
    }
}
