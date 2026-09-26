package dev.kof.compiler.memory;

/**
 * D-MEMORY-SAFETY Fase 2 (fatia 1) — recursos de ciclo de vida gerenciados
 * (spec §9), na ordem da tabela. Kof NAO tem destruidor/finalizer: recurso
 * que precisa de close tem que fechar explicitamente; o diagnostico ao nao
 * fechar e de Fase 3 (a representacao nasce aqui).
 */
public enum ManagedResource {

    /** Servidor `kof.http.server`: vida = processo, ou `kof_web_close` explicito. */
    WEB(true, "MEM014"),

    /** Conexao `kof.db`: vida = processo, ou `kof_db_close` explicito. */
    DB(true, "MEM014"),

    /** Alca de arquivo: por chamada (arquivo inteiro) — fecha sozinha, nunca MEM014. */
    FILE(false, null),

    /** Buffer FFI: arena/confined ou `kof_ffi_release`; copia-fora descartada no native. */
    FFI_BUFFER(true, "MEM005");

    private final boolean closeRequired;
    private final String diagnostic;

    ManagedResource(boolean closeRequired, String diagnostic) {
        this.closeRequired = closeRequired;
        this.diagnostic = diagnostic;
    }

    public boolean closeRequired() {
        return closeRequired;
    }

    /** Codigo esperado do nao-fechamento, ou null quando o recurso fecha sozinho. */
    public String diagnostic() {
        return diagnostic;
    }
}
