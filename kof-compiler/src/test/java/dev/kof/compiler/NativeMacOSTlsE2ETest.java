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
 * M-4 (#1135, D-NATIVE-MACOS) — a cadeia de exceções (o TLS) pela costura.
 *
 * <p><b>A LINHA M-4:</b> o runtime Native guarda a cadeia de exceções
 * numa variável por thread ({@code kof_exc_chain}), lida pelo TLS ELF
 * local-exec ({@code %fs:kof_exc_chain@tpoff}) — uma face que o Mach-O
 * NÃO tem. A costura ganha um par <b>get/set</b> transparente (o get
 * devolve o valor em {@code %rax} e preserva todos os outros registradores;
 * o set recebe o valor em {@code %rdi} e preserva tudo) e o perfil macOS
 * implementa o par por uma <b>pthread key</b> (API pública do SDK:
 * {@code pthread_key_create}/{@code pthread_getspecific}/
 * {@code pthread_setspecific} — a chave nasce no startup, single-thread).
 *
 * <p><b>O QUE ESTA FATIA PROVA:</b> os LOADS da cadeia (9 sítios) passam
 * pela costura — e o corpo do macOS é o da libSystem (o objeto carrega
 * {@code _pthread_getspecific}/{@code _pthread_setspecific}/
 * {@code _pthread_key_create}). Os STORES (13 sítios) ainda falam o TLS ELF
 * — a próxima fatia da linha, porque neles o valor precisa chegar num
 * registrador que o site possa ceder (a auditoria mediu: o {@code %rdi} e o
 * {@code %rax} têm valores VIVOS nesses pontos).
 */
class NativeMacOSTlsE2ETest {

    /** Um programa que puxa a máquina de exceções (a cadeia de exceções). */
    private static final String EXC_PROGRAM = """
            main() {
                try {
                    val x = Integer.parseInt("abc")
                    println(x)
                } catch (String e) {
                    println("caught")
                }
            }
            """;

    /** O texto emitido no alvo nativo (perfil host). */
    private static String nativeAssembly(Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, EXC_PROGRAM);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        try {
            CompilerDriver driver = new CompilerDriver();
            CompilationResult host = driver.compile(src, tmp.resolve("out"), Target.NATIVE);
            assertTrue(host.success(), "the native compile succeeds: "
                    + host.diagnostics().getDiagnostics());
            return Files.readString(tmp.resolve("out/Default/Main.s"));
        } finally {
            if (priorKeep == null) {
                System.clearProperty("KOF_KEEP_ASM");
            } else {
                System.setProperty("KOF_KEEP_ASM", priorKeep);
            }
        }
    }

    /** O texto emitido no perfil macOS. */
    private static String macosAssembly(Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, EXC_PROGRAM);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setNativeProfile(dev.kof.compiler.nat.NativeProfile.of("macos"));
            driver.compile(src, tmp.resolve("outmac"), Target.NATIVE);
            return Files.readString(tmp.resolve("outmac/Default/Main.s"));
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
     * A LEITURA da cadeia passa pela costura — o runtime não lê mais o TLS
     * ELF diretamente.
     */
    @Test
    void theChainLoadsGoThroughTheSeam(@TempDir Path tmp) throws IOException {
        String asm = nativeAssembly(tmp);
        assertTrue(asm.contains("call kof_plat_tls_get"),
                "the chain loads go through the seam");
        // nenhum acesso cru da LEITURA sobra no runtime — o que resta são o
        // corpo da costura (o TLS ELF do Linux) e a máquina de try/catch do
        // EMITTER (a próxima fatia da linha M-4)
        String runtime = asm.replace(block(asm, "kof_plat_tls_get"), "")
                .replace(block(asm, "Default_Main_main"), "");
        assertFalse(runtime.contains("movq %fs:kof_exc_chain@tpoff, %rax"),
                "no raw TLS load left in the runtime: " + runtime);
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

    /** Quantas vezes o texto contém o fragmento. */
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
     * O CORPO macOS da costura é a libSystem (as três APIs públicas do SDK).
     */
    @Test
    void theMacosChainUsesThePthreadKey(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        assertTrue(asm.contains("call pthread_getspecific"),
                "the chain read is a pthread_getspecific: " + asm);
        assertTrue(asm.contains("call pthread_setspecific"),
                "the chain write is a pthread_setspecific: " + asm);
        assertTrue(asm.contains("call pthread_key_create"),
                "the key is created at the startup: " + asm);
        // a chave nasce NO STARTUP (single-thread) — não há corrida de criação
        assertTrue(asm.contains("call kof_plat_tls_init"),
                "the startup creates the key: " + asm);
    }

    /**
     * O MARCO DA LINHA M-4: o texto do perfil macOS não tem NENHUM acesso ao
     * TLS ELF ({@code %fs:}) — a face ELF-only sumiu, e o gate NATIVE005 fica
     * MUDO (a única recusa que resta é a NATIVE004 = o toolchain do M-7).
     */
    @Test
    void theMacosProfileHasNoElfTlsLeft(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        assertFalse(asm.contains("%fs:"),
                "no ELF TLS left in the macOS profile: " + asm);
        assertFalse(asm.contains("@tpoff"),
                "no local-exec TLS relocation left: " + asm);
    }

    /**
     * O ASSEMBLER REAL aceita o corpo da costura — e o objeto carrega os
     * símbolos da libSystem.
     */
    @Test
    void theRealMachOAssemblerAcceptsTheTlsBody(@TempDir Path tmp) throws Exception {
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
                "the Mach-O assembler must accept the TLS body:\n" + output);
        String blob = new String(Files.readAllBytes(obj),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(blob.contains("_pthread_getspecific"),
                "the object carries the libSystem symbol: " + output);
        assertTrue(blob.contains("_pthread_setspecific"),
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
