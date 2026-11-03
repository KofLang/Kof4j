package dev.kof.compiler;

import java.security.SecureRandom;

/**
 * The trust-all client factory shared by the TLS test batteries
 * ({@code KofWebTlsTest}, {@code KofWebTlsVerifyTest}): the test/dev client
 * that accepts ANY certificate with NO hostname verification — the same
 * behavior the slice-3 {@code http.insecure()} opt-in buys at runtime. Test
 * harness only; never a production surface.
 */
final class TlsTrustAllSupport {

    private TlsTrustAllSupport() {}

    static javax.net.ssl.SSLSocketFactory insecureFactory() {
        try {
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
            }}, new SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
