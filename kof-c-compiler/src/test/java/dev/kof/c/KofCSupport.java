package dev.kof.c;

import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Suporte dos testes do compilador C ({@code KofC*CompilerTest}): localização de
 * toolchain, execução sob qemu e o laço de golden por programa. Vive fora das
 * classes de teste para deduplicar (Fase 4/harness, {@code D-TEST-ARCHITECTURE-GO})
 * — os testes e os nomes das classes seguem nos 4 arquivos, zero drift.
 */
abstract class KofCSupport {

    protected record Prog(String name, String src, String golden) {}

    /** Programas a validar por alvo; subclasses que usam o laço sobrescrevem. */
    protected List<Prog> programs() {
        return List.of();
    }

    protected static boolean has(String... cmds) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String c : cmds) {
            boolean found = false;
            for (String d : path.split(File.pathSeparator)) {
                if (Files.isExecutable(Path.of(d, c))) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    protected static void requireTools(KofCTarget t) {
        assumeTrue(has(t.assembler().get(0), t.linker()) && (t.qemu() == null || has(t.qemu())),
                "toolchain " + t + " + qemu ausente — pulando (NATIVE002)");
    }

    protected static String run(KofCTarget target, Path tmp, String source) throws Exception {
        Files.createDirectories(tmp);
        Path c = tmp.resolve("prog.c");
        Files.writeString(c, source);
        KofCCompiler.CompileResult res = KofCCompiler.compile(c, tmp.resolve("out"), target);
        assertTrue(res.success(), "compile " + target + " falhou: " + res.diagnostics());
        assertTrue(Files.exists(res.binary()), "binário ausente para " + target);

        List<String> cmd = new ArrayList<>();
        if (target.qemu() != null) cmd.add(target.qemu());
        cmd.add(res.binary().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError(target + " não terminou em 30s (saída: '" + output + "')");
        }
        assertEquals(0, p.exitValue(), "exit != 0 em " + target + " (saída: '" + output + "')");
        return output;
    }

    protected void assertAllPrograms(KofCTarget target, Path tmp) throws Exception {
        for (Prog p : programs()) {
            assertEquals(p.golden(), run(target, tmp.resolve(p.name().replace(' ', '_')), p.src()),
                    target + " / " + p.name());
        }
    }

    protected static KofCCompiler.CompileResult compile(Path tmp, String source) throws Exception {
        Files.createDirectories(tmp);
        Path c = tmp.resolve("bad.c");
        Files.writeString(c, source);
        return KofCCompiler.compile(c, tmp.resolve("out"), KofCTarget.X86_64);
    }
}
