package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1095 — {@code now - h as Long * 3600000L} silently casts {@code now - h}
 * (the whole left expression) and computes a wrong number with NO diagnostic.
 *
 * <p>The parse is the SPECIFIED one: grammar §5.1 puts {@code as} at precedence
 * 5, below {@code + -} (7) and {@code * /} (8), so {@code now - h} is complete
 * before {@code as} takes it and the {@code *} then applies to the cast result.
 * Readers coming from Java (a unary cast) or Kotlin ({@code as} above
 * multiplication) read the opposite, and nothing told them.
 *
 * <p>The fix does NOT touch the frozen precedence: the parser WARNS
 * ({@code PARSE100}) when the left operand of {@code as} is an unparenthesized
 * expression with a tighter operator ({@code + - * / % << >> >>>}) built by the
 * same precedence climb. A parenthesized operand returns from
 * {@code parsePrimary}, so {@code (a - b) as T}, {@code a - (b as T)},
 * {@code h as Long * k}, {@code x as Long == 2L} and {@code a as B as C} stay
 * silent.
 */
class CastPrecedenceLintE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tmp, String name, String source) throws IOException {
        Path src = tmp.resolve(name + ".kf");
        Files.writeString(src, source);
        return driver.compile(src, tmp.resolve(name + "-out"), Target.JVM);
    }

    private boolean hasParse100(CompilationResult r) {
        return r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "PARSE100".equals(d.code()));
    }

    @Test
    void issueExactReproWarnsAndStillCompiles(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "cast1095", """
                main() {
                    val now = 10000000L
                    val h = 2
                    println(now - h as Long * 3600000L)
                }
                """);
        assertTrue(r.success(), "the warning is non-fatal (frozen precedence unchanged): "
                + r.diagnostics().getDiagnostics());
        assertTrue(hasParse100(r), "expected PARSE100 on `now - h as Long`: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void everyTighterOperatorOnTheLeftWarns(@TempDir Path tmp) throws IOException {
        String[] ops = {"+", "-", "*", "/", "%", "<<", ">>", ">>>"};
        int idx = 0;
        for (String op : ops) {
            CompilationResult r = compile(tmp, "castop" + (idx++), """
                    main() {
                        var a = 8
                        var b = 2
                        println(a %s b as Long)
                    }
                    """.formatted(op));
            assertTrue(hasParse100(r), "expected PARSE100 for `a " + op + " b as Long`: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void parenthesizedLeftOperandStaysSilent(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "castparen", """
                main() {
                    val now = 10000000L
                    val h = 2
                    println((now - h) as Long * 3600000L)
                }
                """);
        assertTrue(r.success(), "parenthesized operand compiles: " + r.diagnostics().getDiagnostics());
        assertFalse(hasParse100(r), "a parenthesized operand must NOT warn: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void castOnTheRightOperandStaysSilent(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "castright", """
                main() {
                    val now = 10000000L
                    val h = 2
                    println(now - (h as Long) * 3600000L)
                }
                """);
        assertTrue(r.success(), "right-operand cast compiles: " + r.diagnostics().getDiagnostics());
        assertFalse(hasParse100(r), "a cast on the right operand must NOT warn: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void castThenTighterOperatorStaysSilent(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "castthen", """
                main() {
                    var h = 2
                    println(h as Long * 3600000L)
                }
                """);
        assertTrue(r.success(), "cast-then-multiply compiles: " + r.diagnostics().getDiagnostics());
        assertFalse(hasParse100(r), "`h as Long * k` casts only h — must NOT warn: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void castThenComparisonStaysSilent(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "castcmp", """
                main() {
                    var x = 5
                    println(x as Long == 5L)
                }
                """);
        assertTrue(r.success(), "cast-then-compare compiles: " + r.diagnostics().getDiagnostics());
        assertFalse(hasParse100(r), "`x as Long == 5L` casts only x — must NOT warn: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void chainedCastsStaySilent(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "castchain", """
                main() {
                    var a = 5
                    println(a as Long as Double)
                }
                """);
        assertTrue(r.success(), "chained casts compile: " + r.diagnostics().getDiagnostics());
        assertFalse(hasParse100(r), "`a as B as C` is pure chaining — must NOT warn: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void plainCastStaysSilent(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "castplain", """
                main() {
                    var a = 5
                    println(a as Long)
                }
                """);
        assertTrue(r.success(), "plain cast compiles: " + r.diagnostics().getDiagnostics());
        assertFalse(hasParse100(r), "a plain cast must NOT warn: "
                + r.diagnostics().getDiagnostics());
    }
}
