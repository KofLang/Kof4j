package dev.kof.compiler.jvm;

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

                // ── kof.time (STDLIB S7-wedge) — calendário civil ─────────
                // isLeapYear: ano bissexto (Gregório: %4 && (!%100 || %400)).
                // daysInMonth: 1..12; mês inválido => 0 (paridade nos 4).
                public static boolean kof_time_isLeapYear(int year) {
                    if (year < 1) return false;
                    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
                }

                private static final int[] KOF_TIME_DIM = {31,28,31,30,31,30,31,31,30,31,30,31};

                public static int kof_time_daysInMonth(int year, int month) {
                    if (year < 1 || month < 1 || month > 12) return 0;
                    return (month == 2 && kof_time_isLeapYear(year)) ? 29 : KOF_TIME_DIM[month - 1];
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
