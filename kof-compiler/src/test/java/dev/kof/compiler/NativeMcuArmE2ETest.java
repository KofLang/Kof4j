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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-4.3 (PLAN-BAREMETAL-BOOT): E2E do emissor Cortex-M3 (Thumb-2) do MCU sob
 * {@code qemu-system-arm -M mps2-an385}. A saída é a UART CMSDK (0x40004000) e
 * o reset path vem da vector table em 0x0 ({@code [0]=SP}, {@code [1]=Reset_Handler|1}),
 * exatamente o aceite do B-4.
 *
 * <p>Guards honestos (Q5): binutils {@code arm-none-eabi-*} + {@code
 * qemu-system-arm} provisionados sem root em {@code ~/.local/share/kof-mcu}
 * (ver {@code scripts/provision-mcu-qemu.sh}). Sem eles o teste é
 * {@code assumeTrue} skip — nunca verde falso.
 */
class NativeMcuArmE2ETest implements NativeToolchainAssumptions {

    private static final String HELLO = "main() { println(\"KO-CM3 OK\") }";
    private static final String MULTI = "main() { print(\"a\"); println(\"b\"); print(\"c\") }";
    private static final String UNSUPPORTED = "main() { println(42) }";

    @Test
    void mcuArmPrintsOverUart(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        Path bin = build(tempDir, HELLO, true);
        assumeTrue(Files.isRegularFile(bin), "binário ARM ausente (assemble/link falhou)");
        String text = serialText(boot(tempDir, bin));
        assertTrue(text.contains("KO-CM3 OK"),
                "UART deveria conter 'KO-CM3 OK', veio: [" + text + "]");
    }

    @Test
    void mcuArmPreservesPrintOrderAndNewlines(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        Path bin = build(tempDir, MULTI, true);
        assumeTrue(Files.isRegularFile(bin), "binário ARM ausente (assemble/link falhou)");
        String text = serialText(boot(tempDir, bin));
        assertEquals("ab\nc", text.strip(),
                "ordem/newlines do print/println alterados");
    }

    @Test
    void mcuArmVectorTableResetPathIsAsserted(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        String objcopy = toolPath("arm-none-eabi-objcopy");
        String nm = toolPath("arm-none-eabi-nm");
        assumeTrue(objcopy != null && nm != null, "binutils arm-none-eabi ausente");
        Path bin = build(tempDir, HELLO, true);
        assumeTrue(Files.isRegularFile(bin), "binário ARM ausente (assemble/link falhou)");

        Path raw = tempDir.resolve("img.bin");
        capture(objcopy, "-O", "binary", bin.toString(), raw.toString());
        byte[] img = Files.readAllBytes(raw);
        assertTrue(img.length >= 8, "imagem crua curta demais: " + img.length);
        long sp = le32(img, 0);
        long reset = le32(img, 4);
        assertEquals(0x00080000L, sp, "vector table [0] deve ser o stack top (SSRAM1)");
        assertEquals(1L, reset & 1L, "vector [1] (Reset_Handler) deve ter o bit Thumb");

        long handler = symbolAddr(capture(nm, bin.toString()), "Reset_Handler");
        assertTrue(handler > 0, "Reset_Handler ausente do símbolo nm");
        assertEquals(handler | 1L, reset,
                "vector table [1] deve apontar para o Reset_Handler real (assert do reset path)");
    }

    @Test
    void mcuArmImageDefinesHalSymbols(@TempDir Path tempDir) throws Exception {
        String nm = toolPath("arm-none-eabi-nm");
        assumeTrue(nm != null, "binutils arm-none-eabi ausente");
        Path bin = build(tempDir, HELLO, true);
        assumeTrue(Files.isRegularFile(bin), "binário ARM ausente (assemble/link falhou)");
        String syms = capture(nm, bin.toString());
        for (String sym : new String[]{"kof_plat_write", "kof_plat_exit",
                "kof_plat_thread_id", "kof_plat_random",
                "kof_plat_time", "kof_plat_time_mono"}) {
            assertTrue(syms.matches("(?s).*\\bT " + sym + "\\b.*"),
                    "imagem MCU ARM deveria definir " + sym + ":\n" + syms);
        }
    }

    @Test
    void mcuArmRejectsConcurrencyWithConc003(@TempDir Path tempDir) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main() { spawn { println(\"x\") } }");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"),
                Target.NATIVE_MCU_ARM, NativeProfile.FREESTANDING);
        assertTrue(!result.success(), "spawn no MCU single-core deve ser recusado");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("CONC003"),
                "recusa deve citar CONC003 (nunca stub), veio: " + diags);
    }

    @Test
    void mcuArmRejectsUnsupportedOpWithDiagnostic(@TempDir Path tempDir) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, UNSUPPORTED);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"),
                Target.NATIVE_MCU_ARM, NativeProfile.FREESTANDING);
        assertTrue(!result.success(), "print de Int ainda não é suportado no MCU — deve recusar");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("NATIVE002"),
                "recusa deve citar NATIVE002, veio: " + diags);
    }

    private Path build(Path tempDir, String program, boolean expectSuccess) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir,
                Target.NATIVE_MCU_ARM, NativeProfile.FREESTANDING);
        assertEquals(expectSuccess, result.success(),
                "compile MCU ARM inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    private Path boot(Path tempDir, Path bin) throws Exception {
        String qemu = toolPath("qemu-system-arm");
        assumeTrue(qemu != null, "qemu-system-arm ausente (scripts/provision-mcu-qemu.sh)");
        Path ser = tempDir.resolve("ser.log");
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(
                qemu, "-M", "mps2-an385", "-display", "none",
                "-serial", "file:" + ser, "-kernel", bin.toString()));
        // Sem test device no mps2-an385: o programa escreve na UART e faz halt,
        // então o qemu nunca sai sozinho. Damos um tempo limitado para a UART
        // drenar e matamos; a saída vai para DISCARD (nunca um pipe sem leitor).
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        Path prefix = mcuPrefix();
        if (prefix != null && qemu.startsWith(prefix.toString())) {
            pb.environment().put("LD_LIBRARY_PATH",
                    prefix.resolve("usr/lib/x86_64-linux-gnu").toString());
        }
        Process p = pb.start();
        p.waitFor(5, TimeUnit.SECONDS);
        p.destroyForcibly();
        p.waitFor(5, TimeUnit.SECONDS);
        return ser;
    }

    private String serialText(Path log) throws IOException {
        return Files.readString(log, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    private static long le32(byte[] b, int off) {
        return (b[off] & 0xffL) | ((b[off + 1] & 0xffL) << 8)
                | ((b[off + 2] & 0xffL) << 16) | ((b[off + 3] & 0xffL) << 24);
    }

    private static long symbolAddr(String nmOutput, String name) {
        Matcher m = Pattern.compile("(?m)^([0-9a-fA-F]+)\\s+\\w\\s+" + name + "$")
                .matcher(nmOutput);
        return m.find() ? Long.parseLong(m.group(1), 16) : -1L;
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

    /** Caminho da ferramenta: PATH ou prefixo provisionado sem root. */
    private static String toolPath(String name) {
        if (hasTool(name, "--version")) return name;
        Path p = mcuPrefix();
        if (p != null) {
            Path t = p.resolve("usr/bin").resolve(name);
            if (Files.isExecutable(t)) return t.toString();
        }
        return null;
    }

    private static Path mcuPrefix() {
        String env = System.getenv("KOF_MCU_HOME");
        if (env != null && Files.isDirectory(Path.of(env))) return Path.of(env);
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-mcu");
        return Files.isDirectory(home) ? home : null;
    }
}
