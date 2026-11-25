package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §1188 (issue #1188) — a constructor whose parameter is a class got
 * ANY argument that was also a class: `Object` (the return of a generic Java
 * method) was accepted where a `String` was expected, the build printed no
 * diagnostic, and the emitted `<init>(ILjava/lang/Object;)` call site rejected
 * the class at load with a VerifyError — reported to the user as a missing
 * JavaFX runtime.
 *
 * <p>Contract under test: the check reports SEM014 at the constructor call when
 * the argument's class is not assignable to the parameter's class, and the same
 * call with a real `String` keeps compiling.
 */
class CtorArgClassHierarchyE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String source) throws Exception {
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, source);
        return driver.compile(main, tmp.resolve("out"), Target.JVM);
    }

    private static String diagnosticsOf(CompilationResult r) {
        return r.diagnostics().getDiagnostics().toString();
    }

    @Test
    void objectIntoStringParameterIsRefusedJvm() throws Exception {
        CompilationResult r = compile("""
                record Caixa(String s)

                class Origem {
                    Object valor() {
                        return "texto"
                    }
                }

                main() {
                    var o = Origem()
                    var c = Caixa(o.valor())
                    println(c.s)
                }
                """);
        assertFalse(r.success(),
                "known-bugs §1188: an `Object` argument into a `String` constructor parameter "
                        + "must be REFUSED at check time — accepting it produces invalid bytecode "
                        + "that the JVM rejects at load with a VerifyError");
        String d = diagnosticsOf(r);
        assertTrue(d.contains("SEM014"), "the refusal must be SEM014, got: " + d);
        assertTrue(d.contains("String") && d.contains("Object"),
                "the diagnostic must name both types, got: " + d);
    }

    @Test
    void stringIntoStringParameterStillCompilesJvm() throws Exception {
        CompilationResult r = compile("""
                record Caixa(String s)

                main() {
                    var c = Caixa("texto")
                    println(c.s)
                }
                """);
        assertTrue(r.success(),
                "a String argument into a String parameter must keep compiling: "
                        + diagnosticsOf(r));
    }
}
