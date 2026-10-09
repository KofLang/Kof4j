package dev.kof.compiler.nat;

import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofDebugInfo;
import dev.kof.compiler.Type;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §625: the prologue of a method with MORE local slots than entry-argument
 * slots (a lambda capture, or a lowering temporary with a high index) used to
 * leave those extra slots UNINITIALIZED ("stale"). The cross native GC scans
 * the frame conservatively ({@code kof_gc_mark} walks {@code [sp..bottom]}), so
 * a stale word that happens to look like a pointer is followed — the AV1 tile
 * walk ({@code Av1CoeffsE2ETest}) printed wrong coefficients and SIGSEGV'd on
 * riscv64/aarch64 after the §620 arg-shift fix. Every local slot must be
 * explicitly initialized (with 0 when it is not an entry argument).
 *
 * <p>This asserts the emitted prologue directly, so it is deterministic and
 * toolchain-free (no qemu/as/ld). RED-first: without the {@code sd zero} /
 * {@code movq $0} initialization the slot offset never appears in the prologue.
 */
class PrologueSlotInitTest {

    private static final Pattern RISCV_STORE =
            Pattern.compile("^\\s*sd\\s+\\S+,\\s*(-\\d+)\\(s11\\)\\s*$");
    private static final Pattern X86_STORE =
            Pattern.compile("^\\s*movq\\s+\\S+,\\s*(-\\d+)\\(%rbp\\)\\s*$");

    private static IRMethod methodWithLocals(List<Type> params, List<IRLocalVariable> locals) {
        return new IRMethod("f", Type.PrimitiveType.VOID, params, 0,
                List.of(), List.of(), locals, KofDebugInfo.EMPTY, List.of(), List.of());
    }

    private static IRClass owner(IRMethod m) {
        return new IRClass("Main", "Object", List.of(), 0, List.of(), List.of(m),
                List.of(), null, 0, List.of());
    }

    /** 8 Int params -> paramSlotMax 9; locals 9..11 are not entry args. */
    private static List<IRLocalVariable> manyParamLocals() {
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", Type.PrimitiveType.INT));
        for (int i = 1; i <= 8; i++) {
            locals.add(new IRLocalVariable(i, "p" + i, Type.PrimitiveType.INT));
        }
        locals.add(new IRLocalVariable(9, "capture", Type.PrimitiveType.INT));
        locals.add(new IRLocalVariable(10, "tmp$a", Type.PrimitiveType.INT));
        locals.add(new IRLocalVariable(11, "tmp$b", Type.PrimitiveType.INT));
        return locals;
    }

    private static List<Type> eightIntParams() {
        List<Type> params = new ArrayList<>();
        for (int i = 0; i < 8; i++) params.add(Type.PrimitiveType.INT);
        return params;
    }

    /** Offsets written by {@code sd ...} lines of the riscv prologue. */
    private static java.util.Set<Integer> riscvStoredOffsets(String asm) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (String line : asm.split("\n", -1)) {
            var m = RISCV_STORE.matcher(line);
            if (m.matches()) out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    private static java.util.Set<Integer> x86StoredOffsets(String asm) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (String line : asm.split("\n", -1)) {
            var m = X86_STORE.matcher(line);
            if (m.matches()) out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    @Test
    void riscvCrossPrologueInitializesEveryLocalSlot() {
        IRMethod method = methodWithLocals(eightIntParams(), manyParamLocals());
        IRClass clazz = owner(method);
        NativeBackend nb = new NativeBackend();
        nb.allClassesMap.put(clazz.name(), clazz);

        NativeRiscvCrossEmit emit = new NativeRiscvCrossEmit(nb);
        StringBuilder sb = new StringBuilder();
        emit.emitCrossMethodRiscv(sb, clazz, method, false);
        String asm = sb.toString();
        java.util.Set<Integer> written = riscvStoredOffsets(asm);

        for (IRLocalVariable lv : method.localVariables()) {
            int off = emit.crossLocalOffRiscv(lv.index());
            assertTrue(written.contains(off),
                    "§625: riscv slot " + lv.index() + " (" + lv.name()
                            + ") never initialized in prologue (offset " + off + "):\n" + asm);
        }
        // high slots are NOT entry args -> they must be zeroed, not left stale
        assertTrue(asm.contains("sd zero, -96(s11)"),
                "§625: slot 9 (capture) must be zeroed in the riscv prologue:\n" + asm);
    }

    @Test
    void x86PrologueInitializesEveryLocalSlot() {
        IRMethod method = methodWithLocals(eightIntParams(), manyParamLocals());
        IRClass clazz = owner(method);
        NativeBackend nb = new NativeBackend();
        nb.allClassesMap.put(clazz.name(), clazz);

        StringBuilder sb = new StringBuilder();
        new NativeMethodEmitter(nb).emitMethod(sb, clazz, method);
        String asm = sb.toString();
        java.util.Set<Integer> written = x86StoredOffsets(asm);

        for (IRLocalVariable lv : method.localVariables()) {
            int off = (lv.index() + 1) * 8;
            assertTrue(written.contains(-off),
                    "§625: x86 slot " + lv.index() + " (" + lv.name()
                            + ") never initialized in prologue (offset -" + off + "):\n" + asm);
        }
        assertTrue(asm.contains("movq $0, -80(%rbp)"),
                "§625: slot 9 (capture) must be zeroed in the x86 prologue:\n" + asm);
    }
}
