package dev.kof.compiler.jvm;

/**
 * Runtime kof.time — calendário civil + wedge ISO (S7/S7a/S7e/S7f/S7g/S7h).
 * Fatia extraída de {@link JvmTimeRuntime} pelo gate REFACTOR-500; o texto
 * gerado é concatenado na MESMA ordem dentro de KofRuntime — byte-idêntico.
 */
public final class JvmTimeCalendar {

    private JvmTimeCalendar() {}

    static String source() {
        return """
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

                // Serial civil -> dias desde 1970-01-01 (algoritmo Howard Hinnant,
                // dias-civil; verificado contra referência em 8 datas 1..9999).
                private static long kof_time_epochDay(int year, int month, int day) {
                    long y = year - (month <= 2 ? 1 : 0);
                    long era = Math.floorDiv(y, 400);
                    long yoe = y - era * 400;
                    long mp = month + (month > 2 ? -3 : 9);
                    long doy = Math.floorDiv(153 * mp + 2, 5) + day - 1;
                    long doe = yoe * 365 + Math.floorDiv(yoe, 4) - Math.floorDiv(yoe, 100) + doy;
                    return era * 146097 + doe - 719468;
                }

                private static boolean kof_time_validDate(int y, int m, int d) {
                    if (y < 1 || y > 9999 || m < 1 || m > 12) return false;
                    return d >= 1 && d <= kof_time_daysInMonth(y, m);
                }

                // ISO: 1=segunda .. 7=domingo. Data inválida => 0 (paridade 4).
                public static int kof_time_dayOfWeek(int year, int month, int day) {
                    if (!kof_time_validDate(year, month, day)) return 0;
                    long ed = kof_time_epochDay(year, month, day);
                    return (int) Math.floorMod(ed + 3, 7) + 1;
                }

                // isWeekend (S7-ext): dayOfWeek >= 6 (ISO 1=seg..7=dom).
                // Data inválida => dayOfWeek 0 => false (gating automático).
                public static boolean kof_time_isWeekend(int year, int month, int day) {
                    return kof_time_dayOfWeek(year, month, day) >= 6;
                }

                public static int kof_time_daysBetween(int y1, int m1, int d1,
                                                       int y2, int m2, int d2) {
                    if (!kof_time_validDate(y1, m1, d1) || !kof_time_validDate(y2, m2, d2)) return 0;
                    // 1..9999 => diff cabe em Int (máx ~3.65M dias)
                     return (int) (kof_time_epochDay(y2, m2, d2) - kof_time_epochDay(y1, m1, d1));
                 }

                 // kof_time_age(birthY,birthM,birthDay, refY,refM,refDay) -> Int | 0
                 // Anos COMPLETOS entre as duas datas. Aritmética inteira (sem
                 // java.time) => MESMA fórmula nos 5 alvos => paridade byte-a-byte.
                 // Data inválida => 0 (política do wedge).
                 public static int kof_time_age(int by, int bm, int bd,
                                                int ry, int rm, int rd) {
                     if (!kof_time_validDate(by, bm, bd) || !kof_time_validDate(ry, rm, rd)) return 0;
                     int years = ry - by;
                     if (rm < bm || (rm == bm && rd < bd)) years -= 1;
                     return years;
                 }

                // ── kof.time (STDLIB S7a) — data ISO (String) add/diff ─────
                // "YYYY-MM-DD" estrito; inválido => "" (add) / 0 (diff) —
                // mesma política "invalid => 0" do calendário wedge.
                // §182 (13/09): parse ESTRITO dígito a dígito — contrato
                // declarado "YYYY-MM-DD … dígitos" (Native é a referência).
                // Integer.parseInt aceitava sinal (+999/-9) = desvio do
                // contrato e divergência silenciosa cross-target (regra 5).
                private static int kof_time_digits(String s, int from, int len) {
                    int v = 0;
                    for (int i = from; i < from + len; i++) {
                        char c = s.charAt(i);
                        if (c < '0' || c > '9') return -1;
                        v = v * 10 + (c - '0');
                    }
                    return v;
                }

                private static java.time.LocalDate kof_time_parseIso(String iso) {
                    if (iso == null || iso.length() != 10) return null;
                    if (iso.charAt(4) != '-' || iso.charAt(7) != '-') return null;
                    int y = kof_time_digits(iso, 0, 4);
                    int m = kof_time_digits(iso, 5, 2);
                    int d = kof_time_digits(iso, 8, 2);
                    if (y < 0 || m < 0 || d < 0) return null;
                    if (!kof_time_validDate(y, m, d)) return null;
                    return java.time.LocalDate.of(y, m, d);
                }

                public static String kof_time_addDays(String iso, int days) {
                    java.time.LocalDate ld = kof_time_parseIso(iso);
                    if (ld == null) return "";
                    java.time.LocalDate r = ld.plusDays(days);
                    if (r.getYear() < 1 || r.getYear() > 9999) return "";
                    return String.format("%04d-%02d-%02d", r.getYear(), r.getMonthValue(), r.getDayOfMonth());
                }

                // S7a-ext — ISO date + N meses. Aritmética inteira pura (sem
                // java.time no resultado): t = ano*12 + (mes-1) + n; y1 = t/12;
                // m1 = t%12 + 1; d1 = min(d, daysInMonth(y1,m1)) (clamp de fim de
                // mês). Pré-guarda t em [12,119999] => divisão sempre positiva
                // (idêntico nos 5 alvos). Inválida/out-of-range => "" (mesma
                // política do addDays). Provado byte-idêntico ao java.time
                // (3M+ casos fuzz), e portanto ao Native.
                public static String kof_time_addMonths(String iso, int months) {
                    java.time.LocalDate ld = kof_time_parseIso(iso);
                    if (ld == null) return "";
                    long t = (long) ld.getYear() * 12 + (ld.getMonthValue() - 1) + (long) months;
                    if (t < 12 || t > 119999) return "";
                    int y1 = (int) (t / 12);
                    int m1 = (int) (t % 12) + 1;
                    int d1 = Math.min(ld.getDayOfMonth(), kof_time_daysInMonth(y1, m1));
                    return String.format("%04d-%02d-%02d", y1, m1, d1);
                }

                // STDLIB S7a-ext2 (front #1): ISO date + N years. Same clamp of
                // end-of-month (Feb 29 -> Feb 28 when the target year is not a
                // leap year), year range 1..9999, and invalid/out-of-range =>
                // "". Pure integer arithmetic (byte-identical across backends).
                public static String kof_time_addYears(String iso, int years) {
                    java.time.LocalDate ld = kof_time_parseIso(iso);
                    if (ld == null) return "";
                    int y1 = ld.getYear() + years;
                    if (y1 < 1 || y1 > 9999) return "";
                    int m1 = ld.getMonthValue();
                    int d1 = Math.min(ld.getDayOfMonth(), kof_time_daysInMonth(y1, m1));
                    return String.format("%04d-%02d-%02d", y1, m1, d1);
                }

                // S7a-ext3 (STDLIB front #1): inicio/fim do periodo de uma data
                // ISO. unit = day|week|month|year; semana = segunda..domingo
                // (dayOfWeek ISO 1=seg..7=dom). Composto dos primitivos ja com
                // paridade provada (dayOfWeek/validDate/daysInMonth) => os 5
                // backends usam a MESMA aritmetica inteira. Data invalida /
                // unit desconhecida / resultado fora de 1..9999 => "".
                public static String kof_time_startOf(String iso, String unit) {
                    return kof_time_boundOf(iso, unit, true);
                }

                public static String kof_time_endOf(String iso, String unit) {
                    return kof_time_boundOf(iso, unit, false);
                }

                private static String kof_time_boundOf(String iso, String unit, boolean start) {
                    java.time.LocalDate ld = kof_time_parseIso(iso);
                    if (ld == null) return "";
                    int y = ld.getYear();
                    int m = ld.getMonthValue();
                    int d = ld.getDayOfMonth();
                    int y1 = y, m1 = m, d1 = d;
                    switch (unit == null ? "" : unit) {
                        case "day" -> { }
                        case "week" -> {
                            int dow = kof_time_dayOfWeek(y, m, d);
                            long epoch = kof_time_epochDay(y, m, d) + (start ? 1L - dow : 7L - dow);
                            long[] ymd = kof_time_civilFromEpochDay(epoch);
                            if (ymd[0] < 1 || ymd[0] > 9999) return "";
                            y1 = (int) ymd[0]; m1 = (int) ymd[1]; d1 = (int) ymd[2];
                        }
                        case "month" -> d1 = start ? 1 : kof_time_daysInMonth(y, m);
                        case "year" -> { m1 = start ? 1 : 12; d1 = start ? 1 : 31; }
                        default -> { return ""; }
                    }
                    if (!kof_time_validDate(y1, m1, d1)) return "";
                    return String.format("%04d-%02d-%02d", y1, m1, d1);
                }

                public static int kof_time_diffDays(String iso1, String iso2) {
                    java.time.LocalDate a = kof_time_parseIso(iso1);
                    java.time.LocalDate b = kof_time_parseIso(iso2);
                    if (a == null || b == null) return 0;
                    long diff = java.time.temporal.ChronoUnit.DAYS.between(a, b);
                    return (diff < Integer.MIN_VALUE || diff > Integer.MAX_VALUE)
                            ? 0 : (int) diff;
                }

                // ── kof.time (STDLIB S7e) — hoje/formato UTC (D-STDLIB) ────
                // D1: UTC-only em TODOS os alvos (deriva de now() em UTC).
                // D4: zero pattern-DSL; invalidade => "". D5: isToday =
                // igualdade com a data UTC de now(). Serial = dias-civil
                // (epochDay Hinnant acima — MESMA base do add/diffDays).
                public static String kof_time_todayIso() {
                    long epochDay = Math.floorDiv(System.currentTimeMillis(), 86400000L);
                    long[] ymd = kof_time_civilFromEpochDay(epochDay);
                    return String.format("%04d-%02d-%02d", ymd[0], ymd[1], ymd[2]);
                }

                public static String kof_time_formatDateIso(int year, int month, int day) {
                    if (!kof_time_validDate(year, month, day)) return "";
                    return String.format("%04d-%02d-%02d", year, month, day);
                }

                public static boolean kof_time_isToday(int year, int month, int day) {
                    return kof_time_isValidIso(year, month, day)
                            && kof_time_formatDateIso(year, month, day).equals(kof_time_todayIso());
                }

                private static boolean kof_time_isValidIso(int y, int m, int d) {
                    return kof_time_validDate(y, m, d);
                }

                // Inversa Hinnant (dias-civil -> [y,m,d]) — MESMO algoritmo do
                // .Lka_civil x86 (RuntimeTimeIso) e .Lu8_civil riscv (B33).
                private static long[] kof_time_civilFromEpochDay(long z) {
                    z += 719468;
                    long era = Math.floorDiv(z, 146097);
                    long doe = z - era * 146097;
                    long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
                    long y = yoe + era * 400;
                    long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
                    long mp = (5 * doy + 2) / 153;
                    long d = doy - (153 * mp + 2) / 5 + 1;
                    long m = mp < 10 ? mp + 3 : mp - 9;
                    return new long[]{m <= 2 ? y + 1 : y, m, d};
                }

                // ── kof.time (STDLIB S7f) — hoursBetween (D3) ──────────────
                // floor simétrico: conta horas COMPLETAS entre os instantes
                // (data+hora), truncado em direção a zero (mesma convenção
                // daysBetween). Datas inválidas => 0; hora fora de 0..23
                // também invalida o instante (paridade do gating do wedge).
                public static int kof_time_hoursBetween(int y1, int m1, int d1, int h1,
                                                        int y2, int m2, int d2, int h2) {
                    if (!kof_time_validDate(y1, m1, d1) || !kof_time_validDate(y2, m2, d2)) return 0;
                    if (h1 < 0 || h1 > 23 || h2 < 0 || h2 > 23) return 0;
                    long hours1 = kof_time_epochDay(y1, m1, d1) * 24 + h1;
                    long hours2 = kof_time_epochDay(y2, m2, d2) * 24 + h2;
                    long diff = hours2 - hours1;
                    return (diff < Integer.MIN_VALUE || diff > Integer.MAX_VALUE)
                            ? 0 : (int) diff;
                }

                // ── kof.time (STDLIB S7g) — parseDateIso (D4) ──────────────
                // STR "YYYY-MM-DD" estrito -> serial daysFromEpoch; inválido
                // => 0 (mesma política do calendário wedge). Serial = MESMO
                // domínio de hoursBetween/daysBetween (recomposição fecha).
                public static int kof_time_parseDateIso(String iso) {
                    java.time.LocalDate ld = kof_time_parseIso(iso);
                    if (ld == null) return 0;
                    return (int) kof_time_epochDay(ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth());
                }

                // ── kof.time (STDLIB S7h) — tzOffsetSeconds (D1) ───────────
                // Fuso do HOST como getter explícito (segundos leste+).
                // D1: todayIso/isToday NUNCA usam isto (UTC-only em todos
                // os alvos) — sem paridade acidental de fuso.
                public static int kof_time_tzOffsetSeconds() {
                    return java.time.ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now()).getTotalSeconds();
                }

            """;
    }
}
