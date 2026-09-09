package dev.kof.cli.editor;

/**
 * Um arquivo de integração que um provider escreve no ambiente do usuário
 * (EDI001, degrau 3). {@code relativePath} é relativo ao HOME (ex.:
 * {@code .config/nvim/after/ftplugin/kof.vim}); {@code content} é o texto
 * idiomático do editor. O installer escreve de forma idempotente (só toca
 * se o conteúdo difere) e registra um marker para uninstall.
 */
public record EditorFile(String relativePath, String content) {
}
