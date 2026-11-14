package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.util.List;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof-esp8266 gate 1: the EMITTER — a Kof program compiles to the
 * `native.esp8266` target and the chain as→link produces a VALID Xtensa ELF
 * (the gate-0 measured shape: little-endian, the real memory map). RUNNing the
 * image needs hardware or Espressif's qemu fork — the honest proof is the
 * artifact chain, never a fake execution.
 *
 * <p>Guards (Q5): the toolchain is probed; absent → a NAMED skip (the asm is
 * still asserted — the emitter's own output is testable without the toolchain).
 */
class NativeEsp8266E2ETest {

    @TempDir Path tmp;

    private static String tcRoot() {
        String env = System.getenv("KOF_ESP_HOME");
        if (env != null && !env.isBlank()) return env;
        // the provision target first, then the gate-0 audit's working dir
        for (Path p : List.of(
                Path.of(System.getProperty("user.home"), ".local/share/kof-esp8266"),
                Path.of("/tmp/xtensa162"))) {
            if (Files.isExecutable(p.resolve("bin/xtensa-esp-elf-as"))) return p.toString();
        }
        return "/tmp/xtensa162";
    }

    private static boolean hasToolchain() {
        String root = tcRoot();
        return Files.isExecutable(Path.of(root, "bin/xtensa-esp-elf-as"))
                && Files.isRegularFile(Path.of(root, "lib/xtensa_esp8266.so"));
    }

    /** The emitter's toolchain envs (KOF_ESP_AS/LD + XTENSA_GNU_CONFIG), set for
     *  the current JVM process so `NativeEsp8266.emit` finds the cross tools.
     *  The dynconfig switch is the gate-0 measured byte-order law. */
    private static void installToolchainEnv() {
        String root = tcRoot();
        System.setProperty("kof.esp.root", root);
    }

    private CompilationResult compile(Path dir, String src) throws Exception {
        Path f = dir.resolve("Main.kf");
        Files.createDirectories(dir);
        Files.writeString(f, src);
        installToolchainEnv();
        return new CompilerDriver().compile(f, dir.resolve("out"), Target.NATIVE_ESP8266);
    }

    @Test
    void theLiteralHelloCompilesAndAssemblesToALittleEndianElf(@TempDir Path dir) throws Exception {
        CompilationResult r = compile(dir, """
                main() {
                    println("KO ESP8266")
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        Path asm = dir.resolve("out/Default/Main.s");
        assertTrue(Files.exists(asm), "o asm foi gerado");
        String text = Files.readString(asm);
        assertTrue(text.contains("literal_position"), "a disciplina l32r (medida no gate 0)");
        assertTrue(text.contains("0x60000074"), "o HAL UART0 (o endereço medido)");
        assertTrue(text.contains("call0 kof_plat_write"), "o HAL seam do bare-metal plan");

        Assumptions.assumeTrue(hasToolchain(),
                "o toolchain xtensa não está provisionado — o asm é mantido (o guard honesto)");
        Path elf = dir.resolve("out/Default/Main");
        assertTrue(Files.exists(elf), "o ELF foi linkado");
        // the gate-0 measured shape: LITTLE-endian Xtensa
        var pb = new ProcessBuilder(tcRoot() + "/bin/xtensa-esp-elf-objdump", "-f", elf.toString());
        String out = new String(pb.start().getInputStream().readAllBytes());
        assertTrue(out.contains("elf32-xtensa-le"), "little-endian (a ordem real do ESP8266): " + out);
    }

    @Test
    void intArithmeticAndLocalsLowerToTheSubset(@TempDir Path dir) throws Exception {
        CompilationResult r = compile(dir, """
                main() {
                    var x = 40
                    var y = x + 2
                    println(y)
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        String asm = Files.readString(dir.resolve("out/Default/Main.s"));
        assertTrue(asm.contains("movi a0, 42"), "a aritmética de literais: " + asm);
        assertTrue(asm.contains("s32i") && asm.contains("l32i"), "os locais em a13");
        assertTrue(asm.contains("kof_str_of_int"), "o printer decimal");
    }

    /** kof-esp8266 gate 2: the MEMORY/STACK REPORT — the budget line is printed
     *  against the ESP8266 windows and the verdict names the fit. The minimum
     *  program must report WITHIN. */
    @Test
    void theMemoryReportPrintsTheBudgetAndTheVerdict(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(hasToolchain(),
                "o toolchain xtensa não está provisionado — o relato de memória pula com o motivo");
        CompilationResult r = compile(dir, """
                main() {
                    println("KO ESP8266")
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        // the report goes to stderr; capture it
        var oldErr = System.err;
        var captured = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(captured));
        try {
            // rebuild through the emitter directly so the stderr is caught
            dev.kof.compiler.IRModule ir = new CompilerDriver()
                    .prepareRepl(java.util.List.of(dir.resolve("Main.kf")), dir.resolve("Main.kf").getParent());
            dev.kof.compiler.nat.mcu.NativeEsp8266.emit(ir, dir.resolve("out2"));
        } finally {
            System.setErr(oldErr);
        }
        String report = captured.toString();
        assertTrue(report.contains("memory — dram "), "a linha de orçamento: " + report);
        assertTrue(report.contains("iram/text"), "o text vs a janela IRAM: " + report);
        assertTrue(report.contains("WITHIN the target constraints"),
                "o veredito do gate 2: " + report);
    }

    @Test
    void theConcurrencyRefusalNamesConc003(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("Conc.kf");
        Files.createDirectories(dir);
        Files.writeString(f, """
                main() {
                    val t = spawn { println("worker") }
                    await t
                }
                """);
        CompilationResult r = new CompilerDriver().compile(f, dir.resolve("out"), Target.NATIVE_ESP8266);
        assertFalse(r.success(), "concorrência fora do subset recusa");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("CONC003"),
                "a recusa nomeia CONC003: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void anUnsupportedOpRefusesByName(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("Bad.kf");
        Files.createDirectories(dir);
        Files.writeString(f, """
                main() {
                    var l = listOf(1, 2, 3)
                    println(l.size)
                }
                """);
        CompilationResult r = new CompilerDriver().compile(f, dir.resolve("out"), Target.NATIVE_ESP8266);
        assertFalse(r.success(), "list ops fora do subset recusam");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("NATIVE002"), "a recusa nomeia o gap: " + diags);
    }
}
