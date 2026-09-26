package dev.kof.compiler.memory;

/**
 * D-MEMORY-SAFETY Fase 2 (fatia 3) — o FATO de transferencia da regra O-02
 * (spec §2.2): `var a = b; b = null` — a posse sai de `b` e entra em `a`, e
 * o registro so existe quando o original foi explicitamente nulado. Kof nao
 * tem operador de move; isto representa o PADRAO, para que o reconhecedor
 * de AST (fatia 4) e a emissao de MEM002 (Fase 3) tenham uma fonte nomeada.
 *
 * Invariantes (falhar aqui = bug do chamador, nunca silencio):
 * nomes validos e ligacao de origem distinta do destino.
 */
public record MoveTransfer(String destination, String source) {

    public MoveTransfer {
        if (destination == null || destination.isEmpty()) {
            throw new IllegalArgumentException("move sem destino nomeado");
        }
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("move sem origem nomeada (O-02 exige b explicito)");
        }
        if (destination.equals(source)) {
            throw new IllegalArgumentException("auto-move não transfere posse: " + source);
        }
    }

    /** O-02: a transferencia so e validada quando a origem foi nula-explicita. */
    public boolean requiresSourceNulling() {
        return true;
    }
}
