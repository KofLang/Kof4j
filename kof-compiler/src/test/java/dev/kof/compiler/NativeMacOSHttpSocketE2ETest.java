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
 * M-6 (#1135, D-NATIVE-MACOS) — a família HTTP/SOCKETS do {@code kof.http}
 * pela costura.
 *
 * <p><b>O DEFEITO (medido):</b> o core do {@code kof.http} abria, configurava
 * e esperava o socket com CINCO syscalls crus — {@code fcntl=72} com
 * {@code O_NONBLOCK=2048}, {@code connect=42}, {@code poll=7},
 * {@code getsockopt=55}/{@code setsockopt=54} com {@code SOL_SOCKET=1} e
 * {@code SO_RCVTIMEO=20}/{@code SO_SNDTIMEO=21}/{@code SO_ERROR=4}. No Darwin
 * todo esse contrato difere (medido no SDK): {@code O_NONBLOCK=4},
 * {@code SOL_SOCKET=0xffff}, {@code SO_RCVTIMEO=0x1006},
 * {@code SO_SNDTIMEO=0x1005}, {@code SO_ERROR=0x1007} — e o {@code errno}
 * vem do {@code int *__error(void)}, não do retorno do syscall (o
 * {@code EINPROGRESS} do Darwin é 36, não 115).
 *
 * <p>A correção é a família {@code kof_plat_connect}/{@code nonblock}/{@code
 * poll}/{@code sock_timeout}/{@code sock_error} da costura: os sítios só
 * trocaram "movq $N, %rax; syscall" por "call", e o corpo do macOS carrega as
 * constantes medidas e NORMALIZA o retorno ({@code -errno}, com o
 * {@code EINPROGRESS} traduzido para o valor do contrato).
 */
class NativeMacOSHttpSocketE2ETest {

    /** Um programa que usa o kof.http (puxa o runtime web). */
    private static final String HTTP_PROGRAM = """
            main() {
                var r = http.get("http://127.0.0.1:1/x")
                println(r.size)
            }
            """;

    private static String assembly(Path tmp, String profile) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, HTTP_PROGRAM);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setNativeProfile(profile == null
                    ? dev.kof.compiler.nat.NativeProfile.active
                    : dev.kof.compiler.nat.NativeProfile.of(profile));
            driver.compile(src, tmp.resolve("out-" + (profile == null ? "host" : profile)),
                    Target.NATIVE);
            return Files.readString(tmp.resolve("out-" + (profile == null ? "host" : profile)
                    + "/Default/Main.s"));
        } finally {
            dev.kof.compiler.nat.NativeProfile.active = previous;
            if (priorKeep == null) {
                System.clearProperty("KOF_KEEP_ASM");
            } else {
                System.setProperty("KOF_KEEP_ASM", priorKeep);
            }
        }
    }

    /**
     * O corpo de uma função: do rótulo {@code label:} até a próxima linha
     * ".section" (o limite de cada família) ou um rótulo global.
     */
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

    private static int indexOf(String text, String needle) {
        int i = text.indexOf(needle);
        assertTrue(i >= 0, "expected '" + needle + "' in:\n" + text);
        return i;
    }

    /**
     * O CORE do kof.http não faz mais syscall de socket: os cinco sítios
     * passaram para a costura.
     */
    @Test
    void theHttpCoreSocketSitesGoThroughTheSeam(@TempDir Path tmp) throws IOException {
        String core = body(assembly(tmp, "macos"), "kof_http_core");
        assertTrue(core.contains("call kof_plat_nonblock"),
                "the nonblocking switch goes through the seam:\n" + core);
        assertTrue(core.contains("call kof_plat_connect"),
                "the connect goes through the seam:\n" + core);
        assertTrue(core.contains("call kof_plat_poll"),
                "the poll goes through the seam:\n" + core);
        assertTrue(core.contains("call kof_plat_sock_error"),
                "the SO_ERROR probe goes through the seam:\n" + core);
        assertTrue(core.contains("call kof_plat_sock_timeout"),
                "the read/write timeouts go through the seam:\n" + core);
        assertFalse(core.contains("syscall"),
                "no raw socket syscall left in the http core:\n" + core);
        for (String linuxOnly : List.of("movq $42, %rax", "movq $72, %rax",
                "movq $7, %rax", "movq $54, %eax", "movq $55, %eax",
                "movl $2048, %edx", "movl $20, %edx", "movl $21, %edx")) {
            assertFalse(core.contains(linuxOnly),
                    "the Linux-only form '" + linuxOnly + "' does not leak into the core:\n" + core);
        }
    }

    /**
     * Os corpos do macOS carregam as constantes MEDIDAS no SDK, não as do
     * Linux.
     */
    @Test
    void theDarwinBodiesCarryTheMeasuredConstants(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp, "macos");

        String nonblock = body(asm, "kof_plat_nonblock");
        assertTrue(nonblock.contains("movl $4, %edx"),
                "O_NONBLOCK is 0x4 on Darwin (0x800 on Linux):\n" + nonblock);
        assertFalse(nonblock.contains("2048"),
                "the Linux O_NONBLOCK does not leak:\n" + nonblock);

        String timeout = body(asm, "kof_plat_sock_timeout");
        assertTrue(timeout.contains("movl $0xffff, %esi"),
                "SOL_SOCKET is 0xffff on Darwin (1 on Linux):\n" + timeout);
        assertTrue(timeout.contains("movl $0x1006, %edx"),
                "SO_RCVTIMEO is 0x1006 on Darwin (20 on Linux):\n" + timeout);
        assertTrue(timeout.contains("movl $0x1005, %edx"),
                "SO_SNDTIMEO is 0x1005 on Darwin (21 on Linux):\n" + timeout);

        String error = body(asm, "kof_plat_sock_error");
        assertTrue(error.contains("movl $0x1007, %edx"),
                "SO_ERROR is 0x1007 on Darwin (4 on Linux):\n" + error);
        assertTrue(error.contains("movl $0xffff, %esi"),
                "SOL_SOCKET is 0xffff on Darwin:\n" + error);
        assertTrue(error.contains("leaq 4(%rsp), %r8"),
                "libSystem's getsockopt wants a POINTER to the socklen:\n" + error);
    }

    /**
     * Os sítios do seletor testam o argumento ANTES de sobrescrever o
     * registrador (o defeito que este teste congela) e o {@code fcntl}
     * variádico recebe {@code %al=0}.
     */
    @Test
    void theSelectorIsTestedBeforeTheRegisterIsClobbered(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp, "macos");

        String nonblock = body(asm, "kof_plat_nonblock");
        assertTrue(indexOf(nonblock, "testq %rsi, %rsi")
                        < indexOf(nonblock, "movl $4, %esi"),
                "the 0/1 selector is tested before F_SETFL overwrites %esi:\n" + nonblock);
        assertTrue(nonblock.contains("xorl %eax, %eax"),
                "fcntl is variadic: AL must hold the XMM count:\n" + nonblock);

        String timeout = body(asm, "kof_plat_sock_timeout");
        assertTrue(timeout.contains("testq %r12, %r12"),
                "the recv/send selector (saved at entry) is what is tested:\n" + timeout);
        assertFalse(timeout.contains("testq %rdx, %rdx"),
                "the seconds are NOT the selector (the old bug):\n" + timeout);
        assertTrue(timeout.contains("movq %rsp, %rcx"),
                "the timeval address is taken AFTER the stack alignment:\n" + timeout);
    }

    /**
     * O errno do Darwin é normalizado para o contrato da costura: o
     * {@code EINPROGRESS=36} vira {@code 115}, o valor que o sítio do core
     * compara.
     */
    @Test
    void theInProgressErrnoIsNormalizedToTheSeamContract(@TempDir Path tmp) throws IOException {
        String connect = body(assembly(tmp, "macos"), "kof_plat_connect");
        assertTrue(connect.contains("call __error"),
                "the Darwin errno comes from int *__error(void):\n" + connect);
        assertTrue(connect.contains("cmpq $-36, %rax"),
                "EINPROGRESS is 36 on Darwin:\n" + connect);
        assertTrue(connect.contains("movq $-115, %rax"),
                "the seam normalizes it to 115 (the contract value):\n" + connect);
    }

    /**
     * O perfil do HOST (Linux) mantém o contrato do syscall — as constantes
     * Linux e o seletor correto.
     */
    @Test
    void theLinuxBodiesKeepTheSyscallContract(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp, null);

        String connect = body(asm, "kof_plat_connect");
        assertTrue(connect.contains("movq $42, %rax"),
                "the Linux connect is the syscall 42:\n" + connect);

        String nonblock = body(asm, "kof_plat_nonblock");
        assertTrue(indexOf(nonblock, "testq %rsi, %rsi")
                        < indexOf(nonblock, "movl $4, %esi"),
                "the Linux body also tests the selector first:\n" + nonblock);
        assertTrue(nonblock.contains("movl $2048, %edx"),
                "O_NONBLOCK is 2048 on Linux:\n" + nonblock);

        String timeout = body(asm, "kof_plat_sock_timeout");
        assertTrue(indexOf(timeout, "testq %rsi, %rsi")
                        < indexOf(timeout, "movl $1, %esi"),
                "the Linux body tests the recv/send selector, not the seconds:\n" + timeout);
        assertTrue(timeout.contains("movl $20, %edx"),
                "SO_RCVTIMEO is 20 on Linux:\n" + timeout);
        assertTrue(timeout.contains("movl $21, %edx"),
                "SO_SNDTIMEO is 21 on Linux:\n" + timeout);

        String error = body(asm, "kof_plat_sock_error");
        assertTrue(error.contains("movl $55, %eax"),
                "the Linux SO_ERROR probe is the syscall 55:\n" + error);
        assertTrue(error.contains("movl $4, %edx"),
                "SO_ERROR is 4 on Linux:\n" + error);
    }

    /**
     * O ASSEMBLER REAL aceita a família inteira no perfil macOS (traduzida
     * para Mach-O; as faces ELF-only ficam de fora, como no teste do relógio).
     */
    @Test
    void theRealMachOAssemblerAcceptsTheHttpSocketFamily(@TempDir Path tmp) throws Exception {
        Path zig = machoToolchain();
        org.junit.jupiter.api.Assumptions.assumeTrue(zig != null,
                "the Mach-O toolchain is absent (scripts/provision-macho-toolchain.sh)");
        String macho = dev.kof.compiler.nat.NativeMachO.translate(assembly(tmp, "macos"));
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
                "the Mach-O assembler must accept the http socket family:\n" + output);
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
