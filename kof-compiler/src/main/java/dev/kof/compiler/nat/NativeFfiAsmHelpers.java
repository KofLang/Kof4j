package dev.kof.compiler.nat;

/**
 * Helpers de runtime dos extern x86-64 (marshaling FFI) — emitidos UMA vez por
 * programa quando o extern correspondente aparece, referenciados pelo próprio
 * call-site. Extraído de {@code NativeFfiCall} (gate &le;500 linhas, regra 7);
 * o cross riscv/aarch tem os seus pares em {@code NativeFfiCallRiscv}.
 */
final class NativeFfiAsmHelpers {

    private NativeFfiAsmHelpers() {}

    /** Helper char*→String (cópia UTF-8 crua na fronteira — o buffer C nunca é
     *  liberado; NULL → 0 = null Kof). Definido UMA vez por programa quando um
     *  extern retorna String; o PRÓPRIO call-site o referencia (a poda de
     *  runtime é por texto do programa — o helper vive no texto do programa). */
    static void emitX86CstrHelper(StringBuilder sb) {
        sb.append("""
                kof_ffi_from_cstr:
                    testq %rdi, %rdi
                    je .Lffc_null
                    pushq %r12
                    pushq %r13
                    pushq %r14
                    movq %rdi, %r12
                    xorq %rcx, %rcx
                .Lffc_scan:
                    cmpb $0, (%r12,%rcx)
                    je .Lffc_got
                    incq %rcx
                    jmp .Lffc_scan
                .Lffc_got:
                    movq %rcx, %r13
                    leal 25(%r13), %edi
                    call kof_alloc
                    movq %rax, %r14
                    movl $1, 0(%r14)
                    movl $0, 4(%r14)
                    movq $0, 8(%r14)
                    movl %r13d, 16(%r14)
                    movl $0, 20(%r14)
                    leaq 24(%r14), %rdi
                    movq %r12, %rsi
                    movl %r13d, %edx
                    call kof_memcpy
                    movb $0, 24(%r14,%r13)
                    movq %r14, %rax
                    popq %r14
                    popq %r13
                    popq %r12
                    ret
                .Lffc_null:
                    xorl %eax, %eax
                    ret
                """);
    }

    /**
     * D6-2/3.7: empacota um array Kof de escalares num buffer C contíguo —
     * copy-in por chamada, paridade com o JVM (o array Kof nunca é mutado pela
     * C; escritas são descartadas). {@code %rdi} = objeto array, {@code %rsi} =
     * tamanho do elemento em bytes; retorno {@code %rax} = buffer
     * ({@code kof_alloc}). Layout Kof do array: len em 16(obj), payload em 24.
     * Definido uma vez por programa quando um extern recebe array (o call-site o
     * referencia).
     */
    static void emitX86ArrayPackHelper(StringBuilder sb) {
        sb.append("""
                .globl kof_ffi_pack_array
                .type kof_ffi_pack_array, @function
                kof_ffi_pack_array:
                    pushq %rbx
                    pushq %r12
                    pushq %r13
                    pushq %r14
                    subq $8, %rsp
                    movq %rdi, %rbx
                    movq %rsi, %r13
                    movl 16(%rbx), %r12d
                    movq %r12, %rdi
                    imulq %r13, %rdi
                    testq %rdi, %rdi
                    jne .Lfpa_alloc
                    movq $8, %rdi
                .Lfpa_alloc:
                    call kof_alloc
                    movq %rax, %r14
                    leaq 24(%rbx), %rsi
                    movq %r14, %rdi
                    movq %r12, %rdx
                    imulq %r13, %rdx
                    testq %rdx, %rdx
                    je .Lfpa_done
                    call kof_memcpy
                .Lfpa_done:
                    movq %r14, %rax
                    addq $8, %rsp
                    popq %r14
                    popq %r13
                    popq %r12
                    popq %rbx
                    ret
                """);
    }

    /**
     * D-MEM-FFI-CROSS-FULL face 2: empacota um array Kof `String[]` num
     * `char**` C — cada slot recebe o payload UTF-8 do objeto String (offset 24,
     * que já é um cstr NUL-terminado, o mesmo que o escalar `'S'` passa), NULL→0.
     * Copy-in por chamada (o array Kof nunca é mutado). {@code %rdi} = objeto
     * array; retorno {@code %rax} = buffer ({@code kof_alloc}). Layout Kof do
     * array: len em 16(obj), payload de ponteiros em 24. Definido uma vez por
     * programa quando um extern recebe `String[]`.
     */
    static void emitX86StrArrayPackHelper(StringBuilder sb) {
        sb.append("""
                .globl kof_ffi_pack_str_array
                .type kof_ffi_pack_str_array, @function
                kof_ffi_pack_str_array:
                    pushq %rbx
                    pushq %r12
                    pushq %r13
                    pushq %r14
                    subq $8, %rsp
                    movq %rdi, %rbx
                    movl 16(%rbx), %r12d
                    movq %r12, %rdi
                    shlq $3, %rdi
                    testq %rdi, %rdi
                    jne .Lfps_alloc
                    movq $8, %rdi
                .Lfps_alloc:
                    call kof_alloc
                    movq %rax, %r13
                    xorl %r14d, %r14d
                .Lfps_loop:
                    cmpq %r12, %r14
                    jae .Lfps_done
                    movq 24(%rbx,%r14,8), %r11
                    testq %r11, %r11
                    je .Lfps_store
                    addq $24, %r11
                .Lfps_store:
                    movq %r11, 0(%r13,%r14,8)
                    incq %r14
                    jmp .Lfps_loop
                .Lfps_done:
                    movq %r13, %rax
                    addq $8, %rsp
                    popq %r14
                    popq %r13
                    popq %r12
                    popq %rbx
                    ret
                """);
    }

    /**
     * M1 callbacks on Native (x86-64): o trampolim de upcall de um descritor
     * {@code desc = <ret><arg0>…}. O C chama este símbolo com os args SysV —
     * inteiros em {@code rdi/rsi/…}, FP em {@code xmm0/…}; o retorno sai em
     * {@code rax} (inteiro) ou {@code xmm0} (FP). O trampolim remonta a chamada
     * para a convenção do {@code invoke} Kof (soft-float-safe: TODO arg, inclusive
     * FP, num registrador INTEIRO com os bits crus; {@code rdi}=objeto lambda) e
     * chama {@code vtable[0]} via {@code call *%r11}.
     *
     * <p>Síncrono/não-escapante: a closure vive num global (um callback ativo por
     * vez) — o mesmo confinamento da call-arena da JVM (R7).
     */
    static void emitX86CallbackTrampoline(StringBuilder sb, String desc) {
        int arity = desc.length() - 1;
        char rc = desc.charAt(0);
        String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
        // Fonte C por arg: inteiro → rdi.. ; FP → xmm0..
        String[] src = new String[arity];
        boolean[] isFp = new boolean[arity];
        int ip = 0, fp = 0;
        for (int i = 0; i < arity; i++) {
            char c = desc.charAt(i + 1);
            boolean f = (c == 'f' || c == 'd');
            isFp[i] = f;
            if (f) src[i] = "%xmm" + (fp++);
            else src[i] = intRegs[ip++];
        }
        sb.append("kof_cb_tramp_").append(desc).append(":\n");
        // Empilha os args do C (do último p/ o 1º; FP via %rax com os bits crus).
        for (int i = arity - 1; i >= 0; i--) {
            if (isFp[i]) {
                sb.append(desc.charAt(i + 1) == 'f'
                        ? "    movd " + src[i] + ", %eax\n"
                        : "    movq " + src[i] + ", %rax\n");
                sb.append("    pushq %rax\n");
            } else {
                sb.append("    pushq ").append(src[i]).append("\n");
            }
        }
        // Desempilha nos registradores do invoke (rsi, rdx, … = intRegs[1..]);
        // args além de 5 ficam na pilha (a convenção do callee os lê acima de rbp).
        int nreg = Math.min(arity, 5);
        for (int k = 0; k < nreg; k++) {
            sb.append("    popq ").append(intRegs[k + 1]).append("\n");
        }
        sb.append("    movq kof_cb_closure_").append(desc).append("(%rip), %rdi\n");
        sb.append("    movq 8(%rdi), %r11\n");   // vtable @ offset 8
        sb.append("    movq (%r11), %r11\n");    // vtable[0] = invoke
        sb.append("    call *%r11\n");
        if (arity > 5) sb.append("    addq $").append((arity - 5) * 8).append(", %rsp\n");
        // Retorno: inteiro já em rax; FP move os bits crus rax → xmm0.
        if (rc == 'f') sb.append("    movd %eax, %xmm0\n");
        else if (rc == 'd') sb.append("    movq %rax, %xmm0\n");
        sb.append("    ret\n");
        sb.append(".section .bss\n");
        sb.append("kof_cb_closure_").append(desc).append(":\n");
        sb.append(".quad 0\n");
        sb.append(".section .text\n");
    }
}
