package dev.kof.compiler;

public final class JvmCacheRuntime {
    private JvmCacheRuntime() {}
    static String source() {
        return """
                // ── kof.cache — in-process TTL cache ────────────────
                private static final java.util.concurrent.ConcurrentHashMap<String, String> KOF_CACHE_DATA =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<String, Long> KOF_CACHE_EXPIRY =
                        new java.util.concurrent.ConcurrentHashMap<>();

                public static String kof_cache_get(String key) {
                    Long exp = KOF_CACHE_EXPIRY.get(key);
                    if (exp != null && exp != 0 && System.currentTimeMillis() > exp) {
                        KOF_CACHE_DATA.remove(key);
                        KOF_CACHE_EXPIRY.remove(key);
                        return null;
                    }
                    return KOF_CACHE_DATA.get(key);
                }

                public static void kof_cache_set(String key, String value) {
                    KOF_CACHE_DATA.put(key, value);
                    KOF_CACHE_EXPIRY.remove(key);
                }

                public static void kof_cache_set_ttl(String key, String value, int ttlSeconds) {
                    KOF_CACHE_DATA.put(key, value);
                    if (ttlSeconds > 0) {
                        KOF_CACHE_EXPIRY.put(key, System.currentTimeMillis() + ttlSeconds * 1000L);
                    } else {
                        KOF_CACHE_EXPIRY.remove(key);
                    }
                }

                public static int kof_cache_ttl(String key) {
                    Long exp = KOF_CACHE_EXPIRY.get(key);
                    if (exp == null || exp == 0) return -1;
                    long remaining = exp - System.currentTimeMillis();
                    if (remaining <= 0) {
                        KOF_CACHE_DATA.remove(key);
                        KOF_CACHE_EXPIRY.remove(key);
                        return -1;
                    }
                    return (int) (remaining / 1000);
                }

                public static void kof_cache_delete(String key) {
                    KOF_CACHE_DATA.remove(key);
                    KOF_CACHE_EXPIRY.remove(key);
                }

                public static void kof_cache_clear() {
                    KOF_CACHE_DATA.clear();
                    KOF_CACHE_EXPIRY.clear();
                }

                """;
    }
}
