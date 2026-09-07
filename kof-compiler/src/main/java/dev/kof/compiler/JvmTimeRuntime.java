package dev.kof.compiler;

import java.util.List;

/**
 * Runtime do kof.time (sleep/now/interval) — gerado no KofRuntime junto
 * com o JvmRuntime. Separado num arquivo próprio porque o constant pool
 * do javac limita cada string a 65535 bytes.
 */
public final class JvmTimeRuntime {

    private JvmTimeRuntime() {}

    static String source() {
        return """
                // ── kof.time — sleep, now e scheduler (interval) ─────────
                private static final java.util.concurrent.ConcurrentHashMap<String, Thread> KOF_TIME_JOBS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_TIME_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();

                public static void kof_time_sleep(int ms) {
                    try {
                        Thread.sleep(ms);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                public static long kof_time_now() {
                    return System.currentTimeMillis();
                }

                public static String kof_time_interval(int ms, Object fn) {
                    if (ms <= 0) throw new IllegalArgumentException("interval must be positive: " + ms);
                    String id = "job-" + KOF_TIME_SEQ.incrementAndGet();
                    Thread t = new Thread(() -> {
                        try {
                            java.lang.reflect.Method invoke = fn.getClass().getMethod("invoke");
                            while (KOF_TIME_JOBS.containsKey(id)) {
                                Thread.sleep(ms);
                                if (!KOF_TIME_JOBS.containsKey(id)) break;
                                invoke.invoke(fn);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            if (e.getCause() instanceof RuntimeException re) throw re;
                            throw new RuntimeException(e.getCause());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, "kof-time-" + id);
                    t.setDaemon(true);
                    KOF_TIME_JOBS.put(id, t);
                    t.start();
                    return id;
                }

                public static void kof_time_cancel(String id) {
                    KOF_TIME_JOBS.remove(id);
                }

                public static String kof_scheduler_every(int ms, Object fn) {
                    return kof_time_interval(ms, fn);
                }

                public static String kof_scheduler_at(String cron, Object fn) {
                    // MVP: cron "0 3 * * *" -> 60s interval for now; parse simple "*/5 * * * *"
                    return kof_time_interval(60000, fn);
                }

                public static void kof_scheduler_cancel(String id) {
                    kof_time_cancel(id);
                }

""";
    }
}
