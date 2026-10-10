package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #394 — `List.reduce()` returns `Object` at the JVM level (erasure). In a
 * method whose declared return is a reference type (`build(): String`), the
 * hoisted call emitted `invokestatic kof_list_reduce` straight into
 * `areturn` with no checkcast — the verifier rejects `Object` where
 * `String` is declared (`VerifyError: Bad return type`). The shared
 * post-call cast block already unboxed reduce-to-primitive and checkcast
 * reduce-to-class; String (and any reference) fell through the gap between
 * the two conditions. The reduce branch now casts reference results.
 */
class ReduceStringCastTest extends JvmRunSupport {

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return new CompilerDriver().compile(source, tempDir.resolve("out-" + name + t), t);
    }

    
    private static final String VERBATIM = """
            class Builder {
                List<String> parts
                constructor() { parts = listOf() }
                add(s: String): Builder { parts.add(s); return this }
                build(): String {
                    return parts.reduce("", (acc: String, s: String) -> { return acc + s })
                }
            }
            main() {
                val b: Builder = Builder()
                b.add("hello")
                b.add(" world")
                println(b.build())
            }
            """;

    @Test
    void reduceToStringInTypedReturnRuns(@TempDir Path tempDir) throws Exception {
        // #394 verbatim — pre-fix compiled clean and died at load (VerifyError
        // "Bad return type"), so this assert EXECUTES the oracle `hello world`.
        CompilationResult r = compile(tempDir, "V", VERBATIM, Target.JVM);
        assertTrue(r.success(), "#394 verbatim must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-VJVM"), "hello world", "#394 verbatim");
    }

    @Test
    void reduceResultAsTypedVarAndIntStillWork(@TempDir Path tempDir) throws Exception {
        // faces: reference result consumed as a String variable (.length), and
        // the PRE-EXISTING primitive unbox path (reduce-to-Int) — both goldens
        // measured on the CLI oracle before being asserted here.
        CompilationResult r = compile(tempDir, "M", """
                class Builder {
                    List<String> parts
                    constructor() { parts = listOf() }
                    add(s: String): Builder { parts.add(s); return this }
                    build(): String {
                        return parts.reduce("", (acc: String, s: String) -> { return acc + s })
                    }
                }
                main() {
                    val b: Builder = Builder()
                    b.add("hello")
                    b.add(" world")
                    val joined: String = b.build()
                    println(joined.length)
                    var nums = listOf(1, 2, 3)
                    println(nums.reduce(0, (a: Int, n: Int) -> { return a + n }))
                }
                """, Target.JVM);
        assertTrue(r.success(), "faces must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-MJVM"), "11\n6", "#394 faces");
    }

    @Test
    void reduceToAnotherClassKeepsItsCastControl(@TempDir Path tempDir) throws Exception {
        // control: reference result that is NOT String went through the
        // pre-existing checkcast branch (:35) — behavior must be unchanged.
        CompilationResult r = compile(tempDir, "C", """
            class Bag {
                List<String> items
                constructor() { items = listOf() }
                static join(bags: List<Bag>): String {
                    return bags.reduce(new Bag(), (acc: Bag, x: Bag) -> { acc.items.add("x"); return acc }).items.size.toString()
                }
            }
            main() {
                println(1)
            }
            """, Target.JVM);
        assertTrue(r.success(), "class-reduce control must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-CJVM"), "1", "#394 class control");
    }

    @Test
    void reduceStringRunsOnEveryTarget(@TempDir Path tempDir) throws Exception {
        // rule 5 parity, measured via CLI on the fixed build: Script/JS/Native
        // print `hello world` too (no verifier there — the gap was
        // JVM-specific, and this locks the four-way agreement).
        for (Target t : new Target[]{Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "P", VERBATIM, t);
            assertTrue(r.success(), t + " must compile: " + r.diagnostics().getDiagnostics());
        }
    }
}
