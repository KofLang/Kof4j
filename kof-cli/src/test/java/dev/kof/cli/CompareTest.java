package dev.kof.cli;

import org.junit.jupiter.api.Test;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof compare — differential testing (docs/future/DIFFERENTIAL_TESTING.md).
 * Runs legacy (.class) and Kof (.kf) with identical inputs and compares
 * stdout/exit code/stderr.
 */
class CompareTest {

    @Test
    void helpWorks() {
        assertEquals(0, Compare.run(new String[]{"compare", "--help"}));
    }

    @Test
    void pureComparisonLogic() {
        Compare.RunResult a = new Compare.RunResult(0, "hello\n", "");
        Compare.RunResult b = new Compare.RunResult(0, "hello\n", "");
        Compare.Verdict v = Compare.compare(a, b);
        assertTrue(v.equivalent());

        Compare.Verdict diverged = Compare.compare(a, new Compare.RunResult(0, "world\n", ""));
        assertFalse(diverged.equivalent());
        assertEquals(Compare.Channel.DIVERGENT, diverged.stdout());
        assertEquals(Compare.Channel.EQUIVALENT, diverged.exit());
    }

    @Test
    void helloEquivalent(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Hello.java");
        Files.writeString(javaFile, """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);
        javac(javaFile, dir);

        Path kofFile = dir.resolve("hello.kf");
        Files.writeString(kofFile, "main() {\n    println(\"hello\")\n}\n");

        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("Hello.class"), List.of(), null);
        Compare.RunResult kof = Compare.runKof(kofFile, List.of(), null);
        assertEquals("hello", legacy.stdout());
        assertEquals("hello", kof.stdout());
        assertTrue(Compare.compare(legacy, kof).equivalent());
    }

    @Test
    void addWithArgsEquivalent(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Add.java");
        Files.writeString(javaFile, """
                public class Add {
                    public static void main(String[] args) {
                        int a = Integer.parseInt(args[0]);
                        int b = Integer.parseInt(args[1]);
                        System.out.println(a + b);
                    }
                }
                """);
        javac(javaFile, dir);

        Path kofFile = dir.resolve("add.kf");
        Files.writeString(kofFile, """
                main() {
                    var a = 2
                    var b = 3
                    println(a + b)
                }
                """);

        // Compare with no args: legacy uses fixed args [2,3], kof uses literals.
        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("Add.class"), List.of("2", "3"), null);
        Compare.RunResult kof = Compare.runKof(kofFile, List.of(), null);
        assertEquals("5", legacy.stdout());
        assertEquals("5", kof.stdout());
        assertTrue(Compare.compare(legacy, kof).equivalent());
    }

    @Test
    void divergenceDetected(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Diff.java");
        Files.writeString(javaFile, """
                public class Diff {
                    public static void main(String[] args) {
                        System.out.println("original");
                    }
                }
                """);
        javac(javaFile, dir);

        Path kofFile = dir.resolve("diff.kf");
        Files.writeString(kofFile, "main() {\n    println(\"ko f\")\n}\n");

        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("Diff.class"), List.of(), null);
        Compare.RunResult kof = Compare.runKof(kofFile, List.of(), null);
        Verify.difference(Compare.compare(legacy, kof));
    }

    private void javac(Path javaFile, Path dir) throws Exception {
        Path javac = Path.of(System.getProperty("java.home"), "bin", "javac");
        ProcessBuilder pb = new ProcessBuilder(javac.toString(), "-d", dir.toString(), javaFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int rc = p.waitFor();
        if (rc != 0) throw new RuntimeException("javac: " + new String(p.getInputStream().readAllBytes()));
    }

    /** Small helper to assert, without static imports, that stdout diverged. */
    private static final class Verify {
        static void difference(Compare.Verdict v) {
            assertFalse(v.equivalent(), "expected divergence");
            assertEquals(Compare.Channel.DIVERGENT, v.stdout());
        }
    }
}