package dev.kof.compiler;

import dev.kof.compiler.lang.PortuKofDiagnostics;

import java.util.List;
import java.util.Locale;

public record Diagnostic(Severity severity, String file, int line, int column, int length,
                         String message, String code, List<Object> args) {

    public enum Severity { ERROR, WARNING, NOTE, INFO }

    /** Construtor histórico (sem argumentos estruturados) — mantém `message`
     *  canônica em inglês; `args` vazio. */
    public Diagnostic(Severity severity, String file, int line, int column, int length,
                      String message, String code) {
        this(severity, file, line, column, length, message, code, List.of());
    }

    static Diagnostic error(String file, int line, int column, int length, String message,
                            String code) {
        return new Diagnostic(Severity.ERROR, file, line, column, length, message, code);
    }

    static Diagnostic error(String file, int line, int column, int length, String message,
                            String code, List<Object> args) {
        return new Diagnostic(Severity.ERROR, file, line, column, length, message, code, args);
    }

    static Diagnostic warning(String file, int line, int column, int length, String message,
                              String code) {
        return new Diagnostic(Severity.WARNING, file, line, column, length, message, code);
    }

    static Diagnostic warning(String file, int line, int column, int length, String message,
                              String code, List<Object> args) {
        return new Diagnostic(Severity.WARNING, file, line, column, length, message, code, args);
    }

    static Diagnostic note(String file, int line, int column, int length, String message,
                           String code) {
        return new Diagnostic(Severity.NOTE, file, line, column, length, message, code);
    }

    static Diagnostic note(String file, int line, int column, int length, String message,
                           String code, List<Object> args) {
        return new Diagnostic(Severity.NOTE, file, line, column, length, message, code, args);
    }

    static Diagnostic info(String file, int line, int column, int length, String message,
                           String code) {
        return new Diagnostic(Severity.INFO, file, line, column, length, message, code);
    }

    static Diagnostic info(String file, int line, int column, int length, String message,
                           String code, List<Object> args) {
        return new Diagnostic(Severity.INFO, file, line, column, length, message, code, args);
    }

    /** `message` é SEMPRE a forma canônica em inglês (prova de teste, LSP
     *  estruturado, `--json`, observabilidade, tooling). A localização NUNCA
     *  substitui este campo. */
    public String message() {
        return message;
    }

    /** Texto HUMANO conforme a superfície: `.ptkf` (extensão → `LanguageProfile`)
     *  renderiza PT-BR a partir do CATÁLOGO indexado pelo CÓDIGO canônico e dos
     *  argumentos estruturados capturados na emissão; `.kf` e qualquer código sem
     *  tradução permanecem em inglês. Detecção por EXTENSÃO, nunca por conteúdo. */
    public String localizedMessage() {
        String pt = PortuKofDiagnostics.localize(code, file, message, args);
        return pt != null ? pt : message;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(file).append(':').append(line).append(':').append(column).append(": ");
        sb.append(severity.name().toLowerCase(Locale.ROOT)).append(": ").append(localizedMessage());
        if (code != null && !code.isEmpty()) {
            sb.append(" [").append(code).append(']');
        }
        return sb.toString();
    }
}
