package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #1196: `kof check` de UM ARQUIVO em um pacote nomeado recusava com PKG004
 * falso ("package 'ui' does not match the directory ('')") — o check não
 * derivava a raiz do módulo e o pacote saía "". A flag `--module-root <dir>`
 * (a mesma do `kof test`) dá a raiz: o pacote deriva do caminho RELATIVO a ela
 * e o `package ui` do arquivo bate.
 */
class CmdCheckModuleRootTest {

    @Test
    void singleFileInNamedPackageChecksWithDeclaredRoot(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src.resolve("ui"));
        Files.writeString(src.resolve("main.kf"), """
                import ui.Abas

                main() {
                    println(Abas.titulo())
                }
                """);
        Files.writeString(src.resolve("ui/Abas.kf"), """
                package ui

                class Abas {
                    static String titulo() { return "Tudo" }
                }
                """);
        // COM a raiz: o check passa
        StringBuilder out = new StringBuilder();
        StringBuilder errBuf = new StringBuilder();
        int rc = CmdCheck.run(new String[]{"check", src.resolve("ui/Abas.kf").toString(),
                "--module-root", src.toString(), "--target", "jvm"},
                new java.io.PrintStream(new java.io.ByteArrayOutputStream() {
                    @Override public synchronized void write(int b) { out.append((char) b); }
                    @Override public synchronized void write(byte[] b, int off, int len) {
                        out.append(new String(b, off, len, java.nio.charset.StandardCharsets.UTF_8));
                    }
                }), new java.io.PrintStream(new java.io.ByteArrayOutputStream() {
                    @Override public synchronized void write(int b) { errBuf.append((char) b); }
                    @Override public synchronized void write(byte[] b, int off, int len) {
                        errBuf.append(new String(b, off, len, java.nio.charset.StandardCharsets.UTF_8));
                    }
                }));
        assertEquals(0, rc, out.toString());
        // o out (o buffer do comando) pode carregar a frase no stream de ERRO
        // (o KofCliSupport usa o err p/ os diagnostics): aceitar qualqu um.
        assertTrue(out.toString().contains("no errors") || errBuf.toString().contains("no errors"),
                out + " | " + errBuf);
    }
}
