package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §738 (FACES CROSS: riscv64 + aarch64): o padrão VAZIO em
 * {@code split("")} e {@code replace("", to)} divergia igual ao x86 — o
 * emissor cross passava {@code sep = 0} (NUL) e o {@code kof_string_replace}
 * nunca casa uma agulha de 0 bytes, então o native devolvia 1 elemento /
 * a string intacta onde o JVM/Script/JS dão um elemento por CARACTERE e
 * inserem {@code to} em toda posição.
 *
 * <p>Fix: ramos do padrão vazio no emissor cross → helpers ADITIVOS
 * {@code kof_string_split_chars} (por code point UTF-8, com o {@code limit}
 * do overload JDK) e {@code kof_string_replace_empty} no runtime riscv
 * ({@code NativeRiscvAsmStrn1}); o aarch64 herda pelo
 * {@code NativeAarch64Translator}. Os caminhos do padrão não-vazio ficam
 * INTACTOS (ambos aparecem na prova).
 *
 * <p>Oráculo é o JVM (≡ Script/JS, medidos). Roda sob qemu; sem a toolchain
 * cross o teste PULA honestamente.
 */
class NativeCrossEmptySeparatorE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                println("s1=" + "abc".split("").size)
                println("s2=" + "".split("").size)
                println("s6=" + "abc".split("", 2).size)
                println("s7=" + "abcdef".split("", 3).size)
                var q = "abc".split("")
                var t = ""
                for (var p in q) { t = t + p + "|" }
                println("j2=" + t)
                var u = "caf\u00e9".split("")
                var v = ""
                for (var p in u) { v = v + p + "|" }
                println("u1=" + u.size + " u2=" + v)
                println("r1=[" + "aXbXc".replace("X", "-") + "]")
                println("r2=[" + "aXbXc".replace("", "-") + "]")
                println("r3=[" + "".replace("", "-") + "]")
                println("r4=[" + "abc".replace("", "") + "]")
                println("r5=[" + "abc".replace("", "XY") + "]")
                println("r7=[" + "a".replace("", "-") + "]")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "s1=3",
            "s2=1",
            "s6=2",
            "s7=3",
            "j2=a|b|c|",
            "u1=4 u2=c|a|f|\u00e9|",
            "r1=[a-b-c]",
            "r2=[-a-X-b-X-c-]",
            "r3=[-]",
            "r4=[abc]",
            "r5=[XYaXYbXYcXY]",
            "r7=[-a-]");

    @Test
    void riscv64MatchesTheJvmOnEmptySeparator() throws Exception {
        runCross("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64MatchesTheJvmOnEmptySeparator() throws Exception {
        runCross("aarch64", Target.NATIVE_AARCH64);
    }

    private void runCross(String arch, Target target) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                arch + " cross toolchain/qemu absent");
        Path dir = tmp.resolve(arch);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> arch + " compile: " + r.diagnostics().getDiagnostics());
        String o = NativeRiscv64E2ETest.runQemu(arch, out.resolve("Default/Main"));
        assertEquals(GOLDEN, o.strip());
    }
}
