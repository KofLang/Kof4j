package dev.kof.compiler.vk;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64Alloc {
    private VkChain64Alloc() {}

    // vk64_alloc_buffer(rdi=bytes, rsi=bufOut, rdx=memOut, rcx=mapOut)
    // → 0 ok / 1 falha (errbuf setado). Tradução do allocBuffer do C.
    static void source(StringBuilder sb) {
        sb.append("""
            .type vk64_alloc_buffer, @function
            vk64_alloc_buffer:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                movq %rdi, %rbx                    # bytes
                movq %rsi, %r12                    # bufOut
                movq %rdx, 16(%rsp)                # memOut (stack; r13 vira t)
                movq %rcx, %r14                    # mapOut
                // VkBufferCreateInfo @scratch+0: sType=12, pNext=0, flags=0,
                // size, usage=VK_BUFFER_USAGE_STORAGE_BUFFER_BIT(32),
                // sharingMode=VK_SHARING_MODE_EXCLUSIVE(0), qfamCount=0, p=0
                leaq vk64_scratch(%rip), %rdi
                movl $12, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movq %rbx, 24(%rdi)
                movl $32, 32(%rdi)
                movl $0, 36(%rdi)
                movl $0, 40(%rdi)
                movq $0, 48(%rdi)
                movl $0, 0(%rsp)                   # buf local (out vkCreateBuffer)
                movq g_vk64_vkCreateBuffer(%rip), %rax
                testq %rax, %rax
                jz .Lvk64_ab_bad
                movq vk64_dev(%rip), %rdi
                leaq vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ab_ck_buffer
                movq 0(%rsp), %r15
                movq %r15, (%r12)                  # *bufOut
                // VkMemoryRequirements @scratch+64: vkGetBufferMemoryRequirements
                movq g_vk64_vkGetBufferMemoryRequirements(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq %r15, %rsi
                leaq vk64_scratch+64(%rip), %rdx
                call *%rax
                // memoryTypeBits @64+16 = scratch+80
                // VkPhysicalDeviceMemoryProperties @scratch+128
                movq g_vk64_vkGetPhysicalDeviceMemoryProperties(%rip), %rax
                movq vk64_phys(%rip), %rdi
                leaq vk64_scratch+128(%rip), %rsi
                call *%rax
                // memoryTypeCount @+128; memoryTypes[32] @+132
                // (8B cada: propertyFlags @0, heapIndex @4)
                movl 128+vk64_scratch(%rip), %r15d # count
                movl $0, 8(%rsp)                   # t = 0 (stack: r13 é memOut)
            .Lvk64_ab_mtloop:
                movl 8(%rsp), %r13d
                cmpl %r15d, %r13d
                jge .Lvk64_ab_nomem
                // (1u<<t) & req.memoryTypeBits
                movl $1, %eax
                movl %r13d, %ecx
                shll %cl, %eax
                andl 80+vk64_scratch(%rip), %eax   # req.memoryTypeBits @64+16
                jz .Lvk64_ab_mtnext
                // memoryTypes[t].propertyFlags @128+4+32t
                movl %r13d, %eax
                shll $3, %eax                      # t*8 (VkMemoryType = 8B)
                cltq
                leaq 128+4+vk64_scratch(%rax), %rcx   # memoryTypes[t].propertyFlags @+4+8t
                movl (%rcx), %edx
                // HOST_VISIBLE(1) | HOST_COHERENT(4) = 5
                andl $5, %edx
                cmpl $5, %edx
                jne .Lvk64_ab_mtnext
                // achou: mi = t (r13d já contém t)
                jmp .Lvk64_ab_mtfound
            .Lvk64_ab_mtnext:
                incl 8(%rsp)
                jmp .Lvk64_ab_mtloop
            .Lvk64_ab_mtfound:
                // VkMemoryAllocateInfo @scratch+4224: sType=10, size=req.size,
                // memoryTypeIndex=mi
                leaq 4224+vk64_scratch(%rip), %rdi
                movl $5, 0(%rdi)
                movq $0, 8(%rdi)
                movq 64+vk64_scratch(%rip), %rax   # req.size @64+0
                movq %rax, 16(%rdi)
                movl %r13d, 24(%rdi)
                movl $0, 0(%rsp)                   # mem out
                movq g_vk64_vkAllocateMemory(%rip), %rax
                testq %rax, %rax
                jz .Lvk64_ab_bad
                movq vk64_dev(%rip), %rdi
                leaq 4224+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ab_ck_allocmem
                movq 0(%rsp), %r15
                movq %r15, 16(%rsp)                # *memOut (direto no slot)
                movq g_vk64_vkBindBufferMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq (%r12), %rsi
                movq %r15, %rdx
                xorl %ecx, %ecx
                call *%rax
                // (bind ok: mem = 16(%rsp))
                testl %eax, %eax
                jnz .Lvk64_ab_ck_bindmem
                movl $0, 0(%rsp)                   # map out
                movq g_vk64_vkMapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq 16(%rsp), %rsi                # mem
                xorl %edx, %edx                    # offset
                movq %rbx, %rcx                    # size = bytes
                xorl %r8d, %r8d                    # flags
                movq %rsp, %r9
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ab_ck_map
                movq 0(%rsp), %rax
                movq %rax, (%r14)                  # *mapOut
                xorl %eax, %eax                    # ok
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_ab_ck_buffer:
                leaq .Lvkv_e_buffer(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ab_ret1
            .Lvk64_ab_ck_allocmem:
                leaq .Lvkv_e_allocmem(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ab_ret1
            .Lvk64_ab_ck_bindmem:
                leaq .Lvkv_e_bindmem(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ab_ret1
            .Lvk64_ab_ck_map:
                leaq .Lvkv_e_map(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ab_ret1
            .Lvk64_ab_nomem:
                leaq .Lvkv_e_mem(%rip), %rdi
                call vk64_fail0
            .Lvk64_ab_bad:
            .Lvk64_ab_ret1:
                movl $1, %eax
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
