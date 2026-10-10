package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch table for {@code kof.security} (docs/stdlib/security.md).
 *
 * The Kof surface is intent-first:
 *
 * <pre>{@code
 * var hash = passwords.hash(password)
 * var ok   = passwords.verify(password, hash)
 *
 * var token  = jwt.create(claimsJson, secret)
 * var claims = jwt.verify(token, secret, issuer, audience)
 *
 * var mac = crypto.hmacSha256(key, data)
 * var ct  = crypto.encryptAesGcm(text, keyHex)
 *
 * var key = secrets.get("API_KEY")
 * var log = secrets.redact(value)
 *
 * if (!security.constantTimeEquals(a, b)) { ... }
 *
 * app.use {
 *     if (!auth.authenticated()) { return "{\"error\":\"unauthorized\"}" }
 *     if (!auth.hasRole("admin")) { return "{\"error\":\"forbidden\"}" }
 *     return null
 * }
 * }</pre>
 *
 * Internally every call maps to a static {@code kof_sec_*} function of the
 * generated runtime (JVM/JS), or an assembly routine (Native). Features not
 * available on a target produce a clear compile-time diagnostic (SECN00x) —
 * never silent divergence.
 */
public final class KofSecurity {

    private KofSecurity() {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type INT_ARRAY = new Type.ArrayType(INT);
    private static final Type BYTE_ARRAY = new Type.ArrayType(Type.PrimitiveType.BYTE);

    /** Face 1 do D-SECRETS (Stage 5 / 3.6): tipo nominal {@code Secret}. Um
     *  valor que NÃO se imprime/serializa sem um ato explícito ({@code reveal()}).
     *  Obtível só por {@code secrets.of}/{@code secrets.secret} — ambos gated,
     *  então os métodos de instância são inalcançáveis nos alvos sem suporte
     *  (mesmo padrão do {@code Buffer}, D-R3-BUFFER). */
    static final Type SECRET = new Type.ClassType("kof", "Secret", List.of());

    static boolean isSecretType(Type t) { return SECRET.equals(t); }

    /** Declared-type hook (same pattern as {@code KofUi}/{@code KofNet} —
     *  §179/§slice-6): without it a Kof function CANNOT declare a
     *  {@code Secret} or {@code KeyHandle} PARAMETER — the name resolves to
     *  an unqualified ClassType and SECN014 fires on legit calls (measured
     *  02/10 building the KofShare handshake; catalogued §569). */
    static Type typeByName(String name) {
        return switch (name) {
            case "Secret" -> SECRET;
            case "KeyHandle" -> KEY_HANDLE;
            default -> null;
        };
    }

    /** D-SECRETS P3 (Stage 5 / 3.6): {@code KeyHandle} — chave nomeada que
     *  nunca expoe bytes ao guest; so os algoritmos de crypto a consomem. */
    static final Type KEY_HANDLE = new Type.ClassType("kof", "KeyHandle", List.of());

    static boolean isKeyHandleType(Type t) { return KEY_HANDLE.equals(t); }

    static final List<String> NAMESPACES = List.of(
            "passwords", "crypto", "jwt", "secrets", "security", "auth", "keyExchange");

    static boolean isSecurityNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record SecCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** X10 fatia 2: membros por namespace de segurança (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case-literals do staticMethod abaixo. */
    static java.util.Map<String, List<String>> functions() {
        return java.util.Map.of(
                "passwords", List.of("hash", "verify", "needsRehash"),
                "crypto", List.of("sha256", "sha512", "sha256Bytes", "hmacSha256Bytes", "hmacSha256", "encryptAesGcm", "decryptAesGcm", "encryptChacha20", "decryptChacha20", "sign", "verify", "randomHex", "randomInt"),
                "jwt", List.of("create", "verify", "secret"),
                "secrets", List.of("get", "redact", "of", "secret", "fromBytes", "keyFromHex", "keyFromPem", "keyFromKeystore"),
                "security", List.of("constantTimeEquals", "randomHex", "redact", "randomInt", "csrfToken", "csrfValid", "corsAllowed", "cspHeader", "hstsHeader", "contentTypeOptionsHeader", "frameHeader", "referrerHeader", "rateLimit", "sessionCreate", "sessionGet", "sessionDestroy", "apiKeyGenerate", "apiKeyValid", "cookieSet", "cookieGet"),
                "auth", List.of("secret", "token", "authenticated", "claims", "user", "hasRole", "hasPermission", "resourceServer", "resourceServerVerify"),
                "keyExchange", List.of("privateKey", "publicKey", "shared", "hkdfSha256"));
    }

    /**
     * Resolves a call in a security namespace. Returns null when the call is
     * not part of the API (the analyzer reports an unknown method).
     */
    static SecCall staticMethod(String namespace, String name, List<Type> argTypes) {
        int argc = argTypes.size();
        return switch (namespace) {
            case "passwords" -> switch (name) {
                case "hash" -> argc == 1
                        ? new SecCall("kof_sec_password_hash", STR, List.of(STR)) : null;
                case "verify" -> argc == 2
                        ? new SecCall("kof_sec_password_verify", BOOL, List.of(STR, STR)) : null;
                case "needsRehash" -> argc == 1
                        ? new SecCall("kof_sec_password_needs_rehash", BOOL, List.of(STR)) : null;
                default -> null;
            };
            case "crypto" -> switch (name) {
                case "sha256" -> argc == 1
                        ? new SecCall("kof_sec_sha256", STR, List.of(STR)) : null;
                case "sha512" -> argc == 1
                        ? new SecCall("kof_sec_sha512", STR, List.of(STR)) : null;
                // D-KOF-DIGEST-BYTES (02/10): a face binária dedicada. Os
                // nomes simples ficam SÓ String/Int (SECN011, §563); Byte[]
                // real exige a face Bytes — SECN013 para o resto.
                case "sha256Bytes" -> argc == 1
                        ? new SecCall("kof_sec_sha256_bytes", STR, List.of(BYTE_ARRAY)) : null;
                case "hmacSha256Bytes" -> argc == 2
                        ? new SecCall("kof_sec_hmac_sha256_bytes", STR, List.of(BYTE_ARRAY, BYTE_ARRAY)) : null;
                case "hmacSha256" -> argc == 2
                        ? new SecCall(isKeyHandleType(argTypes.get(0)) ? "kof_sec_hmac_sha256_key" : "kof_sec_hmac_sha256",
                                STR, isKeyHandleType(argTypes.get(0)) ? List.of(KEY_HANDLE, STR) : List.of(STR, STR)) : null;
                case "encryptAesGcm" -> argc == 2
                        ? new SecCall(isKeyHandleType(argTypes.get(1)) ? "kof_sec_aesgcm_encrypt_key" : "kof_sec_aesgcm_encrypt",
                                STR, isKeyHandleType(argTypes.get(1)) ? List.of(STR, KEY_HANDLE) : List.of(STR, STR)) : null;
                case "decryptAesGcm" -> argc == 2
                        ? new SecCall(isKeyHandleType(argTypes.get(1)) ? "kof_sec_aesgcm_decrypt_key" : "kof_sec_aesgcm_decrypt",
                                STR, isKeyHandleType(argTypes.get(1)) ? List.of(STR, KEY_HANDLE) : List.of(STR, STR)) : null;
                case "encryptChacha20" -> argc == 2
                        ? new SecCall(isKeyHandleType(argTypes.get(1)) ? "kof_sec_chacha20_encrypt_key" : "kof_sec_chacha20_encrypt",
                                STR, isKeyHandleType(argTypes.get(1)) ? List.of(STR, KEY_HANDLE) : List.of(STR, STR)) : null;
                case "decryptChacha20" -> argc == 2
                        ? new SecCall(isKeyHandleType(argTypes.get(1)) ? "kof_sec_chacha20_decrypt_key" : "kof_sec_chacha20_decrypt",
                                STR, isKeyHandleType(argTypes.get(1)) ? List.of(STR, KEY_HANDLE) : List.of(STR, STR)) : null;
                // D-KOF-SIGN (C1): Ed25519 sobre primitiva do JDK. sign exige o
                // Secret privado (184-hex PKCS8||SPKI); verify aceita privado OU
                // so-público (64-hex via secrets.of(hex)). Hex é o formato da casa.
                case "sign" -> argc == 2
                        ? new SecCall("kof_sec_ed25519_sign", STR, List.of(SECRET, BYTE_ARRAY)) : null;
                case "verify" -> argc == 3
                        ? new SecCall("kof_sec_ed25519_verify", BOOL,
                                List.of(SECRET, BYTE_ARRAY, STR)) : null;
                case "randomHex" -> argc == 1
                        ? new SecCall("kof_sec_random_hex", STR, List.of(INT)) : null;
                case "randomInt" -> argc == 1
                        ? new SecCall("kof_sec_random_int", INT, List.of(INT)) : null;
                default -> null;
            };
            // D-KOF-X25519 (mantenedora 02/10): a face de chave de sessao.
            // privateKey/shared devolvem Secret (nunca String — R8); a cara
            // publica e hex exportavel; HKDF-Expand-SHA256 (RFC 5869) expande
            // o segredo compartilhado em material de chave (hex, L<=1020).
            // Real arg non-Secret/len na cara errada: SECN014 (argTypeViolation).
            case "keyExchange" -> switch (name) {
                case "privateKey" -> argc == 0
                        ? new SecCall("kof_sec_x25519_private_key", SECRET, List.of())
                        : argc == 1 && isStringish(argTypes.get(0))
                        ? new SecCall("kof_sec_ed25519_private_key", SECRET, List.of(STR)) : null;
                case "publicKey" -> argc == 1
                        ? new SecCall("kof_sec_public_key_any", STR, List.of(SECRET)) : null;
                case "shared" -> argc == 2
                        ? new SecCall("kof_sec_x25519_shared", SECRET, List.of(SECRET, SECRET)) : null;
                case "hkdfSha256" -> argc == 4
                        ? new SecCall("kof_sec_hkdf_sha256", STR, List.of(SECRET, STR, STR, INT)) : null;
                default -> null;
            };
            case "jwt" -> switch (name) {
                case "create" -> argc == 2
                        ? (isKeyHandleType(argTypes.get(1))
                        ? new SecCall("kof_sec_jwt_create_key", STR, List.of(STR, KEY_HANDLE))
                        : new SecCall("kof_sec_jwt_create", STR, List.of(STR, STR)))
                        : argc == 3
                        ? (isKeyHandleType(argTypes.get(1))
                        ? new SecCall("kof_sec_jwt_create_ttl_key", STR, List.of(STR, KEY_HANDLE, INT))
                        : new SecCall("kof_sec_jwt_create_ttl", STR, List.of(STR, STR, INT)))
                        : null;
                case "verify" -> argc == 2
                        ? (isKeyHandleType(argTypes.get(1))
                        ? new SecCall("kof_sec_jwt_verify_key", STR, List.of(STR, KEY_HANDLE))
                        : new SecCall("kof_sec_jwt_verify", STR, List.of(STR, STR)))
                        : argc == 4
                        ? (isKeyHandleType(argTypes.get(1))
                        ? new SecCall("kof_sec_jwt_verify_iss_aud_key", STR, List.of(STR, KEY_HANDLE, STR, STR))
                        : new SecCall("kof_sec_jwt_verify_iss_aud", STR, List.of(STR, STR, STR, STR)))
                        : null;
                case "secret" -> argc == 0
                        ? new SecCall("kof_sec_jwt_secret", STR, List.of())
                        : null;
                default -> null;
            };
            case "secrets" -> switch (name) {
                case "get" -> argc == 1
                        ? new SecCall("kof_sec_secret_get", STR, List.of(STR))
                        : argc == 2
                        ? new SecCall("kof_sec_secret_get_default", STR, List.of(STR, STR))
                        : null;
                case "redact" -> argc == 1
                        ? new SecCall("kof_sec_redact", STR, List.of(STR))
                        : null;
                // D-SECRETS face 1: valor tipado. `of` envolve um literal;
                // `secret` lê o ambiente por nome. Ambos devolvem `Secret`
                // (não-exportável sem reveal()) — o `get` cru continua String
                // (compatibilidade 0.2.6, freeze).
                case "of" -> argc == 1
                        ? new SecCall("kof_sec_secret_of", SECRET, List.of(STR))
                        : null;
                case "secret" -> argc == 1
                        ? new SecCall("kof_sec_secret", SECRET, List.of(STR))
                        : null;
                case "fromBytes" -> argc == 1
                        ? new SecCall("kof_sec_secret_from_bytes", SECRET, List.of(INT_ARRAY))
                        : null;
                // D-SECRETS P3: fontes de KeyHandle (JVM-primeiro). O handle
                // nunca expoe bytes; os algoritmos de crypto o consomem.
                case "keyFromHex" -> argc == 1
                        ? new SecCall("kof_sec_key_from_hex", KEY_HANDLE, List.of(STR)) : null;
                case "keyFromPem" -> argc == 1
                        ? new SecCall("kof_sec_key_from_pem", KEY_HANDLE, List.of(STR)) : null;
                case "keyFromKeystore" -> argc == 3
                        ? new SecCall("kof_sec_key_from_keystore", KEY_HANDLE, List.of(STR, STR, STR)) : null;
                default -> null;
            };
            case "security" -> switch (name) {
                case "constantTimeEquals" -> argc == 2
                        ? new SecCall("kof_sec_constant_time_equals", BOOL, List.of(STR, STR)) : null;
                case "randomHex" -> argc == 1
                        ? new SecCall("kof_sec_random_hex", STR, List.of(INT)) : null;
                case "redact" -> argc == 1
                        ? new SecCall("kof_sec_redact", STR, List.of(STR)) : null;
                case "randomInt" -> argc == 1
                        ? new SecCall("kof_sec_random_int", INT, List.of(INT)) : null;
                case "csrfToken" -> argc == 0
                        ? new SecCall("kof_sec_csrf_token", STR, List.of()) : null;
                case "csrfValid" -> argc == 1
                        ? new SecCall("kof_sec_csrf_valid", BOOL, List.of(STR)) : null;
                case "corsAllowed" -> argc == 2
                        ? new SecCall("kof_sec_cors_allowed", BOOL, List.of(STR, STR)) : null;
                case "cspHeader" -> argc == 0
                        ? new SecCall("kof_sec_csp_header", STR, List.of()) : null;
                case "hstsHeader" -> argc == 0
                        ? new SecCall("kof_sec_hsts_header", STR, List.of()) : null;
                case "contentTypeOptionsHeader" -> argc == 0
                        ? new SecCall("kof_sec_content_type_options_header", STR, List.of()) : null;
                case "frameHeader" -> argc == 0
                        ? new SecCall("kof_sec_frame_header", STR, List.of()) : null;
                case "referrerHeader" -> argc == 0
                        ? new SecCall("kof_sec_referrer_header", STR, List.of()) : null;
                // ── G9: rate limiting / sessions / API keys ────────────
                case "rateLimit" -> argc == 3
                        ? new SecCall("kof_sec_rate_limit", BOOL, List.of(STR, INT, INT)) : null;
                case "sessionCreate" -> argc == 1
                        ? new SecCall("kof_sec_session_create", STR, List.of(STR)) : null;
                case "sessionGet" -> argc == 1
                        ? new SecCall("kof_sec_session_get", STR, List.of(STR)) : null;
                case "sessionDestroy" -> argc == 1
                        ? new SecCall("kof_sec_session_destroy", BOOL, List.of(STR)) : null;
                case "apiKeyGenerate" -> argc == 0
                        ? new SecCall("kof_sec_api_key_generate", STR, List.of()) : null;
                case "apiKeyValid" -> argc == 1
                        ? new SecCall("kof_sec_api_key_valid", BOOL, List.of(STR)) : null;
                // D-SEC C11 (cookies): set com defaults seguros
                // (HttpOnly, Secure, SameSite=Lax, Path=/) ou com opts-map;
                // get faz o parse do header Cookie do request.
                case "cookieSet" -> argc == 2
                        ? new SecCall("kof_sec_cookie_set", STR, List.of(STR, STR))
                        : (argc == 3
                                ? new SecCall("kof_sec_cookie_set_opts", STR,
                                        List.of(STR, STR, BuiltinTypes.MAP))
                                : null);
                case "cookieGet" -> argc == 2
                        ? new SecCall("kof_sec_cookie_get", STR, List.of(STR, STR)) : null;
                default -> null;
            };
            case "auth" -> switch (name) {
                case "secret" -> argc == 1
                        ? new SecCall("kof_sec_auth_secret", BOOL, List.of(STR)) : null;
                case "token" -> argc == 0
                        ? new SecCall("kof_sec_auth_token", STR, List.of()) : null;
                case "authenticated" -> argc == 0
                        ? new SecCall("kof_sec_auth_authenticated", BOOL, List.of()) : null;
                case "claims" -> argc == 0
                        ? new SecCall("kof_sec_auth_claims", STR, List.of()) : null;
                case "user" -> argc == 0
                        ? new SecCall("kof_sec_auth_user", STR, List.of()) : null;
                case "hasRole" -> argc == 1
                        ? new SecCall("kof_sec_auth_has_role", BOOL, List.of(STR)) : null;
                case "hasPermission" -> argc == 1
                        ? new SecCall("kof_sec_auth_has_permission", BOOL, List.of(STR)) : null;
                // D-SEC layer 16: OAuth2 resource server — valida JWT de
                // TERCEIRO (RS256/ES256 via JWKS + issuer/aud). JVM primeiro.
                case "resourceServer" -> argc == 3
                        ? new SecCall("kof_sec_auth_resource_server", BOOL, List.of(STR, STR, STR))
                        : null;
                case "resourceServerVerify" -> argc == 1
                        ? new SecCall("kof_sec_auth_resource_server_verify", STR, List.of(STR))
                        : null;
                default -> null;
            };
            default -> null;
        };
    }

    /** Face 1 do D-SECRETS (Stage 5/3.6): métodos de instância do tipo
     *  {@code Secret}. Inalcançáveis onde {@code of}/{@code secret} são gated. */
    static SecCall instanceMethod(Type receiver, String name, int argCount) {
        if (isSecretType(receiver)) {
            return switch (name) {
                case "reveal" -> argCount == 0
                        ? new SecCall("kof_sec_secret_reveal", STR, List.of(SECRET)) : null;
                case "redacted" -> argCount == 0
                        ? new SecCall("kof_sec_secret_redacted", STR, List.of(SECRET)) : null;
                default -> null;
            };
        }
        if (isKeyHandleType(receiver)) {
            return switch (name) {
                case "rotate" -> argCount == 0
                        ? new SecCall("kof_sec_key_rotate", KEY_HANDLE, List.of(KEY_HANDLE)) : null;
                default -> null;
            };
        }
        return null;
    }

    /**
     * Android reuses the JVM backend/runtime (`CompilerPipeline:186`), and every
     * `kof.security` shim on JVM is JCA/`java.util` only (MessageDigest, Mac,
     * Cipher, SecureRandom, Base64, KeyStore, `java.nio.file`) — all present on
     * Android. So a JVM-capable function is Android-capable too (§278 port,
     * `D-TECHDEBT-23/09` = "port the stacks"). `kof.gpu` is NOT covered here:
     * its JVM runtime needs FFM (`java.lang.foreign`), absent on Android.
     */
    private static boolean jvmLike(Target t) { return t == Target.JVM || t == Target.ANDROID; }

    /**
     * Target support matrix. Unsupported calls produce a compile-time
     * diagnostic; never silently different behavior.
     */
    static boolean supportedOn(@SuppressWarnings("unused") String function, @SuppressWarnings("unused") Target target) {        // SECN000: o runtime riscv64/aarch64 (asm puro, sem libc) não tem
        // NENHUMA primitiva kof_sec_* (sha/hmac/aes/random/jwt/password/
        // session/api-key). Sem gate, a chamada quebrava no link com
        // undefined-reference (R6). Diagnóstico limpo em compile-time até o
        // port (SHA-256/AES são portáveis em asm; random exige getrandom).
        if (target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64) {
            return false;
        }
        return switch (function) {
            case "kof_sec_aesgcm_encrypt", "kof_sec_aesgcm_decrypt" ->
                    jvmLike(target) || target == Target.JS || target.isNative();
            // D-SEC chacha (13/09): JVM+JS nesta unidade (asm x86 de
            // ChaCha20+Poly1305 p/ NATIVE fica na fila — SECN002 com
            // diagnóstico em compile-time até o port, igual SECN000).
            case "kof_sec_chacha20_encrypt", "kof_sec_chacha20_decrypt" ->
                    jvmLike(target) || target == Target.JS;
            case "kof_sec_password_hash", "kof_sec_password_verify", "kof_sec_password_needs_rehash" ->
                    jvmLike(target) || target == Target.JS || target.isNative();
            case "kof_sec_sha512" -> jvmLike(target) || target == Target.JS || target.isNative();
            // D-KOF-DIGEST-BYTES: JVM+Android+Script (corpo JVM refletido) e
            // x86-Native (alias asm — o layout len@16/dados@24 do array é o
            // mesmo da String). JS (host bridge não traz byte[]) e os cross
            // (SECN000) seguem gap honesto nomeado, nunca silencioso.
            case "kof_sec_sha256_bytes", "kof_sec_hmac_sha256_bytes" ->
                    jvmLike(target) || target == Target.NATIVE;
            case "kof_sec_jwt_create", "kof_sec_jwt_create_ttl", "kof_sec_jwt_verify",
                    "kof_sec_jwt_verify_iss_aud", "kof_sec_jwt_secret" ->
                    jvmLike(target) || target == Target.JS || target.isNative();
            case "kof_sec_csrf_token", "kof_sec_csrf_valid", "kof_sec_cors_allowed",
                    "kof_sec_csp_header", "kof_sec_hsts_header", "kof_sec_content_type_options_header",
                    "kof_sec_frame_header", "kof_sec_referrer_header",
                    "kof_sec_auth_secret", "kof_sec_auth_token", "kof_sec_auth_authenticated",
                    "kof_sec_auth_claims", "kof_sec_auth_user", "kof_sec_auth_has_role",
                    "kof_sec_auth_has_permission" -> jvmLike(target);
            // D-SEC camada 16 (14/09): OAuth2 resource-server (JWKS + RSA/EC)
            // — JVM-only nesta unidade; Native/JS seguem gap honesto.
            case "kof_sec_auth_resource_server", "kof_sec_auth_resource_server_verify" ->
                    jvmLike(target);
            // D-SEC C11 (14/09): cookies parse/set — JVM+JS nesta unidade
            // (Native segue gap honesto em compile-time, igual SECN000/002).
            case "kof_sec_cookie_set", "kof_sec_cookie_set_opts", "kof_sec_cookie_get" ->
                    jvmLike(target) || target == Target.JS;
            // G9: available on all targets (JVM/Native/JS)
            case "kof_sec_rate_limit", "kof_sec_session_create", "kof_sec_session_get", "kof_sec_session_destroy",
                    "kof_sec_api_key_generate", "kof_sec_api_key_valid" -> true;
            // D-SECRETS face 1 (Stage 5/3.6): tipo Secret — JVM + Android
            // (paridade por backend, §278/D-TECHDEBT-23/09); JS/Native/Script
            // seguem gap honesto SECN008.
            // D-KOF-X25519: X25519/HKDF sobre JCA — JVM+Android (Script roda
            // o corpo JVM refletido). JS/Native/cross = gap honesto SECN009
            // (a face Secret ja e jvmLike; sem primitivas nos outros runtimes).
            case "kof_sec_x25519_private_key", "kof_sec_x25519_public_key",
                    "kof_sec_x25519_shared", "kof_sec_hkdf_sha256" -> jvmLike(target);
            // D-KOF-SIGN: mesma casa do X25519 — JVM/Android/Script; JS/Native/cross = SECN013.
            case "kof_sec_ed25519_private_key", "kof_sec_ed25519_public_key",
                    "kof_sec_ed25519_sign", "kof_sec_ed25519_verify" -> jvmLike(target);
            case "kof_sec_secret_of", "kof_sec_secret", "kof_sec_secret_reveal",
                    "kof_sec_secret_redacted", "kof_sec_secret_from_bytes" -> jvmLike(target);
            // D-SECRETS P3 (KeyHandle): JVM + Android; os demais alvos
            // seguem gap honesto SECN008 (nunca link-break/silencioso).
            case "kof_sec_key_from_hex", "kof_sec_key_from_pem", "kof_sec_key_from_keystore",
                    "kof_sec_key_rotate", "kof_sec_hmac_sha256_key",
                    "kof_sec_aesgcm_encrypt_key", "kof_sec_aesgcm_decrypt_key",
                    "kof_sec_chacha20_encrypt_key", "kof_sec_chacha20_decrypt_key",
                    "kof_sec_jwt_create_key", "kof_sec_jwt_create_ttl_key",
                    "kof_sec_jwt_verify_key", "kof_sec_jwt_verify_iss_aud_key" -> jvmLike(target);
            default -> true;
        };
    }

    /** Diagnostic code for target gaps (analogous to CONC001/JSN00x). */
    static String gapCode(String function) {
        return switch (function) {
            case "kof_sec_aesgcm_encrypt", "kof_sec_aesgcm_decrypt",
                    "kof_sec_chacha20_encrypt", "kof_sec_chacha20_decrypt" -> "SECN002";
            case "kof_sec_password_hash", "kof_sec_password_verify", "kof_sec_password_needs_rehash" -> "SECN001";
            case "kof_sec_sha512" -> "SECN003";
            case "kof_sec_jwt_create", "kof_sec_jwt_create_ttl", "kof_sec_jwt_verify",
                    "kof_sec_jwt_verify_iss_aud", "kof_sec_jwt_secret" -> "SECN004";
            case "kof_sec_rate_limit", "kof_sec_session_create", "kof_sec_session_get", "kof_sec_session_destroy",
                    "kof_sec_api_key_generate", "kof_sec_api_key_valid" -> "SECN005";
            case "kof_sec_cookie_set", "kof_sec_cookie_set_opts", "kof_sec_cookie_get" -> "SECN006";
            case "kof_sec_auth_resource_server", "kof_sec_auth_resource_server_verify" -> "SECN007";
            case "kof_sec_x25519_private_key", "kof_sec_x25519_public_key",
                    "kof_sec_x25519_shared", "kof_sec_hkdf_sha256",
                    "kof_sec_public_key_any" -> "SECN012";
            case "kof_sec_ed25519_private_key", "kof_sec_ed25519_public_key",
                    "kof_sec_ed25519_sign", "kof_sec_ed25519_verify" -> "SECN013";
            case "kof_sec_secret_of", "kof_sec_secret", "kof_sec_secret_reveal",
                    "kof_sec_secret_redacted", "kof_sec_secret_from_bytes",
                    "kof_sec_key_from_hex", "kof_sec_key_from_pem", "kof_sec_key_from_keystore",
                    "kof_sec_key_rotate", "kof_sec_hmac_sha256_key",
                    "kof_sec_aesgcm_encrypt_key", "kof_sec_aesgcm_decrypt_key",
                    "kof_sec_chacha20_encrypt_key", "kof_sec_chacha20_decrypt_key",
                    "kof_sec_jwt_create_key", "kof_sec_jwt_create_ttl_key",
                    "kof_sec_jwt_verify_key", "kof_sec_jwt_verify_iss_aud_key" -> "SECN008";
            default -> "SECN000";
        };
    }

    /**
     * §563 (R6): os ramos SecCall declaram os tipos dos parâmetros, mas a
     * guarda histórica era só de aridade — um argumento não-String passava no
     * typer e degradava por alvo: o Script digestava a IDENTIDADE do objeto
     * (dois arrays de mesmo conteúdo → digests diferentes: integridade
     * silenciosamente errada) e a JVM morria VerifyError no load. Medido
     * 02/10 na construção do quadro de integridade do KofShare. Este helper
     * fecha a divergência no typer com um diagnóstico NOMEADO (SECN011) —
     * nunca um fallback por backend. Conservador: só acusa quando a forma
     * declarada é String/Int e o real é uma referência composta (array,
     * classe, nullable de tal, função); null/desconhecido preserva o verde
     * histórico (mesma política do gate de handle do §179).
     */
    static String argTypeViolation(SecCall call, List<Type> actuals) {
        if (call == null || actuals == null) return null;
        List<Type> decl = call.parameterTypes();
        if (decl == null) return null;
        if (decl.size() != actuals.size()) return null;
        for (int i = 0; i < decl.size(); i++) {
            Type d = decl.get(i);
            Type a = actuals.get(i);
            if (a == null || a instanceof Type.UnknownType) continue;
            if (SECRET.equals(d)) {
                if (!SECRET.equals(a)) return "SECN014";
                continue;
            }
            if (BYTE_ARRAY.equals(d)) {
                if (!(a instanceof Type.ArrayType at && Type.PrimitiveType.BYTE.equals(at.componentType()))) {
                    return "SECN013";
                }
                continue;
            }
            if (BuiltinTypes.STRING.equals(d) && !isStringish(a)) return "SECN011";
            if (Type.PrimitiveType.INT.equals(d) && !(a instanceof Type.PrimitiveType pt && pt == Type.PrimitiveType.INT)) {
                if (!(a instanceof Type.PrimitiveType pt2 && (pt2 == Type.PrimitiveType.LONG || pt2 == Type.PrimitiveType.SHORT || pt2 == Type.PrimitiveType.BYTE || pt2 == Type.PrimitiveType.CHAR))) {
                    return "SECN011";
                }
            }
        }
        return null;
    }

    static boolean isStringish(Type t) {
        if (BuiltinTypes.STRING.equals(t)) return true;
        if (t instanceof Type.NullableType nt) return isStringish(nt.inner());
        return t instanceof Type.ClassType ct && "java.lang.String".equals(ct.packageName() + "." + ct.name());
    }
}
