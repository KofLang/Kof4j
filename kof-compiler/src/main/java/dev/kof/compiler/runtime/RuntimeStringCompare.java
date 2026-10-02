package dev.kof.compiler.runtime;

/**
 * Runtime x86_64 (bug 97): String.compareTo/String.hashCode sobre o formato
 * interno UTF-8 do runtime, comparando/hasheando em CODE UNITS UTF-16 — o
 * contrato do JVM (type-system.md:289 declara os dois; o typer aceita em
 * BuiltinCallTyper:420; só o link nativo não os tinha). Um memcmp/byte-sum
 * byte-based daria paridade FALSA em astrais vs BMP (mesma classe do bug 43):
 * o JVM compara a SEQUÊNCIA de code units UTF-16 (par astral = high, low).
 * .Lksu_next produz exatamente essa sequência: 1/2/3-byte → 1 unit
 * (valor 0..0xFFFF); 4-byte → high surrogate, depois low (pendência no
 * cursor). UTF-8 malformado avança 1 byte e rende a unit crua — progresso
 * garantido, nunca trava. unit 0 = sentinel de FIM (dados terminam no NUL;
 * code point real nunca é 0 no decoder de progresso).
 */
public final class RuntimeStringCompare {

    private RuntimeStringCompare() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # cursor (rsi): 0=byteOff(int32) 4=pendingLowSurrogate(int32, 0=nenhuma)
            # .Lksu_next: rdi=bytes, rsi=cursor, rdx=nBytes → eax=unit.
            # Preserva rdi/rsi/rdx/rcx? NÃO: clobbers rax,rcx,r8,r9,r10,r11.
            .Lksu_next:
                movl 4(%rsi), %eax
                testl %eax, %eax
                jnz .Lksn_low
                movl (%rsi), %ecx
                cmpl %edx, %ecx
                jae .Lksn_oob
                movzbl (%rdi,%rcx), %eax
                testb $0x80, %al
                jne .Lksn_lead
                incl %ecx
                movl %ecx, (%rsi)
                ret
            .Lksn_low:
                movl $0, 4(%rsi)
                ret
            .Lksn_oob:
                movl $-1, %eax
                ret
            .Lksn_lead:
                movl %eax, %r8d
                andl $0xE0, %r8d
                cmpl $0xC0, %r8d
                je .Lksn2
                movl %eax, %r8d
                andl $0xF0, %r8d
                cmpl $0xE0, %r8d
                je .Lksn3
                movl %eax, %r8d
                andl $0xF8, %r8d
                cmpl $0xF0, %r8d
                je .Lksn4
            .Lksn_g:
                incl %ecx
                movl %ecx, (%rsi)
                ret
            .Lksn2:
                leal 1(%rcx), %r8d
                cmpl %edx, %r8d
                jae .Lksn_g
                movzbl (%rdi,%r8), %r9d
                andl $0xC0, %r9d
                cmpl $0x80, %r9d
                jne .Lksn_g
                andl $0x1F, %eax
                shll $6, %eax
                movzbl (%rdi,%r8), %r9d           # re-read (r9d foi destruído na validação)
                andl $0x3F, %r9d
                orl %r9d, %eax
                movl %r8d, (%rsi)
                incl (%rsi)
                ret
            .Lksn3:
                leal 1(%rcx), %r8d
                cmpl %edx, %r8d
                jae .Lksn_g
                movzbl (%rdi,%r8), %r9d          # b1 CRU (r9d preservado)
                movl %r9d, %r11d
                andl $0xC0, %r11d
                cmpl $0x80, %r11d
                jne .Lksn_g
                leal 2(%rcx), %r10d
                cmpl %edx, %r10d
                jae .Lksn_g
                movzbl (%rdi,%r10), %r11d        # b2 CRU
                movl %r11d, %r8d                 # scratch (p1 já consumido)
                andl $0xC0, %r8d
                cmpl $0x80, %r8d
                jne .Lksn_g
                andl $0x0F, %eax
                shll $12, %eax
                andl $0x3F, %r9d
                shll $6, %r9d
                orl %r9d, %eax
                andl $0x3F, %r11d
                orl %r11d, %eax
                movl %r10d, (%rsi)
                incl (%rsi)
                ret
            .Lksn4:
                leal 1(%rcx), %r8d
                cmpl %edx, %r8d
                jae .Lksn_g
                movzbl (%rdi,%r8), %r9d          # b1 CRU
                movl %r9d, %r11d
                andl $0xC0, %r11d
                cmpl $0x80, %r11d
                jne .Lksn_g
                leal 2(%rcx), %r11d              # p2 (r8d livre)
                cmpl %edx, %r11d
                jae .Lksn_g
                movzbl (%rdi,%r11), %r10d        # b2 CRU
                movl %r10d, %r8d                 # scratch
                andl $0xC0, %r8d
                cmpl $0x80, %r8d
                jne .Lksn_g
                leal 3(%rcx), %r8d               # p3
                cmpl %edx, %r8d
                jae .Lksn_g
                movzbl (%rdi,%r8), %r11d         # b3 CRU (r11=p2 livre)
                movl %r11d, %r8d
                andl $0xC0, %r8d
                cmpl $0x80, %r8d
                jne .Lksn_g
                andl $0x3F, %r9d
                andl $0x3F, %r10d
                andl $0x3F, %r11d
                movzbl (%rdi,%rcx), %eax
                andl $0x07, %eax
                shll $18, %eax
                shll $12, %r9d
                orl %r9d, %eax
                shll $6, %r10d
                orl %r10d, %eax
                orl %r11d, %eax
                subl $0x10000, %eax
                movl %eax, %r9d
                shrl $10, %r9d
                addl $0xD800, %r9d
                andl $0x3FF, %eax
                addl $0xDC00, %eax
                movl %eax, 4(%rsi)
                leal 4(%rcx), %eax
                movl %eax, (%rsi)
                movl %r9d, %eax
                ret
            """);
        sb.append("""
            # compareTo: rdi=receiver (pilha: arg por cima), rsi=other → eax =
            # primeira unit diferente (A−B, como o JVM) ou unidadesA−unidadesB.
            .globl kof_string_compare_to
            .type kof_string_compare_to, @function
            kof_string_compare_to:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                movl 16(%r12), %r14d
                xorl %r15d, %r15d
                movl %r15d, (%rsp)
                movl %r15d, 4(%rsp)
                movl %r15d, 8(%rsp)
                movl %r15d, 12(%rsp)
            .Lct_countA:
                leaq 24(%rbx), %rdi
                movq %rsp, %rsi
                movl %r13d, %edx
                call .Lksu_next
                cmpl $-1, %eax
                je .Lct_countAdone
                incl %r15d
                jmp .Lct_countA
            .Lct_countAdone:
                movl %r15d, 28(%rsp)
                xorl %r15d, %r15d
                movl %r15d, 8(%rsp)
                movl %r15d, 12(%rsp)
            .Lct_countB:
                leaq 24(%r12), %rdi
                leaq 8(%rsp), %rsi
                movl %r14d, %edx
                call .Lksu_next
                cmpl $-1, %eax
                je .Lct_countBdone
                incl %r15d
                jmp .Lct_countB
            .Lct_countBdone:
                movl %r15d, 32(%rsp)
                # cursor A de novo p/ o loop de diff
                movl %r15d, (%rsp)
                movl $0, (%rsp)
                movl $0, 4(%rsp)
                movl $0, 8(%rsp)
                movl $0, 12(%rsp)
            .Lct_loop:
                leaq 24(%rbx), %rdi
                movq %rsp, %rsi
                movl %r13d, %edx
                call .Lksu_next
                movl %eax, 16(%rsp)
                leaq 24(%r12), %rdi
                leaq 8(%rsp), %rsi
                movl %r14d, %edx
                call .Lksu_next
                movl 16(%rsp), %ecx
                cmpl %ecx, %eax
                jne .Lct_diff
                cmpl $-1, %eax
                je .Lct_same
                jmp .Lct_loop
            .Lct_diff:
                cmpl $-1, %eax
                je .Lct_samedone                  # B terminou → conta de units
                cmpl $-1, %ecx
                je .Lct_samedone                  # A terminou → conta de units
                movl %eax, %r13d
                movl 16(%rsp), %eax
                subl %r13d, %eax
                jmp .Lct_ret
            .Lct_same:
            .Lct_samedone:
                movl 28(%rsp), %eax
                subl 32(%rsp), %eax
            .Lct_ret:
                movslq %eax, %rax
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
        sb.append("""
            # hashCode: rdi=str → eax = h=31*h+unit (JVM, code units UTF-16).
            .globl kof_string_hash_code
            .type kof_string_hash_code, @function
            kof_string_hash_code:
                testq %rdi, %rdi
                je    .Lhc_zero
                pushq %rbx
                pushq %r12
                subq $16, %rsp
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                movq $0, (%rsp)
                movl $0, 8(%rsp)
            .Lhc_loop:
                leaq 24(%rbx), %rdi
                movq %rsp, %rsi
                movl %r12d, %edx
                call .Lksu_next
                cmpl $-1, %eax
                je .Lhc_done
                movl 8(%rsp), %ecx
                imull $31, %ecx, %ecx
                addl %eax, %ecx
                movl %ecx, 8(%rsp)
                jmp .Lhc_loop
            .Lhc_done:
                movl 8(%rsp), %eax
                movslq %eax, %rax
                addq $16, %rsp
                popq %r12
                popq %rbx
                ret
            .Lhc_zero:
                xorl %eax, %eax
                ret
            """);
        String cic = """
            # compareToIgnoreCase: rdi=A, rsi=B -> eax = JVM CASE_INSENSITIVE_ORDER
            # (fold SIMPLES por code unit: upper; se ainda difere, lower; prefixo
            # -> unitsA-unitsB). Reusa .Lksu_next e as tabelas .Lkof_cu_*_tab.
            .globl kof_string_compare_to_ignore_case
            .type kof_string_compare_to_ignore_case, @function
            kof_string_compare_to_ignore_case:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                movl 16(%r12), %r14d
                xorl %r15d, %r15d
                movl %r15d, (%rsp)
                movl %r15d, 4(%rsp)
                movl %r15d, 8(%rsp)
                movl %r15d, 12(%rsp)
            .Lcic_ca:
                leaq 24(%rbx), %rdi
                movq %rsp, %rsi
                movl %r13d, %edx
                call .Lksu_next
                cmpl $-1, %eax
                je .Lcic_cad
                incl %r15d
                jmp .Lcic_ca
            .Lcic_cad:
                movl %r15d, 28(%rsp)
                xorl %r15d, %r15d
                movl %r15d, 8(%rsp)
                movl %r15d, 12(%rsp)
            .Lcic_cb:
                leaq 24(%r12), %rdi
                leaq 8(%rsp), %rsi
                movl %r14d, %edx
                call .Lksu_next
                cmpl $-1, %eax
                je .Lcic_cbd
                incl %r15d
                jmp .Lcic_cb
            .Lcic_cbd:
                movl %r15d, 32(%rsp)
                movl $0, (%rsp)
                movl $0, 4(%rsp)
                movl $0, 8(%rsp)
                movl $0, 12(%rsp)
            .Lcic_loop:
                leaq 24(%rbx), %rdi
                movq %rsp, %rsi
                movl %r13d, %edx
                call .Lksu_next
                movl %eax, 16(%rsp)
                leaq 24(%r12), %rdi
                leaq 8(%rsp), %rsi
                movl %r14d, %edx
                call .Lksu_next
                movl %eax, 20(%rsp)
                movl 16(%rsp), %ecx
                cmpl 20(%rsp), %ecx
                jne .Lcic_diff
                cmpl $-1, %ecx
                je .Lcic_same
                jmp .Lcic_loop
            .Lcic_diff:
                cmpl $-1, 16(%rsp)
                je .Lcic_length
                cmpl $-1, 20(%rsp)
                je .Lcic_length
                movl 16(%rsp), %eax
                call .Lcic_fup
                movl %eax, 16(%rsp)
                movl 20(%rsp), %eax
                call .Lcic_fup
                movl %eax, 20(%rsp)
                movl 16(%rsp), %ecx
                cmpl 20(%rsp), %ecx
                je .Lcic_loop
                movl 16(%rsp), %eax
                call .Lcic_flo
                movl %eax, 16(%rsp)
                movl 20(%rsp), %eax
                call .Lcic_flo
                movl %eax, 20(%rsp)
                movl 16(%rsp), %ecx
                cmpl 20(%rsp), %ecx
                je .Lcic_loop
                movl 16(%rsp), %eax
                subl 20(%rsp), %eax
                jmp .Lcic_ret
            .Lcic_same:
            .Lcic_length:
                movl 28(%rsp), %eax
                subl 32(%rsp), %eax
            .Lcic_ret:
                movslq %eax, %rax
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            # fold simple upper: eax=unit -> eax (ASCII inline; BMP por tabela).
            .Lcic_fup:
                cmpl $0x80, %eax
                jae .Lcic_fup_tab
                cmpl $97, %eax
                jb .Lcic_fup_r
                cmpl $122, %eax
                ja .Lcic_fup_r
                subl $32, %eax
            .Lcic_fup_r:
                ret
            .Lcic_fup_tab:
                movl %eax, %r9d
                leaq .Lkof_cu_up_tab(%rip), %r10
                xorl %esi, %esi
                movl $@UPN@, %edi
                decl %edi
            .Lcic_fup_bs:
                cmpl %edi, %esi
                jg .Lcic_fup_nf
                leal (%rsi,%rdi), %eax
                shrl $1, %eax
                movzwl (%r10,%rax,4), %edx
                cmpl %edx, %r9d
                je .Lcic_fup_f
                jl .Lcic_fup_hi
                leal 1(%rax), %esi
                jmp .Lcic_fup_bs
            .Lcic_fup_hi:
                leal -1(%rax), %edi
                jmp .Lcic_fup_bs
            .Lcic_fup_f:
                movzwl 2(%r10,%rax,4), %eax
                ret
            .Lcic_fup_nf:
                movl %r9d, %eax
                ret
            # fold simple lower: eax=unit -> eax.
            .Lcic_flo:
                cmpl $0x80, %eax
                jae .Lcic_flo_tab
                cmpl $65, %eax
                jb .Lcic_flo_r
                cmpl $90, %eax
                ja .Lcic_flo_r
                addl $32, %eax
            .Lcic_flo_r:
                ret
            .Lcic_flo_tab:
                movl %eax, %r9d
                leaq .Lkof_cu_lo_tab(%rip), %r10
                xorl %esi, %esi
                movl $@LON@, %edi
                decl %edi
            .Lcic_flo_bs:
                cmpl %edi, %esi
                jg .Lcic_flo_nf
                leal (%rsi,%rdi), %eax
                shrl $1, %eax
                movzwl (%r10,%rax,4), %edx
                cmpl %edx, %r9d
                je .Lcic_flo_f
                jl .Lcic_flo_hi
                leal 1(%rax), %esi
                jmp .Lcic_flo_bs
            .Lcic_flo_hi:
                leal -1(%rax), %edi
                jmp .Lcic_flo_bs
            .Lcic_flo_f:
                movzwl 2(%r10,%rax,4), %eax
                ret
            .Lcic_flo_nf:
                movl %r9d, %eax
                ret
            """;
        cic = cic.replace("@UPN@", Integer.toString(RuntimeStringCase.count(true)))
                 .replace("@LON@", Integer.toString(RuntimeStringCase.count(false)));
        sb.append(cic);
    }
}
