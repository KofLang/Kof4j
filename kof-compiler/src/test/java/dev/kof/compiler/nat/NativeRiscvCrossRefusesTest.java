package dev.kof.compiler.nat;

import dev.kof.compiler.KofFunctionAddress;
import dev.kof.compiler.Type;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Q7 (no silent fallback) / Phase D of {@code docs/assembly-optimization-plan.md}:
 * the cross (riscv64/aarch64) emitter must REFUSE an op it does not lower — never
 * silently emit a comment (which would leave the compile "succeeding" with a wrong
 * binary). {@code KofFunctionAddress} is the one IR op NOT in
 * {@link NativeOpSupport#RISCV64_CROSS}, so it is the natural probe.
 */
class NativeRiscvCrossRefusesTest {

    @Test
    void anUndeclaredOpIsRefusedNotSilentlySkipped() {
        assertFalse(NativeOpSupport.supportsCross(KofFunctionAddress.class),
                "KofFunctionAddress is deliberately not in the cross set");
        NativeBackend nb = new NativeBackend();
        NativeRiscvCrossEmit emit = nb.crossEmit();
        StringBuilder sb = new StringBuilder();
        KofFunctionAddress op = new KofFunctionAddress(
                Type.UnknownType.UNKNOWN, "f", List.of());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> emit.emitCrossOpRiscv(sb, op, 0, false),
                "an undeclared op must throw NATIVE002, not emit a comment");
        assertTrue(e.getMessage().startsWith("NATIVE002"), e.getMessage());
        // And nothing was silently emitted for it.
        assertTrue(sb.isEmpty() || !sb.toString().contains("NATIVE002"),
                "no silent comment should be emitted; got: " + sb);
    }
}
