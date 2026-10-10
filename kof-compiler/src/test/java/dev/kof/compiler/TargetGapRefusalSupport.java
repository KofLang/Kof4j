package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Harness do teste compartilhado de recusa nomeada por alvo (Fase 5 do
 * {@code test-architecture-plan}, {@code D-TEST-ARCHITECTURE-PHASES}): o mesmo
 * {@code @Test} — JS/Native/cross recusam a face com o codigo nomeado, nunca em
 * silencio (R6) — serve a familias que so mudam o programa, o codigo e o rotulo.
 * Antes cada classe declarava um {@code targetGapRefusal} identico; agora a
 * subclasse fornece os tres hooks e herda o teste.
 */
abstract class TargetGapRefusalSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    /** O programa Kof que exercita a face que deve ser recusada fora da JVM. */
    protected abstract String gapProgram();

    /** O codigo nomeado que a recusa deve citar (ex.: {@code SECN013}). */
    protected abstract String gapCode();

    /** O rotulo da face na mensagem de falha (ex.: {@code Ed25519 face}). */
    protected abstract String gapLabel();

    @Test
    @DisplayName("target gap is refused with the named code, never silently (R6)")
    void targetGapRefusal(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("gap.kf");
        Files.writeString(s, gapProgram());
        for (Target t : new Target[]{Target.JS, Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(s, dir.resolve("out-" + t), t);
            assertFalse(r.success(), t + " must refuse the " + gapLabel() + " for now");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains(gapCode()),
                    t + " must name " + gapCode() + ": " + r.diagnostics().getDiagnostics());
        }
    }
}
