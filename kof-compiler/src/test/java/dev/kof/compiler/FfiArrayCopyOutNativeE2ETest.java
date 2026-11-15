package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A fatia COPY-OUT de arrays no NATIVO x86-64 (o #1156 face — o espelho
 * nativo da fatia JVM pousada em 15/11): um `extern` com parâmetro `Double[]`/
 * `Int[]` atravessa como o PONTEIRO DO PAYLOAD direto (obj+24 — o mesmo
 * padrão do `Buffer(U8)` fatia A2; o heap Kof nativo é free-list+bump e os
 * objetos NÃO se movem, então o ponteiro é estável durante a chamada) — a C
 * escreve no payload e o array Kof ENXERGA depois da chamada.
 *
 * RED medido antes da fatia: o `kof_ffi_pack_array` copiava o payload para um
 * buffer de arena e passava O PONTEIRO DA CÓPIA — a C escrevia na cópia e o
 * array Kof permanecia com os originais.
 */
class FfiArrayCopyOutNativeE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            void nfill_double(double* arr, int n) {
                for (int i = 0; i < n; i++) arr[i] = (double)(i * 10);
            }
            void nfill_int(int* arr, int n) {
                for (int i = 0; i < n; i++) arr[i] = i + 100;
            }
            """;

    private static final String KOF_SRC = """
            foreign module fixtures {
                library "%s"
                abi c
                ownership borrowed
                extern nfill_double(Double[] arr, Int n): Void
                extern nfill_int(Int[] arr, Int n): Void
            }

            main() {
                var d = new Double[3]
                nfill_double(d, 3)
                println(d[0])
                println(d[1])
                println(d[2])
                var i = new Int[2]
                nfill_int(i, 2)
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

    private static String runNative(Path out) throws Exception {
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary " + bin + " must exist");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().transferTo(baos);
        int rc = p.waitFor();
        assertEquals(0, rc, "native run exit code, output: " + baos.toString(StandardCharsets.UTF_8));
        return baos.toString(StandardCharsets.UTF_8).trim();
    }

    @Test
    void arrayCopyOutFlowsBackOnNativeX86(@TempDir Path dir) throws Exception {
        Path so = compileHostLib(dir);
        Path src = dir.resolve("copyout-native.kf");
        Files.writeString(src, KOF_SRC.formatted(so, so));
        Path out = dir.resolve("out-copyout-native");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        String o = runNative(out);
        // RED (antes da fatia): os originais "0.0\n0.0\n0.0\n0\n0" — a C
        // escreveu na cópia da arena. GREEN (a fatia): os valores da C.
        assertEquals("0.0\n10.0\n20.0\n100\n101", o, "o copy-out nativo de arrays");
    }
}
