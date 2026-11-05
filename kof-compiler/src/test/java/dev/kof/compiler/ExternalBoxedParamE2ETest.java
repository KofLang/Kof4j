package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §813 — a Kof primitive passed to a Java parameter of its BOXED type
 * ({@code Boolean}, {@code Integer}, {@code Long}, {@code Double}) was SEM014
 * ("expected 'Boolean' but got 'Bool'"), so builder calls such as the AWS SDK
 * {@code pathStyleAccessEnabled(Boolean)} could not be written. Method
 * invocation conversion boxes (JLS 5.3), and so does the call site the
 * lowering already emits for a reference formal; the argument check now
 * accepts it, including boxing followed by widening to {@code Number}.
 */
class ExternalBoxedParamE2ETest {

    @TempDir Path tmp;

    private Path buildJar() throws Exception {
        Path src = tmp.resolve("src/ext");
        Files.createDirectories(src);
        Files.writeString(src.resolve("B.java"), """
            package ext;
            public class B {
                public Boolean last;
                public B path(Boolean b) { last = b; return this; }
                public static String flag(Boolean b) { return "flag=" + b; }
                public static String num(Integer i) { return "num=" + i; }
                public static String lng(Long l) { return "lng=" + l; }
                public static String dbl(Double d) { return "dbl=" + d; }
                public static String any(Number n) { return "any=" + n; }
            }
            """);
        Path cls = tmp.resolve("cls");
        Files.createDirectories(cls);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-d", cls.toString(), src.resolve("B.java").toString()));
        Path jar = tmp.resolve("b.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            z.putNextEntry(new ZipEntry("ext/B.class"));
            z.write(Files.readAllBytes(cls.resolve("ext/B.class")));
        }
        return jar;
    }

    private CompilationResult compile(Path jar, String kf) throws Exception {
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, kf);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        return d.compile(main, tmp.resolve("out"), Target.JVM);
    }

    @Test
    void kofPrimitivesBoxIntoJavaWrapperParameters() throws Exception {
        Path jar = buildJar();
        CompilationResult r = compile(jar, """
            import ext.B
            main() {
                Bool t = true
                println(B.flag(t))
                println(B.flag(false))
                println(B.num(3))
                Int? maybe = 5
                println(B.num(maybe))
                println(B.lng(4L))
                println(B.dbl(1.5))
                println(B.any(7))
                println(B.any(2.5))
                println(B().path(true).last)
            }
            """);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{tmp.resolve("out").toUri().toURL(),
                jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("flag=true\nflag=false\nnum=3\nnum=5\nlng=4\ndbl=1.5\nany=7\nany=2.5\ntrue\n",
                buf.toString());
    }

    @Test
    void aMismatchedWrapperIsStillSem014() throws Exception {
        CompilationResult r = compile(buildJar(), """
            import ext.B
            main() {
                println(B.flag(1))
                println(B.num(true))
            }
            """);
        assertFalse(r.success(), "Int into Boolean and Bool into Integer must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM014".equals(d.code())),
                "expected SEM014, was: " + r.diagnostics().getDiagnostics());
    }
}
