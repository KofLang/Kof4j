package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the code in {@code training/idioms/interop.md} section (d): the D6
 * struct/array/out-buffer shapes must compile on the JVM, on JS (the JS FFI
 * surface is complete since R54/R55/R57/R58/R59) and on Native x86-64 (struct
 * by value 3.7 + array copy-in D6-2 + {@code Buffer(U8)} INOUT, #651 fatia A2).
 * The cross riscv64/aarch64 buffer face keeps the honest FFI001 gap (R6).
 * Same discipline as {@link StdlibIdiomsCompileTest} for `stdlib.md`.
 */
class InteropIdiomsCompileTest {

    private final CompilerDriver driver = new CompilerDriver();

    /** Exactly the shapes shown in interop.md (d). */
    private static final String DOC_SHAPES = """
            record Pt(Int x, Int y)
            extern "libshapes.so" mkpt(Int x, Int y): Pt
            extern "libshapes.so" ptlen(Pt p): Int
            extern "libshapes.so" sumn(Int[] xs, Int n): Int
            extern "libshapes.so" fill(Buffer(U8) b, Int n): Int

            main() {
                var xs = new Int[3]
                xs[0] = 1
                var b = buffer.alloc(4)
                fill(b, 4)
                println(b.bytes())
                println(ptlen(Pt(1, 2)))
            }
            """;

    @Test
    void jvmShapeExamplesCompile(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("interop.kf");
        Files.writeString(src, DOC_SHAPES);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "interop.md (d) shapes must compile on JVM: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void nativeShapeExamplesBindOnX86(@TempDir Path dir) throws Exception {
        // 3.7 (struct by value) + D6-2 (array copy-in) + #651 fatia A2
        // (Buffer(U8) INOUT) closed the x86-64 surface for the documented shapes:
        // no FFI001 must appear on the declaration lines. The final `ld` step
        // fails because `libshapes.so` is a documentation placeholder — that is
        // an environment/link condition, not a binding gap (asserted separately).
        Path src = dir.resolve("interopnat.kf");
        Files.writeString(src, DOC_SHAPES);
        CompilationResult r = driver.compile(src, dir.resolve("out-nat"), Target.NATIVE);
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "documented D6 shapes must bind on Native x86-64 (no FFI001): "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void jsShapeExamplesBindByValue(@TempDir Path dir) throws Exception {
        // R54/R55/R57/R58/R59 closed the JS FFI surface: record by value (in+out),
        // scalar `T[]`→ptr copy-in and `Buffer(U8)` INOUT all bind on the JS target
        // now (byte-for-byte JVM==JS). The old FFI002 honest-gap ratchet is stale.
        Path src = dir.resolve("interopjs.kf");
        Files.writeString(src, DOC_SHAPES);
        CompilationResult r = driver.compile(src, dir.resolve("out-js"), Target.JS);
        assertTrue(r.success(), "D6 record/array/buffer externs must bind on JS now: "
                + r.diagnostics().getDiagnostics());
    }
}
