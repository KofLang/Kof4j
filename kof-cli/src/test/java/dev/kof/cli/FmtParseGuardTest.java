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
 * #1101 — `kof fmt -w` on a file that does NOT parse must report it, leave
 * it untouched, and FAIL the run (fmt never changes the program's
 * semantics). Today the lossy token fallback rewrote the broken file
 * (`json.decode < T > (x)` spacing), printed "file(s) reformatted", and
 * exited 0 — a one-line slip became a file of new errors that had to be
 * restored from git.
 */
class FmtParseGuardTest {

    private static final String BROKEN = """
            class Repo {
                String? name
                Map<String, Object> load(String raw) {
                    return json.decode<Map<String, Object>>(raw)
                }
                void oops( {
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void unparseableFileIsLeftUntouchedAndFailsTheRun() throws Exception {
        Path f = tmp.resolve("Repo.kf");
        Files.writeString(f, BROKEN);
        PrintStream oldOut = System.out, oldErr = System.err;
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int exit;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            exit = Fmt.run(new String[]{"fmt", f.toString(), "-w"});
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        assertEquals(1, exit, "the run must FAIL on a parse error (scripts notice)");
        assertEquals(BROKEN, Files.readString(f), "the broken file must be left untouched");
        assertTrue(err.toString().contains("does not parse"),
                "stderr must report the unparseable file: " + err);
        assertTrue(out.toString().contains("0 file(s) reformatted, 1 file(s) with parse errors"),
                "the summary counts the skipped file, not a rewrite: " + out);
    }

    @Test
    void parseableNeighborIsStillFormatted() throws Exception {
        // The tree face: one broken file must not block its valid neighbors.
        Path broken = tmp.resolve("Broken.kf");
        Files.writeString(broken, BROKEN);
        Path good = tmp.resolve("Good.kf");
        Files.writeString(good, "main() {\nval a=1\nprintln( a )\n}\n");
        PrintStream oldOut = System.out, oldErr = System.err;
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int exit;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            exit = Fmt.run(new String[]{"fmt", tmp.toString(), "-w"});
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        assertEquals(1, exit, "the run still fails overall (a file was skipped)");
        assertEquals(BROKEN, Files.readString(broken), "the broken file untouched");
        String goodText = Files.readString(good);
        assertFalse(goodText.contains("val a=1"), "the valid neighbor IS formatted: " + goodText);
    }

    @Test
    void formatCheckedDistinguishesParsedFromBroken() {
        var ok = Fmt.formatChecked("main() {\nprintln(1)\n}\n", "Main.kf");
        assertTrue(ok.parsed(), "a valid file parses (the AST formatter)");
        var bad = Fmt.formatChecked(BROKEN, "Repo.kf");
        assertFalse(bad.parsed(), "the broken file must be reported as unparseable");
    }
}
