package dev.kof.compiler;

import dev.kof.compiler.nat.NativeToolchainGate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI001 residual (#683): {@code kof.ui} era no-op silencioso no alvo Native.
 * O passe {@link UiTargetDiagnostics} emite UM WARNING aditivo (nunca quebra o
 * build) quando o alvo é Native e a IR usa {@code kof.ui}, apontando para
 * {@code --target=js}. Espelha o UI002 (Script) no plano de compilação.
 *
 * <p>Prova: unit sobre IR sintética + JVM-negativo + Native-positivo (gated no
 * toolchain as/ld) + não-UI-negativo. Fonte: UI é KofJS — não renderiza em
 * JVM/Native (paridade documentada).
 */
class Ui001NativeWarnTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String UI_PROGRAM = """
            main() {
                var store = Store(1)
                store.subscribe((v: Int) -> {})
                println("ok")
            }
            """;

    private static final String PLAIN_PROGRAM = """
            main() {
                println("ok")
            }
            """;

    @Test
    void syntheticIrWithUiCallIsDetected() {
        assertTrue(UiTargetDiagnostics.usesUi(moduleWith(uiCall())),
                "um KofCall do pacote kof.ui deve ser detectado");
    }

    @Test
    void syntheticIrWithoutUiCallIsNotDetected() {
        assertFalse(UiTargetDiagnostics.usesUi(moduleWith(
                        new KofCall(new Type.ClassType("kof.io", "File", List.of()),
                                "kof_file_read", List.of(BuiltinTypes.STRING),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION))),
                "KofCall de outro pacote não pode disparar o UI001");
    }

    @Test
    void uiOnJvmHasNoUi001Warning(@TempDir Path temp) throws IOException {
        CompilationResult result = compile(UI_PROGRAM, temp, Target.JVM);
        assertTrue(result.success(), "JVM compile: " + result.diagnostics().getDiagnostics());
        assertEquals(0, countUi001(result),
                "kof.ui no JVM é paridade documentada — sem UI001: "
                        + result.diagnostics().getDiagnostics());
    }

    @Test
    void uiOnNativeWarnsOnce(@TempDir Path temp) throws IOException {
        Assumptions.assumeTrue(isLinux(), "Native target runs on Linux");
        Assumptions.assumeTrue(NativeToolchainGate.present(), "as/ld ausentes — Native x86 depende do toolchain");
        CompilationResult result = compile(UI_PROGRAM, temp, Target.NATIVE);
        assertTrue(result.success(), "Native compile (o warning é aditivo): "
                + result.diagnostics().getDiagnostics());
        assertEquals(1, countUi001(result),
                "esperado exatamente UM UI001 no Native: " + result.diagnostics().getDiagnostics());
        Diagnostic warn = result.diagnostics().getDiagnostics().stream()
                .filter(d -> "UI001".equals(d.code())).findFirst().orElseThrow();
        assertEquals(Diagnostic.Severity.WARNING, warn.severity(), "UI001 deve ser WARNING, não erro");
        assertTrue(warn.message().contains("--target=js"),
                "o UI001 deve apontar para --target=js: " + warn.message());
    }

    @Test
    void plainProgramOnNativeHasNoUi001(@TempDir Path temp) throws IOException {
        Assumptions.assumeTrue(isLinux(), "Native target runs on Linux");
        Assumptions.assumeTrue(NativeToolchainGate.present(), "as/ld ausentes — Native x86 depende do toolchain");
        CompilationResult result = compile(PLAIN_PROGRAM, temp, Target.NATIVE);
        assertTrue(result.success(), "Native compile: " + result.diagnostics().getDiagnostics());
        assertEquals(0, countUi001(result),
                "programa sem kof.ui não pode disparar UI001: " + result.diagnostics().getDiagnostics());
    }

    private CompilationResult compile(String program, Path dir, Target target) throws IOException {
        Path source = dir.resolve("Main.kf");
        Files.createDirectories(dir);
        Files.writeString(source, program);
        return driver.compile(source, dir.resolve("out"), target);
    }

    private static long countUi001(CompilationResult result) {
        return result.diagnostics().getDiagnostics().stream()
                .filter(d -> "UI001".equals(d.code())).count();
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static KofCall uiCall() {
        return new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_store_new", List.of(Type.PrimitiveType.INT),
                Type.PrimitiveType.INT, KofCallKind.FUNCTION);
    }

    private static IRModule moduleWith(KofCall call) {
        IRMethod method = new IRMethod("main", Type.PrimitiveType.VOID, List.of(), 0,
                List.of(), List.of(new IRBasicBlock(0, List.of(call))), List.of());
        IRClass cls = new IRClass("Default/Main", "java/lang/Object", List.of(), 0,
                List.of(), List.of(method), List.of(), null, 0);
        return new IRModule("Default", List.of(cls), List.of());
    }
}
