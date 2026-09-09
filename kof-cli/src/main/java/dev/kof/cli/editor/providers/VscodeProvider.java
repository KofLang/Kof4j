package dev.kof.cli.editor.providers;

import dev.kof.cli.editor.AbstractEditorIntegration;
import dev.kof.cli.editor.DetectContext;
import dev.kof.cli.editor.EditorFile;
import dev.kof.cli.editor.EditorInstaller;
import dev.kof.cli.editor.KofEditorContent;
import java.util.List;
import java.util.regex.Pattern;

/** VS Code — integração completa via grammar TextMate + `kof lsp` (degraus 4+). */
public final class VscodeProvider extends AbstractEditorIntegration {
    public String id() { return "vscode"; }
    public String displayName() { return "Visual Studio Code"; }
    public String integrationName() { return "Kof for VS Code"; }
    protected List<String> executables() { return List.of("code", "code-insiders"); }
    protected List<String> configDirs() { return List.of(".vscode", ".vscode-oss"); }
    protected Pattern versionPattern() { return Pattern.compile("(\\d+\\.\\d+\\.\\d+)"); }

    @Override
    public java.util.List<EditorFile> integrationFiles(DetectContext ctx) {
        return KofEditorContent.vscode(ctx);
    }

    @Override
    protected boolean integrationInstalled(DetectContext ctx, java.nio.file.Path foundPath) {
        return ctx.home() != null && EditorInstaller.isInstalled(ctx.home(), id());
    }
}
