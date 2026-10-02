package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §534 E2E (RED-first): {@code kof run --target js} must provision the JDBC
 * driver exactly like {@code --target jvm}.
 *
 * <p>The KofJS guest runs IN-PROCESS through {@code KofJsRunner} on the CLI's
 * own classpath and reaches JDBC via {@code DriverManager}
 * ({@code KofJsDbBridge}); a provisioned/declared driver must therefore be on
 * that classpath (the JVM path appends it to its child classpath). Without the
 * fix the guest dies with {@code DB001: no JDBC driver}. The CLI now re-execs
 * itself with the provisioned jars appended.</p>
 *
 * <p>Offline: a real H2 jar is copied from {@code ~/.m2} into the kofdeps cache
 * up front, and every {@code h2} entry is removed from the CLI child classpath
 * so the bug cannot be masked by the test JVM's own driver (false green).</p>
 */
class JsRunDbDriverProvisionE2ETest {

    private static final String H2_VERSION = "2.5.250";

    @Test
    void jsRunProvisionsJdbcDriverLikeJvm(@TempDir Path dir) throws Exception {
        Path h2 = newestH2Jar();
        assumeTrue(h2 != null,
                "no H2 jar under ~/.m2/repository/com/h2database/h2 — cannot prove provisioning offline");

        Path cache = dir.resolve("cache");
        Path seeded = cache.resolve("com/h2database/h2/" + H2_VERSION + "/h2-" + H2_VERSION + ".jar");
        Files.createDirectories(seeded.getParent());
        Files.copy(h2, seeded, StandardCopyOption.REPLACE_EXISTING);

        String oldHome = System.getProperty("kof.deps.home");
        System.setProperty("kof.deps.home", cache.toString());
        try {
            // ORM needs an `entity` (not a plain `record`) so orm.create<T>
            // can generate the DDL; the generated type is still `Row`, so
            // orm.save(db, Row(1, "a")) / orm.count<Row>(db) are the same API
            // the JVM ORM E2E tests use.
            Path kf = dir.resolve("Db.kf");
            Files.writeString(kf, """
                entity Row {
                    id: Int
                    name: String
                }

                main() {
                    var db = db.connect("jdbc:h2:mem:jsprov;DB_CLOSE_DELAY=-1")
                    orm.create<Row>(db)
                    orm.save(db, Row(1, "a"))
                    println(orm.count<Row>(db))
                }
                """);

            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin());
            cmd.add("-Dkof.deps.home=" + cache);
            cmd.add("-cp");
            cmd.add(cliClasspathWithoutH2());
            cmd.add("dev.kof.cli.Main");
            cmd.add("run");
            cmd.add(kf.toString());
            cmd.add("--target");
            cmd.add("js");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertTrue(p.waitFor(300, TimeUnit.SECONDS), "kof run --target js timeout\n" + out);
            assertEquals(0, p.exitValue(), "kof run --target js rc\n" + out);
            assertTrue(out.lines().anyMatch(l -> l.trim().equals("1")),
                    "expected the ORM count '1' as a whole line, got: " + out);
        } finally {
            if (oldHome == null) System.clearProperty("kof.deps.home");
            else System.setProperty("kof.deps.home", oldHome);
        }
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /** The test JVM classpath with every H2 entry removed: if H2 were already
     *  on the CLI child classpath the bug would be masked (false green). */
    private static String cliClasspathWithoutH2() {
        StringBuilder sb = new StringBuilder();
        for (String entry : System.getProperty("java.class.path")
                .split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (entry.toLowerCase().contains("h2")) continue;
            if (sb.length() > 0) sb.append(java.io.File.pathSeparator);
            sb.append(entry);
        }
        return sb.toString();
    }

    /** Newest real H2 jar from the local Maven repository, or null if absent. */
    private static Path newestH2Jar() throws IOException {
        Path root = Path.of(System.getProperty("user.home", "."), ".m2", "repository",
                "com", "h2database", "h2");
        if (!Files.isDirectory(root)) return null;
        List<Path> jars = new ArrayList<>();
        try (var versions = Files.list(root)) {
            for (Path versionDir : versions.toList()) {
                if (!Files.isDirectory(versionDir)) continue;
                try (var files = Files.list(versionDir)) {
                    files.filter(x -> x.getFileName().toString().endsWith(".jar"))
                            .forEach(jars::add);
                }
            }
        }
        return jars.stream()
                .max(Comparator.comparing(x -> x.getParent().getFileName().toString()))
                .orElse(null);
    }
}
