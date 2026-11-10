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
 * §775 — a JDK throwable is a Kof builtin for throw/catch, so
 * {@code ExternalClasspath.knows} excluded it and every member the builtin
 * registry does not list ({@code SQLException.getSQLState()},
 * {@code getErrorCode()}) was typed UNKNOWN: the call was emitted as
 * {@code getSQLState()Ljava/lang/Object;} ({@code NoSuchMethodError}) and the
 * next use of the value had an empty owner ({@code ClassFormatError: Illegal
 * class name ""}). A {@code new} or an {@code as} cast to such a class lost
 * its type the same way. Those members now resolve on the classpath.
 */
class JdkThrowableMembersE2ETest {

    @TempDir Path tmp;

    private CompilationResult compile(String kf) throws Exception {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, kf);
        return new CompilerDriver().compile(source, tmp.resolve("out"), Target.JVM);
    }

    private String run(String kf) throws Exception {
        CompilationResult r = compile(kf);
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
        return buf.toString();
    }

    @Test
    void throwableSpecificMembersKeepTheirJdkTypes() throws Exception {
        String out = run("""
            import java.sql.SQLException
            main() {
                val ex = new SQLException("dup", "23505", 7)
                var s = ex.getSQLState()
                println(s.startsWith("23"))
                println(ex.getErrorCode() + 1)
                println(ex.getMessage())
            }
            """);
        assertEquals("true\n8\ndup\n", out);
    }

    @Test
    void aCastToAJdkThrowableIsTyped() throws Exception {
        String out = run("""
            import java.sql.SQLException
            String state(Object o) {
                val se = o as SQLException
                return se.getSQLState()
            }
            main() {
                println(state(new SQLException("m", "42000")))
            }
            """);
        assertEquals("42000\n", out);
    }

    @Test
    void anUnknownMemberOfAJdkThrowableIsSem025() throws Exception {
        CompilationResult r = compile("""
            import java.sql.SQLException
            main() {
                val ex = new SQLException("m")
                println(ex.bogus())
            }
            """);
        assertFalse(r.success(), "an unknown member must not compile into an empty-owner call");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM025".equals(d.code())),
                "expected SEM025, was: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void kofStringThrowAndCatchAreUnchanged() throws Exception {
        String out = run("""
            main() {
                try {
                    throw "x"
                } catch (String e) {
                    println("caught " + e)
                }
                val t = new RuntimeException("r")
                println(t.getMessage())
            }
            """);
        assertEquals("caught x\nr\n", out);
    }
}
