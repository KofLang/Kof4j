package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 2 (plataforma): TargetMatrix — fonte única de topologia
 * backend×frontend. Gates de capacidade por API ficam nos Kof*.java.
 */
class TargetMatrixTest {

    @Test
    void backendRoles() {
        assertTrue(TargetMatrix.isBackend(Target.JVM));
        assertTrue(TargetMatrix.isBackend(Target.NATIVE));
        assertTrue(TargetMatrix.isBackend(Target.NATIVE_RISCV64));
        assertTrue(TargetMatrix.isBackend(Target.SCRIPT));
        assertFalse(TargetMatrix.isBackend(Target.JS), "JS é frontend");
        assertFalse(TargetMatrix.isBackend(Target.ANDROID), "Android é app inteira");
    }

    @Test
    void frontendRoles() {
        assertTrue(TargetMatrix.isFrontend(Target.JS));
        assertTrue(TargetMatrix.isFrontend(Target.SCRIPT));
        assertFalse(TargetMatrix.isFrontend(Target.JVM), "JVM é backend");
        assertFalse(TargetMatrix.isFrontend(Target.NATIVE));
    }

    @Test
    void scriptIsWildcard() {
        assertTrue(TargetMatrix.isBackend(Target.SCRIPT));
        assertTrue(TargetMatrix.isFrontend(Target.SCRIPT));
    }

    @Test
    void validCombinationsPass() {
        assertNull(TargetMatrix.validate(Target.JVM, Target.JS));
        assertNull(TargetMatrix.validate(Target.NATIVE, Target.SCRIPT));
        assertNull(TargetMatrix.validate(Target.SCRIPT, Target.SCRIPT));
        assertNull(TargetMatrix.validate(null, Target.JS), "só frontend");
        assertNull(TargetMatrix.validate(Target.JVM, null), "só backend");
    }

    @Test
    void invalidCombinationsDiagnose() {
        assertNotNull(TargetMatrix.validate(Target.JS, Target.JVM),
                "JVM não é backend? JVM É backend — inverter: JS não é backend");
        assertNotNull(TargetMatrix.validate(Target.JS, Target.JS), "JS não é backend");
        assertNotNull(TargetMatrix.validate(Target.JVM, Target.JVM), "JVM não é frontend");
    }

    @Test
    void wasmIsHonestGap() {
        // 15.1 (07/10, `D-WEB-WASI-DEFAULT-0710`, #776): "wasm"/"wasi" agora
        // SAO targets da topologia — o gap deles e de emissao (validate/
        // compile nomeia WASM001), nao de existencia. Os aliases longos
        // seguem gap de string honesto.
        assertNull(TargetMatrix.frontendGapFor("wasm"), "wasm e target real desde 15.1");
        assertNull(TargetMatrix.frontendGapFor("wasi"), "wasi e target real desde 15.1");
        assertEquals("WASM001", TargetMatrix.frontendGapFor("KofWebAssembly"));
        assertEquals("WASM001", TargetMatrix.frontendGapFor("webassembly"));
        assertNull(TargetMatrix.frontendGapFor("kofjs"), "kofjs existe");
        assertNull(TargetMatrix.frontendGapFor("script"), "script existe");
    }

    // #776 / D-WEB-WASI-DEFAULT-0710: WASI is the ordered DEFAULT web/desktop
    // frontend, so the `wasi`/`wasm32-wasi` spellings are KNOWN planned targets
    // (WASM001 honest gap), never the generic "unknown target" (R6). RED pre-fix
    // (`frontendGapFor("wasi")` returned null → unknown).
    @Test
    void wasiSpellingsAreHonestGap() {
        // 15.1-COMPLETE (07/10, lane 192.168.15.101:9092): the bare spellings
        // "wasi"/"wasm" are now REAL targets (enum + parse) — their honest
        // refusal moved to validate()/compile (WasmTargetGateE2ETest). The
        // LONG solecisms keep the named string gap (lane .30 intent kept).
        for (String name : java.util.List.of(
                "wasi-preview1", "wasip1", "kofwasi",
                "wasm32", "wasm32-wasi")) {
            assertEquals("WASM001", TargetMatrix.frontendGapFor(name), name);
        }
        java.util.List<String> errs = new java.util.ArrayList<>();
        assertNull(TargetMatrix.parse("wasip1", errs), "wasip1 ainda é gap de string → null");
        assertEquals(1, errs.size());
        assertTrue(errs.get(0).contains("WASM001"), errs.get(0));
        assertTrue(errs.get(0).contains("#776"), errs.get(0));
        assertEquals(Target.WASI, TargetMatrix.parse("wasi", new java.util.ArrayList<>()));
        assertEquals(Target.WASM, TargetMatrix.parse("wasm", new java.util.ArrayList<>()));
    }

    @Test
    void wasmGapMessagePointsToRealPlanPath() {
        // R6: diagnóstico honesto com referência CORRETA — o plano de origem
        // (PLATFORM-PLAN) foi consolidado em docs/development/DECISIONS.md
        // §D-PLATFORM 13/09 (caminho morto = mensagem mentirosa para o usuário).
        java.util.List<String> errs = new java.util.ArrayList<>();
        Target t = TargetMatrix.parse("kofwasm", errs);
        assertNull(t, "wasm não existe → null");
        assertEquals(1, errs.size());
        String msg = errs.get(0);
        assertTrue(msg.contains("WASM001"), msg);
        assertTrue(msg.contains("docs/development/DECISIONS.md"),
                "referência do plano deve apontar para o arquivo real: " + msg);
        // caminho relativo ao repo root (cwd do teste é o módulo)
        java.util.List<String> candidates = java.util.List.of(
                "docs/development/DECISIONS.md");
        java.nio.file.Path p = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        java.nio.file.Path plan = null;
        for (int i = 0; i < 6 && p != null && plan == null; i++, p = p.getParent()) {
            for (String c : candidates) {
                java.nio.file.Path cand = p.resolve(c);
                if (java.nio.file.Files.exists(cand)) { plan = cand; break; }
            }
        }
        assertTrue(plan != null, "plano citado na mensagem deve existir no repo");
    }

    @Test
    void namesAreCanonical() {
        assertEquals("jvm", TargetMatrix.name(Target.JVM));
        assertEquals("kofjs", TargetMatrix.name(Target.JS));
        assertEquals("script", TargetMatrix.name(Target.SCRIPT));
        assertEquals("native.riscv64", TargetMatrix.name(Target.NATIVE_RISCV64));
    }

    @Test
    void combinationsMatrixIsCoherent() {
        // toda combinação listada deve validar
        for (String combo : TargetMatrix.supportedCombinations()) {
            String[] parts = combo.split("\\+");
            Target b = parse(parts[0]);
            Target f = parse(parts[1]);
            assertNull(TargetMatrix.validate(b, f), combo + " deveria ser válida");
        }
    }

    private static Target parse(String s) {
        return switch (s) {
            case "jvm" -> Target.JVM;
            case "native" -> Target.NATIVE;
            case "native.riscv64" -> Target.NATIVE_RISCV64;
            case "native.aarch64" -> Target.NATIVE_AARCH64;
            case "kofjs" -> Target.JS;
            case "script" -> Target.SCRIPT;
            default -> throw new IllegalArgumentException(s);
        };
    }
}
