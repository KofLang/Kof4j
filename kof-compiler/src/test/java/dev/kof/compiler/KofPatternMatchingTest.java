package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofPatternMatchingTest {
    private final CompilerDriver driver = new CompilerDriver();

    // SG-014 — guarda no pattern: `case T v if (cond)` — false cai p/ próximo case
    @Test
    void switchCaseGuardFalseFallsThrough(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                class Num {
                    Int v
                    constructor(Int v) { this.v = v }
                }
                main() {
                    var n = Num(5)
                    switch (n) {
                        case Num x if (x.v > 10):
                            println("grande")
                        case Num x:
                            println("pequeno")
                        default:
                            println("outro")
                    }
                }
                """, "pequeno");
    }

    // SG-014 — guarda true: o braço guarda roda, o próximo case não
    @Test
    void switchCaseGuardTrueRunsGuardedArm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                class Num {
                    Int v
                    constructor(Int v) { this.v = v }
                }
                main() {
                    var n = Num(50)
                    switch (n) {
                        case Num x if (x.v > 10):
                            println("grande")
                        case Num x:
                            println("pequeno")
                        default:
                            println("outro")
                    }
                }
                """, "grande");
    }

    @Test
    void switchCaseStringJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var x: Object = "hello"
                    switch (x) {
                        case String s:
                            println("str:" + s)
                        default:
                            println("other")
                    }
                }
                """, "str:hello");
    }

    @Test
    void switchCaseStringNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                main() {
                    var x: Object = "hello"
                    switch (x) {
                        case String s:
                            println("str:" + s)
                        default:
                            println("other")
                    }
                }
                """, "str:hello");
    }

    @Test
    void switchCaseStringJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var x: Object = "hello"
                    switch (x) {
                        case String s:
                            println("str:" + s)
                        default:
                            println("other")
                    }
                }
                """, "str:hello");
    }

    @Test
    void switchCaseStringDefaultJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                class Dummy {}

                main() {
                    var x: Object = new Dummy()
                    switch (x) {
                        case String s:
                            println("str:" + s)
                        default:
                            println("other")
                    }
                }
                """, "other");
    }

    @Test
    void instanceofCheckJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var a: Object = "world"
                    if (a instanceof String) {
                        println("is string")
                    } else {
                        println("not string")
                    }
                }
                """, "is string");
    }

    @Test
    void instanceofCheckJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var a: Object = "world"
                    if (a instanceof String) {
                        println("is string")
                    } else {
                        println("not string")
                    }
                }
                """, "is string");
    }

    @Test
    void checkcastAsJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var b: Object = "test" as String
                    println(b)
                }
                """, "test");
    }

    @Test
    void checkcastAsNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                main() {
                    var b: Object = "test" as String
                    println(b)
                }
                """, "test");
    }

    @Test
    void checkcastAsJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var b: Object = "test" as String
                    println(b)
                }
                """, "test");
    }

    @Test
    void instanceofAndSwitchCombinedJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var x: Object = "hello"
                    switch (x) {
                        case String s:
                            println("str:" + s)
                        default:
                            println("other")
                    }
                    var a: Object = "world"
                    if (a instanceof String) {
                        println("is string")
                    }
                    var b: Object = "test" as String
                    println(b)
                }
                """, "str:hello\nis string\ntest");
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString() + ":kof-runtime/target/classes", "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        Path mjs = outDir.resolve("Default.mjs");
        try {
            Process p = new ProcessBuilder("node", mjs.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals(expected, output, "JS output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }
}
