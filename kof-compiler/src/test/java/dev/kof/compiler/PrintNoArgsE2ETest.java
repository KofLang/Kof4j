package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #495 — {@code println()} sem argumentos compilava silencioso e morria em
 * runtime com {@code NoSuchMethodError: Default.Main.println()}: o builtin
 * era aceito com 0 args pelo typer e caia no emissor generico de metodo
 * ({@link ExpressionPrintLowerer} so trata arity 1). Decisao da mantenedora
 * no issue: "empty println should not compile" — SEM096 em tempo de
 * compilacao (R6: diagnostico, nunca falha muda no load).
 *
 * <p>Preservado: {@code println("")} (a linha em branco pedida no corpo do
 * issue — ida em Kof), {@code println(x)} de 1 arg, e metodos do usuario
 * chamados {@code println} com outra aridade NAO sao tocados (a checagem so
 * vale para o builtin sem receiver).
 */
class PrintNoArgsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tmp, String name, String source) throws IOException {
        Path src = tmp.resolve(name + ".kf");
        Files.writeString(src, source);
        return driver.compile(src, tmp.resolve(name + "-out"), Target.JVM);
    }

    @Test
    void printlnNoArgsRejectedWithSem096(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "p1", """
                main() {
                    println()
                    println("done")
                }
                """);
        assertFalse(r.success(), "println() pelado nao compila (#495), nao NoSuchMethodError");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM096") && d.message().contains("println")),
                "SEM096 em println esperado em: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void printNoArgsRejectedWithSem096(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "p2", """
                main() {
                    print()
                }
                """);
        assertFalse(r.success(), "print() pelado e o mesmo crime");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM096")),
                "SEM096: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void printlnEmptyStringStaysLegal(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "p3", """
                main() {
                    println("")
                    println("done")
                }
                """);
        assertTrue(r.success(), "a linha em branco do issue: println(\\\"\\\") — ida e continua compilando: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void printlnOneArgFaceUntouched(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "p4", """
                main() {
                    println("oi")
                    println(42)
                    println(3.5)
                    println(true)
                }
                """);
        assertTrue(r.success(), "arity 1 intocada (4 tipos): " + r.diagnostics().getDiagnostics());
    }

    @Test
    void userMethodNamedPrintlnWithOtherAritiesUntouched(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "p5", """
                class Box {
                    Int n
                    constructor(Int a) { n = a }
                    println(Int a, Int b) {
                        println(a + b)
                    }
                }
                main() {
                    var x = Box(1)
                    x.println(2, 3)
                }
                """);
        assertTrue(r.success(), "metodo do USUARIO com receiver/arity propria nao e o builtin: "
                + r.diagnostics().getDiagnostics());
    }

    // #1122: the body prints with `print`. A `println("...")` inside a user `println()`
    // is the user function (it shadows the builtin, §931) with an extra argument:
    // SEM013 now; before it compiled and recursed until StackOverflowError.
    @Test
    void userZeroArgPrintlnFunctionStillWins(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "p6", """
                void println() {
                    print("user-println-called")
                }
                main() {
                    println()
                }
                """);
        assertTrue(r.success(), "funcao do usuario chamada `println` com 0 args NAO e o builtin "
                + "nao-sombreado (mesma regra do `sleep`) — era legal antes do #495, tem que "
                + "continuar legal (freeze regra 2): " + r.diagnostics().getDiagnostics());
    }
}
