package dev.kof.compiler;

import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suporte do MVP de {@code kof.shell} ({@code ShellE2ETest}): o harness JVM/JS
 * (captura de stdout) e os oráculos de paridade/refusal. Vive fora da classe de
 * teste para mantê-la abaixo do limite de 500 linhas de teste (Fase 3 da
 * arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os testes e o nome da
 * classe seguem no {@code ShellE2ETest} — zero drift de citação.
 */
abstract class ShellSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    @TempDir
    protected Path tmp;

    protected record Run(boolean ok, String output) {}

    protected Run runJvm(Path src, Path out) throws Exception {
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

    protected Run runJs(Path src, Path out) throws Exception {
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

    protected static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    /** Both targets must run the same Kof source to the SAME output. */
    protected void assertJvmJsParity(String source, String... expectedInOutput) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), source);
        Run jvm = runJvm(tmp.resolve("S.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(tmp.resolve("S.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        for (String e : expectedInOutput) {
            assertTrue(jvm.output().contains(e), () -> "JVM expected '" + e + "' in: " + jvm.output());
            assertEquals(jvm.output(), js.output(), "§2.2 JVM/JS parity broken");
        }
    }

    protected void assertNativeParity(String source, String expected) throws Exception {
        Files.writeString(tmp.resolve("P.kf"), source);
        var jvmOut = new java.io.ByteArrayOutputStream();
        CompilationResult jr = driver.compile(tmp.resolve("P.kf"), tmp.resolve("o-jvm"), Target.JVM);
        assertTrue(jr.success(), "JVM must compile: " + jr.diagnostics().getDiagnostics());
        var oldOut = System.out;
        System.setOut(new java.io.PrintStream(jvmOut, true, java.nio.charset.StandardCharsets.UTF_8));
        String jvm;
        try {
            var cl = new java.net.URLClassLoader(new java.net.URL[]{tmp.resolve("o-jvm").toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(oldOut);
        }
        jvm = jvmOut.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(expected, jvm, "JVM golden");
        CompilationResult nr = driver.compile(tmp.resolve("P.kf"), tmp.resolve("o-nat"), Target.NATIVE);
        assertTrue(nr.success(), "NATIVE must compile: " + nr.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(tmp.resolve("o-nat").resolve("Default/Main").toString())
                .redirectErrorStream(false).start();
        String nat = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(expected, nat, "NATIVE must match the JVM golden byte-for-byte");
    }

    protected void assertCompiles(Target target, String source) throws Exception {
        Files.writeString(tmp.resolve("C.kf"), source);
        CompilationResult r = driver.compile(tmp.resolve("C.kf"), tmp.resolve("o-c-" + target), target);
        assertTrue(r.success(), target + " must compile: " + diags(r));
    }

    protected void assertGap(Target target, String code, String source) throws Exception {
        Files.writeString(tmp.resolve("G.kf"), source);
        CompilationResult r = driver.compile(tmp.resolve("G.kf"), tmp.resolve("o-" + target), target);
        assertFalse(r.success(), target + " must refuse the call (" + code + ")");
        Set<String> codes = new LinkedHashSet<>();
        r.diagnostics().getDiagnostics().forEach(d -> codes.add(String.valueOf(d.code())));
        assertTrue(r.diagnostics().getDiagnostics().toString().contains(code)
                        || codes.contains(code),
                () -> "expected " + code + " for " + target + ", got: " + diags(r));
    }
}
