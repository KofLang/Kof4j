package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §834 — a lambda or a {@code spawn} inside a {@code try} body that has a
 * {@code finally} used to wipe the enclosing finally frames (the "saved" copy
 * was the same deque that was then cleared) and leave {@code tryDepth} at the
 * lambda's value. The outer {@code try} then crashed the compiler
 * (NoSuchElementException, COMP002) or lowered the {@code return} without its
 * finally and with the wrong number of exception-handler unlinks (Native).
 */
class FinallyAcrossLambdaE2ETest {

    private static final String PROGRAM = """
            Int one() { return 1 }

            Int viaSpawn() {
                try {
                    val h = spawn one()
                    return await h
                } finally {
                    println("finally spawn")
                }
            }

            Int viaLambda() {
                try {
                    val inc = (x: Int) -> x + 1
                    return inc(41)
                } finally {
                    println("finally lambda")
                }
            }

            Int viaPlain() {
                try {
                    val inc = 1
                    return inc + 41
                } finally {
                    println("finally plain")
                }
            }

            main() {
                println(viaSpawn())
                println(viaLambda())
                println(viaPlain())
            }
            """;

    private static final String EXPECTED = String.join("\n",
            "finally spawn", "1", "finally lambda", "42", "finally plain", "42");

    @Test
    void finallyStillRunsAfterALambdaOrSpawnInTheTryBody(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, PROGRAM);

        Path jvmOut = tmp.resolve("jvm");
        CompilationResult jvm = new CompilerDriver().compile(src, jvmOut, Target.JVM);
        assertTrue(jvm.success(), () -> "JVM compile: " + jvm.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{jvmOut.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals(EXPECTED, buf.toString(StandardCharsets.UTF_8).trim(), "JVM");

        KofInterpreter.Result script = new CompilerDriver().interpret(List.of(src), tmp, new String[0]);
        assertEquals(EXPECTED, script.stdout().trim(), () -> "Script, stderr: " + script.stderr());

        Path jsOut = tmp.resolve("js");
        CompilationResult js = new CompilerDriver().compile(src, jsOut, Target.JS);
        assertTrue(js.success(), () -> "JS compile: " + js.diagnostics().getDiagnostics());
        ByteArrayOutputStream jsBuf = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsOut.resolve("Default.mjs"), jsBuf,
                new java.io.ByteArrayInputStream(new byte[0]), jsBuf);
        assertEquals(0, exit, () -> "JS: " + jsBuf);
        assertEquals(EXPECTED, jsBuf.toString(StandardCharsets.UTF_8).trim(), "JS");
    }

    @Test
    void aLambdaInTheTryBodyKeepsTheHandlerUnlinkCount(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        IRModule ir = new CompilerDriver().prepareRepl(List.of(src), tmp);
        assertEquals(unlinks(ir, "viaPlain"), unlinks(ir, "viaLambda"),
                "the return inside the try must unlink the same handlers with or without a lambda before it");
    }

    private static long unlinks(IRModule ir, String method) {
        return ir.classes().stream().flatMap(c -> c.methods().stream())
                .filter(m -> m.name().equals(method))
                .flatMap(m -> m.basicBlocks().stream()).flatMap(b -> b.operations().stream())
                .filter(op -> op instanceof KofExcUnlink).count();
    }
}
