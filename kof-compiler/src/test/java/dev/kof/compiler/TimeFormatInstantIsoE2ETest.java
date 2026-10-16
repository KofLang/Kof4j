package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #857 — `time.formatInstantIso(Long millis)`: o INSTANTE completo em UTC
 * (`2026-10-14T16:09:47.555Z`), que faltava ao `kof.time` (havia `now()` em
 * milissegundos e `todayIso()` só com a data).
 *
 * <p>A face existe onde está a maquinaria de calendário — a família JVM
 * (JVM/SCRIPT), a MESMA decisão de `todayIso`/`formatDateIso` — e em JS/nativo o
 * alvo é DIAGNOSTICADO (`TIME005`), nunca um furo silencioso (R6).
 */
class TimeFormatInstantIsoE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static String program(String millis) {
        return """
                main() {
                    println(time.formatInstantIso(%s))
                }
                """.formatted(millis);
    }

    private String jvm(String millis, Path dir, String tag) throws Exception {
        Path src = dir.resolve(tag + ".kf");
        Files.writeString(src, program(millis));
        Path out = dir.resolve(tag + "-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "JVM run: " + o);
        return o;
    }

    @Test
    void epochAndMillisOnJvmAndScript(@TempDir Path dir) throws Exception {
        assertEquals("1970-01-01T00:00:00.000Z", jvm("0L", dir, "epoch"), "epoca exata");
        // ANTES da epoca (floorDiv, nunca truncamento para zero).
        assertEquals("1969-12-31T23:59:59.999Z", jvm("-1L", dir, "pre"), "instante antes da epoca");
        // O formato da issue (data+hora+FRAÇÃO, UTC) contra o ORACULO do host:
        // java.time.Instant.ofEpochMilli(...).toString() da o mesmo ISO-8601 —
        // e um valor com milissegundos != 0 (exato o toString() omite a fracao).
        long sample = 1760458187555L;
        String expected = java.time.Instant.ofEpochMilli(sample).toString();
        assertTrue(expected.endsWith(".555Z"), "o oraculo tem a fracao: " + expected);
        assertEquals(expected, jvm(sample + "L", dir, "sample"), "o instante contra o oraculo");
        assertEquals(expected, jvm(sample + "L", dir, "iso2"), "e estavel entre execucoes");

        Path src = dir.resolve("script.kf");
        Files.writeString(src, program("1760458187555L"));
        KofInterpreter.Result s = driver.interpret(java.util.List.of(src), dir, new String[0]);
        assertEquals(0, s.exitCode(), "Script exit, stderr=" + s.stderr());
        assertEquals(java.time.Instant.ofEpochMilli(1760458187555L).toString(), s.stdout().trim(),
                "Script tem a MESMA face (familia JVM)");
    }

    @Test
    void jsAndNativeAreDiagnosedNotSilent(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("gap.kf");
        Files.writeString(src, program("0L"));
        for (Target t : new Target[]{Target.JS, Target.NATIVE}) {
            CompilationResult r = driver.compile(src, dir.resolve("gap-" + t), t);
            assertFalse(r.success(), "sem a maquinaria de calendario o alvo DIAGNOSTICA (" + t + ")");
            String d = r.diagnostics().getDiagnostics().toString();
            assertTrue(d.contains("TIME005"), "o diagnostico e o gap nomeado (" + t + "): " + d);
        }
    }
}
