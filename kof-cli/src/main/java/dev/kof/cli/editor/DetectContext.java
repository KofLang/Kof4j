package dev.kof.cli.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Ambiente injetável de detecção (EDI001, §23-24). A implementação real lê
 * PATH, dirs de config e versões da máquina; os TESTES usam uma fake com
 * PATH/dirs em fs temporário — a suíte nunca toca o ambiente real do usuário.
 */
public interface DetectContext {

    /** Contexto real da máquina atual. */
    static DetectContext system() {
        return new SystemDetectContext();
    }

    /** true se um executável chamado {@code exe} está no PATH. */
    boolean whichExists(String exe);

    /** Caminho absoluto do executável {@code exe} no PATH, ou null. */
    String whichPath(String exe);

    /** Versão lida de {@code exe --version} (formatada pelo provider), ou "unknown". */
    String readVersion(String exe);

    /** true se o diretório de config existe (ex.: ~/.vscode). */
    boolean dirExists(Path dir);

    /** true se o arquivo existe. */
    boolean fileExists(Path file);

    /** HOME do usuário (para resolver ~/.config etc.), ou null. */
    Path home();

    /** Lista de nomes de executáveis candidatos (ordem de prioridade). */
    List<String> candidateExes();

    /** S.O. atual: "linux" | "mac" | "windows" | "other". */
    String osName();

    /**
     * Executável do Kof que as integrações invocam (LSP/fmt/build). Default
     * {@code "kof"} (assume PATH) — o launcher instalado é sempre {@code kof}.
     */
    default String kofExecutable() { return "kof"; }

    /** Diretório de instalação da distribuição (onde {@code editor/} viaja), ou null. */
    default Path installDir() { return null; }
}
