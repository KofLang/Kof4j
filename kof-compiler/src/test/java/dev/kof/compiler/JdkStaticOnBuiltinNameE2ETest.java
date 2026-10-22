package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JdkStaticOnBuiltinNameE2ETest {

    private static final String SRC = """
            main() {
                println(Char.isDigit('5'))
                println(Character.isLetter('5'))
                println(Char.toUpperCase('q'))
                println(Char.toString('a'))
                println(Byte.parseByte("5") + 1)
                println(Short.parseShort("7"))
            }
            """;

    private static final String OUT = "true\nfalse\nQ\na\n6\n7\n";

    @TempDir
    Path tmp;

    @Test
    void charByteShortStaticsRunJvm() throws Exception {
        Path file = write("jvm", SRC);
        Path out = tmp.resolve("jvm-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals(OUT, buf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void charByteShortStaticsRunScript() throws Exception {
        Path file = write("script", SRC);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), r.stdout() + " " + r.stderr());
        assertEquals(OUT, r.stdout().replace("\r\n", "\n"));
    }

    @Test
    void instanceMethodOnTypeNameIsSem074() throws Exception {
        for (String call : List.of("String.length()", "Integer.intValue()", "Double.isNaN()", "Object.toString()")) {
            Path file = write("inst" + Math.abs(call.hashCode()), "main() {\n    println(" + call + ")\n}\n");
            CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("inst-out"), Target.JVM);
            String diags = String.valueOf(r.diagnostics().getDiagnostics());
            assertFalse(r.success(), call + " compiled: " + diags);
            assertTrue(diags.contains("SEM074"), call + ": " + diags);
        }
    }

    private Path write(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return file;
    }
}
