package dev.kof.cli;

import dev.kof.cli.editor.DetectContext;
import dev.kof.cli.editor.KofEditorContent;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Issue #767: a grammar TextMate da distribuição precisa viajar para o editor.
 * O ctx de produção ({@link DetectContext#system()}) devolvia {@code null} em
 * {@code installDir()}, então {@link KofEditorContent#grammar} sempre caía no
 * {@code MINIMAL_GRAMMAR}. Estes testes usam o ctx REAL (só o
 * {@code kof.install.dir} é controlado) e o fake com {@code installDir}.
 */
class EditorSystemContextTest {

    /** Contexto fake com o diretório de instalação da distribuição controlado. */
    private static DetectContext fakeWithInstallDir(Path installDir, Set<String> installed) {
        return new DetectContext() {
            @Override
            public boolean whichExists(String exe) { return installed.contains(exe); }
            @Override
            public String whichPath(String exe) {
                return installed.contains(exe) ? "/fake/bin/" + exe : null;
            }
            @Override
            public String readVersion(String exe) { return "unknown"; }
            @Override
            public boolean dirExists(Path dir) { return dir != null && Files.isDirectory(dir); }
            @Override
            public boolean fileExists(Path file) { return file != null && Files.isRegularFile(file); }
            @Override
            public Path home() { return Path.of("/fake/home"); }
            @Override
            public List<String> candidateExes() { return List.of(); }
            @Override
            public String osName() { return "linux"; }
            @Override
            public Path installDir() { return installDir; }
        };
    }

    private static void writeDistGrammar(Path dist, String sentinel) throws IOException {
        Path editor = dist.resolve("editor");
        Files.createDirectories(editor);
        Files.writeString(editor.resolve("kof.tmLanguage.json"),
                "{\"name\":\"Kof\",\"scopeName\":\"source.kof\","
                        + "\"fileTypes\":[\"kf\",\"kof\"],\"patterns\":[{\"match\":\"" + sentinel + "\"}]}");
    }

    @Test
    void vscodeGrammarTravelsFromDistribution(@TempDir Path home, @TempDir Path dist) throws IOException {
        // §14: sem rede — a grammar da DISTRIBUIÇÃO é copiada para a extensão
        // (não o MINIMAL_GRAMMAR embutido). Issue #767.
        String sentinel = "kof.distribution.sentinel.767";
        writeDistGrammar(dist, sentinel);

        var ctx = fakeWithInstallDir(dist, Set.of("code"));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, true, StandardCharsets.UTF_8);
        assertEquals(0, CmdEditor.run(new String[]{"editor", "install", "vscode"}, ctx, home,
                new BufferedReader(new StringReader("")), out, out));
        String grammar = Files.readString(
                home.resolve(".vscode/extensions/kof.kof/syntaxes/kof.tmLanguage.json"));
        assertTrue(grammar.contains(sentinel),
                "a grammar da distribuição deve viajar, não o MINIMAL_GRAMMAR: " + grammar);
    }

    @Test
    void systemContextResolvesInstallDirFromProperty(@TempDir Path dist) {
        // O launcher instalado injeta -Dkof.install.dir=$HOME_DIR.
        String prev = System.getProperty("kof.install.dir");
        try {
            System.setProperty("kof.install.dir", dist.toString());
            DetectContext ctx = DetectContext.system();
            assertEquals(dist, ctx.installDir(),
                    "o ctx de produção deve resolver kof.install.dir (issue #767)");
        } finally {
            restoreInstallDir(prev);
        }
    }

    @Test
    void systemContextInstallsDistributionGrammarNotMinimal(@TempDir Path dist) {
        // RED em #767: SystemDetectContext não sobrescrevia installDir() → null
        // → grammar() sempre devolvia MINIMAL_GRAMMAR.
        String sentinel = "kof.distribution.sentinel.767";
        try {
            writeDistGrammar(dist, sentinel);
        } catch (IOException e) {
            fail("fixture da grammar: " + e.getMessage());
        }

        String prev = System.getProperty("kof.install.dir");
        try {
            System.setProperty("kof.install.dir", dist.toString());
            String grammar = KofEditorContent.grammar(DetectContext.system());
            assertTrue(grammar.contains(sentinel),
                    "a grammar da distribuição deve ser instalada (issue #767): " + grammar);
        } finally {
            restoreInstallDir(prev);
        }
    }

    private static void restoreInstallDir(String prev) {
        if (prev == null) System.clearProperty("kof.install.dir");
        else System.setProperty("kof.install.dir", prev);
    }
}
