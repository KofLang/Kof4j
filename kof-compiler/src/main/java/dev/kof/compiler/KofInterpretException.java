package dev.kof.compiler;

import java.util.List;

/**
 * Falha de preparação para interpretação (diagnósticos do frontend).
 * Carrega os diagnósticos para o chamador reportar sem perder a
 * localização (R6: nunca silencioso).
 */
public class KofInterpretException extends RuntimeException {
    private final DiagnosticCollector diagnostics;

    KofInterpretException(DiagnosticCollector diagnostics) {
        super("interpretation failed: " + summarize(diagnostics));
        this.diagnostics = diagnostics;
    }

    public List<Diagnostic> errorDiagnostics() {
        return diagnostics.getDiagnostics().stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
    }

    public DiagnosticCollector diagnostics() {
        return diagnostics;
    }

    private static String summarize(DiagnosticCollector dc) {
        List<Diagnostic> errs = dc.getDiagnostics().stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
        if (errs.isEmpty()) return "unknown";
        Diagnostic first = errs.get(0);
        return first.code() + ": " + first.message()
                + (errs.size() > 1 ? " (+" + (errs.size() - 1) + " more)" : "");
    }
}
