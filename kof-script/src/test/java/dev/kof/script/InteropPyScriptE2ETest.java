package dev.kof.script;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * X2 — o motor Python do `kof.interop` roda no SCRIPT por PARIDADE DE
 * CONSTRUÇÃO (medido 26/09): o interpretador resolve `kof_process_spawn`/
 * `kof_json_*` por reflexão no MESMO `KofRuntime` gerado (javadoc do
 * KofInterpreter) — o host real é injetado nesse alvo
 * ({@code CompilerInterop.PY_ENGINE_TARGETS}) e o output interpretado é o
 * mesmo do JVM compilado, byte a byte (R5). Os alvos sem a face provada
 * (riscv64/aarch64 — §513; ANDROID/MCU/RISCV32 — R7) recebem o host de
 * recusa e falham com o código nomeado {@code INTEROP005}.
 */
class InteropPyScriptE2ETest {

    @Test
    void pyEngineScriptMatchesJvmGolden() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/python3")),
                "python3 ausente — motor não executável neste host");
        Path tmp = Files.createTempDirectory("kofpyscript");
        Path f = tmp.resolve("p.kf");
        Files.writeString(f, """
                import kof.interop
                main() {
                    var py = KofPy("def sq(n):\\n    return n*n\\ndef hi(n):\\n    return \\"oi \\"+n")
                    println(py.callInt("sq", listOf(5)))
                    println(py.callString("hi", listOf("mel")))
                }
                """);
        var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
        assertTrue(script.success(), "SCRIPT: " + script.stderr());
        assertEquals("25\noi mel", script.stdout().replace("\r\n", "\n").trim(),
                "motor py interpretado == golden JVM");
    }
}
