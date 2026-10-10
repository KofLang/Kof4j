package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X5.1 (`D-X5-SURFACE`, 21/09) — `sealed` fecha o conjunto de subtipos em
 * tempo de compilação: um subtipo direto precisa estar na MESMA unidade de
 * compilação (arquivo) do tipo selado; fora dela é SEM080. O modificador é
 * apagado na emissão (compile-time apenas), então os bytes/execução das 4
 * saídas não mudam.
 *
 * Red-first: antes desta fatia `sealed class S` nem parseava, logo SEM080
 * não existia — os casos de fora-da-unidade falhariam.
 */
class SealedTypeE2ETest extends MultiSourceRunSupport {


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
    void sealedClassSubtypesInSameUnitRunOnJvmAndScript(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                sealed class Shape

                class Circle extends Shape {
                    Int r
                    public constructor(Int r) { this.r = r }
                    Int area() { return r * r }
                }

                class Square extends Shape {
                    Int s
                    public constructor(Int s) { this.s = s }
                    Int area() { return s * s }
                }

                main() {
                    println(Circle(2).area())
                    println(Square(3).area())
                }
                """);
        String expected = "4\n9";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
    }

    @Test
    void sealedInterfaceImplementedInSameUnitCompiles(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                sealed interface Shape

                class Circle implements Shape {
                    Int r
                    public constructor(Int r) { this.r = r }
                }

                main() {
                    println(Circle(1).r)
                }
                """);
        runJvm(tmp, List.of(f), "1");
    }

    @Test
    void subtypeOfSealedInAnotherUnitIsSem080(@TempDir Path tmp) throws Exception {
        write(tmp, "Shape.kf", "sealed class Shape\n");
        Path sub = write(tmp, "Main.kf", """
                class Circle extends Shape {
                    Int r
                    public constructor(Int r) { this.r = r }
                }
                main() { println("x") }
                """);
        CompilationResult result = driver.compileSources(List.of(tmp.resolve("Shape.kf"), sub),
                tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "subtipo fora da unidade deve falhar");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM080".equals(d.code())),
                "SEM080 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void sealedInterfaceExtendedInAnotherUnitIsSem080(@TempDir Path tmp) throws Exception {
        write(tmp, "Shape.kf", "sealed interface Shape\n");
        Path sub = write(tmp, "Main.kf", """
                interface Round extends Shape
                main() { println("x") }
                """);
        CompilationResult result = driver.compileSources(List.of(tmp.resolve("Shape.kf"), sub),
                tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "interface fora da unidade deve falhar");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM080".equals(d.code())),
                "SEM080 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void sealedKeywordStillUsableAsIdentifier(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                main() {
                    var sealed = 5
                    println(sealed + 2)
                }
                """);
        runJvm(tmp, List.of(f), "7");
    }

    @Test
    void sealedCompilesOnJsAndNative(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                sealed class Shape

                class Circle extends Shape {
                    Int r
                    public constructor(Int r) { this.r = r }
                }

                main() { println("ok") }
                """);
        CompilationResult js = driver.compileSources(List.of(f), tmp.resolve("js"), Target.JS, tmp);
        assertTrue(js.success(), "JS compile failed: " + js.diagnostics().getDiagnostics());
        CompilationResult nat = driver.compileSources(List.of(f), tmp.resolve("nat"), Target.NATIVE, tmp);
        assertTrue(nat.success(), "Native compile failed: " + nat.diagnostics().getDiagnostics());
    }

    @Test
    void exhaustiveSwitchOverSealedRunsOnJvmScriptJs(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                sealed class Shape

                class Circle extends Shape {
                    Int r
                    public constructor(Int r) { this.r = r }
                }

                class Square extends Shape {
                    Int s
                    public constructor(Int s) { this.s = s }
                }

                String describe(Shape sh) {
                    return switch (sh) {
                        case Circle c -> "circle"
                        case Square q -> "square"
                    }
                }

                main() {
                    println(describe(Circle(1)))
                    println(describe(Square(2)))
                }
                """);
        String expected = "circle\nsquare";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void missingCaseInSealedSwitchIsSem081(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                sealed class Shape

                class Circle extends Shape {
                    Int r
                    public constructor(Int r) { this.r = r }
                }

                class Square extends Shape {
                    Int s
                    public constructor(Int s) { this.s = s }
                }

                String describe(Shape sh) {
                    return switch (sh) {
                        case Circle c -> "circle"
                    }
                }

                main() { println(describe(Circle(1))) }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "switch sobre sealed faltando caso deve falhar");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM081".equals(d.code())),
                "SEM081 esperado, veio: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void defaultAllowsSealedSwitch(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                sealed class Shape

                class Circle extends Shape {
                    Int r
                    public constructor(Int r) { this.r = r }
                }

                class Square extends Shape {
                    Int s
                    public constructor(Int s) { this.s = s }
                }

                String describe(Shape sh) {
                    return switch (sh) {
                        case Circle c -> "circle"
                        default -> "other"
                    }
                }

                main() { println(describe(Square(2))) }
                """);
        runJvm(tmp, List.of(f), "other");
    }
}
