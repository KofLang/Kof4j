package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * kof.ssh MVP (universal plan Stage 2, row 2.3; VISION §6.1 option B
 * "over kof.process/FFI").
 *
 * <p>The golden contract is the argv-as-list: the host and the command are never
 * concatenated into a string for a shell to re-parse (the sh -c injection class),
 * so the literal {@code "a b|c && d"} must survive intact as ONE argv element.
 * {@code cmd} builds the argv ({@code ["ssh","-o","BatchMode=yes","-o",
 * "ConnectTimeout=5",host,command]}); {@code run} lowers onto the existing
 * process layer and therefore carries JVM+JS byte-for-byte parity (§239
 * discipline); {@code ok} is pure field/compare IR on the shared kof.process
 * Result. Native keeps the honest compile-time {@code PROC001}, exactly like
 * process/shell (never a raw call that would ReferenceError — the §235 lesson).
 *
 * <p>Execution is the process layer's already-proven surface; here it is pinned
 * that {@code run} returns an honest Result for a host that cannot be reached
 * (exit != 0, no throw, no hang — BatchMode + ConnectTimeout).
 */
class SshE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static String captureNative(Process p) throws IOException, InterruptedException {
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "native program exit code, output: " + out);
        return out;
    }

    private String runNative(Path src, Path out, Target target) throws IOException {
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), target + " deve compilar ssh.*: " + diags(r));
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), target + " binario deve existir");
        ProcessBuilder pb = target == Target.NATIVE
                ? new ProcessBuilder(bin.toString())
                : NativeRiscv64E2ETest.qemu(target.nativeArch(), bin);
        pb.redirectErrorStream(true);
        try {
            return captureNative(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    /** Both targets must run the same Kof source to the SAME output. */
    private void assertJvmJsParity(String source, String... expectedInOutput) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), source);
        Run jvm = runJvm(tmp.resolve("S.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(tmp.resolve("S.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        for (String e : expectedInOutput) {
            assertTrue(jvm.output().contains(e), () -> "JVM expected '" + e + "' in: " + jvm.output());
        }
        assertEquals(jvm.output(), js.output(), "§2.3 JVM/JS parity broken");
    }

    private void assertCompiles(Target target, String source) throws Exception {
        Files.writeString(tmp.resolve("C.kf"), source);
        CompilationResult r = driver.compile(tmp.resolve("C.kf"), tmp.resolve("o-c-" + target), target);
        assertTrue(r.success(), target + " must compile: " + diags(r));
    }

    private void assertGap(Target target, String code, String source) throws Exception {
        Files.writeString(tmp.resolve("G.kf"), source);
        CompilationResult r = driver.compile(tmp.resolve("G.kf"), tmp.resolve("o-" + target), target);
        assertFalse(r.success(), target + " must refuse the call (" + code + ")");
        Set<String> codes = new LinkedHashSet<>();
        r.diagnostics().getDiagnostics().forEach(d -> codes.add(String.valueOf(d.code())));
        assertTrue(r.diagnostics().getDiagnostics().toString().contains(code)
                        || codes.contains(code),
                () -> "expected " + code + " for " + target + ", got: " + diags(r));
    }

    @Test
    void cmdBuildsSshArgvJvmJsParity() throws Exception {
        assertJvmJsParity("""
            main() {
                var a = ssh.cmd("user@host", "uname -a")
                println(a.size)
                println(a.get(0))
                println(a.get(1))
                println(a.get(2))
                println(a.get(3))
                println(a.get(4))
                println(a.get(5))
                println(a.get(6))
            }
            """,
                "7", "ssh", "-o", "BatchMode=yes", "ConnectTimeout=5", "user@host", "uname -a");
    }

    @Test
    void cmdNeverConcatenatesIntoShellString() throws Exception {
        // host + command with spaces and metacharacters stay ONE element each —
        // a shell would never see them (the injection class).
        assertJvmJsParity("""
            main() {
                var a = ssh.cmd("h; rm -rf /", "echo a b|c && d")
                println(a.size)
                println("[" + a.get(5) + "]")
                println("[" + a.get(6) + "]")
            }
            """,
                "7", "[h; rm -rf /]", "[echo a b|c && d]");
    }

    @Test
    void cmdAcceptsEmptyHostAndCommand() throws Exception {
        // Q3 edge: empty strings are still argv elements, never dropped.
        assertJvmJsParity("""
            main() {
                var a = ssh.cmd("", "")
                println(a.size)
                println("[" + a.get(5) + "]")
                println("[" + a.get(6) + "]")
            }
            """,
                "7", "[]");
    }

    @Test
    void okIsPureOnSharedProcessResult() throws Exception {
        // ssh.ok operates on kof.process's Result — never a fork.
        assertJvmJsParity("""
            main() {
                println(ssh.ok(shell.run("true")))
                println(ssh.ok(shell.run("false")))
            }
            """,
                "true", "false");
    }

    @Test
    void runReturnsHonestResultNeverThrows() throws Exception {
        // A host that cannot resolve: run returns a Result with exit != 0
        // (spawn failure -1 or connection failure 255), never a throw/hang.
        Files.writeString(tmp.resolve("R.kf"), """
            main() {
                var r = ssh.run("ssh.invalid", "true")
                println(r.exitCode != 0)
            }
            """);
        Run jvm = runJvm(tmp.resolve("R.kf"), tmp.resolve("o-r"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        assertTrue(jvm.output().contains("true"),
                () -> "expected exit != 0, got: " + jvm.output());
    }

    @Test
    void cmdAndRunLandedOnX86() throws Exception {
        assertCompiles(Target.NATIVE, """
            main() {
                var a = ssh.cmd("host", "hi")
                println(a.size)
                var r = ssh.run("host", "hi")
                println(r.exitCode)
            }
            """);
    }

    @Test
    void sshFacesLandedOnAllNative() throws Exception {
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            assertCompiles(t, """
                main() {
                    var a = ssh.cmd("host", "hi")
                    println(a.size)
                }
                """);
        }
    }

    @Test
    void sshOnMcuIsHonestProc001() throws Exception {
        for (Target t : new Target[]{Target.NATIVE_RISCV32, Target.NATIVE_MCU_ARM}) {
            assertGap(t, "PROC001", """
                main() {
                    var a = ssh.cmd("host", "hi")
                    println(a.size)
                }
                """);
        }
    }

    @Test
    void cmdArgvMatchesJvmOnX86() throws Exception {
        String src = """
            main() {
                var a = ssh.cmd("user@host", "uname -a")
                println(a.size)
                println(a.get(0))
                println(a.get(1))
                println(a.get(2))
                println(a.get(3))
                println(a.get(4))
                println(a.get(5))
                println(a.get(6))
            }
            """;
        Files.writeString(tmp.resolve("N.kf"), src);
        Run jvm = runJvm(tmp.resolve("N.kf"), tmp.resolve("o-n-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        CompilationResult nr = driver.compile(tmp.resolve("N.kf"), tmp.resolve("o-n-nat"), Target.NATIVE);
        assertTrue(nr.success(), "NATIVE must compile: " + diags(nr));
        Process p = new ProcessBuilder(tmp.resolve("o-n-nat").resolve("Default/Main").toString()).start();
        String nat = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "native exit, output: " + nat);
        assertEquals(jvm.output(), nat, "x86 ssh.cmd argv must match the JVM golden byte-for-byte");
    }

    @Test
    void sshRunIsHonestResultOnJvmAndJs() throws Exception {
        assertJvmJsParity("""
            main() {
                var r = ssh.run("ssh.invalid", "true")
                println(r.exitCode != 0)
                println(ssh.ok(r))
            }
            """, "true", "false");
    }

    @Test
    void allSshFacesMatchJvmAcrossAllTargets() throws Exception {
        String source = """
            main() {
                var c = ssh.cmd("user@host", "uname -a")
                println(c.size)
                println(c.get(0))
                println(c.get(6))
                var r = ssh.run("ssh.invalid", "true")
                println(r.exitCode != 0)
                println(ssh.ok(r))
            }
            """;
        Files.writeString(tmp.resolve("All.kf"), source);
        Run jvm = runJvm(tmp.resolve("All.kf"), tmp.resolve("o-all-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        assertEquals("7\nssh\nuname -a\ntrue\nfalse\n", jvm.output(), "JVM golden ssh.*");

        Run js = runJs(tmp.resolve("All.kf"), tmp.resolve("o-all-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        assertEquals(jvm.output(), js.output(), "JS ssh.* deve bater o golden JVM");

        String x86 = runNative(tmp.resolve("All.kf"), tmp.resolve("o-all-x86"), Target.NATIVE);
        assertEquals(jvm.output(), x86, "x86-64 ssh.* deve bater o golden JVM");

        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && NativeRiscv64E2ETest.qemuPrefix("riscv64") != null,
                "cross riscv64 toolchain/sysroot ausente — pulando");
        String riscv = runNative(tmp.resolve("All.kf"), tmp.resolve("o-all-riscv"), Target.NATIVE_RISCV64);
        assertEquals(jvm.output(), riscv, "riscv64 ssh.* deve bater o golden JVM");

        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")
                        && NativeRiscv64E2ETest.qemuPrefix("aarch64") != null,
                "cross aarch64 toolchain/sysroot ausente — pulando");
        String aarch = runNative(tmp.resolve("All.kf"), tmp.resolve("o-all-aarch"), Target.NATIVE_AARCH64);
        assertEquals(jvm.output(), aarch, "aarch64 ssh.* deve bater o golden JVM");
    }

    @Test
    void unknownSshMethodIsSem025() throws Exception {
        assertGap(Target.JVM, "SEM025", """
            main() {
                println(ssh.frobnicate("x", "y"))
            }
            """);
    }
}
