package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


class LambdaE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    private static final String LAMBDAS = """
            main() {
                var f = (x: Int) -> x * 2
                println(f(21))
                var g = (a: Int, b: Int) -> a + b
                println(g(3, 4))
                var h = () -> 99
                println(h())
                var s = (nome: String) -> "ola " + nome
                println(s("kof"))
            }
            """;

    @Test
    void lambdasJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDAS);
        runJvm(source, tempDir.resolve("out"), "42\n7\n99\nola kof");
    }

    @Test
    void lambdasNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDAS);
        runNative(source, tempDir.resolve("out"), "42\n7\n99\nola kof");
    }

    private static final String IF_EXPRS = """
            main() {
                var v = if (5 > 3) 10 else 20
                println(v)
                var s = if (5 < 3) "maior" else "menor"
                println(s)
                var n = if (2 + 2 == 4) 100 else 0
                println(n)
                println(if (true) "yes" else "no")
                var chain = if (v == 10) if (n == 100) "both" else "v" else "n"
                println(chain)
            }
            """;

    @Test
    void ifExprsJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, IF_EXPRS);
        runJvm(source, tempDir.resolve("out"), "10\nmenor\n100\nyes\nboth");
    }

    @Test
    void ifExprsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, IF_EXPRS);
        runNative(source, tempDir.resolve("out"), "10\nmenor\n100\nyes\nboth");
    }

    // Captura mutável: mutação FORA da lambda refletida na lambda (fix 02/09 —
    // antes capturava por valor e a leitura ficava desatualizada).
    private static final String MUTABLE_OUTER = """
            main() {
                var offset = 10
                var f2 = (x: Int) -> x + offset
                println(f2(5))
                offset = 20
                println(f2(5))
            }
            """;

    @Test
    void mutableCaptureOuterMutationJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, MUTABLE_OUTER);
        runJvm(source, tempDir.resolve("out"), "15\n25");
    }

    // Captura mutável: a lambda ESCREVE numa variável externa (funciona em
    // JVM e Native).
    private static final String MUTABLE_LAMBDA_WRITES = """
            main() {
                var counter = 0
                var inc = () -> { counter = counter + 1 }
                inc()
                inc()
                println(counter)
            }
            """;

    @Test
    void mutableCaptureLambdaWritesJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, MUTABLE_LAMBDA_WRITES);
        runJvm(source, tempDir.resolve("out"), "2");
    }

    @Test
    void mutableCaptureLambdaWritesNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, MUTABLE_LAMBDA_WRITES);
        runNative(source, tempDir.resolve("out"), "2");
    }

    // Lambda retornando lambda que captura variável do lambda EXTERNO:
    // (a) -> (b) -> a + b. O lambda interno só alcança `a` se o externo o
    // capturar e repassar via constructor.
    private static final String LAMBDA_RETURNS_LAMBDA_CAPTURE = """
            main() {
                var make = (a: Int) -> (b: Int) -> a + b
                println(make(5)(3))
            }
            """;

    @Test
    void lambdaReturnsLambdaCaptureJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDA_RETURNS_LAMBDA_CAPTURE);
        runJvm(source, tempDir.resolve("out"), "8");
    }

    @Test
    void lambdaReturnsLambdaCaptureNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDA_RETURNS_LAMBDA_CAPTURE);
        runNative(source, tempDir.resolve("out"), "8");
    }

    // Lambda retornando lambda retornando lambda: (a) -> (b) -> (c) -> a+b+c.
    // `a` precisa ser capturado pelo externo e repassado pelos dois níveis
    // intermediários — regressão do bug em que collectCaptures não descia em
    // lambdas aninhados (o lambda intermediário perdia a captura `a` e o
    // lambda mais interno somava o ponteiro `this` no lugar).
    private static final String TRIPLE_NESTED = """
            main() {
                var make = (a: Int) -> (b: Int) -> (c: Int) -> a + b + c
                var r1 = make(5)
                var r2 = r1(3)
                var r3 = r2(10)
                println(r3)
            }
            """;

    @Test
    void tripleNestedJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TRIPLE_NESTED);
        runJvm(source, tempDir.resolve("out"), "18");
    }

    @Test
    void tripleNestedNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TRIPLE_NESTED);
        runNative(source, tempDir.resolve("out"), "18");
    }

    // Inline triple-nested (make(5)(3)(10) sem variáveis intermediárias):
    // o receiver do invoke é a própria chamada que retorna a lambda.
    private static final String TRIPLE_NESTED_INLINE = """
            main() {
                var make = (a: Int) -> (b: Int) -> (c: Int) -> a + b + c
                println(make(5)(3)(10))
            }
            """;

    @Test
    void tripleNestedInlineJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TRIPLE_NESTED_INLINE);
        runJvm(source, tempDir.resolve("out"), "18");
    }

    @Test
    void tripleNestedInlineNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TRIPLE_NESTED_INLINE);
        runNative(source, tempDir.resolve("out"), "18");
    }
}