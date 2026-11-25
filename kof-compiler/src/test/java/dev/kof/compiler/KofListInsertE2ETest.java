package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1224 RED: {@code list.add(i, value)} — a inserção POSICIONAL do Kof (o sítio
 * único de escrita posicional, {@code CollectionCallLowerer}: {@code add} com 2
 * argumentos → {@code kof_list_insert}) — compila nos quatro alvos mas o NATIVO
 * não linka: a fatia de runtime nunca definiu {@code kof_list_insert}, e o
 * {@code kof_list_insert} do {@code NativeX86Calls} só é recusado para Map/Set.
 * O link morre com {@code undefined reference to "kof_list_insert"} — nem chega
 * a progredir. O JVM/Script/JS já têm a face.
 */
class KofListInsertE2ETest {

    private static final String PROG = """
            main() {
                val xs = listOf(1, 2, 3)
                xs.add(1, 9)
                println(xs)
                xs.add(3, 7)
                println(xs.get(3))
                println(xs.size())
            }
            """;

    @Test
    void theNativeFaceLinksAndInserts(@TempDir Path dir) throws Exception {
        write(dir, PROG, "Main.kf");
        Path out = dir.resolve("native");
        CompilationResult r = new CompilerDriver().compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), "native compile/link: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "saida: " + text);
        assertEquals("[1, 9, 2, 3]\n7\n5", text);
    }

    private static final String EDGES = """
            main() {
                val xs = listOf(1, 2, 3)
                xs.add(0, 0)
                xs.add(xs.size(), 4)
                println(xs)
                val ys = listOf<String>()
                ys.add(0, "a")
                ys.add(1, "b")
                ys.add(0, "z")
                println(ys)
                var i = 0
                while (i < 20) {
                    ys.add(0, "p" + i)
                    i = i + 1
                }
                // a lista final: p19..p0 na frente do [z, a, b]
                println(ys.size() + " " + ys.get(0) + " " + ys.get(19))
            }
            """;

    /** As bordas: i = 0, i = size (é um add), lista vazia e o crescimento (mais
     *  de uma vez, o `kof_list_grow` no meio do shift). */
    @Test
    void theNativeEdgesInsert(@TempDir Path dir) throws Exception {
        write(dir, EDGES, "Main.kf");
        Path out = dir.resolve("native-edges");
        CompilationResult r = new CompilerDriver().compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), "native: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "saida: " + text);
        assertEquals("[0, 1, 2, 3, 4]\n[z, a, b]\n23 p19 p0", text);
    }

    private static void write(Path dir, String src, String name) throws IOException {
        Files.writeString(dir.resolve(name), src);
    }

    /** O JVM é o oráculo: a mesma listagem, pelo interpretador do frontend. */
    @Test
    void theJvmOraclePrintsTheInsert(@TempDir Path dir) throws IOException {
        write(dir, PROG, "Main.kf");
        String out = new CompilerDriver().interpret(java.util.List.of(dir.resolve("Main.kf")), dir,
                new String[0]).stdout().trim();
        assertEquals("[1, 9, 2, 3]\n7\n5", out);
    }
}
