package dev.kof.compiler.nat;

/**
 * Fatia B35 — faces de BUSCA UTF-16 de String no riscv64 (residual do bug 43
 * + residual cross do §102): kof_string_index_of / kof_string_last_index_of
 * contam e DEVOLVEM índice em CODE UNITS UTF-16 (JVM/x86 idênticos), não bytes
 * UTF-8; e as variantes _2 respeitam o índice inicial `from` com os clamps do
 * JDK 21 (§102). Port 1:1 dos refs x86 (RuntimeStringSearch.emitStringIndexOf/
 * LastIndexOf; RuntimeStringSearchFrom.emitStringIndexOf2/LastIndexOf2),
 * reusando o MESMO walk de code units da B34 (.Lu9_walk: a0=str, a1=target →
 * a0=byteOff, t3=units, a2=cut-no-meio-do-par). Substitui os corpos byte-based
 * (removidos de Rt1/Strn0 — sem duplicatas no `.s`). aarch64 herda via
 * tradutor. contains/startsWith ficam byte-based de propósito: retornam bool,
 * e substring de bytes bem-formado casa igual ao de code units (só o VALOR de
 * índice divergia). PROVA: NativeStringUtf16CrossTest.searchUtf16Cross
 * (golden = oracle JVM medido, riscv+aarch sob qemu).
 */
final class NativeRiscvAsmRtB35 {

    private NativeRiscvAsmRtB35() {}

    static final String RISCV_RUNTIME_ASM_B_35 = """
            .section .text

            # kof_string_index_of(str@a0, needle@a1) -> Int (code units UTF-16)
            # needle vazia -> 0; needle > alvo -> -1; corte de par pulado
            # (needle well-formed nunca casa na 2ª unit — bug 43).
            .globl kof_string_index_of
            kof_string_index_of:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0                    # haystack
                mv   s1, a1                    # needle
                mv   a0, s0
                li   a1, 2147483647
                call .Lu9_walk
                mv   s2, t3                    # totalH (units)
                mv   a0, s1
                li   a1, 2147483647
                call .Lu9_walk
                mv   s3, t3                    # totalN (units)
                mv   s4, a0                    # lenBytes da needle
                beqz s3, .Lb5_ifound0
                bgt  s3, s2, .Lb5_inotfound
                li   s5, 0                     # i = 0 (unit)
            .Lb5_scan:
                mv   t0, s2
                sub  t0, t0, s3                # totalH - totalN
                bgt  s5, t0, .Lb5_inotfound
                mv   a0, s0
                mv   a1, s5
                call .Lu9_walk                 # a0=byteOff, a2=cut
                bnez a2, .Lb5_inext
                mv   t1, a0                    # byteOff
                li   t2, 0                     # j (byte)
            .Lb5_cmp:
                bge  t2, s4, .Lb5_ifound
                addi t3, s0, 24
                add  t3, t3, t1
                add  t3, t3, t2
                lbu  t3, 0(t3)
                addi t4, s1, 24
                add  t4, t4, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Lb5_inext
                addi t2, t2, 1
                j    .Lb5_cmp
            .Lb5_inext:
                addi s5, s5, 1
                j    .Lb5_scan
            .Lb5_ifound0:
                li   s5, 0
            .Lb5_ifound:
                mv   a0, s5
                j    .Lb5_iret
            .Lb5_inotfound:
                li   a0, -1
            .Lb5_iret:
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_string_last_index_of(str@a0, needle@a1) -> Int (units UTF-16)
            # needle vazia -> totalH; varre do fim; corte de par pulado.
            .globl kof_string_last_index_of
            kof_string_last_index_of:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   a0, s0
                li   a1, 2147483647
                call .Lu9_walk
                mv   s2, t3                    # totalH
                mv   a0, s1
                li   a1, 2147483647
                call .Lu9_walk
                mv   s3, t3                    # totalN
                mv   s4, a0                    # lenBytes
                beqz s3, .Lb5_lend
                bgt  s3, s2, .Lb5_lnotfound
                mv   s5, s2
                sub  s5, s5, s3                # i = totalH - totalN
            .Lb5_lscan:
                bltz s5, .Lb5_lnotfound
                mv   a0, s0
                mv   a1, s5
                call .Lu9_walk
                bnez a2, .Lb5_lnext
                mv   t1, a0
                li   t2, 0
            .Lb5_lcmp:
                bge  t2, s4, .Lb5_lfound
                addi t3, s0, 24
                add  t3, t3, t1
                add  t3, t3, t2
                lbu  t3, 0(t3)
                addi t4, s1, 24
                add  t4, t4, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Lb5_lnext
                addi t2, t2, 1
                j    .Lb5_lcmp
            .Lb5_lnext:
                addi s5, s5, -1
                j    .Lb5_lscan
            .Lb5_lfound:
                mv   a0, s5
                j    .Lb5_lret
            .Lb5_lend:
                mv   a0, s2
                j    .Lb5_lret
            .Lb5_lnotfound:
                li   a0, -1
            .Lb5_lret:
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_string_index_of2(str@a0, needle@a1, from@a2) -> Int (§102).
            # from<0 -> 0; start=min(from,totalH); needle vazia -> start.
            .globl kof_string_index_of2
            kof_string_index_of2:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s6, a2                    # from (crú)
                bltz s6, .Lb5_i2z
                j    .Lb5_i2from
            .Lb5_i2z:
                li   s6, 0
            .Lb5_i2from:
                mv   a0, s0
                li   a1, 2147483647
                call .Lu9_walk
                mv   s2, t3                    # totalH
                ble  s6, s2, .Lb5_i2clamped
                mv   s6, s2                    # start = min(from, totalH)
            .Lb5_i2clamped:
                mv   a0, s1
                li   a1, 2147483647
                call .Lu9_walk
                mv   s3, t3                    # totalN
                mv   s4, a0                    # lenBytes
                beqz s3, .Lb5_i2empty
                bgt  s3, s2, .Lb5_i2notfound
                mv   s5, s6                    # i = start
            .Lb5_i2scan:
                mv   t0, s2
                sub  t0, t0, s3
                bgt  s5, t0, .Lb5_i2notfound
                mv   a0, s0
                mv   a1, s5
                call .Lu9_walk
                bnez a2, .Lb5_i2next
                mv   t1, a0
                li   t2, 0
            .Lb5_i2cmp:
                bge  t2, s4, .Lb5_i2found
                addi t3, s0, 24
                add  t3, t3, t1
                add  t3, t3, t2
                lbu  t3, 0(t3)
                addi t4, s1, 24
                add  t4, t4, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Lb5_i2next
                addi t2, t2, 1
                j    .Lb5_i2cmp
            .Lb5_i2next:
                addi s5, s5, 1
                j    .Lb5_i2scan
            .Lb5_i2empty:
                mv   a0, s6
                j    .Lb5_i2ret
            .Lb5_i2found:
                mv   a0, s5
                j    .Lb5_i2ret
            .Lb5_i2notfound:
                li   a0, -1
            .Lb5_i2ret:
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                ld   s6, 0(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_string_last_index_of2(str@a0, needle@a1, from@a2) -> Int (§102).
            # from<0 -> -1; from=min(from,totalH); needle vazia -> from;
            # i=min(from, totalH-totalN); varre do i p/ baixo.
            .globl kof_string_last_index_of2
            kof_string_last_index_of2:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s6, a2                    # from (crú)
                bltz s6, .Lb5_l2notfound       # from<0 -> -1 (JDK)
                mv   a0, s0
                li   a1, 2147483647
                call .Lu9_walk
                mv   s2, t3                    # totalH
                ble  s6, s2, .Lb5_l2fromclamp
                mv   s6, s2                    # from = min(from, totalH)
            .Lb5_l2fromclamp:
                mv   a0, s1
                li   a1, 2147483647
                call .Lu9_walk
                mv   s3, t3                    # totalN
                mv   s4, a0                    # lenBytes
                beqz s3, .Lb5_l2empty          # vazia -> from (<=totalH)
                bgt  s3, s2, .Lb5_l2notfound
                mv   t0, s2
                sub  t0, t0, s3                # totalH - totalN
                ble  s6, t0, .Lb5_l2iset
                mv   s6, t0                    # i = min(from, totalH-totalN)
            .Lb5_l2iset:
                mv   s5, s6
            .Lb5_l2start:
                bltz s5, .Lb5_l2notfound
                mv   a0, s0
                mv   a1, s5
                call .Lu9_walk
                bnez a2, .Lb5_l2next
                mv   t1, a0
                li   t2, 0
            .Lb5_l2cmp:
                bge  t2, s4, .Lb5_l2found
                addi t3, s0, 24
                add  t3, t3, t1
                add  t3, t3, t2
                lbu  t3, 0(t3)
                addi t4, s1, 24
                add  t4, t4, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Lb5_l2next
                addi t2, t2, 1
                j    .Lb5_l2cmp
            .Lb5_l2next:
                addi s5, s5, -1
                j    .Lb5_l2start
            .Lb5_l2found:
                mv   a0, s5
                j    .Lb5_l2ret
            .Lb5_l2empty:
                mv   a0, s6
                j    .Lb5_l2ret
            .Lb5_l2notfound:
                li   a0, -1
            .Lb5_l2ret:
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                ld   s6, 0(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_string_starts_with2(str@a0, needle@a1, from@a2) -> Bool (§102).
            # from<0 -> false; from>totalH -> false; needle vazia -> true;
            # corte de par astral -> false; resto = comparação de BYTES a
            # partir do byteOffset (needle bem-formada nunca casa cortando
            # par — bug 43). starts_with de 1 arg fica byte-based (bool de
            # prefixo em UTF-8 bem-formado == bool de prefixo em units).
            .globl kof_string_starts_with2
            kof_string_starts_with2:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s6, a2                    # from (crú)
                bltz s6, .Lb5_sfalse
                mv   a0, s0
                li   a1, 2147483647
                call .Lu9_walk
                mv   s2, t3                    # totalH
                bgt  s6, s2, .Lb5_sfalse       # from > totalH
                mv   a0, s1
                li   a1, 2147483647
                call .Lu9_walk
                mv   s3, t3                    # totalN
                mv   s4, a0                    # lenBytes da needle
                beqz s3, .Lb5_strue            # vazia -> true (JDK)
                mv   a0, s0
                mv   a1, s6
                call .Lu9_walk                 # byteOffset do 'from'
                bnez a2, .Lb5_sfalse           # corte de par astral
                mv   s5, a0                    # byteOff
                lw   t0, 16(s0)                # byteLen str
                add  t1, s5, s4
                bltu t0, t1, .Lb5_sfalse       # não cabe
                li   t2, 0
            .Lb5_scmp:
                bge  t2, s4, .Lb5_strue
                addi t3, s0, 24
                add  t3, t3, s5
                add  t3, t3, t2
                lbu  t3, 0(t3)
                addi t4, s1, 24
                add  t4, t4, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Lb5_sfalse
                addi t2, t2, 1
                j    .Lb5_scmp
            .Lb5_strue:
                li   a0, 1
                j    .Lb5_sret
            .Lb5_sfalse:
                li   a0, 0
            .Lb5_sret:
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                ld   s6, 0(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            """;
}
