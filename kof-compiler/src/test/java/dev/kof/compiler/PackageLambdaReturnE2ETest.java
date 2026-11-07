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
 * §780 — a lambda whose return type is a class or record declared in a
 * package ({@code (n: Int) -> client.send(n)} returning
 * {@code kernel.t.TestResponse}) declared its {@code invoke} with the bare
 * simple name ({@code ()LTestResponse;}) while the functional interface used
 * the qualified one, so the call died with
 * {@code NoClassDefFoundError: TestResponse}. The return type now resolves
 * through the analyzer-aware {@code toType}, like the lambda parameters.
 */
class PackageLambdaReturnE2ETest {

    @TempDir Path tmp;

    @Test
    void aLambdaReturningAPackageTypeKeepsTheQualifiedName() throws Exception {
        Path src = tmp.resolve("src");
        Path lib = src.resolve("kernel/t");
        Path app = src.resolve("app");
        Files.createDirectories(lib);
        Files.createDirectories(app);
        Files.writeString(lib.resolve("T.kf"), """
            package kernel.t

            class TestResponse {
                Int status = 200
            }

            class Client {
                TestResponse send(Int n) { return TestResponse() }
            }

            record IdemResult(Int code, String body)

            Int samePackage() {
                var mk = () -> TestResponse()
                return mk().status
            }
            """);
        Files.writeString(app.resolve("Main.kf"), """
            package app

            import kernel.t.*

            main() {
                val c = Client()
                var send = (n: Int) -> c.send(n)
                println(send(1).status)
                var f = () -> IdemResult(2, "y")
                println(f().body)
                println(samePackage())
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(lib.resolve("T.kf"), app.resolve("Main.kf")), out, Target.JVM, src);
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
        assertEquals("200\ny\n200\n", buf.toString());
    }
}
