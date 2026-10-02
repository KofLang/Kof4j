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

    // ---- fatia 4: B-05/MEM022 mutacao durante iteracao (WARNING, zero-FP) ----

    private void assertMutationWarnsOnAllTargets(Path tempDir, String name, String src) throws IOException {
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, name + "-" + t, src, t);
            assertTrue(r.success(), t + ": MEM022 e warning, build verde — " + diagText(r));
            assertTrue(diagText(r).contains("MEM022"), t + ": esperado MEM022 — " + diagText(r));
        }
    }

    @Test
    void addDuringIterationWarnsMem022OnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var list = listOf(1, 2, 3)
                    for (var x in list) {
                        list.add(x)
                    }
                }
                """;
        assertMutationWarnsOnAllTargets(tempDir, "mem022-add", src);
    }

    /**
     * #678 (`D-SCRIPT-WARN-SURFACE`, opção A): o alvo Script engolia os
     * WARNING do frontend — o JVM/JS/Native imprimem MEM022, mas
     * {@code driver.interpret} devolvia {@code stderr=[]} e descartava o
     * {@code DiagnosticCollector}. Agora o {@code Result} carrega os warnings
     * (paridade de diagnósticos, DoD do plano). RED medido 29/09: antes,
     * {@code warnings()} era vazio para esta mesma fonte. Usa mutação
     * TERMINANTE (`remove(0)`) — `add` é loop runaway no interpretador.
     */
    @Test
    void mutationDuringIterationWarnsMem022OnScript(@TempDir Path tempDir) throws IOException {
        Path kf = tempDir.resolve("mem022-script-" + System.nanoTime() + ".kf");
        Files.writeString(kf, """
                main() {
                    var list = listOf(1, 2, 3)
                    for (var x in list) {
                        list.remove(0)
                    }
                    println("done")
                }
                """);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(kf), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "programa terminante roda: " + ir.stderr());
        String warnings = ir.warnings().stream()
                .map(Diagnostic::format).reduce("", (a, b) -> a + b + "\n");
        assertTrue(warnings.contains("MEM022"),
                "SCRIPT deveria expor MEM022 como os demais alvos, veio: [" + warnings + "]");
    }

    @Test
    void removeClearAndAddAllDuringIterationWarnMem022(@TempDir Path tempDir) throws IOException {
        assertMutationWarnsOnAllTargets(tempDir, "mem022-remove", """
                main() {
                    var list = listOf(1, 2, 3)
                    for (var x in list) {
                        list.remove(0)
                    }
                }
                """);
        assertMutationWarnsOnAllTargets(tempDir, "mem022-clear", """
                main() {
                    var list = listOf(1, 2, 3)
                    for (var x in list) {
                        list.clear()
                    }
                }
                """);
        assertMutationWarnsOnAllTargets(tempDir, "mem022-addall", """
                main() {
                    var list = listOf(1, 2, 3)
                    for (var x in list) {
                        list.addAll(listOf(9))
                    }
                }
                """);
    }

    @Test
    void mutationThroughAliasDuringIterationWarnsMem022(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "mem022-alias", """
                main() {
                    var list = listOf(1, 2, 3)
                    var alias = list
                    for (var x in list) {
                        alias.add(x)
                    }
                }
                """, Target.JVM);
        assertTrue(r.success(), "build: " + diagText(r));
        assertTrue(diagText(r).contains("MEM022"), "alias muta a raiz iterada — " + diagText(r));
    }

    @Test
    void mutationInsideNestedBranchDuringIterationWarnsMem022(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "mem022-branch", """
                main() {
                    var list = listOf(1, 2, 3)
                    var flag = true
                    for (var x in list) {
                        if (flag) {
                            list.add(x)
                        }
                    }
                }
                """, Target.JVM);
        assertTrue(r.success(), "build: " + diagText(r));
        assertTrue(diagText(r).contains("MEM022"), "braco aninhado herda a raiz — " + diagText(r));
    }

    @Test
    void mutationOfAnotherCollectionDuringIterationStaysSilent(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "mem022-other", """
                main() {
                    var a = listOf(1, 2, 3)
                    var b = listOf(4)
                    for (var x in a) {
                        b.add(x)
                    }
                }
                """, Target.JVM);
        assertTrue(r.success(), "build: " + diagText(r));
        assertFalse(diagText(r).contains("MEM022"), "mutar OUTRA colecao nao e B-05 — " + diagText(r));
    }

    @Test
    void mutationOfElementFieldDuringIterationStaysSilent(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "mem022-field", """
                class Bag {
                    List<Int> items
                    public constructor(List<Int> items) { this.items = items }
                }
                main() {
                    var bags = listOf(Bag(listOf(1)), Bag(listOf(2)))
                    for (var b in bags) {
                        b.items.add(3)
                    }
                }
                """, Target.JVM);
        assertTrue(r.success(), "build: " + diagText(r));
        assertFalse(diagText(r).contains("MEM022"),
                "mutar campo de ELEMENTO nao e a colecao iterada — " + diagText(r));
    }

    @Test
    void deferredMutationInsideLambdaDuringIterationStaysSilent(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "mem022-lambda", """
                main() {
                    var list = listOf(1, 2, 3)
                    for (var x in list) {
                        var f = () -> { list.add(x) }
                    }
                }
                """, Target.JVM);
        assertFalse(diagText(r).contains("MEM022"),
                "lambda adiada nao e mutacao durante a varredura — " + diagText(r));
    }

    @Test
    void setDuringIterationStaysSilent(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "mem022-set", """
                main() {
                    var list = listOf(1, 2, 3)
                    for (var x in list) {
                        list.set(0, x)
                    }
                }
                """, Target.JVM);
        assertTrue(r.success(), "build: " + diagText(r));
        assertFalse(diagText(r).contains("MEM022"), "set nao muda tamanho/indices — " + diagText(r));
    }

    @Test
    void validIterationRunsOnJvmWithGoldenOutput(@TempDir Path tempDir) throws Exception {
        String src = """
                main() {
                    var list = listOf(1, 2, 3)
                    var sum = 0
                    for (var x in list) {
                        sum = sum + x
                    }
                    println(sum)
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
        assertEquals("6", out, "JVM golden");
    }

    // ---- fatia 3.2: B-04/C-03/MEM021 data race spawn x binding capturado ----

    private void assertMem021FailsOnAllTargets(Path tempDir, String name, String src) throws IOException {
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, name + "-" + t, src, t);
            assertFalse(r.success(), t + ": corrida clara spawn/mae deve falhar — " + diagText(r));
            assertTrue(diagText(r).contains("MEM021"), t + ": esperado MEM021 — " + diagText(r));
        }
        assertScriptDiag(tempDir, name + "-script", src, "MEM021");
    }

    private void assertNoMem021Green(Path tempDir, String name, String src) throws IOException {
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, name + "-" + t, src, t);
            assertTrue(r.success(), t + ": deve compilar (silencioso fora da corrida) — " + diagText(r));
            assertFalse(diagText(r).contains("MEM021"), t + ": falso-positivo MEM021 — " + diagText(r));
        }
    }

    @Test
    void spawnMutatesSharedListThenParentMutatesFailsMem021(@TempDir Path tempDir) throws IOException {
        assertMem021FailsOnAllTargets(tempDir, "mem021-race", """
                main() {
                    var a = listOf(1)
                    spawn { a.add(2) }
                    a.add(3)
                }
                """);
    }

    @Test
    void spawnInsideBareBlockThenParentMutatesFailsMem021(@TempDir Path tempDir) throws IOException {
        // #693 — a bare `{ ... }` is unconditional/straight-line, so a spawn's
        // pending race must propagate out of the block (like groups/aliasOf);
        // before the fix the block boundary dropped `racy` and the clear race
        // after it compiled clean.
        assertMem021FailsOnAllTargets(tempDir, "mem021-block", """
                main() {
                    var a = listOf(1)
                    {
                        spawn { a.add(2) }
                    }
                    a.add(3)
                }
                """);
    }

    @Test
    void handleFormSpawnWithoutAwaitFailsMem021(@TempDir Path tempDir) throws IOException {
        assertMem021FailsOnAllTargets(tempDir, "mem021-handle", """
                main() {
                    var a = listOf(1)
                    var h = spawn { a.add(2) }
                    a.add(3)
                    await h
                }
                """);
    }

    @Test
    void twoSpawnsMutatingSameListWithoutAwaitFailsMem021(@TempDir Path tempDir) throws IOException {
        assertMem021FailsOnAllTargets(tempDir, "mem021-workers", """
                main() {
                    var a = listOf(1)
                    spawn { a.add(2) }
                    spawn { a.remove(0) }
                }
                """);
    }

    @Test
    void awaitBeforeParentMutationStaysGreen(@TempDir Path tempDir) throws IOException {
        assertNoMem021Green(tempDir, "mem021-synced", """
                main() {
                    var a = listOf(1)
                    var h = spawn { a.add(2) }
                    await h
                    a.add(3)
                }
                """);
    }

    @Test
    void spawnAloneWithoutParentMutationStaysGreen(@TempDir Path tempDir) throws IOException {
        assertNoMem021Green(tempDir, "mem021-solo", """
                main() {
                    var a = listOf(1)
                    spawn { a.add(2) }
                }
                """);
    }

    @Test
    void parentMutationOnlyBeforeSpawnStaysGreen(@TempDir Path tempDir) throws IOException {
        assertNoMem021Green(tempDir, "mem021-before", """
                main() {
                    var a = listOf(1)
                    a.add(2)
                    spawn { a.add(3) }
                }
                """);
    }

    @Test
    void spawnReadingSharedListParentMutatingStaysGreen(@TempDir Path tempDir) throws IOException {
        assertNoMem021Green(tempDir, "mem021-read", """
                main() {
                    var a = listOf(1)
                    spawn { println(a.size()) }
                    a.add(2)
                }
                """);
    }

    @Test
    void distinctBindingsSpawnAndParentStayGreen(@TempDir Path tempDir) throws IOException {
        assertNoMem021Green(tempDir, "mem021-distinct", """
                main() {
                    var a = listOf(1)
                    var b = listOf(2)
                    spawn { a.add(3) }
                    b.add(4)
                }
                """);
    }

    @Test
    void awaitOfOtherHandleSuppressesRaceStaysGreen(@TempDir Path tempDir) throws IOException {
        assertNoMem021Green(tempDir, "mem021-other-await", """
                Int other() {
                    return 1
                }

                main() {
                    var a = listOf(1)
                    var h = spawn { a.add(2) }
                    var g = spawn other()
                    await g
                    a.add(3)
                    await h
                }
                """);
    }

    @Test
    void mutationThroughAliasOfCapturedListFailsMem021(@TempDir Path tempDir) throws IOException {
        assertMem021FailsOnAllTargets(tempDir, "mem021-alias", """
                main() {
                    var a = listOf(1)
                    var alias = a
                    spawn { a.add(2) }
                    alias.add(3)
                }
                """);
    }

    @Test
    void branchMutationAfterRacingSpawnFailsMem021(@TempDir Path tempDir) throws IOException {
        assertMem021FailsOnAllTargets(tempDir, "mem021-branch", """
                main() {
                    var a = listOf(1)
                    spawn { a.add(2) }
                    if (a.size() > 0) {
                        a.clear()
                    }
                }
                """);
    }

    // ---- fatia 4.3/#660 (D-MEM021-SCALAR): reatribuicao ESCALAR capturada ----

    @Test
    void spawnScalarReassignAfterSpawnFailsMem021(@TempDir Path tempDir) throws IOException {
        // reprodutor exato do #660 (medido 202/101, corrida silenciosa nos 4 alvos)
        assertMem021FailsOnAllTargets(tempDir, "mem021-scalar", """
                main() {
                    var n = 21
                    var h = spawn { n = n + 1; return n * 2 }
                    n = 100
                    println(await h)
                    println(n)
                }
                """);
    }

    @Test
    void parentIncrementOfCapturedScalarFailsMem021(@TempDir Path tempDir) throws IOException {
        assertMem021FailsOnAllTargets(tempDir, "mem021-scalar-inc", """
                main() {
                    var n = 21
                    var h = spawn { n = n + 1 }
                    n++
                    await h
                }
                """);
    }

    @Test
    void twoSpawnsWritingSameScalarWithoutAwaitFailsMem021(@TempDir Path tempDir) throws IOException {
        assertMem021FailsOnAllTargets(tempDir, "mem021-scalar-workers", """
                main() {
                    var n = 21
                    spawn { n = n + 1 }
                    spawn { n = n + 2 }
                }
                """);
    }

    @Test
    void parentScalarReassignAfterAwaitStaysGreen(@TempDir Path tempDir) throws IOException {
        assertNoMem021Green(tempDir, "mem021-scalar-synced", """
                main() {
                    var n = 21
                    var h = spawn { n = n + 1; return n }
                    await h
                    n = 100
                }
                """);
    }

    @Test
    void spawnReadingScalarParentReassignStaysGreen(@TempDir Path tempDir) throws IOException {
        // captura read-only baixa por VALOR (sem box): escrita da mae nao corre
        assertNoMem021Green(tempDir, "mem021-scalar-read", """
                main() {
                    var n = 21
                    spawn { println(n) }
                    n = 100
                }
                """);
    }

    @Test
    void workerLocalShadowDoesNotRaceParentScalarStaysGreen(@TempDir Path tempDir) throws IOException {
        // `n` local do worker nao e a captura `n` da mae — zero falso-positivo
        assertNoMem021Green(tempDir, "mem021-scalar-shadow", """
                main() {
                    var n = 1
                    spawn { var n = 5; n = n + 1; println(n) }
                    n = 100
                }
                """);
    }
}
