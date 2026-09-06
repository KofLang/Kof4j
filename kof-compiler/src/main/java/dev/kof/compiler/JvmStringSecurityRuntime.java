package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.security (digest/HMAC/random/PBKDF2/AES-GCM/JWT/auth/CSRF/CORS/headers) - parte 2/5 de JvmStringRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmStringSecurityRuntime {

    private JvmStringSecurityRuntime() {}

    static String source() {
        return """

                // ── kof.security (docs/security.md §5) ──────────────────

                private static final java.security.SecureRandom KOF_SEC_RANDOM = new java.security.SecureRandom();
                private static volatile String KOF_AUTH_SECRET = System.getenv("KOF_JWT_SECRET");
                private static final ThreadLocal<String> KOF_AUTH_CLAIMS = new ThreadLocal<>();
                private static final ThreadLocal<String> KOF_CSRF_TOKEN = new ThreadLocal<>();
                private static final int KOF_PBKDF2_ITERATIONS = 600_000;

                private static final char[] KOF_SEC_HEX = "0123456789abcdef".toCharArray();

                private static String kof_sec_hex(byte[] bytes) {
                    StringBuilder sb = new StringBuilder(bytes.length * 2);
                    for (byte b : bytes) {
                        sb.append(KOF_SEC_HEX[(b >> 4) & 0xF]);
                        sb.append(KOF_SEC_HEX[b & 0xF]);
                    }
                    return sb.toString();
                }

                private static byte[] kof_sec_fromHex(String hex) {
                    if (hex == null || (hex.length() & 1) != 0) throw new IllegalArgumentException("invalid hex");
                    byte[] out = new byte[hex.length() / 2];
                    for (int i = 0; i < out.length; i++) {
                        out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                    }
                    return out;
                }

                public static String kof_sec_sha256(String data) {
                    try {
                        return kof_sec_hex(java.security.MessageDigest.getInstance("SHA-256")
                                .digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (java.security.NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_sha512(String data) {
                    try {
                        return kof_sec_hex(java.security.MessageDigest.getInstance("SHA-512")
                                .digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (java.security.NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_hmac_sha256(String key, String data) {
                    try {
                        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        mac.init(new javax.crypto.spec.SecretKeySpec(
                                key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
                        return kof_sec_hex(mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_random_hex(int bytes) {
                    if (bytes < 0 || bytes > 4096) throw new IllegalArgumentException("invalid length: " + bytes);
                    byte[] buf = new byte[bytes];
                    KOF_SEC_RANDOM.nextBytes(buf);
                    return kof_sec_hex(buf);
                }

                public static int kof_sec_random_int(int bound) {
                    if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
                    return KOF_SEC_RANDOM.nextInt(bound);
                }

                public static boolean kof_sec_constant_time_equals(String a, String b) {
                    if (a == null || b == null) return a == b;
                    return java.security.MessageDigest.isEqual(
                            a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                public static String kof_sec_redact(String value) {
                    if (value == null) return null;
                    if (value.length() <= 8) return "********";
                    return value.substring(0, 4) + "********" + value.substring(value.length() - 4);
                }

                public static String kof_sec_secret_get(String name) {
                    return System.getenv(name);
                }

                public static String kof_sec_secret_get_default(String name, String fallback) {
                    String v = System.getenv(name);
                    return v == null ? fallback : v;
                }

                // password hashing — pbkdf2$sha256$<iterations>$<saltB64>$<hashB64>

                public static String kof_sec_password_hash(String password) {
                    try {
                        byte[] salt = new byte[16];
                        KOF_SEC_RANDOM.nextBytes(salt);
                        javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                                password.toCharArray(), salt, KOF_PBKDF2_ITERATIONS, 256);
                        byte[] dk = f.generateSecret(spec).getEncoded();
                        return "pbkdf2$sha256$" + KOF_PBKDF2_ITERATIONS + "$"
                                + java.util.Base64.getEncoder().encodeToString(salt) + "$"
                                + java.util.Base64.getEncoder().encodeToString(dk);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static boolean kof_sec_password_verify(String password, String hash) {
                    if (hash == null) return false;
                    String[] parts = hash.split("\\\\$");
                    if (parts.length != 5 || !"pbkdf2".equals(parts[0]) || !"sha256".equals(parts[1])) return false;
                    try {
                        int iterations = Integer.parseInt(parts[2]);
                        byte[] salt = java.util.Base64.getDecoder().decode(parts[3]);
                        byte[] expected = java.util.Base64.getDecoder().decode(parts[4]);
                        javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                                password.toCharArray(), salt, iterations, expected.length * 8);
                        byte[] actual = f.generateSecret(spec).getEncoded();
                        return java.security.MessageDigest.isEqual(expected, actual);
                    } catch (Exception e) {
                        return false;
                    }
                }

                public static boolean kof_sec_password_needs_rehash(String hash) {
                    if (hash == null) return true;
                    String[] parts = hash.split("\\\\$");
                    if (parts.length != 5 || !"pbkdf2".equals(parts[0]) || !"sha256".equals(parts[1])) return true;
                    try {
                        return Integer.parseInt(parts[2]) < KOF_PBKDF2_ITERATIONS;
                    } catch (NumberFormatException e) {
                        return true;
                    }
                }

                // AES-GCM — aesgcm$<ivB64>$<ciphertextAndTagB64>

                public static String kof_sec_aesgcm_encrypt(String plaintext, String keyHex) {
                    try {
                        byte[] key = kof_sec_fromHex(keyHex);
                        if (key.length != 32) throw new IllegalArgumentException("AES-GCM key must be 32 bytes (64 hex chars)");
                        byte[] iv = new byte[12];
                        KOF_SEC_RANDOM.nextBytes(iv);
                        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"),
                                new javax.crypto.spec.GCMParameterSpec(128, iv));
                        byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        return "aesgcm$" + java.util.Base64.getEncoder().encodeToString(iv) + "$"
                                + java.util.Base64.getEncoder().encodeToString(ct);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_aesgcm_decrypt(String ciphertext, String keyHex) {
                    try {
                        byte[] key = kof_sec_fromHex(keyHex);
                        if (key.length != 32) throw new IllegalArgumentException("AES-GCM key must be 32 bytes (64 hex chars)");
                        String[] parts = ciphertext.split("\\\\$");
                        if (parts.length != 3 || !"aesgcm".equals(parts[0])) throw new IllegalArgumentException("invalid ciphertext format");
                        byte[] iv = java.util.Base64.getDecoder().decode(parts[1]);
                        byte[] ct = java.util.Base64.getDecoder().decode(parts[2]);
                        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"),
                                new javax.crypto.spec.GCMParameterSpec(128, iv));
                        return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new RuntimeException("decryption failed: " + e.getMessage());
                    }
                }

                // JWT — HS256 only; the algorithm is never taken from the token.

                private static String kof_sec_b64url(byte[] data) {
                    return java.util.Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(data);
                }

                private static byte[] kof_sec_b64urlDecode(String s) {
                    return java.util.Base64.getUrlDecoder().decode(s);
                }

                private static String kof_sec_jwt_sign(String headerB64, String payloadB64, String secret) {
                    try {
                        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        mac.init(new javax.crypto.spec.SecretKeySpec(
                                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
                        return kof_sec_b64url(mac.doFinal(
                                (headerB64 + "." + payloadB64).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_jwt_secret() {
                    String secret = System.getenv("KOF_JWT_SECRET");
                    if (secret != null && !secret.isBlank()) return secret;
                    return kof_sec_random_hex(32);
                }

                public static String kof_sec_jwt_create(String claimsJson, String secret) {
                    return kof_sec_jwt_create_ttl(claimsJson, secret, 3600);
                }

                public static String kof_sec_jwt_create_ttl(String claimsJson, String secret, int ttlSeconds) {
                    Object parsed = kof_json_parse(claimsJson);
                    if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException("JWT claims must be a JSON object");
                    int lastBrace = claimsJson.lastIndexOf('}');
                    if (lastBrace < 0) throw new IllegalArgumentException("JWT claims must be a JSON object");
                    String head = claimsJson.substring(0, lastBrace).trim();
                    String sep = head.isEmpty() || head.endsWith("{") ? "" : ",";
                    long now = System.currentTimeMillis() / 1000;
                    String payload = head + sep + "\\"iat\\":" + now + ",\\"exp\\":" + (now + ttlSeconds) + "}";
                    String headerB64 = kof_sec_b64url("{\\"alg\\":\\"HS256\\",\\"typ\\":\\"JWT\\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    String payloadB64 = kof_sec_b64url(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return headerB64 + "." + payloadB64 + "." + kof_sec_jwt_sign(headerB64, payloadB64, secret);
                }

                public static String kof_sec_jwt_verify(String token, String secret) {
                    return kof_sec_jwt_verify_iss_aud(token, secret, null, null);
                }

                public static String kof_sec_jwt_verify_iss_aud(String token, String secret, String issuer, String audience) {
                    if (token == null || secret == null) throw new IllegalArgumentException("invalid token or secret");
                    String[] parts = token.split("\\\\.");
                    if (parts.length != 3) throw new IllegalArgumentException("malformed token");
                    try {
                        String headerJson = new String(kof_sec_b64urlDecode(parts[0]), java.nio.charset.StandardCharsets.UTF_8);
                        if (!headerJson.contains("\\"HS256\\"")) throw new IllegalArgumentException("algorithm not allowed");
                        String expected = kof_sec_jwt_sign(parts[0], parts[1], secret);
                        if (!kof_sec_constant_time_equals(expected, parts[2])) throw new IllegalArgumentException("invalid signature");
                        String payloadJson = new String(kof_sec_b64urlDecode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
                        Object parsed = kof_json_parse(payloadJson);
                        if (!(parsed instanceof Map<?, ?> claims)) throw new IllegalArgumentException("invalid payload");
                        Object exp = claims.get("exp");
                        if (exp instanceof Number n && n.longValue() * 1000 <= System.currentTimeMillis()) {
                            throw new IllegalArgumentException("token expired");
                        }
                        if (issuer != null) {
                            Object iss = claims.get("iss");
                            if (!(iss instanceof String s && s.equals(issuer))) {
                                throw new IllegalArgumentException("issuer mismatch");
                            }
                        }
                        if (audience != null) {
                            Object aud = claims.get("aud");
                            if (!(aud instanceof String s && s.equals(audience))) {
                                throw new IllegalArgumentException("audience mismatch");
                            }
                        }
                        return payloadJson;
                    } catch (IllegalArgumentException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IllegalArgumentException("malformed token");
                    }
                }

                // Web security context (kof.security.auth) — request-scoped.

                public static boolean kof_sec_auth_secret(String secret) {
                    if (secret == null || secret.isBlank()) return false;
                    KOF_AUTH_SECRET = secret;
                    return true;
                }

                private static String kof_sec_auth_bearerToken() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    if (req == null) return null;
                    String auth = req.header("Authorization");
                    if (auth == null) return null;
                    if (auth.startsWith("Bearer ")) return auth.substring(7);
                    if (auth.startsWith("bearer ")) return auth.substring(7);
                    return auth;
                }

                private static boolean kof_sec_auth_resolve() {
                    String cached = KOF_AUTH_CLAIMS.get();
                    if (cached != null) return true;
                    String token = kof_sec_auth_bearerToken();
                    if (token == null || KOF_AUTH_SECRET == null || KOF_AUTH_SECRET.isBlank()) return false;
                    try {
                        KOF_AUTH_CLAIMS.set(kof_sec_jwt_verify(token, KOF_AUTH_SECRET));
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                }

                public static String kof_sec_auth_token() {
                    return kof_sec_auth_bearerToken();
                }

                public static boolean kof_sec_auth_authenticated() {
                    return kof_sec_auth_resolve();
                }

                public static String kof_sec_auth_claims() {
                    if (!kof_sec_auth_resolve()) return null;
                    return KOF_AUTH_CLAIMS.get();
                }

                public static String kof_sec_auth_user() {
                    Object claims = kof_sec_auth_claims();
                    if (claims == null) return null;
                    try {
                        Object parsed = kof_json_parse((String) claims);
                        if (parsed instanceof Map<?, ?> m && m.get("sub") instanceof String sub) return sub;
                    } catch (IllegalArgumentException ignored) {
                    }
                    return null;
                }

                public static boolean kof_sec_auth_has_role(String role) {
                    return kof_sec_auth_claimContains("roles", role);
                }

                public static boolean kof_sec_auth_has_permission(String permission) {
                    return kof_sec_auth_claimContains("permissions", permission);
                }

                private static boolean kof_sec_auth_claimContains(String claim, String value) {
                    Object claims = kof_sec_auth_claims();
                    if (claims == null) return false;
                    try {
                        Object parsed = kof_json_parse((String) claims);
                        if (!(parsed instanceof Map<?, ?> m)) return false;
                        Object v = m.get(claim);
                        if (v instanceof String s) return s.equals(value);
                        if (v instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof String s && s.equals(value)) return true;
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                    return false;
                }

                // CSRF / CORS / security headers

                public static String kof_sec_csrf_token() {
                    String existing = KOF_CSRF_TOKEN.get();
                    if (existing != null) return existing;
                    String token = kof_sec_random_hex(32);
                    KOF_CSRF_TOKEN.set(token);
                    return token;
                }

                public static boolean kof_sec_csrf_valid(String token) {
                    String expected = KOF_CSRF_TOKEN.get();
                    if (expected == null || token == null) return false;
                    return kof_sec_constant_time_equals(expected, token);
                }

                public static boolean kof_sec_cors_allowed(String origin, String allowed) {
                    if (allowed == null) return false;
                    if ("*".equals(allowed)) return true;
                    for (String a : allowed.split(",")) {
                        if (a.trim().equals(origin)) return true;
                    }
                    return false;
                }

                public static String kof_sec_csp_header() {
                    return "default-src 'self'; frame-ancestors 'none'; base-uri 'self'";
                }

                public static String kof_sec_hsts_header() {
                    return "max-age=31536000; includeSubDomains";
                }

                public static String kof_sec_content_type_options_header() {
                    return "nosniff";
                }

                public static String kof_sec_frame_header() {
                    return "DENY";
                }

                public static String kof_sec_referrer_header() {
                    return "no-referrer";
                }
""";
    }
}
