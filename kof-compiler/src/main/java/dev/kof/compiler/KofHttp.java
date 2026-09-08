package dev.kof.compiler;

import java.util.List;


/**
 * Compile-time dispatch table for the Kof-native HTTP client module
 * ({@code kof.http}).
 *
 * <p>The Kof surface is idiomatic:
 *
 * <pre>{@code
 * var html = http.get("https://example.com")
 * var page = http.get(url, "Accept: text/html")
 * var resp = http.post(api, json.encode(body), "Content-Type: application/json")
 * if (http.status(url) == 404) { ... }
 * http.timeout(30)
 * }</pre>
 *
 * <p>Internally every call maps to a static {@code kof_http_*} function of the
 * generated {@code dev.kof.runtime.KofRuntime} class (JVM target, JDK
 * {@code java.net.http}). The body is returned as a String (JSON flows through
 * {@code kof.json}); headers are a single String with one {@code Name: value}
 * per line. Native and JS targets report {@code HTTP002} at compile time.
 */
public final class KofHttp {

    private KofHttp() {}

    static final Type HTTP = new Type.ClassType("kof.http", "Http", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isHttpNamespace(String name) {
        return "http".equals(name);
    }

    static boolean isHttpMethod(String name) {
        return switch (name) {
            case "get", "post", "put", "delete", "patch", "options", "status", "timeout",
                    "retry", "circuit" -> true;
            default -> false;
        };
    }

    record HttpCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** kof.http: JVM + JS (JS via Java HttpClient interop / fetch),
     *  Native via HTTP/1.1 puro em asm (HTTP002 parcial — http somente,
     *  https em TLS gap; DNS host≠IPv4 cai em 127.0.0.1). */
    static boolean supportedOn(Target target) {
        return true;
    }

    static String gapCode() {
        return "HTTP002";
    }

    /** {@code http.<verb>(url[, body][, headers…])} — headers são variádicos
     *  (GitHub #32): 2+ args de header são mesclados em uma String `\n`-separada
     *  pelo lowering (o runtime já splita por linha). Aridade mínima: 1 (verb
     *  sem body) ou 2 (com body). */
    static HttpCall staticCall(String name, List<Type> argTypes) {
        if (!isHttpMethod(name)) return null;
        return switch (name) {
            case "get", "delete", "options" -> switch (argTypes.size()) {
                case 1 -> new HttpCall("kof_http_" + name, STR, List.of(STR));
                default -> argTypes.size() >= 2
                        ? new HttpCall("kof_http_" + name + "_headers", STR, List.of(STR, STR))
                        : null;
            };
            case "post", "put", "patch" -> switch (argTypes.size()) {
                case 2 -> new HttpCall("kof_http_" + name, STR, List.of(STR, STR));
                default -> argTypes.size() >= 3
                        ? new HttpCall("kof_http_" + name + "_headers", STR, List.of(STR, STR, STR))
                        : null;
            };
            case "status" -> argTypes.size() == 1
                    ? new HttpCall("kof_http_status", INT, List.of(STR))
                    : null;
            case "timeout" -> argTypes.size() == 1
                    ? new HttpCall("kof_http_timeout_set", VOID, List.of(INT))
                    : null;
            case "retry" -> argTypes.size() == 1
                    ? new HttpCall("kof_http_retry_set", VOID, List.of(INT))
                    : null;
            case "circuit" -> argTypes.size() == 1
                    ? new HttpCall("kof_http_circuit_set", VOID, List.of(INT))
                    : null;
            default -> null;
        };
    }
}