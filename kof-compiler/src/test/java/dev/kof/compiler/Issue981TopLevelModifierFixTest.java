package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #981 — fix proof: a modifier (`public`/`private`/`protected`/`static`/...)
 * before a TOP-LEVEL function declaration used to die at parse time (PARSE007,
 * cascading to PARSE010/011/012/023/042/044 in the suffix-return-type form)
 * because the top-level dispatcher checked the token BEFORE any modifier, while
 * {@code TypeDeclarations.parseTypeDeclaration} (which DOES consume modifiers)
 * only knows how to continue into a type declaration. Fix:
 * {@code ParseContext.modifierPrefixedFunctionAhead()} + one new branch in
 * {@code Parser.parse()}, mirroring the already-working
 * {@code sealedModifierAhead}/{@code valueModifierAhead} pattern.
 *
 * NOT a regression test in production {@code kof-compiler} yet — runs only
 * against this isolated, patched copy.
 */
class Issue981TopLevelModifierFixTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit code, output: '" + output + "'");
            assertEquals(expected, output, "output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    // --- #981 fixed: modifier + top-level FUNCTION now compiles ----------------

    @Test
    void privateTopLevelFunctionPrefixedType(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                private Int secretDouble(Int x) {
                    return x * 2
                }
                Int publicUsesSecret(Int x) {
                    return secretDouble(x)
                }
                main() {
                    println(publicUsesSecret(5))
                }
                """);
        runJvm(source, tempDir.resolve("out"), "10");
    }

    @Test
    void privateTopLevelFunctionSuffixedType(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                private secretDouble(Int x): Int {
                    return x * 2
                }
                publicUsesSecret(Int x): Int {
                    return secretDouble(x)
                }
                main() {
                    println(publicUsesSecret(5))
                }
                """);
        runJvm(source, tempDir.resolve("out"), "10");
    }

    @Test
    void publicExplicitAndProtectedTopLevelFunctions(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                public Int a(Int x) {
                    return x + 1
                }
                protected Int b(Int x) {
                    return x + 2
                }
                main() {
                    println(a(1))
                    println(b(1))
                }
                """);
        runJvm(source, tempDir.resolve("out"), "2\n3");
    }

    @Test
    void combinedModifiersOnTopLevelFunction(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                public static Int triple(Int x) {
                    return x * 3
                }
                main() {
                    println(triple(4))
                }
                """);
        runJvm(source, tempDir.resolve("out"), "12");
    }

    @Test
    void privateTopLevelFunctionVoidAndNoExplicitReturnType(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                private void sideEffect() {
                    println("done")
                }
                main() {
                    sideEffect()
                }
                """);
        runJvm(source, tempDir.resolve("out"), "done");
    }

    // --- cross-file, same package (directory expansion) -------------------------

    @Test
    void privateTopLevelHelperVisibleAcrossFilesSamePackage(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("priv2"));
        Files.writeString(tempDir.resolve("priv2/Lib.kf"), """
                package priv2

                private Int secretDouble(Int x) {
                    return x * 2
                }

                Int publicUsesSecret(Int x) {
                    return secretDouble(x)
                }
                """);
        Path main = tempDir.resolve("Main.kf");
        Files.writeString(main, """
                import priv2

                main() {
                    println(publicUsesSecret(5))
                }
                """);
        runJvm(main, tempDir.resolve("out"), "10");
    }

    // --- regression: pre-existing paths must stay undisturbed -------------------

    @Test
    void modifierPrefixedTopLevelTypesStillWork(@TempDir Path tempDir) throws IOException {
        // `private class` on a TOP-LEVEL class is its own (pre-existing, unrelated
        // to #981) access question — each top-level class is its own compiled
        // class, so a private one is correctly unreachable even from `main()` in
        // the same file (measured: IllegalAccessError, same before/after this
        // fix). `public` is what actually exercises "modifier + type keyword
        // still dispatches to parseTypeDeclaration, undisturbed by the new branch".
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                public class Box {
                    Int v
                    constructor(Int v) {
                        this.v = v
                    }
                }
                public record Point(Int x, Int y)
                main() {
                    var b = new Box(7)
                    println(b.v)
                    var p = Point(1, 2)
                    println(p.x + p.y)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "7\n3");
    }

    @Test
    void sealedAndValueModifiersStillWork(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                sealed class Shape {}
                class Circle extends Shape {}
                value record V(Int x)
                main() {
                    var s = new Circle()
                    println(s instanceof Shape)
                    var v = V(9)
                    println(v.x)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "true\n9");
    }

    @Test
    void classMemberModifiersStillWorkUndisturbed(@TempDir Path tempDir) throws IOException {
        // `protected` correctly refuses a call from unrelated top-level code
        // (SEM046, pre-existing, unrelated to #981) — exercised here through an
        // intra-class caller instead, which still proves `protected` parses and
        // runs for a class member, undisturbed by the #981 fix.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class Calc {
                    private Int secretDouble(Int x) {
                        return x * 2
                    }
                    public Int useSecret(Int x) {
                        return secretDouble(x)
                    }
                    protected Int addOne(Int x) {
                        return x + 1
                    }
                    public Int useAddOne(Int x) {
                        return addOne(x)
                    }
                }
                main() {
                    var c = new Calc()
                    println(c.useSecret(5))
                    println(c.useAddOne(5))
                }
                """);
        runJvm(source, tempDir.resolve("out"), "10\n6");
    }

    @Test
    void plainTopLevelFunctionNoModifierStillWorks(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                Int plain(Int x) {
                    return x + 100
                }
                main() {
                    println(plain(1))
                }
                """);
        runJvm(source, tempDir.resolve("out"), "101");
    }
}
