package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 6, lane parity, 26/09): runtime de kof.gpu no cross
// (riscv64 + aarch64). Sem libvkchain.so/Vulkan, o alvo nativo degrada
// honestamente com o MESMO contrato de JvmVkStubRuntime (Android) e dos stubs
// x86-64: available()=false e dispatch devolve o codigo de fallback (nao-zero)
// para o caller cair no golden CPU — nunca resultado errado em silencio (R6).
// Antes desta unidade o cross NAO linkava: KofGpu.supportedOn libera o nativo,
// mas o ramo riscv/aarch64 nao emitia nenhum kof_vk_*/kof_mv64_*.
public final class NativeRiscvAsmGpu {

    private NativeRiscvAsmGpu() {}

    static String RISCV_RUNTIME_ASM_GPU = """
            .section .rodata
            .Lgpu_reason: .asciz "gpu: sem Vulkan no riscv64/aarch64 (fallback CPU)"
            .section .text

            # kof.vk: sem Vulkan no cross — 13 entry points com a MESMA assinatura
            # do x86/JVM, contrato de fallback identico ao JvmVkStubRuntime.
            .globl kof_vk_available
            kof_vk_available:
                li   a0, 0
                ret

            .globl kof_vk_fail_reason
            kof_vk_fail_reason:
                addi sp, sp, -16
                sd   ra, 8(sp)
                la   a0, .Lgpu_reason
                li   a1, 49
                call kof_string_from_literal
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_vk_dispatch
            kof_vk_dispatch:
                li   a0, -1
                ret

            .globl kof_vk_dispatch64
            kof_vk_dispatch64:
                li   a0, -1
                ret

            .globl kof_mv64_set_shape
            kof_mv64_set_shape:
                li   a0, -1
                ret

            .globl kof_mv64_load_w
            kof_mv64_load_w:
                li   a0, -1
                ret

            .globl kof_mv64_matvec
            kof_mv64_matvec:
                li   a0, -1
                ret

            .globl kof_mv64_wput
            kof_mv64_wput:
                li   a0, -1
                ret

            .globl kof_mv64_wrun
            kof_mv64_wrun:
                li   a0, -1
                ret

            .globl kof_mv64_wput32
            kof_mv64_wput32:
                li   a0, -6
                ret

            .globl kof_mv64_wrun32
            kof_mv64_wrun32:
                li   a0, -6
                ret

            .globl kof_mv64_wputsp
            kof_mv64_wputsp:
                li   a0, -6
                ret

            .globl kof_mv64_wrunsp
            kof_mv64_wrunsp:
                li   a0, -6
                ret
            """;
}
