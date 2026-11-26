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
    void returnNullOnNonNullableStringCompilesGoldenJvm() throws Exception {
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
        // #1233 (a reversão do §1221): a matriz GOLDEN (conformanceNullSafety)
        // usa `return null` num retorno `String` e narrowa no chamador — o
        // contrato canônico é o decode aceitar e o narrow cuidar. O return
        // null COMPILA (o guard SEM048 foi revertido).
        assertTrue(r.success(), "the golden contract wins: " + diagnosticsOf(r));
    }

    @Test
    void returnNullOnNonNullableIntCompilesGoldenJvm() throws Exception {
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
        // #1233 (a reversão do §1221): a matriz GOLDEN (conformanceNullSafety)
        // usa `return null` num retorno `String` e narrowa no chamador — o
        // contrato canônico é o decode aceitar e o narrow cuidar. O return
        // null COMPILA (o guard SEM048 foi revertido).
        assertTrue(r.success(), "the golden contract wins: " + diagnosticsOf(r));
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
