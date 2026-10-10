package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suporte dos testes E2E multi-arquivo que rodam o MESMO conjunto de fontes em
 * Script e JS ({@code SealedTypeE2ETest}, {@code TypeVarianceE2ETest},
 * {@code UseSiteVarianceE2ETest}, {@code InteropSchemaE2ETest}): o driver, o
 * {@code runScript} e o {@code runJs} eram byte-idênticos entre as classes. Vive
 * fora delas (Fase 5/harness, {@code D-TEST-ARCHITECTURE-PHASES}); os testes e os
 * nomes das classes seguem nos arquivos — zero drift de citação.
 */
abstract class MultiSourceRunSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    protected void runScript(Path root, List<Path> sources, String expected) {
        KofInterpreter.Result r = driver.interpret(sources, root, new String[0]);
        assertEquals(0, r.exitCode(), "SCRIPT exit code, output: " + r.stdout());
        assertEquals(expected, r.stdout().trim().replace("\r\n", "\n"), "SCRIPT output");
    }

    protected void runJs(Path root, List<Path> sources, String expected) throws Exception {
        Path outDir = root.resolve("js-" + System.nanoTime());
        CompilationResult result = driver.compileSources(sources, outDir, Target.JS, root);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        Path entry;
        try (var s = Files.walk(outDir)) {
            entry = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst()
                    .orElseThrow(() -> new IOException("no Default.mjs in " + outDir));
        }
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                    InputStream.nullInputStream(), new ByteArrayOutputStream());
            String output = buf.toString(StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals(expected, output, "JS output");
        }
    }
}
