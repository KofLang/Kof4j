package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * bug 79 (R6/paridade): String.toInt/toLong = contrato do JDK
 * (Integer/Long.parseLong(s.trim())) — entrada inválida ou overflow LANÇA
 * exceção String (capturável em try/catch), nunca número silencioso. Coberto
 * aqui nos 3 alvos golden (JVM/JS/Native-x86); riscv64/aarch64 = vetores
 * idênticos em NativeRiscv64E2ETest/NativeAarch64E2ETest (qemu).
 */
class KofStringParseTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN = """
            main() {
                println("42".toInt())
                println("-7".toInt())
                println("0".toInt())
                try { println("abc".toInt()); println("S1") } catch (String e) { println("T1") }
                try { println("12a34".toInt()); println("S2") } catch (String e) { println("T2") }
                println(" -42 ".toInt())
                println("+7".toInt())
                println("-2147483648".toInt())
                try { println("2147483648".toInt()); println("S3") } catch (String e) { println("T3") }
                try { println("999999999999".toInt()); println("S4") } catch (String e) { println("T4") }
                try { println("".toInt()); println("S5") } catch (String e) { println("T5") }
                println("1234567890".toLong())
                println("-9223372036854775807".toLong())
                try { println("9223372036854775808".toLong()); println("S6") } catch (String e) { println("T6") }
                var v = "-9223372036854775808".toLong()
                println(v < 0)
                println(("0".toLong()) == 0)
                println(v)
            }
            """;
    private static final String EXPECTED =
            "42\n-7\n0\nT1\nT2\n-42\n7\n-2147483648\nT3\nT4\nT5\n1234567890\n"
            + "-9223372036854775807\nT6\ntrue\ntrue\n-9223372036854775808";

    @Test
    void toIntToLongContractJvm(@TempDir Path tmp) throws Exception {
        runTarget(tmp, Target.JVM, "java");
    }

    @Test
    void toIntToLongContractNativeX86(@TempDir Path tmp) throws Exception {
        runTarget(tmp, Target.NATIVE, "bin");
    }

    // JS: toLong usa Number (double 53-bit) — overflow >2^53 NÃO lança e
    // imprime com precisão de double. É DIVERGÊNCIA R5 de OUTRO bug (modelo
    // numérico JS, design), não do 79 (asm nativo). Cobrindo aqui só o que o
    // JS casa com o contrato: toInt (32-bit cabe no double) + toLong de
    // valores até 2^53. Longos 64-exatos = golden JVM/Native (KofStringParseTest)
    // e riscv/aarch (E2E qemu).
    private static final String GOLDEN_JS = """
            main() {
                println("42".toInt())
                println("-7".toInt())
                println("0".toInt())
                try { println("abc".toInt()); println("S1") } catch (String e) { println("T1") }
                try { println("12a34".toInt()); println("S2") } catch (String e) { println("T2") }
                println(" -42 ".toInt())
                println("+7".toInt())
                println("-2147483648".toInt())
                try { println("2147483648".toInt()); println("S3") } catch (String e) { println("T3") }
                try { println("999999999999".toInt()); println("S4") } catch (String e) { println("T4") }
                try { println("".toInt()); println("S5") } catch (String e) { println("T5") }
                println("1234567890".toLong())
                println("-9007199254740991".toLong())
                try { println("abc".toLong()); println("S6") } catch (String e) { println("T6") }
                println("0".toLong())
            }
            """;
    private static final String EXPECTED_JS =
            "42\n-7\n0\nT1\nT2\n-42\n7\n-2147483648\nT3\nT4\nT5\n1234567890\n"
            + "-9007199254740991\nT6\n0";

    @Test
    void toIntToLongContractJs(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, GOLDEN_JS);
        Path out = tmp.resolve("out-js");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        Path mjs;
        try (var s = Files.walk(out)) {
            mjs = s.filter(p -> p.getFileName().toString().equals("Default.mjs"))
                   .findFirst().orElseThrow();
        }
        Process p = new ProcessBuilder("node", mjs.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JS exit " + ec + ": " + output);
        assertEquals(EXPECTED_JS, output, "JS stdout");
    }

    private void runTarget(Path tmp, Target t, String kind) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, GOLDEN);
        Path out = tmp.resolve("out-" + t);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb;
        if (t == Target.JVM) {
            pb = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", out.toString(), "Default.Main");
        } else {
            pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        }
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, t + " exit " + ec + ": " + output);
        assertEquals(EXPECTED, output, t + " stdout");
    }
}
