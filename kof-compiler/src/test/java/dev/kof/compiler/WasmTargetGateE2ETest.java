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
 * parse + nomes + validação da matriz) com backend de emissao limitado ao
 * subset escalar (unidade 15.2): programas fora do subset recusam
 * {@code WASM002}, e {@code WASI} ainda {@code WASM001} ate a unidade
 * 15.3 — NUNCA fallback silencioso (R6/Q7). O
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
    void matrixTreatsWasmAndWasiAsBackendsNeitherFrontendYet() {
        // 15.2 pousou: WASM É backend de emissao (subset escalar, WasmScalarE2ETest).
        // 15.3 fatia 1 pousou: WASI É backend WASI-preview1 (_start + fd_write,
        // WasmWasiE2ETest). Frontend/deploy/flip de padrao seguem na unidade 15.4.
        assertTrue(TargetMatrix.isBackend(Target.WASM), "wasm emite desde 15.2");
        assertFalse(TargetMatrix.isFrontend(Target.WASM));
        assertTrue(TargetMatrix.isBackend(Target.WASI), "wasi emite desde 15.3 fatia 1");
        assertFalse(TargetMatrix.isFrontend(Target.WASI), "flip de frontend e 15.4");
        assertNull(TargetMatrix.validate(Target.WASM, null), "backend wasm aceito");
        assertNull(TargetMatrix.validate(Target.WASI, null), "backend wasi aceito");
        String fe = TargetMatrix.validate(null, Target.WASI);
        assertTrue(fe != null && fe.contains("WASM001"), "frontend wasi recusa honesta: " + fe);
        String feWasm = TargetMatrix.validate(null, Target.WASM);
        assertTrue(feWasm != null && feWasm.contains("WASM001"), "frontend wasm ainda nao: " + feWasm);
    }

    @Test
    void outOfSubsetProgramsRefuseHonestlyWithNoArtifacts(@TempDir Path dir) throws IOException {
        // 15.3: WASI emite a fatia escalar (SUCESSO abaixo); fora da fatia
        // (strings/args) -> WASM002 nomeando plano/unidade, SEM artefatos (Q7/R6).
        {
            Path src = dir.resolve("GateWasiInSlice.kf");
            Files.writeString(src, SRC);
            Path out = dir.resolve("out-wasi-ok");
            CompilationResult ok = driver.compile(src, out, Target.WASI);
            assertTrue(ok.success(), "WASI emite a fatia 1 (println escalar): "
                    + ok.diagnostics().getDiagnostics());
            Path bin = out.resolve("Default").resolve("Main.wasm");
            assertTrue(Files.exists(bin), "WASI emits the module");
            String raw = new String(Files.readAllBytes(bin), StandardCharsets.ISO_8859_1);
            assertTrue(raw.contains("wasi_snapshot_preview1") && raw.contains("fd_write")
                            && raw.contains("_start"),
                    "WASI module has preview1 imports and _start export");
        }
        {
            Path src = dir.resolve("GateWasiStrings.kf");
            Files.writeString(src, "main(String[] args) { println(args) }\n");
            Path out = dir.resolve("out-wasi-refuse");
            CompilationResult r = driver.compile(src, out, Target.WASI);
            assertFalse(r.success(), "println de args esta fora da fatia 15.3b (WASM002)");
            String diags = r.diagnostics().getDiagnostics().toString();
            for (String mark : new String[] {"WASM002", "#776", "wasm-wasi-plan"}) {
                assertTrue(diags.contains(mark), "WASI diagnostic must name " + mark + ": " + diags);
            }
            assertFalse(Files.exists(out.resolve("Default")),
                    "WASI refusal emits NO artifacts (never a silent half-backend)");
        }
        {
            Path src = dir.resolve("GateWasm.kf");
            Files.writeString(src, "Int echo(Int x) { println(x); return x }\n");
            Path out = dir.resolve("out-wasm");
            CompilationResult r = driver.compile(src, out, Target.WASM);
            assertFalse(r.success(), "echo chama println: fora do subset 15.2");
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("WASM002") && diags.contains("#776")
                    && diags.contains("wasm-wasi-plan"), "WASM002 honest: " + diags);
            assertFalse(Files.exists(out.resolve("Default")),
                    "WASM002 refusal emits NO artifacts");
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
