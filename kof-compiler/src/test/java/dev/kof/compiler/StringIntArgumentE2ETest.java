package dev.kof.compiler;

import dev.kof.runtime.KofJsRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringIntArgumentE2ETest implements NativeToolchainAssumptions {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String[] BAD_CALLS = {
            "s.substring(\"1\")",
            "s.substring(0, true)",
            "s.charAt(1.5)",
            "s.indexOf(\"c\", \"1\")",
            "s.lastIndexOf(\"c\", big)",
            "s.startsWith(\"b\", 1.0)",
            "s.split(\",\", true)",
    };

    private static final String VALID = String.join("\n",
            "pos(): Int {",
            "    return 4",
            "}",
            "",
            "main() {",
            "    var s = \"abcdef\"",
            "    var i = 2",
            "    var nums = listOf(1, 2, 3)",
            "    println(s.substring(1))",
            "    println(s.substring(1, i))",
            "    println(s.charAt(i))",
            "    println(s.indexOf(\"c\", 1))",
            "    println(s.lastIndexOf(\"c\", 5))",
            "    println(s.startsWith(\"b\", 1))",
            "    println(\"a,b,c\".split(\",\", 2).length)",
            "    println(s.substring(s.length - 2))",
            "    println(s.substring(nums.get(0)))",
            "    println(s.substring(pos()))",
            "}");

    private static final String VALID_GOLDEN = String.join("\n",
            "bcdef", "b", "c", "2", "2", "true", "2", "ef", "bcdef", "ef");

    private static String program(String call) {
        return String.join("\n",
                "main() {",
                "    var s = \"abcdef\"",
                "    var big = \"1\".toLong()",
                "    println(" + call + ")",
                "}");
    }

    @Test
    void nonIntArgumentForAnIntParameterIsRejectedOnEveryTarget() throws Exception {
        for (int c = 0; c < BAD_CALLS.length; c++) {
            String call = BAD_CALLS[c];
            Path main = write("bad-" + c, program(call));
            for (Target target : new Target[]{Target.JVM, Target.JS, Target.NATIVE}) {
                CompilationResult r = driver.compile(main, main.getParent().resolve("out-" + target), target);
                String diags = r.diagnostics().getDiagnostics().toString();
                assertFalse(r.success(), () -> call + " @ " + target + " must be rejected; got: " + diags);
                assertTrue(diags.contains("SEM014"), () -> call + " @ " + target + " got: " + diags);
            }
            KofInterpretException scriptError = assertThrows(KofInterpretException.class,
                    () -> driver.interpret(List.of(main), main.getParent(), new String[0]),
                    () -> call + " @ script must be rejected");
            assertTrue(String.valueOf(scriptError.getMessage()).contains("SEM014"),
                    () -> call + " @ script got: " + scriptError.getMessage());
        }
    }

    @Test
    void errorNamesTheArgumentAndItsLine() throws Exception {
        Path main = write("message", program("s.substring(\"1\")"));
        CompilationResult r = driver.compile(main, main.getParent().resolve("out"), Target.JVM);
        List<Diagnostic> diags = r.diagnostics().getDiagnostics();
        assertEquals(1, diags.size(), () -> "got: " + diags);
        assertEquals("Argument 1 of 'substring': expected 'Int' but got 'String'", diags.get(0).message());
        assertEquals("SEM014", diags.get(0).code());
        assertEquals(4, diags.get(0).line());
    }

    @Test
    void validIntArgumentsStillMatchTheJvmOracleOnEveryTarget() throws Exception {
        assertEquals(VALID_GOLDEN, run("valid-jvm", VALID, Target.JVM));
        assertEquals(VALID_GOLDEN, run("valid-script", VALID, Target.SCRIPT));
        assertEquals(VALID_GOLDEN, run("valid-js", VALID, Target.JS));
        assumeToolchain(X86_64_TOOLS);
        assertEquals(VALID_GOLDEN, run("valid-native", VALID, Target.NATIVE));
    }

    private Path write(String name, String source) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Path main = dir.resolve("Main.kf");
        Files.writeString(main, source);
        return main;
    }

    private String run(String name, String source, Target target) throws Exception {
        Path main = write(name, source);
        Path dir = main.getParent();
        if (target == Target.SCRIPT) {
            KofInterpreter.Result result = driver.interpret(List.of(main), dir, new String[0]);
            assertEquals(0, result.exitCode(), () -> name + ": " + result.stderr());
            return result.stdout().strip();
        }
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(main, out, target);
        assertTrue(r.success(), () -> name + ": " + r.diagnostics().getDiagnostics());
        if (target == Target.JS) {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            int exit = KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                    new ByteArrayInputStream(new byte[0]), stdout);
            assertEquals(0, exit, () -> name + ": " + stdout);
            return stdout.toString(StandardCharsets.UTF_8).strip();
        }
        ProcessBuilder pb = target == Target.JVM
                ? new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                        "-cp", out.toString(), "Default.Main")
                : new ProcessBuilder(out.resolve("Default/Main").toString());
        Process p = pb.redirectErrorStream(true).start();
        String output = NativeRiscv64E2ETest.runBounded(p, name);
        assertEquals(0, p.exitValue(), () -> name + ": " + output);
        return output;
    }
}
