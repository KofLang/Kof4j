package dev.kof.cli.editor.providers;

import dev.kof.cli.editor.AbstractEditorIntegration;
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
}
