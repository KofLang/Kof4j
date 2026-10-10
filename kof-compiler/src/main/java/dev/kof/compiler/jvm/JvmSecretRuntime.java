package dev.kof.compiler.jvm;

/** JVM runtime for the {@code Secret} value type (D-SECRETS face 1, Stage 5 /
 *  3.6). {@code KofRuntime$Secret} wraps the raw text; {@code toString()} is the
 *  REDACTED form ({@code Secret(*** )} — never the value, never a prefix) and
 *  {@code equals} is constant-time, so a {@code println} or an interpolation
 *  cannot leak the value and {@code ==} does not short-circuit on content.
 *  {@code reveal()} is the only export. JS/Native never reach this file (honest
 *  gap SECN008 upstream); Native also would need a zeroable buffer (later face). */
public final class JvmSecretRuntime {
    private JvmSecretRuntime() {}

    static String source() {
        return """
                // ── kof.secrets — Secret value type (D-SECRETS face 1, JVM) ──
                public static final class Secret {
                    private final String value;
                    private Secret(String value) { this.value = value == null ? "" : value; }
                    @Override public String toString() { return "Secret(*** )"; }
                    // Identidade intencional (nunca o conteúdo): um mapa não indexa
                    // segredos por conteúdo — o plano P1 exige isto (D-SECRETS).
                    @Override public int hashCode() { return System.identityHashCode(this); }
                    @Override public boolean equals(Object o) {
                        if (this == o) return true;
                        if (!(o instanceof Secret s)) return false;
                        return kof_sec_constant_time_equals(this.value, s.value);
                    }
                }

                public static Secret kof_sec_secret_of(String value) {
                    return new Secret(value);
                }

                // SEC1 (`D-MAINT-BATCH-0510`, issue #758): an unset/blank env var is
                // an EXPLICIT error, never a silent empty `Secret` (a blank credential
                // is a security failure that must surface). Thrown as a catchable
                // Kof String (RuntimeException message), matching the frozen
                // "Kof throws Strings" contract.
                public static Secret kof_sec_secret(String name) {
                    String value = kof_sec_secret_get(name);
                    if (value == null || value.isBlank()) {
                        throw new RuntimeException("SECN015: secret '" + name + "' is not set");
                    }
                    return new Secret(value);
                }

                // Um int por byte (0..255); visão Latin-1 — round-trip SEM perda
                // (UTF-8 substituiria sequências inválidas em silêncio, inaceitável
                // para material de chave). D-SECRETS P1.
                public static Secret kof_sec_secret_from_bytes(int[] bytes) {
                    if (bytes == null) return new Secret("");
                    byte[] raw = new byte[bytes.length];
                    for (int i = 0; i < bytes.length; i++) raw[i] = (byte) (bytes[i] & 0xff);
                    return new Secret(new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1));
                }

                public static String kof_sec_secret_reveal(Secret s) {
                    return s == null ? null : s.value;
                }

                public static String kof_sec_secret_redacted(Secret s) {
                    return "***";
                }

                // ── kof.secrets — KeyHandle (D-SECRETS P3, JVM) ──────────────
                // Uma chave nomeada que NUNCA expoe bytes ao guest: so os
                // algoritmos de crypto a consomem. rotate() revoga o handle
                // antigo; usa-lo depois falha com SECN010 (honesto, nunca
                // silencioso).
                public static final class KeyHandle {
                    private final byte[] bytes;
                    private boolean revoked;
                    private KeyHandle(byte[] bytes) { this.bytes = bytes == null ? new byte[0] : bytes; }
                    @Override public String toString() { return "KeyHandle(*** )"; }
                    @Override public int hashCode() { return System.identityHashCode(this); }
                    @Override public boolean equals(Object o) { return this == o; }
                }

                private static byte[] kof_sec_key_material(KeyHandle k) {
                    if (k == null) throw new IllegalArgumentException("KeyHandle nulo");
                    if (k.revoked) throw new IllegalStateException("SECN010: KeyHandle revogado (rotate()); use o handle novo");
                    return k.bytes;
                }

                public static KeyHandle kof_sec_key_from_hex(String hex) {
                    return new KeyHandle(kof_sec_fromHex(hex));
                }

                public static KeyHandle kof_sec_key_from_pem(String path) {
                    try {
                        String text = java.nio.file.Files.readString(java.nio.file.Path.of(path));
                        int begin = text.indexOf("-----BEGIN");
                        int beginEnd = text.indexOf("-----", begin + 10);
                        int end = text.indexOf("-----END", beginEnd + 5);
                        if (begin < 0 || beginEnd < 0 || end < 0) {
                            throw new IllegalArgumentException("PEM sem bloco BEGIN/END: " + path);
                        }
                        String body = text.substring(beginEnd + 5, end).replaceAll("\\\\s", "");
                        return new KeyHandle(java.util.Base64.getDecoder().decode(body));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("PEM ilegivel: " + path, e);
                    }
                }

                public static KeyHandle kof_sec_key_from_keystore(String path, String alias, String password) {
                    try {
                        String type = path.endsWith(".p12") || path.endsWith(".pfx") ? "PKCS12" : "JKS";
                        java.security.KeyStore ks = java.security.KeyStore.getInstance(type);
                        char[] pw = password == null ? new char[0] : password.toCharArray();
                        try (java.io.InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(path))) {
                            ks.load(in, pw);
                        }
                        java.security.Key key = ks.getKey(alias, pw);
                        if (key == null) throw new IllegalArgumentException("alias ausente no keystore: " + alias);
                        return new KeyHandle(key.getEncoded());
                    } catch (Exception e) {
                        throw new RuntimeException("keystore ilegivel: " + path, e);
                    }
                }

                public static KeyHandle kof_sec_key_rotate(KeyHandle k) {
                    kof_sec_key_material(k);
                    k.revoked = true;
                    byte[] fresh = new byte[32];
                    KOF_SEC_RANDOM.nextBytes(fresh);
                    return new KeyHandle(fresh);
                }

                public static String kof_sec_hmac_sha256_key(KeyHandle k, String data) {
                    try {
                        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        mac.init(new javax.crypto.spec.SecretKeySpec(kof_sec_key_material(k), "HmacSHA256"));
                        return kof_sec_hex(mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_aesgcm_encrypt_key(String plaintext, KeyHandle k) {
                    return kof_sec_aesgcm_encrypt(plaintext, kof_sec_hex(kof_sec_key_material(k)));
                }

                public static String kof_sec_aesgcm_decrypt_key(String ciphertext, KeyHandle k) {
                    return kof_sec_aesgcm_decrypt(ciphertext, kof_sec_hex(kof_sec_key_material(k)));
                }

                public static String kof_sec_chacha20_encrypt_key(String plaintext, KeyHandle k) {
                    return kof_sec_chacha20_encrypt(plaintext, kof_sec_hex(kof_sec_key_material(k)));
                }

                public static String kof_sec_chacha20_decrypt_key(String ciphertext, KeyHandle k) {
                    return kof_sec_chacha20_decrypt(ciphertext, kof_sec_hex(kof_sec_key_material(k)));
                }

                public static String kof_sec_jwt_create_key(String claimsJson, KeyHandle k) {
                    return kof_sec_jwt_create_ttl_bytes(claimsJson, kof_sec_key_material(k), 3600);
                }

                public static String kof_sec_jwt_create_ttl_key(String claimsJson, KeyHandle k, int ttlSeconds) {
                    return kof_sec_jwt_create_ttl_bytes(claimsJson, kof_sec_key_material(k), ttlSeconds);
                }

                public static String kof_sec_jwt_verify_key(String token, KeyHandle k) {
                    return kof_sec_jwt_verify_iss_aud_bytes(token, kof_sec_key_material(k), null, null);
                }

                public static String kof_sec_jwt_verify_iss_aud_key(String token, KeyHandle k, String issuer, String audience) {
                    return kof_sec_jwt_verify_iss_aud_bytes(token, kof_sec_key_material(k), issuer, audience);
                }

                // ── D-KOF-X25519 (02/10): X25519 (RFC 7748) + HKDF-SHA256
                // (RFC 5869) — the session-key face. Scalars/points travel as
                // little-endian 32-byte hex inside `Secret` (never raw String;
                // R8). JCA ships both curves since 11; no BouncyCastle.
                public static Secret kof_sec_x25519_private_key() {
                    byte[] scalar = new byte[32];
                    new java.security.SecureRandom().nextBytes(scalar);
                    scalar[0] &= (byte) 0xf8;
                    scalar[31] = (byte) ((scalar[31] & 0x7f) | 0x40);
                    return new Secret(kof_sec_hex(scalar));
                }

                // D-KOF-SIGN (mantenedora 03/10, fatia C1): assinatura Ed25519 sobre
                // primitiva do JDK (Kof codifica; nada de criptografia caseira). O
                // Secret guarda hex PKCS8(48B)||SPKI(44B) = 184 chars; publicKey
                // exporta os 32 bytes crus (64 hex, mesmo formato do hello X25519).
                public static Secret kof_sec_ed25519_private_key(String alg) {
                    if (!"Ed25519".equals(alg)) {
                        throw new IllegalArgumentException("SECN014: unknown signing algorithm " + alg);
                    }
                    try {
                        java.security.KeyPair kp = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                        return new Secret(kof_sec_hex(kp.getPrivate().getEncoded()) + kof_sec_hex(kp.getPublic().getEncoded()));
                    } catch (java.security.GeneralSecurityException e) {
                        throw new RuntimeException("SECN014: " + e.getMessage(), e);
                    }
                }

                private static byte[] kof_sec_ed25519_spki(String hexValue) {
                    if (hexValue.length() == 184) return kof_sec_fromHex(hexValue.substring(96));
                    if (hexValue.length() == 64) return kof_sec_fromHex("302a300506032b6570032100" + hexValue);
                    throw new IllegalArgumentException("SECN014: not an Ed25519 key secret (hex length " + hexValue.length() + ")");
                }

                public static String kof_sec_ed25519_public_key(Secret priv) {
                    byte[] spki = kof_sec_ed25519_spki(priv.value);
                    if (spki.length != 44) {
                        throw new IllegalArgumentException("SECN014: malformed Ed25519 SPKI");
                    }
                    return kof_sec_hex(java.util.Arrays.copyOfRange(spki, 12, 44));
                }

                public static String kof_sec_ed25519_sign(Secret priv, byte[] msg) {
                    String hex = priv.value;
                    if (hex.length() != 184) {
                        throw new IllegalArgumentException("SECN014: sign needs an Ed25519 private key secret");
                    }
                    try {
                        java.security.PrivateKey k = java.security.KeyFactory.getInstance("Ed25519")
                                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(kof_sec_fromHex(hex.substring(0, 96))));
                        java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
                        sig.initSign(k);
                        sig.update(msg);
                        return kof_sec_hex(sig.sign());
                    } catch (java.security.GeneralSecurityException e) {
                        throw new RuntimeException("SECN014: " + e.getMessage(), e);
                    }
                }

                public static boolean kof_sec_ed25519_verify(Secret key, byte[] msg, String sigHex) {
                    try {
                        java.security.PublicKey k = java.security.KeyFactory.getInstance("Ed25519")
                                .generatePublic(new java.security.spec.X509EncodedKeySpec(kof_sec_ed25519_spki(key.value)));
                        java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
                        sig.initVerify(k);
                        sig.update(msg);
                        return sig.verify(kof_sec_fromHex(sigHex));
                    } catch (java.security.SignatureException e) {
                        return false; // assinatura malformada = nao valida, nunca crash
                    } catch (java.security.GeneralSecurityException e) {
                        throw new RuntimeException("SECN014: " + e.getMessage(), e);
                    }
                }

                // keyExchange.publicKey(Secret): dispatch pelo formato do Secret —
                // 64-hex = scalar X25519 (path histórico, D-KOF-X25519);
                // 184-hex = Ed25519 private (D-KOF-SIGN C1).
                public static String kof_sec_public_key_any(Secret s) {
                    if (s.value != null && s.value.length() == 184) return kof_sec_ed25519_public_key(s);
                    return kof_sec_x25519_public_key(s);
                }

                private static java.math.BigInteger kof_sec_le_to_bn(byte[] le) {
                    byte[] be = new byte[le.length];
                    for (int i = 0; i < le.length; i++) be[i] = le[le.length - 1 - i];
                    return new java.math.BigInteger(1, be);
                }

                private static byte[] kof_sec_bn_to_le(java.math.BigInteger bn, int n) {
                    byte[] be = bn.toByteArray();
                    byte[] le = new byte[n];
                    int src = be.length - 1;
                    for (int i = 0; i < n && src >= 0; i++) {
                        le[i] = be[src];
                        src--;
                    }
                    return le;
                }

                private static java.security.KeyFactory kof_sec_x25519_factory() {
                    try {
                        return java.security.KeyFactory.getInstance("X25519");
                    } catch (java.security.NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }

                private static java.security.PrivateKey kof_sec_x25519_private(String scalarHex) {
                    try {
                        return kof_sec_x25519_factory().generatePrivate(
                                new java.security.spec.XECPrivateKeySpec(
                                        java.security.spec.NamedParameterSpec.X25519,
                                        kof_sec_fromHex(scalarHex)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                private static java.security.PublicKey kof_sec_x25519_public(java.math.BigInteger u) {
                    try {
                        return kof_sec_x25519_factory().generatePublic(
                                new java.security.spec.XECPublicKeySpec(
                                        java.security.spec.NamedParameterSpec.X25519, u));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                private static byte[] kof_sec_x25519_agree(Secret priv, java.security.PublicKey peerPub) {
                    try {
                        javax.crypto.KeyAgreement ka = javax.crypto.KeyAgreement.getInstance("X25519");
                        ka.init(kof_sec_x25519_private(priv.value));
                        ka.doPhase(peerPub, true);
                        return ka.generateSecret();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_x25519_public_key(Secret priv) {
                    byte[] point = kof_sec_x25519_agree(priv, kof_sec_x25519_public(java.math.BigInteger.valueOf(9)));
                    return kof_sec_hex(point);
                }

                public static Secret kof_sec_x25519_shared(Secret priv, Secret peerPublicHex) {
                    byte[] shared = kof_sec_x25519_agree(priv,
                            kof_sec_x25519_public(kof_sec_le_to_bn(kof_sec_fromHex(peerPublicHex.value))));
                    return new Secret(kof_sec_hex(shared));
                }

                public static String kof_sec_hkdf_sha256(Secret ikmHex, String saltHex, String infoHex, int len) {
                    if (len < 1 || len > 8160) {
                        throw new RuntimeException("kof.security: HKDF length " + len
                                + " out of range 1..8160");
                    }
                    byte[] ikm = kof_sec_fromHex(ikmHex.value);
                    byte[] salt = kof_sec_fromHex(saltHex);
                    byte[] info = kof_sec_fromHex(infoHex);
                    try {
                        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        byte[] zero32 = new byte[32];
                        mac.init(new javax.crypto.spec.SecretKeySpec(
                                salt.length == 0 ? zero32 : salt, "HmacSHA256"));
                        byte[] prk = mac.doFinal(ikm);
                        java.io.ByteArrayOutputStream okm = new java.io.ByteArrayOutputStream();
                        byte[] t = new byte[0];
                        int counter = 1;
                        while (okm.size() < len) {
                            mac.init(new javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"));
                            mac.update(t);
                            mac.update(info);
                            mac.update((byte) counter);
                            t = mac.doFinal();
                            okm.write(t, 0, t.length);
                            counter++;
                        }
                        byte[] out = java.util.Arrays.copyOf(okm.toByteArray(), len);
                        return kof_sec_hex(out);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }


                """;
    }
}
