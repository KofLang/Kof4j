package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #766 (`1.0-blocks`) — parâmetros default de CONSTRUTOR e de COMPONENTE de
 * record eram aceitos pela gramática (a lowering já emitia os wrappers
 * {@code <init>} reduzidos em {@link CompilerClassLowering#lowerConstructorDefaults}
 * / {@link CompilerRecordSupport#generateRecordDefaultOverloads}), mas o
 * frontend registrava o construtor só na aridade CHEIA e resolvia por aridade
 * exata ({@code SymbolTable.constructorFor}), então {@code D(1)} num
 * {@code constructor(Int x, String y = "z")} morria em {@code SEM023}.
 *
 * <p>Contrato: a chamada aceita {@code requiredArity <= argc <= total}, só os
 * prefixos fornecidos são conferidos, e o emit usa o descritor/sig do PREFIXO
 * FORMAL (o wrapper sintético). Abaixo do obrigatório / acima do total seguem
 * {@code SEM023}; tipo errado no prefixo fornecido segue {@code SEM014}. O
 * JVM é o oráculo (regra 5) e JS/Script/Native x86-64 + riscv64/aarch64 batem.
 */
class ConstructorDefaultE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** Cobre os quatro alvos de uma vez: primário, explícito, default que
     *  referencia parâmetro anterior e record com componente default. */
    private static final String PROGRAM = """
            class P(Int x, String y = "z") {
                String show() { return "" + x + ":" + y }
            }

            class Q {
                Int x
                Int y
                constructor(Int x, Int y = x * 2) {
                    this.x = x
                    this.y = y
                }
            }

            record R(Int a, Int b = 7)

            main() {
                var p1 = P(1)
                var p2 = new P(2, "q")
                println(p1.show())
                println(p2.show())
                var q = Q(5)
                println("" + q.x + ":" + q.y)
                var r = R(3)
                println("" + r.a + ":" + r.b)
            }
            """;

    private static final String GOLDEN = "1:z\n2:q\n5:10\n3:7";

    private Path source(Path tempDir, String name, String src) throws Exception {
        Path f = tempDir.resolve(name + ".kf");
        Files.writeString(f, src);
        return f;
    }

    private String runJvmOracle(Path tempDir, String name, String src) throws Exception {
        Path f = source(tempDir, name, src);
        Path out = tempDir.resolve("out-" + name + "-jvm");
        CompilationResult r = driver.compile(f, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run must exit 0, got:\n" + o);
        return o;
    }

    @Test
    void constructorDefaultsRunOnJvm(@TempDir Path tempDir) throws Exception {
        assertEquals(GOLDEN, runJvmOracle(tempDir, "CD", PROGRAM));
    }

    @Test
    void constructorDefaultsRunOnJs(@TempDir Path tempDir) throws Exception {
        String expected = runJvmOracle(tempDir, "CD", PROGRAM);
        Path f = source(tempDir, "CDJ", PROGRAM);
        Path out = tempDir.resolve("out-CDJ-js");
        CompilationResult r = driver.compile(f, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out2 = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), out2,
                new java.io.ByteArrayInputStream(new byte[0]), out2);
        assertEquals(0, ec, "JS run, output:\n" + out2);
        assertEquals(expected, out2.toString().replace("\r\n", "\n").trim(),
                "JS must match the JVM oracle (rule 5)");
    }

    @Test
    void constructorDefaultsRunOnScript(@TempDir Path tempDir) throws Exception {
        String expected = runJvmOracle(tempDir, "CD", PROGRAM);
        Path f = source(tempDir, "CDS", PROGRAM);
        KofInterpreter.Result ir = driver.interpret(List.of(f), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals(expected, ir.stdout().replace("\r\n", "\n").trim(),
                "Script must match the JVM oracle (rule 5)");
    }

    @Test
    void constructorDefaultsRunOnNativeX86(@TempDir Path tempDir) throws Exception {
        String expected = runJvmOracle(tempDir, "CD", PROGRAM);
        Path f = source(tempDir, "CDN", PROGRAM);
        Path out = tempDir.resolve("out-CDN-native");
        CompilationResult r = driver.compile(f, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary must exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + o);
        assertEquals(expected, o, "Native x86-64 must match the JVM oracle (rule 5)");
    }

    private void crossTarget(Path tempDir, String name, Target target, String arch) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain(arch),
                "cross " + arch + " + qemu ausente — pulando");
        Path f = source(tempDir, name, PROGRAM);
        Path out = tempDir.resolve("out-" + name);
        CompilationResult r = driver.compile(f, out, target);
        assertTrue(r.success(), name + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), name + " binary must exist");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, arch + " ctor-default probe")
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.exitValue(), name + " run must exit 0, got:\n" + o);
        assertEquals(GOLDEN, o, name + " must match the JVM oracle (rule 5)");
    }

    @Test
    void constructorDefaultsRunOnNativeRiscv64(@TempDir Path tempDir) throws Exception {
        crossTarget(tempDir, "CDRV", Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void constructorDefaultsRunOnNativeAarch64(@TempDir Path tempDir) throws Exception {
        crossTarget(tempDir, "CDAA", Target.NATIVE_AARCH64, "aarch64");
    }

    /** Um construtor EXPLÍCITO da mesma aridade do wrapper vence (o wrapper não
     *  é emitido — senão dois {@code <init>} de mesmo descritor no JVM). */
    @Test
    void explicitArityWinsOverDefaultWrapper(@TempDir Path tempDir) throws Exception {
        String src = """
                class Z {
                    String tag
                    constructor(Int x) { this.tag = "int:" + x }
                    constructor(Int x, String y = "d") { this.tag = "two:" + x + ":" + y }
                }
                main() {
                    println(Z(1).tag)
                    println(Z(2, "q").tag)
                }
                """;
        assertEquals("int:1\ntwo:2:q", runJvmOracle(tempDir, "ZD", src));
        Path f = source(tempDir, "ZDN", src);
        Path out = tempDir.resolve("out-ZDN-native");
        CompilationResult r = driver.compile(f, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
    }

    private boolean hasCode(CompilationResult r, String code) {
        return r.diagnostics().getDiagnostics().stream().anyMatch(d -> code.equals(d.code()));
    }

    @Test
    void belowRequiredArityIsRefused(@TempDir Path tempDir) throws Exception {
        CompilationResult r = driver.compile(source(tempDir, "N1",
                "class P(Int x, String y = \"z\")\nmain() { var p = P() }\n"),
                tempDir.resolve("o1"), Target.JVM);
        assertFalse(r.success(), "P() abaixo do obrigatório não pode compilar");
        assertTrue(hasCode(r, "SEM023"), "got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void aboveTotalArityIsRefused(@TempDir Path tempDir) throws Exception {
        CompilationResult r = driver.compile(source(tempDir, "N2",
                "class P(Int x, String y = \"z\")\nmain() { var p = P(1, \"a\", \"b\") }\n"),
                tempDir.resolve("o2"), Target.JVM);
        assertFalse(r.success(), "P(1,a,b) acima do total não pode compilar");
        assertTrue(hasCode(r, "SEM023"), "got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void wrongTypeAtProvidedPrefixIsRefused(@TempDir Path tempDir) throws Exception {
        CompilationResult r = driver.compile(source(tempDir, "N3",
                "class P(Int x, String y = \"z\")\nmain() { var p = P(\"x\") }\n"),
                tempDir.resolve("o3"), Target.JVM);
        assertFalse(r.success(), "P(\"x\") no prefixo Int não pode compilar");
        assertTrue(hasCode(r, "SEM014"), "got: " + r.diagnostics().getDiagnostics());
    }
}
