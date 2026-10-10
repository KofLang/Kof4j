package dev.kof.cli;

import java.nio.file.Path;

/**
 * Suporte dos testes de CLI ({@code CompareTest}/{@code MigrateTest}): o helper
 * {@code javac} idêntico entre as duas classes. Vive fora delas (Fase 4/harness,
 * {@code D-TEST-ARCHITECTURE-GO}); os testes e os nomes das classes seguem nos
 * dois arquivos — zero drift de citação.
 */
abstract class CliJavacSupport {

    protected void javac(Path javaFile, Path dir) throws Exception {
        Path javac = Path.of(System.getProperty("java.home"), "bin", "javac");
        ProcessBuilder pb = new ProcessBuilder(javac.toString(), "-d", dir.toString(), javaFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int rc = p.waitFor();
        if (rc != 0) throw new RuntimeException("javac: " + new String(p.getInputStream().readAllBytes()));
    }
}
