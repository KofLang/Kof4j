package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §6 the Kof-level browser API of the testing-platform plan: the
 * `kof.test.browser` virtual package over the browser SPI (the seed
 * `KofJsBrowserE2ETest` mechanism — real Chrome `--headless --dump-dom`
 * via `kof.shell.run`).
 *
 * <p>RED-first: pre-slice `import kof.test.browser` had no host — the
 * compiler refused the import (the virtual package did not exist). The
 * injector now resolves it and the helpers run against the REAL Chromium
 * (Playwright cache, installed via `npx playwright install chromium`).
 */
class KofTestBrowserPackageE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void kofTestBrowserImportsResolveAndRunOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
                import kof.test.browser

                main() {
                    var dom = browserContent("data:text/html,<h1>kofbrowser-content-ok</h1>")
                    println(dom)
                }
                """);
        Path out = tempDir.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        String output = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("kofbrowser-content-ok"),
                "o Chromium real renderizou o conteúdo (o dump-dom contém o h1):\n" + output);
    }
}
