package dev.kof.cli.editor.providers;

import dev.kof.cli.editor.AbstractEditorIntegration;
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
}
