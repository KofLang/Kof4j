package dev.kof.compiler;

import java.util.List;

/**
 * The five built-in AST desugars as {@link DesugarStep}s (2.2.3,
 * `DECISIONS.md` §D-DESUGAR-STEP 21/09 + `D-SCOPED-RESOURCES-GO` slice 1).
 *
 * <p>The order is significant and mirrors the historical call order in the
 * pipeline: using, tests, application, infra, nested functions. Each step delegates to
 * the existing {@link CompilerDesugar} routine, so the transformation is
 * behavior-free (freeze rule 3).
 */
final class DesugarSteps {

    private DesugarSteps() {
    }

    static List<DesugarStep> defaults() {
        return List.of(
                new DesugarStep() {
                    @Override
                    public String name() {
                        return "using";
                    }

                    @Override
                    public CompilationUnitNode apply(CompilationUnitNode unit, CompilerDriver driver) {
                        return CompilerDesugar.desugarUsing(unit);
                    }
                },
                new DesugarStep() {
                    @Override
                    public String name() {
                        return "tests";
                    }

                    @Override
                    public CompilationUnitNode apply(CompilationUnitNode unit, CompilerDriver driver) {
                        // §576: o runner precisa saber se o arquivo tem um
                        // `main` executável. A detecção vive aqui porque o
                        // `discoveredTests` é limpo a cada compilação e este
                        // passo roda uma vez por unidade.
                        driver.hasMainEntryPoint = unit.declarations().stream()
                                .anyMatch(d -> d instanceof FunctionDeclarationNode f
                                        && "main".equals(f.name()));
                        return CompilerDesugar.desugarTests(unit, driver.discoveredTests,
                                driver.testHarnessMode, driver.currentSourceName,
                                driver.currentDiagnostics);
                    }
                },
                new DesugarStep() {
                    @Override
                    public String name() {
                        return "application";
                    }

                    @Override
                    public CompilationUnitNode apply(CompilationUnitNode unit, CompilerDriver driver) {
                        return CompilerDesugar.desugarApplication(unit);
                    }
                },
                new DesugarStep() {
                    @Override
                    public String name() {
                        return "infra";
                    }

                    @Override
                    public CompilationUnitNode apply(CompilationUnitNode unit, CompilerDriver driver) {
                        return CompilerDesugar.desugarInfra(unit);
                    }
                },
                new DesugarStep() {
                    @Override
                    public String name() {
                        return "nested-functions";
                    }

                    @Override
                    public CompilationUnitNode apply(CompilationUnitNode unit, CompilerDriver driver) {
                        return CompilerDesugar.desugarNestedFunctions(unit);
                    }
                });
    }
}
