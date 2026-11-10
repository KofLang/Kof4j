package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §791 — {@code import java.net.http.HttpRequest.BodyPublishers} was split as
 * package {@code java.net.http.HttpRequest} + class {@code BodyPublishers}, a
 * class that does not exist ({@code HttpRequest$BodyPublishers} does), so every
 * member was SEM025. An import whose top-level split does not exist now names
 * the nested class, in the analyzer and in the lowering (which had its own
 * copy of the import resolution), for the JDK and for a jar on the classpath
 * ({@code import jakarta.mail.Message.RecipientType} was PKG006).
 */
class NestedJavaClassImportE2ETest {

    @TempDir Path tmp;

    @Test
    void anImportedNestedJdkClassResolvesItsMembers() throws Exception {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, """
            import java.net.http.HttpRequest.BodyPublishers
            import java.lang.Thread.State
            main() {
                val p = BodyPublishers.ofString("abc")
                println(p.contentLength())
                println(State.NEW.name())
                println(State.valueOf("RUNNABLE").ordinal())
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("3\nNEW\n1\n", buf.toString());
    }

    @Test
    void anImportedNestedJarClassResolvesItsMembers() throws Exception {
        Path src = tmp.resolve("src/ext");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Message.java"), """
            package ext;
            public class Message {
                public static class RecipientType {
                    public static final RecipientType TO = new RecipientType("To");
                    private final String name;
                    RecipientType(String n) { name = n; }
                    public String label() { return name; }
                }
                public static String send(RecipientType t, String who) { return t.label() + ":" + who; }
            }
            """);
        Path cls = tmp.resolve("cls");
        Files.createDirectories(cls);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-d", cls.toString(), src.resolve("Message.java").toString()));
        Path jar = tmp.resolve("mail.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Path c : Files.walk(cls).filter(f -> f.toString().endsWith(".class")).toList()) {
                z.putNextEntry(new ZipEntry(cls.relativize(c).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(c));
            }
        }
        Path source = tmp.resolve("Mail.kf");
        Files.writeString(source, """
            import ext.Message
            import ext.Message.RecipientType
            main() {
                println(RecipientType.TO.label())
                println(Message.send(RecipientType.TO, "ana"))
            }
            """);
        Path out = tmp.resolve("out-jar");
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL(), jar.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("To\nTo:ana\n", buf.toString());
    }
}
