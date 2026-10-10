package dev.kof.c;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fatia C4 ({@code docs/development/kof-c-cross.md}): saída de objeto
 * reutilizável (`.o`) + link. A fixture C (com parâmetro struct por valor) é
 * compilada como objeto cross e ligada ao executável do driver — o caminho
 * que os testes FFI cross vão usar. Prova por execução real sob qemu.
 */
class KofCObjectCompilerTest extends KofCSupport {

    private static final String FIXTURE = """
            struct Pair { int a; int b; };
            int take(struct Pair p) { return p.a + p.b; }
            """;

    private static final String DRIVER = """
            struct Pair { int a; int b; };
            int take(struct Pair p);
            void main() {
              struct Pair s;
              s.a = 30;
              s.b = 12;
              print_arg = take(s);
              print();
            }
            """;



    /** Compiles the fixture to an object, links the driver with it, runs, returns stdout. */
    private static String runWithFixture(KofCTarget target, Path tmp) throws Exception {
        Files.createDirectories(tmp);
        Path fx = tmp.resolve("fixture.c");
        Path dr = tmp.resolve("driver.c");
        Files.writeString(fx, FIXTURE);
        Files.writeString(dr, DRIVER);

        Path obj = tmp.resolve("fixture.o");
        KofCCompiler.CompileResult objRes = KofCCompiler.compileObject(fx, obj, target);
        assertTrue(objRes.success(), "compileObject " + target + " falhou: " + objRes.diagnostics());
        assertTrue(Files.exists(obj) && Files.size(obj) > 0, "objeto vazio para " + target);

        KofCCompiler.CompileResult res =
                KofCCompiler.compile(dr, tmp.resolve("out"), target, List.of(obj));
        assertTrue(res.success(), "link do driver+fixture " + target + " falhou: " + res.diagnostics());

        List<String> cmd = new ArrayList<>();
        if (target.qemu() != null) cmd.add(target.qemu());
        cmd.add(res.binary().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError(target + " não terminou em 30s (saída: '" + out + "')");
        }
        assertEquals(0, p.exitValue(), "exit != 0 em " + target + " (saída: '" + out + "')");
        return out;
    }

    @Test
    void x86LinksFixtureObject(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.X86_64);
        assertEquals("42", runWithFixture(KofCTarget.X86_64, tmp));
    }

    @Test
    void riscv64LinksFixtureObject(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.RISCV64);
        assertEquals("42", runWithFixture(KofCTarget.RISCV64, tmp.resolve("rv")));
    }

    @Test
    void aarch64LinksFixtureObject(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.AARCH64);
        assertEquals("42", runWithFixture(KofCTarget.AARCH64, tmp.resolve("arm")));
    }

    @Test
    void objectModeDoesNotRequireMain(@TempDir Path tmp) throws Exception {
        // a fixture não tem main: o modo objeto não pode exigir entry point.
        Files.createDirectories(tmp);
        Path fx = tmp.resolve("fixture.c");
        Files.writeString(fx, FIXTURE);
        var res = KofCCompiler.compileObject(fx, tmp.resolve("fixture.o"), KofCTarget.X86_64);
        assertTrue(res.success(), "objeto sem main deve compilar: " + res.diagnostics());
        assertTrue(Files.exists(res.binary()));
    }

    @Test
    void executableModeStillRequiresMain(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp);
        Path c = tmp.resolve("nom.c");
        Files.writeString(c, "int f(int a) { return a; }\n");
        var res = KofCCompiler.compile(c, tmp.resolve("out"), KofCTarget.X86_64);
        assertFalse(res.success(), "executável sem main deve falhar");
        assertTrue(res.diagnostics().contains("missing main"), res.diagnostics());
    }
}
