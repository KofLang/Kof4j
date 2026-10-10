package dev.kof.compiler;

import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suporte dos E2E que compilam e executam um {@code main} Kof contra uma
 * biblioteca instalada ({@code libs/kofmd}/{@code libs/pdf}): o {@code runKof}
 * (seta {@code kof.install.dir}, compila para JVM, carrega {@code Default.Main}
 * por reflexão) era byte-idêntico em 6 classes Kofmd + {@code PdfLibraryE2ETest}.
 * Vive fora delas (Fase 5/harness, {@code D-TEST-ARCHITECTURE-PHASES}); os testes
 * e os nomes das classes seguem nos arquivos — zero drift de citação. A subclasse
 * fornece {@code libraryName}/{@code libraryMarkers} (via {@link LibraryInstallSupport});
 * o {@code copyLibrary}/{@code findLibraryRoot} vive uma vez na base.
 */
abstract class KofmdRunSupport implements LibraryInstallSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    @TempDir
    protected Path tmp;

    /** Prefixo do diretório temporário de saída; a subclasse pode trocar. */
    protected String outPrefix() {
        return "kofmd-out-";
    }

    protected void runKof(String code, String outPrefix) throws Exception {
        Path installRoot = tmp.resolve("kof-install");
        copyLibrary(installRoot.resolve("lib/kof-libs"));
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = Files.createTempDirectory(tmp, outPrefix);
        String previousInstallDir = System.getProperty("kof.install.dir");
        CompilationResult result;
        System.setProperty("kof.install.dir", installRoot.toString());
        try {
            result = driver.compile(source, out, Target.JVM);
        } finally {
            if (previousInstallDir == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previousInstallDir);
        }
        assertTrue(result.success(), () -> result.diagnostics().getDiagnostics().toString());

        try (var loader = new URLClassLoader(
                new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, loader)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        }
    }

    protected void runKof(String code) throws Exception {
        runKof(code, outPrefix());
    }
}
