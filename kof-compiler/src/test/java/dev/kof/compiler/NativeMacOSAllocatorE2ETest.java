package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-3 (#1135, D-NATIVE-MACOS) — o ALLOCATOR sobre a libSystem: a arena do
 * Kof cresce por {@code mmap}/{@code munmap} (a etapa M-3 do plano).
 *
 * <p><b>POR QUE ESTA FATIA É MAIS QUE UM CALL:</b> o {@code mmap} do Linux
 * (syscall 9) e o {@code mmap} da libSystem do Darwin diferem em TRÊS
 * pontos, e os três foram MEDIDOS nos headers do SDK do macOS que o
 * toolchain provisionado traz:
 * <ol>
 *   <li><b>A 4ª flag vai em {@code %rcx}</b> na libSystem (a ABI SysV); o
 *       syscall Linux põe a 4ª flag em {@code %r10};</li>
 *   <li><b>As flags são outras:</b> {@code MAP_PRIVATE|MAP_ANON|MAP_NORESERVE}
 *       = {@code 0x2|0x1000|0x40} = <b>{@code 0x1042}</b> no Darwin — no
 *       Linux é {@code 0x4022} (o {@code MAP_ANONYMOUS} do Linux é 0x20 e o
 *       {@code MAP_NORESERVE} é 0x4000);</li>
 *   <li><b>O erro é {@code MAP_FAILED}</b> = {@code (void*)-1} exato no
 *       Darwin; o syscall Linux devolvia {@code -errno} em
 *       {@code [-4095,-1]}.</li>
 * </ol>
 *
 * <p>Copiar o corpo host seria um bug silencioso: a arena cresceria com as
 * flags erradas (o {@code MAP_ANON} do Linux não existe no Darwin) e o
 * tratamento de erro leria o retorno errado.
 */
class NativeMacOSAllocatorE2ETest {

    /** O hello real: o allocator entra no texto (a arena é o primeiro alloc). */
    private static final String HELLO = """
            main() {
                println("hi")
            }
            """;

    /** O texto emitido no perfil macOS (o mesmo Shape do teste da costura). */
    private static String macosAssembly(Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, HELLO);
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

    /** O corpo de uma função (do rótulo até o próximo rótulo GLOBAL — os
     *  rótulos locais `.L*` ficam dentro do corpo). */
    private static String body(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder block = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(label + ":")) {
                inside = true;
                continue;
            }
            if (inside && trimmed.endsWith(":") && !trimmed.contains(" ")
                    && !trimmed.startsWith(".")) break;
            if (inside) block.append(line).append('\n');
        }
        return block.toString();
    }

    /** O corpo SEM os comentários (o teste olha o CÓDIGO, não a prosa). */
    private static String code(String body) {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            int hash = line.indexOf('#');
            sb.append(hash >= 0 ? line.substring(0, hash) : line).append('\n');
        }
        return sb.toString();
    }

    /**
     * O ALLOCATOR fala a libSystem — o syscall Linux cru saiu do corpo.
     */
    @Test
    void theAllocatorCallsTheLibSystemMmap(@TempDir Path tmp) throws IOException {
        String grow = code(body(macosAssembly(tmp), "kof_plat_heap_grow"));
        assertTrue(grow.contains("call mmap"),
                "the arena grows by the libSystem mmap: " + grow);
        assertTrue(grow.contains("call munmap"),
                "the failed bitmap is released by the libSystem munmap: " + grow);
        assertFalse(grow.contains("syscall"),
                "no Linux syscall in the allocator: " + grow);
    }

    /**
     * A 4ª flag vai em {@code %rcx} (a ABI SysV da libSystem) e as flags são
     * as DO DARWIN ({@code 0x1042}) — não as do Linux ({@code 0x4022} em
     * {@code %r10}).
     */
    @Test
    void theAllocatorUsesTheDarwinFlagsAndTheSysVFourthArgument(@TempDir Path tmp)
            throws IOException {
        String grow = code(body(macosAssembly(tmp), "kof_plat_heap_grow"));
        assertTrue(grow.contains("movq $0x1042, %rcx"),
                "the Darwin flags go in the 4th SysV register: " + grow);
        assertFalse(grow.contains("0x4022"),
                "the Linux flags (0x4022) are not the Darwin ones: " + grow);
        assertFalse(grow.contains("%r10"),
                "the syscall ABI (flags in %r10) is not the libSystem ABI: " + grow);
    }

    /**
     * O ERRO é o {@code MAP_FAILED} do Darwin ({@code -1} exato) — não a
     * faixa de errno do Linux.
     */
    @Test
    void theAllocatorChecksMapFailed(@TempDir Path tmp) throws IOException {
        String grow = code(body(macosAssembly(tmp), "kof_plat_heap_grow"));
        assertTrue(grow.contains("cmpq $-1, %rax"),
                "MAP_FAILED is -1 on Darwin: " + grow);
        assertFalse(grow.contains("cmpq $-4095"),
                "the Linux errno range is not the Darwin error: " + grow);
    }

    /**
     * O RATCHET: os syscalls crus do hello real caíram de 5 para 2 — o
     * allocator saiu, e o que fica é a concorrência (M-5).
     */
    @Test
    void theRawSyscallRatchetFellForTheAllocator(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        List<String> sites = dev.kof.compiler.nat.NativeMachO.elfSyscallSites(asm);
        assertEquals(0, sites.size(),
                "the seam's x86-64 face is ALL libSystem now (the allocator, the "
                        + "thread id and the futex — M-5): " + sites);
        List<String> owners = dev.kof.compiler.nat.NativeMachO.elfSyscallOwners(asm);
        assertFalse(owners.contains("kof_plat_sync"),
                "the futex is libSystem (ulock) now: " + owners);
        assertFalse(owners.contains(".Lgrow_try"),
                "the allocator is libSystem now: " + owners);
    }

    /**
     * O ASSEMBLER REAL aceita o corpo do allocator — e o objeto carrega os
     * símbolos da libSystem ({@code _mmap}/{@code _munmap}), que o link
     * contra a libSystem resolve.
     */
    @Test
    void theRealMachOAssemblerAcceptsTheAllocatorBody(@TempDir Path tmp) throws Exception {
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
                "the Mach-O assembler must accept the allocator body:\n" + output);
        String blob = new String(Files.readAllBytes(obj),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(blob.contains("_mmap"),
                "the object carries the libSystem symbol: " + output);
        assertTrue(blob.contains("_munmap"),
                "the object carries the libSystem symbol: " + output);
    }

    /**
     * O toolchain Mach-O provisionado ({@code scripts/provision-macho-toolchain.sh}
     * → {@code ~/.local/share/kof-macho/bin/zig}), ou null.
     */
    private static Path machoToolchain() {
        String home = System.getenv("KOF_MACHO_HOME");
        List<Path> candidates = new java.util.ArrayList<>();
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
