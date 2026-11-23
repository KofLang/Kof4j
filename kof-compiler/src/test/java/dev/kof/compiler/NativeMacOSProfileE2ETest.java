package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-1 (#1135, D-NATIVE-MACOS 17/11): o perfil macOS do alvo Native.
 *
 * <p>O M-1 do plano da #1135 é "o emissor de diretivas Mach-O + a linha de link
 * {@code -lSystem} + a entry {@code _main}". NESTA frota Linux o toolchain
 * macOS não existe — o assembler é o GNU {@code as} e o linker é o GNU
 * {@code ld}. A prova da etapa é portanto ESTRUTURAL (o texto traduzido + a
 * linha de link), exatamente como o próprio plano registra ("the emitter+link-line
 * faces are proven by golden/structural tests until a runner exists (M-7)").
 *
 * <p><b>E, quando o assembler Mach-O de verdade está disponível</b> (o clang
 * 18, provisionado por {@code scripts/provision-macho-toolchain.sh}), a prova
 * SOBE de nível: o texto traduzido é ASSEMBLADO de verdade, e o teste exige
 * que o clang o aceite. É essa prova que pegou os defeitos abaixo.
 *
 * <p><b>OS DEFEITOS QUE ESTE TESTE FIXA</b> (medidos em 22/11 contra o
 * assembly REAL de 132 KB emitido pelo backend — o M-1 anterior só foi
 * provado por 6 testes de texto pequeno, e o texto real não bate):
 * <ol>
 *   <li><b>TRAVA</b>: a varredura do operando recomeçava no {@code %} do
 *       REGISTRADOR sem consumi-lo — toda linha com registrador travava o
 *       compilador (um compile macOS de verdade nunca terminaria);</li>
 *   <li><b>IMEDIATOS CORROMPIDOS</b>: {@code $1} virava {@code _$1} (o clang:
 *       "invalid operand for instruction");</li>
 *   <li><b>HEX CORROMPIDO</b>: {@code 0x4f0c…} virava {@code 0_x4f0c…} (o
 *       clang: "unexpected token");</li>
 *   <li><b>MNEMÔNICO COM PREFIXO</b>: {@code lock cmpxchg} virava
 *       {@code lock _cmpxchg} (o clang: "invalid instruction mnemonic");</li>
 *   <li><b>COMENTÁRIO COM DEZ-PONTOS</b>: o mnemônico era prefixado
 *       ({@code _movq});</li>
 *   <li><b>REFERÊNCIA DE DADO</b>: o {@code .quad Pair_toString} (a tabela de
 *       métodos) ficava sem o prefixo — o símbolo ficaria indefinido.</li>
 * </ol>
 */
class NativeMacOSProfileE2ETest {

    @Test
    void theTranslatorDropsTheElfOnlyDirectives() {
        String elf = """
                .globl kof_print
                .type kof_print, @function
                kof_print:
                    ret
                .size kof_print, .-kof_print
                .section .text
                call kof_print
                """;
        String macho = dev.kof.compiler.nat.NativeMachO.translate(elf);
        assertFalse(macho.contains(".type "), "the Mach-O has no .type: " + macho);
        assertFalse(macho.contains(".size "), "the Mach-O has no .size: " + macho);
        assertTrue(macho.contains(".section __TEXT,__text"),
                "the .text becomes the Mach-O __TEXT,__text: " + macho);
    }

    @Test
    void theTranslatorMovesTheSectionsToTheMachOOnes() {
        String macho = dev.kof.compiler.nat.NativeMachO.translate("""
                .section .data
                .section .bss
                .section .rodata
                """);
        assertTrue(macho.contains(".section __DATA,__data"), "the .data: " + macho);
        assertTrue(macho.contains(".section __DATA,__bss"), "the .bss: " + macho);
        assertTrue(macho.contains(".section __TEXT,__const"), "the .rodata: " + macho);
    }

    @Test
    void theGlobalSymbolsAndTheirReferencesGetTheMachOPrefix() {
        String macho = dev.kof.compiler.nat.NativeMachO.translate("""
                .globl kof_print
                .globl kof_main
                kof_main:
                    call kof_print
                    leaq kof_str_true(%rip), %rax
                    ret
                .Llocal:
                    jmp .Llocal
                """);
        assertTrue(macho.contains(".globl _kof_print"), "the global declaration: " + macho);
        assertTrue(macho.contains("_kof_main:"), "the global definition: " + macho);
        assertTrue(macho.contains("call _kof_print"), "the CALL reference: " + macho);
        assertTrue(macho.contains("leaq _kof_str_true(%rip)"), "the LEA reference: " + macho);
        // os rótulos LOCAIS do assembler não ganham prefixo
        assertTrue(macho.contains(".Llocal:"), "the local label: " + macho);
        assertFalse(macho.contains("_Llocal"), "a local label is NOT global: " + macho);
    }

    /**
     * M-6 (#1135): os literais de CARACTERE ({@code $'h'}) são imediatos —
     * verbatim. Sem isto o `'h'` virava operando e o `h` ganhava o `_`
     * (`$'_h'`), e o clang respondia "single quote way too long": o runtime
     * do kof.http compara chars (`cmpb $'h', 0(%rbx)`), então a face apareceu
     * na família de REDE.
     */
    @Test
    void theCharacterLiteralImmediatesAreVerbatim() {
        String macho = dev.kof.compiler.nat.NativeMachO.translate("""
                    cmpb $'h', 0(%rbx)
                    cmpb $':', 4(%rbx)
                    cmpb $'\\n', 8(%rbx)
                    movq $1, %rdi
                """);
        assertTrue(macho.contains("cmpb $'h', 0(%rbx)"),
                "the char immediate is verbatim: " + macho);
        assertTrue(macho.contains("cmpb $':', 4(%rbx)"),
                "the char immediate is verbatim: " + macho);
        assertTrue(macho.contains("cmpb $'\\n', 8(%rbx)"),
                "the escaped char immediate is verbatim: " + macho);
        assertFalse(macho.contains("$'_"),
                "no char immediate is prefixed: " + macho);
    }

    /**
     * O DEFEITO DO TRAVA: a varredura do operando tem que TERMINAR. Um teste
     * com um REGISTRADOR era o que travava o compilador — o teste antigo
     * (6 testes de texto) não tinha nenhum.
     */
    @Test
    void theTranslatorTerminatesOnTheRegisterLines() {
        String macho = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> dev.kof.compiler.nat.NativeMachO.translate("""
                .globl kof_print
                kof_print:
                    pushq %rbx
                    movq %rdi, %rbx
                    xorq %rdx, %rdx
                    movabsq $0x4B4F46425F425801, %rax
                    cmpq $-1, %rax
                    lock cmpxchg %edx, (%rsi)
                    rep stosb
                    ret
                """),
                "the translation must TERMINATE (the old translator looped forever on "
                        + "the register lines — a real macOS compile never finished)");
        assertTrue(macho.contains("pushq %rbx"), "the register is verbatim: " + macho);
        assertTrue(macho.contains("movq %rdi, %rbx"), "the register is verbatim: " + macho);
        assertTrue(macho.contains("movabsq $0x4B4F46425F425801, %rax"),
                "the immediate is verbatim: " + macho);
        assertTrue(macho.contains("cmpq $-1, %rax"), "the negative immediate: " + macho);
        assertTrue(macho.contains("lock cmpxchg %edx, (%rsi)"),
                "the prefixed mnemonic is NOT a symbol: " + macho);
        assertTrue(macho.contains("rep stosb"), "the prefixed mnemonic is NOT a symbol: " + macho);
    }

    /**
     * Os IMEDIATOS e os HEX não são símbolos: o defeito do M-1 os prefixava
     * ({@code _$1}, {@code 0_x4f0c…}) e o clang rejeitava o texto.
     */
    @Test
    void theImmediatesAndTheHexAreNeverPrefixed() {
        String macho = dev.kof.compiler.nat.NativeMachO.translate("""
                    movq $1, %rdi
                    movq $0, 16(%rbx)
                    movq $-1, %r8
                    movabsq $0x4B4F46425F425801, %rax
                    .quad 0x4f0cedc95a718dd4, 0x5b01e8b09aa0d1b5
                """);
        assertTrue(macho.contains("movq $1, %rdi"), "the immediate: " + macho);
        assertTrue(macho.contains("movq $0, 16(%rbx)"), "the immediate: " + macho);
        assertTrue(macho.contains("movq $-1, %r8"), "the immediate: " + macho);
        assertTrue(macho.contains("movabsq $0x4B4F46425F425801, %rax"), "the hex: " + macho);
        assertTrue(macho.contains(".quad 0x4f0cedc95a718dd4, 0x5b01e8b09aa0d1b5"),
                "the hex literal is never prefixed: " + macho);
        assertFalse(macho.contains("_$"), "no immediate is prefixed: " + macho);
        assertFalse(macho.contains("0_x"), "no hex literal is prefixed: " + macho);
    }

    /**
     * O COMENTÁRIO não é operativa: um {@code #} com DEZ-PONTOS dentro (o
     * defeito do M-1, que tratava a linha como definição de rótulo e prefixava
     * o mnemônico) nem uma string com {@code #} são tocadas.
     */
    @Test
    void theCommentAndTheStringsAreNeverTouched() {
        String macho = dev.kof.compiler.nat.NativeMachO.translate("""
                    movq $1, %rdi        # nota: valor inicial
                .Lstr_0: .asciz "a#b:c"
                """);
        assertTrue(macho.contains("movq $1, %rdi        # nota: valor inicial"),
                "the commented line is verbatim: " + macho);
        assertTrue(macho.contains(".Lstr_0: .asciz \"a#b:c\""),
                "the string is data: " + macho);
        assertFalse(macho.contains("_movq"), "the mnemonic is never prefixed: " + macho);
    }

    /**
     * A REFERÊNCIA DE DADO: a tabela de métodos é um {@code .quad símbolo} —
     * o símbolo precisa do prefixo, senão o Mach-O não o resolve.
     */
    @Test
    void theDataReferencesTakeThePrefix() {
        String macho = dev.kof.compiler.nat.NativeMachO.translate("""
                .section .data
                    .quad Pair_toString
                    .quad Default_Main_main
                """);
        assertTrue(macho.contains(".quad _Pair_toString"),
                "the data reference takes the prefix: " + macho);
        assertTrue(macho.contains(".quad _Default_Main_main"),
                "the data reference takes the prefix: " + macho);
    }

    @Test
    void theLinkLineIsTheLibSystemOne() {
        String[] args = dev.kof.compiler.nat.NativeAssembler.macosLinkArgs(
                Path.of("/tmp/out/Main"), Path.of("/tmp/out/Main.o"));
        String line = String.join(" ", args);
        assertTrue(line.contains("-lSystem"), "the macOS link uses libSystem: " + line);
        // M-2 (22/11): a arquitetura segue o EMISSOR — o alvo Native emite
        // x86-64 (o M-1 provou o texto real no assembler `x86_64-macos-none`),
        // e um `-arch arm64` sobre um objeto x86-64 é um link que falha no
        // runner. A face Apple silicon é a próxima linha do plano (#1135).
        assertTrue(line.contains("-arch x86_64"), "the link names the object arch: " + line);
        assertFalse(line.contains("-dynamic-linker"), "no ELF dynamic linker: " + line);
        assertFalse(line.contains("-lc"), "no GNU libc: " + line);
    }

    @Test
    void theProfileIsSelectableAndRefusedOutsideTheNativeTarget() {
        assertTrue(dev.kof.compiler.nat.NativeProfile.of("macos")
                        == dev.kof.compiler.nat.NativeProfile.MACOS,
                "the macos profile is selected");
        assertTrue(dev.kof.compiler.nat.NativeProfile.MACOS.isMacos(),
                "the profile knows it is the macOS one");
        // o perfil macOS é o ÚNICO não-Linux: os demais (host/freestanding/
        // uefi/bios) continuam sendo os de antes — o parse não pode confundir.
        assertTrue(dev.kof.compiler.nat.NativeProfile.of("host")
                        == dev.kof.compiler.nat.NativeProfile.HOST, "the host profile");
        assertTrue(dev.kof.compiler.nat.NativeProfile.of("freestanding")
                        == dev.kof.compiler.nat.NativeProfile.FREESTANDING, "the freestanding profile");
    }

    /**
     * A SELEÇÃO do perfil muda o texto emitido: o mesmo programa, no perfil
     * HOST e no perfil macOS, produz texts diferentes (o GNU contra o Mach-O).
     * O teste não roda o binário (o toolchain macOS não existe nesta frota)
     * — fixa o EFEITO da seleção, que é o que o M-1 entrega.
     */
    @Test
    void theProfileChangesTheEmittedText(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
                main() {
                    println("hi")
                }
                """);
        CompilerDriver driver = new CompilerDriver();
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        // o .s é APAGADO depois do link (a menos de KOF_KEEP_ASM) — a prova da
        // etapa é o TEXTO, então o .s é preservado.
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        try {
            CompilationResult host = driver.compile(src, tmp.resolve("out-host"), Target.NATIVE);
            assertTrue(host.success(), "the host profile compiles: "
                    + host.diagnostics().getDiagnostics());
            String hostAsm = Files.readString(tmp.resolve("out-host/Default/Main.s"));
            assertTrue(hostAsm.contains(".type "), "the host profile emits the ELF directives");

            driver.setNativeProfile(dev.kof.compiler.nat.NativeProfile.MACOS);
            // NESTA frota (Linux) o link macOS é recusado com o diagnóstico
            // nomeado NATIVE004 — o toolchain macOS (o assembler Mach-O + o
            // linker da libSystem) só existe no runner macOS (a etapa M-7).
            // A recusa é a prova de que o gate é HONESTO (R6): nunca um link
            // que falha feio, nunca um binário GNU rotulado de Mach-O.
            boolean macHost = System.getProperty("os.name", "").toLowerCase().contains("mac");
            CompilationResult mac = driver.compile(src, tmp.resolve("out-mac"), Target.NATIVE);
            if (!macHost) {
                assertFalse(mac.success(),
                        "without the macOS toolchain the profile must refuse, not link");
                String diags = mac.diagnostics().getDiagnostics().toString();
                assertTrue(diags.contains("NATIVE004") || diags.contains("NATIVE005"),
                        "the named refusal: " + diags);
            }
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
     * A PROVA DE NÍVEL REAL: o texto traduzido é ASSEMBLADO pelo assembler
     * Mach-O de verdade (o clang 18, provisionado por
     * {@code scripts/provision-macho-toolchain.sh}) e o teste exige que ele
     * aceite.
     *
     * <p>É esta prova que pegou os defeitos do M-1 anterior — o texto pequeno
     * dos 6 testes de antes montava; o texto REAL de 132 KB (o programa + o
     * runtime inteiro) não montava, e o tradutor ainda TRAVAVA nele.
     *
     * <p><b>A FRONTEIRA DA PROVA:</b> o programa real ainda carrega as faces
     * ELF-only — o TLS local-exec ({@code %fs:…@tpoff}) e o syscall cru — que
     * o Mach-O não tem (o clang: "32-bit absolute addressing is not supported
     * in 64-bit mode"). A migração delas é a etapa M-2 (a costura sobre a
     * libSystem) e a M-4 (o TLS), e o perfil RECUSA o programa com o
     * diagnóstico nomeado NATIVE005 (R6). O teste verifica as duas metades:
     * (1) o perfil nomeia as faces que faltam; (2) o resto do texto — TODO o
     * mais o runtime inteiro, ~130 KB — ASSEMBLA de verdade no Mach-O.
     *
     * <p>Sem o toolchain (o host sem provisioning) → {@code assumeTrue} skip,
     * como o precedente do host WASM/ESP8266.
     */
    @Test
    void theRealMachOAssemblerAcceptsTheTranslatedAssembly(@TempDir Path tmp) throws Exception {
        Path zig = machoToolchain();
        org.junit.jupiter.api.Assumptions.assumeTrue(zig != null,
                "the Mach-O toolchain is absent (scripts/provision-macho-toolchain.sh)");

        // o programa REAL: o backend emite o runtime inteiro (o texto é
        // ~130 KB) — é exatamente esse texto que o M-1 antigo não tradzia.
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
                main() {
                    println("hi")
                }
                """);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            CompilerDriver driver = new CompilerDriver();
            CompilationResult host = driver.compile(src, tmp.resolve("out"), Target.NATIVE);
            assertTrue(host.success(), "the native compile succeeds: "
                    + host.diagnostics().getDiagnostics());
            String elfAsm = Files.readString(tmp.resolve("out/Default/Main.s"));
            assertTrue(elfAsm.length() > 20_000,
                    "the real assembly is the whole runtime (" + elfAsm.length() + " bytes)");

            // (1) as faces ELF-only que o macOS ainda nao tem — nomeadas pela
            // recusa NATIVE005 (a migracao delas e a M-2 e a M-4).
            List<String> tls = dev.kof.compiler.nat.NativeMachO.elfTlsSites(elfAsm);
            List<String> syscalls = dev.kof.compiler.nat.NativeMachO.elfSyscallSites(elfAsm);
            assertFalse(tls.isEmpty(),
                    "the real program still carries the ELF local-exec TLS (the M-4 face)");
            assertFalse(syscalls.isEmpty(),
                    "the real program still carries the raw Linux syscalls (the M-2 face)");

            // (2) o RESTO do texto (todo o mais o runtime inteiro) assembla
            // de verdade no Mach-O. As linhas das faces ELF-only sao removidas
            // — elas nao tem forma Mach-O (o clang as rejeita) e a migracao
            // delas e a M-2/M-4, com a recusa NATIVE005 nomeando-as.
            String machoAsm = assertTimeoutPreemptively(Duration.ofSeconds(60),
                    () -> dev.kof.compiler.nat.NativeMachO.translate(elfAsm),
                    "the Mach-O translation must TERMINATE (the old translator looped "
                            + "forever on the register lines — a real macOS compile never finished)");
            StringBuilder withoutElfOnly = new StringBuilder();
            for (String line : machoAsm.split("\n", -1)) {
                String t = line.trim();
                if (t.equals("syscall") || t.contains("%fs:") || t.contains("@tpoff")) continue;
                withoutElfOnly.append(line).append('\n');
            }
            Path asmFile = tmp.resolve("Main.macho.s");
            Files.writeString(asmFile, withoutElfOnly.toString());

            // o assembler Mach-O de verdade
            Path obj = tmp.resolve("Main.macho.o");
            ProcessBuilder pb = new ProcessBuilder(
                    zig.toString(), "cc", "-target", "x86_64-macos-none",
                    "-c", asmFile.toString(), "-o", obj.toString());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            int rc = proc.waitFor();
            assertEquals(0, rc,
                    "the Mach-O assembler must accept the translated text:\n" + output);
            assertTrue(Files.size(obj) > 0, "the Mach-O object was produced");
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
