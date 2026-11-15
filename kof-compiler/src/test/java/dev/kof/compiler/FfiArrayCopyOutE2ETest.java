package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A fatia COPY-OUT de arrays (o #1156 face — "arguments the C function
 * WRITES", o `axpy_`/`gemm_` territory): um `extern` com parâmetro `Double[]`/
 * `Int[]` copia o array para a arena da chamada, a C ESCREVE nele, e o
 * conteúdo VOLTA para o array Kof depois do retorno (o mesmo padrão do
 * `Buffer(U8)` INOUT — o loop `copybacks`, o helper `kof_ffi_copy_out`, o
 * espelho do `kof_ffi_copy_in` com o MESMO ValueLayout element-typed; o
 * comprimento do copy é n ELEMENTOS, nunca bytes; `String[]` nunca faz
 * copy-back — o cstr é imutável).
 *
 * RED medido antes da fatia: a C escrevia na CÓPIA da arena e o array Kof
 * permanecia com os valores originais (o copy-back não existia para arrays —
 * só para Buffer; o comentário do copy-in dizia "o C não vê nem altera o
 * array Java").
 */
class FfiArrayCopyOutE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            void fill_double(double* arr, int n) {
                for (int i = 0; i < n; i++) arr[i] = (double)(i * 10);
            }
            void fill_int(int* arr, int n) {
                for (int i = 0; i < n; i++) arr[i] = i + 100;
            }
            """;

    private static final String KOF_SRC = """
            foreign module fixtures {
                library "%s"
                abi c
                ownership borrowed
                extern fill_double(Double[] arr, Int n): Void
                extern fill_int(Int[] arr, Int n): Void
            }

            main() {
                var d = new Double[3]
                fill_double(d, 3)
                println(d[0])
                println(d[1])
                println(d[2])
                var i = new Int[2]
                fill_int(i, 2)
                println(i[0])
                println(i[1])
            }
            """;

    private Path compileHostLib(Path dir) throws Exception {
        Path cFile = dir.resolve("fill.c");
        Files.writeString(cFile, C_SRC);
        Path so = dir.resolve("libfixtures.so");
        Process p = new ProcessBuilder("cc", "-shared", "-fPIC", "-o",
                so.toString(), cFile.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "cc falhou: " + out);
        return so;
    }

    private static String runJvm(Path out) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().transferTo(baos);
        p.waitFor();
        return baos.toString(StandardCharsets.UTF_8).trim();
    }

    @Test
    void arrayCopyOutFlowsBackOnJvm(@TempDir Path dir) throws Exception {
        Path so = compileHostLib(dir);
        Path src = dir.resolve("copyout.kf");
        Files.writeString(src, KOF_SRC.formatted(so, so));
        Path out = dir.resolve("out-copyout");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        String o = runJvm(out);
        // RED (antes da fatia): "0.0\n0.0\n0.0\n0\n0" — a C escrevia na CÓPIA
        // da arena. GREEN (a fatia): os valores escritos pela C voltam para o
        // array Kof (Double com o formato shortest-repr do JVM; Int direto).
        assertEquals("0.0\n10.0\n20.0\n100\n101", o, "o copy-out de arrays (a C escreve, o Kof enxerga)");
    }
}
