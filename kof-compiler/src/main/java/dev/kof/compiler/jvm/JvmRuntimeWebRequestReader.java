package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500: família "leitura
 * de request" extraída de JvmRuntimeWebDispatch no MESMO commit em que o
 * #1012 a empurrou sobre a linha vermelha de 600 — check_500 exige o split
 * no mesmo commit; todos os fragmentos concatenam num ÚNICO corpo de classe,
 * mover entre fragmentos é semântica idêntica).
 *
 * readRequest ×2 + indexOfHeaderEnd: os bytes da conexão viram um WebRequest
 * (header block cru, corpo contado em BYTES pelo Content-Length, query
 * separada do path).
 */
public final class JvmRuntimeWebRequestReader {

    private JvmRuntimeWebRequestReader() {}

    static String source() {
        return """
                private static WebRequest readRequest(java.io.InputStream in) throws java.io.IOException {
                    return readRequest(in, null, false);
                }

                private static WebRequest readRequest(java.io.InputStream in, String remoteAddr,
                        boolean secure) throws java.io.IOException {
                    byte[] buffer = new byte[8192];
                    java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
                    int headerEnd = -1;
                    while (true) {
                        int n = in.read(buffer);
                        if (n == -1) throw new java.io.IOException("connection closed before headers");
                        raw.write(buffer, 0, n);
                        byte[] seen = raw.toByteArray();
                        headerEnd = indexOfHeaderEnd(seen);
                        if (headerEnd >= 0) break;
                        if (raw.size() > 65536) throw new java.io.IOException("headers too large");
                    }

                    byte[] seen = raw.toByteArray();
                    String headerBlock = new String(seen, 0, headerEnd,
                            java.nio.charset.StandardCharsets.UTF_8);
                    int bodyStart = headerEnd + 4;

                    int contentLength = 0;
                    for (String line : headerBlock.split("\\r\\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            try {
                                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    // O corpo é contado em BYTES (Content-Length), não em chars:
                    // um corpo UTF-8 multibyte ("Olá") tem menos chars que bytes
                    // e o loop antigo (body.length() < contentLength) lia além do
                    // fim e travava a conexão até o timeout (blog E2E F12).
                    java.io.ByteArrayOutputStream bodyBuf = new java.io.ByteArrayOutputStream();
                    int already = seen.length - bodyStart;
                    if (already > 0) bodyBuf.write(seen, bodyStart, already);
                    while (bodyBuf.size() < contentLength) {
                        int n = in.read(buffer);
                        if (n == -1) break;
                        bodyBuf.write(buffer, 0, n);
                    }
                    byte[] bodyBytes = bodyBuf.toByteArray();
                    int keep = Math.min(bodyBytes.length, contentLength);
                    String body = new String(bodyBytes, 0, keep,
                            java.nio.charset.StandardCharsets.UTF_8);

                    String[] lines = headerBlock.split("\\r\\n");
                    String[] parts = lines.length > 0 ? lines[0].split(" ") : new String[0];
                    String method = parts.length > 0 ? parts[0] : "GET";
                    String fullPath = parts.length > 1 ? parts[1] : "/";
                    String path = fullPath;
                    String query = "";
                    int q = fullPath.indexOf('?');
                    if (q >= 0) {
                        path = fullPath.substring(0, q);
                        query = fullPath.substring(q + 1);
                    }
                    return new WebRequest(method, path, query, headerBlock, body, remoteAddr, secure);
                }

                private static int indexOfHeaderEnd(byte[] bytes) {
                    for (int i = 0; i + 3 < bytes.length; i++) {
                        if (bytes[i] == '\\r' && bytes[i + 1] == '\\n'
                                && bytes[i + 2] == '\\r' && bytes[i + 3] == '\\n') {
                            return i;
                        }
                    }
                    return -1;
                }
                """;
    }
}
