package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #708 (30/09) — defeito SEPARADO reportado na issue: a API Java
 * {@code CompilerDriver.compileForTestsSources(..., moduleRoot)} encaminhava
 * {@code driver.moduleRoot} em vez do argumento recebido. Um projeto com
 * raízes separadas (app × test) não conseguia apontar a raiz correta e o CLI
 * ficava sem caminho oficial para compilar fontes reais a partir de duas
 * árvores. A prova abaixo é RED-first contra o encaminhamento errado: o campo
 * fica com o valor DEFASADO se o argumento não for usado.
 */
class CompileForTestsSourcesModuleRootTest {

    @Test
    void compileForTestsSourcesForwardsPassedModuleRoot(@TempDir Path tempDir) throws IOException {
        Path staleRoot = Files.createDirectories(tempDir.resolve("stale"));
        Path testRoot = Files.createDirectories(tempDir.resolve("src/test/kof"));
        Path appRoot = Files.createDirectories(tempDir.resolve("src/main/kof"));

        Path testFile = testRoot.resolve("exemplo/CalculoTest.kf");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "package exemplo\ntest \"ok\" {\n    assert(1 == 1)\n}\n");

        CompilerDriver driver = new CompilerDriver();
        driver.moduleRoot = staleRoot;

        driver.compileForTestsSources(List.of(testFile), tempDir.resolve("out"),
                Target.JVM, appRoot);

        assertEquals(appRoot, driver.moduleRoot,
                "compileForTestsSources must forward the moduleRoot argument, not driver.moduleRoot");
    }
}
