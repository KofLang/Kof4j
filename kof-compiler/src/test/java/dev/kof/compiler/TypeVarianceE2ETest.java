package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X5.3 (`D-X5-SURFACE`, `D-TYPE-VARIANCE`, 21/09) — variância declaration-site
 * `out`/`in` nos type-params de tipos genéricos declarados no módulo. A
 * variância governa a compatibilidade dos ARGS de um MESMO raw:
 *
 *   - `out T` (covariante):   {@code Source<Dog>} → {@code Source<Animal>};
 *   - `in T`  (contravariante): {@code Sink<Animal>} → {@code Sink<Dog>};
 *   - sem variância (default): INVARIANTE — o §270 continua valendo, args
 *     concretos diferentes seguem rejeitados.
 *
 * A variância é apagada na emissão (compile-time apenas): os descritores e a
 * execução das 4 saídas não mudam — o `out`/`in` vive só no typer.
 *
 * Red-first: antes desta fatia `class Source<out T>` parseava o `out` como
 * NOME do type-param (o `T` virava um param fantasma) e a atribuição
 * covariante era rejeitada pelo §270 invariante.
 */
class TypeVarianceE2ETest extends MultiSourceRunSupport {


    private static final String ANIMALS = """
            class Animal {
                String name
                public constructor(String name) { this.name = name }
                String speak() { return "..." }
            }

            class Dog extends Animal {
                public constructor(String name) { super(name) }
                String speak() { return "woof" }
            }
            """;

    private String runJvm(Path root, List<Path> sources, String expected) throws Exception {
        Path outDir = root.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compileSources(sources, outDir, Target.JVM, root);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code, output: " + output);
        assertEquals(expected, output, "JVM output");
        return output;
    }

    
    
    private static Path write(Path dir, String name, String body) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, body);
        return f;
    }

    // ---------- out (covariante) ----------

    @Test
    void covariantOutAssignmentRunsOnJvmScriptJs(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", ANIMALS + """
                // Componente de record é somente-leitura (saída) → `out T`
                // é seguro e permitido.
                record Source<out T>(T value)

                // Retorno declarado Source<Animal> recebendo Source<Dog>:
                // caminho isAssignable(sa) — só passa pela variância `out`.
                Source<Animal> up(Source<Dog> d) { return d }

                main() {
                    println(up(Source<Dog>(Dog("rex"))).value().name)
                }
                """);
        String expected = "rex";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void covariantErasesToSameDescriptorsOnAllTargets(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", ANIMALS + """
                record Source<out T>(T value)

                Source<Animal> up(Source<Dog> d) { return d }

                main() {
                    println(up(Source<Dog>(Dog("rex"))).value().name)
                }
                """);
        for (Target t : new Target[]{Target.JVM, Target.JS, Target.NATIVE}) {
            CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("o-" + t), t, tmp);
            assertTrue(r.success(), t + " compile failed: " + r.diagnostics().getDiagnostics());
        }
        runJvm(tmp, List.of(f), "rex");
        // SCRIPT nao emite artefato (interpreta direto) — valida a execucao.
        KofInterpreter.Result sc = driver.interpret(List.of(f), tmp, new String[0]);
        assertEquals(0, sc.exitCode(), "SCRIPT exit code, output: " + sc.stdout());
    }

    // ---------- in (contravariante) ----------

    @Test
    void contravariantInAssignmentCompilesAndRuns(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", ANIMALS + """
                class Sink<in T> {
                    public constructor() { }
                    // posição de entrada — permitida para `in T`.
                    String consume(T value) { return "sink" }
                    String label() { return "sink" }
                }

                // Retorno declarado Sink<Dog> recebendo Sink<Animal>:
                // caminho isAssignable(sa) — só passa pela variância `in`.
                Sink<Dog> down(Sink<Animal> w) { return w }

                main() {
                    println(down(Sink<Animal>()).label())
                }
                """);
        runJvm(tmp, List.of(f), "sink");
    }

    // ---------- invariante (default §270 preservado) ----------

    @Test
    void invariantGenericStillRejectsDifferentArgs(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", ANIMALS + """
                class Box<T> {
                    T value
                    public constructor(T value) { this.value = value }
                }

                // Sem `out`/`in` = invariante (§270): Source<Dog> NÃO sobe p/
                // Source<Animal>.
                Box<Animal> up(Box<Dog> d) { return d }

                main() { println(up(Box<Dog>(Dog("rex"))).value.name) }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "Box<Dog> → Box<Animal> invariante deve falhar");
    }

    // ---------- solidez: `out`/`in` em posição errada = SEM082 ----------

    @Test
    void outInWritableFieldIsSem082(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                class Bad<out T> {
                    T value
                    public constructor(T value) { this.value = value }
                }

                main() { println("no") }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "out T em campo gravável deve falhar (solidez)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM082".equals(d.code())),
                "SEM082 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void inInReturnPositionIsSem082(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                class Bad<in T> {
                    public constructor() { }
                    T get() { throw "x" }
                }

                main() { println("no") }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "in T em retorno deve falhar (solidez)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM082".equals(d.code())),
                "SEM082 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void outInMethodParameterIsSem082(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                class Bad<out T> {
                    public constructor() { }
                    void put(T value) { }
                }

                main() { println("no") }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "out T em parâmetro deve falhar (solidez)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM082".equals(d.code())),
                "SEM082 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    // ---------- #689: o cheque vale igual no lado RECORD ----------

    @Test
    void outInRecordMethodParameterIsSem082(@TempDir Path tmp) throws Exception {
        // #689: sem super explícito, o record pulava o cheque de variância.
        Path f = write(tmp, "Ninho.kf", """
                record Ninho<out T>(T ocupante) {
                    void substituir(T novo) { }
                }

                main() { println("no") }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out-rec"), Target.JVM, tmp);
        assertFalse(result.success(), "out T em parâmetro de MÉTODO de record deve falhar (solidez)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM082".equals(d.code())),
                "SEM082 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void outInRecordReturnPositionStaysValid(@TempDir Path tmp) throws Exception {
        // Controle: `T` em posição de saída (retorno) é legítimo para `out T`.
        Path f = write(tmp, "Ninho.kf", """
                record Ninho<out T>(T ocupante) {
                    verOcupante(): T { return ocupante }
                }

                main() { println("ok") }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out-rec-ok"), Target.JVM, tmp);
        assertTrue(result.success(), "out T em retorno deve compilar, veio: "
                + result.diagnostics().getDiagnostics());
    }

    // ---------- compatibilidade: `out`/`in` seguem identificadores ----------

    @Test
    void outStillUsableAsIdentifier(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                main() {
                    var out = 5
                    var result = out + 2
                    println(result)
                }
                """);
        runJvm(tmp, List.of(f), "7");
    }

    @Test
    void outUsedAsSingleTypeParamNameStaysValid(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                class Holder<out> {
                    Int value
                    public constructor(Int value) { this.value = value }
                }

                main() { println(Holder(3).value) }
                """);
        runJvm(tmp, List.of(f), "3");
    }

    // ---------- solidez na HERANÇA (X5.3b) = SEM083 ----------

    @Test
    void outPassedToContravariantSupertypeIsSem083(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                class Sink<in T> {
                    public constructor() { }
                    String consume(T value) { return "sink" }
                }

                // `out T` passado a um parâmetro `in` do supertipo: o supertipo
                // reintroduz T numa posição de ENTRADA → insólido.
                class Bad<out T> extends Sink<T> {
                    public constructor() { super() }
                }

                main() { println("no") }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "out T passado a supertipo in T deve falhar (solidez na herança)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM083".equals(d.code())),
                "SEM083 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void inPassedToCovariantSupertypeIsSem083(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                class Source<out T> {
                    public constructor() { }
                    T get() { throw "x" }
                }

                // `in T` passado a um parâmetro `out` do supertipo: o supertipo
                // reintroduz T numa posição de SAÍDA → insólido.
                class Bad<in T> extends Source<T> {
                    public constructor() { super() }
                }

                main() { println("no") }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "in T passado a supertipo out T deve falhar (solidez na herança)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM083".equals(d.code())),
                "SEM083 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void outPassedToInvariantSupertypeIsSem083(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                class Box<T> {
                    T value
                    public constructor(T value) { this.value = value }
                }

                // Parâmetro invariante do supertipo exige leitura E escrita de T.
                class Bad<out T> extends Box<T> {
                    public constructor(T value) { super(value) }
                }

                main() { println("no") }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "out T passado a supertipo invariante deve falhar (solidez na herança)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM083".equals(d.code())),
                "SEM083 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void matchingVarianceInHeritageCompiles(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                class Source<out T> {
                    public constructor() { }
                    T get() { throw "x" }
                }

                class Sink<in T> {
                    public constructor() { }
                    String consume(T value) { return "sink" }
                }

                // Mesma variância do supertipo → permitido.
                class Sub<out T> extends Source<T> {
                    public constructor() { super() }
                    T get() { throw "y" }
                }

                class SubSink<in T> extends Sink<T> {
                    public constructor() { super() }
                }

                main() {
                    println(SubSink<Int>().consume(1))
                }
                """);
        runJvm(tmp, List.of(f), "sink");
    }
}
