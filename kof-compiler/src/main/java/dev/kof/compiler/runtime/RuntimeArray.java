package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM das funções de array (kof_array_alloc/length/get/set) do
 * runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeArray {

    private RuntimeArray() {}

    public static void emitArrayAlloc(StringBuilder sb) {
        sb.append("""
            .globl kof_array_alloc
            .type kof_array_alloc, @function
            kof_array_alloc:
                pushq %rbx
                pushq %r12
                movl %edi, %ebx
                movl %esi, %r12d
                movq %rbx, %rax
                imulq %r12, %rax
                addq $24, %rax
                movq %rax, %rdi
                call kof_alloc
                movq %rax, %rcx
                movl $2, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl %ebx, 16(%rcx)
                movl %r12d, 20(%rcx)
                movq %rcx, %rax
                popq %r12
                popq %rbx
                ret
            """);
    }
    public static void emitMultiArrayAlloc(StringBuilder sb) {
        sb.append("""
            # §113 — alocação recursiva de array multidimensional. O chamador
            # empurra os n tamanhos (d_1 primeiro ... d_n no topo). Frame por
            # nível: ret(8) + 6 pushes(48) + dummy(8) = 64 → d_i na depth i está
            # em rsp + 56i + 8(n-i). ABI de entrada: esi=i (1-based), edx=n,
            # rbx=stride da última dim. Ret: rax = nó (header de kof_array_alloc).
            # Nós internos: elemSize 8 (ponteiros p/ sub-array). Folhas:
            # elemSize=rbx e payload zeroed (paridade MULTIANEWARRAY/newMultiArray).
            .globl kof_multi_alloc
            .type kof_multi_alloc, @function
            kof_multi_alloc:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %rbp
                pushq %rax
                movl %edx, %r15d
                movl %esi, %r12d
                movq %r12, %rax
                imulq $56, %rax
                movq %r15, %rcx
                imulq $8, %rcx
                addq %rcx, %rax
                movq (%rsp,%rax), %rdi
                movq %rdi, %r13
                movl $8, %esi
                cmpl %r15d, %r12d
                jne .Lma_go
                movl %ebx, %esi
            .Lma_go:
                call kof_array_alloc
                movq %rax, %r14
                xorl %ebp, %ebp
                cmpl %r15d, %r12d
                jne .Lma_fill
                movq %r14, %rdi
                addq $24, %rdi
                movq %r13, %rcx
                imulq %rbx, %rcx
                xorl %eax, %eax
                cld
                rep stosb
                jmp .Lma_done
            .Lma_fill:
                cmpq %r13, %rbp
                jae .Lma_done
                leal 1(%r12), %esi
                movl %r15d, %edx
                call kof_multi_alloc
                movq %rax, 24(%r14,%rbp,8)
                incq %rbp
                jmp .Lma_fill
            .Lma_done:
                movq %r14, %rax
                addq $8, %rsp
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
    public static void emitArrayLength(StringBuilder sb) {
        sb.append("""
            .globl kof_array_length
            .type kof_array_length, @function
            kof_array_length:
                movl 16(%rdi), %eax
                ret
            """);
    }
    public static void emitArrayGet(StringBuilder sb) {
        sb.append("""
            .globl kof_array_get
            .type kof_array_get, @function
            kof_array_get:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl %esi, %r12d
                testq %rbx, %rbx
                jz .Lkof_array_get_null
                movl 16(%rbx), %ecx
                cmpl %ecx, %r12d
                jge .Lkof_array_get_bounds
                cmpl $0, %r12d
                jl .Lkof_array_get_bounds
                movl 20(%rbx), %edx
                movq %r12, %rax
                imulq %rdx, %rax
                addq $24, %rax
                addq %rbx, %rax
                cmpl $8, %edx
                je .Lkof_array_get_q
                cmpl $4, %edx
                je .Lkof_array_get_d
                cmpl $2, %edx
                je .Lkof_array_get_w
                movsbq (%rax), %rax
                jmp .Lkof_array_get_done
            .Lkof_array_get_w:
                movswq (%rax), %rax
                jmp .Lkof_array_get_done
            .Lkof_array_get_d:
                movslq (%rax), %rax
                jmp .Lkof_array_get_done
            .Lkof_array_get_q:
                movq (%rax), %rax
            .Lkof_array_get_done:
                popq %r12
                popq %rbx
                ret
            .Lkof_array_get_null:
                call kof_null_error
            .Lkof_array_get_bounds:
                movl %r12d, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error
            """);
    }
    public static void emitArraySet(StringBuilder sb) {
        sb.append("""
            .globl kof_array_set
            .type kof_array_set, @function
            kof_array_set:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl %esi, %r12d
                movq %rdx, %r13
                testq %rbx, %rbx
                jz .Lkof_array_set_null
                movl 16(%rbx), %ecx
                cmpl %ecx, %r12d
                jge .Lkof_array_set_bounds
                cmpl $0, %r12d
                jl .Lkof_array_set_bounds
                movl 20(%rbx), %edx
                movq %r12, %rax
                imulq %rdx, %rax
                addq $24, %rax
                addq %rbx, %rax
                cmpl $8, %edx
                je .Lkof_array_set_q
                cmpl $4, %edx
                je .Lkof_array_set_d
                cmpl $2, %edx
                je .Lkof_array_set_w
                movb %r13b, (%rax)
                jmp .Lkof_array_set_done
            .Lkof_array_set_w:
                movw %r13w, (%rax)
                jmp .Lkof_array_set_done
            .Lkof_array_set_d:
                movl %r13d, (%rax)
                jmp .Lkof_array_set_done
            .Lkof_array_set_q:
                movq %r13, (%rax)
            .Lkof_array_set_done:
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_array_set_null:
                call kof_null_error
            .Lkof_array_set_bounds:
                movl %r12d, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error
            """);
    }}
