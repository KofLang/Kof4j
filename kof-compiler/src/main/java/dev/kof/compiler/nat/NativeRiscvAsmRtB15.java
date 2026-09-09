package dev.kof.compiler.nat;

// FASE 3 (STDLIB STRN001): fatia 15 de RISCV_RUNTIME_ASM_B — kof.strings
// conversores de PALAVRA (joinWords + toCamelCase/toPascalCase/toSnakeCase/
// toKebabCase/slugify). Fecha o gate STRN001: estes 5 passam a rodar nos 4
// targets (paridade byte-a-byte com x86, travada por diff do golden oracle).
//
// Mesma lógica do x86 RuntimeStringsWords: Words = sequências de [0-9A-Za-z];
// boundary em primeiro alnum após não-alnum, lower/dígito→Upper, Upper→Upper
// + lower-seguinte (HTTPServer=http|Server, XMLParser=xml|Parser). >=128 é
// delimitador (NAT-STR01, mesma regra nos 4). mode 0=camel 1=pascal 2=snake
// 3=kebab 4=slug. Saída <= 2*len. UMA passada, sem call dentro do laço.
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a). Layout KofStr: typeId=1@0,
// super@4, vtable@8(8B), length@16, pad@20, bytes@24, NUL@24+len.
// Classificação de char usa compare UNSIGNED (bltu/bgeu) para reproduzir o
// wraparound do subl/cmpl do x86 sobre bytes 0..255.
public final class NativeRiscvAsmRtB15 {

    static final String RISCV_RUNTIME_ASM_B_15 = """

            .section .text
            # _kof_strings_joinWords(a0=v, a1=mode) -> a0 = String nova
            _kof_strings_joinWords:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0              # v
                mv   s1, a1              # mode
                beqz s0, .Lv_jw_orig
                lw   s2, 16(s0)          # len
                # alocar (2*len + 25 + 15) & -16
                slli t0, s2, 1
                addi t0, t0, 25
                addi a0, t0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0              # novo
                li   t0, 1
                sw   t0, 0(s3)           # typeId=1
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                li   s4, 0               # i
                li   s5, 0               # pos
                li   s6, 0               # wc
                li   s7, -1              # prev
            .Lv_jw_loop:
                bge  s4, s2, .Lv_jw_term
                add  t0, s0, s4
                lbu  t0, 24(t0)          # c = v.bytes[i]
                # classify: upper? lower? digit? notalnum
                addi t2, t0, -65
                li   t3, 26
                bltu t2, t3, .Lv_jw_up
                addi t2, t0, -97
                li   t3, 26
                bltu t2, t3, .Lv_jw_lo
                addi t2, t0, -48
                li   t3, 10
                bgeu t2, t3, .Lv_jw_notalnum
                li   t1, 0               # dígito: alnum, não-upper
                j    .Lv_jw_alnum
            .Lv_jw_up:
                li   t1, 1
                j    .Lv_jw_alnum
            .Lv_jw_lo:
                li   t1, 0
            .Lv_jw_alnum:
                li   t2, -1
                beq  s7, t2, .Lv_jw_nw   # prev == -1 => nova palavra
                beqz t1, .Lv_jw_emit_low # c não-upper => continua palavra
                addi t2, s7, -97
                li   t3, 26
                bltu t2, t3, .Lv_jw_nw   # prev lower => nova
                addi t2, s7, -48
                li   t3, 10
                bltu t2, t3, .Lv_jw_nw   # prev dígito => nova
                addi t2, s7, -65
                li   t3, 26
                bgeu t2, t3, .Lv_jw_emit_low   # prev não-upper => não nova
                addi t2, s4, 1
                bge  t2, s2, .Lv_jw_emit_low   # i+1 >= len => não nova
                add  t4, s0, t2
                lbu  t4, 24(t4)
                addi t4, t4, -97
                li   t3, 26
                bgeu t4, t3, .Lv_jw_emit_low   # próximo não-lower => não nova
            .Lv_jw_nw:
                beqz s5, .Lv_jw_cap
                li   t2, 2
                blt  s1, t2, .Lv_jw_cap        # mode<2: sem separador
                li   t2, 2
                beq  s1, t2, .Lv_jw_sepu       # snake => '_'
                li   t2, 45                    # kebab/slug => '-'
                j    .Lv_jw_sep
            .Lv_jw_sepu:
                li   t2, 95
            .Lv_jw_sep:
                add  t4, s3, s5
                sb   t2, 24(t4)
                addi s5, s5, 1
            .Lv_jw_cap:
                li   t2, 1                     # cap = 1
                li   t3, 1
                beq  s1, t3, .Lv_jw_emit_c     # pascal: cap=1
                li   t3, 1
                bgt  s1, t3, .Lv_jw_c0         # mode>1 (snake/kebab/slug): cap=0
                beqz s6, .Lv_jw_c0             # camel: 1ª palavra => cap=0
                j    .Lv_jw_emit_c
            .Lv_jw_c0:
                li   t2, 0
            .Lv_jw_emit_c:
                mv   t5, t0
                beqz t2, .Lv_jw_eclow
                addi t4, t5, -97
                li   t3, 26
                bgeu t4, t3, .Lv_jw_put
                addi t5, t5, -32               # lower -> UPPER
                j    .Lv_jw_put
            .Lv_jw_eclow:
                addi t4, t5, -65
                li   t3, 26
                bgeu t4, t3, .Lv_jw_put
                addi t5, t5, 32                # UPPER -> lower
            .Lv_jw_put:
                add  t4, s3, s5
                sb   t5, 24(t4)
                addi s5, s5, 1
                addi s6, s6, 1
                mv   s7, t0                    # prev = c ORIGINAL
                addi s4, s4, 1
                j    .Lv_jw_loop
            .Lv_jw_emit_low:
                mv   t5, t0
                addi t4, t5, -65
                li   t3, 26
                bgeu t4, t3, .Lv_jw_put2
                addi t5, t5, 32
            .Lv_jw_put2:
                add  t4, s3, s5
                sb   t5, 24(t4)
                addi s5, s5, 1
                mv   s7, t0
                addi s4, s4, 1
                j    .Lv_jw_loop
            .Lv_jw_notalnum:
                li   t2, -1
                mv   s7, t2
                addi s4, s4, 1
                j    .Lv_jw_loop
            .Lv_jw_term:
                sw   s5, 16(s3)                # length = pos
                add  t0, s3, s5
                sb   zero, 24(t0)              # NUL
                mv   a0, s3
                j    .Lv_jw_done
            .Lv_jw_orig:
                mv   a0, s0
            .Lv_jw_done:
                ld   s6, 0(sp)
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            .globl kof_strings_toCamelCase
            kof_strings_toCamelCase:
                li   a1, 0
                j    _kof_strings_joinWords
            .globl kof_strings_toPascalCase
            kof_strings_toPascalCase:
                li   a1, 1
                j    _kof_strings_joinWords
            .globl kof_strings_toSnakeCase
            kof_strings_toSnakeCase:
                li   a1, 2
                j    _kof_strings_joinWords
            .globl kof_strings_toKebabCase
            kof_strings_toKebabCase:
                li   a1, 3
                j    _kof_strings_joinWords
            .globl kof_strings_slugify
            kof_strings_slugify:
                li   a1, 4
                j    _kof_strings_joinWords
            """;
}
