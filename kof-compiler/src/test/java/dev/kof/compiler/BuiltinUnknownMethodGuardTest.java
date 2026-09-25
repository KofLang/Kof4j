package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #490 — the `kof.io` guard of #617 was not the only pseudo-type family with a
 * silent fall-through. `Buffer(U8)` (`kof.buffer`) and `Secret`/`KeyHandle`
 * (`kof.security`) have dedicated typer branches: a method absent from their
 * live table fell through to the generic resolution, which kept no contract —
 * the emit returned the RECEIVER (silent no-op: `s.bogus()` printed the Secret)
 * or leaked an UNKNOWN whose empty class name blew up at load
 * (`ClassFormatError: Illegal class name ""`). Both faces are R6 violations:
 * exactly the #617 mechanism, one family over.
 *
 * <p>This class pins the contract: an unknown method (or a known method with
 * the WRONG arity) on those builtins is a clean `SEM102` at compile time, and
 * the live tables keep compiling.
 */
class BuiltinUnknownMethodGuardTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String source, Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        return driver.compile(src, tempDir.resolve("out"), Target.JVM);
    }

    private void assertSem102(CompilationResult result, String method, String type) {
        assertFalse(result.success(), type + "." + method + "() must fail to compile (SEM102)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "Expected SEM102, was: " + diags);
        assertTrue(diags.contains(method), "Diagnostic must name the method, was: " + diags);
        assertFalse(diags.contains("ClassFormatError"),
                "Must be a compile diagnostic, not a crash: " + diags);
    }

    private void assertSem025(CompilationResult result, String method, String ns) {
        assertFalse(result.success(), ns + "." + method + "() must fail to compile (SEM025)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM025"), "Expected SEM025, was: " + diags);
        assertTrue(diags.contains(method), "Diagnostic must name the method, was: " + diags);
        assertFalse(diags.contains("VerifyError"),
                "Must be a compile diagnostic, not a crash: " + diags);
    }

    // ---- diagnosis: the silent/ClassFormatError faces become SEM102 ----

    @Test
    void unknownBufferMethodFailsWithSem102(@TempDir Path tempDir) throws IOException {
        // symptom A: `buffer.alloc(8).bogus()` printed `Buffer[8]` (receiver).
        assertSem102(compile("""
            main() {
                var b = buffer.alloc(8)
                println(b.bogus())
            }
            """, tempDir), "bogus", "Buffer");
    }

    @Test
    void unknownSecretMethodFailsWithSem102(@TempDir Path tempDir) throws IOException {
        // symptom A: `secrets.of("x").bogus()` printed `Secret(*** )` (receiver).
        assertSem102(compile("""
            main() {
                var s = secrets.of("x")
                println(s.bogus())
            }
            """, tempDir), "bogus", "Secret");
    }

    @Test
    void unknownSecretMethodToStringFailsWithSem102NotClassFormatError(@TempDir Path tempDir) throws IOException {
        // symptom B: `.bogus(1,2)` compiled and the JVM aborted at load with
        // `ClassFormatError: Illegal class name ""`.
        assertSem102(compile("""
            main() {
                var s = secrets.of("x")
                println(s.bogus(1, 2))
            }
            """, tempDir), "bogus", "Secret");
    }

    @Test
    void unknownKeyHandleMethodFailsWithSem102(@TempDir Path tempDir) throws IOException {
        assertSem102(compile("""
            main() {
                var k = secrets.keyFromHex("00")
                println(k.bogus())
            }
            """, tempDir), "bogus", "KeyHandle");
    }

    @Test
    void knownMethodWithWrongArityIsSem102(@TempDir Path tempDir) throws IOException {
        // `reveal()` takes zero args; `s.reveal(1)` was accepted silently and
        // returned the receiver. Wrong arity on a pseudo-type is now diagnosed.
        assertSem102(compile("""
            main() {
                var s = secrets.of("x")
                println(s.reveal(1))
            }
            """, tempDir), "reveal", "Secret");
    }

    // ---- §494: the same guard, for the `scheduler` NAMESPACE ----

    @Test
    void unknownSchedulerMethodFailsWithSem025(@TempDir Path tempDir) throws IOException {
        // symptom: `scheduler.bogus()` compiled clean; the lowerer returned
        // without emitting anything and the JVM aborted at load with
        // `VerifyError: Operand stack underflow`.
        assertSem025(compile("""
            main() {
                println(scheduler.bogus())
            }
            """, tempDir), "bogus", "scheduler");
    }

    // ---- §498: unknown method on the `web` namespace ----

    @Test
    void unknownWebNamespaceMethodFailsWithSem025(@TempDir Path tempDir) throws IOException {
        // `web.bogus()` compiled clean; the lowering emitted nothing (silent
        // no-op as a statement; as an expression the operand stack underflowed
        // and the JVM aborted at load with VerifyError).
        assertSem025(compile("""
            main() {
                println(web.bogus())
            }
            """, tempDir), "bogus", "web");
    }

    // ---- §499: unknown static method on a builtin type name ----

    private void assertSem074(CompilationResult result, String method, String type) {
        assertFalse(result.success(), type + "." + method + "() must fail to compile (SEM074)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM074"), "Expected SEM074, was: " + diags);
        assertTrue(diags.contains(method), "Diagnostic must name the method, was: " + diags);
        assertFalse(diags.contains("NoSuchMethodError"),
                "Must be a compile diagnostic, not a runtime crash: " + diags);
    }

    @Test
    void unknownStringStaticMethodFailsWithSem074(@TempDir Path tempDir) throws IOException {
        // `String.bogus()` compiled clean and emitted
        // `invokestatic java/lang/String.bogus` → NoSuchMethodError.
        assertSem074(compile("""
            main() {
                println(String.bogus())
            }
            """, tempDir), "bogus", "String");
    }

    @Test
    void unknownStaticMethodOnOtherBuiltinTypesFailsWithSem074(@TempDir Path tempDir) throws IOException {
        assertSem074(compile("main() { println(Int.bogus()) }\n", tempDir), "bogus", "Int");
        assertSem074(compile("main() { println(Long.bogus()) }\n", tempDir), "bogus", "Long");
        assertSem074(compile("main() { println(Float.bogus()) }\n", tempDir), "bogus", "Float");
        assertSem074(compile("main() { println(Double.bogus()) }\n", tempDir), "bogus", "Double");
        assertSem074(compile("main() { println(Bool.bogus()) }\n", tempDir), "bogus", "Bool");
        assertSem074(compile("main() { println(Char.bogus()) }\n", tempDir), "bogus", "Char");
        assertSem074(compile("main() { println(Byte.bogus()) }\n", tempDir), "bogus", "Byte");
        assertSem074(compile("main() { println(Short.bogus()) }\n", tempDir), "bogus", "Short");
        assertSem074(compile("main() { println(Object.bogus()) }\n", tempDir), "bogus", "Object");
    }

    @Test
    void unknownStaticMethodWithWrongArityFailsWithSem074(@TempDir Path tempDir) throws IOException {
        // `String.valueOf` exists with 1 arg; 4 args must not silently emit.
        assertSem074(compile("""
            main() {
                println(String.valueOf(1, 2, 3, 4))
            }
            """, tempDir), "valueOf", "String");
    }

    @Test
    void validJdkStaticsOnBuiltinTypesStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                println(String.valueOf(42))
                println(String.join(",", listOf("a", "b")))
                println(Long.parseLong("5"))
                println(Double.isNaN(1.0))
                println(Bool.parseBoolean("true"))
            }
            """, tempDir);
        assertTrue(result.success(), "Valid JDK statics on builtin types must compile: "
                + result.diagnostics().getDiagnostics());
    }

    // ---- §502: unknown method on a `spawn` Handle<T> ----

    @Test
    void unknownConcurrencyHandleMethodFailsWithSem025(@TempDir Path tempDir) throws IOException {
        // symptom: `val h = spawn { ... }` + `h.bogus()` compiled clean and
        // emitted `invokevirtual java/util/concurrent/CompletableFuture.bogus`
        // → NoSuchMethodError at runtime.
        assertSem025(compile("""
            main() {
                val h = spawn { return 42 }
                println(h.bogus())
            }
            """, tempDir), "bogus", "Handle");
    }

    @Test
    void validHandleAwaitStillCompiles(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                val h = spawn { return 42 }
                println(await h)
            }
            """, tempDir);
        assertTrue(result.success(), "await on a Handle must still compile: "
                + result.diagnostics().getDiagnostics());
    }

    // ---- control: the live tables still compile ----

    @Test
    void validWebAppStillCompiles(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                var app = web.app()
                println(app)
            }
            """, tempDir);
        assertTrue(result.success(), "web.app() must still compile: "
                + result.diagnostics().getDiagnostics());
    }


    @Test
    void validSchedulerMethodsStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                var h = scheduler.every(1000) { println("tick") }
                println(h)
                scheduler.cancel(h)
                var c = scheduler.at("0 3 * * *", () -> println("cron"))
                println(c)
            }
            """, tempDir);
        assertTrue(result.success(), "Valid scheduler.every/cancel/at must compile: "
                + result.diagnostics().getDiagnostics());
    }


    @Test
    void validBufferAndSecretMethodsStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                var b = buffer.alloc(4)
                println(b.bytes())
                var s = secrets.of("hunter2")
                println(s.reveal())
                println(s.redacted())
                var k = secrets.keyFromHex("00")
                println(k.rotate())
            }
            """, tempDir);
        assertTrue(result.success(), "Valid Buffer/Secret/KeyHandle members must compile: "
                + result.diagnostics().getDiagnostics());
    }
}
