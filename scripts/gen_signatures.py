#!/usr/bin/env python3
"""LSP-A: regenera o literal SIGNATURES de StdCatalog.java (fonte unica das
assinaturas de hover). A tabela e um artefato de codigo: cada forma foi
MEDIDA no dispatcher real do ns e travada comportamento-a-`staticCall` em
`StdCatalogSignaturesTest`. Membro sem tabela mantem hover simples (R6).

Uso: python3 scripts/gen_signatures.py check|write
"""
import io
import re
import sys

CAT = "kof-compiler/src/main/java/dev/kof/compiler/StdCatalog.java"

# Blocos por fatia LSP-A (1-4 vivem EXTRAIDOS do arquivo; 5+ declarados aqui)
BASE = {
    "db": [
        ("connect", ['connect(String url) -> String', 'connect(String url, String user, String pass) -> String']),
        ("query", ['query(String url, String sql) -> List<String>', 'query(String url, String sql, Object... binds[1..4]) -> List<String>']),
        ("execute", ['execute(String url, String sql) -> Int', 'execute(String url, String sql, Object... binds[1..4]) -> Int']),
        ("close", ['close(String url) -> void']),
        ("transaction", ['transaction(callback) -> void']),
    ],
    "http": [
        ("get", ['get(String url) -> String', 'get(String url, String headers...) -> String']),
        ("delete", ['delete(String url) -> String', 'delete(String url, String headers...) -> String']),
        ("options", ['options(String url) -> String', 'options(String url, String headers...) -> String']),
        ("post", ['post(String url, String body) -> String', 'post(String url, String body, String headers...) -> String']),
        ("put", ['put(String url, String body) -> String', 'put(String url, String body, String headers...) -> String']),
        ("patch", ['patch(String url, String body) -> String', 'patch(String url, String body, String headers...) -> String']),
        ("status", ['status(String url) -> Int']),
        ("timeout", ['timeout(Int ms) -> void']),
        ("retry", ['retry(Int count) -> void']),
        ("circuit", ['circuit(Int threshold) -> void']),
    ],
    "time": [
        ("sleep", ['sleep(Int ms) -> void']),
        ("now", ['now() -> Long']),
        ("collect", ['collect() -> void']),
        ("interval", ['interval(Int ms, callback) -> String']),
        ("cancel", ['cancel(String id) -> void']),
        ("isLeapYear", ['isLeapYear(Int year) -> Bool']),
        ("daysInMonth", ['daysInMonth(Int year, Int month) -> Int']),
        ("dayOfWeek", ['dayOfWeek(Int y, Int m, Int d) -> Int']),
        ("isWeekend", ['isWeekend(Int y, Int m, Int d) -> Bool']),
        ("daysBetween", ['daysBetween(Int y1, Int m1, Int d1, Int y2, Int m2, Int d2) -> Int']),
        ("age", ['age(Int by, Int bm, Int bd, Int ry, Int rm, Int rd) -> Int']),
        ("isToday", ['isToday(Int y, Int m, Int d) -> Bool']),
        ("addDays", ['addDays(String iso, Int days) -> String']),
        ("addMonths", ['addMonths(String iso, Int months) -> String']),
        ("addYears", ['addYears(String iso, Int years) -> String']),
        ("startOf", ['startOf(String iso, String unit) -> String']),
        ("endOf", ['endOf(String iso, String unit) -> String']),
        ("diffDays", ['diffDays(String isoA, String isoB) -> Int']),
        ("todayIso", ['todayIso() -> String']),
        ("formatDateIso", ['formatDateIso(Int y, Int m, Int d) -> String']),
        ("parseDateIso", ['parseDateIso(String iso) -> Int']),
        ("tzOffsetSeconds", ['tzOffsetSeconds() -> Int']),
        ("hoursBetween", ['hoursBetween(Int y1, Int m1, Int d1, Int h1, Int y2, Int m2, Int d2, Int h2) -> Int']),
    ],
    "cache": [
        ("get", ['get(String key) -> String']),
        ("set", ['set(String key, String value) -> void', 'set(String key, String value, Int ttlSeconds) -> void']),
        ("ttl", ['ttl(String key) -> Int']),
        ("delete", ['delete(String key) -> void']),
        ("clear", ['clear() -> void']),
    ],
    "process": [
        ("run", ['run(String program, String... args) -> Result']),
        ("spawn", ['spawn(String program, String... args) -> Handle']),
        ("exit", ['exit(Int code) -> void']),
    ],
    "shell": [
        ("cmd", ['cmd(String program, List<String> args) -> List<String>']),
        ("run", ['run(String program) -> Result', 'run(String program, List<String> args) -> Result']),
        ("runWith", ['runWith(List<String> argv, String cwd, Map<String,String> env) -> Result']),
        ("pipeline", ['pipeline(List<List<String>> stages) -> Result']),
        ("ok", ['ok(result) -> Bool']),
    ],
    "ssh": [
        ("cmd", ['cmd(String host, String command) -> List<String>']),
        ("run", ['run(String host, String command) -> Result']),
        ("ok", ['ok(result) -> Bool']),
    ],
    "net": [
        ("scheme", ['scheme(String url) -> String']),
        ("host", ['host(String url) -> String']),
        ("port", ['port(String url) -> String']),
        ("path", ['path(String url) -> String']),
        ("query", ['query(String url) -> String']),
        ("fragment", ['fragment(String url) -> String']),
        ("queryEncode", ['queryEncode(String s) -> String']),
        ("queryDecode", ['queryDecode(String s) -> String']),
        ("listen", ['listen(Int port) -> Listener']),
        ("connect", ['connect(String host, Int port) -> Conn', 'connect(String host, Int port, String address) -> Conn']),
        ("bind", ['bind(Int port) -> Endpoint']),
        ("resolve", ['resolve(String host) -> List<String>']),
    ],
    "uuid": [
        ("isUuid", ['isUuid(String s) -> Bool']),
        ("v4", ['v4() -> String']),
        ("v7", ['v7() -> String']),
    ],
    "random": [
        ("double", ['double() -> Double']),
        ("boolean", ['boolean() -> Bool']),
        ("int", ['int(Int n) -> Int']),
        ("hex", ['hex(Int n) -> String']),
        ("randomBytesHex", ['randomBytesHex(Int n) -> String']),
        ("randomInt", ['randomInt(Int n) -> Int']),
        ("randomBoolean", ['randomBoolean() -> Bool']),
        ("randomString", ['randomString(Int n, String s) -> String']),
    ],
    "rng": [
        ("seed", ['seed(Int n) -> void']),
        ("int", ['int(Int n) -> Int']),
        ("boolean", ['boolean() -> Bool']),
        ("double", ['double() -> Double']),
        ("string", ['string(Int n, String s) -> String']),
    ],
    "encoding": [
        ("hexEncode", ['hexEncode(String s) -> String']),
        ("hexDecode", ['hexDecode(String s) -> String']),
        ("base64Encode", ['base64Encode(String s) -> String']),
        ("base64Decode", ['base64Decode(String s) -> String']),
        ("urlEncode", ['urlEncode(String s) -> String']),
        ("urlDecode", ['urlDecode(String s) -> String']),
        ("base64UrlEncode", ['base64UrlEncode(String s) -> String']),
        ("base64UrlDecode", ['base64UrlDecode(String s) -> String']),
    ],
    "strings": [
        ("isAlpha", ['isAlpha(String s) -> Bool']),
        ("isNumeric", ['isNumeric(String s) -> Bool']),
        ("isAlphaNumeric", ['isAlphaNumeric(String s) -> Bool']),
        ("isAscii", ['isAscii(String s) -> Bool']),
        ("isUpperCase", ['isUpperCase(String s) -> Bool']),
        ("isLowerCase", ['isLowerCase(String s) -> Bool']),
        ("count", ['count(String s, String needle) -> Int']),
        ("capitalize", ['capitalize(String s) -> String']),
        ("uncapitalize", ['uncapitalize(String s) -> String']),
        ("reverse", ['reverse(String s) -> String']),
        ("toCamelCase", ['toCamelCase(String s) -> String']),
        ("toPascalCase", ['toPascalCase(String s) -> String']),
        ("toSnakeCase", ['toSnakeCase(String s) -> String']),
        ("toKebabCase", ['toKebabCase(String s) -> String']),
        ("slugify", ['slugify(String s) -> String']),
        ("escapeHtml", ['escapeHtml(String s) -> String']),
        ("unescapeHtml", ['unescapeHtml(String s) -> String']),
        ("escapeJson", ['escapeJson(String s) -> String']),
        ("removeWhitespace", ['removeWhitespace(String s) -> String']),
        ("normalizeWhitespace", ['normalizeWhitespace(String s) -> String']),
        ("dedent", ['dedent(String s) -> String']),
        ("repeat", ['repeat(String s, Int n) -> String']),
        ("truncate", ['truncate(String s, Int n) -> String']),
        ("indent", ['indent(String s, Int n) -> String']),
        ("padLeft", ['padLeft(String s, Int n, String pad) -> String']),
        ("padRight", ['padRight(String s, Int n, String pad) -> String']),
    ],
    # fatia 4
    "math": [
        ("abs", ['abs(Int n) -> Int']),
        ("sign", ['sign(Int n) -> Int']),
        ("clamp", ['clamp(Int v, Int lo, Int hi) -> Int']),
        ("min", ['min(Int a, Int b) -> Int']),
        ("max", ['max(Int a, Int b) -> Int']),
        ("isEven", ['isEven(Int n) -> Bool']),
        ("isOdd", ['isOdd(Int n) -> Bool']),
        ("isPositive", ['isPositive(Int n) -> Bool']),
        ("isNegative", ['isNegative(Int n) -> Bool']),
        ("isZero", ['isZero(Int n) -> Bool']),
        ("sqrt", ['sqrt(Double x) -> Double']),
        ("sin", ['sin(Double x) -> Double']),
        ("cos", ['cos(Double x) -> Double']),
        ("tan", ['tan(Double x) -> Double']),
        ("asin", ['asin(Double x) -> Double']),
        ("acos", ['acos(Double x) -> Double']),
        ("atan", ['atan(Double x) -> Double']),
        ("atan2", ['atan2(Double y, Double x) -> Double']),
        ("toRadians", ['toRadians(Double angdeg) -> Double']),
        ("toDegrees", ['toDegrees(Double angrad) -> Double']),
        ("pi", ['pi() -> Double']),
        ("e", ['e() -> Double']),
        ("tau", ['tau() -> Double']),
        ("lerp", ['lerp(Double a, Double b, Double t) -> Double']),
        ("percentage", ['percentage(Double part, Double whole) -> Double']),
        ("isInteger", ['isInteger(Double x) -> Bool']),
        ("isDecimal", ['isDecimal(Double x) -> Bool']),
        ("roundTo", ['roundTo(Double v, Int decimals) -> Double']),
        ("pow", ['pow(Double base, Double exp) -> Double']),
        ("parseInt", ['parseInt(String s) -> Int']),
        ("parseLong", ['parseLong(String s) -> Long']),
        ("parseDouble", ['parseDouble(String s) -> Double']),
        ("parseIntOrDefault", ['parseIntOrDefault(String s, Int d) -> Int']),
        ("parseLongOrDefault", ['parseLongOrDefault(String s, Long d) -> Long']),
        ("parseDoubleOrDefault", ['parseDoubleOrDefault(String s, Double d) -> Double']),
    ],
    "log": [
        ("debug", ['debug(String msg) -> void']),
        ("info", ['info(String msg) -> void']),
        ("warn", ['warn(String msg) -> void']),
        ("error", ['error(String msg) -> void']),
    ],
}

EXTRA_FATIAS = {
    "orm": [
        ("create", ["create(String entity) -> Bool"]),
        ("save", ["save(String entity, Object row) -> Object"]),
        ("find", ["find(String entity, Object id) -> Object"]),
        ("all", ["all(String entity) -> List"]),
        ("delete", ["delete(String entity, Object id) -> Bool"]),
        ("count", ["count(String entity) -> Long",
                   "count(String entity, String where, Object value) -> Long"]),
        ("deleteAll", ["deleteAll(String entity) -> Bool"]),
        ("where", ["where(String entity, String cond, Object value) -> List",
                   "where(String entity, String col, String op, Object value) -> List"]),
        ("saveAll", ["saveAll(String entity, List rows) -> Bool"]),
        ("page", ["page(String entity, Object limit, Object offset) -> List"]),
        ("migrate", ["migrate(String url, String user, String pass) -> Bool"]),
    ],
    "config": [
        ("get", ["get(String key) -> String"]),
        ("env", ["env(String key) -> String"]),
        ("has", ["has(String key) -> Bool"]),
        ("str", ["str(String key, String d) -> String"]),
        ("int", ["int(String key, Int d) -> Int"]),
        ("long", ["long(String key, Long d) -> Long"]),
        ("bool", ["bool(String key, Bool d) -> Bool"]),
        ("required", ["required(String key) -> String"]),
    ],
    "gpu": [
        ("available", ["available() -> Bool"]),
        ("failReason", ["failReason() -> String"]),
        ("dispatchMatmul", ["dispatchMatmul(Array<Int> a, Array<Int> b, Array<Int> c, Int m, Int n, Int k) -> Int"]),
        ("dispatchMatmul64", ["dispatchMatmul64(Array<Long> a, Array<Long> b, Array<Long> c, Int m, Int n, Int k) -> Int"]),
        ("mvSetShape", ["mvSetShape(Int rows, Int cols) -> Int"]),
        ("mvLoadW", ["mvLoadW(Array<Long> w, Int rows, Int cols) -> Int"]),
        ("mvMatvec", ["mvMatvec(Array<Long> w, Array<Long> x, Int rows, Int cols) -> Int"]),
        ("mvPutW", ["mvPutW(Int slot, Array<Long> w, Int rows, Int cols) -> Int"]),
        ("mvRun", ["mvRun(Int slot, Array<Long> x, Array<Long> out, Int rows, Int cols, Long reserved) -> Int"]),
        ("mvPut32", ["mvPut32(Int slot, Array<Int> w, Int rows, Int cols) -> Int"]),
        ("mvRun32", ["mvRun32(Int slot, Array<Long> x, Array<Long> out, Int rows, Int cols, Long reserved) -> Int"]),
        ("mvPutSp", ["mvPutSp(Int slot, Array<Int> w, Array<Int> sp, Int rows, Int cols) -> Int"]),
        ("mvRunSp", ["mvRunSp(Int slot, Array<Long> x, Array<Long> out, Int rows, Int cols, Long reserved) -> Int"]),
    ],
    "mq": [
        ("publish", ["publish(String topic, Object payload) -> void"]),
        ("subscribe", ["subscribe(String topic, Object handler) -> void"]),
        ("unsubscribe", ["unsubscribe(String topic, Object handler) -> void"]),
        ("queue", ["queue() -> String"]),
        ("push", ["push(String queue, Object value) -> void"]),
        ("pop", ["pop(String queue) -> Object"]),
        ("queueSize", ["queueSize(String queue) -> Int"]),
    ],
    "validation": [
        ("required", ["required(String s) -> Bool"]),
        ("notBlank", ["notBlank(String s) -> Bool"]),
        ("minLength", ["minLength(String s, Int n) -> Bool"]),
        ("maxLength", ["maxLength(String s, Int n) -> Bool"]),
        ("lengthBetween", ["lengthBetween(String s, Int lo, Int hi) -> Bool"]),
        ("isEmail", ["isEmail(String s) -> Bool"]),
        ("isUrl", ["isUrl(String s) -> Bool"]),
        ("matches", ["matches(String s, String regex) -> Bool"]),
        ("isInt", ["isInt(String s) -> Bool"]),
        ("isLong", ["isLong(String s) -> Bool"]),
        ("inRange", ["inRange(Int v, Int lo, Int hi) -> Bool"]),
        ("min", ["min(Int v, Int min) -> Bool"]),
        ("max", ["max(Int v, Int max) -> Bool"]),
        ("formatCpf", ["formatCpf(String s) -> String"]),
        ("formatCep", ["formatCep(String s) -> String"]),
        ("formatCnpj", ["formatCnpj(String s) -> String"]),
        ("isCpf", ["isCpf(String s) -> Bool"]),
        ("isCnpj", ["isCnpj(String s) -> Bool"]),
        ("isCep", ["isCep(String s) -> Bool"]),
        ("isPis", ["isPis(String s) -> Bool"]),
        ("isNis", ["isNis(String s) -> Bool"]),
        ("isIpv4", ["isIpv4(String s) -> Bool"]),
        ("isMac", ["isMac(String s) -> Bool"]),
        ("isPort", ["isPort(Int p) -> Bool"]),
        ("isCreditCard", ["isCreditCard(String s) -> Bool"]),
        ("isIpv6", ["isIpv6(String s) -> Bool"]),
        ("creditCardBrand", ["creditCardBrand(String card) -> String"]),
        ("last4", ["last4(String card) -> String"]),
        ("isDomain", ["isDomain(String s) -> Bool"]),
    ],
    "observability": [
        ("health", ["health() -> String"]),
        ("readiness", ["readiness() -> Bool"]),
        ("liveness", ["liveness() -> Bool"]),
        ("counter", ["counter(String name) -> Int"]),
        ("increment", ["increment(String name, Int by) -> Int"]),
        ("gauge", ["gauge(String name, Int value) -> void"]),
        ("histogram", ["histogram(String name, Int value) -> void"]),
        ("metrics", ["metrics() -> String"]),
        ("requestId", ["requestId() -> String"]),
        ("correlationId", ["correlationId() -> String"]),
        ("traceId", ["traceId() -> String"]),
        ("spanId", ["spanId() -> String"]),
        ("spanStart", ["spanStart(String name) -> String"]),
        ("spanEnd", ["spanEnd(String id) -> String"]),
        ("exportSpans", ["exportSpans() -> String"]),
    ],
    "keyExchange": [
        ("privateKey", ["privateKey() -> Secret", "privateKey(String alg) -> Secret"]),
        ("publicKey", ["publicKey(Secret priv) -> String"]),
        ("shared", ["shared(Secret priv, Secret peerPublicHex) -> Secret"]),
        ("hkdfSha256", ["hkdfSha256(Secret ikmHex, String saltHex, String infoHex, Int len) -> String"]),
    ],
    "tetris": [("run", ["run() -> void"])],
    # fatia 6: seguranca (dispatcher aninhado KofSecurity.staticMethod) + media
    "passwords": [
        ("hash", ["hash(String plain) -> String"]),
        ("verify", ["verify(String plain, String hash) -> Bool"]),
        ("needsRehash", ["needsRehash(String hash) -> Bool"]),
    ],
    "crypto": [
        ("sha256", ["sha256(String s) -> String"]),
        ("sha256Bytes", ["sha256Bytes(Byte[] data) -> String"]),
        ("sha512", ["sha512(String s) -> String"]),
        ("hmacSha256", ["hmacSha256(String key, String msg) -> String",
                        "hmacSha256(KeyHandle key, String msg) -> String"]),
        ("hmacSha256Bytes", ["hmacSha256Bytes(Byte[] key, Byte[] msg) -> String"]),
        ("encryptAesGcm", ["encryptAesGcm(String plain, String keyHex64) -> String",
                           "encryptAesGcm(String plain, KeyHandle key) -> String"]),
        ("decryptAesGcm", ["decryptAesGcm(String cipher, String keyHex64) -> String",
                           "decryptAesGcm(String cipher, KeyHandle key) -> String"]),
        ("encryptChacha20", ["encryptChacha20(String plain, String keyHex) -> String",
                             "encryptChacha20(String plain, KeyHandle key) -> String"]),
        ("decryptChacha20", ["decryptChacha20(String cipher, String keyHex) -> String",
                             "decryptChacha20(String cipher, KeyHandle key) -> String"]),
        ("sign", ["sign(Secret priv, Byte[] msg) -> String"]),
        ("verify", ["verify(Secret key, Byte[] msg, String sigHex) -> Bool"]),
        ("randomHex", ["randomHex(Int n) -> String"]),
        ("randomInt", ["randomInt(Int max) -> Int"]),
    ],
    "jwt": [
        ("create", ["create(String claims, String secret) -> String",
                    "create(String claims, String secret, Int ttlSeconds) -> String",
                    "create(String claims, KeyHandle key) -> String",
                    "create(String claims, KeyHandle key, Int ttlSeconds) -> String"]),
        ("verify", ["verify(String token, String secret) -> String",
                    "verify(String token, String secret, String iss, String aud) -> String",
                    "verify(String token, KeyHandle key) -> String",
                    "verify(String token, KeyHandle key, String iss, String aud) -> String"]),
        ("secret", ["secret() -> String"]),
    ],
    "secrets": [
        ("get", ["get(String key) -> String", "get(String key, String d) -> String"]),
        ("redact", ["redact(String s) -> String"]),
        ("of", ["of(String literal) -> Secret"]),
        ("secret", ["secret(String name) -> Secret"]),
        ("fromBytes", ["fromBytes(Int[] bytes) -> Secret"]),
        ("keyFromHex", ["keyFromHex(String hex) -> KeyHandle"]),
        ("keyFromPem", ["keyFromPem(String path) -> KeyHandle"]),
        ("keyFromKeystore", ["keyFromKeystore(String path, String alias, String password) -> KeyHandle"]),
    ],
    "security": [
        ("constantTimeEquals", ["constantTimeEquals(String a, String b) -> Bool"]),
        ("randomHex", ["randomHex(Int n) -> String"]),
        ("redact", ["redact(String s) -> String"]),
        ("randomInt", ["randomInt(Int max) -> Int"]),
        ("csrfToken", ["csrfToken() -> String"]),
        ("csrfValid", ["csrfValid(String token) -> Bool"]),
        ("corsAllowed", ["corsAllowed(String origin, String allowed) -> Bool"]),
        ("cspHeader", ["cspHeader() -> String"]),
        ("hstsHeader", ["hstsHeader() -> String"]),
        ("contentTypeOptionsHeader", ["contentTypeOptionsHeader() -> String"]),
        ("frameHeader", ["frameHeader() -> String"]),
        ("referrerHeader", ["referrerHeader() -> String"]),
        ("rateLimit", ["rateLimit(String key, Int limit, Int windowSeconds) -> Bool"]),
        ("sessionCreate", ["sessionCreate(String data) -> String"]),
        ("sessionGet", ["sessionGet(String id) -> String"]),
        ("sessionDestroy", ["sessionDestroy(String id) -> Bool"]),
        ("apiKeyGenerate", ["apiKeyGenerate() -> String"]),
        ("apiKeyValid", ["apiKeyValid(String key) -> Bool"]),
        ("cookieSet", ["cookieSet(String name, String value) -> String",
                       "cookieSet(String name, String value, Map opts) -> String"]),
        ("cookieGet", ["cookieGet(String cookieHeader, String name) -> String"]),
    ],
    "auth": [
        ("secret", ["secret(String s) -> Bool"]),
        ("token", ["token() -> String"]),
        ("authenticated", ["authenticated() -> Bool"]),
        ("claims", ["claims() -> String"]),
        ("user", ["user() -> String"]),
        ("hasRole", ["hasRole(String role) -> Bool"]),
        ("hasPermission", ["hasPermission(String perm) -> Bool"]),
        ("resourceServer", ["resourceServer(String a, String b, String c) -> Bool"]),
        ("resourceServerVerify", ["resourceServerVerify(String token) -> String"]),
    ],
    "Image": [("open", ["open(String path) -> ImageData"])],
    "Audio": [("openWav", ["openWav(String path) -> Audio"])],
    "Video": [("open", ["open(String path) -> Video"])],
    "json": [
        ("encode", ["encode(value) -> String"]),
        ("decode", ["decode<T>(jsonString) -> T"]),
    ],
    "Mic": [("record", ["record(Int seconds) -> Audio"]),
            ("list", ["list() -> List"])],
}

HEADER = (
    "    private static final Map<String, Map<String, List<String>>> SIGNATURES =\n"
    "            // 19/09 LSP-A fatias 1-6: forma MEDIDA no dispatcher real de cada\n"
    "            // ns e travada comportamento-a-`staticCall` (StdCatalogSignaturesTest).\n"
    "            // Membro sem tabela mantem hover simples (R6: nunca chute).\n"
    "            // ARTEFATO DO GERADOR scripts/gen_signatures.py \u2014 nao editar a m\u00e3o.\n"
    "            java.util.Map.ofEntries(\n")


def match_close(txt, start):
    d, k, inq, esc = 0, start, False, False
    while k < len(txt):
        c = txt[k]
        if esc:
            esc = False
        elif c == "\\":
            esc = True
        elif c == '"':
            inq = not inq
        elif not inq:
            if c == "(":
                d += 1
            elif c == ")":
                d -= 1
                if d == 0:
                    return k
        k += 1
    raise ValueError("parencsese nao fechado")


def extract(s):
    i0 = s.index("private static final Map<String, Map<String, List<String>>> SIGNATURES")
    j0 = s.index("java.util.Map.ofEntries(", i0)
    end = match_close(s, j0)
    body = s[j0 + 1:end]
    groups, pos = [], 0
    while True:
        m = re.compile(r'Map\.entry\("(\w+)", java\.util\.Map\.ofEntries\(').search(body, pos)
        if not m:
            break
        op = body.index("(", m.end() - 1)
        cl = match_close(body, op)
        inner, items, q = body[op + 1:cl], [], 0
        while True:
            mm = re.compile(r'Map\.entry\("(\w+)", List\.of\(').search(inner, q)
            if not mm:
                break
            name = mm.group(1)
            op2 = inner.index("(", mm.end() - 1)
            cl2 = match_close(inner, op2)
            sigs = re.findall(r'"((?:[^"\\]|\\.)*)"', inner[op2 + 1:cl2])
            items.append((name, sigs))
            q = cl2 + 1
        groups.append((m.group(1), items))
        pos = cl + 1
    return groups


def block(ns, items):
    ent = ",\n                    ".join(
        'Map.entry("%s", List.of(%s))' % (k, ", ".join('"%s"' % x for x in sigs))
        for k, sigs in items)
    return '            Map.entry("%s", java.util.Map.ofEntries(\n                    %s))' % (ns, ent)


def render(groups):
    return HEADER + ",\n".join(block(ns, it) for ns, it in groups) + ");\n\n"


def build(cat_path):
    """Le o catalogo, re-renderiza a tabela e devolve (fonte, esperado, grupos, membros, formas)."""
    s = io.open(cat_path, encoding="utf-8").read()
    groups = [(ns, items) for ns, items in list(BASE.items()) + list(EXTRA_FATIAS.items())]
    tabled = sum(len(g) for _, g in groups)
    out = render(groups)
    i0 = s.index("    private static final Map<String, Map<String, List<String>>> SIGNATURES")
    i1 = s.index("    /** Overloads gravados", i0)
    s2 = s[:i0] + out + s[i1:]
    d = 0
    inq = esc = False
    for ch in s2:
        if esc:
            esc = False
        elif ch == "\\":
            esc = True
        elif ch == '"':
            inq = not inq
        elif not inq:
            if ch == "(":
                d += 1
            elif ch == ")":
                d -= 1
    assert d == 0, "profundidade %d — gerador abortado" % d
    ns_ = sum(len(x) for _, g in groups for _, x in g)
    return s, s2, groups, tabled, ns_


def selftest():
    """RED-first do detector: catalogo limpo = rc0; deriva plantada = rc1."""
    import os
    import shutil
    import subprocess
    import tempfile
    s = io.open(CAT, encoding="utf-8").read()
    tmpd = tempfile.mkdtemp(prefix="gensig-selftest.")
    try:
        clean = os.path.join(tmpd, "Clean.java")
        io.open(clean, "w", encoding="utf-8").write(s)
        rc = subprocess.call([sys.executable, os.path.abspath(__file__), "check", "--catalog", clean])
        if rc != 0:
            print("SELFTEST FALHOU: catalogo limpo acusado como deriva")
            return 1
        probe = 'Map.entry("sin", List.of("sin(Double x) -> Double")),'
        planted = s.replace(probe, "", 1)
        if planted == s:
            print("SELFTEST FALHOU: linha-sonda nao encontrada (catalogo mudou)")
            return 1
        bad = os.path.join(tmpd, "Bad.java")
        io.open(bad, "w", encoding="utf-8").write(planted)
        rc = subprocess.call([sys.executable, os.path.abspath(__file__), "check", "--catalog", bad],
                             stderr=subprocess.DEVNULL)
        if rc == 0:
            print("SELFTEST FALHOU: deriva plantada NAO foi detectada")
            return 1
    finally:
        shutil.rmtree(tmpd)
    print("SELFTEST OK: limpo=rc0, deriva plantada=rc1")
    return 0


def main():
    argv = sys.argv[1:]
    if argv and argv[0] == "--selftest":
        return selftest()
    mode = argv[0] if argv and not argv[0].startswith("--") else "check"
    cat = CAT
    if "--catalog" in argv:
        cat = argv[argv.index("--catalog") + 1]
    s, s2, groups, tabled, ns_ = build(cat)
    print("%d ns, %d membros, %d formas" % (len(groups), tabled, ns_))
    if mode == "write":
        if s2 != s:
            io.open(cat, "w", encoding="utf-8").write(s2)
            print("gravado")
        else:
            print("inalterado")
        return 0
    if s2 != s:
        sys.stderr.write("DRIFT: %s nao casa com gen_signatures.py — rode: python3 scripts/gen_signatures.py write\n" % cat)
        return 1
    print("inalterado")
    return 0


if __name__ == "__main__":
    sys.exit(main())
