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
 * per line. Native and JS targets would report {@code HTTP002} at compile time
 * (but the branch is dead — see §259; no HTTP gap code is emitted today).
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

/** X10 fatia 2: nomes aceitos pelo dispatch real (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case-literals da fonte abaixo. */
    static List<String> functions() { return List.of("get", "post", "put", "delete", "patch", "options", "status", "timeout", "retry", "circuit", "insecure", "send"); }

    static boolean isHttpMethod(String name) {
        return switch (name) {
            case "get", "post", "put", "delete", "patch", "options", "status", "timeout",
                    "retry", "circuit", "insecure", "send" -> true;
            default -> false;
        };
    }

    record HttpCall(String function, Type returnType, List<Type> parameterTypes) {}

    private static boolean isStr(Type t) {
        return STR.equals(t) || "String".equals(t.toString());
    }

    /** §832: the body of {@code http.send} may be absent: a String, a {@code String?} or {@code null}. */
    private static boolean isBody(Type t) {
        return isStr(t) || Type.isUnknown(t) || t instanceof Type.NullableType nt && isStr(nt.inner());
    }

    private static boolean isInt(Type t) {
        return t == INT || "Int".equals(t.toString()) || "int".equals(t.toString());
    }

    /** kof.http: JVM + JS (JS via Java HttpClient interop / fetch),
     *  Native via HTTP/1.1 puro em asm (HTTP002 parcial — http somente,
     *  https em TLS gap; DNS host≠IPv4 cai em 127.0.0.1). */
    // §259: kof.http is available on every shipping target today (JVM/JS + native
    // HTTP/1.1 http-only; the https/TLS gap lives at the scheme level, not here).
    // The exhaustive switch reads `target` (CodeQL #776 java/unused-parameter) and
    // stays compile-safe: a new Target forces this guard to be revisited, and the
    // day a target genuinely drops out of the http surface, the HTTP002 branch in
    // ExpressionHttpCallLowerer:19-30 becomes live (gapCode wired here).
    // WASM/WASI (15.1, #776) enter like the MCU slices: the scalar backend
    // refuses any http use with its own honest WASM002, never a silent path.
    static boolean supportedOn(Target target) {
        return switch (target) {
            case JVM, NATIVE, NATIVE_RISCV64, NATIVE_AARCH64, NATIVE_RISCV32, NATIVE_MCU_ARM,
                    JS, ANDROID, SCRIPT, WASM, WASI -> true;
        };
    }

    // §259: gapCode() has no callers — no HTTP gap code is emitted today.
    // Stays for when native http configurators gain a real gap code.
    static String gapCode() {
        return "HTTP002";
    }

    /** {@code http.<verb>(url[, body][, headers…])} — headers são variádicos
     *  (GitHub #32): 2+ args de header são mesclados em uma String `\n`-separada
     *  pelo lowering (o runtime já splita por linha). Aridade mínima: 1 (verb
     *  sem body) ou 2 (com body). */
    static HttpCall staticCall(String name, List<Type> argTypes) {
        if (!isHttpMethod(name)) return null;
        // audit 0.6.0 HTTP-01 (ledger §665): a ligação era SÓ POR ARIDADE —
        // http.status(401) ligava kof_http_status(String) com um Int e o JVM
        // morria no VerifyError. Guarda de tipo em cada overload (espelho
        // KofMath isInt/isStr, R6): sem casa tipada, devolve null e o SEM
        // emite o "Cannot resolve method" (SEM025) já existente.
        return switch (name) {
            case "get", "delete", "options" -> switch (argTypes.size()) {
                case 1 -> isStr(argTypes.get(0))
                        ? new HttpCall("kof_http_" + name, STR, List.of(STR)) : null;
                default -> argTypes.size() >= 2 && isStr(argTypes.get(0)) && isStr(argTypes.get(1))
                        ? new HttpCall("kof_http_" + name + "_headers", STR, List.of(STR, STR))
                        : null;
            };
            case "post", "put", "patch" -> switch (argTypes.size()) {
                case 2 -> isStr(argTypes.get(0)) && isStr(argTypes.get(1))
                        ? new HttpCall("kof_http_" + name, STR, List.of(STR, STR)) : null;
                default -> argTypes.size() >= 3 && isStr(argTypes.get(0))
                        && isStr(argTypes.get(1)) && isStr(argTypes.get(2))
                        ? new HttpCall("kof_http_" + name + "_headers", STR, List.of(STR, STR, STR))
                        : null;
            };
            // §832: send(method, url[, body][, headers...]) -> Response, no throw on 4xx/5xx
            case "send" -> argTypes.size() >= 2 && isStr(argTypes.get(0)) && isStr(argTypes.get(1))
                    && (argTypes.size() < 3 || isBody(argTypes.get(2)))
                    && argTypes.stream().skip(3).allMatch(KofHttp::isStr)
                    ? new HttpCall(HttpResponseCalls.SEND, HttpResponseCalls.RESPONSE, List.of(STR, STR, STR, STR))
                    : null;
            case "status" -> argTypes.size() == 1 && isStr(argTypes.get(0))
                    ? new HttpCall("kof_http_status", INT, List.of(STR))
                    : null;
            case "timeout" -> argTypes.size() == 1 && isInt(argTypes.get(0))
                    ? new HttpCall("kof_http_timeout_set", VOID, List.of(INT))
                    : null;
            case "retry" -> argTypes.size() == 1 && isInt(argTypes.get(0))
                    ? new HttpCall("kof_http_retry_set", VOID, List.of(INT))
                    : null;
            case "circuit" -> argTypes.size() == 1 && isInt(argTypes.get(0))
                    ? new HttpCall("kof_http_circuit_set", VOID, List.of(INT))
                    : null;
            // TLS hardening slice 3: the trust-all opt-in — a JVM face; the
            // emit refuses JS/Native honestly (HTTP002). No args, warns once.
            case "insecure" -> argTypes.isEmpty()
                    ? new HttpCall("kof_http_insecure_set", VOID, List.of())
                    : null;
            default -> null;
        };
    }
}