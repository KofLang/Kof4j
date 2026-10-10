package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P4 (D-PAGINATION-P4-LOWERING) — {@code orm.window<T>(db, limit,
 * offset[, true])}: a janela SQL sobre o host virtual {@code kof.pagination}.
 *
 * <p>O lowerer de ORM dessuga a face para o helper Kof {@code window(...)}
 * injetado sobre {@code orm.page}/{@code orm.count} (library-first: nenhum
 * runtime por alvo constroi o record {@code Window<T>}, que e por-programa).
 * A semantica e a MESMA da face em memoria (P2): {@code hasPrevious = offset >
 * 0}; {@code hasNext} otimista ({@code page.size == limit}) sem total e exato
 * com ele; {@code total} so existe quando pedido (COUNT(*) opt-in, lazy — a
 * forma de 3 args nunca conta).
 */
class PaginationOrmWindowE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ENTITY_SRC = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            """;

    /** Corpo comum: 5 linhas; exercita 3-arg (sem total), 4-arg (total exato),
     *  pagina parcial final, offset alem do fim e limit 0. */
    private static final String BODY = """
            import kof.pagination

            %s

            main() {
                var db = db.connect("%%s")
                orm.create<User>(db)
                var batch = listOf(User(0, "A", "a@kof.dev", 20),
                                   User(0, "B", "b@kof.dev", 21),
                                   User(0, "C", "c@kof.dev", 22),
                                   User(0, "D", "d@kof.dev", 23),
                                   User(0, "E", "e@kof.dev", 24))
                println(orm.saveAll<User>(db, batch))
                var w1 = orm.window<User>(db, 2, 0)
                println(w1.items().size())
                println(w1.hasPrevious())
                println(w1.hasNext())
                if (w1.total() != null) println("tot") else println("none")
                println(w1.items().get(0).name())
                var w2 = orm.window<User>(db, 2, 2, true)
                println(w2.items().size())
                println(w2.hasPrevious())
                println(w2.hasNext())
                if (w2.total() != null) println(w2.total()) else println("none")
                var w3 = orm.window<User>(db, 2, 4, true)
                println(w3.items().size())
                println(w3.hasNext())
                if (w3.total() != null) println(w3.total()) else println("none")
                var w4 = orm.window<User>(db, 2, 9, true)
                println(w4.items().size())
                println(w4.hasPrevious())
                println(w4.hasNext())
                var w5 = orm.window<User>(db, 0, 0, true)
                println(w5.items().size())
                db.close(db)
            }
            """.formatted(ENTITY_SRC);

    private static final String GOLDEN =
            "true\n2\nfalse\ntrue\nnone\nA\n2\ntrue\ntrue\n5\n1\nfalse\n5\n0\ntrue\nfalse\n0";

    // ── JVM (H2) ──────────────────────────────────────────────────────────

    private static String findDriverJar(String marker, String label) {
        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry.contains(marker) && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException(label + " jar not found on test classpath");
    }

    private String runJvm(Path source, Path outDir, String extraJar, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile: " + result.diagnostics().getDiagnostics());
        String cp = outDir + java.io.File.pathSeparator + extraJar;
        try {
            ProcessBuilder pb = new ProcessBuilder(TestJdk.javaBin(), "-Dfile.encoding=UTF-8", "-cp", cp, "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit, output: '" + output + "'");
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void ormWindowOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, BODY.formatted("jdbc:h2:mem:p4w;DB_CLOSE_DELAY=-1"));
        runJvm(source, tempDir.resolve("out"), findDriverJar("h2", "H2"), GOLDEN);
    }

    // ── Native x86-64 (sqlite) ────────────────────────────────────────────

    private static String kofPath(Path p) {
        return p.toString().replace('\\', '/');
    }

    @Test
    void ormWindowOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, BODY.formatted("sqlite:" + kofPath(tempDir.resolve("p4w.db"))));
        CompilationResult result = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("native-out/Default/Main");
        assertTrue(Files.exists(bin), "binary exists");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native exit, output: '" + out + "'");
        assertEquals(GOLDEN, out, "native x86_64 must match the JVM oracle (rule 5)");
    }

    // ── JS (H2 via KofJsOrmBridge) ────────────────────────────────────────

    @Test
    void ormWindowOnJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, BODY.formatted("jdbc:h2:mem:p4wjs;DB_CLOSE_DELAY=-1"));
        Path outDir = tempDir.resolve("js-out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compile: " + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals(GOLDEN, txt, "js must match the JVM oracle (parity rule 5)");
    }

    // ── Edges ─────────────────────────────────────────────────────────────

    @Test
    void negativeLimitIsANamedError(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Neg.kf");
        Files.writeString(source, """
                import kof.pagination

                %s

                main() {
                    var db = db.connect("jdbc:h2:mem:p4neg;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(1, "A", "a@kof.dev", 20))
                    var w = orm.window<User>(db, -1, 0)
                    println(w.items().size())
                    db.close(db)
                }
                """.formatted(ENTITY_SRC));
        CompilationResult result = driver.compile(source, tempDir.resolve("neg-out"), Target.JVM);
        assertTrue(result.success(), "negative orm.window compiles (runtime error): "
                + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                tempDir.resolve("neg-out") + java.io.File.pathSeparator + findDriverJar("h2", "H2"),
                "Default.Main").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertNotEquals(0, p.waitFor(), "negative orm.window must fail, got:\n" + out);
        assertTrue(out.contains("PAGINATION: limit/offset must be >= 0"), "named error, got:\n" + out);
    }

    @Test
    void ormWindowRequiresThePaginationImport(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("NoImport.kf");
        Files.writeString(source, """
                %s

                main() {
                    var db = db.connect("jdbc:h2:mem:p4noi;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var w = orm.window<User>(db, 1, 0)
                    println(w.items().size())
                }
                """.formatted(ENTITY_SRC));
        CompilationResult result = driver.compile(source, tempDir.resolve("noi-out"), Target.JVM);
        assertFalse(result.success(), "orm.window must not resolve without import kof.pagination");
    }
}
