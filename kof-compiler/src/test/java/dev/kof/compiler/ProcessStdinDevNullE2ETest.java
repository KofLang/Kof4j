package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessStdinDevNullE2ETest extends ShellSupport {

    private static final String SOURCE = """
        main() {
            var r = process.run("java", "-version")
            println("exit=" + r.exitCode)
        }
        """;

    @Test
    void jvmProcessRunSpawnsWithoutUnixDevNull() throws Exception {
        Files.writeString(tmp.resolve("S.kf"), SOURCE);
        Run jvm = runJvm(tmp.resolve("S.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        assertTrue(jvm.output().contains("exit=0"), () -> "JVM output: " + jvm.output());
    }

    @Test
    void jsProcessRunSpawnsWithoutUnixDevNull() throws Exception {
        Files.writeString(tmp.resolve("S.kf"), SOURCE);
        Run js = runJs(tmp.resolve("S.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        assertTrue(js.output().contains("exit=0"), () -> "JS output: " + js.output());
    }
}
