package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Auditoria de paridade (frente de revisão, 21/09) — trava a invariante R6:
 * um caminho não-suportado num alvo carrega um gap code honesto, nunca cai em
 * silêncio nem no link quebrado. O golden é MEDIDO do código (Q3), não de
 * memória; espelha {@code docs/bugs-and-gaps/uncatalogued-stubs-audit.md}.
 *
 * <p>Escopo: os namespaces com gate real + os always-true que a varredura
 * mediu. {@code KofStd} (delegador) e {@code KofMedia} (sem
 * {@code supportedOn}) ficam fora; um gate novo num namespace always-true
 * quebra aqui de propósito (a matriz é lei e tem de ser atualizada junto).
 */
class StdParityGapAuditTest {

    private static Set<Target> unsupported(Predicate<Target> supported) {
        var s = new LinkedHashSet<Target>();
        for (var t : Target.values()) {
            // NATIVE_RISCV32 / NATIVE_MCU_ARM (B-4 MCU slices) are deliberately
            // OUTSIDE the stdlib parity matrix: minimal RV32I / Cortex-M Thumb-2
            // codegen slices whose every non-slice path is rejected at emit time
            // with a clean NATIVE002 diagnostic (never a silent gate). They are
            // not among the six stdlib-parity targets, so they are excluded here
            // rather than pretending a stdlib surface they do not have.
            if (t == Target.NATIVE_RISCV32 || t == Target.NATIVE_MCU_ARM) continue;
            if (!supported.test(t)) {
                s.add(t);
            }
        }
        return s;
    }

    @Test
    @DisplayName("buffer: gate JVM+JS (D-R3-BUFFER) + FFI001 nos demais")
    void bufferGatesToJvmWithFfiCodes() {
        // R57/R58: the `kof.buffer` namespace + Buffer(U8) INOUT bind on the JS
        // target too (KofBuffer.supportedOn = JVM||JS) — JS is no longer gated.
        assertEquals(Set.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64,
                Target.ANDROID, Target.SCRIPT), unsupported(KofBuffer::supportedOn));
        assertTrue(KofBuffer.supportedOn(Target.JS));
        assertEquals("FFI001", KofBuffer.gapCode(Target.NATIVE));
    }

    @Test
    @DisplayName("db: só SCRIPT é gated (DB001)")
    void dbGatesOnlyScript() {
        assertEquals(Set.of(Target.SCRIPT), unsupported(KofDb::supportedOn));
        assertEquals("DB001", KofDb.gapCode());
    }

    @Test
    @DisplayName("log: SCRIPT + ANDROID gated (LOG001)")
    void logGatesScriptAndAndroid() {
        assertEquals(Set.of(Target.ANDROID, Target.SCRIPT), unsupported(KofLog::supportedOn));
        assertEquals("LOG001", KofLog.gapCode());
    }

    @Test
    @DisplayName("orm: 3 nativos + SCRIPT gated (ORM001)")
    void ormGatesNativesAndScript() {
        assertEquals(Set.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64,
                Target.SCRIPT), unsupported(KofOrm::supportedOn));
        assertEquals("ORM001", KofOrm.gapCode());
    }

    @Test
    @DisplayName("rng: cross + ANDROID + SCRIPT gated (RNG001)")
    void rngGatesCrossAndroidScript() {
        assertEquals(Set.of(Target.NATIVE_RISCV64, Target.NATIVE_AARCH64,
                Target.ANDROID, Target.SCRIPT),
                unsupported(t -> KofRng.supportedOn("kof_rng_int", t)));
        assertEquals("RNG001", KofRng.gapCode("kof_rng_int"));
    }

    @Test
    @DisplayName("gpu: paridade 4 alvos (D-FULL-PARITY-050 row 6) — GPU001 deixou de ser emitido")
    void gpuUngatedOnAllTargets() {
        // Antes: JS+SCRIPT gated por GPU001. Agora o nativo/cross/x86 tem o
        // fallback real e o JS/Script degradam pelo runtime (JsRuntimeGpuSupport
        // /interpretador): available=false, dispatch -1/-6 — nunca GPU001.
        assertTrue(unsupported(KofGpu::supportedOn).isEmpty(),
                "gpu deve ser aceito em todos os alvos (row 6)");
    }

    @Test
    @DisplayName("tetris: JVM-only (EGG001)")
    void tetrisGatesToJvm() {
        assertEquals(Set.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64,
                Target.JS, Target.ANDROID, Target.SCRIPT), unsupported(KofTetris::supportedOn));
        assertEquals("EGG001", KofTetris.gapCode());
    }

    @Test
    @DisplayName("scheduler: SCRIPT gated (SCHED001) + at() nativo gated (CRON001)")
    void schedulerGates() {
        assertEquals(Set.of(Target.SCRIPT),
                unsupported(t -> KofScheduler.supportedOn("kof_scheduler_every", t)));
        assertFalse(KofScheduler.supportedOn("kof_scheduler_at", Target.NATIVE));
        assertFalse(KofScheduler.supportedOn("kof_scheduler_at", Target.NATIVE_RISCV64));
        assertTrue(KofScheduler.supportedOn("kof_scheduler_at", Target.JVM));
        assertEquals("CRON001", KofScheduler.gapCode("kof_scheduler_at"));
        assertEquals("SCHED001", KofScheduler.gapCode("kof_scheduler_every"));
    }

    @Test
    @DisplayName("math.pow: cross riscv/aarch gated (MATH001)")
    void mathPowGatesCross() {
        assertTrue(KofMath.supportedOn("kof_math_pow", Target.JVM));
        assertEquals(Set.of(Target.NATIVE_RISCV64, Target.NATIVE_AARCH64),
                unsupported(t -> KofMath.supportedOn("kof_math_pow", t)));
        assertEquals("MATH001", KofMath.gapCode("kof_math_pow"));
    }

    @Test
    @DisplayName("observability.export_spans: 3 nativos gated (OBS003)")
    void observabilityExportSpansGatedOnNative() {
        assertEquals(Set.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64),
                unsupported(t -> KofObservability.supportedOn("kof_observability_export_spans", t)));
        assertEquals("OBS003", KofObservability.gapCode("kof_observability_export_spans"));
    }

    @Test
    @DisplayName("time.tzOffsetSeconds: 3 nativos gated (TIME003)")
    void timeTzOffsetGatedOnNative() {
        assertEquals(Set.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64),
                unsupported(t -> KofTime.supportedOn("tzOffsetSeconds", t)));
        assertEquals("TIME003", KofTime.gapCode("tzOffsetSeconds"));
    }

    @Test
    @DisplayName("security.sha512: riscv/aarch + SCRIPT gated (SECN003); ANDROID segue o JVM (§278)")
    void securitySha512GatedOnCross() {
        assertEquals(Set.of(Target.NATIVE_RISCV64, Target.NATIVE_AARCH64, Target.SCRIPT),
                unsupported(t -> KofSecurity.supportedOn("kof_sec_sha512", t)));
        assertEquals("SECN003", KofSecurity.gapCode("kof_sec_sha512"));
    }

    @Test
    @DisplayName("security por função: chacha/cookie (JVM+JS) e auth/resource-server (JVM-only) — ANDROID = JVM (§278)")
    void securityPerFunctionGates() {
        var jvmJsOnly = Set.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64,
                Target.SCRIPT);
        var jvmOnly = Set.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64,
                Target.JS, Target.SCRIPT);
        assertEquals(jvmJsOnly,
                unsupported(t -> KofSecurity.supportedOn("kof_sec_chacha20_encrypt", t)));
        assertEquals("SECN002", KofSecurity.gapCode("kof_sec_chacha20_encrypt"));
        assertEquals(jvmJsOnly,
                unsupported(t -> KofSecurity.supportedOn("kof_sec_cookie_set", t)));
        assertEquals("SECN006", KofSecurity.gapCode("kof_sec_cookie_set"));
        assertEquals(jvmOnly,
                unsupported(t -> KofSecurity.supportedOn("kof_sec_auth_token", t)));
        assertEquals("SECN000", KofSecurity.gapCode("kof_sec_auth_token"));
        assertEquals(jvmOnly,
                unsupported(t -> KofSecurity.supportedOn("kof_sec_auth_resource_server", t)));
        assertEquals("SECN007", KofSecurity.gapCode("kof_sec_auth_resource_server"));
    }

    @Test
    @DisplayName("time.addDays/diffDays: TIME002 fechado — sem gate em nenhum alvo")
    void timeAddDaysDiffDaysUngated() {
        assertTrue(unsupported(t -> KofTime.supportedOn("addDays", t)).isEmpty());
        assertTrue(unsupported(t -> KofTime.supportedOn("diffDays", t)).isEmpty());
    }

    @Test
    @DisplayName("kof.config: real em todos os alvos (row 9 fechado 26/09)")
    void configUngatedOnAllTargets() {
        assertTrue(unsupported(t -> KofConfig.supportedOn(t)).isEmpty());
    }

    @Test
    @DisplayName("namespaces sem gate: supportedOn true em todo alvo")
    void alwaysTrueNamespacesHaveNoSilentGate() {
        assertTrue(unsupported(KofCache::supportedOn).isEmpty(), "cache");
        assertTrue(unsupported(KofHttp::supportedOn).isEmpty(), "http");
        assertTrue(unsupported(KofMq::supportedOn).isEmpty(), "mq");
        assertTrue(unsupported(t -> KofEncoding.supportedOn("x", t)).isEmpty(), "encoding");
        assertTrue(unsupported(t -> KofRandom.supportedOn("x", t)).isEmpty(), "random");
        assertTrue(unsupported(t -> KofNet.supportedOn("x", t)).isEmpty(), "net");
        assertTrue(unsupported(t -> KofStrings.supportedOn("x", t)).isEmpty(), "strings");
        assertTrue(unsupported(t -> KofUuid.supportedOn("x", t)).isEmpty(), "uuid");
        assertTrue(unsupported(t -> KofValidation.supportedOn("x", t)).isEmpty(), "validation");
    }
}
