package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #786 — `db` é o namespace KofDb E um nome que o idioma
 * {@code var db = db.connect(...)} vincula. Um local/parâmetro chamado `db` de
 * OUTRO tipo é um receiver comum; antes do fix a chamada era roteada ao lowerer
 * do namespace, {@code KofDb.staticCall} devolvia null e o emit não produzia
 * nada — a chamada sumia da IR (R6/Q7) e o frame quebrava.
 */
class DbNamespaceLocalShadowE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** O parâmetro `db` de tipo de usuário não pode engolir a chamada. */
    private static final String FOREIGN_LOCAL = """
            class Rng {
                Double next() {
                    return 0.5
                }
            }

            class Vazia {
            }

            class Engine {
                Int count(Int n, Rng db) {
                    var hits = 0
                    var i = 0
                    while (i < n) {
                        var reversed = db.next() < 0.35
                        if (reversed) {
                            hits = hits + 1
                        }
                        i = i + 1
                    }
                    return hits
                }
            }

            main() {
                var e = new Engine()
                println(e.count(3, new Rng()))
            }
            """;

    /** Controle: o idioma do namespace continua roteando `db.<fn>` ao KofDb. */
    private static final String HANDLE_IDIOM = """
            main() {
                var db = db.connect("jdbc:h2:mem:shadow786")
                db.execute(db, "create table t(id int)")
                db.execute(db, "insert into t values (?)", 1)
                var rows = db.query(db, "select id from t")
                println(rows.size)
                db.close(db)
            }
            """;

    private static final String UNRESOLVED_QUERY = """
            main() {
                var db = db.connect("jdbc:h2:mem:shadow883")
                var x = db.query("SELECT 1")
                println(x.length())
            }
            """;

    private static final String UNRESOLVED_STATEMENT = """
            main() {
                var db = db.connect("jdbc:h2:mem:shadow883")
                db.exec(db, "create table t(id int)")
                db.close(db)
            }
            """;

    private java.util.List<Diagnostic> compileFailing(Path dir, String name, String source) throws IOException {
        Path src = dir.resolve(name);
        Files.writeString(src, source);
        CompilationResult result = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Compilation should fail: " + result.diagnostics().getDiagnostics());
        return result.diagnostics().getDiagnostics();
    }

    private String runJvm(Path source, Path outDir, String extraCp, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        String cp = outDir + (extraCp != null ? java.io.File.pathSeparator + extraCp : "");
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", cp, "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private static String findH2Jar() {
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) return entry;
        }
        return null;
    }

    @Test
    void foreignLocalNamedDbLowersAsAnInstanceCall(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("Shadow786.kf");
        Files.writeString(src, FOREIGN_LOCAL);
        // 0.5 < 0.35 == false em todas as iterações -> hits = 0.
        runJvm(src, dir.resolve("out"), null, "0");
    }

    @Test
    void foreignLocalNamedDbIsIndependentOfClassDeclarationOrder(@TempDir Path dir) throws IOException {
        // Mesmo programa com `Engine` ANTES de `Vazia` (a 2ª ordem medida no #786).
        String swapped = FOREIGN_LOCAL.replace(
                "class Vazia {\n}\n\nclass Engine {", "class Engine {")
                .replace("    }\n}\n\nmain()", "    }\n}\n\nclass Vazia {\n}\n\nmain()");
        Path src = dir.resolve("Shadow786Order.kf");
        Files.writeString(src, swapped);
        runJvm(src, dir.resolve("out"), null, "0");
    }

    @Test
    void handleTypedLocalNamedDbKeepsTheNamespacePath(@TempDir Path dir) throws IOException {
        String h2 = findH2Jar();
        org.junit.jupiter.api.Assumptions.assumeTrue(h2 != null, "H2 jar absent");
        Path src = dir.resolve("HandleIdiom.kf");
        Files.writeString(src, HANDLE_IDIOM);
        runJvm(src, dir.resolve("out"), h2, "1");
    }

    @Test
    void unresolvedDbCallOnTheHandleIsDiagnosedInsteadOfCrashingTheBackend(@TempDir Path dir) throws IOException {
        var diags = compileFailing(dir, "Unresolved883.kf", UNRESOLVED_QUERY);
        assertTrue(diags.stream().anyMatch(d -> "SEM025".equals(d.code())
                        && d.message().contains("'query'") && d.message().contains("1 argument")),
                "SEM025 esperado para db.query com 1 argumento: " + diags);
        assertTrue(diags.stream().noneMatch(d -> "COMP002".equals(d.code())),
                "o backend não pode morrer com erro interno: " + diags);
    }

    @Test
    void unresolvedDbCallAsStatementIsDiagnosedInsteadOfDropped(@TempDir Path dir) throws IOException {
        var diags = compileFailing(dir, "Dropped883.kf", UNRESOLVED_STATEMENT);
        assertTrue(diags.stream().anyMatch(d -> "SEM025".equals(d.code()) && d.message().contains("'exec'")),
                "SEM025 esperado para db.exec (a chamada não pode sumir da IR): " + diags);
    }
}
