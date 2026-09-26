package dev.kof.compiler.nat;

/**
 * D-FULL-PARITY-050 linha 4 FATIA 2A (26/09): faces Video do kof.media no
 * cross riscv64 (aarch64 herda via {@code NativeAarch64Translator}). Port
 * byte-fiel de {@code RuntimeMedia} x86 (infra de handles + Video/MP4); o
 * scanner MP4 vive em {@code NativeRiscvAsmMediaMp4} (irmao do par x86
 * RuntimeMedia/RuntimeMediaMp4). Mesmos labels .Lmed_* — homonimos cross-peca
 * continuam 0 (disciplina das fatias B*). Audio/Image/Mic NAO estao aqui
 * (MEDIA001 declarado ate as fatias seguintes). §503: .balign 8 em todo
 * global que guarda ponteiro de heap. Divergencias herda das docs do x86
 * (cap 64, "Video.open failed" aproxima com o path, path contra o CWD).
 */
public final class NativeRiscvAsmMedia {

    private NativeRiscvAsmMedia() {}

    static String RISCV_ASM_MEDIA = """
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
            .Lmed_nf_pre:  .ascii "file not found: "
            .Lmed_vo_pre:  .ascii "Video.open failed: "
            .Lmed_iv_pre:  .ascii "invalid video: "
            .Lmed_tm:      .ascii "media: too many open handles (max 64)"
            .section .bss
            .balign 8
            .Lmed_seq:     .zero 8
            .balign 8
            .Lmed_vslots:  .zero 3584
            .section .text

            # .Lmed_throw_ps(a0=pre,a1=preLen,a2=KofStr sufixo)
            .Lmed_throw_ps:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s2, a2
                call kof_string_from_literal
                mv   s0, a0
                mv   a1, s2
                call kof_string_concat
                call kof_throw_string
                li   a0, 1
                call kof_plat_exit

            # .Lmed_throw_pi(a0=pre,a1=preLen,a2=int)
            .Lmed_throw_pi:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)
                sd   s1, 16(sp)
                sd   s2, 8(sp)
                mv   s2, a2
                call kof_string_from_literal
                mv   s0, a0
                mv   a0, s2
                call kof_int_to_string
                mv   s1, a0
                mv   a0, s0
                mv   a1, s1
                call kof_string_concat
                call kof_throw_string
                li   a0, 1
                call kof_plat_exit

            # .Lmed_bad_v(a0=id): lanca "invalid video: N"
            .Lmed_bad_v:
                mv   a2, a0
                la   a0, .Lmed_iv_pre
                li   a1, 15
                j    .Lmed_throw_pi

            # .Lmed_str_slice(a0=src,a1=off,a2=len) -> a0=KofStr
            .Lmed_str_slice:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                addi a0, s2, 25
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   s2, 16(s3)
                sw   zero, 20(s3)
                beqz s2, .Lmed_ss_done
                addi a0, s3, 24
                addi a1, s0, 24
                mv   t1, s1
                slli t1, t1, 32
                srai t1, t1, 32
                add  a1, a1, t1
                mv   a2, s2
                call kof_memcpy
            .Lmed_ss_done:
                mv   a0, s3
                ld   s4, 0(sp)
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # .Lmed_next_seq() -> a0=id (sequencia global c/ Audio no x86)
            .Lmed_next_seq:
                la   t0, .Lmed_seq
                ld   t1, 0(t0)
                addi t1, t1, 1
                sd   t1, 0(t0)
                mv   a0, t1
                ret

            # .Lmed_vslot() -> a0=vaga (throw cap-64); slot 56B:
            # in_use@0,id@8,data@16,len@24,fmt@32,path@40,dur@48
            .Lmed_vslot:
                li   t0, 0
                la   t1, .Lmed_vslots
                li   t2, 64
                li   t3, 56
            .Lmed_vs:
                bge  t0, t2, .Lmed_vs_full
                mul  t4, t0, t3
                add  t4, t4, t1
                ld   t5, 0(t4)
                beqz t5, .Lmed_vs_yes
                addi t0, t0, 1
                j    .Lmed_vs
            .Lmed_vs_yes:
                mv   a0, t4
                ret
            .Lmed_vs_full:
                la   a0, .Lmed_tm
                li   a1, 37
                call kof_string_from_literal
                call kof_throw_string
                li   a0, 1
                call kof_plat_exit

            # .Lmed_find_v(a0=id) -> a0=slot|0
            .Lmed_find_v:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0
                li   t0, 0
                la   t1, .Lmed_vslots
                li   t2, 64
                li   t3, 56
            .Lmed_fv:
                bge  t0, t2, .Lmed_fv_no
                mul  t4, t0, t3
                add  t4, t4, t1
                ld   t5, 0(t4)
                beqz t5, .Lmed_fv_next
                lw   t6, 8(t4)
                beq  t6, s0, .Lmed_fv_yes
            .Lmed_fv_next:
                addi t0, t0, 1
                j    .Lmed_fv
            .Lmed_fv_yes:
                mv   a0, t4
                j    .Lmed_fv_ret
            .Lmed_fv_no:
                mv   a0, zero
            .Lmed_fv_ret:
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # .Lmed_ext_fmt(a0=KofStr path) -> a0=KofStr fmt — paridade do
            # kof_media_video_format JVM (nome apos ultimo '/', minusculo,
            # ultimo '.', sem dot -> "mp4", mpeg/mpg -> "mpeg", senao a ext)
            .Lmed_ext_fmt:
                addi sp, sp, -56
                sd   ra, 48(sp)
                sd   s0, 40(sp)
                sd   s1, 32(sp)
                sd   s2, 24(sp)
                sd   s3, 16(sp)
                sd   s4, 8(sp)
                sd   s5, 0(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                addi s2, s0, 24
                li   s3, 0
                mv   t0, s1
            .Lmed_ef_slash:
                bge  zero, t0, .Lmed_ef_slash_done
                addi t0, t0, -1
                add  t1, s2, t0
                lbu  t2, 0(t1)
                li   t3, 47
                bne  t2, t3, .Lmed_ef_slash
                addi s3, t0, 1
            .Lmed_ef_slash_done:
                mv   t4, s1
                mv   t0, s3
            .Lmed_ef_dot:
                bge  t0, s1, .Lmed_ef_dot_done
                add  t1, s2, t0
                lbu  t2, 0(t1)
                li   t3, 46
                bne  t2, t3, .Lmed_ef_dot_next
                mv   t4, t0
            .Lmed_ef_dot_next:
                addi t0, t0, 1
                j    .Lmed_ef_dot
            .Lmed_ef_dot_done:
                bge  t4, s3, .Lmed_ef_mp4
                addi t4, t4, 1
                mv   t5, s1
                sub  t5, t5, t4
                li   t3, 4
                bne  t5, t3, .Lmed_ef_mpg
                add  t1, s2, t4
                lbu  t2, 0(t1)
                li   t3, 109
                bne  t2, t3, .Lmed_ef_slice
                lbu  t2, 1(t1)
                li   t3, 112
                bne  t2, t3, .Lmed_ef_slice
                lbu  t2, 2(t1)
                li   t3, 101
                bne  t2, t3, .Lmed_ef_slice
                lbu  t2, 3(t1)
                li   t3, 103
                bne  t2, t3, .Lmed_ef_slice
                j    .Lmed_ef_mpeg
            .Lmed_ef_mpg:
                li   t3, 3
                bne  t5, t3, .Lmed_ef_slice
                add  t1, s2, t4
                lbu  t2, 0(t1)
                li   t3, 109
                bne  t2, t3, .Lmed_ef_slice
                lbu  t2, 1(t1)
                li   t3, 112
                bne  t2, t3, .Lmed_ef_slice
                lbu  t2, 2(t1)
                li   t3, 103
                bne  t2, t3, .Lmed_ef_slice
            .Lmed_ef_mpeg:
                la   s5, .Lmed_mpeg_str
                j    .Lmed_ef_ret
            .Lmed_ef_mp4:
                la   s5, .Lmed_mp4_str
                j    .Lmed_ef_ret
            .Lmed_ef_slice:
                mv   a0, s0
                mv   a1, t4
                mv   a2, t5
                call .Lmed_str_slice
                mv   s5, a0
                lw   t0, 16(s5)
                addi t1, s5, 24
            .Lmed_ef_lower:
                bge  zero, t0, .Lmed_ef_ret
                addi t0, t0, -1
                add  t2, t1, t0
                lbu  t3, 0(t2)
                li   t4, 65
                blt  t3, t4, .Lmed_ef_lower
                li   t4, 90
                bgt  t3, t4, .Lmed_ef_lower
                addi t3, t3, 32
                sb   t3, 0(t2)
                j    .Lmed_ef_lower
            .Lmed_ef_ret:
                mv   a0, s5
                ld   s5, 0(sp)
                ld   s4, 8(sp)
                ld   s3, 16(sp)
                ld   s2, 24(sp)
                ld   s1, 32(sp)
                ld   s0, 40(sp)
                ld   ra, 48(sp)
                addi sp, sp, 56
                ret

            # ── faces Video ─────────────────────────────────────────
            .globl kof_media_video_open
            .type kof_media_video_open, @function
            kof_media_video_open:
                addi sp, sp, -192
                sd   ra, 184(sp)
                sd   s0, 176(sp)
                sd   s1, 168(sp)
                sd   s2, 160(sp)
                sd   s3, 152(sp)
                sd   s4, 144(sp)
                mv   s0, a0
                li   a0, -100
                addi a1, s0, 24
                mv   a2, sp
                li   a3, 0
                li   a7, 79
                ecall
                blt  a0, zero, .Lmed_vo_nf
                lw   t1, 16(sp)
                srli t1, t1, 12
                andi t1, t1, 15
                li   t2, 8
                bne  t1, t2, .Lmed_vo_nf
                mv   a0, s0
                call kof_io_read_text
                beqz a0, .Lmed_vo_fail
                mv   s1, a0
                mv   a0, s0
                call .Lmed_ext_fmt
                mv   s2, a0
                mv   a0, s1
                call .Lmed_mp4_dur
                mv   s3, a0
                call .Lmed_next_seq
                mv   s4, a0
                call .Lmed_vslot
                li   t1, 1
                sd   t1, 0(a0)
                sw   s4, 8(a0)
                sd   s1, 16(a0)
                lw   t2, 16(s1)
                sd   t2, 24(a0)
                sd   s2, 32(a0)
                sd   s0, 40(a0)
                sw   s3, 48(a0)
                mv   a0, s4
                ld   s4, 144(sp)
                ld   s3, 152(sp)
                ld   s2, 160(sp)
                ld   s1, 168(sp)
                ld   s0, 176(sp)
                ld   ra, 184(sp)
                addi sp, sp, 192
                ret
            .Lmed_vo_nf:
                addi sp, sp, 192
                la   a0, .Lmed_nf_pre
                li   a1, 16
                mv   a2, s0
                call .Lmed_throw_ps
            .Lmed_vo_fail:
                addi sp, sp, 192
                la   a0, .Lmed_vo_pre
                li   a1, 19
                mv   a2, s0
                call .Lmed_throw_ps

            .globl kof_media_video_path
            .type kof_media_video_path, @function
            kof_media_video_path:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0
                call .Lmed_find_v
                beqz a0, .Lmed_vp_bad
                ld   a0, 40(a0)
                j    .Lmed_vp_ret
            .Lmed_vp_bad:
                mv   a0, s0
                call .Lmed_bad_v
            .Lmed_vp_ret:
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_media_video_format
            .type kof_media_video_format, @function
            kof_media_video_format:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0
                call .Lmed_find_v
                beqz a0, .Lmed_vf_bad
                ld   a0, 32(a0)
                j    .Lmed_vf_ret
            .Lmed_vf_bad:
                mv   a0, s0
                call .Lmed_bad_v
            .Lmed_vf_ret:
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_media_video_size
            .type kof_media_video_size, @function
            kof_media_video_size:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0
                call .Lmed_find_v
                beqz a0, .Lmed_vsz_bad
                lw   a0, 24(a0)
                j    .Lmed_vsz_ret
            .Lmed_vsz_bad:
                mv   a0, s0
                call .Lmed_bad_v
            .Lmed_vsz_ret:
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_media_video_duration_ms
            .type kof_media_video_duration_ms, @function
            kof_media_video_duration_ms:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0
                call .Lmed_find_v
                beqz a0, .Lmed_vd_bad
                lw   a0, 48(a0)
                j    .Lmed_vd_ret
            .Lmed_vd_bad:
                mv   a0, s0
                call .Lmed_bad_v
            .Lmed_vd_ret:
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_media_video_bytes
            .type kof_media_video_bytes, @function
            kof_media_video_bytes:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)
                sd   s1, 16(sp)
                sd   s2, 8(sp)
                sd   s3, 0(sp)
                mv   s0, a0
                call .Lmed_find_v
                beqz a0, .Lmed_vb_bad
                mv   s0, a0
                ld   s1, 24(s0)
                mv   a0, s1
                li   a1, 4
                call kof_array_alloc
                mv   s2, a0
                ld   s3, 16(s0)
                addi s3, s3, 24
                li   t0, 0
            .Lmed_vb_loop:
                bge  t0, s1, .Lmed_vb_done
                add  t1, s3, t0
                lb   t2, 0(t1)
                slli t3, t0, 2
                add  t4, s2, t3
                addi t4, t4, 24
                sw   t2, 0(t4)
                addi t0, t0, 1
                j    .Lmed_vb_loop
            .Lmed_vb_done:
                mv   a0, s2
                ld   s3, 0(sp)
                ld   s2, 8(sp)
                ld   s1, 16(sp)
                ld   s0, 24(sp)
                ld   ra, 32(sp)
                addi sp, sp, 40
                ret
            .Lmed_vb_bad:
                mv   a0, s0
                call .Lmed_bad_v

            # close = remove silente (JVM map.remove sem erro em id ruim)
            .globl kof_media_video_close
            .type kof_media_video_close, @function
            kof_media_video_close:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call .Lmed_find_v
                beqz a0, .Lmed_vc_ret
                sd   zero, 0(a0)
            .Lmed_vc_ret:
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            """;
}
