package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.NativeProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-4.1 (PLAN-BAREMETAL-BOOT): E2E do emissor RV32I do MCU sob
 * {@code qemu-system-riscv32 -M virt}. Sem semihosting (o qemu 8.2 não
 * reconhece o {@code ebreak} de semihosting) — a saída é a UART do virt
 * (0x10000000) e o encerramento é o test device (0x100000), exatamente o
 * caminho que o plano B-4 autoriza.
 *
 * <p>Guards honestos (Q5): toolchain binutils riscv64 + qemu-system-riscv32
 * provisionado em {@code ~/.local/share/kof-mcu} (ver
 * {@code scripts/provision-mcu-qemu.sh}). Sem eles o teste é {@code assumeTrue}
 * skip — nunca verde falso.
 */
class NativeMcuE2ETest {

    private static final String HELLO = "main() { println(\"KO-MCU OK\") }";
    private static final String MULTI = "main() { print(\"a\"); println(\"b\"); print(\"c\") }";
    private static final String INT_LIT = "main() { println(42) }";
    private static final String UNSUPPORTED = "main() { var m = mapOf(\"a\", 1) }";
    private static final String LIST_SPIKE =
            "main() { var xs = listOf(1, 2, 3)\n    println(xs.size)\n    println(42)\n}";

    @Test
    void mcuRiscv32PrintsOverUart(@TempDir Path tempDir) throws Exception {
        assumeToolchain();
        Path bin = build(tempDir, HELLO, true);
        Path ser = boot(tempDir, bin);
        String text = serialText(ser);
        assertTrue(text.contains("KO-MCU OK"),
                "UART deveria conter 'KO-MCU OK', veio: [" + text + "]");
    }

    @Test
    void mcuRiscv32PreservesPrintOrderAndNewlines(@TempDir Path tempDir) throws Exception {
        assumeToolchain();
        Path bin = build(tempDir, MULTI, true);
        String text = serialText(boot(tempDir, bin));
        assertEquals("ab\nc", text.strip(),
                "ordem/newlines do print/println alterados");
    }

    @Test
    void mcuArtifactIsElf32Riscv(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasAs(), "binutils riscv64 ausente");
        Path bin = build(tempDir, HELLO, true);
        byte[] b = Files.readAllBytes(bin);
        assertTrue(b.length > 20 && b[0] == 0x7f && b[1] == 'E' && b[2] == 'L' && b[3] == 'F',
                "artefato MCU deveria ser ELF");
        assertEquals(1, b[4] & 0xff, "ELF deve ser 32-bit");
        int machine = (b[18] & 0xff) | ((b[19] & 0xff) << 8);
        assertEquals(243, machine, "e_machine deve ser RISC-V (243)");
    }

    @Test
    void mcuImageDefinesHalSymbols(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("riscv64-linux-gnu-nm", "--version"), "binutils nm ausente");
        Path bin = build(tempDir, HELLO, true);
        String syms = capture("riscv64-linux-gnu-nm", bin.toString());
        assertTrue(syms.matches("(?s).*\\bT kof_plat_write\\b.*"),
                "imagem MCU deveria definir kof_plat_write:\n" + syms);
        assertTrue(syms.matches("(?s).*\\bT kof_plat_exit\\b.*"),
                "imagem MCU deveria definir kof_plat_exit:\n" + syms);
        assertTrue(syms.matches("(?s).*\\bT kof_plat_thread_id\\b.*"),
                "imagem MCU deveria definir kof_plat_thread_id:\n" + syms);
        assertTrue(syms.matches("(?s).*\\bT kof_plat_random\\b.*"),
                "imagem MCU deveria definir kof_plat_random:\n" + syms);
    }

    @Test
    void mcuResetEntryIsAtLoadBase(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("riscv64-linux-gnu-nm", "--version"), "binutils nm ausente");
        Path bin = build(tempDir, HELLO, true);
        String syms = capture("riscv64-linux-gnu-nm", bin.toString());
        // Reset path: _start must be the first instruction at the load base
        // (0x80000000 for -M virt), i.e. the CPU's reset entry.
        assertTrue(syms.matches("(?s).*\\b80000000 T _start\\b.*"),
                "o reset path (_start @ 0x80000000) deveria estar asserido na imagem:\n" + syms);
    }

    @Test
    void mcuRejectsConcurrencyWithConc003(@TempDir Path tempDir) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main() { spawn { println(\"x\") } }");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"),
                Target.NATIVE_RISCV32, NativeProfile.FREESTANDING);
        assertTrue(!result.success(), "spawn no MCU single-core deve ser recusado");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("CONC003"),
                "recusa deve citar CONC003 (nunca stub), veio: " + diags);
    }

    @Test
    void mcuSpikeListOfPrintlnSizeAndIntOverUart(@TempDir Path tempDir) throws Exception {
        assumeToolchain();
        Path bin = build(tempDir, LIST_SPIKE, true);
        String text = serialText(boot(tempDir, bin));
        assertEquals("3\n42", text.strip(),
                "fatia F: listOf(1,2,3).size e println(42) byte-exatos: [" + text + "]");
    }

    @Test
    void mcuRejectsUnsupportedOpWithDiagnostic(@TempDir Path tempDir) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, UNSUPPORTED);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"),
                Target.NATIVE_RISCV32, NativeProfile.FREESTANDING);
        assertTrue(!result.success(), "mapOf no MCU deve ser recusado honesto (sem stub)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("NATIVE002"),
                "recusa deve citar NATIVE002, veio: " + diags);
    }

    // §506 (regressão 396ff7de4 fechada): o println de literal Int COMPILE-TIME
    // agora é código real (literal na .rodata + kof_plat_write); o payload do
    // println(String) carrega o '\n' EXATO em bytes UTF-8 (antes liam 1 byte
    // além do literal — lixo do vizinho de .ascii).
    @Test
    void mcuPrintsCompileTimeIntLiteral(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasAs(), "binutils riscv64 ausente");
        byte[] img = Files.readAllBytes(build(tempDir, INT_LIT, true));
        String raw = new String(img, StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("42\n"),
                "payload '42\\n' deve estar na .rodata da imagem (emissão real, não facade)");
    }

    @Test
    void mcuImageCarriesExactStringPayload(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasAs(), "binutils riscv64 ausente");
        byte[] img = Files.readAllBytes(build(tempDir, HELLO, true));
        String raw = new String(img, StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("KO-MCU OK\n"),
                "payload com newline exato deve estar na imagem (fim da leitura de byte estranho)");
    }

    private Path build(Path tempDir, String program, boolean expectSuccess) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir,
                Target.NATIVE_RISCV32, NativeProfile.FREESTANDING);
        assertEquals(expectSuccess, result.success(),
                "compile MCU inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    private Path boot(Path tempDir, Path bin) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-riscv32 ausente (scripts/provision-mcu-qemu.sh)");
        Path ser = tempDir.resolve("ser.log");
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(
                qemu.toString(), "-M", "virt", "-bios", "none", "-display", "none",
                "-serial", "file:" + ser, "-kernel", bin.toString()));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Path prefix = mcuPrefix();
        if (prefix != null && qemu.isAbsolute() && qemu.startsWith(prefix)) {
            pb.environment().put("LD_LIBRARY_PATH",
                    prefix.resolve("usr/lib/x86_64-linux-gnu").toString());
        }
        Process p = pb.start();
        try (var in = p.getInputStream()) {
            in.readAllBytes();
        }
        p.waitFor(20, TimeUnit.SECONDS);
        p.destroyForcibly();
        return ser;
    }

    private String serialText(Path log) throws IOException {
        return Files.readString(log, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    private void assumeToolchain() {
        assumeTrue(hasAs(), "binutils riscv64 ausente (riscv64-linux-gnu-as)");
    }

    private static boolean hasAs() {
        return hasTool("riscv64-linux-gnu-as", "--version");
    }

    private static String capture(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(20, TimeUnit.SECONDS);
        return out;
    }

    private static boolean hasTool(String tool, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = tool;
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process p = new ProcessBuilder(cmd).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Prefixo provisionado sem root (scripts/provision-mcu-qemu.sh). */
    private static Path mcuPrefix() {
        String env = System.getenv("KOF_MCU_HOME");
        if (env != null && Files.isRegularFile(Path.of(env, "usr/bin/qemu-system-riscv32"))) {
            return Path.of(env);
        }
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-mcu");
        if (Files.isRegularFile(home.resolve("usr/bin/qemu-system-riscv32"))) {
            return home;
        }
        return null;
    }

    private static Path findQemu() {
        if (hasTool("qemu-system-riscv32", "--version")) return Path.of("qemu-system-riscv32");
        Path p = mcuPrefix();
        return p != null ? p.resolve("usr/bin/qemu-system-riscv32") : null;
    }
}
