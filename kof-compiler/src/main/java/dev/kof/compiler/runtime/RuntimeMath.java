package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de kof.math (STDLIB S1) do runtime nativo x86_64.
 * Int-only (paridade byte-idêntica com JVM/JS/interpreter):
 * clamp/abs/sign/min/max + predicados paridade/sinal/zero.
 * SysV: edi/esi/edx = args, eax = retorno.
 */
public final class RuntimeMath {

    private RuntimeMath() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_math_abs
            .type kof_math_abs, @function
            kof_math_abs:
                testl %edi, %edi
                jge .Lv_math_abs_pos
                negl %edi
            .Lv_math_abs_pos:
                movl %edi, %eax
                ret

            # kof_math_sign(edi=v) -> 1/0/-1
            .globl kof_math_sign
            .type kof_math_sign, @function
            kof_math_sign:
                testl %edi, %edi
                jg .Lv_math_sign_pos
                jge .Lv_math_sign_zero
                movl $-1, %eax
                ret
            .Lv_math_sign_zero:
                xorl %eax, %eax
                ret
            .Lv_math_sign_pos:
                movl $1, %eax
                ret

            # kof_math_clamp(edi=v, esi=lo, edx=hi) -> v
            .globl kof_math_clamp
            .type kof_math_clamp, @function
            kof_math_clamp:
                cmpl %esi, %edi
                jge .Lv_math_clamp_mid
                movl %esi, %eax
                jmp .Lv_math_clamp_done
            .Lv_math_clamp_mid:
                movl %edi, %eax
            .Lv_math_clamp_done:
                cmpl %edx, %eax
                jle .Lv_math_clamp_ret
                movl %edx, %eax
            .Lv_math_clamp_ret:
                ret

            # kof_math_min(edi=a, esi=b) -> menor
            .globl kof_math_min
            .type kof_math_min, @function
            kof_math_min:
                cmpl %esi, %edi
                jle .Lv_math_min_a
                movl %esi, %edi
            .Lv_math_min_a:
                movl %edi, %eax
                ret

            # kof_math_max(edi=a, esi=b) -> maior
            .globl kof_math_max
            .type kof_math_max, @function
            kof_math_max:
                cmpl %esi, %edi
                jge .Lv_math_max_a
                movl %esi, %edi
            .Lv_math_max_a:
                movl %edi, %eax
                ret

            # kof_math_isEven(edi=v) -> 1/0
            .globl kof_math_isEven
            .type kof_math_isEven, @function
            kof_math_isEven:
                testl $1, %edi
                jnz .Lv_math_even_false
                movl $1, %eax
                ret
            .Lv_math_even_false:
                xorl %eax, %eax
                ret

            # kof_math_isOdd(edi=v) -> 1/0
            .globl kof_math_isOdd
            .type kof_math_isOdd, @function
            kof_math_isOdd:
                testl $1, %edi
                jz .Lv_math_odd_false
                movl $1, %eax
                ret
            .Lv_math_odd_false:
                xorl %eax, %eax
                ret

            # kof_math_isPositive(edi=v) -> 1/0
            .globl kof_math_isPositive
            .type kof_math_isPositive, @function
            kof_math_isPositive:
                cmpl $0, %edi
                jg .Lv_math_pos_true
                xorl %eax, %eax
                ret
            .Lv_math_pos_true:
                movl $1, %eax
                ret

            # kof_math_isNegative(edi=v) -> 1/0
            .globl kof_math_isNegative
            .type kof_math_isNegative, @function
            kof_math_isNegative:
                cmpl $0, %edi
                jge .Lv_math_neg_false
                movl $1, %eax
                ret
            .Lv_math_neg_false:
                xorl %eax, %eax
                ret

            # kof_math_isZero(edi=v) -> 1/0
            .globl kof_math_isZero
            .type kof_math_isZero, @function
            kof_math_isZero:
                testl %edi, %edi
                jnz .Lv_math_zero_false
                movl $1, %eax
                ret
            .Lv_math_zero_false:
                xorl %eax, %eax
                ret

            # S1b: kof_math_sqrt(xmm0=v) -> xmm0 (FLT fechado 31/08 via XMM;
            # riscv64/aarch64 = MATH001). NaN em <0 — paridade Math.sqrt.
            .globl kof_math_sqrt
            .type kof_math_sqrt, @function
            kof_math_sqrt:
                sqrtsd %xmm0, %xmm0
                ret

            # S1b.2 (decisão 7a): pow(base,exp) — PRIMEIRO caso libm. O
            # caminho genérico entrega base/exp como 8 bits crus em rdi/rsi
            # (pilha 1-slot do emitArgs); SysV quer doubles em xmm0/xmm1.
            # A pilha de operandos tem paridade imprevisível (1 push = 8B),
            # então auto-alinha via rbx como o snprintf (RuntimePrintNum):
            # o código GERADO nunca usa rbx e pow o preserva (callee-saved
            # SysV — medido no Arith/StringCalls: só rax/rdi/rsi/rcx/rdx).
            # Retorno = bits crus em rax (pushq do genérico).
            # R2 fatia 1 (20/09): FRACO por design — o link sem -lm fecha
            # (link-by-use do libm); sem libm o simbolo resolve a 0 e NUNCA e
            # chamado (call-sites so nascem quando usesPow=true, que e quando
            # -lm entra). Com -lm, o pow forte da libm vincula normalmente.
            .weak pow
            .globl kof_math_pow
            .type kof_math_pow, @function
            kof_math_pow:
                movq %rdi, %xmm0
                movq %rsi, %xmm1
                movq %rsp, %rbx
                andq $-16, %rsp
                call pow
                movq %rbx, %rsp
                movq %xmm0, %rax
                ret
            ret

            # === Stage 2 — libm shims x86_64 ===
            .globl kof_math_sinh; .type kof_math_sinh, @function
            kof_math_sinh:
                subq $8, %rsp
                call sinh@PLT
                addq $8, %rsp
                ret

            .globl kof_math_cosh; .type kof_math_cosh, @function
            kof_math_cosh:
                subq $8, %rsp
                call cosh@PLT
                addq $8, %rsp
                ret

            .globl kof_math_tanh; .type kof_math_tanh, @function
            kof_math_tanh:
                subq $8, %rsp
                call tanh@PLT
                addq $8, %rsp
                ret

            .globl kof_math_asinh; .type kof_math_asinh, @function
            kof_math_asinh:
                subq $8, %rsp
                call asinh@PLT
                addq $8, %rsp
                ret

            .globl kof_math_acosh; .type kof_math_acosh, @function
            kof_math_acosh:
                subq $8, %rsp
                call acosh@PLT
                addq $8, %rsp
                ret

            .globl kof_math_atanh; .type kof_math_atanh, @function
            kof_math_atanh:
                subq $8, %rsp
                call atanh@PLT
                addq $8, %rsp
                ret

            .globl kof_math_exp; .type kof_math_exp, @function
            kof_math_exp:
                subq $8, %rsp
                call exp@PLT
                addq $8, %rsp
                ret

            .globl kof_math_expm1; .type kof_math_expm1, @function
            kof_math_expm1:
                subq $8, %rsp
                call expm1@PLT
                addq $8, %rsp
                ret

            .globl kof_math_log; .type kof_math_log, @function
            kof_math_log:
                subq $8, %rsp
                call log@PLT
                addq $8, %rsp
                ret

            .globl kof_math_log1p; .type kof_math_log1p, @function
            kof_math_log1p:
                subq $8, %rsp
                call log1p@PLT
                addq $8, %rsp
                ret

            .globl kof_math_log10; .type kof_math_log10, @function
            kof_math_log10:
                subq $8, %rsp
                call log10@PLT
                addq $8, %rsp
                ret

            .globl kof_math_cbrt; .type kof_math_cbrt, @function
            kof_math_cbrt:
                subq $8, %rsp
                call cbrt@PLT
                addq $8, %rsp
                ret

            .globl kof_math_hypot; .type kof_math_hypot, @function
            kof_math_hypot:
                subq $8, %rsp
                call hypot@PLT
                addq $8, %rsp
                ret

            .globl kof_math_ceil; .type kof_math_ceil, @function
            kof_math_ceil:
                subq $8, %rsp
                call ceil@PLT
                addq $8, %rsp
                ret

            .globl kof_math_floor; .type kof_math_floor, @function
            kof_math_floor:
                subq $8, %rsp
                call floor@PLT
                addq $8, %rsp
                ret

            .globl kof_math_rint; .type kof_math_rint, @function
            kof_math_rint:
                subq $8, %rsp
                call rint@PLT
                addq $8, %rsp
                ret

            .globl kof_math_round; .type kof_math_round, @function
            kof_math_round:
                subq $8, %rsp
                call round@PLT
                addq $8, %rsp
                ret

            .globl kof_math_signum; .type kof_math_signum, @function
            kof_math_signum:
                cmpq $0, %xmm0
                jb .signum_neg
                ja .signum_pos
                movl $0x3FF00000, %eax
                movq %rax, %xmm0
                ret
            .signum_pos:
                movl $0x3FF00000, %eax
                movq %rax, %xmm0
                ret
            .signum_neg:
                movl $0xBFF00000, %eax
                movq %rax, %xmm0
                ret

            # S1b.1: escalares Double puros (SSE2 — sem libm). Args chegam
            # como 8 bits crus em rdi/rsi/rdx (pilha 1-slot do generic path);
            # retorno = bits crus em rax (o generic path faz pushq %rax).
            # lerp(a,b,t) = a+(b-a)*t — mesma ordem do JVM/JS (dsub/dmul/dadd).
            .globl kof_math_lerp
            .type kof_math_lerp, @function
            kof_math_lerp:
                movq %rdi, %xmm0                 # a
                movq %rsi, %xmm1                 # b
                movq %rdx, %xmm2                 # t
                subsd %xmm0, %xmm1               # b-a
                mulsd %xmm2, %xmm1               # (b-a)*t
                addsd %xmm1, %xmm0               # a+...
                movq %xmm0, %rax
                ret

            # percentage(part,total) = part/total*100.0 — ordem div-then-mul
            # igual JVM/JS. 0/0 => NaN em todos (IEEE). 100.0 = 0x4059000...0
            # em constante imediata (sem .rodata — RuntimeMath é concatenado
            # no meio do .text; trocar de seção aqui arrastaria os runtimes
            # seguintes p/ .rodata).
            .globl kof_math_percentage
            .type kof_math_percentage, @function
            kof_math_percentage:
                movq %rdi, %xmm0                 # part
                movq %rsi, %xmm1                 # total
                divsd %xmm1, %xmm0
                movabsq $0x4059000000000000, %rax
                movq %rax, %xmm1
                mulsd %xmm1, %xmm0
                movq %xmm0, %rax
                ret

            # isInteger(v): exp==0x7ff (NaN/Inf) => 0; exp>=0x433 (|v|>=2^52,
            # finito) => 1; senão trunc==v. floor vs trunc: equivalente p/
            # igualdade (não-inteiro nenhum dos dois casa). 1/0 em eax.
            .globl kof_math_isInteger
            .type kof_math_isInteger, @function
            kof_math_isInteger:
                movq %rdi, %xmm0
                movq %xmm0, %rax
                movq %rax, %rdx
                shrq $52, %rdx
                andl $0x7ff, %edx
                cmpl $0x7ff, %edx
                je .Lv_mathii_false
                cmpl $0x433, %edx
                jae .Lv_mathii_true
                cvttsd2si %xmm0, %rdx
                cvtsi2sdq %rdx, %xmm1
                ucomisd %xmm1, %xmm0
                jp .Lv_mathii_false
                jne .Lv_mathii_false
            .Lv_mathii_true:
                movl $1, %eax
                ret
            .Lv_mathii_false:
                xorl %eax, %eax
                ret

            # isDecimal(v) = !isInteger(v)
            .globl kof_math_isDecimal
            .type kof_math_isDecimal, @function
            kof_math_isDecimal:
                call kof_math_isInteger
                xorl $1, %eax
                ret

            # S1b.3 (DECISIONS §3): kof_math_roundTo(rdi=v bits, esi=decimals)
            # -> rax=bits. Half-away-from-zero (âncora C round()) por escala
            # decimal determinística (âncora Java BigDecimal.setScale), SEM
            # libm. p = 10^m (m=|d|, saturado em 308) por multiplicação
            # REPETIDA — cada mul é 1 op IEEE corretamente arredondada →
            # byte-idêntico JVM/JS/riscv/aarch. d>=0: scaled=v*p, r/p. d<0:
            # scaled=v/p (encolhe, nunca estoura), r*p. Overflow de v*p (|v|
            # grande demais p/ ter casas na escala) → devolve v (no-op).
            # NÃO usa xmm1 (p) — kof_math_roundHalfAway preserva.
            .globl kof_math_roundTo
            .type kof_math_roundTo, @function
            kof_math_roundTo:
                movq %rdi, %xmm0                 # v
                # v NaN/Inf (exp==0x7ff) -> devolve v
                movq %rdi, %rax
                shrq $52, %rax
                andl $0x7ff, %eax
                cmpl $0x7ff, %eax
                je .Lv_rt_ret_v
                # m = min(|d|, 308)
                movl %esi, %ecx
                movl %ecx, %eax
                sarl $31, %eax                   # eax = d<0 ? -1 : 0
                xorl %eax, %ecx
                subl %eax, %ecx                  # ecx = |d|
                cmpl $308, %ecx
                jle .Lv_rt_m_ok
                movl $308, %ecx
            .Lv_rt_m_ok:
                # p = 10^m
                movabsq $0x3ff0000000000000, %rdx # 1.0
                movq %rdx, %xmm1                 # p = 1.0
                movabsq $0x4024000000000000, %rdx # 10.0
                movq %rdx, %xmm2
                testl %ecx, %ecx
                jz .Lv_rt_p_done
            .Lv_rt_p_loop:
                mulsd %xmm2, %xmm1               # p *= 10.0
                decl %ecx
                jnz .Lv_rt_p_loop
            .Lv_rt_p_done:
                testl %esi, %esi
                js .Lv_rt_negd
                mulsd %xmm1, %xmm0               # scaled = v*p
                movq %xmm0, %rax
                shrq $52, %rax
                andl $0x7ff, %eax
                cmpl $0x7ff, %eax
                je .Lv_rt_ret_v                  # overflow -> v
                call kof_math_roundHalfAway
                divsd %xmm1, %xmm0               # r/p
                movq %xmm0, %rax
                ret
            .Lv_rt_negd:
                divsd %xmm1, %xmm0               # scaled = v/p
                movq %xmm0, %rax
                shrq $52, %rax
                andl $0x7ff, %eax
                cmpl $0x7ff, %eax
                je .Lv_rt_ret_v
                call kof_math_roundHalfAway
                mulsd %xmm1, %xmm0               # r*p
                movq %xmm0, %rax
                ret
            .Lv_rt_ret_v:
                movq %rdi, %rax
                ret

            # kof_math_roundHalfAway(xmm0=x) -> xmm0. Half-away-from-zero:
            # trunc + correção do resto (|f|>=0.5 → ±1). |x|>=2^52 (exp>=0x433,
            # inclui NaN/Inf) já é inteiro → devolve x. Evita o double-rounding
            # do floor(x+0.5) (0.49999999999999994 → 0). Clobbers xmm3/xmm4,
            # rax/rcx/rdx; PRESERVA xmm1/xmm2 (fator do caller).
            .globl kof_math_roundHalfAway
            .type kof_math_roundHalfAway, @function
            kof_math_roundHalfAway:
                movq %xmm0, %rax
                movq %rax, %rdx
                shrq $52, %rdx
                andl $0x7ff, %edx
                cmpl $0x433, %edx
                jae .Lv_rha_ret
                cvttsd2si %xmm0, %rcx            # t = trunc (|x|<2^52, cabe)
                cvtsi2sdq %rcx, %xmm3            # (double)t
                subsd %xmm3, %xmm0               # f = x - t
                movabsq $0x3fe0000000000000, %rdx # 0.5
                movq %rdx, %xmm4
                ucomisd %xmm4, %xmm0
                jae .Lv_rha_up                   # f >= 0.5
                movabsq $0xbfe0000000000000, %rdx # -0.5
                movq %rdx, %xmm4
                ucomisd %xmm4, %xmm0
                jbe .Lv_rha_down                 # f <= -0.5
                movq %xmm3, %xmm0
                ret
            .Lv_rha_up:
                movabsq $0x3ff0000000000000, %rdx # 1.0
                movq %rdx, %xmm4
                addsd %xmm4, %xmm3
                movq %xmm3, %xmm0
                ret
            .Lv_rha_down:
                movabsq $0x3ff0000000000000, %rdx # 1.0
                movq %rdx, %xmm4
                subsd %xmm4, %xmm3
                movq %xmm3, %xmm0
                ret
            .Lv_rha_ret:
                ret

            # §146 (12/09, #101): Double % variável devolvia o dividendo (o
            # MOD do bloco Double em NativeX86Arith caía no `default` que
            # reempurra xmm0; só o fold de literais acertava). fmod em SSE2
            # puro, sem libm: a - trunc(a/b)*b. NaN/Inf/±0: trunc via
            # cvttsd2si satura em INT64_MIN (0x8000...) — o caminho SAT trata
            # como "quociente indefinido" e devolve NaN (JVM: 7.5%0=NaN,
            # Inf%x=NaN, x%0=NaN). b==±0.0 finito-normal também dá NaN.
            # Args: rdi=a-bits, rsi=b-bits; retorno: rax=bits do resto.
            .globl kof_double_mod
            .type kof_double_mod, @function
            kof_double_mod:
                movq %rdi, %xmm0                 # a
                movq %rsi, %xmm1                 # b
                movq %rsi, %rax
                # b==0.0? (bits & ~sign == 0) -> NaN
                movabsq $0x7fffffffffffffff, %rdx
                andq %rax, %rdx
                testq %rdx, %rdx
                jz .Lv_dmod_nan
                # b NaN ou Inf? (exp==0x7ff) -> NaN
                movq %rax, %rdx
                shrq $52, %rdx
                andl $0x7ff, %edx
                cmpl $0x7ff, %edx
                je .Lv_dmod_nan
                # a Inf ou NaN? (exp==0x7ff) -> NaN
                movq %rdi, %rdx
                shrq $52, %rdx
                andl $0x7ff, %edx
                cmpl $0x7ff, %edx
                je .Lv_dmod_nan
                # q = trunc(a/b); cvttsd2si satura p/ INT64_MIN se |q|>=2^63
                divsd %xmm1, %xmm0               # xmm0 = a/b (exato p/ finitos)
                cvttsd2si %xmm0, %rdx            # q truncado (ou SAT)
                movabsq $0x8000000000000000, %rcx
                cmpq %rcx, %rdx
                je .Lv_dmod_nan                  # |a/b|>=2^63 -> NaN (JVM idem)
                cvtsi2sdq %rdx, %xmm1            # (double)q exato (|q|<2^63)
                movq %rsi, %xmm2
                movq %xmm2, %xmm2
                mulsd %xmm2, %xmm1               # q*b (arredondado 1x)
                movq %rdi, %xmm0                 # a
                subsd %xmm1, %xmm0               # a-q*b
                movq %xmm0, %rax
                ret
            .Lv_dmod_nan:
                movabsq $0x7ff8000000000000, %rax # NaN canônico
                ret

            # kof_double_hash(rdi=bits do double) -> eax = (int)(bits ^ (bits>>32))
            # §114 face hash: Double.hashCode do JVM (o campo Double do record
            # somava 0 na fórmula `31*h + campo`). Bits crus, sem xmm.
            .globl kof_double_hash
            .type kof_double_hash, @function
            kof_double_hash:
                movq %rdi, %rax
                shrq $32, %rax
                xorl %eax, %edi
                movl %edi, %eax
                ret

            # kof_obj_hash(rdi=obj) -> eax = hashCode de CONTEUDO
            # (§114 face hash aninhado). 0 se null/sem hashCode; String por
            # kof_string_hash_code; senão despacho por kof_hashcode_table[type_id].
            # Fica NESTA fatia (não na RuntimeList, que o hello já puxa) para não
            # entrar em todo binário — gate ArtifactSizeTest.
            .globl kof_obj_hash
            .type kof_obj_hash, @function
            kof_obj_hash:
                testq %rdi, %rdi
                jz .Lkoh_zero
                movl (%rdi), %eax
                cmpl $1, %eax
                je .Lkoh_str
                leaq kof_hashcode_table(%rip), %rcx
                movq (%rcx,%rax,8), %rax
                testq %rax, %rax
                jz .Lkoh_zero
                jmp *%rax
            .Lkoh_str:
                jmp kof_string_hash_code
            .Lkoh_zero:
                xorl %eax, %eax
                ret
        """);
    }
}
