package dev.kof.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Paridade interpretador (Target.SCRIPT) × JVM compilado da stdlib nova da
 * lane STDLIB (S10/S11/S12/S12b/S3b-ext/S7-ext). O interpretador resolve
 * kof_* por reflexão no MESMO KofRuntime gerado (KofInterpreter javadoc:
 * "paridade por construção, não por reimplementação") — este teste PROVA a
 * afirmação nos alvos recém-adicionados, onde regressão seria silenciosa
 * (R5 cross-target). Funções não-determinísticas (random) entram pela
 * FACHADA (saída formatada), não pelo valor sorteado.
 */
class KofScriptStdlibParityTest {

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    private void parity(String src, String expected) throws Exception {
        Path tmp = Files.createTempDirectory("kofparity");
        try {
            Path f = tmp.resolve("P-" + System.nanoTime() + ".kf");
            Files.writeString(f, src);
            var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
            assertTrue(script.success(), "SCRIPT: " + script.stderr());
            assertEquals(expected, norm(script.stdout()), "SCRIPT stdout");
            var comp = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
            assertTrue(comp.success(), "JVM: " + comp.stderr());
            assertEquals(expected, norm(comp.stdout()),
                    "paridade interpretado vs compilado JVM (R5)");
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void mapGetOrDefaultParity() throws Exception {
        // #386 slice 1: interpreter and compiled JVM must agree on hit,
        // primitive-miss default and reference-miss default (R5).
        parity("""
            main() {
                val m: Map<String, Int> = mapOf()
                m.put("a", 1)
                println(m.getOrDefault("a", 7))
                println(m.getOrDefault("b", 0))
                val s: Map<String, String> = mapOf()
                println(s.getOrDefault("k", "fb"))
            }
            """, "1\n0\nfb");
    }

    @Test
    void paginationSliceParity() throws Exception {
        // pagination P1 — take/drop/slice: interpretador e JVM concordam
        // (clamping honesto + erro nomeado; o E2E cobre JVM/JS/native).
        parity("""
            main() {
                val l: List<Int> = listOf(10, 20, 30, 40, 50)
                println(l.take(2).size)
                println(l.take(0).size)
                println(l.take(9).size)
                println(l.take(2).get(1))
                println(l.drop(2).size)
                println(l.drop(9).size)
                println(l.drop(2).get(0))
                val a = l.slice(1, 2)
                println(a.size)
                println(a.get(1))
                println(l.slice(4, 10).size)
                println(l.slice(9, 3).size)
                println(l.slice(0, 0).size)
                val t = l.take(2)
                t.add(99)
                println(l.size)
                val s: List<String> = listOf("a", "b", "c")
                println(s.take(2).get(1))
                println(s.drop(1).get(0))
                println(s.slice(1, 1).get(0))
            }
            """, "2\n0\n5\n20\n3\n0\n30\n2\n30\n1\n0\n0\n5\nb\nb\nb");
    }

    @Test
    void quantifiersParity() throws Exception {
        // D-MULTIPARADIGMA-PHASE1A slice 1a — any/all/none: interpretador e
        // JVM concordam (vácuos + match; println de Bool rende igual nos dois
        // lados, medido; o E2E cobre JVM/JS/native/cross + short-circuit).
        parity("""
            main() {
                var xs = listOf(1, 2, 3)
                var empty = listOf()
                println(xs.any((x) -> x > 2))
                println(xs.all((x) -> x > 0))
                println(xs.none((x) -> x > 9))
                println(empty.any((x) -> true))
                println(empty.all((x) -> false))
                println(empty.none((x) -> true))
            }
            """, "true\ntrue\ntrue\nfalse\ntrue\ntrue");
    }

    @Test
    void findCountParity() throws Exception {
        // D-MULTIPARADIGMA-PHASE1A slice 1b — find/count: interpretador e JVM
        // concordam (match/valor, ausência, contagens, count nu).
        parity("""
            main() {
                var xs = listOf(1, 2, 3)
                println(xs.find((x) -> x > 1))
                println(xs.find((x) -> x > 9))
                println(xs.count((x) -> x > 1))
                println(xs.count())
            }
            """, "2\nnull\n2\n3");
    }

    @Test
    void forEachParity() throws Exception {
        // D-MULTIPARADIGMA-PHASE1A slice 1c — forEach: efeito + vácuo.
        parity("""
            main() {
                var xs = listOf(1, 2, 3)
                xs.forEach((x) -> println(x * 10))
                listOf().forEach((x) -> println("never"))
                println("done")
            }
            """, "10\n20\n30\ndone");
    }

    @Test
    void flatMapParity() throws Exception {
        // D-MULTIPARADIGMA-PHASE1A slice 1d — flatMap concatena em ordem.
        parity("""
            main() {
                var xs = listOf(1, 2, 3)
                var ys = xs.flatMap((x) -> listOf(x, x * 10))
                println(ys.size)
                println(ys.get(0))
                println(ys.get(5))
            }
            """, "6\n1\n30");
    }

    @Test
    void distinctParity() throws Exception {
        // D-MULTIPARADIGMA-PHASE1A slice 1e — distinct primeira-ocorrência.
        parity("""
            main() {
                var xs = listOf(3, 1, 2, 1, 3)
                var d = xs.distinct()
                println(d.size)
                println(d.get(0))
                println(d.get(2))
            }
            """, "3\n3\n2");
    }

    // #386 slice 2 + #382: containsValue/putIfAbsent e a família de List
    // (indexOf/lastIndexOf/subList/addAll/sort) — interpretador e JVM
    // compilado concordam com o oráculo medido (java.util no dois lados).
    @Test
    void collectionMethodsParity() throws Exception {
        parity("""
            main() {
                val m: Map<String, Int> = mapOf()
                m.put("a", 1)
                println(m.containsValue(1))
                println(m.containsValue(9))
                println(m.putIfAbsent("a", 5))
                println(m.getOrDefault("a", 0))
                println(m.putIfAbsent("b", 2) == null)
                println(m.containsValue(2))
                val l: List<Int> = listOf(3, 1, 2, 1)
                println(l.indexOf(1))
                println(l.indexOf(99))
                println(l.lastIndexOf(1))
                println(l.subList(1, 3).size)
                println(l.subList(2, 2).size)
                val b: List<Int> = listOf()
                println(b.addAll(l))
                println(b.addAll(listOf<Int>()))
                val rev: List<Int> = listOf(5, 4, 3, 2, 1)
                rev.sort()
                println(rev.get(0))
                println(rev.get(4))
                val str: List<String> = listOf("pear", "apple")
                str.sort()
                println(str.get(0))
            }
            """, "true\nfalse\n1\n1\ntrue\ntrue\n1\n-1\n3\n2\n0\ntrue\nfalse\n1\n5\napple");
    }

    @Test
    void uncapitalizeParity() throws Exception {
        parity("""
            main() {
                println(strings.uncapitalize("Hello World"))
                println(strings.uncapitalize("HELLO"))
                println(strings.uncapitalize("1abc"))
                println(strings.uncapitalize("") + "|")
            }
            """, "hello World\nhELLO\n1abc\n|");
    }

    @Test
    void formatDocumentsParity() throws Exception {
        parity("""
            main() {
                println(validation.formatCpf("52998224725"))
                println(validation.formatCpf("123") + "|")
                println(validation.formatCep("01310100"))
                println(validation.formatCnpj("34546401000163"))
                println(validation.formatCnpj("123") + "|")
            }
            """, "529.982.247-25\n123|\n01310-100\n34.546.401/0001-63\n123|");
    }

    // STDLIB S13a (plan-stdlib-expansion §2, P0): math.parseInt/parseLong/
    // parseDouble — fachada sobre kof_string_to_* (o interpretador resolve
    // por reflexão no MESMO KofRuntime; paridade por construção). Golden
    // igual ao KofMathTest/ConformanceMatrixTest (fonte única de verdade);
    // erro de parse lança (contrato JDK com trim). Long > 2^53 prova Long
    // real; Double via == Bool (bug 44).
    @Test
    void mathParseParity() throws Exception {
        parity("""
            main() {
                println(math.parseInt("42"))
                println(math.parseInt(" -7 "))
                println(math.parseInt("+13"))
                println(math.parseInt("0"))
                println(math.parseInt("-2147483648"))
                println(math.parseLong("9007199254740993"))
                println(math.parseLong("-9223372036854775807"))
                println(math.parseDouble("2.5") == 2.5)
                println(math.parseDouble("  -0.25 ") == -0.25)
                println(math.parseDouble("1e2") == 100.0)
                try { println(math.parseInt("abc")); println("S1") } catch (String e) { println("T1") }
                try { println(math.parseInt("12a34")); println("S2") } catch (String e) { println("T2") }
                try { println(math.parseInt("2147483648")); println("S3") } catch (String e) { println("T3") }
                try { println(math.parseInt("")); println("S4") } catch (String e) { println("T4") }
                try { println(math.parseLong("9223372036854775808")); println("S5") } catch (String e) { println("T5") }
            }
            """, "42\n-7\n13\n0\n-2147483648\n9007199254740993\n-9223372036854775807\ntrue\ntrue\ntrue\nT1\nT2\nT3\nT4\nT5");
    }

    // STDLIB S13b (plan-stdlib-expansion §2, P0): parse com default (§43 —
    // falha DEVOLVE o default, nunca lança). Paridade interpretador×JVM;
    // Linhas ""/"   " do Double INCLUÍDAS pós-§175 (vazio lançava 0.0 no
    // Native — paridade do parse base fechada).
    @Test
    void mathParseOrDefaultParity() throws Exception {
        parity("""
            main() {
                println(math.parseIntOrDefault("42", 0))
                println(math.parseIntOrDefault("abc", -1))
                println(math.parseIntOrDefault("", 7))
                println(math.parseIntOrDefault("  15  ", 0))
                println(math.parseIntOrDefault("99999999999999", 3))
                println(math.parseLongOrDefault("9007199254740993", 0))
                println(math.parseLongOrDefault("x", -5))
                println(math.parseLongOrDefault("9223372036854775808", 8))
                println(math.parseDoubleOrDefault("2.5", 0.0) == 2.5)
                println(math.parseDoubleOrDefault("nope", -0.5) == -0.5)
                println(math.parseDoubleOrDefault("1e2", 0.0) == 100.0)
                println(math.parseDoubleOrDefault("", 1.5) == 1.5)
                println(math.parseDoubleOrDefault("   ", -0.25) == -0.25)
            }
            """, "42\n-1\n7\n15\n3\n9007199254740993\n-5\n8\ntrue\ntrue\ntrue\ntrue\ntrue");
    }

    // STDLIB S1b.3 (DECISIONS §3): math.roundTo(value: Double, decimals: Int)
    // — half-away-from-zero por escala decimal determinística. O interpretador
    // resolve kof_math_roundTo por reflexão no MESMO KofRuntime do JVM
    // (paridade por construção). Bool via == (bug 44: nunca println de double
    // cru). Golden = fonte única (KofMathTest/ConformanceMatrixTest).
    @Test
    void mathRoundToParity() throws Exception {
        parity("""
            main() {
                println(math.roundTo(2.5, 0) == 3.0)
                println(math.roundTo(-2.5, 0) == -3.0)
                println(math.roundTo(2.4, 0) == 2.0)
                println(math.roundTo(0.49999999999999994, 0) == 0.0)
                println(math.roundTo(3.14159, 2) == 3.14)
                println(math.roundTo(2.675, 2) == 2.68)
                println(math.roundTo(1234.0, -2) == 1200.0)
                println(math.roundTo(-1250.0, -2) == -1300.0)
                println(math.roundTo(1.0, 0) == 1.0)
                println(math.roundTo(0.0, 5) == 0.0)
            }
            """, "true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue");
    }

    @Test
    void isUuidParity() throws Exception {
        parity("""
            main() {
                println(uuid.isUuid("550e8400-e29b-41d4-a716-446655440000"))
                println(uuid.isUuid("550E8400-E29B-41D4-A716-446655440000"))
                println(uuid.isUuid("550e8400e29b41d4a716446655440000"))
                println(uuid.isUuid("550e8400-e29b-41d4-a716-44665544000g"))
                println(uuid.isUuid(uuid.v4()))
            }
            """, "true\ntrue\nfalse\nfalse\ntrue");
    }

    @Test
    void isWeekendParity() throws Exception {
        parity("""
            main() {
                println(time.isWeekend(2026, 9, 12))
                println(time.isWeekend(2026, 9, 13))
                println(time.isWeekend(2026, 9, 9))
                println(time.isWeekend(2026, 2, 30))
                println(time.isWeekend(2024, 2, 25))
            }
            """, "true\ntrue\nfalse\nfalse\ntrue");
    }

    @Test
    void timeHoursBetweenParity() throws Exception {
        // S7f (D3): floor simétrico; datas inválidas/hora fora 0..23 => 0.
        parity("""
            main() {
                println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
            }
            """, "26\n-26\n23\n1\n0\n0\n0\n24");
    }

    @Test
    void timeTodayParity() throws Exception {
        // S7e (D-STDLIB): todayIso por FORMATO (o dia vira — nunca valor
        // literal); formatDateIso/isToday determinísticos.
        parity("""
            main() {
                var today = time.todayIso()
                println(today.length)
                println(time.formatDateIso(2026, 9, 13))
                println(time.formatDateIso(2024, 2, 29))
                println(time.formatDateIso(2023, 2, 29))
                println(time.formatDateIso(0, 1, 1))
                println(time.formatDateIso(2026, 13, 1))
                println(time.isToday(2026, 9, 12))
                println(time.isToday(2026, 2, 30))
                var parts = today.split("-")
                println(parts.size)
                println(parts[0].length)
                println(parts[1].length)
            }
            """, "10\n2026-09-13\n2024-02-29\n\n\n\nfalse\nfalse\n3\n4\n2");
    }

    @Test
    void timeParseDateIsoParity() throws Exception {
        // S7g (D4): parse estrito; inválido => 0; serial = daysFromEpoch.
        parity("""
            main() {
                println(time.parseDateIso("1970-01-01"))
                println(time.parseDateIso("2026-09-13"))
                println(time.parseDateIso("2024-02-29"))
                println(time.parseDateIso("0001-01-01"))
                println(time.parseDateIso("9999-12-31"))
                println(time.parseDateIso("2023-02-29"))
                println(time.parseDateIso("garbage"))
                println(time.parseDateIso(""))
            }
            """, "0\n20709\n19782\n-719162\n2932896\n0\n0\n0");
    }

    @Test
    void timeTzOffsetParity() throws Exception {
        // S7h (D1): fuso do HOST; SCRIPT herda o runtime JVM => MESMO valor
        // (oracle ZoneId, medição real). Contrato: mód 60 == 0, range.
        int tzNow = java.time.ZoneId.systemDefault().getRules()
                .getOffset(java.time.Instant.now()).getTotalSeconds();
        parity("""
            main() {
                var tz = time.tzOffsetSeconds()
                println(tz % 60)
                println(tz >= -43200 && tz <= 50400)
                println(tz)
            }
            """, "0\ntrue\n" + tzNow);
    }

    @Test
    void randomFacadeParity() throws Exception {
        // Não-determinístico: valida a FACHADA (formato/contrato), não o
        // valor sorteado. randomInt(1) == 0 travado; randomString(-1) == "";
        // randomBoolean() ∈ {true,false}; randomString(8, "ab") é 8 chars
        // de {a,b}.
        parity("""
            main() {
                println(random.randomInt(1))
                println(random.randomString(-1, "abc") + "|")
                println(random.randomString(0, "abc") + "|")
                println(random.randomString(4, "") + "|")
                var b = random.randomBoolean()
                println(b || !b)
                var s = random.randomString(8, "ab")
                println(s.length)
                println(s == s)
            }
            """, "0\n|\n|\n|\ntrue\n8\ntrue");
    }

    @Test
    void indentDedentParity() throws Exception {
        parity("""
            main() {
                println("---")
                println(strings.indent("a\\nb", 2))
                println(strings.indent("x", 0))
                println(strings.dedent("  a\\n    b"))
                println(strings.dedent("hello"))
            }
            """, "---\n  a\n  b\nx\na\n  b\nhello");
    }

    private static void deleteRecursively(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignore) {}
            });
        } catch (Exception ignore) {}
    }

    /** 8.5 (corpus stdlib.md, 19/09): as faces cache/net/config interpretam
     *  igual ao JVM compilado (mesma golden nos dois caminhos). */
    @Test
    void cacheNetConfigFacesParity() throws Exception {
        parity("main() { cache.set(\"k\", \"v\"); println(cache.get(\"k\") + \"|\" + cache.ttl(\"k\")) }\n", "v|-1");
        parity("main() { val u = \"https://x.io:8443/a/b?p=1\"; println(net.host(u) + \"|\" + net.port(u) + \"|\" + net.path(u) + \"|\" + net.query(u)) }\n", "x.io|8443|/a/b|p=1");
        parity("main() { println(config.str(\"missing.key\", \"fallback\")) }\n", "fallback");
    }
}