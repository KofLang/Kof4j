package dev.kof.compiler.jvm;

/**
 * Runtime JVM do tipo {@code kof.http.Response} (#1126, decisão da
 * mantenedora 05/11). Nested class em {@code KofRuntime} (mesmo padrão do
 * {@code InteropError}/{@code Secret}/{@code KeyHandle}). A diferença de
 * contrato para o {@code kof_http_request}: o {@code kof_http_send_response}
 * NUNCA lança em 4xx/5xx — o status é dado que o caller lê — mas falhas de
 * CONEXÃO seguem o contrato catchable-String existente (kofHttpFailure).
 */
public final class JvmHttpResponseRuntime {

    private JvmHttpResponseRuntime() {}

    static String source() {
        return """
                // ── kof.http — Response builtin (#1126, JVM) ──
                public static final class HttpResponse {
                    public final int status;
                    public final String headers;
                    public final String body;

                    public HttpResponse(int status, String headers, String body) {
                        this.status = status;
                        this.headers = headers == null ? "" : headers;
                        this.body = body == null ? "" : body;
                    }
                }

                public static HttpResponse kof_http_send_response(String method, String url,
                                                                  String body, String headers) throws Exception {
                    java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(url))
                            .timeout(java.time.Duration.ofSeconds(KOF_HTTP_TIMEOUT.get()));
                    if (headers != null && !headers.isBlank()) {
                        for (String line : headers.split("\\\\n")) {
                            int i = line.indexOf(':');
                            if (i > 0) b.header(line.substring(0, i).trim(), line.substring(i + 1).trim());
                        }
                    }
                    String m = method == null ? "GET" : method.trim().toUpperCase();
                    java.net.http.HttpRequest.BodyPublisher pub = body == null
                            ? java.net.http.HttpRequest.BodyPublishers.noBody()
                            : java.net.http.HttpRequest.BodyPublishers.ofString(body);
                    switch (m) {
                        case "GET" -> b.GET();
                        case "HEAD" -> b.method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody());
                        case "DELETE" -> b.DELETE();
                        default -> b.method(m, pub);
                    }
                    try {
                        java.net.http.HttpClient client = kofHttpClientFor(url);
                        java.net.http.HttpResponse<String> r = client
                                .send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                        StringBuilder hs = new StringBuilder();
                        r.headers().map().forEach((k, vs) -> {
                            for (String v : vs) hs.append(k).append(": ").append(v).append('\\n');
                        });
                        return new HttpResponse(r.statusCode(), hs.toString(), r.body());
                    } catch (Exception e) {
                        // falha de CONEXÃO segue o contrato catchable-String (#756);
                        // status HTTP nunca é exceção (#1126).
                        throw kofHttpFailure(url, e);
                    }
                }

                public static int kof_http_response_status(HttpResponse r) {
                    return r == null ? 0 : r.status;
                }

                public static String kof_http_response_header(HttpResponse r, String name) {
                    if (r == null || name == null) return "";
                    String want = name.toLowerCase() + ":";
                    for (String line : r.headers.split("\\\\n")) {
                        String low = line.toLowerCase();
                        if (low.startsWith(want)) return line.substring(line.indexOf(':') + 1).trim();
                    }
                    return "";
                }

                public static String kof_http_response_headers(HttpResponse r) {
                    return r == null ? "" : r.headers;
                }

                public static String kof_http_response_body(HttpResponse r) {
                    return r == null ? "" : r.body;
                }
                """;
    }
}
