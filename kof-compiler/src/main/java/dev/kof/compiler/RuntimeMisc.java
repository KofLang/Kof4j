package dev.kof.compiler;

/**
 * Emissão do ASM de utilidades (kof_instanceof) do runtime nativo. Domínio isolado
 * do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeMisc {

    private RuntimeMisc() {}

    static void emitInstanceof(StringBuilder sb) {
        sb.append("""
            .globl kof_instanceof
            .type kof_instanceof, @function
            kof_instanceof:
                testq %rdi, %rdi
                jz .Lkof_instanceof_null
                movl (%rdi), %eax
            .Lkof_instanceof_loop:
                cmpl %esi, %eax
                je .Lkof_instanceof_found
                testl %eax, %eax
                jz .Lkof_instanceof_null
                leaq kof_super_table(%rip), %rcx
            .Lkof_instanceof_search:
                movl (%rcx), %edx
                testl %edx, %edx
                jz .Lkof_instanceof_null
                cmpl %edx, %eax
                je .Lkof_instanceof_got_super
                addq $8, %rcx
                jmp .Lkof_instanceof_search
            .Lkof_instanceof_got_super:
                movl 4(%rcx), %eax
                jmp .Lkof_instanceof_loop
            .Lkof_instanceof_found:
                movl $1, %eax
                ret
            .Lkof_instanceof_null:
                xorl %eax, %eax
                ret
            """);
    }

}