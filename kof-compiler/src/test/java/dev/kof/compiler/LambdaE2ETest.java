package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class LambdaE2ETest extends LambdaSupport {


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

    // ---- fase 4.1: faces acima tambem em Script e JS (paridade B-06) ----

    @Test
    void lambdasScript(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDAS);
        runScript(source, "42\n7\n99\nola kof");
    }

    @Test
    void lambdasJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDAS);
        runJs(source, tempDir.resolve("jsout"), "42\n7\n99\nola kof");
    }

    @Test
    void mutableCaptureOuterMutationScript(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, MUTABLE_OUTER);
        runScript(source, "15\n25");
    }

    @Test
    void mutableCaptureOuterMutationJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, MUTABLE_OUTER);
        runJs(source, tempDir.resolve("jsout"), "15\n25");
    }

    @Test
    void mutableCaptureLambdaWritesScript(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, MUTABLE_LAMBDA_WRITES);
        runScript(source, "2");
    }

    @Test
    void mutableCaptureLambdaWritesJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, MUTABLE_LAMBDA_WRITES);
        runJs(source, tempDir.resolve("jsout"), "2");
    }

    @Test
    void lambdaReturnsLambdaCaptureScript(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDA_RETURNS_LAMBDA_CAPTURE);
        runScript(source, "8");
    }

    @Test
    void lambdaReturnsLambdaCaptureJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDA_RETURNS_LAMBDA_CAPTURE);
        runJs(source, tempDir.resolve("jsout"), "8");
    }

    @Test
    void tripleNestedScript(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TRIPLE_NESTED);
        runScript(source, "18");
    }

    @Test
    void tripleNestedJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TRIPLE_NESTED);
        runJs(source, tempDir.resolve("jsout"), "18");
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

    // bug 8: invocar valor de TIPO DE FUNÇÃO DECLARADO. As lambdas da
    // assinatura implementam a interface sintética; o call site invoca via
    // dispatch por interface (antes: SEM032).
    private static final String DECLARED_FN_TYPE_VAR = """
            main() {
                var s: (Int) -> Int = (x: Int) -> x * 2
                println(s(5))
            }
            """;

    @Test
    void declaredFunctionTypeVarJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, DECLARED_FN_TYPE_VAR);
        runJvm(source, tempDir.resolve("out"), "10");
    }

    @Test
    void declaredFunctionTypeVarNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, DECLARED_FN_TYPE_VAR);
        runNative(source, tempDir.resolve("out"), "10");
    }

    private static final String DECLARED_FN_TYPE_PARAM = """
            Int apply(Int x, (Int) -> Int f) { return f(x) }
            main() {
                var dbl = (x: Int) -> x * 2
                println(apply(5, dbl))
            }
            """;

    @Test
    void declaredFunctionTypeParamJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, DECLARED_FN_TYPE_PARAM);
        runJvm(source, tempDir.resolve("out"), "10");
    }

    @Test
    void declaredFunctionTypeParamNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, DECLARED_FN_TYPE_PARAM);
        runNative(source, tempDir.resolve("out"), "10");
    }

    // bug 127: cast para TIPO-FUNÇÃO (`x as () -> Int` / `as (Int) -> Int`)
    // gerava `checkcast` para a classe inexistente "?" no JVM (VerifyError /
    // CCE). O alvo é a interface SAM sintética da assinatura.
    private static final String CAST_FN_TYPE = """
            main() {
                var l = listOf(() -> 5)
                var g = l.get(0) as () -> Int
                println(g() == 5)
                var o: Object = (x: Int) -> x - 3
                var h = o as (Int) -> Int
                println(h(10))
            }
            """;

    @Test
    void castToFunctionTypeJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CAST_FN_TYPE);
        runJvm(source, tempDir.resolve("out"), "true\n7");
    }

    @Test
    void castToFunctionTypeNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CAST_FN_TYPE);
        runNative(source, tempDir.resolve("out"), "true\n7");
    }

    // bug 155: tipo-função como ARGUMENTO GENÉRICO declarado
    // (`List<(Int) -> Int>`, `listOf<(Int) -> Int>()`). O parser de type-args
    // concatenava os tokens crus sem espaços → "(Int)->Int", que `Type.of` não
    // reconhece → ClassType com nome inválido → ClassFormatError no JVM e
    // COMPILE-FAIL/lixo nos outros 3 targets.
    private static final String DECLARED_FN_TYPE_LIST = """
            main() {
                List<(Int) -> Int> l = listOf((x: Int) -> x + 1)
                println(l.get(0)(5))
                var fs = listOf<(Int) -> Int>()
                fs.add((x: Int) -> x * 2)
                println(fs.get(0)(5))
            }
            """;

    @Test
    void declaredFunctionTypeListJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, DECLARED_FN_TYPE_LIST);
        runJvm(source, tempDir.resolve("out"), "6\n10");
    }

    @Test
    void declaredFunctionTypeListNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, DECLARED_FN_TYPE_LIST);
        runNative(source, tempDir.resolve("out"), "6\n10");
    }

    // §156: lista HETEROGÊNEA de lambdas com a MESMA assinatura —
    // o elemento carregava o className da primeira lambda concreta
    // (Lambda0) e o `kof_list_get` fazia checkcast p/ ela (CCE quando o
    // elemento era Lambda1). O elemento agora desce sem className
    // (dispatch pela interface SAM sintética, bug 8).
    private static final String HETEROGENEOUS_LAMBDA_LIST = """
            main() {
                var l = listOf((x: Int) -> x + 1, (x: Int) -> x * 2)
                println(l.get(1)(5))
                println(l.get(0)(5))
                for (var f in l) { println(f(10)) }
            }
            """;

    @Test
    void heterogeneousLambdaListJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, HETEROGENEOUS_LAMBDA_LIST);
        runJvm(source, tempDir.resolve("out"), "10\n6\n11\n20");
    }

    @Test
    void heterogeneousLambdaListNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, HETEROGENEOUS_LAMBDA_LIST);
        runNative(source, tempDir.resolve("out"), "10\n6\n11\n20");
    }

    // #119: `g` chama `f` sem receiver (`f(y)`) — uma variável local de tipo
    // função capturada do escopo externo, não uma função top-level. O parser
    // não distingue as duas formas de MethodCallExpr, e CompilerCaptures só
    // tratava IdentifierExpr como candidato a captura. `g` perdia `f` como
    // campo da classe sintética; no JS isso vira `ReferenceError: f is not
    // defined` em runtime (JVM/Native falhavam por símbolo não resolvido).
    private static final String NESTED_LAMBDA_BARE_CALL_CAPTURE = """
            main() {
                var f = (x: Int) -> x + 1
                var g = (y: Int) -> f(y) * 2
                println(g(3))
            }
            """;

    @Test
    void nestedLambdaBareCallCaptureJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, NESTED_LAMBDA_BARE_CALL_CAPTURE);
        runJvm(source, tempDir.resolve("out"), "8");
    }

    @Test
    void nestedLambdaBareCallCaptureNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, NESTED_LAMBDA_BARE_CALL_CAPTURE);
        runNative(source, tempDir.resolve("out"), "8");
    }

    @Test
    void nestedLambdaBareCallCaptureJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, NESTED_LAMBDA_BARE_CALL_CAPTURE);
        Path outDir = tempDir.resolve("out-js");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        Path entry;
        try (var s = Files.walk(outDir)) {
            entry = s.filter(p -> p.getFileName().toString().equals("Default.mjs"))
                    .findFirst().orElseThrow();
        }
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals("8", output, "JS output");
        }
    }
}