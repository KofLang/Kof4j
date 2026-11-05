package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lowering typer asked for a call receiver's type several times per call,
 * so the work grew exponentially with the length of a fluent chain: a
 * 36-call {@code DbParams().bindStr(..)...} argument kept the compiler busy
 * for over an hour. A 60-call chain now compiles in well under the timeout.
 */
class CallChainInferenceE2ETest {

    @TempDir Path tmp;

    @Test
    void aLongFluentChainCompilesInLinearTime() throws Exception {
        Path src = tmp.resolve("src");
        Path lib = src.resolve("kernel/db");
        Path app = src.resolve("app");
        Files.createDirectories(lib);
        Files.createDirectories(app);
        Files.writeString(lib.resolve("Db.kf"), """
            package kernel.db
            import java.util.ArrayList
            class Params {
                List<Object> items = new ArrayList<Object>()
                Params bindStr(v: String) {
                    items.add(v as Object)
                    return this
                }
                Params bindStrOrNull(v: String?) {
                    if (v == null) { return this }
                    items.add(v as Object)
                    return this
                }
                Params bindInt(v: Int) {
                    items.add(v as Object)
                    return this
                }
            }
            record Rec(String id, String? note, Int plays)
            class Store {
                Int execute(sql: String, p: Params) { return p.items.size() }
            }
            """);
        StringBuilder chain = new StringBuilder("Params()");
        String[] links = {".bindStr(g.id())", ".bindStrOrNull(g.note())", ".bindInt(g.plays())"};
        for (int i = 0; i < 60; i++) chain.append(links[i % 3]);
        Files.writeString(app.resolve("Main.kf"), """
            package app
            import kernel.db.*
            class Svc {
                Store store = Store()
                Int save(g: Rec) {
                    return store.execute("insert", %s)
                }
            }
            main() { println(Svc().save(Rec("x", "n", 2))) }
            """.formatted(chain));
        Path out = tmp.resolve("out");
        CompilationResult r = assertTimeoutPreemptively(Duration.ofSeconds(60), () ->
                new CompilerDriver().compileSources(
                        List.of(lib.resolve("Db.kf"), app.resolve("Main.kf")), out, Target.JVM, src));
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
        assertEquals("60\n", buf.toString());
    }
}
