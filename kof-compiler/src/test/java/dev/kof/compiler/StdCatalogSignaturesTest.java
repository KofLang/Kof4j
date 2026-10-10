package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * LSP-A (19/09): trava COMPORTAMENTAL da tabela de assinaturas do
 * {@link StdCatalog} — cada forma gravada e re-executada contra o dispatcher
 * REAL (KofDb.staticCall / KofHttp.staticCall): aridade gravada binda; a
 * aridade proibida pela fonte (bind 5 do db, zero-arg/sem-body do http) nao
 * binda. A tabela nao e comentario: divergir do dispatcher quebra aqui
 * (mesma filosofia de trava do StdCatalogTest, no nivel da assinatura).
 */
class StdCatalogSignaturesTest {

    private static final Type S = BuiltinTypes.STRING;
    private static final Type I = Type.PrimitiveType.INT;
    private static final Type O = Type.UnknownType.UNKNOWN;
    private static final Type D = Type.PrimitiveType.DOUBLE;

    @Test
    void dbTableBindsAgainstRealDispatcher() {
        assertNotNull(KofDb.staticCall("connect", List.of(S), true), "connect(1)");
        assertNull(KofDb.staticCall("connect", List.of(S, S), true), "connect(2) NAO binda");
        assertNotNull(KofDb.staticCall("connect", List.of(S, S, S), true), "connect(3)");
        assertNotNull(KofDb.staticCall("close", List.of(S), true), "close");
        assertNull(KofDb.staticCall("close", List.of(S, S), true), "close(2)");
        assertNotNull(KofDb.staticCall("transaction", List.of(O), true), "transaction(cb)");
        assertNotNull(KofDb.staticCall("query", List.of(S, S), true), "query sem bind");
        assertNotNull(KofDb.staticCall("query", List.of(S, S, O, O, O, O), true), "query bind 4");
        assertNull(KofDb.staticCall("query", List.of(S, S, O, O, O, O, O), true),
                "query bind 5 excede MAX_BIND");
        assertNotNull(KofDb.staticCall("execute", List.of(S, S), true), "execute sem bind");
        assertNotNull(KofDb.staticCall("execute", List.of(S, S, O), true), "execute bind 1");
        assertNull(KofDb.staticCall("execute", List.of(S), true), "execute 1 arg NAO binda");
        for (String m : List.of("connect", "query", "execute", "close", "transaction")) {
            assertFalse(StdCatalog.signaturesOf("db", m).isEmpty(), "tabela sem db." + m);
            assertTrue(KofDb.functions().contains(m), "catalog db sem " + m);
        }
        assertTrue(StdCatalog.signaturesOf("db", "ghost").isEmpty(), "assinatura fantasma");
    }

    @Test
    void httpTableBindsAgainstRealDispatcher() {
        for (String v : List.of("get", "delete", "options")) {
            assertNotNull(KofHttp.staticCall(v, List.of(S)), v);
            assertNotNull(KofHttp.staticCall(v, List.of(S, S)), v + " headers");
            assertNull(KofHttp.staticCall(v, List.of()), v + " zero-arg NAO binda");
        }
        for (String v : List.of("post", "put", "patch")) {
            assertNotNull(KofHttp.staticCall(v, List.of(S, S)), v);
            assertNotNull(KofHttp.staticCall(v, List.of(S, S, S)), v + " headers");
            assertNull(KofHttp.staticCall(v, List.of(S)), v + " sem body NAO binda");
        }
        assertNotNull(KofHttp.staticCall("status", List.of(S)), "status");
        assertNull(KofHttp.staticCall("status", List.of()), "status sem url");
        for (String c : List.of("timeout", "retry", "circuit")) {
            assertNotNull(KofHttp.staticCall(c, List.of(I)), c);
            assertNull(KofHttp.staticCall(c, List.of()), c + " sem argumento");
        }
        for (String m : KofHttp.functions()) {
            assertFalse(StdCatalog.signaturesOf("http", m).isEmpty(), "tabela sem http." + m);
        }
    }

    @Test
    void timeTableBindsAgainstRealDispatcher() {
        assertNotNull(KofTime.staticCall("sleep", List.of(I)), "sleep(Int)");
        assertNull(KofTime.staticCall("sleep", List.of()), "sleep sem argumento");
        assertNotNull(KofTime.staticCall("now", List.of()), "now()");
        assertNull(KofTime.staticCall("now", List.of(I)), "now nao aceita args");
        assertNotNull(KofTime.staticCall("interval", List.of(I, O)), "interval(Int, cb)");
        assertNull(KofTime.staticCall("interval", List.of(I)), "interval sem cb");
        assertNotNull(KofTime.staticCall("daysBetween", List.of(I, I, I, I, I, I)), "daysBetween(6)");
        assertNull(KofTime.staticCall("daysBetween", List.of(I, I, I, I, I)), "daysBetween(5)");
        assertNotNull(KofTime.staticCall("hoursBetween", List.of(I, I, I, I, I, I, I, I)), "hoursBetween(8)");
        assertNull(KofTime.staticCall("hoursBetween", List.of(I, I, I, I, I, I, I)), "hoursBetween(7)");
        assertNull(KofTime.staticCall("isLeapYear", List.of(S)), "isLeapYear(String) NAO binda (gate de tipo)");
        assertNull(KofTime.staticCall("addDays", List.of(S, S)), "addDays(String,String) NAO binda");
        assertNotNull(KofTime.staticCall("addDays", List.of(S, I)), "addDays(String,Int)");
        assertNotNull(KofTime.staticCall("tzOffsetSeconds", List.of()), "tzOffsetSeconds()");
        for (String m : KofTime.functions())
            assertFalse(StdCatalog.signaturesOf("time", m).isEmpty(), "tabela sem time." + m);
    }

    @Test
    void cacheProcessShellTablesBindAgainstRealDispatchers() {
        assertNotNull(KofCache.staticCall("get", List.of(S)), "cache.get");
        assertNull(KofCache.staticCall("get", List.of()), "cache.get/0");
        assertNotNull(KofCache.staticCall("set", List.of(S, S)), "cache.set/2");
        assertNotNull(KofCache.staticCall("set", List.of(S, S, I)), "cache.set/3 (ttl)");
        assertNull(KofCache.staticCall("set", List.of(S, S, I, I)), "cache.set/4 NAO binda");
        assertNotNull(KofCache.staticCall("clear", List.of()), "cache.clear");
        assertNull(KofCache.staticCall("clear", List.of(S)), "cache.clear/1 NAO binda");
        for (String m : KofCache.functions())
            assertFalse(StdCatalog.signaturesOf("cache", m).isEmpty(), "tabela sem cache." + m);

        assertNotNull(KofProcess.entryCall("run", List.of(S)), "process.run/1");
        assertNotNull(KofProcess.entryCall("run", List.of(S, S, S)), "process.run variadico");
        assertNotNull(KofProcess.entryCall("spawn", List.of(S)), "process.spawn/1");
        assertNull(KofProcess.runCall(List.of()), "run sem programa");
        assertNotNull(KofProcess.exitCall(List.of(I)), "process.exit(Int)");
        assertNull(KofProcess.exitCall(List.of(S)), "exit(String) NAO binda");
        for (String m : KofProcess.functions())
            assertFalse(StdCatalog.signaturesOf("process", m).isEmpty(), "tabela sem process." + m);

        assertNotNull(KofShell.staticCall("cmd", List.of(S, KofProcess.STRING_LIST)), "shell.cmd");
        assertNull(KofShell.staticCall("cmd", List.of(S)), "shell.cmd/1 NAO binda");
        assertNotNull(KofShell.staticCall("run", List.of(S)), "shell.run/1");
        assertNotNull(KofShell.staticCall("run", List.of(S, KofProcess.STRING_LIST)), "shell.run/2");
        assertNull(KofShell.staticCall("run", List.of()), "shell.run/0");
        assertNotNull(KofShell.staticCall("ok", List.of(KofProcess.RESULT)), "shell.ok(Result)");
        assertNull(KofShell.staticCall("ok", List.of(S)), "ok(String) NAO binda");
        for (String m : KofShell.functions())
            assertFalse(StdCatalog.signaturesOf("shell", m).isEmpty(), "tabela sem shell." + m);
    }

    @Test
    void fatiaThreeTablesBindAgainstRealDispatchers() {
        assertNotNull(KofNet.staticMethod("net", "host", List.of(S)), "net.host/1");
        assertNull(KofNet.staticMethod("net", "host", List.of(S, S)), "net.host/2 NAO binda");
        assertNotNull(KofNet.staticMethod("net", "queryEncode", List.of(S)), "net.queryEncode");
        assertNull(KofNet.staticMethod("net", "nope", List.of(S)), "net.?");
        assertNotNull(KofUuid.staticMethod("uuid", "isUuid", List.of(S)), "uuid.isUuid");
        assertNull(KofUuid.staticMethod("uuid", "isUuid", List.of()), "isUuid/0");
        assertNotNull(KofUuid.staticMethod("uuid", "v7", List.of()), "uuid.v7()");
        assertNull(KofUuid.staticMethod("uuid", "v7", List.of(I)), "v7 nao aceita args");
        assertNotNull(KofRandom.staticMethod("random", "int", List.of(I)), "random.int/1");
        assertNull(KofRandom.staticMethod("random", "int", List.of()), "random.int/0");
        assertNotNull(KofRandom.staticMethod("random", "double", List.of()), "random.double()");
        assertNotNull(KofRandom.staticMethod("random", "randomBytesHex", List.of(I)), "randomBytesHex(Int)");
        assertNull(KofRandom.staticMethod("random", "randomBytesHex", List.of(S)), "randomBytesHex(String) NAO");
        assertNull(KofRandom.staticMethod("random", "randomString", List.of(I)), "randomString/1 NAO binda");
        assertNotNull(KofRandom.staticMethod("random", "randomString", List.of(I, S)), "randomString(Int,String)");
        assertNotNull(KofRng.staticMethod("rng", "seed", List.of(I)), "rng.seed");
        assertNull(KofRng.staticMethod("rng", "boolean", List.of(I)), "rng.boolean/1 NAO binda");
        assertNotNull(KofRng.staticMethod("rng", "string", List.of(I, S)), "rng.string(Int,String)");
        assertNull(KofRng.staticMethod("rng", "string", List.of(S, I)), "rng.string(String,Int) NAO (ordem)");
        assertNotNull(KofEncoding.staticMethod("encoding", "urlEncode", List.of(S)), "encoding.urlEncode");
        assertNull(KofEncoding.staticMethod("encoding", "urlEncode", List.of()), "urlEncode/0 (gate e argc)");
        assertNotNull(KofEncoding.staticMethod("encoding", "base64UrlEncode", List.of(S)), "base64UrlEncode");
        assertNotNull(KofEncoding.staticMethod("encoding", "base64UrlDecode", List.of(S)), "base64UrlDecode");
        assertNotNull(KofEncoding.staticMethod("encoding", "base64Decode", List.of(S)), "encoding.base64Decode");
        assertNotNull(KofStrings.staticMethod("strings", "count", List.of(S, S)), "strings.count/2");
        assertNull(KofStrings.staticMethod("strings", "count", List.of(S)), "count/1 NAO binda");
        assertNotNull(KofStrings.staticMethod("strings", "padRight", List.of(S, I, S)), "padRight/3");
        assertNull(KofStrings.staticMethod("strings", "padRight", List.of(S, I)), "padRight/2 NAO binda");
        assertNotNull(KofStrings.staticMethod("strings", "isNumeric", List.of(S)), "isNumeric");
        for (String ns : List.of("net", "uuid", "random", "rng", "encoding", "strings"))
            for (String m : StdCatalog.membersOf(ns))
                assertFalse(StdCatalog.signaturesOf(ns, m).isEmpty(), "tabela sem " + ns + "." + m);
    }

    @Test
    void mathFamilyDriftIsCataloged() {
        // o lock de virgulas expo6 a familia escondida; cada nome tem que bindar
        // de verdade no dispatcher (prova de que nao e nome inventado)
        for (String m : List.of("isEven", "isOdd", "isPositive", "isNegative", "isZero"))
            assertNotNull(KofMath.staticMethod("math", m, List.of(I)), "math." + m + "(Int)");
        for (String m : List.of("isInteger", "isDecimal")) {
            assertNotNull(KofMath.staticMethod("math", m, List.of(D)), "math." + m + "(Double)");
            assertNull(KofMath.staticMethod("math", m, List.of(I)), "math." + m + "(Int) NAO binda");
        }
    }

    @Test
    void ghostSignaturesAndUnknownNamespaceAreEmpty() {
        assertTrue(StdCatalog.signaturesOf("db", "ghost").isEmpty());
        assertTrue(StdCatalog.signaturesOf("nope", "get").isEmpty());
    }

    @Test
    void fatiaFourMathAndLogBindAgainstRealDispatchers() {
        assertNotNull(KofMath.staticMethod("math", "abs", List.of(I)), "math.abs(Int)");
        assertNull(KofMath.staticMethod("math", "abs", List.of(D)), "abs(Double) NAO binda (familia Int)");
        assertNotNull(KofMath.staticMethod("math", "sqrt", List.of(D)), "sqrt(Double)");
        assertNull(KofMath.staticMethod("math", "sqrt", List.of(I)), "sqrt(Int) NAO binda");
        assertNotNull(KofMath.staticMethod("math", "clamp", List.of(I, I, I)), "clamp/3");
        assertNull(KofMath.staticMethod("math", "clamp", List.of(I, I)), "clamp/2");
        assertNotNull(KofMath.staticMethod("math", "parseLongOrDefault", List.of(S, I)), "parseLongOrDefault aceita Int");
        assertNotNull(KofMath.staticMethod("math", "roundTo", List.of(D, I)), "roundTo(Double,Int)");
        assertNull(KofMath.staticMethod("math", "roundTo", List.of(I, D)), "roundTo(Int,Double) NAO (ordem)");
        assertNotNull(KofLog.staticCall("info", List.of(S)), "log.info(String)");
        assertNull(KofLog.staticCall("info", List.of(S, S)), "info/2 NAO binda");
        assertNull(KofLog.staticCall("nope", List.of(S)), "log.?");
        for (String m : KofMath.functions())
            assertFalse(StdCatalog.signaturesOf("math", m).isEmpty(), "tabela sem math." + m);
        for (String m : KofLog.functions())
            assertFalse(StdCatalog.signaturesOf("log", m).isEmpty(), "tabela sem log." + m);
    }

    @Test
    void ormPageCatalogDocumentsLimitBeforeOffset() {
        // P0/pagination (28/09): the LSP catalog said (offset, limit) but
        // kof_orm_page binds (limit, offset) (JvmOrmRuntime.java:450) — the
        // latent trap is corrected here and locked against regression.
        assertEquals(List.of("page(String entity, Object limit, Object offset) -> List"),
                StdCatalog.signaturesOf("orm", "page"), "ordem limit,offset no catalogo");
    }

    @Test
    void fatiaFiveTablesBindAgainstRealDispatchers() {
        Type L = Type.UnknownType.UNKNOWN; // gates de orm/mq sao de ARGC
        assertNotNull(KofOrm.staticCall("save", List.of(S, L), false, "User"), "orm.save");
        assertNotNull(KofOrm.staticCall("find", List.of(S, L), true, "User"), "orm.find(typed)");
        assertNull(KofOrm.staticCall("find", List.of(S, L), false, "User"), "find sem typed NAO binda");
        assertNull(KofOrm.staticCall("count", List.of(S, S), true, "User"), "count/2 NAO binda");
        assertNotNull(KofOrm.staticCall("count", List.of(S, S, L), true, "User"), "count/3 (where)");
        assertNotNull(KofOrm.staticCall("where", List.of(S, S, S, L), true, "User"), "where/4 (op)");
        assertNotNull(KofConfig.staticCall("int", List.of(S, I)), "config.int/2");
        assertNull(KofConfig.staticCall("int", List.of(S)), "config.int/1");
        assertNotNull(KofGpu.staticCall("mvPutSp", List.of(I, L, L, I, I)), "gpu.mvPutSp/5");
        assertNull(KofGpu.staticCall("mvPutSp", List.of(I, L, L, I)), "mvPutSp/4 NAO binda");
        assertNotNull(KofGpu.staticCall("available", List.of()), "gpu.available");
        assertNotNull(KofMq.staticCall("subscribe", List.of(S, L)), "mq.subscribe");
        assertNotNull(KofMq.staticCall("queue", List.of()), "mq.queue/0");
        assertNull(KofMq.staticCall("queue", List.of(S)), "queue/1 NAO binda");
        assertNotNull(KofValidation.staticMethod("validation", "lengthBetween", List.of(S, I, I)), "lengthBetween/3");
        assertNull(KofValidation.staticMethod("validation", "lengthBetween", List.of(S, I)), "lengthBetween/2");
        assertNotNull(KofValidation.staticMethod("validation", "isPort", List.of(I)), "isPort(Int)");
        assertNull(KofObservability.staticMethod("observability", "counter", List.of(I)), "counter(Int) NAO passa o gate isString");
        assertNotNull(KofObservability.staticMethod("observability", "increment", List.of(S, I)), "obs.increment");
        assertNotNull(KofObservability.staticMethod("observability", "metrics", List.of()), "obs.metrics/0");
        assertNotNull(KofTetris.staticMethod("tetris", "run", 0), "tetris.run/0");
        assertNull(KofTetris.staticMethod("tetris", "run", 1), "run/1 NAO binda");
        for (String ns : List.of("orm", "config", "gpu", "mq", "validation", "observability", "tetris"))
            for (String m : StdCatalog.membersOf(ns))
                assertFalse(StdCatalog.signaturesOf(ns, m).isEmpty(), "tabela sem " + ns + "." + m);
    }

    @Test
    void fatiaSixSecurityAndMediaBindAgainstRealDispatchers() {
        assertNotNull(KofSecurity.staticMethod("crypto", "hmacSha256", List.of(S, S)), "crypto.hmacSha256");
        assertNull(KofSecurity.staticMethod("crypto", "hmacSha256", List.of(S)), "hmac/1");
        assertNotNull(KofSecurity.staticMethod("crypto", "randomHex", List.of(I)), "crypto.randomHex(Int)");
        assertNotNull(KofSecurity.staticMethod("jwt", "create", List.of(S, S)), "jwt.create/2");
        assertNotNull(KofSecurity.staticMethod("jwt", "create", List.of(S, S, I)), "jwt.create/3 (ttl)");
        assertNull(KofSecurity.staticMethod("jwt", "create", List.of(S)), "create/1 NAO binda");
        assertNotNull(KofSecurity.staticMethod("jwt", "verify", List.of(S, S, S, S)), "jwt.verify/4 (iss+aud)");
        assertNotNull(KofSecurity.staticMethod("secrets", "get", List.of(S, S)), "secrets.get/2 (default)");
        assertNotNull(KofSecurity.staticMethod("security", "csrfToken", List.of()), "csrfToken/0");
        assertNull(KofSecurity.staticMethod("security", "csrfToken", List.of(S)), "csrfToken/1 NAO");
        assertNotNull(KofSecurity.staticMethod("security", "cookieSet", List.of(S, S)), "cookieSet/2");
        assertNull(KofSecurity.staticMethod("security", "rateLimit", List.of(S, I)), "rateLimit/2 NAO");
        assertNotNull(KofSecurity.staticMethod("auth", "hasRole", List.of(S)), "auth.hasRole");
        assertNull(KofSecurity.staticMethod("auth", "hasRole", List.of()), "hasRole/0 NAO");
        assertNotNull(KofSecurity.staticMethod("passwords", "needsRehash", List.of(S)), "needsRehash");
        assertNotNull(KofMedia.staticCall("Image", "open", 1), "Image.open/1");
        assertNull(KofMedia.staticCall("Image", "open", 0), "Image.open/0 NAO");
        assertNotNull(KofMedia.staticCall("Mic", "record", 1), "Mic.record(Int)");
        assertNotNull(KofMedia.staticCall("Mic", "list", 0), "Mic.list/0");
        for (String ns : List.of("passwords", "crypto", "jwt", "secrets", "security", "auth"))
            for (String m : StdCatalog.membersOf(ns))
                assertFalse(StdCatalog.signaturesOf(ns, m).isEmpty(), "tabela sem " + ns + "." + m);
    }

    @Test
    void untabledNamespacesStayHonestEmpty() {
        // 32/32 ns com tabela desde 19/09 (~22h): `json` entrou na ultima
        // fatia — o "dispatcher por aridade no typer" que faltava JA EXISTE
        // (MemberCallNamespaces valida aridade e cobra o <T> de decode com
        // SEM025; o dispatch POR TIPO continua no lowerer/JsonDispatch e nao
        // entra na tabela). Inventar forma e proibido (R6).
        assertTrue(StdCatalog.signaturesOf("nope", "get").isEmpty());
    }

    @Test
    void jsonTableBindsAgainstRealTyperArities(@TempDir Path tmp) throws Exception {
        java.util.List<String> enc = StdCatalog.signaturesOf("json", "encode");
        java.util.List<String> dec = StdCatalog.signaturesOf("json", "decode");
        assertEquals(List.of("encode(value) -> String"), enc, "forma de encode na tabela");
        assertEquals(List.of("decode<T>(jsonString) -> T"), dec, "forma de decode na tabela");
        assertTrue(diagOf(tmp, "enc1", "main() {\n    println(json.encode(1))\n}").isEmpty(),
                "encode(1) deve compilar limpo");
        assertTrue(diagOf(tmp, "dec1", "record P(Int a)\nmain() {\n"
                + "    val v = json.decode<P>(\"{\\\"a\\\":1}\")\n    println(v)\n}").isEmpty(),
                "decode<P>(s) deve compilar limpo");
        assertTrue(diagOf(tmp, "enc2", "main() {\n    println(json.encode(1, 2))\n}").contains("SEM025"),
                "encode(2) NAO binda (a tabela diz 1 forma de 1 arg)");
        assertTrue(diagOf(tmp, "enc0", "main() {\n    println(json.encode())\n}").contains("SEM025"),
                "encode(0) NAO binda");
        assertTrue(diagOf(tmp, "dec0", "main() {\n    val v = json.decode(\"{}\")\n    println(v)\n}").contains("SEM025"),
                "decode sem <T> NAO binda (1 arg + type argument)");
    }

    private String diagOf(Path tmp, String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("out-" + tag), Target.JVM);
        return String.join("\n", r.diagnostics().getDiagnostics().stream()
                .map(Object::toString).toList());
    }
}
