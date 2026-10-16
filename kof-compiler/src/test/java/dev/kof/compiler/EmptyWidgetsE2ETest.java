package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** #873: Form()/Fieldset()/View()/Input()/Style()/Select()/Ul()/Ol()/Table()
 *  sem args passavam no check mas emitiam globais nus no JS
 *  (`let f = Form()`) — ReferenceError na primeira linha, mesma classe do
 *  Row #827. A forma vazia deve baixar para o construtor do runtime com
 *  defaults honestos (listas vazias, "" e 0). */
class EmptyWidgetsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "Exit code should be 0, output:\n" + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    /** Executa o .mjs emitido com node. A face JS desta issue era
     *  `ReferenceError` em TEMPO DE EXECUÇÃO — a checagem textual sozinha não
     *  pega uma chamada com forma errada (arg count), só o nome ausente. */
    private String runNode(Path jsOutDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("node", jsOutDir.resolve("Default.mjs").toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "node exit code, output:\n" + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void emptyWidgetsLowerToRuntimeConstructors(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var w = Window("T")
                    var f = Form()
                    var fs = Fieldset()
                    var v = View()
                    var i = Input()
                    var s = Style()
                    var sel = Select()
                    var u = Ul()
                    var o = Ol()
                    var t = Table()
                    v.bind(Label("x"))
                    w.bind(v)
                    w.show()
                    println("done")
                }
                """;
        Path source = tempDir.resolve("emptywidgets.kf");
        Files.writeString(source, program);
        String out = runJvm(source, tempDir.resolve("jvm"));
        assertEquals("done", out, "JVM should run the program");
        CompilationResult result = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(result.success(), "JS compile should succeed: " + result.diagnostics().getDiagnostics());
        String emitted = Files.readString(tempDir.resolve("js/Default.mjs"));
        String[] fns = {"kofUiFormNew", "kofUiFieldsetNew", "kofUiViewNew", "kofUiInputNew",
                "kofUiStyleNew", "kofUiSelectNew", "kofUiUlNew", "kofUiOlNew", "kofUiTableNew"};
        String[] bare = {"= Form(", "= Fieldset(", "= View(", "= Input(", "= Style(",
                "= Select(", "= Ul(", "= Ol(", "= Table("};
        for (String fn : fns) {
            assertTrue(emitted.contains(fn), "empty ctor must lower to the runtime constructor " + fn);
        }
        for (String b : bare) {
            assertFalse(emitted.contains(b), "must not emit a bare global " + b + " (ReferenceError at runtime)");
        }
        assertEquals("done", runNode(tempDir.resolve("js")),
                "the emitted .mjs must RUN to completion — the issue's JS face was a runtime ReferenceError");
    }
}
