package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #336 — `list.add(index, value)` (the Java positional insert) compiled
 * silently and broke differently on each target: JVM VerifyError at class
 * load, JS/Script swallowed the index and did a silent append (rule-5
 * divergence + R6). Kof's List contract is APPEND of one element
 * (learn/12, training/idioms/collections); positional insert does not
 * exist — `set(i, v)` (replace) does. The shared semantic pass now rejects
 * wrong arity universally with SEM072 (one gate, four targets — same face
 * as CatchTypeCheck/#332).
 */
class ListAddArityTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String program, Target t) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + t), t);
    }

    @Test
    void addWithIndexIsRejectedSem072(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, """
                main() {
                    var list = listOf(1, 3, 5)
                    list.add(1, 2)
                    println(list.get(1))
                }
                """, Target.JVM);
        assertFalse(r.success(), "2-arg List.add must be rejected (#336 — old code compiled and VerifyErrored on load)");
        String d = r.diagnostics().getDiagnostics().toString();
        assertTrue(d.contains("SEM072"), "must carry SEM072: " + d);
        assertTrue(d.contains("set(index, value)"), "must point at the real API: " + d);
    }

    @Test
    void wrongArityRejectedOnAllArtifactTargets(@TempDir Path tempDir) throws Exception {
        // rule 5: the SAME diagnostic on every backend (JVM/Native/JS emit
        // artifacts; SCRIPT was measured via `kof run --target script` on the
        // reproducer — the semantic pass fires SEM072 before the interpret
        // stage, so it is the same shared gate).
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, """
                    main() {
                        var l = listOf(1, 2)
                        l.add(0, 9)
                    }
                    """, t);
            assertFalse(r.success(), t + ": 2-arg add must be rejected");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM072"),
                    t + ": must be SEM072 (universal gate): " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void pushAndAppendShareTheGuard(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, """
                main() {
                    var l = listOf(1, 2)
                    l.push(0, 3)
                }
                """, Target.JVM);
        assertFalse(r.success(), "push(i,v) is the same non-existent insert (#336 family)");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM072"));
    }

    @Test
    void oneArgAppendAndSetStillWork(@TempDir Path tempDir) throws Exception {
        // control (backward compatibility, freeze rule 2): the documented
        // API keeps compiling and running unchanged.
        CompilationResult r = compile(tempDir, """
                main() {
                    var l = listOf(1, 3, 5)
                    l.add(7)
                    l.set(0, 9)
                    println(l.get(0))
                    println(l.size())
                }
                """, Target.JVM);
        assertTrue(r.success(), "append(1) + set(i,v) must stay legal: " + r.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out-JVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor());
        assertEquals("9\n4", out, "append+set semantics unchanged");
    }
}
