package dev.kof.cli;

import dev.kof.cli.editor.DetectContext;
import dev.kof.cli.editor.EditorInfo;
import dev.kof.cli.editor.EditorIntegration;
import dev.kof.cli.editor.EditorRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EDI001 degraus 1-2: infra de integração de editores + CLI read-only.
 * Regra §24: NENHUM teste toca o ambiente real — tudo via DetectContext
 * fake (PATH/dir/versões simulados).
 */
class EditorIntegrationTest {

    /** Contexto fake: PATH/dir/versões controlados pelo teste. */
    private static DetectContext fake(Set<String> installed, Map<String, String> versions,
                                      Set<String> configDirs) {
        return new DetectContext() {
            public boolean whichExists(String exe) { return installed.contains(exe); }
            public String whichPath(String exe) {
                return installed.contains(exe) ? "/fake/bin/" + exe : null;
            }
            public String readVersion(String exe) {
                return versions.getOrDefault(exe, exe + ": not found");
            }
            public boolean dirExists(Path dir) {
                return dir != null && configDirs.contains(dir.toString().replaceFirst("^/fake/home/", ""));
            }
            public boolean fileExists(Path file) { return false; }
            public Path home() { return Path.of("/fake/home"); }
            public List<String> candidateExes() { return List.of(); }
            public String osName() { return "linux"; }
        };
    }

    @Test
    void registryHasAllSevenEditorsInOrder() {
        List<String> ids = EditorRegistry.all().stream().map(EditorIntegration::id).toList();
        assertEquals(List.of("vscode", "vim", "neovim", "intellij", "geany", "nano", "emacs"), ids);
        assertNotNull(EditorRegistry.byId("vscode"));
        assertNull(EditorRegistry.byId("sublime"));
    }

    @Test
    void detectFindsInstalledAndReportsVersionAndPath() {
        var ctx = fake(Set.of("code"), Map.of("code", "1.102.3\nabc"), Set.of(".vscode"));
        EditorInfo info = EditorRegistry.byId("vscode").detect(ctx);
        assertTrue(info.installed());
        assertEquals("1.102.3", info.version());
        assertEquals("/fake/bin/code", info.path());
        assertTrue(info.integrationAvailable());
        assertTrue(info.integrationInstalled()); // ~/.vscode existe no fake
    }

    @Test
    void detectAbsentEditorIsNotInstalledAndNeverInventsVersion() {
        var ctx = fake(Set.of(), Map.of(), Set.of());
        EditorInfo info = EditorRegistry.byId("emacs").detect(ctx);
        assertFalse(info.installed());
        assertNull(info.path());
        assertEquals("unknown", info.version()); // nunca inventa
        assertTrue(info.integrationAvailable());
        assertFalse(info.integrationInstalled());
    }

    @Test
    void localizedVersionOutputStillParses() {
        // nano/geany em pt-BR: "GNU nano, versão 7.2" / "geany 2.0 (construído ... GTK 3.24.41)"
        var ctx = fake(Set.of("nano", "geany"),
                Map.of("nano", "GNU nano, versão 7.2",
                       "geany", "geany 2.0 (construído em 2024-03-31 com GTK 3.24.41, GLib 2.80.0)"),
                Set.of());
        assertEquals("7.2", EditorRegistry.byId("nano").detect(ctx).version());
        assertEquals("2.0", EditorRegistry.byId("geany").detect(ctx).version()); // não 3.24.41 (GTK)
    }

    @Test
    void unparseableVersionIsUnknown() {
        var ctx = fake(Set.of("nvim"), Map.of("nvim", "NVIM v0.13-dev+abc"), Set.of());
        assertEquals("unknown", EditorRegistry.byId("neovim").detect(ctx).version());
    }

    @Test
    void cliListAndDetectAndStatusProduceExpectedShape() {
        var ctx = fake(Set.of("code"), Map.of("code", "1.102.3"), Set.of(".vscode"));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);

        assertEquals(0, CmdEditor.run(new String[]{"editor", "list"}, ctx, out, out));
        String list = bo.toString(StandardCharsets.UTF_8);
        assertTrue(list.contains("Kof for VS Code"), list);
        assertTrue(list.contains("Kof mode for Emacs"), list);

        bo.reset();
        assertEquals(0, CmdEditor.run(new String[]{"editor", "detect"}, ctx, out, out));
        String detect = bo.toString(StandardCharsets.UTF_8);
        assertTrue(detect.contains("✓ Visual Studio Code"), detect);
        assertTrue(detect.contains("✗ Emacs"), detect);
        assertTrue(detect.contains("kof editor setup"), detect);

        bo.reset();
        assertEquals(0, CmdEditor.run(new String[]{"editor", "status"}, ctx, out, out));
        String status = bo.toString(StandardCharsets.UTF_8);
        assertTrue(status.contains("debugger: PARTIAL"), status);
        assertTrue(status.contains("LSP: OK"), status);
        assertTrue(status.contains("version: 1.102.3"), status);
    }

    @Test
    void writeCommandsAreHonestNotSilent() {
        // R6: install/setup ainda não implementados → exit != 0 + mensagem clara
        var ctx = fake(Set.of(), Map.of(), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        assertEquals(2, CmdEditor.run(new String[]{"editor", "setup"}, ctx, out, out));
        assertTrue(bo.toString(StandardCharsets.UTF_8).contains("não implementado"));
        assertEquals(1, CmdEditor.run(new String[]{"editor", "bogus"}, ctx, out, out));
    }
}
