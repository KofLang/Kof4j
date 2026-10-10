package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #389 — a function-type whose parameters are themselves function types
 * (`((Int) -> Int, (Int) -> Int) -> Int`) compiled as a 1-PARAMETER function:
 * the string→Type reader cut the parameter list at the FIRST ')'
 * (`name.indexOf(')')` in Type.of and MemberResolver.declaredTypeUnresolved),
 * so `(Int` became the whole param list and the correct 2-argument call died
 * with SEM013 "expected 1 but got 2". The canonical `(params) -> ret` shape is
 * now split by paren BALANCE (Type.fnTypeArrow); non-canonical strings keep
 * the legacy path (additive — nothing that compiled before changes).
 */
class NestedFnTypeArityTest extends JvmRunSupport {

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        return compileWith(new CompilerDriver(), tempDir, name, program, t);
    }

    private CompilationResult compileWith(CompilerDriver d, Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return d.compile(source, tempDir.resolve("out-" + name + t), t);
    }

    
    @Test
    void verbatimFromIssueRunsSevenAndFourteen(@TempDir Path tempDir) throws Exception {
        // #389 verbatim — expected output is literally "7\n14" (flat control
        // val kept in the same program on purpose: both halves must hold).
        CompilationResult r = compile(tempDir, "V", """
                main() {
                    val add: (Int, Int) -> Int = (a: Int, b: Int) -> { return a + b }
                    println(add(3, 4))
                    val apply2: ((Int) -> Int, (Int) -> Int) -> Int =
                        (a: (Int) -> Int, b: (Int) -> Int) -> { return a(1) + b(2) }
                    println(apply2((x: Int) -> { return x * 2 }, (x: Int) -> { return x + 10 }))
                }
                """, Target.JVM);
        assertTrue(r.success(), "#389 verbatim must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-VJVM"), "7\n14");
    }

    @Test
    void tripleNestedParamKeepsArity(@TempDir Path tempDir) throws Exception {
        // depth-3: parameter type is itself a nested-param fn type.
        CompilationResult r = compile(tempDir, "T", """
                main() {
                    val apply3: (((Int) -> Int) -> Int, Int) -> Int =
                        (g: ((Int) -> Int) -> Int, k: Int) -> { return g((x: Int) -> { return x * 2 }) + k }
                    println(apply3((f: (Int) -> Int) -> { return f(5) }, 3))
                }
                """, Target.JVM);
        assertTrue(r.success(), "triple-nested param type must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-TJVM"), "13");
    }

    @Test
    void wrongArityIsStillRejected(@TempDir Path tempDir) throws Exception {
        // the fix must make arity 2 REAL — calling with 1 argument fails with
        // "expected 2" (pre-fix the phantom 1-param type ACCEPTED this call).
        CompilationResult r = compile(tempDir, "W", """
                main() {
                    val apply2: ((Int) -> Int, (Int) -> Int) -> Int =
                        (a: (Int) -> Int, b: (Int) -> Int) -> { return a(1) + b(2) }
                    println(apply2((x: Int) -> { return x * 2 }))
                }
                """, Target.JVM);
        assertFalse(r.success(), "1-arg call on a 2-param fn type must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.code().equals("SEM013") && d.message().contains("expected 2")),
                "must be SEM013 'expected 2', got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void returnOnlyArrowKeepsLegacyBehavior(@TempDir Path tempDir) throws Exception {
        // curried return: `(Int) -> (Int) -> Int` — the canonical arrow sits
        // right after the matching ')' (same cut as the legacy first-indexOf
        // for this shape); the control guards the fallback path stays sane.
        CompilationResult r = compile(tempDir, "C", """
                main() {
                    val add: (Int, Int) -> Int = (a: Int, b: Int) -> { return a + b }
                    println(add(3, 4))
                }
                """, Target.JVM);
        assertTrue(r.success(), "flat fn type must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-CJVM"), "7");
    }

    @Test
    void nestedParamsCompileOnEveryArtifactTarget(@TempDir Path tempDir) throws Exception {
        // rule 5 (cross-target): the string reader lives in Type — shared by
        // all backends. Artifacts: JVM/Native/JS compile (4-target `7\n14`
        // was also measured through the CLI incl. the direct IR interpreter,
        // which emits no artifact by design — COMP003).
        // ONE shared driver, JVM->NATIVE->JS: this ordering is the §277
        // regression too — before the reset cleared `functionInterfaces`/
        // `lambdaClassNames` alongside `syntheticClasses`, the second
        // compile hit the stale interface cache, skipped re-adding the
        // synthetic IRClass, and the Native link died with
        // `undefined reference to kof_Function1_int_int_invoke`.
        CompilerDriver shared = new CompilerDriver();
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compileWith(shared, tempDir, "P", """
                    main() {
                        val apply2: ((Int) -> Int, (Int) -> Int) -> Int =
                            (a: (Int) -> Int, b: (Int) -> Int) -> { return a(1) + b(2) }
                        println(apply2((x: Int) -> { return x * 2 }, (x: Int) -> { return x + 10 }))
                    }
                    """, t);
            assertTrue(r.success(), t + ": nested fn-type params must compile: " + r.diagnostics().getDiagnostics());
        }
    }
}
