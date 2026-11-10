package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static dev.kof.cli.DepsRegistrySupport.runWithEnv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §783 — {@code kof test} never set an external classpath, so a test that
 * reaches a {@code kofdeps} jar failed with PKG006 (and could not have run
 * without the jar on the child JVM classpath). {@code test --deps} compiles
 * and runs the suite with the same jars and source roots as
 * {@code run --deps}.
 */
class CmdTestDepsTest {

    @Test
    void testWithDepsCompilesAndRunsAgainstTheKofdepsJars(@TempDir Path tmp) throws Exception {
        Path home = tmp.resolve("home");
        CmdCheckDepsTest.cachedJar(home);
        Path proj = tmp.resolve("proj");
        Files.createDirectories(proj.resolve("tests"));
        Files.writeString(proj.resolve("kofdeps"), "com.example:greeter:1.0\n");
        Files.writeString(proj.resolve("tests/GreeterTest.kf"), """
            import ext.Greeter
            test "the kofdeps class is reachable" {
                assert(Greeter.hello() == "hi")
            }
            """);
        Map<String, String> env = Map.of("HOMEOF", home.toString());

        var plain = runWithEnv(proj, env, "test", "tests");
        assertNotEquals(0, plain.exit(), plain.out());
        assertTrue(plain.out().contains("PKG006"), "without --deps the import stays PKG006:\n" + plain.out());

        var withDeps = runWithEnv(proj, env, "test", "tests", "--deps");
        assertEquals(0, withDeps.exit(), "test --deps must compile and run with the jar:\n" + withDeps.out());
    }
}
