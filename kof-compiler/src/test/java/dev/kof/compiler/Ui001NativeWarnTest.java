package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * UI001 (R6, issue #683): kof.ui on the Native targets is a no-op — the
 * compiler must warn once ([UI001]), never stay silent. Mirrors the UI002
 * contract (warn once, never break the build).
 */
class Ui001NativeWarnTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static boolean nativeToolchain() {
        try {
            return new ProcessBuilder("as", "--version").redirectErrorStream(true)
                    .start().waitFor() == 0
                    && new ProcessBuilder("ld", "--version").redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static IRModule moduleWith(String... callees) {
        List<KofOperation> ops = new java.util.ArrayList<>();
        for (String fn : callees) {
            ops.add(new KofCall(null, fn, List.of(), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
        }
        IRMethod m = new IRMethod("main", Type.PrimitiveType.VOID, List.of(), 0,
                List.of(), List.of(new IRBasicBlock(0, ops)), List.of());
        IRClass c = new IRClass("Default/Main", "java/lang/Object", List.of(), 0,
                List.of(), List.of(m), List.of(), null, 0);
        return new IRModule("Default", List.of(c), List.of());
    }

    @Test
    void warnsOnceNamingFirstUiCall() {
        DiagnosticCollector diags = new DiagnosticCollector();
        String first = Ui001NativeWarn.warnOnce(
                moduleWith("kof_ui_window_new", "kof_ui_label_new"), Target.NATIVE, diags);
        assertEquals("kof_ui_window_new", first);
        assertEquals(1, diags.getDiagnostics().size(), "UI001 must warn exactly once");
        Diagnostic d = diags.getDiagnostics().get(0);
        assertEquals(Diagnostic.Severity.WARNING, d.severity(), "UI001 is a warning, never an error");
        assertEquals("UI001", d.code());
        assertTrue(d.message().contains("kof_ui_window_new"), d.message());
        assertTrue(d.message().contains("--target=js"), d.message());
    }

    @Test
    void warnsOnEveryNativeFlavor() {
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64,
                Target.NATIVE_RISCV32, Target.NATIVE_MCU_ARM}) {
            DiagnosticCollector diags = new DiagnosticCollector();
            assertEquals("kof_ui_label_new",
                    Ui001NativeWarn.warnOnce(moduleWith("kof_ui_label_new"), t, diags), "target " + t);
            assertEquals(1, diags.getDiagnostics().size(), "target " + t);
        }
    }

    @Test
    void silentWithoutUiCalls() {
        DiagnosticCollector diags = new DiagnosticCollector();
        assertNull(Ui001NativeWarn.warnOnce(moduleWith("kof_string_concat"), Target.NATIVE, diags));
        assertTrue(diags.getDiagnostics().isEmpty());
        assertNull(Ui001NativeWarn.warnOnce(new IRModule("Default", List.of(), List.of()),
                Target.NATIVE, diags));
        assertTrue(diags.getDiagnostics().isEmpty());
    }

    @Test
    void silentOnNonNativeTargets() {
        for (Target t : new Target[]{Target.JVM, Target.JS, Target.ANDROID}) {
            DiagnosticCollector diags = new DiagnosticCollector();
            assertNull(Ui001NativeWarn.warnOnce(moduleWith("kof_ui_window_new"), t, diags),
                    "target " + t);
            assertTrue(diags.getDiagnostics().isEmpty(), "target " + t);
        }
    }

    @Test
    void jvmCompileOfUiProgramHasNoUi001(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("UiNoWarn.kf");
        Files.writeString(f, """
                main() {
                    var w = Window("App")
                    var l = Label("oi")
                    w.bind(l)
                    w.show()
                    println("ok")
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("jvm"), Target.JVM);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        assertTrue(r.diagnostics().getDiagnostics().stream().noneMatch(d -> "UI001".equals(d.code())),
                "JVM no-op is by design — no UI001 there");
    }

    @Test
    void nativeCompileOfUiProgramWarnsUi001Once(@TempDir Path tmp) throws IOException {
        assumeTrue(isLinux(), "Native target runs on Linux");
        assumeTrue(nativeToolchain(), "as/ld absent — honest environmental skip");
        Path f = tmp.resolve("UiWarn.kf");
        Files.writeString(f, """
                main() {
                    var w = Window("App")
                    var l = Label("oi")
                    w.bind(l)
                    w.show()
                    println("ok")
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("native"), Target.NATIVE);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        long ui001 = r.diagnostics().getDiagnostics().stream()
                .filter(d -> "UI001".equals(d.code())).count();
        assertEquals(1, ui001, "exactly one UI001: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nativeCompileWithoutUiHasNoUi001(@TempDir Path tmp) throws IOException {
        assumeTrue(isLinux(), "Native target runs on Linux");
        assumeTrue(nativeToolchain(), "as/ld absent — honest environmental skip");
        Path f = tmp.resolve("NoUi.kf");
        Files.writeString(f, """
                main() {
                    println("plain")
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("native-plain"), Target.NATIVE);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        assertTrue(r.diagnostics().getDiagnostics().stream().noneMatch(d -> "UI001".equals(d.code())),
                "no kof.ui calls — no UI001");
    }
}
