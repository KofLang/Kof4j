package dev.kof.compiler;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Suporte dos testes E2E que compilam para JVM e executam {@code Default.Main}
 * capturando o stdout ({@code FnTypeInGenericDeclaredTypeTest},
 * {@code HeterogeneousListInferTest}, {@code ReduceStringCastTest},
 * {@code NestedFnTypeArityTest}, {@code LambdaFieldCaptureTest},
 * {@code FnTypeFieldCallTest}, {@code PrimitiveStringEqTest}): o corpo de
 * {@code assertRuns} era byte-idêntico em 7 classes. Vive fora delas (Fase 5/
 * harness, {@code D-TEST-ARCHITECTURE-PHASES}); os testes e os nomes das classes
 * seguem nos arquivos — zero drift de citação.
 */
abstract class JvmRunSupport {

    /** Executa {@code Default.Main} do diretório compilado e devolve o stdout normalizado. */
    protected String runJvmMain(Path outDir) throws Exception {
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run must exit 0, got:\n" + out);
        return out;
    }

    protected void assertRuns(Path outDir, String expected, String label) throws Exception {
        assertEquals(expected, runJvmMain(outDir), label);
    }

    protected void assertRuns(Path outDir, String expected) throws Exception {
        assertEquals(expected, runJvmMain(outDir));
    }
}
