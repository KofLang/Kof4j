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
 * M-6 (#1135, D-NATIVE-MACOS) — a família de REDE da costura sobre o
 * libSystem.
 *
 * <p>A ABI dos sockets do BSD é a MESMA da libc Linux
 * ({@code socket(domain, type, protocol)}, {@code connect(fd, addr, len)},
 * {@code bind}, {@code listen(fd, backlog)}, {@code accept(fd, addr, lenp)}
 * — os mesmos registradores SysV). A ÚNICA diferença é a 4ª flag do
 * {@code sendto}: o syscall Linux a põe em {@code %r10}; a ABI SysV, em
 * {@code %rcx} — o corpo do macOS move antes de chamar.
 *
 * <p><b>TRANSPARENTE:</b> os corpos preservam os caller-saved (só o
 * {@code %rax} — o retorno — muda), então os chamadores do runtime (escritos
 * contra o syscall) continuam certos.
 */
class NativeMacOSNetE2ETest {

    /** Um programa que puxa a rede (o runtime net + a costura). */
    private static final String NET_PROGRAM = """
            main() {
                var r = http.get("http://127.0.0.1:1/x")
                println(r.size)
            }
            """;

    private static String macosAssembly(Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, NET_PROGRAM);
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

    /** O corpo de uma função (do rótulo até o próximo rótulo global). */
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
     * A REDE fala o libSystem — e cada função da costura tem o corpo da libc
     * (e nenhum syscall Linux cru).
     */
    @Test
    void theNetCallsTheLibSystem(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        String[][] expected = {
                {"kof_plat_net_socket", "socket"},
                {"kof_plat_net_connect", "connect"},
                {"kof_plat_net_bind", "bind"},
                {"kof_plat_net_listen", "listen"},
                {"kof_plat_net_accept", "accept"},
                {"kof_plat_net_send", "sendto"},
        };
        for (String[] e : expected) {
            String body = codeBlock(asm, e[0]);
            assertTrue(body.contains("call " + e[1]),
                    e[0] + " must call " + e[1] + ": " + body);
            assertFalse(body.contains("syscall"),
                    "no Linux syscall in " + e[0] + ": " + body);
        }
    }

    /** O corpo sem os comentários (o teste olha o CÓDIGO). */
    private static String codeBlock(String asm, String label) {
        String body = block(asm, label);
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            int hash = line.indexOf('#');
            sb.append(hash >= 0 ? line.substring(0, hash) : line).append('\n');
        }
        return sb.toString();
    }

    /**
     * A 4ª flag do {@code sendto}: o syscall Linux a põe em {@code %r10}; a
     * ABI SysV, em {@code %rcx} — o corpo move.
     */
    @Test
    void theSendtoMovesTheFlagsToTheSysVRegister(@TempDir Path tmp) throws IOException {
        String body = codeBlock(macosAssembly(tmp), "kof_plat_net_send");
        assertTrue(body.contains("movq %r10, %rcx"),
                "the sendto flags move from %r10 to %rcx: " + body);
    }

    /**
     * O ASSEMBLER REAL aceita a família — e o objeto carrega os símbolos da
     * libSystem.
     */
    @Test
    void theRealMachOAssemblerAcceptsTheNetFamily(@TempDir Path tmp) throws Exception {
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
                "the Mach-O assembler must accept the net family:\n" + output);
        String blob = new String(Files.readAllBytes(obj),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(blob.contains("_socket"),
                "the object carries the libSystem symbol: " + output);
        assertTrue(blob.contains("_sendto"),
                "the object carries the libSystem symbol: " + output);
    }

    /**
     * O toolchain Mach-O provisionado ({@code scripts/provision-macho-toolchain.sh}
     * → {@code ~/.local/share/kof-macho/bin/zig}), ou null.
     */
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
