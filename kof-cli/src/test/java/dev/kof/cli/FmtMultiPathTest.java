package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1050 — `kof fmt -w <file>` answered `not found: -w` (the path was read
 * from the FIXED position args[1]) and `kof fmt a.kf b.kf -w` formatted only
 * the FIRST path, silently dropping the rest. The contract: every argument
 * is either `-w` (any position), an unknown flag (refused as before, R6),
 * or a path — every path given is formatted; no path at all is the usage
 * error; a missing path fails the run BEFORE the first rewrite.
 */
class FmtMultiPathTest {

    private static final String UNFORMATTED = "main() {\nval a=1\nprintln( a )\n}\n";

    @TempDir
    Path tmp;

    private String lastErr = "";
    private String lastOut = "";

    private int runFmt(String... args) throws Exception {
        PrintStream oldOut = System.out, oldErr = System.err;
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            return Fmt.run(args);
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            lastOut = out.toString();
            lastErr = err.toString();
        }
    }

    private Path file(String name) throws Exception {
        Path p = tmp.resolve(name);
        Files.writeString(p, UNFORMATTED);
        return p;
    }

    private void assertFormatted(Path p) throws Exception {
        assertFalse(Files.readString(p).contains("val a=1"),
                p.getFileName() + " must be formatted: " + Files.readString(p));
    }

    @Test
    void wBeforeThePathFormatsInPlace() throws Exception {
        Path a = file("A.kf");
        int exit = runFmt("fmt", "-w", a.toString());
        assertEquals(0, exit, "stderr: " + lastErr);
        assertFormatted(a);
        assertTrue(lastOut.contains("1 file(s) reformatted"), "out: " + lastOut);
    }

    @Test
    void everyPathIsFormattedNotJustTheFirst() throws Exception {
        Path a = file("A.kf");
        Path b = file("B.kf");
        int exit = runFmt("fmt", a.toString(), b.toString(), "-w");
        assertEquals(0, exit, "stderr: " + lastErr);
        assertFormatted(a);
        assertFormatted(b);
        assertTrue(lastOut.contains("2 file(s) reformatted"), "out: " + lastOut);
    }

    @Test
    void unknownFlagIsRefusedAnywhere() throws Exception {
        Path a = file("A.kf");
        int exit = runFmt("fmt", "-w", a.toString(), "--quux");
        assertEquals(1, exit, "an unknown flag must be refused (R6)");
        assertTrue(lastErr.contains("unknown flag: --quux"), "stderr: " + lastErr);
        assertTrue(Files.readString(a).contains("val a=1"),
                "the refused run must not rewrite anything");
    }

    @Test
    void noPathAtAllIsTheUsageError() throws Exception {
        int exit = runFmt("fmt", "-w");
        assertEquals(1, exit);
        assertTrue(lastErr.contains("usage: kof fmt"), "stderr: " + lastErr);
    }

    @Test
    void notFoundFailsBeforeAnyRewrite() throws Exception {
        Path a = file("A.kf");
        Path ghost = tmp.resolve("Ghost.kf");
        int exit = runFmt("fmt", a.toString(), ghost.toString(), "-w");
        assertEquals(1, exit, "a missing path must fail the run");
        assertTrue(lastErr.contains("not found"), "stderr: " + lastErr);
        assertTrue(Files.readString(a).contains("val a=1"),
                "the valid file must be untouched — the missing path fails the run BEFORE the first rewrite");
    }

    @Test
    void directoryAndFilePathsMixInOneRun() throws Exception {
        Path dir = tmp.resolve("src");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("S1.kf"), UNFORMATTED);
        Path loose = file("Loose.kf");
        int exit = runFmt("fmt", dir.toString(), loose.toString(), "-w");
        assertEquals(0, exit, "stderr: " + lastErr);
        assertFormatted(dir.resolve("S1.kf"));
        assertFormatted(loose);
        assertTrue(lastOut.contains("2 file(s) reformatted"), "out: " + lastOut);
    }
}
