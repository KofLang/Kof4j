package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance + equivalence proof for the RISCV64 peephole's push/pop fold.
 *
 * <p>The historical implementation restarted the scan from index 0 after every
 * fold ({@code while (changed) { for (i = 0; ...) { ...; break; } }}), which is
 * O(folds x lines) regex matches over the whole method+program text — measured
 * at 52 s vs 8 s with {@code -Dkof.native.peephole.disable=1} on the AV1 cross
 * faces, and hours inside a full suite under memory pressure. A single forward
 * pass finds the same folds: a fold only removes lines at/after its own start,
 * so it never introduces a new {@code addi sp, sp, -8}. The only position that
 * can become foldable through a self-move removal is the candidate formed by
 * the last non-debug line before the removal ({@code m - 1}); the differencing
 * test below pins that equivalence against the historical algorithm verbatim.
 */
class NativeRiscvPeepholePerformanceTest {

    private static final Pattern REF_STORE = Pattern.compile("^\\s*sd\\s+(\\S+),\\s*0\\(sp\\)$");
    private static final Pattern REF_POP = Pattern.compile("^\\s*pop\\s+(\\S+)$");

    /** The historical algorithm, verbatim, as the equivalence oracle. */
    private static int referenceCollapse(List<String> lines) {
        int folded = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 2 < lines.size(); i++) {
                if (!lines.get(i).strip().equals("addi sp, sp, -8")) continue;
                Matcher mStore = REF_STORE.matcher(lines.get(i + 1));
                if (!mStore.matches()) continue;
                int k = i + 2;
                while (k < lines.size() && refDebug(lines.get(k))) k++;
                if (k >= lines.size()) continue;
                Matcher mPop = REF_POP.matcher(lines.get(k));
                if (!mPop.matches()) continue;
                String x = mStore.group(1);
                String y = mPop.group(1);
                String indent = lines.get(k).substring(0,
                        lines.get(k).length() - lines.get(k).stripLeading().length());
                if (x.equals(y)) {
                    for (int z = k; z >= i; z--) lines.remove(z);
                } else {
                    lines.set(i, indent + "mv " + y + ", " + x);
                    for (int z = k; z > i; z--) lines.remove(z);
                }
                folded++;
                changed = true;
                break;
            }
        }
        return folded;
    }

    private static boolean refDebug(String line) {
        String t = line.strip();
        return t.isEmpty() || t.startsWith(".loc ") || t.startsWith(".file ");
    }

    private static final List<String[]> FRAGMENTS = List.of(
            new String[]{"    addi sp, sp, -8\n    sd t0, 0(sp)\n    pop a0"},
            new String[]{"    addi sp, sp, -8\n    sd a2, 0(sp)\n    pop a2"},
            new String[]{"    addi sp, sp, -8\n    sd a1, 0(sp)\n    .loc 1 7 0\n    pop t1"},
            new String[]{"    addi sp, sp, -16\n    sd t0, 0(sp)\n    pop a0"},
            new String[]{"    addi sp, sp, -8\n    sd t0, 8(sp)\n    pop a0"},
            new String[]{"    addi sp, sp, -8\n    sd s1, 0(sp)\n    addi t0, t0, 1"},
            new String[]{"    mv t0, t0"},
            new String[]{"    mv a0, t1"},
            new String[]{"    .loc 1 3 0"},
            new String[]{"    .file 1 \"x.kf\""},
            new String[]{""},
            new String[]{"    ret"},
            new String[]{"    lw a0, 0(sp)"});

    private static List<String> buildCorpus(Random rnd, int fragments) {
        List<String> lines = new ArrayList<>();
        for (int f = 0; f < fragments; f++) {
            String[] frag = FRAGMENTS.get(rnd.nextInt(FRAGMENTS.size()));
            lines.addAll(Arrays.asList(frag[0].split("\n", -1)));
        }
        return lines;
    }

    @Test
    void randomizedCorporaMatchTheReferenceAlgorithmByteForByte() {
        Random rnd = new Random(20261120L);
        for (int round = 0; round < 400; round++) {
            final int r = round;
            int size = 1 + rnd.nextInt(40);
            List<String> mine = buildCorpus(rnd, size);
            List<String> reference = new ArrayList<>(mine);
            int mineFolded = NativeRiscvPeephole.collapsePushPopPairs(mine);
            final int refFolded = referenceCollapse(reference);
            assertEquals(reference, mine, () -> "round " + r + " diverged from the reference");
            assertEquals(refFolded, mineFolded, () -> "round " + r + " fold count diverged");
        }
    }

    /**
     * The regression: folds DISPERSED through the text, which is the shape of a
     * real function body (a push/pop pair every few instructions). The historical
     * scan restarts at index 0 after every fold, so it re-walks the whole already
     * folded prefix — O(folds x lines) strip+regex. The expected result is still
     * analytic: N {@code mv a0, t0} lines after the padding, N folds.
     */
    @Test
    void aLargeCorpusWithDispersedFoldsFoldsInsideTheLinearBound() {
        final int triples = 6_000;
        final int padding = 20;
        List<String> lines = new ArrayList<>(triples * (padding + 3));
        for (int t = 0; t < triples; t++) {
            for (int p = 0; p < padding; p++) {
                lines.add("    addi t0, t0, 1");
            }
            lines.add("    addi sp, sp, -8");
            lines.add("    sd t0, 0(sp)");
            lines.add("    pop a0");
        }

        long start = System.nanoTime();
        int folded = NativeRiscvPeephole.collapsePushPopPairs(lines);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(triples, folded, "every dispersed triple must fold");
        assertEquals(triples * (padding + 1), lines.size());
        int moves = 0;
        for (String line : lines) {
            if (line.equals("    mv a0, t0")) moves++;
        }
        assertEquals(triples, moves, "each triple collapses to exactly one mv");
        // MEASURED: the restart-from-zero scan needs 7971 ms on this corpus (the
        // reference in this file); the linear pass needs ~5 ms. The bound is two
        // orders of magnitude above the linear cost and four below the old one.
        assertTrue(elapsedMs < 2_000,
                "folding " + triples + " dispersed triples took " + elapsedMs
                        + " ms — the scan must not restart from zero after each fold");
    }
}
