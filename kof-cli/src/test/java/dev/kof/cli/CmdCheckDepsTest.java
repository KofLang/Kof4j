package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static dev.kof.cli.DepsRegistrySupport.runWithEnv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §772 — {@code kof check} never loaded the {@code kofdeps} classpath, so an
 * import of a third-party class resolved by {@code kof run --deps} failed with
 * PKG006 under {@code check}. {@code check --deps} now hands the compiler the
 * same jar classpath and source roots as {@code run --deps}.
 */
class CmdCheckDepsTest {

    private static final String GREETER = """
        package ext;
        public class Greeter { public static String hello() { return "hi"; } }
        """;

    private static final String MAIN = """
        import ext.Greeter
        main() { println(Greeter.hello()) }
        """;

    static Path cachedJar(Path home) throws Exception {
        Path src = home.resolve("src/ext");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Greeter.java"), GREETER);
        Path classes = home.resolve("cls");
        Files.createDirectories(classes);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-d", classes.toString(), src.resolve("Greeter.java").toString()));
        Path jar = home.resolve(".kof/deps/com/example/greeter/1.0/greeter-1.0.jar");
        Files.createDirectories(jar.getParent());
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            z.putNextEntry(new ZipEntry("ext/Greeter.class"));
            z.write(Files.readAllBytes(classes.resolve("ext/Greeter.class")));
        }
        return jar;
    }

    @Test
    void checkWithDepsSeesTheKofdepsJarsLikeRun(@TempDir Path tmp) throws Exception {
        Path home = tmp.resolve("home");
        cachedJar(home);
        Path proj = tmp.resolve("proj");
        Files.createDirectories(proj);
        Files.writeString(proj.resolve("kofdeps"), "com.example:greeter:1.0\n");
        Files.writeString(proj.resolve("Main.kf"), MAIN);
        Map<String, String> env = Map.of("HOMEOF", home.toString());

        var plain = runWithEnv(proj, env, "check", "Main.kf");
        assertNotEquals(0, plain.exit(), plain.out());
        assertTrue(plain.out().contains("PKG006"), "without --deps the import stays PKG006:\n" + plain.out());

        var withDeps = runWithEnv(proj, env, "check", "Main.kf", "--deps");
        assertEquals(0, withDeps.exit(), "check --deps must see the kofdeps jar:\n" + withDeps.out());
        assertTrue(withDeps.out().contains("checked 1 file(s) — no errors"), withDeps.out());

        var run = runWithEnv(proj, env, "run", "Main.kf", "--deps");
        assertEquals(0, run.exit(), "same classpath as run --deps:\n" + run.out());
        assertTrue(run.out().contains("hi"), run.out());
    }
}
