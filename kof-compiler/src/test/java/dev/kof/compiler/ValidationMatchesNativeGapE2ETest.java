package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §979 — {@code validation.matches} on the NATIVE target was a LITERAL
 * substring search (the asm {@code kof_validation_matches} compares the whole
 * pattern byte by byte), while the JVM/Script/JS contract is a REGEX with
 * {@code find} semantics: {@code "abc".matches("[a-c]+")} is true on the JVM
 * and false on native. A silent wrong value (rule 5 — worse than a refusal),
 * and the parity matrix marked {@code kof.validation} green on every target.
 *
 * <p>The honest minimal fix (the issue's own proposal, and the same contract
 * {@link StringTargetGaps} already applies to {@code String.matches} with
 * {@code STR003}): a regex engine on native is a separate maintainer decision,
 * so the native target REFUSES the call with {@code VAL001} instead of
 * answering wrong. A pattern that is a plain string literal with no regex
 * metacharacter is still accepted on native — there the literal substring
 * search IS the {@code find} contract, so the existing capability is kept.
 */
class ValidationMatchesNativeGapE2ETest extends JvmRunSupport {

    private static final String REGEX_SRC = """
            main() {
                println(validation.matches("abc", "[a-c]+"))
            }
            """;

    private static final String LITERAL_SRC = """
            main() {
                println(validation.matches("abc", "b"))
                println(validation.matches("abc", "zzz"))
            }
            """;

    private static final String DYNAMIC_SRC = """
            Bool check(String pattern) {
                return validation.matches("abc", pattern)
            }
            main() {
                println(check("b"))
            }
            """;

    @Test
    void aRegexPatternIsRefusedOnNativeWithVal001(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, REGEX_SRC);
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out"), Target.NATIVE);
        assertFalse(r.success(), "a regex pattern must NOT compile silently on native");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("VAL001"),
                "the refusal must be the named VAL001 diagnostic, got: " + diags);
    }

    @Test
    void aDynamicPatternIsRefusedOnNativeWithVal001(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, DYNAMIC_SRC);
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out"), Target.NATIVE);
        assertFalse(r.success(), "a pattern the compiler cannot prove literal must be refused on native");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("VAL001"));
    }

    @Test
    void aPlainLiteralPatternStillWorksOnNative(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, LITERAL_SRC);
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "a metacharacter-free literal keeps the existing native capability: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void theRegexContractIsUnchangedOnJvmScriptAndJs(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, REGEX_SRC);
        for (Target t : new Target[]{Target.JVM, Target.JS}) {
            CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out-" + t), t);
            assertTrue(r.success(), t + " must keep the regex contract: " + r.diagnostics().getDiagnostics());
        }
        // SCRIPT emits no artifacts (COMP003 by design) — its face is the
        // interpreter, which must also answer the regex contract.
        KofInterpreter.Result script = new CompilerDriver().interpret(List.of(src), tmp, new String[0]);
        assertEquals("true", script.stdout().trim(), "Script must keep the regex contract");
    }

    @Test
    void theJvmOracleAnswersTheRegexContract(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, REGEX_SRC);
        Path out = tmp.resolve("jvm");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        String got = runJvmMain(out);
        assertEquals("true", got.trim(), "the JVM oracle: [a-c]+ finds a match in abc");
    }
}
