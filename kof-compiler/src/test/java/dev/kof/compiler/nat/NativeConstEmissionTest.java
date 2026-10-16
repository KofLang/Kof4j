package dev.kof.compiler.nat;

import dev.kof.compiler.KofLoadLiteral;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 x86_64 of {@code docs/development/assembly-optimization-plan.md}:
 * "reduction of instructions for constants". An immediate that fits in a
 * sign-extended imm32 is emitted as a single {@code pushq $imm} instead of
 * {@code movq $imm, %rax; pushq %rax} (2 instructions → 1).
 */
class NativeConstEmissionTest {

    @Test
    void anIntConstantIsASinglePushImmediate() {
        StringBuilder sb = new StringBuilder();
        NativeOpHelpers.emitLoadLiteral(null, sb, KofLoadLiteral.ofInt(5));
        assertEquals("    pushq $5\n", sb.toString(),
                "an int constant is one pushq $imm, not movq+pushq");
    }

    @Test
    void aNegativeIntConstantIsASinglePushImmediate() {
        StringBuilder sb = new StringBuilder();
        NativeOpHelpers.emitLoadLiteral(null, sb, KofLoadLiteral.ofInt(-3));
        assertEquals("    pushq $-3\n", sb.toString());
    }

    @Test
    void aSmallLongConstantIsASinglePushImmediate() {
        StringBuilder sb = new StringBuilder();
        NativeOpHelpers.emitLoadLiteral(null, sb, KofLoadLiteral.ofLong(7L));
        assertEquals("    pushq $7\n", sb.toString(),
                "a long that round-trips through int is one pushq $imm");
    }

    @Test
    void aWideLongConstantKeepsTheMovqPath() {
        // A long beyond imm32 cannot be pushed directly: it must materialize
        // through %rax (movq $imm, %rax; pushq %rax).
        StringBuilder sb = new StringBuilder();
        NativeOpHelpers.emitLoadLiteral(null, sb, KofLoadLiteral.ofLong(0x1_0000_0000L));
        assertEquals("    movq $4294967296, %rax\n    pushq %rax\n", sb.toString(),
                "a wide long keeps the two-instruction path");
    }

    @Test
    void aBooleanConstantIsASinglePushImmediate() {
        StringBuilder sb = new StringBuilder();
        NativeOpHelpers.emitLoadLiteral(null, sb, new KofLoadLiteral(dev.kof.compiler.Type.PrimitiveType.BOOL, true));
        assertEquals("    pushq $1\n", sb.toString());
    }
}
