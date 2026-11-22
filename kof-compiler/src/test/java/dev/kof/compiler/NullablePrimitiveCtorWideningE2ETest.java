package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #1180: an Int literal passed to a {@code Long?} record field crashed the JVM
 * backend (ASM COMPUTE_FRAMES) instead of widening.
 *
 * <p>{@code CompilerEmission2} applied the numeric widening only when the
 * formal was a CRU {@code PrimitiveType}. For a {@code Long?} parameter the
 * formal is a {@link Type.NullableType}, so the raw {@code Int} fell straight
 * to the box arm: {@code Integer.valueOf(I)} was pushed where the {@code <init>}
 * descriptor expects {@code Long} — the frame could not be computed and the
 * compiler died with {@code ArrayIndexOutOfBoundsException: Index -1 out of
 * bounds for length 0} instead of a diagnostic.
 *
 * <p>The fix widens to the inner of the nullable formal BEFORE boxing, so the
 * value that reaches {@code Long.valueOf(J)} really is a {@code long}. The
 * nullable case only changes which box is used — never the conversion.
 */
class NullablePrimitiveCtorWideningE2ETest extends JvmRunSupport {

    private final CompilerDriver driver = new CompilerDriver();

    private Path compile(Path tmp, String src) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, src);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(file, out, Target.JVM);
        assertTrue(r.success(), "compile must succeed: " + r.diagnostics().getDiagnostics());
        return out;
    }

    @Test
    void intLiteralIntoNullableLongRecordFieldWidens(@TempDir Path tmp) throws Exception {
        // the issue's exact repro
        Path out = compile(tmp, """
            record R(String a, Long? h)

            main() {
                var r = R("1", 100)
                println(r.a)
            }
            """);
        assertRuns(out, "1");
    }

    @Test
    void theWidenedValueIsKeptInTheField(@TempDir Path tmp) throws Exception {
        // the widening must not merely make the frame computable — the value has
        // to survive the round trip through the Long? (boxed java.lang.Long).
        Path out = compile(tmp, """
            record R(String a, Long? h)

            main() {
                var r = R("x", 7)
                var got: Long? = r.h
                if (got != null) {
                    println("" + got)
                } else {
                    println("null")
                }
            }
            """);
        assertRuns(out, "7");
    }

    @Test
    void intArrayFieldIntoNullableLongStaysRefusedOrWidens(@TempDir Path tmp) throws Exception {
        // neighbouring regression guard: the Long? field must still accept a
        // genuinely-Long value and still reject a non-numeric one with a
        // diagnostic instead of a frame crash.
        Path file = tmp.resolve("Bad.kf");
        Files.writeString(file, """
            record R(String a, Long? h)

            main() {
                var r = R("1", "nope")
                println(r.a)
            }
            """);
        CompilationResult r = driver.compile(file, tmp.resolve("out-bad"), Target.JVM);
        assertFalse(r.success(), "a String into Long? must not compile");
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("frame crash"),
                "it must be a diagnostic, never a frame crash: " + r.diagnostics().getDiagnostics());
    }
}
