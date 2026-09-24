package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §104b-ii (24/09) — igualdade por CONTEUDO de referencias Kof (record/classe)
 * dentro de colecoes no Native: {@code contains}/{@code indexOf}/
 * {@code lastIndexOf} de {@code List} e {@code contains}/{@code add}/
 * {@code remove} de {@code Set}. O tag 2 do lowering (stringTag) leva o runtime
 * a {@code kof_obj_equals}, que despacha o equals virtual da classe
 * ({@code kof_equals_table}). O oracle e o JVM, rodado no proprio teste; a
 * regressao de String cobre a divergencia historica do riscv (kof_list_contains
 * comparava ponteiro e ignorava a tag).
 */
class NativeRecordCollectionEqualityE2ETest {

    private static final String PROGRAM = """
            record Point(Int x, Int y)
            record Box(Double d, Bool ok)
            record Inner(Int v)
            record Outer(Inner i, String s)
            record MaybeInner(Inner? i, String s)
            record L2(Outer o)

            class User {
                String name
                public constructor(String name) { this.name = name }
            }
            record HasUser(User u)

            String make(String a, String b) { return a + b }

            Inner? maybe(Int v) {
                if (v > 0) { return Inner(v) }
                return null
            }

            main() {
                println(listOf(Point(1, 2)).contains(Point(1, 2)))
                println(listOf(Point(1, 2)).contains(Point(9, 9)))
                println(listOf(Point(1, 2)).indexOf(Point(1, 2)))
                println(listOf(Point(1, 2), Point(3, 4), Point(1, 2)).lastIndexOf(Point(1, 2)))
                println(listOf(Box(1.5, true)).contains(Box(1.5, true)))
                println(listOf(Box(1.5, true)).contains(Box(1.5, false)))

                println(setOf(Point(1, 2), Point(1, 2)).size)
                println(setOf("dup", make("du", "p")).size)

                var s = setOf(Point(1, 2))
                println(s.contains(Point(1, 2)))
                s.add(Point(1, 2))
                println(s.size)
                s.add(Point(3, 4))
                println(s.size)
                s.remove(Point(1, 2))
                println(s.size)
                println(s.contains(Point(1, 2)))

                var s1 = "hello"
                var s2 = make("hel", "lo")
                println(listOf(s1).contains(s2))
                println(listOf(s1).indexOf(s2))
                println(setOf(s1).contains(s2))

                var u1 = User("a")
                var u2 = User("a")
                println(listOf(u1).contains(u1))
                println(listOf(u1).contains(u2))

                println(mapOf(Point(1, 2), 7).get(Point(1, 2)))
                println(mapOf(Point(1, 2), 7).containsKey(Point(1, 2)))
                println(mapOf(Point(1, 2), 7).containsKey(Point(9, 9)))
                var m = mapOf(Point(1, 2), 7)
                m.put(Point(3, 4), 9)
                println(m.get(Point(3, 4)))
                println(m.remove(Point(1, 2)))
                println(m.size)
                var mk = make("hel", "lo")
                println(mapOf(mk, 5).get("hello"))
                println(mapOf(1, 2).get(1))
                println(mapOf(1, 2).containsKey(3))

                // #604 — containsValue com valor de tipo record/classe: face
                // do §104b-ii nunca portada pro VALOR (só a CHAVE tinha).
                var mv = mapOf("a", Point(1, 2), "b", Point(3, 4))
                println(mv.containsValue(Point(1, 2)))
                println(mv.containsValue(Point(9, 9)))

                println(listOf(1, 2, 3).contains(2))
                println(listOf(1, 2, 3).indexOf(7))

                println(Outer(Inner(1), "z") == Outer(Inner(1), "z"))
                println(Outer(Inner(1), "z") == Outer(Inner(1), "y"))
                println(Outer(Inner(1), "z") == Outer(Inner(2), "z"))
                println(Inner(1) == Inner(1))
                println(HasUser(u1) == HasUser(u1))
                println(HasUser(u1) == HasUser(u2))

                println(MaybeInner(maybe(1), "z") == MaybeInner(maybe(1), "z"))
                println(MaybeInner(maybe(-1), "z") == MaybeInner(maybe(-1), "z"))
                println(L2(Outer(Inner(1), "z")) == L2(Outer(Inner(1), "z")))
                println(L2(Outer(Inner(1), "z")) == L2(Outer(Inner(2), "z")))
            }
            """;

    private static String run(Path tempDir, Target target, String program) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out-" + target.name());
        CompilationResult result = driver.compile(source, outDir, target);
        assertTrue(result.success(), target + " compile should succeed: "
                + result.diagnostics().getDiagnostics());
        if (target == Target.JVM) {
            return runJvm(tempDir, outDir);
        }
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), target + " binary should exist");
        ProcessBuilder pb = new ProcessBuilder(binFile.toString());
        if (target == Target.NATIVE_RISCV64) {
            pb = NativeRiscv64E2ETest.qemu("riscv64", binFile);
        } else if (target == Target.NATIVE_AARCH64) {
            pb = NativeRiscv64E2ETest.qemu("aarch64", binFile);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
        assertEquals(0, ec, target + " exit code should be 0, output: '" + output + "'");
        return output;
    }

    private static String runJvm(Path tempDir, Path outDir) throws IOException {
        try {
            Path runnerDir = Files.createDirectory(tempDir.resolve("runner"));
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                    public class Run {
                        public static void main(String[] args) throws Exception {
                            Class.forName(args[0]).getMethod("main", String[].class)
                                .invoke(null, (Object) new String[0]);
                        }
                    }
                    """);
            Process pCompile = new ProcessBuilder(TestJdk.javacBin(), "-d", runnerDir.toString(),
                    runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor(), "runner javac");
            Process p = new ProcessBuilder(TestJdk.javaBin(),
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code " + ec + ", output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void recordCollectionEqualityMatchesJvmOnNativeX86(@TempDir Path tempDir) throws IOException {
        Path jvmDir = Files.createDirectory(tempDir.resolve("jvmdir"));
        String oracle = run(jvmDir, Target.JVM, PROGRAM);
        Path nativeDir = Files.createDirectory(tempDir.resolve("natdir"));
        String nativeOut = run(nativeDir, Target.NATIVE, PROGRAM);
        assertEquals(oracle, nativeOut, "Native record collection equality must match the JVM oracle");
    }

    @Test
    void recordCollectionEqualityMatchesJvmOnRiscv64(@TempDir Path tempDir) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "toolchain riscv64/qemu ausente — pulando (NATIVE002)");
        Path jvmDir = Files.createDirectory(tempDir.resolve("jvmdir"));
        String oracle = run(jvmDir, Target.JVM, PROGRAM);
        Path nativeDir = Files.createDirectory(tempDir.resolve("natdir"));
        String nativeOut = run(nativeDir, Target.NATIVE_RISCV64, PROGRAM);
        assertEquals(oracle, nativeOut, "riscv64 record collection equality must match the JVM oracle");
    }

    @Test
    void recordCollectionEqualityMatchesJvmOnAarch64(@TempDir Path tempDir) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "toolchain aarch64/qemu ausente — pulando (NATIVE002)");
        Path jvmDir = Files.createDirectory(tempDir.resolve("jvmdir"));
        String oracle = run(jvmDir, Target.JVM, PROGRAM);
        Path nativeDir = Files.createDirectory(tempDir.resolve("natdir"));
        String nativeOut = run(nativeDir, Target.NATIVE_AARCH64, PROGRAM);
        assertEquals(oracle, nativeOut, "aarch64 record collection equality must match the JVM oracle");
    }
}
