package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TIER 15 unidade 15.1 (07/10, mantainer order `D-WEB-WASI-DEFAULT-0710`,
 * issue #776): {@code WASM} e {@code WASI} entram na TOPOLOGIA (enum +
 * parse + nomes + validação da matriz) sem backend de emissão ainda — o
 * compile recusa com o gap honesto {@code WASM001} apontando o plano e a
 * unidade que implementa (15.2), NUNCA fallback silencioso (R6/Q7). O
 * flip de padrão (web/desktop → WASI) é a unidade 15.4 e só acontece com
 * paridade total; nada do que funciona hoje muda (as asserções de alvo
 * existentes cobrem isso).
 */
class WasmTargetGateE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int twice(Int x) { return x + x }

            main() { println(twice(21)) }
            """;

    private static final String[] GAP_MARKS = {"WASM001", "#776", "wasm-wasi-plan"};

    @Test
    void enumCarriesWasmAndWasi() {
        assertEquals(2, Target.values().length - 9,
                "topologia: JVM, NATIVE, NATIVE_RISCV64, NATIVE_AARCH64, NATIVE_RISCV32, "
                        + "NATIVE_MCU_ARM, JS, ANDROID, SCRIPT + WASM + WASI");
        assertFalse(Target.WASM.isNative(), "WASM não é nativo");
        assertFalse(Target.WASI.isScript(), "WASI não é script");
    }

    @Test
    void matrixNamesAndParsesTheCanonicalStrings() {
        assertEquals("wasm", TargetMatrix.name(Target.WASM));
        assertEquals("wasi", TargetMatrix.name(Target.WASI));
        assertEquals(Target.WASM, TargetMatrix.parse("wasm", null));
        assertEquals(Target.WASI, TargetMatrix.parse("wasi", null));
        // aliases longos continuam gap de string (o nome canônico é wasm/wasi)
        assertEquals("WASM001", TargetMatrix.frontendGapFor("webassembly"));
        assertEquals("WASM001", TargetMatrix.frontendGapFor("kofwasm"));
        assertNull(TargetMatrix.frontendGapFor("wasm"), "wasm agora É target");
        assertNull(TargetMatrix.frontendGapFor("wasi"), "wasi agora É target");
    }

    @Test
    void matrixStillRefusesWasmWasiAsBackendOrFrontend() {
        // 15.1 = topologia sem emissão. Nem backend nem frontend (ainda) — a
        // recusa NOMEA WASM001 (não é "unknown"), e a matriz é a fonte única.
        assertFalse(TargetMatrix.isBackend(Target.WASM));
        assertFalse(TargetMatrix.isFrontend(Target.WASM));
        assertFalse(TargetMatrix.isBackend(Target.WASI));
        assertFalse(TargetMatrix.isFrontend(Target.WASI));
        String be = TargetMatrix.validate(Target.WASM, null);
        assertTrue(be != null && be.contains("WASM001"), "backend wasm recusa honesta: " + be);
        String fe = TargetMatrix.validate(null, Target.WASI);
        assertTrue(fe != null && fe.contains("WASM001"), "frontend wasi recusa honesta: " + fe);
    }

    @Test
    void compileToWasmRefusesHonestlyNoArtifacts(@TempDir Path dir) throws IOException {
        for (Target t : new Target[]{Target.WASM, Target.WASI}) {
            Path src = dir.resolve("Gate-" + t.name() + ".kf");
            Files.writeString(src, SRC);
            Path out = dir.resolve("out-" + t.name());
            CompilationResult r = driver.compile(src, out, t);
            assertFalse(r.success(), t + " must refuse before the backend emits (15.2 pending)");
            String diags = r.diagnostics().getDiagnostics().toString();
            for (String mark : GAP_MARKS) {
                assertTrue(diags.contains(mark), t + " diagnostic must name " + mark + ": " + diags);
            }
            assertFalse(Files.exists(out.resolve("Default")),
                    t + ": refusal emits NO artifacts (never a silent half-backend)");
        }
    }

    @Test
    void existingTargetsCompileUnchanged(@TempDir Path dir) throws IOException {
        // zero-regression por construção: o mesmo fonte dos pins acima roda
        // no alvo real (JVM) e produz o golden — a topologia nova não tocou
        // os caminhos existentes.
        Path src = dir.resolve("GateJvm.kf");
        Files.writeString(src, SRC);
        CompilationResult r = driver.compile(src, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(r.success(), "JVM unchanged: " + r.diagnostics().getDiagnostics());
    }
}
