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

                public static void kof_gc_collect_now() {
                    System.gc();
                }
            """ + JvmTimeCalendar.source() + """
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

                // ── kof.scheduler.at — duração idiomática (D-SCHED-DURATION)
                // Além do cron de 5 campos, `at` aceita expressões como
                // "30m", "90s", "1d&30m": termo = dígitos + unidade, unidade
                // ∈ { s, m, h, d, M, a }; composição com '&'. s/m/h/d são
                // fixos em ms; M/a avançam o calendário UTC com clamp no
                // último dia do mês alvo (2024-01-31 + 1M = 2024-02-29).
                // Malformada lança IllegalArgumentException (R6 — nunca
                // silencioso). Native mantém o gap honesto CRON001.
                /** { fixedMs, months, years }; null se a expressão NÃO é
                 *  duração (cai no caminho cron). */
                static long[] kof_duration_parse(String expr) {
                    if (expr == null) return null;
                    String e = expr.trim();
                    if (e.isEmpty()) return null;
                    String[] terms = e.split("&", -1);
                    if (terms.length == 0) return null;
                    long fixed = 0;
                    long months = 0;
                    long years = 0;
                    for (String raw : terms) {
                        String t = raw.trim();
                        if (t.isEmpty() || !Character.isDigit(t.charAt(0))) return null;
                        int i = 0;
                        while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
                        if (i == t.length() || i > 18) return null;
                        long n;
                        try { n = Long.parseLong(t.substring(0, i)); }
                        catch (NumberFormatException nfe) { return null; }
                        if (n <= 0) return null;
                        // unidade: 'ms' (2 chars) antes da forma de 1 char
                        String unit;
                        if (t.charAt(i) == 'm' && i + 1 < t.length() && t.charAt(i + 1) == 's') {
                            unit = "ms";
                            if (t.length() - i != 2) return null;
                        } else {
                            if (t.length() - i != 1) return null;
                            unit = t.substring(i);
                        }
                        long add;
                        switch (unit) {
                            case "ms" -> add = n;
                            case "s" -> {
                                if (n > Long.MAX_VALUE / 1000L) return null;
                                add = n * 1000L;
                            }
                            case "m" -> {
                                if (n > Long.MAX_VALUE / 60000L) return null;
                                add = n * 60000L;
                            }
                            case "h" -> {
                                if (n > Long.MAX_VALUE / 3600000L) return null;
                                add = n * 3600000L;
                            }
                            case "d" -> {
                                if (n > Long.MAX_VALUE / 86400000L) return null;
                                add = n * 86400000L;
                            }
                            case "M" -> {
                                if (n > 999_999_999L) return null;
                                months += n;
                                add = 0;
                            }
                            case "a" -> {
                                if (n > 999_999_999L) return null;
                                years += n;
                                add = 0;
                            }
                            default -> { return null; }
                        }
                        fixed += add;
                        if (fixed < 0) return null;   // overflow da soma
                    }
                    return new long[]{fixed, months, years};
                }

                /** Próximo instante (epoch ms) para a duração a partir da
                 *  âncora: fixo = âncora + fixedMs; calendário = âncora
                 *  avançada (years, months) + fixedMs. UTC, clamp java.time. */
                static long kof_duration_next_from(long[] dur, long anchorMillis) {
                    long next = anchorMillis + dur[0];
                    if (dur[1] != 0 || dur[2] != 0) {
                        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(anchorMillis)
                                .atZone(java.time.ZoneOffset.UTC);
                        if (dur[2] != 0) z = z.plusYears(dur[2]);
                        if (dur[1] != 0) z = z.plusMonths(dur[1]);
                        next = z.toInstant().toEpochMilli() + dur[0];
                    }
                    return next;
                }

                /** Delay (ms) do primeiro disparo a partir de nowMillis. */
                public static long kof_duration_next_delay_ms(String expr, long nowMillis) {
                    long[] dur = kof_duration_parse(expr);
                    if (dur == null) throw new IllegalArgumentException("duration: not a duration expression: " + expr);
                    return kof_duration_next_from(dur, nowMillis) - nowMillis;
                }

                // ── kof.scheduler.at — cron real (CRON001) ──────────────
                // 5 campos: minuto hora dia-do-mês mês dia-da-semana,
                // avaliados em UTC (convenção do stdlib: determinismo e
                // paridade entre alvos — sem DST). Suporta *, a, a-b,
                // a-b/s, */s e listas com vírgula. DOW 0/7 = domingo.
                // Quando dia-do-mês E dia-da-semana são restritos vale o OU
                // (regra cron clássica). Cron inválido lança
                // IllegalArgumentException (alto, nunca silencioso — R6).
                public static long kof_cron_next_delay_ms(String cron, long nowMillis) {
                    return kof_cron_next_delay_ms(kof_cron_parse(cron), nowMillis);
                }

                static long[] kof_cron_parse(String cron) {
                    if (cron == null) throw new IllegalArgumentException("cron: null expression");
                    String[] f = cron.trim().split("\\\\s+");
                    if (f.length != 5) {
                        throw new IllegalArgumentException("cron: expected 5 fields, got " + f.length + ": " + cron);
                    }
                    long[] out = new long[7];
                    out[0] = kof_cron_field(f[0], 0, 59, "minute");
                    out[1] = kof_cron_field(f[1], 0, 23, "hour");
                    out[2] = kof_cron_field(f[2], 1, 31, "day-of-month");
                    out[3] = kof_cron_field(f[3], 1, 12, "month");
                    out[4] = kof_cron_field(f[4], 0, 7, "day-of-week");
                    if ((out[4] & (1L << 7)) != 0) out[4] |= 1L;   // 7 == domingo == 0
                    out[4] &= ~(1L << 7);
                    out[5] = f[2].equals("*") ? 0 : 1;
                    out[6] = f[4].equals("*") ? 0 : 1;
                    return out;
                }

                static long kof_cron_field(String spec, int min, int max, String name) {
                    long mask = 0;
                    for (String part : spec.split(",")) {
                        if (part.isEmpty()) throw new IllegalArgumentException("cron: empty " + name + " field");
                        int step = 1;
                        String range = part;
                        int slash = part.indexOf('/');
                        if (slash >= 0) {
                            range = part.substring(0, slash);
                            step = kof_cron_int(part.substring(slash + 1), name);
                            if (step <= 0) throw new IllegalArgumentException("cron: bad step in " + name + ": " + part);
                        }
                        int lo;
                        int hi;
                        if (range.equals("*")) {
                            lo = min;
                            hi = max;
                        } else {
                            int dash = range.indexOf('-');
                            if (dash >= 0) {
                                lo = kof_cron_int(range.substring(0, dash), name);
                                hi = kof_cron_int(range.substring(dash + 1), name);
                            } else {
                                lo = kof_cron_int(range, name);
                                hi = slash >= 0 ? max : lo;
                            }
                        }
                        if (lo < min || hi > max || lo > hi) {
                            throw new IllegalArgumentException("cron: " + name + " out of range: " + part);
                        }
                        for (int v = lo; v <= hi; v += step) mask |= 1L << v;
                    }
                    return mask;
                }

                static int kof_cron_int(String s, String name) {
                    try {
                        return Integer.parseInt(s.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("cron: bad " + name + " value: " + s);
                    }
                }

                static boolean kof_cron_matches(long[] f, java.time.ZonedDateTime z) {
                    if ((f[0] >>> z.getMinute() & 1L) == 0) return false;
                    if ((f[1] >>> z.getHour() & 1L) == 0) return false;
                    if ((f[3] >>> z.getMonthValue() & 1L) == 0) return false;
                    boolean domOk = (f[2] >>> z.getDayOfMonth() & 1L) != 0;
                    boolean dowOk = (f[4] >>> (z.getDayOfWeek().getValue() % 7) & 1L) != 0;
                    if (f[5] == 1 && f[6] == 1) return domOk || dowOk;
                    if (f[5] == 1) return domOk;
                    if (f[6] == 1) return dowOk;
                    return true;
                }

                /** Próximo delay (ms) a partir de nowMillis; varre minuto a
                 *  minuto por até 4 anos (cobre 29/02). */
                static long kof_cron_next_delay_ms(long[] f, long nowMillis) {
                    long t = (nowMillis / 60000L) * 60000L + 60000L;
                    for (int i = 0; i < 366 * 24 * 60 * 4; i++) {
                        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(t)
                                .atZone(java.time.ZoneOffset.UTC);
                        if (kof_cron_matches(f, z)) return t - nowMillis;
                        t += 60000L;
                    }
                    return 60000L;
                }

                public static String kof_scheduler_at(String cron, Object fn) {
                    long[] dur = kof_duration_parse(cron);
                    if (dur != null) return kof_scheduler_duration(dur, fn);
                    long[] fields = kof_cron_parse(cron);
                    String id = "job-" + KOF_TIME_SEQ.incrementAndGet();
                    Thread t = new Thread(() -> {
                        try {
                            java.lang.reflect.Method invoke = fn.getClass().getMethod("invoke");
                            while (KOF_TIME_JOBS.containsKey(id)) {
                                long delay = kof_cron_next_delay_ms(fields, System.currentTimeMillis());
                                long slept = 0;
                                while (slept < delay && KOF_TIME_JOBS.containsKey(id)) {
                                    long chunk = Math.min(1000L, delay - slept);
                                    Thread.sleep(chunk);
                                    slept += chunk;
                                }
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

                /** Agendador por duração (D-SCHED-DURATION): 1º disparo após
                 *  o intervalo, depois repetido; a âncora dos termos M/a
                 *  avança do disparo anterior (nunca de `now` — sem drift).
                 *  Se o host atrasar além do alvo, a âncora salta em passos
                 *  inteiros até o futuro (sem rajada de disparos). */
                static String kof_scheduler_duration(long[] dur, Object fn) {
                    String id = "job-" + KOF_TIME_SEQ.incrementAndGet();
                    Thread t = new Thread(() -> {
                        try {
                            java.lang.reflect.Method invoke = fn.getClass().getMethod("invoke");
                            long anchor = System.currentTimeMillis();
                            while (KOF_TIME_JOBS.containsKey(id)) {
                                long target = kof_duration_next_from(dur, anchor);
                                long now = System.currentTimeMillis();
                                while (target <= now && KOF_TIME_JOBS.containsKey(id)) {
                                    anchor = target;
                                    target = kof_duration_next_from(dur, anchor);
                                }
                                if (!KOF_TIME_JOBS.containsKey(id)) break;
                                long delay = target - now;
                                long slept = 0;
                                while (slept < delay && KOF_TIME_JOBS.containsKey(id)) {
                                    long chunk = Math.min(1000L, delay - slept);
                                    Thread.sleep(chunk);
                                    slept += chunk;
                                }
                                if (!KOF_TIME_JOBS.containsKey(id)) break;
                                anchor = target;
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
                    }, "kof-duration-" + id);
                    t.setDaemon(true);
                    KOF_TIME_JOBS.put(id, t);
                    t.start();
                    return id;
                }

                public static void kof_scheduler_cancel(String id) {
                    kof_time_cancel(id);
                }

            """;
    }
}
