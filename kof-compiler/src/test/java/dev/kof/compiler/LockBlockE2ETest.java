package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * G240 — {@code lock (target) { body }}: mutual exclusion that is released on
 * every exit (normal end, throw, return, break) and is reentrant. Four tasks
 * race on one counter; without the lock the read-modify-write loses updates.
 * Same output on JVM, Script, JS and Native x86-64 (Linux).
 */
class LockBlockE2ETest {

    static final String PROGRAM = """
            class Counter {
                Int n = 0

                add(Int times) {
                    for (var i = 0; i < times; i = i + 1) {
                        lock (this) {
                            val v = n
                            n = v + 1
                        }
                    }
                }
            }

            Int bump(Counter c, Int times) {
                c.add(times)
                return times
            }

            Int read(Counter c) {
                lock (c) {
                    return c.n
                }
            }

            exits(Counter c) {
                lock (c) {
                    lock (c) {
                        println("reentrant")
                    }
                }
                try {
                    lock (c) {
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught " + e)
                }
                for (var i = 0; i < 3; i = i + 1) {
                    lock (c) {
                        if (i == 1) {
                            break
                        }
                    }
                }
                println(read(c))
            }

            main() {
                val c = Counter()
                val a = spawn bump(c, 20000)
                val b = spawn bump(c, 20000)
                val d = spawn bump(c, 20000)
                val e = spawn bump(c, 20000)
                println(await a + await b + await d + await e)
                println(c.n)
                exits(c)
                val f = spawn bump(c, 1)
                await f
                println(c.n)
            }
            """;

    static final String EXPECTED = String.join("\n",
            "80000", "80000", "reentrant", "caught boom", "80000", "80001");

    @Test
    void lockExcludesAndReleasesOnEveryExitOnJvmScriptAndJs(@TempDir Path tmp) throws Exception {
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
    void lockExcludesOnNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"), "Native target runs on Linux");
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        CompilationResult nat = new CompilerDriver().compile(src, tmp.resolve("out"), Target.NATIVE);
        assertTrue(nat.success(), () -> "Native compile: " + nat.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(tmp.resolve("out").resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), () -> "exit, output: " + out);
        assertEquals(EXPECTED, out, "Native");
    }

    @Test
    void theFormatterKeepsTheLockStatementAndACallNamedLockStaysACall(@TempDir Path tmp) throws IOException {
        String once = KofFormatter.format(PROGRAM, "Main.kf");
        assertTrue(once.contains("lock (this) {"), once);
        assertEquals(once, KofFormatter.format(once, "Main.kf"), "idempotent");
        String callProgram = """
                Int lock(Int x) { return x + 1 }
                main() {
                    println(lock(41))
                }
                """;
        assertTrue(KofFormatter.format(callProgram, "Main.kf").contains("println(lock(41))"));
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, callProgram);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(src), tmp, new String[0]);
        assertEquals("42", r.stdout().trim(), () -> "a function named lock is still callable: " + r.stderr());
    }

    @Test
    void lockExcludesOnNativeCross(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch), "cross toolchain " + arch + " missing");
            Path src = tmp.resolve("M" + arch + ".kf");
            Files.writeString(src, PROGRAM);
            Path outDir = tmp.resolve("cross-" + arch);
            CompilationResult r = new CompilerDriver().compile(src, outDir, t);
            assertTrue(r.success(), () -> t + " compile: " + r.diagnostics().getDiagnostics());
            assertEquals(EXPECTED, NativeRiscv64E2ETest.runQemu(arch, outDir.resolve("Default/Main")), t.toString());
        }
    }

    @Test
    void aValueTargetIsConc004AndASuspendingLockOnJsIsConc005(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
                main() {
                    val n = 3
                    lock (n) {
                        println(n)
                    }
                }
                """);
        CompilationResult bad = new CompilerDriver().compile(src, tmp.resolve("bad"), Target.JVM);
        assertFalse(bad.success());
        assertTrue(bad.diagnostics().getDiagnostics().stream().anyMatch(d -> "CONC004".equals(d.code())),
                () -> bad.diagnostics().getDiagnostics().toString());

        Files.writeString(src, """
                class Box {
                    Int n = 0
                }
                Int one() { return 1 }
                main() {
                    val b = Box()
                    lock (b) {
                        val h = spawn one()
                        b.n = await h
                    }
                    println(b.n)
                }
                """);
        CompilationResult js = new CompilerDriver().compile(src, tmp.resolve("js"), Target.JS);
        assertFalse(js.success());
        assertTrue(js.diagnostics().getDiagnostics().stream().anyMatch(d -> "CONC005".equals(d.code())),
                () -> js.diagnostics().getDiagnostics().toString());
        CompilationResult jvm = new CompilerDriver().compile(src, tmp.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), () -> "awaiting inside a lock is fine on a threaded target: "
                + jvm.diagnostics().getDiagnostics());
    }
}
