package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 RISC-V 64 of the assembly-optimization plan ("lower-cost instruction
 * selection"): the riscv stack machine's push+pop triple
 * ({@code addi sp,sp,-8} / {@code sd X,0(sp)} / {@code pop Y}) collapses to
 * {@code mv Y, X} (or vanishes when {@code X == Y}).
 */
class NativeRiscvPeepholeTest {

    private static List<String> run(String... body) {
        List<String> lines = new ArrayList<>(Arrays.asList(body));
        NativeRiscvPeephole.collapsePushPopPairs(lines);
        return lines;
    }

    @Test
    void aRiscvPushPopPairBecomesAMove() {
        assertEquals(List.of("    mv a0, t0"),
                run("    addi sp, sp, -8", "    sd t0, 0(sp)", "    pop a0"));
    }

    @Test
    void aRiscvSelfPushPopPairIsDropped() {
        assertEquals(List.of(),
                run("    addi sp, sp, -8", "    sd t0, 0(sp)", "    pop t0"));
    }

    @Test
    void aRiscvLocBetweenDoesNotBlockTheCollapse() {
        assertEquals(List.of("    .loc 1 1 0", "    mv a0, t0", "    ret"),
                run("    .loc 1 1 0", "    addi sp, sp, -8", "    sd t0, 0(sp)",
                        "    .loc 1 2 0", "    pop a0", "    ret"));
    }

    @Test
    void aLargerStackAdjustIsNotCollapsed() {
        assertEquals(List.of("    addi sp, sp, -16", "    sd t0, 0(sp)", "    pop a0"),
                run("    addi sp, sp, -16", "    sd t0, 0(sp)", "    pop a0"));
    }

    @Test
    void aStoreAtANonZeroOffsetIsNotCollapsed() {
        assertEquals(List.of("    addi sp, sp, -8", "    sd t0, 8(sp)", "    pop a0"),
                run("    addi sp, sp, -8", "    sd t0, 8(sp)", "    pop a0"));
    }

    @Test
    void aSelfMoveIsDropped() {
        List<String> lines = new ArrayList<>(Arrays.asList(
                "    mv t0, t0", "    addi t0, t0, 1"));
        assertEquals(1, NativeRiscvPeephole.dropSelfMoves(lines));
        assertEquals(List.of("    addi t0, t0, 1"), lines);
    }

    @Test
    void aRealMoveIsKept() {
        List<String> lines = new ArrayList<>(Arrays.asList("    mv t0, t1"));
        assertEquals(0, NativeRiscvPeephole.dropSelfMoves(lines));
        assertEquals(List.of("    mv t0, t1"), lines);
    }
}