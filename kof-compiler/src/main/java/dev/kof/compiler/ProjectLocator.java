package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fase 1 (plataforma): localiza a RAIZ DE PROJETO a partir de um arquivo
 * fonte, subindo diretórios até achar {@code kof.toml}.
 *
 * Contrato (aditivo — zero mudança de semântica):
 * - kof.toml presente → projectRoot = diretório que o contém.
 * - Ausente → null (caller mantém o comportamento atual: LCA das fontes).
 * Isso permite {@code import shared.Validation} com arquivo em
 * {@code src/main.kf} e módulo em {@code shared/} sob a mesma raiz.
 */
final class ProjectLocator {

    /** Nome do arquivo-manifesto do projeto. */
    static final String MANIFEST = "kof.toml";

    private ProjectLocator() {}

    /**
     * Sobe a partir de {@code startDir} (inclusive) até a raiz do filesystem
     * procurando kof.toml. Retorna o diretório que o contém, ou null.
     */
    static Path locate(Path startDir) {
        if (startDir == null) return null;
        Path dir;
        try {
            dir = startDir.toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
        // teto defensivo: profundidade máxima de subida
        for (int i = 0; i < 64 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve(MANIFEST))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /** Indica se {@code projectRoot} contém um manifesto válido. */
    static boolean isProjectRoot(Path projectRoot) {
        return projectRoot != null && Files.isRegularFile(projectRoot.resolve(MANIFEST));
    }

    /** Lê o texto bruto do manifesto (para diagnostics/futuro parser). */
    static String readManifest(Path projectRoot) throws IOException {
        return Files.readString(projectRoot.resolve(MANIFEST));
    }
}
