package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Packaging guard for the #814 family (known-bugs §656, audit `VER-01`): the
 * 0.6.0 cut bumped VERSION+pom but skipped version.properties, so the published
 * CLI reported 0.5.0-beta while the distribution was 0.6.0. The source of truth
 * is the VERSION file at the repository root (scripts/bump-version.sh header);
 * this test pins that pom.xml, the packaged properties and the KofVersion
 * runtime face the CLI prints can never ship diverged from it again.
 *
 * <p>Contract detail (scripts/bump-version.sh): {@code kof.version} carries the
 * FULL version (release phase included, e.g. {@code 0.5.0-beta}) while the three
 * components carry the PHASE-STRIPPED version ({@code 0.5.0}). Asserting the
 * components against the full string false-fails on every pre-release bump
 * (measured: {@code VERSION=0.6.0-beta} + {@code kof.compiler.version=0.6.0}
 * → {@code expected: <0.6.0-beta> but was: <0.6.0>}).
 */
class KofVersionParityTest {

    private static Path findRepoRoot() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("VERSION"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("repo root with the VERSION file not found from "
                    + Path.of("").toAbsolutePath());
        }
        return dir;
    }

    private static String component(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    private static Properties readPackagedProps(Path root) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(
                root.resolve("kof-compiler/src/main/resources/dev/kof/version.properties"))) {
            props.load(in);
        }
        return props;
    }

    @Test
    void packagedVersionPropertiesAgreesWithTheRepoVersion() throws IOException {
        Path root = findRepoRoot();
        String version = Files.readString(root.resolve("VERSION")).trim();
        String component = component(version);
        Properties props = readPackagedProps(root);
        assertEquals(version, props.getProperty("kof.version"), "kof.version diverges from VERSION");
        assertEquals(component, props.getProperty("kof.compiler.version"), "kof.compiler.version diverges from VERSION");
        assertEquals(component, props.getProperty("kof.runtime.version"), "kof.runtime.version diverges from VERSION");
        assertEquals(component, props.getProperty("kof.stdlib.version"), "kof.stdlib.version diverges from VERSION");
    }

    @Test
    void pomRevisionAgreesWithTheRepoVersion() throws IOException {
        Path root = findRepoRoot();
        String version = Files.readString(root.resolve("VERSION")).trim();
        Matcher m = Pattern.compile("<revision>([^<]+)</revision>")
                .matcher(Files.readString(root.resolve("pom.xml")));
        assertTrue(m.find(), "pom.xml must declare <revision>");
        assertEquals(version, m.group(1).trim(),
                "pom.xml <revision> diverges from VERSION — run scripts/bump-version.sh");
    }

    @Test
    void kofVersionRuntimeFaceReportsTheRepoVersion() throws IOException {
        String version = Files.readString(findRepoRoot().resolve("VERSION")).trim();
        String component = component(version);
        assertEquals(version, KofVersion.version(), "KofVersion.version() (the `kof version` face) diverges from VERSION");
        assertEquals(component, KofVersion.compiler(), "KofVersion.compiler() diverges from VERSION");
        assertEquals(component, KofVersion.runtime(), "KofVersion.runtime() diverges from VERSION");
        assertEquals(component, KofVersion.stdlib(), "KofVersion.stdlib() diverges from VERSION");
    }

    /**
     * #1142 (#1143): the README "Current State" version must equal VERSION. The
     * first PR face put a `<!-- version -->` marker in the README but no `sed`
     * pattern that ever matched it (the README stayed at 0.6.1 while VERSION was
     * 0.7.2), so the sync script was silent about a broken step. This pins the
     * marker AND its value in both languages, and pins the script step itself.
     */
    @Test
    void readmeCurrentStateAgreesWithTheRepoVersion() throws IOException {
        Path root = findRepoRoot();
        String version = Files.readString(root.resolve("VERSION")).trim();
        for (String readme : new String[] {"README.md", "README.pt_BR.md"}) {
            String text = Files.readString(root.resolve(readme));
            Matcher m = Pattern.compile("<!-- version -->(.*?)<!-- end_version -->").matcher(text);
            assertTrue(m.find(), readme + " must mark the current version with <!-- version -->…<!-- end_version -->");
            assertEquals("**" + version + "**", m.group(1).trim(),
                    readme + " Current State version diverges from VERSION — run scripts/bump-version.sh");
        }
        String script = Files.readString(root.resolve("scripts/bump-version.sh"));
        assertTrue(script.contains("readme") || script.contains("README.md"),
                "scripts/bump-version.sh must keep both READMEs in sync");
        assertFalse(script.contains("s|<!-- version -->**.***"),
                "the README step must not use the pre-#1143 pattern that never matched");
    }
}
