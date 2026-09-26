package dev.kof.compiler.memory;

/**
 * D-MEMORY-SAFETY Fase 2 (fatia 2) — os dois modos de capture de closure da
 * spec {@code docs/spec/memory-safety.md} §6.1 (E-01/E-02). O capture define
 * QUAL representacao o corpo da closure ve: SNAPSHOT congela o valor no
 * momento da criacao; SHARED_BOX aponta para a mesma caixa heap que o local
 * marcado pelo capture-scanner (E-03: a posse da closure estende a vida do
 * capture ate a coleta da propria closure — nunca antes).
 *
 * Representacao interna; nenhum comportamento muda nesta fatia.
 */
public enum CaptureMode {

    /** E-01: local nao mutado — capturado por valor (fotografia). */
    SNAPSHOT,

    /** E-02: local mutado — re-boxado em caixa heap compartilhada. */
    SHARED_BOX
}
