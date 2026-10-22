package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de utilidades (kof_instanceof) do runtime nativo. Domínio isolado
 * do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeMisc {

    private RuntimeMisc() {}

    public static void emitInstanceof(StringBuilder sb) {
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

    /**
     * §678 face B (D-SEM-REJECTS-1210): o cast de referência SIMPLES é
     * ESTRITO — `kof_cast_check(obj, typeId)` devolve o MESMO objeto quando
     * passa (incluindo `null`, a semântica CHECKCAST do JVM, o oráculo) e
     * derruba via `kof_panic` quando não é instância. Antes disto o emissor
     * x86 baixava `KofCheckCast` para NO-OP e o valor do tipo errado seguia.
     */
    public static void emitCastCheck(StringBuilder sb) {
        sb.append("""
            .globl kof_cast_check
            .type kof_cast_check, @function
            kof_cast_check:
                testq %rdi, %rdi
                jz .Lkof_cast_ok
                pushq %rdi
                call kof_instanceof
                popq %rdi
                testl %eax, %eax
                jz .Lkof_cast_fail
            .Lkof_cast_ok:
                movq %rdi, %rax
                ret
            .Lkof_cast_fail:
                andq $-16, %rsp
                leaq .Lkof_cast_msg(%rip), %rdi
                movl $16, %esi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
            .Lkof_cast_msg: .asciz "type cast failed"
            """);
    }

}