package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof migrate — migration report with traceability (docs/future/LEGACY_MIGRATION.md,
 * Fase H). Verifies the report is honest about what was recovered (structure)
 * vs. what needs manual review (method bodies).
 */
class MigrateTest {

    @Test
    void classReportFlagsUnrecoveredBodies(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Calc.java");
        Files.writeString(javaFile, """
                public class Calc {
                    int total = 0;
                    public int add(int a, int b) { return a + b; }
                    public String greet(String name) { return "hi " + name; }
                }
                """);
        javac(javaFile, dir);

        Path classFile = dir.resolve("Calc.class");
        Migrate.MigrationReport report = Migrate.reportClass(classFile, "dummy");

        assertEquals("class", report.format());
        assertEquals(1, report.fields());
        assertEquals(2, report.methodBodiesNotRecovered(), "add+greet bodies must be flagged");
        assertEquals(2, report.manualReview().size());
        assertTrue(report.methodBodiesNotRecovered() > 0);
        assertTrue(report.recoveredPct() < 100.0, "structural-only recovery < 100%:\n" + report);
    }

    @Test
    void javaReportRecoversBodies(@TempDir Path dir) throws Exception {
        Migrate.MigrationReport report = Migrate.reportJava(
                Path.of("Main.java"),
                "main() {\n    println(\"hi\")\n}\n");
        assertEquals("java", report.format());
        assertEquals(100.0, report.recoveredPct());
        assertEquals(0, report.manualReview().size());
    }

    @Test
    void migrateCliEndToEnd(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Hello.java");
        Files.writeString(javaFile, """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hi");
                    }
                }
                """);
        javac(javaFile, dir);

        // migrate the .java directly (jvm class also fine; use java for full recovery)
        int rc = Migrate.run(new String[]{"migrate", javaFile.toString(), "--output",
                dir.resolve("Hello.kf").toString()});
        assertEquals(0, rc);
        assertTrue(Files.exists(dir.resolve("Hello.kf")), "output file created");
    }

    private void javac(Path javaFile, Path dir) throws Exception {
        Path javac = Path.of(System.getProperty("java.home"), "bin", "javac");
        ProcessBuilder pb = new ProcessBuilder(javac.toString(), "-d", dir.toString(), javaFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int rc = p.waitFor();
        if (rc != 0) throw new RuntimeException("javac: " + new String(p.getInputStream().readAllBytes()));
    }
}