package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CmdCheckTest {

    @Test
    void checkTextOutputSuccess(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "main() { println(\"ok\") }");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", file.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, ec);
        String output = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(output.contains("checked 1 file(s) — no errors"), output);
    }

    @Test
    void checkTextOutputFailure(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "main() { val x: Int = \"invalido\" }");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", file.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, ec);
        String output = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(output.contains("error:") || output.contains("SEM021"), output);
    }

    @Test
    void checkJsonOutputSuccess(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "main() { val a = 10; println(a) }");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", file.toString(), "--json"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, ec);
        String output = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(output.contains("\"success\":true"), output);
        assertTrue(output.contains("\"filesChecked\":1"), output);
        assertTrue(output.contains("\"errorCount\":0"), output);
        assertTrue(output.contains("\"diagnostics\":[]"), output);
    }

    @Test
    void checkJsonOutputFailure(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "main() { val x: Int = \"invalido\" }");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", file.toString(), "--json"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, ec);
        String output = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(output.contains("\"success\":false"), output);
        assertTrue(output.contains("\"errorCount\":1"), output);
        assertTrue(output.contains("\"severity\":\"ERROR\""), output);
        assertTrue(output.contains("\"code\":\"SEM021\""), output);
        assertTrue(output.contains("\"message\":"), output);
        assertTrue(output.contains("\"file\":"), output);
    }

    @Test
    void checkJsonEmptyDirectory(@TempDir Path tmp) throws Exception {
        Path emptyDir = tmp.resolve("empty");
        Files.createDirectories(emptyDir);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", emptyDir.toString(), "--json"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, ec);
        String output = out.toString(StandardCharsets.UTF_8).trim();
        assertEquals("{\"success\":true,\"filesChecked\":0,\"errorCount\":0,\"diagnostics\":[]}", output);
    }

    @Test
    void checkJsonArgOrderFlexible(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "main() { println(\"ok\") }");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", "--json", file.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, ec);
        String output = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(output.contains("\"success\":true"), output);
        assertTrue(output.contains("\"diagnostics\":[]"), output);
    }

    @Test
    void checkHelpOutputsJsonOption() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", "--help"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, ec);
        String output = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(output.contains("--json"), output);
    }

    @Test
    void checkTargetAndroidEnforcesAnd002(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "main() {\n    var app = web.app()\n}\n");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", file.toString(), "--target", "android"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, ec, "web.app() no android deve ser AND002 no check");
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("AND002"),
                out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void checkTargetJvmKeepsWebClean(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "main() {\n    var app = web.app()\n}\n");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", file.toString(), "--target", "jvm"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, ec, "no jvm web.app() nao e gap");
    }

    @Test
    void checkUnknownFlagIsRejected(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "main() { println(\"ok\") }");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", file.toString(), "--bogus"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, ec, "flag desconhecida deve ser recusada (R6)");
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unknown flag"),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void scriptTargetIsRefusedHonestlyBeforeTypeChecking(@TempDir Path tmp) throws Exception {
        // MEASURED before the guard: `check --target script` ran the FULL pipeline
        // and returned rc=1 on VALID code with COMP003 "emits no artifacts" — an
        // EMISSION diagnostic, while this command's help says "type-check without
        // emitting output". A phantom error on a correct program.
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "Int f() { return 42 }\nmain() { println(f()) }");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int ec = CmdCheck.run(new String[]{"check", file.toString(), "--target", "script"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, ec, "must refuse: " + err);
        String stderr = err.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("cannot be checked"), stderr);
        assertTrue(stderr.contains("default jvm"), "points at the working default: " + stderr);
        assertFalse(stderr.contains("COMP003"),
                "must not leak the internal emission code: " + stderr);
    }

    @Test
    void wasmAndWasiTargetsStillTypeCheck(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, "Int f() { return 42 }\nmain() { println(f()) }");
        for (String t : java.util.List.of("jvm", "wasm", "wasi")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int ec = CmdCheck.run(new String[]{"check", file.toString(), "--target", t},
                    new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8));
            assertEquals(0, ec, t + " must type-check: " + err);
        }
    }
}
