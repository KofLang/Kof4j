package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §134 — regressão do external classpath (0.3.1→0.4.x): PKG006 (e7005c69)
 * passou a rejeitar TODO import não-whitelisted SEM consultar os entries
 * carregados pelo --classpath/--deps, e a análise semântica marcava SEM011
 * no receiver de chamada estática externa (Greeter.hello) — o lowering
 * (ExpressionMethodCallLowerer) já resolvia via ExternalClasspath, mas
 * nunca era alcançado. Estes testes usam um pacote FORA da whitelist
 * (ext.*, como qualquer gson/postgresql/lib interna) e provam:
 * compila + roda com o valor real do jar externo.
 */
class ExternalClasspathE2ETest {

    private Path buildJar(Path tempDir) throws IOException, InterruptedException {
        Path srcRoot = tempDir.resolve("extsrc");
        Files.createDirectories(srcRoot.resolve("ext"));
        Files.writeString(srcRoot.resolve("ext/Greeter.java"), """
            package ext;
            public class Greeter {
                public static String hello(String n) { return "hi " + n; }
            }
            """);
        Path classes = tempDir.resolve("extcls");
        ProcessBuilder pb = new ProcessBuilder("javac", "--release", "21", "-d",
                classes.toString(), srcRoot.resolve("ext/Greeter.java").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), "javac falhou: " + out);
        Path jar = tempDir.resolve("ext-greeter.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Path cls : Files.walk(classes).filter(f -> f.toString().endsWith(".class")).toList()) {
                z.putNextEntry(new ZipEntry(classes.relativize(cls).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(cls));
            }
        }
        return jar;
    }

    private void compileAndRun(Path tempDir, Path jar, String kf, String expect) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kf);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "compilação deve passar (PKG006/SEM011 não podem aparecer): "
                + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp",
                tempDir.resolve("out").toString() + ":" + jar, "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "execução falhou: " + out);
        assertEquals(expect, out);
    }

    @Test
    void staticCallOnExternalClassFromJarCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() { println(Greeter.hello("mel")) }
            """, "hi mel");
    }

    @Test
    void instancePathStillWorksWithExternalJar(@TempDir Path tempDir) throws Exception {
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() {
                var g = new Greeter()
                println(g != null)
            }
            """, "true");
    }

    @Test
    void unknownImportOutsideClasspathStillFailsPKG006(@TempDir Path tempDir) throws Exception {
        // R6: a correção não pode virar silêncio — import que NÃO está na
        // whitelist nem nos entries continua PKG006.
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import nope.Absent
            main() { println(Absent.x()) }
            """);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(x -> x.code().equals("PKG006")),
                "esperado PKG006, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void externalJvmClassIsRejectedOnNativeAndJs(@TempDir Path tempDir) throws Exception {
        // §134 (R6 cross-target honesto): interop com .class JVM só faz
        // sentido nos targets JVM-family (JVM/ANDROID). Em NATIVE o linker
        // não tem o símbolo (undefined reference); em JS o lowering emitiria
        // `ext_Greeter` pendurado e rodaria null em runtime. Deixar o import
        // passar nesses targets seria regressão de silêncio — os dois
        // continuam PKG006 mesmo com o jar carregado.
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import ext.Greeter
            main() { println(Greeter.hello("mel")) }
            """);
        for (Target t : new Target[] { Target.NATIVE, Target.JS }) {
            CompilerDriver d = new CompilerDriver();
            d.setExternalClasspath(List.of(jar));
            CompilationResult r = d.compile(source, tempDir.resolve("out-" + t), t);
            assertFalse(r.success(), t + " não pode aceitar classe externa JVM: "
                    + r.diagnostics().getDiagnostics());
            assertTrue(r.diagnostics().getDiagnostics().stream()
                    .anyMatch(x -> x.code().equals("PKG006")),
                    t + ": esperado PKG006, veio " + r.diagnostics().getDiagnostics());
        }
    }
}
