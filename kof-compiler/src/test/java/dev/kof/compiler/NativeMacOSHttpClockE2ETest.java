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
 * M-6 (#1135, D-NATIVE-MACOS) — o relógio do {@code kof.http} pela costura.
 *
 * <p><b>O DEFEITO (medido):</b> o {@code kof_http_now_ms} (o relógio
 * monotônico do circuit-breaker do kof.http) fazia o syscall {@code
 * clock_gettime=228} CRU com o {@code CLOCK_MONOTONIC=1} — o id do LINUX. No
 * Darwin o id é <b>6</b> (medido no SDK): o corpo cru teria pedido outro
 * relógio (ou falhado).
 *
 * <p>A correção é o relógio pela costura {@code kof_plat_time_mono} — que já
 * sabe o id de cada plataforma (1 no Linux, 6 no Darwin) — sem duplicar a
 * matemática dos milissegundos.
 */
class NativeMacOSHttpClockE2ETest {

    /** Um programa que usa o kof.http (puxa o runtime web). */
    private static final String HTTP_PROGRAM = """
            main() {
                var r = http.get("http://127.0.0.1:1/x")
                println(r.size)
            }
            """;

    private static String macosAssembly(Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, HTTP_PROGRAM);
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
     * O RELÓGIO do kof.http passa pela costura — o corpo cru (o syscall 228
     * com o id 1 do Linux) saiu.
     */
    @Test
    void theHttpClockGoesThroughTheSeam(@TempDir Path tmp) throws IOException {
        String body = block(macosAssembly(tmp), "kof_http_now_ms");
        assertTrue(body.contains("call kof_plat_time_mono"),
                "the http clock goes through the seam: " + body);
        assertFalse(body.contains("movl $228, %eax"),
                "no raw clock_gettime syscall left: " + body);
        assertFalse(body.contains("movl $1, %edi"),
                "the Linux CLOCK id does not leak into the macOS profile: " + body);
    }

    /**
     * A costura do relógio MONOTÔNICO é a que knows o id de cada plataforma:
     * 6 no Darwin (medido no SDK), 1 no Linux.
     */
    @Test
    void theMonoClockSeamCarriesTheDarwinClockId(@TempDir Path tmp) throws IOException {
        String body = block(macosAssembly(tmp), "kof_plat_time_mono");
        assertTrue(body.contains("movq $6, %rdi"),
                "CLOCK_MONOTONIC is 6 on Darwin: " + body);
        assertTrue(body.contains("call clock_gettime"),
                "the monotonic clock is the libSystem's: " + body);
    }

    /**
     * O ASSEMBLER REAL aceita o texto do kof.http no perfil macOS.
     */
    @Test
    void theRealMachOAssemblerAcceptsTheHttpClock(@TempDir Path tmp) throws Exception {
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
                "the Mach-O assembler must accept the http clock:\n" + output);
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
