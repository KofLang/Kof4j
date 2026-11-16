package dev.kof.compiler.runtime;

/**
 * MATH-D (16/11, #1169 — o split do gate-500 do RuntimeMath): as sete faces
 * double do kof.math NATIVO x86_64 (SSE/SSE4.1 — sem libm). Convenção double:
 * bits crus em rdi (a/v), rsi (b/lo), rdx (hi); retorno bits crus em rax
 * (dsign devolve o Int em rax). A cadeia branchy de min/max/clamp/sign É o
 * JDK EXATO (Math.min/max fontes: NaN → a/v; o caso ±0 por bits crus; o
 * comparador final ≤/≥) — byte-idêntico.
 */
public final class RuntimeMathD {

    private RuntimeMathD() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── MATH-D (16/11, #1169): as sete double ────────────────────
            # Convenção double: bits crus em rdi (a/v), rsi (b/lo), rdx (hi);
            # retorno bits crus em rax (dsign devolve o Int em rax). A cadeia
            # branchy É o JDK EXATO (Math.min/max fontes: NaN → a/v; o caso
            # ±0 por bits crus; o comparador final ≤/≥) — byte-idêntico.

            # kof_math_dabs(rdi=bits) -> rax = |v| (limpa o bit de sinal;
            # -0→+0, NaN payload preservado — Math.abs exato)
            .globl kof_math_dabs
            .type kof_math_dabs, @function
            kof_math_dabs:
                movq %rdi, %rax
                btrq $63, %rax
                ret

            # kof_math_floor(rdi=bits) -> rax = floor(v) (roundsd rdn;
            # NaN/±0/Inf preservados — Math.floor exato)
            .globl kof_math_floor
            .type kof_math_floor, @function
            kof_math_floor:
                movq %rdi, %xmm0
                roundsd $9, %xmm0, %xmm0
                movq %xmm0, %rax
                ret

            # kof_math_ceil(rdi=bits) -> rax = ceil(v) (roundsd rup)
            .globl kof_math_ceil
            .type kof_math_ceil, @function
            kof_math_ceil:
                movq %rdi, %xmm0
                roundsd $10, %xmm0, %xmm0
                movq %xmm0, %rax
                ret

            # kof_math_dmin(rdi=a, rsi=b) -> rax = Math.min(a,b) EXATO:
            # 1) a NaN → a; 2) a±0 && b±0 && b==-0 → a; 3) a<=b ? a : b
            .globl kof_math_dmin
            .type kof_math_dmin, @function
            kof_math_dmin:
                movq %rdi, %xmm0
                ucomisd %xmm0, %xmm0
                jnp .Ldm_a_ok
                movq %rdi, %rax
                ret
            .Ldm_a_ok:
                movq %rsi, %rcx
                addq %rcx, %rcx
                jnz .Ldm_cmp
                movq %rdi, %rcx
                addq %rcx, %rcx
                jnz .Ldm_cmp
                movq %rsi, %rcx
                shrq $63, %rcx
                jz .Ldm_cmp
                movq %rdi, %rax
                ret
            .Ldm_cmp:
                movq %rsi, %xmm1
                ucomisd %xmm1, %xmm0
                ja .Ldm_min_b
                movq %rdi, %rax
                ret
            .Ldm_min_b:
                movq %rsi, %rax
                ret

            # kof_math_dmax(rdi=a, rsi=b) -> rax = Math.max(a,b) EXATO:
            # 1) a NaN → a; 2) a±0 && b±0 && a==-0 → b; 3) a>=b ? a : b
            .globl kof_math_dmax
            .type kof_math_dmax, @function
            kof_math_dmax:
                movq %rdi, %xmm0
                ucomisd %xmm0, %xmm0
                jnp .LdM_a_ok
                movq %rdi, %rax
                ret
            .LdM_a_ok:
                movq %rsi, %rcx
                addq %rcx, %rcx
                jnz .LdM_cmp
                movq %rdi, %rcx
                addq %rcx, %rcx
                jnz .LdM_cmp
                movq %rdi, %rcx
                shrq $63, %rcx
                jz .LdM_cmp
                movq %rsi, %rax
                ret
            .LdM_cmp:
                movq %rsi, %xmm1
                ucomisd %xmm1, %xmm0
                jae .LdM_max_a
                movq %rsi, %rax
                ret
            .LdM_max_a:
                movq %rdi, %rax
                ret

            # kof_math_dclamp(rdi=v, rsi=lo, rdx=hi) -> rax
            # JDK: v < lo ? lo : (v > hi ? hi : v) — NaN em v propaga (as
            # duas comparações falsas → v); NaN em lo/hi → a comparação
            # fica unordered → FALSE → segue a cadeia (o JDK igual)
            .globl kof_math_dclamp
            .type kof_math_dclamp, @function
            kof_math_dclamp:
                movq %rdi, %xmm0
                movq %rsi, %xmm1
                movq %rdx, %xmm2
                ucomisd %xmm0, %xmm0
                jnp .Ldc_v_ok
                movq %rdi, %rax
                ret
            .Ldc_v_ok:
                # #1169 (medido 16/11): `ucomisd %xmm0, %xmm1` compara xmm1(lo)
                # CONTRA xmm0(v) — CF=1 quando lo < v. O salto para a checagem
                # do hi tem de acontecer quando v >= lo (lo <= v), i.e. CF=0 OU
                # ZF=1 = `jae`; a versão anterior usava `jae` lendo-o como
                # "v >= lo" mas com os operandos trocados ele só saltava quando
                # lo >= v — o clamp devolvia o LO para qualquer v acima dele
                # (clamp(5.0,0.0,3.0) → 0.0 em vez de 3.0). Troca os operandos
                # para a ordem legível `v ? lo`: ucomisd %xmm1, %xmm0 → CF=1
                # quando v < lo → `jb .Ldc_ret_lo`; senão segue para o hi.
                ucomisd %xmm1, %xmm0
                jp .Ldc_lo_nan          # lo NaN → unordered → segue
                jb .Ldc_ret_lo          # v < lo → devolve lo
                jmp .Ldc_hi
            .Ldc_ret_lo:
                movq %rsi, %rax
                ret
            .Ldc_lo_nan:
                ucomisd %xmm0, %xmm2
                jp .Ldc_ret_v
                ja .Ldc_hi_ret
                jmp .Ldc_ret_v
            .Ldc_hi:
                # v > hi → devolve hi (`ucomisd %xmm2, %xmm0`: CF=1 quando
                # v < hi; JA = CF=0 e ZF=0 = v > hi)
                ucomisd %xmm2, %xmm0
                jp .Ldc_ret_v
                ja .Ldc_hi_ret
                jmp .Ldc_ret_v
            .Ldc_ret_v:
                movq %rdi, %rax
                ret
            .Ldc_hi_ret:
                movq %rdx, %rax
                ret

            # kof_math_dsign(rdi=bits) -> rax = Int (NaN → 0; v<0 → -1;
            # v>0 → 1; ±0 → 0 — a cadeia carregada exata)
            .globl kof_math_dsign
            .type kof_math_dsign, @function
            kof_math_dsign:
                movq %rdi, %xmm0
                ucomisd %xmm0, %xmm0
                jnp .Lds_v_ok
                xorl %eax, %eax
                ret
            .Lds_v_ok:
                pxor %xmm1, %xmm1
                ucomisd %xmm1, %xmm0
                jp .Lds_zero
                jb .Lds_neg
                ja .Lds_pos
            .Lds_zero:
                xorl %eax, %eax
                ret
            .Lds_neg:
                movq $-1, %rax
                ret
            .Lds_pos:
                movq $1, %rax
                ret

        """);
    }
}
