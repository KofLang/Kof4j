package dev.kof.compiler;

import java.util.List;

/**
 * #1126 — o tipo {@code kof.http.Response} (decisão da mantenedora 05/11,
 * gravada na issue): uma resposta HTTP como VALOR que o caller lê —
 * {@code status()}/{@code header(name)}/{@code headers()}/{@code body()} —
 * sem throw em 4xx/5xx (o contrato está no código e nos headers, não numa
 * exceção). {@code http.get}/{@code post} ficam intactos (CONGELADOS pelo
 * uso). Builtin no padrão da casa (InteropError/Secret/KeyHandle):
 * acessores são MÉTODOS; o runtime JVM carrega a nested class
 * {@code KofRuntime$HttpResponse} ({@link dev.kof.compiler.jvm.JvmHttpResponseRuntime}).
 *
 * <p>Fatia 1 = JVM (onde get/post funcionam hoje). Script/JS são a fatia 2
 * registrada — fora do JVM a face é recusada pelo gap de interop existente
 * (INTEROP003/HTTP002), nunca stub silencioso (R6).
 */
public final class KofHttpResponse {

    private KofHttpResponse() {}

    static final Type RESPONSE = new Type.ClassType("kof.http", "Response", List.of());

    static boolean isResponseType(Type t) {
        return RESPONSE.equals(t);
    }

    record ResponseCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** Acessores de instância sobre o receiver Response. */
    static ResponseCall instanceMethod(Type recv, String name, int argc) {
        if (!RESPONSE.equals(recv)) return null;
        if ("status".equals(name) && argc == 0) {
            return new ResponseCall("kof_http_response_status", Type.PrimitiveType.INT, List.of(RESPONSE));
        }
        if ("header".equals(name) && argc == 1) {
            return new ResponseCall("kof_http_response_header", BuiltinTypes.STRING,
                    List.of(RESPONSE, BuiltinTypes.STRING));
        }
        if ("headers".equals(name) && argc == 0) {
            return new ResponseCall("kof_http_response_headers", BuiltinTypes.STRING, List.of(RESPONSE));
        }
        if ("body".equals(name) && argc == 0) {
            return new ResponseCall("kof_http_response_body", BuiltinTypes.STRING, List.of(RESPONSE));
        }
        return null;
    }
}
