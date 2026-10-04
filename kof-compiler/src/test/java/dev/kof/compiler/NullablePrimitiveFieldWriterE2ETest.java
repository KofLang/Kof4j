package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * §361 — o escritor de campo nullable-primitivo deve boxar o RHS, espelhando
 * o cluster de locais do §295(b). Um campo {@code Int?} e fisicamente uma
 * referencia boxed (descritor {@code Ljava/lang/Integer;}); escrever o
 * primitivo cru caia direto no putfield/astore e morria no LOAD da classe
 * (VerifyError) — a entrega parcial que a invariante D-NULL-INTENT nao
 * permite. Faces medidas no corpo da issue: Int?/Long?/Double?/Char? simples,
 * composto {@code +=}, estatico, e os controles (leitura de campo nunca
 * escrito = null; campo Int puro = OK).
 */
class NullablePrimitiveFieldWriterE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            // Runner por reflexao: o launcher `java -cp dir Default.Main`
            // mascara VerifyError como "JavaFX runtime ausente" (regra 09/12).
            Path runnerDir = outDir.resolveSibling(outDir.getFileName() + "-runner");
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
            java.nio.file.Path javaHome = java.nio.file.Path.of(System.getProperty("java.home"));
            Process pCompile = new ProcessBuilder(javaHome.resolve("bin").resolve("javac").toString(),
                    "-d", runnerDir.toString(), runnerSrc.toString())
                    .redirectErrorStream(true).start();
            String compileOut = new String(pCompile.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, pCompile.waitFor(), "runner javac: " + compileOut);
            String classpath = String.join(java.io.File.pathSeparator, outDir.toString(), runnerDir.toString());
            Process p = new ProcessBuilder(javaHome.resolve("bin").resolve("java").toString(),
                    "-Xverify:all", "-cp", classpath, "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM run falhou (exit " + ec + "): " + output);
            assertEquals(expected, output, "JVM golden");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private String runScript(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        try {
            KofInterpreter.Result r = driver.interpret(List.of(file), tempDir, new String[0]);
            return assertTarget("SCRIPT", r.exitCode(), r.stdout().replace("\r\n", "\n").trim(), expected);
        } catch (KofInterpretException e) {
            fail("SCRIPT frontend error: " + e.getMessage());
            return null;
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("js-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                (java.io.InputStream) new java.io.ByteArrayInputStream(new byte[0]), out);
        return assertTarget("JS", ec, out.toString().replace("\r\n", "\n").trim(), expected);
    }

    private String assertTarget(String target, int ec, String output, String expected) {
        assertEquals(0, ec, target + " exit code, output: " + output);
        assertEquals(expected, output, target + " output");
        return output;
    }

    private void runAll4Targets(Path tempDir, String source, String expected) throws IOException {
        runJvm(tempDir, source, expected);
        runScript(tempDir, source, expected);
        runJs(tempDir, source, expected);
        runNativeX86(tempDir, source, expected);
    }

    /**
     * Face Native x86-64 do §361: o SIGSEGV original era AQUI (slot boxed lido
     * como ponteiro sobre inteiro cru). `as`/`ld` ausentes = skip honesto via
     * assume (Q5), nunca falacia; o golden igual fecha a matriz 4-alvos.
     */
    private String runNativeX86(Path tempDir, String source, String expected) throws IOException {
        boolean toolchain = new java.io.File("/usr/bin/as").canExecute()
                && new java.io.File("/usr/bin/ld").canExecute();
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getProperty("os.name").toLowerCase().contains("linux") && toolchain,
                "Native x86 requer Linux + as/ld");
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("nat-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "NATIVE compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(outDir.resolve("Default").resolve("Main").toString())
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "NATIVE run falhou (exit " + ec + "): " + output);
            assertEquals(expected, output, "NATIVE golden");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private static final String BOX = "class Box { Int? n }\n";

    @Test
    void intNullableFieldWriteRead(@TempDir Path tempDir) throws IOException {
        runAll4Targets(tempDir, BOX + """
            main() {
                var b = Box()
                b.n = 42
                println(b.n)
            }
            """, "42");
    }

    @Test
    void longNullableFieldWriteRead(@TempDir Path tempDir) throws IOException {
        runAll4Targets(tempDir, "class Box { Long? n }\n" + """
            main() {
                var b = Box()
                b.n = 7
                println(b.n)
            }
            """, "7");
    }

    @Test
    void doubleNullableFieldWriteRead(@TempDir Path tempDir) throws IOException {
        runAll4Targets(tempDir, "class Box { Double? v }\n" + """
            main() {
                var b = Box()
                b.v = 2.5
                println(b.v)
            }
            """, "2.5");
    }

    @Test
    void charNullableFieldWriteRead(@TempDir Path tempDir) throws IOException {
        runAll4Targets(tempDir, "class Box { Char? c }\n" + """
            main() {
                var b = Box()
                b.c = 'x'
                println(b.c)
            }
            """, "x");
    }

    @Test
    void compoundPlusEqualsOnNullableField(@TempDir Path tempDir) throws IOException {
        runAll4Targets(tempDir, BOX + """
            main() {
                var b = Box()
                b.n = 40
                b.n += 2
                println(b.n)
            }
            """, "42");
    }

    @Test
    void staticNullableFieldWriteRead(@TempDir Path tempDir) throws IOException {
        runAll4Targets(tempDir, "class Holder { static Int? n }\n" + """
            main() {
                Holder.n = 9
                println(Holder.n)
            }
            """, "9");
    }

    @Test
    void controlPlainIntFieldStillWorks(@TempDir Path tempDir) throws IOException {
        runAll4Targets(tempDir, "class Box { Int n }\n" + """
            main() {
                var b = Box()
                b.n = 42
                println(b.n)
            }
            """, "42");
    }

    @Test
    void controlNeverWrittenNullableFieldReadsNull(@TempDir Path tempDir) throws IOException {
        runAll4Targets(tempDir, BOX + """
            main() {
                var b = Box()
                println(b.n)
            }
            """, "null");
    }

    /**
     * Face estrutural (guarda, mesmo padrao ASM da guarda-scope-3 da #278):
     * no sitio do store de um campo Int? o .class deve llamar o box
     * ({@code Integer.valueOf(I)}) ANTES do putfield de descritor boxed —
     * cru-no-boxed e exatamente a entrega parcial do §241/§361.
     */
    @Test
    void fieldStoreSiteBoxesPrimitiveBeforePutfield(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, BOX + """
            main() {
                var b = Box()
                b.n = 42
                println(b.n)
            }
            """);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        List<String> calls = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        try (InputStream in = Files.newInputStream(outDir.resolve("Default").resolve("Main.class"))) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (!name.equals("main")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mth,
                                String descriptor, boolean isInterface) {
                            calls.add(owner + "." + mth + ":" + descriptor);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String mth,
                                String descriptor) {
                            fields.add(opcode + ":" + owner + "." + mth + ":" + descriptor);
                        }
                    };
                }
            }, 0);
        }
        boolean boxedField = fields.stream().anyMatch(f ->
                f.contains(".n:Ljava/lang/Integer;"));
        assertTrue(boxedField, "campo Int? sem descritor boxed no .class: " + fields);
        boolean boxesValue = calls.stream().anyMatch(c ->
                c.equals("java/lang/Integer.valueOf:(I)Ljava/lang/Integer;"));
        assertTrue(boxesValue,
                "sem box no sitio do store de Int? (primitivo cru no slot boxed = §361 vivo): " + calls);
    }
}
