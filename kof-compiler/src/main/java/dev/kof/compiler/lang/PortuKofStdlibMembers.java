package dev.kof.compiler.lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ARTEFATO DO GERADOR scripts/gen_portukof_aliases.py — nao editar a mao.
 *
 * D-PORTUKOF (07/10) - paridade COMPLETA de membros da stdlib: cada membro
 * canonico do catalogo real (`StdCatalog` + dispatchers `Kof*`) tem exatamente
 * um alias pt-BR por namespace (identidade quando a traducao colidiria).
 * Alias != implementacao: o simbolo canonico e unico; `arquivo`/`tempo`/... so
 * resolvidos para a mesma funcao. A fonte e a lista real; `--check` recusa
 * deriva; o gate `scripts/check_portukof_parity.sh` cobra cobertura total.
 */
public final class PortuKofStdlibMembers {

    private PortuKofStdlibMembers() {}

    /** namespace canônico -> alias pt-BR (bijetivo; colisão mantém o canônico). */
    public static Map<String, String> namespaces() {
        var m = new LinkedHashMap<String, String>();
        m.put("Audio", "audio");
        m.put("Image", "imagem");
        m.put("Mic", "microfone");
        m.put("Video", "video");
        m.put("auth", "autenticar");
        m.put("buffer", "buffer");
        m.put("cache", "cache");
        m.put("config", "config");
        m.put("crypto", "crypto");
        m.put("db", "banco");
        m.put("encoding", "codificacao");
        m.put("gpu", "gpu");
        m.put("http", "http");
        m.put("image", "image");
        m.put("json", "json");
        m.put("jwt", "jwt");
        m.put("keyExchange", "chaveExchange");
        m.put("log", "registrar");
        m.put("math", "matematica");
        m.put("mq", "fila");
        m.put("net", "rede");
        m.put("observability", "observabilidade");
        m.put("orm", "orm");
        m.put("passwords", "senhas");
        m.put("process", "processo");
        m.put("random", "aleatorio");
        m.put("rng", "rng");
        m.put("secrets", "secrets");
        m.put("security", "security");
        m.put("shell", "shell");
        m.put("ssh", "ssh");
        m.put("strings", "textos");
        m.put("tetris", "tetris");
        m.put("time", "tempo");
        m.put("uuid", "uuid");
        m.put("validation", "validacao");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** namespace canônico -> [(membro canônico, alias pt-BR)] (ordem do catálogo). */
    public static Map<String, String[][]> table() {
        var m = new LinkedHashMap<String, String[][]>();
        m.put("Audio", new String[][]{{"openWav", "abrirWav"}});
        m.put("Image", new String[][]{{"open", "abrir"}});
        m.put("Mic", new String[][]{{"record", "gravar"}, {"list", "list"}});
        m.put("Video", new String[][]{{"open", "abrir"}});
        m.put("auth", new String[][]{{"secret", "secret"}, {"token", "token"}, {"authenticated", "autenticado"}, {"claims", "claims"}, {"user", "user"}, {"hasRole", "hasPapel"}, {"hasPermission", "hasPermission"}, {"resourceServer", "resourceServer"}, {"resourceServerVerify", "resourceServerVerificar"}});
        m.put("buffer", new String[][]{{"alloc", "alloc"}});
        m.put("cache", new String[][]{{"get", "obter"}, {"set", "definir"}, {"ttl", "ttl"}, {"delete", "excluir"}, {"clear", "limpar"}});
        m.put("config", new String[][]{{"get", "obter"}, {"env", "env"}, {"has", "has"}, {"str", "str"}, {"int", "int"}, {"long", "long"}, {"bool", "bool"}, {"required", "required"}});
        m.put("crypto", new String[][]{{"sha256", "sha256"}, {"sha512", "sha512"}, {"sha256Bytes", "sha256Bytes"}, {"hmacSha256Bytes", "hmacSha256Bytes"}, {"hmacSha256", "hmacSha256"}, {"encryptAesGcm", "encryptAesGcm"}, {"decryptAesGcm", "decriptarAesGcm"}, {"encryptChacha20", "encryptChacha20"}, {"decryptChacha20", "decriptarChacha20"}, {"sign", "assinar"}, {"verify", "verificar"}, {"randomHex", "aleatorioHex"}, {"randomInt", "aleatorioInt"}});
        m.put("db", new String[][]{{"connect", "conectar"}, {"query", "consultar"}, {"execute", "executar"}, {"close", "fechar"}, {"transaction", "transaction"}});
        m.put("encoding", new String[][]{{"hexEncode", "hexCodificar"}, {"hexDecode", "hexDecodificar"}, {"base64Encode", "base64Codificar"}, {"base64Decode", "base64Decodificar"}, {"base64UrlEncode", "base64UrlCodificar"}, {"base64UrlDecode", "base64UrlDecodificar"}, {"urlEncode", "urlCodificar"}, {"urlDecode", "urlDecodificar"}});
        m.put("gpu", new String[][]{{"available", "disponivel"}, {"failReason", "failReason"}, {"dispatchMatmul", "dispatchMatmul"}, {"dispatchMatmul64", "dispatchMatmul64"}, {"mvSetShape", "mvDefinirShape"}, {"mvLoadW", "mvCarregarW"}, {"mvMatvec", "mvMatvec"}, {"mvPutW", "mvPutW"}, {"mvRun", "mvExecutar"}, {"mvPut32", "mvPut32"}, {"mvRun32", "mvRun32"}, {"mvPutSp", "mvPutSp"}, {"mvRunSp", "mvExecutarSp"}});
        m.put("http", new String[][]{{"get", "obter"}, {"post", "post"}, {"put", "put"}, {"delete", "excluir"}, {"patch", "patch"}, {"options", "options"}, {"status", "status"}, {"timeout", "timeout"}, {"retry", "tentarNovamente"}, {"circuit", "circuit"}});
        m.put("image", new String[][]{{"decode", "decodificar"}});
        m.put("json", new String[][]{{"encode", "codificar"}, {"decode", "decodificar"}});
        m.put("jwt", new String[][]{{"create", "criar"}, {"verify", "verificar"}, {"secret", "secret"}});
        m.put("keyExchange", new String[][]{{"privateKey", "privadaChave"}, {"publicKey", "publicaChave"}, {"shared", "shared"}, {"hkdfSha256", "hkdfSha256"}});
        m.put("log", new String[][]{{"debug", "debug"}, {"info", "info"}, {"warn", "warn"}, {"error", "error"}});
        m.put("math", new String[][]{{"abs", "abs"}, {"sign", "assinar"}, {"clamp", "limitar"}, {"min", "minimo"}, {"max", "maximo"}, {"isEven", "ehEven"}, {"isOdd", "ehOdd"}, {"isPositive", "ehPositive"}, {"isNegative", "ehNegative"}, {"isZero", "ehZero"}, {"sqrt", "raizQuadrada"}, {"lerp", "lerp"}, {"percentage", "percentage"}, {"isInteger", "ehInteger"}, {"isDecimal", "ehDecimal"}, {"sin", "sin"}, {"cos", "cos"}, {"tan", "tan"}, {"asin", "asin"}, {"acos", "acos"}, {"atan", "atan"}, {"atan2", "atan2"}, {"toRadians", "paraRadians"}, {"toDegrees", "paraDegrees"}, {"pi", "pi"}, {"e", "e"}, {"tau", "tau"}, {"roundTo", "arredondarPara"}, {"pow", "pow"}, {"parseInt", "analisarInt"}, {"parseLong", "analisarLong"}, {"parseDouble", "analisarDouble"}, {"parseIntOrDefault", "analisarIntOrDefault"}, {"parseLongOrDefault", "analisarLongOrDefault"}, {"parseDoubleOrDefault", "analisarDoubleOrDefault"}});
        m.put("mq", new String[][]{{"publish", "publicar"}, {"subscribe", "assinarCanal"}, {"unsubscribe", "unsubscribe"}, {"queue", "queue"}, {"push", "push"}, {"pop", "pop"}, {"queueSize", "queueTamanho"}});
        m.put("net", new String[][]{{"scheme", "scheme"}, {"host", "host"}, {"port", "porta"}, {"path", "path"}, {"query", "consultar"}, {"fragment", "fragment"}, {"queryEncode", "consultarCodificar"}, {"queryDecode", "consultarDecodificar"}, {"listen", "escutar"}, {"connect", "conectar"}, {"bind", "vincular"}, {"resolve", "resolver"}});
        m.put("observability", new String[][]{{"health", "health"}, {"readiness", "readiness"}, {"liveness", "liveness"}, {"counter", "counter"}, {"increment", "increment"}, {"gauge", "gauge"}, {"histogram", "histogram"}, {"metrics", "metrics"}, {"requestId", "requisicaoId"}, {"correlationId", "correlationId"}, {"traceId", "traceId"}, {"spanId", "spanId"}, {"spanStart", "spanIniciar"}, {"spanEnd", "spanFim"}, {"exportSpans", "exportarSpans"}});
        m.put("orm", new String[][]{{"create", "criar"}, {"save", "salvar"}, {"find", "find"}, {"all", "all"}, {"delete", "excluir"}, {"count", "contar"}, {"deleteAll", "excluirAll"}, {"where", "onde"}, {"saveAll", "salvarAll"}, {"page", "page"}, {"migrate", "migrate"}});
        m.put("passwords", new String[][]{{"hash", "hash"}, {"verify", "verificar"}, {"needsRehash", "precisaRehash"}});
        m.put("process", new String[][]{{"run", "executar"}, {"spawn", "spawn"}, {"exit", "sair"}});
        m.put("random", new String[][]{{"double", "double"}, {"boolean", "boolean"}, {"int", "int"}, {"hex", "hex"}, {"randomBytesHex", "aleatorioBytesHex"}, {"randomInt", "aleatorioInt"}, {"randomBoolean", "aleatorioBoolean"}, {"randomString", "aleatorioString"}});
        m.put("rng", new String[][]{{"seed", "semente"}, {"int", "int"}, {"boolean", "boolean"}, {"double", "double"}, {"string", "string"}});
        m.put("secrets", new String[][]{{"get", "obter"}, {"redact", "redact"}, {"of", "de"}, {"secret", "secret"}, {"fromBytes", "deBytes"}, {"keyFromHex", "chaveDeHex"}, {"keyFromPem", "chaveDePem"}, {"keyFromKeystore", "chaveDeKeystore"}});
        m.put("security", new String[][]{{"constantTimeEquals", "constanteTempoIgual"}, {"randomHex", "aleatorioHex"}, {"redact", "redact"}, {"randomInt", "aleatorioInt"}, {"csrfToken", "csrfToken"}, {"csrfValid", "csrfValid"}, {"corsAllowed", "corsAllowed"}, {"cspHeader", "cspHeader"}, {"hstsHeader", "hstsHeader"}, {"contentTypeOptionsHeader", "contentTypeOptionsHeader"}, {"frameHeader", "frameHeader"}, {"referrerHeader", "referrerHeader"}, {"rateLimit", "taxaLimite"}, {"sessionCreate", "sessionCriar"}, {"sessionGet", "sessionObter"}, {"sessionDestroy", "sessionDestruir"}, {"apiKeyGenerate", "apiChaveGerar"}, {"apiKeyValid", "apiChaveValid"}, {"cookieSet", "cookieDefinir"}, {"cookieGet", "cookieObter"}});
        m.put("shell", new String[][]{{"cmd", "cmd"}, {"run", "executar"}, {"runWith", "executarWith"}, {"pipeline", "pipeline"}, {"ok", "ok"}});
        m.put("ssh", new String[][]{{"cmd", "cmd"}, {"run", "executar"}, {"ok", "ok"}});
        m.put("strings", new String[][]{{"isAlpha", "ehAlfa"}, {"isNumeric", "ehNumeric"}, {"isAlphaNumeric", "ehAlfaNumeric"}, {"isAscii", "ehAscii"}, {"isUpperCase", "ehUpperCaixa"}, {"isLowerCase", "ehMinusculoCaixa"}, {"count", "contar"}, {"capitalize", "capitalizar"}, {"uncapitalize", "uncapitalize"}, {"reverse", "reverter"}, {"toCamelCase", "paraCamelCaixa"}, {"toPascalCase", "paraPascalCaixa"}, {"toSnakeCase", "paraSnakeCaixa"}, {"toKebabCase", "paraKebabCaixa"}, {"slugify", "slugify"}, {"escapeHtml", "escaparHtml"}, {"unescapeHtml", "unescapeHtml"}, {"escapeJson", "escaparJson"}, {"removeWhitespace", "removerWhitespace"}, {"normalizeWhitespace", "normalizeWhitespace"}, {"dedent", "dedent"}, {"repeat", "repetir"}, {"truncate", "truncate"}, {"indent", "indent"}, {"padLeft", "padLeft"}, {"padRight", "padRight"}});
        m.put("tetris", new String[][]{{"run", "executar"}});
        m.put("time", new String[][]{{"sleep", "dormir"}, {"now", "agora"}, {"interval", "intervalo"}, {"cancel", "cancelar"}, {"collect", "coletar"}, {"isLeapYear", "ehBissextoAno"}, {"daysInMonth", "diasInMes"}, {"dayOfWeek", "diaDeSemana"}, {"daysBetween", "diasEntre"}, {"age", "idade"}, {"isWeekend", "ehFimDeSemana"}, {"addDays", "adicionarDias"}, {"addMonths", "adicionarMeses"}, {"addYears", "adicionarAnos"}, {"startOf", "iniciarDe"}, {"endOf", "fimDe"}, {"diffDays", "diferencaDias"}, {"todayIso", "hojeIso"}, {"formatDateIso", "formatarDateIso"}, {"isToday", "ehHoje"}, {"hoursBetween", "horasEntre"}, {"parseDateIso", "analisarDateIso"}, {"tzOffsetSeconds", "fusoDeslocamentoSegundos"}});
        m.put("uuid", new String[][]{{"isUuid", "ehUuid"}, {"v4", "v4"}, {"v7", "v7"}});
        m.put("validation", new String[][]{{"required", "required"}, {"notBlank", "notBlank"}, {"minLength", "minimoLength"}, {"maxLength", "maximoLength"}, {"lengthBetween", "lengthEntre"}, {"isEmail", "ehEmail"}, {"isUrl", "ehUrl"}, {"matches", "matches"}, {"isInt", "ehInt"}, {"isLong", "ehLong"}, {"inRange", "inRange"}, {"min", "minimo"}, {"max", "maximo"}, {"formatCpf", "formatarCpf"}, {"formatCep", "formatarCep"}, {"formatCnpj", "formatarCnpj"}, {"isCpf", "ehCpf"}, {"isCnpj", "ehCnpj"}, {"isCep", "ehCep"}, {"isPis", "ehPis"}, {"isNis", "ehNis"}, {"isIpv4", "ehIpv4"}, {"isMac", "ehMac"}, {"isPort", "ehPorta"}, {"isCreditCard", "ehCreditCard"}, {"isIpv6", "ehIpv6"}, {"creditCardBrand", "creditCardBrand"}, {"last4", "last4"}, {"isDomain", "ehDominio"}});
        return java.util.Collections.unmodifiableMap(m);
    }
}
