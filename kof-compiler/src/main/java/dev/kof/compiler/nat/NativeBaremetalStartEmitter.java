package dev.kof.compiler.nat;

import dev.kof.compiler.IRClass;

/**
 * B-2/B-3 (PLAN-BAREMETAL-BOOT) + o split do gate-500 (16/11, o §7 do
 * KOF_ASAN): os entries BAREMETAL (UEFI PE32+, BIOS payload) extraídos do
 * {@link NativeMethodEmitter} (606→551; a mesma disciplina do
 * {@link NativeBiosBootEmitter}). O perfil HOST (o _start cru + a face
 * KOF_ASAN) fica no emitter principal — os entries aqui NUNCA tocam o modo
 * sanitizado (o ASan é uma face do perfil host).
 */
final class NativeBaremetalStartEmitter {

    private NativeBaremetalStartEmitter() {}

    /**
     * B-2 (PLAN-BAREMETAL-BOOT): entry UEFI x86_64 — PE32+ com
     * {@code objcopy --target=pei-x86-64} (NativeAssembler). UEFI x86_64 é
     * MS x64: {@code RCX=ImageHandle, RDX=SystemTable} (medição B-2: chamar
     * OutputString com This/String em RCX/RDX + rsp%16==0 na chamada). O
     * fim é {@code BootServices->Exit} via a costura ({@code RuntimeUefi}) —
     * nunca retorna, análogo ao {@code exit_group} do perfil host.
     */
    static void emitStartUefi(NativeBackend nb, StringBuilder sb, IRClass clazz) {
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        boolean mainHasArgs = clazz.methods().stream()
                .filter(m -> "main".equals(m.name()))
                .anyMatch(m -> !m.parameterTypes().isEmpty());
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        // MS x64: RCX=ImageHandle, RDX=SystemTable -> globals (RuntimeUefi).
        sb.append("    call kof_efi_save_args\n");
        // G-6b: fundo da pilha do main ANTES do alinhamento (mesmo contrato do host).
        sb.append("    movq %rsp, kof_main_stack_bottom(%rip)\n");
        sb.append("    andq $-16, %rsp\n");       // o firmware entra MS: rsp%16==8
        sb.append("    call kof_plat_thread_id\n");
        sb.append("    movq %rax, kof_main_tid(%rip)\n");
        if (nb.rings) sb.append("    call kof_rings_init\n    call kof_rings_selftest\n    call kof_ring1_selftest\n    call kof_ring1_gp_selftest\n    call kof_ring1_sabotage_selftest\n    call kof_rings_restore\n"); // B-6.1/B-6.2a/B-6.3
        if (mainHasArgs) {
            // N3: array vazio — mesmo contrato do _start host.
            sb.append("    xorl %edi, %edi\n");
            sb.append("    movl $8, %esi\n");
            sb.append("    call kof_array_alloc\n");
            sb.append("    movq %rax, %rdi\n");
        }
        NativeMethodEmitter.emitClinitCalls(nb, sb);
        sb.append("    call ").append(nb.sanitizeName(clazz.name())).append("_main\n");
        // Saída pela costura: no UEFI o corpo DEVOLVE o status em RAX
        // (retorno ao StartImage) — o _start termina com ret, nunca cai fora.
        sb.append("    xorl %edi, %edi\n");
        sb.append("    call kof_plat_exit_group\n");
        sb.append("    ret\n");
    }

    /**
     * B-3 (PLAN-BAREMETAL-BOOT): entry LEGACY BIOS. A emissão do setor de
     * boot (modo real 16-bit, carga do payload e long mode) vive em
     * {@link NativeBiosBootEmitter} (gate <=500 linhas); aqui só delegamos.
     * B-3b-3: o entry do PROGRAMA Kof é emitido como {@code kof_payload_entry}
     * (mesmo corpo do _start freestanding; rótulo distinto evita colisão com
     * o {@code _start} do setor de boot) — o boot salta para ele após copiar
     * o staging para a base 0x100000, tornando o programa REACHABLE (o
     * --gc-sections não poda mais o runtime).
     */
    static void emitStartBios(NativeBackend nb, StringBuilder sb, IRClass clazz) {
        NativeBiosBootEmitter.emit(sb, clazz);
        emitPayloadEntry(nb, sb, clazz);
    }

    private static void emitPayloadEntry(NativeBackend nb, StringBuilder sb, IRClass clazz) {
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        boolean mainHasArgs = clazz.methods().stream()
                .filter(m -> "main".equals(m.name()))
                .anyMatch(m -> !m.parameterTypes().isEmpty());
        sb.append("\n.section .text\n");
        sb.append(".globl kof_payload_entry\n");
        sb.append("kof_payload_entry:\n");
        // B-1: pilha vinda do linker script (arena de heap+pilha do payload).
        sb.append("    leaq __kof_stack_top(%rip), %rsp\n");
        // G-6b: fundo da pilha da thread main — contrato idêntico ao _start.
        sb.append("    movq %rsp, kof_main_stack_bottom(%rip)\n");
        sb.append("    call kof_plat_thread_id\n");
        sb.append("    movq %rax, kof_main_tid(%rip)\n");
        if (mainHasArgs) {
            // N3: array vazio — mesmo contrato do _start host.
            sb.append("    xorl %edi, %edi\n");
            sb.append("    movl $8, %esi\n");
            sb.append("    call kof_array_alloc\n");
            sb.append("    movq %rax, %rdi\n");
        }
        NativeMethodEmitter.emitClinitCalls(nb, sb);
        sb.append("    call ").append(nb.sanitizeName(clazz.name())).append("_main\n");
        // B-3b-3: a saída cruza a costura kof_plat_exit_group (corpo BIOS:
        // cli;hlt — nunca retorna ao boot).
        sb.append("    xorl %edi, %edi\n");
        sb.append("    call kof_plat_exit_group\n");
    }
}
