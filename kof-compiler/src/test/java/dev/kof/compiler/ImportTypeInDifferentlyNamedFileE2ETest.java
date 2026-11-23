package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED-first (23/11, §1200, app do Énois): num pacote, o tipo pode morar num
 * arquivo com OUTRO nome (`enois/Communities.kf` declara `CommunitiesScreen`)
 * e cada arquivo pode declarar VÁRIOS tipos (os widgets que ele usa). O
 * import pelo tipo (`import enois.CommunitiesScreen`) tem que achá-lo.
 *
 * <p>Medido no CLI das fontes (o app): `import enois.X` só resolve com
 * `X.kf` — multi-tipo e nome-diferente dão PKG006, e um `<Tipo>.kf` que
 * declara outra coisa "vende" o import e o erro sai como SEM011.
 */
class ImportTypeInDifferentlyNamedFileE2ETest {

    /** Um arquivo com VÁRIOS tipos: cada um é importável pelo nome do tipo. */
    @Test
    void everyTypeOfAMultiClassFileIsImportable(@TempDir Path dir) throws Exception {
        Path pkg = dir.resolve("loja");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Vitrine.kf"), """
                package loja

                class Catalogo {
                    static String titulo() { return "Vitrine" }
                }

                class Estoque {
                    static Int quantidade() { return 7 }
                }
                """, StandardCharsets.UTF_8);
        Path main = dir.resolve("Main.kf");
        Files.writeString(main, """
                import loja.Catalogo
                import loja.Estoque

                main() {
                    println("q=" + Estoque.quantidade())
                }
                """, StandardCharsets.UTF_8);
        var r = new CompilerDriver().compileForTests(main, dir.resolve("out"), Target.NATIVE, dir);
        assertTrue(r.success(), "os tipos do arquivo de outro nome resolvem: "
                + r.diagnostics().getDiagnostics());
    }

    /** Um arquivo de nome igual que NÃO declara o tipo não pode esconder o que declara. */
    @Test
    void aSameNamedFileWithoutTheTypeDoesNotHideTheRealOne(@TempDir Path dir) throws Exception {
        Path pkg = dir.resolve("loja");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Vitrine.kf"), """
                package loja

                class Catalogo {
                    static String titulo() { return "Vitrine" }
                }

                class Estoque {
                    static Int quantidade() { return 3 }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(pkg.resolve("Estoque.kf"), """
                package loja

                class Outra {
                    static String nada() { return "x" }
                }
                """, StandardCharsets.UTF_8);
        Path main = dir.resolve("Main.kf");
        Files.writeString(main, """
                import loja.Estoque

                main() {
                    println("q=" + Estoque.quantidade())
                }
                """, StandardCharsets.UTF_8);
        var r = new CompilerDriver().compileForTests(main, dir.resolve("out"), Target.NATIVE, dir);
        assertTrue(r.success(), "o tipo do arquivo certo é encontrado: "
                + r.diagnostics().getDiagnostics());
    }

    /** Um tipo inexistente continua PKG006 (sem falso verde). */
    @Test
    void aMissingTypeStillFails(@TempDir Path dir) throws Exception {
        Path pkg = dir.resolve("loja");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Vitrine.kf"), """
                package loja

                class Catalogo {
                    static String titulo() { return "Vitrine" }
                }
                """, StandardCharsets.UTF_8);
        Path main = dir.resolve("Main.kf");
        Files.writeString(main, """
                import loja.SemNinguem

                main() {
                    println("x")
                }
                """, StandardCharsets.UTF_8);
        var r = new CompilerDriver().compileForTests(main, dir.resolve("out"), Target.NATIVE, dir);
        assertFalse(r.success(), "tipo inexistente segue erro");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "PKG006".equals(String.valueOf(d.code()))),
                "o diagnóstico continua PKG006: " + r.diagnostics().getDiagnostics());
    }
}
