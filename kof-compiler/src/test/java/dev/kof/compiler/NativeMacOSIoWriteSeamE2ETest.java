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
 * M-6 (#1135, D-NATIVE-MACOS) — a face de ESCRITA/DELETE do {@code io} pela
 * costura, e um defeito de portabilidade que a migração expõe.
 *
 * <p><b>O DEFEITO (medido no texto emitido):</b> o {@code kof_io_append_text}
 * guardava o descritor no {@code %rdi} e o reusava no {@code close} DEPOIS do
 * {@code write}: {@code movq %rdi, %rdi; call kof_plat_close}. No Linux isso
 * funciona porque o {@code syscall} preserva o {@code %rdi} — mas no Darwin o
 * corpo da costura chama a libSystem, e a chamada da libSystem DESTRÓI o
 * {@code %rdi}. O {@code close} fecharia um descritor QUALQUER (o stdout, às
 * vezes). O descritor agora vive num registro callee-saved.
 *
 * <p>E o {@code open}/{@code unlink}/{@code rmdir} do {@code io} ainda eram
 * syscalls crus: no Darwin não existe syscall — a face é da costura
 * ({@code kof_plat_open}/{@code kof_plat_unlink}/{@code kof_plat_rmdir}).
 */
class NativeMacOSIoWriteSeamE2ETest {

    /**
     * O texto do RUNTIME no perfil pedido. O caminho pelo compilador nao serve
     * aqui: enquanto a familia io nao estiver toda migrada, o perfil macOS
     * RECUSA o programa (NATIVE005 — por design, para nao emitir um binario
     * que falha em runtime). O runtime, esse, e' emitido sem recusa — e e'
     * ele que a prova mede.
     */
    private static String runtime(String profile) {
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            dev.kof.compiler.nat.NativeProfile.active = profile == null
                    ? previous
                    : dev.kof.compiler.nat.NativeProfile.of(profile);
            return NativeRuntime.generateRuntimeAssembly();
        } finally {
            dev.kof.compiler.nat.NativeProfile.active = previous;
        }
    }

    private static String body(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(label + ":")) {
                inside = true;
                continue;
            }
            if (!inside) continue;
            if (trimmed.startsWith(".section")) break;
            if (trimmed.endsWith(":") && !trimmed.contains(" ")
                    && !trimmed.startsWith(".")) break;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * O descritor do append vive num registro CALLEE-SAVED — a libSystem não
     * preserva o {@code %rdi} através do {@code write}.
     */
    @Test
    void theAppendedDescriptorSurvivesTheWriteCall() {
        String append = body(runtime("macos"), "kof_io_append_text");
        assertTrue(append.contains("movq %rax, %r13"),
                "the descriptor is kept in a callee-saved register:\n" + append);
        assertTrue(append.contains("movq %r13, %rdi"),
                "the close takes the descriptor from there:\n" + append);
        assertFalse(append.contains("movq %rdi, %rdi"),
                "the no-op that assumed %rdi survives the call is gone:\n" + append);
    }

    /**
     * O {@code open} e o {@code delete} do {@code io} passam pela costura — no
     * Darwin não há syscall.
     */
    @Test
    void theIoWriteFacesGoThroughTheSeam() {
        String asm = runtime("macos");
        String append = body(asm, "kof_io_append_text");
        assertTrue(append.contains("call kof_plat_open"),
                "the open goes through the seam:\n" + append);
        assertFalse(append.contains("syscall"),
                "no raw syscall left at the append site:\n" + append);

        String delete = body(asm, "kof_io_delete");
        assertTrue(delete.contains("call kof_plat_unlink"),
                "the unlink goes through the seam:\n" + delete);
        assertTrue(delete.contains("call kof_plat_rmdir"),
                "the rmdir goes through the seam:\n" + delete);
        assertFalse(delete.contains("syscall"),
                "no raw syscall left at the delete site:\n" + delete);
    }

    /**
     * Os corpos do Darwin: {@code unlink}/{@code rmdir} da libSystem, com a
     * falha normalizada em {@code -errno} (o kernel devolve -errno; a libc
     * devolve -1 e põe o errno num slot).
     */
    @Test
    void theDarwinUnlinkAndRmdirNormalizeTheErrno() {
        String asm = runtime("macos");
        String unlink = body(asm, "kof_plat_unlink");
        assertTrue(unlink.contains("call unlink"),
                "the Darwin unlink is the libSystem's:\n" + unlink);
        assertTrue(unlink.contains("call __error"),
                "the errno comes from int *__error(void):\n" + unlink);
        assertTrue(unlink.contains("negl %eax"),
                "the failure is normalized to -errno:\n" + unlink);

        String rmdir = body(asm, "kof_plat_rmdir");
        assertTrue(rmdir.contains("call rmdir"),
                "the Darwin rmdir is the libSystem's:\n" + rmdir);
        assertTrue(rmdir.contains("negl %eax"),
                "the failure is normalized to -errno:\n" + rmdir);
    }

    /**
     * O perfil do HOST (Linux) mantem o contrato do syscall.
     */
    @Test
    void theLinuxIoWriteFacesKeepTheSyscallContract() {
        String asm = runtime(null);
        String append = body(asm, "kof_io_append_text");
        assertTrue(append.contains("call kof_plat_open"),
                "the open goes through the seam:\n" + append);
        assertTrue(append.contains("movq %rax, %r13"),
                "the descriptor is kept in a callee-saved register:\n" + append);
        String delete = body(asm, "kof_io_delete");
        assertTrue(delete.contains("call kof_plat_unlink"),
                "the unlink goes through the seam:\n" + delete);
    }

    /**
     * O ASSEMBLER REAL aceita a família inteira no perfil macOS.
     */
    @Test
    void theRealMachOAssemblerAcceptsTheIoWriteFaces(@TempDir Path tmp) throws Exception {
        Path zig = machoToolchain();
        org.junit.jupiter.api.Assumptions.assumeTrue(zig != null,
                "the Mach-O toolchain is absent (scripts/provision-macho-toolchain.sh)");
        String macho = dev.kof.compiler.nat.NativeMachO.translate(runtime("macos"));
        // o FRAGMENTO que esta fatia mudou: o resto do runtime ainda tem
        // faces ELF-only sem forma Mach-O (o enderecamento absoluto-indexado
        // e' a proxima linha do tradutor, NOMEADA em known-bugs). O assembler
        // aceita simbolos indefinidos — o objeto fecha com os nomes externos,
        // como o link resolveria.
        StringBuilder withoutElfOnly = new StringBuilder();
        for (String label : List.of("kof_plat_open", "kof_plat_unlink", "kof_plat_rmdir",
                "kof_io_append_text", "kof_io_delete")) {
            withoutElfOnly.append(body(macho, label));
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
                "the Mach-O assembler must accept the io write faces:\n" + output);
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
