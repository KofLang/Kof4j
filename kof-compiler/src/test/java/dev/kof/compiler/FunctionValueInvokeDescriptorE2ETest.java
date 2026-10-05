package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A function-typed local whose value came from a FIELD, called inside a loop,
 * used to crash the JVM backend with {@code COMP002 "frame crash"} (ASM
 * COMPUTE_FRAMES AIOOBE). Root: the synthetic SAM {@code invoke} call site was
 * emitted with the INFERRED argument types ({@code Int} for a {@code 0}
 * literal) instead of the declared function type's parameter types
 * ({@code Long}), so the bytecode pushed an {@code LCONST_0} where the
 * descriptor {@code invoke(I)J} expected an int — the frame computation then
 * failed. The declared types are the ones the synthetic interface/class
 * declares ({@code CompilerLambdaClass.lambdaInterfaceType}).
 */
class FunctionValueInvokeDescriptorE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void fieldFunctionValueCalledInLoopOnJvm() throws Exception {
        assertEquals("r=3", runJvm(callInLoop()));
        assertEquals("r=14", runJvm(valueThroughLoop()));
    }

    @Test
    void fieldFunctionValueCalledInLoopOnScript() throws Exception {
        assertEquals("r=3", runScript(callInLoop()));
        assertEquals("r=14", runScript(valueThroughLoop()));
    }

    @Test
    void fieldFunctionValueCalledInLoopOnJs() throws Exception {
        assertEquals("r=3", runJs(callInLoop()));
        assertEquals("r=14", runJs(valueThroughLoop()));
    }

    // The minimal reproducer: local from field, called in a while loop. The
    // argument is the Int literal 0 but the field's parameter type is Long.
    private static String callInLoop() {
        return """
                class C {
                    (Long) -> Long f
                    constructor((Long) -> Long f) { this.f = f }
                    Long go() {
                        var src = this.f
                        var i = 0
                        while (i < 3) { src(0); i = i + 1 }
                        return i as Long
                    }
                }
                main() { var c = C((x: Long) -> x + 1); println("r=" + c.go()) }
                """;
    }

    // Value-correctness face: the Long flows through the loop unchanged.
    private static String valueThroughLoop() {
        return """
                class C {
                    (Long) -> Long f
                    constructor((Long) -> Long f) { this.f = f }
                    Long go(Long seed) {
                        var src = this.f
                        var now = src(seed)
                        var i = 0
                        while (i < 3) { now = src(now); i = i + 1 }
                        return now
                    }
                }
                main() { var c = C((x: Long) -> x + 1); println("r=" + c.go(10)) }
                """;
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(source, out, Target.JVM);
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = driver.interpret(
                java.util.List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JS);
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }
}
