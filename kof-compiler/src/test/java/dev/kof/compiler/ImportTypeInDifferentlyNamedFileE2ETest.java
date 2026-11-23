package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED-first (23/11, §1200, app do Énois): num pacote, o tipo pode morar num arquivo
 * com OUTRO nome (`enois/Communities.kf` declara `CommunitiesScreen`). O import
 * pelo tipo (`import enois.CommunitiesScreen`) tem que achá-lo — medido no app
 * que não acha quando o arquivo tem um vizinho (o pacote do Énois), embora ache
 * quando o arquivo está sozinho.
 */
class ImportTypeInDifferentlyNamedFileE2ETest {

    private static void write(Path dir, String file, String body) throws Exception {
        Path p = dir.resolve(file);
        Files.createDirectories(p.getParent());
        Files.writeString(p, body, StandardCharsets.UTF_8);
    }

    private static CompilationResult compile(Path dir) throws Exception {
        return new CompilerDriver().compileForTests(dir.resolve("Main.kf"), dir.resolve("out"),
                Target.NATIVE, dir);
    }

    /** O caso do app: o tipo num arquivo de outro nome, sozinho no pacote. */
    @Test
    void aTypeInADifferentlyNamedFileIsImportable(@TempDir Path dir) throws Exception {
        write(dir, "loja/Vitrine.kf", """
                package loja

                class Catalogo {
                    static String titulo() { return "Vitrine" }
                }
                """);
        write(dir, "Main.kf", """
                import loja.Catalogo

                main() {
                    println("c=" + Catalogo.titulo())
                }
                """);
        var r = compile(dir);
        assertTrue(r.success(), "o tipo do arquivo de outro nome resolve: "
                + r.diagnostics().getDiagnostics());
    }

    /** O caso do §1200 medido no app: o MESMO tipo, com um vizinho no pacote. */
    @Test
    void aNeighbourInThePackageDoesNotHideTheType(@TempDir Path dir) throws Exception {
        write(dir, "loja/Vitrine.kf", """
                package loja

                class Catalogo {
                    static String titulo() { return "Vitrine" }
                }
                """);
        write(dir, "loja/Helper.kf", """
                package loja

                class Helper {
                    static Int n() { return 1 }
                }
                """);
        write(dir, "Main.kf", """
                import loja.Catalogo

                main() {
                    println("c=" + Catalogo.titulo())
                }
                """);
        var r = compile(dir);
        assertTrue(r.success(), "um vizinho no pacote não esconde o tipo: "
                + r.diagnostics().getDiagnostics());
    }

    /** Um tipo inexistente continua PKG006 (sem falso verde). */
    @Test
    void aMissingTypeStillFails(@TempDir Path dir) throws Exception {
        write(dir, "loja/Vitrine.kf", """
                package loja

                class Catalogo {
                    static String titulo() { return "Vitrine" }
                }
                """);
        write(dir, "Main.kf", """
                import loja.SemNinguem

                main() {
                    println("x")
                }
                """);
        var r = compile(dir);
        assertFalse(r.success(), "tipo inexistente segue erro");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "PKG006".equals(String.valueOf(d.code()))),
                "o diagnóstico continua PKG006: " + r.diagnostics().getDiagnostics());
    }
}
