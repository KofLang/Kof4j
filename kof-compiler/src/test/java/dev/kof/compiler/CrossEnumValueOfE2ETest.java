package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §750 (riscv64 + aarch64): {@code Enum.valueOf(String)} morria com
 * SIGSEGV no qemu. O emissor cross tinha um ramo {@code STATIC && "valueOf"}
 * que tratava TODO {@code valueOf} estatico como a conversao
 * {@code String.valueOf}/{@code wrapper.valueOf}; na classe do enum
 * ({@code valueOf(String) -> Enum}) nenhum dos casos de tipo de argumento
 * casava com String, e o ramo retornava <b>sem emitir nada</b> — o argumento
 * ficava na pilha e o dispatch virtual seguinte lia a String como receiver.
 *
 * <p>O gate agora aceita so donos BUILTIN (String / wrappers JDK); um dono de
 * usuario (enum ou classe) segue para a chamada DIRETA do fim do emissor.
 * Oráculo e o JVM; {@code values()}, {@code name()}, {@code ordinal()} e a
 * identidade dos constantes aparecem na prova junto (zero-regressao do ramo).
 * Sem a toolchain cross o teste PULA honestamente.
 */
class CrossEnumValueOfE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            enum Dir { N, S, E, W }

            main() {
                println(Dir.valueOf("S"))
                var d = Dir.valueOf("E")
                println(d.name() + ":" + d.ordinal())
                println(Dir.valueOf("S") == Dir.S)
                println(Dir.values().size())
                println(Dir.valueOf("W") == Dir.valueOf("W"))
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "S",
            "E:2",
            "true",
            "4",
            "true");

    @Test
    void riscv64MatchesTheJvmOnEnumValueOf() throws Exception {
        runCross("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64MatchesTheJvmOnEnumValueOf() throws Exception {
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
