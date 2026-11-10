package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §809 — a project class named like an explicitly imported Java class
 * ({@code kernel.security.Security} next to {@code import java.security.Security})
 * took over {@code Security.getProvider("SUN")}: the call lowered as a static
 * call into the Kof class and died with {@code VerifyError}. A single-type
 * import now shadows every other type with the same simple name (JLS 6.4.1),
 * so the receiver is the imported class; the project class still resolves
 * where nothing shadows it.
 */
class ExplicitImportWinsE2ETest {

    @TempDir Path tmp;

    private String run(String main) throws Exception {
        Path src = tmp.resolve("src");
        Path lib = src.resolve("kernel/security");
        Path app = src.resolve("app");
        Files.createDirectories(lib);
        Files.createDirectories(app);
        Files.writeString(lib.resolve("Security.kf"), """
            package kernel.security

            class Security {
                static String name() { return "kernel" }
            }

            String kernelName() { return Security.name() }
            """);
        Files.writeString(app.resolve("Main.kf"), main);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(lib.resolve("Security.kf"), app.resolve("Main.kf")), out, Target.JVM, src);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString();
    }

    @Test
    void anExplicitJavaImportWinsOverAProjectClassWithTheSameName() throws Exception {
        assertEquals("true\n", run("""
            package app

            import java.security.Security

            main() {
                println(Security.getProvider("SUN") != null)
            }
            """));
    }

    @Test
    void theExplicitImportAlsoBeatsAWildcardOfTheProjectPackage() throws Exception {
        assertEquals("true\nkernel\n", run("""
            package app

            import kernel.security.*
            import java.security.Security

            main() {
                println(Security.getProvider("SUN") != null)
                println(kernelName())
            }
            """));
    }
}
