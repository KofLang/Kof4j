package dev.kof.cli.editor.providers;

import dev.kof.cli.editor.AbstractEditorIntegration;
import java.util.List;
import java.util.regex.Pattern;

/** IntelliJ IDEA — plugin LSP4IJ + language (degraus 10+; issue #1). */
public final class IntelliJProvider extends AbstractEditorIntegration {
    public String id() { return "intellij"; }
    public String displayName() { return "IntelliJ IDEA"; }
    public String integrationName() { return "Kof for IntelliJ IDEA"; }
    protected List<String> executables() { return List.of("idea"); }
    protected List<String> configDirs() {
        return List.of("Library/Application Support/JetBrains", ".config/JetBrains", "AppData/Roaming/JetBrains");
    }
    protected Pattern versionPattern() { return Pattern.compile("(20\\d\\d\\.\\d+(\\.\\d+)?)"); }
}
