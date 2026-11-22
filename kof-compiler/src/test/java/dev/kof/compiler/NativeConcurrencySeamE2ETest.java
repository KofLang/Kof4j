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
 * M-5 (#1135, D-NATIVE-MACOS) — a face da CONCORRÊNCIA: o contrato da
 * costura {@code kof_plat_sync} (o futex) e o id da thread no Darwin.
 *
 * <p><b>O CONTRATO DA COSTURA (medido, não suposto):</b> a costura
 * {@code kof_plat_sync(uaddr, op, val, timeout)} é o ABI do futex —
 * {@code uaddr=%rdi}, {@code op=%rsi}, {@code val=%rdx},
 * {@code timeout=%r10} — e o runtime riscv a declara assim
 * ({@code kof_plat_sync(uaddr=a0, op=a1, val=a2, timeout=a3)}). O
 * {@code RuntimeChannel} e o {@code RuntimeLock} honram o contrato (o
 * "bug 50" do repo), mas o {@code kof_alloc} e o scheduler NÃO: os sítios de
 * WAIT passavam o endereço no {@code %rsi} (o registro do OP) e deixavam o
 * {@code %rdi} com um valor velho — o futex recebia um op inválido e
 * devolvia EINVAL, e a espera virava um <b>spin que nunca dorme</b>. E os
 * sítios de WAKE do scheduler acordavam o endereço ERRADO (o ponteiro do
 * job, não o do lock).
 *
 * <p><b>A PROVA É ESTRUTURAL E DETERMINÍSTICA:</b> o teste emittedo
 * percorre o assembly real e, em CADA {@code call kof_plat_sync}, exige o
 * contrato — o op tem que ser uma constante (0 = WAIT, 1 = WAKE), o uaddr
 * tem que ser um endereço e o val uma constante. No código antigo isso falha
 * nos sítios quebrados (e o teste não depende de KINDADE de corrida).
 */
class NativeConcurrencySeamE2ETest {

    /** O hello real: o runtime inteiro (o allocator e o scheduler entram). */
    private static final String HELLO = """
            main() {
                println("hi")
            }
            """;

    /** O texto emitido no alvo nativo (perfil host). */
    private static String nativeAssembly(Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, HELLO);
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
        Files.writeString(src, HELLO);
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

    /** A última instrução que ESCREVE no registrador dado, dentro da função. */
    private static String lastWrite(List<String> lines, int callIndex, String... regs) {
        for (int i = callIndex - 1; i >= 0; i--) {
            String t = lines.get(i).trim();
            int hash = t.indexOf('#');
            if (hash >= 0) t = t.substring(0, hash).trim();
            // o começo de outra função (rótulo global) encerra a busca
            if (t.endsWith(":") && !t.contains(" ") && !t.startsWith(".")) return null;
            String[] parts = t.split("[,\\s]+");
            String dest = parts[parts.length - 1];
            for (String r : regs) {
                if (dest.equals(r)) return t;
            }
        }
        return null;
    }

    /**
     * TODO sítio de {@code kof_plat_sync} honra o contrato do futex:
     * {@code uaddr=%rdi} (um endereço), {@code op=%rsi} (a constante 0 = WAIT
     * ou 1 = WAKE) e {@code val=%rdx} (uma constante).
     */
    @Test
    void everySyncCallSiteHonorsTheFutexContract(@TempDir Path tmp) throws IOException {
        String asm = nativeAssembly(tmp);
        List<String> lines = List.of(asm.split("\n", -1));
        List<String> broken = new ArrayList<>();
        int sites = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).trim().equals("call kof_plat_sync")) continue;
            sites++;
            String op = lastWrite(lines, i, "%rsi", "%esi");
            String uaddr = lastWrite(lines, i, "%rdi", "%edi");
            String val = lastWrite(lines, i, "%rdx", "%edx");
            boolean opOk = op != null && (op.equals("xorl %esi, %esi")
                    || op.equals("movl $1, %esi") || op.equals("xorl %rsi, %rsi")
                    || op.equals("movq $0, %rsi") || op.equals("movl $0, %esi"));
            boolean uaddrOk = uaddr != null && (uaddr.startsWith("leaq ")
                    || uaddr.startsWith("movq %r") || uaddr.startsWith("movq %rb"));
            boolean valOk = val != null && (val.startsWith("movl $") || val.startsWith("xorl %edx")
                    || val.startsWith("movq $") || val.startsWith("movl %e"));
            if (!opOk || !uaddrOk || !valOk) {
                broken.add(lines.get(i).trim() + " [op=" + op + ", uaddr=" + uaddr
                        + ", val=" + val + "]");
            }
        }
        assertTrue(sites > 0, "the program really calls the seam: " + sites);
        assertEquals(List.of(), broken,
                "every kof_plat_sync call site must honor the futex contract "
                        + "(uaddr=%rdi, op=%rsi, val=%rdx)");
    }

    /**
     * O ID DA THREAD no macOS vem da API pública do Darwin
     * ({@code pthread_threadid_np}) — o {@code gettid} do Linux não existe
     * lá.
     */
    @Test
    void theMacosThreadIdCallsTheDarwinApi(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        assertTrue(asm.contains("call pthread_threadid_np"),
                "the thread id comes from the Darwin API: " + findBlock(asm, "kof_plat_thread_id"));
        assertFalse(findBlock(asm, "kof_plat_thread_id").contains("syscall"),
                "no Linux syscall in the thread id: " + findBlock(asm, "kof_plat_thread_id"));
    }

    /**
     * O RATCHET: com o id da thread e a sincronia migrados, o hello real não
     * tem MAIS NENHUM syscall Linux cru — a face x86-64 da costura é toda
     * libSystem.
     */
    @Test
    void theHelloHasNoRawSyscallLeft(@TempDir Path tmp) throws IOException {
        List<String> owners = dev.kof.compiler.nat.NativeMachO.elfSyscallOwners(macosAssembly(tmp));
        assertEquals(List.of(), owners,
                "the seam's x86-64 face is all libSystem now: " + owners);
    }

    /**
     * O ASSEMBLER REAL aceita o corpo do id da thread — e o objeto carrega o
     * símbolo da libSystem ({@code _pthread_threadid_np}).
     */
    @Test
    void theRealMachOAssemblerAcceptsTheThreadIdBody(@TempDir Path tmp) throws Exception {
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
                "the Mach-O assembler must accept the thread id body:\n" + output);
        String blob = new String(Files.readAllBytes(obj),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(blob.contains("_pthread_threadid_np"),
                "the object carries the libSystem symbol: " + output);
    }

    /**
     * A SINCRONIA no macOS: o futex não existe — a costura fala a API ulock
     * do Darwin ({@code __ulock_wait}/{@code __ulock_wake}), com os símbolos
     * medidos na lista de exportação da libSystem ({@code ___ulock_*} — o
     * nome C com o {@code _} do Mach-O) e a semântica medida na fonte do
     * XNU (o compare-and-wait e o {@code ULF_WAKE_ALL} para o wake total).
     */
    @Test
    void theMacosSyncCallsTheUlockApi(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        String sync = findBlock(asm, "kof_plat_sync");
        assertTrue(sync.contains("call ___ulock_wait"),
                "the futex WAIT is the Darwin ulock wait: " + sync);
        assertTrue(sync.contains("call ___ulock_wake"),
                "the futex WAKE is the Darwin ulock wake: " + sync);
        assertTrue(sync.contains("orl $0x100, %edi"),
                "the futex WAKE(INT_MAX) maps to ULF_WAKE_ALL: " + sync);
        assertTrue(sync.contains("andl $0x7f, %eax"),
                "the futex command is the low 7 bits (the PRIVATE flag rides above): " + sync);
        assertFalse(sync.contains("syscall"),
                "no Linux syscall in the macOS sync: " + sync);
    }

    /**
     * O RATCHET FINAL da costura x86-64: o hello real não tem MAIS NENHUM
     * syscall Linux — o perfil ainda recusa, mas só pela face de TLS (M-4).
     */
    @Test
    void theRefusalNamesOnlyTheTlsFaceNow(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, HELLO);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setNativeProfile(dev.kof.compiler.nat.NativeProfile.of("macos"));
            CompilationResult mac = driver.compile(src, tmp.resolve("outref"), Target.NATIVE);
            String diags = mac.diagnostics().getDiagnostics().toString();
            // M-4 (#1135): sem face de syscall E sem face de TLS, o NATIVE005
            // fica MUDA — a unica recusa que resta e' a NATIVE004 (o toolchain).
            assertFalse(diags.contains("NATIVE005"),
                    "the macOS profile has no ELF-only face left: " + diags);
            assertTrue(diags.contains("NATIVE004"), "the named refusal: " + diags);
            assertFalse(diags.contains("a face de syscall"),
                    "the refusal no longer names a syscall face: " + diags);
            assertFalse(diags.contains("a face de TLS"),
                    "the refusal no longer names a TLS face: " + diags);
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
     * O ASSEMBLER REAL aceita o corpo da sincronia — e o objeto carrega os
     * símbolos da libSystem ({@code ___ulock_wait}/{@code ___ulock_wake}).
     */
    @Test
    void theRealMachOAssemblerAcceptsTheSyncBody(@TempDir Path tmp) throws Exception {
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
                "the Mach-O assembler must accept the sync body:\n" + output);
        String blob = new String(Files.readAllBytes(obj),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(blob.contains("___ulock_wait"),
                "the object carries the ulock symbol: " + output);
        assertTrue(blob.contains("___ulock_wake"),
                "the object carries the ulock symbol: " + output);
    }

    /** O corpo de uma função (do rótulo até o próximo rótulo global). */
    private static String findBlock(String asm, String label) {
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
