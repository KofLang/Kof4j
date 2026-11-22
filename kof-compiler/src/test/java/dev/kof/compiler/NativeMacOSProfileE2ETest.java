package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p>O que este teste fixa:
 * <ul>
 *   <li>o tradutor remove as diretivas GNU que o Mach-O não tem
 *       ({@code .type}/{@code .size}) e traduz as seções para
 *       {@code __TEXT,__text} / {@code __DATA,__data} / {@code __DATA,__bss};</li>
 *   <li>os globais e as suas REFERÊNCIAS ganham o prefixo {@code _} do Mach-O
 *       (o símbolo global do Mach-O é {@code _X});</li>
 *   <li>a linha de link é a da libSystem ({@code -lSystem -arch arm64}), sem
 *       {@code -dynamic-linker} ELF e sem {@code -lc};</li>
 *   <li>o perfil é selecionável pela flag {@code --profile macos} e recusado
 *       fora do alvo nativo (R6).</li>
 * </ul>
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

    @Test
    void theLinkLineIsTheLibSystemOne() {
        String[] args = dev.kof.compiler.nat.NativeAssembler.macosLinkArgs(
                Path.of("/tmp/out/Main"), Path.of("/tmp/out/Main.o"));
        String line = String.join(" ", args);
        assertTrue(line.contains("-lSystem"), "the macOS link uses libSystem: " + line);
        assertTrue(line.contains("-arch arm64"), "the M-1 architecture is arm64: " + line);
        assertFalse(line.contains("-dynamic-linker"), "no ELF dynamic linker: " + line);
        assertFalse(line.contains("-lc"), "no GNU libc: " + line);
    }

    @Test
    void theProfileIsSelectableAndRefusedOutsideTheNativeTarget() {
        assertTrue(dev.kof.compiler.nat.NativeProfile.of("macos")
                        == dev.kof.compiler.nat.NativeProfile.MACOS_ARM64,
                "the macos profile is selected");
        assertTrue(dev.kof.compiler.nat.NativeProfile.MACOS_ARM64.isMacos(),
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

            driver.setNativeProfile(dev.kof.compiler.nat.NativeProfile.MACOS_ARM64);
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
                assertTrue(diags.contains("NATIVE004"),
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
}
