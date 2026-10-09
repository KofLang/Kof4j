package dev.kof.compiler.nat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pré-requisito do dtoa §448 no cross: o port riscv64 (Schubfach) usa
 * `mulhu`/`sltu`/`xori`/shifts por registrador, e o aarch64 herda pelo
 * tradutor. Sem estes ramos o `as` aarch64 recebia o mnemônico riscv cru
 * (fallback passthrough) e o build aarch64 quebrava. Aqui o contrato é provado
 * em duas camadas: a tradução linha a linha e a aceitação pelo `as` real.
 */
class NativeAarch64TranslatorAluTest {

    private static boolean has(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-c", "command -v " + cmd)
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return p.waitFor() == 0 && !out.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static String tr(String line) {
        return String.join("\n", NativeAarch64Translator.translateRiscvToAarch64(line));
    }

    @Test
    void mulhuBecomesUmulh() {
        String out = tr("    mulhu a1, a2, a3");
        assertTrue(out.contains("umulh x1, x2, x3"), "mulhu -> umulh: " + out);
    }

    @Test
    void sltuBecomesUnsignedCset() {
        String out = tr("    sltu a1, a2, a3");
        assertTrue(out.contains("cmp x2, x3"), "sltu compara operandos: " + out);
        assertTrue(out.contains("cset x1, lo"), "sltu -> cset lo (unsigned <): " + out);
    }

    @Test
    void xoriExpandsThroughTemp() {
        String out = tr("    xori a1, a2, 1");
        assertTrue(out.contains("eor x1, x2, x17"), "xori -> eor via temporario: " + out);
    }

    @Test
    void registerShiftsBecomeLslLsrAsr() {
        assertTrue(tr("    sll a1, a2, a3").contains("lsl x1, x2, x3"), "sll -> lsl");
        assertTrue(tr("    srl a1, a2, a3").contains("lsr x1, x2, x3"), "srl -> lsr");
        assertTrue(tr("    sra a1, a2, a3").contains("asr x1, x2, x3"), "sra -> asr");
    }

    /**
     * §631: `amoswap.w` é 32-bit (o lock do allocator é `.word`). A tradução
     * anterior usava registradores X (`swpal x`) = 64-bit, sobrescrevendo os
     * 4 bytes vizinhos do lock. O contrato é a LARGURA: `swpal w`.
     */
    @Test
    void amoswapWordBecomesSwpalWithWRegisters() {
        String out = tr("    amoswap.w t1, t0, (s1)");
        assertTrue(out.contains("swpal w9, w10, [x20]"), "amoswap.w -> swpal w (32-bit): " + out);
        assertFalse(out.contains("swpal x"), "nunca a forma X (64-bit): " + out);
    }

    /**
     * ABI: no RISC-V `s10` é callee-saved (sobrevive a `call`); no aarch64 o
     * equivalente só pode ser um registrador callee-saved. O mapa anterior
     * apontava `s10 -> x16` (caller-saved / IP0), então um valor mantido em
     * `s10` através de um `call` podia ser destruído pelo callee — e o
     * `kof_gc_mark` derramava `x18` (vazio) em vez do registrador real,
     * escondendo o ponteiro vivo do coletor. `x18` é callee-saved no Linux e
     * não era usado por nenhum outro caminho do tradutor.
     */
    @Test
    void s10MapsToCalleeSavedRegister() {
        String spill = tr("    sd s10, 32(sp)");
        assertTrue(spill.contains("str x18, [sp, #32]"), "s10 -> x18 (callee-saved): " + spill);
        assertFalse(spill.contains("x16"), "s10 nunca em x16 (caller-saved/IP0): " + spill);
        String use = tr("    mv s10, a0");
        assertTrue(use.contains("mov x18, x0"), "mv s10 -> mov x18: " + use);
        assertFalse(use.contains("x16"), "mv s10 nunca em x16: " + use);
    }

    /** Camada forte: o `as` aarch64 REAL aceita o texto traduzido (não basta a string). */
    @Test
    void translatedMnemonicsAssembleWithAarch64As(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as"), "aarch64-linux-gnu-as ausente — pulando");
        List<String> riscv = List.of(
                ".globl _probe",
                "_probe:",
                "    mulhu a1, a2, a3",
                "    sltu a1, a2, a3",
                "    xori a1, a2, 1",
                "    sll a1, a2, a3",
                "    srl a1, a2, a3",
                "    sra a1, a2, a3",
                "    amoswap.w a1, a2, (a3)",
                "    ret");
        StringBuilder asm = new StringBuilder();
        // §631: o emissor real abre o .s com `.arch armv8.1-a` (habilita LSE,
        // ex. `swpal`); o probe precisa do mesmo para o `as` aceitar o atômico.
        asm.append(".arch armv8.1-a\n");
        for (String line : riscv) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) asm.append(t).append('\n');
        }
        Path s = dir.resolve("probe.s");
        Files.writeString(s, asm.toString());
        Path o = dir.resolve("probe.o");
        List<String> cmd = new ArrayList<>(List.of("aarch64-linux-gnu-as", "-o", o.toString(), s.toString()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        assertEquals(0, ec, "as aarch64 recusou o texto traduzido (" + ec + "):\n" + asm + "\n" + out);
    }
}
