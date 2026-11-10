package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §769 — reading a public PRIMITIVE instance field of an external Java object
 * ({@code Int n = flyway.migrate().migrationsExecuted}) emitted the right
 * {@code getfield ...:I} but the lowering typer inferred the field as UNKNOWN,
 * so the consumer unboxed it as a reference ({@code checkcast Integer} over an
 * {@code int}) and the class died at load with {@code VerifyError}. The typer
 * now resolves the field descriptor exactly like the lowerer.
 *
 * <p>Goldens are the output of the same reads compiled with javac.
 */
class ExternalInstanceFieldE2ETest {

    private static final String RESULT = """
        package ext;
        public class Result {
            public int migrationsExecuted = 3;
            public boolean success = true;
            public long total = 7L;
            public double ratio = 0.5;
            public String name = "r";
            public static Result make() { return new Result(); }
        }
        """;

    @TempDir Path tmp;

    private Path buildJar() throws Exception {
        Path src = tmp.resolve("src/ext");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Result.java"), RESULT);
        Path classes = tmp.resolve("cls");
        Files.createDirectories(classes);
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        assertNotNull(jc, "the JDK JavaCompiler is required (surefire must run on a JDK)");
        assertEquals(0, jc.run(null, null, null, "--release", "21", "-d", classes.toString(),
                src.resolve("Result.java").toString()));
        Path jar = tmp.resolve("ext-fields.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Path c : Files.walk(classes).filter(f -> f.toString().endsWith(".class")).toList()) {
                z.putNextEntry(new ZipEntry(classes.relativize(c).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(c));
            }
        }
        return jar;
    }

    private String compileAndRun(String kf) throws Exception {
        Path jar = buildJar();
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, kf);
        Path out = tmp.resolve("out");
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(
                new URL[]{out.toUri().toURL(), jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString();
    }

    @Test
    void primitiveFieldsReadIntoTypedLocals() throws Exception {
        String out = compileAndRun("""
            import ext.Result
            main() {
                val r = Result.make()
                Int n = r.migrationsExecuted
                Long t = r.total
                Bool ok = r.success
                println(n)
                println(t)
                println(ok)
            }
            """);
        assertEquals("3\n7\ntrue\n", out);
    }

    @Test
    void primitiveFieldsInConcatArithmeticAndConditions() throws Exception {
        String out = compileAndRun("""
            import ext.Result
            main() {
                val r = Result.make()
                println("n=" + Result.make().migrationsExecuted)
                println(r.total + 1)
                println(r.ratio * 2.0)
                if (r.success) { println("ok") }
                println(r.name)
            }
            """);
        assertEquals("n=3\n8\n1.0\nok\nr\n", out);
    }
}
