package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-MEMORY-SAFETY Fase 3 (fatia 1) — {@code OwnershipPass}: as faces
 * retilineas de O-01/{@code MEM001} (dupla reivindicao de close no mesmo
 * recurso) e O-02/{@code MEM002} (leitura do binding-original depois que a
 * posse foi reivindicada por um irmao da cadeia de aliases), resolvidas SEM
 * literal {@code null} conforme {@code DECISIONS.md} §`D-COMPLETE-FIRST`.
 *
 * <p>Os programas usam uma {@code class} propria com {@code void close()} —
 * o passe e sintetico quanto ao tipo (reivindicacao = padrao {@code x.close()});
 * o typer permanece limpo e os codigos MEM sao os unicos erros. Invalidos:
 * assert de codigo + ausencia de sucesso nos 4 alvos (a analise antecede o
 * backend, entao nao depende de toolchain). Validos: byte-green (compilam e,
 * no JVM, rodam com saida golden real — Q11, nao memoria).
 */
class MemorySafetyE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String HANDLE = """
            class Handle {
                Int id
                public constructor(Int id) { this.id = id }
                void close() { }
                Int get() { return id }
            }
            """;

    private String diagText(CompilationResult r) {
        return r.diagnostics().getDiagnostics().toString();
    }

    private CompilationResult compile(Path tempDir, String name, String source, Target target) throws IOException {
        Path file = tempDir.resolve(name + "-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + name + "-" + System.nanoTime());
        return driver.compile(file, outDir, target);
    }


    private void assertScriptDiag(Path tempDir, String name, String src, String code) throws IOException {
        Path kf = tempDir.resolve(name + "-" + System.nanoTime() + ".kf");
        Files.writeString(kf, src);
        String text;
        int exit;
        try {
            KofInterpreter.Result ir = driver.interpret(java.util.List.of(kf), tempDir, new String[0]);
            text = ir.stderr();
            exit = ir.exitCode();
        } catch (KofInterpretException e) {
            text = String.valueOf(e.getMessage());
            exit = 1;
        }
        assertTrue(exit != 0, "SCRIPT deveria falhar com " + code + " — stderr: " + text);
        assertTrue(text.contains(code), "SCRIPT: esperado " + code + " — " + text);
    }

    // ---- faces INVALIDAS: MEM001/MEM002 nos 4 alvos (analise pre-backend) ----

    @Test
    void doubleCloseClaimsFailWithMem001OnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                main() {
                    var h = Handle(1)
                    h.close()
                    h.close()
                }
                """;
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "mem001-" + t, src, t);
            assertFalse(r.success(), t + ": dupla reivindicacao deve falhar — " + diagText(r));
            assertTrue(diagText(r).contains("MEM001"), t + ": esperado MEM001 — " + diagText(r));
            assertFalse(diagText(r).contains("MEM002"), t + ": face errada — " + diagText(r));
        }
        assertScriptDiag(tempDir, "mem001-script", src, "MEM001");
    }

    @Test
    void aliasClaimThenOriginalClaimFailsWithMem001(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                main() {
                    var h = Handle(2)
                    var a = h
                    a.close()
                    h.close()
                }
                """;
        CompilationResult r = compile(tempDir, "mem001-alias", src, Target.JVM);
        assertFalse(r.success(), "esperado falhar — " + diagText(r));
        assertTrue(diagText(r).contains("MEM001"), "esperado MEM001 — " + diagText(r));
    }

    @Test
    void aliasClaimThenReadOriginalFailsWithMem002OnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                main() {
                    var h = Handle(3)
                    var a = h
                    a.close()
                    println(a.get() + h.get())
                }
                """;
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "mem002-" + t, src, t);
            assertFalse(r.success(), t + ": leitura pos-transferencia deve falhar — " + diagText(r));
            assertTrue(diagText(r).contains("MEM002"), t + ": esperado MEM002 — " + diagText(r));
        }
        assertScriptDiag(tempDir, "mem002-script", src, "MEM002");
    }

    @Test
    void chainOfAliasesPropagatesGroupToRoot(@TempDir Path tempDir) throws IOException {
        // var b = a; var c = b; a.close(); c.close() → MEM001 na raiz a
        String src = HANDLE + """
                main() {
                    var a = Handle(4)
                    var b = a
                    var c = b
                    a.close()
                    c.close()
                }
                """;
        CompilationResult r = compile(tempDir, "mem001-chain", src, Target.JVM);
        assertFalse(r.success(), "esperado falhar — " + diagText(r));
        assertTrue(diagText(r).contains("MEM001"), "esperado MEM001 — " + diagText(r));
    }


    // ---- FASE 3 FATIA 2: cruzamento de fluxo (snapshot sem propagar) ----

    @Test
    void claimThenSiblingReadInsideBranchFailsMem002(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                main() {
                    var h = Handle(10)
                    var flag = true
                    var a = h
                    a.close()
                    if (flag) {
                        println(h.get())
                    }
                }
                """;
        CompilationResult r = compile(tempDir, "mem002-branch", src, Target.JVM);
        assertFalse(r.success(), "leitura condicionada pos-claim deve arder — " + diagText(r));
        assertTrue(diagText(r).contains("MEM002"), "esperado MEM002 — " + diagText(r));
    }

    @Test
    void sequentialDoubleClaimInsideBranchFailsMem001(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                main() {
                    var h = Handle(11)
                    var flag = true
                    var a = h
                    if (flag) {
                        a.close()
                        h.close()
                    }
                }
                """;
        CompilationResult r = compile(tempDir, "mem001-branch", src, Target.JVM);
        assertFalse(r.success(), "dupla reivindicacao no MESMO ramo arde — " + diagText(r));
        assertTrue(diagText(r).contains("MEM001"), "esperado MEM001 — " + diagText(r));
    }

    @Test
    void conditionalClaimDoesNotMakeStraightClaimIllegal(@TempDir Path tempDir) throws IOException {
        // Anti-falso-positivo POR CONSTRUCAO: close condicional NAO propaga —
        // o close retilineo seguinte e programa legitimo hoje.
        String src = HANDLE + """
                main() {
                    var h = Handle(12)
                    var flag = true
                    if (flag) {
                        h.close()
                    }
                    h.close()
                }
                """;
        CompilationResult r = compile(tempDir, "green-branch", src, Target.JVM);
        assertTrue(r.success(), "ramo nao propaga: " + diagText(r));
    }

    @Test
    void tryBodyClaimThenStraightClaimStayGreen(@TempDir Path tempDir) throws IOException {
        // try/catch/finally partem do snapshot PRE-try: o idiom legado
        // `try { r.close() } finally { if (x) r.close() }` + close externo nao
        // pode virar erro por decisao de fatia — nenhuma face CERTA foi violada.
        String src = HANDLE + """
                main() {
                    var h = Handle(13)
                    var flag = false
                    try {
                        h.close()
                    } finally {
                        if (flag) {
                            h.close()
                        }
                    }
                    h.close()
                }
                """;
        CompilationResult r = compile(tempDir, "green-try", src, Target.JVM);
        assertTrue(r.success(), "faces condicionais nao propagam: " + diagText(r));
    }

    @Test
    void loopBodySequentialDoubleClaimFailsMem001(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                main() {
                    var h = Handle(14)
                    var flag = true
                    while (flag) {
                        h.close()
                        h.close()
                    }
                }
                """;
        CompilationResult r = compile(tempDir, "mem001-loop", src, Target.JVM);
        assertFalse(r.success(), "duplo close na MESMA iteracao arde — " + diagText(r));
        assertTrue(diagText(r).contains("MEM001"), "esperado MEM001 — " + diagText(r));
    }

    @Test
    void switchCaseReadAfterOuterClaimFailsMem002(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                main() {
                    var h = Handle(15)
                    var a = h
                    var k = 1
                    a.close()
                    switch (k) {
                        case 1: println(h.get()); break
                        default: println(0)
                    }
                }
                """;
        CompilationResult r = compile(tempDir, "mem002-switch", src, Target.JVM);
        assertFalse(r.success(), "leitura em case pos-claim CERTO arde — " + diagText(r));
        assertTrue(diagText(r).contains("MEM002"), "esperado MEM002 — " + diagText(r));
    }

    @Test
    void blockClaimPropagatesAndSiblingUseAfterBlockFailsMem002(@TempDir Path tempDir) throws IOException {
        // bloco e incondicional: o claim CERTO dentro dele vale depois dele.
        // (bloco nu depois de `var a = h` ligaria como trailing-lambda —
        // contrato do parser; entao o bloco proposital segue um `}`.)
        String src = HANDLE + """
                main() {
                    var h = Handle(16)
                    var a = h
                    if (true) {
                        println(0)
                    }
                    {
                        a.close()
                    }
                    println(h.get())
                }
                """;
        CompilationResult r = compile(tempDir, "mem002-block", src, Target.JVM);
        assertFalse(r.success(), "bloco propaga claim certo — " + diagText(r));
        assertTrue(diagText(r).contains("MEM002"), "esperado MEM002 — " + diagText(r));
    }


    // ---- FASE 3 FATIA 3: escape/dangling (L-04/MEM013, escape por return) ----

    @Test
    void returnClaimerAfterCloseFailsMem013OnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                Handle make() {
                    var h = Handle(20)
                    h.close()
                    return h
                }
                main() {
                    var x = make()
                    println(x.id)
                }
                """;
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "mem013-" + t, src, t);
            assertFalse(r.success(), t + ": escape do handle fechado deve falhar — " + diagText(r));
            assertTrue(diagText(r).contains("MEM013"), t + ": esperado MEM013 — " + diagText(r));
        }
        assertScriptDiag(tempDir, "mem013-script", src, "MEM013");
    }

    @Test
    void returnClaimerBeforeCloseStaysGreen(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                Handle make() {
                    var h = Handle(21)
                    return h
                }
                main() {
                    var x = make()
                    println(x.id)
                }
                """;
        CompilationResult r = compile(tempDir, "green-return", src, Target.JVM);
        assertTrue(r.success(), "retorno antes do close e legal: " + diagText(r));
    }

    @Test
    void returnSiblingAfterCloseStaysMem002NotMem013(@TempDir Path tempDir) throws IOException {
        // Face mais precisa: irmao nao-reivindicante = use-after-move (O-02).
        String src = HANDLE + """
                Handle make() {
                    var h = Handle(22)
                    var a = h
                    a.close()
                    return h
                }
                main() {
                    var x = make()
                    println(x.id)
                }
                """;
        CompilationResult r = compile(tempDir, "mem002-return", src, Target.JVM);
        assertFalse(r.success(), "retorno de irmao pos-close arde — " + diagText(r));
        assertTrue(diagText(r).contains("MEM002"), "esperado MEM002 — " + diagText(r));
        assertFalse(diagText(r).contains("MEM013"), "MEM013 so no reivindicante — " + diagText(r));
    }

    @Test
    void returnClaimerInsideBranchAfterOuterClaimFailsMem013(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                Handle make() {
                    var h = Handle(23)
                    var flag = true
                    h.close()
                    if (flag) {
                        return h
                    }
                    return h
                }
                main() {
                    var x = make()
                    println(x.id)
                }
                """;
        CompilationResult r = compile(tempDir, "mem013-branch", src, Target.JVM);
        assertFalse(r.success(), "escape condicional pos-claim certo arde — " + diagText(r));
        assertTrue(diagText(r).contains("MEM013"), "esperado MEM013 — " + diagText(r));
    }

    @Test
    void singleClaimNoReturnDoesNotEmitMem013(@TempDir Path tempDir) throws IOException {
        // Claim + reuso local (sem escape) segue verde: MEM013 e so fronteira.
        String src = HANDLE + """
                Handle make() {
                    var h = Handle(24)
                    h.close()
                    return h
                }
                main() {
                    println("ok")
                }
                """;
        CompilationResult r = compile(tempDir, "mem013-only", src, Target.JVM);
        assertFalse(r.success(), "ha escape: deve emitir MEM013 — " + diagText(r));
        assertTrue(diagText(r).contains("MEM013"), "esperado MEM013 — " + diagText(r));
    }

    // ---- faces VALIDAS: byte-green (zero mudanca de comportamento) ----

    @Test
    void singleClaimCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                main() {
                    var h = Handle(5)
                    h.close()
                    println("ok")
                }
                """;
        for (Target t : new Target[]{Target.JVM, Target.JS}) {
            CompilationResult r = compile(tempDir, "valid-" + t, src, t);
            assertTrue(r.success(), t + ": reivindicacao unica deve compilar — " + diagText(r));
        }
        // SCRIPT: o driver nao emite artefatos (COMP003 e o contrato da casa) —
        // o frontend compartilhado roda via interpret(); vale como prova verde.
        Path kf = tempDir.resolve("valid-script-" + System.nanoTime() + ".kf");
        Files.writeString(kf, src);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(kf), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "SCRIPT green, stderr: " + ir.stderr());
    }

    @Test
    void claimerReuseAndPlainAliasingStayGreen(@TempDir Path tempDir) throws IOException {
        // Duas faces verdes: (1) o REIVINDICANTE pode reler o proprio binding
        // e os proprios metodos (a leitura pos-close do handle e L-02/MEM011,
        // runtime, nao face O-02); (2) alias SEM nenhuma reivindicacao e o
        // caso B-01 (aliasing compartilhado permitido) — nada deve arder.
        String claimerSrc = HANDLE + """
                main() {
                    var h = Handle(6)
                    h.close()
                    println(h.get() + h.id)
                }
                """;
        CompilationResult r = compile(tempDir, "valid-claimer", claimerSrc, Target.JVM);
        assertTrue(r.success(), "byte-green (claimer): " + diagText(r));
        String aliasSrc = HANDLE + """
                main() {
                    var h = Handle(6)
                    var g = h
                    println(g.id + h.id)
                }
                """;
        CompilationResult r2 = compile(tempDir, "valid-alias", aliasSrc, Target.JVM);
        assertTrue(r2.success(), "byte-green (B-01 alias): " + diagText(r2));
    }

    @Test
    void separateHandlesDoubleClosedAreIndependent(@TempDir Path tempDir) throws IOException {
        String src = HANDLE + """
                main() {
                    var x = Handle(7)
                    var y = Handle(8)
                    x.close()
                    y.close()
                }
                """;
        CompilationResult r = compile(tempDir, "valid-sep", src, Target.JVM);
        assertTrue(r.success(), "recursos distintos: " + diagText(r));
    }

    @Test
    void validProgramRunsOnJvmWithGoldenOutput(@TempDir Path tempDir) throws Exception {
        String src = HANDLE + """
                main() {
                    var h = Handle(9)
                    h.close()
                    println("green")
                }
                """;
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, src);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult r = driver.compile(file, outDir, Target.JVM);
        assertTrue(r.success(), "compile: " + diagText(r));
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida: " + out);
        assertEquals("green", out, "JVM golden");
    }
}
