package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-NET-JS-V1 (maintainer vote 02/10, "(c) kof.net fora do JS no v1"): the
 * network front is refused at COMPILE time on {@code Target.JS} with the
 * named code {@code NETN001} — no browser/Node sub-target is introduced
 * ({@code D-JS-PLATFORM-SELECTOR} stays valid for every other runtime-only
 * surface; network simply never reaches a JS artifact). The JVM and Script
 * legs of the frozen plan are untouched.
 */
class NetJsV1RefusalE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path dir, String name, String src, Target t) throws Exception {
        Path s = dir.resolve(name + ".kf");
        Files.writeString(s, src);
        return driver.compile(s, dir.resolve("out-" + name), t);
    }

    @Test
    @DisplayName("D-NET-JS-V1: bare net.listen under Target.JS refuses with NETN001 (compile-time, not runtime shim)")
    void bareCallRefused(@TempDir Path dir) throws Exception {
        CompilationResult r = compile(dir, "bare", """
                main() {
                    var l = net.listen(19941)
                    println("no")
                }
                """, Target.JS);
        assertFalse(r.success(), "must refuse");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("NETN001"),
                "must name NETN001: " + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("D-NET-JS-V1: import kof.net under Target.JS refuses with NETN001 even with no call")
    void importRefused(@TempDir Path dir) throws Exception {
        CompilationResult r = compile(dir, "imp", """
                import kof.net

                main() {
                    println("oi")
                }
                """, Target.JS);
        assertFalse(r.success(), "must refuse");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("NETN001"),
                "must name NETN001: " + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("D-NET-JS-V1: the same programs stay green on JVM and plain JS programs are untouched")
    void otherLegsGreen(@TempDir Path dir) throws Exception {
        CompilationResult jvm = compile(dir, "jvm", """
                import kof.net

                main() {
                    println("ok")
                }
                """, Target.JVM);
        assertTrue(jvm.success(), "JVM leg must compile: " + jvm.diagnostics().getDiagnostics());
        CompilationResult js = compile(dir, "jsok", """
                main() {
                    println(1 + 2)
                }
                """, Target.JS);
        assertTrue(js.success(), "non-net JS must compile: " + js.diagnostics().getDiagnostics());
    }
}
