package dev.kof.cli.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class SystemDetectContextTest {

    private static Path script(Path dir, String name, String body) throws IOException {
        Path exe = dir.resolve(name);
        Files.writeString(exe, "#!/bin/sh\n" + body + "\n");
        assertTrue(exe.toFile().setExecutable(true));
        return exe;
    }

    private static Path scratch(Path dir) throws IOException {
        return Files.createDirectory(dir.resolve("scratch"));
    }

    private static void assertNoLeftoverFiles(Path scratch) throws IOException {
        try (Stream<Path> files = Files.list(scratch)) {
            assertEquals(0, files.count(), "temporary version output must be deleted");
        }
    }

    @Test
    void versionOutputIsReturned(@TempDir Path dir) throws IOException {
        Path exe = script(dir, "fake-editor", "echo \"fake-editor 1.2.3\"");
        Path scratch = scratch(dir);
        assertEquals("fake-editor 1.2.3\n", SystemDetectContext.runVersion(exe.toString(), 5000, scratch));
        assertNoLeftoverFiles(scratch);
    }

    @Test
    void editorThatNeverExitsIsUnknownWithinTheBoundAndKilled(@TempDir Path dir) throws Exception {
        Path pidFile = dir.resolve("pid");
        Path exe = script(dir, "tui-editor", "echo $$ > \"" + pidFile + "\"\nexec sleep 60");
        Path scratch = scratch(dir);
        long start = System.nanoTime();
        String v = SystemDetectContext.runVersion(exe.toString(), 500, scratch);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertEquals("unknown", v);
        assertTrue(ms < 5000, "readVersion must honour its bound, took " + ms + " ms");
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        Optional<ProcessHandle> child = ProcessHandle.of(pid);
        if (child.isPresent()) {
            child.get().onExit().get(5, TimeUnit.SECONDS);
            assertFalse(child.get().isAlive(), "timed-out editor process must be terminated");
        }
        assertNoLeftoverFiles(scratch);
    }

    @Test
    void editorReadingStdinSeesEndOfInput(@TempDir Path dir) throws IOException {
        Path exe = script(dir, "stdin-editor", "cat\necho \"stdin-editor 4.5\"");
        Path scratch = scratch(dir);
        assertEquals("stdin-editor 4.5\n", SystemDetectContext.runVersion(exe.toString(), 5000, scratch));
        assertNoLeftoverFiles(scratch);
    }

    @Test
    void missingExecutableIsUnknown(@TempDir Path dir) throws IOException {
        Path scratch = scratch(dir);
        assertEquals("unknown", SystemDetectContext.runVersion(dir.resolve("absent").toString(), 5000, scratch));
        assertNoLeftoverFiles(scratch);
    }
}
