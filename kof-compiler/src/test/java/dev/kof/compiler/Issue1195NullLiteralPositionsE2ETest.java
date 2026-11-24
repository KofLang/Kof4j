package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #1195 — SG-008 says NO {@code null} literal is assignable, not even to
 * {@code T?}. SEM048 enforced that in a variable declaration and in an
 * assignment only; a function ARGUMENT, a record field in the constructor, a
 * {@code return null} and a collection element all passed {@code kof check}
 * and died with a NullPointerException at run.
 *
 * <p>Pins the four positions the issue measured (argument, record constructor,
 * return, {@code listOf}/{@code mapOf}) plus the legitimate shapes that must
 * stay green: a {@code T?} PARAMETER accepts an absent value from an API, and
 * narrowing keeps working.
 */
class Issue1195NullLiteralPositionsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path dir, String tag, String source) throws IOException {
        Path src = dir.resolve(tag + ".kf");
        Files.writeString(src, source);
        return driver.compile(src, dir.resolve("out-" + tag), Target.JVM);
    }

    private void assertNullLiteralRejected(Path dir, String tag, String source) throws IOException {
        CompilationResult r = compile(dir, tag, source);
        assertFalse(r.success(), tag + ": null literal tem de ser recusado — passou: "
                + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM048".equals(d.code())),
                tag + ": esperava SEM048, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nullArgumentToReferenceParameterIsRejected(@TempDir Path dir) throws IOException {
        assertNullLiteralRejected(dir, "arg", """
                class U {
                    static String eco(String texto) { return texto }
                }
                main() {
                    val a = U.eco(null)
                    println(a.length())
                }
                """);
    }

    @Test
    void nullRecordFieldIsRejected(@TempDir Path dir) throws IOException {
        assertNullLiteralRejected(dir, "rec", """
                record Par(String nome)
                main() {
                    val p = Par(null)
                    println(p.nome)
                }
                """);
    }

    /**
     * The issue listed {@code return null} as a fifth position. Measured, it is
     * FROZEN SEMANTICS, not a defect: {@code String? maybe() { return null }}
     * is the canonical nullable idiom and the whole {@code NullSafetyE2ETest}
     * battery runs it on four targets (§125). A guard here turned 7 of that
     * battery's cases red. So this case is pinned as MUST-STAY-GREEN.
     */
    @Test
    void nullReturnFromANullableFunctionStaysGreen(@TempDir Path dir) throws IOException {
        CompilationResult r = compile(dir, "retok", """
                String? maybe(Bool yes) {
                    if (yes) return "abc"
                    return null
                }
                main() {
                    var s = maybe(false)
                    if (s != null) { println(s.length) }
                    println("ok")
                }
                """);
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .noneMatch(d -> "SEM048".equals(d.code())),
                "`return null` de T? e o idioma congelado (SG-008 fala de atribuicao,"
                        + " nao de retorno): " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nullListAndMapElementsAreRejected(@TempDir Path dir) throws IOException {
        assertNullLiteralRejected(dir, "lst", """
                main() {
                    val xs = listOf<String>("a", null)
                    println(xs.size)
                }
                """);
        assertNullLiteralRejected(dir, "map", """
                main() {
                    val m = mapOf<String, String>("a", null)
                    println(m.size)
                }
                """);
    }

    /**
     * Contraprova de escopo: um dono KOF sempre e' julgado. (A face externa —
     * {@code super.onCreate(null)} num override cuja assinatura vem do
     * classpath — e' coberta por {@code AndroidInteropE2ETest}, que exige
     * compilar: uma assinatura externa nao tem como declarar {@code T?}.)
     */
    @Test
    void nullOnAForeignParameterIsNotJudged(@TempDir Path dir) throws IOException {
        CompilationResult r = compile(dir, "ext", """
                class Flutuador {
                    static String desenha(String rotulo) { return rotulo }
                }
                main() {
                    println(Flutuador.desenha(null))
                }
                """);
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM048".equals(d.code())),
                "dono Kof: o guard julga (contraprova do caso exterior): "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void theLegitimateShapesStayGreen(@TempDir Path dir) throws IOException {
        CompilationResult r = compile(dir, "ok", """
                record Par(String nome)

                class U {
                    static String eco(String? texto) { return "x" }
                }

                main() {
                    val p = Par("ana")
                    val xs = listOf<String>("a", "b")
                    val m = mapOf<String, Int>("a", 1)
                    println(U.eco(null))
                    println(p.nome)
                    val n: String? = "y"
                    if (n != null) { println(n.length) }
                    println(xs.size + m.size)
                }
                """);
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .noneMatch(d -> "SEM048".equals(d.code())),
                "T? em PARAMETRO recebe ausente de API e o narrowing segue valido: "
                        + r.diagnostics().getDiagnostics());
    }
}
