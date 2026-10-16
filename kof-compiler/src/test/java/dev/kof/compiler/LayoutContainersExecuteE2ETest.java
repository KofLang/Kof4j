package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** #861: a família dos oito containers de layout EXECUTADA nos dois alvos.
 *
 *  Os testes por widget (`BoxE2ETest`, `StackE2ETest`, `SpacerE2ETest`,
 *  `ScrollE2ETest`, `GridE2ETest`, `WrapE2ETest`, `CenterE2ETest`,
 *  `AlignE2ETest`) asserem o lowering — e a face JS deles é checada por TEXTO
 *  (`emitted.contains("kofUiBoxNew")` / rejeição do global nu), o que não pega
 *  uma chamada com forma errada (arg count), só o nome ausente. Este teste
 *  fecha a outra metade: o programa da família inteira de uma vez RODA até o
 *  fim na JVM e sob `node`. Os oito no MESMO programa é de propósito — a
 *  issue #861 é sobre a família, e um erro de emissão em qualquer um deles
 *  derruba a execução.
 */
class LayoutContainersExecuteE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** Os oito construtores vazios + `bind` onde o widget tem filhos
     *  (`Spacer` não tem — só construção, e o runtime trata `0` como flex 1). */
    private static final String PROGRAM = """
            main() {
                var w = Window("T")
                var b = Box()
                var s = Stack()
                var sp = Spacer()
                var sc = Scroll()
                var g = Grid()
                var wr = Wrap()
                var c = Center()
                var a = Align()
                b.bind(Label("a"))
                s.bind(Label("b"))
                sc.bind(Label("c"))
                g.bind(Label("d"))
                wr.bind(Label("e"))
                c.bind(Label("f"))
                a.bind(Label("g"))
                w.bind(b)
                w.bind(s)
                w.bind(sp)
                w.bind(sc)
                w.bind(g)
                w.bind(wr)
                w.bind(c)
                w.bind(a)
                w.show()
                println("done")
            }
            """;

    private String run(String... cmd) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), String.join(" ", cmd) + " exit code, output:\n" + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void eightContainersRunOnJvmAndNode(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("containers.kf");
        Files.writeString(source, PROGRAM);

        Path jvm = tempDir.resolve("jvm");
        CompilationResult rj = driver.compile(source, jvm, Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        assertEquals("done", run("java", "-cp", jvm.toString(), "Default.Main"),
                "the eight containers must RUN on the JVM (the #861 face was a VerifyError)");

        Path js = tempDir.resolve("js");
        CompilationResult rs = driver.compile(source, js, Target.JS);
        assertTrue(rs.success(), "JS compile: " + rs.diagnostics().getDiagnostics());
        String emitted = Files.readString(js.resolve("Default.mjs"));
        for (String bare : new String[]{"= Box(", "= Stack(", "= Spacer(", "= Scroll(",
                "= Grid(", "= Wrap(", "= Center(", "= Align("}) {
            assertFalse(emitted.contains(bare),
                    "must not emit a bare global " + bare + " (ReferenceError at runtime)");
        }
        assertEquals("done", run("node", js.resolve("Default.mjs").toString()),
                "the emitted .mjs must RUN to completion (the #861 face was a runtime ReferenceError)");
    }
}