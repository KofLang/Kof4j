package dev.kof.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suporte do gate de paridade do interpretador
 * ({@code KofInterpreterParityTest}): o harness {@code parity} (interpretado vs
 * JVM compilado+fork). Os programas hoisted vivem em
 * {@code KofInterpreterParityPrograms}. Vive fora da classe de teste (Fase 3 da
 * arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os testes e o nome da
 * classe seguem no {@code KofInterpreterParityTest} — zero drift de citação.
 */
abstract class KofInterpreterParitySupport extends KofInterpreterParityPrograms {

    protected void parity(String label, String code) throws IOException {
        Path d = Files.createTempDirectory("kip-" + label);
        Path f = d.resolve("Main.kf");
        Files.writeString(f, code);

        // interpretado (sem bytecode, sem fork)
        String interpOut;
        int interpExit;
        try {
            KofInterpreter.Result r = new CompilerDriver().interpret(List.of(f), d, new String[0]);
            interpOut = r.stdout();
            interpExit = r.exitCode();
        } catch (KofInterpretException e) {
            interpOut = "FRONTEND-ERR";
            interpExit = -1;
        }

        // compilado + fork JVM real
        String jvmOut;
        int jvmExit;
        Path outDir = d.resolve("o");
        CompilationResult cr = new CompilerDriver().compile(f, outDir, Target.JVM);
        if (!cr.success()) {
            jvmOut = "FRONTEND-ERR";
            jvmExit = -1;
        } else {
            try {
                ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
                pb.redirectErrorStream(false);
                Process p = pb.start();
                jvmOut = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                jvmExit = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }

        assertEquals(jvmExit, interpExit, label + ": exit code divergente");
        assertEquals(jvmOut, interpOut, label + ": stdout divergente (interpretado vs JVM)");
    }
}
