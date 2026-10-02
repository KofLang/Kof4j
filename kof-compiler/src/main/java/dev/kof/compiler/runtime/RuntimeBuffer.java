package dev.kof.compiler.runtime;

/**
 * Fatia de runtime x86_64 — {@code kof.buffer} / tipo nominal {@code Buffer(U8)}.
 *
 * <p>Superfície da #651 fatia A1 (x86-64): {@code buffer.alloc(Int)},
 * {@code Buffer.bytes()} e {@code println(Buffer)} com o mesmo contrato do
 * JVM/JS — tamanho negativo vira {@code 0}, payload é zero-filled e
 * {@code bytes()} devolve uma cópia. O token FFI {@code B} binda no x86-64
 * (fatia A2); o cross riscv64/aarch64 tem o port próprio em
 * {@code NativeRiscvAsmBuffer} (fatia B, 29/09).
 */
public final class RuntimeBuffer {

    private RuntimeBuffer() {}

    public static void emit(StringBuilder sb) {
        sb.append("""

            # ── kof.buffer (D-R3-BUFFER) — superfície x86-64 (fatia A1) ────
            .text
            # kof_buffer_alloc(edi=n) -> rax = Buffer obj
            # Layout: [0..8]=header neutro, [16]=cap(Int), [24..]=payload.
            # Negative/zero clamps to 0; payload is zero-filled.
            .globl kof_buffer_alloc
            .type kof_buffer_alloc, @function
            kof_buffer_alloc:
                testl %edi, %edi
                jns .Lbfk_alloc_ok
                xorl %edi, %edi
            .Lbfk_alloc_ok:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %ebx
                leal 25(%rbx), %edi
                call kof_alloc
                movq %rax, %r12
                movl $0, 0(%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                movq %rbx, 16(%r12)
                leaq 24(%r12), %rdi
                movl %ebx, %ecx
                xorl %eax, %eax
                cld
                rep stosb
                movq %r12, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_buffer_bytes(rdi=Buffer?) -> rax = Byte[] copy
            # Null receiver yields an empty byte array (parity with JVM Buffer).
            .globl kof_buffer_bytes
            .type kof_buffer_bytes, @function
            kof_buffer_bytes:
                testq %rdi, %rdi
                jne .Lbfk_bytes_ok
                xorl %edi, %edi
                movl $1, %esi
                call kof_array_alloc
                ret
            .Lbfk_bytes_ok:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq 16(%rbx), %r12
                movl %r12d, %edi
                movl $1, %esi
                call kof_array_alloc
                movq %rax, %r13
                leaq 24(%r13), %rdi
                leaq 24(%rbx), %rsi
                movq %r12, %rdx
                testq %rdx, %rdx
                je .Lbfk_bytes_done
                call kof_memcpy
            .Lbfk_bytes_done:
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_buffer_borrow_acquire(rdi=Buffer?) — B-03 (D-MEM030-BORROW-RUNTIME).
            # Borrow flag no header offset 8 (spare, não sobrepõe o payload@24);
            # uma segunda aquisição concorrente lança MEM020 via kof_throw_string
            # (capturável em try/catch; panic sem handler — igual aos demais throws).
            .globl kof_buffer_borrow_acquire
            .type kof_buffer_borrow_acquire, @function
            kof_buffer_borrow_acquire:
                testq %rdi, %rdi
                je .Lbfk_ba_done
                cmpq $0, 8(%rdi)
                je .Lbfk_ba_free
                leaq .Lbfk_mem020(%rip), %rdi
                jmp kof_throw_string
            .Lbfk_ba_free:
                movq $1, 8(%rdi)
            .Lbfk_ba_done:
                ret

            # kof_buffer_borrow_release(rdi=Buffer?) — limpa o flag (null-safe).
            .globl kof_buffer_borrow_release
            .type kof_buffer_borrow_release, @function
            kof_buffer_borrow_release:
                testq %rdi, %rdi
                je .Lbfk_br_done
                movq $0, 8(%rdi)
            .Lbfk_br_done:
                ret

            # kof_buffer_to_string(rdi=Buffer?) -> rax = "Buffer[cap]" KofString*
            # Used by the native println valueOf dispatch, mirroring JVM/JS
            # toString rather than the raw object-pointer print path.
            .globl kof_buffer_to_string
            .type kof_buffer_to_string, @function
            kof_buffer_to_string:
                testq %rdi, %rdi
                jne .Lbfk_ts_ok
                leaq .Lbfk_null(%rip), %rax
                ret
            .Lbfk_ts_ok:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq 16(%rbx), %rdi
                call kof_int_to_string
                movq %rax, %r13
                leaq .Lbfk_prefix(%rip), %rdi
                movq %r13, %rsi
                call kof_string_concat
                movq %rax, %r12
                movq %r12, %rdi
                leaq .Lbfk_suffix(%rip), %rsi
                call kof_string_concat
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .rodata
            .balign 8
            .Lbfk_null:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 4
                .int 0
                .ascii "null"
                .byte 0
            .Lbfk_prefix:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 7
                .int 0
                .ascii "Buffer["
                .byte 0
            .Lbfk_suffix:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 1
                .int 0
                .ascii "]"
                .byte 0
            .Lbfk_mem020:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 63
                .int 0
                .ascii "MEM020: Buffer(U8) writable borrow already held by another task"
                .byte 0
            .text
            """);
    }
}
