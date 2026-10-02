package dev.kof.compiler;

import java.util.List;


/**
 * Compile-time dispatch table for the Kof-native time module ({@code kof.time}).
 *
 * <p>The Kof surface is idiomatic:
 *
 * <pre>{@code
 * time.sleep(500)                  // pausa o thread atual (ms)
 * var now = time.now()             // epoch millis
 * var job = time.interval(1000, () -> { poll() })
 * time.cancel(job)
 * }</pre>
 *
 * <p>Internally every call maps to a static {@code kof_time_*} function of the
 * generated {@code dev.kof.runtime.KofRuntime} class (JVM target).
 * Native reuses the scheduler (SCHED001); JS runs a cooperative timer queue
 * pumped by {@code time.sleep} (GraalJS has no event loop — TIME001 closed).
 */
public final class KofTime {

    private KofTime() {}

    static final Type TIME = new Type.ClassType("kof.time", "Time", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type LONG = Type.PrimitiveType.LONG;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isTimeNamespace(String name) {
        return "time".equals(name);
    }

/** X10 fatia 2: nomes aceitos pelo dispatch real (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case-literals da fonte abaixo. */
    static List<String> functions() { return List.of("sleep", "now", "interval", "cancel", "collect", "isLeapYear", "daysInMonth", "dayOfWeek", "daysBetween", "age", "isWeekend", "addDays", "addMonths", "addYears", "startOf", "endOf", "diffDays", "todayIso", "formatDateIso", "isToday", "hoursBetween", "parseDateIso", "tzOffsetSeconds"); }

    static boolean isTimeMethod(String name) {
        return switch (name) {
            case "sleep", "now", "interval", "cancel",
                    // GC manual: mark+sweep conservador do runtime (kof_gc_collect_now)
                    "collect",
                    // STDLIB S7-wedge: calendário civil (escalares puros —
                    // dias entre datas e dia-da-semana chegam no próximo degrau)
                    "isLeapYear", "daysInMonth", "dayOfWeek", "daysBetween", "age",
                    // S7-ext: fim de semana (dayOfWeek >= 6)
                    "isWeekend",
                    // STDLIB S7a: add/diff sobre data ISO (STR->STR/Int)
                    "addDays", "addMonths", "addYears", "startOf", "endOf", "diffDays",
                    // S7e (D-STDLIB ratificado 13/09): hoje/formato UTC-only
                    "todayIso", "formatDateIso", "isToday",
                    // S7f (D3): diferença de horas entre dois instantes
                    // (data+hora), floor simétrico
                    "hoursBetween", "parseDateIso", "tzOffsetSeconds" -> true;
            default -> false;
        };
    }

    record TimeCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** kof.time: now/sleep em todos targets; interval/cancel em JVM+Native
     *  (reaproveita o scheduler — SCHED001) + JS (fila cooperativa bombeada
     *  por time.sleep — GraalJS não expõe setInterval; TIME001 fechado). */
    static boolean supportedOn(@SuppressWarnings("unused") Target target) {
        return true;
    }

    static boolean supportedOn(@SuppressWarnings("unused") String method, @SuppressWarnings("unused") Target target) {
        // TIME001 FEITO no cross (05/09): kof_time_interval/cancel são alias
        // de kof_scheduler_every/cancel no runtime riscv64/aarch64 (thread por
        // job via clone+nanosleep — mesmo mecanismo do spawn).
        // S7a TIME002 (10/09): addDays/diffDays = só JVM-family (JVM/SCRIPT/
        // ANDROID — interpretador herda o KofRuntime do JVM). JS/Native = gap
        // honesto (String-alocação no asm + parse data: escopo próprio, R6 —
        // nunca fallback silencioso).
        // S7b (10/09): JS FECHADO — kofTimeAddDays/kofTimeDiffDays no
        // JsRuntimeUiWeb (mesmo algoritmo civil do wedge, SEM Date =>
        // paridade byte-idêntica).
        // S7c (10/09): x86 FECHADO — RuntimeTimeIso (parse ISO + inversa
        // civil Hinnant + alocação de String no asm; harness C 200k fuzz +
        // matriz stdtime2 rodando local).
        // S7c-1 (11/09): riscv64/aarch64 FECHADOS — TIME002 encerrado.
        // Fatia B33 (NativeRiscvAsmRtB33) = transcrição fiel da máquina x86
        // (parse2/civil/put4/put2 + kof_time_addDays/diffDays) reusando
        // kdv_valid/kdv_epoch da B14; aarch64 via tradutor (divu/remu/
        // sext.w cobertos — verificado). golden stdtime2 nos 4 targets
        // (KofTimeE2ETest#timeAddDaysDiffDaysJvmShapeAndCrossArch).
        // S7h (D1 ratificado 13/09): tzOffsetSeconds = fuso do HOST.
        // Native = gap honesto TIME003 (D1: sem TZ//etc/localtime no asm —
        // implementar seria paridade acidental/falsa). JVM/JS/SCRIPT seguem.
        if ("tzOffsetSeconds".equals(method)
                && (target == Target.NATIVE || target == Target.NATIVE_RISCV64
                    || target == Target.NATIVE_AARCH64)) {
            return false;
        }
        // §426 (improved 25/09): `collect` (manual GC) now has a real face on
        // JS too — `kofGcCollectNow` requests the host GC (`KofJsRunner`
        // `kof_platform.gcCollect` -> `System.gc()`), the SAME JVM semantics
        // as the JVM/SCRIPT runtime (both are GC requests, not guarantees);
        // browser/hostless degrades to an honest runtime error (R7), never a
        // silent no-op. JVM/SCRIPT (JVM runtime), x86 (RuntimeGc) and
        // riscv64/aarch64 (RtB44 + translator) keep the real mark-sweep.
        return true;
    }

    static String gapCode(String method) {
        // TIME001 (interval/cancel) fechado no cross (05/09); TIME002
        // (addDays/diffDays) fechado no cross 11/09 (S7c-1, fatia B35);
        // TIME004 (collect no JS) fechado 25/09 (face real via host GC).
        // gapCode só alimenta o gate de suporte; mantém as chaves por
        // retrocompatibilidade dos diagnósticos existentes.
        if ("tzOffsetSeconds".equals(method)) return "TIME003";
        return ("addDays".equals(method) || "diffDays".equals(method))
                ? "TIME002" : "TIME001";
    }

    static String gapCode() {
        return "TIME001";
    }

    /** {@code time.<method>(...)} — sleep/interval em ms; now em epoch millis. */
    static TimeCall staticCall(String name, List<Type> argTypes) {
        if (!isTimeMethod(name)) return null;
        return switch (name) {
            case "sleep" -> argTypes.size() == 1
                    ? new TimeCall("kof_time_sleep", VOID, List.of(INT))
                    : null;
            case "now" -> argTypes.size() == 0
                    ? new TimeCall("kof_time_now", LONG, List.of())
                    : null;
            case "collect" -> argTypes.size() == 0
                    ? new TimeCall("kof_gc_collect_now", VOID, List.of())
                    : null;
            case "interval" -> argTypes.size() == 2
                    ? new TimeCall("kof_time_interval", STR, List.of(INT, OBJ))
                    : null;
            case "cancel" -> argTypes.size() == 1
                    ? new TimeCall("kof_time_cancel", VOID, List.of(STR))
                    : null;
            // STDLIB S7-wedge — calendário civil (só mnemônicos aritméticos;
            // year >= 1 => nada negativo; paridade byte-a-byte JVM/JS/Native).
            case "isLeapYear" -> argTypes.size() == 1 && argTypes.get(0) == INT
                    ? new TimeCall("kof_time_isLeapYear", BOOL, List.of(INT)) : null;
            case "daysInMonth" -> argTypes.size() == 2
                    ? new TimeCall("kof_time_daysInMonth", INT, List.of(INT, INT)) : null;
            case "dayOfWeek" -> argTypes.size() == 3
                    ? new TimeCall("kof_time_dayOfWeek", INT, List.of(INT, INT, INT)) : null;
            // S7-ext (STDLIB): isWeekend — dayOfWeek>=6 (ISO 1=seg..7=dom).
            // Data inválida => dayOfWeek 0 => false (gating automático, paridade 4).
            case "isWeekend" -> argTypes.size() == 3 && argTypes.get(0) == INT
                    ? new TimeCall("kof_time_isWeekend", BOOL, List.of(INT, INT, INT)) : null;
            case "daysBetween" -> argTypes.size() == 6
                    ? new TimeCall("kof_time_daysBetween", INT,
                            List.of(INT, INT, INT, INT, INT, INT)) : null;
            // S7-wedge ext — idade: anos COMPLETOS entre a data de nascimento e a
            // data de referência (subtrai 1 quando o aniversário ainda não ocorreu
            // na referência). Mesma política "data inválida => 0" do wedge; aritmética
            // 100% inteira => paridade byte-a-byte nos 5 alvos (sem asm de String).
            case "age" -> argTypes.size() == 6
                    ? new TimeCall("kof_time_age", INT,
                            List.of(INT, INT, INT, INT, INT, INT)) : null;
            // STDLIB S7a — data ISO (String) add/diff. JVM/SCRIPT via
            // java.time; x86 (RuntimeTimeIso), riscv64/aarch64 (NativeRiscvAsmRtB33)
            // e JS (JsRuntimeUiWeb) fecham TIME002 (11/09). Inválido => ""/0
            // (paridade com a política "invalid => 0" do calendário wedge).
            case "addDays" -> argTypes.size() == 2 && argTypes.get(0) == STR
                    && argTypes.get(1) == INT
                    ? new TimeCall("kof_time_addDays", STR, List.of(STR, INT)) : null;
            // S7a-ext (STDLIB front #1) — data ISO + N meses. Mesmo contrato de
            // addDays (parse estrito, inválido/out-of-range => ""). clamp de fim
            // de mês (dia=min(dia, daysInMonth)) => paridade com java.time.
            case "addMonths" -> argTypes.size() == 2 && argTypes.get(0) == STR
                    && argTypes.get(1) == INT
                    ? new TimeCall("kof_time_addMonths", STR, List.of(STR, INT)) : null;
            // S7a-ext2 (STDLIB front #1) — data ISO + N anos. clamp de fim de
            // mês em 29/fev (ano destino não-bissexto => 28/fev); inválida /
            // resultado fora de 1..9999 => "". Aritmética inteira pura.
            case "addYears" -> argTypes.size() == 2 && argTypes.get(0) == STR
                    && argTypes.get(1) == INT
                    ? new TimeCall("kof_time_addYears", STR, List.of(STR, INT)) : null;
            // S7a-ext3 (STDLIB front #1) — inicio/fim do período de uma data ISO.
            // unit = day|week|month|year (semana = segunda..domingo, dayOfWeek ISO).
            // Composta dos primitivos já com paridade provada (dayOfWeek/addDays/
            // daysInMonth) => byte-idêntica por construção. Data inválida / unit
            // desconhecida / resultado fora de 1..9999 => "".
            case "startOf" -> argTypes.size() == 2 && argTypes.get(0) == STR
                    && argTypes.get(1) == STR
                    ? new TimeCall("kof_time_startOf", STR, List.of(STR, STR)) : null;
            case "endOf" -> argTypes.size() == 2 && argTypes.get(0) == STR
                    && argTypes.get(1) == STR
                    ? new TimeCall("kof_time_endOf", STR, List.of(STR, STR)) : null;
            case "diffDays" -> argTypes.size() == 2 && argTypes.get(0) == STR
                    && argTypes.get(1) == STR
                    ? new TimeCall("kof_time_diffDays", INT, List.of(STR, STR)) : null;
            // S7e (D-STDLIB ratificado 13/09): hoje/formato UTC-only (D1);
            // formato zero-DSL (D4: invalidez => ""); isToday = igualdade com
            // a data UTC de now() (D5). Sem retorno composto (D2).
            case "todayIso" -> argTypes.isEmpty()
                    ? new TimeCall("kof_time_todayIso", STR, List.of()) : null;
            case "formatDateIso" -> argTypes.size() == 3 && argTypes.get(0) == INT
                    && argTypes.get(1) == INT && argTypes.get(2) == INT
                    ? new TimeCall("kof_time_formatDateIso", STR,
                            List.of(INT, INT, INT)) : null;
            case "isToday" -> argTypes.size() == 3 && argTypes.get(0) == INT
                    && argTypes.get(1) == INT && argTypes.get(2) == INT
                    ? new TimeCall("kof_time_isToday", BOOL,
                            List.of(INT, INT, INT)) : null;
            // D3: floor simétrico sobre horas completas (consistente com
            // daysBetween = truncado a zero); sem float (FLT001).
            // D4: parseDateIso (STR) -> Int serial daysFromEpoch; inválido => 0.
            case "parseDateIso" -> argTypes.size() == 1 && argTypes.get(0) == STR
                    ? new TimeCall("kof_time_parseDateIso", INT, List.of(STR)) : null;
            // D1: fuso do HOST como getter explícito — JVM host TZ, JS
            // getTimezoneOffset (min->seg, invertido), SCRIPT herda JVM;
            // NATIVE = gap honesto TIME003 (sem TZ//etc/localtime no asm).
            case "tzOffsetSeconds" -> argTypes.isEmpty()
                    ? new TimeCall("kof_time_tzOffsetSeconds", INT, List.of()) : null;
            case "hoursBetween" -> {
                if (argTypes.size() == 8 && argTypes.stream().allMatch(a -> a == INT)) {
                    yield new TimeCall("kof_time_hoursBetween", INT,
                            List.of(INT, INT, INT, INT, INT, INT, INT, INT));
                }
                yield null;
            }
            default -> null;
        };
    }
}