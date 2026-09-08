package dev.kof.cli.editor.providers;

import dev.kof.cli.editor.AbstractEditorIntegration;
import java.util.List;
import java.util.regex.Pattern;

/** Neovim — plugin lua apontando para `kof lsp` (degraus 5+). */
public final class NeovimProvider extends AbstractEditorIntegration {
    public String id() { return "neovim"; }
    public String displayName() { return "Neovim"; }
    public String integrationName() { return "Kof for Neovim"; }
    protected List<String> executables() { return List.of("nvim"); }
    protected List<String> configDirs() { return List.of(".config/nvim"); }
    protected Pattern versionPattern() { return Pattern.compile("v(\\d+\\.\\d+\\.\\d+)"); }
}
