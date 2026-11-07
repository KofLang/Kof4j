package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §280 — os 91 sítios `error("", 0, 0, 0, …)` emitiam SEM/PKG com
 * posição ausente (o usuário via `:0:0`). Cada site com AST node em escopo
 * agora reporta o file/line/column do node; este teste prova (Q0/Q3) que os
 * repros canônicos reportam line >= 1 AND column >= 1, nunca o 0:0 fantasma.
 */
class DiagnosticSourceLocationTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(@TempDir Path tmp, String name, String src) throws IOException {
        Path source = tmp.resolve(name);
        Files.writeString(source, src);
        return driver.compile(source, tmp.resolve("out"), Target.JVM);
    }

    private Optional<Diagnostic> first(CompilationResult r, String code) {
        return r.diagnostics().getDiagnostics().stream()
                .filter(d -> code.equals(d.code()))
                .findFirst();
    }

    private void assertCodeAtSource(CompilationResult r, String code, String snippet) {
        assertFalse(r.success(), "deve falhar: " + snippet);
        Optional<Diagnostic> d = first(r, code);
        assertTrue(d.isPresent(), "esperava " + code + " em: " + snippet
                + ", foi: " + r.diagnostics().getDiagnostics());
        assertTrue(d.get().line() >= 1 && d.get().column() >= 1,
                code + " deve apontar posição real (line>=1, column>=1); veio line="
                        + d.get().line() + " column=" + d.get().column()
                        + " em: " + r.diagnostics().getDiagnostics());
    }

    // (a) `Int f() { return "x" }` → SEM010 com a posição do `return`.
    @Test
    void sem010ReturnTypeMismatchReportsSourceLocation(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "A010.kf", """
                Int dsq010() { return "x" }
                main() { println(1) }
                """);
        assertCodeAtSource(r, "SEM010", "retorno String em função Int");
        assertTrue(first(r, "SEM010").get().message().contains("expected 'Int' but got 'String'"),
                "mensagem SEM010 inesperada: " + r.diagnostics().getDiagnostics());
    }

    // (b) `show(String)` chamado com `42` → SEM014 (argumento de função top-level).
    @Test
    void sem014WrongArgumentTypeReportsSourceLocation(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "B014.kf", """
                void dsq014show(String s) { println(s) }
                main() { dsq014show(42) }
                """);
        assertCodeAtSource(r, "SEM014", "argumento Int em parâmetro String");
        assertTrue(first(r, "SEM014").get().message().contains("expected 'String' but got 'Int'"),
                "mensagem SEM014 inesperada: " + r.diagnostics().getDiagnostics());
    }

    // (c) escrita em `val` → SEM037 com a posição do alvo.
    @Test
    void sem037AssignmentToImmutableValReportsSourceLocation(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "C037.kf", """
                main() {
                    val dsq037 = 1
                    dsq037 = 2
                    println(dsq037)
                }
                """);
        assertCodeAtSource(r, "SEM037", "escrita em val");
        assertTrue(first(r, "SEM037").get().message().contains("'dsq037'"),
                "mensagem SEM037 inesperada: " + r.diagnostics().getDiagnostics());
    }

    // (d) família SEM012/SEM025/SEM041 — instantiation de classe abstrata (SEM041)
    // com a posição do `new`.
    @Test
    void sem041AbstractClassInstantiationReportsSourceLocation(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "D041.kf", """
                abstract class Dsq41 {
                    Int area() { return 0 }
                }
                main() {
                    var z = new Dsq41()
                    println(z)
                }
                """);
        assertCodeAtSource(r, "SEM041", "new de classe abstrata");
        assertTrue(first(r, "SEM041").get().message().contains("abstract class 'Dsq41'"),
                "mensagem SEM041 inesperada: " + r.diagnostics().getDiagnostics());
    }

    // (e) sites de chamada de membro (MemberCallTyper): SEM072 (aridade do
    // `List.add`) e SEM025 (método inexistente em classe do módulo) — ambos com
    // a posição do call-site.
    @Test
    void memberCallTypeErrorSitesReportSourceLocation(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "E072.kf", """
                main() {
                    var dsq72 = listOf(1)
                    dsq72.add(9, 8)
                    println(dsq72.size())
                }
                """);
        assertCodeAtSource(r, "SEM072", "add com dois argumentos");

        CompilationResult r2 = compile(tmp, "E025.kf", """
                class Pdsq25 {
                    Int a
                }
                main() {
                    var p = Pdsq25()
                    p.naoExisteDsq25()
                }
                """);
        assertCodeAtSource(r2, "SEM025", "método inexistente em classe conhecida");
    }
}
