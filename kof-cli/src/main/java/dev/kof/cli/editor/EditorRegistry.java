package dev.kof.cli.editor;

import dev.kof.cli.editor.providers.*;
import java.util.List;

/**
 * Registro central dos editores suportados (EDI001, §2). Adicionar um editor
 * novo = adicionar um provider aqui + um arquivo em providers/ — nunca tocar
 * no core da CLI (regra de extensibilidade, §26).
 */
public final class EditorRegistry {

    private EditorRegistry() {}

    /** Ordem de exibição estável (a do briefing). */
    public static List<EditorIntegration> all() {
        return List.of(
                new VscodeProvider(),
                new VimProvider(),
                new NeovimProvider(),
                new IntelliJProvider(),
                new GeanyProvider(),
                new NanoProvider(),
                new EmacsProvider());
    }

    /** Busca por id (para install/uninstall). null se desconhecido. */
    public static EditorIntegration byId(String id) {
        for (EditorIntegration e : all()) {
            if (e.id().equals(id)) return e;
        }
        return null;
    }
}
