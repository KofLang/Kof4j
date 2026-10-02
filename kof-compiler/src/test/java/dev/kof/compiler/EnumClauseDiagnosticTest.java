package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §687 — an unexpected token between an `enum`'s name and its `{` (e.g.
 * `enum Cor extends Tudo { A, B }`) was SILENTLY ignored: the enum came out
 * with zero constants and the leftover tokens leaked into the rest of the
 * parse, producing a confusing cascade of unrelated errors. A Kof enum is
 * constants-only, so the clause is invalid — but it must be ONE clear
 * diagnostic, and the surrounding declarations must still parse.
 */
class EnumClauseDiagnosticTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String diagnostics(String source, Path dir, String name) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, source);
        CompilationResult r = driver.compile(f, dir.resolve(name + "-out"), Target.JVM);
        return r.diagnostics().getDiagnostics().toString();
    }

    @Test
    void extendsClauseBeforeBraceIsOneClearDiagnostic(@TempDir Path t) throws Exception {
        String d = diagnostics("enum Cor extends Tudo { A, B }\n\nmain() { println(\"ok\") }\n",
                t, "Extends.kf");
        assertTrue(d.contains("PARSE034"), "expected PARSE034 for `enum X extends ... {`, got: " + d);
    }

    @Test
    void implementsClauseBeforeBraceIsOneClearDiagnostic(@TempDir Path t) throws Exception {
        String d = diagnostics("enum Dir implements Rotulavel { N, S }\n\nmain() { println(\"ok\") }\n",
                t, "Implements.kf");
        assertTrue(d.contains("PARSE034"), "expected PARSE034 for `enum X implements ... {`, got: " + d);
    }

    @Test
    void noMisleadingCascadeAfterTheClause(@TempDir Path t) throws Exception {
        // Pre-fix the leaked `Rotulavel { N, S }` tokens cascaded into
        // unrelated parse errors; post-fix only the single PARSE034 remains.
        String d = diagnostics("enum Dir implements Rotulavel { N, S }\n\nmain() { println(\"ok\") }\n",
                t, "NoCascade.kf");
        assertFalse(d.contains("PARSE007"), "unexpected cascade (PARSE007): " + d);
        assertFalse(d.contains("PARSE001"), "unexpected cascade (PARSE001): " + d);
    }

    @Test
    void wellFormedEnumStillCompiles(@TempDir Path t) throws Exception {
        Path f = t.resolve("Ok.kf");
        Files.writeString(f, "enum Cor { A, B, C }\n\nmain() { println(\"ok\") }\n");
        CompilationResult r = driver.compile(f, t.resolve("ok-out"), Target.JVM);
        assertTrue(r.success(), "a constants-only enum must still compile: " + r.diagnostics().getDiagnostics());
    }
}
