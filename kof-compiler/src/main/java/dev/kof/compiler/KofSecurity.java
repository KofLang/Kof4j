package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch table for {@code kof.security} (docs/security.md).
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
final class KofSecurity {

    private KofSecurity() {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type INT = Type.PrimitiveType.INT;

    static final List<String> NAMESPACES = List.of(
            "passwords", "crypto", "jwt", "secrets", "security", "auth");

    static boolean isSecurityNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record SecCall(String function, Type returnType, List<Type> parameterTypes) {}

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
                case "hmacSha256" -> argc == 2
                        ? new SecCall("kof_sec_hmac_sha256", STR, List.of(STR, STR)) : null;
                case "encryptAesGcm" -> argc == 2
                        ? new SecCall("kof_sec_aesgcm_encrypt", STR, List.of(STR, STR)) : null;
                case "decryptAesGcm" -> argc == 2
                        ? new SecCall("kof_sec_aesgcm_decrypt", STR, List.of(STR, STR)) : null;
                case "randomHex" -> argc == 1
                        ? new SecCall("kof_sec_random_hex", STR, List.of(INT)) : null;
                case "randomInt" -> argc == 1
                        ? new SecCall("kof_sec_random_int", INT, List.of(INT)) : null;
                default -> null;
            };
            case "jwt" -> switch (name) {
                case "create" -> argc == 2
                        ? new SecCall("kof_sec_jwt_create", STR, List.of(STR, STR))
                        : argc == 3
                        ? new SecCall("kof_sec_jwt_create_ttl", STR, List.of(STR, STR, INT))
                        : null;
                case "verify" -> argc == 2
                        ? new SecCall("kof_sec_jwt_verify", STR, List.of(STR, STR))
                        : argc == 4
                        ? new SecCall("kof_sec_jwt_verify_iss_aud", STR, List.of(STR, STR, STR, STR))
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
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * Target support matrix. Unsupported calls produce a compile-time
     * diagnostic; never silently different behavior.
     */
    static boolean supportedOn(String function, Target target) {
        // SECN000: o runtime riscv64/aarch64 (asm puro, sem libc) não tem
        // NENHUMA primitiva kof_sec_* (sha/hmac/aes/random/jwt/password/
        // session/api-key). Sem gate, a chamada quebrava no link com
        // undefined-reference (R6). Diagnóstico limpo em compile-time até o
        // port (SHA-256/AES são portáveis em asm; random exige getrandom).
        if (target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64) {
            return false;
        }
        return switch (function) {
            case "kof_sec_aesgcm_encrypt", "kof_sec_aesgcm_decrypt" ->
                    target == Target.JVM || target == Target.JS || target.isNative();
            case "kof_sec_password_hash", "kof_sec_password_verify", "kof_sec_password_needs_rehash" ->
                    target == Target.JVM || target == Target.JS || target.isNative();
            case "kof_sec_sha512" -> target == Target.JVM || target == Target.JS || target.isNative();
            case "kof_sec_jwt_create", "kof_sec_jwt_create_ttl", "kof_sec_jwt_verify",
                    "kof_sec_jwt_verify_iss_aud", "kof_sec_jwt_secret" ->
                    target == Target.JVM || target == Target.JS || target.isNative();
            case "kof_sec_csrf_token", "kof_sec_csrf_valid", "kof_sec_cors_allowed",
                    "kof_sec_csp_header", "kof_sec_hsts_header", "kof_sec_content_type_options_header",
                    "kof_sec_frame_header", "kof_sec_referrer_header",
                    "kof_sec_auth_secret", "kof_sec_auth_token", "kof_sec_auth_authenticated",
                    "kof_sec_auth_claims", "kof_sec_auth_user", "kof_sec_auth_has_role",
                    "kof_sec_auth_has_permission" -> target == Target.JVM;
            // G9: available on all targets (JVM/Native/JS)
            case "kof_sec_rate_limit", "kof_sec_session_create", "kof_sec_session_get", "kof_sec_session_destroy",
                    "kof_sec_api_key_generate", "kof_sec_api_key_valid" -> true;
            default -> true;
        };
    }

    /** Diagnostic code for target gaps (analogous to CONC001/JSN00x). */
    static String gapCode(String function) {
        return switch (function) {
            case "kof_sec_aesgcm_encrypt", "kof_sec_aesgcm_decrypt" -> "SECN002";
            case "kof_sec_password_hash", "kof_sec_password_verify", "kof_sec_password_needs_rehash" -> "SECN001";
            case "kof_sec_sha512" -> "SECN003";
            case "kof_sec_jwt_create", "kof_sec_jwt_create_ttl", "kof_sec_jwt_verify",
                    "kof_sec_jwt_verify_iss_aud", "kof_sec_jwt_secret" -> "SECN004";
            case "kof_sec_rate_limit", "kof_sec_session_create", "kof_sec_session_get", "kof_sec_session_destroy",
                    "kof_sec_api_key_generate", "kof_sec_api_key_valid" -> "SECN005";
            default -> "SECN000";
        };
    }
}