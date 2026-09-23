package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #603 — a class implementing two UNRELATED interfaces that each declare a
 * `default` method of the same name+arity compiled clean and only crashed at
 * class-LOAD with `IncompatibleClassChangeError: Conflicting default
 * methods` (the JVM's own diamond check, JLS 9.4.1.3). #322/SEM043
 * deliberately skips default methods ("already has a body, implementer need
 * not declare it") — that skip never accounted for TWO defaults colliding.
 * javac catches this exact shape at compile time; Kof now does too (SEM101).
 */
class DiamondDefaultMethodConflictTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void unrelatedDefaultMethodsWithSameSignatureAreRejectedWithSEM101(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                interface Greeter { default String greet() { return "hi from Greeter" } }
                interface Waver { default String greet() { return "hi from Waver" } }
                class Both implements Greeter, Waver {
                }
                main() {
                    println(Both().greet())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Compilation must fail on an unresolved default-method diamond");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM101".equals(d.code()) && d.message().contains("greet")),
                "Must report SEM101 naming the conflicting default method: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void explicitOverrideResolvesTheDiamondAndRuns(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                interface Greeter { default String greet() { return "hi from Greeter" } }
                interface Waver { default String greet() { return "hi from Waver" } }
                class Both implements Greeter, Waver {
                    String greet() { return "hi from Both" }
                }
                main() {
                    println(Both().greet())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "An explicit override must resolve the diamond and compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void relatedInterfacesThroughExtendsAreNotAFalseDiamond(@TempDir Path tempDir) throws Exception {
        // Base <- Mid (extends Base) — Leaf implementing BOTH is the common
        // "re-declare an ancestor interface" shape, not a real diamond: the
        // two defaults come from the SAME method, not two unrelated ones.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                interface Base { default String greet() { return "hi from Base" } }
                interface Mid extends Base { }
                class Leaf implements Mid, Base { }
                main() {
                    println(Leaf().greet())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Related interfaces (extends chain) must not be flagged as a diamond: "
                + result.diagnostics().getDiagnostics());
    }
}
