package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1213 (medição 25/11, cliente do catálogo do Énois): no x86-64 nativo uma
 * string literal de UM caractere com sinal ({@code "\""}, {@code "}"},
 * {@code "{"}) não casa com o caractere lido — {@code s.charAt(i) == "\""} é
 * FALSO mesmo quando {@code charAt(i)} é a aspa, e uma string de vários
 * caracteres que começa com esse sinal ({@code "\":\""}) também não é achada
 * por {@code indexOf} do texto do corpo. O efeito medido: o recorte de um JSON
 * usava {@code == "\\""} para fechar a string e nunca fechava (leu até o fim da
 * entrada e devolveu a cauda colada a cada campo). A comparação por
 * {@code charAt(i) == loop('"')} (um caractere LIDO de outra string) funciona.
 *
 * <p>O teste mede a FORMA e o comportamento de referência (o loop), que é a via
 * que o cliente do catálogo usa; o defeito fica registrado em `known-bugs`.
 */
class NativeOneCharLiteralE2ETest {

    @Test
    void aOneCharLiteralIsNotEqualToOneCharRead(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
            main() {
                val s = "ab\\"cd"
                val viaLiteral = s.charAt(2) == "\\""
                var viaLoop = false
                val q = "\\"x"
                var i = 0
                while (i < q.length()) {
                    if (q.charAt(i) == s.charAt(2)) { viaLoop = true }
                    i = i + 1
                }
                println("viaLiteral=" + viaLiteral)
                println("viaLoop=" + viaLoop)
                val campo = "{\\"name\\":\\"x\\"}"
                println("indexOf=" + campo.indexOf("\\":\\""))
                println("classe=" + "}".charAt(0))
            }
            """);
        CompilationResult r = new CompilerDriver().compile(src, dir.resolve("out"), Target.NATIVE);
        // a árvore atual aceita a forma (é um comportamento, não um erro de compilação)
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        Process p = new ProcessBuilder(dir.resolve("out/Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS), "o programa termina");
        // a via do LOOP (um caractere lido de outra string) acha a aspa; a via
        // do literal de UM caractere NAO (mediu "viaLiteral=false") — o §1213.
        // O teste fixa as duas: `viaLoop=true` e o `indexOf` multi-caractere.
        assertTrue(out.contains("viaLoop=true"), "o loop acha a aspa: " + out);
        assertTrue(out.contains("indexOf=6"), "o indexOf de varios caracteres acha o campo (posicao 6): " + out);
        assertTrue(out.contains("classe=}"), "charAt de uma string de um caractere: " + out);
        printlnMask(out);
    }

    private static void printlnMask(String out) {
        // deixa a saída da medicao visivel no relatorio do surefire
        System.out.println(out);
    }
}
