package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §652 — the ASM 9.10.1 PIN is the mitigation for the
 * {@code ComputeLimits} regression: ASM 9.11 makes the JVM backend's
 * COMPUTE_FRAMES throw {@code LimitExceededException: Too many operations} on
 * large dense methods, which
 * {@code CoreRegressionE2ETest#largeDenseFileLoadsOnJvm} reproduces (measured
 * this cycle: 1/1 GREEN at the pin, and the resolved dependency is 9.10.1).
 *
 * <p>The pin was silently undone once by a dependabot GROUP bump
 * ({@code 137ea696d}) while the pom comment still claimed it held. This
 * tripwire fails if the property moves past 9.10.1 or if the dependabot ignore
 * is dropped, so a re-bump requires a conscious proof (an upstream release
 * lifting the limit) instead of a routine dependency update.
 *
 * <p>Self-test hook: {@code KOF_TEST_REPO_ROOT} points the reader at a fixture
 * root (the same style as the shell gates' {@code --selftest}). It exists so
 * the tripwire can be proven RED against a fixture without editing the real
 * pom — an offline Maven cannot resolve a bumped ASM artifact, so the failure
 * would otherwise happen before the test runs.
 */
class AsmVersionPinTest {

    private static final String PINNED = "9.10.1";

    @Test
    void asmStaysPinnedAt9101() throws Exception {
        String pom = Files.readString(repoRoot().resolve("pom.xml"));
        Matcher m = Pattern.compile("<asm\\.version>([^<]+)</asm\\.version>").matcher(pom);
        assertTrue(m.find(), "the root pom must declare <asm.version>");
        assertEquals(PINNED, m.group(1).trim(),
                "known-bugs §652: ASM 9.11+ adds ComputeLimits and the JVM backend's "
                        + "COMPUTE_FRAMES dies with LimitExceededException on large dense "
                        + "methods (CoreRegressionE2ETest#largeDenseFileLoadsOnJvm). Re-bump "
                        + "only after an upstream release lifts the limit, with the dense-file "
                        + "proof, and update this test in the same commit.");
    }

    @Test
    void dependabotKeepsTheAsmIgnore() throws Exception {
        String cfg = Files.readString(repoRoot().resolve(".github/dependabot.yml"));
        assertTrue(cfg.contains("dependency-name: \"org.ow2.asm:*\""),
                "known-bugs §652: the dependabot group bump `137ea696d` silently undid the "
                        + "ASM pin once; the maven update block must keep the org.ow2.asm:* "
                        + "ignore so the weekly group bump cannot re-raise it.");
    }

    private static Path repoRoot() {
        String override = System.getenv("KOF_TEST_REPO_ROOT");
        if (override != null && !override.isBlank()) return Path.of(override);
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path pom = dir.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                try {
                    if (Files.readString(pom).contains("<asm.version>")) return dir;
                } catch (Exception ignored) {
                    // keep walking
                }
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("root pom.xml with <asm.version> not found from "
                + System.getProperty("user.dir"));
    }
}
