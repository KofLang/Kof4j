package dev.kof.compiler.jvm;

/**
 * Runtime JVM do tipo {@code InteropError} (D-INTEROP-ERR-TYPE, 02/10).
 * Nested class em {@code KofRuntime} (mesmo padrao do {@code Secret} e
 * {@code KeyHandle}). Estende {@code RuntimeException} para coexistir com o
 * contrato congelado de {@code catch (String)} (o String-catch do Kof captura
 * throwables do runtime e extrai {@code getMessage()}); o construtor prefixa
 * {@code INTEROP010: } para que o String-catch veja o codigo nomeado.
 */
public final class JvmInteropErrorRuntime {

    private JvmInteropErrorRuntime() {}

    static String source() {
        return """
                // ── kof.interop — InteropError builtin (D-INTEROP-ERR-TYPE, JVM) ──
                public static final class InteropError extends RuntimeException {
                    public final String code;

                    public InteropError(String message, Throwable cause) {
                        super("INTEROP010: " + (message == null ? "" : message), cause);
                        this.code = "INTEROP010";
                    }

                    public InteropError(String message) {
                        this(message, null);
                    }

                    public String getCode() { return this.code; }
                }

                public static String kof_interop_error_message(InteropError e) {
                    return e == null ? "" : e.getMessage();
                }

                public static String kof_interop_error_code(InteropError e) {
                    return e == null ? "" : e.getCode();
                }
                """;
    }
}
