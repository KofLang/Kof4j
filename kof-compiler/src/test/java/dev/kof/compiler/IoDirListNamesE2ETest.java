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
 * known-bugs §518 (#631) — {@code Directory.list()} no host JS devolvia o
 * CAMINHO COMPLETO de cada entrada ({@code p.toString()}), enquanto o runtime
 * JVM ({@code kof_io_dir_list}, o contrato documentado "the names") devolve o
 * NOME ({@code getFileName()}). O uso natural — montar {@code pasta + "/" +
 * entrada} — funciona na JVM e no JS produz caminho duplicado/malformado com
 * falha silenciosa na compilação (só explode no open). Fix: o host JS espelha
 * o oracle JVM. Golden = JVM medido 26/09, comparado byte a byte no host JS e
 * no interpretador (Script).
 */
class IoDirListNamesE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private static final String PROGRAM = """
            main() {
                val pasta = "__BASE__/clientes"
                val d = Directory(pasta)
                d.createDirectories()
                File(pasta + "/empresa.txt").writeText("x")
                File(pasta + "/outra.txt").writeText("y")
                for (var entrada in d.list()) {
                    println(entrada)
                    println(File(pasta + "/" + entrada).exists())
                }
            }
            """;

    // Oracle = medido 26/09 no motor JVM: nomes ordenados + o caminho montado
    // com o NOME resolve (exists true). No JS pré-fix a saida era
    // "<base>/clientes/empresa.txt" + exists false (Path invaledo duplicado).
    private static final String EXPECTED =
            "empresa.txt\ntrue\noutra.txt\ntrue\n";

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
    void dirListReturnsNamesOnJsLikeJvm() throws Exception {
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
                "§518/#631: Directory.list() no host JS devolvia o caminho completo "
                        + "(p.toString()) — pasta + \"/\" + entrada sai duplicado e o "
                        + "open so falha em runtime; o contrato (JvmRuntimeIo.kof_io_dir_list, "
                        + "'the names') e getFileName()");
    }

    @Test
    void scriptTargetMatchesJvmOracleForListNames() throws Exception {
        Files.createDirectories(tmp.resolve("w-sc"));
        Path src = tmp.resolve("sc.kf");
        Files.writeString(src, PROGRAM.replace("__BASE__", tmp.resolve("w-sc").toString()));
        KofInterpreter.Result r = driver.interpret(List.of(src), tmp, new String[0]);
        assertEquals(0, r.exitCode(), "Script run failed: " + r.stderr());
        assertEquals(EXPECTED, r.stdout(),
                "§518: o alvo Script deve devolver os NOMES como a JVM");
    }
}
