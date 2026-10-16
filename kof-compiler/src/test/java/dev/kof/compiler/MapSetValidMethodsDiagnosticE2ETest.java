package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code SEM025} "Cannot resolve method … on type 'Map'/'Set' (valid: …)" hints
 * must list EVERY method the typer accepts. Found by the same proactive diagnostic
 * audit as §737 (the List hint): both the Map and Set hints omitted {@code length}
 * and {@code count} — the typer accepts {@code size}/{@code length}/{@code count}
 * for BOTH (member-call typer, the size-like triple), and they are measured working
 * ({@code s.length}/{@code s.count()}/{@code m.length}/{@code m.count()}).
 */
class MapSetValidMethodsDiagnosticE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        return driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM);
    }

    private String hint(CompilationResult r, String type) {
        return r.diagnostics().getDiagnostics().stream()
                .filter(d -> "SEM025".equals(d.code()) && d.message().contains("'" + type + "'"))
                .map(d -> d.message())
                .reduce("", (a, b) -> a + b);
    }

    @Test
    void lengthAndCountAreAcceptedOnMapAndSet() throws Exception {
        CompilationResult r = compile("ok", """
                main() {
                    var s = setOf(1, 2)
                    var m = mapOf("a", 1)
                    println(s.length + s.count() + s.size)
                    println(m.length + m.count() + m.size)
                }
                """);
        assertTrue(r.success(), () -> "length/count must compile on Map+Set: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void theMapAndSetHintsListLengthAndCount() throws Exception {
        CompilationResult rs = compile("sb", """
                main() {
                    var s = setOf(1, 2)
                    s.nope()
                }
                """);
        assertEquals(false, rs.success(), "s.nope() must not compile");
        String sh = hint(rs, "Set");
        for (String m : new String[] {"length", "count"}) {
            assertTrue(sh.contains(m), () -> "SEM025 Set 'valid:' hint must list '" + m + "': " + sh);
        }
        CompilationResult rm = compile("mb", """
                main() {
                    var m = mapOf("a", 1)
                    m.nope()
                }
                """);
        assertEquals(false, rm.success(), "m.nope() must not compile");
        String mh = hint(rm, "Map");
        for (String m : new String[] {"length", "count"}) {
            assertTrue(mh.contains(m), () -> "SEM025 Map 'valid:' hint must list '" + m + "': " + mh);
        }
    }
}
