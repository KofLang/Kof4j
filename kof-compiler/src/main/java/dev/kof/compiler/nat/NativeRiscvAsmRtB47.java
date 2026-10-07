package dev.kof.compiler.nat;

// DB001 fatia 1 (15/09, dono = 192.168.100.18): runtime kof.db SQLite no
// cross riscv64/aarch64 (aarch64 herda via tradutor). Port do subconjunto
// SQLite do x86 — RuntimeDb2 (resolve/type/connect), RuntimeDb4
// (close/transaction/bind). O dispatch de execute/query (com o ramo mysql,
// fatia S5.4-2) vive na peça B47b; o connect aceita "sqlite:*" aqui e delega
// mysql:// / mariadb:// para a peça B73 (wire TCP real, registra o fd como
// type 2 nas tabelas .Ldb_* daqui); outro esquema vira DB001 em B73.
//
// Divergências honestas vs x86 (catalogadas, não silenciosas):
// - kof_db_bind classifica Int×String pela JANELA DO HEAP cross
//   ([_kof_heap, kof_alloc_ptr)) — o x86 usa o limite 0x1000000 do mmap.
//   Um Int cujo valor caia dentro da janela (256KB) seria classificado
//   String; em troca a janela é exata para todo ponteiro vivo.
// - NULL em coluna de query vira o literal JSON `null` (o x86 anexa string
//   VAZIA — make_string len=0 — o que produz JSON inválido; face nunca
//   exercida pelos E2E, que só consultam colunas não-nulas).
// - .Ldb_slots/.Ldb_tx_handle são globais (não TLS como o x86 pós-§129):
//   transação concorrente em workers diferentes compartilha o mesmo slot de
//   tx — residual conhecido do runtime db cross (não é o §129; a cadeia de
//   handlers já é por-TID desde o port de 19/09).
//
// O className literal (último arg de kof_db_queryN, empilhado pelo frontend
// — ExpressionDbCallLowerer) chega em a(2+N) e é DESCARTADO por não-uso —
// mesma convenção do x86 (KOF_DB_QUERY_N nunca o lê; stack balanceada).
public final class NativeRiscvAsmRtB47 {

    private NativeRiscvAsmRtB47() {}

    static final String RISCV_RUNTIME_ASM_B_47;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                .section .rodata
                .Ldb_null: .asciz "null"
                # #773: MAGIC do box §284 — kof_db_bind desembrulha o VALOR de um
                # arg erased (Integer.valueOf do db.execute) em vez de bind_text.
                .p2align 3
                .Ldb_bind_magic: .8byte 0x4B4F46425F425801
                .section .bss
                .align 3
                .Ldb_slots: .zero 512
                .Ldb_types: .zero 64
                .Ldb_count: .quad 0
                # handle (KofString*) da conexão "default" — o que transaction usa.
                .Ldb_default_handle: .quad 0
                # bug 78: handle da conexão COM transação ativa (equivalente do
                # ThreadLocal KOF_DB_TX do JvmConfigRuntime) — bloco transaction
                # aninhado NA MESMA conexão não BEGIN/COMMIT/ROLLBACK.
                .Ldb_tx_handle: .quad 0
                .section .text

                # kof_db_resolve(handle@a0) -> sqlite3* (0 se null). Port Db2:206.
                # Handle = "db<N>" — dígitos em data+2 (offset 26).
                kof_db_resolve:
                    beqz a0, .Lres_null
                    addi t0, a0, 26
                    li   t1, 0
                .Lres_parse:
                    lbu  t2, 0(t0)
                    beqz t2, .Lres_done
                    addi t2, t2, -48
                    li   t3, 10
                    mul  t1, t1, t3
                    add  t1, t1, t2
                    addi t0, t0, 1
                    j    .Lres_parse
                .Lres_done:
                    addi t1, t1, -1
                    la   t0, .Ldb_slots
                    slli t1, t1, 3
                    add  t0, t0, t1
                    ld   a0, 0(t0)
                    ret
                .Lres_null:
                    li   a0, 0
                    ret

                # kof_db_type(handle@a0) -> 1=sqlite (0 se null). Port Db2:229.
                kof_db_type:
                    beqz a0, .Ltyp_null
                    addi t0, a0, 26
                    li   t1, 0
                .Ltyp_parse:
                    lbu  t2, 0(t0)
                    beqz t2, .Ltyp_done
                    addi t2, t2, -48
                    li   t3, 10
                    mul  t1, t1, t3
                    add  t1, t1, t2
                    addi t0, t0, 1
                    j    .Ltyp_parse
                .Ltyp_done:
                    addi t1, t1, -1
                    la   t0, .Ldb_types
                    add  t0, t0, t1
                    lbu  a0, 0(t0)
                    ret
                .Ltyp_null:
                    li   a0, 0
                    ret

                # kof_db_connect(url@a0) — "sqlite:*" abre via sqlite3_open;
                # "mysql://"/"mariadb://" delegam ao kof_db_connect_mysql (B73,
                # S5.4); qualquer outro esquema vira DB001 nomeado na B73.
                .globl kof_db_connect
                kof_db_connect:
                    # sem userinfo na assinatura: user2/pass2 = 0 (host-only
                    # cai no parser da B73 com as credenciais vazias).
                    li   a1, 0
                    li   a2, 0
                    j    .Lconn_go

                # kof_db_connect2(url@a0, user@a1, pass@a2) — userinfo explícito
                # (forma host-only, usado pelo ORM/driver); mesma rota da B73.
                .globl kof_db_connect2
                kof_db_connect2:
                .Lconn_go:
                    addi sp, sp, -128
                    sd   ra, 120(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    sd   s3, 32(sp)
                    sd   s4, 40(sp)
                    # prefixo "sqlite:" em data[0..6] (offsets 24..30)
                    lbu  t0, 24(a0)
                    li   t1, 115
                    bne  t0, t1, .Lconn_other
                    lbu  t0, 25(a0)
                    li   t1, 113
                    bne  t0, t1, .Lconn_other
                    lbu  t0, 26(a0)
                    li   t1, 108
                    bne  t0, t1, .Lconn_other
                    lbu  t0, 27(a0)
                    li   t1, 105
                    bne  t0, t1, .Lconn_other
                    lbu  t0, 28(a0)
                    li   t1, 116
                    bne  t0, t1, .Lconn_other
                    lbu  t0, 29(a0)
                    li   t1, 101
                    bne  t0, t1, .Lconn_other
                    lbu  t0, 30(a0)
                    li   t1, 58
                    bne  t0, t1, .Lconn_other
                    # sqlite3_open(data+7, &slot@96(sp))
                    sd   zero, 96(sp)
                    addi a0, a0, 31
                    addi a1, sp, 96
                    call sqlite3_open
                    bnez a0, .Lconn_bad
                    ld   s0, 96(sp)
                    # registra o slot: slots[count]=db, types[count]=1, count++
                    la   t0, .Ldb_count
                    ld   t1, 0(t0)
                    li   t2, 63
                    bge  t1, t2, .Lconn_bad
                    la   t2, .Ldb_slots
                    slli t3, t1, 3
                    add  t2, t2, t3
                    sd   s0, 0(t2)
                    la   t2, .Ldb_types
                    add  t2, t2, t1
                    li   t3, 1
                    sb   t3, 0(t2)
                    addi t1, t1, 1
                    la   t2, .Ldb_count
                    sd   t1, 0(t2)
                    # handle "db<N>" — dígitos ao contrário em buffer de 48B @48(sp)
                    addi t2, sp, 95
                    li   t3, 10
                .Lconn_itoa:
                    remu t4, t1, t3
                    addi t4, t4, 48
                    sb   t4, 0(t2)
                    divu t1, t1, t3
                    beqz t1, .Lconn_itoa_done
                    addi t2, t2, -1
                    j    .Lconn_itoa
                .Lconn_itoa_done:
                    mv   s1, t2              # última posição escrita (dígito)
                    addi t2, t2, -1
                    li   t4, 98              # 'b'
                    sb   t4, 0(t2)
                    addi t2, t2, -1
                    li   t4, 100             # 'd'
                    sb   t4, 0(t2)           # t2 = início do handle
                    addi t4, s1, 1
                    sub  t4, t4, t2          # len
                    mv   s2, t2
                    mv   s3, t4
                    # KofString: alloc(25+len), header, copia, NUL
                    addi a0, s3, 25
                    call kof_alloc
                    mv   s4, a0
                    li   t0, 1
                    sw   t0, 0(s4)
                    sw   zero, 4(s4)
                    sd   zero, 8(s4)
                    sw   s3, 16(s4)
                    sw   zero, 20(s4)
                    addi a0, s4, 24
                    mv   a1, s2
                    mv   a2, s3
                    call kof_memcpy
                    addi t0, s4, 24
                    add  t0, t0, s3
                    sb   zero, 0(t0)
                    # conexão atual = default (tx)
                    la   t0, .Ldb_default_handle
                    sd   s4, 0(t0)
                    mv   a0, s4
                    j    .Lconn_out
                .Lconn_other:
                    # mysql:// | mariadb:// -> B73; outro -> DB001 nomeado lá.
                    call kof_db_connect_mysql
                    j    .Lconn_out
                .Lconn_bad:
                    li   a0, 0
                .Lconn_out:
                    ld   s4, 40(sp)
                    ld   s3, 32(sp)
                    ld   s2, 24(sp)
                    ld   s1, 16(sp)
                    ld   s0, 8(sp)
                    ld   ra, 120(sp)
                    addi sp, sp, 128
                    ret

                # kof_db_close(id@a0). Port Db4:19 + fechamento do fd mysql
                # (type 2, S5.4 fatia 2) — sqlite3_close vs kof_plat_close.
                .globl kof_db_close
                kof_db_close:
                    beqz a0, .Lclose_zero
                    addi sp, sp, -16
                    sd   ra, 8(sp)
                    sd   s0, 0(sp)
                    mv   s0, a0
                    call kof_db_type
                    li   t0, 2
                    beq  a0, t0, .Lclose_mysql
                    li   t0, 1
                    bne  a0, t0, .Lclose_done
                    mv   a0, s0
                    call kof_db_resolve
                    beqz a0, .Lclose_done
                    call sqlite3_close
                    j    .Lclose_done
                .Lclose_mysql:
                    mv   a0, s0
                    call kof_db_resolve
                    beqz a0, .Lclose_done
                    call kof_plat_close
                .Lclose_done:
                    ld   s0, 0(sp)
                    ld   ra, 8(sp)
                    addi sp, sp, 16
                .Lclose_zero:
                    ret

                # kof_db_bind(stmt@a0, idx@a1, val@a2): val na janela do heap
                # [_kof_heap, kof_alloc_ptr) => KofString* (bind_text), senão
                # Int cru (bind_int). Ver cabeçalho (divergência vs x86).
                kof_db_bind:
                    addi sp, sp, -32
                    sd   ra, 24(sp)
                    la   t0, kof_alloc_ptr
                    ld   t0, 0(t0)
                    la   t1, _kof_heap
                    bltu a2, t1, .Lbind_int
                    bgeu a2, t0, .Lbind_int
                    # #773: ponteiro vivo pode ser um §284 MAGIC box (arg erased
                    # do db.execute passou por Integer.valueOf) — desembrulha o
                    # valor (+16) e bind_int; paridade com o autobox JVM.
                    la   t1, .Ldb_bind_magic
                    ld   t1, 0(t1)
                    ld   t2, 0(a2)
                    bne  t1, t2, .Lbind_str
                    ld   a2, 16(a2)
                    j    .Lbind_int
                .Lbind_str:
                    sd   s0, 8(sp)
                    sd   s1, 0(sp)
                    mv   s0, a0
                    mv   s1, a2
                    mv   a0, s0
                    addi a2, s1, 24
                    li   a3, -1
                    li   a4, -1
                    call sqlite3_bind_text
                    ld   s1, 0(sp)
                    ld   s0, 8(sp)
                    j    .Lbind_out
                .Lbind_int:
                    call sqlite3_bind_int
                .Lbind_out:
                    ld   ra, 24(sp)
                    addi sp, sp, 32
                    ret

                # kof_db_transaction(task@a0): BEGIN; lambda; COMMIT (ou
                # ROLLBACK + re-throw). Port Db4:63. Frame de try de 48B com o
                # layout do KofTryStart do emitter (0/8/16/24 = unwinder) e o
                # flag `nested` no slot 32 (mesmo truque do x86 — a lambda
                # pode clobberar registradores; o frame não).
                .globl kof_db_transaction
                kof_db_transaction:
                    addi sp, sp, -112
                    sd   ra, 96(sp)
                    sd   s0, 80(sp)
                    sd   s1, 88(sp)
                    mv   s0, a0              # task (lambda)
                    la   t0, .Ldb_default_handle
                    ld   t0, 0(t0)           # c
                    la   t1, .Ldb_tx_handle
                    ld   t1, 0(t1)
                    li   t3, 0               # nested = 0
                    beqz t1, .Ltx_store_nested
                    bne  t1, t0, .Ltx_store_nested
                    li   t3, 1               # nested = 1
                .Ltx_store_nested:
                    sw   t3, 32(sp)
                    # BEGIN (só se !nested && c != 0) + marca tx ativa
                    bnez t3, .Ltx_begin_done
                    beqz t0, .Ltx_begin_done
                    mv   a0, t0
                    la   a1, .Ldb_begin_str
                    call kof_db_execute
                    la   t2, .Ldb_default_handle
                    ld   t2, 0(t2)
                    la   t3, .Ldb_tx_handle
                    sd   t2, 0(t3)
                .Ltx_begin_done:
                    # try start (mesmo layout de KofTryStart no emitter)
                    la   t0, .Ltx_handler
                    sd   t0, 0(sp)
                    sd   sp, 8(sp)
                    sd   s11, 16(sp)
                    call kof_exc_slot           # §129: chain da thread atual
                    ld   t2, 0(a0)
                    sd   t2, 24(sp)
                    sd   sp, 0(a0)
                    # invoca a lambda (vtable[0]); a0 = task
                    mv   a0, s0
                    ld   t0, 8(a0)
                    ld   t0, 0(t0)
                    jalr t0
                    # try end / commit (nested NÃO comita — o externo decide)
                    call kof_exc_slot
                    ld   t2, 24(sp)
                    sd   t2, 0(a0)
                    lw   t3, 32(sp)
                    bnez t3, .Ltx_done
                    la   t0, .Ldb_default_handle
                    ld   t0, 0(t0)
                    beqz t0, .Ltx_done
                    mv   a0, t0
                    la   a1, .Ldb_commit_str
                    call kof_db_execute
                    la   t0, .Ldb_tx_handle
                    sd   zero, 0(t0)
                .Ltx_done:
                    ld   s1, 88(sp)
                    ld   s0, 80(sp)
                    ld   ra, 96(sp)
                    addi sp, sp, 112
                    ret
                .Ltx_handler:
                    # sp = frame base; a0 = exceção (chain já restaurada)
                    mv   s1, a0
                    lw   t3, 32(sp)         # nested
                    bnez t3, .Ltx_rethrow
                    la   t0, .Ldb_default_handle
                    ld   t0, 0(t0)
                    beqz t0, .Ltx_rethrow
                    mv   a0, t0
                    la   a1, .Ldb_rollback_str
                    call kof_db_execute
                    la   t0, .Ldb_tx_handle
                    sd   zero, 0(t0)
                .Ltx_rethrow:
                    mv   a0, s1
                    call kof_throw_string

                # strings Kof para BEGIN/COMMIT/ROLLBACK (lidas por data+24)
                .section .data
                .align 2
                .Ldb_begin_str:
                    .long 1
                    .long 0
                    .quad 0
                    .long 5
                    .long 0
                    .asciz "begin"
                .align 2
                .Ldb_commit_str:
                    .long 1
                    .long 0
                    .quad 0
                    .long 6
                    .long 0
                    .asciz "commit"
                .align 2
                .Ldb_rollback_str:
                    .long 1
                    .long 0
                    .quad 0
                    .long 8
                    .long 0
                    .asciz "rollback"
                # S0/§421: o esquema fora do contrato agora é recusado na B73
                # (kof_db_connect_mysql) com DB001 nomeado — nunca handle nulo.
                .section .text

                """);
        RISCV_RUNTIME_ASM_B_47 = sb.toString();
    }
}