package dev.kof.compiler.nat;

/**
 * §7 x86_64 of {@code docs/development/assembly-optimization-plan.md}
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
        // Split into lines (keeping the trailing empty element for a final \n).
        // Skip any trailing debug (.loc) lines — the value push may be separated
        // from the return by source-position directives — then drop the push if it
        // is `pushq %rax` (rax already holds the value), else pop it into rax.
        java.util.List<String> lines = new java.util.ArrayList<>(
                java.util.Arrays.asList(sb.toString().split("\n", -1)));
        int i = lines.size() - 1;
        if (i >= 0 && lines.get(i).isEmpty()) i--;
        while (i >= 0 && lines.get(i).startsWith("    .loc ")) i--;
        if (i >= 0 && lines.get(i).equals("    pushq %rax")) {
            lines.remove(i);
            sb.setLength(0);
            sb.append(String.join("\n", lines));
            return;
        }
        sb.append("    popq %rax\n");
    }
}
