package dev.kof.cli.editor;

/**
 * Contrato de integração de um editor (EDI001, §2). Cada editor tem seu
 * próprio provider — a CLI nunca faz {@code if (vscode)} espalhado.
 *
 * <p>Degraus 1-2 (este commit) cobrem a face READ-ONLY: {@link #detect}.
 * {@code install}/{@code uninstall}/{@code configure} entram no degrau 3
 * (plano §20) — adicionar depois é aditivo, nunca quebrar quem chama detect.
 */
public interface EditorIntegration {

    /** id estável usado na CLI ({@code kof editor install <id>}). */
    String id();

    /** nome legível ({@code Visual Studio Code}). */
    String displayName();

    /** rótulo da integração oficial ({@code Kof for VS Code}). */
    String integrationName();

    /**
     * Detecta o editor no ambiente dado. Retorna {@link EditorInfo} com
     * {@code installed()} true/false e {@code integrationInstalled()}.
     * Nunca inventa versão (usa {@code "unknown"} quando ilegível).
     */
    EditorInfo detect(DetectContext ctx);

    /**
     * Arquivos de integração que este provider escreve no HOME do usuário
     * (degrau 3). Default vazio: um provider ainda sem conteúdo idiomático
     * não instala nada (nunca sobrescreve silenciosamente). Adicionar é
     * aditivo — quem só chama {@link #detect} não é afetado.
     */
    default java.util.List<EditorFile> integrationFiles(DetectContext ctx) {
        return java.util.List.of();
    }
}
