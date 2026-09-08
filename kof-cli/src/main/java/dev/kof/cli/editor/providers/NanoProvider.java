package dev.kof.cli.editor.providers;

import dev.kof.cli.editor.AbstractEditorIntegration;
import java.util.List;
import java.util.regex.Pattern;

/** Nano — integração proporcional: syntax + filetype (degraus 9+). */
public final class NanoProvider extends AbstractEditorIntegration {
    public String id() { return "nano"; }
    public String displayName() { return "Nano"; }
    public String integrationName() { return "Kof syntax for Nano"; }
    protected List<String> executables() { return List.of("nano"); }
    protected List<String> configDirs() { return List.of(".nano"); }
    // saída localizada ("GNU nano, versão 7.2") — ancora no "nano" e aceita
    // qualquer palavra entre ele e o número (não depende do idioma).
    protected Pattern versionPattern() {
        return Pattern.compile("nano[^\\d]*(\\d+\\.\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    }
}
