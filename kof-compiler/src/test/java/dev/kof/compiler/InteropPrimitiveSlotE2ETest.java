package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `known-bugs` §554 (`D-MAINT-BATCH-0610`/C tighten): a JVM interop call
 * whose argument only matches a primitive-widened Java overload used to
 * type-check silently and die at class load with
 * `VerifyError: Bad type on operand stack` — the external arms resolved by
 * name+arity only (first match wins) and never checked argument types.
 *
 * <p>The fixture class (emitted with ASM so the test needs no {@code javac})
 * declares the §554 shape in class-file order: {@code update(byte)} FIRST,
 * then {@code update(byte[])}, mirroring
 * {@code Ed25519Signer.update(byte)}/{@code update(byte[])}. Pre-fix the
 * array call bound the scalar overload (silent check + {@code VerifyError}
 * running `Default.Main`); post-fix it reports an honest call-site
 * {@code SEM014} naming both sides, while the exact-arity 3-arg form and
 * primitive widening ({@code Int} literal into {@code long}) keep working.
 */
class InteropPrimitiveSlotE2ETest {

    private static final String PKG = "ext";
    private static final String CLS = "Overloads";

    /** Emits {@code ext/Overloads} with scalar-first overloads + widening control. */
    private static Path fixtureJar(Path dir) throws Exception {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String internal = PKG + "/" + CLS;
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null);
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        // Scalar FIRST (the §554 trap order): update(byte).
        MethodVisitor scalar = w.visitMethod(Opcodes.ACC_PUBLIC, "update", "(B)V", null, null);
        scalar.visitCode();
        scalar.visitInsn(Opcodes.RETURN);
        scalar.visitMaxs(0, 0);
        scalar.visitEnd();
        // Exact reference overload, second: update(byte[]).
        MethodVisitor arr = w.visitMethod(Opcodes.ACC_PUBLIC, "update", "([B)V", null, null);
        arr.visitCode();
        arr.visitInsn(Opcodes.RETURN);
        arr.visitMaxs(0, 0);
        arr.visitEnd();
        // Exact-arity workaround form (§554 green path): update(byte[],int,int).
        MethodVisitor arr3 = w.visitMethod(Opcodes.ACC_PUBLIC, "update", "([BII)I", null, null);
        arr3.visitCode();
        arr3.visitVarInsn(Opcodes.ILOAD, 3);
        arr3.visitInsn(Opcodes.IRETURN);
        arr3.visitMaxs(0, 0);
        arr3.visitEnd();
        // Widening control: echo(long) must keep accepting an Int literal.
        MethodVisitor echo = w.visitMethod(Opcodes.ACC_PUBLIC, "echo", "(J)J", null, null);
        echo.visitCode();
        echo.visitVarInsn(Opcodes.LLOAD, 1);
        echo.visitInsn(Opcodes.LRETURN);
        echo.visitMaxs(0, 0);
        echo.visitEnd();
        w.visitEnd();
        Path jar = dir.resolve("overloads.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            z.putNextEntry(new ZipEntry(internal + ".class"));
            z.write(w.toByteArray());
            z.closeEntry();
        }
        return jar;
    }

    private static CompilationResult compileWith(Path dir, Path jar, String name, String kf) throws Exception {
        Path src = dir.resolve(name);
        Files.writeString(src, kf);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        return d.compile(src, dir.resolve("out-" + name.replace('.', '-')), Target.JVM);
    }

    private static String runJvm(Path out, Path jar) throws Exception {
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL(), jar.toUri().toURL()},
                    InteropPrimitiveSlotE2ETest.class.getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    @Test
    void refIntoPrimitiveSlotIsSem014(@TempDir Path dir) throws Exception {
        Path jar = fixtureJar(dir);
        CompilationResult r = compileWith(dir, jar, "Main.kf", """
                import ext.Overloads

                main() {
                    var o = Overloads()
                    var msg = new Byte[2]
                    o.update(msg)
                    println("called")
                }
                """);
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d ->
                        d.toString().contains("SEM014")
                                && d.toString().contains("Byte[]")),
                "a Byte[] into a byte slot must refuse at the call site, diagnostics: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void exactArityFormStillRuns(@TempDir Path dir) throws Exception {
        Path jar = fixtureJar(dir);
        CompilationResult r = compileWith(dir, jar, "Main3.kf", """
                import ext.Overloads

                main() {
                    var o = Overloads()
                    var msg = new Byte[2]
                    println(o.update(msg, 0, 2))
                }
                """);
        assertTrue(r.success(), "exact-arity form must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("2", runJvm(dir.resolve("out-Main3-kf"), jar),
                "the §554 workaround shape runs green (no VerifyError)");
    }

    @Test
    void primitiveWideningPreserved(@TempDir Path dir) throws Exception {
        Path jar = fixtureJar(dir);
        CompilationResult r = compileWith(dir, jar, "MainW.kf", """
                import ext.Overloads

                main() {
                    var o = Overloads()
                    println(o.echo(7))
                }
                """);
        assertTrue(r.success(), "Int literal into long must keep widening: "
                + r.diagnostics().getDiagnostics());
        assertEquals("7", runJvm(dir.resolve("out-MainW-kf"), jar),
                "widened call runs with the value intact");
    }
}
