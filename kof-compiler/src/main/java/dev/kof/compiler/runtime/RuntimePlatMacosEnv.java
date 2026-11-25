package dev.kof.compiler.runtime;

/**
 * M-6 (#1135): o corpo DARWIN da família ENVIRON da costura — no Darwin o
 * ambiente vem do {@code getenv} da libSystem (o
 * {@code /proc/self/environ} do Linux não existe aqui).
 *
 * <p><b>O CONTRATO (o mesmo da família):</b> {@code rdi} = o nome como C
 * string e {@code rax = KofString* | 0} — o corpo Linux está em
 * {@link RuntimePlatEnv}.
 */
public final class RuntimePlatMacosEnv {

    private RuntimePlatMacosEnv() {}

    /**
     * M-6 (#1135): a família ENVIRON da costura — no Darwin o ambiente vem do
     * {@code getenv} da libSystem (o {@code /proc/self/environ} do Linux não
     * existe aqui). O contrato é o mesmo da família: {@code rdi = nome (C
     * string)} e {@code rax = KofString* | 0}.
     */
    public static void emitEnv(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_env_get
            .type kof_plat_env_get, @function
            # kof_plat_env_get(rdi=nome C-string) -> rax = KofString*|0
            kof_plat_env_get:
                pushq %rbx
                pushq %r12
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call getenv              # char *getenv(const char *)
                testq %rax, %rax
                jz .Lpe_none
                movq %rax, %rbx           # o valor
                xorq %r12, %r12           # len
            .Lpe_len:
                cmpb $0, (%rbx,%r12)
                je .Lpe_have
                incq %r12
                jmp .Lpe_len
            .Lpe_have:
                movq %rbx, %rdi
                movl %r12d, %esi
                call kof_string_from_literal
                jmp .Lpe_ret
            .Lpe_none:
                xorq %rax, %rax
            .Lpe_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                popq %r12
                popq %rbx
                ret
            """);
    }
}
