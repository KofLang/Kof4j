package dev.kof.compiler.nat;

import java.util.ArrayList;
import java.util.List;

/**
 * §14 (ABI and optimization) of {@code docs/assembly-optimization-plan.md}:
 * "Optimizations must never incorrectly change … stack alignment".
 *
 * <p>The x86-64 System V ABI requires {@code %rsp} to be 16-byte aligned at every
 * {@code call} (so that a callee's SSE spills are aligned). This emitter is a
 * stack machine that PUSHES operands before calls; an odd number of operand
 * pushes in flight makes {@code %rsp % 16 == 8} at the call. The callee then runs
 * misaligned, and any function using aligned SSE (e.g. glibc's
 * {@code pthread_create}) faults — the exact class recorded in {@code §253}.
 *
 * <p>This pass tracks the running stack delta over a method's lines (mirroring the
 * pushes/pops and {@code sub}/{@code add $n,%rsp} the emitter produces) and, at a
 * {@code call} whose delta is not a multiple of 16, inserts {@code subq $8, %rsp}
 * before it and {@code addq $8, %rsp} after.
 *
 * <p><b>Stack-passed arguments:</b> a call with 7+ arguments pushes them just
 * before the {@code call} (arg7 on top, at {@code 0(%rsp)}). The padding MUST sit
 * BELOW those pushes, or it becomes what the callee reads as arg7 — a
 * wrong-argument miscompile (measured: {@code Av1CoeffsE2ETest}). So the
 * {@code subq} is inserted after the register-argument pops but before the
 * stack-argument pushes; only the emitter's {@code pushq -N(%rbp)} form is
 * treated as a stack argument, so an operand push is never crossed.
 */
final class NativeX86StackAlign {

    private NativeX86StackAlign() {
    }

    /**
     * Pads the misaligned {@code call} sites in {@code lines}, returning the
     * number of sites padded.
     */
    static int alignCalls(List<String> lines, int from) {
        // Pass 1: the stack delta (mod 16) at every line index.
        int[] deltaAt = deltas(lines, from);

        // Pass 2: collect the insertion points, then apply them LAST-to-FIRST so
        // earlier indices stay valid.
        List<int[]> sites = new ArrayList<>();   // {subqIndex, callIndex}
        for (int i = from; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (!t.startsWith("call ") || deltaAt[i] % 16 == 0 || !restoreIsAdjacent(lines, i)) {
                continue;
            }
            sites.add(new int[]{stackArgInsertionPoint(lines, i), i});
        }
        for (int s = sites.size() - 1; s >= 0; s--) {
            int[] site = sites.get(s);
            lines.add(site[1] + 1, "    addq $8, %rsp");  // restore first: index stays valid
            lines.add(site[0], "    subq $8, %rsp");
        }
        return sites.size();
    }

    /**
     * The index just before the call, moved up past its stack-argument pushes.
     *
     * <p>A virtual/interface dispatch places the vtable RESOLUTION instructions
     * ({@code movq 8(%rax), %r11}, {@code addq $N, %r11}, {@code movq (%r11), %r11})
     * between the argument pushes and the {@code call *%r11}. Stopping at the first
     * non-push line therefore left the padding ABOVE the stack arguments, so the
     * callee read the padding as arg7 and every stack-passed argument came out
     * shifted (measured, #935: an 8-parameter method received f/g/h wrong, which
     * made the AV1 palette probe read zero palettes on native x86-64). The scan
     * skips those resolution lines so the padding lands BELOW the arguments,
     * exactly as the direct-call path already did.
     */
    private static int stackArgInsertionPoint(List<String> lines, int callIndex) {
        int ins = callIndex;
        while (ins > 0) {
            String t = lines.get(ins - 1).trim();
            if (isStackArgPush(t) || isVtableResolution(t) || t.startsWith(".loc")
                    || t.startsWith("#") || t.isEmpty()) {
                ins--;
                continue;
            }
            break;
        }
        return ins;
    }

    /** The vtable tail the emitter places immediately before an indirect call. */
    private static boolean isVtableResolution(String t) {
        return t.equals("movq 8(%rax), %r11")
                || t.equals("movq (%r11), %r11")
                || (t.startsWith("addq $") && t.endsWith(", %r11"));
    }

    /** The emitter's stack-argument push is exactly {@code pushq -N(%rbp)}. */
    private static boolean isStackArgPush(String t) {
        return t.startsWith("pushq -") && t.endsWith("(%rbp)");
    }

    /** True when the op after the call is not a label, so the restore cannot be skipped. */
    private static boolean restoreIsAdjacent(List<String> lines, int callIndex) {
        for (int j = callIndex + 1; j < lines.size(); j++) {
            String t = lines.get(j).trim();
            if (t.isEmpty() || t.startsWith(".loc") || t.startsWith("#")) {
                continue;
            }
            return !isLabel(t);
        }
        return true;
    }

    private static int[] deltas(List<String> lines, int from) {
        int[] deltaAt = new int[lines.size()];
        int delta = 8; // a call pushed the 8-byte return address
        for (int i = from; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (isLabel(t)) {
                delta = 8; // each function/section starts fresh
            } else if (t.startsWith("push")) {
                delta -= 8;
            } else if (t.startsWith("pop")) {
                delta += 8;
            } else if (t.startsWith("subq $") && t.contains("%rsp")) {
                delta -= immediate(t);
            } else if (t.startsWith("addq $") && t.contains("%rsp")) {
                delta += immediate(t);
            } else if (t.equals("leave")) {
                delta = 8;
            }
            deltaAt[i] = delta;
        }
        return deltaAt;
    }

    private static boolean isLabel(String t) {
        return t.endsWith(":") && !t.startsWith(".");
    }

    private static int immediate(String t) {
        int start = t.indexOf('$') + 1;
        int end = t.indexOf(',', start);
        try {
            return Integer.parseInt(t.substring(start, end).trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The stack delta (mod 16) at each misaligned call, for tests/diagnostics. */
    static List<Integer> misalignedCallSites(List<String> lines, int from) {
        int[] deltaAt = deltas(lines, from);
        List<Integer> out = new ArrayList<>();
        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("call ") && deltaAt[i] % 16 != 0) {
                out.add(deltaAt[i] % 16);
            }
        }
        return out;
    }
}