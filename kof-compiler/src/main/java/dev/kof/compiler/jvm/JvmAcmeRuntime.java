package dev.kof.compiler.jvm;

/**
 * Runtime ACME (RFC 8555) — o núcleo JWS ES256, a camada ACCOUNT (fatia 4
 * incremento 2) e a camada ORDER (incremento 3) do plano
 * {@code docs/development/tls-hardening-plan.md}. Extraído de
 * {@code JvmWebHttpRuntime} para o check_500 (a camada ORDER empurrou o
 * arquivo além de 600 linhas); concatenação preserva ordem e conteúdo
 * byte-a-byte. A chave de conta é SEMPRE do operador (nunca persistida
 * pelo Kof); o DNS é do operador (non-goal do plano).
 */
public final class JvmAcmeRuntime {

    private JvmAcmeRuntime() {}

    static String source() {
        return """
                // ── TLS hardening slice 4 (increment 1): the ACME JWS core ──

                private static byte[] kofAcmeB64url(byte[] src) {
                    return java.util.Base64.getUrlEncoder().withoutPadding().encode(src);
                }

                private static byte[] kofAcmeB64urlDecode(String s) {
                    return java.util.Base64.getUrlDecoder().decode(s);
                }

                private static java.security.PrivateKey kofAcmeKey(String pem) throws Exception {
                    String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replaceAll("\\s", "");
                    byte[] der = java.util.Base64.getMimeDecoder().decode(body);
                    return java.security.KeyFactory.getInstance("EC")
                            .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
                }

                /** DER ECDSA signature -> JWS r||s (64 bytes, RFC 7518 §3.4). */
                private static byte[] kofAcmeJwsSig(java.security.Signature sig) throws Exception {
                    byte[] der = sig.sign();
                    byte[] r = new byte[32];
                    byte[] s2 = new byte[32];
                    int i = 2; // 0x30 len
                    int rlen = der[i + 1] & 0xff;
                    byte[] rb = java.util.Arrays.copyOfRange(der, i + 2, i + 2 + rlen);
                    System.arraycopy(rb, rb.length - Math.min(rb.length, 32), r, 32 - Math.min(rb.length, 32),
                            Math.min(rb.length, 32));
                    i = i + 2 + rlen + 1;
                    int slen = der[i] & 0xff;
                    byte[] sb = java.util.Arrays.copyOfRange(der, i + 1, i + 1 + slen);
                    System.arraycopy(sb, sb.length - Math.min(sb.length, 32), s2, 32 - Math.min(sb.length, 32),
                            Math.min(sb.length, 32));
                    byte[] out = new byte[64];
                    System.arraycopy(r, 0, out, 0, 32);
                    System.arraycopy(s2, 0, out, 32, 32);
                    return out;
                }

                /**
                 * TLS hardening slice 4 (increment 1): signs ONE RFC 7515 JWS
                 * with the operator's EC P-256 key:
                 * b64url(header).b64url(payload).b64url(ES256(r||s)).
                 * Key storage is DELEGATED to the operator (the PEM arrives from
                 * their secret store; Kof never persists it).
                 */
                public static String kof_acme_jws_es256(String keyPem, String headerJson,
                        String payloadJson) throws Exception {
                    if (keyPem == null || headerJson == null || payloadJson == null) {
                        throw new RuntimeException("WEB008: acme.jwsEs256(keyPem, header, payload) — "
                                + "all three arguments are required");
                    }
                    try {
                        java.security.PrivateKey key = kofAcmeKey(keyPem);
                        String signingInput = new String(kofAcmeB64url(
                                headerJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                java.nio.charset.StandardCharsets.US_ASCII)
                                + "."
                                + new String(kofAcmeB64url(
                                        payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                        java.nio.charset.StandardCharsets.US_ASCII);
                        java.security.Signature sig =
                                java.security.Signature.getInstance("SHA256withECDSA");
                        sig.initSign(key);
                        sig.update(signingInput.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        String jwsSig = new String(kofAcmeB64url(kofAcmeJwsSig(sig)),
                                java.nio.charset.StandardCharsets.US_ASCII);
                        return signingInput + "." + jwsSig;
                    } catch (java.security.InvalidKeyException e) {
                        throw new RuntimeException("WEB008: the account key must be an EC P-256 "
                                + "PKCS#8 PEM: " + e.getMessage(), e);
                    }
                }

                // ── TLS hardening slice 4 (increment 2): the ACME ACCOUNT layer ──

                /**
                 * TLS hardening slice 4 (increment 2): the ACME ACCOUNT layer.
                 * GET directory → newNonce (Replay-Nonce capture) → newAccount
                 * JWS with the EMBEDDED JWK (P-256 crv/kty/x/y) + the RFC 7638
                 * thumbprint → returns the account kid (Location header). The
                 * account key stays DELEGATED to the operator (Kof never
                 * persists it).
                 */
                public static String kof_acme_new_account(String directoryUrl, String accountKeyPem,
                        String contact) throws Exception {
                    if (directoryUrl == null || accountKeyPem == null) {
                        throw new RuntimeException("WEB008: acme.newAccount(directoryUrl, accountKeyPem, "
                                + "contact) — the directory and the key are required");
                    }
                    java.security.PrivateKey priv = kofAcmeKey(accountKeyPem);
                    java.security.interfaces.ECPublicKey pub = kofAcmePublicFromPriv(priv);
                    String x = new String(kofAcmeB64url(
                            kofAcmeLeftTrim(pub.getW().getAffineX().toByteArray(), 32)),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    String y = new String(kofAcmeB64url(
                            kofAcmeLeftTrim(pub.getW().getAffineY().toByteArray(), 32)),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    String q = q();
                    String jwk = "{" + q + "crv" + q + ":" + q + "P-256" + q + "," + q + "kty" + q
                            + ":" + q + "EC" + q + "," + q + "x" + q + ":" + q + x + q + ","
                            + q + "y" + q + ":" + q + y + q + "}";
                    // RFC 7638: SHA-256 of the canonical {"crv","kty","x","y"} JSON.
                    String canonical = jwk; // RFC 7638: exatamente estes 4 membros, nesta ordem
                    String thumbprint = new String(kofAcmeB64url(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    // 1) directory
                    String dirJson = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(directoryUrl)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String nonceUrl = kofAcmeJsonString(dirJson, "newNonce");
                    String accountUrl = kofAcmeJsonString(dirJson, "newAccount");
                    if (nonceUrl == null || accountUrl == null) {
                        throw new RuntimeException("WEB008: the ACME directory lacks newNonce/newAccount: "
                                + dirJson);
                    }
                    // 2) newNonce — capture the Replay-Nonce
                    java.net.http.HttpResponse<Void> nonceResp = client.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(nonceUrl))
                                    .header("User-Agent", "kof-acme")
                                    .GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
                    String nonce = nonceResp.headers().firstValue("Replay-Nonce").orElse(null);
                    if (nonce == null) {
                        throw new RuntimeException("WEB008: the ACME newNonce response carries no "
                                + "Replay-Nonce");
                    }
                    // 3) newAccount JWS (jwk embedded; ToS agreed; contact)
                    String protectedJson = "{" + q + "alg" + q + ":" + q + "ES256" + q + ","
                            + q + "jwk" + q + ":" + jwk + "," + q + "nonce" + q + ":" + q + nonce
                            + q + "," + q + "url" + q + ":" + q + accountUrl + q + "}";
                    String payloadJson = "{" + q + "termsOfServiceAgreed" + q + ":true,"
                            + q + "contact" + q + ":[" + q + contact + q + "]}";
                    String jws = kof_acme_jws_es256(accountKeyPem, protectedJson, payloadJson);
                    java.net.http.HttpResponse<Void> created = client.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(accountUrl))
                                    .header("Content-Type", "application/jose+json")
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jws))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
                    if (created.statusCode() != 201) {
                        throw new RuntimeException("WEB008: the ACME newAccount returned HTTP "
                                + created.statusCode() + " (expected 201)");
                    }
                    String kid = created.headers().firstValue("Location").orElse(null);
                    if (kid == null) {
                        throw new RuntimeException("WEB008: the ACME newAccount 201 carries no "
                                + "Location (kid)");
                    }
                    return kid;
                }

                /**
                 * A pública EC derivada da privada por double-and-add na curva
                 * (o PKCS#8 do keytool NÃO carrega o ponto embutido; o JDK não
                 * expõe derivação pública '—' a aritmética é curta e explícita).
                 */
                private static java.security.interfaces.ECPublicKey kofAcmePublicFromPriv(
                        java.security.PrivateKey priv) throws Exception {
                    java.security.interfaces.ECPrivateKey ec =
                            (java.security.interfaces.ECPrivateKey) priv;
                    java.security.spec.ECParameterSpec spec = ec.getParams();
                    java.math.BigInteger p =
                            ((java.security.spec.ECFieldFp) spec.getCurve().getField()).getP();
                    java.math.BigInteger a = spec.getCurve().getA();
                    java.security.spec.ECPoint g = spec.getGenerator();
                    java.math.BigInteger gx = g.getAffineX();
                    java.math.BigInteger gy = g.getAffineY();
                    java.math.BigInteger px = null;
                    java.math.BigInteger py = null;
                    java.math.BigInteger s = ec.getS();
                    for (int i = s.bitLength() - 1; i >= 0; i--) {
                        if (px != null) {
                            java.math.BigInteger lambda = px.pow(2).multiply(java.math.BigInteger.valueOf(3))
                                    .add(a).multiply(py.shiftLeft(1).modInverse(p)).mod(p);
                            java.math.BigInteger nx = lambda.pow(2).subtract(px.shiftLeft(1)).mod(p);
                            java.math.BigInteger ny = lambda.multiply(px.subtract(nx)).subtract(py).mod(p);
                            px = nx.mod(p);
                            py = ny.mod(p);
                        }
                        if (s.testBit(i)) {
                            if (px == null) {
                                px = gx;
                                py = gy;
                            } else {
                                java.math.BigInteger lambda = gy.subtract(py)
                                        .multiply(gx.subtract(px).modInverse(p)).mod(p);
                                java.math.BigInteger nx = lambda.pow(2).subtract(px).subtract(gx).mod(p);
                                java.math.BigInteger ny = lambda.multiply(px.subtract(nx)).subtract(py).mod(p);
                                px = nx.mod(p);
                                py = ny.mod(p);
                            }
                        }
                    }
                    if (px == null) throw new RuntimeException("WEB008: zero scalar");
                    java.security.PublicKey pk = java.security.KeyFactory.getInstance("EC")
                            .generatePublic(new java.security.spec.ECPublicKeySpec(
                                    new java.security.spec.ECPoint(px, py), spec));
                    return (java.security.interfaces.ECPublicKey) pk;
                }

                private static byte[] kofAcmeLeftTrim(byte[] v, int size) {
                    byte[] out = new byte[size];
                    if (v.length > size) {
                        System.arraycopy(v, v.length - size, out, 0, size);
                    } else {
                        System.arraycopy(v, 0, out, size - v.length, v.length);
                    }
                    return out;
                }

                /** O valor de uma chave String num JSON plano (sem parser — os
                 *  shapes do directory são simples e conhecidos). */
                /** Quote como char-code: o text block do template não carrega
                 *  NENHUM backslash (os escapes de \" do template chegam
                 *  quebrados ao helper gerado). */
                private static String q() { return String.valueOf((char) 34); }

                // ── TLS hardening slice 4 (increment 3): the ACME ORDER layer ──
                // All three POST as JWS with the KID in the protected header
                // (the account exists after increment 2), re-fetching a fresh
                // nonce per POST.

                /** A String num JSON plano, por indexOf (sem regex/escapes). */
                private static String kofAcmeJsonString(String json, String key) {
                    StringBuilder nb = new StringBuilder().append((char) 34).append(key)
                            .append((char) 34).append(':').append((char) 34);
                    String needle = nb.toString();
                    int k = json.indexOf(needle);
                    if (k < 0) return null;
                    int from = k + needle.length();
                    int to = json.indexOf(String.valueOf((char) 34), from);
                    return to < 0 ? null : json.substring(from, to);
                }

                /** Um array de Strings num JSON plano (o primeiro array da chave). */
                private static String kofAcmeFirstArrayItem(String json, String key) {
                    StringBuilder kb = new StringBuilder().append((char) 34).append(key)
                            .append((char) 34).append(':').append('[').append((char) 34);
                    String needle = kb.toString();
                    int k = json.indexOf(needle);
                    if (k < 0) return null;
                    int from = k + needle.length();
                    int to = json.indexOf(String.valueOf((char) 34), from);
                    return to < 0 ? null : json.substring(from, to);
                }

                // ── TLS hardening slice 4 (increment 3): the ACME ORDER layer ──
                // All three POST as JWS with the KID in the protected header
                // (the account exists after increment 2), re-fetching a fresh
                // nonce per POST.

                private record AcmePost(int status, String body, String location) {}

                private static AcmePost kofAcmePost(String kid, String accountKeyPem, String nonceUrl,
                        String url, String payloadJson) throws Exception {
                    java.security.PrivateKey priv = kofAcmeKey(accountKeyPem);
                    java.security.interfaces.ECPublicKey pub = kofAcmePublicFromPriv(priv);
                    String x = new String(kofAcmeB64url(
                            kofAcmeLeftTrim(pub.getW().getAffineX().toByteArray(), 32)),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    String y = new String(kofAcmeB64url(
                            kofAcmeLeftTrim(pub.getW().getAffineY().toByteArray(), 32)),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    String jwk = "{" + q() + "crv" + q() + ":" + q() + "P-256" + q() + "," + q()
                            + "kty" + q() + ":" + q() + "EC" + q() + "," + q() + "x" + q() + ":"
                            + q() + x + q() + "," + q() + "y" + q() + ":" + q() + y + q() + "}";
                    String thumbprint = new String(kofAcmeB64url(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(jwk.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpResponse<Void> nonceResp = client.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(nonceUrl))
                                    .header("User-Agent", "kof-acme")
                                    .method("GET", java.net.http.HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
                    String nonce = nonceResp.headers().firstValue("Replay-Nonce").orElse(null);
                    if (nonce == null) {
                        throw new RuntimeException("WEB008: no Replay-Nonce from " + url);
                    }
                    String q2 = q();
                    String protectedJson = "{" + q2 + "alg" + q2 + ":" + q2 + "ES256" + q2 + ","
                            + q2 + "kid" + q2 + ":" + q2 + kid + q2 + "," + q2 + "nonce" + q2 + ":"
                            + q2 + nonce + q2 + "," + q2 + "url" + q2 + ":" + q2 + url + q2 + "}";
                    String jws = kof_acme_jws_es256(accountKeyPem, protectedJson, payloadJson);
                    java.net.http.HttpResponse<byte[]> resp = client.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                                    .header("Content-Type", "application/jose+json")
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jws))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                    String body = new String(resp.body(), java.nio.charset.StandardCharsets.UTF_8);
                    return new AcmePost(resp.statusCode(), body,
                            resp.headers().firstValue("Location").orElse(null));
                }

                /**
                 * TLS hardening slice 4 (increment 3): POST newOrder com o
                 * identificador DNS; devolve a URL do order.
                 */
                public static String kof_acme_new_order(String kid, String accountKeyPem,
                        String directoryUrl, String domain) throws Exception {
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    String dirJson = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(directoryUrl)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String orderUrl = kofAcmeJsonString(dirJson, "newOrder");
                    if (orderUrl == null) {
                        throw new RuntimeException("WEB008: the ACME directory lacks newOrder");
                    }
                    String payload = "{" + q() + "identifiers" + q() + ":[{" + q() + "type" + q()
                            + ":" + q() + "dns" + q() + "," + q() + "value" + q() + ":"
                            + q() + domain + q() + "}]}";
                    String nonceUrl = kofAcmeJsonString(dirJson, "newNonce");
                    if (nonceUrl == null) {
                        throw new RuntimeException("WEB008: the ACME directory lacks newNonce");
                    }
                    AcmePost post = kofAcmePost(kid, accountKeyPem, nonceUrl, orderUrl, payload);
                    if (post.status() != 201) {
                        throw new RuntimeException("WEB008: the ACME newOrder returned HTTP "
                                + post.status() + ": " + post.body());
                    }
                    return post.location();
                }

                /**
                 * TLS hardening slice 4 (increment 3): o valor TXT DNS-01:
                 * base64url(SHA-256(token || "." || thumbprint)). O OPERADOR
                 * publica o registro; o Kof nunca toca no DNS (non-goal do plano).
                 */
                public static String kof_acme_dns01_txt(String kid, String accountKeyPem,
                        String directoryUrl, String domain) throws Exception {
                    String orderUrl = kof_acme_new_order(kid, accountKeyPem, directoryUrl, domain);
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    String orderJson = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(orderUrl)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String authzUrl = kofAcmeFirstArrayItem(orderJson, "authorizations");
                    if (authzUrl == null) {
                        throw new RuntimeException("WEB008: the ACME order carries no authorizations: "
                                + orderJson);
                    }
                    String authzJson = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(authzUrl)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String token = kofAcmeJsonString(authzJson, "token");
                    if (token == null) {
                        throw new RuntimeException("WEB008: the authorization carries no token: "
                                + authzJson);
                    }
                    java.security.PrivateKey priv = kofAcmeKey(accountKeyPem);
                    java.security.interfaces.ECPublicKey pub = kofAcmePublicFromPriv(priv);
                    String x = new String(kofAcmeB64url(
                            kofAcmeLeftTrim(pub.getW().getAffineX().toByteArray(), 32)),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    String y = new String(kofAcmeB64url(
                            kofAcmeLeftTrim(pub.getW().getAffineY().toByteArray(), 32)),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    String jwk = "{" + q() + "crv" + q() + ":" + q() + "P-256" + q() + "," + q()
                            + "kty" + q() + ":" + q() + "EC" + q() + "," + q() + "x" + q() + ":"
                            + q() + x + q() + "," + q() + "y" + q() + ":" + q() + y + q() + "}";
                    String thumbprint = new String(kofAcmeB64url(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(jwk.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    String keyauth = token + "." + thumbprint;
                    return new String(kofAcmeB64url(java.security.MessageDigest
                            .getInstance("SHA-256")
                            .digest(keyauth.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                            java.nio.charset.StandardCharsets.US_ASCII);
                }

                /** Um array de Strings num JSON plano (o primeiro item da chave). */

                /**
                 * TLS hardening slice 4 (increment 3): dispara o desafio DNS-01
                 * (POST vazio ao challenge URL); devolve a URL da autorização.
                 */
                public static String kof_acme_challenge(String kid, String accountKeyPem,
                        String directoryUrl, String domain) throws Exception {
                    String orderUrl = kof_acme_new_order(kid, accountKeyPem, directoryUrl, domain);
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    String orderJson = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(orderUrl)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String authzUrl = kofAcmeFirstArrayItem(orderJson, "authorizations");
                    if (authzUrl == null) {
                        throw new RuntimeException("WEB008: the ACME order carries no authorizations");
                    }
                    String authzJson = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(authzUrl)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String challengeUrl = kofAcmeJsonString(authzJson, "url");
                    if (challengeUrl == null) {
                        throw new RuntimeException("WEB008: the authorization carries no challenge "
                                + "URL: " + authzJson);
                    }
                    // o challenge POST precisa do nonce: busca no directory do
                    // order flow (a mesma raiz do authz URL).
                    String nonceUrl = directoryUrl.replaceAll("/[^/]*$", "/nonce");
                    AcmePost post = kofAcmePost(kid, accountKeyPem, nonceUrl, challengeUrl,
                            "{" + q() + "keyAuthorization" + q() + ":" + q() + "stub" + q() + "}");
                    if (post.status() >= 400) {
                        throw new RuntimeException("WEB008: the ACME challenge POST returned HTTP "
                                + post.status() + ": " + post.body());
                    }
                    return authzUrl;
                }
        """;
    }
}
