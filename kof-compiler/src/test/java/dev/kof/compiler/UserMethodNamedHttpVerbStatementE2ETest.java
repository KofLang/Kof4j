package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #755 (04/10, external report): a user method named after an HTTP verb
 * (`get`/`post`/`put`/`patch`/`delete`) called as a STATEMENT inside a `try`
 * body skipped its value POP — `CompilerComparisons.hasReturnValueInner`
 * matched the receiver-agnostic `KofWeb.instanceMethod` table (VOID route)
 * before the resolved user method, while the JVM backend emitted the real
 * `String`-returning `invokevirtual`. The result was a class that compiled
 * clean and died at load with `VerifyError: Inconsistent stackmap frames`.
 * The web table is receiver-gated on `kof.web.App` in `MethodCallTyper`, so
 * this pin drives the same shape end-to-end on the JVM.
 */
class UserMethodNamedHttpVerbStatementE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path dir;

    @Test
    void userMethodNamedAfterHttpVerbAsTryStatementLoads() throws Exception {
        for (String verb : new String[]{"get", "post", "put", "patch", "delete"}) {
            Path src = dir.resolve(verb + ".kf");
            Files.writeString(src, """
                    class Box {
                        String %s(String a, String b) {
                            return a + b
                        }
                    }

                    main() {
                        var b = Box()
                        var err = ""
                        try { b.%s("x", "y") } catch (String e) { err = e }
                        println("done " + err)
                    }
                    """.formatted(verb, verb));

            Path out = dir.resolve("out-" + verb);
            CompilationResult r = driver.compileSources(List.of(src), out, Target.JVM, dir);
            assertTrue(r.success(), verb + " compile: " + r.diagnostics().getDiagnostics());

            Process p = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", out.toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int rc = p.waitFor();
            assertEquals(0, rc, verb + " must load and run (no VerifyError): " + output);
            assertTrue(output.contains("done"), verb + " output: " + output);
            assertFalse(output.contains("VerifyError"), verb + " must not VerifyError: " + output);
        }
    }
}
