package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * known-bugs §1192 (adendo 23/11) — um `extern` de lib DO APP por caminho
 * ABSOLUTO não chega ao ld: o símbolo sai como "referência não definida".
 *
 * <p>Medido na troca do app para `enois-kof-desktop`: um `extern
 * "/tmp/…/libkof_text.so"` no fonte que NÃO mora num módulo importado (o
 * `Demo.kf` do app) falha o link, enquanto o mesmo extern dentro de uma lib
 * importada (`enois.Text`) liga. O teste fixa o caso: um extern absoluto no
 * módulo principal tem de aparecer no link (com `-L` do diretório), como já
 * acontece para os que moram nas libs.
 */
class AbsolutePathExternLinkE2ETest {

    @Test
    void anAbsolutePathExternInTheMainModuleReachesTheLinker(@TempDir Path dir) throws Exception {
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld", "gcc"), "toolchain ausente");
        Path shimDir = Files.createTempDirectory("kof-extern");
        Path src = shimDir.resolve("probe.c");
        Path so = shimDir.resolve("libkof_extern_probe.so");
        Files.writeString(src, "int kof_extern_probe(void) { return 42; }\n", StandardCharsets.UTF_8);
        var gcc = new ProcessBuilder("gcc", "-shared", "-fPIC", "-o", so.toString(), src.toString())
                .redirectErrorStream(true).start();
        assertTrue(gcc.waitFor(60, TimeUnit.SECONDS) && gcc.exitValue() == 0,
                "gcc: " + new String(gcc.getInputStream().readAllBytes()));

        String code = "extern \"" + so + "\" kof_extern_probe(): Int\n"
                + "\n"
                + "main() {\n"
                + "    println(\"probe=\" + kof_extern_probe())\n"
                + "}\n";
        Files.writeString(dir.resolve("Main.kf"), code, StandardCharsets.UTF_8);

        var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        assertTrue(out.contains("probe=42"), "o extern absoluto roda: " + out);
    }
}
