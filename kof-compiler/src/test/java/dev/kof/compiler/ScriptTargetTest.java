package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 2 (plataforma) — Target.SCRIPT: o coringa de execução.
 * - compilar para SCRIPT falha com COMP003 (nunca fallback silencioso, R6).
 * - interpret() executa a IR no mesmo frontend (paridade por construção).
 * - resolveModuleRoot descobre kof.toml (integração com a Fase 1).
 */
class ScriptTargetTest {
    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path dir, String rel, String content) throws IOException {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
        return f;
    }

    @Test
    void scriptIsATarget() {
        assertNotNull(Target.SCRIPT);
        assertTrue(Target.SCRIPT.isScript());
        assertFalse(Target.SCRIPT.isNative());
    }

    @Test
    void compileToScriptIsDiagnosed(@TempDir Path tmp) throws IOException {
        Path main = write(tmp, "Main.kf", """
                main() {
                    println("oi")
                }
                """);
        CompilationResult r = driver.compile(main, tmp.resolve("out"), Target.SCRIPT);
        assertFalse(r.success(), "compilar para script deve falhar (não emite artefato)");
        boolean comp003 = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "COMP003".equals(d.code()));
        assertTrue(comp003, "esperava COMP003, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void interpretRunsDirectly(@TempDir Path tmp) throws IOException {
        Path main = write(tmp, "Main.kf", """
                main() {
                    println(6 * 7)
                }
                """);
        KofInterpreter.Result ir = driver.interpret(List.of(main), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "stderr: " + ir.stderr());
        assertTrue(ir.stdout().contains("42"), "stdout: " + ir.stdout());
    }

    @Test
    void interpretSeesProjectRootImports(@TempDir Path tmp) throws IOException {
        // integração F1+F2: kof.toml na raiz, import cross-directory,
        // execução interpretada (sem compilar)
        write(tmp, "kof.toml", "[project]\nname = \"s\"\n");
        write(tmp, "shared/Validation.kf", """
                package shared

                Int min(Int x, Int y) {
                    if (x < y) { return x }
                    return y
                }
                """);
        Path main = write(tmp, "src/Main.kf", """
                import shared.Validation

                main() {
                    println(min(3, 7))
                }
                """);
        Path root = driver.resolveModuleRoot(List.of(main));
        assertEquals(tmp.toAbsolutePath().normalize(), root.toAbsolutePath().normalize(),
                "resolveModuleRoot deve descobrir a raiz do projeto (kof.toml)");
        KofInterpreter.Result ir = driver.interpret(List.of(main), root, new String[0]);
        assertEquals(0, ir.exitCode(), "stderr: " + ir.stderr());
        assertTrue(ir.stdout().contains("3"), "stdout: " + ir.stdout());
    }

    // issue #54 — interp `super(v)` explícito em classe de domínio dava
    // StackOverflowError (o dispatch resolveu o <init> da MESMA classe →
    // recursão). JVM/JS ok. Agora <init> usa o ownerType ESTÁTICO do IR e
    // KofCallKind.SUPER sobe p/ a superclasse (duas lanes, caminhos do
    // lowering: CONSTRUCTOR-owner=super e SUPER-método).
    @Test
    void interpretExplicitSuperConstructor(@TempDir Path tmp) throws IOException {
        Path main = write(tmp, "Main.kf", """
                class Base {
                    Int v
                    public constructor(Int v) {
                        this.v = v
                    }
                }
                class Derived extends Base {
                    public constructor(Int v) {
                        super(v)
                        println(this.v)
                    }
                }
                main() {
                    Derived(42)
                }
                """);
        KofInterpreter.Result ir = driver.interpret(List.of(main), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "stderr: " + ir.stderr());
        assertTrue(ir.stdout().contains("42"), "stdout: " + ir.stdout());
    }

    // #54 — variante: `d.get()` herda de Base (prova o dispatch NÃO-virtual
    // do ctor e o método virtual normal coexistindo).
    @Test
    void explicitSuperConstructorDoesNotRecurse(@TempDir Path tmp) throws IOException {
        Path main = write(tmp, "Main.kf", """
                class Base {
                    Int v
                    constructor(Int v) { this.v = v }
                    Int get() { return v }
                }
                class Derived extends Base {
                    constructor(Int v) {
                        super(v)
                    }
                }
                main() {
                    var d = Derived(42)
                    println(d.get())
                }
                """);
        KofInterpreter.Result ir = driver.interpret(List.of(main), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "stderr: " + ir.stderr());
        assertTrue(ir.stdout().contains("42"), "super(v) deve subir p/ o ctor da superclasse: " + ir.stdout());
    }

    @Test
    void recordWithExplicitConstructorRunsOnInterpreter(@TempDir Path tmp) throws IOException {
        // par com #53 (metade script): o ctor canônico é o EXPLÍCITO, e o
        // super() sintético de Record não tem runtime no interpretador.
        Path main = write(tmp, "Main.kf", """
                record Q(Int x) {
                    constructor(Int x) {
                        this.x = x
                    }
                }
                main() {
                    println(Q(3).x())
                }
                """);
        KofInterpreter.Result ir = driver.interpret(List.of(main), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "stderr: " + ir.stderr());
        assertTrue(ir.stdout().contains("3"), "stdout: " + ir.stdout());
    }
}
