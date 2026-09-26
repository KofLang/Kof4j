package dev.kof.compiler.runtime;

/**
 * Runtime de mídia (kof.media) do nativo x86-64 — infraestrutura comum
 * (registro de handles, helpers de erro) + faces Video (metadados MP4/MOV).
 * D-FULL-PARITY-050 linha 4 (MEDIA001), paridade com o oráculo JVM
 * (JvmMediaCoreRuntime). A metade Audio (WAV) vive em RuntimeMediaWav (mesmo
 * .s: os labels .Lmed_* são compartilhados).
 *
 * <p>IDs da MESMA sequência global do JVM (KOF_MEDIA_SEQ, compartilhada
 * Audio/Video). Cap de 64 handles com throw honesto (divergência declarada:
 * o JVM usa mapas sem cap — mesmo modelo do cap 64 do shell pipeline).
 * Erros lançam a MESMA String do JVM ("file not found: ", "invalid video:
 * N"). Divergências declaradas: (1) "Video.open failed: <iomsg>" do JVM
 * embute texto de IOException — aqui aproxima com o path; (2) path relativo:
 * JVM resolve contra -Dkof.root, nativo contra o CWD do processo (face do
 * runner CLI). Âncoras: §503 — .balign 8 em todo global que guarda ponteiro
 * de heap.
 */
public final class RuntimeMedia {

    private RuntimeMedia() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .section .data
            .balign 8
            .Lmed_mp4_str:
                .long 1
                .long 0
                .quad 0
                .long 3
                .long 0
                .asciz "mp4"
            .Lmed_mpeg_str:
                .long 1
                .long 0
                .quad 0
                .long 4
                .long 0
                .asciz "mpeg"
            .Lmed_nf_pre:  .asciz "file not found: "
            .set .Lmed_nf_len, . - .Lmed_nf_pre - 1
            .Lmed_vo_pre:  .asciz "Video.open failed: "
            .set .Lmed_vo_len, . - .Lmed_vo_pre - 1
            .Lmed_iv_pre:  .asciz "invalid video: "
            .set .Lmed_iv_len, . - .Lmed_iv_pre - 1
            .Lmed_tm:      .asciz "media: too many open handles (max 64)"
            .set .Lmed_tm_len, . - .Lmed_tm - 1
            .section .bss
            .balign 8
            .Lmed_seq:     .zero 8
            .balign 8
            .Lmed_vslots:  .zero 3584
            .balign 8
            .Lmed_aslots:  .zero 3072
            .section .text

            # .Lmed_throw_ps(rdi=pre, esi=preLen, rdx=KofStr sufixo)
            .Lmed_throw_ps:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdx, %r13
                call kof_string_from_literal
                movq %rax, %rdi
                movq %r13, %rsi
                call kof_string_concat
                movq %rax, %rdi
                call kof_throw_string
                ud2

            # .Lmed_throw_pi(rdi=pre, esi=preLen, edx=int)
            .Lmed_throw_pi:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edx, %r13d
                call kof_string_from_literal
                movq %rax, %rbx
                movl %r13d, %edi
                call kof_int_to_string
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rdi
                call kof_throw_string
                ud2

            # .Lmed_throw_pis(rdi=pre, esi=preLen, edx=int, rcx=suf, r8=sufLen)
            .Lmed_throw_pis:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rcx, %r14
                movl %r8d, %r13d
                movl %edx, %r12d
                call kof_string_from_literal
                movq %rax, %rbx
                movl %r12d, %edi
                call kof_int_to_string
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                movq %r14, %rdi
                movl %r13d, %esi
                call kof_string_from_literal
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rdi
                call kof_throw_string
                ud2

            # .Lmed_bad_v(edi=id): lanca "invalid video: N"
            .Lmed_bad_v:
                movl %edi, %edx
                leaq .Lmed_iv_pre(%rip), %rdi
                movl $.Lmed_iv_len, %esi
                jmp .Lmed_throw_pi

            # .Lmed_str_slice(rdi=KofStr src, esi=off, edx=len) -> rax=KofStr
            .Lmed_str_slice:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movl %esi, %r12d
                movl %edx, %r13d
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %r14
                movl $1, 0(%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %r13d, 16(%r14)
                movl $0, 20(%r14)
                testl %r13d, %r13d
                je .Lmed_ss_done
                movq %r14, %rdi
                addq $24, %rdi
                leaq 24(%rbx), %rsi
                movslq %r12d, %r12
                addq %r12, %rsi
                movl %r13d, %edx
                call kof_memcpy
            .Lmed_ss_done:
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Lmed_next_seq() -> eax=id (sequência global c/ Audio)
            .Lmed_next_seq:
                movq .Lmed_seq(%rip), %rax
                incq %rax
                movq %rax, .Lmed_seq(%rip)
                ret

            # .Lmed_vslot() -> rax=vaga livre (throw cap-64 se cheia);
            # slot 56B: in_use@0,id@8,data@16,len@24,fmt@32,path@40,dur@48
            .Lmed_vslot:
                xorq %rcx, %rcx
            .Lmed_vs:
                cmpq $64, %rcx
                jge .Lmed_vs_full
                leaq .Lmed_vslots(%rip), %rax
                imulq $56, %rcx, %rdx
                addq %rdx, %rax
                cmpq $0, (%rax)
                je .Lmed_vs_yes
                incq %rcx
                jmp .Lmed_vs
            .Lmed_vs_yes:
                ret
            .Lmed_vs_full:
                leaq .Lmed_tm(%rip), %rdi
                movl $.Lmed_tm_len, %esi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
                ud2

            # .Lmed_aslot() -> rax=vaga livre; slot 48B: in_use@0,id@8,
            # pcm@16,len@24,rate@32,ch@40
            .Lmed_aslot:
                xorq %rcx, %rcx
            .Lmed_as:
                cmpq $64, %rcx
                jge .Lmed_as_full
                leaq .Lmed_aslots(%rip), %rax
                imulq $48, %rcx, %rdx
                addq %rdx, %rax
                cmpq $0, (%rax)
                je .Lmed_as_yes
                incq %rcx
                jmp .Lmed_as
            .Lmed_as_yes:
                ret
            .Lmed_as_full:
                leaq .Lmed_tm(%rip), %rdi
                movl $.Lmed_tm_len, %esi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
                ud2

            # .Lmed_find_v(edi=id) -> rax=slot|0
            .Lmed_find_v:
                xorq %rcx, %rcx
            .Lmed_fv:
                cmpq $64, %rcx
                jge .Lmed_fv_no
                leaq .Lmed_vslots(%rip), %rax
                imulq $56, %rcx, %rdx
                addq %rdx, %rax
                cmpq $0, (%rax)
                je .Lmed_fv_next
                cmpl %edi, 8(%rax)
                je .Lmed_fv_yes
            .Lmed_fv_next:
                incq %rcx
                jmp .Lmed_fv
            .Lmed_fv_yes:
                ret
            .Lmed_fv_no:
                xorl %eax, %eax
                ret

            # .Lmed_find_a(edi=id) -> rax=slot|0
            .Lmed_find_a:
                xorq %rcx, %rcx
            .Lmed_fa:
                cmpq $64, %rcx
                jge .Lmed_fa_no
                leaq .Lmed_aslots(%rip), %rax
                imulq $48, %rcx, %rdx
                addq %rdx, %rax
                cmpq $0, (%rax)
                je .Lmed_fa_next
                cmpl %edi, 8(%rax)
                je .Lmed_fa_yes
            .Lmed_fa_next:
                incq %rcx
                jmp .Lmed_fa
            .Lmed_fa_yes:
                ret
            .Lmed_fa_no:
                xorl %eax, %eax
                ret

            # .Lmed_ext_fmt(rdi=KofStr path) -> rax=KofStr — paridade do
            # kof_media_video_format JVM (nome apos ultimo '/', minusculo,
            # ultimo '.', sem dot -> "mp4", mpeg/mpg -> "mpeg", senao a ext)
            .Lmed_ext_fmt:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leaq 24(%rbx), %r13
                movl $0, %r14d                   # start do nome
                movl %r12d, %ecx
            .Lmed_ef_slash:
                testl %ecx, %ecx
                jle .Lmed_ef_slash_done
                decl %ecx
                cmpb $47, (%r13,%rcx)
                jne .Lmed_ef_slash
                leal 1(%rcx), %r14d
            .Lmed_ef_slash_done:
                movl %r12d, %r15d                # sentinela dot = len
                movl %r14d, %ecx
            .Lmed_ef_dot:
                cmpl %r12d, %ecx
                jge .Lmed_ef_dot_done
                cmpb $46, (%r13,%rcx)
                jne .Lmed_ef_dot_next
                movl %ecx, %r15d
            .Lmed_ef_dot_next:
                incl %ecx
                jmp .Lmed_ef_dot
            .Lmed_ef_dot_done:
                cmpl %r14d, %r15d
                jge .Lmed_ef_mp4
                incl %r15d
                movl %r12d, %edx
                subl %r15d, %edx                 # len da ext
                cmpl $4, %edx
                jne .Lmed_ef_mpg
                cmpb $109, (%r13,%r15)
                jne .Lmed_ef_slice
                cmpb $112, 1(%r13,%r15)
                jne .Lmed_ef_slice
                cmpb $101, 2(%r13,%r15)
                jne .Lmed_ef_slice
                cmpb $103, 3(%r13,%r15)
                jne .Lmed_ef_slice
                jmp .Lmed_ef_mpeg
            .Lmed_ef_mpg:
                cmpl $3, %edx
                jne .Lmed_ef_slice
                cmpb $109, (%r13,%r15)
                jne .Lmed_ef_slice
                cmpb $112, 1(%r13,%r15)
                jne .Lmed_ef_slice
                cmpb $103, 2(%r13,%r15)
                jne .Lmed_ef_slice
            .Lmed_ef_mpeg:
                leaq .Lmed_mpeg_str(%rip), %rbx
                jmp .Lmed_ef_ret
            .Lmed_ef_mp4:
                leaq .Lmed_mp4_str(%rip), %rbx
                jmp .Lmed_ef_ret
            .Lmed_ef_slice:
                movq %rbx, %rdi
                movl %r15d, %esi
                movl %r12d, %edx
                subl %r15d, %edx
                call .Lmed_str_slice
                movq %rax, %rbx
                movl 16(%rbx), %ecx
                leaq 24(%rbx), %rdx
            .Lmed_ef_lower:
                testl %ecx, %ecx
                jle .Lmed_ef_ret
                decl %ecx
                movzbl (%rdx,%rcx), %eax
                cmpl $65, %eax
                jb .Lmed_ef_lower
                cmpl $90, %eax
                ja .Lmed_ef_lower
                addb $32, (%rdx,%rcx)
                jmp .Lmed_ef_lower
            .Lmed_ef_ret:
                movq %rbx, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── faces Video ─────────────────────────────────────────
            .globl kof_media_video_open
            .type kof_media_video_open, @function
            kof_media_video_open:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                subq $144, %rsp
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq %rsp, %rdx
                xorq %r10, %r10
                movq $262, %rax
                syscall
                testq %rax, %rax
                js .Lmed_vo_nf
                movl 24(%rsp), %eax
                andl $61440, %eax
                cmpl $32768, %eax                # S_IFREG
                jne .Lmed_vo_nf
                addq $144, %rsp
                movq %rbx, %rdi
                call kof_read_file
                testq %rax, %rax
                je .Lmed_vo_fail
                movq %rax, %r12                  # data
                movq %rbx, %rdi
                call .Lmed_ext_fmt
                movq %rax, %r13                  # fmt
                movq %r12, %rdi
                call .Lmed_mp4_dur
                movl %eax, %r14d                 # dur
                call .Lmed_next_seq
                movl %eax, %r15d                 # id
                call .Lmed_vslot
                movq $1, (%rax)
                movl %r15d, 8(%rax)
                movq %r12, 16(%rax)
                movslq 16(%r12), %rcx
                movq %rcx, 24(%rax)
                movq %r13, 32(%rax)
                movq %rbx, 40(%rax)
                movl %r14d, 48(%rax)
                movl %r15d, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lmed_vo_nf:
                addq $144, %rsp
                leaq .Lmed_nf_pre(%rip), %rdi
                movl $.Lmed_nf_len, %esi
                movq %rbx, %rdx
                call .Lmed_throw_ps
            .Lmed_vo_fail:
                leaq .Lmed_vo_pre(%rip), %rdi
                movl $.Lmed_vo_len, %esi
                movq %rbx, %rdx
                call .Lmed_throw_ps

            .globl kof_media_video_path
            .type kof_media_video_path, @function
            kof_media_video_path:
                call .Lmed_find_v
                testq %rax, %rax
                je .Lmed_vp_bad
                movq 40(%rax), %rax
                ret
            .Lmed_vp_bad:
                call .Lmed_bad_v
                ud2

            .globl kof_media_video_format
            .type kof_media_video_format, @function
            kof_media_video_format:
                call .Lmed_find_v
                testq %rax, %rax
                je .Lmed_vf_bad
                movq 32(%rax), %rax
                ret
            .Lmed_vf_bad:
                call .Lmed_bad_v
                ud2

            .globl kof_media_video_size
            .type kof_media_video_size, @function
            kof_media_video_size:
                call .Lmed_find_v
                testq %rax, %rax
                je .Lmed_vsz_bad
                movl 24(%rax), %eax
                ret
            .Lmed_vsz_bad:
                call .Lmed_bad_v
                ud2

            .globl kof_media_video_duration_ms
            .type kof_media_video_duration_ms, @function
            kof_media_video_duration_ms:
                call .Lmed_find_v
                testq %rax, %rax
                je .Lmed_vd_bad
                movl 48(%rax), %eax
                ret
            .Lmed_vd_bad:
                call .Lmed_bad_v
                ud2

            .globl kof_media_video_bytes
            .type kof_media_video_bytes, @function
            kof_media_video_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                call .Lmed_find_v
                testq %rax, %rax
                je .Lmed_vb_bad
                movq %rax, %rbx
                movslq 24(%rbx), %r12
                movq %r12, %rdi
                movl $4, %esi
                call kof_array_alloc
                movq %rax, %r13
                movq 16(%rbx), %r14
                addq $24, %r14
                xorl %ecx, %ecx
            .Lmed_vb_loop:
                cmpq %r12, %rcx
                jge .Lmed_vb_done
                movsbl (%r14,%rcx), %eax         # SIGNED (JVM out[i]=b[i])
                movl %eax, 24(%r13,%rcx,4)
                incq %rcx
                jmp .Lmed_vb_loop
            .Lmed_vb_done:
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lmed_vb_bad:
                call .Lmed_bad_v
                ud2

            # close = remove silente (JVM map.remove sem erro em id ruim)
            .globl kof_media_video_close
            .type kof_media_video_close, @function
            kof_media_video_close:
                call .Lmed_find_v
                testq %rax, %rax
                je .Lmed_vc_ret
                movq $0, (%rax)
            .Lmed_vc_ret:
                ret

            """);
    }
}
