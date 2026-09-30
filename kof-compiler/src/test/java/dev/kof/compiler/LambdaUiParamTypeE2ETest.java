package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LambdaUiParamTypeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static String drain(Process p) throws IOException, InterruptedException {
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
        return output;
    }

    private void runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            assertEquals(expected, drain(pb.start()), "Unexpected JVM output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private void runNative(Path source, Path outDir, String expected) throws IOException {
        assumeTrue(isLinux(), "Native target runs on Linux");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            assertEquals(expected, drain(pb.start()), "Unexpected native output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    private Path write(Path tempDir, String name, String program) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return source;
    }

    @Test
    void eventLambdaParamKeyFlowsIntoStringParameter(@TempDir Path tempDir) throws IOException {
        Path source = write(tempDir, "eventkey", """
            mostrar(String s) {
                println("tecla: " + s)
            }

            main() {
                var b = Button("x", () -> {})
                b.on("keydown", (e: Event) -> { mostrar(e.key()) })
                b.on("input", (e: Event) -> {
                    String v = e.value()
                    mostrar(v)
                })
                println("ok")
            }
            """);
        runJvm(source, tempDir.resolve("jvm"), "ok");
        runNative(source, tempDir.resolve("native"), "ok");
        CompilationResult js = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        String mjs = Files.readString(tempDir.resolve("js").resolve("Default.mjs"));
        assertTrue(mjs.contains("kofUiEventKey(e)"), "Event accessor lowers to the runtime helper on JS");
    }

    @Test
    void widgetLambdaParamIsTheUiHandle(@TempDir Path tempDir) throws IOException {
        Path source = write(tempDir, "labelparam", """
            main() {
                var l = Label("x")
                var h = (w: Label) -> { w.text = "y" }
                h(l)
                println("ok")
            }
            """);
        runJvm(source, tempDir.resolve("jvm"), "ok");
        runNative(source, tempDir.resolve("native"), "ok");
    }

    @Test
    void userClassNamedEventStillShadowsTheBuiltin(@TempDir Path tempDir) throws IOException {
        Path source = write(tempDir, "shadow", """
            class Event {
                String key() {
                    return "classe-do-usuario"
                }
            }

            mostrar(String s) {
                println("tecla: " + s)
            }

            main() {
                var f = (e: Event) -> { mostrar(e.key()) }
                f(Event())
            }
            """);
        runJvm(source, tempDir.resolve("jvm"), "tecla: classe-do-usuario");
    }
}
