package dev.kof.compiler;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * X10 fatia 1 (fila X, IMPLEMENTATION-UNIVERSAL-PLATFORM) — catálogo de
 * membros dos namespaces stdlib roteado por {@link KofStd#staticMethod}
 * (math, strings, encoding, net, uuid, random, rng — fatia 1; time, http, db,
 * cache, process e os 6 namespaces de segurança — fatia 2; json, log, orm, config,
 * gpu, mq, validation, observability, tetris e Image/Audio/Video/Mic — fatia 3).
 * Alimenta o completion
 * domain-aware do LSP SEM parser paralelo: os nomes são uma transcrição
 * dos `case` dos próprios typers, e o `StdCatalogTest` trava as duas pontas
 * contra a fonte real — lista ≠ case-literals do `switch (name)` ou
 * dispatch do KofStd ≠ chaves do catálogo ⇒ vermelho (padrão
 * RuntimeSlices: transcription protegida por teste, nunca confiança).
 *
 * <p>Escopo honesto: o DSL de {@code web}/{@code app} (recebedores com
 * semântica própria de rota) e {@code ui}/{@code ffi} ficam de fora — faces
 * seguintes do X10 (R6: não fingir cobertura total).
 */
public final class StdCatalog {

    private StdCatalog() {}

    private static final Map<String, List<String>> MEMBERS;

    static {
        // fatia 1: os 7 do dispatch KofStd; fatia 2: dispatch próprio
        // (time/http/db/cache/process) + os 6 namespaces de segurança.
        var m = new java.util.LinkedHashMap<String, List<String>>();
        m.put("math", KofMath.functions());
        m.put("strings", KofStrings.functions());
        m.put("encoding", KofEncoding.functions());
        m.put("net", KofNet.functions());
        m.put("uuid", KofUuid.functions());
        m.put("random", KofRandom.functions());
        m.put("rng", KofRng.functions());
        m.put("time", KofTime.functions());
        m.put("http", KofHttp.functions());
        m.put("db", KofDb.functions());
        m.put("cache", KofCache.functions());
        m.put("buffer", KofBuffer.functions());
        m.put("process", KofProcess.functions());
        m.put("shell", KofShell.functions());
        m.put("ssh", KofSsh.functions());
        m.putAll(KofSecurity.functions());
        // fatia 3: receiver-typed com dispatch próprio (MemberCallNamespaces)
        m.put("json", List.of("encode", "decode"));
        m.put("log", KofLog.functions());
        m.put("orm", KofOrm.functions());
        m.put("config", KofConfig.functions());
        m.put("gpu", KofGpu.functions());
        m.put("mq", KofMq.functions());
        m.put("validation", KofValidation.functions());
        m.put("observability", KofObservability.functions());
        m.put("tetris", KofTetris.functions());
        m.put("image", KofImage.functions());
        m.putAll(KofMedia.functions());
        MEMBERS = java.util.Collections.unmodifiableMap(m);
    }

    // LSP-A (D-POLL-19 19/09; linha 8.3): assinaturas transcritas do DISPATCHER
    // REAL de cada namespace (KofDb.staticCall / KofHttp.staticCall) e travadas
    // comportamento-a-comportamento em StdCatalogSignaturesTest (chamar com a
    // aridade gravada => bind; um bind a mais no db => null). Fonte unica viva,
    // nao comentario: fatia 1 = db+http; demais namespaces entram fatia a fatia
    // SEM fingir cobertura (R6: member sem tabela mantem o hover simples).
    private static final Map<String, Map<String, List<String>>> SIGNATURES =
            // 19/09 LSP-A fatias 1-6: forma MEDIDA no dispatcher real de cada
            // ns e travada comportamento-a-`staticCall` (StdCatalogSignaturesTest).
            // Membro sem tabela mantem hover simples (R6: nunca chute).
            // ARTEFATO DO GERADOR scripts/gen_signatures.py — nao editar a mão.
            java.util.Map.ofEntries(
            Map.entry("db", java.util.Map.ofEntries(
                    Map.entry("connect", List.of("connect(String url) -> String", "connect(String url, String user, String pass) -> String")),
                    Map.entry("query", List.of("query(String url, String sql) -> List<String>", "query(String url, String sql, Object... binds[1..4]) -> List<String>")),
                    Map.entry("execute", List.of("execute(String url, String sql) -> Int", "execute(String url, String sql, Object... binds[1..4]) -> Int")),
                    Map.entry("close", List.of("close(String url) -> void")),
                    Map.entry("transaction", List.of("transaction(callback) -> void")))),
            Map.entry("http", java.util.Map.ofEntries(
                    Map.entry("get", List.of("get(String url) -> String", "get(String url, String headers...) -> String")),
                    Map.entry("delete", List.of("delete(String url) -> String", "delete(String url, String headers...) -> String")),
                    Map.entry("options", List.of("options(String url) -> String", "options(String url, String headers...) -> String")),
                    Map.entry("post", List.of("post(String url, String body) -> String", "post(String url, String body, String headers...) -> String")),
                    Map.entry("put", List.of("put(String url, String body) -> String", "put(String url, String body, String headers...) -> String")),
                    Map.entry("patch", List.of("patch(String url, String body) -> String", "patch(String url, String body, String headers...) -> String")),
                    Map.entry("status", List.of("status(String url) -> Int")),
                    Map.entry("timeout", List.of("timeout(Int ms) -> void")),
                    Map.entry("retry", List.of("retry(Int count) -> void")),
                    Map.entry("circuit", List.of("circuit(Int threshold) -> void")))),
            Map.entry("time", java.util.Map.ofEntries(
                    Map.entry("sleep", List.of("sleep(Int ms) -> void")),
                    Map.entry("now", List.of("now() -> Long")),
                    Map.entry("collect", List.of("collect() -> void")),
                    Map.entry("interval", List.of("interval(Int ms, callback) -> String")),
                    Map.entry("cancel", List.of("cancel(String id) -> void")),
                    Map.entry("isLeapYear", List.of("isLeapYear(Int year) -> Bool")),
                    Map.entry("daysInMonth", List.of("daysInMonth(Int year, Int month) -> Int")),
                    Map.entry("dayOfWeek", List.of("dayOfWeek(Int y, Int m, Int d) -> Int")),
                    Map.entry("isWeekend", List.of("isWeekend(Int y, Int m, Int d) -> Bool")),
                    Map.entry("daysBetween", List.of("daysBetween(Int y1, Int m1, Int d1, Int y2, Int m2, Int d2) -> Int")),
                    Map.entry("age", List.of("age(Int by, Int bm, Int bd, Int ry, Int rm, Int rd) -> Int")),
                    Map.entry("isToday", List.of("isToday(Int y, Int m, Int d) -> Bool")),
                    Map.entry("addDays", List.of("addDays(String iso, Int days) -> String")),
                    Map.entry("addMonths", List.of("addMonths(String iso, Int months) -> String")),
                    Map.entry("addYears", List.of("addYears(String iso, Int years) -> String")),
                    Map.entry("startOf", List.of("startOf(String iso, String unit) -> String")),
                    Map.entry("endOf", List.of("endOf(String iso, String unit) -> String")),
                    Map.entry("diffDays", List.of("diffDays(String isoA, String isoB) -> Int")),
                    Map.entry("todayIso", List.of("todayIso() -> String")),
                    Map.entry("formatDateIso", List.of("formatDateIso(Int y, Int m, Int d) -> String")),
                    Map.entry("parseDateIso", List.of("parseDateIso(String iso) -> Int")),
                    Map.entry("tzOffsetSeconds", List.of("tzOffsetSeconds() -> Int")),
                    Map.entry("hoursBetween", List.of("hoursBetween(Int y1, Int m1, Int d1, Int h1, Int y2, Int m2, Int d2, Int h2) -> Int")))),
            Map.entry("cache", java.util.Map.ofEntries(
                    Map.entry("get", List.of("get(String key) -> String")),
                    Map.entry("set", List.of("set(String key, String value) -> void", "set(String key, String value, Int ttlSeconds) -> void")),
                    Map.entry("ttl", List.of("ttl(String key) -> Int")),
                    Map.entry("delete", List.of("delete(String key) -> void")),
                    Map.entry("clear", List.of("clear() -> void")))),
            Map.entry("process", java.util.Map.ofEntries(
                    Map.entry("run", List.of("run(String program, String... args) -> Result")),
                    Map.entry("spawn", List.of("spawn(String program, String... args) -> Handle")),
                    Map.entry("exit", List.of("exit(Int code) -> void")))),
            Map.entry("shell", java.util.Map.ofEntries(
                    Map.entry("cmd", List.of("cmd(String program, List<String> args) -> List<String>")),
                    Map.entry("run", List.of("run(String program) -> Result", "run(String program, List<String> args) -> Result")),
                    Map.entry("runWith", List.of("runWith(List<String> argv, String cwd, Map<String,String> env) -> Result")),
                    Map.entry("pipeline", List.of("pipeline(List<List<String>> stages) -> Result")),
                    Map.entry("ok", List.of("ok(result) -> Bool")))),
            Map.entry("ssh", java.util.Map.ofEntries(
                    Map.entry("cmd", List.of("cmd(String host, String command) -> List<String>")),
                    Map.entry("run", List.of("run(String host, String command) -> Result")),
                    Map.entry("ok", List.of("ok(result) -> Bool")))),
            Map.entry("net", java.util.Map.ofEntries(
                    Map.entry("scheme", List.of("scheme(String url) -> String")),
                    Map.entry("host", List.of("host(String url) -> String")),
                    Map.entry("port", List.of("port(String url) -> String")),
                    Map.entry("path", List.of("path(String url) -> String")),
                    Map.entry("query", List.of("query(String url) -> String")),
                    Map.entry("fragment", List.of("fragment(String url) -> String")),
                    Map.entry("queryEncode", List.of("queryEncode(String s) -> String")),
                    Map.entry("queryDecode", List.of("queryDecode(String s) -> String")),
                    Map.entry("listen", List.of("listen(Int port) -> Listener")),
                    Map.entry("connect", List.of("connect(String host, Int port) -> Conn")),
                    Map.entry("bind", List.of("bind(Int port) -> Endpoint")))),
            Map.entry("uuid", java.util.Map.ofEntries(
                    Map.entry("isUuid", List.of("isUuid(String s) -> Bool")),
                    Map.entry("v4", List.of("v4() -> String")),
                    Map.entry("v7", List.of("v7() -> String")))),
            Map.entry("random", java.util.Map.ofEntries(
                    Map.entry("double", List.of("double() -> Double")),
                    Map.entry("boolean", List.of("boolean() -> Bool")),
                    Map.entry("int", List.of("int(Int n) -> Int")),
                    Map.entry("hex", List.of("hex(Int n) -> String")),
                    Map.entry("randomBytesHex", List.of("randomBytesHex(Int n) -> String")),
                    Map.entry("randomInt", List.of("randomInt(Int n) -> Int")),
                    Map.entry("randomBoolean", List.of("randomBoolean() -> Bool")),
                    Map.entry("randomString", List.of("randomString(Int n, String s) -> String")))),
            Map.entry("rng", java.util.Map.ofEntries(
                    Map.entry("seed", List.of("seed(Int n) -> void")),
                    Map.entry("int", List.of("int(Int n) -> Int")),
                    Map.entry("boolean", List.of("boolean() -> Bool")),
                    Map.entry("double", List.of("double() -> Double")),
                    Map.entry("string", List.of("string(Int n, String s) -> String")))),
            Map.entry("encoding", java.util.Map.ofEntries(
                    Map.entry("hexEncode", List.of("hexEncode(String s) -> String")),
                    Map.entry("hexDecode", List.of("hexDecode(String s) -> String")),
                    Map.entry("base64Encode", List.of("base64Encode(String s) -> String")),
                    Map.entry("base64Decode", List.of("base64Decode(String s) -> String")),
                    Map.entry("urlEncode", List.of("urlEncode(String s) -> String")),
                    Map.entry("urlDecode", List.of("urlDecode(String s) -> String")),
                    Map.entry("base64UrlEncode", List.of("base64UrlEncode(String s) -> String")),
                    Map.entry("base64UrlDecode", List.of("base64UrlDecode(String s) -> String")))),
            Map.entry("strings", java.util.Map.ofEntries(
                    Map.entry("isAlpha", List.of("isAlpha(String s) -> Bool")),
                    Map.entry("isNumeric", List.of("isNumeric(String s) -> Bool")),
                    Map.entry("isAlphaNumeric", List.of("isAlphaNumeric(String s) -> Bool")),
                    Map.entry("isAscii", List.of("isAscii(String s) -> Bool")),
                    Map.entry("isUpperCase", List.of("isUpperCase(String s) -> Bool")),
                    Map.entry("isLowerCase", List.of("isLowerCase(String s) -> Bool")),
                    Map.entry("count", List.of("count(String s, String needle) -> Int")),
                    Map.entry("capitalize", List.of("capitalize(String s) -> String")),
                    Map.entry("uncapitalize", List.of("uncapitalize(String s) -> String")),
                    Map.entry("reverse", List.of("reverse(String s) -> String")),
                    Map.entry("toCamelCase", List.of("toCamelCase(String s) -> String")),
                    Map.entry("toPascalCase", List.of("toPascalCase(String s) -> String")),
                    Map.entry("toSnakeCase", List.of("toSnakeCase(String s) -> String")),
                    Map.entry("toKebabCase", List.of("toKebabCase(String s) -> String")),
                    Map.entry("slugify", List.of("slugify(String s) -> String")),
                    Map.entry("escapeHtml", List.of("escapeHtml(String s) -> String")),
                    Map.entry("unescapeHtml", List.of("unescapeHtml(String s) -> String")),
                    Map.entry("escapeJson", List.of("escapeJson(String s) -> String")),
                    Map.entry("removeWhitespace", List.of("removeWhitespace(String s) -> String")),
                    Map.entry("normalizeWhitespace", List.of("normalizeWhitespace(String s) -> String")),
                    Map.entry("dedent", List.of("dedent(String s) -> String")),
                    Map.entry("repeat", List.of("repeat(String s, Int n) -> String")),
                    Map.entry("truncate", List.of("truncate(String s, Int n) -> String")),
                    Map.entry("indent", List.of("indent(String s, Int n) -> String")),
                    Map.entry("padLeft", List.of("padLeft(String s, Int n, String pad) -> String")),
                    Map.entry("padRight", List.of("padRight(String s, Int n, String pad) -> String")))),
            Map.entry("math", java.util.Map.ofEntries(
                    Map.entry("abs", List.of("abs(Int n) -> Int")),
                    Map.entry("sign", List.of("sign(Int n) -> Int")),
                    Map.entry("clamp", List.of("clamp(Int v, Int lo, Int hi) -> Int")),
                    Map.entry("min", List.of("min(Int a, Int b) -> Int")),
                    Map.entry("max", List.of("max(Int a, Int b) -> Int")),
                    Map.entry("isEven", List.of("isEven(Int n) -> Bool")),
                    Map.entry("isOdd", List.of("isOdd(Int n) -> Bool")),
                    Map.entry("isPositive", List.of("isPositive(Int n) -> Bool")),
                    Map.entry("isNegative", List.of("isNegative(Int n) -> Bool")),
                    Map.entry("isZero", List.of("isZero(Int n) -> Bool")),
                    Map.entry("sqrt", List.of("sqrt(Double x) -> Double")),
                    Map.entry("sin", List.of("sin(Double x) -> Double")),
                    Map.entry("cos", List.of("cos(Double x) -> Double")),
                    Map.entry("tan", List.of("tan(Double x) -> Double")),
                    Map.entry("asin", List.of("asin(Double x) -> Double")),
                    Map.entry("acos", List.of("acos(Double x) -> Double")),
                    Map.entry("atan", List.of("atan(Double x) -> Double")),
                    Map.entry("atan2", List.of("atan2(Double y, Double x) -> Double")),
                    Map.entry("toRadians", List.of("toRadians(Double angdeg) -> Double")),
                    Map.entry("toDegrees", List.of("toDegrees(Double angrad) -> Double")),
                    Map.entry("pi", List.of("pi() -> Double")),
                    Map.entry("e", List.of("e() -> Double")),
                    Map.entry("tau", List.of("tau() -> Double")),
                    Map.entry("lerp", List.of("lerp(Double a, Double b, Double t) -> Double")),
                    Map.entry("percentage", List.of("percentage(Double part, Double whole) -> Double")),
                    Map.entry("isInteger", List.of("isInteger(Double x) -> Bool")),
                    Map.entry("isDecimal", List.of("isDecimal(Double x) -> Bool")),
                    Map.entry("roundTo", List.of("roundTo(Double v, Int decimals) -> Double")),
                    Map.entry("pow", List.of("pow(Double base, Double exp) -> Double")),
                    Map.entry("parseInt", List.of("parseInt(String s) -> Int")),
                    Map.entry("parseLong", List.of("parseLong(String s) -> Long")),
                    Map.entry("parseDouble", List.of("parseDouble(String s) -> Double")),
                    Map.entry("parseIntOrDefault", List.of("parseIntOrDefault(String s, Int d) -> Int")),
                    Map.entry("parseLongOrDefault", List.of("parseLongOrDefault(String s, Long d) -> Long")),
                    Map.entry("parseDoubleOrDefault", List.of("parseDoubleOrDefault(String s, Double d) -> Double")))),
            Map.entry("log", java.util.Map.ofEntries(
                    Map.entry("debug", List.of("debug(String msg) -> void")),
                    Map.entry("info", List.of("info(String msg) -> void")),
                    Map.entry("warn", List.of("warn(String msg) -> void")),
                    Map.entry("error", List.of("error(String msg) -> void")))),
            Map.entry("orm", java.util.Map.ofEntries(
                    Map.entry("create", List.of("create(String entity) -> Bool")),
                    Map.entry("save", List.of("save(String entity, Object row) -> Object")),
                    Map.entry("find", List.of("find(String entity, Object id) -> Object")),
                    Map.entry("all", List.of("all(String entity) -> List")),
                    Map.entry("delete", List.of("delete(String entity, Object id) -> Bool")),
                    Map.entry("count", List.of("count(String entity) -> Long", "count(String entity, String where, Object value) -> Long")),
                    Map.entry("deleteAll", List.of("deleteAll(String entity) -> Bool")),
                    Map.entry("where", List.of("where(String entity, String cond, Object value) -> List", "where(String entity, String col, String op, Object value) -> List")),
                    Map.entry("saveAll", List.of("saveAll(String entity, List rows) -> Bool")),
                    Map.entry("page", List.of("page(String entity, Object limit, Object offset) -> List")),
                    Map.entry("migrate", List.of("migrate(String url, String user, String pass) -> Bool")))),
            Map.entry("config", java.util.Map.ofEntries(
                    Map.entry("get", List.of("get(String key) -> String")),
                    Map.entry("env", List.of("env(String key) -> String")),
                    Map.entry("has", List.of("has(String key) -> Bool")),
                    Map.entry("str", List.of("str(String key, String d) -> String")),
                    Map.entry("int", List.of("int(String key, Int d) -> Int")),
                    Map.entry("long", List.of("long(String key, Long d) -> Long")),
                    Map.entry("bool", List.of("bool(String key, Bool d) -> Bool")),
                    Map.entry("required", List.of("required(String key) -> String")))),
            Map.entry("gpu", java.util.Map.ofEntries(
                    Map.entry("available", List.of("available() -> Bool")),
                    Map.entry("failReason", List.of("failReason() -> String")),
                    Map.entry("dispatchMatmul", List.of("dispatchMatmul(Array<Int> a, Array<Int> b, Array<Int> c, Int m, Int n, Int k) -> Int")),
                    Map.entry("dispatchMatmul64", List.of("dispatchMatmul64(Array<Long> a, Array<Long> b, Array<Long> c, Int m, Int n, Int k) -> Int")),
                    Map.entry("mvSetShape", List.of("mvSetShape(Int rows, Int cols) -> Int")),
                    Map.entry("mvLoadW", List.of("mvLoadW(Array<Long> w, Int rows, Int cols) -> Int")),
                    Map.entry("mvMatvec", List.of("mvMatvec(Array<Long> w, Array<Long> x, Int rows, Int cols) -> Int")),
                    Map.entry("mvPutW", List.of("mvPutW(Int slot, Array<Long> w, Int rows, Int cols) -> Int")),
                    Map.entry("mvRun", List.of("mvRun(Int slot, Array<Long> x, Array<Long> out, Int rows, Int cols, Long reserved) -> Int")),
                    Map.entry("mvPut32", List.of("mvPut32(Int slot, Array<Int> w, Int rows, Int cols) -> Int")),
                    Map.entry("mvRun32", List.of("mvRun32(Int slot, Array<Long> x, Array<Long> out, Int rows, Int cols, Long reserved) -> Int")),
                    Map.entry("mvPutSp", List.of("mvPutSp(Int slot, Array<Int> w, Array<Int> sp, Int rows, Int cols) -> Int")),
                    Map.entry("mvRunSp", List.of("mvRunSp(Int slot, Array<Long> x, Array<Long> out, Int rows, Int cols, Long reserved) -> Int")))),
            Map.entry("mq", java.util.Map.ofEntries(
                    Map.entry("publish", List.of("publish(String topic, Object payload) -> void")),
                    Map.entry("subscribe", List.of("subscribe(String topic, Object handler) -> void")),
                    Map.entry("unsubscribe", List.of("unsubscribe(String topic, Object handler) -> void")),
                    Map.entry("queue", List.of("queue() -> String")),
                    Map.entry("push", List.of("push(String queue, Object value) -> void")),
                    Map.entry("pop", List.of("pop(String queue) -> Object")),
                    Map.entry("queueSize", List.of("queueSize(String queue) -> Int")))),
            Map.entry("validation", java.util.Map.ofEntries(
                    Map.entry("required", List.of("required(String s) -> Bool")),
                    Map.entry("notBlank", List.of("notBlank(String s) -> Bool")),
                    Map.entry("minLength", List.of("minLength(String s, Int n) -> Bool")),
                    Map.entry("maxLength", List.of("maxLength(String s, Int n) -> Bool")),
                    Map.entry("lengthBetween", List.of("lengthBetween(String s, Int lo, Int hi) -> Bool")),
                    Map.entry("isEmail", List.of("isEmail(String s) -> Bool")),
                    Map.entry("isUrl", List.of("isUrl(String s) -> Bool")),
                    Map.entry("matches", List.of("matches(String s, String regex) -> Bool")),
                    Map.entry("isInt", List.of("isInt(String s) -> Bool")),
                    Map.entry("isLong", List.of("isLong(String s) -> Bool")),
                    Map.entry("inRange", List.of("inRange(Int v, Int lo, Int hi) -> Bool")),
                    Map.entry("min", List.of("min(Int v, Int min) -> Bool")),
                    Map.entry("max", List.of("max(Int v, Int max) -> Bool")),
                    Map.entry("formatCpf", List.of("formatCpf(String s) -> String")),
                    Map.entry("formatCep", List.of("formatCep(String s) -> String")),
                    Map.entry("formatCnpj", List.of("formatCnpj(String s) -> String")),
                    Map.entry("isCpf", List.of("isCpf(String s) -> Bool")),
                    Map.entry("isCnpj", List.of("isCnpj(String s) -> Bool")),
                    Map.entry("isCep", List.of("isCep(String s) -> Bool")),
                    Map.entry("isPis", List.of("isPis(String s) -> Bool")),
                    Map.entry("isNis", List.of("isNis(String s) -> Bool")),
                    Map.entry("isIpv4", List.of("isIpv4(String s) -> Bool")),
                    Map.entry("isMac", List.of("isMac(String s) -> Bool")),
                    Map.entry("isPort", List.of("isPort(Int p) -> Bool")),
                    Map.entry("isCreditCard", List.of("isCreditCard(String s) -> Bool")),
                    Map.entry("isIpv6", List.of("isIpv6(String s) -> Bool")),
                    Map.entry("creditCardBrand", List.of("creditCardBrand(String card) -> String")),
                    Map.entry("last4", List.of("last4(String card) -> String")),
                    Map.entry("isDomain", List.of("isDomain(String s) -> Bool")))),
            Map.entry("observability", java.util.Map.ofEntries(
                    Map.entry("health", List.of("health() -> String")),
                    Map.entry("readiness", List.of("readiness() -> Bool")),
                    Map.entry("liveness", List.of("liveness() -> Bool")),
                    Map.entry("counter", List.of("counter(String name) -> Int")),
                    Map.entry("increment", List.of("increment(String name, Int by) -> Int")),
                    Map.entry("gauge", List.of("gauge(String name, Int value) -> void")),
                    Map.entry("histogram", List.of("histogram(String name, Int value) -> void")),
                    Map.entry("metrics", List.of("metrics() -> String")),
                    Map.entry("requestId", List.of("requestId() -> String")),
                    Map.entry("correlationId", List.of("correlationId() -> String")),
                    Map.entry("traceId", List.of("traceId() -> String")),
                    Map.entry("spanId", List.of("spanId() -> String")),
                    Map.entry("spanStart", List.of("spanStart(String name) -> String")),
                    Map.entry("spanEnd", List.of("spanEnd(String id) -> String")),
                    Map.entry("exportSpans", List.of("exportSpans() -> String")))),
            Map.entry("tetris", java.util.Map.ofEntries(
                    Map.entry("run", List.of("run() -> void")))),
            Map.entry("passwords", java.util.Map.ofEntries(
                    Map.entry("hash", List.of("hash(String plain) -> String")),
                    Map.entry("verify", List.of("verify(String plain, String hash) -> Bool")),
                    Map.entry("needsRehash", List.of("needsRehash(String hash) -> Bool")))),
            Map.entry("crypto", java.util.Map.ofEntries(
                    Map.entry("sha256", List.of("sha256(String s) -> String")),
                    Map.entry("sha512", List.of("sha512(String s) -> String")),
                    Map.entry("hmacSha256", List.of("hmacSha256(String key, String msg) -> String", "hmacSha256(KeyHandle key, String msg) -> String")),
                    Map.entry("encryptAesGcm", List.of("encryptAesGcm(String plain, String keyHex64) -> String", "encryptAesGcm(String plain, KeyHandle key) -> String")),
                    Map.entry("decryptAesGcm", List.of("decryptAesGcm(String cipher, String keyHex64) -> String", "decryptAesGcm(String cipher, KeyHandle key) -> String")),
                    Map.entry("encryptChacha20", List.of("encryptChacha20(String plain, String keyHex) -> String", "encryptChacha20(String plain, KeyHandle key) -> String")),
                    Map.entry("decryptChacha20", List.of("decryptChacha20(String cipher, String keyHex) -> String", "decryptChacha20(String cipher, KeyHandle key) -> String")),
                    Map.entry("randomHex", List.of("randomHex(Int n) -> String")),
                    Map.entry("randomInt", List.of("randomInt(Int max) -> Int")))),
            Map.entry("jwt", java.util.Map.ofEntries(
                    Map.entry("create", List.of("create(String claims, String secret) -> String", "create(String claims, String secret, Int ttlSeconds) -> String", "create(String claims, KeyHandle key) -> String", "create(String claims, KeyHandle key, Int ttlSeconds) -> String")),
                    Map.entry("verify", List.of("verify(String token, String secret) -> String", "verify(String token, String secret, String iss, String aud) -> String", "verify(String token, KeyHandle key) -> String", "verify(String token, KeyHandle key, String iss, String aud) -> String")),
                    Map.entry("secret", List.of("secret() -> String")))),
            Map.entry("secrets", java.util.Map.ofEntries(
                    Map.entry("get", List.of("get(String key) -> String", "get(String key, String d) -> String")),
                    Map.entry("redact", List.of("redact(String s) -> String")),
                    Map.entry("of", List.of("of(String literal) -> Secret")),
                    Map.entry("secret", List.of("secret(String name) -> Secret")),
                    Map.entry("fromBytes", List.of("fromBytes(Int[] bytes) -> Secret")),
                    Map.entry("keyFromHex", List.of("keyFromHex(String hex) -> KeyHandle")),
                    Map.entry("keyFromPem", List.of("keyFromPem(String path) -> KeyHandle")),
                    Map.entry("keyFromKeystore", List.of("keyFromKeystore(String path, String alias, String password) -> KeyHandle")))),
            Map.entry("security", java.util.Map.ofEntries(
                    Map.entry("constantTimeEquals", List.of("constantTimeEquals(String a, String b) -> Bool")),
                    Map.entry("randomHex", List.of("randomHex(Int n) -> String")),
                    Map.entry("redact", List.of("redact(String s) -> String")),
                    Map.entry("randomInt", List.of("randomInt(Int max) -> Int")),
                    Map.entry("csrfToken", List.of("csrfToken() -> String")),
                    Map.entry("csrfValid", List.of("csrfValid(String token) -> Bool")),
                    Map.entry("corsAllowed", List.of("corsAllowed(String origin, String allowed) -> Bool")),
                    Map.entry("cspHeader", List.of("cspHeader() -> String")),
                    Map.entry("hstsHeader", List.of("hstsHeader() -> String")),
                    Map.entry("contentTypeOptionsHeader", List.of("contentTypeOptionsHeader() -> String")),
                    Map.entry("frameHeader", List.of("frameHeader() -> String")),
                    Map.entry("referrerHeader", List.of("referrerHeader() -> String")),
                    Map.entry("rateLimit", List.of("rateLimit(String key, Int limit, Int windowSeconds) -> Bool")),
                    Map.entry("sessionCreate", List.of("sessionCreate(String data) -> String")),
                    Map.entry("sessionGet", List.of("sessionGet(String id) -> String")),
                    Map.entry("sessionDestroy", List.of("sessionDestroy(String id) -> Bool")),
                    Map.entry("apiKeyGenerate", List.of("apiKeyGenerate() -> String")),
                    Map.entry("apiKeyValid", List.of("apiKeyValid(String key) -> Bool")),
                    Map.entry("cookieSet", List.of("cookieSet(String name, String value) -> String", "cookieSet(String name, String value, Map opts) -> String")),
                    Map.entry("cookieGet", List.of("cookieGet(String cookieHeader, String name) -> String")))),
            Map.entry("auth", java.util.Map.ofEntries(
                    Map.entry("secret", List.of("secret(String s) -> Bool")),
                    Map.entry("token", List.of("token() -> String")),
                    Map.entry("authenticated", List.of("authenticated() -> Bool")),
                    Map.entry("claims", List.of("claims() -> String")),
                    Map.entry("user", List.of("user() -> String")),
                    Map.entry("hasRole", List.of("hasRole(String role) -> Bool")),
                    Map.entry("hasPermission", List.of("hasPermission(String perm) -> Bool")),
                    Map.entry("resourceServer", List.of("resourceServer(String a, String b, String c) -> Bool")),
                    Map.entry("resourceServerVerify", List.of("resourceServerVerify(String token) -> String")))),
            Map.entry("Image", java.util.Map.ofEntries(
                    Map.entry("open", List.of("open(String path) -> ImageData")))),
            Map.entry("Audio", java.util.Map.ofEntries(
                    Map.entry("openWav", List.of("openWav(String path) -> Audio")))),
            Map.entry("Video", java.util.Map.ofEntries(
                    Map.entry("open", List.of("open(String path) -> Video")))),
            Map.entry("json", java.util.Map.ofEntries(
                    Map.entry("encode", List.of("encode(value) -> String")),
                    Map.entry("decode", List.of("decode<T>(jsonString) -> T")))),
            Map.entry("Mic", java.util.Map.ofEntries(
                    Map.entry("record", List.of("record(Int seconds) -> Audio")),
                    Map.entry("list", List.of("list() -> List")))));

    /** Overloads gravados do membro (vazio = sem tabela ainda; nunca chute, R6). */
    public static List<String> signaturesOf(String ns, String member) {
        var m = SIGNATURES.get(ns);
        return m == null ? List.of() : m.getOrDefault(member, List.of());
    }

    public static Set<String> namespaces() { return MEMBERS.keySet(); }

    public static boolean isNamespace(String ns) { return MEMBERS.containsKey(ns); }

    /** Membros na ordem declarada no typer (estável p/ UI). */
    public static List<String> membersOf(String ns) {
        return MEMBERS.getOrDefault(ns, List.of());
    }
}
