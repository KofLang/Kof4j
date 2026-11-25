package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §1221 (issue #1195 face `return null`) — the SEM048 rule ("null is
 * never assignable; null safety is by narrowing") covered the variable
 * declaration, the assignment, the ARGUMENT, the record FIELD and the
 * collection ELEMENT — but a bare `return null` in a function whose declared
 * return type is NON-nullable (`String`, `Int`) passed the check and the NPE
 * surfaced at runtime.
 *
 * <p>Contract under test: `return null` with a NON-nullable declared return
 * type is refused with SEM048 at compile time; a NULLABLE return type (`String?`,
 * `Int?`) KEEPS accepting the literal (the §125 precedent: `Int?` receives the
 * primitive's DEFAULT; `String?` is the absent value).
 */
class ReturnNullNonNullableE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult check(String source) throws Exception {
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, source);
        return driver.compileSources(
                java.util.List.of(main), tmp.resolve("out"), Target.JVM,
                tmp.toAbsolutePath().normalize());
    }

    private static String diagnosticsOf(CompilationResult r) {
        return r.diagnostics().getDiagnostics().toString();
    }

    @Test
    void returnNullOnNonNullableStringIsRefusedJvm() throws Exception {
        CompilationResult r = check("""
                class U2 {
                    static String volta() {
                        return null
                    }
                }

                main() {
                    println(U2.volta())
                }
                """);
        assertFalse(r.success(),
                "known-bugs §1221: `return null` with a NON-nullable return type must be "
                        + "REFUSED at check time — the NPE surfacing at runtime was the measured "
                        + "defect (Cannot invoke String.length() because the return value is null)");
        assertTrue(diagnosticsOf(r).contains("SEM048"),
                "the refusal must be SEM048, got: " + diagnosticsOf(r));
    }

    @Test
    void returnNullOnNonNullableIntIsRefusedJvm() throws Exception {
        CompilationResult r = check("""
                class U4 {
                    static Int dez() {
                        return null
                    }
                }

                main() {
                    println(U4.dez())
                }
                """);
        assertFalse(r.success(),
                "`return null` with a primitive return type must also be refused: "
                        + diagnosticsOf(r));
        assertTrue(diagnosticsOf(r).contains("SEM048"), diagnosticsOf(r));
    }

    @Test
    void returnNullOnNullableReturnStillCompilesJvm() throws Exception {
        CompilationResult r = check("""
                class U3 {
                    static String? talvez() {
                        return null
                    }
                    static Int? num() {
                        return null
                    }
                }
                main() {
                    println(U3.talvez())
                    println(U3.num())
                }
                """);
        assertTrue(r.success(),
                "the NULLABLE return types keep accepting `return null` (the §125 default "
                        + "for `Int?`, the absent for `String?`): " + diagnosticsOf(r));
    }
}
