package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §740 (NATIVE x86 FACE): {@code String.split} com um separador de
 * MAIS de um caractere dividia pelo PRIMEIRO byte no nativo — o emissor passava
 * só {@code movzbl 24(%rsi), %esi} aos helpers de {@code sep_char} único, então
 * {@code "aXXb".split("XX")} dava 3 fatias em vez de 2 (e {@code "aaa".split("aa")}
 * dava 0), em SILÊNCIO (regra 5).
 *
 * <p>Fix: o ramo do separador não-vazio passa o PONTEIRO do separador e o limit
 * ao helper ADITIVO {@code kof_string_split_str}, que COMPÕE o
 * {@code kof_string_index_of2} (busca de substring em code units) com o
 * {@code kof_string_substring} (fatia em code units), avançando pelo comprimento
 * do separador em code units e mantendo a poda §111 dos vazios à direita quando
 * {@code limit <= 0}. O separador VAZIO segue no {@code kof_string_split_chars}
 * (§738) e o caminho de 1 caractere passa pelo mesmo helper novo (o caso
 * {@code "aXbXc".split("X")} está na prova, como regressão).
 *
 * <p>Oráculo é o JVM (≡ Script/JS, medidos). A face CROSS (riscv64/aarch64)
 * segue ABERTA no ledger — face separada, com as mesmas shapes por corrigir.
 */
class NativeSplitSubstrSeparatorE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            String j(String[] xs) {
                var s = String.valueOf(xs.length) + "|"
                for (var x in xs) { s = s + "[" + x + "]" }
                return s
            }
            main() {
                println("a=" + j("aXXb".split("XX")))
                println("b=" + j("abc".split("bc")))
                println("c=" + j("aaa".split("aa")))
                println("d=" + j("aXbXc".split("X")))
                println("e=" + j("a,b,".split(",")))
                println("f=" + j(",a,b".split(",")))
                println("g=" + j("::".split("::")))
                println("h=" + j("".split("::")))
                println("i=" + j("aXXbXXc".split("XX", 2)))
                println("k=" + j("aXXbXXc".split("XX", 1)))
                println("l=" + j("aXXb".split("XX", 5)))
                println("m=" + j("caf\\u00e9,\\u00fcber".split(",")))
                println("n=" + j("a\\u00A0b".split("\\u00A0")))
                // §740 (JDK): limit NEGATIVO mantem TODOS os vazios; limit 0 poda; limit > 0 = no maximo N, ultima = o RESTO.
                println("o=" + j("a,,b,,".split(",", -1)))
                println("p=" + j("a,,b,,".split(",", 0)))
                println("q=" + j("a,b,c".split(",", 2)))
                println("r=" + j("a,,b,,".split(",", 5)))
                println("s=" + j("".split(",", 3)))
                println("t=" + j("abc".split(",", 2)))
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "a=2|[a][b]",
            "b=1|[a]",
            "c=2|[][a]",
            "d=3|[a][b][c]",
            "e=2|[a][b]",
            "f=3|[][a][b]",
            "g=0|",
            "h=1|[]",
            "i=2|[a][bXXc]",
            "k=1|[aXXbXXc]",
            "l=2|[a][b]",
            "m=2|[caf\u00e9][\u00fcber]",
            "n=2|[a][b]",
            "o=5|[a][][b][][]",
            "p=3|[a][][b]",
            "q=2|[a][b,c]",
            "r=5|[a][][b][][]",
            "s=1|[]",
            "t=1|[abc]");

    @Test
    void jvmIsTheContractForSplitSubstr() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnSplitSubstr() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnSplitSubstr() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var so = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), so,
                new java.io.ByteArrayInputStream(new byte[0]), so);
        assertEquals(0, exit, () -> "js saída: " + so);
        assertEquals(GOLDEN, so.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86SplitSubstrMatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("x86");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), () -> "x86 saída: " + o);
        assertEquals(GOLDEN, o);
    }

    private String runJvm() throws Exception {
        Path dir = tmp.resolve("jvm");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "jvm");
        assertEquals(0, p.exitValue(), () -> "oráculo JVM: " + o);
        return o;
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
