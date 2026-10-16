package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code SEM025} "Cannot resolve method … on type 'String' (valid: …)" hint
 * must list the String methods the typer ACCEPTS. Same audit family as §737
 * (List/Map/Set): the hint omitted {@code getBytes} — a real String method the
 * guard itself accepts (measured: {@code "abc".getBytes().size} = 3). Note the
 * exclusions: {@code isBlank}/{@code strip} are REJECTED (SEM025), so they must
 * stay OUT of the hint.
 */
class StringValidMethodsDiagnosticE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        return driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM);
    }

    @Test
    void getBytesIsAcceptedOnString() throws Exception {
        CompilationResult r = compile("ok", """
                main() {
                    println("abc".getBytes().size)
                }
                """);
        assertTrue(r.success(), () -> "getBytes must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void theStringHintListsGetBytesAndNotTheRejectedOnes() throws Exception {
        CompilationResult r = compile("bad", """
                main() {
                    var s = "x"
                    s.nope()
                }
                """);
        assertEquals(false, r.success(), "s.nope() must not compile");
        String msg = r.diagnostics().getDiagnostics().stream()
                .filter(d -> "SEM025".equals(d.code()) && d.message().contains("'String'"))
                .map(d -> d.message())
                .reduce("", (a, b) -> a + b);
        assertTrue(msg.contains("getBytes"), () -> "SEM025 String hint must list getBytes: " + msg);
        assertTrue(!msg.contains("isBlank") && !msg.contains("strip"),
                () -> "SEM025 String hint must NOT list the REJECTED isBlank/strip: " + msg);
    }
}
