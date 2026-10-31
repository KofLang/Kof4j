package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §362/#545 — phantom de aridade na construcao IMPLICITA {@code Class(args)}
 * (sem {@code new}). Antes do gate, {@code P(1,2)} em {@code record P(Int x)}
 * compilava "clean" e morria em {@code NoSuchMethodError} no JVM; o Script
 * imprimia phantom ({@code P[x=null]}) e o JS nem emitia Main.js. O caminho
 * {@code new} ja dava SEM023; a face implicita caia em silencio (R6 violada).
 * Gate: {@code ImplicitCtorCallTyper.reportNoCtorArity} — mesmo SEM023, mesmas
 * excecoes (classe sem construtor declarado = default implicito legal).
 */
public class ConstructorPhantomE2ETest {

    @TempDir
    Path dir;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compileJvm(String source) throws Exception {
        Path f = dir.resolve("Test.kf");
        Files.writeString(f, source);
        return driver.compile(f, dir.resolve("out"), Target.JVM);
    }

    private boolean hasCode(CompilationResult r, String code) {
        return r.diagnostics().getDiagnostics().stream().anyMatch(d -> code.equals(d.code()));
    }

    private String message(CompilationResult r, String code) {
        return r.diagnostics().getDiagnostics().stream()
                .filter(d -> code.equals(d.code())).map(Diagnostic::message)
                .findFirst().orElse("");
    }

    @Test
    void implicitRecordArityRejected() throws Exception {
        CompilationResult r = compileJvm("record P(Int x)\n"
                + "main() { var p = P(1, 2); println(p) }\n");
        assertFalse(r.success(),
                "§362: P(1,2) em record P(Int x) nao pode compilar — phantom <init>(int,int) no load");
        assertTrue(hasCode(r, "SEM023"), "diagnostico deve ser SEM023, nao outro");
        assertTrue(message(r, "SEM023").contains("no constructor of 'P' with 2 argument(s) (expected 1)"),
                "mensagem deve nomear aridade chamada e esperada, got: " + message(r, "SEM023"));
    }

    @Test
    void implicitClassTooManyArgsRejected() throws Exception {
        CompilationResult r = compileJvm("class C { Int n\n"
                + "  public constructor(Int n) { this.n = n } }\n"
                + "main() { var x = C(1, 2); println(x.n) }\n");
        assertFalse(r.success(),
                "§362: C(1,2) em ctor(Int) nao pode compilar — ld undefined reference C_init_1 no Native");
        assertTrue(hasCode(r, "SEM023"), "got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void implicitClassTooFewArgsRejected() throws Exception {
        CompilationResult r = compileJvm("class C { Int n\n"
                + "  public constructor(Int n) { this.n = n } }\n"
                + "main() { var x = C(); println(42) }\n");
        assertFalse(r.success(),
                "§362: C() em ctor(Int) nao pode compilar — phantom <init>()V");
        assertTrue(hasCode(r, "SEM023"), "got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void jsAndScriptFrontendsRejectIdentically() throws Exception {
        Path f = dir.resolve("Multi.kf");
        Files.writeString(f, "record P(Int x)\n"
                + "main() { var p = P(1, 2); println(p) }\n");
        CompilationResult js = driver.compile(f, dir.resolve("ojs"), Target.JS);
        assertFalse(js.success(), "§362: JS nao pode aceitar o phantom (divergia do JVM)");
        assertTrue(hasCode(js, "SEM023"), "§362: JS deve reportar SEM023, got: "
                + js.diagnostics().getDiagnostics());
        // Script (KofInterpreter) roda o MESMO SemanticAnalyzer; o
        // interpretador imprimia phantom (rc=0, "P[x=null]"). Post-gate lanca
        // KofInterpretException com o SEM023 (nunca saida silenciosa, R6).
        KofInterpretException ex = assertThrows(KofInterpretException.class,
                () -> driver.interpret(List.of(f), dir, new String[0]));
        assertTrue(ex.getMessage().contains("no constructor of 'P'"),
                "§362: Script deve falhar com o SEM023, got: " + ex.getMessage());
    }

    @Test
    void defaultImplicitConstructorStillLegal() throws Exception {
        CompilationResult r = compileJvm("class Z { }\nmain() { var z = Z(); println(1) }\n");
        assertTrue(r.success(),
                "controle: classe sem ctor declarado + Z() = default implicito legal, got: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void matchingArityStillLegal() throws Exception {
        CompilationResult r = compileJvm("record P(Int x)\n"
                + "class C { Int n\n  public constructor(Int n) { this.n = n } }\n"
                + "main() { var p = P(1); var c = C(2); println(p); println(c.n) }\n");
        assertTrue(r.success(),
                "controle: aridade casada nao pode ser rejeitada, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void overloadSelectionStillLegal() throws Exception {
        CompilationResult r = compileJvm("class C { Int n\n"
                + "  public constructor(Int n) { this.n = n }\n"
                + "  public constructor(Int n, Int m) { this.n = n + m } }\n"
                + "main() { var a = C(1); var b = C(1, 2); println(a.n); println(b.n) }\n");
        assertTrue(r.success(),
                "controle: overload por aridade deve continuar resolvendo, got: "
                        + r.diagnostics().getDiagnostics());
    }

    // Segundo sítio (ImplicitCtorCallTyper.inferTail): uma chamada COM receptor
    // cujo método casa o nome de uma classe chega à resolução de construtor.
    private static final String FOO = "class Foo { Int x\n"
            + "  public constructor(Int x) { this.x = x } }\n";

    @Test
    void receiverCallNamedLikeAClassKeepsTheArityDiagnostic() throws Exception {
        CompilationResult r = compileJvm(FOO + "main() { var f = Foo.Foo(1, 2) }\n");
        assertFalse(r.success(), "got: " + r.diagnostics().getDiagnostics());
        assertTrue(message(r, "SEM023").contains("no constructor of 'Foo' with 2 argument(s) (expected 1)"),
                "got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void receiverCallNamedLikeAClassKeepsTheArgumentTypeDiagnostic() throws Exception {
        CompilationResult r = compileJvm(FOO + "main() { var f = Foo.Foo(\"x\") }\n");
        assertFalse(r.success(), "got: " + r.diagnostics().getDiagnostics());
        assertTrue(message(r, "SEM014").contains("Argument 1 of 'Foo': expected Int but got String"),
                "got: " + r.diagnostics().getDiagnostics());
    }
}
