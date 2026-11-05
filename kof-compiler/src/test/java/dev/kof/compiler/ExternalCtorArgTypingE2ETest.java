package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The semantic analyzer never typed the arguments of an external (JDK or jar)
 * constructor, so a call used directly as one reached the lowering untyped and
 * the constructor was emitted as {@code <init>(Object)}:
 * {@code new File(System.getProperty(k))} and
 * {@code new ByteArrayInputStream(s.getBytes())} died with
 * {@code NoSuchMethodError}, while the same value through a local worked.
 * An undefined name inside such an argument was not diagnosed at all.
 */
class ExternalCtorArgTypingE2ETest {

    @TempDir Path tmp;

    private CompilationResult compile(String kf) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, kf);
        return new CompilerDriver().compile(src, tmp.resolve("out"), Target.JVM);
    }

    @Test
    void aCallPassedToAJdkConstructorKeepsItsType() throws Exception {
        CompilationResult r = compile("""
            import java.lang.System
            import java.io.File
            import java.io.ByteArrayInputStream
            main() {
                println(new File(System.getProperty("java.io.tmpdir")).exists())
                println(new ByteArrayInputStream("hi".getBytes()).available())
            }
            """);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{tmp.resolve("out").toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("true\n2\n", buf.toString());
    }

    @Test
    void anUndefinedNameInsideTheArgumentIsDiagnosed() throws Exception {
        CompilationResult r = compile("""
            import java.io.File
            main() {
                println(new File(nowhere).exists())
            }
            """);
        assertFalse(r.success(), "an undefined name must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM011".equals(d.code())),
                "expected SEM011, was: " + r.diagnostics().getDiagnostics());
    }
}
