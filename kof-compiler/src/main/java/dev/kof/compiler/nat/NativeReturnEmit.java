package dev.kof.compiler.nat;

/**
 * §7 x86_64 of {@code docs/assembly-optimization-plan.md}
 * ("reduction of instructions"): return-site emission helpers for the x86-64
 * emitter, extracted to keep {@code NativeMethodEmitter} under the 500-line gate.
 */
final class NativeReturnEmit {

    private NativeReturnEmit() {
    }

    /**
     * The return value is the last value pushed. When that push was {@code pushq
     * %rax} (a binary/call/frame-load result — rax already holds it), the {@code
     * pushq %rax} + {@code popq %rax} pair is a no-op; drop both instead of popping
     * back. Any other producer (a register-placed local's {@code pushq %rbx}, a
     * literal's {@code pushq $imm}) still needs the pop into rax.
     */
    static void popReturnValue(StringBuilder sb) {
        int end = sb.length();
        if (end > 0 && sb.charAt(end - 1) == '\n') end--;
        while (end > 0) {
            int start = sb.lastIndexOf("\n", end - 1) + 1;
            if (sb.indexOf("    .loc ", start) == start) {
                end = start - 1;
                continue;
            }
            if (sb.substring(start, end).equals("    pushq %rax")) {
                int deleteEnd = end;
                if (deleteEnd < sb.length() && sb.charAt(deleteEnd) == '\n') {
                    deleteEnd++;
                }
                sb.delete(start, deleteEnd);
                return;
            }
            sb.append("    popq %rax\n");
            return;
        }
        sb.append("    popq %rax\n");
    }
}
