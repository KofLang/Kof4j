package dev.kof.compiler.nat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * §7 AArch64 of {@code docs/assembly-optimization-plan.md}
 * ("lower-cost instruction selection"): a text peephole over the aarch64 output
 * (after the riscv→aarch64 line translation). The riscv stack machine pushes a
 * value with {@code sub sp, sp, #8} + {@code str X, [sp]} and pops it with the
 * {@code pop Y} assembler macro. When a push is IMMEDIATELY followed by a pop,
 * the pair is exactly {@code Y = X} with the stack pointer unchanged:
 *
 * <pre>
 *   sub sp, sp, #8          mov xY, xX      (or: nothing, when X == Y)
 *   str xX, [sp]     ==&gt;
 *   pop xY
 * </pre>
 *
 * <p>Only {@code .loc}/blank debug lines may sit between the three. This is
 * unconditionally safe: the net effect of the triple is {@code Y = X} with sp
 * restored.
 */
final class NativeAarch64Peephole {

    private static final Pattern PUSH_STORE =
            Pattern.compile("^\\s*str\\s+(x\\d+),\\s*\\[sp\\]$");
    private static final Pattern POP =
            Pattern.compile("^\\s*pop\\s+(x\\d+)$");

    private NativeAarch64Peephole() {
    }

    /** Collapses {@code sub sp,sp,#8} + {@code str X,[sp]} + {@code pop Y} into
     *  {@code mov Y, X} (or nothing when X == Y). Returns the number folded. */
    static int collapsePushPopPairs(List<String> lines) {
        int folded = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 2 < lines.size(); i++) {
                if (!lines.get(i).strip().equals("sub sp, sp, #8")) continue;
                Matcher mStore = PUSH_STORE.matcher(lines.get(i + 1));
                if (!mStore.matches()) continue;
                int k = i + 2;
                while (k < lines.size() && isDebug(lines.get(k))) k++;
                if (k >= lines.size()) continue;
                Matcher mPop = POP.matcher(lines.get(k));
                if (!mPop.matches()) continue;
                String x = mStore.group(1);
                String y = mPop.group(1);
                String indent = lines.get(k).substring(0, lines.get(k).length() - lines.get(k).stripLeading().length());
                if (x.equals(y)) {
                    for (int z = k; z >= i; z--) lines.remove(z);
                } else {
                    lines.set(i, indent + "mov " + y + ", " + x);
                    for (int z = k; z > i; z--) lines.remove(z);
                }
                folded++;
                changed = true;
                break;
            }
        }
        return folded;
    }

    /** Drops {@code mov X, X} self-moves (a no-op). Returns the number dropped. */
    static int dropSelfMoves(List<String> lines) {
        int dropped = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String t = lines.get(i).strip();
            if (t.startsWith("mov ")) {
                String[] a = t.substring(4).split(",");
                if (a.length == 2 && a[0].strip().equals(a[1].strip())) {
                    lines.remove(i);
                    dropped++;
                }
            }
        }
        return dropped;
    }

    private static boolean isDebug(String line) {
        String t = line.strip();
        return t.isEmpty() || t.startsWith(".loc ") || t.startsWith(".file ");
    }
}