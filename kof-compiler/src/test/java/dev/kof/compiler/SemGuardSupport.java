package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared SEM102 diagnostic guard (#977/#490/#617 family): the invariants every
 * unknown-member case pins — the compile must FAIL, the diagnostic must name
 * SEM102 and must name the missing member. Test-specific extras (the no-crash
 * face) stay with their test; no assertion is weakened by the consolidation.
 */
final class SemGuardSupport {

    private SemGuardSupport() {
    }

    static void expectSem102(CompilationResult r, String member) {
        assertFalse(r.success(), "unknown member on a builtin/primitive must fail to compile");
        String diags = "" + r.diagnostics().getDiagnostics();
        assertTrue(diags.contains("SEM102"), "expected SEM102, got: " + diags);
        assertTrue(diags.contains(member), "diagnostic must name the member: " + diags);
    }
}
