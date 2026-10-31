package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §765/#980: um throw no OPERANDO do `return` tem que ver o handler do `try`
 * ainda vinculado, e o valor tem que sobreviver ao unlink (que reseta o sp nos
 * nativos) em todo tipo de retorno. O último `plain()` prova a cadeia íntegra.
 */
class ReturnOperandThrowE2ETest {

    private static final String PROGRAM = """
            record P(Int x)
            String boom() { throw "boom" }
            Int boomInt() { throw "boomInt" }
            String plain() {
                try { return "v=" + boom() } catch (String m) { return "caught:" + m }
            }
            String withFinally() {
                try { return "v=" + boom() } catch (String m) { return "caught:" + m } finally { println("F1") }
            }
            String outer() {
                try {
                    try { return "v=" + boom() } finally { println("F2") }
                } catch (String m) {
                    return "outer:" + m
                }
            }
            Int nestedCatch() {
                try {
                    try { return boomInt() } catch (String m) { return 7 }
                } catch (String m) {
                    return 8
                }
            }
            Int okInt() { try { return 41 + 1 } catch (String m) { return -1 } }
            Long okLong() { try { return 5000000000L } catch (String m) { return -1L } }
            Double okDouble() { try { return 2.5 } catch (String m) { return -1.0 } }
            Bool okBool() { try { return 1 < 2 } catch (String m) { return false } }
            P okRecord() { try { return P(3) } catch (String m) { return P(-1) } }
            main() {
                println(plain())
                println(withFinally())
                println(outer())
                println(nestedCatch())
                println(okInt())
                println(okLong())
                println(okDouble())
                println(okBool())
                println(okRecord().x)
                try { throw "later" } catch (String m) { println("still:" + m) }
                println(plain())
            }
            """;

    private static final String EXPECTED = String.join("\n",
            "caught:boom", "F1", "caught:boom", "F2", "outer:boom", "7", "42", "5000000000",
            "2.5", "true", "3", "still:later", "caught:boom");

    private final CompilerDriver driver = new CompilerDriver();

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"JVM", "JS", "NATIVE", "NATIVE_RISCV64", "NATIVE_AARCH64"})
    void matchesOnEveryCompiledTarget(Target target, @TempDir Path dir) throws Exception {
        if (target != Target.JVM && target != Target.JS) {
            Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                    "native roda em Linux");
        }
        if (target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64) {
            Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(target.nativeArch()),
                    target.nativeArch() + " toolchain/QEMU required");
        }
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, execute(target, out), target.toString());
    }

    @Test
    void matchesOnScript(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        KofInterpreter.Result r = driver.interpret(List.of(src), dir, new String[0]);
        assertEquals(0, r.exitCode(), "Script stderr: " + r.stderr());
        assertEquals(EXPECTED, r.stdout().replace("\r\n", "\n").trim());
    }

    private static String execute(Target target, Path out) throws Exception {
        if (target == Target.JS) {
            var buf = new ByteArrayOutputStream();
            int rc = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            String o = buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, rc, "JS: " + o);
            return o;
        }
        Path binary = out.resolve("Default/Main");
        ProcessBuilder pb = switch (target) {
            case JVM -> new ProcessBuilder("java", "-cp", out.toString(), "Default.Main");
            case NATIVE -> new ProcessBuilder(binary.toString());
            default -> NativeRiscv64E2ETest.qemu(target.nativeArch(), binary);
        };
        Process p = pb.redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), target + ": " + o);
        return o;
    }
}
