package dev.kof.cli.editor;

/**
 * Resultado da detecção de um editor numa máquina (EDI001, §10). Imutável.
 * {@code version} é {@code "unknown"} quando não se consegue ler — nunca se
 * inventa versão (regra do briefing). {@code path} é {@code null} quando o
 * executável não foi localizado.
 */
public record EditorInfo(
        String id,
        String displayName,
        String version,
        String path,
        boolean integrationAvailable,
        boolean integrationInstalled) {

    public boolean installed() { return path != null; }

    public static EditorInfo absent(String id, String displayName, boolean integrationAvailable) {
        return new EditorInfo(id, displayName, "unknown", null, integrationAvailable, false);
    }
}
