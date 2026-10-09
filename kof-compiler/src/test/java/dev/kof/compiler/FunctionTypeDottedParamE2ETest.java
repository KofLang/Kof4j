package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §632 — a FUNCTION-typed parameter whose component type is PONTUADO
 * ({@code (kof.web.App, String) -> Void}, {@code (java.lang.Object, Int) ->
 * Void}) produced an invalid JVM method descriptor. Found while building the
 * §5 server-lifecycle helper {@code withServer(app, port, body)}, which needs
 * a {@code kof.web.App} parameter.
 *
 * <p>Root (read): {@code CompilerTypes.toType} had a dotted-name split for a
 * plain class name ({@code a.b.C} → package {@code a.b}). A function-type
 * string hit the same branch and was cut at the FIRST {@code '.'} — which sits
 * INSIDE a parameter — so {@code (kof.web.App, String) -> Void} became
 * {@code ClassType("(kof.web", "App, String) -> Void")} and the emitted
 * descriptor was {@code L(kof/web/App, String) -> Void;}. The class does not
 * exist, so loading {@code Main} died
 * {@code NoClassDefFoundError: (kof/web/App, String) -> Void}. {@code kof
 * check} passed (no descriptor emitted); only {@code run}/{@code build}
 * surfaced it.
 *
 * <p>Fix: {@code CompilerTypes.toType} recognises a function-type string
 * ({@code startsWith("(")} + {@code Type.fnTypeArrow >= 0}) BEFORE the
 * dotted-name split and parses it with {@code Type.of} (paren-balanced); the
 * existing {@code qualifyDeep} recursion then qualifies each parameter.
 *
 * <p>RED-first: pre-fix the dotted cases fail with the exact
 * {@code NoClassDefFoundError} at {@code Main} load; the un-dotted control
 * passes both before and after.
 */
class FunctionTypeDottedParamE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(int exitCode, String output) {}

    private Run runJvm(String source, Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out");
        CompilationResult r = driver.compile(src, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        return new Run(p.waitFor(), out);
    }

    @Test
    void dottedJdkComponentParamLoadsAndDispatches(@TempDir Path tempDir) throws Exception {
        Run r = runJvm("""
                Void apply((java.lang.Object, Int) -> Void body, java.lang.Object o, Int n) {
                    body(o, n)
                }
                main() {
                    apply((o: java.lang.Object, n: Int) -> {
                        println("got " + o + "/" + n)
                    }, "hi", 7)
                }
                """, tempDir);
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals("got hi/7", r.output());
    }

    @Test
    void dottedPhantomHandleComponentParamLoadsAndDispatches(@TempDir Path tempDir) throws Exception {
        Run r = runJvm("""
                Void withApp(kof.web.App app, (kof.web.App, String) -> Void body) {
                    body(app, "u")
                }
                main() {
                    var app = web.app()
                    app.get("/ping") {
                        return "pong"
                    }
                    withApp(app, (a: kof.web.App, u: String) -> {
                        println("url=" + u)
                    })
                    app.close()
                }
                """, tempDir);
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals("url=u", r.output());
    }

    @Test
    void undottedFunctionParamStillWorks(@TempDir Path tempDir) throws Exception {
        Run r = runJvm("""
                Int twice((Int, Int) -> Int f) {
                    return f(3, 4)
                }
                main() {
                    println(twice((a: Int, b: Int) -> {
                        return a * b
                    }))
                }
                """, tempDir);
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals("12", r.output());
    }
}
