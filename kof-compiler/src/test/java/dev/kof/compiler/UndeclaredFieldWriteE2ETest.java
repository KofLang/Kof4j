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

/**
 * #1099 — `this.y = y` on a class that declares no `y` compiled CLEAN and
 * died `NoSuchFieldError` at the first construction (the JVM) or stored
 * PHANTOM fields (Script/JS). The read of the same field already reported
 * SEM025; the WRITE now reports the same diagnosis when the hierarchy is
 * all-Kof. A class with a JAVA superclass stays out (it may write an
 * inherited Java field the Kof hierarchy walk does not see — `this.modCount`
 * on an ArrayList subclass).
 */
class UndeclaredFieldWriteE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void undeclaredFieldWriteIsSem025Jvm() throws Exception {
        Path dir = tmp.resolve("u1099");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), """
                class A {
                    String x
                    constructor(String x, String y) {
                        this.x = x
                        this.y = y
                    }
                }
                main() {
                    val a = A("p", "q")
                    println(a.x)
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertFalse(r.success(), "the undeclared write must NOT compile clean");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM025"),
                "the SEM025 diagnosis: " + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("Cannot resolve field 'y' on type 'A'"),
                "the read's exact diagnosis: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void declaredFieldWriteStillCompilesJvm() throws Exception {
        // The parity guard: the RIGHT writes keep compiling and running.
        Path dir = tmp.resolve("ok1099");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), """
                class A {
                    String x
                    String y
                    constructor(String x, String y) {
                        this.x = x
                        this.y = y
                    }
                }
                main() {
                    val a = A("p", "q")
                    println(a.x + a.y)
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(new URL[]{out.toUri().toURL()},
                    getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals("pq", stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void undeclaredFieldWriteIsSem025OnScriptToo() throws Exception {
        // The Script face stored PHANTOM fields (the issue matrix:
        // it printed `p`) — the named error is the contract there too.
        Path dir = tmp.resolve("script1099");
        Files.createDirectories(dir);
        String src = "class A {\n"
                + "    String x\n"
                + "    constructor(String x, String y) {\n"
                + "        this.x = x\n"
                + "        this.y = y\n"
                + "    }\n"
                + "}\n"
                + "main() {\n"
                + "    val a = A(\"p\", \"q\")\n"
                + "    println(a.x)\n"
                + "}\n";
        Files.writeString(dir.resolve("Main.kf"), src);
        // the prepare phase fails with the named error (the compile is the
        // gate — the interpreter never runs the phantom write)
        var e = assertThrows(dev.kof.compiler.KofInterpretException.class,
                () -> driver.interpret(List.of(dir.resolve("Main.kf")), dir, new String[0]));
        assertTrue(String.valueOf(e).contains("SEM025")
                        && String.valueOf(e).contains("Cannot resolve field 'y' on type 'A'"),
                "the SEM025 diagnosis: " + e);
    }
}
