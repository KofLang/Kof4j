package dev.kof.compiler.nat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * §7 x86_64 of {@code docs/development/assembly-optimization-plan.md}
 * ("lower-cost instruction selection"): a text peephole over an emitted method
 * body. The x86-64 emitter is a stack machine — a value produced by one op is
 * {@code push}ed and consumed by the next op's {@code pop}. When a push is
 * IMMEDIATELY followed by a pop (only {@code .loc} debug lines between), the two
 * instructions are a register move:
 *
 * <pre>
 *   pushq %rbx          movq %rbx, %rax      (or: nothing, when src == dst)
 *   popq  %rax
 * </pre>
 *
 * <p>This is unconditionally safe: nothing runs between adjacent lines, so the
 * pair's net effect is exactly {@code dst = src} (the stack pointer is unchanged
 * either way). It removes one instruction and one stack round-trip per match —
 * common around binary operands and register-placed locals.
 */
final class NativeX86Peephole {

    private NativeX86Peephole() {
    }

    /**
     * Collapses {@code pushq}/{@code popq} pairs in {@code sb} from {@code from}
     * onward (the region one method appended), in place. Returns the number of
     * pairs collapsed (for tests/metrics).
     */
    static int collapsePushPopPairs(StringBuilder sb, int from) {
        String region = sb.substring(from);
        List<String> lines = new ArrayList<>(Arrays.asList(region.split("\n", -1)));
        int collapsed = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String push = lines.get(i).trim();
                if (!push.startsWith("pushq ")) continue;
                // find the next non-.loc line
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                String pop = lines.get(j).trim();
                if (!pop.startsWith("popq ")) continue;
                String src = push.substring("pushq ".length()).trim();
                String dst = pop.substring("popq ".length()).trim();
                // The destination is always a register in this emitter.
                if (!dst.startsWith("%")) continue;
                if (src.startsWith("$")) {
                    // An immediate fits the push only when it is an int or a small
                    // long (see emitLoadLiteral), so `movq $imm, %reg` (imm32,
                    // sign-extended) is exact.
                    lines.set(i, "    movq " + src + ", " + dst);
                    lines.remove(j);
                } else if (src.startsWith("%")) {
                    // Register-register: exact, avoids memory encoding edge cases.
                    if (src.equals(dst)) {
                        lines.remove(j);        // no-op: drop the pop and the push
                        lines.remove(i);
                    } else {
                        lines.set(i, "    movq " + src + ", " + dst);
                        lines.remove(j);
                    }
                } else {
                    continue; // memory source: leave alone
                }
                collapsed++;
                changed = true;
                break; // restart the scan after a change
            }
        }
        collapsed += collapseSpannedPushPop(lines);
        collapsed += foldScratchLoadIntoOp(lines);
        collapsed += NativeX86LeaFold.foldShiftToLea(lines);
        collapsed += NativeX86LeaFold.foldAddToLea(lines);
        collapsed += foldLoadIntoCompare(lines);
        collapsed += NativeX86LeaFold.foldAddIntoLea(lines);
        collapsed += cmpZeroToTest(lines);
        collapsed += dropRedundantFlagTest(lines);
        collapsed += epilogueToLeave(lines);
        collapsed += dropJumpToNext(lines);
        collapsed += invertJumpOverJump(lines);
        collapsed += NativeX86LeaFold.foldLeaIntoDestination(lines);
        sb.setLength(from);
        sb.append(String.join("\n", lines));
        return collapsed;
    }

    /** 64-bit register name -> its 32-bit name (for substituting a source operand). */
    static final java.util.Map<String, String> REG32 = java.util.Map.ofEntries(
            java.util.Map.entry("%rax", "%eax"), java.util.Map.entry("%rcx", "%ecx"),
            java.util.Map.entry("%rdx", "%edx"), java.util.Map.entry("%rbx", "%ebx"),
            java.util.Map.entry("%rsp", "%esp"), java.util.Map.entry("%rbp", "%ebp"),
            java.util.Map.entry("%rsi", "%esi"), java.util.Map.entry("%rdi", "%edi"),
            java.util.Map.entry("%r8", "%r8d"), java.util.Map.entry("%r9", "%r9d"),
            java.util.Map.entry("%r10", "%r10d"), java.util.Map.entry("%r11", "%r11d"),
            java.util.Map.entry("%r12", "%r12d"), java.util.Map.entry("%r13", "%r13d"),
            java.util.Map.entry("%r14", "%r14d"), java.util.Map.entry("%r15", "%r15d"));

    /** The binary ops (not shifts — the shift count must be %cl) whose second operand
     *  may be substituted from %rcx with the loading {@code movq %Y, %rcx} removed. */
    private static final java.util.Set<String> SUBSTITUTABLE = java.util.Set.of(
            "addl", "addq", "subl", "subq", "imull", "imulq", "andl", "andq",
            "orl", "orq", "xorl", "xorq", "cmpl", "cmpq");

    /** Flag-only ops: substituting their %rcx DESTINATION is safe because they do
     *  not produce a value in the destination (only the flags change). */
    private static final java.util.Set<String> FLAG_OPS = java.util.Set.of(
            "cmpl", "cmpq", "testl", "testq");

    /**
     * Folds {@code movq %Y, %rcx} + a following op that reads or (flag-only) writes
     * {@code %rcx} into the same op with {@code %Y32/%Y} in its place, dropping the
     * scratch load. The op sees exactly the value the movq put in rcx, so the
     * result and the flags are unchanged.
     *
     * <p>Conservative guard: only applied when {@code %rcx}/{@code %ecx}/{@code %cl}
     * appears in the region SOLELY as such a scratch load and its immediately-
     * following foldable op — if rcx is used anywhere else (e.g. a shift count that
     * must stay in {@code %cl}, or a store to a register-placed local), nothing is
     * folded.
     */
    private static int foldScratchLoadIntoOp(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (!mentionsRcx(t)) continue;
            if (isFoldableOp(t)) continue;
            boolean isLoad = t.startsWith("movq ") && t.endsWith(", %rcx");
            if (!isLoad || !isFoldableOp(skipToFoldableOp(lines, i + 1))) return 0;
        }
        int folded = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String load = lines.get(i).trim();
                if (!load.startsWith("movq ") || !load.endsWith(", %rcx")) continue;
                String y = load.substring("movq ".length(), load.length() - ", %rcx".length()).trim();
                if (!y.startsWith("%") || y.equals("%rax") || y.equals("%rcx")) continue;
                int j = indexOfFoldableOp(lines, i + 1);
                if (j < 0) continue;
                String op = lines.get(j).trim();
                if (!isFoldableOp(op)) continue;
                String mnemonic = op.substring(0, op.indexOf(' '));
                String rest = op.substring(op.indexOf(' ') + 1);           // "src, dst"
                int comma = rest.indexOf(',');
                String src = rest.substring(0, comma).trim();
                String dst = rest.substring(comma + 1).trim();
                String y32 = REG32.getOrDefault(y, y);
                boolean srcRcx32 = src.equals("%ecx");
                boolean srcRcx64 = src.equals("%rcx");
                boolean dstRcx32 = dst.equals("%ecx");
                boolean dstRcx64 = dst.equals("%rcx");
                // Substitute with the SAME width as the operand being replaced.
                String srcVal = srcRcx32 ? y32 : y;
                String dstVal = dstRcx32 ? y32 : y;
                if ((srcRcx32 || srcRcx64) && (dstRcx32 || dstRcx64)) {
                    // e.g. `testl %ecx, %ecx` (both operands the scratch): both are Y.
                    lines.set(j, "    " + mnemonic + " " + srcVal + ", " + dstVal);
                } else if (srcRcx32 || srcRcx64) {
                    // The op READS rcx as its second operand: substitute the source.
                    lines.set(j, "    " + mnemonic + " " + srcVal + ", " + dst);
                } else if (dstRcx32 || dstRcx64) {
                    // The op is flag-only and WRITES rcx: substitute the destination.
                    lines.set(j, "    " + mnemonic + " " + src + ", " + dstVal);
                } else {
                    continue;
                }
                lines.remove(i);
                folded++;
                changed = true;
                break;
            }
        }
        return folded;
    }

    private static boolean mentionsRcx(String t) {
        return t.contains("%rcx") || t.contains("%ecx") || t.contains("%cl");
    }

    /**
     * §7 x86_64 ("efficient use of flags"): folds {@code movq %X, %rax} into a
     * following compare that reads {@code %rax} but does not depend on it surviving:
     *
     * <pre>
     *   movq %X, %rax          cmpl $2, %X32        (branch/compare immediate)
     *   cmpl $2, %eax     ==&gt;   (or) cmpl %ecx, %X32  (register compare)
     *                          (or) testl %X32, %X32  (test self)
     * </pre>
     *
     * <p>Safe because the compare READS only rax's low 32 (or full 64), which the
     * movq set to exactly X, and everything after a compare either reads the FLAGS
     * (jcc) or overwrites rax (setcc + movzbl) — never rax's value. So dropping the
     * materialization is exact. The movq must be IMMEDIATELY followed by the compare
     * (only {@code .loc} between), so X is unchanged. {@code %X} must be a register
     * other than rax.
     */
    private static int foldLoadIntoCompare(List<String> lines) {
        int folded = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String mv = lines.get(i).trim();
                if (!mv.startsWith("movq ") || !mv.endsWith(", %rax")) continue;
                String x = mv.substring("movq ".length(), mv.length() - ", %rax".length()).trim();
                if (!x.startsWith("%") || x.equals("%rax")) continue;
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                String newOp = rewriteCompareReadingRax(lines.get(j).trim(), x);
                if (newOp == null) continue;
                lines.set(j, newOp);
                lines.remove(i);
                folded++;
                changed = true;
                break;
            }
        }
        return folded;
    }

    /** Rewrites a compare/test that reads {@code %eax/%rax} to read {@code %X}
     *  (the same width), or returns null when the line is not such a compare. */
    private static String rewriteCompareReadingRax(String op, String x) {
        int sp = op.indexOf(' ');
        if (sp < 0) return null;
        String mnemonic = op.substring(0, sp);
        String rest = op.substring(sp + 1);
        int comma = rest.indexOf(',');
        if (comma < 0) return null;
        String src = rest.substring(0, comma).trim();
        String dst = rest.substring(comma + 1).trim();
        boolean dstRax = dst.equals("%eax") || dst.equals("%rax");
        boolean srcRax = src.equals("%eax") || src.equals("%rax");
        if (!dstRax && !srcRax) return null;
        boolean isCmp = mnemonic.equals("cmpl") || mnemonic.equals("cmpq");
        boolean isTest = mnemonic.equals("testl") || mnemonic.equals("testq");
        if (!isCmp && !isTest) return null;
        boolean wide = mnemonic.endsWith("q");
        String xw = wide ? x : REG32.getOrDefault(x, x);
        if (isTest) {
            // testl %eax, %eax  ->  testl %X, %X
            if (!srcRax || !dstRax) return null;
            return "    " + mnemonic + " " + xw + ", " + xw;
        }
        // cmpl $N, %eax  or  cmpl %ecx, %eax  ->  cmpl $N/%ecx, %Xw
        if (!dstRax) return null; // the compared value must be the destination (rax)
        String newSrc = srcRax ? xw : src;
        return "    " + mnemonic + " " + newSrc + ", " + xw;
    }

    /**
     * The index of the next foldable op after {@code from}, skipping {@code .loc}
     * lines and intervening {@code movq ... , %rax} scratch loads (independent of
     * the rcx load being folded — the operand load lands in a different register).
     * Returns -1 if a non-skippable line comes first.
     */
    private static int indexOfFoldableOp(List<String> lines, int from) {
        int j = from;
        while (j < lines.size()) {
            String t = lines.get(j).trim();
            if (t.startsWith(".loc ")) { j++; continue; }
            if (t.startsWith("movq ") && t.endsWith(", %rax")) { j++; continue; }
            return isFoldableOp(t) ? j : -1;
        }
        return -1;
    }

    private static String skipToFoldableOp(List<String> lines, int from) {
        int j = indexOfFoldableOp(lines, from);
        return j < 0 ? "" : lines.get(j).trim();
    }

    /** A foldable op: reads {@code %ecx/%rcx} as its second operand (SUBSTITUTABLE),
     *  or is flag-only and writes it as its destination (FLAG_OPS). */
    private static boolean isFoldableOp(String t) {
        int sp = t.indexOf(' ');
        if (sp < 0) return false;
        String mnemonic = t.substring(0, sp);
        String rest = t.substring(sp + 1);
        int comma = rest.indexOf(',');
        if (comma < 0) return false;
        String src = rest.substring(0, comma).trim();
        String dst = rest.substring(comma + 1).trim();
        if (SUBSTITUTABLE.contains(mnemonic) && (src.equals("%ecx") || src.equals("%rcx"))) {
            return true;
        }
        return FLAG_OPS.contains(mnemonic) && (dst.equals("%ecx") || dst.equals("%rcx"));
    }

    /**
     * §7 x86_64 ("lower-cost instruction selection"): {@code cmp{l,q} $0, %reg} is
     * rewritten to {@code test{l,q} %reg, %reg} — identical FLAGS (ZF set iff reg is
     * zero; SF/CF=0 as for a compare against 0), shorter encoding, no immediate.
     */
    /**
     * §7 x86_64 ("lower-cost instruction selection"): {@code movq %rbp, %rsp} +
     * {@code popq %rbp} is exactly the {@code leave} instruction — one instruction
     * and one byte shorter, same effect (rsp = rbp; rbp = [rsp]).
     */
    /**
     * §7 x86_64 ("lower-cost instruction selection"): an unconditional
     * {@code jmp L} immediately followed by the label {@code L:} jumps to the very
     * next instruction — the jump is a no-op and is dropped. Only {@code .loc}
     * debug lines may sit between them.
     */
    private static int dropJumpToNext(List<String> lines) {
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
    private static int invertJumpOverJump(List<String> lines) {
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

    private static int epilogueToLeave(List<String> lines) {
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

    private static int cmpZeroToTest(List<String> lines) {
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
            if (wide != REG32.containsKey(dst)) continue; // width must match the register
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
    private static int dropRedundantFlagTest(List<String> lines) {
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
                if (wide != REG32.containsKey(dst)) continue;
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

    /**
     * Collapses a push/pop SPANNING exactly one {@code movq}:
     *
     * <pre>
     *   pushq %A            movq %A, %D
     *   movq  %B, %C   ==&gt;  movq %B, %C
     *   popq  %D
     * </pre>
     *
     * <p>This is the shape of a binary op whose two operands are both register
     * values: the first operand is pushed, the second is loaded into the scratch
     * register, then the first is popped back. The two moves are independent, so
     * the reorder is safe — EXCEPT when it would read a value after it was
     * clobbered: the first move writes {@code %D}, so the middle move must not
     * READ {@code %D} ({@code B != %D}) and must not WRITE {@code %D}
     * ({@code C != %D}, which the original relied on the pop to overwrite last).
     * All operands must be registers; anything else is left alone.
     */
    private static int collapseSpannedPushPop(List<String> lines) {
        int collapsed = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String push = lines.get(i).trim();
                if (!push.startsWith("pushq ")) continue;
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                int k = j + 1;
                while (k < lines.size() && lines.get(k).trim().startsWith(".loc ")) k++;
                if (k >= lines.size()) continue;
                String mid = lines.get(j).trim();
                String pop = lines.get(k).trim();
                if (!pop.startsWith("popq ")) continue;
                if (!mid.startsWith("movq ")) continue;
                String[] mv = mid.substring("movq ".length()).split(",");
                if (mv.length != 2) continue;
                String srcA = push.substring("pushq ".length()).trim();
                String srcB = mv[0].trim();
                String dstC = mv[1].trim();
                String dstD = pop.substring("popq ".length()).trim();
                if (!srcA.startsWith("%") || !srcB.startsWith("%") || !dstC.startsWith("%")
                        || !dstD.startsWith("%")) {
                    continue;
                }
                if (srcB.equals(dstD) || dstC.equals(dstD)) continue; // clobber hazard
                if (srcA.equals(dstD)) {
                    // pushq %D; movq %B, %C; popq %D  ->  the first move is a no-op.
                    // Remove the POP first: removing the earlier push would shift
                    // the pop's index and delete the wrong line.
                    lines.remove(k);
                    lines.remove(i);
                } else {
                    lines.set(i, "    movq " + srcA + ", " + dstD);
                    lines.remove(k);
                }
                collapsed++;
                changed = true;
                break;
            }
        }
        return collapsed;
    }
}
