package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §356 — rio da erasure (família 2): issues #385/#366/#365.
 *
 * <p>Três faces do mesmo buraco: o IR do MÉTODO baixava sem os type-params do
 * pai. (a) Interface genérica: {@code lowerInterface} passava {@code List.of()}
 * → descritor literal {@code ()LT;} na interface — a chamada via
 * {@code Wrapper<String>} era {@code invokeinterface ...()Ljava/lang/Object;}
 * → NoSuchMethodError (#385/#366). (b) O gerador de bridges covariantes (#248)
 * só olhava o SUPERCLASS e não boxava o retorno primitivo → {@code IntHolder
 * extends Holder<Int>} sem bridge {@code ()Ljava/lang/Object;} ou com bridge
 * {@code areturn} de um int cru → VerifyError (#365). Agora: type-params da
 * interface chegam ao IR; bridges são varridas contra PAIS E INTERFACES com
 * chave de erasure nos parâmetros e box/unbox conforme o tipo.
 */
class GenericIfaceEmitE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Path runnerDir = tempDir.resolve("run-" + System.nanoTime());
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder(TestJdk.javacBin(), "-d", runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            Process p = new ProcessBuilder(TestJdk.javaBin(),
                    "-cp", outDir.toString() + ":" + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code " + ec + ", output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    // ---- #385: chamada via parâmetro de interface genérica (descritor LT; → LOject;) ----

    @Test
    void interfaceGenericMethodCallThroughParam(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                interface Wrapper<T> {
                    get(): T
                }
                class StringWrapper implements Wrapper<String> {
                    String data
                    constructor(s: String) { data = s }
                    get(): String { return data }
                }
                printWrapped(w: Wrapper<String>) {
                    println(w.get())
                }
                main() {
                    printWrapped(StringWrapper("world"))
                }
                """);
        assertEquals("world", out, "#385: interface genérica baixava com descritor ()LT;");
    }

    // ---- #366: variável do tipo da interface + get() (extends de interface) ----

    @Test
    void interfaceTypedVarDispatchesToCovariantImpl(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                interface Box<T> {
                    get(): T
                }
                class StringBox extends Box<String> {
                    String value
                    constructor(v: String) { value = v }
                    get(): String { return value }
                }
                main() {
                    val sb: StringBox = StringBox("hi")
                    val b: Box<String> = sb
                    println(b.get())
                }
                """);
        assertEquals("hi", out, "#366: invokeinterface no descritor apagado sem bridge covariante");
    }

    // ---- #365: bridge de retorno PRIMITIVO para Object (boxing na bridge) ----

    @Test
    void abstractClassPrimitiveReturnGetsBoxedBridge(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                abstract class Holder<T> {
                    abstract getValue(): T
                }
                class IntHolder extends Holder<Int> {
                    Int stored
                    constructor(n: Int) { stored = n }
                    getValue(): Int { return stored }
                }
                main() {
                    val h: IntHolder = IntHolder(42)
                    println(h.getValue())
                }
                """);
        assertEquals("42", out, "#365: bridge ()Ljava/lang/Object; emitia areturn de int cru (VerifyError)");
    }

    @Test
    void primitiveBridgeThroughParentTypedVar(@TempDir Path tmp) throws IOException {
        // Q3/cruzamento: o caso real da bridge — chamada pelo PAI (descritor
        // apagado) com implementação primitiva no filho.
        String out = runJvm(tmp, """
                abstract class Holder<T> {
                    abstract getValue(): T
                }
                class IntHolder extends Holder<Int> {
                    Int stored
                    constructor(n: Int) { stored = n }
                    getValue(): Int { return stored }
                }
                main() {
                    val h: Holder<Int> = IntHolder(7)
                    println(h.getValue())
                }
                """);
        assertEquals("7", out, "invokevirtual Holder.getValue()Ljava/lang/Object; deve achar a bridge boxed");
    }

    @Test
    void ifaceBridgeThroughParentVarWithPrimitiveImpl(@TempDir Path tmp) throws IOException {
        // interface + primitivo (as duas metades do §356 juntas).
        String out = runJvm(tmp, """
                interface Counter<T> {
                    current(): T
                }
                class IntCounter implements Counter<Int> {
                    Int n
                    constructor(v: Int) { n = v }
                    current(): Int { return n }
                }
                use(c: Counter<Int>): Int {
                    return c.current()
                }
                main() {
                    println(use(IntCounter(9)))
                }
                """);
        assertEquals("9", out, "bridge de interface com retorno primitivo precisa boxar");
    }

    @Test
    void sameArityOverloadInImplDoesNotDuplicateBridge(@TempDir Path tmp) throws IOException {
        // Q3/idempotência: dois métodos de MESMO nome e aridade com tipos
        // diferentes no pai — a chave de parâmetros pela erasure evita bridge
        // duplicada (ClassFormatError: "Method current(...) duplicates").
        String out = runJvm(tmp, """
                interface Holder<T> {
                    set(v: T)
                    other(v: String)
                }
                class IntHolder implements Holder<Int> {
                    Int n
                    String s
                    set(v: Int) { n = v }
                    other(v: String) { s = v }
                }
                main() {
                    val h: Holder<Int> = IntHolder()
                    h.set(5)
                    h.other("x")
                    val ih: IntHolder = h as IntHolder
                    println(ih.n)
                    println(ih.s)
                }
                """);
        assertEquals("5\nx", out, "bridges por erasure de parâmetros, não por nome+aridade");
    }

    // ---- #608: record (não class) implementando interface genérica também precisa da bridge ----

    @Test
    void recordImplementingGenericInterfaceGetsErasureBridge(@TempDir Path tmp) throws IOException {
        // generateCovariantReturnBridges (§356/#248) só era chamada de
        // CompilerClassLowering.lowerClass — CompilerIfaceRecordLowering.lowerRecord
        // nunca chamava. Um record implementando Box<Int> nunca ganhava o
        // bridge Object get() apagado da interface: AbstractMethodError no
        // invokeinterface do default describe() (que chama get() por `this`).
        String out = runJvm(tmp, """
                interface Box<T> {
                    T get()
                    default String describe() {
                        return "Box: " + get()
                    }
                }
                record IntBox(Int value) implements Box<Int> {
                    Int get() { return value }
                }
                main() {
                    println(IntBox(42).describe())
                }
                """);
        assertEquals("Box: 42", out, "#608: record implementando interface genérica sem bridge de erasure");
    }

    @Test
    void interfaceGenericsRunOnScript(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("S-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
                interface Wrapper<T> {
                    get(): T
                }
                class StringWrapper implements Wrapper<String> {
                    String data
                    constructor(s: String) { data = s }
                    get(): String { return data }
                }
                main() {
                    println(StringWrapper("world").get())
                }
                """);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(file), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals("world\n", ir.stdout());
    }
}
