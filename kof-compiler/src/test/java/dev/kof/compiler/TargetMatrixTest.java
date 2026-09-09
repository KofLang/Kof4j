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
        assertEquals("WASM001", TargetMatrix.frontendGapFor("wasm"));
        assertEquals("WASM001", TargetMatrix.frontendGapFor("KofWebAssembly"));
        assertNull(TargetMatrix.frontendGapFor("kofjs"), "kofjs existe");
        assertNull(TargetMatrix.frontendGapFor("script"), "script existe");
    }

    @Test
    void wasmGapMessagePointsToRealPlanPath() {
        // R6: diagnóstico honesto com referência CORRETA — o plano vive em
        // docs/development/future/PLATFORM-PLAN.md (docs/future/ é caminho
        // morto pós-reorganização do repo).
        java.util.List<String> errs = new java.util.ArrayList<>();
        Target t = TargetMatrix.parse("kofwasm", errs);
        assertNull(t, "wasm não existe → null");
        assertEquals(1, errs.size());
        String msg = errs.get(0);
        assertTrue(msg.contains("WASM001"), msg);
        assertTrue(msg.contains("docs/development/future/PLATFORM-PLAN.md"),
                "referência do plano deve apontar para o arquivo real: " + msg);
        // caminho relativo ao repo root (cwd do teste é o módulo)
        java.nio.file.Path p = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        java.nio.file.Path plan = null;
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            java.nio.file.Path cand = p.resolve("docs/development/future/PLATFORM-PLAN.md");
            if (java.nio.file.Files.exists(cand)) { plan = cand; break; }
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
