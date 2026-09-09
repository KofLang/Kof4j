package dev.kof.compiler.nat;

// FASE 3 (STDLIB S3b / SECN000): fatia 25 de RISCV_RUNTIME_ASM_B — kof.uuid
// v4. Fecha SECN000: entropia por getrandom(2) via ecall — syscall 278
// CONFIRMADO por probe em qemu-riscv64 E qemu-aarch64 (rc=16, genérico
// asm-generic nos dois). R11 honesto: sem cripto caseira, só a primitiva do
// SO; fallback null se getrandom falhar (mesmo contrato do x86
// kof_sec_random_hex: rax=0 na falha — nunca saída fraca silenciosa).
//
// kof_uuid_v4() -> String 8-4-4-4-12 minúsculo (36 chars + NUL):
//   buf[16] na pilha (sp+0..15); getrandom(buf,16,0); se rc!=16 => null.
//   version: b[6] = (b[6]&0x0f)|0x40  => char[14]='4'
//   variant: b[8] = (b[8]&0x3f)|0x80  => char[19] ∈ {8,9,a,b}
//            (MÁSCARA — paridade com JVM/JS; o x86 RuntimeUuid força '8',
//             um subset do RFC — seguir o JVM/JS aqui, não o subset x86).
//   hex minúsculo (mesma tabela do x86 .Lsec_hex_chars / JVM H / JS).
// Frame -80: buf sp+0..15; s-regs salvos 24..72; ra 72. Call kof_alloc =>
// 16-align (80 = 5*16). Pós-alloc só s-regs (buf em sp, imutável).
// Subs .Lv_uuid_hx (a0=nibble→a0=char, t-only, call-safe p/ estado em s-regs).
//
// ⚠ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB25 {

    static final String RISCV_RUNTIME_ASM_B_25 = """

            .section .text
            .globl kof_uuid_v4
            kof_uuid_v4:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                # --- getrandom(sp+0, 16, 0) ---
                addi a0, sp, 0
                li   a1, 16
                li   a2, 0
                li   a7, 278
                ecall
                li   t1, 16
                bne  a0, t1, .Lv_uuid_null   # rc != 16 (falha OU parcial) =>
                                             # null — NUNCA uuid fraco (R11)
                # --- version nibble: b[6]=(b[6]&0x0f)|0x40 ---
                lbu  t0, 6(sp)
                andi t0, t0, 15
                ori  t0, t0, 64
                sb   t0, 6(sp)
                # --- variant nibble: b[8]=(b[8]&0x3f)|0x80 ---
                lbu  t0, 8(sp)
                andi t0, t0, 63
                ori  t0, t0, 128
                sb   t0, 8(sp)
                # --- alloc String 36: 36+25=61 -> round 16 -> 64 ---
                li   a0, 61
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s0, a0              # novo
                li   t0, 1
                sw   t0, 0(s0)
                sw   zero, 4(s0)
                sd   zero, 8(s0)
                li   t0, 36
                sw   t0, 16(s0)
                sw   zero, 20(s0)
                li   s1, 0               # i (byte 0..15)
                li   s2, 0               # out pos (0..35)
            .Lv_uuid_loop:
                li   t1, 16
                bge  s1, t1, .Lv_uuid_term
                add  t2, sp, s1
                lbu  t3, 0(t2)           # byte i
                # high nibble
                srli a0, t3, 4
                call .Lv_uuid_hx
                add  t4, s0, s2
                addi t4, t4, 24
                sb   a0, 0(t4)
                addi s2, s2, 1
                # low nibble
                andi a0, t3, 15
                call .Lv_uuid_hx
                add  t4, s0, s2
                addi t4, t4, 24
                sb   a0, 0(t4)
                addi s2, s2, 1
                # dash após bytes 3,5,7,9
                li   t1, 3
                beq  s1, t1, .Lv_uuid_dash
                li   t1, 5
                beq  s1, t1, .Lv_uuid_dash
                li   t1, 7
                beq  s1, t1, .Lv_uuid_dash
                li   t1, 9
                beq  s1, t1, .Lv_uuid_dash
                j    .Lv_uuid_next
            .Lv_uuid_dash:
                add  t4, s0, s2
                addi t4, t4, 24
                li   t3, 45
                sb   t3, 0(t4)
                addi s2, s2, 1
            .Lv_uuid_next:
                addi s1, s1, 1
                j    .Lv_uuid_loop
            .Lv_uuid_term:
                add  t0, s0, s2
                addi t0, t0, 24
                sb   zero, 0(t0)
                mv   a0, s0
                j    .Lv_uuid_done
            .Lv_uuid_null:
                li   a0, 0
            .Lv_uuid_done:
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # sub .Lv_uuid_hx: a0 = nibble 0..15 -> a0 char hex minúsculo.
            # t-only clobber (d0/estado em s-regs — call-safe).
            .Lv_uuid_hx:
                li   t0, 10
                bltu a0, t0, .Lv_uuid_h9
                addi a0, a0, 87          # 'a'-10 = 87
                ret
            .Lv_uuid_h9:
                addi a0, a0, 48          # '0'
                ret
            """;
}
