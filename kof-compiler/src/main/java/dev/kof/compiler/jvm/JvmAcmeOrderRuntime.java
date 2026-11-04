package dev.kof.compiler.jvm;

/** Runtime ACME — a camada ORDER (fatia 4 incremento 3) do plano
{@code docs/development/tls-hardening-plan.md}: a cache de ordens por
(kid,domain), o POST-as-JWS com retry de badNonce, newOrder, dns01Txt e
o gatilho do desafio (payload RFC "{}" — o servidor deriva o
keyAuthorization). Concatenado DEPOIS de {@code JvmAcmeRuntime}. */
public final class JvmAcmeOrderRuntime {

    private JvmAcmeOrderRuntime() {}

    static String source() {
        return """

                // ── TLS hardening slice 4 (increment 3): the ACME ORDER layer ──
                // All three POST as JWS with the KID in the protected header
                // (the account exists after increment 2), re-fetching a fresh
                // nonce per POST.

                /** A String num JSON plano, por indexOf (sem regex/escapes). */
                private static String kofAcmeJsonString(String json, String key) {
                    // String-JSON lookup tolerante a pretty-printing (o Pebble
                    // emite quebras e espaços entre a chave e o valor).
                    String kq = String.valueOf((char) 34);
                    String needle = kq + key + kq;
                    int k = json.indexOf(needle);
                    if (k < 0) return null;
                    int i = k + needle.length();
                    while (i < json.length()) {
                        char c = json.charAt(i);
                        if (c == ':') {
                            i++;
                            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
                            if (i < json.length() && json.charAt(i) == kq.charAt(0)) i++;
                            int to = json.indexOf(kq, i);
                            return to < 0 ? null : json.substring(i, to);
                        }
                        if (!Character.isWhitespace(c)) return null;
                        i++;
                    }
                    return null;
                }

                /** A memória da ÚLTIMA ordem por (kid,domain): o fluxo kof é
                 *  stateless por processo, mas a MESMA ordem precisa atravessar
                 *  dns01Txt '→' challenge '→' finalize (o token é da ordem; uma
                 *  ordem nova invalida o TXT já publicado pelo operador). */
                private static final java.util.Map<String, String> KOF_ACME_ORDER_BY_DOMAIN =
                        new java.util.concurrent.ConcurrentHashMap<>();

                /** O campo url/token do desafio DNS-01 do authz (o authz lista
                 *  vários desafios; o DNS-01 é o que importa ao plano). */
                private static String kofAcmeDns01Field(String authzJson, String field) {
                    // acha o segmento do desafio dns-01: divide por "type" e
                    // toma o bloco que o contém; o url/token/status seguem no
                    // MESMO objeto desafio (antes do próximo desafio do array).
                    String kq = String.valueOf((char) 34);
                    int dnsIdx = authzJson.indexOf(kq + "dns-01" + kq);
                    if (dnsIdx < 0) return null;
                    String seg = authzJson.substring(dnsIdx);
                    String urlV = null;
                    String tokenV = null;
                    String statusV = null;
                    int urlI = seg.indexOf(kq + "url" + kq);
                    if (urlI >= 0) urlV = jsonValueAt(seg, urlI);
                    int tokI = seg.indexOf(kq + "token" + kq);
                    if (tokI >= 0) tokenV = jsonValueAt(seg, tokI);
                    int stI = seg.indexOf(kq + "status" + kq);
                    if (stI >= 0) statusV = jsonValueAt(seg, stI);
                    if ("url".equals(field)) return urlV;
                    if ("status".equals(field)) return statusV;
                    return tokenV;
                }

                /** O valor String a partir de um "key": no JSON (tolerante a pretty). */
                private static String jsonValueAt(String json, int keyIdx) {
                    String kq = String.valueOf((char) 34);
                    int i = keyIdx;
                    // pula a PRÓPRIA chave (a aspa de abertura já está em keyIdx)
                    while (i < json.length() && json.charAt(i) != ':') i++;
                    while (i < json.length()) {
                        char c = json.charAt(i);
                        if (c == ':') {
                            i++;
                            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
                            if (i < json.length() && json.charAt(i) == kq.charAt(0)) i++;
                            int to = json.indexOf(kq, i);
                            return to < 0 ? null : json.substring(i, to);
                        }
                        if (!Character.isWhitespace(c)) return null;
                        i++;
                    }
                    return null;
                }

                /** O primeiro item String de um array JSON plano (tolerante a
                //  pretty-printing; o Pebble quebra linhas entre a chave e [). */
                private static String kofAcmeFirstArrayItem(String json, String key) {
                    String kq = String.valueOf((char) 34);
                    String needle = kq + key + kq;
                    int k = json.indexOf(needle);
                    if (k < 0) return null;
                    int i = k + needle.length();
                    while (i < json.length()) {
                        char c = json.charAt(i);
                        if (c == ':' || Character.isWhitespace(c)) { i++; continue; }
                        if (c == '[') {
                            i++;
                            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
                            if (i < json.length() && json.charAt(i) == (char) 34) i++;
                            int to = json.indexOf(kq, i);
                            return to < 0 ? null : json.substring(i, to);
                        }
                        if (!Character.isWhitespace(c)) return null;
                        i++;
                    }
                    return null;
                }

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
                    String q2 = q();
                    // O Pebble rejeita ~5% dos nonces bons (a config de teste):
                    // um badNonce = retry com um nonce NOVO (o fluxo RFC 8555 §7.2).
                    for (int attempt = 0; attempt < 4; attempt++) {
                        java.net.http.HttpResponse<Void> nonceResp = client.send(
                                java.net.http.HttpRequest.newBuilder(java.net.URI.create(nonceUrl))
                                        .header("User-Agent", "kof-acme")
                                        .method("GET", java.net.http.HttpRequest.BodyPublishers.noBody())
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.discarding());
                        String nonce = nonceResp.headers().firstValue("Replay-Nonce").orElse(null);
                        if (nonce == null) {
                            throw new RuntimeException("WEB008: no Replay-Nonce from " + nonceUrl);
                        }
                        String protectedJson = "{" + q2 + "alg" + q2 + ":" + q2 + "ES256" + q2 + ","
                                + q2 + "kid" + q2 + ":" + q2 + kid + q2 + "," + q2 + "nonce" + q2 + ":"
                                + q2 + nonce + q2 + "," + q2 + "url" + q2 + ":" + q2 + url + q2 + "}";
                        String jws = kof_acme_jws_es256(accountKeyPem, protectedJson, payloadJson);
                        // flattened JSON serialization (RFC 8555 §7.5): o Pebble NÃO
                        // aceita a string nua p.p.s — exige o objeto {"protected",...}.
                        int d1 = jws.indexOf((char) 46);
                        int d2 = jws.indexOf((char) 46, d1 + 1);
                        String jwsJson = "{" + q2 + "protected" + q2 + ":" + q2
                                + jws.substring(0, d1) + q2 + "," + q2 + "payload" + q2 + ":" + q2
                                + jws.substring(d1 + 1, d2) + q2 + "," + q2 + "signature" + q2 + ":"
                                + q2 + jws.substring(d2 + 1) + q2 + "}";
                        java.net.http.HttpResponse<byte[]> resp = client.send(
                                java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                                        .header("Content-Type", "application/jose+json")
                                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jwsJson))
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                        String body = new String(resp.body(), java.nio.charset.StandardCharsets.UTF_8);
                        if (body.contains("badNonce") && attempt < 3) {
                            continue;
                        }
                        return new AcmePost(resp.statusCode(), body,
                                resp.headers().firstValue("Location").orElse(null));
                    }
                    throw new RuntimeException("WEB008: the ACME POST exhausted the nonce retries");
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
                    KOF_ACME_ORDER_BY_DOMAIN.put(kid + "|" + domain, post.location());
                    return post.location();
                }

                /**
                 * TLS hardening slice 4 (increment 3): o valor TXT DNS-01:
                 * base64url(SHA-256(token || "." || thumbprint)). O OPERADOR
                 * publica o registro; o Kof nunca toca no DNS (non-goal do plano).
                 */
                public static String kof_acme_dns01_txt(String kid, String accountKeyPem,
                        String directoryUrl, String domain) throws Exception {
                    String orderUrl = KOF_ACME_ORDER_BY_DOMAIN.containsKey(kid + "|" + domain)
                            ? KOF_ACME_ORDER_BY_DOMAIN.get(kid + "|" + domain)
                            : kof_acme_new_order(kid, accountKeyPem, directoryUrl, domain);
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
                    String token = kofAcmeDns01Field(authzJson, "token");
                    if (token == null) {
                        throw new RuntimeException("WEB008: the authorization carries no dns-01 "
                                + "token: " + authzJson);
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


                /**
                 * TLS hardening slice 4 (increment 3): dispara o desafio DNS-01
                 * (POST vazio ao challenge URL); devolve a URL da autorização.
                 */
                public static String kof_acme_challenge(String kid, String accountKeyPem,
                        String directoryUrl, String domain) throws Exception {
                    java.net.http.HttpClient dirClient = java.net.http.HttpClient.newHttpClient();
                    String dirJson = dirClient.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(directoryUrl)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String orderUrl = KOF_ACME_ORDER_BY_DOMAIN.containsKey(kid + "|" + domain)
                            ? KOF_ACME_ORDER_BY_DOMAIN.get(kid + "|" + domain)
                            : kof_acme_new_order(kid, accountKeyPem, directoryUrl, domain);
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
                    String challengeUrl = kofAcmeDns01Field(authzJson, "url");
                    if (challengeUrl == null) {
                        throw new RuntimeException("WEB008: the authorization carries no dns-01 "
                                + "challenge URL: " + authzJson);
                    }
                    // RFC 8555 §7.5.1: o gatilho é um POST com o payload "{}" —
                    // o SERVIDOR deriva o keyAuthorization do SEU token (o campo
                    // legacy KeyAuthorization é recusado no strict do Pebble).
                    String nonceUrl = kofAcmeJsonString(dirJson, "newNonce");
                    if (nonceUrl == null) {
                        throw new RuntimeException("WEB008: the ACME directory lacks newNonce");
                    }
                    AcmePost post = kofAcmePost(kid, accountKeyPem, nonceUrl, challengeUrl,
                            "{}");
                    if (post.status() >= 400) {
                        throw new RuntimeException("WEB008: the ACME challenge POST returned HTTP "
                                + post.status() + ": " + post.body());
                    }
                    return authzUrl;
                }

        """;
    }
}
