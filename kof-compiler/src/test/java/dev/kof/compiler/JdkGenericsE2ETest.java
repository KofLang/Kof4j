package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1124 — JDK generics threading through reflection: {@code Optional<T>.get()}
 * typed by the receiver's type argument instead of the erased {@code Object}.
 *
 * <p>Cycle #166 measured: the semantic substitution works (slot2 = String after
 * checkcast), but the JVM emitter's println/boxing sequence was invalid for
 * calls chained on substituted receivers (invokestatic expecting String sees
 * an int). This harness INSTRUMENTS that face: when the run dies, the emitted
 * bytecode is disassembled into the failure message — the exact order is read,
 * never guessed. The measured root: the substituted return type reached the
 * INVOCATION DESCRIPTOR (get()Ljava/lang/String; — a method the owner doesn't
 * declare); the fix emits the erased descriptor + an explicit CHECKCAST.
 */
class JdkGenericsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** javap -c of the compiled class, for failure diagnostics (the instrument). */
    private String disassemble(Path outDir, String className) {
        try {
            String javap = Path.of(System.getProperty("java.home"), "bin", "javap").toString();
            Process p = new ProcessBuilder(javap, "-c", "-p", "-cp",
                    outDir.toString(), className).redirectErrorStream(true).start();
            return new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(javap failed: " + e + ")";
        }
    }

    /** Compiled output dir + program output. */
    private record Run(Path outDir, String output) {}

    private Run compileAndRun(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: "
                + result.diagnostics().getDiagnostics());
        try {
            Path runnerDir = tempDir.resolve("run-" + System.nanoTime());
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder(TestJdk.javacBin(), "-d",
                    runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", outDir.toString()
                    + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            if (ec != 0) {
                fail("JVM exit code " + ec + ", output: " + output
                        + "\n---- bytecode ----\n"
                        + disassemble(outDir, "Default.Main"));
            }
            if (!expected.equals(output)) {
                fail("JVM output expected <" + expected + "> got <" + output + ">"
                        + "\n---- bytecode ----\n"
                        + disassemble(outDir, "Default.Main"));
            }
            return new Run(outDir, output);
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void optionalGetTypedByReceiverTypeArgumentJvm(@TempDir Path tmp) throws Exception {
        // #1124 slice 1 (explicit annotation): get() returns the type ARGUMENT
        // (String), not Object — so .length() resolves and prints "2".
        compileAndRun(tmp, """
                import java.util.Optional
                main() {
                    var o: Optional<String> = Optional.of("hi")
                    var s = o.get()
                    println(s.length())
                }
                """, "2");
    }

    @Test
    void optionalGetChainedOnSubstitutedReceiverJvm(@TempDir Path tmp) throws Exception {
        // The #166 face: chained call DIRECTLY on the substituted receiver —
        // the boxing/checkcast order inside the argument emission.
        Run run = compileAndRun(tmp, """
                import java.util.Optional
                main() {
                    var o: Optional<String> = Optional.of("hi")
                    println(o.get().length())
                }
                """, "2");
        // The instrument's proof, asserted on the GREEN path too: the call
        // carries the ERASED descriptor (()Ljava/lang/Object; — what the JDK
        // class actually declares) and the substituted reference materializes
        // via an explicit CHECKCAST — never a ()Ljava/lang/String; descriptor
        // the owner doesn't have.
        String asm = disassemble(run.outDir(), "Default.Main");
        assertTrue(asm.contains("get:()Ljava/lang/Object;"),
                "erased descriptor must be emitted, was:\n" + asm);
        assertTrue(asm.contains("checkcast"), "substitution checkcast must exist:\n" + asm);
    }

    @Test
    void optionalGetPrintedDirectJvm(@TempDir Path tmp) throws Exception {
        // Control face (no member call after get): println of the substituted
        // value itself must print the string, not a reference artifact.
        compileAndRun(tmp, """
                import java.util.Optional
                main() {
                    var o: Optional<String> = Optional.of("hi")
                    println(o.get())
                }
                """, "hi");
    }

    @Test
    void optionalFactoryInfersWithoutAnnotationJvm(@TempDir Path tmp) throws Exception {
        // #1124 slice 2: NO annotation — `var o = Optional.of("hi")` types the
        // local Optional<String> by substituting the factory return's
        // TypeVariable from the argument. get() then carries the receiver's
        // type argument (slice 1 machinery) and length() resolves.
        compileAndRun(tmp, """
                import java.util.Optional
                main() {
                    var o = Optional.of("hi")
                    var s = o.get()
                    println(s.length())
                }
                """, "2");
    }

    @Test
    void optionalFactoryChainedWithoutAnnotationJvm(@TempDir Path tmp) throws Exception {
        // The #167 face driven straight from the un-annotated factory: the
        // inferred receiver type must reach the chained member call.
        compileAndRun(tmp, """
                import java.util.Optional
                main() {
                    var o = Optional.of("hi")
                    println(o.get().length())
                }
                """, "2");
    }

    @Test
    void optionalPrimitiveFactoryStaysRawJvm(@TempDir Path tmp) throws Exception {
        // Boundary (honest): a PRIMITIVE argument is NOT substituted by this
        // slice (the box/unbox face is its own slice) — the factory still
        // resolves (the erased Object param accepts the boxed int) and the
        // raw-path get() prints the value through the Object dispatch. This
        // probe also pins the owner-"" ClassFormatError the cycle measured:
        // the erased-typed local must emit a REAL owner for get().
        compileAndRun(tmp, """
                import java.util.Optional
                main() {
                    var o = Optional.of(42)
                    println(o.get())
                }
                """, "42");
    }

    @Test
    void optionalOfNullableFactoryInfersJvm(@TempDir Path tmp) throws Exception {
        // Same single-TypeVariable shape, different factory: ofNullable(T).
        compileAndRun(tmp, """
                import java.util.Optional
                main() {
                    var o = Optional.ofNullable("x")
                    println(o.get())
                }
                """, "x");
    }

    @Test
    void inheritedGenericInterfaceMethodReturnsReceiverTypeArgJvm(@TempDir Path tmp) throws Exception {
        // #1093 slice 1 (the AWS fluent-builder shape, JDK-native): Path
        // declares a CONCRETE `Iterator<Path> iterator()` and the INHERITED
        // `Iterator<E>.next()` returns E — paired against the RECEIVER's type
        // argument. Without the hierarchy-parameterization face, next() was
        // erased Object and getFileName() died SEM025.
        compileAndRun(tmp, """
                import java.nio.file.Path
                main() {
                    var p = Path.of("a")
                    var it = p.iterator()
                    var f = it.next()
                    println(f.getFileName())
                }
                """, "a");
    }

    @Test
    void interfaceStaticOverloadPicksTheArgumentShapeJvm(@TempDir Path tmp) throws Exception {
        // The faces the slice measured on the way: `Path.of("a")` must pick
        // of(String, String...) (NOT of(URI) — the null-args lower-time
        // resolution lost to the varargs penalty) and the emit needs the
        // invokestatic itf flag (Path is an INTERFACE).
        compileAndRun(tmp, """
                import java.nio.file.Path
                main() {
                    println(Path.of("b").getFileName().toString())
                }
                """, "b");
    }
}
