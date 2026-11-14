package dev.kof.browser;

import java.util.List;

/**
 * Fase 8 (testing-platform plan): the page diagnostics — the console output
 * and the uncaught JS errors collected during the load (the actionable answer
 * to "why did this test fail").
 */
public record PageDiagnostics(List<String> console, List<String> errors) {

    /** The first error message, or "" when the page ran clean. */
    public String firstError() {
        return errors.isEmpty() ? "" : errors.get(0);
    }
}
