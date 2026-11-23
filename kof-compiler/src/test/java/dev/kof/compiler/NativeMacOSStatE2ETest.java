package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-6 (#1135, D-NATIVE-MACOS) — a família STAT da costura.
 *
 * <p><b>O PROBLEMA MEDIDO:</b> o runtime lê os campos do stat em offsets do
 * Linux ({@code st_mode@24}, {@code st_size@48}, {@code st_mtim@88/96}), mas o
 * {@code struct stat} do Darwin tem OUTRO layout — medido compilando os
 * {@code offsetof} com o clang real:
 *
 * <pre>
 *   Darwin   mode@4 (u16)  nlink@6 (u16)  mtimespec@48  size@96  sizeof=144
 *   Linux    mode@24 (u32) nlink@16 (u32) mtim@88      size@48  sizeof=144
 * </pre>
 *
 * <p>E as FLAGS: {@code AT_SYMLINK_NOFOLLOW = 0x20} no Darwin (0x100 no Linux)
 * e {@code AT_FDCWD = -2} (-100 no Linux). Copiar o corpo Linux teria feito o
 * runtime ler lixo (o {@code st_size@96} do Darwin seria lido como mode).
 */
class NativeMacOSStatE2ETest {

    /** Um programa que usa a io de stat (o tamanho e o modo). */
    private static final String IO_PROGRAM = """
            main() {
                var f = File("/tmp/kof-stat-probe.bin")
                println(f.size())
                println(f.exists())
            }
            """;

    private static String macosAssembly(Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, IO_PROGRAM);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setNativeProfile(dev.kof.compiler.nat.NativeProfile.of("macos"));
            driver.compile(src, tmp.resolve("out"), Target.NATIVE);
            return Files.readString(tmp.resolve("out/Default/Main.s"));
        } finally {
            dev.kof.compiler.nat.NativeProfile.active = previous;
            if (priorKeep == null) {
                System.clearProperty("KOF_KEEP_ASM");
            } else {
                System.setProperty("KOF_KEEP_ASM", priorKeep);
            }
        }
    }

    private static String block(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(label + ":")) {
                inside = true;
                continue;
            }
            if (inside && trimmed.endsWith(":") && !trimmed.contains(" ")
                    && !trimmed.startsWith(".")) break;
            if (inside) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * O CORPO do macOS: as flags e o dirfd TRADUZIDOS e a normalização do
     * layout — cada campo do Darwin para o seu lugar no layout do host.
     */
    @Test
    void theStatNormalizesTheDarwinLayout(@TempDir Path tmp) throws IOException {
        String body = block(macosAssembly(tmp), "kof_plat_stat");
        assertTrue(body.contains("call fstatat"), "the stat is the fstatat: " + body);
        // as FLAGS: 0x100 do Linux -> 0x20 do Darwin
        assertTrue(body.contains("movq $0x20, %r10"),
                "AT_SYMLINK_NOFOLLOW is 0x20 on Darwin: " + body);
        // o dirfd: -100 -> -2
        assertTrue(body.contains("movq $-2, %rdi"),
                "AT_FDCWD is -2 on Darwin: " + body);
        // a normalização: os 4 campos (os offsets medidos com offsetof)
        assertTrue(body.contains("movzwl 4(%rsp), %eax") && body.contains("movl %eax, 24(%rbx)"),
                "mode: Darwin@4 -> host@24: " + body);
        assertTrue(body.contains("movzwl 6(%rsp), %eax") && body.contains("movl %eax, 16(%rbx)"),
                "nlink: Darwin@6 -> host@16: " + body);
        assertTrue(body.contains("movq 48(%rsp), %rax") && body.contains("movq %rax, 88(%rbx)"),
                "mtime.sec: Darwin@48 -> host@88: " + body);
        assertTrue(body.contains("movq 96(%rsp), %rax") && body.contains("movq %rax, 48(%rbx)"),
                "size: Darwin@96 -> host@48: " + body);
        assertFalse(body.contains("syscall"),
                "no Linux syscall in the macOS stat: " + body);
    }

    /**
     * O RUNTIME não faz mais o stat direto — o syscall 262 saiu dos sítios
     * (eles chamam a costura), e assim a diferença de plataforma fica num
     * lugar só.
     */
    @Test
    void theRuntimeStatGoesThroughTheSeam(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        assertTrue(asm.contains("call kof_plat_stat"),
                "the runtime stat goes through the seam");
        // no perfil macOS o corpo da costura é o fstatat da libSystem — o
        // syscall 262 (o corpo do HOST) não entra no texto
        assertFalse(block(asm, "kof_plat_stat").contains("movq $262, %rax"),
                "the macOS stat body is not the Linux syscall");
    }

    /**
     * O CORPO do HOST continua sendo o {@code newfstatat} (semântica
     * idêntica à de antes da costura): o perfil host não muda de
     * comportamento — o teste de paridade do stat é este.
     */
    @Test
    void theHostStatBodyIsTheSameSyscall(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, IO_PROGRAM);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        try {
            CompilerDriver driver = new CompilerDriver();
            CompilationResult host = driver.compile(src, tmp.resolve("outh"), Target.NATIVE);
            assertTrue(host.success(), "the host compile: "
                    + host.diagnostics().getDiagnostics());
            String asm = Files.readString(tmp.resolve("outh/Default/Main.s"));
            assertTrue(block(asm, "kof_plat_stat").contains("movq $262, %rax"),
                    "the host stat body is the newfstatat: "
                            + block(asm, "kof_plat_stat"));
        } finally {
            if (priorKeep == null) {
                System.clearProperty("KOF_KEEP_ASM");
            } else {
                System.setProperty("KOF_KEEP_ASM", priorKeep);
            }
        }
    }

    private static int count(String text, String needle) {
        int n = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            n++;
            at = text.indexOf(needle, at + needle.length());
        }
        return n;
    }

    /**
     * O ASSEMBLER REAL aceita o corpo — e o objeto carrega
     * {@code _fstatat} da libSystem.
     */
    @Test
    void theRealMachOAssemblerAcceptsTheStatBody(@TempDir Path tmp) throws Exception {
        Path zig = machoToolchain();
        org.junit.jupiter.api.Assumptions.assumeTrue(zig != null,
                "the Mach-O toolchain is absent (scripts/provision-macho-toolchain.sh)");
        String macho = dev.kof.compiler.nat.NativeMachO.translate(macosAssembly(tmp));
        StringBuilder withoutElfOnly = new StringBuilder();
        for (String line : macho.split("\n", -1)) {
            String t = line.trim();
            if (t.equals("syscall") || t.contains("%fs:") || t.contains("@tpoff")) continue;
            withoutElfOnly.append(line).append('\n');
        }
        Path asmFile = tmp.resolve("Main.macho.s");
        Files.writeString(asmFile, withoutElfOnly.toString());
        Path obj = tmp.resolve("Main.macho.o");
        ProcessBuilder pb = new ProcessBuilder(
                zig.toString(), "cc", "-target", "x86_64-macos-none",
                "-c", asmFile.toString(), "-o", obj.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes());
        int rc = proc.waitFor();
        assertEquals(0, rc,
                "the Mach-O assembler must accept the stat body:\n" + output);
        String blob = new String(Files.readAllBytes(obj),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(blob.contains("_fstatat"),
                "the object carries the libSystem symbol: " + output);
    }

    private static Path machoToolchain() {
        String home = System.getenv("KOF_MACHO_HOME");
        List<Path> candidates = new ArrayList<>();
        if (home != null && !home.isBlank()) {
            candidates.add(Path.of(home, "bin", "zig"));
        }
        candidates.add(Path.of(System.getProperty("user.home", ""),
                ".local", "share", "kof-macho", "bin", "zig"));
        for (Path p : candidates) {
            if (Files.isExecutable(p)) return p;
        }
        return null;
    }
}
