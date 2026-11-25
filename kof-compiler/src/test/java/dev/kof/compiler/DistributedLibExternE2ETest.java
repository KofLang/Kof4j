package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §1224 / issue #1191 — an `extern "libX.so"` with a BARE library
 * name (no `/`) was turned into `-l:libX.so` and the ld searched only its own
 * system dirs: a lib the APP DISTRIBUTES (side by side with the sources, e.g.
 * `libs/libkof_text.so`) failed the link with `cannot find -l:libkof_text.so`
 * while the SAME source with the absolute path linked and ran fine — the
 * absolute path had to be CRAMMED into the source (the reporter's shim).
 *
 * <p>Contract under test: the bare-name extern resolves FIRST against the
 * module's own library dirs (the module root, its `libs/` subdirectory and the
 * source directory) — a lib distributed by the app links and runs from a
 * source with NO absolute path at all.
 */
class DistributedLibExternE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void bareNameExternResolvesAgainstModuleLibs() throws Exception {
        // the shipped lib: a REAL .so next to the app's source tree
        Path libs = tmp.resolve("libs");
        Files.createDirectories(libs);
        Path src = tmp.resolve("main.kf");
        Files.writeString(src, """
                extern "libkof_text.so" kt_open(Buffer(U8) path, Int px): Long

                main() {
                    var b = buffer.alloc(64)
                    var face = kt_open(b, 14)
                    println(face)
                }
                """);
        // the REAL lib of the repo (the very one the reporter's workaround ships)
        copyRealTextLib(libs.resolve("libkof_text.so"));

        Path out = tmp.resolve("out-native");
        // moduleRoot = tmp (the `libs/` sibling of the source is searched)
        CompilationResult r = driver.compileSources(java.util.List.of(src), out, Target.NATIVE, tmp);
        assertTrue(r.success(),
                "known-bugs §1224: a bare-name extern must resolve against the module's own "
                        + "library dirs (the ld was searching only the system dirs): "
                        + r.diagnostics().getDiagnostics());
        assertTrue(Files.exists(out.resolve("Default/Main")),
                "the native binary must be produced with the module's lib");
    }

    /** Copies the repo's real libkof_text.so (measured: 17720 bytes, the app-face text lib). */
    private static void copyRealTextLib(Path dest) throws Exception {
        Path real = Path.of("libs/enois/libkof_text.so");
        assumeTrueReal(real, dest);
    }

    private static void assumeTrueReal(Path real, Path dest) throws Exception {
        if (!java.nio.file.Files.isRegularFile(real)) {
            org.junit.jupiter.api.Assumptions.abort(
                    "a lib real do repo não existe: " + real.toAbsolutePath());
        }
        Files.copy(real, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
