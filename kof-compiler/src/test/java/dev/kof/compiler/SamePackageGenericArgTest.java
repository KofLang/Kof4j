package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #697 — the type-argument of an explicit generic was not resolved to the
 * package-qualified type in two places:
 *
 * <ul>
 *   <li>{@code SemNewExprTyper} applied {@code Type::of} (text-only) to the
 *       type-args of a builtin collection ctor, so {@code new
 *       List<Rotulo>()} / {@code new List<dominio.Rotulo>()} produced
 *       {@code kof.List<ClassType("", "Rotulo")>} while the declared LHS was
 *       package-qualified → spurious SEM021;</li>
 *   <li>the {@code VarDeclStmt} declared type was only {@code qualifyDeep}-ed
 *       when it contained a {@code '.'}, so a same-package simple-name
 *       type-argument ({@code List<Rotulo>}) stayed package-less while
 *       {@code listOf<Rotulo>()} resolved it → spurious SEM021.</li>
 * </ul>
 *
 * The measured matrix (all six shapes must compile) and one JVM golden pin
 * the fix end-to-end (the generic must carry {@code dominio.Rotulo} into the
 * emitted descriptor, not a package-less name).
 */
class SamePackageGenericArgTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ROTULO = """
            package dominio

            record Rotulo(String campo, String texto)
            """;

    private static final String MAIN = """
            package dominio

            main() {
                var xs = new List<Rotulo>()
                xs.add(Rotulo("cnpj", "NUMERO"))
                xs.add(Rotulo("uf", "UF"))
                println(xs.size())
                println(xs.get(0).campo())
                println(xs.get(1).campo())
            }
            """;

    private List<Path> sources(Path tmp) throws IOException {
        Path pkg = tmp.resolve("dominio");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Rotulo.kf"), ROTULO);
        Files.writeString(pkg.resolve("Main.kf"), MAIN);
        return List.of(pkg.resolve("Main.kf"), pkg.resolve("Rotulo.kf"));
    }

    private void assertShapeCompiles(Path tmp, String decl) throws Exception {
        Path pkg = tmp.resolve("dominio");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Rotulo.kf"), ROTULO);
        Files.writeString(pkg.resolve("Main.kf"),
                "package dominio\n\nmain() {\n  " + decl + "\n  println(xs.size())\n}\n");
        CompilationResult r = driver.compileSources(
                List.of(pkg.resolve("Main.kf"), pkg.resolve("Rotulo.kf")),
                tmp.resolve("classes-" + System.nanoTime()), Target.JVM, tmp);
        assertTrue(r.success(), () -> decl + " must compile; got "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void measuredMatrixCompiles(@TempDir Path tmp) throws Exception {
        assertShapeCompiles(tmp, "var xs = new List<Rotulo>()");
        assertShapeCompiles(tmp, "List<Rotulo> xs = new List<Rotulo>()");
        assertShapeCompiles(tmp, "var xs = new List<dominio.Rotulo>()");
        assertShapeCompiles(tmp, "List<dominio.Rotulo> xs = new List<dominio.Rotulo>()");
        assertShapeCompiles(tmp, "var xs = listOf<Rotulo>()");
        assertShapeCompiles(tmp, "List<Rotulo> xs = listOf<Rotulo>()");
    }

    @Test
    void newListOfSamePackageTypeCompiles(@TempDir Path tmp) throws Exception {
        CompilationResult r = driver.compileSources(sources(tmp), tmp.resolve("classes"),
                Target.JVM, tmp);
        assertTrue(r.success(), "compilou esperado; diagnostics: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void newListOfSamePackageTypeRunsJvm(@TempDir Path tmp) throws Exception {
        CompilationResult r = driver.compileSources(sources(tmp), tmp.resolve("classes"),
                Target.JVM, tmp);
        assertTrue(r.success(), "JVM build: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", r.outputDir().toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "JVM run timeout");
        assertEquals(0, p.exitValue(), "JVM exit: " + out);
        assertEquals("2" + System.lineSeparator()
                + "cnpj" + System.lineSeparator()
                + "uf" + System.lineSeparator(), out, "JVM golden");
    }
}
