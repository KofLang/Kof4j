package dev.kof.compiler.nat;

/**
 * §1187 (22/11) — o vetor de argumentos do {@code main(String[] args)} no
 * NATIVO x86-64: a LEITURA do {@code argc}/{@code argv} e a MONTAGEM do
 * array de Strings.
 *
 * <p>POR QUE ESTA CLASSE EXISTE: a leitura do vetor tem TRÊS fontes
 * diferentes por modo de entrada, e cada uma delas tem um modo de falhar
 * próprio (o primeiro rascunho lia a pilha em TODO modo e quebrou os dois
 * modos que nao a tem):
 *
 * <ul>
 *   <li><b>{@code _start} cru</b> (host e freestanding): o kernel Linux
 *       entrega {@code [rsp]=argc} e {@code [rsp+8]=argv[0..)} — a leitura
 *       tem que acontecer ANTES de qualquer mexida no {@code rsp} (o
 *       freestanding troca para {@code __kof_stack_top}, um {@code .bss}
 *       zerado: ler depois entregava um {@code argc} de lixo).</li>
 *   <li><b>ASAN</b> ({@code KOF_ASAN=1}): o {@code main} do crt salta para
 *       {@code kof_program_entry} com a ABI C — {@code %edi=argc},
 *       {@code %rsi=argv} — e NAO com o vetor na pilha ({@code (%rsp)} la' e'
 *       o endereco de retorno do {@code __libc_start_main}).</li>
 *   <li><b>sem argumentos</b>: o {@code argc} pode ser 0 (ou 1, so' o
 *       caminho do binario) — depois do {@code decq} (o {@code argv[0]} e' o
 *       caminho do BINARIO e o contrato do Kof entrega so' os argumentos DO
 *       USUARIO, como o JVM e o WASI) o contador vai a -1, e o
 *       {@code kof_array_alloc} trata o {@code %edi} como unsigned de 32
 *       bits: 0xffffffff elementos = ~34 GiB = "out of memory". O clamp
 *       fecha o caso.</li>
 * </ul>
 *
 * <p>A disciplina de registradores: {@code %r15} (contador), {@code %r14}
 * (argv), {@code %rbx} (o array), {@code %r12}/{@code %r13} (o loop) sao
 * callee-saved na ABI SysV — o runtime preserva (o {@code kof_array_alloc}
 * empurra/puxa {@code %rbx}/{@code %r12}; o
 * {@code kof_string_from_literal} empurra/puxa {@code %rbx}/{@code %r12}/
 * {@code %r13}), e o {@code kof_plat_thread_id} so usa {@code %rax}.
 */
final class NativeMainArgs {

    private NativeMainArgs() {}

    /**
     * Le o {@code argc}/{@code argv} e monta o array de Strings do
     * {@code main(String[] args)} — o array VAI em {@code %rdi} na saida.
     *
     * @param asan {@code true} no modo ASAN (a ABI C do crt: {@code %edi}/
     *             {@code %rsi}), {@code false} no {@code _start} cru (o vetor
     *             do kernel na pilha).
     */
    static void emit(StringBuilder sb, boolean asan) {
        // o argc (menos o argv[0] = caminho do binario) e' o numero de
        // argumentos DO USUARIO; o argv aponta no primeiro deles.
        if (asan) {
            sb.append("    movslq %edi, %r15\n");
            sb.append("    decq %r15\n");
            sb.append("    leaq 8(%rsi), %r14\n");
        } else {
            sb.append("    movq (%rsp), %r15\n");
            sb.append("    decq %r15\n");
            sb.append("    leaq 16(%rsp), %r14\n");
        }
        // clamp: sem argumentos (argc 0) o contador ficaria -1 e o
        // kof_array_alloc pediria ~34 GiB (o "%edi" e' lido como unsigned).
        sb.append("    testq %r15, %r15\n");
        sb.append("    jns .Lkof_args_clamp_ok\n");
        sb.append("    xorl %r15d, %r15d\n");
        sb.append(".Lkof_args_clamp_ok:\n");
        // [len at +16][argv[0..argc) at +24], cada elemento = o String de
        // kof_string_from_literal(ptr, len) — o helper que o backend JA emite
        // para os literais (nao depende do FFI).
        sb.append("    movq %r15, %rdi\n");
        sb.append("    movl $8, %esi\n");
        sb.append("    call kof_array_alloc\n");
        sb.append("    movq %rax, %rbx\n");
        sb.append("    xorq %r12, %r12\n");
        sb.append(".Lkof_args_loop:\n");
        sb.append("    cmpq %r15, %r12\n");
        sb.append("    jge .Lkof_args_done\n");
        sb.append("    movq (%r14,%r12,8), %rdi\n");   // argv[i] (C str)
        sb.append("    movq %rdi, %r13\n");
        sb.append("    xorq %rcx, %rcx\n");
        sb.append(".Lkof_args_strlen:\n");
        sb.append("    cmpb $0, (%r13,%rcx)\n");
        sb.append("    je .Lkof_args_strlen_done\n");
        sb.append("    incq %rcx\n");
        sb.append("    jmp .Lkof_args_strlen\n");
        sb.append(".Lkof_args_strlen_done:\n");
        sb.append("    movl %ecx, %esi\n");              // len
        sb.append("    call kof_string_from_literal\n");  // (ptr, len) -> String
        sb.append("    movq %rax, 24(%rbx,%r12,8)\n");
        sb.append("    incq %r12\n");
        sb.append("    jmp .Lkof_args_loop\n");
        sb.append(".Lkof_args_done:\n");
        sb.append("    movq %rbx, %rdi\n");
    }
}
