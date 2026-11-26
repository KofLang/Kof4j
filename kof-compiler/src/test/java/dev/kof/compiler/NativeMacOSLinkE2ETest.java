package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-7 (#1135, D-NATIVE-MACOS) — o LINK do runtime do perfil macOS, medido
 * NESTE host com o cross-toolchain do zig (o linker Mach-O dele).
 *
 * <p><b>O QUE ESTE TESTE PROVA (e o que não):</b> o texto do runtime do
 * perfil macOS — com as faces ELF-only removidas (a etapa M-2/M-4) e com um
 * STUB mínimo do lado do PROGRAMA — é montado e LINKADO num executável
 * Mach-O de verdade. A EXECUÇÃO continua precisando do host macos-14 (o
 * runner M-7): este teste mede a cadeia de ferramentas (montagem + link +
 * o formato do artefato), não o comportamento em runtime.
 *
 * <p><b>O STUB é nomeado:</b> os símbolos do lado do PROGRAMA (as tabelas de
 * métodos, a raiz do heap, os literais {@code true}/{@code false}) só existem
 * no texto do PROGRAMA — que o perfil macOS ainda não emite enquanto as
 * famílias de syscall cru não forem migradas (a recusa NATIVE005). O link do
 * RUNTIME é o que é medido aqui.
 *
 * <p><b>OS DEFEITOS QUE ESTE LINK JÁ MEDIU (e o tradutor corrigiu):</b> o
 * {@code .lcomm} do GNU não tem forma Mach-O (o símbolo do
 * {@code vk64_scratch} ficava sem DEFINIÇÃO) — virou {@code .zerofill}; e o
 * {@code __error} (o nome C que já começa com '_') precisava do prefixo
 * EXTRA ("undefined symbol: __error" sem a correção).
 */
class NativeMacOSLinkE2ETest {

    /** Os símbolos do lado do PROGRAMA, com o tamanho mínimo de cada um. */
    private static final String PROGRAM_STUB = """
            .section __DATA,__data
            _kof_super_table:   .quad 0
            _kof_equals_table:  .quad 0
            _kof_tostring_table: .quad 0
            _kof_hashcode_table: .quad 0
            _kof_heap_root_start: .quad 0
            _end:               .quad 0
            .section __TEXT,__const
            .Lkof_str_true:  .asciz "true"
            .Lkof_str_false: .asciz "false"
            .Lnewline:      .asciz "\n"
            .Lsch_type_registry: .quad 0
            .section __TEXT,__text,regular,pure_instructions
            .globl _main
            _main:
                xorl %eax, %eax
                ret
            """;

    private static String runtimeLinkText() {
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            dev.kof.compiler.nat.NativeProfile.active =
                    dev.kof.compiler.nat.NativeProfile.of("macos");
            String macho = dev.kof.compiler.nat.NativeMachO.translate(
                    dev.kof.compiler.NativeRuntime.generateRuntimeAssembly());
            StringBuilder out = new StringBuilder();
            for (String line : macho.split("\n", -1)) {
                String t = line.trim();
                if (t.equals("syscall") || t.contains("%fs:") || t.contains("@tpoff")) continue;
                out.append(line).append('\n');
            }
            return out.toString();
        } finally {
            dev.kof.compiler.nat.NativeProfile.active = previous;
        }
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

    /**
     * O LINK do runtime do perfil macOS produz um executável Mach-O de
     * verdade (o magic 0xFEEDFACF — o MH_MAGIC_64 do x86-64).
     */
    @Test
    void theMacosRuntimeLinksIntoAMachOExecutable(@TempDir Path tmp) throws Exception {
        Path zig = machoToolchain();
        org.junit.jupiter.api.Assumptions.assumeTrue(zig != null,
                "the Mach-O toolchain is absent (scripts/provision-macho-toolchain.sh)");
        Path asmFile = tmp.resolve("Runtime.macho.s");
        String texto = PROGRAM_STUB + "\n" + runtimeLinkText();
        Files.writeString(asmFile, texto);
        Files.writeString(Path.of("/tmp/link.s"), texto);
        Path bin = tmp.resolve("Runtime.macho");
        ProcessBuilder pb = new ProcessBuilder(
                zig.toString(), "cc", "-target", "x86_64-macos-none",
                asmFile.toString(), "-o", bin.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes());
        int rc = proc.waitFor();
        assertEquals(0, rc,
                "the runtime of the macOS profile must LINK end-to-end:\n" + output);
        byte[] bytes = Files.readAllBytes(bin);
        assertTrue(bytes.length > 1024,
                "the Mach-O executable is not empty (" + bytes.length + " bytes)");
        // MH_MAGIC_64 = FE ED FA CF (little-endian: cf fa ed fe)
        assertTrue((bytes[0] & 0xFF) == 0xCF && (bytes[1] & 0xFF) == 0xFA
                        && (bytes[2] & 0xFF) == 0xED && (bytes[3] & 0xFF) == 0xFE,
                "the artifact is a Mach-O 64-bit executable (the magic 0xFEEDFACF)");
    }
}
