package dev.kof.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Paridade interpretador (Target.SCRIPT) × JVM compilado da stdlib nova da
 * lane STDLIB (S10/S11/S12/S12b/S3b-ext/S7-ext). O interpretador resolve
 * kof_* por reflexão no MESMO KofRuntime gerado (KofInterpreter javadoc:
 * "paridade por construção, não por reimplementação") — este teste PROVA a
 * afirmação nos alvos recém-adicionados, onde regressão seria silenciosa
 * (R5 cross-target). Funções não-determinísticas (random) entram pela
 * FACHADA (saída formatada), não pelo valor sorteado.
 */
class KofScriptStdlibParityTest {

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    private void parity(String src, String expected) throws Exception {
        Path tmp = Files.createTempDirectory("kofparity");
        try {
            Path f = tmp.resolve("P-" + System.nanoTime() + ".kf");
            Files.writeString(f, src);
            var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
            assertTrue(script.success(), "SCRIPT: " + script.stderr());
            assertEquals(expected, norm(script.stdout()), "SCRIPT stdout");
            var comp = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
            assertTrue(comp.success(), "JVM: " + comp.stderr());
            assertEquals(expected, norm(comp.stdout()),
                    "paridade interpretado vs compilado JVM (R5)");
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void uncapitalizeParity() throws Exception {
        parity("""
            main() {
                println(strings.uncapitalize("Hello World"))
                println(strings.uncapitalize("HELLO"))
                println(strings.uncapitalize("1abc"))
                println(strings.uncapitalize("") + "|")
            }
            """, "hello World\nhELLO\n1abc\n|");
    }

    @Test
    void formatDocumentsParity() throws Exception {
        parity("""
            main() {
                println(validation.formatCpf("52998224725"))
                println(validation.formatCpf("123") + "|")
                println(validation.formatCep("01310100"))
                println(validation.formatCnpj("34546401000163"))
                println(validation.formatCnpj("123") + "|")
            }
            """, "529.982.247-25\n123|\n01310-100\n34.546.401/0001-63\n123|");
    }

    @Test
    void isUuidParity() throws Exception {
        parity("""
            main() {
                println(uuid.isUuid("550e8400-e29b-41d4-a716-446655440000"))
                println(uuid.isUuid("550E8400-E29B-41D4-A716-446655440000"))
                println(uuid.isUuid("550e8400e29b41d4a716446655440000"))
                println(uuid.isUuid("550e8400-e29b-41d4-a716-44665544000g"))
                println(uuid.isUuid(uuid.v4()))
            }
            """, "true\ntrue\nfalse\nfalse\ntrue");
    }

    @Test
    void isWeekendParity() throws Exception {
        parity("""
            main() {
                println(time.isWeekend(2026, 9, 12))
                println(time.isWeekend(2026, 9, 13))
                println(time.isWeekend(2026, 9, 9))
                println(time.isWeekend(2026, 2, 30))
                println(time.isWeekend(2024, 2, 25))
            }
            """, "true\ntrue\nfalse\nfalse\ntrue");
    }

    @Test
    void randomFacadeParity() throws Exception {
        // Não-determinístico: valida a FACHADA (formato/contrato), não o
        // valor sorteado. randomInt(1) == 0 travado; randomString(-1) == "";
        // randomBoolean() ∈ {true,false}; randomString(8, "ab") é 8 chars
        // de {a,b}.
        parity("""
            main() {
                println(random.randomInt(1))
                println(random.randomString(-1, "abc") + "|")
                println(random.randomString(0, "abc") + "|")
                println(random.randomString(4, "") + "|")
                var b = random.randomBoolean()
                println(b || !b)
                var s = random.randomString(8, "ab")
                println(s.length)
                println(s == s)
            }
            """, "0\n|\n|\n|\ntrue\n8\ntrue");
    }

    @Test
    void indentDedentParity() throws Exception {
        parity("""
            main() {
                println("---")
                println(strings.indent("a\\nb", 2))
                println(strings.indent("x", 0))
                println(strings.dedent("  a\\n    b"))
                println(strings.dedent("hello"))
            }
            """, "---\n  a\n  b\nx\na\n  b\nhello");
    }

    private static void deleteRecursively(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignore) {}
            });
        } catch (Exception ignore) {}
    }
}
