package dev.kof.compiler;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64Helpers {
    private VkChain64Helpers() {}

    // ───────────────────────── helpers ───────────────────────────────
    static void source(StringBuilder sb) {
        sb.append("""
            // vk64_read_file(rdi=path) → rax=ptr malloc, rdx=size, 0/0 falha
            .type vk64_read_file, @function
            vk64_read_file:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                leaq .Lvkv_rb(%rip), %rsi
                call fopen@PLT
                testq %rax, %rax
                jz .Lvk64_rf0
                movq %rax, %r12
                movq %r12, %rdi
                xorl %esi, %esi                    # offset 0
                movl $2, %edx                      # SEEK_END
                call fseek@PLT
                movq %r12, %rdi
                call ftell@PLT
                movq %rax, %r13                    # size
                movq %r12, %rdi
                xorl %esi, %esi                    # offset 0
                xorl %edx, %edx                    # SEEK_SET
                call fseek@PLT
                testq %r13, %r13
                jz .Lvk64_rf_close
                movq %r13, %rdi
                call malloc@PLT
                testq %rax, %rax
                jz .Lvk64_rf_close
                movq %rax, %rbx                    # buf
                movq %rbx, %rdi
                movq $1, %rsi
                movq %r13, %rdx
                movq %r12, %rcx
                call fread@PLT
                cmpq %r13, %rax
                jne .Lvk64_rf_free
                movq %r12, %rdi
                call fclose@PLT
                movq %rbx, %rax
                movq %r13, %rdx
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_rf_free:
                movq %rbx, %rdi
                call free@PLT
            .Lvk64_rf_close:
                movq %r12, %rdi
                call fclose@PLT
            .Lvk64_rf0:
                xorl %eax, %eax
                xorl %edx, %edx
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
