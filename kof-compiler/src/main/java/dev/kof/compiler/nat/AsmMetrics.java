package dev.kof.compiler.nat;

import java.util.List;

/**
 * Assembly-level metrics over an emitted x86-64 method body (§18 of
 * {@code docs/assembly-optimization-plan.md}).
 *
 * <p>{@code IrMetrics} counts the IR op stream. §18 also lists counters for the
 * emitted backend — instruction count, loads, stores, branches, calls — and the
 * ISA peepholes (§7) can only be MEASURED at this level. This class counts the
 * text the emitter produced, so a fold's effect is measured rather than
 * asserted (the same discipline used to FIND Phase E slices 25-27).
 *
 * <p>Counting is conservative and exact over the given lines: labels,
 * directives (anything starting with {@code .}, e.g. {@code .loc}/{@code .file}),
 * comments and blank lines are not instructions. A load is an instruction whose
 * memory operand is the SOURCE and a register the destination (plus
 * {@code pop}); a store is the mirror (plus {@code push}). The classification is
 * syntactic — it never changes what is emitted.
 */
public record AsmMetrics(int instructions, int loads, int stores, int branches,
                         int calls, int returns) {

    /** Counts the x86-64 assembly in {@code lines}. */
    public static AsmMetrics of(List<String> lines) {
        int instructions = 0, loads = 0, stores = 0, branches = 0, calls = 0, returns = 0;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith(".") || line.startsWith("#")
                    || line.startsWith("//") || line.endsWith(":")) {
                continue;
            }
            // Drop a trailing comment so it cannot affect operand analysis.
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash).stripTrailing();
            if (line.isEmpty()) continue;
            String mnemonic = line.split("[\\s,]", 2)[0];
            instructions++;
            if (isCall(mnemonic)) {
                calls++;
            } else if (isBranch(mnemonic)) {
                branches++;
            } else if (isReturn(mnemonic)) {
                returns++;
            }
            String operands = line.length() > mnemonic.length()
                    ? line.substring(mnemonic.length()).strip() : "";
            if (mnemonic.startsWith("push")) {
                stores++;
            } else if (mnemonic.startsWith("pop")) {
                loads++;
            } else if (mnemonic.startsWith("mov")) {
                // Only the mov family is classified by memory operand: `lea`
                // does not dereference, and arithmetic/compare memory operands
                // would over-count a plain read as a "load".
                if (isLoad(operands)) loads++;
                else if (isStore(operands)) stores++;
            } else if (isRiscvLoad(mnemonic)) {
                // RISC-V spells memory access by WIDTH (ld/lw/lh/lb + the
                // unsigned lbu/lhu/lwu), not with a `mov` family — without these
                // the cross runs reported loads=0/stores=0 apart from push/pop
                // (measured). AArch64 keeps `ldr`/`str`, covered below.
                loads++;
            } else if (isRiscvStore(mnemonic)) {
                stores++;
            } else if (mnemonic.equals("ldr") || mnemonic.startsWith("ldur")
                    || mnemonic.startsWith("ldp")) {
                loads++;
            } else if (mnemonic.equals("str") || mnemonic.startsWith("stur")
                    || mnemonic.startsWith("stp")) {
                stores++;
            }
        }
        return new AsmMetrics(instructions, loads, stores, branches, calls, returns);
    }

    private static boolean isRiscvLoad(String m) {
        return m.equals("ld") || m.equals("lw") || m.equals("lh") || m.equals("lb")
                || m.equals("lbu") || m.equals("lhu") || m.equals("lwu");
    }

    private static boolean isRiscvStore(String m) {
        return m.equals("sd") || m.equals("sw") || m.equals("sh") || m.equals("sb");
    }

    // The four architectures spell these differently: x86-64 `call`/`j*`/`ret`,
    // AArch64 `bl`/`b`/`b.cond`/`cbz`/`tbz`/`ret`, RISC-V `call`/`jal`/`jalr`/
    // `beq`…/`ret`. Counting only the x86 spellings silently reported 0 branches
    // and 0 calls on the cross targets (measured), so each family is recognised.
    private static boolean isCall(String m) {
        return m.startsWith("call") || m.equals("bl") || m.equals("jal") || m.equals("jalr");
    }

    private static boolean isBranch(String m) {
        return m.startsWith("j") || m.startsWith("b.") || m.equals("b")
                || m.equals("cbz") || m.equals("cbnz") || m.equals("tbz") || m.equals("tbnz")
                || m.equals("beq") || m.equals("bne") || m.equals("blt") || m.equals("bge")
                || m.equals("bltu") || m.equals("bgeu");
    }

    private static boolean isReturn(String m) {
        return m.equals("ret");
    }

    /**
     * True when the SOURCE operand is memory and the DESTINATION is a register,
     * e.g. {@code movq -8(%rbp), %rax} (AT&T puts the source first).
     */
    private static boolean isLoad(String operands) {
        String[] parts = operands.split(",");
        if (parts.length != 2) return false;
        return refersToMemory(parts[0]) && isRegister(parts[1]);
    }

    /** The mirror of {@link #isLoad}: memory DESTINATION, register/immediate source. */
    private static boolean isStore(String operands) {
        String[] parts = operands.split(",");
        if (parts.length != 2) return false;
        return refersToMemory(parts[1]);
    }

    private static boolean refersToMemory(String operand) {
        return operand.contains("(");
    }

    private static boolean isRegister(String operand) {
        String o = operand.strip();
        return o.startsWith("%");
    }

    /** A one-line summary for reports/logs. */
    public String summary() {
        return "instructions=" + instructions + " loads=" + loads + " stores=" + stores
                + " branches=" + branches + " calls=" + calls + " returns=" + returns;
    }
}
