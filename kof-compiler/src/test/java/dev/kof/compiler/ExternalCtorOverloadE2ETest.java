package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JDK constructor was emitted with the exact static types of its arguments
 * ({@code InputStreamReader.<init>(ByteArrayInputStream, String)},
 * NoSuchMethodError) or, among same-arity overloads, by declaration order. It
 * is now selected like an external method (§770, JLS 15.12.2 phases, the most
 * specific wins) and emitted with the selected descriptor, so a {@code null}
 * literal for a {@code String} parameter binds the String overload (enois
 * G378: {@code URI.<init>(String, Object, ...)} was NoSuchMethodError).
 * <p>
 * (Integration of #1079: the first face — the argument TYPING of an external
 * constructor — lives in {@code ExternalCtorArgTypingE2ETest}, from #1061; the
 * selection face is this one.)
 */
class ExternalCtorOverloadE2ETest {

    @TempDir Path tmp;

    @Test
    void jdkConstructorsAreSelectedByArgumentType() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
            import java.io.BufferedReader
            import java.io.InputStreamReader
            import java.io.ByteArrayInputStream
            import java.io.File
            import java.net.URI
            main() {
                val in0 = new ByteArrayInputStream("hi\\nthere".getBytes())
                val r = new BufferedReader(new InputStreamReader(in0, "UTF-8"))
                println(r.readLine())
                val r2 = new BufferedReader(new InputStreamReader(new ByteArrayInputStream("x".getBytes())), 64)
                println(r2.readLine())
                val dir = new File("base")
                println(new File(dir, "leaf").getPath() == "base" + File.separator + "leaf")
                println(new URI("https", null, "a.com", -1, "/x", null, null).toString())
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
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
        assertEquals("hi\nx\ntrue\nhttps://a.com/x\n", buf.toString());
    }
}
