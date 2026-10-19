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

class CollectionSizePropertyTypeE2ETest implements NativeToolchainAssumptions {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String STDLIB_ARGUMENTS = String.join("\n",
            "main() {",
            "    var l = listOf(\"a\", \"b\", \"c\")",
            "    var m = mapOf(\"a\", 1, \"b\", 2)",
            "    var s = setOf(1, 2, 3, 4)",
            "    var a = new Int[5]",
            "    var n = l.size",
            "    println(math.abs(l.size))",
            "    println(math.max(m.size, s.size))",
            "    println(math.min(l.length, m.length))",
            "    println(math.clamp(s.length, 0, n))",
            "    println(math.max(a.size, a.count))",
            "    println(strings.repeat(\"ab\", m.size))",
            "    println(random.randomInt(l.size) < l.size)",
            "    println(l[random.randomInt(l.size)].length() == 1)",
            "    println(random.randomInt(a.count) < a.length)",
            "}");

    private static final String STDLIB_ARGUMENTS_GOLDEN = String.join("\n",
            "3", "4", "2", "3", "5", "abab", "true", "true", "true");

    private static final String SIZES_IN_A_LIST = String.join("\n",
            "main() {",
            "    var a = listOf(1, 2)",
            "    var b = setOf(\"x\")",
            "    var c = new Int[4]",
            "    var sizes = listOf(a.size, b.size, c.count)",
            "    println(sizes.get(0) + sizes.get(1) + sizes.get(2))",
            "}");

    private static final String[][] TYPE_ERRORS = {
            {"SEM021", String.join("\n",
                    "main() {",
                    "    var l = listOf(1, 2, 3)",
                    "    String t = l.size",
                    "    println(t)",
                    "}")},
            {"SEM010", String.join("\n",
                    "nonEmpty(List<Int> xs): Bool {",
                    "    return xs.size",
                    "}",
                    "",
                    "main() {",
                    "    println(nonEmpty(listOf(1)))",
                    "}")},
    };

    @Test
    void stdlibNamespacesAcceptTheSizePropertyOnEveryTarget() throws Exception {
        assertEquals(STDLIB_ARGUMENTS_GOLDEN, run("stdlib-jvm", STDLIB_ARGUMENTS, Target.JVM));
        assertEquals(STDLIB_ARGUMENTS_GOLDEN, run("stdlib-script", STDLIB_ARGUMENTS, Target.SCRIPT));
        assertEquals(STDLIB_ARGUMENTS_GOLDEN, run("stdlib-js", STDLIB_ARGUMENTS, Target.JS));
        assumeToolchain(X86_64_TOOLS);
        assertEquals(STDLIB_ARGUMENTS_GOLDEN, run("stdlib-native", STDLIB_ARGUMENTS, Target.NATIVE));
    }

    @Test
    void sizePropertiesStoredInAListAddAsIntsOnEveryTarget() throws Exception {
        assertEquals("7", run("sizes-jvm", SIZES_IN_A_LIST, Target.JVM));
        assertEquals("7", run("sizes-script", SIZES_IN_A_LIST, Target.SCRIPT));
        assertEquals("7", run("sizes-js", SIZES_IN_A_LIST, Target.JS));
        assumeToolchain(X86_64_TOOLS);
        assertEquals("7", run("sizes-native", SIZES_IN_A_LIST, Target.NATIVE));
    }

    @Test
    void sizePropertyIsRejectedWhereAnIntIsRejectedOnEveryTarget() throws Exception {
        for (String[] bad : TYPE_ERRORS) {
            Path main = write("bad-" + bad[0], bad[1]);
            for (Target target : new Target[]{Target.JVM, Target.JS, Target.NATIVE}) {
                CompilationResult r = driver.compile(main, main.getParent().resolve("out-" + target), target);
                String diags = r.diagnostics().getDiagnostics().toString();
                assertFalse(r.success(), () -> bad[0] + " @ " + target + " must be rejected; got: " + diags);
                assertTrue(diags.contains(bad[0]), () -> bad[0] + " @ " + target + " got: " + diags);
            }
            KofInterpretException scriptError = assertThrows(KofInterpretException.class,
                    () -> driver.interpret(List.of(main), main.getParent(), new String[0]),
                    () -> bad[0] + " @ script must be rejected");
            assertTrue(String.valueOf(scriptError.getMessage()).contains(bad[0]),
                    () -> bad[0] + " @ script got: " + scriptError.getMessage());
        }
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
