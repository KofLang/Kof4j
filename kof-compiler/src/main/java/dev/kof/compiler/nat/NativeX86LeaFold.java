package dev.kof.compiler.nat;

import java.util.List;

/**
 * §7 x86_64 lea folds for the x86-64 peephole, extracted from
 * {@link NativeX86Peephole} to keep it under the 500-line gate: recognize a
 * {@code movq}/{@code add}/{@code shl}/{@code imul} pair that a single {@code lea}
 * can compute, and an immediate add that folds into an existing lea's
 * displacement. All are exact by construction — a lea has no side effects but
 * writing its destination, and the ops' flags are unused by Kof (explicit cmp).
 */
final class NativeX86LeaFold {

    private NativeX86LeaFold() {}


    /**
     * §7 x86_64 ("choice between mov, lea and combined operations"): folds
     * {@code movq %X, %rax} + {@code add{l,q} %Y/%imm, %{e,r}ax} into a single
     * {@code lea} that computes the sum without a separate materialization:
     *
     * <pre>
     *   movq %X, %rax        leaq (%X,%Y), %rax      (register add)
     *   addq %Y, %rax   ==&gt;  leaq imm(%X), %rax      (immediate add)
     * </pre>
     *
     * <p>Safe because the add's result is a value in rax and its FLAGS are unused
     * (Kof comparisons are explicit {@code cmp}, never a flag-consuming jcc right
     * after an add), so the flags-agnostic {@code lea} is equivalent. Only when the
     * destination is {@code %rax}, X is a register other than rax, and (for the
     * register form) Y is a register other than rax (so the base+index is legal).
     */
    static int foldAddToLea(List<String> lines) {
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
                String add = lines.get(j).trim();
                String newOp = rewriteAddAsLea(add, x);
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

    /** Rewrites {@code add{l,q} src, %{e,r}ax} (with X in rax) as a {@code lea}
     *  computing X+src, or returns null when it is not such an add. */
    private static String rewriteAddAsLea(String add, String x) {
        int sp = add.indexOf(' ');
        if (sp < 0) return null;
        String mnemonic = add.substring(0, sp);
        boolean wide;
        if (mnemonic.equals("addq")) wide = true;
        else if (mnemonic.equals("addl")) wide = false;
        else return null;
        String rest = add.substring(sp + 1);
        int comma = rest.indexOf(',');
        if (comma < 0) return null;
        String src = rest.substring(0, comma).trim();
        String dst = rest.substring(comma + 1).trim();
        String ax = wide ? "%rax" : "%eax";
        if (!dst.equals(ax)) return null;
        String xw = wide ? x : NativeX86Peephole.REG32.getOrDefault(x, x);
        String lea = wide ? "leaq" : "leal";
        if (src.startsWith("$")) {
            // add $imm, %rax  ->  lea imm(%X), %rax
            return "    " + lea + " " + src.substring(1) + "(" + xw + "), " + ax;
        }
        if (src.startsWith("%")) {
            String y = src;
            if (y.equals("%rax") || y.equals("%eax")) return null; // index cannot be rax with base rax
            String yw = wide ? y : NativeX86Peephole.REG32.getOrDefault(y, y);
            return "    " + lea + " (" + xw + "," + yw + "), " + ax;
        }
        return null;
    }

    /**
     * §7 x86_64 ("choice between mov, lea and combined operations"): folds an
     * immediate add into an existing {@code lea}'s displacement:
     *
     * <pre>
     *   leal (%ebx,%ebx,2), %eax     leal 1(%ebx,%ebx,2), %eax
     *   addl $1, %eax           ==&gt;
     * </pre>
     *
     * <p>Safe because the {@code lea} has no side effects but writing its
     * destination, and the immediate add's FLAGS are unused (Kof uses explicit
     * {@code cmp}), so combining the two displacements is exact. Only when the
     * lea writes {@code %rax}, the add is an immediate add to the same width, and
     * (to keep the match unambiguous) the lea has no existing displacement.
     */
    /**
     * §7 x86_64 ("choice between mov, lea and combined operations"): folds
     * {@code lea{l,q} OP, %{e,r}ax} + {@code movq %rax, %rN} into
     * {@code lea{l,q} OP, %rN} — the lea writes the callee's destination directly,
     * dropping the move. Safe because a lea has no side effects but writing its
     * destination (no flags), and its memory operand is READ-ONLY — the lea reads
     * the operand BEFORE writing the destination register in the same instruction,
     * so even if {@code %rN} appears in the operand the computed value is unchanged.
     * Any allocatable destination register works; the operand width is matched to the
     * lea ({@code leal} needs the 32-bit name {@code %r15d}, {@code leaq} the 64-bit
     * one), and the 32-bit lea zero-extends into the 64-bit register, so it is exactly
     * {@code leal …,%eax; movq %rax,%r15}. Only callee-saved / allocatable registers
     * are folded (a lea into {@code %rax} itself is not a pair).
     */
    static int foldLeaIntoDestination(List<String> lines) {
        int folded = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String lea = lines.get(i).trim();
                boolean wide;
                if (lea.startsWith("leal ") && lea.endsWith(", %eax")) wide = false;
                else if (lea.startsWith("leaq ") && lea.endsWith(", %rax")) wide = true;
                else continue;
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                String mv = lines.get(j).trim();
                if (!mv.startsWith("movq %rax, ")) continue;
                String dst = mv.substring("movq %rax, ".length()).trim();
                // A 64-bit destination register with a known 32-bit name (rbx/r12-r15
                // — the allocator's callee-saved file).
                if (!NativeX86Peephole.REG32.containsKey(dst)) continue;
                String mem = lea.substring(lea.indexOf(' ') + 1, lea.lastIndexOf(',')).trim();
                // Match the operand width to the lea: a 32-bit `leal` needs the
                // 32-bit destination name (`%r15d`), a `leaq` the 64-bit one.
                String dstW = wide ? dst : NativeX86Peephole.REG32.get(dst);
                String newOp = (wide ? "leaq" : "leal") + " " + mem + ", " + dstW;
                lines.set(i, "    " + newOp);
                lines.remove(j);
                folded++;
                changed = true;
                break;
            }
        }
        return folded;
    }

    static int foldAddIntoLea(List<String> lines) {
        int folded = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String lea = lines.get(i).trim();
                boolean wide;
                if (lea.startsWith("leal ") && lea.endsWith(", %eax")) wide = false;
                else if (lea.startsWith("leaq ") && lea.endsWith(", %rax")) wide = true;
                else continue;
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                String add = lines.get(j).trim();
                String ax = wide ? "%rax" : "%eax";
                String mnem = wide ? "addq" : "addl";
                if (!add.startsWith(mnem + " $") || !add.endsWith(", " + ax)) continue;
                int imm = parseImmediate(add);
                if (imm == Integer.MIN_VALUE) continue;
                // The lea operand is "disp(base,index,scale)" or "disp(base,...)".
                String mem = lea.substring(lea.indexOf(' ') + 1, lea.lastIndexOf(',')).trim();
                int paren = mem.indexOf('(');
                String disp = paren < 0 ? mem : mem.substring(0, paren).trim();
                String idx = paren < 0 ? "" : mem.substring(paren);
                if (!disp.isEmpty()) continue; // no existing displacement -> unambiguous
                int newDisp = imm;
                lines.set(i, "    " + (wide ? "leaq" : "leal") + " " + newDisp + idx + ", " + ax);
                lines.remove(j);
                folded++;
                changed = true;
                break;
            }
        }
        return folded;
    }

    /**
     * §7 x86_64 ("lea for suitable arithmetic operations"): folds
     * {@code movq %X, %rax} + {@code shl{l,q} $N, %{e,r}ax} (N = 1/2/3, i.e. a
     * multiply by 2/4/8 — what {@code StrengthReduction} produces) into a single
     * {@code lea} that scales {@code %X} directly:
     *
     * <pre>
     *   movq %rbx, %rax     leal (%rbx,%rbx), %eax     (x*2)
     *   shll $1, %eax
     * </pre>
     *
     * <p>{@code lea} computes the address {@code X + X*scale} = {@code X*(1+scale)},
     * which for scale 1/2/4 is {@code 2X/4X/8X} — exactly the shift result (32-bit
     * for `leal`, 64-bit for `leaq`, matching the shift width). One instruction
     * instead of two. Only when the destination is {@code %rax} (the value register)
     * and the source is not {@code %rax}.
     */
    static int foldShiftToLea(List<String> lines) {
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
                String sh = lines.get(j).trim();
                String lea;
                if (sh.startsWith("shll $") && sh.endsWith(", %eax")) {
                    lea = leaForShift(sh, x, false);
                } else if (sh.startsWith("shlq $") && sh.endsWith(", %rax")) {
                    lea = leaForShift(sh, x, true);
                } else if (sh.startsWith("imull $") && sh.endsWith(", %eax")) {
                    lea = leaForImul(sh, x, false);
                } else if (sh.startsWith("imulq $") && sh.endsWith(", %rax")) {
                    lea = leaForImul(sh, x, true);
                } else {
                    continue;
                }
                if (lea == null) continue;
                lines.set(i, lea.stripTrailing());
                lines.remove(j);
                folded++;
                changed = true;
                break;
            }
        }
        return folded;
    }

    /** The immediate of a {@code mnem $N, %reg} line, or Integer.MIN_VALUE when absent. */
    private static int parseImmediate(String line) {
        int dollar = line.indexOf('$');
        int comma = line.indexOf(',');
        if (dollar < 0 || comma < 0 || comma < dollar) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(line.substring(dollar + 1, comma).trim());
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    /** {@code shl{l,q} $N, %ax} (N=1/2/3) as a {@code lea} scaling X, or null. */
    private static String leaForShift(String sh, String x, boolean is64) {
        int n = parseImmediate(sh);
        if (n == Integer.MIN_VALUE) return null;
        String mnemonic = is64 ? "leaq" : "leal";
        String ax = is64 ? "%rax" : "%eax";
        String xr = is64 ? x : NativeX86Peephole.REG32.getOrDefault(x, x);
        return switch (n) {
            case 1 -> "    " + mnemonic + " (" + xr + "," + xr + "), " + ax + "\n";
            case 2 -> "    " + mnemonic + " 0(," + xr + ",4), " + ax + "\n";
            case 3 -> "    " + mnemonic + " 0(," + xr + ",8), " + ax + "\n";
            default -> null;
        };
    }

    /**
     * {@code imul{l,q} $N, %ax} for N=3/5/9 as a {@code lea}: X*3 = lea (X,X,2),
     * X*5 = lea (X,X,4), X*9 = lea (X,X,8). Other N have no base+index*scale form.
     */
    private static String leaForImul(String im, String x, boolean is64) {
        int n = parseImmediate(im);
        if (n == Integer.MIN_VALUE) return null;
        String mnemonic = is64 ? "leaq" : "leal";
        String ax = is64 ? "%rax" : "%eax";
        String xr = is64 ? x : NativeX86Peephole.REG32.getOrDefault(x, x);
        int scale = switch (n) {
            case 3 -> 2;
            case 5 -> 4;
            case 9 -> 8;
            default -> -1;
        };
        if (scale < 0) return null;
        return "    " + mnemonic + " (" + xr + "," + xr + "," + scale + "), " + ax + "\n";
    }

}
