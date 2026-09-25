package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 9, lane parity, 26/09): fatia 3b do kof.config no
// cross — leitura de arquivo (formato chave=valor, '#'-comentário, trim) e a
// cadeia de lookup crua: KOF_CONFIG explícito → env KOF_<KEY> → perfil
// kof.<KOF_PROFILE>.config | kof.config. Porta RuntimeConfig1 (x86) fiel.
// KofStr: len@16, bytes@24; kof_env_getc/.Lcfg1_envname vêm da fatia 3a.
public final class NativeRiscvAsmConfig2 {

    private NativeRiscvAsmConfig2() {}

    static String RISCV_RUNTIME_ASM_CONFIG_2 = """
            .section .rodata
            .Lcfg2_s_kofconfig:  .asciz "KOF_CONFIG"
            .Lcfg2_s_kofprofile: .asciz "KOF_PROFILE"
            .Lcfg2_s_default:    .asciz "kof.config"
            .section .text

            # .Lcfg2_file_find(a0=path C-string, a1=key KofString*) -> KofString*|0
            .Lcfg2_file_find:
                li   t0, 16448
                sub  sp, sp, t0
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                sd   s5, 48(sp)
                mv   s0, a1                  # key
                mv   s1, a0                  # path
                li   a0, -100
                mv   a1, s1
                li   a2, 0
                li   a3, 0
                li   a7, 56
                ecall
                bltz a0, .Lcfg2_ff_fail
                mv   s1, a0                  # fd
                mv   a0, s1
                addi a1, sp, 64
                li   a2, 16384
                li   a7, 63
                ecall
                mv   s2, a0                  # n
                mv   a0, s1
                li   a7, 57
                ecall
                blez s2, .Lcfg2_ff_fail
                addi s1, sp, 64              # buf base
                add  s2, s1, s2              # end = base + n
                lw   s5, 16(s0)              # key len
                addi s4, s0, 24              # key data
                mv   s3, s1                  # line cursor
            .Lcfg2_ff_line:
                bgeu s3, s2, .Lcfg2_ff_fail
                mv   t0, s3                  # find eol
            .Lcfg2_ff_eol:
                bgeu t0, s2, .Lcfg2_ff_haveeol
                lbu  t1, 0(t0)
                li   t2, 10
                beq  t1, t2, .Lcfg2_ff_haveeol
                addi t0, t0, 1
                j    .Lcfg2_ff_eol
            .Lcfg2_ff_haveeol:
                mv   t3, s3                  # left trim
            .Lcfg2_ff_tls:
                bgeu t3, t0, .Lcfg2_ff_blank
                lbu  t1, 0(t3)
                li   t2, 32
                beq  t1, t2, .Lcfg2_ff_tls1
                li   t2, 9
                bne  t1, t2, .Lcfg2_ff_tle
            .Lcfg2_ff_tls1:
                addi t3, t3, 1
                j    .Lcfg2_ff_tls
            .Lcfg2_ff_tle:
                mv   t4, t0                  # right trim (exclusive)
            .Lcfg2_ff_tle_loop:
                bgeu t3, t4, .Lcfg2_ff_blank
                addi t5, t4, -1
                lbu  t1, 0(t5)
                li   t2, 32
                beq  t1, t2, .Lcfg2_ff_tle1
                li   t2, 9
                beq  t1, t2, .Lcfg2_ff_tle1
                li   t2, 13
                beq  t1, t2, .Lcfg2_ff_tle1
                j    .Lcfg2_ff_hash
            .Lcfg2_ff_tle1:
                addi t4, t4, -1
                j    .Lcfg2_ff_tle_loop
            .Lcfg2_ff_blank:
                addi s3, t0, 1
                j    .Lcfg2_ff_line
            .Lcfg2_ff_hash:
                lbu  t1, 0(t3)
                li   t2, 35
                beq  t1, t2, .Lcfg2_ff_blank
                mv   t6, t3                  # scan '='
            .Lcfg2_ff_eqscan:
                bgeu t6, t4, .Lcfg2_ff_blank
                lbu  t1, 0(t6)
                li   t2, 61
                beq  t1, t2, .Lcfg2_ff_eqfound
                addi t6, t6, 1
                j    .Lcfg2_ff_eqscan
            .Lcfg2_ff_eqfound:
                sd   t6, 56(sp)              # guarda o '='
            .Lcfg2_ff_keyt:
                bgeu t3, t6, .Lcfg2_ff_blank
                addi t1, t6, -1
                lbu  t2, 0(t1)
                li   t5, 32
                beq  t2, t5, .Lcfg2_ff_keyt1
                li   t5, 9
                bne  t2, t5, .Lcfg2_ff_keycmp
            .Lcfg2_ff_keyt1:
                addi t6, t6, -1
                j    .Lcfg2_ff_keyt
            .Lcfg2_ff_keycmp:
                sub  t1, t6, t3
                bne  t1, s5, .Lcfg2_ff_valskip
                li   t2, 0
            .Lcfg2_ff_cmpline:
                bge  t2, s5, .Lcfg2_ff_matched
                add  t5, t3, t2
                lbu  t5, 0(t5)
                add  a2, s4, t2
                lbu  a2, 0(a2)
                bne  t5, a2, .Lcfg2_ff_valskip
                addi t2, t2, 1
                j    .Lcfg2_ff_cmpline
            .Lcfg2_ff_matched:
                ld   t5, 56(sp)              # '='
                addi t5, t5, 1               # value start
            .Lcfg2_ff_vtls:
                bgeu t5, t4, .Lcfg2_ff_vmk
                lbu  t1, 0(t5)
                li   t2, 32
                beq  t1, t2, .Lcfg2_ff_vtls1
                li   t2, 9
                bne  t1, t2, .Lcfg2_ff_vmk
            .Lcfg2_ff_vtls1:
                addi t5, t5, 1
                j    .Lcfg2_ff_vtls
            .Lcfg2_ff_vmk:
                sub  a1, t4, t5              # vallen
                mv   a0, t5                  # value ptr
                call kof_string_from_literal
                j    .Lcfg2_ff_exit
            .Lcfg2_ff_valskip:
                addi s3, t0, 1
                j    .Lcfg2_ff_line
            .Lcfg2_ff_fail:
                li   a0, 0
            .Lcfg2_ff_exit:
                ld   s5, 48(sp)
                ld   s4, 40(sp)
                ld   s3, 32(sp)
                ld   s2, 24(sp)
                ld   s1, 16(sp)
                ld   s0, 8(sp)
                ld   ra, 0(sp)
                li   t0, 16448
                add  sp, sp, t0
                ret

            # kof_config_lookup_raw(a0=key KofString*) -> KofString*|0
            .globl kof_config_lookup_raw
            .type kof_config_lookup_raw, @function
            kof_config_lookup_raw:
                addi sp, sp, -640
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                mv   s0, a0
                la   a0, .Lcfg2_s_kofconfig
                call kof_env_getc
                beqz a0, .Lcfg2_envkey
                addi a0, a0, 24
                mv   a1, s0
                call .Lcfg2_file_find
                bnez a0, .Lcfg2_exit
            .Lcfg2_envkey:
                mv   a0, s0
                addi a1, sp, 64
                call .Lcfg1_envname
                addi a0, sp, 64
                call kof_env_getc
                bnez a0, .Lcfg2_exit
                la   a0, .Lcfg2_s_kofprofile
                call kof_env_getc
                beqz a0, .Lcfg2_defaultfile
                mv   s1, a0
                li   t0, 107
                sb   t0, 64(sp)
                li   t0, 111
                sb   t0, 65(sp)
                li   t0, 102
                sb   t0, 66(sp)
                li   t0, 46
                sb   t0, 67(sp)
                lw   t0, 16(s1)
                addi t1, s1, 24
                li   t2, 0
            .Lcfg2_pcopy:
                bge  t2, t0, .Lcfg2_pdone
                add  t3, t1, t2
                lbu  t4, 0(t3)
                add  t5, sp, t2
                addi t5, t5, 68
                sb   t4, 0(t5)
                addi t2, t2, 1
                j    .Lcfg2_pcopy
            .Lcfg2_pdone:
                add  t5, sp, t0
                addi t5, t5, 68
                li   t4, 46
                sb   t4, 0(t5)
                li   t4, 99
                sb   t4, 1(t5)
                li   t4, 111
                sb   t4, 2(t5)
                li   t4, 110
                sb   t4, 3(t5)
                li   t4, 102
                sb   t4, 4(t5)
                li   t4, 105
                sb   t4, 5(t5)
                li   t4, 103
                sb   t4, 6(t5)
                sb   zero, 7(t5)
                addi a0, sp, 64
                mv   a1, s0
                call .Lcfg2_file_find
                j    .Lcfg2_exit
            .Lcfg2_defaultfile:
                la   a0, .Lcfg2_s_default
                mv   a1, s0
                call .Lcfg2_file_find
            .Lcfg2_exit:
                ld   s1, 16(sp)
                ld   s0, 8(sp)
                ld   ra, 0(sp)
                addi sp, sp, 640
                ret
            """;
}
