package dev.kof.cli.editor.providers;

import dev.kof.cli.editor.AbstractEditorIntegration;
import dev.kof.cli.editor.DetectContext;
import dev.kof.cli.editor.EditorFile;
import dev.kof.cli.editor.EditorInstaller;
import dev.kof.cli.editor.KofEditorContent;
import java.util.List;
import java.util.regex.Pattern;

/** Geany — filetype/syntax/build commands (degraus 8+). */
public final class GeanyProvider extends AbstractEditorIntegration {
    public String id() { return "geany"; }
    public String displayName() { return "Geany"; }
    public String integrationName() { return "Kof for Geany"; }
    protected List<String> executables() { return List.of("geany"); }
    protected List<String> configDirs() { return List.of(".config/geany"); }
    // "geany 2.0 (construído ... com GTK 3.24.41, GLib 2.80.0)" — ancora no
    // "geany" para não pegar a versão do GTK/GLib.
    protected Pattern versionPattern() {
        return Pattern.compile("geany (\\d+\\.\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public java.util.List<EditorFile> integrationFiles(DetectContext ctx) {
        return KofEditorContent.geany(ctx);
    }

    @Override
    protected boolean integrationInstalled(DetectContext ctx, java.nio.file.Path foundPath) {
        return ctx.home() != null && EditorInstaller.isInstalled(ctx.home(), id());
    }
}
