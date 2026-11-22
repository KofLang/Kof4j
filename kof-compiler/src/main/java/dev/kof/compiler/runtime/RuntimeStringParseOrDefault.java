package dev.kof.compiler.runtime;

/**
 * S13b (plan-stdlib-expansion §2, P0): parse String→número com default —
 * briefing §43 ("falha de parse = OrNull/OrDefault"). Wrapper x86 sobre os
 * parseadores EXISTENTES de RuntimeStringParse/RuntimeStringParseFp (regra 2,
 * zero parse novo): instala um handler no kof_exc_chain (MESMO mecanismo do
 * try/catch Kof — KofTryStart/KofCatchStart em NativeMethodEmitter, frame de
 * 32 bytes handler/rsp/rbp/chain), chama kof_string_to_* e, se o parse
 * lançar, o handler restaura o chain e DEVOLVE o default (nunca lança —
 * paridade com JvmStringCoreRuntime try/catch e wrappers JS).
 *
 * Convenções (mesmas dos parseadores): Int/Long = %rax; Double = %xmm0
 * (bits crus). Argumentos: %rdi = String, %rsi/%xmm1 = default (Int/Long
 * chegam como 8 bytes na pilha→rsi; Double = bits na pilha→movq xmm1).
 */
public final class RuntimeStringParseOrDefault {

    private RuntimeStringParseOrDefault() {}

    /** Emite os 3 wrappers (int/long/double). Double entra como bits crus
     * na pilha (convenção double do native) — o wrapper passa os 8 bytes
     * direto pro slot %rsi e devolve por xmm0 (idem kof_string_to_double). */
    public static void emitAll(StringBuilder sb) {
        emitInt(sb);
        emitLong(sb);
        emitDouble(sb);
    }

    private static void emitInt(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_string_to_int_or_default
            .type kof_string_to_int_or_default, @function
            # (String, Int default) -> Int. Handler local no exc_chain:
            # parse lança -> handler devolve o default (nunca lança, §43).
            kof_string_to_int_or_default:
                pushq %rbp
                movq %rsp, %rbp
                subq $48, %rsp
                # frame do handler: [0]=handler [8]=rsp [16]=rbp [24]=chain antigo
                leaq .Lkof_stiod_handler(%rip), %rax
                movq %rax, 0(%rsp)
                movq %rsp, 8(%rsp)
                movq %rbp, 16(%rsp)
                call kof_plat_tls_get       # M-4 (#1135): a cadeia de exceções pela costura
                movq %rax, 24(%rsp)
                pushq %rdi
                pushq %rcx
                leaq 16(%rsp), %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                # args do parse: %rdi já é a String; default fica salvo no rbp
                movq %rsi, -8(%rbp)
                call kof_string_to_int
                # sucesso: restaura chain e devolve %rax
                movq 24(%rsp), %rcx
                pushq %rdi
                pushq %rcx
                movq %rcx, %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lkof_stiod_handler:
                # chega com a String do erro em %rdi; devolve o default
                call kof_plat_tls_get       # M-4 (#1135): a cadeia de exceções pela costura
                movq 24(%rsp), %rcx
                pushq %rdi
                pushq %rcx
                movq %rcx, %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                movq -8(%rbp), %rax
                movq %rbp, %rsp
                popq %rbp
                ret
            """);
    }

    private static void emitLong(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_string_to_long_or_default
            .type kof_string_to_long_or_default, @function
            kof_string_to_long_or_default:
                pushq %rbp
                movq %rsp, %rbp
                subq $48, %rsp
                leaq .Lkof_stlod_handler(%rip), %rax
                movq %rax, 0(%rsp)
                movq %rsp, 8(%rsp)
                movq %rbp, 16(%rsp)
                call kof_plat_tls_get       # M-4 (#1135): a cadeia de exceções pela costura
                movq %rax, 24(%rsp)
                pushq %rdi
                pushq %rcx
                leaq 16(%rsp), %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                movq %rsi, -8(%rbp)
                call kof_string_to_long
                movq 24(%rsp), %rcx
                pushq %rdi
                pushq %rcx
                movq %rcx, %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lkof_stlod_handler:
                call kof_plat_tls_get       # M-4 (#1135): a cadeia de exceções pela costura
                movq 24(%rsp), %rcx
                pushq %rdi
                pushq %rcx
                movq %rcx, %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                movq -8(%rbp), %rax
                movq %rbp, %rsp
                popq %rbp
                ret
            """);
    }

    private static void emitDouble(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_string_to_double_or_default
            .type kof_string_to_double_or_default, @function
            # (String, Double default) -> Double. Default chega como bits crus
            # na pilha (convenção double do native): 2º slot -> xmm1. Devolve
            # por xmm0 (idem kof_string_to_double em NativeX86StringCalls).
            kof_string_to_double_or_default:
                pushq %rbp
                movq %rsp, %rbp
                subq $48, %rsp
                leaq .Lkof_stdod_handler(%rip), %rax
                movq %rax, 0(%rsp)
                movq %rsp, 8(%rsp)
                movq %rbp, 16(%rsp)
                call kof_plat_tls_get       # M-4 (#1135): a cadeia de exceções pela costura
                movq %rax, 24(%rsp)
                pushq %rdi
                pushq %rcx
                leaq 16(%rsp), %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                # default (bits crus em %rsi) salvo no rbp; parse lê %rdi
                movq %rsi, -8(%rbp)
                call kof_string_to_double
                movq 24(%rsp), %rcx
                pushq %rdi
                pushq %rcx
                movq %rcx, %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                movq %xmm0, %rax
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lkof_stdod_handler:
                call kof_plat_tls_get       # M-4 (#1135): a cadeia de exceções pela costura
                movq 24(%rsp), %rcx
                pushq %rdi
                pushq %rcx
                movq %rcx, %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                # devolve o default por xmm0 (bits crus do rbp)
                movq -8(%rbp), %rax
                movq %rax, %xmm0
                movq %rbp, %rsp
                popq %rbp
                ret
            """);
    }
}
