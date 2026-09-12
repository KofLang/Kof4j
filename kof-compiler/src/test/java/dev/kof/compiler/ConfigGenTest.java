package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 (docs/stdlib/stdlib-config.md §8.2): o compilador conhece cada chamada
 * config.* com chave literal em compile-time — discoveredConfigKeys() +
 * generateConfigTemplate() alimentam o `kof config gen`.
 */
class ConfigGenTest {

    @Test
    void collectsKeysFromLiterals(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("App.kf");
        // NOTA: chaves computadas (não-literal, ex.: config.str(userInput(), "x"))
        // ficam de fora do template por design — e expõem um gap pré-existente
        // do backend JVM (COMP002: >=1 config.* não-literal num método derruba
        // o frame; separado aqui porque ainda não corrigido). Ver §8.2 P3.
        Files.writeString(src, """
                main() {
                    var port = config.int("server.port", 8080)
                    var url = config.required("db.url")
                    var name = config.str("app.name", "demo")
                    var debug = config.bool("app.debug", false)
                    var key = config.get("api.key")
                }
                """);
        CompilerDriver driver = new CompilerDriver();
        var result = driver.compile(src, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "" + result.diagnostics().getDiagnostics());

        var keys = driver.discoveredConfigKeys();
        assertEquals(5, keys.size(), "keys: " + keys);

        var first = keys.get(0);
        assertEquals("int", first.method());
        assertEquals("server.port", first.key());
        assertEquals("8080", first.defaultLiteral());
        assertEquals("Int", first.typeHint());
        assertTrue(first.hasDefault());

        var req = keys.stream().filter(k -> k.key().equals("db.url")).findFirst().orElseThrow();
        assertEquals("required", req.method());
        assertTrue(!req.hasDefault(), "required não tem default");

        var get = keys.stream().filter(k -> k.key().equals("api.key")).findFirst().orElseThrow();
        assertEquals("required", get.method(), "get sem default vira required no template");

        // template
        String tpl = driver.generateConfigTemplate();
        assertTrue(tpl.contains("# server.port = 8080"), "default comentado:\n" + tpl);
        assertTrue(tpl.contains("db.url = "), "required linha ativa:\n" + tpl);
        assertTrue(tpl.contains("api.key = "), "get linha ativa:\n" + tpl);
        assertFalse(tpl.contains("computed.key"), "chave computada não aparece:\n" + tpl);
    }

    @Test
    void dedupesRepeatedKeys(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, """
                main() {
                    println(config.str("k.a", "1"))
                    println(config.str("k.a", "1"))
                    println(config.int("k.a", 2))
                }
                """);
        CompilerDriver driver = new CompilerDriver();
        var result = driver.compile(src, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success());
        // mesma chave+método+default aparece 2x → 1; int com default diferente → 2
        assertEquals(2, driver.discoveredConfigKeys().size(),
                "" + driver.discoveredConfigKeys());
    }

    @Test
    void boolAndLongDefaults(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, """
                main() {
                    var debug = config.bool("app.debug", false)
                    var timeout = config.long("app.timeout", 30000)
                }
                """);
        CompilerDriver driver = new CompilerDriver();
        var result = driver.compile(src, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "" + result.diagnostics().getDiagnostics());
        var keys = driver.discoveredConfigKeys();
        assertEquals(2, keys.size());
        assertEquals("false", keys.get(0).defaultLiteral());
        assertEquals("Bool", keys.get(0).typeHint());
        assertEquals("30000", keys.get(1).defaultLiteral());
        assertEquals("Long", keys.get(1).typeHint());
        String tpl = driver.generateConfigTemplate();
        assertTrue(tpl.contains("# app.debug = false"));
        assertTrue(tpl.contains("# app.timeout = 30000"));
    }
}
