package dev.kof.compiler;

/**
 * Desvincula o handler de exceção da região de try corrente SEM rotular o fim
 * da região — emitido no caminho de SAÍDA NORMAL do corpo do try (que salta por
 * cima do {@link KofTryEnd}, tornando-o inalcançável em runtime).
 *
 * <p>Bug medido 30/09 (§549): o {@code KofTryEnd} era emitido só no ramo
 * {@code else} do lowering do {@code TryStmt} — inalcançável, porque o corpo do
 * try salta para o label do finally antes dele. Nos alvos nativos o handler
 * continuava vinculado em {@code kof_exc_chain} e capturava o {@code throw}
 * SEGUINTE ao try (o JVM/Script seguiam corretos: tabela estática / match por
 * intervalo de pc). Este op dá o pop equivalente ao {@link KofTryEnd} no
 * caminho normal, mantendo a cadeia de exceção em sincronia com o escopo
 * léxico.</p>
 *
 * <p>Por backend: nativo x86 e cross riscv/aarch64 desvinculam o frame (mesmas
 * instruções do {@link KofTryEnd}); JVM e JS são no-op (a tabela do JVM é
 * estática e o JS reconstrói estruturalmente); o interpretador dá pop no
 * tryStack (higiene — o match por pc já o tornava inerte).</p>
 */
public record KofExcUnlink() implements KofOperation {
}
