package dev.kof.compiler.nat;

/**
 * #651 fatia B (lane memory-safety/paridade, 29/09): runtime do tipo nominal
 * {@code Buffer(U8)} no cross riscv64 (aarch64 herda pelo tradutor
 * linha-a-linha). Port do {@code RuntimeBuffer} x86-64 com o MESMO layout de
 * objeto — header 24 B, {@code cap@16} (qword), {@code payload@24}:
 * {@code kof_buffer_alloc} (negativo→0, payload zero-filled),
 * {@code kof_buffer_bytes} (cópia {@code Byte[]}) e {@code kof_buffer_to_string}
 * ({@code "Buffer[cap]"} / {@code "null"}), para paridade com JVM/JS/x86-64.
 */
public final class NativeRiscvAsmBuffer {

    private NativeRiscvAsmBuffer() {}

    // §257: NÃO `final` — o javac inlinaria a constante nos consumidores
    // (build incremental deixa byte velho); mesmo padrão de
    // NativeRiscvAsmProcessResult.
    static String RISCV_ASM_BUFFER = """
            .section .rodata
            .Lkof_buf_null:
                .ascii "null"
            .Lkof_buf_prefix:
                .ascii "Buffer["
            .Lkof_buf_suffix:
                .ascii "]"
            .Lkof_buf_mem020:
                .ascii "MEM020: Buffer(U8) writable borrow already held by another task"
            .section .text

            # kof_buffer_borrow_acquire(a0=Buffer?) — B-03 (D-MEM030-BORROW-RUNTIME).
            # Flag no header offset 8 (spare); segunda aquisição concorrente lança
            # MEM020 via kof_throw_string (capturável em try/catch; panic sem handler).
            .globl kof_buffer_borrow_acquire
            .type kof_buffer_borrow_acquire, @function
            kof_buffer_borrow_acquire:
                beqz a0, .Lkof_bfk_ba_done
                ld   t0, 8(a0)
                beqz t0, .Lkof_bfk_ba_free
                la   a0, .Lkof_buf_mem020
                li   a1, 63
                call kof_string_from_literal
                call kof_throw_string
            .Lkof_bfk_ba_free:
                li   t0, 1
                sd   t0, 8(a0)
            .Lkof_bfk_ba_done:
                ret

            # kof_buffer_borrow_release(a0=Buffer?) — limpa o flag (null-safe).
            .globl kof_buffer_borrow_release
            .type kof_buffer_borrow_release, @function
            kof_buffer_borrow_release:
                beqz a0, .Lkof_bfk_br_done
                sd   zero, 8(a0)
            .Lkof_bfk_br_done:
                ret

            # kof_buffer_alloc(a0=n) -> a0=Buffer*. Cap negativo/zero clampa p/ 0;
            # payload zero-filled. Layout: [0..8]=header, cap@16(qword), payload@24.
            .globl kof_buffer_alloc
            .type kof_buffer_alloc, @function
            kof_buffer_alloc:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                bgez s0, .Lkof_bfk_alloc_ok
                li   s0, 0
            .Lkof_bfk_alloc_ok:
                addi a0, s0, 24
                call kof_alloc
                mv   s1, a0
                sw   zero, 0(s1)
                sw   zero, 4(s1)
                sd   zero, 8(s1)
                sd   s0, 16(s1)
                addi t0, s1, 24
                mv   t1, s0
                beqz t1, .Lkof_bfk_alloc_done
            .Lkof_bfk_fill:
                sb   zero, 0(t0)
                addi t0, t0, 1
                addi t1, t1, -1
                bnez t1, .Lkof_bfk_fill
            .Lkof_bfk_alloc_done:
                mv   a0, s1
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                addi sp, sp, 32
                ret

            # kof_buffer_bytes(a0=Buffer?) -> a0=Byte[] copia. Nulo -> [].
            .globl kof_buffer_bytes
            .type kof_buffer_bytes, @function
            kof_buffer_bytes:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                bnez s0, .Lkof_bfk_bytes_ok
                li   a0, 0
                li   a1, 1
                call kof_array_alloc
                j    .Lkof_bfk_bytes_done
            .Lkof_bfk_bytes_ok:
                ld   s1, 16(s0)
                mv   a0, s1
                li   a1, 1
                call kof_array_alloc
                mv   s2, a0
                addi a0, s2, 24
                addi a1, s0, 24
                mv   a2, s1
                beqz a2, .Lkof_bfk_bytes_ret
                call kof_memcpy
            .Lkof_bfk_bytes_ret:
                mv   a0, s2
            .Lkof_bfk_bytes_done:
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   s2, 0(sp)
                addi sp, sp, 32
                ret

            # kof_buffer_to_string(a0=Buffer?) -> a0=KofString* ("Buffer[cap]" / "null")
            .globl kof_buffer_to_string
            .type kof_buffer_to_string, @function
            kof_buffer_to_string:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                bnez s0, .Lkof_bfk_ts_ok
                la   a0, .Lkof_buf_null
                li   a1, 4
                call kof_string_from_literal
                j    .Lkof_bfk_ts_done
            .Lkof_bfk_ts_ok:
                ld   a0, 16(s0)
                call kof_int_to_string
                mv   s1, a0
                la   a0, .Lkof_buf_prefix
                li   a1, 7
                call kof_string_from_literal
                mv   a1, s1
                call kof_string_concat
                mv   s2, a0
                la   a0, .Lkof_buf_suffix
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s2
                call kof_string_concat
            .Lkof_bfk_ts_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                addi sp, sp, 48
                ret
            """;
}
