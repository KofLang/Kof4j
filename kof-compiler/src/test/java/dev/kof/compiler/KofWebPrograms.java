package dev.kof.compiler;

/**
 * Programas Kof inline do E2E web ({@code KofWebE2ETest}), hoisted de inline para
 * constantes. Vive fora da classe de teste para mantê-la abaixo do limite de 500
 * linhas de teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}).
 */
abstract class KofWebPrograms {

    static final String WEB_APP = """
            record User(String name, Int age)

            main() {
                var app = web.app()
                app.use {
                    if (header("x-auth") == "secret") {
                        return null
                    }
                    return "{\\"error\\": \\"unauthorized\\"}"
                }
                app.get("/hello") {
                    return "Hello from Kof"
                }
                app.get("/users/:id") {
                    return "user " + param("id") + " q=" + query("name")
                }
                app.get("/agent") {
                    return "agent=" + header("user-agent")
                }
                app.get("/me") {
                    return method() + " " + path()
                }
                app.post("/echo") {
                    return "got:" + body()
                }
                app.post("/user") {
                    var user = json.decode<User>(body())
                    return json.encode(user)
                }
                app.listen(PORT)
            }
            """;

    static final String SRC_ABSENT_HEADER_AND_QUERY_ARE_NULLABLE = """
                main() {
                    var app = web.app()
                    app.get("/h") {
                        var c = header("x-ausente")
                        if (c != null) {
                            return "len:" + c.length
                        }
                        return "nada"
                    }
                    app.get("/q") {
                        var n = query("name")
                        if (n != null) {
                            return "nome:" + n
                        }
                        return "sem-nome"
                    }
                    app.listen(PORT)
                }
                """;

    static final String SRC_HEALTH_ENDPOINT_BYPASSES_MIDDLEWARE = """
                main() {
                    var app = web.app()
                    app.health("/health")
                    app.use {
                        if (header("x-auth") == "secret") {
                            return null
                        }
                        return "{\\"error\\": \\"unauthorized\\"}"
                    }
                    app.get("/hello") {
                        return "Hello from Kof"
                    }
                    app.listen(PORT)
                }
                """;

    static final String SRC_DELETE_ROUTE_COMPILES_AND_RESPONDS = """
                main() {
                    var app = web.app()
                    app.delete("/item/:id") {
                        return "deleted:" + param("id")
                    }
                    app.listen(PORT)
                }
                """;

    static final String SRC_HANDLER_RETURNING_NULL_AS_LAST_PATH_STILL_RESPONDS_VALUE = """
                main() {
                    var app = web.app()
                    app.get("/x/:id") {
                        var id = param("id").toInt()
                        if (id == 1) {
                            return "one"
                        }
                        return null
                    }
                    app.listen(PORT)
                }
                """;

    static final String SRC_MULTIPLE_TRAILING_LAMBDA_ROUTES = """
                main() {
                    var app = web.app()
                    app.get("/a") { return "A" }
                    app.get("/b") { return "B" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SSE_AND_WS_GAP_ON_NATIVE = """
                main() {
                    var app = web.app()
                    app.sse("/events") { return "x" }
                    app.ws("/chat") { return "x" }
                }
                """;

    static final String SRC_SECURITY_HEADERS_BY_DEFAULT = """
                main() {
                    var app = web.app()
                    app.security()
                    app.get("/hello") { return "ok" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_REQUIRES_VALID_BEARER_WHEN_AUTH_ENABLED = """
                main() {
                    auth.secret("s3cret")
                    var app = web.app()
                    var o = mapOf()
                    o.put("auth", true)
                    app.security(o)
                    app.get("/me") { return "hi " + auth.user() }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_REJECTS_INVALID_TOKEN_IF_PRESENT_EVEN_WITHOUT_AUTH_REQUIRED = """
                main() {
                    auth.secret("s3cret")
                    var app = web.app()
                    app.security()
                    app.get("/open") { return "public" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_ENFORCES_ROLES = """
                main() {
                    auth.secret("s3cret")
                    var app = web.app()
                    var o = mapOf()
                    o.put("roles", "admin")
                    app.security(o)
                    app.get("/admin") { return "secret" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_CORS_DENIES_UNKNOWN_ORIGIN_AND_ANSWERS_PREFLIGHT = """
                main() {
                    var app = web.app()
                    var o = mapOf()
                    o.put("cors", "https://app.example")
                    app.security(o)
                    app.get("/x") { return "ok" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_CSRF_DOUBLE_SUBMIT = """
                main() {
                    var app = web.app()
                    var o = mapOf()
                    o.put("csrf", true)
                    app.security(o)
                    app.post("/p") { return "posted" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_CSRF_IS_ON_BY_DEFAULT = """
                main() {
                    var app = web.app()
                    app.security()
                    app.post("/p") { return "posted" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_PERMIT_ALL_ALIAS_IS_PUBLIC_PATHS = """
                main() {
                    var app = web.app()
                    var opts = mapOf("sessionHeader", "authorization", "permitAll", "/open")
                    app.security(opts)
                    app.get("/open") { return "open" }
                    app.get("/closed") { return "closed" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_RATE_LIMIT_BY_REMOTE_ADDRESS = """
                main() {
                    var app = web.app()
                    var o = mapOf()
                    o.put("rateLimit", "2/60")
                    app.security(o)
                    app.get("/r") { return "ok" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_APP_SECURITY_PIPELINE_E2_E = """
                main() {
                    var app = web.app()
                    var opts = mapOf("sessionHeader", "authorization", "publicPaths", "/public,/login")
                    app.security(opts)
                    app.get("/public") { return "public content" }
                    app.get("/secret") { return "secret content" }
                    app.post("/secret") { return "secret content" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_DECLARATIVE_PAYLOADS_FOR_UNAUTHORIZED_AND_RATE_LIMIT = """
                main() {
                    var app = web.app()
                    var r = mapOf()
                    r.put("unauthorized", "{\\"error\\":\\"custom-401\\"}")
                    r.put("tooManyRequests", "{\\"error\\":\\"custom-429\\"}")
                    val rObj: Object = r
                    val o: Map<String, Object> = mapOf()
                    val h: Object = "authorization"
                    o.put("sessionHeader", h)
                    val rl: Object = "1/60"
                    o.put("rateLimit", rl)
                    o.put("responses", rObj)
                    app.security(o)
                    app.get("/secret") { return "s" }
                    app.listen(PORT)
                }
                """;

    static final String SRC_SECURITY_DECLARATIVE_FORBIDDEN_PAYLOAD = """
                main() {
                    var app = web.app()
                    var r = mapOf("forbidden", "{\\"error\\":\\"custom-403\\"}")
                    val rObj: Object = r
                    val o: Map<String, Object> = mapOf()
                    val c: Object = "https://good.example"
                    o.put("cors", c)
                    o.put("responses", rObj)
                    app.security(o)
                    app.get("/x") { return "ok" }
                    app.listen(PORT)
                }
                """;
}
