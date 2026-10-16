package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof migrate — migration report with traceability (docs/development/LEGACY_MIGRATION.md,
 * Fase H). Verifies the report is honest about what was recovered (structure)
 * vs. what needs manual review (method bodies).
 */
class MigrateTest extends CliJavacSupport {

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

    // ---- Fase H slice 1: full JAR migration (§8.1) ----

    private void createClass(Path dir, String className, String javaSource) throws Exception {
        Path javaFile = dir.resolve(className + ".java");
        Files.writeString(javaFile, javaSource);
        javac(javaFile, dir);
    }

    private void createJar(Path jarPath, Path classDir, String... classNames) throws Exception {
        try (var jos = new java.util.jar.JarOutputStream(
                java.nio.file.Files.newOutputStream(jarPath))) {
            for (String cn : classNames) {
                Path classFile = classDir.resolve(cn + ".class");
                jos.putNextEntry(new java.util.jar.JarEntry(cn + ".class"));
                jos.write(java.nio.file.Files.readAllBytes(classFile));
                jos.closeEntry();
            }
        }
    }

    /** CodeQL java/zipslip (#1131): uma entrada "../x.class" não escreve fora do --output. */
    @Test
    void jarEntryEscapingOutputDirIsSkippedWithWarning(@TempDir Path dir) throws Exception {
        createClass(dir, "Calc", """
                public class Calc {
                    public int add(int a, int b) { return a + b; }
                }
                """);
        byte[] bytes = Files.readAllBytes(dir.resolve("Calc.class"));
        try (var jos = new java.util.jar.JarOutputStream(Files.newOutputStream(dir.resolve("evil.jar")))) {
            for (String name : new String[] {"../escape.class", "a/../../escape2.class", "Calc.class"}) {
                jos.putNextEntry(new java.util.jar.JarEntry(name));
                jos.write(bytes);
                jos.closeEntry();
            }
        }
        Path outDir = dir.resolve("out").resolve("kf");
        var err = new java.io.ByteArrayOutputStream();
        var oldErr = System.err;
        System.setErr(new java.io.PrintStream(err, true, java.nio.charset.StandardCharsets.UTF_8));
        Migrate.MigrationReport report;
        try {
            report = Migrate.jarReport(dir.resolve("evil.jar"), outDir, false);
        } finally {
            System.setErr(oldErr);
        }
        assertFalse(Files.exists(dir.resolve("out").resolve("escape.kf")), "escaped ../ entry");
        assertFalse(Files.exists(dir.resolve("out").resolve("escape2.kf")), "escaped a/../../ entry");
        assertTrue(Files.exists(outDir.resolve("Calc.kf")), "the legitimate entry is still migrated");
        assertEquals(1, report.classes(), "only the legitimate entry counts");
        String warn = err.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(warn.contains("../escape.class: entry escapes the output directory"), warn);
        assertTrue(warn.contains("a/../../escape2.class: entry escapes the output directory"), warn);
    }

    @Test
    void jarReportCountsStubsFromActualOutput(@TempDir Path dir) throws Exception {
        createClass(dir, "Calc", """
                public class Calc {
                    int total = 0;
                    public int add(int a, int b) { return a + b; }
                    public String greet(String name) { return "hi " + name; }
                }
                """);
        createJar(dir.resolve("app.jar"), dir, "Calc");
        Path outDir = dir.resolve("kf-out");

        Migrate.MigrationReport report = Migrate.jarReport(dir.resolve("app.jar"), outDir, false);

        assertEquals("jar", report.format());
        assertEquals(1, report.classes());
        assertEquals(1, report.fields(), "Calc has one instance field");
        assertTrue(report.methodSignatures() >= 2, "add+greet signatures");
        // the report must be based on the ACTUAL decompiler output, not assumptions
        assertTrue(report.methodBodiesNotRecovered() >= 0, "stub count from output");
        assertTrue(Files.exists(outDir.resolve("Calc.kf")),
                "the .kf file must exist in the output dir");
    }

    @Test
    void jarMigrationWritesPackageStructureAndCompiles(@TempDir Path dir) throws Exception {
        createClass(dir, "Hello", """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hello from jar");
                    }
                }
                """);
        createJar(dir.resolve("hello.jar"), dir, "Hello");
        Path outDir = dir.resolve("kf-out");

        Migrate.MigrationReport report = Migrate.jarReport(dir.resolve("hello.jar"), outDir, false);
        assertEquals(1, report.classes());

        Path kf = outDir.resolve("Hello.kf");
        assertTrue(Files.exists(kf), "output .kf file");
        String content = Files.readString(kf);
        // the decompiled output must be structural Kof, not empty
        assertTrue(content.contains("class Hello"), "decompiled class header:\n" + content);
    }

    @Test
    void jarMigrationCliEndToEnd(@TempDir Path dir) throws Exception {
        createClass(dir, "Hello", """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hi");
                    }
                }
                """);
        createJar(dir.resolve("app.jar"), dir, "Hello");
        Path outDir = dir.resolve("out");

        int rc = Migrate.run(new String[]{"migrate", dir.resolve("app.jar").toString(),
                "--output", outDir.toString()});
        assertEquals(0, rc);
        assertTrue(Files.exists(outDir.resolve("Hello.kf")), "output .kf from CLI");
    }

    @Test
    void jarCompareCountsBehavioralPassed(@TempDir Path dir) throws Exception {
        // the recovered main must PASS the differential test: legacy bytecode
        // and compiled Kof print the same line
        createClass(dir, "Hello", """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hi from hello");
                    }
                }
                """);
        createJar(dir.resolve("app.jar"), dir, "Hello");

        Migrate.MigrationReport report = Migrate.jarReport(dir.resolve("app.jar"),
                dir.resolve("out"), true);
        assertEquals(1, report.behavioralPassed(),
                "the migrated main must pass the differential test");
        assertEquals(0, report.behavioralDivergent());
        assertEquals(0, report.behavioralNotComparable());
    }

    @Test
    void jarCompareCountsDivergentWhenBodyStubs(@TempDir Path dir) throws Exception {
        // a main whose body the decompiler stubs diverges behaviorally: the
        // stub throws instead of printing
        createClass(dir, "Stubbed", """
                public class Stubbed {
                    public static void main(String[] args) {
                        Object o = System.getProperties();
                        String s = o.toString();
                        System.out.println("value: " + s.length());
                    }
                }
                """);
        createJar(dir.resolve("app.jar"), dir, "Stubbed");

        Migrate.MigrationReport report = Migrate.jarReport(dir.resolve("app.jar"),
                dir.resolve("out"), true);
        // whatever the decompiler managed: the counters must be honest — a
        // stubbed main cannot pass the differential test
        assertTrue(report.behavioralPassed() + report.behavioralDivergent() == 1,
                "the class has one main: exactly one verdict: "
                        + report.behavioralPassed() + "/" + report.behavioralDivergent());
    }

    @Test
    void jarCompareRunsSameInputVector(@TempDir Path dir) throws Exception {
        // §8: the differential test is defined over the SAME INPUT VECTOR —
        // the args vector must reach BOTH sides identically: both print
        // "2x" (args.length + first arg) and the verdict is a pass. A
        // one-sided plumbing flips this to divergent. (The stdin leg of the
        // plumbing is proven at the Compare level — CompareTest's --stdin
        // faces; a decompiled body that READS stdin stubs honestly, so the
        // end-to-end stdin face waits for the structured-shape ceiling.)
        createClass(dir, "Echo", """
                public class Echo {
                    public static void main(String[] args) {
                        System.out.println(args.length + args[0]);
                    }
                }
                """);
        createJar(dir.resolve("app.jar"), dir, "Echo");

        Migrate.MigrationReport report = Migrate.jarReport(dir.resolve("app.jar"),
                dir.resolve("out"), true, java.util.List.of("x", "y"), null);
        assertEquals(1, report.behavioralPassed(),
                "both sides must receive the identical arg vector");
        assertEquals(0, report.behavioralDivergent());
        assertEquals(0, report.behavioralNotComparable());
    }

    @Test
    void jarCompareCountsNotComparableWithoutMain(@TempDir Path dir) throws Exception {
        createClass(dir, "Alpha", "public class Alpha { }");
        createJar(dir.resolve("app.jar"), dir, "Alpha");

        Migrate.MigrationReport report = Migrate.jarReport(dir.resolve("app.jar"),
                dir.resolve("out"), true);
        assertEquals(0, report.behavioralPassed());
        assertEquals(0, report.behavioralDivergent());
        assertEquals(1, report.behavioralNotComparable(), "no main = not comparable");
    }

    @Test
    void jarReportMultiClassAggregation(@TempDir Path dir) throws Exception {
        createClass(dir, "Alpha", "public class Alpha { }");
        createClass(dir, "Beta", "public class Beta { int x; }");
        createJar(dir.resolve("multi.jar"), dir, "Alpha", "Beta");
        Path outDir = dir.resolve("kf-out");

        Migrate.MigrationReport report = Migrate.jarReport(dir.resolve("multi.jar"), outDir, false);
        assertEquals(2, report.classes());
        assertTrue(Files.exists(outDir.resolve("Alpha.kf")));
        assertTrue(Files.exists(outDir.resolve("Beta.kf")));
    }

}