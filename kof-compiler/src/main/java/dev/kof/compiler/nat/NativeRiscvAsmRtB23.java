package dev.kof.compiler.nat;

// FASE 3 (STDLIB S4.2 / ENC002): fatia 23 de RISCV_RUNTIME_ASM_B —
// kof.encoding base64/base64Url (RFC 4648). Fecha ENC002: até aqui os
// base64* eram gated em riscv/aarch (reusavam kof_b64_*_internal só-x86).
// Semântica travada no oracle Python + JVM/JS/x86 (KofEncodingTest/stdenc):
//  - encode: 4*((len+2)/3) chars; padding '=' std, SEM padding url;
//    alfabeto std "+/" e url "-_"; entrada = bytes UTF-8 (null => null).
//  - decode TOLERANTE (especificação única nos 3, mesma do x86
//    kof_b64_decode_internal / JVM kof_encoding_base64Decode):
//    '=' para a decodificação; inválidos são IGNORADOS; grupos de 4 => 3
//    bytes; resto 2 => 1 byte (acc>>4); resto 3 => 2 bytes (>>10, >>2);
//    resto 1 => nada. urlDecode aceita AMBOS os alfabetos (x86 pré-substitui
//    -_ => +/ antes do decode comum — aqui é flag s7 no map, mesmo mapa).
//    null => null.
// Aloc: encode 4*len+33 (>= 4*ceil(len/3) p/ todo len — teto folggado, bump
// allocator nunca observado); decode len+25 (saida <= entrada). kof_alloc
// chamado ANTES dos laços => após o call só s-regs + subs locais (call/ret
// só a label local — padrão B11 urlDecode nib; nunca div/rem no port).
// Subs: .Lv_b64_put (alfabeto com flag s7) e .Lv_b64_val (map c->0..63/-1/
// -2-break, flag s7). Estado do laço 100% em s-regs => call-safe.
//
// ⚠ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB23 {

    static final String RISCV_RUNTIME_ASM_B_23 = """

            .section .text
            # ── encode: std (s7=0, com padding) / url (s7=1, sem padding) ──
            .globl kof_encoding_base64Encode
            kof_encoding_base64Encode:
                li   a1, 0
                j    .Lv_b64_enc
            .globl kof_encoding_base64UrlEncode
            kof_encoding_base64UrlEncode:
                li   a1, 1
            .Lv_b64_enc:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s7, 24(sp)
                mv   s7, a1              # flag url
                mv   s0, a0
                beqz s0, .Lv_b64_e_orig
                lw   s1, 16(s0)          # len
                beqz s1, .Lv_b64_e_alloc0
                slli a0, s1, 2
                addi a0, a0, 33
                j    .Lv_b64_e_alloc
            .Lv_b64_e_alloc0:
                li   a0, 33
            .Lv_b64_e_alloc:
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                li   s3, 0               # i
                li   s4, 0               # pos
            .Lv_b64_e_loop:
                addi t1, s3, 3
                bgt  t1, s1, .Lv_b64_e_rem   # i+3 > len => resto
                add  t2, s0, 24
                add  t2, t2, s3
                lbu  t3, 0(t2)
                lbu  t4, 1(t2)
                lbu  t5, 2(t2)
                slli t3, t3, 16
                slli t4, t4, 8
                or   t3, t3, t4
                or   t3, t3, t5          # t3 = 24 bits
                srli a0, t3, 18
                call .Lv_b64_put
                srli a0, t3, 12
                andi a0, a0, 63
                call .Lv_b64_put
                srli a0, t3, 6
                andi a0, a0, 63
                call .Lv_b64_put
                andi a0, t3, 63
                call .Lv_b64_put
                addi s3, s3, 3
                j    .Lv_b64_e_loop
            .Lv_b64_e_rem:
                sub  t1, s1, s3          # resto 0/1/2
                beqz t1, .Lv_b64_e_term
                addi t6, t1, -1
                beqz t6, .Lv_b64_e_r1
                # resto 2: 3 chars [+ 1 '=']
                add  t2, s0, 24
                add  t2, t2, s3
                lbu  t3, 0(t2)
                lbu  t4, 1(t2)
                slli t3, t3, 8
                or   t3, t3, t4          # 16 bits
                srli a0, t3, 10
                call .Lv_b64_put
                srli a0, t3, 4
                andi a0, a0, 63
                call .Lv_b64_put
                slli a0, t3, 2
                andi a0, a0, 63
                call .Lv_b64_put
                beqz s7, .Lv_b64_e_pad1
                j    .Lv_b64_e_term
            .Lv_b64_e_pad1:
                add  t2, s2, 24
                add  t2, t2, s4
                li   t3, 61
                sb   t3, 0(t2)
                addi s4, s4, 1
                j    .Lv_b64_e_term
            .Lv_b64_e_r1:
                # resto 1: 2 chars [+ 2 '=']
                add  t2, s0, 24
                add  t2, t2, s3
                lbu  t3, 0(t2)
                slli t3, t3, 16
                srli a0, t3, 18
                call .Lv_b64_put
                srli a0, t3, 12
                andi a0, a0, 63
                call .Lv_b64_put
                beqz s7, .Lv_b64_e_pad2
                j    .Lv_b64_e_term
            .Lv_b64_e_pad2:
                add  t2, s2, 24
                add  t2, t2, s4
                li   t3, 61
                sb   t3, 0(t2)
                sb   t3, 1(t2)
                addi s4, s4, 2
            .Lv_b64_e_term:
                sw   s4, 16(s2)
                add  t0, s2, 24
                add  t0, t0, s4
                sb   zero, 0(t0)
                mv   a0, s2
                j    .Lv_b64_e_done
            .Lv_b64_e_orig:
                mv   a0, s0
            .Lv_b64_e_done:
                ld   s7, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # sub .Lv_b64_put: a0 = 0..63, flag s7 (0 std, 1 url).
            # store em s2.bytes[s4++]. Clobbers t0/t1/a1.
            .Lv_b64_put:
                li   t0, 26
                bltu a0, t0, .Lv_b64_pA
                li   t0, 52
                bltu a0, t0, .Lv_b64_pZ
                li   t0, 62
                bltu a0, t0, .Lv_b64_pz
                li   t0, 62
                beq  a0, t0, .Lv_b64_p62
                li   a1, 47              # 63 std '/'
                beqz s7, .Lv_b64_pst
                li   a1, 95              # 63 url '_'
                j    .Lv_b64_pst
            .Lv_b64_p62:
                li   a1, 43              # std '+'
                beqz s7, .Lv_b64_pst
                li   a1, 45              # url '-'
                j    .Lv_b64_pst
            .Lv_b64_pA:
                addi a1, a0, 65
                j    .Lv_b64_pst
            .Lv_b64_pZ:
                addi a1, a0, 71
                j    .Lv_b64_pst
            .Lv_b64_pz:
                addi a1, a0, -4
            .Lv_b64_pst:
                add  t1, s2, 24
                add  t1, t1, s4
                sb   a1, 0(t1)
                addi s4, s4, 1
                ret

            # ── decode tolerante: std (s7=0) / url (s7=1, aceita -_ também) ──
            .globl kof_encoding_base64Decode
            kof_encoding_base64Decode:
                li   a1, 0
                j    .Lv_b64_dec
            .globl kof_encoding_base64UrlDecode
            kof_encoding_base64UrlDecode:
                li   a1, 1
            .Lv_b64_dec:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   s6, 16(sp)
                sd   s7, 8(sp)
                mv   s7, a1
                mv   s0, a0
                beqz s0, .Lv_b64_d_null
                lw   s1, 16(s0)
                beqz s1, .Lv_b64_d_a0
                addi a0, s1, 25
                j    .Lv_b64_d_al
            .Lv_b64_d_a0:
                li   a0, 25
            .Lv_b64_d_al:
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                li   s3, 0               # i
                li   s4, 0               # out pos
                li   s5, 0               # acc
                li   s6, 0               # nch
            .Lv_b64_d_loop:
                bge  s3, s1, .Lv_b64_d_tail
                add  t2, s0, 24
                add  t2, t2, s3
                lbu  a0, 0(t2)
                addi s3, s3, 1
                call .Lv_b64_val
                li   t0, -2
                beq  a1, t0, .Lv_b64_d_tail
                li   t0, -1
                beq  a1, t0, .Lv_b64_d_loop
                slli s5, s5, 6
                or   s5, s5, a1
                addi s6, s6, 1
                li   t0, 4
                blt  s6, t0, .Lv_b64_d_loop
                srli t1, s5, 16
                add  t2, s2, 24
                add  t2, t2, s4
                sb   t1, 0(t2)
                srli t1, s5, 8
                sb   t1, 1(t2)
                sb   s5, 2(t2)
                addi s4, s4, 3
                li   s5, 0
                li   s6, 0
                j    .Lv_b64_d_loop
            .Lv_b64_d_tail:
                li   t0, 2
                blt  s6, t0, .Lv_b64_d_term
                li   t0, 3
                beq  s6, t0, .Lv_b64_d_t3
                # nch == 2 → 1 byte
                srli t1, s5, 4
                add  t2, s2, 24
                add  t2, t2, s4
                sb   t1, 0(t2)
                addi s4, s4, 1
                j    .Lv_b64_d_term
            .Lv_b64_d_t3:
                srli t1, s5, 10
                add  t2, s2, 24
                add  t2, t2, s4
                sb   t1, 0(t2)
                srli t1, s5, 2
                sb   t1, 1(t2)
                addi s4, s4, 2
            .Lv_b64_d_term:
                sw   s4, 16(s2)
                add  t0, s2, 24
                add  t0, t0, s4
                sb   zero, 0(t0)
                mv   a0, s2
                j    .Lv_b64_d_done
            .Lv_b64_d_null:
                li   a0, 0
            .Lv_b64_d_done:
                ld   s7, 8(sp)
                ld   s6, 16(sp)
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # sub .Lv_b64_val: a0=char, s7 flag → a1 = 0..63, -1 ignorar,
            # -2 break ('='). Clobbers t0.
            .Lv_b64_val:
                li   t0, 61
                beq  a0, t0, .Lv_b64_vbr
                li   t0, 65
                bltu a0, t0, .Lv_b64_vlow1
                li   t0, 91
                bltu a0, t0, .Lv_b64_vup
                li   t0, 97
                bltu a0, t0, .Lv_b64_vjunk
                li   t0, 123
                bltu a0, t0, .Lv_b64_vlow
                j    .Lv_b64_vjunk
            .Lv_b64_vup:
                addi a1, a0, -65
                ret
            .Lv_b64_vlow:
                addi a1, a0, -71
                ret
            .Lv_b64_vlow1:
                li   t0, 48
                bltu a0, t0, .Lv_b64_vsym
                li   t0, 58
                bltu a0, t0, .Lv_b64_vdig
                j    .Lv_b64_vjunk
            .Lv_b64_vdig:
                addi a1, a0, 4
                ret
            .Lv_b64_vsym:
                li   t0, 43
                beq  a0, t0, .Lv_b64_v62
                li   t0, 47
                beq  a0, t0, .Lv_b64_v63
                beqz s7, .Lv_b64_vjunk
                li   t0, 45
                beq  a0, t0, .Lv_b64_v62
                li   t0, 95
                beq  a0, t0, .Lv_b64_v63
                j    .Lv_b64_vjunk
            .Lv_b64_v62:
                li   a1, 62
                ret
            .Lv_b64_v63:
                li   a1, 63
                ret
            .Lv_b64_vbr:
                li   a1, -2
                ret
            .Lv_b64_vjunk:
                li   a1, -1
                ret
            """;
}
