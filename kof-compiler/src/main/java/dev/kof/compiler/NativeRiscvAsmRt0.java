package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 0 de RISCV_RUNTIME_ASM — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmRt0 {

    private NativeRiscvAsmRt0() {}

    static final String RISCV_RUNTIME_ASM_0 = """
            .option arch, rv64g
            .section .text

            # kof_alloc(size) -> ptr (bump atômico em .bss — amoadd.d: main e
            # workers do spawn compartilham o heap; ldadd no aarch64 via tradutor)
            .globl kof_alloc
            kof_alloc:
                addi t1, a0, 15
                andi t1, t1, -16
                la   t0, kof_alloc_ptr
                amoadd.d a0, t1, (t0)
                ret

            # kof_memcpy(dst, src, len)
            .globl kof_memcpy
            kof_memcpy:
                li   t0, 0
            .Lkf_mcpy:
                bgeu t0, a2, .Lkf_mcpy_done
                lbu  t1, 0(a1)
                sb   t1, 0(a0)
                addi a0, a0, 1
                addi a1, a1, 1
                addi t0, t0, 1
                j    .Lkf_mcpy
            .Lkf_mcpy_done:
                ret

            # kof_string_from_literal(s, len) -> KofStr*
            .globl kof_string_from_literal
            kof_string_from_literal:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s3, 0(sp)
                mv   s0, a0
                mv   s1, a1
                addi t0, s1, 25
                addi t0, t0, 15
                andi t0, t0, -16
                mv   a0, t0
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                li   t0, 0
                sw   t0, 4(s3)
                sd   t0, 8(s3)
                sw   s1, 16(s3)
                sw   t0, 20(s3)
                addi a0, s3, 24
                mv   a1, s0
                mv   a2, s1
                call kof_memcpy
                li   t0, 0
                addi t1, s3, 24
                add  t1, t1, s1
                sb   t0, 0(t1)
                mv   a0, s3
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   s3, 0(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_print_string(str*) — write(1, data=header+24, len)
            .globl kof_print_string
            kof_print_string:
                addi sp, sp, -16
                sd   ra, 8(sp)
                bnez a0, .Lps_ok
                la   a1, .Lstr_null         # null → "null" (bytes crus, paridade x86_64)
                li   a2, 4
                j    .Lps_write
            .Lps_ok:
                addi a1, a0, 24
                lw   a2, 16(a0)
            .Lps_write:
                li   a0, 1
                li   a7, 64
                ecall
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_println_string
            kof_println_string:
                # writev(1, iov[2], 2) — string + newline num ÚNICO syscall:
                # atomico, sem interleave entre threads (spawn/await).
                addi sp, sp, -48
                sd   ra, 40(sp)
                bnez a0, .Lpls_ok
                la   t0, .Lstr_null         # null → "null" (bytes crus)
                li   t1, 4
                sd   t0, 0(sp)              # iov[0].base
                sd   t1, 8(sp)              # iov[0].len
                j    .Lpls_nl
            .Lpls_ok:
                addi t0, a0, 24
                sd   t0, 0(sp)              # iov[0].base = data
                lw   t1, 16(a0)
                sd   t1, 8(sp)              # iov[0].len
            .Lpls_nl:
                la   t0, .Lnewline
                sd   t0, 16(sp)             # iov[1].base
                li   t1, 1
                sd   t1, 24(sp)             # iov[1].len
                li   a0, 1
                addi a1, sp, 0
                li   a2, 2
                li   a7, 66
                ecall
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            # kof_int_to_string(n) -> KofStr*
            .globl kof_int_to_string
            kof_int_to_string:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s3, 16(sp)
                sd   s4, 8(sp)
                sd   s5, 0(sp)
                mv   s0, a0
                li   s4, 0
                bgez s0, .Lkits_pos
                li   s4, 1
                neg  s0, s0
            .Lkits_pos:
                mv   s5, s0
                li   s3, 0
                mv   t4, s5
            .Lkits_cnt:
                li   t1, 10
                div  t4, t4, t1
                addi s3, s3, 1
                bnez t4, .Lkits_cnt
                beqz s4, .Lkits_cnt_done
                addi s3, s3, 1
            .Lkits_cnt_done:
                addi t0, s3, 25
                addi t0, t0, 15
                andi t0, t0, -16
                mv   a0, t0
                call kof_alloc
                mv   s1, a0
                li   t0, 1
                sw   t0, 0(s1)
                li   t0, 0
                sw   t0, 4(s1)
                sd   t0, 8(s1)
                sw   s3, 16(s1)
                sw   t0, 20(s1)
                addi t0, s3, 23
                add  t1, s1, t0
                mv   t0, s5
            .Lkits_loop:
                li   t2, 10
                rem  t3, t0, t2
                addi t3, t3, 48
                sb   t3, 0(t1)
                addi t1, t1, -1
                div  t0, t0, t2
                bnez t0, .Lkits_loop
                beqz s4, .Lkits_neg_done
                li   t3, 45
                sb   t3, 0(t1)
            .Lkits_neg_done:
                li   t0, 0
                addi t1, s1, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                mv   a0, s1
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s3, 16(sp)
                ld   s4, 8(sp)
                ld   s5, 0(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_print_int(n) — escreve o inteiro via raw write
            .globl kof_print_int
            kof_print_int:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   a0, 0(sp)
                call kof_int_to_string
                call kof_print_string
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_println_int(n)
            .globl kof_println_int
            kof_println_int:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   a0, 0(sp)
                call kof_int_to_string
                call kof_println_string
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_long_to_string(n) -> KofStr*
            .globl kof_long_to_string
            kof_long_to_string:
                j kof_int_to_string

            # kof_bool_to_string(b) -> KofStr*
            .globl kof_bool_to_string
            kof_bool_to_string:
                addi sp, sp, -16
                sd   ra, 8(sp)
                bnez a0, .Lbts_true
                la   a0, .Lstr_false
                li   a1, 5
                call kof_string_from_literal
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lbts_true:
                la   a0, .Lstr_true
                li   a1, 4
                call kof_string_from_literal
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_init_object(obj, typeId, vtable)
            .globl kof_init_object
            kof_init_object:
                sw   a1, 0(a0)
                li   t0, 0
                sw   t0, 4(a0)
                sd   a2, 8(a0)
                ret

            # kof_instanceof(obj, typeId) -> 0/1 (percorre kof_super_table)
            .globl kof_instanceof
            kof_instanceof:
                beqz a0, .Lio_null
                lw   t0, 0(a0)
            .Lio_loop:
                bne  t0, a1, .Lio_search
                li   a0, 1
                ret
            .Lio_search:
                beqz t0, .Lio_null
                la   t2, kof_super_table
            .Lio_search2:
                lw   t1, 0(t2)
                beqz t1, .Lio_null
                bne  t1, t0, .Lio_next
                lw   a0, 4(t2)
                j    .Lio_loop
            .Lio_next:
                addi t2, t2, 8
                j    .Lio_search2
            .Lio_null:
                li   a0, 0
                ret

            # kof_panic(str) — imprime e exit(1)
            .globl kof_panic
            kof_panic:
                # a0 = C-string raw (.asciz), NÃO KofString — imprime via
                # strlen+write (o kof_println_string leria 16(a0) como length
                # → lixo/silêncio). Paridade com o x86_64 (kof_print raw).
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0              # base
                li   t0, 0               # len
            .Lpanic_len:
                add  t1, s0, t0
                lbu  t2, 0(t1)
                beqz t2, .Lpanic_w
                addi t0, t0, 1
                j    .Lpanic_len
            .Lpanic_w:
                li   a0, 1
                mv   a1, s0
                mv   a2, t0
                li   a7, 64
                ecall
                la   a0, .Lnewline
                li   a1, 1
                li   a7, 64
                ecall
                li   a0, 1
                li   a7, 93
                ecall

            .globl kof_null_error
            kof_null_error:
                la   a0, .Lstr_null_err
                call kof_panic

            .globl kof_bounds_error
            kof_bounds_error:
                la   a0, .Lstr_bounds_err
                call kof_panic

            # kof_throw_string(str) — desempilha a chain; sem handler → panic.
            # a0=str é preservado (só usa t-regs) até o handler.
            .globl kof_throw_string
            kof_throw_string:
                la   t0, kof_exc_chain
                ld   t0, 0(t0)
                beqz t0, .Lthrow_panic
                ld   t1, 8(t0)
                ld   t2, 16(t0)
                ld   t3, 24(t0)
                la   t4, kof_exc_chain
                sd   t3, 0(t4)
                ld   t4, 0(t0)
                beqz t4, .Lthrow_panic
                mv   sp, t1
                mv   s11, t2
                jr   t4
            .Lthrow_panic:
                call kof_panic

            # ---- arrays (header 24: typeId@0 super@4 vtable@8 len@16 elemSize@20 data@24) ----
            .globl kof_array_alloc
            kof_array_alloc:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mul  t0, s0, s1
                addi a0, t0, 24
                call kof_alloc
                mv   s2, a0
                li   t0, 2
                sw   t0, 0(s2)
                li   t0, 0
                sw   t0, 4(s2)
                sd   t0, 8(s2)
                sw   s0, 16(s2)
                sw   s1, 20(s2)
                mv   a0, s2
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   s2, 0(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_array_length
            kof_array_length:
                lw   a0, 16(a0)
                ret

            .globl kof_array_get
            kof_array_get:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                beqz s0, .Lag_null
                lw   t0, 16(s0)
                bge  s1, t0, .Lag_bounds
                blt  s1, zero, .Lag_bounds
                lw   t1, 20(s0)
                mul  t2, s1, t1
                addi t2, t2, 24
                add  t2, s0, t2
                li   t3, 8
                beq  t1, t3, .Lag_q
                li   t3, 4
                beq  t1, t3, .Lag_d
                li   t3, 2
                beq  t1, t3, .Lag_w
                lb   a0, 0(t2)
                j    .Lag_done
            .Lag_w:
                lh   a0, 0(t2)
                j    .Lag_done
            .Lag_d:
                lw   a0, 0(t2)
                j    .Lag_done
            .Lag_q:
                ld   a0, 0(t2)
            .Lag_done:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lag_null:
                call kof_null_error
            .Lag_bounds:
                call kof_bounds_error

            .globl kof_array_set
            kof_array_set:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                beqz s0, .Las_null
                lw   t0, 16(s0)
                bge  s1, t0, .Las_bounds
                blt  s1, zero, .Las_bounds
                lw   t1, 20(s0)
                mul  t2, s1, t1
                addi t2, t2, 24
                add  t2, s0, t2
                li   t3, 8
                beq  t1, t3, .Las_q
                li   t3, 4
                beq  t1, t3, .Las_d
                li   t3, 2
                beq  t1, t3, .Las_w
                sb   s2, 0(t2)
                j    .Las_done
            .Las_w:
                sh   s2, 0(t2)
                j    .Las_done
            .Las_d:
                sw   s2, 0(t2)
                j    .Las_done
            .Las_q:
                sd   s2, 0(t2)
            .Las_done:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .Las_null:
                call kof_null_error
            .Las_bounds:
                call kof_bounds_error

            # ---- strings ----
            .globl kof_string_length
            kof_string_length:
                lw   a0, 16(a0)
                ret

            # kof_string_concat(a, b) -> KofStr*
            """;
}
