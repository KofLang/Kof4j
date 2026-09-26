package dev.kof.compiler.runtime;

/**
 * Runtime de mídia (kof.media) do nativo x86-64 — faces Audio (WAV RIFF
 * PCM 16-bit), metade da linha 4 do ledger D-FULL-PARITY-050 (MEDIA001).
 * Paridade com o oráculo JVM (JvmMediaCoreRuntime.kof_media_read_wav /
 * kof_media_write_wav): mesma varredura de chunks (le32/le16 LITTLE, "fmt "
 * antes de "data", tamanho ímpar com 1 byte de pad, último "data" vence),
 * mesmas Strings de erro ("not a WAV RIFF: ", "unsupported WAV: codec N
 * (needs PCM 1)", "unsupported WAV: needs PCM 16-bit (bits=N)", "invalid
 * audio: N"). Divergências declaradas: (1) io-fail do JVM embute texto de
 * IOException ("Audio.openWav/saveWav failed: <iomsg>") — aqui aproxima com
 * o path; (2) WAV malformado que no JVM estoura em AIOOBE/
 * NegativeArraySizeException (crash não-capturável) é tratado aqui com a
 * quebra honesta do parse (mesma família de erro, R6).
 *
 * <p>Compartilha os labels .Lmed_* de RuntimeMedia (mesmo .s): slots,
 * sequência, helpers de throw/concat. §503: .balign 8 em todo .quad global.
 */
public final class RuntimeMediaWav {

    private RuntimeMediaWav() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .section .data
            .balign 8
            .Lmed_ao_pre:  .asciz "Audio.openWav failed: "
            .set .Lmed_ao_len, . - .Lmed_ao_pre - 1
            .Lmed_nw_pre:  .asciz "not a WAV RIFF: "
            .set .Lmed_nw_len, . - .Lmed_nw_pre - 1
            .Lmed_cc_pre:  .asciz "unsupported WAV: codec "
            .set .Lmed_cc_len, . - .Lmed_cc_pre - 1
            .Lmed_cc_suf:  .asciz " (needs PCM 1)"
            .set .Lmed_cc_sl, . - .Lmed_cc_suf - 1
            .Lmed_bb_pre:  .asciz "unsupported WAV: needs PCM 16-bit (bits="
            .set .Lmed_bb_len, . - .Lmed_bb_pre - 1
            .Lmed_bb_suf:  .asciz ")"
            .Lmed_ia_pre:  .asciz "invalid audio: "
            .set .Lmed_ia_len, . - .Lmed_ia_pre - 1
            .Lmed_sw_pre:  .asciz "Audio.saveWav failed: "
            .set .Lmed_sw_len, . - .Lmed_sw_pre - 1
            .section .text

            # .Lmed_bad_a(edi=id): lanca "invalid audio: N"
            .Lmed_bad_a:
                movl %edi, %edx
                leaq .Lmed_ia_pre(%rip), %rdi
                movl $.Lmed_ia_len, %esi
                jmp .Lmed_throw_pi

            # ── kof_media_audio_open_wav(path) -> id | throw ────────
            # stack (rsp-64): 0=data, 8=pcm, 16=pcmLen, 24=ch, 28=rate,
            #                  32=bits, 40=hasData
            .globl kof_media_audio_open_wav
            .type kof_media_audio_open_wav, @function
            kof_media_audio_open_wav:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movq %rdi, %rbx                  # path
                movq %rbx, %rdi
                call kof_read_file
                testq %rax, %rax
                je .Lmed_ao_fail
                movq %rax, %r12                  # data
                movslq 16(%r12), %r14            # len
                leaq 24(%r12), %r13              # payload
                cmpq $12, %r14
                jl .Lmed_ao_notwav
                movl (%r13), %eax
                bswapl %eax
                cmpl $0x52494646, %eax           # "RIFF"
                jne .Lmed_ao_notwav
                movl 8(%r13), %eax
                bswapl %eax
                cmpl $0x57415645, %eax           # "WAVE"
                jne .Lmed_ao_notwav
                movq $0, 8(%rsp)                 # pcm
                movq $0, 16(%rsp)                # pcmLen
                movl $0, 24(%rsp)                # ch
                movl $0, 28(%rsp)                # rate
                movl $0, 32(%rsp)                # bits
                movl $0, 40(%rsp)                # hasData
                movl $12, %r15d                  # pos
            .Lmed_ao_loop:
                leal 8(%r15), %eax
                cmpl %r14d, %eax
                jg .Lmed_ao_end                  # pos+8 > len
                movl 4(%r13,%r15), %eax          # size = le32 (sinalizado)
                leal 8(%r15), %ecx               # body = pos+8
                leal (%ecx,%eax), %edx           # body+size (int wrap)
                cmpl %r14d, %edx
                jg .Lmed_ao_end                  # corpo ultrapassa o arquivo
                movl (%r13,%r15), %esi
                bswapl %esi
                cmpl $0x666D7420, %esi           # "fmt "
                jne .Lmed_ao_data
                leal 16(%ecx), %eax
                cmpl %r14d, %eax
                jg .Lmed_ao_end                  # fmt curto (JVM: AIOOBE — declarado)
                movzwl (%r13,%rcx), %eax         # audioFormat
                cmpl $1, %eax
                jne .Lmed_ao_codec
                movzwl 2(%r13,%rcx), %eax
                movl %eax, 24(%rsp)              # channels
                movl 4(%r13,%rcx), %eax
                movl %eax, 28(%rsp)              # sampleRate
                movzwl 14(%r13,%rcx), %eax
                movl %eax, 32(%rsp)              # bits
                jmp .Lmed_ao_advance
            .Lmed_ao_data:
                cmpl $0x64617461, %esi           # "data"
                jne .Lmed_ao_advance
                testl %eax, %eax
                jl .Lmed_ao_end                  # size<0 (JVM: NegativeArraySize — declarado)
                movl $1, 40(%rsp)                # hasData
                movslq %eax, %rdx
                movq %rdx, 16(%rsp)              # pcmLen
                testl %eax, %eax
                jne .Lmed_ao_dcopy
                jmp .Lmed_ao_advance
            .Lmed_ao_dcopy:
                movl %eax, %edi
                incl %edi
                call kof_alloc
                movq %rax, 8(%rsp)
                movq %rax, %rdi
                leaq 8(%r13,%r15), %rsi          # body
                movl 4(%r13,%r15), %edx
                movslq %edx, %rdx
                call kof_memcpy
            .Lmed_ao_advance:
                movl 4(%r13,%r15), %eax
                leal 8(%r15,%rax), %r15d         # pos = body + size
                testb $1, %al
                je .Lmed_ao_loop
                incl %r15d                       # + (size & 1)
                jmp .Lmed_ao_loop
            .Lmed_ao_end:
                cmpl $0, 40(%rsp)
                je .Lmed_ao_bits                 # data==null
                movl 28(%rsp), %eax
                testl %eax, %eax
                jle .Lmed_ao_bits                # sampleRate<=0
                cmpl $16, 32(%rsp)
                jne .Lmed_ao_bits                # bits!=16
                call .Lmed_next_seq
                movl %eax, %r15d                 # id
                call .Lmed_aslot
                movq $1, (%rax)
                movl %r15d, 8(%rax)
                movq 8(%rsp), %rcx
                movq %rcx, 16(%rax)              # pcm (pode ser 0 = vazio)
                movq 16(%rsp), %rcx
                movq %rcx, 24(%rax)              # pcmLen
                movl 28(%rsp), %ecx
                movl %ecx, 32(%rax)              # rate
                movl 24(%rsp), %ecx
                movl %ecx, 40(%rax)              # ch
                movl %r15d, %eax
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lmed_ao_codec:
                movl %eax, %edx
                addq $64, %rsp
                leaq .Lmed_cc_pre(%rip), %rdi
                movl $.Lmed_cc_len, %esi
                leaq .Lmed_cc_suf(%rip), %rcx
                movl $.Lmed_cc_sl, %r8d
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                jmp .Lmed_throw_pis
            .Lmed_ao_bits:
                movl 32(%rsp), %edx
                addq $64, %rsp
                leaq .Lmed_bb_pre(%rip), %rdi
                movl $.Lmed_bb_len, %esi
                leaq .Lmed_bb_suf(%rip), %rcx
                movl $1, %r8d
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                jmp .Lmed_throw_pis
            .Lmed_ao_notwav:
                addq $64, %rsp
                leaq .Lmed_nw_pre(%rip), %rdi
                movl $.Lmed_nw_len, %esi
                movq %rbx, %rdx
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                jmp .Lmed_throw_ps
            .Lmed_ao_fail:
                addq $64, %rsp
                leaq .Lmed_ao_pre(%rip), %rdi
                movl $.Lmed_ao_len, %esi
                movq %rbx, %rdx
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                jmp .Lmed_throw_ps

            # ── kof_media_audio_sample_rate(id) -> int ──────────────
            .globl kof_media_audio_sample_rate
            .type kof_media_audio_sample_rate, @function
            kof_media_audio_sample_rate:
                call .Lmed_find_a
                testq %rax, %rax
                je .Lmed_sr_bad
                movl 32(%rax), %eax
                ret
            .Lmed_sr_bad:
                call .Lmed_bad_a
                ud2

            # ── kof_media_audio_duration_ms(id) -> int ──────────────
            # JVM: frames = pcm.length / (2L*channels); ms = frames*1000/rate
            .globl kof_media_audio_duration_ms
            .type kof_media_audio_duration_ms, @function
            kof_media_audio_duration_ms:
                pushq %rbx
                call .Lmed_find_a
                testq %rax, %rax
                je .Lmed_dm_bad
                movq %rax, %rbx
                movl 32(%rbx), %eax
                testl %eax, %eax
                jle .Lmed_dm_zero                # rate<=0
                movl 40(%rbx), %ecx
                testl %ecx, %ecx
                jle .Lmed_dm_zero                # ch<=0
                movq 24(%rbx), %rax              # pcmLen
                leal 0(,%rcx,2), %edx
                movl %edx, %edx                  # 2*ch (int) — JVM 2L*channels
                movq %rdx, %rsi
                xorq %rdx, %rdx
                divq %rsi                        # frames (unsigned: len>=0, ch>0)
                imulq $1000, %rax, %rax
                movslq 32(%rbx), %rsi            # rate (signed)
                cqto
                idivq %rsi
                popq %rbx
                ret
            .Lmed_dm_zero:
                xorl %eax, %eax
                popq %rbx
                ret
            .Lmed_dm_bad:
                call .Lmed_bad_a
                ud2

            # ── kof_media_audio_pcm_bytes(id) -> Int[] ──────────────
            .globl kof_media_audio_pcm_bytes
            .type kof_media_audio_pcm_bytes, @function
            kof_media_audio_pcm_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                call .Lmed_find_a
                testq %rax, %rax
                je .Lmed_pb_bad
                movq %rax, %rbx
                movslq 24(%rbx), %r12
                movq %r12, %rdi
                movl $4, %esi
                call kof_array_alloc
                movq %rax, %r13
                movq 16(%rbx), %rcx
                xorl %edx, %edx
            .Lmed_pb_loop:
                cmpq %r12, %rdx
                jge .Lmed_pb_done
                movsbl (%rcx,%rdx), %eax         # SIGNED (JVM out[i]=b[i])
                movl %eax, 24(%r13,%rdx,4)
                incq %rdx
                jmp .Lmed_pb_loop
            .Lmed_pb_done:
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lmed_pb_bad:
                call .Lmed_bad_a
                ud2

            # ── kof_media_audio_save_wav(id, path) -> 1 | throw ─────
            .globl kof_media_audio_save_wav
            .type kof_media_audio_save_wav, @function
            kof_media_audio_save_wav:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rsi, %r12                  # path
                call .Lmed_find_a
                testq %rax, %rax
                je .Lmed_sw_bad
                movq %rax, %rbx                  # slot
                # parent mkdirs (JVM createDirectories(p.getParent()))
                movl 16(%r12), %r13d
                leaq 24(%r12), %r14
                movl %r13d, %ecx
            .Lmed_sw_scan:
                testl %ecx, %ecx
                jle .Lmed_sw_write
                decl %ecx
                cmpb $47, (%r14,%rcx)
                jne .Lmed_sw_scan
                testl %ecx, %ecx
                jne .Lmed_sw_parent
                movl $1, %r13d                   # parent = "/" (idx 0)
                jmp .Lmed_sw_slice
            .Lmed_sw_parent:
                movl %ecx, %r13d                 # parent = [0, idx)
            .Lmed_sw_slice:
                movq %r12, %rdi
                xorl %esi, %esi
                movl %r13d, %edx
                call .Lmed_str_slice
                movq %rax, %rdi
                call kof_io_dir_create_dirs
                testq %rax, %rax
                je .Lmed_sw_mkdir_fail
            .Lmed_sw_write:
                movl 40(%rbx), %r13d             # ch
                movl 32(%rbx), %r14d             # rate
                movslq 24(%rbx), %r15            # pcmLen
                movq %r15, %rdi
                addq $69, %rdi                   # 24 header + 44 + len + 1
                call kof_alloc
                movq %rax, %r8                   # buf
                movl $1, 0(%r8)
                movl $0, 4(%r8)
                movq $0, 8(%r8)
                leaq 44(%r15), %rax
                movl %eax, 16(%r8)               # len = 44 + pcmLen
                movl $0, 20(%r8)
                movq %r8, %rsi
                addq $24, %rsi                   # payload
                movl $0x46464952, (%rsi)         # "RIFF" (LE bytes)
                leal 36(%r15), %eax
                movl %eax, 4(%rsi)
                movl $0x45564157, 8(%rsi)        # "WAVE"
                movl $0x20746D66, 12(%rsi)       # "fmt "
                movl $16, 16(%rsi)
                movw $1, 20(%rsi)                # PCM
                movw %r13w, 22(%rsi)             # channels
                movl %r14d, 24(%rsi)             # sampleRate
                movl %r13d, %eax
                imull %r14d, %eax
                addl %eax, %eax                  # byteRate = rate*ch*2 (int)
                movl %eax, 28(%rsi)
                movl %r13d, %eax
                addl %eax, %eax
                movw %ax, 32(%rsi)               # blockAlign = ch*2
                movw $16, 34(%rsi)               # bits
                movl $0x61746164, 36(%rsi)       # "data"
                movl %r15d, 40(%rsi)             # data size
                movq 16(%rbx), %rcx              # pcm (0 se vazio)
                xorq %rdx, %rdx
            .Lmed_sw_copy:
                cmpq %r15, %rdx
                jge .Lmed_sw_copy_done
                movb (%rcx,%rdx), %al
                movb %al, 44(%rsi,%rdx)
                incq %rdx
                jmp .Lmed_sw_copy
            .Lmed_sw_copy_done:
                movq %r12, %rdi                  # path
                movq %r8, %rsi                   # wav KofStr
                call kof_write_file
                testq %rax, %rax
                jne .Lmed_sw_fail
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lmed_sw_fail:
                leaq .Lmed_sw_pre(%rip), %rdi
                movl $.Lmed_sw_len, %esi
                movq %r12, %rdx
                call .Lmed_throw_ps
            .Lmed_sw_mkdir_fail:
                leaq .Lmed_sw_pre(%rip), %rdi
                movl $.Lmed_sw_len, %esi
                movq %r12, %rdx
                call .Lmed_throw_ps
            .Lmed_sw_bad:
                call .Lmed_bad_a
                ud2
            """);
    }
}
