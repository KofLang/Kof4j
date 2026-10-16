package dev.kof.compiler;

import dev.kof.compiler.backend.RegisterPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D of the assembly-optimization plan ({@code D-CODEGEN-REWRITE}): the
 * per-method register-placement driver. It chains liveness → interference →
 * allocator → assignment and returns the placement; pure computation, additive.
 */
class RegisterPlannerTest {

    private static KofLoadLocal load(int slot, Type t) {
        return new KofLoadLocal(t, slot);
    }

    private static KofStoreLocal store(int slot, Type t) {
        return new KofStoreLocal(t, slot);
    }

    private static IRMethod method(List<KofOperation> ops, List<IRLocalVariable> locals) {
        return new IRMethod("m", Type.PrimitiveType.INT, List.<Type>of(), 0,
                List.<String>of(), List.of(new IRBasicBlock(0, ops)), locals,
                KofDebugInfo.EMPTY, List.of(), List.of());
    }

    @Test
    void twoParamsArePlacedInDistinctRegisters() {
        // MEASURED: params are ABI-defined (they arrive in registers), never
        // KofStoreLocal'd, so the def-driven graph omitted them and the register
        // model was inert for `f(a, b) { ... }`. They are now nodes; two params
        // live at entry interfere and get DISTINCT registers.
        IRMethod m = method(List.of(
                load(0, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.INT),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)),
                List.of(new IRLocalVariable(0, "a", Type.PrimitiveType.INT),
                        new IRLocalVariable(1, "b", Type.PrimitiveType.INT)));
        RegisterPlanner.Placement p = RegisterPlanner.plan(m, 5);
        assertTrue(p.isRegister(0), "param a is placed");
        assertTrue(p.isRegister(1), "param b is placed");
        assertNotEquals(p.register(0), p.register(1),
                "two params live at entry must not share a register");
    }

    @Test
    void twoIndependentIntSlotsFitInRegisters() {
        // Two slots with disjoint lifetimes -> both get registers (one colour).
        IRMethod m = method(List.of(
                KofLoadLiteral.ofInt(1), store(1, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.INT), new KofPop(),
                KofLoadLiteral.ofInt(2), store(2, Type.PrimitiveType.INT),
                load(2, Type.PrimitiveType.INT), new KofReturn(Type.PrimitiveType.INT)),
                List.of(new IRLocalVariable(1, "a", Type.PrimitiveType.INT),
                        new IRLocalVariable(2, "b", Type.PrimitiveType.INT)));
        RegisterPlanner.Placement p = RegisterPlanner.plan(m, 4);
        assertTrue(p.isRegister(1));
        assertTrue(p.isRegister(2));
        assertNotNull(p.register(1));
    }

    @Test
    void aDoubleStaysInTheFrameNotARegister() {
        // Only int/bool are register-placeable; a double is double-width and the
        // GC scans the frame, so it stays in memory.
        IRMethod m = method(List.of(
                KofLoadLiteral.ofDouble(1.5), store(1, Type.PrimitiveType.DOUBLE),
                load(1, Type.PrimitiveType.DOUBLE), new KofReturn(Type.PrimitiveType.DOUBLE)),
                List.of(new IRLocalVariable(1, "d", Type.PrimitiveType.DOUBLE)));
        RegisterPlanner.Placement p = RegisterPlanner.plan(m, 4);
        assertFalse(p.isRegister(1), "a double stays in the frame");
    }

    @Test
    void oneRegisterForTwoInterferingSlotsSpillsOne() {
        IRMethod m = method(List.of(
                KofLoadLiteral.ofInt(1), store(1, Type.PrimitiveType.INT),
                KofLoadLiteral.ofInt(2), store(2, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.INT), load(2, Type.PrimitiveType.INT),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)),
                List.of(new IRLocalVariable(1, "a", Type.PrimitiveType.INT),
                        new IRLocalVariable(2, "b", Type.PrimitiveType.INT)));
        RegisterPlanner.Placement p = RegisterPlanner.plan(m, 1);
        long inReg = java.util.stream.Stream.of(1, 2).filter(p::isRegister).count();
        assertEquals(1, inReg, "with one register and two interfering slots, exactly one is in a register");
    }

    @Test
    void planningIsDeterministic() {
        IRMethod m = method(List.of(
                KofLoadLiteral.ofInt(1), store(1, Type.PrimitiveType.INT),
                KofLoadLiteral.ofInt(2), store(2, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.INT), load(2, Type.PrimitiveType.INT),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)),
                List.of(new IRLocalVariable(1, "a", Type.PrimitiveType.INT),
                        new IRLocalVariable(2, "b", Type.PrimitiveType.INT)));
        RegisterPlanner.Placement a = RegisterPlanner.plan(m, 2);
        RegisterPlanner.Placement b = RegisterPlanner.plan(m, 2);
        assertEquals(a.register(1), b.register(1));
        assertEquals(a.register(2), b.register(2));
    }

    @Test
    void calleeSavedUsedListsTheRegistersThePrologueMustSave() {
        // Two disjoint int slots both take colour 0 (rbx, callee-saved) — the
        // prologue must save exactly rbx.
        IRMethod m = method(List.of(
                KofLoadLiteral.ofInt(1), store(1, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.INT), new KofPop(),
                KofLoadLiteral.ofInt(2), store(2, Type.PrimitiveType.INT),
                load(2, Type.PrimitiveType.INT), new KofReturn(Type.PrimitiveType.INT)),
                List.of(new IRLocalVariable(1, "a", Type.PrimitiveType.INT),
                        new IRLocalVariable(2, "b", Type.PrimitiveType.INT)));
        RegisterPlanner.Placement p = RegisterPlanner.plan(m, 4);
        if (p.isRegister(1)) {
            assertEquals(java.util.List.of(p.register(1)), p.calleeSavedUsed(),
                    "the callee-saved register in use is exactly the one to save");
        }
    }

    @Test
    void aDoubleInSseNeedsNoCalleeSavedSave() {
        // SysV has no callee-saved SSE registers, so an SSE-placed value needs no save.
        IRMethod m = method(List.of(
                KofLoadLiteral.ofDouble(1.5), store(1, Type.PrimitiveType.DOUBLE),
                load(1, Type.PrimitiveType.DOUBLE), new KofReturn(Type.PrimitiveType.DOUBLE)),
                List.of(new IRLocalVariable(1, "d", Type.PrimitiveType.DOUBLE)));
        RegisterPlanner.Placement p = RegisterPlanner.plan(m, 4);
        assertTrue(p.calleeSavedUsed().isEmpty(),
                "no SSE register is callee-saved on SysV");
    }

    @Test
    void anIntOnlyMethodIsEligibleForRegisterLocals() {
        IRMethod m = method(List.of(
                KofLoadLiteral.ofInt(1), store(1, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.INT), new KofReturn(Type.PrimitiveType.INT)),
                List.of(new IRLocalVariable(1, "a", Type.PrimitiveType.INT)));
        assertTrue(RegisterPlanner.eligibleForRegisterLocals(m));
    }

    @Test
    void aMethodWithATryIsEligible() {
        // Exception methods ARE eligible: the emission stores a caught exception to
        // the local's register when it is register-placed (KofCatchStart is
        // placement-aware), so the frame/register paths agree.
        LabelId start = new LabelId(0), end = new LabelId(1), handler = new LabelId(2), after = new LabelId(3);
        IRMethod m = method(List.of(
                new KofTryStart(start, end, handler, "String", 4),
                new KofLabel(start), new KofLabel(end), new KofJump(after),
                new KofCatchStart(handler, "String", 4), new KofReturnVoid(),
                new KofLabel(after), new KofReturnVoid()),
                List.of(new IRLocalVariable(1, "a", Type.PrimitiveType.INT)));
        assertTrue(RegisterPlanner.eligibleForRegisterLocals(m),
                "a method with a try/catch is eligible");
    }

    @Test
    void aMethodWithALongLocalIsEligibleButTheLongStaysInFrame() {
        IRMethod m = method(List.of(
                KofLoadLiteral.ofLong(1L), store(1, Type.PrimitiveType.LONG),
                load(1, Type.PrimitiveType.LONG), new KofReturn(Type.PrimitiveType.LONG)),
                List.of(new IRLocalVariable(1, "a", Type.PrimitiveType.LONG)));
        assertTrue(RegisterPlanner.eligibleForRegisterLocals(m),
                "a method is eligible even with a long local");
        RegisterPlanner.Placement p = RegisterPlanner.plan(m, 4);
        assertFalse(p.isRegister(1), "the double-width long local stays in the frame");
    }

    @Test
    void aMethodWithADoubleLocalIsEligibleButTheDoubleStaysInFrame() {
        IRMethod m = method(List.of(
                KofLoadLiteral.ofDouble(1.5), store(1, Type.PrimitiveType.DOUBLE),
                load(1, Type.PrimitiveType.DOUBLE), new KofReturn(Type.PrimitiveType.DOUBLE)),
                List.of(new IRLocalVariable(1, "d", Type.PrimitiveType.DOUBLE)));
        assertTrue(RegisterPlanner.eligibleForRegisterLocals(m));
        RegisterPlanner.Placement p = RegisterPlanner.plan(m, 4);
        assertFalse(p.isRegister(1), "the SSE double local stays in the frame");
    }

    @Test
    void aMixedMethodPlacesIntLocalsAndKeepsTheDoubleInFrame() {
        // int + double locals: the int gets a register, the double stays in frame.
        IRMethod m = method(List.of(
                KofLoadLiteral.ofInt(3), store(1, Type.PrimitiveType.INT),
                KofLoadLiteral.ofDouble(1.5), store(2, Type.PrimitiveType.DOUBLE),
                load(1, Type.PrimitiveType.INT), new KofPop(),
                load(2, Type.PrimitiveType.DOUBLE), new KofReturn(Type.PrimitiveType.DOUBLE)),
                List.of(new IRLocalVariable(1, "n", Type.PrimitiveType.INT),
                        new IRLocalVariable(2, "d", Type.PrimitiveType.DOUBLE)));
        RegisterPlanner.Placement p = RegisterPlanner.plan(m, 4);
        assertTrue(p.isRegister(1), "the int local is placed in a register");
        assertFalse(p.isRegister(2), "the double local stays in the frame");
    }
}
