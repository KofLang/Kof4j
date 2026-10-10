package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #779 (hardening, 10/10, `known-bugs` §645): the relational lowering fallback
 * compared a dynamic/reference operand against a `Double`/`Float` operand with
 * `if_icmp*`/`if_acmp*` over the wrong physical slot, producing invalid JVM
 * frames or silently-wrong order. The fix rejects only the non-primitive
 * ordering family; `Unknown` vs `Int` stays accepted (the injected `zipPairs`
 * helper uses physical int ordering). Lives in its own class so the
 * `test-architecture-plan` ratchet on `SemanticResolutionTest` is untouched.
 */
class UnorderedComparisonLoweringE2ETest extends SemanticResolutionSupport {

    @Test
    void unorderedDoubleComparisonsRejected(@TempDir Path tmp) throws IOException {
        String[] cases = {
            "main() { var xs = listOf(); var v = xs.get(0); if (v < 0.35) { println(\"low\") } }",
            "main() { var m = mapOf(); var v = m.get(\"a\"); if (v <= 0.35) { println(\"miss\") } }",
            "record P(Int x)\nmain() { var p = P(1); if (p > 0.35) { println(\"p\") } }",
            "lt<T>(T v, Double d): Bool { return v < d }\nmain() { println(lt(1, 0.5)) }"
        };
        for (String src : cases) {
            CompilationResult r = compile(tmp, "u.kf", src);
            assertFalse(r.success(), "deve falhar: " + src);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM104".equals(d.code())
                            && d.message().contains("Double/Float operand"));
            assertTrue(found, "esperava SEM104, foi: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void dynamicIntAndKnownNumericOrderingStillCompile(@TempDir Path tmp) throws IOException {
        String src = "main() {\n" +
                "    var xs = listOf();\n" +
                "    var v = xs.get(0);\n" +
                "    if (v < 1) { println(\"int\") }\n" +
                "    var a = 1;\n" +
                "    var b = 2.5;\n" +
                "    if (a < b) { println(\"num\") }\n" +
                "    var xs2 = listOf(1.0, 2.0);\n" +
                "    if (xs2.get(0) < 3.0) { println(\"double\") }\n" +
                "}\n";
        CompilationResult r = compile(tmp, "ok.kf", src);
        assertTrue(r.success(), "legítimo deve compilar: " + r.diagnostics().getDiagnostics());
    }
}
