package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #402 + #388 (same bug, two reports) — calling a class field whose type is a
 * FUNCTION TYPE (`formatter(n)` / `handler(event)` inside a method) skipped the
 * field and fell into the top-level-function fallback, emitting
 * `invokestatic Default/Main.<field>` — a phantom static that never exists and
 * returns Object — so the class died at load with VerifyError ("Bad return
 * type" #402 / "Bad type on operand stack" #388). The bare-call lowerer now
 * resolves the owner-class field BEFORE the fallback: this + getfield +
 * invokeinterface on the synthetic lambda interface (the exact shape of the
 * declared-function-type local branch right above it, bug 8).
 */
class FnTypeFieldCallTest extends JvmRunSupport {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + name + t), t);
    }

    
    @Test
    void stringReturningFormatterFieldRuns(@TempDir Path tempDir) throws Exception {
        // #402 verbatim
        CompilationResult r = compile(tempDir, "S", """
                class Handler {
                    (Int) -> String formatter
                    constructor(f: (Int) -> String) { formatter = f }
                    format(n: Int): String { return formatter(n) }
                }
                main() {
                    val h: Handler = Handler((n: Int) -> { return "value=" + n })
                    println(h.format(42))
                }
                """, Target.JVM);
        assertTrue(r.success(), "#402 verbatim must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-SJVM"), "value=42");
    }

    @Test
    void boolReturningHandlerFieldRuns(@TempDir Path tempDir) throws Exception {
        // #388 verbatim (ireturn face)
        CompilationResult r = compile(tempDir, "B", """
                class EventHandler {
                    (String) -> Bool handler
                    constructor(h: (String) -> Bool) {
                        handler = h
                    }
                    handle(event: String): Bool {
                        return handler(event)
                    }
                }
                main() {
                    val h: EventHandler = EventHandler((e: String) -> { return e.length() > 3 })
                    println(h.handle("hi"))
                    println(h.handle("hello"))
                }
                """, Target.JVM);
        assertTrue(r.success(), "#388 verbatim must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-BJVM"), "false\ntrue");
    }

    @Test
    void localShadowingFieldWins(@TempDir Path tempDir) throws Exception {
        // §179 declared-local-wins: a local lambda named like the field keeps
        // dispatching to the LOCAL (the field-branch sits AFTER findLocalVar).
        CompilationResult r = compile(tempDir, "L", """
                class Box {
                    (Int) -> String tag
                    constructor(t: (Int) -> String) { tag = t }
                    use(n: Int): String {
                        var tag: (Int) -> String = (m: Int) -> { return "local" + m }
                        return tag(n)
                    }
                }
                main() {
                    var b = Box((n: Int) -> { return "field" + n })
                    println(b.use(7))
                }
                """, Target.JVM);
        assertTrue(r.success(), "shadowing form must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-LJVM"), "local7");
    }

    @Test
    void voidFieldCallStillRejectedControl(@TempDir Path tempDir) throws Exception {
        // control (freeze rule 2): plain top-level function call unchanged.
        CompilationResult r = compile(tempDir, "T", """
                Int twice(Int x) { return x * 2 }
                main() {
                    println(twice(21))
                }
                """, Target.JVM);
        assertTrue(r.success(), "top-level call path untouched: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-TJVM"), "42");
    }

    @Test
    void compilesOnOtherTargetsToo(@TempDir Path tempDir) throws Exception {
        // rule 5: the fix is in the shared IR lowerer — same program lowers on
        // the other backends (positive native/JS codegen rides the full suite;
        // here only assert the shared pass no longer dies with the phantom).
        CompilationResult r = compile(tempDir, "J", """
                class Handler {
                    (Int) -> String formatter
                    constructor(f: (Int) -> String) { formatter = f }
                    format(n: Int): String { return formatter(n) }
                }
                main() {
                    val h: Handler = Handler((n: Int) -> { return "v" + n })
                    println(h.format(1))
                }
                """, Target.JS);
        assertTrue(r.success(), "JS backend must accept it too: " + r.diagnostics().getDiagnostics());
    }
}
