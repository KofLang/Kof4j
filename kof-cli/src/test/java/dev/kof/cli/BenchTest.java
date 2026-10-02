package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bench — parse de opções numéricas com diagnóstico limpo (CodeQL
 * java/uncaught-number-format-exception, 14/09). Antes do fix,
 * `--iterations abc` estourava NumberFormatException na stack; agora
 * retorna 1 com mensagem no stderr. Matriz Q3: happy path (valor válido
 * não-crash) + entrada inválida + as duas formas (--op valor / --op=valor).
 */
class BenchTest {

    private record Run(int code, String err) {}

    private static Run runCapturing(String... args) {
        PrintStream original = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(buf, true, StandardCharsets.UTF_8));
            int code = Bench.run(args);
            return new Run(code, buf.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(original);
        }
    }

    @Test
    void invalidIntOptionExitsWithDiagnosis() {
        Run r = runCapturing("bench", "--iterations", "abc");
        assertEquals(1, r.code, "código de saída 1 em vez de stack-trace");
        assertTrue(r.err.contains("--iterations"), "nome da opção no diagnóstico: " + r.err);
        assertFalse(r.err.contains("Exception"), "sem exceção crua: " + r.err);

        Run r2 = runCapturing("bench", "--warmup=xyz");
        assertEquals(1, r2.code, "forma --op=valor também diagnosticada");
        assertTrue(r2.err.contains("--warmup"), r2.err);
    }

    @Test
    void invalidThresholdOptionExitsWithDiagnosis() {
        Run r = runCapturing("bench", "--threshold", "não-é-número");
        assertEquals(1, r.code);
        assertTrue(r.err.contains("--threshold"), r.err);
        assertFalse(r.err.contains("Exception"), r.err);

        Run r2 = runCapturing("bench", "--threshold=1.2.3");
        assertEquals(1, r2.code);
        assertTrue(r2.err.contains("--threshold"), r2.err);
    }

    @Test
    void validNumericOptionsDoNotCrash() {
        // valores válidos seguem o fluxo normal (sem benchmarks no cwd de
        // teste → exit 1 por "no benchmarks", NÃO por NumberFormatException)
        Run r = runCapturing("bench", "--iterations", "2", "--warmup=0", "--threshold", "1.5");
        assertEquals(1, r.code, "chega ao estágio de descoberta de benchmarks");
        assertTrue(r.err.contains("benchmark"), "diagnóstico de benchmarks, não de parse: " + r.err);
        assertFalse(r.err.contains("invalid value"), r.err);
        assertFalse(r.err.contains("Exception"), r.err);
    }

    @Test
    void negativeIterationsClampsInsteadOfCrashing() {
        // borda numérica: negativo é parseável (não é NumberFormatException) e
        // o clamp existente (iterations < 1 → 1) continua valendo
        Run r = runCapturing("bench", "--iterations", "-5");
        assertEquals(1, r.code);
        assertTrue(r.err.contains("benchmark"), "passa pelo parse e cai no discovery: " + r.err);
    }

    /**
     * Regressão (unidade): `wallNanos` é nanossegundos, mas a métrica publicada
     * (`ms`, baseline JSON, guarda absoluta de 10 ms) é milissegundos. Antes, o
     * valor cru em ns era gravado sob a chave `ms` — a coluna imprimia ~10^6× o
     * tempo real. Prova medível: 500_000_000 ns = 500 ms.
     */
    @Test
    void wallTimeIsPublishedInMillisecondsNotNanoseconds() {
        assertEquals(0L, Bench.medianMs(java.util.List.of()), "sem amostras = 0 ms");
        assertEquals(500L, Bench.medianMs(java.util.List.of(500_000_000L)), "500 ms");
        assertEquals(1L, Bench.medianMs(java.util.List.of(1_000_000L)), "1 ms");
        // mediana par: média das duas centrais, ainda em ms
        assertEquals(15L, Bench.medianMs(java.util.List.of(20_000_000L, 10_000_000L)), "(20+10)/2 ms");
    }

    /**
     * Regressão (unidade + estatística): `cpu_ms` é a mediana do CPU time
     * (user+system) de UMA iteração, em ms — não o acumulado das iterações.
     * Antes o somatório era gravado, então `--iterations 3` inflava a coluna
     * por 3 (700→2240 no `pipelines/map`) enquanto `ms` já era mediana.
     */
    @Test
    void cpuTimeIsMeanPerIterationInMilliseconds() {
        assertEquals(0L, Bench.medianCpuMs(java.util.List.of()), "sem amostras = 0 ms");
        assertEquals(700L, Bench.medianCpuMs(java.util.List.of(700_000L)), "700 ms de uma iteração");
        // 3 iterações de ~700ms NÃO podem virar 2100 (o bug antigo somava)
        assertEquals(700L, Bench.medianCpuMs(java.util.List.of(700_000L, 710_000L, 690_000L)),
                "mediana por iteração, não soma");
        assertEquals(15L, Bench.medianCpuMs(java.util.List.of(20_000L, 10_000L)), "(20+10)/2 ms");
    }
}
