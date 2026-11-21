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

    /**
     * TLS hardening slice 1: the value {@code web.secureHost(host, certPem,
     * keyPem)} produces — a compile-time-only descriptor consumed by
     * {@code app.listenSecure(port, hosts)}. It never reaches the runtime as a
     * type: the lowering flattens the list into three parallel String arrays.
     */
    static final Type SECURE_HOST = new Type.ClassType("kof.web", "SecureHost", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type VOID = Type.PrimitiveType.VOID;

    /** HTTP methods that can be routed with {@code app.<method>(path, handler)}. */
    private static final List<String> ROUTE_METHODS =
            List.of("get", "post", "put", "delete", "patch", "options", "ws", "sse");

    static boolean isAppType(Type t) {
        return APP.equals(t);
    }

    /**
     * §575 — phantom handle types: they exist only at COMPILE time; at runtime
     * the value is a String handle in the web registry. Any JVM position that
     * materializes a class descriptor or owner name (lambda capture field,
     * getfield/putfield, checkcast) MUST erase them to {@code java.lang.String}
     * or the class load dies {@code NoClassDefFoundError: kof/web/App}. Public
     * so the JVM type mapper (subpackage) can consult the single registry.
     */
    public static boolean isPhantomHandleType(Type t) {
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

    /** {@code web.secureHost(host, certPem, keyPem)} — one SNI entry. */
    static WebCall secureHostConstructor() {
        return new WebCall("kof_web_secure_host", SECURE_HOST, List.of(STR, STR, STR));
    }

    static boolean isSecureHostType(Type t) {
        return SECURE_HOST.equals(t);
    }

    /** {@code List<SecureHost>} — the SNI overload's second argument. */
    static boolean isSecureHostList(Type t) {
        return t instanceof Type.ClassType ct
                && "kof".equals(ct.packageName()) && "List".equals(ct.name())
                && ct.typeArguments().size() == 1
                && isSecureHostType(ct.typeArguments().get(0));
    }

    static boolean isContextFunction(String name) {
        return switch (name) {
            case "param", "query", "header" -> true;
            case "body", "method", "path" -> true;
            // #1110: o endereço TCP do peer (a decisão da mantenedora 05/11).
            case "remoteAddr" -> true;
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
            // §825: listen(host, port) binds one interface (listen(port) is 0.0.0.0).
            case "listen" -> argTypes.size() == 1 && isInt(argTypes.get(0))
                    ? new WebCall("kof_web_listen", VOID, List.of(STR, INT))
                    : argTypes.size() == 2 && isString(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? // #1114: liga UMA interface (`app.listen("127.0.0.1", p)`)
                    new WebCall("kof_web_listen_host", VOID, List.of(STR, STR, INT))
                    : null;
            case "serveDir" -> argTypes.size() == 2
                    ? new WebCall("kof_web_serve_dir", VOID, List.of(STR, STR, STR))
                    : null;
            case "health" -> argTypes.size() == 1
                    ? new WebCall("kof_web_health", VOID, List.of(STR, STR))
                    : null;
            case "reloadTls" -> {
                // TLS hardening slice 2: hot-reload — the atomic context swap.
                // (certPem, keyPem): re-arms a single-cert server; (hosts):
                // replaces the whole SNI map. A failure keeps the old state and
                // reports WEB007 (catchable as a Kof String).
                if (argTypes.size() == 2 && isString(argTypes.get(0)) && isString(argTypes.get(1))) {
                    yield new WebCall("kof_web_reload_tls_pem", VOID, List.of(STR, STR, STR));
                }
                if (argTypes.size() == 1 && isSecureHostList(argTypes.get(0))) {
                    yield new WebCall("kof_web_reload_tls_sni", VOID,
                            List.of(STR, STR, STR, STR));
                }
                yield null;
            }
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
                // TLS hardening slice 1: SNI — one process, many hostnames.
                // The list is flattened by the lowering into three parallel
                // String arrays (hosts, certs, keys); the runtime selects the
                // certificate from the ClientHello SNI and REFUSES an unknown
                // hostname (never the default cert).
                if (argTypes.size() == 2 && isInt(argTypes.get(0))
                        && isSecureHostList(argTypes.get(1))) {
                    yield new WebCall("kof_web_listen_secure_sni", VOID,
                            List.of(STR, INT, STR, STR, STR));
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
            case "kof_web_listen_secure", "kof_web_listen_secure_pem",
                 "kof_web_listen_secure_sni", "kof_web_reload_tls_pem",
                 "kof_web_reload_tls_sni" -> "WEB002";
            default -> "WEB001";
        };
    }

    /**
     * #102 item 3: quais funções de contexto o runtime nativo realmente emite.
     * O T1 nativo cobre listen/route + body/method/path; o resto não tem
     * símbolo no .s gerado e virava `undefined reference` no ld.
     *
     * <p>§1159 (21/11): o runtime web nativo é x86-64-only (o
     * {@code NativeWebRuntime} é emitido só no caminho x86). O gate
     * {@code contextNativeSupported} é POR ALVO: no riscv64/aarch64 nenhuma
     * função de contexto tem símbolo — emití-la seria um link break (o
     * `ld` falhando com `undefined reference to 'kof_web_param'`, medido). O
     * cross recusa com o MESMO diagnóstico honesto (WEB001) em vez de
     * quebrar no link (R6).
     */
    static boolean contextNativeSupported(String function) {
        return switch (function) {
            case "kof_web_body" -> true;
            // §1159 (20/11): o contexto da request corrente no runtime web
            // nativo x86-64 — o handle_client guarda o método e o path quando
            // uma rota casa; kof_web_path/kof_web_method os devolvem.
            case "kof_web_path", "kof_web_method" -> true;
            // §1159 (20/11): os PATH PARAMS — o match da rota agora é por
            // segmento (kof_web_match_path): um `:name` no padrão casa
            // qualquer segmento e kof_web_param(name) devolve o valor.
            case "kof_web_param" -> true;
            case "kof_web_header_set" -> true;
            // §1159 (20/11): o status da resposta no runtime web nativo —
            // kof_web_status guarda o código e devolve o body; o escritor de
            // resposta emite o código e o texto da tabela (mesmo conjunto do
            // JVM).
            case "kof_web_status" -> true;
            // §1159 (21/11): a QUERY STRING — o handle_client separa o path
            // do "?..." (o roteamento casa o path sem o query) e
            // kof_web_query(name) devolve o valor (null quando ausente, o
            // mesmo contrato do JVM). Era o bloqueio que impedia QUALQUER
            // programa Native com `import kof.web` de compilar (o inject de
            // pageRequest chama query()).
            case "kof_web_query" -> true;
            // §1159 (20/11, fatia 3): headerSet segue recusado — o ASM da
            // fila de headers foi escrito mas o caminho de emissão tem um
            // bug de desreferenciamento não resolvido neste ciclo.
            default -> false;
        };
    }

    /**
     * §1159 (21/11): o runtime web nativo (o conjunto de símbolos
     * {@code kof_web_*}) existe SÓ no alvo x86-64. O cross riscv64/aarch64
     * não tem NENHUM deles — emitir uma função de contexto lá é um link
     * break (medido: {@code undefined reference to 'kof_web_param'} no
     * {@code riscv64-ld}). O gate é honesto e POR ALVO: no cross, TODA função
     * de contexto recusa em compile-time (WEB001), como o T1 (NAT007) recusa.
     */
    static boolean contextNativeSupported(Target t, String function) {
        if (!isNativeTarget(t)) return true;         // fora do nativo: o gate não aplica
        if (t == Target.NATIVE) return contextNativeSupported(function);
        return false;                                  // cross: nenhum símbolo web
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
                 "kof_web_body", "kof_web_method", "kof_web_path", "kof_web_remote_addr",
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
    static WebCall contextCall(String name, List<Type> argTypes) {
        int argCount = argTypes.size();
        // #102 item 4: query()/header() devolvem null quando o parâmetro/cabeçalho
        // não está presente no request (HashMap.get no runtime). Declará-las como
        // String era uma mentira de tipo (NPE silencioso em header().split(...));
        // agora Nullable(STR) força o narrowing no kof check (SEM049), espelhando
        // SG-008 (Map.get -> V?). param() continua STR: só rota matchada chega ao
        // handler e todo :param do match tem valor (training/idioms/web.md usa
        // param("id").toInt() sem check — código válido, não pode virar erro).
        return switch (name) {
            // §667 Boundary (ledger §669): guardas de tipo — a tabela ligava
            // SÓ POR ARIDADE (status("a","b") ligava kof_web_status(Int,String)
            // com duas Strings → VerifyError). isString já existia aqui.
            case "param" -> argCount == 1 && isString(argTypes.get(0))
                    ? new WebCall("kof_web_param", STR, List.of(STR))
                    : null;
            case "query", "header" -> argCount == 1 && isString(argTypes.get(0))
                    ? new WebCall("kof_web_" + name, new Type.NullableType(STR), List.of(STR))
                    : null;
            case "body", "method", "path" -> argCount == 0
                    ? new WebCall("kof_web_" + name, STR, List.of())
                    : null;
            // #1110: o endereço TCP do peer da requisição (a decisão da
            // mantenedora 05/11) — "" quando desconhecido; atrás de proxy o
            // cliente é o header x-forwarded-for (nota da stdlib-web).
            case "remoteAddr" -> argCount == 0
                    ? new WebCall("kof_web_remote_addr", STR, List.of())
                    : null;
            case "status" -> argCount == 2 && argTypes.get(0) == INT
                    && isString(argTypes.get(1))
                    ? new WebCall("kof_web_status", STR, List.of(INT, STR))
                    : null;
            case "headerSet", "setHeader" -> argCount == 2
                    && isString(argTypes.get(0)) && isString(argTypes.get(1))
                    ? new WebCall("kof_web_header_set", STR, List.of(STR, STR))
                    : null;
            case "sse" -> argCount == 1 && isString(argTypes.get(0))
                    ? new WebCall("kof_web_sse_send", STR, List.of(STR))
                    : null;
            case "wsSend" -> argCount == 1 && isString(argTypes.get(0))
                    ? new WebCall("kof_web_ws_send", VOID, List.of(STR))
                    : null;
            case "wsMessage" -> argCount == 0
                    ? new WebCall("kof_web_ws_message", STR, List.of())
                    : null;
            case "stats" -> argCount == 1 && isString(argTypes.get(0))
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
