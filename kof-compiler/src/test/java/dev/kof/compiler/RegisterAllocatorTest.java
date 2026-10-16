package dev.kof.compiler;

import dev.kof.compiler.backend.InterferenceGraph;
import dev.kof.compiler.backend.RegisterAllocator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C of the assembly-optimization plan: the register allocator (greedy
 * graph coloring over the interference graph). It is a pure computation over the
 * IR — these tests pin correctness (no two interfering slots share a colour) and
 * the deterministic spill behaviour; compiler behavior is unchanged.
 */
class RegisterAllocatorTest {

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofStoreLocal store(int slot) {
        return new KofStoreLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void twoInterferingSlotsGetDistinctColors() {
        // slot 1 live across the def of slot 2 -> they interfere.
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(7), store(1),
                KofLoadLiteral.ofInt(8), store(2),
                load(1), load(2), new KofReturnVoid()));
        RegisterAllocator.Allocation a = RegisterAllocator.allocate(g, 2);
        assertFalse(a.isSpilled(1));
        assertFalse(a.isSpilled(2));
        assertNotEquals(a.color(1), a.color(2), "interfering slots must differ");
        assertEquals(2, a.colorsUsed());
    }

    @Test
    void nonInterferingSlotsMayShareAColor() {
        // disjoint lifetimes -> a single register suffices.
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(1), store(1), load(1),
                KofLoadLiteral.ofInt(2), store(2), load(2),
                new KofReturnVoid()));
        RegisterAllocator.Allocation a = RegisterAllocator.allocate(g, 1);
        assertFalse(a.isSpilled(1));
        assertFalse(a.isSpilled(2));
        assertEquals(a.color(1), a.color(2), "disjoint lifetimes share the register");
        assertEquals(1, a.colorsUsed());
    }

    @Test
    void aSlotWithNoFreeColorSpills() {
        // two interfering slots but only one register -> one must spill.
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(7), store(1),
                KofLoadLiteral.ofInt(8), store(2),
                load(1), load(2), new KofReturnVoid()));
        RegisterAllocator.Allocation a = RegisterAllocator.allocate(g, 1);
        assertEquals(1, a.spills().size(), "exactly one of the two slots spills");
        assertEquals(1, a.colorsUsed());
    }

    @Test
    void allocationNeverGivesInterferingSlotsTheSameColor() {
        // A chain of 3 mutually-live slots with 3 registers: all distinct.
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(0), store(1),
                KofLoadLiteral.ofInt(1), store(2),
                KofLoadLiteral.ofInt(2), store(3),
                load(1), load(2), load(3), new KofReturnVoid()));
        RegisterAllocator.Allocation a = RegisterAllocator.allocate(g, 3);
        for (int x : g.nodes()) {
            for (int y : g.neighbors(x)) {
                assertNotEquals(a.color(x), a.color(y),
                        "interfering slots " + x + "/" + y + " must not share a colour");
            }
        }
    }

    @Test
    void allocationIsDeterministic() {
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), store(1),
                KofLoadLiteral.ofInt(1), store(2),
                KofLoadLiteral.ofInt(2), store(3),
                load(1), load(2), load(3), new KofReturnVoid());
        RegisterAllocator.Allocation a = RegisterAllocator.allocate(InterferenceGraph.ofOps(ops), 2);
        RegisterAllocator.Allocation b = RegisterAllocator.allocate(InterferenceGraph.ofOps(ops), 2);
        assertEquals(a.colorOf(), b.colorOf());
        assertEquals(a.spills(), b.spills());
    }

    @Test
    void zeroRegistersIsRejected() {
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(1), store(1), load(1), new KofReturnVoid()));
        assertThrows(IllegalArgumentException.class, () -> RegisterAllocator.allocate(g, 0));
    }
}
