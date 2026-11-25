package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-6 (#1135, D-NATIVE-MACOS) — o RUNTIME INTEIRO no assembler do Mach-O.
 *
 * <p><b>O QUE ESTE TESTE MEDE:</b> o texto do runtime inteiro, traduzido
 * para Mach-O, é montado pelo assembler real. As faces ELF-only (o syscall
 * cru, o TLS local-exec) saem — elas são a etapa M-2/M-4, nomeadas pela recusa
 * NATIVE005. O resto tem de ASSEMBLAR.
 *
 * <p><b>A REGRA DA PROVA (honesta):</b> enquanto a face Vulkan ainda usar o
 * endereçamento ABSOLUTO com base (`sym+off(%reg)` — que o Mach-O de 64 bits
 * recusa), a montagem falha SÓ por ela. O teste aceita a falha, mas exige
 * que TODA linha de erro nomeie a face Vk — qualquer outro erro (uma face
 * esquecida) reprova.
 */
class NativeMacOSRuntimeAssemblyE2ETest {

    /** As faces ELF-only que o perfil macOS ainda não tem (a etapa M-2/M-4). */
    private static String withoutElfOnly(String macho) {
        StringBuilder out = new StringBuilder();
        for (String line : macho.split("\n", -1)) {
            String t = line.trim();
            if (t.equals("syscall") || t.contains("%fs:") || t.contains("@tpoff")) continue;
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String runtime(String profile) {
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            dev.kof.compiler.nat.NativeProfile.active = profile == null
                    ? previous
                    : dev.kof.compiler.nat.NativeProfile.of(profile);
            return dev.kof.compiler.NativeRuntime.generateRuntimeAssembly();
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
     * O runtime inteiro monta no Mach-O — e, enquanto a face Vulkan não for
     * reescrita, a falha tem de ser SÓ dela.
     */
    @Test
    void theWholeRuntimeAssemblesExceptTheNamedVkFace(@TempDir Path tmp) throws Exception {
        Path zig = machoToolchain();
        org.junit.jupiter.api.Assumptions.assumeTrue(zig != null,
                "the Mach-O toolchain is absent (scripts/provision-macho-toolchain.sh)");
        String macho = dev.kof.compiler.nat.NativeMachO.translate(runtime("macos"));
        Path asmFile = tmp.resolve("Runtime.macho.s");
        Files.writeString(asmFile, withoutElfOnly(macho));
        Path obj = tmp.resolve("Runtime.macho.o");
        ProcessBuilder pb = new ProcessBuilder(
                zig.toString(), "cc", "-target", "x86_64-macos-none",
                "-c", asmFile.toString(), "-o", obj.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes());
        int rc = proc.waitFor();
        if (rc != 0) {
            // a falha tem de ser a face NOMEADA (a Vk) — e nada mais
            // a linha do erro NAO traz o trecho — a fonte vem na linha
            // seguinte (o assembler imprime a linha e o cursor)
            String[] outLines = output.split("\n");
            List<String> errors = new ArrayList<>();
            for (int i = 0; i < outLines.length; i++) {
                if (!outLines[i].contains("error:")) continue;
                String trecho = (i + 1 < outLines.length) ? outLines[i + 1] : "";
                errors.add(trecho.trim());
            }
            List<String> fora = new ArrayList<>();
            for (String e : errors) {
                // a face Vk nomeada: a familia de simbolos `vk64_*`
                if (!e.contains("vk64_")) fora.add(e);
            }
            assertTrue(fora.isEmpty(),
                    "only the named Vk face may fail to assemble; the others:\\n" + String.join("\n", fora)
                            + "\n--- the full output:\n" + output);
        }
        assertTrue(Files.exists(obj), "the Mach-O object was produced");
    }
}
