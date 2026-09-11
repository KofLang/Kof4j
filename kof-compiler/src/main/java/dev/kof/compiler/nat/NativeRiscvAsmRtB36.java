package dev.kof.compiler.nat;

// FASE (STDLIB S1b/S1b.1, MATH001): fatia 36 de RISCV_RUNTIME_ASM_B — escalares
// Double puros de kof.math no cross (sqrt/lerp/percentage/isInteger/isDecimal).
// Transcrição fiel da spec x86 (runtime/RuntimeMath.java, série SSE2 sem libm):
// args/retorno = BITS CRUS de double em a0..aN / a0 (mesmo modelo "rax cru" do
// x86 — o generic path cross dá push/pop de 8 bytes, zero mudança no call site).
//
//   sqrt        fsqrt.d (NaN em <0 — paridade Math.sqrt/IEEE)
//   lerp(a,b,t) a+(b-a)*t        (ordem idêntica JVM/JS/x86: fsub/fmul/fadd)
//   percentage  part/total*100.0 (fdiv + mul por 0x4059000000000000 via li+fmv.d.x)
//   isInteger   exp==0x7ff(NaN/Inf)=>0; exp>=0x433(|v|>=2^52)=>1; senão trunc==v
//               (fcvt.l.d rtz + fcvt.d.l + feq.d; os casos NaN/Inf já foram
//                desviados pelo guard de exp, então feq == a igualdade x86)
//   isDecimal   !isInteger (xor 1)
//
// Convenção: helpers/fns globais `kof_math_*`; label local namespace `.Ls36*`
// (nunca colidir — lição B32/bug95). `.section .text` NO TOPO (7be4fd0a).
// Estado vivo só em t-regs (folha) — nenhum call que clobber. `feq.d`/`srli`/
// `andi 0x7ff`/`bgeu`/`li`-grande/`xor` todos cobertos pelo tradutor (verificado
// com qemu). fsqrt.d + fcvt.l.d-direção foram consertados no NativeAarch64Translator.
public final class NativeRiscvAsmRtB36 {

    private NativeRiscvAsmRtB36() {}

    static final String RISCV_RUNTIME_ASM_B_36 = """

            .section .text
            .globl kof_math_sqrt
            kof_math_sqrt:
                fmv.d.x f0, a0
                fsqrt.d f0, f0
                fmv.x.d a0, f0
                ret

            .globl kof_math_lerp
            kof_math_lerp:
                fmv.d.x f0, a0             # a
                fmv.d.x f1, a1             # b
                fmv.d.x f2, a2             # t
                fsub.d f1, f1, f0          # b-a
                fmul.d f1, f1, f2          # (b-a)*t
                fadd.d f0, f0, f1          # a+...
                fmv.x.d a0, f0
                ret

            .globl kof_math_percentage
            kof_math_percentage:
                fmv.d.x f0, a0             # part
                fmv.d.x f1, a1             # total
                fdiv.d f0, f0, f1          # part/total
                li   t1, 4636737291354636288   # 0x4059000000000000 (100.0 — bits
                                        #  exatos do IEEE-754)
                fmv.d.x f1, t1
                fmul.d f0, f0, f1          # *100.0
                fmv.x.d a0, f0
                ret

            .globl kof_math_isInteger
            kof_math_isInteger:
                fmv.d.x f0, a0
                srli t1, a0, 52
                li   t2, 2047              # 0x7ff
                and  t1, t1, t2            # exp = (bits>>52)&0x7ff (x86 faz
                                           #  shrq+andl). O AND é OBRIGATÓRIO:
                                           #  srli traz o bit de sign junto
                                           #  (p.ex. -0.5 -> 0xBFE >= 0x433
                                           #  viraria inteiro; -Inf -> 0xFFF
                                           #  != 0x7ff não pegaria NaN/Inf).
                beq  t1, t2, .Ls36_f         # NaN/Inf => não-inteiro
                li   t2, 1075             # 0x433 (|v| >= 2^52, finito)
                bgeu t1, t2, .Ls36_t
                fcvt.l.d t3, f0, rtz       # trunc(v)
                fcvt.d.l f2, t3            # roundtrip
                feq.d a0, f0, f2           # 1 se igual (NaN já excluído)
                ret
            .Ls36_t:
                li   a0, 1
                ret
            .Ls36_f:
                li   a0, 0
                ret

            .globl kof_math_isDecimal
            kof_math_isDecimal:
                # NAO-FOlha: riscv `call` = jal ra — sobrescreve ra; sem
                # salvar, o `ret` do isDecimal saltaria p/ depois do call
                # interno (laço infinito — pego no qemu 11/09). x86 empurra
                # o ret-addr p/ pilha automaticamente; aqui é registro.
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_math_isInteger
                li   t0, 1
                xor  a0, a0, t0
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            """;
}
