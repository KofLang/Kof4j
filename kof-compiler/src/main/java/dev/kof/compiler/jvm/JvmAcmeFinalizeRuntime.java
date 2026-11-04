package dev.kof.compiler.jvm;

/** Runtime ACME — a camada FINALIZE (fatia 4 incremento 4) do plano
{@code docs/development/tls-hardening-plan.md}: os escritores DER
mínimos, o CSR PKCS#10 (subject CN + SAN via extensionRequest,
ecdsa-with-SHA256, comprimentos mínimos) e o finalize com o download do
par. Concatenado DEPOIS de {@code JvmAcmeOrderRuntime}. */
public final class JvmAcmeFinalizeRuntime {

    private JvmAcmeFinalizeRuntime() {}

    static String source() {
        return """
                // ── TLS hardening slice 4 (increment 4): the FINALIZE layer ──

                /** DER wrapper (short/long form) para um corpo dado. O comprimento
                 *  longo é MÍNIMO (o BigInteger.toByteArray é SIGNED — 128 sai
                 *  {0x00,0x80} e o parser ASN.1 do Go recusa zero à esquerda). */
                private static byte[] kofAcmeDer(int tag, byte[] body) {
                    int len = body.length;
                    if (len < 128) {
                        byte[] out = new byte[2 + len];
                        out[0] = (byte) tag;
                        out[1] = (byte) len;
                        System.arraycopy(body, 0, out, 2, len);
                        return out;
                    }
                    byte[] lenBytes = java.math.BigInteger.valueOf(len).toByteArray();
                    if (lenBytes.length > 1 && lenBytes[0] == 0) {
                        lenBytes = java.util.Arrays.copyOfRange(lenBytes, 1, lenBytes.length);
                    }
                    byte[] out = new byte[2 + lenBytes.length + len];
                    out[0] = (byte) tag;
                    out[1] = (byte) (0x80 | lenBytes.length);
                    System.arraycopy(lenBytes, 0, out, 2, lenBytes.length);
                    System.arraycopy(body, 0, out, 2 + lenBytes.length, len);
                    return out;
                }

                private static byte[] kofAcmeDerInt(java.math.BigInteger v) {
                    return kofAcmeDer(0x02, v.toByteArray());
                }

                private static byte[] kofAcmeConcat(byte[] a, byte[] b) {
                    byte[] out = new byte[a.length + b.length];
                    System.arraycopy(a, 0, out, 0, a.length);
                    System.arraycopy(b, 0, out, a.length, b.length);
                    return out;
                }

                /** PKCS#10 CSR (RFC 2986) assinado com SHA256withECDSA:
                 *  subject CN=domain + a extensão SAN via extensionRequest
                 *  (RFC 8555: os nomes saem da CSR — o Pebble exige
                 *  parsedCSR.DNSNames == order names); SPKI do par efêmero. */
                private static byte[] kofAcmeCsr(java.security.KeyPair kp, String domain)
                        throws Exception {
                    byte[] cn = domain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    byte[] oidCn = new byte[]{0x06, 0x03, 0x55, 0x04, 0x03};
                    byte[] rdnVal = kofAcmeDer(0x0c, cn);
                    byte[] atv = kofAcmeDer(0x30,
                            kofAcmeDer(0x31, kofAcmeDer(0x30,
                                    kofAcmeConcat(oidCn, rdnVal))));
                    byte[] spki = kp.getPublic().getEncoded();
                    // a extensão SAN DNS: Extension{oid subjectAltName 2.5.29.17,
                    // OCTET STRING(SEQUENCE{[2] dNSName})}
                    byte[] dnsName = kofAcmeDer(0x82, cn);
                    byte[] sanSeq = kofAcmeDer(0x30, dnsName);
                    byte[] extBody = kofAcmeDer(0x30,
                            kofAcmeConcat(new byte[]{0x06, 0x03, 0x55, 0x1d, 0x11},
                                    kofAcmeDer(0x04, sanSeq)));
                    // Attribute{oid extensionRequest 1.2.840.113549.1.9.14,
                    // SET(SEQUENCE(Extension))} — o SET carrega um WRAPPER
                    // SEQUENCE OF Extension (a forma que o Go/OpenSSL geram;
                    // a Extension nua no SET quebra o parseCSRExtensions).
                    byte[] extReq = kofAcmeDer(0x30,
                            kofAcmeConcat(new byte[]{0x06, 0x09, 0x2a, (byte) 0x86, 0x48,
                                    (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x09, 0x0e},
                                    kofAcmeDer(0x31, kofAcmeDer(0x30, extBody))));
                    byte[] cri = kofAcmeDer(0x30, kofAcmeConcat(
                            kofAcmeDerInt(java.math.BigInteger.ZERO),
                            kofAcmeConcat(atv, kofAcmeConcat(spki,
                                    kofAcmeDer(0xA0, extReq)))));
                    java.security.Signature sig =
                            java.security.Signature.getInstance("SHA256withECDSA");
                    sig.initSign(kp.getPrivate());
                    sig.update(cri);
                    byte[] signature = sig.sign();
                    // ecdsa-with-SHA256 1.2.840.10045.4.3.2 (RFC 5758 §3.2:
                    // SEM parâmetros NULL — eles só existem no RSA)
                    byte[] algId = new byte[]{0x30, 0x0a, 0x06, 0x08, 0x2a, (byte) 0x86, 0x48,
                            (byte) 0xce, 0x3d, 0x04, 0x03, 0x02};
                    byte[] bits = kofAcmeDer(0x03,
                            kofAcmeConcat(new byte[]{0x00}, signature));
                    return kofAcmeDer(0x30, kofAcmeConcat(cri, kofAcmeConcat(algId, bits)));
                }

                public static String kof_acme_finalize(String kid, String accountKeyPem,
                        String directoryUrl, String domain) throws Exception {
                    java.security.KeyPairGenerator kpg =
                            java.security.KeyPairGenerator.getInstance("EC");
                    kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
                    java.security.KeyPair certKey = kpg.generateKeyPair();
                    byte[] csr = kofAcmeCsr(certKey, domain);
                    String csrB64 = new String(kofAcmeB64url(csr),
                            java.nio.charset.StandardCharsets.US_ASCII);
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    String dirJson = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(directoryUrl)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String nonceUrl = kofAcmeJsonString(dirJson, "newNonce");
                    String orderUrl = kofAcmeJsonString(dirJson, "newOrder");
                    if (nonceUrl == null || orderUrl == null) {
                        throw new RuntimeException("WEB008: the ACME directory lacks newNonce/newOrder");
                    }
                    // A ordem da CACHÊ (a mesma do dns01Txt/challenge): uma ordem
                    // nova teria outro token e invalidaria o TXT já publicado.
                    String cachedOrder = KOF_ACME_ORDER_BY_DOMAIN.get(kid + "|" + domain);
                    String orderLoc;
                    if (cachedOrder != null) {
                        orderLoc = cachedOrder;
                    } else {
                        AcmePost order = kofAcmePost(kid, accountKeyPem, nonceUrl, orderUrl,
                                "{" + q() + "identifiers" + q() + ":[{" + q() + "type" + q() + ":"
                                + q() + "dns" + q() + "," + q() + "value" + q() + ":" + q() + domain
                                + q() + "}]}");
                        if (order.status() != 201) {
                            throw new RuntimeException("WEB008: the ACME newOrder returned HTTP "
                                    + order.status() + ": " + order.body());
                        }
                        orderLoc = order.location();
                        KOF_ACME_ORDER_BY_DOMAIN.put(kid + "|" + domain, orderLoc);
                    }
                    // O desafio DESTA ordem: o TXT já está publicado (o papel do
                    // operador). O gatilho SÓ é disparado com o desafio ainda
                    // pending — um re-gatilho em processing/valid é recusado
                    // (400) pelo Pebble; depois espera a autorização validar.
                    String orderJson2 = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(orderLoc)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String authzUrl = kofAcmeFirstArrayItem(orderJson2, "authorizations");
                    String authzJson = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(authzUrl)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    String challengeUrl = kofAcmeDns01Field(authzJson, "url");
                    String chalStatus = kofAcmeDns01Field(authzJson, "status");
                    if (chalStatus == null || "pending".equals(chalStatus)) {
                        // RFC 8555 §7.5.1: o payload do gatilho é "{}".
                        AcmePost trigger = kofAcmePost(kid, accountKeyPem, nonceUrl, challengeUrl,
                                "{}");
                        if (trigger.status() >= 400) {
                            throw new RuntimeException("WEB008: the ACME challenge POST returned "
                                    + "HTTP " + trigger.status() + ": " + trigger.body());
                        }
                    }
                    // espera a AUTORIZAÇÃO validar (o desafio DNS-01 do TXT publicado)
                    boolean authzValid = false;
                    for (int i = 0; i < 30 && !authzValid; i++) {
                        Thread.sleep(500);
                        String a2 = client.send(java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(authzUrl)).GET().build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                        authzValid = "valid".equals(kofAcmeJsonString(a2, "status"));
                    }
                    if (!authzValid) {
                        throw new RuntimeException("WEB008: the ACME authorization did not reach "
                                + "valid (is the TXT published?)");
                    }
                    // A URL de finalize vem da PRÓPRIA ordem (o caminho do
                    // Pebble é /finalize-order/<id>, NÃO <order>/finalize).
                    String finalizeUrl = kofAcmeJsonString(orderJson2, "finalize");
                    if (finalizeUrl == null) {
                        throw new RuntimeException("WEB008: the ACME order carries no finalize "
                                + "URL: " + orderJson2);
                    }
                    AcmePost fin = kofAcmePost(kid, accountKeyPem, nonceUrl, finalizeUrl,
                            "{" + q() + "csr" + q() + ":" + q() + csrB64 + q() + "}");
                    if (fin.status() >= 400) {
                        throw new RuntimeException("WEB008: the ACME finalize returned HTTP "
                                + fin.status() + ": " + fin.body());
                    }
                    String certUrl = null;
                    for (int i = 0; i < 30; i++) {
                        String orderJson = client.send(java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(orderLoc)).GET().build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                        String status = kofAcmeJsonString(orderJson, "status");
                        if ("valid".equals(status)) {
                            certUrl = kofAcmeJsonString(orderJson, "certificate");
                            break;
                        }
                        Thread.sleep(200);
                    }
                    if (certUrl == null) {
                        throw new RuntimeException("WEB008: the ACME order did not reach valid");
                    }
                    java.net.http.HttpResponse<byte[]> cert = client.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(certUrl))
                                    .GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                    String certPem = "-----BEGIN CERTIFICATE-----\\n"
                            + java.util.Base64.getMimeEncoder(64, "\\n".getBytes(
                                    java.nio.charset.StandardCharsets.US_ASCII))
                                    .encodeToString(cert.body()) + "\\n"
                            + "-----END CERTIFICATE-----\\n";
                    String keyPemOut = "-----BEGIN PRIVATE KEY-----\\n"
                            + java.util.Base64.getMimeEncoder(64, "\\n".getBytes(
                                    java.nio.charset.StandardCharsets.US_ASCII))
                                    .encodeToString(certKey.getPrivate().getEncoded()) + "\\n"
                            + "-----END PRIVATE KEY-----\\n";
                    return certPem + "\\n" + keyPemOut;
                }
        """;
    }
}
