package dev.kof.compiler.nat;

/**
 * §1187/§1188 — o array de argumentos de `main(String[] args)` nos alvos
 * CROSS (riscv64/aarch64).
 *
 * <p>O `_start` CRU recebe o vetor inicial do Linux na pilha — `[sp]=argc`,
 * `[sp+8]=argv[0..)` — e o entry do cross chamava `<Main>_main` SEM argumento
 * algum (medido: `n=0` no riscv64, SEGFAULT no aarch64). O bloco abaixo le o
 * vetor ANTES de alinhar o sp, tira o `argv[0]` do SO (o contrato: `args`
 * carrega so' os argumentos do usuario — o JVM e o WASI fazem o mesmo) e
 * preenche cada slot com `kof_string_from_literal(argv[i], strlen)`.
 *
 * <p>O dialeto e' o RISCV: o aarch64 o herda pela traducao linha-a-linha, e a
 * forma sai byte-identica dos dois emissores (mesmo texto, mesmo helper).
 * Deixa o ARRAY em `a0` — o registro de argumento do `<Main>_main`.
 */
final class NativeCrossMainArgs {

    private NativeCrossMainArgs() {}

    private static final String[] BLOCK = {
        "    ld   s1, 0(sp)",            // argc
        "    addi s1, s1, -1",           // o argv[0] do SO sai
        "    addi s2, sp, 16",           // argv
        "    mv   a0, s1",
        "    li   a1, 8",
        "    call kof_array_alloc",
        "    mv   s3, a0",               // o array
        "    li   s4, 0",                // i
        ".Lkof_args_loop:",
        "    bge  s4, s1, .Lkof_args_done",
        "    slli t0, s4, 3",
        "    add  t0, s2, t0",
        "    ld   a0, 0(t0)",            // argv[i]
        "    mv   t1, a0",
        "    li   t2, 0",
        ".Lkof_args_strlen:",
        "    lbu  t3, 0(t1)",
        "    beqz t3, .Lkof_args_strlen_done",
        "    addi t1, t1, 1",
        "    addi t2, t2, 1",
        "    j    .Lkof_args_strlen",
        ".Lkof_args_strlen_done:",
        "    mv   a1, t2",               // len
        "    call kof_string_from_literal",
        "    slli t0, s4, 3",
        "    add  t0, s3, t0",
        "    sd   a0, 24(t0)",           // data[i] = String
        "    addi s4, s4, 1",
        "    j    .Lkof_args_loop",
        ".Lkof_args_done:",
        "    mv   a0, s3",               // o array = o arg do main
    };

    static void emit(StringBuilder sb) {
        for (String line : BLOCK) sb.append(line).append("\n");
    }
}
