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
 * D-KOFMD fatia 3.6 — `kof md check|format` (spec §15). The CLI is the
 * mechanism; every decision (parse, vocabulary, canonical form) is made by
 * the pure-Kof lib `libs/kofmd` compiled on the fly. Non-zero exit on MDxxx
 * with file:line (§14); format is an idempotent in-place rewrite; convert is
 * refused honestly (§21).
 */
class CmdMdTest {

    @Test
    void checkCleanDocumentExitsZero(@TempDir Path tmp) throws Exception {
        Path doc = tmp.resolve("note.md");
        Files.writeString(doc, "doing: parser\nnext: tests\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdMd.run(new String[]{"md", "check", doc.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(0, ec, err.toString(StandardCharsets.UTF_8));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("no MDxxx"), out.toString());
    }

    @Test
    void checkUnknownIntentExitsOneWithFileLine(@TempDir Path tmp) throws Exception {
        Path doc = tmp.resolve("note.md");
        Files.writeString(doc, "@decision\nkeep\n\n@todo\nfix it\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdMd.run(new String[]{"md", "check", doc.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(1, ec);
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains(":4: MD002:"), printed);
        assertTrue(printed.contains("outside reserved vocabulary"), printed);
    }

    @Test
    void formatRewritesCanonicallyAndIsIdempotent(@TempDir Path tmp) throws Exception {
        Path doc = tmp.resolve("note.md");
        Files.writeString(doc, "zeta: 1\nlast: a\nalpha: 2\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdMd.run(new String[]{"md", "format", doc.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(0, ec, err.toString(StandardCharsets.UTF_8));
        String once = Files.readString(doc);
        assertEquals("last: a\nalpha: 2\nzeta: 1\n", once);
        int again = CmdMd.run(new String[]{"md", "format", doc.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(0, again);
        assertEquals(once, Files.readString(doc), "second format must be a no-op");
    }

    @Test
    void convertMissingFileAndUnknownFlagAreRefused(@TempDir Path tmp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8);
        PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8);
        assertEquals(1, CmdMd.run(new String[]{"md", "convert", "x.md"}, o, e));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("non-goal"));

        err.reset();
        assertEquals(1, CmdMd.run(new String[]{"md", "check", tmp.resolve("nope.md").toString()}, o, e));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("not found"));

        err.reset();
        assertEquals(1, CmdMd.run(new String[]{"md", "check", tmp.resolve("x.md").toString(), "--json"}, o, e));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unknown flag"));

        err.reset();
        assertEquals(1, CmdMd.run(new String[]{"md", "serve"}, o, e));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unknown subcommand"));
    }

    @Test
    void checkSchemaMismatchExitsOneWithFileLine(@TempDir Path tmp) throws Exception {
        Path doc = tmp.resolve("lab.md");
        Files.writeString(doc, "record Lab(Int total, Int passed)\n\ntotal: 5\npassed: cinco\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdMd.run(new String[]{"md", "check", doc.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(1, ec, err.toString(StandardCharsets.UTF_8));
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains(":4: MD002:"), printed);
        assertTrue(printed.contains("schema type"), printed);
    }

    @Test
    void helpListsTheContractWithoutExecuting(@TempDir Path tmp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdMd.run(new String[]{"md", "help"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(0, ec);
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("kof md check"), printed);
        assertTrue(printed.contains("kof md format"), printed);
        assertTrue(printed.contains("convert"), printed);
    }
}
