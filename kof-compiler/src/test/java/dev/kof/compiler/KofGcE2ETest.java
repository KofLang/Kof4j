package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * E2E do GC mark-sweep no Native (status.md Bugs #8).
 *
 * O que prova:
 *   1. sweep real — kof_gc_sweep implementado (antes era {@code ret} stub):
 *      anda pela GC list, limpa mark dos vivos, insere mortos na free list
 *      (flag bit1 @24 do header).
 *   2. Paridade de OUTPUT com JVM/JS para programas que alocam muitos
 *      objetos transitórios — o comportamento é indistinguível (não vemos
 *      OOM; o alloc com free-list já absorve muito).
 *
 * NOTA (G-6(a) 19/09): o collect automatico no alloc esta LIGADO — o gatilho
 * de free-list exausta chama {@code kof_gc_collect_now} exatamente 1x por
 * programa (flag no frame), com gate {@code kof_spawn_count==0} (contador
 * cumulativo, incq na entrada de handle_new) e blanket-spill dos 15 GPRs no
 * collect, de modo que o mark conservador ve todo temporario vivo em
 * registrador no call-site. Multithread permanece no comportamento antigo
 * (sem auto-collect; face worker-stack-scan catalogada em
 * docs/development/native-multiarch.md §G-6 e known-bugs §260).
 * {@code kof_gc_collect_now} continua exposto para chamada explicita.
 */
class KofGcE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private void runNative(Path tempDir, String kof, String expected) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, kof);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit, output: " + out);
            assertEquals(expected, out, "output");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void gcAutoCollectFitsUnderMemoryCap(@TempDir Path tempDir) throws IOException {
        // G-6(a) §260(1) — the free-list-exhaustion trigger (gate
        // kof_spawn_count==0, collect_now with the 15-GPR blanket spill)
        // must RECYCLE dead strings instead of mmapping until the cap.
        // Measured history (doc §260, 16/09): without the trigger this loop
        // peaks ~1.2GB and exits 1 under `ulimit -v 256M`; with it, ~1.5MB
        // and exit 0. The cap is applied through bash so the guard is the
        // memory limit itself, not an output heuristic.
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
            main() {
                var acc = 0
                var i = 0
                while (i < 200000) {
                    var s = "s" + i
                    acc = acc + s.length()
                    i = i + 1
                }
                println(acc)
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder("bash", "-c",
                    "ulimit -v 262144; exec '" + bin + "'")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "auto-collect must fit 256MB cap, output: " + out);
            assertEquals("1288890", out, "output");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void gcSweepsDeadStrings(@TempDir Path tempDir) throws IOException {
        // Aloca 200k strings fugazes. Sem sweep funcionando, a heap cresceria
        // ate o OOM/timeout; com o sweep o processo termina rapidamente.
        runNative(tempDir, """
                main() {
                    var i = 0
                    while (i < 200000) {
                        var s = "s" + i
                        i++
                    }
                    println("ok")
                }
                """, "ok");
    }

    @Test
    void gcKeepsLiveObjects(@TempDir Path tempDir) throws IOException {
        // GC nao pode coletar objetos ainda referenciados.
        runNative(tempDir, """
                main() {
                    var keep = "keep-me"
                    var xs = listOf(1, 2, 3)
                    var i = 0
                    while (i < 50000) {
                        var junk = "junk" + i
                        xs.add(i % 10)
                        i++
                    }
                    var n = xs.size
                    println(keep)
                    println(n > 100)
                }
                """, "keep-me\ntrue");
    }

    @Test
    @Timeout(20)
    void gcPacesCollectionsOnMonotonicGrowth(@TempDir Path tempDir) throws IOException {
        // #781: with the OLD trigger (kof_gc_collect_now on EVERY free-list
        // miss) a monotonically-growing live set fires N full collections ->
        // O(N^2) marking; the 70k-node reproducer of the issue does not finish
        // in >2 min (kof_gc_mark ~97% of CPU). The pacing threshold (collect
        // only after the arena advanced >= 1 MiB since the last collection)
        // makes this linear-ish. 50k nodes is enough to hang the OLD code well
        // past the timeout while staying fast with the fix. Sum(0..49999) =
        // 1249975000 (fits Int). The @Timeout is the RED-first proof: MEASURED
        // 60.5s on the pre-fix tree (RED) vs ~0.6s after (GREEN), a ~100x
        // margin under the 20s bound.
        runNative(tempDir, """
                record Node(Int value, Node? nextNode)

                main() {
                    var head: Node?
                    var i = 0
                    while (i < 50000) {
                        head = Node(i, head)
                        i = i + 1
                    }
                    var curr = head
                    var sum = 0
                    while (curr != null) {
                        sum = sum + curr.value()
                        curr = curr.nextNode()
                    }
                    println(sum)
                }
                """, "1249975000");
    }

    @Test
    void gcReusesFreedSlots(@TempDir Path tempDir) throws IOException {
        // Sem sweep: memoria cresceria linearmente (cada iter = nova alloc).
        // Com sweep: reusa o slot liberado; aloc grande o suficiente para
        // falhar sem GC (200k * ~64 bytes = 12.8MB, daria mmap massiva).
        runNative(tempDir, """
                main() {
                    var acc = 0
                    var i = 0
                    while (i < 200000) {
                        val r = "x"
                        acc = acc + r.length
                        i++
                    }
                    println(acc)
                }
                """, "200000");
    }
}
