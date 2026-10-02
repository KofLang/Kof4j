package dev.kof.compiler;

import java.util.List;


/**
 * Compile-time dispatch table for the Kof-native web stack ({@code kof.web}).
 *
 * <p>The Kof surface is idiomatic:
 *
 * <pre>{@code
 * app = web.app()
 * app.get("/hello") { return "Hello" }
 * app.get("/users/:id") { return "user " + param("id") }
 * app.listen(8080)
 * }</pre>
 *
 * <p>Internally every call maps to a static {@code kof_web_*} function of the
 * generated {@code dev.kof.runtime.KofRuntime} class (JVM target). The
 * {@code kof.web.App} type exists only at compile time; at runtime an app is
 * a String handle registered in the runtime registry.
 */
public final class KofWeb {

    private KofWeb() {}

    static final Type APP = new Type.ClassType("kof.web", "App", List.of());
    static final Type SSE_CONNECTION =
            new Type.ClassType("dev.kof.runtime", "KofRuntime$SseConnection", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type VOID = Type.PrimitiveType.VOID;

    /** HTTP methods that can be routed with {@code app.<method>(path, handler)}. */
    private static final List<String> ROUTE_METHODS =
            List.of("get", "post", "put", "delete", "patch", "options", "ws", "sse");

    static boolean isAppType(Type t) {
        return APP.equals(t);
    }

    static boolean isSseConnectionType(Type t) {
        return SSE_CONNECTION.equals(t);
    }

    /** Methods available on the synthetic {@code sse} handler parameter. */
    static WebCall sseConnectionMethod(String name, List<Type> argTypes) {
        return switch (name) {
            case "send" -> argTypes.size() == 1
                    ? new WebCall(name, VOID, argTypes) : null;
            case "event" -> argTypes.size() == 2
                    ? new WebCall(name, VOID, argTypes) : null;
            case "close" -> argTypes.isEmpty()
                    ? new WebCall(name, VOID, List.of()) : null;
            case "isOpen" -> argTypes.isEmpty()
                    ? new WebCall(name, Type.PrimitiveType.BOOL, List.of()) : null;
            default -> null;
        };
    }

    static boolean isWebNamespace(String name) {
        return "web".equals(name);
    }

    static boolean isContextFunction(String name) {
        return switch (name) {
            case "param", "query", "header" -> true;
            case "body", "method", "path" -> true;
            case "status", "headerSet", "setHeader" -> true;
            case "sse", "wsSend", "wsMessage", "stats" -> true;
            default -> false;
        };
    }

    static boolean isRouteMethod(String name) {
        return ROUTE_METHODS.contains(name);
    }


    record WebCall(String function, Type returnType, List<Type> parameterTypes) {}


    /** {@code web.app()} — creates a new application and returns its handle. */
    static WebCall appConstructor() {
        return new WebCall("kof_web_app_new", APP, List.of());
    }


    /** Instance methods on {@code kof.web.App} receivers. */
    static WebCall instanceMethod(String name, List<Type> argTypes) {
        if (ROUTE_METHODS.contains(name)) {
            if ("sse".equals(name)) {
                return instanceSseMethod(name, argTypes);
            }
            if ("ws".equals(name)) {
                return instanceWsMethod(name, argTypes);
            }
            // D-HTTP-POLICIES (F3): `app.get(path, opts) { }` — rota com
            // policy por endpoint (3 args: String, Map, handler).
            if (argTypes.size() == 3 && BuiltinTypes.isMap(argTypes.get(1))) {
                return new WebCall("kof_web_route_opts", VOID,
                        List.of(STR, STR, STR, BuiltinTypes.MAP, argTypes.get(2)));
            }
            if (argTypes.size() == 2) {
                return new WebCall("kof_web_route", VOID,
                        List.of(STR, STR, STR, argTypes.get(1)));
            }
            return null;
        }
        return switch (name) {
            case "use" -> argTypes.size() == 1
                    ? new WebCall("kof_web_use", VOID, List.of(STR, argTypes.get(0)))
                    : null;
            // D-SEC C18: `app.security()` — middleware composto com ordem
            // fixa (rate-limit → cors → headers → cookies/session → csrf →
            // auth → RBAC → rota). Sem args = defaults seguros (headers
            // hardening); com um Map = overrides documentados em
            // docs/stdlib/stdlib-web.md. JVM primeiro; Native/JS = WEB006.
            case "security" -> {
                if (argTypes.isEmpty()) {
                    yield new WebCall("kof_web_security", VOID, List.of(STR));
                }
                if (argTypes.size() == 1 && BuiltinTypes.isMap(argTypes.get(0))) {
                    yield new WebCall("kof_web_security_opts", VOID,
                            List.of(STR, BuiltinTypes.MAP));
                }
                yield null;
            }
            // D-HTTP-POLICIES (F2): `app.policy(prefix, opts)` — policy de
            // recurso para toda rota sob o prefixo (escalar mais profundo
            // vence; listas somam). JVM completo; Native/JS = WEB006.
            case "policy" -> argTypes.size() == 2 && isString(argTypes.get(0))
                    && BuiltinTypes.isMap(argTypes.get(1))
                    ? new WebCall("kof_web_policy", VOID,
                            List.of(STR, STR, BuiltinTypes.MAP))
                    : null;
            // #102.2 (13/09): `listen` aceita SÓ Int — String virava
            // VerifyError em runtime. Com o gate aqui, `listen("8100")`
            // retorna null → o typer emite SEM025 em compile-time
            // (kof check) em vez de bytecode inválido.
            case "listen" -> argTypes.size() == 1 && isInt(argTypes.get(0))
                    ? new WebCall("kof_web_listen", VOID, List.of(STR, INT))
                    : null;
            case "serveDir" -> argTypes.size() == 2
                    ? new WebCall("kof_web_serve_dir", VOID, List.of(STR, STR, STR))
                    : null;
            case "health" -> argTypes.size() == 1
                    ? new WebCall("kof_web_health", VOID, List.of(STR, STR))
                    : null;
            case "listenSecure" -> {
                // 1 arg: TLS self-signed de dev (G12). 3 args: certificado
                // próprio PKCS#8 PEM (D-SEC: `listenSecure(port, certPem,
                // keyPem)`) — produção; Native/JS seguem WEB002 honesto.
                if (argTypes.size() == 1 && isInt(argTypes.get(0))) {
                    yield new WebCall("kof_web_listen_secure", VOID, List.of(STR, INT));
                }
                if (argTypes.size() == 3 && isInt(argTypes.get(0))
                        && isString(argTypes.get(1)) && isString(argTypes.get(2))) {
                    yield new WebCall("kof_web_listen_secure_pem", VOID,
                            List.of(STR, INT, STR, STR));
                }
                yield null;
            }
            case "port" -> argTypes.isEmpty()
                    ? new WebCall("kof_web_port", INT, List.of(STR))
                    : null;
            case "close" -> argTypes.isEmpty()
                    ? new WebCall("kof_web_close", VOID, List.of(STR))
                    : null;
            case "configure" -> argTypes.size() == 2
                    && isString(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? new WebCall("kof_web_configure", VOID, List.of(STR, STR, INT))
                    : null;
            default -> null;
        };
    }


    /** {@code app.sse(path, handler)} — route kind SSE, protocol comes later. */
    static WebCall instanceSseMethod(String name, List<Type> argTypes) {
        return "sse".equals(name) && argTypes.size() == 2
                ? new WebCall("kof_web_sse_route", VOID,
                        List.of(STR, STR, STR, argTypes.get(1)))
                : null;
    }


    /** {@code app.ws(path, handler)} — route kind WS, protocol comes later. */
    static WebCall instanceWsMethod(String name, List<Type> argTypes) {
        return "ws".equals(name) && argTypes.size() == 2
                ? new WebCall("kof_web_ws_route", VOID,
                        List.of(STR, STR, argTypes.get(1)))
                : null;
    }


    static String gapCode(String function) {
        return switch (function) {
            case "kof_web_sse_route" -> "WEB003";
            case "kof_web_ws_route" -> "WEB004";
            case "kof_web_serve_dir" -> "WEB005";
            case "kof_web_security", "kof_web_security_opts", "kof_web_policy",
                 "kof_web_route_opts" -> "WEB006";
            case "kof_web_listen_secure", "kof_web_listen_secure_pem" -> "WEB002";
            default -> "WEB001";
        };
    }

    /**
     * #102 item 3: quais funções de contexto o runtime nativo realmente emite.
     * O T1 nativo cobre listen/route + body/method/path; o resto não tem
     * símbolo no .s gerado e virava `undefined reference` no ld.
     */
    static boolean contextNativeSupported(String function) {
        return switch (function) {
            case "kof_web_body" -> true;
            default -> false;
        };
    }

    /** #104 item (16/09): funções de contexto que o runtime WEB JS
     *  (JsRuntimeUiWeb + JsRuntimeOps ramo `kof_web_`) realmente emite.
     *  param/query/header/body/method/path/status/headerSet/setHeader são
     *  reais (leem o request/response corrente); sse/wsSend/wsMessage/stats
     *  NÃO têm símbolo — antes caíam no `kofWebStub` (return 0 SILENCIOSO,
     *  R6). Agora viram gap em tempo de compilação, como o gate nativo. */
    static boolean contextJsSupported(String function) {
        return switch (function) {
            case "kof_web_param", "kof_web_query", "kof_web_header",
                 "kof_web_body", "kof_web_method", "kof_web_path",
                 "kof_web_status", "kof_web_header_set",
                 // WEB001 SSE (16/09): sse(text) handler-scoped no host JS.
                 "kof_web_sse_send" -> true;
            default -> false;
        };
    }

    static boolean isNativeTarget(Target t) {
        return t == Target.NATIVE || t == Target.NATIVE_RISCV64 || t == Target.NATIVE_AARCH64;
    }


    /** Request-context functions available inside route handlers. */
    static WebCall contextCall(String name, int argCount) {
        // #102 item 4: query()/header() devolvem null quando o parâmetro/cabeçalho
        // não está presente no request (HashMap.get no runtime). Declará-las como
        // String era uma mentira de tipo (NPE silencioso em header().split(...));
        // agora Nullable(STR) força o narrowing no kof check (SEM049), espelhando
        // SG-008 (Map.get -> V?). param() continua STR: só rota matchada chega ao
        // handler e todo :param do match tem valor (training/idioms/web.md usa
        // param("id").toInt() sem check — código válido, não pode virar erro).
        return switch (name) {
            case "param" -> argCount == 1
                    ? new WebCall("kof_web_param", STR, List.of(STR))
                    : null;
            case "query", "header" -> argCount == 1
                    ? new WebCall("kof_web_" + name, new Type.NullableType(STR), List.of(STR))
                    : null;
            case "body", "method", "path" -> argCount == 0
                    ? new WebCall("kof_web_" + name, STR, List.of())
                    : null;
            case "status" -> argCount == 2
                    ? new WebCall("kof_web_status", STR, List.of(INT, STR))
                    : null;
            case "headerSet", "setHeader" -> argCount == 2
                    ? new WebCall("kof_web_header_set", STR, List.of(STR, STR))
                    : null;
            case "sse" -> argCount == 1
                    ? new WebCall("kof_web_sse_send", STR, List.of(STR))
                    : null;
            case "wsSend" -> argCount == 1
                    ? new WebCall("kof_web_ws_send", VOID, List.of(STR))
                    : null;
            case "wsMessage" -> argCount == 0
                    ? new WebCall("kof_web_ws_message", STR, List.of())
                    : null;
            case "stats" -> argCount == 1
                    ? new WebCall("kof_web_stats", STR, List.of(STR))
                    : null;
            default -> null;
        };
    }

    private static boolean isString(Type t) {
        return t == STR || t.toString().contains("String");
    }

    private static boolean isInt(Type t) {
        return t == INT || t.toString().contains("Int");
    }
}
