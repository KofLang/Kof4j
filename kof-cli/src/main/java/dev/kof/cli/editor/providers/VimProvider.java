package dev.kof.cli.editor.providers;

import dev.kof.cli.editor.AbstractEditorIntegration;
import dev.kof.cli.editor.DetectContext;
import dev.kof.cli.editor.EditorFile;
import dev.kof.cli.editor.EditorInstaller;
import dev.kof.cli.editor.KofEditorContent;
import java.util.List;
import java.util.regex.Pattern;

/** Vim — filetype/syntax/indent/compiler + LSP (degraus 6+). */
public final class VimProvider extends AbstractEditorIntegration {
    public String id() { return "vim"; }
    public String displayName() { return "Vim"; }
    public String integrationName() { return "Kof for Vim"; }
    protected List<String> executables() { return List.of("vim"); }
    protected List<String> configDirs() { return List.of(".vim"); }
    protected Pattern versionPattern() { return Pattern.compile("Vi IMproved (\\d+\\.\\d+)"); }

    @Override
    public java.util.List<EditorFile> integrationFiles(DetectContext ctx) {
        return KofEditorContent.vim(ctx);
    }

    @Override
    protected boolean integrationInstalled(DetectContext ctx, java.nio.file.Path foundPath) {
        return ctx.home() != null && EditorInstaller.isInstalled(ctx.home(), id());
    }
}
