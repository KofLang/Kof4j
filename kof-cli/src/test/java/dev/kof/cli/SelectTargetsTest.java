package dev.kof.cli;

import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F2-parte-4 (plataforma): seleção backend×frontend via
 * {@code --backend/--frontend} com override do kof.toml. Prioridade:
 * flag > kof.toml > default(null). Combinação inválida → IllegalArgumentException
 * com mensagem legível (R6: erro honesto, validado ANTES de compilar).
 */
class SelectTargetsTest {

    private static Path writeKofToml(Path dir, String content) throws IOException {
        Path f = dir.resolve("kof.toml");
        Files.writeString(f, content);
        return dir;
    }

    @Test
    void noFlagNoManifest_yieldsNulls(@TempDir Path dir) {
        KofCliSupport.Targets t = KofCliSupport.selectTargets(null, null, dir);
        assertNull(t.backend());
        assertNull(t.frontend());
    }

    @Test
    void flagsOnly(@TempDir Path dir) {
        KofCliSupport.Targets t = KofCliSupport.selectTargets("jvm", "kofjs", dir);
        assertEquals(Target.JVM, t.backend());
        assertEquals(Target.JS, t.frontend());
    }

    @Test
    void tomlOnly(@TempDir Path dir) throws IOException {
        writeKofToml(dir, "[backend]\ntarget = \"native.riscv64\"\n[frontend]\ntarget = \"script\"\n");
        KofCliSupport.Targets t = KofCliSupport.selectTargets(null, null, dir);
        assertEquals(Target.NATIVE_RISCV64, t.backend());
        assertEquals(Target.SCRIPT, t.frontend());
    }

    @Test
    void flagOverridesToml(@TempDir Path dir) throws IOException {
        writeKofToml(dir, "[backend]\ntarget = \"native\"\n[frontend]\ntarget = \"kofjs\"\n");
        KofCliSupport.Targets t = KofCliSupport.selectTargets("jvm", null, dir);
        assertEquals(Target.JVM, t.backend(), "flag --backend sobrepõe [backend] do kof.toml");
        assertEquals(Target.JS, t.frontend(), "frontend sem flag vem do kof.toml");
    }

    @Test
    void scriptBothSides(@TempDir Path dir) throws IOException {
        writeKofToml(dir, "[backend]\ntarget = \"script\"\n[frontend]\ntarget = \"script\"\n");
        KofCliSupport.Targets t = KofCliSupport.selectTargets(null, null, dir);
        assertEquals(Target.SCRIPT, t.backend());
        assertEquals(Target.SCRIPT, t.frontend());
    }

    @Test
    void jsCannotBeBackend() {
        // R6: JS não é backend na matriz → erro honesto, não fallback silencioso
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> KofCliSupport.selectTargets("js", "kofjs", null));
        assertTrue(e.getMessage().contains("backend"), e.getMessage());
    }

    @Test
    void wasmFrontendIsHonestGap() {
        // WASM ainda não existe (Fase 6) → rejeitado com mensagem, nunca silencioso
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> KofCliSupport.selectTargets("jvm", "wasm", null));
        assertTrue(e.getMessage().contains("WASM001"), e.getMessage());
    }

    @Test
    void unknownTargetRejected(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> KofCliSupport.selectTargets("not-a-target", null, dir));
    }

    @Test
    void legacyTargetFlagSharesWasmHonestGap() {
        // --target=wasm (legado) deve explicar o WASM001/Fase 6 como
        // --frontend=wasm — nunca "unknown target" genérico (R6).
        var msgs = KofCliSupport.unknownTargetMessages("wasm");
        assertEquals(1, msgs.size());
        assertTrue(msgs.get(0).contains("WASM001"), msgs.get(0));
        assertTrue(msgs.get(0).contains("docs/development/future/PLATFORM-PLAN.md"),
                "mensagem deve apontar para o caminho REAL do plano: " + msgs.get(0));
        // alvo válido → sem diagnóstico; lixo → unknown genérico
        assertTrue(KofCliSupport.unknownTargetMessages("native").isEmpty());
        assertTrue(KofCliSupport.unknownTargetMessages("nada").toString().contains("unknown target"));
    }
}
