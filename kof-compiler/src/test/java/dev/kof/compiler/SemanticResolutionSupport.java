package dev.kof.compiler;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suporte do teste de resolução semântica ({@code SemanticResolutionTest}): o driver e os
 * oráculos SEM025/SEM050. Os programas Kof hoisted vivem em {@code SemanticResolutionPrograms}. Vive
 * fora da classe de teste (Fase 3 da arquitetura de testes,
 * {@code D-TEST-ARCHITECTURE-GO}); os testes e o nome da classe seguem no
 * {@code SemanticResolutionTest} — zero drift de citação.
 */
abstract class SemanticResolutionSupport extends SemanticResolutionPrograms {

    protected final CompilerDriver driver = new CompilerDriver();

    protected CompilationResult compile(@TempDir Path tmp, String name, String src) throws IOException {
        Path source = tmp.resolve(name);
        Files.writeString(source, src);
        return driver.compile(source, tmp.resolve("out"), Target.JVM);
    }

    protected void assertSem025(CompilationResult r, String snippet) {
        assertFalse(r.success(), "deve falhar: " + snippet);
        boolean found = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM025".equals(d.code()) && d.message().contains(snippet));
        assertTrue(found, "esperava SEM025 contendo '" + snippet + "', foi: "
                + r.diagnostics().getDiagnostics());
    }

    protected void assertSem050(CompilationResult r, String snippet) {
        assertFalse(r.success(), "deve falhar: " + snippet);
        boolean found = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM050".equals(d.code()) && d.message().contains(snippet));
        assertTrue(found, "esperava SEM050 contendo '" + snippet + "', foi: "
                + r.diagnostics().getDiagnostics());
    }
}
