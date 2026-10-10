package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X6.1 ({@code D-INTEROP-REFLECT}, 21/09/2026) — {@code interop.schema(R)} é um
 * intrínseco de compile-time no namespace {@code interop} (ativado por
 * {@code import kof.interop}). Resolve para uma {@code List<Field>} imutável,
 * com {@code record Field(String name, String type)} fornecido pelo compilador,
 * na ordem de declaração dos componentes.
 *
 * <p>Zero reflexão em runtime: o compilador já conhece a estrutura do record e
 * a dobra acontece no frontend (as mesmas ops do {@code listOf(Field(…))}
 * equivalente) — logo a saída é a mesma nos alvos que executam o frontend
 * (JVM/Script/JS) e o Native apenas compila o mesmo IR.</p>
 */
class InteropSchemaE2ETest extends MultiSourceRunSupport {


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

    @Test
    void schemaOfRecordPrintsFieldsInDeclarationOrder(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record User(String name, Int age)

                main() {
                    for (var f in interop.schema(User)) {
                        println(f.name() + ":" + f.type())
                    }
                }
                """);
        String expected = "name:String\nage:Int";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void schemaIsAnImmutableListOfField(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record Point(Int x, Int y)

                main() {
                    List<Field> s = interop.schema(Point)
                    println(s.size)
                    println(s.get(0).name() + "," + s.get(1).name())
                }
                """);
        String expected = "2\nx,y";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void schemaOfSingleFieldRecord(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record Tag(String value)

                main() {
                    for (var f in interop.schema(Tag)) {
                        println(f.name() + "=" + f.type())
                    }
                }
                """);
        String expected = "value=String";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void schemaOfGenericRecordUsesDeclaredComponentType(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record Box<T>(T value, Int count)

                main() {
                    for (var f in interop.schema(Box)) {
                        println(f.name() + ":" + f.type())
                    }
                }
                """);
        String expected = "value:T\ncount:Int";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
    }

    @Test
    void schemaCompilesOnNativeTarget(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record User(String name, Int age)

                main() {
                    for (var f in interop.schema(User)) {
                        println(f.name() + ":" + f.type())
                    }
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("native"),
                Target.NATIVE, tmp);
        assertTrue(r.success(), "NATIVE compile failed: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void schemaOfNonRecordDiagnoses(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                class NotARecord {
                    Int x
                }

                main() {
                    for (var f in interop.schema(NotARecord)) {
                        println(f.name())
                    }
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("bad"),
                Target.JVM, tmp);
        assertFalse(r.success(), "class argument must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "INTEROP001".equals(d.code())
                                && d.message().contains("is a class, not a record")),
                "expected INTEROP001 'is a class, not a record', got: "
                        + r.diagnostics().getDiagnostics());
    }

    // ── X6.2 (D-INTEROP-REFLECT): matriz de edge — toda face inválida do
    //    namespace é diagnóstico honesto com posição (R6), nunca silêncio.
    //    Antes do X6.2, `interop.foo()` e `interop.schema(A, B)` compilavam
    //    sem emitir NADA (a chamada sumia no instance-lowerer genérico).

    @Test
    void unknownMemberOfInteropNamespaceDiagnoses(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record User(String name, Int age)

                main() {
                    interop.foo()
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("unk"),
                Target.JVM, tmp);
        assertFalse(r.success(), "interop.foo() must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "INTEROP002".equals(d.code())
                                && d.message().contains("no member 'foo()'")),
                "expected INTEROP002 for unknown member, got: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void schemaWithTwoArgsDiagnoses(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record User(String name, Int age)
                record Point(Int x, Int y)

                main() {
                    interop.schema(User, Point)
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("arity2"),
                Target.JVM, tmp);
        assertFalse(r.success(), "interop.schema(A, B) must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "INTEROP001".equals(d.code())
                                && d.message().contains("got 2")),
                "expected INTEROP001 wrong arity, got: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void schemaWithZeroArgsDiagnoses(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                main() {
                    interop.schema()
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("arity0"),
                Target.JVM, tmp);
        assertFalse(r.success(), "interop.schema() must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "INTEROP001".equals(d.code())
                                && d.message().contains("got 0")),
                "expected INTEROP001 wrong arity, got: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void schemaOfLocalValueDiagnoses(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record User(String name, Int age)

                main() {
                    var u = User("a", 1)
                    interop.schema(u)
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("local"),
                Target.JVM, tmp);
        assertFalse(r.success(), "interop.schema(localValue) must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "INTEROP001".equals(d.code())
                                && d.message().contains("is a value (local variable)")),
                "expected INTEROP001 'value, not type', got: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void schemaOfEnumDiagnoses(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                enum Color { Red, Green, Blue }

                main() {
                    interop.schema(Color)
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("enum"),
                Target.JVM, tmp);
        assertFalse(r.success(), "interop.schema(enum) must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "INTEROP001".equals(d.code())
                                && d.message().contains("is an enum, not a record")),
                "expected INTEROP001 'enum, not a record', got: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void schemaOfUnknownNameDiagnosesWithSem011(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                main() {
                    interop.schema(DoesNotExist)
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("unkname"),
                Target.JVM, tmp);
        assertFalse(r.success(), "interop.schema(unknown) must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM011".equals(d.code())),
                "expected SEM011 for the undefined name, got: "
                        + r.diagnostics().getDiagnostics());
        // SEM011 já cobre o nome — INTEROP não pode duplicar o diagnóstico.
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .noneMatch(d -> "INTEROP001".equals(d.code())
                                || "INTEROP002".equals(d.code())),
                "must not double-report the undefined name, got: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void schemaOfEntityRecordWorks(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                entity User {
                    name: String
                    age: Int
                }

                main() {
                    for (var f in interop.schema(User)) {
                        println(f.name() + ":" + f.type())
                    }
                }
                """);
        String expected = "name:String\nage:Int";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void localVariableNamedInteropShadowsNamespace(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                class Box {
                    Int schema() { return 7 }
                }

                main() {
                    var interop = Box()
                    println(interop.schema())
                }
                """);
        String expected = "7";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
    }

    @Test
    void schemaOfEmptyRecordRuns(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record Empty()

                main() {
                    List<Field> s = interop.schema(Empty)
                    println(s.size)
                }
                """);
        String expected = "0";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void schemaOfCrossFileRecordWorks(@TempDir Path tmp) throws Exception {
        Path a = write(tmp, "A.kf", """
                import kof.interop

                main() {
                    for (var f in interop.schema(Widget)) {
                        println(f.name() + ":" + f.type())
                    }
                }
                """);
        Path b = write(tmp, "B.kf", "record Widget(Int id, String label)\n");
        String expected = "id:Int\nlabel:String";
        runJvm(tmp, List.of(a, b), expected);
        runScript(tmp, List.of(a, b), expected);
    }

    @Test
    void schemaWithoutImportDiagnoses(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                record User(String name, Int age)

                main() {
                    for (var f in interop.schema(User)) {
                        println(f.name())
                    }
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("noimp"),
                Target.JVM, tmp);
        assertFalse(r.success(), "interop.schema without import must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "INTEROP001".equals(d.code())),
                "expected INTEROP001, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void bindingE2eDrivesAnArrowShapedMapper(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Bind.kf", """
                import kof.interop

                record Order(String id, Double amount, Long qty)

                main() {
                    var fields = interop.schema(Order)
                    // Arrow/Parquet-shaped header DERIVED from the record structure
                    // (name:type joined by '|') — never hand-written.
                    var header = ""
                    var first = true
                    for (var f in fields) {
                        if (!first) { header = header + "|" }
                        header = header + f.name() + ":" + f.type()
                        first = false
                    }
                    println(header)
                    // schema-driven binder: resolve a value slot by field NAME.
                    var idx = -1
                    var i = 0
                    for (var f in fields) {
                        if (f.name() == "amount") { idx = i }
                        i = i + 1
                    }
                    println(idx)
                }
                """);
        String expected = "id:String|amount:Double|qty:Long\n1";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("native"),
                Target.NATIVE, tmp);
        assertTrue(r.success(), "NATIVE compile failed: " + r.diagnostics().getDiagnostics());
    }
}
