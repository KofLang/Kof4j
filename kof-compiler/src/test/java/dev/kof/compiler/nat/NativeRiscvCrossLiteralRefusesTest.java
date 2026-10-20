package dev.kof.compiler.nat;

import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Q7 (no silent fallback) / Phase D: the cross (riscv64/aarch64) emitter must
 * REFUSE a literal kind it does not lower — never silently push {@code 0} (which
 * would leave the compile "succeeding" with a WRONG binary). A {@code char} value
 * boxed as a raw {@link Character} is not a supported literal kind here (Kof lowers
 * chars as int), so it is the natural probe.
 */
class NativeRiscvCrossLiteralRefusesTest {

    @Test
    void anUnsupportedLiteralKindIsRefusedNotSilentlyZeroed() {
        NativeBackend nb = new NativeBackend();
        NativeRiscvCrossEmit emit = nb.crossEmit();
        StringBuilder sb = new StringBuilder();
        KofLoadLiteral bad = new KofLoadLiteral(Type.PrimitiveType.CHAR, 'x');
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> emit.emitCrossLoadLiteralRiscv(sb, bad),
                "an unsupported literal must throw NATIVE002, not push 0");
        assertTrue(e.getMessage().startsWith("NATIVE002"), e.getMessage());
        assertFalse(sb.toString().contains("li t0, 0"),
                "no silent zero should be emitted; got: " + sb);
    }
}
