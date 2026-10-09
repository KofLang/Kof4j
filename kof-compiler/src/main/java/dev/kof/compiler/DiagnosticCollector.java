package dev.kof.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiagnosticCollector {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    void report(Diagnostic d) {
        diagnostics.add(d);
    }

    private SourcePosition fallbackPosition;

    public SourcePosition fallbackPosition() {
        return fallbackPosition;
    }

    public void setFallbackPosition(SourcePosition position) {
        fallbackPosition = position;
    }

    public void error(String file, int line, int column, int length, String message, String code) {
        emitError(file, line, column, length, message, code, java.util.List.of());
    }

    /** D-PORTUKOF F6: emissão com ARGUMENTOS ESTRUTURADOS (a mensagem canônica
     *  em inglês continua idêntica; os args alimentam a renderização PT pelo
     *  código, sem regex). Aridade = placeholders do template do código. */
    public void error(String file, int line, int column, int length, String message, String code,
                      Object... args) {
        emitError(file, line, column, length, message, code, java.util.List.of(args));
    }

    private void emitError(String file, int line, int column, int length, String message,
                           String code, java.util.List<Object> args) {
        if (fallbackPosition != null && (file == null || file.isEmpty()) && line == 0 && column == 0) {
            SourcePosition p = fallbackPosition;
            report(Diagnostic.error(p.file() == null ? "" : p.file(), p.line(), p.column(), p.length(),
                    message, code, args));
            return;
        }
        report(Diagnostic.error(file, line, column, length, message, code, args));
    }

    public void error(AstNode node, String message, String code) {
        SourcePosition pos = node == null ? null : node.position();
        if (pos == null) {
            report(Diagnostic.error("", 0, 0, 0, message, code));
        } else {
            report(Diagnostic.error(pos.file() == null ? "" : pos.file(), pos.line(), pos.column(),
                    pos.length(), message, code));
        }
    }

    void warning(String file, int line, int column, int length, String message, String code) {
        report(Diagnostic.warning(file, line, column, length, message, code));
    }

    public void warning(AstNode node, String message, String code) {
        SourcePosition pos = node == null ? null : node.position();
        if (pos == null) {
            report(Diagnostic.warning("", 0, 0, 0, message, code));
        } else {
            report(Diagnostic.warning(pos.file() == null ? "" : pos.file(), pos.line(), pos.column(),
                    pos.length(), message, code));
        }
    }

    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    int errorCount() {
        return (int) diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).count();
    }

    String formatAll() {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics) {
            sb.append(d.format()).append('\n');
        }
        return sb.toString();
    }
}
