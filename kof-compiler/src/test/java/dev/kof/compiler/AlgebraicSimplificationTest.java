package dev.kof.compiler;

import dev.kof.compiler.backend.AlgebraicSimplification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B/§3 O1 of the assembly-optimization plan: algebraic simplification of
 * the absorbing elements ({@code x * 0}, {@code x & 0}, {@code x % 1}). The pass
 * is additive — these tests pin the rewrite and the conservative barriers
 * (floating point, division, impure producers).
 */
class AlgebraicSimplificationTest {

    private final AlgebraicSimplification pass = new AlgebraicSimplification();

    private List<KofOperation> run(List<KofOperation> ops) {
        return pass.run(ops, new java.util.IdentityHashMap<>());
    }

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void multiplyByZeroBecomesZero() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(0),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(2, out.size(), "x*0 folds to a literal, keeping the return");
        assertEquals(KofLoadLiteral.ofInt(0), out.get(0));
        assertInstanceOf(KofReturn.class, out.get(1));
    }

    @Test
    void andZeroBecomesZero() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(0),
                new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofLoadLiteral.ofInt(0), out.get(0));
    }

    @Test
    void modOneBecomesZero() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(1),
                new KofBinary(KofBinaryOp.MOD, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofLoadLiteral.ofInt(0), out.get(0), "x % 1 is always 0");
    }

    @Test
    void modMinusOneBecomesZero() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(-1),
                new KofBinary(KofBinaryOp.MOD, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofLoadLiteral.ofInt(0), out.get(0), "x % -1 is always 0");
    }

    @Test
    void multiplyByNonZeroIsUnchanged() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(2),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(4, out.size(), "x*2 is not an absorbing element");
    }

    @Test
    void floatingPointMultiplyByZeroIsNeverFolded() {
        // x * 0.0 is NaN for x = NaN/Inf, -0.0 otherwise — NOT always 0.
        List<KofOperation> out = run(List.of(
                new KofLoadLocal(Type.PrimitiveType.DOUBLE, 1), KofLoadLiteral.ofDouble(0.0),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.DOUBLE),
                new KofReturn(Type.PrimitiveType.DOUBLE)));
        assertEquals(4, out.size(), "float x*0.0 must not fold to 0");
    }

    @Test
    void divisionByZeroIsNeverTouched() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(0),
                new KofBinary(KofBinaryOp.DIV, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(4, out.size(), "x/0 traps; it is never folded");
    }

    @Test
    void impureProducerIsKept() {
        // call(); 0; MUL — the producer is a call, so nothing is removed.
        List<KofOperation> out = run(List.of(
                new KofCall(null, "m", List.of(), Type.PrimitiveType.INT, KofCallKind.STATIC),
                KofLoadLiteral.ofInt(0),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(4, out.size(), "a call producer is never dropped");
        assertTrue(out.stream().anyMatch(o -> o instanceof KofCall));
    }

    @Test
    void longMultiplyByZeroYieldsALongZero() {
        List<KofOperation> out = run(List.of(
                new KofLoadLocal(Type.PrimitiveType.LONG, 1), KofLoadLiteral.ofLong(0),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.LONG),
                new KofReturn(Type.PrimitiveType.LONG)));
        assertEquals(KofLoadLiteral.ofLong(0), out.get(0), "long x*0 -> long 0");
    }

    @Test
    void subtractSameLocalBecomesZero() {
        List<KofOperation> out = run(List.of(
                load(1), load(1),
                new KofBinary(KofBinaryOp.SUB, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(2, out.size(), "x - x folds to a literal zero");
        assertEquals(KofLoadLiteral.ofInt(0), out.get(0));
    }

    @Test
    void xorSameLocalBecomesZero() {
        List<KofOperation> out = run(List.of(
                load(1), load(1),
                new KofBinary(KofBinaryOp.XOR, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofLoadLiteral.ofInt(0), out.get(0), "x ^ x is always 0");
    }

    @Test
    void subtractDifferentLocalsIsUnchanged() {
        List<KofOperation> out = run(List.of(
                load(1), load(2),
                new KofBinary(KofBinaryOp.SUB, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(4, out.size(), "x - y (different slots) is not folded");
    }

    @Test
    void addSameLocalIsNotFolded() {
        // x + x is 2x, not an annihilator.
        List<KofOperation> out = run(List.of(
                load(1), load(1),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(4, out.size(), "x + x must not fold to 0");
    }

    @Test
    void floatSubtractSameIsNotFolded() {
        // NaN - NaN is NaN, not 0; float x - x must not fold.
        List<KofOperation> out = run(List.of(
                new KofLoadLocal(Type.PrimitiveType.DOUBLE, 1),
                new KofLoadLocal(Type.PrimitiveType.DOUBLE, 1),
                new KofBinary(KofBinaryOp.SUB, Type.PrimitiveType.DOUBLE),
                new KofReturn(Type.PrimitiveType.DOUBLE)));
        assertEquals(4, out.size(), "float x - x is not 0 (NaN)");
    }

    @Test
    void equalSameLocalBecomesTrue() {
        List<KofOperation> out = run(List.of(
                load(1), load(1),
                new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofLoadLiteral.ofBool(true), out.get(0), "x == x is always true");
    }

    @Test
    void lessThanSameLocalBecomesFalse() {
        List<KofOperation> out = run(List.of(
                load(1), load(1),
                new KofBinary(KofBinaryOp.LT, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(KofLoadLiteral.ofBool(false), out.get(0), "x < x is always false");
    }

    @Test
    void floatEqualSameIsNotFolded() {
        // NaN == NaN is false, so float x == x is not always true.
        List<KofOperation> out = run(List.of(
                new KofLoadLocal(Type.PrimitiveType.DOUBLE, 1),
                new KofLoadLocal(Type.PrimitiveType.DOUBLE, 1),
                new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.DOUBLE),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(4, out.size(), "float x == x is not always true (NaN)");
    }

    @Test
    void orMinusOneBecomesMinusOne() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(-1),
                new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(2, out.size(), "x | -1 folds to the constant -1");
        assertEquals(KofLoadLiteral.ofInt(-1), out.get(0));
    }

    @Test
    void andMinusOneIsIdentity() {
        // x & -1 == x: the producer is kept, the literal and the AND are dropped.
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(-1),
                new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(2, out.size(), "x & -1 keeps x and drops the AND");
        assertEquals(load(1), out.get(0));
    }

    @Test
    void orNonMinusOneIsUnchanged() {
        List<KofOperation> out = run(List.of(
                load(1), KofLoadLiteral.ofInt(3),
                new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(4, out.size(), "x | 3 is not an absorbing element");
    }
}
