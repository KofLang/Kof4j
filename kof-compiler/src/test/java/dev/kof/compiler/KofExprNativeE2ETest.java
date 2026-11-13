package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kof.expr slice 6 (`D-KOF-EXPR`) — the NATIVE face of the matcher: the SAME
 * compiled chain of states runs on the native backend, where the plan's §9
 * parity becomes a fact instead of a promise.
 *
 * <p>Why native was the elected front (MEASURED, not estimated): the native
 * backend is a 1:1 op→instruction emitter and every operation the matcher
 * emits is DECLARED in {@code NativeOpSupport.X86_64} / {@code RISCV64_CROSS}
 * ({@code KofLabel}, {@code KofJump}, {@code KofConditionalJump},
 * {@code KofCall}, {@code KofNewArray}, {@code KofArrayStore},
 * {@code KofArrayLoad}, {@code KofNewObject}, {@code KofLoadField},
 * {@code KofStoreField}, {@code KofThrow}, {@code KofDup}) — so the chain
 * needs no parser work, only the runtime predicates and the code-point read.
 *
 * <p>The oracle is the JVM: the same source is compiled twice and the two
 * stdouts must be byte-identical. A native face that merely COMPILES is not
 * parity; parity is the same answers.
 */
class KofExprNativeE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void theCorpusAnswersIdenticallyOnNativeAndJvm() throws Exception {
        String src = """
                expr identifier { one_or_more { either { letter or digit or "_" } } }
                expr uuid {
                    exactly(8) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(12) hex
                }
                expr phone {
                    optional("+55")
                    exactly(2) digit
                    "-"
                    exactly(4) digit
                }
                expr date {
                    capture("day") { exactly(2) digit }
                    "/"
                    capture("month") { exactly(2) digit }
                }

                main() {
                    println(identifier.matches("user_name_2"))
                    println(identifier.matches("user-2"))
                    println(uuid.matches("deadBEEF-1234-abcd-AAAA-0123456789ab"))
                    println(uuid.matches("geadBEEF-1234-abcd-AAAA-0123456789ab"))
                    println(phone.matches("+5511-9876"))
                    println(phone.matches("1198765432"))
                    var m = date.match("25/12")
                    if (m != null) {
                        var d = m.day
                        if (d != null) { println(d) }
                        var mo = m.month
                        if (mo != null) { println(mo) }
                    } else {
                        println("none")
                    }
                }
                """;
        String expected = "true\nfalse\ntrue\nfalse\ntrue\nfalse\n25\n12\n";
        assertEquals(expected, runJvm("corpus", src), "the JVM oracle changed — review the case");
        assertEquals(expected, runNative("corpus", src), "the native face must answer like the JVM");
    }

    @Test
    void thePinnedUnicodeLetterIsALetterOnNativeToo() throws Exception {
        // U+1C89 (KAKTOVIK NUMERAL) is a LETTER only since Unicode 16.0 — the
        // table the native face carries is PINNED to the same version the JVM
        // face measures on this JDK.
        String src = """
                expr name { one_or_more { letter } }

                main() {
                    println(name.matches("Kof"))
                    println(name.matches("Ωμέγα"))
                    println(name.matches("ᲉᲉ"))
                    println(name.matches("Kof4j"))
                }
                """;
        String expected = "true\ntrue\ntrue\nfalse\n";
        assertEquals(expected, runJvm("pinned", src));
        assertEquals(expected, runNative("pinned", src),
                "the native table must be the SAME pinned Unicode 16.0.0 the JVM asserts");
    }

    @Test
    void theSearchOperationsAnswerIdenticallyOnNative() throws Exception {
        String src = """
                expr word { one_or_more { either { letter or digit } } }

                main() {
                    println(word.find("abc 123"))
                    println(word.findAll("a1 b2 c3"))
                    println(word.replace("a1 b2", "X"))
                }
                """;
        String expected = runJvm("search", src);
        assertEquals(expected, runNative("search", src));
    }

    @Test
    void theSubjectBudgetIsRefusedOnNativeWithItsOwnMessage() throws Exception {
        // A recusa do §12 é POR-ALVO: no nativo o `throw` é uma String (o
        // runtime nativo não tem java.lang.RuntimeException) — a mensagem do
        // orçamento tem de chegar ao programa, não um link-fail mudo.
        StringBuilder subject = new StringBuilder();
        for (int i = 0; i < 2100; i++) subject.append('a');
        String src = "expr word { one_or_more { letter } }\n"
                + "\n"
                + "main() {\n"
                + "    println(word.find(\"" + subject + "\"))\n"
                + "}\n";
        Path file = tmp.resolve("budget").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve("budget-out");
        CompilationResult r = driver.compile(file, out, Target.NATIVE);
        assertTrue(r.success(), "budget (native): " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String outText = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        p.waitFor();
        assertTrue(outText.contains("EXPR-E2"), () -> "the budget message must reach the program: " + outText);
        assertTrue(outText.contains("2,000"), () -> "the message names the bound: " + outText);
    }

    @Test
    void thePinnedTableAgreesWithTheJvmAcrossTheCodePointSpace() throws Exception {
        // A tabela pinada é DADO nos dois backends nativos e na JVM — a prova
        // forte é o ACORDO ponto a ponto: o JVM decide via Character (medido
        // neste JDK) e o nativo via a tabela; qualquer divergência aparece.
        // A amostra cobre as bordas (BMP, o plano de valores Unicode, as
        // pontas dos intervalos da tabela) e um passo regular no plano alto.
        // O programa é quebrado em BLOCOS (o `main` só chama os blocos): um
        // `main` com 1.400+ statements estoura o limite de método da JVM.
        java.util.List<int[]> blocks = new java.util.ArrayList<>();
        int blockSize = 60;
        int[] sample = SAMPLE;
        for (int from = 0; from < sample.length; from += blockSize) {
            blocks.add(java.util.Arrays.copyOfRange(sample, from,
                    Math.min(sample.length, from + blockSize)));
        }
        StringBuilder src = new StringBuilder();
        src.append("expr l { one_or_more { letter } }\n");
        src.append("expr d { one_or_more { digit } }\n");
        src.append("expr s { one_or_more { whitespace } }\n");
        src.append("expr w { one_or_more { word } }\n");
        src.append("expr h { exactly(1) hex }\n");
        src.append("expr a { exactly(1) any }\n");
        for (int b = 0; b < blocks.size(); b++) {
            src.append("block").append(b).append("() {\n");
            for (int cp : blocks.get(b)) {
                String lit = codePointLiteral(cp);
                src.append("    println(l.matches(\"").append(lit).append("\"))\n");
                src.append("    println(d.matches(\"").append(lit).append("\"))\n");
                src.append("    println(s.matches(\"").append(lit).append("\"))\n");
                src.append("    println(w.matches(\"").append(lit).append("\"))\n");
                src.append("    println(h.matches(\"").append(lit).append("\"))\n");
                src.append("    println(a.matches(\"").append(lit).append("\"))\n");
            }
            src.append("}\n");
        }
        src.append("main() {\n");
        for (int b = 0; b < blocks.size(); b++) {
            src.append("    block").append(b).append("()\n");
        }
        src.append("}\n");
        String expected = runJvm("parity-table", src.toString());
        assertEquals(expected, runNative("parity-table", src.toString()),
                "the native table must agree with the JVM point by point");
    }

    /**
     * O literal do ponto de código: o par UTF-16 COMPLETO quando o escalar
     * está fora do BMP (o modelo do §10 — um caractere fora do BMP é UM
     * `any`, que ocupa DUAS unidades UTF-16). Truncar para um `char` só
     * produziria um surrogate solto (que não tem representação no modelo de
     * string).
     */
    private static String codePointLiteral(int cp) {
        return new String(Character.toChars(cp));
    }

    /** A amostra: as bordas + as pontas dos intervalos da tabela pinada. */
    private static final int[] SAMPLE = buildSample();

    private static int[] buildSample() {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        int[] edges = {0, 0x09, 0x0A, 0x20, 0x30, 0x39, 0x41, 0x5A, 0x5F, 0x61, 0x7A,
                0x7F, 0x80, 0xAA, 0xBA, 0xC0, 0xD6, 0xD8, 0xF6, 0x1680, 0x1C89, 0x1FAE8,
                0x2028, 0x3000, 0x10D40, 0x1E030, 0x20000, 0x2A6DF, 0x3134A, 0x323AF,
                0xD7A3, 0xE000, 0xFFFF, 0x10000};
        for (int cp : edges) set.add(cp);
        // as pontas de CADA intervalo da tabela de letra: o limite exato é
        // onde um off-by-one apareceria.
        for (String part : KofExprUnicode.RANGES_LETTER.split(",")) {
            if (part.isEmpty()) continue;
            String[] se = part.split("-");
            set.add(Integer.parseInt(se[0], 16));
            set.add(Integer.parseInt(se.length == 2 ? se[1] : se[0], 16));
        }
        // um passo regular no plano alto (os intervalos não são contíguos lá)
        for (int cp = 0x10000; cp < 0x11000; cp += 37) set.add(cp);
        int[] out = new int[set.size()];
        int i = 0;
        for (int cp : set) {
            // o modelo de sujeito do §10 é o de VALOR ESCALAR — os
            // surrogates (D800-DFFF) não são escalares e não têm
            // representação no modelo de string (UTF-8), então ficam fora.
            if (cp >= 0xD800 && cp <= 0xDFFF) continue;
            out[i++] = cp;
        }
        return java.util.Arrays.copyOf(out, i);
    }

    // ------------------------------------------------------------------ exec

    private String runJvm(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "-jvm").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve(tag + "-jvm-out");
        CompilationResult r = driver.compile(file, out, Target.JVM);
        assertTrue(r.success(), tag + " (jvm): " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String runNative(String tag, String src) throws Exception {
        assumeTrue(has("as", "ld"), "native toolchain absent");
        Path file = tmp.resolve(tag + "-nat").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve(tag + "-nat-out");
        CompilationResult r = driver.compile(file, out, Target.NATIVE);
        assertTrue(r.success(), tag + " (native): " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "the native binary must exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String outText = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "the native run must exit clean, output: '" + outText + "'");
        return outText + "\n";
    }

    private static boolean has(String... tools) {
        for (String t : tools) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + t).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
