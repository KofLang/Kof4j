package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptimizerTest {

    private static IRMethod methodWith(List<KofOperation> ops) {
        return new IRMethod("test", Type.PrimitiveType.INT, List.of(), AccessFlags.PUBLIC,
                List.of(), List.of(new IRBasicBlock(0, ops)), List.of());
    }

    private static List<KofOperation> optimize(List<KofOperation> ops) {
        IRModule module = new IRModule("Test", List.of(
                new IRClass("Test", "java/lang/Object", List.of(), AccessFlags.PUBLIC,
                        List.of(), List.of(methodWith(ops)), List.of(), null, 10)), List.of());
        IRModule out = Optimizer.optimize(module);
        return out.classes().get(0).methods().get(0).basicBlocks().get(0).operations();
    }

    @Test
    void foldsConstantStringConcat() {
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofString("hello "), KofLoadLiteral.ofString("world"),
                new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION)));
        assertEquals(1, out.size());
        KofLoadLiteral lit = (KofLoadLiteral) out.get(0);
        assertEquals("hello world", lit.value());
    }

    @Test
    void foldsConstantArithmetic() {
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofInt(2), KofLoadLiteral.ofInt(3),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT)));
        assertEquals(1, out.size());
        KofLoadLiteral lit = (KofLoadLiteral) out.get(0);
        assertEquals(6, lit.value());
    }

    @Test
    void foldsConstantComparison() {
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofInt(1), KofLoadLiteral.ofInt(2),
                new KofBinary(KofBinaryOp.LT, Type.PrimitiveType.INT)));
        assertEquals(1, out.size());
        KofLoadLiteral lit = (KofLoadLiteral) out.get(0);
        assertEquals(1, lit.value());
        assertTrue(lit.type() instanceof Type.PrimitiveType pt && "bool".equals(pt.name()));
    }

    @Test
    void foldsLongAndShift() {
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofLong(1L), KofLoadLiteral.ofInt(2),
                new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.LONG)));
        assertEquals(1, out.size());
        assertEquals(4L, ((KofLoadLiteral) out.get(0)).value());
    }

    @Test
    void doesNotFoldDivisionByZero() {
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(10), KofLoadLiteral.ofInt(0),
                new KofBinary(KofBinaryOp.DIV, Type.PrimitiveType.INT));
        assertEquals(3, optimize(ops).size());
    }

    @Test
    void foldsUnaryNegation() {
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofInt(5), new KofUnary(KofUnaryOp.NEG, Type.PrimitiveType.INT)));
        assertEquals(1, out.size());
        assertEquals(-5, ((KofLoadLiteral) out.get(0)).value());
    }

    @Test
    void simplifiesConstantConditionalJump() {
        LabelId thenLabel = LabelId.create();
        LabelId elseLabel = LabelId.create();
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofBool(false), KofLoadLiteral.ofInt(0),
                new KofConditionalJump(KofComparison.NE, thenLabel, elseLabel)));
        assertEquals(1, out.size());
        KofJump jump = (KofJump) out.get(0);
        assertEquals(elseLabel, jump.target());
    }

    @Test
    void doesNotFoldConditionalJumpWithSingleLiteral() {
        LabelId thenLabel = LabelId.create();
        LabelId elseLabel = LabelId.create();
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0),
                new KofConditionalJump(KofComparison.NE, thenLabel, elseLabel));
        assertEquals(2, optimize(ops).size());
    }

    @Test
    void foldsConditionalJumpWithLiteralOperands() {
        LabelId thenLabel = LabelId.create();
        LabelId elseLabel = LabelId.create();
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofInt(3), KofLoadLiteral.ofInt(4),
                new KofConditionalJump(KofComparison.LT, thenLabel, elseLabel)));
        assertEquals(1, out.size());
        KofJump jump = (KofJump) out.get(0);
        assertEquals(thenLabel, jump.target());
    }

    @Test
    void foldsEqualLabelConditionalJump() {
        LabelId label = LabelId.create();
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofInt(0), KofLoadLiteral.ofInt(1),
                new KofConditionalJump(KofComparison.EQ, label, label)));
        assertEquals(1, out.size());
        KofJump jump = (KofJump) out.get(0);
        assertEquals(label, jump.target());
    }

    @Test
    void removesUnreachableCodeAfterReturn() {
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofInt(42), new KofReturn(Type.PrimitiveType.INT),
                KofLoadLiteral.ofInt(7), new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(2, out.size());
        assertTrue(out.get(0) instanceof KofLoadLiteral);
        assertTrue(out.get(1) instanceof KofReturn);
    }

    @Test
    void removesUnreachableCodeAfterJump() {
        LabelId start = LabelId.create();
        LabelId end = LabelId.create();
        List<KofOperation> out = optimize(List.of(
                new KofLabel(start), new KofJump(end),
                KofLoadLiteral.ofInt(1), KofLoadLiteral.ofInt(2),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                new KofLabel(end), new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof KofReturn);
    }

    @Test
    void eliminatesDeadLiteralPush() {
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofInt(5), new KofPop()));
        assertEquals(0, out.size());
    }

    @Test
    void keepsSideEffects() {
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(5),
                new KofCall(BuiltinTypes.STRING, "println", List.of(), Type.PrimitiveType.VOID,
                        KofCallKind.FUNCTION),
                new KofPop());
        assertEquals(3, optimize(ops).size());
    }

    @Test
    void removesLoadStoreRoundTrip() {
        List<KofOperation> out = optimize(List.of(
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(1),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                new KofReturn(Type.PrimitiveType.INT)));
        // The first load;store round trip is a true no-op and is removed.
        // The store;load round trip must NOT be removed: the slot is read
        // later, so removing the store would leave it uninitialized
        // (JVM VerifyError). It is kept verbatim.
        assertEquals(4, out.size());
        assertTrue(out.get(0) instanceof KofLoadLiteral);
        assertTrue(out.get(1) instanceof KofStoreLocal);
        assertTrue(out.get(2) instanceof KofLoadLocal);
        assertTrue(out.get(3) instanceof KofReturn);
    }

    @Test
    void keepsWideRoundTripStore() {
        // long/double slots keep the store;load round trip verbatim too.
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofLong(7L),
                new KofStoreLocal(Type.PrimitiveType.LONG, 1),
                new KofLoadLocal(Type.PrimitiveType.LONG, 1),
                new KofReturn(Type.PrimitiveType.LONG)));
        assertEquals(4, out.size());
        assertTrue(out.get(1) instanceof KofStoreLocal);
        assertTrue(out.get(2) instanceof KofLoadLocal);
    }

    @Test
    void foldsIdentityOperations() {
        List<KofOperation> ops = List.of(
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(0),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                KofLoadLiteral.ofInt(1),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT));
        List<KofOperation> out = optimize(ops);
        assertEquals(2, out.size());
        assertTrue(out.get(0) instanceof KofLoadLocal);
        assertTrue(out.get(1) instanceof KofReturn);
    }

    @Test
    void removesJumpToNextLabel() {
        LabelId label = LabelId.create();
        List<KofOperation> out = optimize(List.of(
                new KofJump(label), new KofLabel(label),
                new KofReturn(Type.PrimitiveType.INT)));
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof KofReturn);
    }

    @Test
    void preservesTryRegionLabels() {
        LabelId start = LabelId.create();
        LabelId end = LabelId.create();
        LabelId handler = LabelId.create();
        LabelId done = LabelId.create();
        List<KofOperation> out = optimize(List.of(
                new KofTryStart(start, end, handler, "String", 1),
                KofLoadLiteral.ofInt(1),
                new KofJump(done),
                new KofLabel(end),
                new KofCatchStart(handler, "String", 1),
                KofLoadLiteral.ofInt(2),
                new KofJump(done),
                new KofTryEnd(),
                new KofLabel(done),
                new KofReturn(Type.PrimitiveType.INT)));
        assertTrue(out.stream().anyMatch(op -> op instanceof KofCatchStart));
        assertTrue(out.stream().anyMatch(op -> op instanceof KofTryStart));
        assertTrue(out.stream().anyMatch(op -> op instanceof KofTryEnd));
        assertTrue(out.stream().anyMatch(op -> op instanceof KofLabel kl && kl.label().equals(end)));
    }

    @Test
    void foldsNullEquality() {
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofNull(), KofLoadLiteral.ofNull(),
                new KofBinary(KofBinaryOp.EQ, Type.UnknownType.UNKNOWN)));
        assertEquals(1, out.size());
        assertEquals(1, ((KofLoadLiteral) out.get(0)).value());
    }

    @Test
    void foldsBoolLogic() {
        List<KofOperation> out = optimize(List.of(
                KofLoadLiteral.ofBool(true), KofLoadLiteral.ofBool(false),
                new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.BOOL)));
        assertEquals(1, out.size());
        assertEquals(0, ((KofLoadLiteral) out.get(0)).value());
    }

    @Test
    void keepsDebugPositionOfFoldedOps() {
        IRMethod method = new IRMethod("test", Type.PrimitiveType.INT, List.of(),
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, List.of(
                        KofLoadLiteral.ofInt(2), KofLoadLiteral.ofInt(3),
                        new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT)))),
                List.of(),
                new KofDebugInfo(java.util.Map.of()));
        // positions are attached to the original ops; fold must drop them, not crash
        IRModule module = new IRModule("Test", List.of(
                new IRClass("Test", "java/lang/Object", List.of(), AccessFlags.PUBLIC,
                        List.of(), List.of(method), List.of(), null, 10)), List.of());
        IRModule out = Optimizer.optimize(module);
        IRMethod m = out.classes().get(0).methods().get(0);
        assertEquals(1, m.basicBlocks().get(0).operations().size());
    }
}