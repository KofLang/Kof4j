package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §8 do plano Kof Connector Ecosystem ({@code D-CONNECTORS-GO}): {@code kof
 * connector init <dir>} — a metade CLI do gerador de conectores. A politica
 * (render do manifest §4.2) vive na lib Kof pura {@code interop.ConnectorTemplate};
 * o CLI so cria a estrutura §8 e dispara a lib.
 *
 * <p>RED pre-ligacao: {@code kof connector init} respondia {@code unknown:
 * connector} e saia 0 (no-op silencioso, R6). Q3: a estrutura existe; o manifest
 * gerado e relido por {@code ConnectorManifest.validate()} (round-trip real);
 * opcoes aplicam; manifesto existente e recusado; flag desconhecida e recusada.</p>
 */
class CmdConnectorInitTest {

    @Test
    void initCreatesScaffoldAndManifest(@TempDir Path dir) throws Exception {
        Path conn = dir.resolve("my-conn");
        assertEquals(0, CmdConnector.run(new String[]{
                "connector", "init", conn.toString(),
                "--name", "kof-cc", "--language", "c", "--runtime", "native"}));
        assertTrue(Files.exists(conn.resolve("kof-connector.toml")), "manifest must exist");
        for (String d : List.of("bindings", "runtime", "types", "tests", "docs")) {
            assertTrue(Files.isDirectory(conn.resolve(d)), "§8 dir must exist: " + d);
        }
        String manifest = Files.readString(conn.resolve("kof-connector.toml"));
        assertTrue(manifest.contains("name = \"kof-cc\""), manifest);
        assertTrue(manifest.contains("language = \"c\""), manifest);
        assertTrue(manifest.contains("runtime = \"native\""), manifest);
    }

    @Test
    void defaultNameComesFromDirectory(@TempDir Path dir) throws Exception {
        Path conn = dir.resolve("acme-db");
        assertEquals(0, CmdConnector.run(new String[]{"connector", "init", conn.toString()}));
        assertTrue(Files.readString(conn.resolve("kof-connector.toml"))
                .contains("name = \"acme-db\""), "default name = dir name");
    }

    @Test
    void generatedManifestRoundTripsThroughConnectorManifest(@TempDir Path dir) throws Exception {
        Path conn = dir.resolve("rt");
        assertEquals(0, CmdConnector.run(new String[]{
                "connector", "init", conn.toString(),
                "--name", "kof-rt", "--language", "c", "--version", "2.0",
                "--abi", "c", "--runtime", "native"}));
        Path manifest = conn.resolve("kof-connector.toml");

        String probe = """
            import interop.ConnectorManifest

            main() {
                var m = ConnectorManifest("%s")
                m.validate()
                println("name=" + m.name())
                println("language=" + m.language())
                println("version=" + m.version())
                println("abi=" + m.abi())
                println("runtime=" + m.runtime())
                println("valid=ok")
            }
            """.formatted(manifest.toString().replace('\\', '/'));
        Path src = Files.createTempDirectory("connector-probe-src-");
        Path out = Files.createTempDirectory("connector-probe-out-");
        try {
            URLClassLoader loader = InteropLibrary.compile("Main.kf", probe, src, out);
            assertNotNull(loader, "interop library must compile the validation probe");
            var stdout = new ByteArrayOutputStream();
            var previous = System.out;
            try {
                System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
                try (loader) {
                    Class.forName("Default.Main", true, loader)
                            .getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                }
            } finally {
                System.setOut(previous);
            }
            String output = stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
            assertEquals(String.join("\n",
                    "name=kof-rt", "language=c", "version=2.0",
                    "abi=c", "runtime=native", "valid=ok"), output);
        } finally {
            KofCliSupport.cleanup(src);
            KofCliSupport.cleanup(out);
        }
    }

    @Test
    void existingManifestIsRefused(@TempDir Path dir) {
        Path conn = dir.resolve("twice");
        assertEquals(0, CmdConnector.run(new String[]{"connector", "init", conn.toString()}));
        String before;
        try {
            before = Files.readString(conn.resolve("kof-connector.toml"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertFalse(CmdConnector.run(new String[]{"connector", "init", conn.toString()}) == 0,
                "second init must be refused");
        try {
            assertEquals(before, Files.readString(conn.resolve("kof-connector.toml")), "never overwrite");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void unknownFlagIsRefused(@TempDir Path dir) {
        assertFalse(CmdConnector.run(new String[]{
                "connector", "init", dir.resolve("x").toString(), "--bogus"}) == 0);
        assertFalse(Files.exists(dir.resolve("x/kof-connector.toml")));
    }

    @Test
    void connectorInitMissingDirIsUsageNotCrash() {
        assertFalse(CmdConnector.run(new String[]{"connector", "init"}) == 0);
    }

    @Test
    void unknownSubcommandIsRefused() {
        assertFalse(CmdConnector.run(new String[]{"connector", "frobnicate"}) == 0);
    }
}
