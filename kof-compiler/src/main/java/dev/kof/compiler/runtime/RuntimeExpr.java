package dev.kof.compiler.runtime;

import dev.kof.compiler.KofExprUnicode;

/**
 * Kof.expr fatia 6 (`D-KOF-EXPR`) — o runtime NATIVO x86-64 das classes de
 * caractere e da leitura de ponto de código.
 *
 * <p>Os predicados {@code kof_expr_is_*} recebem o PONTO DE CÓDIGO (edi) e
 * devolvem 1/0 em eax — a MESMA definição da tabela pinada Unicode 16.0.0 que
 * a JVM executa via {@code Character} ({@code KofExprUnicode.JVM_SOURCE}) e a
 * JS embute como dado ({@code JsRuntimeExpr}): a tabela viaja como DADO
 * (`.long` no .rodata, pares [start,end] por linha) com busca binária, o
 * mesmo algoritmo da face JS. {@code hex} é ASCII por definição (§10 do plano).
 *
 * <p>{@code kof_string_code_point_at(rdi=KofStr*, esi=unit)} devolve o valor
 * escalar na UNIDADE UTF-16 pedida (o contrato §827 da JVM/JS), -1 fora do
 * texto — o modelo de string nativo é UTF-8 em bytes, e o caminhar
 * byte↔unidade reutiliza o EXATO walk de {@code kof_string_char_at}
 * (RuntimeStringOps): 1 byte=1 unidade, 2/3 bytes=1, astral (4 bytes)=2 e o
 * par surrogado lido pelo MESMO decode (high em unit i, low em i+1). O bounds
 * estourado devolve -1 — DIFERENTE do char_at, que panica via kof_bounds_error,
 * porque o chamador do matcher testa o comprimento antes e -1 é o protocolo de
 * fim documentado (§10: nenhuma classe nem literal casa -1).
 */
public final class RuntimeExpr {

    private RuntimeExpr() {}

    /** Os pares [start,end] como .long — a fonte única é a tabela pinada. */
    private static String table(String label, String ranges) {
        StringBuilder sb = new StringBuilder();
        sb.append("            .balign 4\n").append(label).append(":\n");
        for (String part : ranges.split(",")) {
            if (part.isEmpty()) continue;
            String[] se = part.split("-");
            String start = se[0], end = se.length == 2 ? se[1] : se[0];
            sb.append("            .long 0x").append(start).append(", 0x").append(end).append("\n");
        }
        return sb.toString();
    }

    /** A busca binária compartilhada: edi=cp, rsi=tabela (pares), edx=n pares. */
    private static final String BINSEARCH = """
                # kof_expr_in_ranges: edi=cp, rsi=table, edx=pairs -> eax=1/0
                cmpl $0, %edx
                jle .Lker_no
                xorl %eax, %eax              # lo
                movl %edx, %ecx              # hi
            .Lker_loop:
                cmpl %ecx, %eax
                jge .Lker_no
                movl %ecx, %r8d
                subl %eax, %r8d
                shrl $1, %r8d
                addl %eax, %r8d              # mid
                movl %r8d, %r9d
                shll $1, %r9d                # idx = mid*2
                movl (%rsi,%r9,4), %r10d     # start
                cmpl %r10d, %edi
                jl .Lker_left
                movl 4(%rsi,%r9,4), %r10d    # end
                cmpl %r10d, %edi
                jg .Lker_right
                movl $1, %eax
                ret
            .Lker_left:
                movl %r8d, %ecx              # hi = mid
                jmp .Lker_loop
            .Lker_right:
                leal 1(%r8), %eax            # lo = mid+1
                jmp .Lker_loop
            .Lker_no:
                xorl %eax, %eax
                ret
            """;

    /** Um predicado de classe: edi=cp → eax=1/0, binária sobre a sua tabela. */
    private static String predicate(String fn, String label, int pairs) {
        return ".globl " + fn + "\n"
                + ".type " + fn + ", @function\n"
                + fn + ":\n"
                + "    leaq " + label + "(%rip), %rsi\n"
                + "    movl $" + pairs + ", %edx\n"
                + "    jmp kof_expr_in_ranges\n";
    }

    public static void emit(StringBuilder sb) {
        sb.append(".globl kof_expr_in_ranges\n.type kof_expr_in_ranges, @function\nkof_expr_in_ranges:\n");
        sb.append(BINSEARCH);
        sb.append("""
                .globl kof_expr_is_hex
                .type kof_expr_is_hex, @function
                kof_expr_is_hex:
                    # ASCII por definição (§10)
                    cmpl $0x30, %edi
                    jl .Lexh_no
                    cmpl $0x39, %edi
                    jle .Lexh_yes
                    cmpl $0x41, %edi
                    jl .Lexh_no
                    cmpl $0x46, %edi
                    jle .Lexh_yes
                    cmpl $0x61, %edi
                    jl .Lexh_no
                    cmpl $0x66, %edi
                    jle .Lexh_yes
                .Lexh_no:
                    xorl %eax, %eax
                    ret
                .Lexh_yes:
                    movl $1, %eax
                    ret

                .globl kof_expr_is_word
                .type kof_expr_is_word, @function
                kof_expr_is_word:
                    # letter OR digit OR '_' — o cp sobrevive aos dois calls em ebx
                    pushq %rbx
                    movl %edi, %ebx
                    call kof_expr_is_letter
                    testl %eax, %eax
                    jne .Lexw_yes
                    movl %ebx, %edi
                    call kof_expr_is_digit
                    testl %eax, %eax
                    jne .Lexw_yes
                    xorl %eax, %eax
                    cmpl $0x5F, %ebx
                    jne .Lexw_pop
                    movl $1, %eax
                .Lexw_pop:
                    popq %rbx
                    ret
                .Lexw_yes:
                    popq %rbx
                    movl $1, %eax
                    ret
                """);
        int letterPairs = countPairs(KofExprUnicode.RANGES_LETTER);
        int digitPairs = countPairs(KofExprUnicode.RANGES_DIGIT);
        int spacePairs = countPairs(KofExprUnicode.RANGES_SPACE);
        sb.append(predicate("kof_expr_is_letter", ".Lkex_letter_ranges", letterPairs));
        sb.append(predicate("kof_expr_is_digit", ".Lkex_digit_ranges", digitPairs));
        sb.append(predicate("kof_expr_is_space", ".Lkex_space_ranges", spacePairs));
        sb.append("            .section .rodata\n");
        sb.append(table(".Lkex_letter_ranges", KofExprUnicode.RANGES_LETTER));
        sb.append(table(".Lkex_digit_ranges", KofExprUnicode.RANGES_DIGIT));
        sb.append(table(".Lkex_space_ranges", KofExprUnicode.RANGES_SPACE));
        sb.append("            .section .text\n");
        sb.append("""
                .globl kof_string_code_point_at
                .type kof_string_code_point_at, @function
                kof_string_code_point_at:
                    # rdi=KofStr*, esi=unit → eax=escalar (ou -1 fora)
                    testq %rdi, %rdi
                    jz .Lkcpa_minus1
                    testl %esi, %esi
                    jl .Lkcpa_minus1
                    pushq %rbx
                    pushq %r12
                    pushq %r13
                    movl 16(%rdi), %r10d      # byteLen
                    leaq 24(%rdi), %r11       # bytes
                    xorl %ebx, %ebx           # byteOff
                    xorl %r12d, %r12d         # units consumidos
                .Lkcpa_walk:
                    cmpl %r10d, %ebx
                    jge .Lkcpa_minus1_pop
                    cmpl %esi, %r12d
                    jge .Lkcpa_at
                    movzbl (%r11,%rbx), %eax
                    movb %al, %r13b
                    andb $0x80, %r13b
                    jz .Lkcpa_adv1
                    movb %al, %r13b
                    andb $0xE0, %r13b
                    cmpb $0xC0, %r13b
                    je .Lkcpa_adv2
                    movb %al, %r13b
                    andb $0xF0, %r13b
                    cmpb $0xE0, %r13b
                    je .Lkcpa_adv3
                    leal 1(%r12), %eax        # astral: unit i = high, i+1 = low
                    cmpl %esi, %eax
                    je .Lkcpa_low             # a unidade pedida é a LOW
                    addl $2, %r12d
                    addl $4, %ebx
                    jmp .Lkcpa_walk
                .Lkcpa_adv1:
                    addl $1, %r12d
                    addl $1, %ebx
                    jmp .Lkcpa_walk
                .Lkcpa_adv2:
                    addl $1, %r12d
                    addl $2, %ebx
                    jmp .Lkcpa_walk
                .Lkcpa_adv3:
                    addl $1, %r12d
                    addl $3, %ebx
                    jmp .Lkcpa_walk
                .Lkcpa_at:
                    movzbl (%r11,%rbx), %eax
                    movb %al, %r13b
                    andb $0x80, %r13b
                    jz .Lkcpa_done            # ASCII
                    movb %al, %r13b
                    andb $0xE0, %r13b
                    cmpb $0xC0, %r13b
                    je .Lkcpa_dec2
                    movb %al, %r13b
                    andb $0xF0, %r13b
                    cmpb $0xE0, %r13b
                    je .Lkcpa_dec3
                    # astral na posição do HIGH surrogate: o contrato §827 da
                    # JVM devolve o PONTO DE CÓDIGO (o par completo), não o
                    # surrogate — o par ocupa as unidades i (high) e i+1 (low).
                    movzbl (%r11,%rbx), %eax
                    andl $0x07, %eax
                    shll $18, %eax
                    movzbl 1(%r11,%rbx), %ecx
                    andl $0x3F, %ecx
                    shll $12, %ecx
                    orl %ecx, %eax
                    movzbl 2(%r11,%rbx), %ecx
                    andl $0x3F, %ecx
                    shll $6, %ecx
                    orl %ecx, %eax
                    movzbl 3(%r11,%rbx), %ecx
                    andl $0x3F, %ecx
                    orl %ecx, %eax
                    jmp .Lkcpa_done                # o PONTO DE CÓDIGO (21 bits)
                .Lkcpa_low:
                    # a unidade i+1 do par: o contrato §827 devolve o LOW
                    # surrogate (0xDC00 + os 10 bits baixos do ponto de código).
                    movzbl (%r11,%rbx), %eax
                    andl $0x07, %eax
                    shll $18, %eax
                    movzbl 1(%r11,%rbx), %ecx
                    andl $0x3F, %ecx
                    shll $12, %ecx
                    orl %ecx, %eax
                    movzbl 2(%r11,%rbx), %ecx
                    andl $0x3F, %ecx
                    shll $6, %ecx
                    orl %ecx, %eax
                    movzbl 3(%r11,%rbx), %ecx
                    andl $0x3F, %ecx
                    orl %ecx, %eax
                    andl $0x3FF, %eax
                    addl $0xDC00, %eax
                    jmp .Lkcpa_done
                .Lkcpa_dec2:
                    movzbl (%r11,%rbx), %eax
                    andl $0x1F, %eax
                    shll $6, %eax
                    movzbl 1(%r11,%rbx), %ecx
                    andl $0x3F, %ecx
                    orl %ecx, %eax
                    jmp .Lkcpa_done
                .Lkcpa_dec3:
                    movzbl (%r11,%rbx), %eax
                    andl $0x0F, %eax
                    shll $12, %eax
                    movzbl 1(%r11,%rbx), %ecx
                    andl $0x3F, %ecx
                    shll $6, %ecx
                    orl %ecx, %eax
                    movzbl 2(%r11,%rbx), %ecx
                    andl $0x3F, %ecx
                    orl %ecx, %eax
                    jmp .Lkcpa_done
                .Lkcpa_done:
                    popq %r13
                    popq %r12
                    popq %rbx
                    ret
                .Lkcpa_minus1_pop:
                    popq %r13
                    popq %r12
                    popq %rbx
                .Lkcpa_minus1:
                    movl $-1, %eax
                    ret
                """);
    }

    private static int countPairs(String ranges) {
        int n = 0;
        for (String p : ranges.split(",")) if (!p.isEmpty()) n++;
        return n;
    }
}
