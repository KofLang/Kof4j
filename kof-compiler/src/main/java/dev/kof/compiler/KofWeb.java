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
final class KofWeb {

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
            case "listen" -> argTypes.size() == 1
                    ? new WebCall("kof_web_listen", VOID, List.of(STR, argTypes.get(0)))
                    : null;
            case "serveDir" -> argTypes.size() == 2
                    ? new WebCall("kof_web_serve_dir", VOID, List.of(STR, STR, STR))
                    : null;
            case "health" -> argTypes.size() == 1
                    ? new WebCall("kof_web_health", VOID, List.of(STR, STR))
                    : null;
            case "listenSecure" -> argTypes.size() == 1
                    ? new WebCall("kof_web_listen_secure", VOID, List.of(STR, INT))
                    : null;
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
            case "kof_web_listen_secure" -> "WEB002";
            default -> "WEB001";
        };
    }


    /** Request-context functions available inside route handlers. */
    static WebCall contextCall(String name, int argCount) {
        return switch (name) {
            case "param", "query", "header" -> argCount == 1
                    ? new WebCall("kof_web_" + name, STR, List.of(STR))
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
