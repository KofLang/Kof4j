package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §556 — o launcher de {@code kof run --target jvm} não pode mascarar uma
 * falha de load/link da classe principal como "os componentes de runtime do
 * JavaFX não foram encontrados". A JDK faz isso em {@code LauncherHelper}
 * quando {@code getDeclaredMethods()} dispara o {@code VerifyError}/
 * {@code NoClassDefFoundError}; o wrapper {@code KofJvmMain} invoca main por
 * reflexão e expõe a causa real.
 *
 * RED-first: na árvore pré-fix, o caso broken imprime a linha JavaFX (o teste
 * falha); pós-fix imprime o {@code VerifyError} real.
 */
class JvmLauncherDiagnosticE2ETest {

    private static final String BROKEN_MAIN = """
        public class BrokenMain {
            public static void main(Dep args) { System.out.println("never"); }
        }
        """;

    private static final String DEP = "public class Dep { }\n";

    private static final String GOOD_MAIN = """
        public class GoodMain {
            public static void main(String[] args) { System.out.println("ok " + args.length); }
        }
        """;

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static Process start(List<String> cmd, Path workDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static String read(Process p) throws Exception {
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "processo travou:\n" + out);
        return out;
    }

    private static void compileJava(Path dir, String... namesAndSources) throws IOException {
        List<String> sources = new ArrayList<>();
        for (int i = 0; i < namesAndSources.length; i += 2) {
            Path src = dir.resolve(namesAndSources[i] + ".java");
            Files.writeString(src, namesAndSources[i + 1]);
            sources.add(src.toString());
        }
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        assertNotNull(jc, "JavaCompiler do JDK indisponível (surefire precisa rodar em JDK)");
        List<String> args = new ArrayList<>(List.of("--release", "21", "-d", dir.toString()));
        args.addAll(sources);
        int rc = jc.run(null, null, null, args.toArray(String[]::new));
        assertTrue(rc == 0, "compilação Java de apoio falhou");
    }

    @Test
    void brokenMainSurfacesRealErrorNotJavafxMask(@TempDir Path dir) throws Exception {
        Path cls = Files.createDirectories(dir.resolve("broken"));
        compileJava(cls, "Dep", DEP, "BrokenMain", BROKEN_MAIN);
        Files.delete(cls.resolve("Dep.class"));

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(cls + java.io.File.pathSeparator + System.getProperty("java.class.path"));
        cmd.add("dev.kof.runtime.KofJvmMain");
        cmd.add("BrokenMain");
        String out = read(start(cmd, dir));

        assertFalse(out.contains("JavaFX"),
                "a mensagem falsa de JavaFX não pode aparecer (§556):\n" + out);
        assertTrue(out.contains("NoClassDefFoundError") && out.contains("Dep"),
                "a causa real (classe ausente) deve ser impressa:\n" + out);
    }

    @Test
    void goodMainRunsThroughWrapper(@TempDir Path dir) throws Exception {
        Path cls = Files.createDirectories(dir.resolve("good"));
        compileJava(cls, "GoodMain", GOOD_MAIN);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(cls + java.io.File.pathSeparator + System.getProperty("java.class.path"));
        cmd.add("dev.kof.runtime.KofJvmMain");
        cmd.add("GoodMain");
        cmd.add("a");
        cmd.add("b");
        Process p = start(cmd, dir);
        String out = read(p);
        assertTrue(out.contains("ok 2"), "wrapper deve repassar os argumentos:\n" + out);
        assertFalse(out.contains("JavaFX"), "sem máscara no caminho feliz:\n" + out);
    }

    @Test
    void kofRunNeverPrintsJavafxMask(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
            import java.security.MessageDigest
            main() {
                var md = MessageDigest.getInstance("SHA-256")
                var b = new Byte[3]
                b[0] = 97
                b[1] = 98
                b[2] = 99
                md.update(b)
                println(md.digest().length)
            }
            """);
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.add("run");
        cmd.add(src.toString());
        String out = read(start(cmd, dir));

        assertFalse(out.contains("JavaFX"),
                "kof run não pode imprimir a máscara JavaFX (§556):\n" + out);
        if (!out.contains("32")) {
            // §628: desde o aperto §554 (`ExternalArgTighten`), a fixture
            // `md.update(Byte[])` recusa no COMPILE com SEM014 em vez de
            // morrer VerifyError no load — o diagnóstico de compile-time
            // TAMBÉM é a causa real revelada (nunca a máscara).
            assertTrue(out.contains("VerifyError") || out.contains("NoClassDefFoundError")
                            || out.contains("Exception") || out.contains("SEM"),
                    "se o programa falha ao carregar, a causa real deve aparecer:\n" + out);
        }
    }

    @Test
    void benchAndWorkflowCommandsRouteThroughWrapper(@TempDir Path dir) throws Exception {
        Path out = Files.createDirectories(dir.resolve("out/Default"));
        Files.write(out.resolve("Main.class"), new byte[]{(byte) 0xCA, (byte) 0xFE});
        java.util.List<String> bench = BenchRunners.commandFor(
                dev.kof.compiler.Target.JVM, dir.resolve("out"));
        assertNotNull(bench, "commandFor deve resolver a classe Main");
        java.nio.file.Path wrapper = KofCliSupport.jvmLaunchWrapperLocation();
        if (wrapper != null) {
            assertTrue(bench.contains("dev.kof.runtime.KofJvmMain"),
                    "kof bench deve passar pelo wrapper §556: " + bench);
            assertTrue(bench.stream().anyMatch(s -> s.contains(wrapper.toString())),
                    "o classpath do filho deve incluir o wrapper: " + bench);
        }
    }

    @Test
    void kofWorkflowNeverPrintsJavafxMask(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("pipe.kf");
        Files.writeString(src, """
            import kof.workflow
            import java.security.MessageDigest

            KofWfDag pipeline() {
                var j = job("md", () -> {
                    var md = MessageDigest.getInstance("SHA-256")
                    var b = new Byte[3]
                    b[0] = 97
                    b[1] = 98
                    b[2] = 99
                    md.update(b)
                    return md.digest().length == 32
                })
                return dag(listOf(j))
            }
            """);
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.add("workflow");
        cmd.add("run");
        cmd.add(src.toString());
        String out = read(start(cmd, dir));

        assertFalse(out.contains("JavaFX"),
                "kof workflow run não pode imprimir a máscara JavaFX (§556):\n" + out);
        assertTrue(out.contains("ok") || out.contains("allOk") || out.contains("VerifyError")
                        || out.contains("NoClassDefFoundError") || out.contains("Exception")
                        || out.contains("SEM"),
                "o pipeline roda ou a causa real aparece (§628: SEM014 de compile-time conta):\n" + out);
    }

    @Test
    void kofTestNeverPrintsJavafxMask(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
            import java.security.MessageDigest

            test "digest" {
                var md = MessageDigest.getInstance("SHA-256")
                var b = new Byte[3]
                b[0] = 97
                b[1] = 98
                b[2] = 99
                md.update(b)
                assert(md.digest().length == 32)
            }
            """);
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.add("test");
        cmd.add(src.toString());
        String out = read(start(cmd, dir));

        assertFalse(out.contains("JavaFX"),
                "kof test não pode imprimir a máscara JavaFX (§556):\n" + out);
        assertTrue(out.contains("PASS digest") || out.contains("VerifyError")
                        || out.contains("NoClassDefFoundError") || out.contains("Exception")
                        || out.contains("SEM"),
                "o teste roda ou a causa real aparece (§628: SEM014 de compile-time conta):\n" + out);
    }
}
