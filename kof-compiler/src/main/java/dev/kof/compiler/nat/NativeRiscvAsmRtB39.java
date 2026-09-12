package dev.kof.compiler.nat;

/**
 * Fatia B39 — §107: toString de coleção (List/Set/Map) para riscv64.
 * Port 1:1 do {@code RuntimeCollectionToString} x86_64: mesmos helpers,
 * mesma ABI (a0=container, a1=tag do elem; Map: a1=tag chave, a2=tag
 * valor) e MESMA semântica de tag (0=int/char/short/byte, 1=String,
 * 2=Long, 3=Bool, 6=desconhecido/record/aninhado → "?"). Double/Float
 * (tags 4/5) são barrados em tempo de compilação pelo dispatch cross
 * (FLT001 — mesma recusa do valueOf escalar, sem snprintf no asm puro).
 *
 * <p>Disciplina de frame: os helpers do runtime riscv salvam SUBCONJUNTOS
 * INCONSISTENTES dos callee-saved ({@code kof_string_from_literal} preserva
 * s0,s1,s3; {@code kof_int_to_string} preserva s0,s1,s3,s4,s5; cada um
 * CLOBBER o resto), e todos usam t0..t6 livremente. Nenhum registrador é
 * confiável ATRAVÉS de um `call` — todo estado do laço (container, tag,
 * size, acc, sep, i, keyStr) vive em SLOTS DO PRÓPRIO FRAME e é recarregado
 * a cada bloco. Não há GC no riscv (bump-pointer), então o slot não precisa
 * ser "raiz" — só sobreviver ao clobber. aarch64 herda via tradutor (li/mv/
 * sd/ld/lw/sw/beqz/bnez/blt/bge/beq/bne/j/call/ret/la/slli/neg/rem/todos
 * cobertos; diretivas .passam verbatim).
 */
final class NativeRiscvAsmRtB39 {

    private NativeRiscvAsmRtB39() {}

    static final String RISCV_RUNTIME_ASM_B_39 = """
            .section .rodata
            .align 3
            .Lc2s_lbr:   .ascii "["
            .Lc2s_rbr:   .ascii "]"
            .Lc2s_lcur:  .ascii "{"
            .Lc2s_rcur:  .ascii "}"
            .Lc2s_comma: .ascii ", "
            .Lc2s_eq:    .ascii "="
            .Lc2s_q:     .ascii "?"

            .section .text

            # kof_elem_to_string(a0=&slot, a1=tag) -> a0 String*
            # tag 0=int/char/short/byte (word), 1=String (ponteiro), 2=Long
            # (doubleword), 3=Bool; QUALQUER outra (record/aninhado/FP que
            # o dispatcher já barraria) → "?" — recusa honesta, nunca lixo.
            # Guarda `ra`: os ramos 0/2/3 e o `?` fazem `call` (o call esmaga
            # ra — sem save/restore o ret voltaria p/ lixo; só tag 1, que é
            # puro ld, não chama).
            .globl kof_elem_to_string
            kof_elem_to_string:
                addi sp, sp, -16
                sd   ra, 8(sp)
                li   t0, 1
                beq  a1, t0, .Lce_str
                li   t0, 2
                beq  a1, t0, .Lce_long
                li   t0, 3
                beq  a1, t0, .Lce_bool
                li   t0, 0
                beq  a1, t0, .Lce_int
                j    .Lce_q
            .Lce_int:
                lw   a0, 0(a0)
                call kof_int_to_string
                j    .Lce_ret
            .Lce_str:
                ld   a0, 0(a0)
                j    .Lce_ret
            .Lce_long:
                ld   a0, 0(a0)
                call kof_int_to_string
                j    .Lce_ret
            .Lce_bool:
                lw   a0, 0(a0)
                call kof_bool_to_string
                j    .Lce_ret
            .Lce_q:
                la   a0, .Lc2s_q
                li   a1, 1
                call kof_string_from_literal
            .Lce_ret:
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_list_to_string / kof_set_to_string (a0=container, a1=tag)
            # -> a0 String* "[e1, e2, ...]". Set É um List (kof_set_new =
            # j kof_list_new, forma 100) — os dois partilham o asm.
            # Frame (72B): 0=container 8=tag 12=size 16=acc 24=i 32=sep
            # 40=elemStr 64=ra. Estado do laço NUNCA em registrador (lição
            # do doc: helpers riscv clobberam s-regs inconsistentes).
            .globl kof_list_to_string
            .globl kof_set_to_string
            kof_list_to_string:
            kof_set_to_string:
                addi sp, sp, -72
                sd   ra, 64(sp)
                sd   a0, 0(sp)           # container
                sw   a1, 8(sp)           # tag
                lw   t0, 16(a0)
                sw   t0, 12(sp)          # size
                sd   zero, 24(sp)        # i = 0
                la   a0, .Lc2s_lbr
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 16(sp)          # acc = "["
                lw   t0, 12(sp)
                beqz t0, .Lcl_fin
            .Lcl_loop:
                ld   t1, 24(sp)          # i
                beqz t1, .Lcl_sep0
                la   a0, .Lc2s_comma
                li   a1, 2
                call kof_string_from_literal
                sd   a0, 32(sp)          # sep = ", "
                j    .Lcl_haveSep
            .Lcl_sep0:
                la   a0, .Lc2s_lbr       # ponteiro qualquer + LEN 0 = ""
                li   a1, 0
                call kof_string_from_literal
                sd   a0, 32(sp)          # sep = ""
            .Lcl_haveSep:
                ld   a0, 0(sp)           # container
                ld   t0, 24(sp)          # i
                ld   t2, 24(a0)          # data
                slli t0, t0, 3
                add  a0, t2, t0          # &data[i]
                lw   a1, 8(sp)           # tag
                call kof_elem_to_string
                sd   a0, 40(sp)          # elemStr
                ld   a0, 32(sp)          # sep
                ld   a1, 40(sp)          # elemStr
                call kof_string_concat   # sep + elem
                sd   a0, 40(sp)
                ld   a0, 16(sp)          # acc
                ld   a1, 40(sp)
                call kof_string_concat   # acc + (sep+elem)
                sd   a0, 16(sp)          # acc
                ld   t0, 24(sp)
                addi t0, t0, 1
                sd   t0, 24(sp)
                ld   t0, 24(sp)
                lw   t1, 12(sp)
                blt  t0, t1, .Lcl_loop
            .Lcl_fin:
                la   a0, .Lc2s_rbr
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 40(sp)          # "]"
                ld   a0, 16(sp)          # acc
                ld   a1, 40(sp)
                call kof_string_concat   # acc + "]"
                ld   ra, 64(sp)
                addi sp, sp, 72
                ret

            # kof_map_to_string (a0=map, a1=tag chave, a2=tag valor)
            # -> a0 String* "{k=v, ...}". Ordem de ARMAZENAMENTO (inserção)
            # — vetor linear vs buckets de hash do JVM (divergência de
            # arquitetura registrada §107; single-entry idêntico).
            # Frame (88B): 0=container 8=tagChave 12=tagValor 16=size
            # 24=acc 32=i 40=sep 48=keyStr 80=ra.
            .globl kof_map_to_string
            kof_map_to_string:
                addi sp, sp, -88
                sd   ra, 80(sp)
                sd   a0, 0(sp)           # container
                sw   a1, 8(sp)           # tag chave
                sw   a2, 12(sp)          # tag valor
                lw   t0, 16(a0)
                sw   t0, 16(sp)          # size
                sd   zero, 32(sp)        # i = 0
                la   a0, .Lc2s_lcur
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 24(sp)          # acc = "{"
                lw   t0, 16(sp)
                beqz t0, .Lcm_fin
            .Lcm_loop:
                ld   t1, 32(sp)          # i
                beqz t1, .Lcm_sep0
                la   a0, .Lc2s_comma
                li   a1, 2
                call kof_string_from_literal
                sd   a0, 40(sp)          # sep = ", "
                j    .Lcm_haveSep
            .Lcm_sep0:
                la   a0, .Lc2s_lcur
                li   a1, 0
                call kof_string_from_literal
                sd   a0, 40(sp)          # sep = ""
            .Lcm_haveSep:
                ld   t2, 0(sp)           # container
                ld   t0, 32(sp)          # i
                slli t0, t0, 3
                ld   t3, 24(t2)          # keys ptr
                add  a0, t3, t0          # &keys[i]
                lw   a1, 8(sp)
                call kof_elem_to_string
                sd   a0, 48(sp)          # keyStr
                la   a0, .Lc2s_eq
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 56(sp)          # "="
                ld   a0, 48(sp)          # keyStr
                ld   a1, 56(sp)
                call kof_string_concat   # keyStr + "="
                sd   a0, 48(sp)          # "chave="
                ld   t2, 0(sp)
                ld   t0, 32(sp)
                slli t0, t0, 3
                ld   t3, 32(t2)          # vals ptr
                add  a0, t3, t0          # &vals[i]
                lw   a1, 12(sp)          # tag valor
                call kof_elem_to_string
                sd   a0, 56(sp)          # valStr
                ld   a0, 48(sp)          # "chave="
                ld   a1, 56(sp)
                call kof_string_concat   # "chave=valor"
                sd   a0, 48(sp)
                ld   a0, 40(sp)          # sep
                ld   a1, 48(sp)
                call kof_string_concat   # sep + "chave=valor"
                sd   a0, 48(sp)
                ld   a0, 24(sp)          # acc
                ld   a1, 48(sp)
                call kof_string_concat   # acc + ...
                sd   a0, 24(sp)          # acc
                ld   t0, 32(sp)
                addi t0, t0, 1
                sd   t0, 32(sp)
                ld   t0, 32(sp)
                lw   t1, 16(sp)
                blt  t0, t1, .Lcm_loop
            .Lcm_fin:
                la   a0, .Lc2s_rcur
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 48(sp)
                ld   a0, 24(sp)
                ld   a1, 48(sp)
                call kof_string_concat
                ld   ra, 80(sp)
                addi sp, sp, 88
                ret
            """;
}
