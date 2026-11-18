package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §1134 (16/11, docs/lab): a INVARIANTE DE ALINHAMENTO DE SP — o
 * "alignment-asserting emulator" do critério de fechamento do issue (o
 * hardware aarch64 REAL não está neste host; a asserção estática É o
 * veículo de prova que o critério nomeia). O programa do repro do issue
 * (a classe Box) é compilado para NATIVE_AARCH64 e a saída é asserida:
 * TODO ajuste de sp (sub/add sp, sp, #N) é múltiplo de 16 — o sp nunca
 * fica 8-mod-16 num acesso seguinte (o SIGBUS do issue). O fix POUSADO
 * (NativeAarch64SpAligner, o alargamento sub/add ao múltiplo de 16 +
 * a parametrização de stride dos call-sites cross) deixou a saída na
 * invariante; RED-first medido: o checker pegou 60 violações na saída
 * PRÉ-fix; os faces qemu (o oráculo regra 5) seguem byte-exact.
 */
class NativeAarch64SpAlignmentE2ETest {

    private static final Pattern SP_ADJUST =
            Pattern.compile("^(sub|add)\\s+sp,\\s*sp,\\s*#(\\d+)$");

    private static final String REPRO = """
            class Box {
                Int n = 0
            }
            main() {
                val b = Box()
                b.n = 41
                println(b.n + 1)
            }
            """;

    private static final String FACES = """
            record Point(Int x, Int y)
            main() {
                println(listOf(Point(1, 2)))
                println(math.floor(2.7))
                println(math.min(1.5, 2.5))
            }
            """;

    @TempDir
    Path tmp;

    /** A asserção §1134 sobre o texto asm: todo sub/add sp é mult de 16. */
    static void assertSpAlignment(Path asmFile) throws Exception {
        boolean inMacro = false;
        int violations = 0;
        StringBuilder first = new StringBuilder();
        for (String raw : Files.readAllLines(asmFile)) {
            String s = raw.strip();
            if (s.startsWith(".macro")) { inMacro = true; continue; }
            if (s.startsWith(".endm")) { inMacro = false; continue; }
            if (inMacro) continue;   // os corpos de macro são texto morto
            var m = SP_ADJUST.matcher(s);
            if (m.matches() && Long.parseLong(m.group(2)) % 16 != 0) {
                violations++;
                if (first.isEmpty()) first.append(raw.strip());
            }
        }
        assertEquals(0, violations,
                "§1134: todo ajuste de sp na saída aarch64 tem que ser múltiplo de 16"
                        + " (o SIGBUS em hardware real; o qemu-user não enforcementa);"
                        + " primeira violação: " + first);
    }

    private Path compile(String source, String tag) throws Exception {
        // a property é VISÍVEL para o fork inteiro do surefire: sem o restore
        // ela vaza para os testes seguintes (medido 18/11: o CompilerDriverTest
        // .phaseF via o .s retido e disparou assertions pré-podador — o padrão
        // try/finally é o dos irmãos NativeAbiInvariantsTest/NativePeepholeEffectTest)
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");   // o .s é apagado após o link
        try {
            Files.writeString(tmp.resolve(tag + ".kf"), source);
            Path out = tmp.resolve("out-" + tag);
            CompilerDriver driver = new CompilerDriver();
            CompilationResult r = driver.compile(tmp.resolve(tag + ".kf"), out, Target.NATIVE_AARCH64);
            assertTrue(r.success(), "aarch64 compile (" + tag + "): " + r.diagnostics().getDiagnostics());
            return out.resolve("Default/Main.s");
        } finally {
            if (priorKeep == null) System.clearProperty("KOF_KEEP_ASM");
            else System.setProperty("KOF_KEEP_ASM", priorKeep);
        }
    }

    @Test
    void reproSpIsAligned(@TempDir Path ignored) throws Exception {
        assertSpAlignment(compile(REPRO, "repro"));
    }

    @Test
    void crossFacesSpIsAligned(@TempDir Path ignored) throws Exception {
        assertSpAlignment(compile(FACES, "faces"));
    }
}
