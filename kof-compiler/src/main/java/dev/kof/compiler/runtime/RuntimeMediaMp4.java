package dev.kof.compiler.runtime;

/**
 * Scan MP4/MOV do nativo x86-64 (kof.media Video.durationMs) — paridade com
 * o oraculo JVM (JvmMediaCoreRuntime POS #624): box-size lido como be32
 * UNSIGNED, `size==1` = largesize 64-bit (ISO/IEC 14496-12 §4.2), `size==0`
 * = box ate o fim do container, variavel de varredura externa em 64 bits.
 * Compartilha os labels .Lmed_* dos irmaos (mesmo .s); §503 nao toca aqui
 * (funcao pura, sem .bss).
 */
public final class RuntimeMediaMp4 {

    private RuntimeMediaMp4() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .section .text
            # ── MP4 (paridade exata do oráculo JVM, be32 SINALIZADO) ──
            # .Lmed_mp4_dur(rdi=KofStr data) -> eax=durationMs
            .Lmed_mp4_dur:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movslq 16(%rbx), %r12
                leaq 24(%rbx), %r13
                xorl %r14d, %r14d
            .Lmed_mp4_loop:
                leaq 8(%r14), %rax
                cmpq %r12, %rax
                jg .Lmed_mp4_end
                movl (%r13,%r14), %eax
                bswapl %eax
                movl %eax, %r15d                 # boxSize = be32 UNSIGNED (64-bit)
                testq %r15, %r15
                je .Lmed_mp4_to0                 # size==0: box ate o fim do container
                cmpq $1, %r15
                je .Lmed_mp4_ext                 # size==1: largesize 64-bit (#624)
            .Lmed_mp4_sz:
                cmpq $8, %r15
                jl .Lmed_mp4_end
                movl 4(%r13,%r14), %eax
                bswapl %eax
                cmpl $0x6D6F6F76, %eax           # "moov"
                jne .Lmed_mp4_next
                leal 8(%r14), %esi               # from = (int)pos + 8
                movq %r14, %rax
                addq %r15, %rax
                movl %eax, %edx                  # to = (int)(pos + boxSize)
                movq %rbx, %rdi
                call .Lmed_mvhd_dur
                jmp .Lmed_mp4_ret
            .Lmed_mp4_next:
                addq %r15, %r14                  # pos += boxSize (long — scan 64-bit)
                jmp .Lmed_mp4_loop
            .Lmed_mp4_to0:
                movq %r12, %r15
                subq %r14, %r15                  # boxSize = limit - pos
                jmp .Lmed_mp4_sz
            .Lmed_mp4_ext:
                leaq 16(%r14), %rax
                cmpq %r12, %rax
                jg .Lmed_mp4_end                 # header truncado = -1 = <8 = break
                movq 8(%r13,%r14), %r15
                bswapq %r15                      # largesize (BE 64-bit)
                jmp .Lmed_mp4_sz
            .Lmed_mp4_end:
                xorl %eax, %eax
            .Lmed_mp4_ret:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Lmed_mvhd_dur(rdi=KofStr data, esi=from, edx=to) -> eax=ms
            .Lmed_mvhd_dur:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                leaq 24(%rbx), %r13
                movslq %edx, %r12                # to como long (o limit da JVM)
                movl %esi, %r14d
            .Lmed_mv_loop:
                movslq %r14d, %rax
                addq $8, %rax
                cmpq %r12, %rax
                jg .Lmed_mv_zero
                movl (%r13,%r14), %eax
                bswapl %eax
                movl %eax, %r15d                 # be32 UNSIGNED
                testq %r15, %r15
                je .Lmed_mv_to0
                cmpq $1, %r15
                je .Lmed_mv_ext
            .Lmed_mv_sz:
                cmpq $8, %r15
                jl .Lmed_mv_zero
                movl 4(%r13,%r14), %eax
                bswapl %eax
                cmpl $0x6D766864, %eax           # "mvhd"
                jne .Lmed_mv_next
                movzbl 8(%r13,%r14), %eax
                cmpl $1, %eax
                jne .Lmed_mv_v0
                movslq %r14d, %rax
                addq $40, %rax
                cmpq %r12, %rax
                jg .Lmed_mv_zero
                movl 32(%r13,%r14), %eax
                bswapl %eax
                movq %rax, %rcx
                shlq $32, %rcx
                movl 36(%r13,%r14), %eax
                bswapl %eax
                movslq %eax, %rax                # baixo SINALIZADO (igual JVM)
                orq %rcx, %rax
                movq %rax, %r8
                movl 28(%r13,%r14), %eax         # timescale
                bswapl %eax
                jmp .Lmed_mv_math
            .Lmed_mv_v0:
                movslq %r14d, %rax
                addq $28, %rax
                cmpq %r12, %rax
                jg .Lmed_mv_zero
                movl 24(%r13,%r14), %eax
                bswapl %eax
                movslq %eax, %r8
                movl 20(%r13,%r14), %eax
                bswapl %eax
            .Lmed_mv_math:
                testl %eax, %eax
                jle .Lmed_mv_zero
                movslq %eax, %r9
                movq %r8, %rax
                imulq $1000, %rax, %rax
                cqto
                idivq %r9
                cmpq $2147483647, %rax
                jle .Lmed_mv_ret
                movq $2147483647, %rax
                jmp .Lmed_mv_ret
            .Lmed_mv_next:
                addl %r15d, %r14d                # JVM: pos += (int) boxSize (trunca)
                jmp .Lmed_mv_loop
            .Lmed_mv_to0:
                movq %r12, %r15
                movslq %r14d, %rax
                subq %rax, %r15                  # boxSize = to - pos
                jmp .Lmed_mv_sz
            .Lmed_mv_ext:
                movslq %r14d, %rax
                addq $16, %rax
                cmpq %r12, %rax
                jg .Lmed_mv_zero                 # -1 = <8 = break
                movq 8(%r13,%r14), %r15
                bswapq %r15
                jmp .Lmed_mv_sz
            .Lmed_mv_zero:
                xorl %eax, %eax
            .Lmed_mv_ret:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
