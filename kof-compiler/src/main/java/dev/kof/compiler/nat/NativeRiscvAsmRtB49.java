package dev.kof.compiler.nat;

import dev.kof.compiler.runtime.RuntimeErasureBox;

// §284 (18/09): box de erasure real no riscv64 — port dos kof_box_*/
// kof_unbox_int/kof_box_to_string do x86 (RuntimeErasureBox), substituindo o
// no-op que deixava o primitivo cru onde o consumer esperava ponteiro (SIGSEGV
// em `var o: Object = 99` medido sob qemu). Layout 24B [magic][tag][value];
// MAGIC/tag/msg compartilhados com o x86 via RuntimeErasureBox (fonte única).
// Rótulos .Lk49_* — disciplina de namespace por peça (RiscvSlices 0 homônimos).
// Aarch64 herda via tradutor (regra 5).
public final class NativeRiscvAsmRtB49 {

    private NativeRiscvAsmRtB49() {}

    static final String RISCV_RUNTIME_ASM_B_49 = """
            # ---- §284: box de erasure (port RuntimeErasureBox x86) ----
            .globl kof_box_int
            kof_box_int:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   a0, 16(sp)
                li   a0, 24
                call kof_alloc
                sd   a0, 8(sp)
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                sd   t0, 0(a0)
                sd   zero, 8(a0)
                ld   t1, 16(sp)
                sd   t1, 16(a0)
                ld   a0, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .globl kof_box_long
            kof_box_long:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   a0, 16(sp)
                li   a0, 24
                call kof_alloc
                sd   a0, 8(sp)
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                sd   t0, 0(a0)
                li   t0, 2
                sd   t0, 8(a0)
                ld   t1, 16(sp)
                sd   t1, 16(a0)
                ld   a0, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .globl kof_box_bool
            kof_box_bool:
                addi sp, sp, -32
                sd   ra, 24(sp)
                snez t1, a0            # 1 se a0 != 0 (normaliza p/ golden JVM)
                sd   t1, 16(sp)
                li   a0, 24
                call kof_alloc
                sd   a0, 8(sp)
                la   t2, .Lk49_magic
                ld   t2, 0(t2)
                sd   t2, 0(a0)
                li   t2, 3
                sd   t2, 8(a0)
                ld   t1, 16(sp)
                sd   t1, 16(a0)
                ld   a0, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .globl kof_box_double
            kof_box_double:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   a0, 16(sp)
                li   a0, 24
                call kof_alloc
                sd   a0, 8(sp)
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                sd   t0, 0(a0)
                li   t0, 4
                sd   t0, 8(a0)
                ld   t1, 16(sp)
                sd   t1, 16(a0)
                ld   a0, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .globl kof_box_float
            kof_box_float:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   a0, 16(sp)
                li   a0, 24
                call kof_alloc
                sd   a0, 8(sp)
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                sd   t0, 0(a0)
                li   t0, 5
                sd   t0, 8(a0)
                ld   t1, 16(sp)
                sd   t1, 16(a0)
                ld   a0, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            # kof_unbox_int(a0=box) -> a0=valor. Regra JVM (medida 18/09):
            # `o as Int` sobre Long/Double/Bool/String = CCE -> tag != 0 ou
            # sem MAGIC = throw do diagnostico (String Kof estatica, nunca
            # valor inventado — R6).
            .globl kof_unbox_int
            kof_unbox_int:
                beqz a0, .Lk49_ubad                 # null -> CCE honesto
                la   t0, .Lk49_magic                # (sem isto: ld 0(a0) = SEGV)
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_ubad
                ld   t2, 8(a0)
                bnez t2, .Lk49_ubad
                ld   a0, 16(a0)
                ret
            .Lk49_ubad:
                la   a0, .Lk49_msg
                call kof_throw_string
            # §284-map: kof_unbox_long — paridade Number.longValue(): aceita
            # caixa Int (tag 0, qword com signo) e caixa Long (tag 2).
            .globl kof_unbox_long
            kof_unbox_long:
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_lbad
                ld   t2, 8(a0)
                li   t3, 2
                beq  t2, t3, .Lk49_lk
                bnez t2, .Lk49_lbad
            .Lk49_lk:
                ld   a0, 16(a0)
                ret
            .Lk49_lbad:
                la   a0, .Lk49_msg
                call kof_throw_string
            # §284-map: unbox SOFT p/ consumidores de `Int?` (arithmetic,
            # relacional, println, pos-call do get): caixa -> valor; cru ->
            # passa cru (variavel local ja desembalada); null/tag-alienigen
            # -> diagnostico igual ao estrito. Espelha os *_soft x86.
            .globl kof_unbox_int_soft
            kof_unbox_int_soft:
                beqz a0, .Lk49_usbad
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_usraw
                ld   t2, 8(a0)
                bnez t2, .Lk49_usbad
                ld   a0, 16(a0)
                ret
            .Lk49_usraw:
                mv   a0, a0
                ret
            .Lk49_usbad:
                la   a0, .Lk49_msg
                call kof_throw_string
            .globl kof_unbox_long_soft
            kof_unbox_long_soft:
                beqz a0, .Lk49_ulbad
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_ulraw
                ld   t2, 8(a0)
                li   t3, 2
                beq  t2, t3, .Lk49_ulk
                bnez t2, .Lk49_ulbad
            .Lk49_ulk:
                ld   a0, 16(a0)
                ret
            .Lk49_ulraw:
                mv   a0, a0
                ret
            .Lk49_ulbad:
                la   a0, .Lk49_msg
                call kof_throw_string
            .globl kof_unbox_bool
            kof_unbox_bool:
                beqz a0, .Lk49_ubad
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_ubad
                ld   t2, 8(a0)
                li   t3, 3
                bne  t2, t3, .Lk49_ubad
                ld   a0, 16(a0)
                ret
            .globl kof_unbox_bool_soft
            kof_unbox_bool_soft:
                beqz a0, .Lk49_usbad
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_usraw
                ld   t2, 8(a0)
                li   t3, 3
                bne  t2, t3, .Lk49_usbad
                ld   a0, 16(a0)
                ret
            .globl kof_unbox_double
            kof_unbox_double:
                beqz a0, .Lk49_ubad
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_ubad
                ld   t2, 8(a0)
                li   t3, 4
                bne  t2, t3, .Lk49_ubad
                ld   a0, 16(a0)
                ret
            .globl kof_unbox_double_soft
            kof_unbox_double_soft:
                beqz a0, .Lk49_usbad
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_usraw
                ld   t2, 8(a0)
                li   t3, 4
                bne  t2, t3, .Lk49_usbad
                ld   a0, 16(a0)
                ret
            .globl kof_unbox_float
            kof_unbox_float:
                beqz a0, .Lk49_ubad
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_ubad
                ld   t2, 8(a0)
                li   t3, 5
                bne  t2, t3, .Lk49_ubad
                ld   a0, 16(a0)
                ret
            .globl kof_unbox_float_soft
            kof_unbox_float_soft:
                beqz a0, .Lk49_usbad
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_usraw
                ld   t2, 8(a0)
                li   t3, 5
                bne  t2, t3, .Lk49_usbad
                ld   a0, 16(a0)
                ret
            # §717 (18/11): kof_unbox_any_soft(a0=valor de um `T` apagado)
            # -> a0 = representacao FISICA do slot de colecao nativo. No
            # corpo generico o valor de um `T` e SEMPRE referencia (caixa,
            # objeto ou null) — abre QUALQUER caixa pela tag-agnostica [16],
            # passa referencia crua e mapeia null -> 0. Espelha o x86
            # (RuntimeErasureBox); aarch64 herda via tradutor.
            .globl kof_unbox_any_soft
            kof_unbox_any_soft:
                beqz a0, .Lk49_uany_null
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_uany_raw
                ld   a0, 16(a0)
                ret
            .Lk49_uany_raw:
                ret
            .Lk49_uany_null:
                li   a0, 0
                ret
            # §284-map: kof_box_equals(a0=L, a1=R) -> a0 0/1. Caixa=valor,
            # null vs null = 1, null vs presente = 0.
            .globl kof_box_equals
            kof_box_equals:
                beqz a0, .Lk49_ke_lnull
                beqz a1, .Lk49_ke_zero
                j    .Lk49_ke_go
            .Lk49_ke_lnull:
                bnez a1, .Lk49_ke_zero
                li   a0, 1
                ret
            .Lk49_ke_zero:
                li   a0, 0
                ret
            .Lk49_ke_go:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                la   t0, .Lk49_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_ke_lraw
                ld   s0, 8(a0)
                ld   s1, 16(a0)
                j    .Lk49_ke_lfin
            .Lk49_ke_lraw:
                li   s0, -1
                mv   s1, a0
            .Lk49_ke_lfin:
                ld   t1, 0(a1)
                bne  t0, t1, .Lk49_ke_rraw
                ld   s2, 8(a1)
                ld   s3, 16(a1)
                j    .Lk49_ke_rfin
            .Lk49_ke_rraw:
                li   s2, -1
                mv   s3, a1
            .Lk49_ke_rfin:
                li   a0, 0
                bne  s0, s2, .Lk49_ke_end
                bne  s1, s3, .Lk49_ke_end
                li   a0, 1
            .Lk49_ke_end:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .Lk49_ke_bad:
                la   a0, .Lk49_msg
                call kof_throw_string
            # kof_box_to_string(a0=box|ptr) -> a0=String. Box -> golden JVM
            # por tag (0/2 int64, 3 bool, 4 double, 5 float); nao-box passa
            # cru (o que ja era string/objeto nao muda de mao).
            .globl kof_box_to_string
            kof_box_to_string:
                beqz a0, .Lk49_bts_null               # null -> "null" (paridade
                la   t0, .Lk49_magic                  #  JVM; sem isto ld 0(a0) = SEGV)
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .Lk49_bts_pass
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   a0, 0(sp)
                ld   t2, 8(a0)
                li   t3, 2
                beq  t2, t3, .Lk49_bts_long
                li   t3, 3
                beq  t2, t3, .Lk49_bts_bool
                li   t3, 4
                beq  t2, t3, .Lk49_bts_dbl
                li   t3, 5
                beq  t2, t3, .Lk49_bts_flt
                bnez t2, .Lk49_bts_gen
                ld   a0, 16(a0)
                call kof_int_to_string
                j    .Lk49_bts_end
            .Lk49_bts_long:
                ld   a0, 16(a0)
                call kof_long_to_string     # 64-bit cru (era int_to_string =
                j    .Lk49_bts_end          # trunc de 32 bits, face do x86 fix)
            .Lk49_bts_bool:
                ld   a0, 16(a0)
                call kof_bool_to_string
                j    .Lk49_bts_end
            .Lk49_bts_dbl:
                ld   a0, 16(a0)
                call kof_double_to_string
                j    .Lk49_bts_end
            .Lk49_bts_flt:
                ld   a0, 16(a0)
                call kof_float_to_string
                j    .Lk49_bts_end
            .Lk49_bts_gen:
                ld   a0, 0(sp)
            .Lk49_bts_end:
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lk49_bts_pass:
                # N2 (23/09): referencia nao-caixa — String (type_id==1 no
                # offset 0) passa; outro objeto despacha o toString da classe
                # por kof_tostring_table[type_id] (tail-call: a0 ja e o this).
                lw   t0, 0(a0)
                li   t1, 1
                beq  t0, t1, .Lk49_bts_pass_ret
                la   t2, kof_tostring_table
                slli t0, t0, 3
                add  t2, t2, t0
                ld   t3, 0(t2)
                beqz t3, .Lk49_bts_pass_ret
                jr   t3
            .Lk49_bts_pass_ret:
                ret
            .Lk49_bts_null:
                la   a0, .Lk49_nullstr
                ret
            # kof_char_to_string(a0=codepoint) -> a0=String UTF-8 (1-3 bytes).
            # #259: o cross só tinha o kof_int_to_string — um `Char?` PRESENTE
            # caía nele e imprimia o CODEPOINT (75 no lugar de 'K', medido sob
            # qemu nos 2 arcos). Port da lógica do RuntimeStringConv x86
            # (0..0x7F 1 byte, 0x80..0x7FF 2, 0x800..0xFFFF 3), no MESMO layout
            # de string do kof_int_to_string: len em +16, bytes em +24, nul.
            .globl kof_char_to_string
            kof_char_to_string:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                li   s1, 1
                li   t0, 128
                blt  s0, t0, .Lk49_cts_size
                li   s1, 2
                li   t0, 2048
                blt  s0, t0, .Lk49_cts_size
                li   s1, 3
            .Lk49_cts_size:
                addi t0, s1, 25
                addi t0, t0, 15
                andi t0, t0, -16
                mv   a0, t0
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                li   t0, 0
                sw   t0, 4(s2)
                sd   t0, 8(s2)
                sw   s1, 16(s2)
                sw   t0, 20(s2)
                addi s3, s2, 24
                li   t0, 128
                blt  s0, t0, .Lk49_cts_e1
                li   t0, 2048
                blt  s0, t0, .Lk49_cts_e2
                srli t0, s0, 12
                ori  t0, t0, 0xE0
                sb   t0, 0(s3)
                srli t0, s0, 6
                andi t0, t0, 0x3F
                ori  t0, t0, 0x80
                sb   t0, 1(s3)
                andi t0, s0, 0x3F
                ori  t0, t0, 0x80
                sb   t0, 2(s3)
                j    .Lk49_cts_end
            .Lk49_cts_e2:
                srli t0, s0, 6
                ori  t0, t0, 0xC0
                sb   t0, 0(s3)
                andi t0, s0, 0x3F
                ori  t0, t0, 0x80
                sb   t0, 1(s3)
                j    .Lk49_cts_end
            .Lk49_cts_e1:
                sb   s0, 0(s3)
            .Lk49_cts_end:
                li   t0, 0
                add  t1, s3, s1
                sb   t0, 0(t1)
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .section .rodata
            .p2align 3
            .Lk49_magic: .8byte @@MAGIC@@
            .p2align 3
            .Lk49_nullstr:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 4
                .int 0
                .ascii "null"
                .byte 0
            # String Kof estatica (layout [0]=typeId [16]=len [24]=bytes):
            # kof_throw_string espera objeto String, nao asciz (o catch(String
            # e) faz "+e" sobre o ponteiro).
            .align 4
            .Lk49_msg:
                .int 1
                .int 0
                .int 0
                .int 0
                .int @@MSGLEN@@
                .int 0
                .ascii "@@MSG@@"
                .byte 0
            .text
            """.replace("@@MAGIC@@", RuntimeErasureBox.MAGIC)
            .replace("@@MSG@@", RuntimeErasureBox.UNBOX_MSG)
            .replace("@@MSGLEN@@", String.valueOf(
                    RuntimeErasureBox.UNBOX_MSG.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8).length));
}
