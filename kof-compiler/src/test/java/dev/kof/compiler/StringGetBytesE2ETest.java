package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #719: {@code String.getBytes()} accepted by frontend but emitted descriptor
 * {@code ()Ljava/lang/Object;} instead of {@code ()[B}, causing
 * {@code NoSuchMethodError} or {@code ClassFormatError}.
 *
 * JVM executes correctly; JS and Native refuse at compile time with {@code STR003}.
 */
class StringGetBytesE2ETest {

    @TempDir
    Path tmp;

    private record ExecResult(boolean success, String output) {}

    private ExecResult runJvm(String code) throws Exception {
        Path src = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(src, code);
        Path out = Files.createTempDirectory(tmp, "out-jvm");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        if (!r.success()) {
            StringBuilder sb = new StringBuilder("COMPILE_FAIL:\n");
            r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
            return new ExecResult(false, sb.toString());
        }
        PrintStream oldOut = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try {
            URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
            return new ExecResult(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new ExecResult(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    @DisplayName("#719 JVM: String.getBytes() ignored return value executes without NoSuchMethodError")
    void jvmIgnoredReturnValueExecutes() throws Exception {
        String src = """
            main() {
                var s = "hello"
                s.getBytes()
                println("reached")
            }
            """;
        ExecResult r = runJvm(src);
        assertTrue(r.success(), "execution failed: " + r.output());
        assertTrue(r.output().contains("reached"), "expected reached: " + r.output());
    }

    @Test
    @DisplayName("#719 JVM: String.getBytes() stored in variable yields byte array with correct length and content")
    void jvmStoredReturnValueCorrect() throws Exception {
        String src = """
            main() {
                var s = "abc"
                var b = s.getBytes()
                println("len=" + b.size)
                println("b0=" + b[0])
                println("b1=" + b[1])
                println("b2=" + b[2])
            }
            """;
        ExecResult r = runJvm(src);
        assertTrue(r.success(), "execution failed: " + r.output());
        assertTrue(r.output().contains("len=3"), "expected len=3: " + r.output());
        assertTrue(r.output().contains("b0=97"), "expected b0=97: " + r.output());
        assertTrue(r.output().contains("b1=98"), "expected b1=98: " + r.output());
        assertTrue(r.output().contains("b2=99"), "expected b2=99: " + r.output());
    }

    @Test
    @DisplayName("#719 JS: refuses String.getBytes() honestly with STR003 at compile time")
    void jsRefusesGetBytesWithStr003() throws Exception {
        Path src = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(src, """
            main() {
                var b = "abc".getBytes()
            }
            """);
        Path out = Files.createTempDirectory(tmp, "out-js");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JS);
        assertFalse(r.success(), "JS compilation must fail at compile time");
        String diag = r.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("STR003"), "expected STR003, got: " + diag);
    }

    @Test
    @DisplayName("#719 Native: refuses String.getBytes() honestly with STR003 at compile time")
    void nativeRefusesGetBytesWithStr003() throws Exception {
        Path src = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(src, """
            main() {
                var b = "abc".getBytes()
            }
            """);
        Path out = Files.createTempDirectory(tmp, "out-native");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.NATIVE);
        assertFalse(r.success(), "Native compilation must fail at compile time");
        String diag = r.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("STR003"), "expected STR003, got: " + diag);
    }
}
