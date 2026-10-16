package dev.kof.compiler.js;

import dev.kof.compiler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** #836 — uma classe com parâmetros de construtor que `extends` uma base
 *  perdia o ACESSOR no JS: o backing field (sem prefixo `_`) era emitido como
 *  `this.r = <default>` no construtor, SOBRESCREVENDO o método `r()` com um
 *  número → `obj.r()` virava `TypeError: obj.r is not a function`. JVM/Native/
 *  Script tinham o acessor. O predicado de record-ness (recordClassNames) tem
 *  de valer também para o nome do backing field. */
class JsClassExtendsAccessorTest {

    @Test
    void ctorParamAccessorSurvivesExtendsOnJs(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("M.kf");
        Files.writeString(src, """
                class Shape
                class Circle(Double r) extends Shape
                main() {
                    var c = Circle(2.0)
                    println(c.r())
                }
                """);
        Path out = tmp.resolve("out");
        var r = new CompilerDriver().compile(src, out, Target.JS);
        assertTrue(r.success(), "compile JS: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("node", out.resolve("Default.mjs").toString())
                .redirectErrorStream(true).start();
        String os = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "node timeout");
        assertEquals(0, p.exitValue(), "JS deve rodar (o acessor r() existe): " + os);
        assertEquals("2.0", os.trim(), "c.r() deve ser o acessor, não o campo");
    }
}
