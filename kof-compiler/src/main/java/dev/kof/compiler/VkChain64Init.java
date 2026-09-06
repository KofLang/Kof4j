package dev.kof.compiler;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64Init {
    private VkChain64Init() {}

    // ───────────────────────── init_common ───────────────────────────
    // Tradução do init_common do C. Stack frame (após prólogo):
    //   0(%rsp): n / out genérico
    //   8(%rsp): spv size
    //   16(%rsp): prio f32
    //   32(%rsp): physv[4] (32B)
    //   64(%rsp): qf[8] (VkQueueFamilyProperties = flags 4, queueCount 4,
    //     timestampValidBits 4, minImageTransferGranularity 12 → 24B; 8×24=192B)
    //   256(%rsp): sm (shader module)
    // Scratch: ver mapa no cabeçalho da classe.
    static void source(StringBuilder sb) {
        sb.append("""
            // vk64_init_common(rdi=spv_path, esi=nbinds) → 0 ok / 1 falha
            .type vk64_init_common, @function
            vk64_init_common:
                cmpl $0, vk64_inited(%rip)
                jne .Lvk64_ic_ok0
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $288, %rsp
                movl %esi, %r13d                   # nbinds
                call vk64_load
                # features: tudo 0, shaderInt64 = 1
                leaq 5904+vk64_scratch(%rip), %rdi
                movq $0, 0(%rdi)
                movq $0, 8(%rdi)
                movq $0, 16(%rdi)
                movq $0, 24(%rdi)
                movq $0, 32(%rdi)
                movq $0, 40(%rdi)
                movq $0, 48(%rdi)
                movq $0, 56(%rdi)
                movq $0, 64(%rdi)
                movq $0, 72(%rdi)
                movq $0, 80(%rdi)
                movq $0, 88(%rdi)
                movq $0, 96(%rdi)
                movq $0, 104(%rdi)
                movq $0, 112(%rdi)
                movq $0, 120(%rdi)
                movq $0, 128(%rdi)
                movq $0, 136(%rdi)
                movq $0, 144(%rdi)
                movq $0, 152(%rdi)
                movl $1, 160(%rdi)                 # shaderInt64
                testl %eax, %eax
                jnz .Lvk64_ic_fail
                // VkApplicationInfo @+4288: sType=0, pNext=0, pApplicationName
                // ="kof", appVer=0, engineVer=0, apiVersion=VK_API_VERSION_1_3
                // (4202623 = variante0 major1 minor3 patch3 → (1<<22)|(3<<12)|3)
                leaq 4288+vk64_scratch(%rip), %rdi
                movl $0, 0(%rdi)
                movq $0, 8(%rdi)
                leaq .Lvkv_appname(%rip), %rax
                movq %rax, 16(%rdi)
                movl $0, 24(%rdi)
                movl $0, 28(%rdi)
                movl $4206592, 32(%rdi)                  # VK_API_VERSION_1_3 = (1<<22)|(3<<12)
                // VkInstanceCreateInfo @+4352: sType=1, pNext=0, flags=0,
                // pApplicationInfo=@4288, 0,0,0,0
                leaq 4352+vk64_scratch(%rip), %rdi
                movl $1, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                leaq 4288+vk64_scratch(%rip), %rax
                movq %rax, 24(%rdi)
                movl $0, 32(%rdi)
                movq $0, 40(%rdi)
                movl $0, 48(%rdi)
                movq $0, 56(%rdi)
                movl $0, 0(%rsp)                   # inst out
                movq g_vk64_vkCreateInstance(%rip), %rax
                leaq 4352+vk64_scratch(%rip), %rdi # pCreateInfo
                xorl %esi, %esi                    # pAllocator
                movq %rsp, %rdx                    # pInstance
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_instance
                movq 0(%rsp), %rax
                movq %rax, vk64_inst(%rip)
                // vkEnumeratePhysicalDevices(inst, &n, 0)
                movq g_vk64_vkEnumeratePhysicalDevices(%rip), %rax
                movq vk64_inst(%rip), %rdi
                movq %rsp, %rsi
                xorl %edx, %edx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_enum0
                cmpl $0, 0(%rsp)
                jne .Lvk64_ic_havephys
                leaq .Lvkv_e_nodev(%rip), %rdi
                call vk64_fail0
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_havephys:
                // vkEnumeratePhysicalDevices(inst, &n, physv) — n ≤ 4
                movq g_vk64_vkEnumeratePhysicalDevices(%rip), %rax
                movq vk64_inst(%rip), %rdi
                movq %rsp, %rsi
                leaq 32(%rsp), %rdx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_enum
                movq 32(%rsp), %rax
                movq %rax, vk64_phys(%rip)
                // queue families: 2× vkGetPhysicalDeviceQueueFamilyProperties
                movq g_vk64_vkGetPhysicalDeviceQueueFamilyProperties(%rip), %rax
                movq vk64_phys(%rip), %rdi
                leaq 0(%rsp), %rsi                 # &n (reuso)
                xorl %edx, %edx
                call *%rax
                movq g_vk64_vkGetPhysicalDeviceQueueFamilyProperties(%rip), %rax
                movq vk64_phys(%rip), %rdi
                leaq 0(%rsp), %rsi
                leaq 64(%rsp), %rdx                # qf[8]
                call *%rax
                // achar a family com VK_QUEUE_COMPUTE_BIT (4)
                movl $0, %r14d                     # qfam
            .Lvk64_ic_qloop:
                movl 0(%rsp), %eax                 # qn
                cmpl %eax, %r14d
                jge .Lvk64_ic_noqueue
                movl %r14d, %eax
                imull $24, %eax, %eax
                cltq                               # extende p/ rax 64
                leaq 64(%rsp,%rax), %rcx
                movl (%rcx), %edx                  # queueFlags
                andl $4, %edx
                jnz .Lvk64_ic_qfound
                incl %r14d
                jmp .Lvk64_ic_qloop
            .Lvk64_ic_qfound:
                movl %r14d, vk64_qfam(%rip)
                // VkDeviceQueueCreateInfo @+4416: sType=2, flags=0,
                // queueFamilyIndex=qfam, queueCount=1, pQueuePriorities=&prio
                leaq 4416+vk64_scratch(%rip), %rdi
                movl $2, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl %r14d, 20(%rdi)
                movl $1, 24(%rdi)
                leaq 16(%rsp), %rax
                movq %rax, 32(%rdi)
                movl $0x3f800000, 16(%rsp)         # prio = 1.0f
                // VkDeviceCreateInfo @+4464: sType=3, flags=0, count=1,
                // pQueueCreateInfos=@4416, 0,0,0,0
                leaq 4464+vk64_scratch(%rip), %rdi
                movl $3, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $1, 20(%rdi)
                leaq 4416+vk64_scratch(%rip), %rax
                movq %rax, 24(%rdi)
                movl $0, 32(%rdi)
                movq $0, 40(%rdi)
                movl $0, 48(%rdi)
                movq $0, 56(%rdi)
                movq $0, 64(%rdi)
                # pEnabledFeatures @72: VkPhysicalDeviceFeatures com
                # shaderInt64 (@+160) = 1 (o matvec64.spv usa Int64)
                leaq 5904+vk64_scratch(%rip), %rax
                movq %rax, 72(%rdi)
                movl $0, 0(%rsp)                   # dev out
                movq g_vk64_vkCreateDevice(%rip), %rax
                movq vk64_phys(%rip), %rdi
                leaq 4464+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_device
                movq 0(%rsp), %rax
                movq %rax, vk64_dev(%rip)
                movq g_vk64_vkGetDeviceQueue(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movl %r14d, %esi
                xorl %edx, %edx
                leaq vk64_queue(%rip), %rcx
                call *%rax
                jmp .Lvk64_ic_spv
            .Lvk64_ic_noqueue:
                leaq .Lvkv_e_noqueue(%rip), %rdi
                call vk64_fail0
                jmp .Lvk64_ic_ret1
            """);
        VkChain64InitSpv.sourcePartB(sb);
        sb.append("""
            .Lvk64_ic_ok0:
                xorl %eax, %eax
                ret
            .Lvk64_ic_fail:
                movl $1, %eax
                addq $288, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_ic_ret1:
                movl $1, %eax
                addq $288, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
