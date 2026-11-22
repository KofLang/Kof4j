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
 * M-2 (#1135, D-NATIVE-MACOS) — a COSTURA {@code kof_plat_*} sobre a
 * libSystem, primeira família: write/writev/exit.
 *
 * <p><b>O QUE ESTA FATIA CORRIGE (medido, 22/11):</b> o perfil macOS emitia
 * o MESMO texto do host — e o texto do host fala o ABI Linux cru
 * ({@code movq $1, %rax; syscall}). O Darwin não tem syscall Linux: um
 * binário macOS com esse corpo não roda. A primeira família migrada é a de
 * escrita/saída — {@code write}, {@code writev}, {@code exit} — que viram
 * chamadas da libSystem (a libc do Darwin), com a MESMA ABI de argumentos
 * (SysV x86-64: {@code %rdi/%rsi/%rdx}).
 *
 * <p><b>A ARQUITECTURA DA LINHA DE LINK (medido):</b> o M-1 provou o texto
 * real de 132 KB no assembler Mach-O de verdade — como
 * {@code x86_64-macos-none}. A linha de link dizia {@code -arch arm64}: um
 * link que FALHA no runner (objeto x86-64, flag arm64). A arquitetura segue o
 * EMISSOR ({@code x86_64}); a face Apple silicon (arm64) é a próxima linha do
 * plano (#1135) e trará o emissor cross aarch64 + a sua própria prova.
 *
 * <p><b>O DETECTOR DE SYSCALL CRU (a honestidade da recusa):</b> a recusa
 * NATIVE005 media só o {@code syscall} do x86-64 — nas outras faces (o
 * {@code svc #0} do aarch64, o {@code ecall} do riscv64) ela ficava CEGA. O
 * detector agora vê as três, e a recusa NOMEIA os sítios (a família que ainda
 * fala o ABI Linux).
 */
class NativeMacOSSeamE2ETest {

    /** O hello real: o mesmo programa que o M-1 usou (o runtime inteiro). */
    private static final String HELLO = """
            main() {
                println("hi")
            }
            """;

    /**
     * Compila o hello no perfil macOS e devolve o TEXTO EMITIDO (antes da
     * tradução para Mach-O). O link é recusado nesta frota Linux (NATIVE004/
     * NATIVE005 — o toolchain macOS é o runner M-7), mas o texto é o que a
     * etapa mede.
     */
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

    /** O corpo de uma função da costura (do rótulo até o próximo rótulo). */
    private static String seamBody(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder block = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(label + ":")) {
                inside = true;
                continue;
            }
            if (inside && trimmed.endsWith(":") && !trimmed.contains(" ")) break;
            if (inside) block.append(line).append('\n');
        }
        return block.toString();
    }

    /**
     * A FAMÍLIA write/exit fala com a libSystem — e o syscall Linux cru
     * desapareceu desses corpos.
     */
    @Test
    void theWriteAndExitFamilyCallsTheLibSystem(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        String write = seamBody(asm, "kof_plat_write");
        String writev = seamBody(asm, "kof_plat_writev");
        String exit = seamBody(asm, "kof_plat_exit");
        String exitGroup = seamBody(asm, "kof_plat_exit_group");

        assertTrue(write.contains("call write"),
                "kof_plat_write calls the libSystem: " + write);
        assertTrue(writev.contains("call writev"),
                "kof_plat_writev calls the libSystem: " + writev);
        assertTrue(exit.contains("call exit"),
                "kof_plat_exit calls the libSystem: " + exit);
        assertTrue(exitGroup.contains("call exit"),
                "kof_plat_exit_group calls the libSystem: " + exitGroup);

        // o syscall CRU do Linux não pode sobreviver nesses corpos
        assertFalse(write.contains("syscall"), "no Linux syscall in write: " + write);
        assertFalse(writev.contains("syscall"), "no Linux syscall in writev: " + writev);
        assertFalse(exit.contains("syscall"), "no Linux syscall in exit: " + exit);
        assertFalse(exitGroup.contains("syscall"), "no Linux syscall in exit_group: " + exitGroup);

        // a chamada da libSystem alinha a pilha (a ABI SysV exige rsp ≡ 0 no
        // site do call — a glibc/Darwin usam SSE)
        assertTrue(write.contains("andq $-16, %rsp"),
                "the libSystem call aligns the stack: " + write);
    }

    /**
     * O RATCHET da costura: os syscalls crus do hello real caíram de 9 para
     * 5 — os 4 migrados são da família write/exit; os 5 que ficam são o
     * allocator (M-3) e a concorrência (M-5).
     */
    @Test
    void theRawSyscallRatchetFellByTheMigratedFamily(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        List<String> sites = dev.kof.compiler.nat.NativeMachO.elfSyscallSites(asm);
        assertEquals(5, sites.size(),
                "the write/exit family left the Linux syscall ABI; the rest are the "
                        + "allocator (M-3) and the concurrency (M-5): " + sites);
        List<String> owners = dev.kof.compiler.nat.NativeMachO.elfSyscallOwners(asm);
        assertTrue(owners.contains("kof_plat_thread_id"),
                "the concurrency face is still Linux: " + owners);
        assertTrue(owners.contains("kof_plat_sync"),
                "the concurrency face is still Linux: " + owners);
        assertTrue(owners.contains(".Lgrow_try"),
                "the allocator face is still Linux: " + owners);
        assertFalse(owners.contains("kof_plat_write"),
                "the write family is libSystem now: " + owners);
        assertFalse(owners.contains("kof_plat_exit"),
                "the exit family is libSystem now: " + owners);
    }

    /**
     * O TEXTO TRADUZIDO leva o prefixo do Mach-O nos símbolos da libSystem
     * ({@code _write}, {@code _exit}) — o símbolo do Darwin é o nome da
     * função C com um {@code _} na frente.
     */
    @Test
    void theTranslatedTextCallsTheUnderscoredLibSystemSymbols(@TempDir Path tmp) throws IOException {
        String asm = macosAssembly(tmp);
        String macho = dev.kof.compiler.nat.NativeMachO.translate(asm);
        assertTrue(macho.contains("call _write"),
                "the Mach-O call carries the underscore: " + macho);
        assertTrue(macho.contains("call _writev"),
                "the Mach-O call carries the underscore: " + macho);
        assertTrue(macho.contains("call _exit"),
                "the Mach-O call carries the underscore: " + macho);
    }

    /**
     * A LINHA DE LINK nomeia a arquitetura do OBJETO — o texto emitido é
     * x86-64 (medido pelo assembler Mach-O de verdade no M-1), então
     * {@code -arch arm64} seria um link que falha.
     */
    @Test
    void theLinkLineNamesTheArchitectureOfTheEmitter() {
        String line = String.join(" ", dev.kof.compiler.nat.NativeAssembler.macosLinkArgs(
                Path.of("/tmp/out/Main"), Path.of("/tmp/out/Main.o")));
        assertTrue(line.contains("-arch x86_64"),
                "the link names the architecture of the object: " + line);
        assertFalse(line.contains("-arch arm64"),
                "the emitter is x86-64: " + line);
    }

    /**
     * O PERFIL não carrega a arquitetura — o nome do perfil é o do alvo
     * (macOS/Mach-O), e a face vem do emissor.
     */
    @Test
    void theProfileIsTheMacosOneWithoutAnArchitectureClaim() {
        assertEquals("MACOS", dev.kof.compiler.nat.NativeProfile.of("macos").name(),
                "the macos profile does not claim an architecture");
        assertTrue(dev.kof.compiler.nat.NativeProfile.of("macos").isMacos(),
                "the macos profile knows it is the macOS one");
    }

    /**
     * O DETECTOR de syscall cru vê as TRÊS faces do runtime nativo — antes
     * ele via só o x86-64, e a recusa NATIVE005 ficava cega justamente nas
     * faces onde o syscall é outra instrução ({@code svc #0} no aarch64,
     * {@code ecall} no riscv64).
     */
    @Test
    void theRawSyscallDetectorSeesTheThreeFaces() {
        String riscv = """
                kof_plat_write:
                    li a0, 1
                    ecall
                """;
        String aarch64 = """
                kof_plat_write:
                    mov x0, #1
                    svc #0
                """;
        assertEquals(1, dev.kof.compiler.nat.NativeMachO.elfSyscallSites(riscv).size(),
                "the riscv64 ecall is a raw syscall");
        assertEquals(1, dev.kof.compiler.nat.NativeMachO.elfSyscallSites(aarch64).size(),
                "the aarch64 svc is a raw syscall");
        assertEquals(List.of("kof_plat_write"),
                dev.kof.compiler.nat.NativeMachO.elfSyscallOwners(riscv),
                "the refusal names the family that still speaks Linux");
    }

    /**
     * A RECUSA NOMEADA: o perfil macOS ainda NÃO emite um binário (o
     * allocator e a concorrência ainda falam o ABI Linux, e o TLS ainda é o
     * do ELF) — a recusa NATIVE005 diz QUANTOS e ONDE (a família que ainda
     * fala o Linux), em vez de um "N syscall(s)" sem dono.
     */
    @Test
    void theRefusalNamesTheFamiliesThatStillSpeakLinux(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, HELLO);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setNativeProfile(dev.kof.compiler.nat.NativeProfile.of("macos"));
            CompilationResult mac = driver.compile(src, tmp.resolve("out"), Target.NATIVE);
            String diags = mac.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("NATIVE005"), "the named refusal: " + diags);
            assertTrue(diags.contains("5 syscall(s) crus"),
                    "the measured count of the raw syscalls: " + diags);
            assertTrue(diags.contains("kof_plat_sync"),
                    "the refusal names the family: " + diags);
            assertTrue(diags.contains(".Lgrow_try"),
                    "the refusal names the allocator: " + diags);
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
     * A PROVA DE NÍVEL REAL (a mesma do M-1, agora sobre o TEXTO DO PERFIL
     * macOS): o assembler Mach-O de verdade (o clang 18, provisionado por
     * {@code scripts/provision-macho-toolchain.sh}) aceita o texto com os
     * corpos da libSystem — e o objeto carrega os símbolos da libSystem
     * ({@code _write}, {@code _exit}) como externos indefinidos, que é o que
     * o link contra a libSystem resolve.
     */
    @Test
    void theRealMachOAssemblerAcceptsTheLibSystemBodies(@TempDir Path tmp) throws Exception {
        Path zig = machoToolchain();
        org.junit.jupiter.api.Assumptions.assumeTrue(zig != null,
                "the Mach-O toolchain is absent (scripts/provision-macho-toolchain.sh)");
        String asm = macosAssembly(tmp);
        String macho = dev.kof.compiler.nat.NativeMachO.translate(asm);
        // as faces ELF-only que o macOS ainda não tem saem (a recusa NATIVE005
        // as nomeia): o TLS local-exec e o syscall Linux que sobrou.
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
                "the Mach-O assembler must accept the libSystem bodies:\n" + output);
        String blob = new String(Files.readAllBytes(obj), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(blob.contains("_write"),
                "the object carries the libSystem symbol: " + output);
        assertTrue(blob.contains("_exit"),
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
