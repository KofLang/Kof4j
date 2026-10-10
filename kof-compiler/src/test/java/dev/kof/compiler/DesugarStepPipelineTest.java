package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.2.3 — internal AST-phase desugar hook {@link DesugarStepPipeline}
 * ({@code DECISIONS.md} §D-DESUGAR-STEP, 21/09).
 *
 * <p>Proves the same three properties as the R4 codegen hook, at the AST phase:
 * (1) an empty/null registry is the IDENTITY; (2) steps run in registration
 * order and a null return fails loudly (R6/never silent); (3) the default
 * registry is behavior-free — a registered identity/probe step runs during a
 * real compile and does not change the emitted bytecode.
 */
class DesugarStepPipelineTest {

    private static final String HELLO = """
            main() { println("ok") }
            """;

    private static CompilationUnitNode unit() {
        return new CompilationUnitNode(new SourcePosition("m.kf", 1, 1, 0, 0), "", List.of(), List.of());
    }

    /** Appends a marker to the unit's imports, so ordering is observable. */
    private static DesugarStep mark(String suffix, List<String> order) {
        return new DesugarStep() {
            @Override
            public String name() {
                return "mark-" + suffix;
            }

            @Override
            public CompilationUnitNode apply(CompilationUnitNode u, CompilerDriver driver) {
                order.add(suffix);
                List<String> imports = new ArrayList<>(u.imports());
                imports.add(suffix);
                return new CompilationUnitNode(u.position(), u.packageName(), imports, u.declarations());
            }
        };
    }

    @Test
    void emptyRegistryIsIdentity() {
        CompilerDriver driver = new CompilerDriver();
        CompilationUnitNode original = unit();
        assertSame(original, DesugarStepPipeline.run(List.of(), original, driver),
                "no steps must return the caller's unit untouched");
    }

    @Test
    void nullRegistryIsIdentity() {
        CompilerDriver driver = new CompilerDriver();
        CompilationUnitNode original = unit();
        assertSame(original, DesugarStepPipeline.run(null, original, driver));
    }

    @Test
    void stepsRunInRegistrationOrder() {
        CompilerDriver driver = new CompilerDriver();
        List<String> order = new ArrayList<>();
        CompilationUnitNode out = DesugarStepPipeline.run(
                List.of(mark("A", order), mark("B", order)), unit(), driver);
        assertEquals(List.of("A", "B"), out.imports(), "steps must compose in registration order");
        assertEquals(List.of("A", "B"), order, "execution order must be deterministic");
    }

    @Test
    void nullReturnFailsLoudly() {
        CompilerDriver driver = new CompilerDriver();
        DesugarStep bad = new DesugarStep() {
            @Override
            public String name() {
                return "bad";
            }

            @Override
            public CompilationUnitNode apply(CompilationUnitNode u, CompilerDriver d) {
                return null;
            }
        };
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DesugarStepPipeline.run(List.of(bad), unit(), driver),
                "a step that drops the unit must fail loudly, never silently (R6)");
        assertTrue(ex.getMessage().contains("bad"), "the diagnostic must name the offending step");
    }

    @Test
    void defaultRegistryHoldsTheFiveBuiltinDesugarsInOrder() {
        CompilerDriver driver = new CompilerDriver();
        List<String> names = new ArrayList<>();
        for (DesugarStep s : driver.desugarSteps) {
            names.add(s.name());
        }
        assertEquals(List.of("using", "tests", "application", "infra", "nested-functions"), names,
                "the default AST registry must preserve the historical desugar order (using first: "
                + "downstream steps see plain try/finally, D-SCOPED-RESOURCES-GO slice 1)");
    }

    @Test
    void registeredStepRunsDuringCompile(@TempDir Path tmp) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        AtomicInteger calls = new AtomicInteger();
        boolean[] sawUnit = {false};
        driver.desugarSteps.add(new DesugarStep() {
            @Override
            public String name() {
                return "probe";
            }

            @Override
            public CompilationUnitNode apply(CompilationUnitNode u, CompilerDriver d) {
                calls.incrementAndGet();
                assertNotNull(u, "the step must receive a real unit");
                assertSame(d, driver, "the step must receive the compiling driver");
                sawUnit[0] = !u.declarations().isEmpty();
                return u;
            }
        });

        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, HELLO);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);

        assertTrue(result.success(), "program must compile: " + result.diagnostics().getDiagnostics());
        assertEquals(1, calls.get(), "the registered step must run exactly once per compile");
        assertTrue(sawUnit[0], "the step must see the parsed declarations, before analysis");
    }

    @Test
    void identityStepDoesNotChangeEmittedBytecode(@TempDir Path tmp) throws Exception {
        byte[] withoutHook = compileMain(tmp.resolve("a"), tmp.resolve("aout"), new CompilerDriver());

        CompilerDriver hooked = new CompilerDriver();
        hooked.desugarSteps.add(new DesugarStep() {
            @Override
            public String name() {
                return "identity";
            }

            @Override
            public CompilationUnitNode apply(CompilationUnitNode u, CompilerDriver d) {
                return u;
            }
        });
        byte[] withHook = compileMain(tmp.resolve("b"), tmp.resolve("bout"), hooked);

        assertArrayEquals(withoutHook, withHook,
                "an identity desugar step must not change the emitted bytecode");
    }

    private static byte[] compileMain(Path dir, Path outDir, CompilerDriver driver) throws Exception {
        Path file = dir.resolve("Main.kf");
        Files.createDirectories(dir);
        Files.writeString(file, HELLO);
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "compile failed: " + result.diagnostics().getDiagnostics());
        Path mainClass = outDir.resolve("Default").resolve("Main.class");
        assertTrue(Files.exists(mainClass), "expected emitted Default/Main.class");
        return Files.readAllBytes(mainClass);
    }
}
