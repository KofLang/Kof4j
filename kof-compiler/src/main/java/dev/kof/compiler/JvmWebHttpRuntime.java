package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.http (JDK client) + kof.mq (pub/sub + filas) - parte 2/2 de JvmWebRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmWebHttpRuntime {

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

                public static int kof_http_status(String url) throws Exception {
                    boolean isHttps = url != null && url.toLowerCase().startsWith("https://");
                    java.net.http.HttpClient client = isHttps ? KOF_HTTP_CLIENT_INSECURE : java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(url))
                            .timeout(java.time.Duration.ofSeconds(KOF_HTTP_TIMEOUT.get()))
                            .method("GET", java.net.http.HttpRequest.BodyPublishers.noBody());
                    java.net.http.HttpResponse<String> r = client
                            .send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    return r.statusCode();
                }

                private static String kof_http_request(String url, String method, String headers, String body)
                        throws Exception {
                    if (kof_http_circuit_open()) {
                        throw new java.io.IOException("kof.http circuit open (fail fast): " + url);
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
                            boolean isHttps2 = url != null && url.toLowerCase().startsWith("https://");
                            java.net.http.HttpClient client2 = isHttps2 ? KOF_HTTP_CLIENT_INSECURE : java.net.http.HttpClient.newHttpClient();
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
                    throw last;
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
