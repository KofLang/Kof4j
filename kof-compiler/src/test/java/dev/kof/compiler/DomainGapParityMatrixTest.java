package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R6 machine gate (mirrors the R1 boundary gate): every gap code the compiler
 * is proven to emit — i.e. every code pinned by {@link DomainGapCodesTest}'s
 * {@code assertGap} calls — must appear in {@code docs/backend-parity.md}. The
 * ledger is read from the pin file's own {@code assertGap} calls, so the check
 * cannot rot: adding a pin without a matrix row fails here.
 *
 * <p>Split out of {@code DomainGapCodesTest} (30/09, test-architecture Phase 3)
 * to keep the pin file under the 500-line gate; the pins themselves are
 * unchanged.
 */
class DomainGapParityMatrixTest {

    private static final Pattern GAP_CODE = Pattern.compile("\"([A-Z]{2,6}[0-9]{3})\"");

    @Test
    void everyPinnedGapIsDocumentedInTheParityMatrix() throws IOException {
        Path root = repoRoot();
        Set<String> pinned = new LinkedHashSet<>();
        Matcher m = GAP_CODE.matcher(Files.readString(root.resolve(
                "kof-compiler/src/test/java/dev/kof/compiler/DomainGapCodesTest.java")));
        while (m.find()) pinned.add(m.group(1));
        assertFalse(pinned.isEmpty(), "no gap codes found in the pin file's assertGap calls");

        String matrix = Files.readString(root.resolve("docs/backend-parity.md"));
        for (String code : pinned) {
            assertTrue(matrix.contains(code),
                    "gap " + code + " is pinned by the guard (the compiler emits it) "
                            + "but has no entry in docs/backend-parity.md (R6)");
        }
    }

    /** Repo root, found by walking up to the parity matrix (same as
     *  {@code ConformanceMatrixDocTest}). */
    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            if (Files.exists(p.resolve("docs/backend-parity.md"))) return p;
        }
        throw new IllegalStateException("backend-parity.md not found from " + p);
    }
}
