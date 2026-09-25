package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §491 — the FIELD sibling of #617/§490. The pseudo-types with a dedicated typer
 * branch ({@code Buffer}, {@code Secret}, {@code KeyHandle} from
 * kof.buffer/kof.security and {@code File}/{@code Path}/{@code Directory} from
 * kof.io) have NO property form: their accessors are methods. An unknown field
 * access compiled clean and fell through to the generic {@code ClassType} path,
 * which emitted {@code getfield <receiver>.<name>} against a class that does not
 * exist in the runtime ({@code kof/Buffer}, {@code kof/Secret},
 * {@code kof/io/File}) — a {@code NoClassDefFoundError} at class load (hidden
 * behind the JavaFX launcher message) that no diagnostic warned about.
 *
 * <p>This class pins the contract: an unknown field on those builtins is a clean
 * {@code SEM102} at compile time, and the valid property faces elsewhere
 * ({@code String.length}/{@code name}/{@code path}, {@code List.size},
 * {@code Map.size}, array {@code length}, record/class fields) keep compiling.
 */
class BuiltinUnknownFieldGuardTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String source, Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        return driver.compile(src, tempDir.resolve("out"), Target.JVM);
    }

    private void assertSem102Field(CompilationResult result, String field, String type) {
        assertFalse(result.success(), type + "." + field + " must fail to compile (SEM102)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "Expected SEM102, was: " + diags);
        assertTrue(diags.contains(type), "Diagnostic must name the type, was: " + diags);
        assertTrue(diags.contains(field), "Diagnostic must name the field, was: " + diags);
        assertFalse(diags.contains("ClassFormatError"),
                "Must be a compile diagnostic, not a crash: " + diags);
    }

    // ---- diagnosis: the invalid-bytecode faces become SEM102 ----

    @Test
    void unknownBufferFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var b = buffer.alloc(8)
                println(b.bogus)
            }
            """, tempDir), "bogus", "Buffer");
    }

    @Test
    void unknownSecretFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var s = secrets.of("x")
                println(s.bogus)
            }
            """, tempDir), "bogus", "Secret");
    }

    @Test
    void unknownKeyHandleFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var k = secrets.keyFromHex("00")
                println(k.bogus)
            }
            """, tempDir), "bogus", "KeyHandle");
    }

    @Test
    void unknownFileFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var f = File("x")
                println(f.bogus)
            }
            """, tempDir), "bogus", "File");
    }

    @Test
    void unknownPathFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var p = Path("x")
                println(p.bogus)
            }
            """, tempDir), "bogus", "Path");
    }

    @Test
    void unknownDirectoryFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var d = Directory("x")
                println(d.bogus)
            }
            """, tempDir), "bogus", "Directory");
    }

    @Test
    void plausibleButInvalidPathFieldIsSem102(@TempDir Path tempDir) throws IOException {
        // `path` is a METHOD (`File("x").path()`); the field form used to emit
        // `getfield kof/io/File.path` against a nonexistent class.
        assertSem102Field(compile("""
            main() {
                var f = File("x")
                println(f.path)
            }
            """, tempDir), "path", "File");
    }

    // ---- diagnosis: the WRITE face (§491 face b) also becomes SEM102 ----

    @Test
    void unknownBufferFieldWriteIsSem102(@TempDir Path tempDir) throws IOException {
        // simple assignment emitted `putfield kof/Buffer.bogus`.
        assertSem102Field(compile("""
            main() {
                var b = buffer.alloc(8)
                b.bogus = 1
            }
            """, tempDir), "bogus", "Buffer");
    }

    @Test
    void unknownFileFieldCompoundWriteIsSem102(@TempDir Path tempDir) throws IOException {
        // compound assignment (`+=`) went through the same assignment analyzer.
        assertSem102Field(compile("""
            main() {
                var f = File("x")
                f.bogus += 1
            }
            """, tempDir), "bogus", "File");
    }

    @Test
    void unknownSecretFieldWriteIsSem102(@TempDir Path tempDir) throws IOException {
        // a reference RHS (secret) also slipped through.
        assertSem102Field(compile("""
            main() {
                var s = secrets.of("x")
                s.bogus = secrets.of("y")
            }
            """, tempDir), "bogus", "Secret");
    }

    @Test
    void unknownBufferFieldIncrementIsSem102(@TempDir Path tempDir) throws IOException {
        // `++` reads the field first, so it was already caught by the READ guard.
        assertSem102Field(compile("""
            main() {
                var b = buffer.alloc(8)
                b.bogus++
            }
            """, tempDir), "bogus", "Buffer");
    }

    // ---- diagnosis: the NAMESPACE field face (§496) also becomes SEM102 ----

    private void assertNamespaceFieldIsSem102(CompilationResult result, String ns, String field) {
        assertFalse(result.success(), ns + "." + field + " must fail to compile (SEM102)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "Expected SEM102, was: " + diags);
        assertTrue(diags.contains(ns), "Diagnostic must name the namespace, was: " + diags);
        assertTrue(diags.contains(field), "Diagnostic must name the field, was: " + diags);
        assertFalse(diags.contains("ClassFormatError"),
                "Must be a compile diagnostic, not a crash: " + diags);
    }

    @Test
    void unknownMathFieldIsSem102(@TempDir Path tempDir) throws IOException {
        // `math.bogus` compiled clean and emitted `getfield "?".bogus`.
        assertNamespaceFieldIsSem102(compile("""
            main() {
                println(math.bogus)
            }
            """, tempDir), "math", "bogus");
    }

    @Test
    void plausibleButInvalidNamespaceConstantIsSem102(@TempDir Path tempDir) throws IOException {
        // There is no namespace constant: `math.PI` is as invalid as `.bogus`.
        assertNamespaceFieldIsSem102(compile("""
            main() {
                println(math.PI)
            }
            """, tempDir), "math", "PI");
    }

    @Test
    void unknownStringsNamespaceFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertNamespaceFieldIsSem102(compile("""
            main() {
                println(strings.EMPTY)
            }
            """, tempDir), "strings", "EMPTY");
    }

    @Test
    void unknownTimeNamespaceFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertNamespaceFieldIsSem102(compile("""
            main() {
                println(time.EPOCH)
            }
            """, tempDir), "time", "EPOCH");
    }

    @Test
    void namespaceMethodCallsStillCompile(@TempDir Path tempDir) throws IOException {
        // control: the FUNCTION surface of the namespaces is untouched — only the
        // field form is rejected.
        CompilationResult result = compile("""
            main() {
                println(math.sqrt(4.0))
                println(math.abs(-1))
                println(strings.capitalize("hi"))
                println(time.now())
            }
            """, tempDir);
        assertTrue(result.success(), "Namespace method calls must compile: "
                + result.diagnostics().getDiagnostics());
    }

    // ---- control: the real property faces still compile ----

    @Test
    void validPropertyFacesStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            record Point(Int x, Int y)
            main() {
                var s = "hello"
                println(s.length)
                println(s.name)
                println(s.path)
                var l = listOf(1, 2)
                println(l.size)
                var m = mapOf("a", 1)
                println(m.size)
                var a = new Int[3]
                println(a.length)
                var p = Point(1, 2)
                println(p.x)
            }
            """, tempDir);
        assertTrue(result.success(), "Valid property faces must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void validClassFieldWriteStillCompiles(@TempDir Path tempDir) throws IOException {
        // control for the WRITE guard: a genuine mutable class field still
        // accepts `=` and `+=`.
        CompilationResult result = compile("""
            class Counter {
                Int n
                public constructor(Int n) { this.n = n }
            }
            main() {
                var c = Counter(1)
                c.n = 5
                c.n += 2
                println(c.n)
            }
            """, tempDir);
        assertTrue(result.success(), "Valid class field writes must compile: "
                + result.diagnostics().getDiagnostics());
    }

    // ---- §503: unknown field on Channel<T>/Handle<T> (kof.concurrent) ----

    @Test
    void unknownChannelFieldIsSem102(@TempDir Path tempDir) throws IOException {
        // `c.bogusField` compiled clean and emitted
        // `getfield java/util/concurrent/LinkedBlockingQueue.bogusField`
        // → NoSuchFieldError.
        assertSem102Field(compile("""
            main() {
                val c = channel<Int>()
                println(c.bogusField)
            }
            """, tempDir), "bogusField", "Channel");
    }

    @Test
    void unknownHandleFieldIsSem102(@TempDir Path tempDir) throws IOException {
        // `h.bogusField` compiled clean and emitted a getfield on the
        // backing CompletableFuture → NoSuchFieldError.
        assertSem102Field(compile("""
            main() {
                val h = spawn { return 1 }
                println(h.bogusField)
            }
            """, tempDir), "bogusField", "Handle");
    }

    @Test
    void validChannelAndHandleFacesStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                val c = channel<Int>()
                c.send(1)
                println(c.receive())
                val h = spawn { return 42 }
                println(await h)
            }
            """, tempDir);
        assertTrue(result.success(), "Valid Channel send/receive and await must compile: "
                + result.diagnostics().getDiagnostics());
    }

    // ---- diagnosis: the kof.ui field face (§498) also becomes SEM079 ----

    private void assertUiFieldIsSem079(CompilationResult result, String field, String type) {
        assertFalse(result.success(), type + "." + field + " must fail to compile (SEM079)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM079"), "Expected SEM079, was: " + diags);
        assertTrue(diags.contains(field), "Diagnostic must name the field, was: " + diags);
        assertFalse(diags.contains("ClassFormatError"),
                "Must be a compile diagnostic, not a crash: " + diags);
    }

    @Test
    void unknownPaletteMemberIsSem079(@TempDir Path tempDir) throws IOException {
        // `Palette.bogus` fell through to the generic path and emitted
        // `getfield "?".bogus`.
        assertUiFieldIsSem079(compile("""
            main() {
                println(Palette.bogus)
            }
            """, tempDir), "bogus", "Palette");
    }

    @Test
    void unknownColorFieldIsSem079(@TempDir Path tempDir) throws IOException {
        // Color is a UI constructor (Color.rgba(...)); it has no fields.
        assertUiFieldIsSem079(compile("""
            main() {
                println(Color.bogus)
            }
            """, tempDir), "bogus", "Color");
    }

    @Test
    void unknownThemeFieldIsSem079(@TempDir Path tempDir) throws IOException {
        // Theme is a UI constructor (Theme.light()/dark()); it has no fields.
        assertUiFieldIsSem079(compile("""
            main() {
                println(Theme.bogus)
            }
            """, tempDir), "bogus", "Theme");
    }

    @Test
    void validUiFacesStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                println(Palette.red)
                println(Color.rgba(1, 2, 3, 4))
                println(Theme.light())
            }
            """, tempDir);
        assertTrue(result.success(), "Valid Palette/Color/Theme faces must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void ioAccessorsAsMethodsStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                var f = File("x")
                println(f.path())
                println(f.size())
                var p = Path("x")
                println(p.path())
            }
            """, tempDir);
        assertTrue(result.success(), "The io accessor METHODS must compile: "
                + result.diagnostics().getDiagnostics());
    }
}
