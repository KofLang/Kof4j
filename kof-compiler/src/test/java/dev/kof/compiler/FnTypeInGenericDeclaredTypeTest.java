package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #374 — a function type inside a generic DECLARED type (`List<(Int) -> Int>`,
 * `List<() -> Void>`, `Map<Int, () -> Void>`) was rejected as a parameter/field/
 * return type with SEM011 while the identical local variable compiled fine.
 * Root: `declaredTypeUnresolved` tested `contains(" -> ")` BEFORE the `<...>`
 * branch, so the whole `List<(Int) -> Int>` was parsed as a function whose
 * "return" was the garbage `Int>` — the nested arrow is not the top-level
 * connective. The fix validates only a top-level arrow (outside `<>` and `()`),
 * which also repairs the real function-of-function face `((Int) -> Int) -> Int`.
 */
class FnTypeInGenericDeclaredTypeTest extends JvmRunSupport {

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return new CompilerDriver().compile(source, tempDir.resolve("out-" + name + t), t);
    }

    
    @Test
    void fnTypeAsParameterTypeCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        // #374 verbatim face A — pre-fix died with SEM011 at :0:0; the proof
        // executes the lambda through the List-typed parameter (CLI oracle: 10).
        CompilationResult r = compile(tempDir, "A", """
                applyFirst(fns: List<(Int) -> Int>, x: Int): Int {
                    return fns.get(0)(x)
                }
                main() {
                    println(applyFirst(listOf((n: Int) -> { return n * 2 }), 5))
                }
                """, Target.JVM);
        assertTrue(r.success(), "#374 parameter face must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-AJVM"), "10", "#374 parameter face");
    }

    @Test
    void fnTypeAsFieldCompiles(@TempDir Path tempDir) throws Exception {
        // #374 verbatim face B — the Void-return face: `Void` must resolve
        // (builtin) and the emitter must build the class holding the field.
        CompilationResult r = compile(tempDir, "B", """
                class EventEmitter {
                    List<() -> Void> handlers
                    constructor() { handlers = listOf() }
                    emit() { for (var h in handlers) { h() } }
                }
                main() { val e: EventEmitter = EventEmitter() }
                """, Target.JVM);
        assertTrue(r.success(), "#374 field face must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-BJVM"), "", "#374 field face (quiet main)");
    }

    @Test
    void fnTypeInsideMapAndNestedFunctionOfFunction(@TempDir Path tempDir) throws Exception {
        // faces: generic with a MIXED arg list (Int + fn-type) and the real
        // fn-type-of-fn-type parameter — the top-level arrow rule is what
        // picks the OUTER connective (pre-fix the inner arrow poisoned it).
        CompilationResult r = compile(tempDir, "C", """
            run2(fns: Map<Int, () -> Void>): Int {
                return fns.size
            }
            twice(g: (Int) -> Int): Int {
                val inner: (Int) -> Int = g
                return inner(1) + inner(2)
            }
            main() {
                val m: Map<Int, () -> Void> = mapOf()
                println(twice((n: Int) -> { return n * 2 }))
                println(run2(m))
            }
            """, Target.JVM);
        assertTrue(r.success(), "Map/mixed faces must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-CJVM"), "6\n0", "Map/mixed faces");
    }

    @Test
    void trulyUndeclaredTypeStillRejected(@TempDir Path tempDir) throws Exception {
        // r1/Q5 control: the §251/§249 guard must NOT be weakened — an
        // undefined NAME inside the generic argument still fails SEM011.
        CompilationResult r = compile(tempDir, "D", """
                usesIt(xs: List<NoSuchType>): Int {
                    return xs.size
                }
                main() { println(0) }
                """, Target.JVM);
        assertFalse(r.success(), "undefined inner type must stay rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.code().equals("SEM011")),
                "must carry SEM011: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void fnTypeInGenericCompilesOnNativeAndJs(@TempDir Path tempDir) throws Exception {
        // rule 5: gate the artifact targets on the same program (CLI oracle
        // measured the JVM run; inference/checking is shared).
        for (Target t : new Target[]{Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "E", """
                    applyFirst(fns: List<(Int) -> Int>, x: Int): Int {
                        return fns.get(0)(x)
                    }
                    main() {
                        println(applyFirst(listOf((n: Int) -> { return n * 2 }), 5))
                    }
                    """, t);
            assertTrue(r.success(), t + " must compile: " + r.diagnostics().getDiagnostics());
        }
    }
}
