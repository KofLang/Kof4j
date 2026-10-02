package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-STR-UNICODE (linha 11) — medição comportamental das faces Unicode
 * ({@code toUpperCase}/{@code toLowerCase}/{@code compareToIgnoreCase}/
 * {@code strings.reverse} não-ASCII) contra o oráculo JVM. Os valores são
 * MEDIDOS neste tip (27/09), não deduzidos: a bateria imprime unidades UTF-16
 * via a face {@code toCharArray} (a única face char provada — não depende de
 * encoding de stdout).
 *
 * <p>Medido: <b>JS == oráculo JVM nas 9 linhas + 5 comparações</b>; o native
 * (D-STR-UNICODE) == oráculo nas faces de caixa SIMPLES e em
 * {@code compareToIgnoreCase} (o JVM usa {@code Character.toUpperCase/
 * toLowerCase}, mapeamento SIMPLES por code unit — JVM/JS/native/Script
 * concordam), divergindo apenas nas 4 linhas de mapeamento COMPLETO/locale-aware
 * ({@code ß->SS}, {@code İ->i+U+0307}, {@code ǰ->J+caron}, sigma final
 * {@code Σ->ς}) fora do escopo ratificado. reverse com surrogate pair já é
 * code-point no x86. Este arquivo é CARACTERIZAÇÃO: trava o oráculo (JVM/JS) e
 * o estado nativo medido (simple-fold + CIC == oráculo). Padrão: StringGapMeasuredTest/§424.
 */
class StringUnicodeFacesMeasuredTest {

    private final CompilerDriver driver = new CompilerDriver();

    // Unidades UTF-16 impressas por linha (length,c0,c1,...) + as 5 comparações.
    private static final String ORACLE_JVM = """
        4,98,55357,56832,97
        4,55357,56832,225,8364
        5,72,201,76,76,79
        6,103,114,252,223,101,110
        7,83,84,82,65,83,83,69
        2,105,775
        2,74,780
        2,913,931
        5,959,32,948,965,962""";

    // compareToIgnoreCase (CASE_INSENSITIVE_ORDER do JVM): fold SIMPLES por
    // code unit — o mesmo resultado em JVM, JS e native (D-FULL-PARITY-050).
    private static final String ORACLE_CIC = "108\n0\n0\n390\n0\n7554\n-1\n0\n0\n64155\n-1\n0";

    // Estado medido do native x86_64 (D-STR-UNICODE landed): reverse code-point OK
    // + caixa por code unit (simple-fold). Diverge do oráculo JVM/JS nas linhas
    // 5/6/7/9 (mapeamentos completos: ß->SS, İ->i+U+0307, ǰ->J+caron, Σ->ς).
    private static final String NATIVE_SIMPLE_FOLD = """
        4,98,55357,56832,97
        4,55357,56832,225,8364
        5,72,201,76,76,79
        6,103,114,252,223,101,110
        6,83,84,82,65,223,69
        1,105
        1,496
        2,913,931
        5,959,32,948,965,963""";

    private static final String BATTERY = """
        String Units(String s) {
            var a = s.toCharArray()
            var r = "" + (a.length as Int)
            for (var i = 0; i < a.length; i++) {
                r = r + "," + (a[i] as Int)
            }
            return r
        }
        main() {
            println(Units(strings.reverse("a😀b")))
            println(Units(strings.reverse("€á😀")))
            println(Units("héllo".toUpperCase()))
            println(Units("GRÜẞEN".toLowerCase()))
            println(Units("straße".toUpperCase()))
            println(Units("İ".toLowerCase()))
            println(Units("ǰ".toUpperCase()))
            println(Units("ΑΣ".toUpperCase()))
            println(Units("Ο ΔΥΣ".toLowerCase()))
        }
        """;

    private static final String CIC_SRC = """
        main() {
            println("straße".compareToIgnoreCase("STRASSE"))
            println("İ".compareToIgnoreCase("i"))
            println("Hello".compareToIgnoreCase("hello"))
            println("ǰ".compareToIgnoreCase("J̌"))
            println("Σ".compareToIgnoreCase("σ"))
            println("ẛ".compareToIgnoreCase("ẞ"))
            println("abc".compareToIgnoreCase("abcd"))
            println("".compareToIgnoreCase(""))
            println("ǅ".compareToIgnoreCase("ǆ"))
            println("ﬁ".compareToIgnoreCase("fi"))
            println("😀x".compareToIgnoreCase("😀Y"))
            println("ÉCOLE".compareToIgnoreCase("école"))
        }
        """;

    @Test
    @DisplayName("linha 11: oráculo JVM medido (caixa/reverse Unicode + compareToIgnoreCase)")
    void jvmOracleMeasured(@TempDir Path tmp) throws Exception {
        assertEquals(ORACLE_JVM, run(tmp, BATTERY, Target.JVM).trim());
        assertEquals(ORACLE_CIC, run(tmp, CIC_SRC, Target.JVM).trim());
    }

    @Test
    @DisplayName("linha 11: JS == oráculo JVM (built-ins pinados — D-STR-UNICODE)")
    void jsMatchesOracle(@TempDir Path tmp) throws Exception {
        assertEquals(ORACLE_JVM, run(tmp, BATTERY, Target.JS).trim());
    }

    @Test
    @DisplayName("linha 11: native x86 = reverse + caixa por code unit (simple-fold; mapeamentos completos fora do escopo)")
    void nativeCharacterization(@TempDir Path tmp) throws Exception {
        assertEquals(NATIVE_SIMPLE_FOLD, run(tmp, BATTERY, Target.NATIVE).trim());
    }

    @Test
    @DisplayName("linha 11: compareToIgnoreCase == oráculo JVM em JVM/JS/native/Script")
    void cicMatchesJvm(@TempDir Path tmp) throws Exception {
        assertEquals(ORACLE_CIC, run(tmp, CIC_SRC, Target.JVM).trim());
        assertEquals(ORACLE_CIC, run(tmp, CIC_SRC, Target.JS).trim());
        assertEquals(ORACLE_CIC, run(tmp, CIC_SRC, Target.NATIVE).trim());
        Path sfile = tmp.resolve("CIC-script-" + System.nanoTime() + ".kf");
        Files.writeString(sfile, CIC_SRC);
        var sr = driver.interpret(java.util.List.of(sfile), tmp, new String[0]);
        assertEquals(0, sr.exitCode(), sr.stdout());
        assertEquals(ORACLE_CIC, sr.stdout().replace("\r\n", "\n").trim());
    }

    private String run(Path tmp, String source, Target t) throws Exception {
        Path file = tmp.resolve("M-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path out = tmp.resolve("o-" + t + "-" + System.nanoTime());
        var r = driver.compile(file, out, t);
        assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
        if (t == Target.JVM) {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), o);
            return o.replace("\r\n", "\n");
        }
        if (t == Target.NATIVE) {
            Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                    .redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), o);
            return o.replace("\r\n", "\n");
        }
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
             java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream()) {
            Path entry;
            try (var w = Files.walk(out)) {
                entry = w.filter(p -> p.getFileName().toString().equals("Default.mjs"))
                        .findFirst().orElseThrow();
            }
            int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                    java.io.InputStream.nullInputStream(), err);
            assertEquals(0, ec, buf + " " + err);
            return buf.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
