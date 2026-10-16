package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §716: a generic type-parameter BOUND was NOT validated — an
 * unknown bound (`T: Foobar`) or the non-type `T: Any` compiled CLEAN and the
 * emitted class referenced a nonexistent class → {@code NoClassDefFoundError}
 * at JVM load (a silent bound typo, violates R6/Q7).
 *
 * <p>Fix: {@code DeclaredTypeChecker} now validates each bound with the SAME
 * predicate as declared types (§251/SEM011), exempting the owner's own
 * type-parameters. A bound that is a module class, an import, a builtin, or
 * {@code java.lang} resolves; anything else is SEM011.
 */
class GenericBoundValidationE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private boolean compiles(String src, String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        return driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM).success();
    }

    private boolean hasSem011(String src, String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM);
        return r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM011".equals(d.code()));
    }

    @Test
    void anUnknownBoundIsRejected() throws Exception {
        assertFalse(compiles("""
                record Box<T: Foobar>(T value) {
                    T id() { return value }
                }
                main() { println(Box<Int>(4).id()) }
                """, "bogus"), "T: Foobar (unknown type) must NOT compile");
        assertTrue(hasSem011("""
                record Box<T: Foobar>(T value) {
                    T id() { return value }
                }
                main() { println(Box<Int>(4).id()) }
                """, "bogus2"), "the unknown bound must be a SEM011");
    }

    @Test
    void theNonTypeAnyIsRejected() throws Exception {
        assertFalse(compiles("""
                record Box<T: Any>(T value) {
                    T id() { return value }
                }
                main() { println(Box<Int>(4).id()) }
                """, "any"), "T: Any (not a Kof type) must NOT compile");
    }

    @Test
    void aFunctionTypeParameterBoundIsValidated() throws Exception {
        assertFalse(compiles("""
                record Box<T: Foobar>(T value)
                Int f() { return 1 }
                main() { println(f()) }
                """, "func"), "a function's type-parameter bound must be validated too");
    }

    @Test
    void aModuleClassBoundStillCompiles() throws Exception {
        assertTrue(compiles("""
                record Animal(String n)
                record Box<T: Animal>(T value) {
                    T id() { return value }
                }
                main() { println(Box<Animal>(Animal("x")).id().n) }
                """, "known"), "a bound to a declared class must compile");
    }

    @Test
    void anInterfaceBoundStillCompiles() throws Exception {
        assertTrue(compiles("""
                interface Named {
                    String name()
                }
                record Box<T: Named>(T value) {
                    T id() { return value }
                }
                main() { println(1) }
                """, "iface"), "a bound to a declared interface must compile");
    }

    @Test
    void anUnboundedTypeParameterStillCompiles() throws Exception {
        assertTrue(compiles("""
                record Box<T>(T value) {
                    T id() { return value }
                }
                main() { println(Box<Int>(4).id()) }
                """, "unbounded"), "a bound-less type-parameter must compile");
    }
}
