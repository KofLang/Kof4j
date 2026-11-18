package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §728 — the JS internal runtime throws bind a String (the #843
 * fix) but carry the JS runtime's OWN message text; issue #843's
 * message-EQUALITY line stays false on JS. The JVM/Script oracle (measured
 * on the tip, this test is the RED-first):
 *
 * <pre>
 *   listOf(1, 2).get(99)  → Index 99 out of bounds for length 2
 *   "nope".toInt()        → For input string: "nope"
 *   "nope".toDouble()     → For input string: "nope"
 * </pre>
 *
 * <p>The JS texts were its own: {@code Index out of bounds: 99 (size 2)},
 * {@code Cannot parse "nope" as Int} / {@code as Double}. The fix aligns the
 * JS runtime texts to the JDK wording on the three measured faces (the
 * bounds message of {@code java.util.ArrayList.get} / the
 * {@code Integer.parseInt}/{@code Double.parseDouble} wording). The bound of
 * this slice: only the message TEXT — the TYPE (a catchable String) was
 * already fixed by #843.
 */
class JsThrowMessageParityE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                try { println(listOf(1, 2).get(99)) }
                catch (String e) { println("m1=" + e) }
                try { println("nope".toInt()) }
                catch (String e) { println("m2=" + e) }
                try { println("nope".toDouble()) }
                catch (String e) { println("m3=" + e) }
            }
            """;

    /** The JDK wording — the JVM/Script oracle, now on JS too. */
    private static final String GOLDEN = String.join("\n",
            "m1=Index 99 out of bounds for length 2",
            "m2=For input string: \"nope\"",
            "m3=For input string: \"nope\"");

    private Path write(Path tmp, String tag) throws Exception {
        Path p = tmp.resolve("Main-" + tag + ".kf");
        Files.writeString(p, SRC, StandardCharsets.UTF_8);
        return p;
    }

    private String runJvmOrScript(Target target, Path tmp, String tag) throws Exception {
        Path main = write(tmp, tag);
        Path dir = main.getParent();
        if (target == Target.SCRIPT) {
            KofInterpreter.Result result = driver.interpret(List.of(main), dir, new String[0]);
            assertEquals(0, result.exitCode(), () -> "script: " + result.stderr());
            return result.stdout().strip();
        }
        Path out = dir.resolve("out-" + tag);
        CompilationResult r = driver.compile(main, out, target);
        assertTrue(r.success(), () -> target + " compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String outTxt = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, p.waitFor(), outTxt);
        return outTxt;
    }

    @Test
    void jsThrowJvmOracle(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runJvmOrScript(Target.JVM, tmp, "jvm"),
                "the oracle: the JDK wording");
    }

    @Test
    void scriptOracle(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runJvmOrScript(Target.SCRIPT, tmp, "script"),
                "the interpreter mirrors the JDK wording");
    }

    @Test
    void jsCarriesTheSameMessages(@TempDir Path tmp) throws Exception {
        Path main = write(tmp, "js");
        Path out = main.getParent().resolve("out-js");
        CompilationResult r = driver.compile(main, out, Target.JS);
        assertTrue(r.success(), () -> "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip(),
                "§728: the JS runtime messages must carry the JDK wording");
    }
}
