package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1227 RED: a native program that allocates a few KiB must run under an address-space
 * cap of 256 MiB. Measured 25/11: the same binary printed `x=1` and `Runtime error: out
 * of memory` in consecutive runs without any limit changing, and ALWAYS failed under
 * `ulimit -v 256M` — while a hello world without the app's library set passed. The
 * cause is the heap arena: the runtime tries 4 GiB, halves down to a 64 MiB floor and,
 * below it, gives up ("no arena") although the program only needs a few KiB.
 */
class NativeSmallArenaE2ETest {

    private static final String PROG = """
            Int soma() { return 196 }

            main() {
                println("x=" + soma())
            }
            """;

    @Test
    void aSmallProgramRunsUnderA256MiBCap(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, PROG);
        Path out = dir.resolve("native");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native: " + r.diagnostics().getDiagnostics());
        String bin = out.resolve("Default/Main").toString();
        for (int i = 1; i <= 3; i++) {
            Process p = new ProcessBuilder("bash", "-c", "ulimit -v 262144; exec '" + bin + "'")
                    .redirectErrorStream(true).start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "run " + i + " under 256 MiB: " + text);
            assertEquals("x=196", text, "run " + i);
        }
    }
}
