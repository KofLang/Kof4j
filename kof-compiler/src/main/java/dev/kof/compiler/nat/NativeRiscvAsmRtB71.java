package dev.kof.compiler.nat;

// S5.3 (db-parity-plan, gaps-db lane, 24/09): bind client-side do wire MySQL
// no cross — `kof_db_mysql_render` (valor -> literal SQL) +
// `kof_db_mysql_replace_q` (troca o 1o `?` pelo literal). Port de
// RuntimeDb1 (`kof_db_mysql_render` + cauda em RuntimeDb2) e RuntimeDb2
// (`kof_db_mysql_replace_q`) do x86 para riscv64; aarch64 herda via tradutor.
// É o caminho de fallback do x86 (`.Ldb_exec_subst` em RuntimeDb4/Db5):
// COM_QUERY nao suporta `?`, entao cada bind vira literal e o `?` é
// substituido em sequência.
//
// Contrato riscv (mirror do x86):
//   kof_db_mysql_render(a0=val) -> a0 = literal (KofString)
//     Int -> decimais via kof_int_to_string; KofString -> `'escaped'`
//     (MySQL: `'` -> `''` e `\` -> `\\`, com aspas externas).
//   kof_db_mysql_replace_q(a0=sql, a1=literal) -> a0 = sql novo (KofString)
//     troca o 1o `?` pelo literal; sem `?` devolve o sql inalterado.
//
// Divergência honesta vs x86 (mesma postura da B47, cabeçalho lá): o x86
// classifica Int x String pelo limite `0x1000000` do mmap; o cross usa a
// JANELA DO HEAP (`[_kof_heap, kof_alloc_ptr)`) — exata para todo ponteiro
// vivo. Efeito colateral medido: Int negativo no x86 cai no ramo string
// (comparacao unsigned); no cross cai no ramo int (correto, via
// kof_int_to_string). Sem scratch de 4096 no stack como o x86: o tamanho
// escapado é medido antes do alloc, então qualquer comprimento cabe.
public final class NativeRiscvAsmRtB71 {

    private NativeRiscvAsmRtB71() {}

    static String RISCV_RUNTIME_ASM_B_71 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_render(a0=val) -> a0 = literal SQL (KofString)
            # ---------------------------------------------------------------
            .globl kof_db_mysql_render
            .type kof_db_mysql_render, @function
            kof_db_mysql_render:
                li   t6, 64
                sub  sp, sp, t6
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                sd   s5, 48(sp)
                mv   s0, a0
                la   t0, kof_alloc_ptr
                ld   t0, 0(t0)
                la   t1, _kof_heap
                bltu s0, t1, .L71_rnd_int
                bgeu s0, t0, .L71_rnd_int
                # #773: ponteiro vivo pode ser um §284 MAGIC box (arg erased via
                # Integer.valueOf) — desembrulha o valor (+16) e renderiza int.
                li   t1, 0x4B4F46425F425801
                ld   t2, 0(s0)
                bne  t1, t2, .L71_rnd_str
                ld   s0, 16(s0)
                j    .L71_rnd_int
            .L71_rnd_str:
                lw   s1, 16(s0)
                addi s2, s0, 24
                li   s3, 0
                li   s4, 0
            .L71_esc_cnt:
                bge  s4, s1, .L71_esc_done
                add  t0, s2, s4
                lbu  t1, 0(t0)
                addi s3, s3, 1
                li   t2, 39
                beq  t1, t2, .L71_esc_extra
                li   t2, 92
                bne  t1, t2, .L71_esc_next
            .L71_esc_extra:
                addi s3, s3, 1
            .L71_esc_next:
                addi s4, s4, 1
                j    .L71_esc_cnt
            .L71_esc_done:
                addi s3, s3, 2
                addi a0, s3, 25
                call kof_alloc
                mv   s5, a0
                li   t0, 1
                sw   t0, 0(s5)
                sw   zero, 4(s5)
                sd   zero, 8(s5)
                sw   s3, 16(s5)
                sw   zero, 20(s5)
                addi t0, s5, 24
                li   t1, 39
                sb   t1, 0(t0)
                addi s4, s5, 25
                li   t0, 0
            .L71_fill:
                bge  t0, s1, .L71_fill_done
                add  t1, s2, t0
                lbu  t1, 0(t1)
                li   t2, 39
                beq  t1, t2, .L71_dbl
                li   t2, 92
                beq  t1, t2, .L71_dbl
                sb   t1, 0(s4)
                addi s4, s4, 1
                j    .L71_fill_next
            .L71_dbl:
                sb   t1, 0(s4)
                addi s4, s4, 1
                sb   t1, 0(s4)
                addi s4, s4, 1
            .L71_fill_next:
                addi t0, t0, 1
                j    .L71_fill
            .L71_fill_done:
                li   t1, 39
                sb   t1, 0(s4)
                addi s4, s4, 1
                sb   zero, 0(s4)
                mv   a0, s5
                j    .L71_rnd_out
            .L71_rnd_int:
                mv   a0, s0
                call kof_int_to_string
            .L71_rnd_out:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                ld   s3, 32(sp)
                ld   s4, 40(sp)
                ld   s5, 48(sp)
                li   t6, 64
                add  sp, sp, t6
                ret
            # ---------------------------------------------------------------
            # kof_db_mysql_replace_q(a0=sql, a1=literal) -> a0 = sql novo
            # ---------------------------------------------------------------
            .globl kof_db_mysql_replace_q
            .type kof_db_mysql_replace_q, @function
            kof_db_mysql_replace_q:
                li   t6, 80
                sub  sp, sp, t6
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                sd   s5, 48(sp)
                sd   s6, 56(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                addi s3, s0, 24
                lw   s4, 16(s1)
                addi s5, s1, 24
                li   t0, 0
            .L71_scan:
                bge  t0, s2, .L71_noq
                add  t1, s3, t0
                lbu  t1, 0(t1)
                li   t2, 63
                beq  t1, t2, .L71_found
                addi t0, t0, 1
                j    .L71_scan
            .L71_noq:
                mv   a0, s0
                j    .L71_rq_out
            .L71_found:
                sd   t0, 64(sp)
                sub  t1, s2, t0
                addi t1, t1, -1
                add  t2, t0, s4
                add  t2, t2, t1
                sd   t2, 72(sp)
                addi a0, t2, 25
                call kof_alloc
                mv   s6, a0
                li   t0, 1
                sw   t0, 0(s6)
                sw   zero, 4(s6)
                sd   zero, 8(s6)
                ld   t0, 72(sp)
                sw   t0, 16(s6)
                sw   zero, 20(s6)
                addi t3, s6, 24
                ld   t0, 64(sp)
                li   t1, 0
            .L71_c1:
                bge  t1, t0, .L71_c1_done
                add  t2, s3, t1
                lbu  t2, 0(t2)
                add  t4, t3, t1
                sb   t2, 0(t4)
                addi t1, t1, 1
                j    .L71_c1
            .L71_c1_done:
                add  t3, t3, t0
                li   t1, 0
            .L71_c2:
                bge  t1, s4, .L71_c2_done
                add  t2, s5, t1
                lbu  t2, 0(t2)
                add  t4, t3, t1
                sb   t2, 0(t4)
                addi t1, t1, 1
                j    .L71_c2
            .L71_c2_done:
                add  t3, t3, s4
                ld   t0, 64(sp)
                addi t0, t0, 1
            .L71_c3:
                bge  t0, s2, .L71_c3_done
                add  t1, s3, t0
                lbu  t1, 0(t1)
                sb   t1, 0(t3)
                addi t3, t3, 1
                addi t0, t0, 1
                j    .L71_c3
            .L71_c3_done:
                sb   zero, 0(t3)
                mv   a0, s6
            .L71_rq_out:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                ld   s3, 32(sp)
                ld   s4, 40(sp)
                ld   s5, 48(sp)
                ld   s6, 56(sp)
                li   t6, 80
                add  sp, sp, t6
                ret
            .section .text
            """;
}
