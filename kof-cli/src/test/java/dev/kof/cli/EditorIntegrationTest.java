package dev.kof.cli;

import dev.kof.cli.editor.DetectContext;
import dev.kof.cli.editor.EditorInfo;
import dev.kof.cli.editor.EditorIntegration;
import dev.kof.cli.editor.EditorInstaller;
import dev.kof.cli.editor.EditorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        // integrationInstalled é por MARKER (não por config dir) — sem marker, false
        assertFalse(info.integrationInstalled());
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
        BufferedReader in = new BufferedReader(new StringReader(""));

        assertEquals(0, CmdEditor.run(new String[]{"editor", "list"}, ctx, null, in, out, out));
        String list = bo.toString(StandardCharsets.UTF_8);
        assertTrue(list.contains("Kof for VS Code"), list);
        assertTrue(list.contains("Kof mode for Emacs"), list);

        bo.reset();
        assertEquals(0, CmdEditor.run(new String[]{"editor", "detect"}, ctx, null, in, out, out));
        String detect = bo.toString(StandardCharsets.UTF_8);
        assertTrue(detect.contains("✓ Visual Studio Code"), detect);
        assertTrue(detect.contains("✗ Emacs"), detect);
        assertTrue(detect.contains("kof editor setup"), detect);

        bo.reset();
        assertEquals(0, CmdEditor.run(new String[]{"editor", "status"}, ctx, null, in, out, out));
        String status = bo.toString(StandardCharsets.UTF_8);
        assertTrue(status.contains("debugger: PARTIAL"), status);
        assertTrue(status.contains("LSP: OK"), status);
        assertTrue(status.contains("version: 1.102.3"), status);
    }

    @Test
    void unknownSubcommandFails() {
        var ctx = fake(Set.of(), Map.of(), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        assertEquals(1, CmdEditor.run(new String[]{"editor", "bogus"}, ctx, null,
                new BufferedReader(new StringReader("")), out, out));
    }

    // ---- degrau 3: install/uninstall/setup (escrevem só no tempDir, §24) --

    @Test
    void installWritesIdiomaticFiles_andIsIdempotent(@TempDir Path home) throws IOException {
        var ctx = fake(Set.of("nvim"), Map.of("nvim", "NVIM v0.10.0"), Set.of());
        EditorIntegration nvim = EditorRegistry.byId("neovim");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);

        assertEquals(0, CmdEditor.run(new String[]{"editor", "install", "neovim"}, ctx, home,
                new BufferedReader(new StringReader("")), out, out));
        assertTrue(EditorInstaller.isInstalled(home, "neovim"), "marker deve existir");
        Path ftplugin = home.resolve(".config/nvim/after/ftplugin/kof.lua");
        assertTrue(Files.isRegularFile(ftplugin), "ftplugin kof.lua: " + bo);
        String content = Files.readString(ftplugin);
        assertTrue(content.contains("vim.lsp.start"), "deve delegar ao LSP: " + content);
        assertTrue(content.contains("'lsp'"), "cmd do LSP: " + content);

        // idempotência: rodar de novo não muda nada
        bo.reset();
        assertEquals(0, CmdEditor.run(new String[]{"editor", "install", "neovim"}, ctx, home,
                new BufferedReader(new StringReader("")), out, out));
        assertTrue(bo.toString(StandardCharsets.UTF_8).contains("já atualizado"), bo.toString());
    }

    @Test
    void uninstallRemovesOnlyWhatWeWrote(@TempDir Path home) throws IOException {
        var ctx = fake(Set.of("nvim"), Map.of("nvim", "NVIM v0.10.0"), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        CmdEditor.run(new String[]{"editor", "install", "neovim"}, ctx, home,
                new BufferedReader(new StringReader("")), out, out);
        Path ftplugin = home.resolve(".config/nvim/after/ftplugin/kof.lua");
        assertTrue(Files.isRegularFile(ftplugin));
        // um arquivo que NÃO é nosso deve sobreviver
        Path foreign = home.resolve(".config/nvim/init.lua");
        Files.createDirectories(foreign.getParent());
        Files.writeString(foreign, "-- user config");

        bo.reset();
        assertEquals(0, CmdEditor.run(new String[]{"editor", "uninstall", "neovim"}, ctx, home,
                new BufferedReader(new StringReader("")), out, out));
        assertFalse(Files.isRegularFile(ftplugin), "nosso arquivo foi removido");
        assertTrue(Files.isRegularFile(foreign), "arquivo do usuário NÃO pode sumir");
        assertFalse(EditorInstaller.isInstalled(home, "neovim"));
    }

    @Test
    void setupInstallsRecommendedOnlyWithConsent(@TempDir Path home) {
        var ctx = fake(Set.of("code"), Map.of("code", "1.102.3"), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);

        // recusa (n) → nada instalado, mensagem de "depois"
        assertEquals(0, CmdEditor.run(new String[]{"editor", "setup"}, ctx, home,
                new BufferedReader(new StringReader("n\n")), out, out));
        assertFalse(EditorInstaller.isInstalled(home, "vscode"), "recusou → não instala");
        assertTrue(bo.toString(StandardCharsets.UTF_8).contains("kof editor setup"),
                "deve dizer como instalar depois");

        // aceita (y) → instala
        bo.reset();
        assertEquals(0, CmdEditor.run(new String[]{"editor", "setup"}, ctx, home,
                new BufferedReader(new StringReader("y\n")), out, out));
        assertTrue(EditorInstaller.isInstalled(home, "vscode"), "confirmou → instala");
        assertTrue(Files.isRegularFile(home.resolve(".vscode/extensions/kof.kof/package.json")));
    }

    @Test
    void vscodeGrammarTravelsFromDistribution(@TempDir Path home) throws IOException {
        // §14: sem rede — a grammar da distribuição é copiada para a extensão
        var ctx = fake(Set.of("code"), Map.of("code", "1.102.3"), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        assertEquals(0, CmdEditor.run(new String[]{"editor", "install", "vscode"}, ctx, home,
                new BufferedReader(new StringReader("")), out, out));
        String grammar = Files.readString(
                home.resolve(".vscode/extensions/kof.kof/syntaxes/kof.tmLanguage.json"));
        assertTrue(grammar.contains("source.kof"), "grammar válida: " + grammar.substring(0, Math.min(80, grammar.length())));
    }

    @Test
    void vscodeExtensionHasCommandsSnippetsAndValidJson(@TempDir Path home) throws IOException {
        // §3/§19: a extensão precisa de extension.js (senão "command not found")
        // + snippets; todo JSON gerado tem que ser parseável (JSON inválido
        // quebra a extensão silenciosamente).
        var ctx = fake(Set.of("code"), Map.of("code", "1.102.3"), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        assertEquals(0, CmdEditor.run(new String[]{"editor", "install", "vscode"}, ctx, home,
                new BufferedReader(new StringReader("")), out, out));
        Path ext = home.resolve(".vscode/extensions/kof.kof");
        assertTrue(Files.isRegularFile(ext.resolve("extension.js")), "extension.js presente");
        String js = Files.readString(ext.resolve("extension.js"));
        assertTrue(js.contains("registerCommand"), "registra comandos: " + js.substring(0, Math.min(60, js.length())));
        assertTrue(js.contains("kof.build") && js.contains("kof.selectTarget"), "comandos Kof: (§19)");
        assertTrue(js.contains("require('vscode')"), "usa API do vscode");
        assertTrue(Files.isRegularFile(ext.resolve("snippets/kof.json")), "snippets presentes (§3)");

        // JSONs parseáveis (parser do próprio projeto)
        for (String rel : List.of("package.json", "language-configuration.json",
                "snippets/kof.json", "syntaxes/kof.tmLanguage.json")) {
            Object parsed = Json.parse(Files.readString(ext.resolve(rel)));
            assertInstanceOf(Map.class, parsed, rel + " deve ser JSON objeto válido");
        }
        // package.json declara main + activationEvents + os 9 comandos
        String pkg = Files.readString(ext.resolve("package.json"));
        assertTrue(pkg.contains("\"main\": \"./extension.js\""), "declara entrypoint");
        assertTrue(pkg.contains("kof.startLsp"), "comando Start LSP (§19)");
    }

    // ---- degrau 11: hook pós-instalador (§13) -----------------------------

    @Test
    void postInstallHookNeverBlocksHeadless(@TempDir Path home) {
        // sem console (interactive=false): NÃO pergunta, NÃO instala, só aponta
        var ctx = fake(Set.of("code"), Map.of("code", "1.102.3"), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        assertEquals(0, CmdEditor.offerAfterInstall(ctx, home,
                new BufferedReader(new StringReader("")), out, out, false));
        String s = bo.toString(StandardCharsets.UTF_8);
        assertTrue(s.contains("kof editor setup"), s);
        assertFalse(s.contains("[Y/n]"), "headless não pergunta: " + s);
        assertFalse(EditorInstaller.isInstalled(home, "vscode"), "headless não instala");
    }

    @Test
    void postInstallHookRespectsDeclination(@TempDir Path home) {
        var ctx = fake(Set.of("code"), Map.of("code", "1.102.3"), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        assertEquals(0, CmdEditor.offerAfterInstall(ctx, home,
                new BufferedReader(new StringReader("n\n")), out, out, true));
        assertFalse(EditorInstaller.isInstalled(home, "vscode"), "recusou → nada instalado");
        assertTrue(bo.toString(StandardCharsets.UTF_8).contains("later"));
    }

    @Test
    void postInstallHookInstallsOnYes(@TempDir Path home) {
        var ctx = fake(Set.of("code"), Map.of("code", "1.102.3"), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        assertEquals(0, CmdEditor.offerAfterInstall(ctx, home,
                new BufferedReader(new StringReader("y\n")), out, out, true));
        assertTrue(EditorInstaller.isInstalled(home, "vscode"), "confirmou → instala");
    }

    @Test
    void postInstallHookSilentWhenNothingDetected(@TempDir Path home) {
        var ctx = fake(Set.of(), Map.of(), Set.of());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        assertEquals(0, CmdEditor.offerAfterInstall(ctx, home,
                new BufferedReader(new StringReader("")), out, out, true));
        assertEquals("", bo.toString(StandardCharsets.UTF_8), "nada detectado → silêncio");
    }
}
