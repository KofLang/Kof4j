package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #933 — o crescimento do {@code Map<String, Int>} no nativo x86-64 corrompia
 * o heap: o {@code kof_map_put} (slice {@code RuntimeMap}) dobrava o
 * BYTE-COUNT no campo de capacidade (usado como SLOTS pelo limiar e pelo
 * índice) E alocava o novo array de VALORES com {@code oldCap*8} (metade do
 * keys) — todo insert além da capacidade antiga escrevia OOB no payload de
 * blocos posteriores (corrupção silenciosa; o coletor conservador morria
 * dentro de {@code kof_gc_mark_transitive} lendo um header sobreposto —
 * SIGSEGV 139 medido com watchpoint no gdb). O gêmeo riscv/aarch64 sempre
 * esteve correto ({@code cap *= 2} em slots; os dois arrays no mesmo
 * tamanho). O gatilho da coleta (o pacing #781 de 1 MiB) é o que fixava o
 * "cliff" perto de ~4k chaves: o volume de alocação das strings temporárias
 * cruza 1 MiB ali e o mark anda na lista corrompida.
 *
 * <p>Oráculo é o JVM. As faces x86 falhavam com rc=139 pré-fix (RED-first
 * provado por stash). O riscv64/aarch64 têm o MESMO family bug (o under-alloc
 * do vals) e o fix do mapa os alcança, mas o alocador cross (.bss 16 MiB + o
 * GC tick no kof_alloc) morre ainda mais cedo por um defeito PRÉ-EXISTENTE —
 * medido: baseline morre em 19 chaves, pós-fix vira OOM em 19 — catalogado no
 * known-bugs §765 (frente do alocador cross, não desta issue). Por isso as
 * faces aqui são JVM+x86 (o alvo da issue #933).
 */
class NativeMapGrowthE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** O repro exato da issue #933. */
    private static final String GROWTH = """
            main() {
                var m = mapOf<String, Int>()
                var i = 0
                while (i < 5000) {
                    m.put("key" + i, i)
                    i = i + 1
                }
                println("ok " + m.size)
            }
            """;

    private static final String GROWTH_GOLDEN = "ok 5000";

    /** Integridade dos valores após o crescimento (o pre-fix corrompia em silêncio). */
    private static final String INTEGRITY = """
            main() {
                var m = mapOf<String, Int>()
                var i = 0
                while (i < 5000) {
                    m.put("key" + i, i * 2)
                    i = i + 1
                }
                var wrong = 0
                i = 0
                while (i < 5000) {
                    var v = m.get("key" + i)
                    if (v != null && v != i * 2) { wrong = wrong + 1 }
                    i = i + 1
                }
                println("size=" + m.size + " wrong=" + wrong)
            }
            """;

    private static final String INTEGRITY_GOLDEN = "size=5000 wrong=0";

    @Test
    void mapGrowthPastFourThousandKeysMatchesTheJvm(@TempDir Path dir) throws Exception {
        assertJvmAndX86(dir, "growth", GROWTH, GROWTH_GOLDEN);
    }

    @Test
    void mapValueIntegrityAcrossGrowthMatchesTheJvm(@TempDir Path dir) throws Exception {
        assertJvmAndX86(dir, "integrity", INTEGRITY, INTEGRITY_GOLDEN);
    }

    private void assertJvmAndX86(Path dir, String name, String source, String golden)
            throws Exception {
        Path src = dir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path jvmOut = dir.resolve(name + "-jvm");
        CompilationResult j = driver.compile(src, jvmOut, Target.JVM);
        assertTrue(j.success(), () -> "JVM compile: " + j.diagnostics().getDiagnostics());
        Process jp = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", jvmOut.toString(), "Default.Main").redirectErrorStream(true).start();
        String jo = NativeRiscv64E2ETest.runBounded(jp, name + " jvm");
        assertEquals(0, jp.exitValue(), () -> "oráculo JVM: " + jo);
        assertEquals(golden, jo.strip());

        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path out = dir.resolve(name + "-x86");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " x86");
        assertEquals(0, p.exitValue(), () -> "x86 rc!=0 (o cliff do #933 voltou?): " + o);
        assertEquals(golden, o.strip());
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
