package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §790 — the builtin Map and Set methods had no arity gate (only List and two
 * Map methods did), so {@code m.remove(k, v)} pushed two arguments to the
 * one-argument {@code kof_map_remove} and the class failed verification
 * ({@code VerifyError}, or a frame crash). A wrong arity is now SEM025 at the
 * call, on every target, like the List methods (#386).
 */
class CollectionArityGateTest {

    @TempDir Path tmp;

    private CompilationResult compile(String name, String kf) throws Exception {
        Path source = tmp.resolve(name + ".kf");
        Files.writeString(source, kf);
        return new CompilerDriver().compile(source, tmp.resolve(name), Target.JVM);
    }

    private void assertRefused(String name, String call, String expected) throws Exception {
        CompilationResult r = compile(name, """
            main() {
                val m = mapOf("k", "v")
                val s = setOf("a")
                println(CALL)
            }
            """.replace("CALL", call));
        assertFalse(r.success(), call + " must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM025".equals(d.code())
                        && d.message().contains(expected)),
                call + ": " + r.diagnostics().getDiagnostics());
    }

    @Test
    void aWrongArityOnAMapOrSetMethodIsSem025() throws Exception {
        assertRefused("m1", "m.remove(\"k\", \"v\")", "Map.remove takes exactly 1 argument");
        assertRefused("m2", "m.get(\"k\", \"x\")", "Map.get takes exactly 1 argument");
        assertRefused("m3", "m.put(\"k\")", "Map.put takes exactly 2 argument");
        assertRefused("m4", "m.size(1)", "Map.size takes exactly 0 argument");
        assertRefused("s1", "s.add(\"b\", \"c\")", "Set.add takes exactly 1 argument");
        assertRefused("s2", "s.contains()", "Set.contains takes exactly 1 argument");
    }

    @Test
    void theRightArityStillCompiles() throws Exception {
        CompilationResult r = compile("ok", """
            main() {
                val m = mapOf("k", "v")
                m.put("x", "y")
                println(m.get("x"))
                println(m.getOrDefault("z", "d"))
                println(m.remove("k"))
                println(m.containsKey("x"))
                println(m.size())
                val s = setOf("a")
                s.add("b")
                println(s.contains("b"))
                println(s.remove("a"))
                println(s.size())
            }
            """);
        assertTrue(r.success(), "valid calls: " + r.diagnostics().getDiagnostics());
    }
}
