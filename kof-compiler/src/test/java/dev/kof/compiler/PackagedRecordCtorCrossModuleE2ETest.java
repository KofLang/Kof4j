package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * §582 (03/10, KofShare C2 sharecheck): a CONSTRUCTOR call of a packaged
 * record written inside an IMPORTED module's function body lowered the owner
 * BARE — `new ShareInfo(...)` in kofshare.control.share.kf emitted a `new`
 * with class name `ShareInfo` (no package), so the JVM died at LOAD
 * (`NoClassDefFoundError: ShareInfo`) while the same expression in the main
 * file worked. §573's chokepoint qualified INFERRED types (locals/members);
 * the ctor lowering path reads the type straight off the NEW expression and
 * never passed through it. RED-first pin: both shapes (ctor in imported body,
 * member-access in the same body) must run green on JVM and Script.
 */
class PackagedRecordCtorCrossModuleE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private void writeSources() throws Exception {
        Path root = tmp.resolve("src/main/kof");
        Path pkg = root.resolve("cc");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("model.kf"), """
                package cc

                record Pnt(Int x, Int y)

                String store(Map<String, Pnt> m, String id, Int a, Int b) {
                    m.put(id, new Pnt(a, b))
                    return "OK"
                }

                Pnt bump(Map<String, Pnt> m, String id) {
                    var p: Pnt? = m.get(id)
                    if (p == null) {
                        return new Pnt(0, 0)
                    }
                    return new Pnt(p.x() + 1, p.y())
                }
                """);
        Files.writeString(pkg.resolve("main.kf"), """
                package cc

                import cc.model

                main() {
                    var ms = new Map<String, Pnt>()
                    println("store=" + (store(ms, "s1", 3, 4) == "OK"))
                    var h = bump(ms, "s1")
                    println("bumped=" + h.x() + "," + h.y())
                }
                """);
    }

    @Test
    void ctorInImportedModuleBodyLoadsOnJvm() throws Exception {
        writeSources();
        Path root = tmp.resolve("src/main/kof");
        CompilationResult r = driver.compileSources(
                List.of(root.resolve("cc/main.kf")), tmp.resolve("out"), Target.JVM, root);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", tmp.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, proc.waitFor(), "load/run: " + out);
        assertEquals("store=true\nbumped=4,4", out.trim());
    }

    @Test
    void ctorInImportedModuleBodyRunsOnScript() throws Exception {
        writeSources();
        Path root = tmp.resolve("src/main/kof");
        KofInterpreter.Result r = driver.interpret(
                List.of(root.resolve("cc/main.kf")), root, new String[0]);
        assertEquals(0, r.exitCode(), "script exit: " + r.stderr());
        assertEquals("store=true\nbumped=4,4", r.stdout().trim());
    }
}
