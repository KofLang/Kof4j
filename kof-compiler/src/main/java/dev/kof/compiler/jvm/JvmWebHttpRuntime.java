package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.http (JDK client) + kof.mq (pub/sub + filas) - parte 2/2 de JvmWebRuntime. Concatenacao preserva byte-a-byte.
 */
public final class JvmWebHttpRuntime {

    private JvmWebHttpRuntime() {}

    static String source() {
        return """
                // ── kof.http — HTTP client (JDK java.net.http) ───────────
                private static final java.util.concurrent.atomic.AtomicInteger KOF_HTTP_TIMEOUT =
                        new java.util.concurrent.atomic.AtomicInteger(15);
                private static final java.util.concurrent.atomic.AtomicInteger KOF_HTTP_RETRIES =
                        new java.util.concurrent.atomic.AtomicInteger(0);
                private static final java.util.concurrent.atomic.AtomicInteger KOF_HTTP_CIRCUIT_TRIPS =
                        new java.util.concurrent.atomic.AtomicInteger(0);
                private static final java.util.concurrent.atomic.AtomicInteger KOF_HTTP_CIRCUIT_FAILURES =
                        new java.util.concurrent.atomic.AtomicInteger(0);
                private static volatile long KOF_HTTP_CIRCUIT_OPEN_UNTIL = 0L;
                private static final long KOF_HTTP_CIRCUIT_WINDOW_MS = 30_000L;

                public static void kof_http_timeout_set(int seconds) {
                    KOF_HTTP_TIMEOUT.set(seconds);
                }

                public static void kof_http_retry_set(int n) {
                    KOF_HTTP_RETRIES.set(Math.max(0, n));
                }

                public static void kof_http_circuit_set(int trips) {
                    KOF_HTTP_CIRCUIT_TRIPS.set(Math.max(0, trips));
                    if (trips <= 0) {
                        KOF_HTTP_CIRCUIT_FAILURES.set(0);
                        KOF_HTTP_CIRCUIT_OPEN_UNTIL = 0L;
                    }
                }

                private static boolean kof_http_circuit_open() {
                    long openUntil = KOF_HTTP_CIRCUIT_OPEN_UNTIL;
                    if (openUntil == 0L) return false;
                    if (System.currentTimeMillis() >= openUntil) {
                        KOF_HTTP_CIRCUIT_OPEN_UNTIL = 0L;
                        return false;
                    }
                    return true;
                }

                private static void kof_http_circuit_record_failure() {
                    int trips = KOF_HTTP_CIRCUIT_TRIPS.get();
                    if (trips <= 0) return;
                    if (KOF_HTTP_CIRCUIT_FAILURES.incrementAndGet() >= trips) {
                        KOF_HTTP_CIRCUIT_OPEN_UNTIL = System.currentTimeMillis() + KOF_HTTP_CIRCUIT_WINDOW_MS;
                    }
                }

                private static void kof_http_circuit_record_success() {
                    KOF_HTTP_CIRCUIT_FAILURES.set(0);
                    KOF_HTTP_CIRCUIT_OPEN_UNTIL = 0L;
                }

                public static String kof_http_get(String url) throws Exception {
                    return kof_http_request(url, "GET", null, null);
                }

                public static String kof_http_get_headers(String url, String headers) throws Exception {
                    return kof_http_request(url, "GET", headers, null);
                }

                public static String kof_http_delete(String url) throws Exception {
                    return kof_http_request(url, "DELETE", null, null);
                }

                public static String kof_http_delete_headers(String url, String headers) throws Exception {
                    return kof_http_request(url, "DELETE", headers, null);
                }

                public static String kof_http_options(String url) throws Exception {
                    return kof_http_request(url, "OPTIONS", null, null);
                }

                public static String kof_http_options_headers(String url, String headers) throws Exception {
                    return kof_http_request(url, "OPTIONS", headers, null);
                }

                public static String kof_http_post(String url, String body) throws Exception {
                    return kof_http_request(url, "POST", null, body);
                }

                public static String kof_http_post_headers(String url, String body, String headers) throws Exception {
                    return kof_http_request(url, "POST", headers, body);
                }

                public static String kof_http_put(String url, String body) throws Exception {
                    return kof_http_request(url, "PUT", null, body);
                }

                public static String kof_http_put_headers(String url, String body, String headers) throws Exception {
                    return kof_http_request(url, "PUT", headers, body);
                }

                public static String kof_http_patch(String url, String body) throws Exception {
                    return kof_http_request(url, "PATCH", null, body);
                }

                public static String kof_http_patch_headers(String url, String body, String headers) throws Exception {
                    return kof_http_request(url, "PATCH", headers, body);
                }

                private static final java.net.http.HttpClient KOF_HTTP_CLIENT_INSECURE;
                static {
                    java.net.http.HttpClient tmp = null;
                    try {
                        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                        sc.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        }}, new java.security.SecureRandom());
                        javax.net.ssl.SSLParameters sslParams = new javax.net.ssl.SSLParameters();
                        sslParams.setEndpointIdentificationAlgorithm("");
                        tmp = java.net.http.HttpClient.newBuilder()
                                .sslContext(sc)
                                .sslParameters(sslParams)
                                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                                .connectTimeout(java.time.Duration.ofSeconds(15))
                                .build();
                    } catch (Exception e) { tmp = java.net.http.HttpClient.newHttpClient(); }
                    KOF_HTTP_CLIENT_INSECURE = tmp;
                }

                /**
                 * TLS hardening slice 3 — the trust-all opt-in
                 * ({@code http.insecure()}): switches {@code kof.http} https://
                 * requests back to the TRUST-ALL client (test/dev only) and
                 * warns on stderr. The DEFAULT is strict: chain against the
                 * platform trust store WITH hostname verification
                 * ({@code HttpClient.newHttpClient()} already does both).
                 */
                private static final java.util.concurrent.atomic.AtomicBoolean
                        KOF_HTTP_INSECURE = new java.util.concurrent.atomic.AtomicBoolean(false);

                public static void kof_http_insecure_set() {
                    KOF_HTTP_INSECURE.set(true);
                    System.err.println("TLS-TRUST-ALL: kof.http https:// requests will accept "
                            + "self-signed and hostname-mismatched certificates "
                            + "(test/dev only — never production)");
                }

                private static java.net.http.HttpClient kofHttpClientFor(String url) {
                    boolean isHttps = url != null && url.toLowerCase().startsWith("https://");
                    if (!isHttps) return java.net.http.HttpClient.newHttpClient();
                    return KOF_HTTP_INSECURE.get() ? KOF_HTTP_CLIENT_INSECURE
                            : java.net.http.HttpClient.newHttpClient();
                }

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

                public static int kof_http_status(String url) throws Exception {
                    try {
                        java.net.http.HttpClient client = kofHttpClientFor(url);
                        java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create(url))
                                .timeout(java.time.Duration.ofSeconds(KOF_HTTP_TIMEOUT.get()))
                                .method("GET", java.net.http.HttpRequest.BodyPublishers.noBody());
                        java.net.http.HttpResponse<String> r = client
                                .send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                        return r.statusCode();
                    } catch (Exception e) {
                        throw kofHttpFailure(url, e);
                    }
                }

                // #756 (D-MAINT-BATCH-0510/HTTP1): `kof.http` failures must be
                // catchable by `catch (String e)` (the frozen "Kof throws
                // Strings" contract). The generated catch for a String type
                // registers `java/lang/RuntimeException` and reads
                // getMessage(); a raw ConnectException/IOException is not a
                // RuntimeException, so it escaped the catch. Wrapping keeps the
                // original message (and the cause for JVM stack traces) while
                // making the failure a Kof String on the JVM, matching Native
                // (`kof_throw_string`) and JS.
                private static RuntimeException kofHttpFailure(String url, Exception cause) {
                    String msg = cause == null ? null : cause.getMessage();
                    if (msg == null || msg.isBlank()) {
                        msg = cause == null
                                ? ("request failed: " + url)
                                : (cause.getClass().getSimpleName() + " from " + url);
                    }
                    // TLS hardening slice 3: a STRICT https rejection is a NAMED
                    // diagnostic (catchable as a Kof String, never silent).
                    if (cause instanceof javax.net.ssl.SSLHandshakeException
                            || cause instanceof javax.net.ssl.SSLPeerUnverifiedException) {
                        return new RuntimeException("TLS-VERIFY: certificate rejected for " + url
                                + " — " + msg
                                + " (use http.insecure() to opt into trust-all for test/dev)", cause);
                    }
                    return new RuntimeException(msg, cause);
                }

                private static String kof_http_request(String url, String method, String headers, String body)
                        throws Exception {
                    if (kof_http_circuit_open()) {
                        throw kofHttpFailure(url,
                                new java.io.IOException("kof.http circuit open (fail fast): " + url));
                    }
                    Exception last = null;
                    int attempts = KOF_HTTP_RETRIES.get() + 1;
                    for (int attempt = 0; attempt < attempts; attempt++) {
                        try {
                            java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(url))
                                    .timeout(java.time.Duration.ofSeconds(KOF_HTTP_TIMEOUT.get()));
                            if (headers != null && !headers.isBlank()) {
                                for (String line : headers.split("\\n")) {
                                    int c = line.indexOf(':');
                                    if (c > 0) {
                                        b.header(line.substring(0, c).trim(), line.substring(c + 1).trim());
                                    }
                                }
                            }
                            if (body != null) {
                                b.method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body));
                            } else {
                                b.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
                            }
                            java.net.http.HttpClient client2 = kofHttpClientFor(url);
                            java.net.http.HttpResponse<String> r = client2
                                    .send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                            if (r.statusCode() >= 500) {
                                last = new java.io.IOException("HTTP " + r.statusCode() + " from " + url);
                                kof_http_circuit_record_failure();
                                continue;
                            }
                            kof_http_circuit_record_success();
                            return r.body();
                        } catch (Exception e) {
                            last = e;
                            kof_http_circuit_record_failure();
                        }
                    }
                    if (last == null) last = new java.io.IOException("request failed: " + url);
                    throw kofHttpFailure(url, last);
                }

                // ── kof.mq — messageria em memória (pub/sub + filas) ─────
                private static final java.util.concurrent.ConcurrentHashMap<String,
                        java.util.concurrent.CopyOnWriteArrayList<Object>> KOF_MQ_SUBS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_MQ_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();
                private static final java.util.concurrent.ConcurrentHashMap<String,
                        java.util.concurrent.ArrayBlockingQueue<Object>> KOF_MQ_QUEUES =
                        new java.util.concurrent.ConcurrentHashMap<>();

                public static void kof_mq_publish(String topic, Object msg) {
                    java.util.List<Object> subs = KOF_MQ_SUBS.get(topic);
                    if (subs != null) {
                        for (Object fn : subs) {
                            try {
                                fn.getClass().getMethod("invoke", Object.class).invoke(fn, msg);
                            } catch (java.lang.reflect.InvocationTargetException e) {
                                if (e.getCause() instanceof RuntimeException re) throw re;
                                throw new RuntimeException(e.getCause());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }

                public static void kof_mq_subscribe(String topic, Object fn) {
                    KOF_MQ_SUBS.computeIfAbsent(topic, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                            .add(fn);
                }

                public static void kof_mq_unsubscribe(String topic, Object fn) {
                    java.util.List<Object> subs = KOF_MQ_SUBS.get(topic);
                    if (subs != null) {
                        subs.remove(fn);
                    }
                }

                public static String kof_mq_queue() {
                    return "mq-" + KOF_MQ_SEQ.incrementAndGet();
                }

                public static void kof_mq_push(String q, Object item) {
                    KOF_MQ_QUEUES.computeIfAbsent(q,
                            k -> new java.util.concurrent.ArrayBlockingQueue<>(1024)).add(item);
                }

                public static Object kof_mq_pop(String q) {
                    java.util.concurrent.ArrayBlockingQueue<Object> queue = KOF_MQ_QUEUES.get(q);
                    return queue == null ? null : queue.poll();
                }

                public static int kof_mq_queue_size(String q) {
                    java.util.concurrent.ArrayBlockingQueue<Object> queue = KOF_MQ_QUEUES.get(q);
                    return queue == null ? 0 : queue.size();
                }

""";
    }
}
