package dev.kof.compiler;

/**
 * FFI (R3, TIER 2.1.5/2.1.6): downcall nativo via {@code dlopen}+{@code dlsym}
 * para o target Native x86-64. Kof String é objeto com os bytes C
 * (null-terminado) em offset 24. Mantido fora de {@code NativeRuntime} para
 * a regra de ≤500 linhas/classe.
 */
final class NativeFfiRuntime {

    private NativeFfiRuntime() {}

    static String source() {
        return """
            .section .text
            .globl kof_ffi_i
            .type kof_ffi_i, @function
            kof_ffi_i:
                pushq %rbx
                pushq %r12
                pushq %r13
                # rdi = Kof String lib, rsi = Kof String name, edx = int arg
                addq $24, %rdi
                addq $24, %rsi
                movq %rdi, %r12
                movq %rsi, %r13
                movl %edx, %ebx
                # dlopen(lib, RTLD_NOW=2)
                movq %r12, %rdi
                movl $2, %esi
                call dlopen@PLT
                testq %rax, %rax
                jz .Lffi_fail
                # dlsym(handle, name)
                movq %rax, %rdi
                movq %r13, %rsi
                call dlsym@PLT
                testq %rax, %rax
                jz .Lffi_fail
                # call *(fn)(int a); int em %eax
                movl %ebx, %edi
                call *%rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lffi_fail:
                movl $0, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_ffi_si
            .type kof_ffi_si, @function
            kof_ffi_si:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $8, %rsp
                addq $24, %rdi
                addq $24, %rsi
                addq $24, %rdx
                movq %rdi, %r12
                movq %rsi, %r13
                movq %rdx, %r14
                movq %r12, %rdi
                movl $2, %esi
                call dlopen@PLT
                testq %rax, %rax
                jz .Lffi_si_fail
                movq %rax, %rdi
                movq %r13, %rsi
                call dlsym@PLT
                testq %rax, %rax
                jz .Lffi_si_fail
                movq %r14, %rdi
                call *%rax
                addq $8, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lffi_si_fail:
                movl $0, %eax
                addq $8, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """;
    }
}