package dev.kof.compiler;

import java.util.List;

/**
 * D-INTEROP-ERR-TYPE (02/10, DECISIONS): o erro de interop estrangeiro é um
 * tipo REAL e catchável do idioma — {@code catch (InteropError e)} — com
 * {@code message()}/{@code code()} (vocabulário {@code INTEROP00x}; código da
 * falha de downcall FFI = {@code INTEROP010}, novo item da escala). A face
 * vive no JVM (é lá que a ffi estrangeira baixa); fora dele a face é recusada
 * com código nomeado ({@code INTEROP009}) no SEM do catch — nunca stub
 * silencioso (R6). O contrato {@code catch (String)} é CONGELADO: o
 * {@code InteropError} é subclasse de {@code RuntimeException} e a mensagem
 * carrega o prefixo {@code INTEROP010: }, então o String-catch de hoje vê a
 * falha exatamente como via antes (prova: InteropErrorE2ETest).
 *
 * <p>Builtin no padrão da casa (Secret/KeyHandle/Buffer): acessores são
 * MÉTODOS, não propriedades — {@code e.message()}/{@code e.code()}; um campo
 * {@code e.message} cai no SEM102 da família.
 */
public final class KofInteropError {

    private KofInteropError() {}

    static final Type INTEROP_ERROR = new Type.ClassType("kof", "InteropError", List.of());

    static Type typeByName(String name) {
        return "InteropError".equals(name) ? INTEROP_ERROR : null;
    }

    static boolean isInteropErrorType(Type t) {
        return INTEROP_ERROR.equals(t);
    }

    /** O código nomeado da recusa fora do JVM (gap da face, R6). */
    static final String GAP_CODE = "INTEROP009";

    /** Runtime code for a failed foreign downcall (missing symbol, marshal, invoke). */
    static final String FFI_FAILURE_CODE = "INTEROP010";

    record InteropCall(String function, Type returnType, List<Type> parameterTypes) {}

    static InteropCall instanceMethod(Type recv, String name, int argc) {
        if (!INTEROP_ERROR.equals(recv) || argc != 0) return null;
        return switch (name) {
            case "message" -> new InteropCall("kof_interop_error_message", BuiltinTypes.STRING, List.of(INTEROP_ERROR));
            case "code" -> new InteropCall("kof_interop_error_code", BuiltinTypes.STRING, List.of(INTEROP_ERROR));
            default -> null;
        };
    }
}
