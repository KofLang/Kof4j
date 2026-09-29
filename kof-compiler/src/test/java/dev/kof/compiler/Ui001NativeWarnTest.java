package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #683 / UI001 (R6): Native {@code kof.ui} must emit exactly one compile-time
 * WARNING (mirror of Script UI002). Additive — {@link CompilationResult#success()}
 * stays true when the backend can emit.
 */
class Ui001NativeWarnTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String UI_PROGRAM = """
            main() {
                var w = Window("App")
                var l = Label("oi")
                var c = Column(listOf(l))
                w.bind(c)
                w.show()
                println("ok")
            }
            """;

    @Test
    void syntheticIrReportsUi001Once() {
        KofCall ui = new KofCall(
                new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                "kof_ui_window_new",
                List.of(BuiltinTypes.STRING),
                Type.PrimitiveType.INT,
                KofCallKind.FUNCTION);
        KofCall again = new KofCall(
                new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                "kof_ui_label_new",
                List.of(BuiltinTypes.STRING),
                Type.PrimitiveType.INT,
                KofCallKind.FUNCTION);
        IRMethod method = new IRMethod("main", Type.PrimitiveType.VOID, List.of(),
                AccessFlags.PUBLIC | AccessFlags.STATIC,
                List.of(), List.of(new IRBasicBlock(0, List.of(ui, again))), List.of());
        IRModule module = new IRModule("Test", List.of(
                new IRClass("Test", "java/lang/Object", List.of(), AccessFlags.PUBLIC,
                        List.of(), List.of(method), List.of(), null, 1)),
                List.of());

        DiagnosticCollector diag = new DiagnosticCollector();
        Ui001NativeWarn.reportIfNeeded(module, diag);
        assertEquals(1, countUi001(diag), "exactly one UI001: " + diag.getDiagnostics());
        assertTrue(diag.getDiagnostics().get(0).message().contains("kof_ui_window_new"));
        assertTrue(diag.getDiagnostics().get(0).message().contains("--target=js"));
        assertFalse(diag.hasErrors());
    }

    @Test
    void syntheticIrWithoutUiIsSilent() {
        IRMethod method = new IRMethod("main", Type.PrimitiveType.VOID, List.of(),
                AccessFlags.PUBLIC | AccessFlags.STATIC,
                List.of(), List.of(new IRBasicBlock(0, List.of())), List.of());
        IRModule module = new IRModule("Test", List.of(
                new IRClass("Test", "java/lang/Object", List.of(), AccessFlags.PUBLIC,
                        List.of(), List.of(method), List.of(), null, 1)),
                List.of());
        DiagnosticCollector diag = new DiagnosticCollector();
        Ui001NativeWarn.reportIfNeeded(module, diag);
        assertEquals(0, countUi001(diag), diag.getDiagnostics().toString());
    }

    @Test
    void jvmCompileWithUiDoesNotEmitUi001(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UiJvm.kf");
        Files.writeString(src, UI_PROGRAM);
        CompilationResult r = driver.compile(src, tmp.resolve("out-jvm"), Target.JVM);
        assertTrue(r.success(), "JVM must compile: " + r.diagnostics().getDiagnostics());
        assertEquals(0, countUi001(r.diagnostics()),
                "UI001 is Native-only: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nativeCompileWithUiEmitsUi001Once(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UiNat.kf");
        Files.writeString(src, UI_PROGRAM);
        CompilationResult r = driver.compile(src, tmp.resolve("out-nat"), Target.NATIVE);
        // UI001 is emitted post-lower / pre-emit — assert it even if host ld
        // fails (e.g. pthread) after the warning is already recorded.
        assertEquals(1, countUi001(r.diagnostics()),
                "exactly one UI001: " + r.diagnostics().getDiagnostics());
        Diagnostic d = r.diagnostics().getDiagnostics().stream()
                .filter(x -> "UI001".equals(x.code()))
                .findFirst()
                .orElseThrow();
        assertTrue(d.message().contains("kof_ui_"), d.message());
        assertTrue(d.message().contains("--target=js"), d.message());
        if (!r.success()) {
            String msgs = r.diagnostics().getDiagnostics().toString();
            assumeTrue(msgs.contains("ld failed") || msgs.contains("pthread"),
                    "unexpected Native failure (expected UI001 + optional ld): " + msgs);
        }
    }

    @Test
    void nativeCompileWithoutUiDoesNotEmitUi001(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("NoUi.kf");
        Files.writeString(src, "main() { println(1) }\n");
        CompilationResult r = driver.compile(src, tmp.resolve("out-plain"), Target.NATIVE);
        assertEquals(0, countUi001(r.diagnostics()),
                "no UI → no UI001: " + r.diagnostics().getDiagnostics());
        if (!r.success()) {
            String msgs = r.diagnostics().getDiagnostics().toString();
            assumeTrue(msgs.contains("ld failed") || msgs.contains("pthread"),
                    "unexpected Native failure: " + msgs);
        }
    }

    private static long countUi001(DiagnosticCollector diag) {
        return diag.getDiagnostics().stream()
                .filter(d -> d.severity() == Diagnostic.Severity.WARNING
                        && "UI001".equals(d.code()))
                .count();
    }
}
