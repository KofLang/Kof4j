package dev.kof.compiler;

import java.util.List;

/**
 * Runtime do kof.time (sleep/now/interval) — gerado no KofRuntime junto
 * com o JvmRuntime. Separado num arquivo próprio porque o constant pool
 * do javac limita cada string a 65535 bytes.
 */
final class JvmTimeRuntime {

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
                    String id = "cron-" + KOF_TIME_SEQ.incrementAndGet();
                    Thread t = new Thread(() -> {
                        try {
                            java.lang.reflect.Method invoke = fn.getClass().getMethod("invoke");
                            while (KOF_TIME_JOBS.containsKey(id)) {
                                long delay = kof_cron_next_delay_ms(cron);
                                Thread.sleep(delay);
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
                    }, "kof-cron-" + id);
                    t.setDaemon(true);
                    KOF_TIME_JOBS.put(id, t);
                    t.start();
                    return id;
                }

                // Cron 5 campos (min hora mday mon wday), com '*', '*', lista,
                // intervalo e valor exato. wday 0/7 = domingo. Resolução de minutos.
                public static long kof_cron_next_delay_ms(String cron) {
                    java.util.Calendar now = java.util.Calendar.getInstance();
                    long start = now.getTimeInMillis();
                    for (int i = 0; i < 366 * 24 * 60; i++) {
                        now.add(java.util.Calendar.MINUTE, 1);
                        if (kof_cron_matches(cron, now)) {
                            return now.getTimeInMillis() - start;
                        }
                    }
                    return 60000L;
                }

                private static boolean kof_cron_matches(String cron, java.util.Calendar c) {
                    String[] f = cron.trim().split("\\s+");
                    if (f.length < 5) return false;
                    boolean mdayRestricted = !"*".equals(f[2]);
                    boolean wdayRestricted = !"*".equals(f[4]);
                    long min = kof_cron_parse(f[0], 0, 59);
                    long hour = kof_cron_parse(f[1], 0, 23);
                    long mday = kof_cron_parse(f[2], 1, 31);
                    long mon = kof_cron_parse(f[3], 1, 12);
                    long wday = kof_cron_parse(f[4], 0, 6);
                    int minute = c.get(java.util.Calendar.MINUTE);
                    int hourV = c.get(java.util.Calendar.HOUR_OF_DAY);
                    int day = c.get(java.util.Calendar.DAY_OF_MONTH);
                    int month = c.get(java.util.Calendar.MONTH) + 1;
                    int dow = c.get(java.util.Calendar.DAY_OF_WEEK) - 1;
                    if (((min >> minute) & 1L) == 0) return false;
                    if (((hour >> hourV) & 1L) == 0) return false;
                    if (((mon >> month) & 1L) == 0) return false;
                    boolean mdayOk = ((mday >> day) & 1L) != 0;
                    boolean wdayOk = ((wday >> dow) & 1L) != 0;
                    if (mdayRestricted && wdayRestricted) return mdayOk || wdayOk;
                    if (mdayRestricted) return mdayOk;
                    if (wdayRestricted) return wdayOk;
                    return true;
                }

                private static long kof_cron_parse(String field, int min, int max) {
                    long bits = 0L;
                    for (String part : field.split(",")) {
                        if ("*".equals(part)) {
                            for (int v = min; v <= max; v++) bits |= (1L << v);
                        } else if (part.startsWith("*/")) {
                            int step = Integer.parseInt(part.substring(2));
                            for (int v = min; v <= max; v += step) bits |= (1L << v);
                        } else if (part.contains("-")) {
                            String[] r = part.split("-");
                            int lo = Integer.parseInt(r[0]);
                            int hi = Integer.parseInt(r[1]);
                            for (int v = lo; v <= hi; v++) bits |= (1L << v);
                        } else {
                            bits |= (1L << Integer.parseInt(part));
                        }
                    }
                    return bits;
                }

                public static void kof_scheduler_cancel(String id) {
                    kof_time_cancel(id);
                }

""";
    }
}
