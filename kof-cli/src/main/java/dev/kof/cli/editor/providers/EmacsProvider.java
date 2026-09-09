package dev.kof.cli.editor.providers;

import dev.kof.cli.editor.AbstractEditorIntegration;
import dev.kof.cli.editor.DetectContext;
import dev.kof.cli.editor.EditorFile;
import dev.kof.cli.editor.EditorInstaller;
import dev.kof.cli.editor.KofEditorContent;
import java.util.List;
import java.util.regex.Pattern;

/** Emacs — kof-mode + eglot (degraus 7+). */
public final class EmacsProvider extends AbstractEditorIntegration {
    public String id() { return "emacs"; }
    public String displayName() { return "Emacs"; }
    public String integrationName() { return "Kof mode for Emacs"; }
    protected List<String> executables() { return List.of("emacs", "emacsclient"); }
    protected List<String> configDirs() { return List.of(".emacs.d", ".config/emacs"); }
    protected Pattern versionPattern() { return Pattern.compile("GNU Emacs (\\d+\\.\\d+)"); }

    @Override
    public java.util.List<EditorFile> integrationFiles(DetectContext ctx) {
        return KofEditorContent.emacs(ctx);
    }

    @Override
    protected boolean integrationInstalled(DetectContext ctx, java.nio.file.Path foundPath) {
        return ctx.home() != null && EditorInstaller.isInstalled(ctx.home(), id());
    }
}
