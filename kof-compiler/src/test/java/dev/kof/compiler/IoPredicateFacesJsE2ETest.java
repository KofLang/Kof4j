package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §517 (#630) — os PREDICADOS do kof.io tipados BOOL pelo typer
 * ({@code KofIo}: file_exists/file_is_file/file_is_dir/path_is_absolute)
 * eram as 4 faces BOOL que faltavam depois do §382: o host JS devolvia o
 * NUMERO 1/0. O contexto booleano funcionava (`if` toma o ramo certo), mas
 * tudo que OBSERVA o valor divergia em silêncio entre alvos:
 * {@code String.valueOf(f.exists())} → "true" na JVM, "1" no JS;
 * {@code listOf(f.exists()).contains(true)} → true na JVM, false no JS.
 * O fix é o KofJsRunner retornar o booleano REAL (mesma casa §382).
 * Golden = programa medido 26/09 no motor JVM (oracle) e comparado byte a
 * byte no host JS (KofJsRunner) e no interpretador (Script).
 */
class IoPredicateFacesJsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private static final String PROGRAM = """
            main() {
                val f = File("__BASE__/p.txt")
                println(f.exists())
                println(String.valueOf(f.writeText("x")))
                println(f.exists())
                println(String.valueOf(f.exists()))
                println(f.isFile())
                val d = Directory("__BASE__")
                println(d.exists())
                println(d.isDirectory())
                val p = Path("__BASE__/p.txt")
                println(p.isAbsolute())
                val l = listOf(f.exists())
                println(l.contains(true))
            }
            """;

    // Oracle = medido 26/09 no motor JVM: exists-miss false | writeText true |
    // exists true | String.valueOf(exists) "true" | isFile true |
    // dir-exists true | isDirectory true | isAbsolute true | contains(true) true
    private static final String EXPECTED =
            "false\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\n";

    private Run runJvm(Path src, Path out, String base) throws Exception {
        Files.writeString(src, PROGRAM.replace("__BASE__", base));
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out, String base) throws Exception {
        Files.writeString(src, PROGRAM.replace("__BASE__", base));
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        int rc = dev.kof.runtime.KofJsRunner.run(
                out.resolve("Default.mjs"), buf,
                new ByteArrayInputStream(new byte[0]), buf);
        return new Run(rc == 0, buf.toString());
    }

    private static String diags(CompilationResult r) {
        return String.valueOf(r.diagnostics().getDiagnostics());
    }

    @Test
    void predicateFacesMatchJvmOracleOnJsHost() throws Exception {
        Files.createDirectories(tmp.resolve("w-jvm"));
        Files.createDirectories(tmp.resolve("w-js"));
        Run jvm = runJvm(tmp.resolve("jvm.kf"), tmp.resolve("o-jvm"),
                tmp.resolve("w-jvm").toString());
        assertTrue(jvm.ok(), "JVM compile/run failed: " + jvm.output());
        assertEquals(EXPECTED, jvm.output(), "JVM oracle drifted (golden = measured 26/09)");
        Run js = runJs(tmp.resolve("js.kf"), tmp.resolve("o-js"),
                tmp.resolve("w-js").toString());
        assertTrue(js.ok(), "JS run failed rc/out: " + js.output());
        assertEquals(EXPECTED, js.output(),
                "§517/#630: o host JS devolve os PREDICADOS BOOL (file_exists/"
                        + "file_is_file/file_is_dir/path_is_absolute) como 1/0 — "
                        + "String.valueOf da \"1\" e List<Bool>.contains(true) da "
                        + "false; a face observada tem de ser Bool REAL nos dois alvos");
    }

    @Test
    void scriptTargetSharesTheJvmOracleForPredicates() throws Exception {
        Files.createDirectories(tmp.resolve("w-sc"));
        Path src = tmp.resolve("sc.kf");
        Files.writeString(src, PROGRAM.replace("__BASE__", tmp.resolve("w-sc").toString()));
        KofInterpreter.Result r = driver.interpret(List.of(src), tmp, new String[0]);
        assertEquals(0, r.exitCode(), "Script run failed: " + r.stderr());
        assertEquals(EXPECTED, r.stdout(),
                "§517: o alvo Script deve imprimir os predicados como Bool real tambem");
    }
}
