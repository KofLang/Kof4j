package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kof4j §1207 RED — a STATIC method with SAME-ARITY overloads is defined with the
 * signature tag (`Duas_soma_I_I`, `Duas_soma_I_String`) but the call sites are
 * emitted UNTAGGED (`Duas_soma`), so the native link fails with `undefined
 * reference` (measured: `ld: referência não definida para "n8b_Duas_soma"`).
 *
 * <p>One-arity overloads alone work (the symbol is `soma_I` / `soma_String`
 * without a tag); the defect needs TWO overloads of the SAME arity, which is the
 * case the definitions already tag.
 */
class NativeStaticOverloadCallSiteE2ETest {

    private static final String TWO_SAME_ARITY = """
            class Duas {
                static Int soma(Int a, Int b) {
                    return a + b
                }
                static Int soma(Int a) {
                    return a + 1
                }
            }

            main() {
                println("s2=" + Duas.soma(2, 3))
                println("s1=" + Duas.soma(4))
            }
            """;

    private static final String THREE_MIXED = """
            class Duas {
                static Int soma(Int a) {
                    return a + 1
                }
                static Int soma(Int a, Int b) {
                    return a + b
                }
                static Int soma(String s) {
                    return s.length()
                }
            }

            main() {
                println("a=" + Duas.soma(4))
                println("b=" + Duas.soma(2, 3))
                println("c=" + Duas.soma("abc"))
            }
            """;

    private static String run(Path dir, String program) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), program, StandardCharsets.UTF_8);
        CompilationResult r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.NATIVE);
        org.junit.jupiter.api.Assertions.assertTrue(r.success(),
                "compile + link do nativo (o call site precisa da tag da assinatura): "
                        + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(dir.resolve("out/Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        org.junit.jupiter.api.Assumptions.assumeTrue(p.waitFor(120, TimeUnit.SECONDS), "o programa termina");
        return out;
    }

    @Test
    void theCallSiteCarriesTheSameArityTag(@TempDir Path dir) throws Exception {
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        assertEquals("s2=5\ns1=5", run(dir.resolve("a"), TWO_SAME_ARITY),
                "duas sobrecargas de mesma aridade: cada chamada acha a sua definição");
    }

    @Test
    void threeMixedOverloadsLinkAndRun(@TempDir Path dir) throws Exception {
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        assertEquals("a=5\nb=5\nc=3", run(dir.resolve("b"), THREE_MIXED),
                "três sobrecargas mistas: cada chamada acha a sua definição");
    }
}