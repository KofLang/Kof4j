package dev.kof.compiler.nat;

import dev.kof.compiler.KofExprUnicode;

import java.util.ArrayList;
import java.util.List;

/**
 * Kof.expr fatia 6 (`D-KOF-EXPR`) — a face CROSS (riscv64, e por tradução
 * linha-a-linha o aarch64) das classes de caractere e da leitura de ponto de
 * código: o MESMO runtime da face x86-64 ({@code RuntimeExpr}), reescrito na
 * convenção do runtime cross.
 *
 * <p>Convenção do runtime cross (a mesma das fatias {@code NativeRiscvAsm*}):
 * o resultado volta em {@code a0}, os argumentos entram em {@code a0..a7}, e
 * o compilador cross carrega a operand stack nesses registradores
 * ({@code emitCrossArgLoads}). As tabelas pinadas viajam como DADO em
 * {@code .rodata} (pares de 64 bits, como o padrão {@code .quad} das fatias
 * cross) com a MESMA busca binária da face x86-64 e da face JS — a tabela é
 * a fonte única ({@link KofExprUnicode}), o algoritmo é o mesmo nos três
 * backends.
 *
 * <p>O aarch64 herda por tradução: o {@link NativeAarch64Translator} traduz
 * a linha a linha, então este ASM tem de usar SÓ o vocabulário que ele
 * conhece (li/ld/lw/lbu/slli/srli/slti/… e {@code la} para as tabelas) — um
 * mnemonic fora desse conjunto é recusado com NATIVE002 (nunca um
 * passthrough silencioso).
 */
public final class NativeRiscvAsmExpr {

    private NativeRiscvAsmExpr() {}

    /** Os pares [start,end] como .quad — a fonte única é a tabela pinada. */
    private static void table(List<String> out, String label, String ranges) {
        out.add("        .align 3");
        out.add("        " + label + ":");
        for (String part : ranges.split(",")) {
            if (part.isEmpty()) continue;
            String[] se = part.split("-");
            String start = se[0], end = se.length == 2 ? se[1] : se[0];
            out.add("        .quad 0x" + start + ", 0x" + end);
        }
    }

    private static int countPairs(String ranges) {
        int n = 0;
        for (String p : ranges.split(",")) if (!p.isEmpty()) n++;
        return n;
    }

    /** A busca binária compartilhada: a0=cp, a1=tabela, a2=n pares → a0=1/0. */
    private static void binarySearch(List<String> out) {
        out.add("                .globl kof_expr_in_ranges");
        out.add("                .type kof_expr_in_ranges, @function");
        out.add("                kof_expr_in_ranges:");
        out.add("                    blez a2, .Lker_no");
        out.add("                    li   t0, 0                  # lo");
        out.add("                    mv   t1, a2                 # hi");
        out.add("                .Lker_loop:");
        out.add("                    bge  t0, t1, .Lker_no");
        out.add("                    sub  t2, t1, t0");
        out.add("                    srli t2, t2, 1");
        out.add("                    add  t2, t2, t0             # mid");
        out.add("                    slli t3, t2, 4");
        out.add("                    add  t4, a1, t3             # &pairs[mid]");
        out.add("                    ld   t5, 0(t4)             # start");
        out.add("                    blt  a0, t5, .Lker_left");
        out.add("                    ld   t6, 8(t4)             # end");
        out.add("                    bgt  a0, t6, .Lker_right");
        out.add("                    li   a0, 1");
        out.add("                    ret");
        out.add("                .Lker_left:");
        out.add("                    mv   t1, t2                 # hi = mid");
        out.add("                    j    .Lker_loop");
        out.add("                .Lker_right:");
        out.add("                    addi t0, t2, 1              # lo = mid+1");
        out.add("                    j    .Lker_loop");
        out.add("                .Lker_no:");
        out.add("                    li   a0, 0");
        out.add("                    ret");
    }

    /** Um predicado de classe: a0=cp → a0=1/0 (binária sobre a sua tabela). */
    private static void predicate(List<String> out, String fn, String label, int pairs) {
        out.add("                .globl " + fn);
        out.add("                .type " + fn + ", @function");
        out.add("                " + fn + ":");
        out.add("                    la   a1, " + label);
        out.add("                    li   a2, " + pairs);
        out.add("                    j    kof_expr_in_ranges");
    }

    static final String RISCV_EXPR_ASM = build();

    private static String build() {
        List<String> out = new ArrayList<>();
        out.add("        .section .text");
        binarySearch(out);
        out.add("                .globl kof_expr_is_hex");
        out.add("                .type kof_expr_is_hex, @function");
        out.add("                kof_expr_is_hex:");
        out.add("                    # ASCII por definição (§10)");
        out.add("                    li   t0, 48");
        out.add("                    blt  a0, t0, .Lexh_no");
        out.add("                    li   t0, 57");
        out.add("                    ble  a0, t0, .Lexh_yes");
        out.add("                    li   t0, 65");
        out.add("                    blt  a0, t0, .Lexh_no");
        out.add("                    li   t0, 70");
        out.add("                    ble  a0, t0, .Lexh_yes");
        out.add("                    li   t0, 97");
        out.add("                    blt  a0, t0, .Lexh_no");
        out.add("                    li   t0, 102");
        out.add("                    ble  a0, t0, .Lexh_yes");
        out.add("                .Lexh_no:");
        out.add("                    li   a0, 0");
        out.add("                    ret");
        out.add("                .Lexh_yes:");
        out.add("                    li   a0, 1");
        out.add("                    ret");
        out.add("                .globl kof_expr_is_word");
        out.add("                .type kof_expr_is_word, @function");
        out.add("                kof_expr_is_word:");
        out.add("                    # letter OR digit OR '_' — o cp sobrevive em t6");
        out.add("                    mv   t6, a0");
        out.add("                    call kof_expr_is_letter");
        out.add("                    bnez a0, .Lexw_yes");
        out.add("                    mv   a0, t6");
        out.add("                    call kof_expr_is_digit");
        out.add("                    bnez a0, .Lexw_yes");
        out.add("                    li   a0, 0");
        out.add("                    li   t0, 95");
        out.add("                    bne  t6, t0, .Lexw_no");
        out.add("                    li   a0, 1");
        out.add("                .Lexw_no:");
        out.add("                    ret");
        out.add("                .Lexw_yes:");
        out.add("                    li   a0, 1");
        out.add("                    ret");
        predicate(out, "kof_expr_is_letter", ".Lkex_letter_ranges",
                countPairs(KofExprUnicode.RANGES_LETTER));
        predicate(out, "kof_expr_is_digit", ".Lkex_digit_ranges",
                countPairs(KofExprUnicode.RANGES_DIGIT));
        predicate(out, "kof_expr_is_space", ".Lkex_space_ranges",
                countPairs(KofExprUnicode.RANGES_SPACE));
        codePointAt(out);
        out.add("        .section .rodata");
        table(out, ".Lkex_letter_ranges", KofExprUnicode.RANGES_LETTER);
        table(out, ".Lkex_digit_ranges", KofExprUnicode.RANGES_DIGIT);
        table(out, ".Lkex_space_ranges", KofExprUnicode.RANGES_SPACE);
        out.add("        .section .text");
        return String.join("\n", out) + "\n";
    }

    /** A leitura do escalar na unidade UTF-16 (o contrato §827 da JVM/JS). */
    private static void codePointAt(List<String> out) {
        out.add("                .globl kof_string_code_point_at");
        out.add("                .type kof_string_code_point_at, @function");
        out.add("                kof_string_code_point_at:");
        out.add("                    # a0=KofStr*, a1=unit → a0=escalar (ou -1 fora)");
        out.add("                    beqz a0, .Lkcpa_minus1");
        out.add("                    bltz a1, .Lkcpa_minus1");
        out.add("                    addi t0, a0, 24             # bytes");
        out.add("                    lw   t1, 16(a0)             # byteLen");
        out.add("                    li   t2, 0                  # byteOff");
        out.add("                    li   t3, 0                  # units");
        out.add("                .Lkcpa_walk:");
        out.add("                    bgeu t2, t1, .Lkcpa_minus1");
        out.add("                    bge  t3, a1, .Lkcpa_at");
        out.add("                    add  t4, t0, t2");
        out.add("                    lbu  t5, 0(t4)");
        out.add("                    andi t6, t5, 128");
        out.add("                    beqz t6, .Lkcpa_adv1");
        out.add("                    andi t6, t5, 224");
        out.add("                    li   t4, 192");
        out.add("                    beq  t6, t4, .Lkcpa_adv2");
        out.add("                    andi t6, t5, 240");
        out.add("                    li   t4, 224");
        out.add("                    beq  t6, t4, .Lkcpa_adv3");
        out.add("                    addi t4, t3, 1             # astral: unit i = high, i+1 = low");
        out.add("                    beq  a1, t4, .Lkcpa_low");
        out.add("                    addi t3, t3, 2");
        out.add("                    addi t2, t2, 4");
        out.add("                    j    .Lkcpa_walk");
        out.add("                .Lkcpa_adv1:");
        out.add("                    addi t3, t3, 1");
        out.add("                    addi t2, t2, 1");
        out.add("                    j    .Lkcpa_walk");
        out.add("                .Lkcpa_adv2:");
        out.add("                    addi t3, t3, 1");
        out.add("                    addi t2, t2, 2");
        out.add("                    j    .Lkcpa_walk");
        out.add("                .Lkcpa_adv3:");
        out.add("                    addi t3, t3, 1");
        out.add("                    addi t2, t2, 3");
        out.add("                    j    .Lkcpa_walk");
        out.add("                .Lkcpa_at:");
        out.add("                    add  t4, t0, t2");
        out.add("                    lbu  t5, 0(t4)");
        out.add("                    andi t6, t5, 128");
        out.add("                    beqz t6, .Lkcpa_ascii");
        out.add("                    andi t6, t5, 224");
        out.add("                    li   t4, 192");
        out.add("                    beq  t6, t4, .Lkcpa_dec2");
        out.add("                    andi t6, t5, 240");
        out.add("                    li   t4, 224");
        out.add("                    beq  t6, t4, .Lkcpa_dec3");
        // astral na posição do HIGH: o ponto de código (21 bits)
        out.add("                    andi a0, t5, 7");
        out.add("                    slli a0, a0, 18");
        out.add("                    lbu  t5, 1(t4)");
        out.add("                    andi t5, t5, 63");
        out.add("                    slli t5, t5, 12");
        out.add("                    or   a0, a0, t5");
        out.add("                    lbu  t5, 2(t4)");
        out.add("                    andi t5, t5, 63");
        out.add("                    slli t5, t5, 6");
        out.add("                    or   a0, a0, t5");
        out.add("                    lbu  t5, 3(t4)");
        out.add("                    andi t5, t5, 63");
        out.add("                    or   a0, a0, t5");
        out.add("                    ret");
        out.add("                .Lkcpa_low:");
        // a unidade i+1 do par: o LOW surrogate (§827)
        out.add("                    andi a0, t5, 7");
        out.add("                    slli a0, a0, 18");
        out.add("                    lbu  t5, 1(t4)");
        out.add("                    andi t5, t5, 63");
        out.add("                    slli t5, t5, 12");
        out.add("                    or   a0, a0, t5");
        out.add("                    lbu  t5, 2(t4)");
        out.add("                    andi t5, t5, 63");
        out.add("                    slli t5, t5, 6");
        out.add("                    or   a0, a0, t5");
        out.add("                    lbu  t5, 3(t4)");
        out.add("                    andi t5, t5, 63");
        out.add("                    or   a0, a0, t5");
        out.add("                    andi a0, a0, 1023");
        out.add("                    li   t4, 56320");
        out.add("                    add  a0, a0, t4");
        out.add("                    ret");
        out.add("                .Lkcpa_ascii:");
        out.add("                    andi a0, t5, 255");
        out.add("                    ret");
        out.add("                .Lkcpa_dec2:");
        out.add("                    andi a0, t5, 31");
        out.add("                    slli a0, a0, 6");
        out.add("                    lbu  t5, 1(t4)");
        out.add("                    andi t5, t5, 63");
        out.add("                    or   a0, a0, t5");
        out.add("                    ret");
        out.add("                .Lkcpa_dec3:");
        out.add("                    andi a0, t5, 15");
        out.add("                    slli a0, a0, 12");
        out.add("                    lbu  t5, 1(t4)");
        out.add("                    andi t5, t5, 63");
        out.add("                    slli t5, t5, 6");
        out.add("                    or   a0, a0, t5");
        out.add("                    lbu  t5, 2(t4)");
        out.add("                    andi t5, t5, 63");
        out.add("                    or   a0, a0, t5");
        out.add("                    ret");
        out.add("                .Lkcpa_minus1:");
        out.add("                    li   a0, -1");
        out.add("                    ret");
    }
}
