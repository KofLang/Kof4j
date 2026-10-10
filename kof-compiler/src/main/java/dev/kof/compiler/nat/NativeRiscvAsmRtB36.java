package dev.kof.compiler.nat;

/**
 * Fatia B36 — §97 cross: String.compareTo / String.hashCode / String.equals
 * no riscv64 (o x86 os tem em RuntimeStringCompare.java; o link riscv caía
 * em undefined reference a `String_compareTo`/`String_hashCode`/`String_equals`
 * — o router cross só tinha o intrínseco do `==`, não o MÉTODO). Port 1:1 do
 * algoritmo x86: helper de cursor `.Lu5_next` (a0=bytes, a1=&cursor{off@0,
 * pendLow@4}, a2=nBytes -> unit; par astral: HIGH na hora, LOW pendurado no
 * cursor p/ a próxima chamada; OOB -> -1 = sentinela de FIM (-1 nunca é unit
 * legítima); UTF-8 malformado avança 1 byte devolvendo a unit crua — progresso
 * garantido, nunca trava) + compareTo (contagem de UNITS p/ o caso prefixo —
 * byte-offset NÃO serve: "a😀b" vs "a😀bc" é -1, não -2; primeira unit dif ->
 * A−B, como o JVM) + hashCode (h=31*h+unit em Int32 — JVM) + equals (bytewise:
 * byte-igual ⟺ unit-igual em UTF-8 bem-formado — teorema da codificação;
 * espelha o kof_string_equals x86). Labels .Lu5_/prefixo .Lb6_ (namespace
 * próprio — labels fixas duplicadas = bug 95). Convenção SYSTEM_V: receiver
 * a0, arg a1, resultado a0. aarch64 herda via tradutor. PROVA:
 * NativeStringCompareCrossTest (golden = oracle JVM medido == x86_64, riscv +
 * aarch sob qemu).
 */
final class NativeRiscvAsmRtB36 {

    private NativeRiscvAsmRtB36() {}

    static  String RISCV_RUNTIME_ASM_B_36 = """
            .section .text

            # String_equals(a0=String*, a1=String*) -> a0=Bool (bytewise, como
            # o kof_string_equals x86). Mesma identidade ponteira de cara.
            .globl String_equals
            String_equals:
                beq  a1, a0, .Lb6_eq_true
                lw   t0, 16(a0)
                lw   t1, 16(a1)
                bne  t0, t1, .Lb6_eq_false
                addi t2, a0, 24
                addi t3, a1, 24
            .Lb6_eq_loop:
                beqz t1, .Lb6_eq_true
                lbu  t4, 0(t2)
                lbu  t5, 0(t3)
                bne  t4, t5, .Lb6_eq_false
                addi t2, t2, 1
                addi t3, t3, 1
                addi t1, t1, -1
                j    .Lb6_eq_loop
            .Lb6_eq_true:
                li   a0, 1
                ret
            .Lb6_eq_false:
                li   a0, 0
                ret

            # .Lu5_next(a0=ptr, a1=&cursor{off@0,pendLow@4}, a2=n) -> a0=unit|-1
            # Uma code unit UTF-16 por chamada: 1/2/3-byte -> 1 unit; 4-byte
            # astral -> HIGH (LOW pendurado em cursor+4, sai na próxima chamada
            # sem avançar o off). OOB -> -1. Malformado -> avança 1 byte,
            # devolve a unit crua (progresso garantido). Clobbers a0-a5,t0-t6.
            .Lu5_next:
                lw   t0, 4(a1)                   # pendLow
                bnez t0, .Lu5_pendlow
                lw   t1, 0(a1)                   # off
                bgeu t1, a2, .Lu5_oob
                add  t2, a0, t1
                lbu  a3, 0(t2)                   # lead
                andi t4, a3, 128
                beqz t4, .Lu5_a1
                andi t6, a3, 224
                li   t5, 192
                beq  t6, t5, .Lu5_try2
                andi t6, a3, 240
                li   t5, 224
                beq  t6, t5, .Lu5_try3
                andi t6, a3, 248
                li   t5, 240
                beq  t6, t5, .Lu5_try4
                j    .Lu5_raw
            .Lu5_pendlow:
                mv   a0, t0
                sw   zero, 4(a1)
                ret
            .Lu5_a1:
                addi t1, t1, 1
                sw   t1, 0(a1)
                mv   a0, a3
                ret
            .Lu5_raw:
                addi t1, t1, 1
                sw   t1, 0(a1)
                mv   a0, a3
                ret
            .Lu5_try2:
                addi t3, t1, 1
                bgeu t3, a2, .Lu5_raw
                add  t4, a0, t3
                lbu  t5, 0(t4)
                andi t6, t5, 192
                li   a4, 128
                bne  t6, a4, .Lu5_raw
                andi a0, a3, 31
                slli a0, a0, 6
                andi t5, t5, 63
                or   a0, a0, t5
                addi t1, t1, 2
                sw   t1, 0(a1)
                ret
            .Lu5_try3:
                addi t3, t1, 1
                bgeu t3, a2, .Lu5_raw
                add  t4, a0, t3
                lbu  a5, 0(t4)                   # b1
                andi t6, a5, 192
                li   t2, 128
                bne  t6, t2, .Lu5_raw
                addi t3, t1, 2
                bgeu t3, a2, .Lu5_raw
                add  t4, a0, t3
                lbu  a4, 0(t4)                   # b2
                andi t6, a4, 192
                bne  t6, t2, .Lu5_raw
                andi a0, a3, 15
                slli a0, a0, 12
                andi t6, a5, 63
                slli t6, t6, 6
                or   a0, a0, t6
                andi t6, a4, 63
                or   a0, a0, t6
                addi t1, t1, 3
                sw   t1, 0(a1)
                ret
            .Lu5_try4:
                addi t3, t1, 1
                bgeu t3, a2, .Lu5_raw
                add  t4, a0, t3
                lbu  a5, 0(t4)                   # b1
                andi t6, a5, 192
                li   t2, 128
                bne  t6, t2, .Lu5_raw
                addi t3, t1, 2
                bgeu t3, a2, .Lu5_raw
                add  t4, a0, t3
                lbu  a4, 0(t4)                   # b2
                andi t6, a4, 192
                bne  t6, t2, .Lu5_raw
                addi t3, t1, 3
                bgeu t3, a2, .Lu5_raw
                add  t4, a0, t3
                lbu  t2, 0(t4)                   # b3
                andi t6, t2, 192
                li   t5, 128
                bne  t6, t5, .Lu5_raw
                andi a0, a3, 7                   # cp = v
                slli a0, a0, 18
                andi t6, a5, 63
                slli t6, t6, 12
                or   a0, a0, t6
                andi t6, a4, 63
                slli t6, t6, 6
                or   a0, a0, t6
                andi t6, t2, 63
                or   a0, a0, t6
                li   t6, 65536
                sub  a0, a0, t6                  # v = cp - 0x10000
                andi t3, a0, 1023
                li   t6, 56320                   # 0xDC00
                add  t3, t3, t6                  # LOW
                srli a0, a0, 10
                li   t6, 55296                   # 0xD800
                add  a0, a0, t6                  # HIGH
                addi t1, t1, 4
                sw   t1, 0(a1)
                sw   t3, 4(a1)                   # pend = LOW
                ret
            .Lu5_oob:
                li   a0, -1
                ret

            # String_hashCode(a0=String*) -> a0=Int (h=31*h+unit, Int32 wrap —
            # JVM; port do kof_string_hash_code x86). Frame 64: cursor em
            # 0..7 (off@0, pend@4), h em 16.
            .globl String_hashCode
            String_hashCode:
                beqz a0, .Lb6_hc_zero
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                mv   s0, a0                      # str
                lw   s1, 16(s0)                  # nBytes
                sd   zero, 0(sp)
                sd   zero, 16(sp)                # h = 0
            .Lb6_hc_loop:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s1
                call .Lu5_next
                li   t0, -1
                beq  a0, t0, .Lb6_hc_done
                lw   t1, 16(sp)
                sext.w t1, t1                    # aarch: lw zero-estende hash
                                                 #  negativo; riscv no-op
                slli t2, t1, 5
                sub  t1, t2, t1                  # 31*h
                add  t1, t1, a0
                sw   t1, 16(sp)
                j    .Lb6_hc_loop
            .Lb6_hc_done:
                lw   a0, 16(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lb6_hc_zero:
                li   a0, 0
                ret

            # §114 (face hash aninhado, 24/09): kof_obj_hash(a0=obj) -> a0 =
            # hashCode de CONTEUDO. 0 se null/sem hashCode; String (type_id==1)
            # -> String_hashCode; senao kof_hashcode_table[type_id]. Fica nesta
            # peca (junto de String_hashCode) p/ nao criar aresta B0->B36 e
            # inflar o piso podavel.
            .globl kof_obj_hash
            kof_obj_hash:
                beqz a0, .Lkoh_zero
                lw   t0, 0(a0)
                li   t1, 1
                beq  t0, t1, .Lkoh_str
                la   t2, kof_hashcode_table
                slli t0, t0, 3
                add  t2, t2, t0
                ld   t3, 0(t2)
                beqz t3, .Lkoh_zero
                jr   t3
            .Lkoh_str:
                j    String_hashCode
            .Lkoh_zero:
                li   a0, 0
                ret

            # String_compareTo(a0=this, a1=other) -> a0=Int — port do
            # kof_string_compare_to x86: contagens de UNITS (passadas de
            # contagem A/B — byte-offset NÃO serve p/ o caso prefixo) e o
            # loop de diff: primeira unit diferente -> A−B; qualquer -1
            # envolvido na diferença -> unidadesA − unidadesB (x86 .Lct_diff:
            # um lado acabou = prefixo; iguais+ambos -1 = idênticas -> 0).
            # Frame 96: curA off@0 pend@4, curB off@8 pend@12, uA@16, uB@20,
            # unitA@24.
            .globl String_compareTo
            String_compareTo:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                mv   s0, a0                      # A
                mv   s1, a1                      # B
                lw   s2, 16(s0)                  # nA
                lw   s3, 16(s1)                  # nB
                sd   zero, 0(sp)                 # curA
                sd   zero, 8(sp)                 # curB
                sw   zero, 16(sp)                # uA
                sw   zero, 20(sp)                # uB
            .Lb6_ca:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s2
                call .Lu5_next
                li   t0, -1
                beq  a0, t0, .Lb6_cadone
                lw   t1, 16(sp)
                addi t1, t1, 1
                sw   t1, 16(sp)
                j    .Lb6_ca
            .Lb6_cadone:
                sd   zero, 0(sp)                 # curA de volta ao 0
            .Lb6_cb:
                addi a0, s1, 24
                mv   a1, sp
                addi a1, a1, 8
                mv   a2, s3
                call .Lu5_next
                li   t0, -1
                beq  a0, t0, .Lb6_cbdone
                lw   t1, 20(sp)
                addi t1, t1, 1
                sw   t1, 20(sp)
                j    .Lb6_cb
            .Lb6_cbdone:
                sd   zero, 0(sp)                 # curA reset (reconta p/ diff)
                sd   zero, 8(sp)                 # curB reset
            .Lb6_cd:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s2
                call .Lu5_next
                sw   a0, 24(sp)                  # unitA
                addi a0, s1, 24
                addi a1, sp, 8
                mv   a2, s3
                call .Lu5_next                   # unitB
                lw   t0, 24(sp)
                sext.w t0, t0                    # aarch: ldr w zero-estende a
                                                 # sentinela -1; riscv no-op
                beq  t0, a0, .Lb6_cd_next
                li   t1, -1
                beq  t0, t1, .Lb6_cdsame
                beq  a0, t1, .Lb6_cdsame
                sub  a0, t0, a0                  # A − B
                j    .Lb6_cdret
            .Lb6_cd_next:
                li   t1, -1
                beq  a0, t1, .Lb6_cdsame
                j    .Lb6_cd
            .Lb6_cdsame:
                lw   a0, 16(sp)
                lw   t0, 20(sp)
                sub  a0, a0, t0
            .Lb6_cdret:
                ld   s0, 80(sp)
                ld   s1, 72(sp)
                ld   s2, 64(sp)
                ld   s3, 56(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret

            # String_compareToIgnoreCase(a0=this, a1=other) -> a0=Int —
            # CASE_INSENSITIVE_ORDER do JVM: fold SIMPLES por code unit (upper;
            # se ainda difere, lower; prefixo -> unitsA-unitsB). Frame 96:
            # curA@0 curB@8 uA@16 uB@20 countA@24 countB@28.
            .globl String_compareToIgnoreCase
            String_compareToIgnoreCase:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   s3, 16(s1)
                sd   zero, 0(sp)
                sd   zero, 8(sp)
                sw   zero, 24(sp)
            .Lb6_ci_ca:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s2
                call .Lu5_next
                li   t0, -1
                beq  a0, t0, .Lb6_ci_cad
                lw   t1, 24(sp)
                addi t1, t1, 1
                sw   t1, 24(sp)
                j    .Lb6_ci_ca
            .Lb6_ci_cad:
                sw   zero, 28(sp)
            .Lb6_ci_cb:
                addi a0, s1, 24
                addi a1, sp, 8
                mv   a2, s3
                call .Lu5_next
                li   t0, -1
                beq  a0, t0, .Lb6_ci_cbd
                lw   t1, 28(sp)
                addi t1, t1, 1
                sw   t1, 28(sp)
                j    .Lb6_ci_cb
            .Lb6_ci_cbd:
                sd   zero, 0(sp)
                sd   zero, 8(sp)
            .Lb6_ci_loop:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s2
                call .Lu5_next
                sw   a0, 16(sp)
                addi a0, s1, 24
                addi a1, sp, 8
                mv   a2, s3
                call .Lu5_next
                sw   a0, 20(sp)
                lw   t0, 16(sp)
                lw   t1, 20(sp)
                bne  t0, t1, .Lb6_ci_diff
                li   t2, -1
                beq  t0, t2, .Lb6_ci_same
                j    .Lb6_ci_loop
            .Lb6_ci_diff:
                li   t2, -1
                lw   t0, 16(sp)
                beq  t0, t2, .Lb6_ci_length
                lw   t1, 20(sp)
                beq  t1, t2, .Lb6_ci_length
                lw   a0, 16(sp)
                call .Lb6_ci_fup
                sw   a0, 16(sp)
                lw   a0, 20(sp)
                call .Lb6_ci_fup
                sw   a0, 20(sp)
                lw   t0, 16(sp)
                lw   t1, 20(sp)
                beq  t0, t1, .Lb6_ci_loop
                lw   a0, 16(sp)
                call .Lb6_ci_flo
                sw   a0, 16(sp)
                lw   a0, 20(sp)
                call .Lb6_ci_flo
                sw   a0, 20(sp)
                lw   t0, 16(sp)
                lw   t1, 20(sp)
                beq  t0, t1, .Lb6_ci_loop
                sub  a0, t0, t1
                j    .Lb6_ci_ret
            .Lb6_ci_same:
            .Lb6_ci_length:
                lw   a0, 24(sp)
                lw   t0, 28(sp)
                sub  a0, a0, t0
            .Lb6_ci_ret:
                ld   s0, 80(sp)
                ld   s1, 72(sp)
                ld   s2, 64(sp)
                ld   s3, 56(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret

            # .Lb6_ci_fup(a0=unit) -> a0 = simple upper (ASCII inline; tabela BMP).
            .Lb6_ci_fup:
                li   t0, 128
                bgeu a0, t0, .Lb6_ci_fup_tab
                li   t0, 97
                bltu a0, t0, .Lb6_ci_fup_r
                li   t0, 122
                bltu t0, a0, .Lb6_ci_fup_r
                addi a0, a0, -32
            .Lb6_ci_fup_r:
                ret
            .Lb6_ci_fup_tab:
                mv   t3, a0
                la   t4, .Lkof_cu_up_tab
                li   a1, 0
                li   a2, @UPN@
                addi a2, a2, -1
            .Lb6_ci_fup_bs:
                bgt  a1, a2, .Lb6_ci_fup_nf
                add  a3, a1, a2
                srli a3, a3, 1
                slli t0, a3, 2
                add  t0, t4, t0
                lhu  t1, 0(t0)
                beq  t1, t3, .Lb6_ci_fup_f
                bltu t3, t1, .Lb6_ci_fup_hi
                addi a1, a3, 1
                j    .Lb6_ci_fup_bs
            .Lb6_ci_fup_hi:
                addi a2, a3, -1
                j    .Lb6_ci_fup_bs
            .Lb6_ci_fup_f:
                lhu  a0, 2(t0)
                ret
            .Lb6_ci_fup_nf:
                mv   a0, t3
                ret

            # .Lb6_ci_flo(a0=unit) -> a0 = simple lower.
            .Lb6_ci_flo:
                li   t0, 128
                bgeu a0, t0, .Lb6_ci_flo_tab
                li   t0, 65
                bltu a0, t0, .Lb6_ci_flo_r
                li   t0, 90
                bltu t0, a0, .Lb6_ci_flo_r
                addi a0, a0, 32
            .Lb6_ci_flo_r:
                ret
            .Lb6_ci_flo_tab:
                mv   t3, a0
                la   t4, .Lkof_cu_lo_tab
                li   a1, 0
                li   a2, @LON@
                addi a2, a2, -1
            .Lb6_ci_flo_bs:
                bgt  a1, a2, .Lb6_ci_flo_nf
                add  a3, a1, a2
                srli a3, a3, 1
                slli t0, a3, 2
                add  t0, t4, t0
                lhu  t1, 0(t0)
                beq  t1, t3, .Lb6_ci_flo_f
                bltu t3, t1, .Lb6_ci_flo_hi
                addi a1, a3, 1
                j    .Lb6_ci_flo_bs
            .Lb6_ci_flo_hi:
                addi a2, a3, -1
                j    .Lb6_ci_flo_bs
            .Lb6_ci_flo_f:
                lhu  a0, 2(t0)
                ret
            .Lb6_ci_flo_nf:
                mv   a0, t3
                ret
            """.replace("@UPN@", Integer.toString(dev.kof.compiler.runtime.RuntimeStringCase.count(true)))
                .replace("@LON@", Integer.toString(dev.kof.compiler.runtime.RuntimeStringCase.count(false)));
}
