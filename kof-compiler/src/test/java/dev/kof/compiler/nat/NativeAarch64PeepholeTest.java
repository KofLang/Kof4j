package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 AArch64 of the assembly-optimization plan ("lower-cost instruction
 * selection"): the riscv stack machine's push+pop triple
 * ({@code sub sp,sp,#8} / {@code str X,[sp]} / {@code pop Y}) collapses to
 * {@code mov Y, X} (or vanishes when {@code X == Y}).
 */
class NativeAarch64PeepholeTest {

    private static List<String> run(String... body) {
        List<String> lines = new ArrayList<>(Arrays.asList(body));
        NativeAarch64Peephole.collapsePushPopPairs(lines);
        return lines;
    }

    @Test
    void anAarch64PushPopPairBecomesAMove() {
        assertEquals(List.of("    mov x0, x9"),
                run("    sub sp, sp, #8", "    str x9, [sp]", "    pop x0"));
    }

    @Test
    void aSelfPushPopPairIsDropped() {
        assertEquals(List.of(),
                run("    sub sp, sp, #8", "    str x9, [sp]", "    pop x9"));
    }

    @Test
    void anAarch64LocBetweenDoesNotBlockTheCollapse() {
        assertEquals(List.of("    .loc 1 1 0", "    mov x0, x9", "    ret"),
                run("    .loc 1 1 0", "    sub sp, sp, #8", "    str x9, [sp]",
                        "    .loc 1 2 0", "    pop x0", "    ret"));
    }

    @Test
    void aStackAdjustWithATwoWordStoreIsNotCollapsed() {
        // Only the exact four-byte push shape is folded.
        assertEquals(List.of("    sub sp, sp, #16", "    str x9, [sp]", "    pop x0"),
                run("    sub sp, sp, #16", "    str x9, [sp]", "    pop x0"));
    }

    @Test
    void aPushWithoutAnImmediatePopIsKept() {
        assertEquals(List.of("    sub sp, sp, #8", "    str x9, [sp]", "    mov x0, #0"),
                run("    sub sp, sp, #8", "    str x9, [sp]", "    mov x0, #0"));
    }

    @Test
    void anAarch64SelfMoveIsDropped() {
        List<String> lines = new ArrayList<>(Arrays.asList("    mov x9, x9", "    add x10, x10, #1"));
        assertEquals(1, NativeAarch64Peephole.dropSelfMoves(lines));
        assertEquals(List.of("    add x10, x10, #1"), lines);
    }

    @Test
    void anAarch64RealMoveIsKept() {
        List<String> lines = new ArrayList<>(Arrays.asList("    mov x9, x10"));
        assertEquals(0, NativeAarch64Peephole.dropSelfMoves(lines));
        assertEquals(List.of("    mov x9, x10"), lines);
    }
}
