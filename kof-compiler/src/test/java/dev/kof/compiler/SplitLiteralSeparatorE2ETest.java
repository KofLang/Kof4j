package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.kof.runtime.KofJsRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §742 — DECISION: the Kof split separator is LITERAL (never a
 * regex). Recorded as `D-SPLIT-LITERAL` (pending the maintainer's
 * ratification; the Kof-first simplicity reading — 3 of the 4 backends were
 * already literal, the divergence was JVM/Script-only, and the platform
 * rules forbid a regex engine on the native targets).
 *
 * <p>The pre-fix matrix (measured 15/10, the ledger's record):
 * <pre>
 *   "a.b".split(".")  JVM/Script = 0 pieces []   NATIVE/JS = 2 [a][b]
 *   "a|b".split("|")  JVM/Script = 3 [a][|][b]   NATIVE/JS = 2 [a][b]
 *   "a[b".split("[")  JVM = UNCAUGHT PatternSyntaxException (the raw Java
 *                     trace kills the program) / NATIVE/JS = 2 [a][b]
 * </pre>
 * The post-fix contract (all four targets byte-identical): LITERAL split —
 * `a.b` → 2 pieces, `a|b` → 2, `a[b` → 2 — with the §111/§655
 * trailing-empties + limit rules UNCHANGED (the metacharacter-free case
 * was already correct on all targets; this decision only stops the
 * JVM/Script from INTERPRETING the separator).
 */
class SplitLiteralSeparatorE2ETest extends NativeCrossSupport {

    private static final String SRC = """
            main() {
                var a = "a.b".split(".")
                println(a.size)
                println(a[0] + "|" + a[1])
                var b = "a|b".split("|")
                println(b.size)
                println(b[0] + "|" + b[1])
                var c = "a[b".split("[")
                println(c.size)
                println(c[0] + "|" + c[1])
                var d = "abXcd".split("X")
                println(d.size + ":" + d[0] + "|" + d[1])
                println("a,".split(",").size)
                println("x1y".split("1").size)
            }
            """;

    /** The LITERAL contract — the same 9 lines on every target. */
    private static final String GOLDEN = String.join("\n",
            "2", "a|b",
            "2", "a|b",
            "2", "a|b",
            "2:ab|cd",
            "1",
            "2");

    private Path writeSrc(Path tmp, String tag) throws Exception {
        Path p = tmp.resolve("Main-" + tag + ".kf");
        Files.writeString(p, SRC, StandardCharsets.UTF_8);
        return p;
    }

    /** Driver cross-target: Script interprets in-process; JS runs the module
     *  through the embedded runner; JVM/native run as processes. */
    private String runOn(Target target, Path tmp, String tag) throws Exception {
        Path main = writeSrc(tmp, tag);
        Path dir = main.getParent();
        if (target == Target.SCRIPT) {
            KofInterpreter.Result result = driver.interpret(List.of(main), dir, new String[0]);
            assertEquals(0, result.exitCode(), () -> "script: " + result.stderr());
            return result.stdout().strip();
        }
        Path out = dir.resolve("out-" + tag);
        CompilationResult r = driver.compile(main, out, target);
        assertTrue(r.success(), () -> target + " compile: " + r.diagnostics().getDiagnostics());
        if (target == Target.JS) {
            java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
            int exit = KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                    new java.io.ByteArrayInputStream(new byte[0]), stdout);
            assertEquals(0, exit, () -> "js: " + stdout);
            return stdout.toString(StandardCharsets.UTF_8).strip();
        }
        ProcessBuilder pb = target == Target.JVM
                ? new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                        "-cp", out.toString(), "Default.Main")
                : new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = NativeRiscv64E2ETest.runBounded(p, "split-literal-" + tag);
        assertEquals(0, p.exitValue(), () -> target + ": " + output);
        return output.strip();
    }

    @Test
    void jvmSplitsLITERALLY(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runOn(Target.JVM, tmp, "jvm"),
                "the JVM must quote the separator (the pre-fix JVM ate `.` as"
                        + " regex → 0 pieces, and `|` alternated → 3 pieces)");
    }

    @Test
    void scriptSplitsLITERALLY(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runOn(Target.SCRIPT, tmp, "script"),
                "the interpreter must split literally (the pre-fix path called"
                        + " String.split(regex) directly)");
    }

    @Test
    void jsSplitsLITERALLY(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runOn(Target.JS, tmp, "js"),
                "the JS face was already literal — stays so (the §111 trailing"
                        + " rule rides kofSplit)");
    }

    @Test
    void nativeX86SplitsLITERALLY(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain x86-64 absent");
        assertEquals(GOLDEN, runOn(Target.NATIVE, tmp, "nat"),
                "the native face was already literal — stays so");
    }
}
