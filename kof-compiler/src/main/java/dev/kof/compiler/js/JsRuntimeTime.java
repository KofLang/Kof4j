package dev.kof.compiler.js;

/**
 * kof-runtime.mjs — relógio/timers JS (kof.time): sleep cooperativo async
 * (§132/#83-JS), timers de interval e a bomba `__kofSleepStep` que o host
 * {@code KofJsRunner} dirige no GraalJS (sem event-loop nativo). Extraído de
 * {@code JsRuntimeUiWeb} para manter cada classe dentro do orçamento de linhas
 * (gate REFACTOR-500). Concatenado na MESMA mensagem de módulo, na mesma ordem
 * anterior — o JS emitido é byte-a-byte idêntico.
 */
final class JsRuntimeTime {
    private JsRuntimeTime() {
    }

    static String calendarRuntime() {
        return """
            // ── kof.time (STDLIB S7) — calendário civil + ISO wedge (split do
            // UiWeb, gate REFACTOR-500). Concatenado no MESMO módulo, mesma ordem;
            // o JS emitido é byte-a-byte idêntico ao anterior.
            export function kofTimeNow() {
                return Date.now();
            }

            // ── kof.time (STDLIB S7e) — hoje/formato UTC (D-STDLIB) ──────
            // D1: UTC-only (getUTC* — Date.now() é epoch UTC). D4: invalidade
            // => "". D5: isToday = igualdade com a data UTC de now(). Serial =
            // MESMO epochDay do kofTimeEpochDay acima (addDays/diffDays).
            export function kofTimeTodayIso() {
                const ed = Math.floor(Date.now() / 86400000);
                const c = kofTimeCivilFromEpochDay(ed);
                return kofTimePut4(c[0]) + "-" + kofTimePut2(c[1]) + "-" + kofTimePut2(c[2]);
            }
            export function kofTimeFormatDateIso(year, month, day) {
                if (year < 1 || year > 9999 || month < 1 || month > 12) return "";
                if (day < 1 || day > kofTimeDaysInMonth(year, month)) return "";
                return kofTimePut4(year) + "-" + kofTimePut2(month) + "-" + kofTimePut2(day);
            }
            export function kofTimeIsToday(year, month, day) {
                if (year < 1 || year > 9999 || month < 1 || month > 12) return false;
                if (day < 1 || day > kofTimeDaysInMonth(year, month)) return false;
                return kofTimeFormatDateIso(year, month, day) === kofTimeTodayIso();
            }
            function kofTimeCivilFromEpochDay(z) {
                z += 719468;
                const era = Math.floor(z / 146097);
                const doe = z - era * 146097;
                const yoe = Math.floor((doe - Math.floor(doe / 1460) + Math.floor(doe / 36524) - Math.floor(doe / 146096)) / 365);
                const y = yoe + era * 400;
                const doy = doe - (365 * yoe + Math.floor(yoe / 4) - Math.floor(yoe / 100));
                const mp = Math.floor((5 * doy + 2) / 153);
                const d = doy - Math.floor((153 * mp + 2) / 5) + 1;
                const m = mp < 10 ? mp + 3 : mp - 9;
                return [m <= 2 ? y + 1 : y, m, d];
            }
            function kofTimePut4(v) {
                return String(Math.floor(v / 1000) % 10)
                    + String(Math.floor(v / 100) % 10)
                    + String(Math.floor(v / 10) % 10)
                    + String(v % 10);
            }
            function kofTimePut2(v) {
                return String(Math.floor(v / 10) % 10) + String(v % 10);
            }

            // ── kof.time (STDLIB S7f) — hoursBetween (D3) ─────────────────
            // floor simétrico (truncado a zero, como daysBetween); datas
            // inválidas/hora fora de 0..23 => 0 (paridade wedge).
            export function kofTimeHoursBetween(y1, m1, d1, h1, y2, m2, d2, h2) {
                if (y1 < 1 || y1 > 9999 || m1 < 1 || m1 > 12) return 0;
                if (y2 < 1 || y2 > 9999 || m2 < 1 || m2 > 12) return 0;
                if (d1 < 1 || d1 > kofTimeDaysInMonth(y1, m1)) return 0;
                if (d2 < 1 || d2 > kofTimeDaysInMonth(y2, m2)) return 0;
                if (h1 < 0 || h1 > 23 || h2 < 0 || h2 > 23) return 0;
                const hours1 = kofTimeEpochDay(y1, m1, d1) * 24 + h1;
                const hours2 = kofTimeEpochDay(y2, m2, d2) * 24 + h2;
                const diff = hours2 - hours1;
                return (diff < -2147483648 || diff > 2147483647) ? 0 : diff;
            }

            // ── kof.time (STDLIB S7g) — parseDateIso (D4) ─────────────────
            // "YYYY-MM-DD" estrito (10 chars, hífens 4/7, dígitos, data
            // válida) -> serial daysFromEpoch; inválido => 0. MESMO serial de
            // hoursBetween (epochDay*24+h) — recomposição fecha.
            export function kofTimeParseDateIso(iso) {
                if (typeof iso !== "string" || iso.length !== 10) return 0;
                if (iso.charAt(4) !== "-" || iso.charAt(7) !== "-") return 0;
                for (let i = 0; i < 10; i++) {
                    if (i === 4 || i === 7) continue;
                    const c = iso.charAt(i);
                    if (c < "0" || c > "9") return 0;
                }
                const y = parseInt(iso.substring(0, 4), 10);
                const m = parseInt(iso.substring(5, 7), 10);
                const d = parseInt(iso.substring(8, 10), 10);
                if (y < 1 || y > 9999 || m < 1 || m > 12) return 0;
                if (d < 1 || d > kofTimeDaysInMonth(y, m)) return 0;
                return kofTimeEpochDay(y, m, d);
            }

            // ── kof.time (STDLIB S7h) — tzOffsetSeconds (D1) ──────────────
            // Fuso do HOST: Date.getTimezoneOffset() = minutos A OESTE do
            // UTC (São Paulo = +180) => segundos leste+ = -min*60 (paridade
            // JVM ZoneOffset.systemDefault().getTotalSeconds()).
            export function kofTimeTzOffsetSeconds() {
                return -(new Date().getTimezoneOffset()) * 60;
            }

            // ── kof.time (STDLIB S7-wedge) — calendário civil ─────────────
            export function kofTimeIsLeapYear(year) {
                if (year < 1) return 0;
                return (year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)) ? 1 : 0;
            }
            export function kofTimeDaysInMonth(year, month) {
                if (year < 1 || month < 1 || month > 12) return 0;
                const dim = [31,28,31,30,31,30,31,31,30,31,30,31];
                if (month === 2 && kofTimeIsLeapYear(year)) return 29;
                return dim[month - 1];
            }
            function kofTimeEpochDay(year, month, day) {
                const y = year - (month <= 2 ? 1 : 0);
                const era = Math.floor(y / 400);
                const yoe = y - era * 400;
                const mp = month + (month > 2 ? -3 : 9);
                const doy = Math.floor((153 * mp + 2) / 5) + day - 1;
                const doe = yoe * 365 + Math.floor(yoe / 4) - Math.floor(yoe / 100) + doy;
                return era * 146097 + doe - 719468;
            }
            function kofTimeValidDate(y, m, d) {
                if (y < 1 || y > 9999 || m < 1 || m > 12) return false;
                return d >= 1 && d <= kofTimeDaysInMonth(y, m);
            }
            export function kofTimeDayOfWeek(year, month, day) {
                if (!kofTimeValidDate(year, month, day)) return 0;
                const ed = kofTimeEpochDay(year, month, day);
                return ((ed % 7) + 7 + 3) % 7 + 1;   // floorMod(ed+3, 7) + 1
            }
            export function kofTimeIsWeekend(year, month, day) {
                return kofTimeDayOfWeek(year, month, day) >= 6 ? 1 : 0;
            }
             export function kofTimeDaysBetween(y1, m1, d1, y2, m2, d2) {
                 if (!kofTimeValidDate(y1, m1, d1) || !kofTimeValidDate(y2, m2, d2)) return 0;
                 return kofTimeEpochDay(y2, m2, d2) - kofTimeEpochDay(y1, m1, d1);
             }
             // STDLIB S7-wedge ext — idade: anos COMPLETOS entre nascimento e
             // referência. Mesma fórmula inteira dos demais alvos => paridade
             // byte-idêntica. Data inválida => 0 (política do wedge).
             export function kofTimeAge(by, bm, bd, ry, rm, rd) {
                 if (!kofTimeValidDate(by, bm, bd) || !kofTimeValidDate(ry, rm, rd)) return 0;
                 let years = ry - by;
                 if (rm < bm || (rm === bm && rd < bd)) years -= 1;
                 return years;
             }
            // STDLIB S7b — data ISO (String) add/diff. MESMO algoritmo civil
            // do wedge (época de Hinnant + inversa), SEM Date (evita DST e o
            // parse de ano 2-dígitos) => paridade byte-idêntica JVM/JS/Native.
            // Inválido => "" (add) / 0 (diff) — política "invalid => 0".
            function kofTimeCivilFromEpoch(ed) {
                const floor = (a, b) => Math.floor(a / b);
                const z = ed + 719468;
                const era = z >= 0 ? floor(z, 146097) : floor(z - 146096, 146097);
                const doe = z - era * 146097;                              // [0, 146096]
                const yoe = floor(doe - floor(doe, 1460) + floor(doe, 36524) - floor(doe, 146096), 365);
                const y = yoe + era * 400;
                const doy = doe - (365 * yoe + floor(yoe, 4) - floor(yoe, 100)); // [0, 365]
                const mp = floor(5 * doy + 2, 153);
                const d = doy - floor(153 * mp + 2, 5) + 1;
                const m = mp + (mp < 10 ? 3 : -9);
                return { y: y + (m <= 2 ? 1 : 0), m: m, d: d };
            }
            // §182 (13/09): parse ESTRITO dígito a dígito — MESMO contrato do
            // kofTimeParseDateIso (l. acima) e do Native (referência). O
            // parseInt aceitava sinal (+999/-9) = inconsistência interna do
            // JS (addDays/diffDays divergiam de parseDateIso) e cross-target.
            function kofTimeDigits(s, from, len) {
                let v = 0;
                for (let i = from; i < from + len; i++) {
                    const c = s.charCodeAt(i);
                    if (c < 48 || c > 57) return -1;
                    v = v * 10 + (c - 48);
                }
                return v;
            }
            function kofTimeParseIso(s) {
                if (typeof s !== "string" || s.length !== 10) return null;
                if (s.charCodeAt(4) !== 45 || s.charCodeAt(7) !== 45) return null;
                const y = kofTimeDigits(s, 0, 4);
                const m = kofTimeDigits(s, 5, 2);
                const d = kofTimeDigits(s, 8, 2);
                if (y < 0 || m < 0 || d < 0 || !kofTimeValidDate(y, m, d)) return null;
                return { y: y, m: m, d: d };
            }
            function kofTimePad2(n) { return (n < 10 ? "0" : "") + n; }
            function kofTimePad4(n) {
                let s = "" + n;
                while (s.length < 4) s = "0" + s;
                return s;
            }
             export function kofTimeAddDays(iso, days) {
                 const a = kofTimeParseIso(iso);
                 if (!a) return "";
                 const r = kofTimeCivilFromEpoch(kofTimeEpochDay(a.y, a.m, a.d) + days);
                 if (r.y < 1 || r.y > 9999) return "";
                 return kofTimePad4(r.y) + "-" + kofTimePad2(r.m) + "-" + kofTimePad2(r.d);
             }
             // STDLIB S7a-ext — ISO date + N meses (front #1 da stdlib). MESMA
             // aritmética inteira dos demais alvos: t = y*12+(m-1)+n; clamp de fim
             // de mês d = min(d, daysInMonth). Pré-guarda t em [12,119999] (divisão
             // positiva, idêntica). Inválida/out-of-range => "" (política addDays).
              export function kofTimeAddMonths(iso, months) {
                  const a = kofTimeParseIso(iso);
                  if (!a) return "";
                  const t = a.y * 12 + (a.m - 1) + months;
                  if (t < 12 || t > 119999) return "";
                  const y1 = Math.floor(t / 12);
                  const m1 = (t % 12) + 1;
                  const d1 = Math.min(a.d, kofTimeDaysInMonth(y1, m1));
                  return kofTimePad4(y1) + "-" + kofTimePad2(m1) + "-" + kofTimePad2(d1);
              }
            export function kofTimeAddYears(iso, years) {
                const a = kofTimeParseIso(iso);
                if (!a) return "";
                const y1 = a.y + years;
                if (y1 < 1 || y1 > 9999) return "";
                const m1 = a.m;
                const d1 = Math.min(a.d, kofTimeDaysInMonth(y1, m1));
                return kofTimePad4(y1) + "-" + kofTimePad2(m1) + "-" + kofTimePad2(d1);
            }
            // S7a-ext3 (STDLIB front #1): inicio/fim do periodo de uma data ISO.
            // unit = day|week|month|year; semana = segunda..domingo (dayOfWeek ISO
            // 1=seg..7=dom). Mesma aritmetica inteira dos demais alvos => os 5
            // backends sao byte-identicos por construcao. Invalida/unit desconhecida
            // / fora de 1..9999 => "".
            function kofTimeBoundOf(iso, unit, start) {
                const a = kofTimeParseIso(iso);
                if (!a) return "";
                let y1 = a.y, m1 = a.m, d1 = a.d;
                if (unit === "day") {
                    // identity
                } else if (unit === "week") {
                    const dow = kofTimeDayOfWeek(a.y, a.m, a.d);
                    const delta = start ? 1 - dow : 7 - dow;
                    const c = kofTimeCivilFromEpochDay(kofTimeEpochDay(a.y, a.m, a.d) + delta);
                    if (c[0] < 1 || c[0] > 9999) return "";
                    y1 = c[0]; m1 = c[1]; d1 = c[2];
                } else if (unit === "month") {
                    d1 = start ? 1 : kofTimeDaysInMonth(a.y, a.m);
                } else if (unit === "year") {
                    m1 = start ? 1 : 12;
                    d1 = start ? 1 : 31;
                } else {
                    return "";
                }
                if (!kofTimeValidDate(y1, m1, d1)) return "";
                return kofTimePad4(y1) + "-" + kofTimePad2(m1) + "-" + kofTimePad2(d1);
            }
            export function kofTimeStartOf(iso, unit) {
                return kofTimeBoundOf(iso, unit, true);
            }
            export function kofTimeEndOf(iso, unit) {
                return kofTimeBoundOf(iso, unit, false);
            }
            export function kofTimeDiffDays(iso1, iso2) {
                const a = kofTimeParseIso(iso1);
                const b = kofTimeParseIso(iso2);
                if (!a || !b) return 0;
                return kofTimeEpochDay(b.y, b.m, b.d) - kofTimeEpochDay(a.y, a.m, a.d);
            }
            """;
    }

    static String timeRuntime() {
        return """
            // §132/#83-JS: sleep yields to the cooperative scheduler instead of
            // busy-waiting the single JS thread, so concurrent spawned/async tasks
            // advance WHILE a task sleeps. node/browser: the real event loop
            // (setTimeout) resolves it. GraalJS (no event loop — same absence that
            // forces kofTimeInterval onto the cooperative queue): a host pump
            // (KofJsRunner) resolves due sleepers via __kofSleepStep. time.now()/
            // Date.now() stay the REAL clock (no cross-cutting clock-model change).
            const __kofSleepers = [];
            export function kofTimeSleep(ms) {
                const wait = (typeof ms === "number" && ms > 0) ? ms : 0;
                if (typeof setTimeout === "function") {
                    return new Promise(res => setTimeout(res, wait));
                }
                return new Promise(res => __kofSleepers.push({ at: Date.now() + wait, res: res }));
            }
            // Host pump hook (GraalJS only): fire interval jobs (kofTimePump), resolve
            // due sleepers, and return the epoch-ms deadline of the earliest pending
            // sleeper, or -1 when none. The host drains microtasks after this via an
            // eval, sleeps toward the returned deadline, and stops when it is -1 and
            // no spawned tasks remain — i.e. a minimal host-side event loop.
            globalThis.__kofSleepStep = function () {
                if (typeof kofTimePump === "function") { kofTimePump(); }
                const now = Date.now();
                const due = __kofSleepers.filter(s => s.at <= now);
                for (let i = __kofSleepers.length - 1; i >= 0; i--) {
                    if (__kofSleepers[i].at <= now) __kofSleepers.splice(i, 1);
                }
                for (const s of due) { s.res(); }
                let next = -1;
                for (const s of __kofSleepers) { if (next < 0 || s.at < next) next = s.at; }
                return next;
            };

            // ── Cooperative timers (TIME001 fechado): GraalJS não tem
            // event loop nativo nem setInterval, então os jobs vivem numa
            // fila bombeada por kofTimeSleep (que já bloqueia). Em browser/
            // Node, onde setInterval existe, os timers disparam assíncronos.
            const kofTimeJobs = new Map();
            const kofTimeSeq = { value: 0 };
            function kofTimeRunJob(fn) {
                if (typeof fn.invoke === 'function') fn.invoke();
                else if (typeof fn === 'function') fn();
            }
            export function kofTimeInterval(ms, fn) {
                if (typeof setInterval === 'function') {
                    return "n" + String(setInterval(() => kofTimeRunJob(fn), ms));
                }
                const id = "c" + (++kofTimeSeq.value);
                kofTimeJobs.set(id, { ms: ms, run: () => kofTimeRunJob(fn), next: Date.now() + ms });
                return id;
            }
            function kofTimePump() {
                const now = Date.now();
                for (const [id, job] of kofTimeJobs) {
                    if (now >= job.next) {
                        if (job.dur) {
                            // Duração (D-SCHED-DURATION): âncora avança do
                            // disparo anterior — sem drift; atraso do host
                            // salta em passos inteiros (sem rajada).
                            let anchor = job.next;
                            let next = kofDurationNextFrom(job.dur, anchor);
                            while (next <= now) {
                                anchor = next;
                                next = kofDurationNextFrom(job.dur, anchor);
                            }
                            job.anchor = anchor;
                            job.next = next;
                        } else {
                            job.next = now + (job.cron ? kofCronNextDelayMs(job.cron, now) : job.ms);
                        }
                        job.run();
                    }
                }
            }

            export function kofTimeCancel(id) {
                const key = String(id);
                if (key.charAt(0) === "n") {
                    if (typeof clearInterval === 'function') clearInterval(Number(key.substring(1)));
                    return;
                }
                kofTimeJobs.delete(key);
            }

            // §426 (improved 25/09): time.collect() (manual GC). Previously
            // imported but never exported -> clean compile + module-load
            // failure; the fix is a REAL host request, not a no-op stub (R6).
            // The KofJsRunner host exposes kof_platform.gcCollect()
            // (System.gc(), the exact JVM semantics used by JVM/SCRIPT). A
            // hostless runtime (browser) has no GC control -> honest error,
            // never a silent success (R7).
            export function kofGcCollectNow() {
                const host = globalThis.kof_platform;
                if (host) {
                    host.gcCollect();
                    return;
                }
                throw new Error("kof.time: collect() is not available in this host (no GC control)");
            }
            """;
    }
}
