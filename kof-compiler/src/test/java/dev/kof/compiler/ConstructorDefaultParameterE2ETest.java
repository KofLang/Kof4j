package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructorDefaultParameterE2ETest extends JvmJsRunSupport {

    @Test
    void constructorDefaultUsesSyntheticReducedArity(@TempDir Path tempDir) throws IOException {
        runCrossTargets("""
                class Point {
                    Int x
                    String label

                    constructor(Int x, String label = "point") {
                        this.x = x
                        this.label = label
                    }
                }

                main() {
                    var a = Point(1)
                    var b = Point(2, "vector")
                    var c = new Point(3)
                    var d = new Point(4, "new")
                    println(a.x + ":" + a.label)
                    println(b.x + ":" + b.label)
                    println(c.x + ":" + c.label)
                    println(d.x + ":" + d.label)
                }
                """, "1:point\n2:vector\n3:point\n4:new", tempDir, "ctor-default");
    }

    @Test
    void constructorOnlyDefaultAcceptsZeroAndSuppliedArgument(@TempDir Path tempDir) throws IOException {
        runCrossTargets("""
                class Name {
                    String value

                    constructor(String value = "anonymous") {
                        this.value = value
                    }

                    String display() {
                        return "name:" + this.value
                    }
                }

                main() {
                    var empty = Name()
                    var given = Name("ada")
                    var created = new Name("grace")
                    println(empty.display())
                    println(given.display())
                    println(created.display())
                }
                """, "name:anonymous\nname:ada\nname:grace", tempDir, "ctor-default-only");
    }

    @Test
    void explicitExactConstructorWinsOverReducedDefaultPrefix(@TempDir Path tempDir) throws IOException {
        runCrossTargets("""
                class Mode {
                    String label

                    constructor(Int code) {
                        this.label = "exact:" + code
                    }

                    constructor(Int code, String suffix = "") {
                        this.label = "default:" + code + suffix
                    }

                    String display() {
                        return this.label
                    }
                }

                main() {
                    println(Mode(1).display())
                    println(Mode(2, "!").display())
                }
                """, "exact:1\ndefault:2!", tempDir, "ctor-default-precedence");
    }

    @Test
    void defaultConstructorSupportsInheritanceAndSuperPrefix(@TempDir Path tempDir) throws IOException {
        runCrossTargets("""
                class Base {
                    String label

                    constructor(String label = "base") {
                        this.label = label
                    }

                    String baseShow() {
                        return "base:" + this.label
                    }
                }

                class Derived extends Base {
                    Int count

                    constructor(Int count) {
                        super()
                        this.count = count
                    }

                    String show() {
                        return baseShow() + ":" + this.count
                    }
                }

                main() {
                    println(Base().baseShow())
                    println(Base("explicit").baseShow())
                    println(Derived(7).show())
                }
                """, "base:base\nbase:explicit\nbase:base:7", tempDir, "ctor-default-super");
    }

    @Test
    void constructorDefaultTypeMismatchRemainsDiagnostic(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("ctor-default-type.kf");
        String source = """
                class Point {
                    Int x
                    String label

                    constructor(Int x, String label = "point") {
                        this.x = x
                        this.label = label
                    }
                }

                main() {
                    var a = Point("one")
                }
                """;
        java.nio.file.Files.writeString(src, source);
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.JVM);
        assertTrue(!result.success(), "invalid default-arity constructor call must fail");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM014") || d.code().equals("SEM023")),
                "unexpected diagnostics: " + result.diagnostics().getDiagnostics());
    }

    private void runCrossTargets(String source, String expected, Path tempDir, String name) throws IOException {
        runBoth(source, expected, tempDir, name);
        Path src = tempDir.resolve(name + ".kf");
        assertEquals(expected, runScript(src, tempDir.resolve(name + "-script")), name + " script output mismatch");
        assertEquals(expected, runNative(src, tempDir.resolve(name + "-native")), name + " native output mismatch");
    }

    private String runScript(Path src, Path outDir) throws IOException {
        KofInterpreter.Result result = driver.interpret(List.of(src), outDir, new String[0]);
        assertEquals(0, result.exitCode(), "script exit code, output: " + result.stdout());
        return result.stdout().replace("\r\n", "\n").trim();
    }

    private String runNative(Path src, Path outDir) throws IOException {
        CompilationResult result = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(result.success(), "native compile failed: " + result.diagnostics().getDiagnostics());
        Path binary = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binary), "native binary should exist");
        try {
            Process process = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, process.waitFor(), "native exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
