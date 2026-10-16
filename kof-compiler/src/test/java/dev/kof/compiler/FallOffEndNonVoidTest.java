package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** #837 — função não-void cujo corpo TERMINA em switch/while/for-in/do-while/try
 *  (todos os braços com `return`) ficava SEM a injeção do valor default:
 *  o JVM emitia ireturn/areturn com a pilha vazia (VerifyError: operand stack
 *  underflow), o JS devolvia undefined, o Script lançava. O lowerer considerava
 *  a última op como KofReturn e pulava o defaultValueOp. O corpo DEVE terminar
 *  com o default do tipo de retorno (paridade com `return` vazio), ou recusar
 *  em compile-time — nunca bytecode inválido. */
class FallOffEndNonVoidTest extends SemanticResolutionSupport {

    private void jvmOk(Path tmp, String src, String expected) throws Exception {
        Path f = tmp.resolve("F837.kf");
        Files.writeString(f, src);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.JVM);
        assertTrue(r.success(), "deve compilar: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", tmp.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String os = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "classe deve carregar e rodar (VerifyError pré-fix): " + os);
        assertEquals(expected, os.trim(), "saída");
    }

    @Test
    void bodyEndingInSwitch(@TempDir Path tmp) throws Exception {
        jvmOk(tmp, """
                String describe(Int x) {
                    switch (x) {
                        case 0: return "zero"
                        case 1: return "one"
                    }
                }
                main() { println(describe(1)) }
                """, "one");
    }

    @Test
    void bodyEndingInWhile(@TempDir Path tmp) throws Exception {
        jvmOk(tmp, """
                Int f(Int n) {
                    var i = 0
                    while (i < n) { return 42 }
                }
                main() { println(f(5)) }
                """, "42");
    }

    @Test
    void bodyEndingInTry(@TempDir Path tmp) throws Exception {
        jvmOk(tmp, """
                Int f(Int n) {
                    try { return n } catch (String e) { return 0 }
                }
                main() { println(f(7)) }
                """, "7");
    }
}
