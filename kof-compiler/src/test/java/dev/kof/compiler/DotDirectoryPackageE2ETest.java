package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitHub #999 (open `1.0-blocks`) — a source under a directory whose name is
 * NOT a valid Kof identifier (a dot-prefixed `.agent/`, a hyphenated
 * `.hidden-x/`) derived a package containing that segment (`.agent`), so the
 * JVM internal name became `/agent/Box` (absolute → `outputDir.resolve`
 * discards the output dir → EACCES, mislabeled `COMP001`) or `sub//agent/Box`
 * (double slash → `ClassFormatError: Illegal class name` at load, a FALSE
 * GREEN from `kof check`). The JS target was unaffected — a target divergence.
 *
 * <p>Contract (`docs/language-reference/modules.md` §1/§2): `package` is
 * optional; absent → package `""` (the default package). A directory name
 * that is not a valid identifier cannot be a package, so the file belongs to
 * the default package — the same rule the issue's expected behavior asks for
 * (`ok` on both targets).</p>
 *
 * <p>RED-first: before the fix the JVM compile fails with `COMP001` (the
 * `.agent` case) or emits an illegal internal name (the `sub/.agent` case).</p>
 */
class DotDirectoryPackageE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path root, String rel, String src) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent() == null ? root : f.getParent());
        Files.writeString(f, src);
        return f;
    }

    private CompilationResult compileJvm(Path root, List<Path> sources, String tag) {
        Path outDir = root.resolve("out-" + tag);
        return driver.compileSources(sources, outDir, Target.JVM, root);
    }

    private void runJvm(Path root, String tag, String expected) throws Exception {
        Path outDir = root.resolve("out-" + tag);
        Path javaHome = Path.of(System.getProperty("java.home"));
        Process p = new ProcessBuilder(javaHome.resolve("bin").resolve("java").toString(),
                "-cp", outDir.toAbsolutePath().toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail(tag + " JVM run hung (>30s)");
        }
        assertEquals(0, p.exitValue(), tag + " JVM exit, output: " + output);
        assertEquals(expected, output, tag + " JVM output");
    }

    @Test
    void dotPrefixedDirectoryWithClassCompilesAndRuns(@TempDir Path tmp) throws Exception {
        // The issue's exact reproducer: a class in `.agent/Main.kf` with a
        // kof.toml at the project root (moduleRoot = tmp).
        Files.writeString(tmp.resolve("kof.toml"), "");
        Path main = write(tmp, ".agent/Main.kf", """
                class Box {}

                main() {
                    println("ok")
                }
                """);
        CompilationResult result = compileJvm(tmp, List.of(main), "dot");
        assertTrue(result.success(), "dot: compile failed: " + result.diagnostics().getDiagnostics());
        runJvm(tmp, "dot", "ok");
    }

    @Test
    void dotPrefixedNestedDirectoryCompilesAndRuns(@TempDir Path tmp) throws Exception {
        // The second, worse face: `sub/.agent/Main.kf` used to emit
        // `sub//agent/Box` (ClassFormatError at load) while `kof check`
        // reported "no errors" — a false green.
        Files.writeString(tmp.resolve("kof.toml"), "");
        Path main = write(tmp, "sub/.agent/Main.kf", """
                class Box {}

                main() {
                    println("nested")
                }
                """);
        CompilationResult result = compileJvm(tmp, List.of(main), "nested");
        assertTrue(result.success(), "nested: compile failed: " + result.diagnostics().getDiagnostics());
        runJvm(tmp, "nested", "nested");
    }

    @Test
    void hyphenatedDirectoryCompilesAndRuns(@TempDir Path tmp) throws Exception {
        // `.hidden-x/` (the issue's other measured name) — a hyphen is not a
        // valid identifier character either.
        Files.writeString(tmp.resolve("kof.toml"), "");
        Path main = write(tmp, ".hidden-x/Main.kf", """
                class Box {}

                main() {
                    println("hyphen")
                }
                """);
        CompilationResult result = compileJvm(tmp, List.of(main), "hyphen");
        assertTrue(result.success(), "hyphen: compile failed: " + result.diagnostics().getDiagnostics());
        runJvm(tmp, "hyphen", "hyphen");
    }

    @Test
    void validDirectoryStillDerivesItsPackage(@TempDir Path tmp) throws Exception {
        // Control: a valid identifier directory keeps the directory-is-a-package
        // rule (no over-application of the fix). `main()` always lands in
        // `Default/Main` (the merged unit's package is ""); the CLASS carries
        // the derived package.
        Files.writeString(tmp.resolve("kof.toml"), "");
        Path main = write(tmp, "agent/Main.kf", """
                class Box {}

                main() {
                    println("valid")
                }
                """);
        CompilationResult result = compileJvm(tmp, List.of(main), "valid");
        assertTrue(result.success(), "valid: compile failed: " + result.diagnostics().getDiagnostics());
        Path outDir = tmp.resolve("out-valid");
        assertTrue(Files.exists(outDir.resolve("agent/Box.class")),
                "valid: agent/Box.class ausente (o pacote valido foi perdido)");
        assertFalse(Files.exists(outDir.resolve("Box.class")),
                "valid: Box.class na raiz (o pacote valido foi descartado)");
    }

    @Test
    void noKofTomlDotDirectoryStillCompiles(@TempDir Path tmp) throws Exception {
        // Control: without kof.toml the module root is the file's own directory
        // (the CLI's LCA rule), so the derived package is already "" — the fix
        // must not regress it.
        Path main = write(tmp, ".agent/Main.kf", """
                class Box {}

                main() {
                    println("no-toml")
                }
                """);
        CompilationResult result = compileJvm(main.getParent(), List.of(main), "notoml");
        assertTrue(result.success(), "notoml: compile failed: " + result.diagnostics().getDiagnostics());
        runJvm(main.getParent(), "notoml", "no-toml");
    }
}
