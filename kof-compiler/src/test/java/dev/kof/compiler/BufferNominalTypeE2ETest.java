package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §1195 — `Buffer` as a NOMINAL TYPE: a method parameter, a class FIELD and a
 * return all compile now, and the buffer operations work on a value that
 * crossed a call boundary.
 *
 * <p>Before the fix (`isBuiltinTypeName` lacked `Buffer`, and `Type.of` did not
 * map the declared name), all three shapes failed with SEM011
 * (`Undefined variable or type: 'Buffer'`), and even after the name resolved,
 * `b.size()` compiled to a call to a nonexistent `Buffer_size` — the declared
 * name and the `buffer.alloc` result were DIFFERENT types.
 *
 * <p>RED-first: the first assertion below is exactly the program that failed
 * with SEM011 before the fix.
 */
class BufferNominalTypeE2ETest {

    @Test
    void bufferCrossesAMethodBoundaryAndItsPixelsAreWritable(@TempDir Path dir) throws Exception {
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Files.writeString(dir.resolve("Main.kf"), """
                class Mask {
                    // §1195: o PARAMETRO e' `Buffer` (antes: SEM011)
                    Int round(Buffer b, Int w, Int h) {
                        var cut = 0
                        val cx = w / 2
                        val cy = h / 2
                        val r2 = w * w / 4
                        var y = 0
                        while (y < h) {
                            val dy = y - cy
                            var x = 0
                            while (x < w) {
                                val dx = x - cx
                                if (dx * dx + dy * dy > r2) {
                                    buffer.poke8(b, (y * w + x) * 4 + 3, 0)
                                    cut = cut + 1
                                }
                                x = x + 1
                            }
                            y = y + 1
                        }
                        return cut
                    }
                }
                main() {
                    val b = buffer.alloc(16 * 16 * 4)
                    var i = 0
                    while (i < 256) {
                        buffer.poke8(b, i * 4 + 3, 255)
                        i = i + 1
                    }
                    val cut = Mask().round(b, 16, 16)
                    val bytes = b.bytes()
                    println("cut=" + cut)
                    println("canto=" + (bytes[3] & 255))
                    println("centro=" + (bytes[(8 * 16 + 8) * 4 + 3] & 255))
                }
                """, StandardCharsets.UTF_8);
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());

        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        // 256 texels, o circulo cobre pi*r^2 = 201 -> ~55 cortados
        var m = java.util.regex.Pattern.compile("cut=(\\d+)").matcher(out);
        assertTrue(m.find(), "o recorte mede: " + out);
        int cut = Integer.parseInt(m.group(1));
        assertTrue(cut > 30 && cut < 90, "o circulo corta ~55 de 256: " + cut);
        assertTrue(out.contains("canto=0"), "o canto FOI cortado (alfa 0): " + out);
        assertTrue(out.contains("centro=255"), "o centro ficou opaco: " + out);
    }

    @Test
    void bufferIsAValidFieldAndParameterType(@TempDir Path dir) throws Exception {
        // a FORMA do tipo: campo + parametro + uso, sem tocar em pixel
        Files.writeString(dir.resolve("Main.kf"), """
                class Holder {
                    Buffer buf
                    constructor(Buffer b) { this.buf = b }
                    Int first(Buffer b) { return buffer.peek8(b, 0) }
                }
                main() {
                    val x = buffer.alloc(4)
                    buffer.poke8(x, 0, 42)
                    val h = Holder(x)
                    println("campo=" + buffer.peek8(h.buf, 0))
                    println("param=" + h.first(x))
                }
                """, StandardCharsets.UTF_8);
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "campo/parametro/ctor de Buffer: " + r.diagnostics().getDiagnostics());
    }
}
