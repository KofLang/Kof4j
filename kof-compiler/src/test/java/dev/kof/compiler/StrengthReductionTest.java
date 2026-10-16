package dev.kof.compiler;

import dev.kof.compiler.backend.StrengthReduction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §12 of the assembly-optimization plan: strength reduction. A multiplication by
 * a power of two becomes a left shift, but ONLY where it is exactly equivalent
 * (int/long two's complement); never floating point, never division. The pass is
 * additive — these tests pin the rewrite and the conservative barriers.
 */
class StrengthReductionTest {

    private final StrengthReduction pass = new StrengthReduction();

    private List<KofOperation> run(List<KofOperation> ops) {
        return pass.run(ops, new java.util.IdentityHashMap<>());
    }

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void multiplyByTwoBecomesShiftLeftOne() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(2),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(4, out.size());
        assertEquals(KofLoadLiteral.ofInt(1), out.get(1), "2 -> shift 1");
        assertInstanceOf(KofBinary.class, out.get(2));
        assertEquals(KofBinaryOp.SHL, ((KofBinary) out.get(2)).op());
    }

    @Test
    void multiplyByPowerOfTwoUsesItsLog2() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(16),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofLoadLiteral.ofInt(4), out.get(1), "16 -> shift 4");
        assertEquals(KofBinaryOp.SHL, ((KofBinary) out.get(2)).op());
    }

    @Test
    void multiplyByNonPowerOfTwoIsUnchanged() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(3),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofBinaryOp.MUL, ((KofBinary) out.get(2)).op(),
                "3 is not a power of two -> keep MUL");
    }

    @Test
    void multiplyByOneIsNotShifted() {
        // 1 is identity; a shift by 0 is a no-op, so it is left for other passes.
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(1),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofBinaryOp.MUL, ((KofBinary) out.get(2)).op());
    }

    @Test
    void floatingPointMultiplyIsNeverShifted() {
        List<KofOperation> out = run(List.of(
                new KofLoadLocal(Type.PrimitiveType.DOUBLE, 1), KofLoadLiteral.ofDouble(2.0),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.DOUBLE),
                new KofReturn(Type.PrimitiveType.DOUBLE)));
        assertEquals(KofBinaryOp.MUL, ((KofBinary) out.get(2)).op(),
                "float * 2.0 is not a shift");
    }

    @Test
    void divisionIsNeverReduced() {
        // x / 2 rounds toward zero; x >> 1 rounds toward -inf. NOT equivalent.
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(2),
                new KofBinary(KofBinaryOp.DIV, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofBinaryOp.DIV, ((KofBinary) out.get(2)).op(),
                "division is not strength-reduced (negative-x divergence)");
    }

    @Test
    void longMultiplyUsesAnIntShiftAmount() {
        // The shift amount is ALWAYS an int, even for a long value: JVM `lshl`
        // takes (long,int) — a long shift literal is a VerifyError (§167).
        List<KofOperation> out = run(List.of(
                new KofLoadLocal(Type.PrimitiveType.LONG, 1), KofLoadLiteral.ofLong(8),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.LONG),
                new KofReturn(Type.PrimitiveType.LONG)));
        assertEquals(KofLoadLiteral.ofInt(3), out.get(1), "8 -> shift 3, as an INT literal");
        assertEquals(KofBinaryOp.SHL, ((KofBinary) out.get(2)).op());
        assertEquals(Type.PrimitiveType.LONG, ((KofBinary) out.get(2)).operandType(),
                "the SHL value type stays long");
    }
}
