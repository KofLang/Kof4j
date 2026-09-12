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
}
