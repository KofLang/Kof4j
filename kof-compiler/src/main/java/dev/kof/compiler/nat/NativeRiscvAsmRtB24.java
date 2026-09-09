package dev.kof.compiler.nat;

// FASE 3 (STDLIB S8-C): fatia 24 de RISCV_RUNTIME_ASM_B — kof.net URI-parse
// (fecha NET001). MESMA máquina de RuntimeUri (x86) / JVM / JS, validada no
// harness C isolado (92/92) e nos 17 vetores do oracle Python (KofNetTest).
// kof_net_field(v, idx 0..5) + 6 globls finos; spans 6×{int32 start,len} em
// 0..47(sp), salvos s0..s7/ra a partir de 48; frame 128 (16-align p/
// kof_alloc/kof_memcpy — pós-call só s-regs). Estado: s0=v s1=len s2=idx
// s3=after s4=bodyEnd s5=&bytes[0] s6=start s7=len-campo. Subs .Lv_nt_le/.
// Lv_nt_sc (a0=byte→a0=0/1; só t/a-regs — call-safe). Classificação por
// subtração UNSIGNED (bltu) — lição isIpv4; lbu com reg endereço SEPARADO do
// reg valor (lição B22). null=>0; len<=0=>"" novo; delimitadores ASCII, bytes
// >=128 fluem como conteúdo.
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB24 {

    static final String RISCV_RUNTIME_ASM_B_24 = """

            .section .text
            .globl kof_net_scheme
            kof_net_scheme:
                li   a1, 0
                j    kof_net_field
            .globl kof_net_host
            kof_net_host:
                li   a1, 1
                j    kof_net_field
            .globl kof_net_port
            kof_net_port:
                li   a1, 2
                j    kof_net_field
            .globl kof_net_path
            kof_net_path:
                li   a1, 3
                j    kof_net_field
            .globl kof_net_query
            kof_net_query:
                li   a1, 4
                j    kof_net_field
            .globl kof_net_fragment
            kof_net_fragment:
                li   a1, 5
                j    kof_net_field

            .globl kof_net_field
            kof_net_field:
                addi sp, sp, -128
                sd   ra, 120(sp)
                sd   s0, 112(sp)
                sd   s1, 104(sp)
                sd   s2, 96(sp)
                sd   s3, 88(sp)
                sd   s4, 80(sp)
                sd   s5, 72(sp)
                sd   s6, 64(sp)
                sd   s7, 56(sp)
                mv   s0, a0              # v
                mv   s2, a1              # idx
                sw   zero, 0(sp)
                sw   zero, 4(sp)
                sw   zero, 8(sp)
                sw   zero, 12(sp)
                sw   zero, 16(sp)
                sw   zero, 20(sp)
                sw   zero, 24(sp)
                sw   zero, 28(sp)
                sw   zero, 32(sp)
                sw   zero, 36(sp)
                sw   zero, 40(sp)
                sw   zero, 44(sp)
                beqz s0, .Lv_nt_null
                addi s5, a0, 24          # &bytes[0]
                lw   s1, 16(s0)
                blez s1, .Lv_nt_zero
                li   s3, 0               # after = 0
                # --- A: 1º ':' ---
                mv   t0, zero
            .Lv_nt_cfc:
                bge  t0, s1, .Lv_nt_nocf
                add  t2, s5, t0
                lbu  t4, 0(t2)
                li   t1, 58
                beq  t4, t1, .Lv_nt_cf
                addi t0, t0, 1
                j    .Lv_nt_cfc
            .Lv_nt_cf:                   # t0 = cp
                blez t0, .Lv_nt_nocf
                mv   s4, t0              # (reusa s4 temporário p/ cp)
                lbu  a0, 0(s5)
                call .Lv_nt_le
                beqz a0, .Lv_nt_nocf
                li   t0, 1
            .Lv_nt_ckv:
                bge  t0, s4, .Lv_nt_vok
                add  t2, s5, t0
                lbu  a0, 0(t2)
                call .Lv_nt_sc
                beqz a0, .Lv_nt_nocf
                addi t0, t0, 1
                j    .Lv_nt_ckv
            .Lv_nt_vok:
                sw   s4, 4(sp)           # sch len = cp (start 0 já zero)
                addi s3, s4, 1           # after = cp+1
            .Lv_nt_nocf:
                # --- B: 1º '#' em [after,len) ---
                mv   s4, s1              # bodyEnd = len (default)
                mv   t0, s3
            .Lv_nt_fsc:
                bge  t0, s1, .Lv_nt_fend
                add  t2, s5, t0
                lbu  t4, 0(t2)
                li   t1, 35
                beq  t4, t1, .Lv_nt_fh
                addi t0, t0, 1
                j    .Lv_nt_fsc
            .Lv_nt_fh:
                mv   s4, t0              # bodyEnd = fp
                addi t3, t0, 1
                sw   t3, 40(sp)          # frag start
                sub  t3, s1, t3
                sw   t3, 44(sp)          # frag len
            .Lv_nt_fend:
                # --- C: 1º '?' em [after,bodyEnd) ---
                mv   t5, s4              # qpos = bodyEnd (default)
                mv   t0, s3
            .Lv_nt_qsc:
                bge  t0, s4, .Lv_nt_qend
                add  t2, s5, t0
                lbu  t4, 0(t2)
                li   t1, 63
                beq  t4, t1, .Lv_nt_qh
                addi t0, t0, 1
                j    .Lv_nt_qsc
            .Lv_nt_qh:
                mv   t5, t0              # qpos
                addi t3, t0, 1
                sw   t3, 32(sp)          # query start = q+1
                sub  t3, s4, t3
                sw   t3, 36(sp)          # query len = bodyEnd-(q+1)
            .Lv_nt_qend:
                sw   s3, 24(sp)          # path start = after
                sub  t3, t5, s3
                sw   t3, 28(sp)          # path len = qpos - after
                # --- D: "//" authority ---
                lw   t1, 28(sp)          # plen
                li   t2, 2
                blt  t1, t2, .Lv_nt_save
                lw   t0, 24(sp)          # pstart
                add  t2, s5, t0
                lbu  t4, 0(t2)
                li   t3, 47
                bne  t4, t3, .Lv_nt_save
                lbu  t4, 1(t2)
                bne  t4, t3, .Lv_nt_save
                addi t3, t0, 2           # ast = pstart+2
                add  t4, t0, t1          # pathEnd = pstart+plen
                mv   s6, t3              # ast (callee-saved p/ host split)
                mv   s7, t4              # pathEnd
                sw   t4, 48(sp)          # aend slot (default = pathEnd)
                mv   t0, s6              # i = ast
            .Lv_nt_asc:
                bge  t0, s7, .Lv_nt_aend
                add  t2, s5, t0
                lbu  t4, 0(t2)
                li   t1, 47
                beq  t4, t1, .Lv_nt_acut
                li   t1, 63
                beq  t4, t1, .Lv_nt_acut
                li   t1, 35
                beq  t4, t1, .Lv_nt_acut
                addi t0, t0, 1
                j    .Lv_nt_asc
            .Lv_nt_acut:
                sw   t0, 48(sp)          # aend = i
            .Lv_nt_aend:
                lw   t3, 48(sp)          # aend
                sw   t3, 24(sp)          # path start = aend
                sub  t4, s7, t3
                sw   t4, 28(sp)          # path len = pathEnd - aend
                # último '@' em [s6=ast, t3=aend) — backscan
                mv   t0, t3
            .Lv_nt_ats:
                ble  t0, s6, .Lv_nt_atd
                addi t0, t0, -1
                add  t2, s5, t0
                lbu  t4, 0(t2)
                li   t1, 64
                beq  t4, t1, .Lv_nt_atf
                j    .Lv_nt_ats
            .Lv_nt_atf:
                addi s6, t0, 1           # ast = at+1
            .Lv_nt_atd:
                lw   t3, 48(sp)          # aend
                mv   t0, s6              # i = ast
            .Lv_nt_hsc:
                bge  t0, t3, .Lv_nt_nohp
                add  t2, s5, t0
                lbu  t4, 0(t2)
                li   t1, 58
                beq  t4, t1, .Lv_nt_hp
                addi t0, t0, 1
                j    .Lv_nt_hsc
            .Lv_nt_hp:
                sw   s6, 8(sp)           # host start
                sub  t4, t0, s6
                sw   t4, 12(sp)          # host len
                addi t4, t0, 1
                sw   t4, 16(sp)          # port start
                sub  t4, t3, t0
                addi t4, t4, -1
                sw   t4, 20(sp)          # port len
                j    .Lv_nt_save
            .Lv_nt_nohp:                 # sem ':' no authority => host inteiro
                sw   s6, 8(sp)           # host start = ast
                sub  t4, t3, s6          # aend - ast
                sw   t4, 12(sp)          # host len
            .Lv_nt_save:
                # lê spans[idx] = (start offset no v, len) — valores brutos
                li   t1, 8
                mul  t0, s2, t1          # idx*8 (offset do par na pilha)
                add  t0, sp, t0
                lw   s6, 0(t0)           # start (offset no v.bytes)
                lw   s7, 4(t0)           # len
                j    .Lv_nt_new
            .Lv_nt_zero:
                li   s6, 0
                li   s7, 0
            .Lv_nt_new:
                addi a0, s7, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0              # novo (idx não precisa mais)
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   s7, 16(s2)
                sw   zero, 20(s2)
                add  t1, s2, s7
                sb   zero, 24(t1)
                beqz s7, .Lv_nt_ret
                addi a0, s2, 24
                add  a1, s5, s6
                mv   a2, s7
                call kof_memcpy
            .Lv_nt_ret:
                mv   a0, s2
                j    .Lv_nt_done
            .Lv_nt_null:
                li   a0, 0
            .Lv_nt_done:
                ld   s7, 56(sp)
                ld   s6, 64(sp)
                ld   s5, 72(sp)
                ld   s4, 80(sp)
                ld   s3, 88(sp)
                ld   s2, 96(sp)
                ld   s1, 104(sp)
                ld   s0, 112(sp)
                ld   ra, 120(sp)
                addi sp, sp, 128
                ret

            # fachada queryEncode/Decode — tail p/ kof_encoding_url* (B11)
            .globl kof_net_queryEncode
            kof_net_queryEncode:
                j    kof_encoding_urlEncode
            .globl kof_net_queryDecode
            kof_net_queryDecode:
                j    kof_encoding_urlDecode

            .Lv_nt_le:                   # a0 byte -> a0 0/1 (t-only)
                li   t1, 65
                bltu a0, t1, .Lv_nt_le0
                li   t1, 91
                bltu a0, t1, .Lv_nt_le1
                li   t1, 97
                bltu a0, t1, .Lv_nt_le0
                li   t1, 123
                bltu a0, t1, .Lv_nt_le1
            .Lv_nt_le0:
                li   a0, 0
                ret
            .Lv_nt_le1:
                li   a0, 1
                ret

            .Lv_nt_sc:                   # letra|dígito|+-. -> a0 0/1
                li   t1, 65
                bltu a0, t1, .Lv_nt_sc_dig
                li   t1, 91
                bltu a0, t1, .Lv_nt_sc1
                li   t1, 97
                bltu a0, t1, .Lv_nt_sc_sym
                li   t1, 123
                bltu a0, t1, .Lv_nt_sc1
                j    .Lv_nt_sc0
            .Lv_nt_sc_dig:
                li   t1, 48
                bltu a0, t1, .Lv_nt_sc_sym
                li   t1, 58
                bltu a0, t1, .Lv_nt_sc1
                j    .Lv_nt_sc0
            .Lv_nt_sc_sym:
                li   t1, 43
                beq  a0, t1, .Lv_nt_sc1
                li   t1, 45
                beq  a0, t1, .Lv_nt_sc1
                li   t1, 46
                beq  a0, t1, .Lv_nt_sc1
            .Lv_nt_sc0:
                li   a0, 0
                ret
            .Lv_nt_sc1:
                li   a0, 1
                ret
            """;
}
