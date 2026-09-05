package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pacotes e imports entre pastas: um diretório é um pacote; `import a.b.C`
 * traz tipos de outros diretórios do módulo; métodos estáticos cruzam
 * pacotes via INVOKESTATIC com owner qualificado.
 */
class PackagesE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void multiFileSameDirectory(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("models.kf"), """
            class Usuario {
                String nome
                constructor(String n) {
                    nome = n
                }
            }
            """);
        Files.writeString(tempDir.resolve("app.kf"), """
            main() {
                var u = new Usuario("Ana")
                println("olá " + u.nome)
            }
            """);
        CompilationResult r = driver.compileSources(
                List.of(tempDir.resolve("app.kf"), tempDir.resolve("models.kf")),
                tempDir.resolve("out"), Target.JVM, tempDir);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        assertTrue(Files.exists(tempDir.resolve("out").resolve("Default/Main.class")));
        assertTrue(Files.exists(tempDir.resolve("out").resolve("Usuario.class")));
    }

    @Test
    void crossPackageImportAndStaticCall(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("proj");
        Files.createDirectories(root.resolve("vendas/models"));
        Files.createDirectories(root.resolve("vendas/regras"));
        Files.writeString(root.resolve("main.kf"), """
            import vendas.models.Cliente
            import vendas.regras.Desconto

            main() {
                var c = new Cliente("Ana", 1000.0)
                println(Desconto.aplicar(c))
                println(c.nome)
            }
            """);
        Files.writeString(root.resolve("vendas/models/Cliente.kf"), """
            package vendas.models

            class Cliente {
                String nome
                Float total
                constructor(String n, Float t) {
                    nome = n
                    total = t
                }
            }
            """);
        Files.writeString(root.resolve("vendas/regras/Desconto.kf"), """
            package vendas.regras

            import vendas.models.Cliente

            class Desconto {
                static String aplicar(Cliente c) {
                    if (c.total >= 500.0) {
                        return "cliente " + c.nome + ": 10% off"
                    }
                    return "cliente " + c.nome + ": sem desconto"
                }
            }
            """);
        List<Path> sources = List.of(
                root.resolve("main.kf"),
                root.resolve("vendas/models/Cliente.kf"),
                root.resolve("vendas/regras/Desconto.kf"));
        CompilationResult r = driver.compileSources(sources,
                root.resolve("out"), Target.JVM, root);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());

        // classes nos DIRETÓRIOS de pacote corretos
        assertTrue(Files.exists(root.resolve("out").resolve("vendas/models/Cliente.class")),
                "Cliente em vendas/models/");
        assertTrue(Files.exists(root.resolve("out").resolve("vendas/regras/Desconto.class")),
                "Desconto em vendas/regras/");
    }

    @Test
    void packageMismatchIsDiagnostic(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("proj");
        Files.createDirectories(root.resolve("errado"));
        Files.writeString(root.resolve("errado/X.kf"), """
            package certo

            class X {}
            """);
        List<Path> sources = List.of(root.resolve("errado/X.kf"));
        CompilationResult r = driver.compileSources(sources,
                root.resolve("out"), Target.JVM, root);
        assertFalse(r.success(), "package declarado ≠ diretório deve falhar");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "PKG004".equals(d.code())),
                "gap PKG004 esperado: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void duplicateTypeAcrossPackagesIsAllowed(@TempDir Path tempDir) throws IOException {
        // PKG005 (03/09): nomes simples iguais em pacotes DIFERENTES são
        // válidos (como em Java) — resolução por FQ name. Não é mais erro.
        Files.createDirectories(tempDir.resolve("a"));
        Files.createDirectories(tempDir.resolve("b"));
        Files.writeString(tempDir.resolve("a/Nome.kf"), """
            package a
            class Nome {}
            """);
        Files.writeString(tempDir.resolve("b/Nome.kf"), """
            package b
            class Nome {}
            """);
        List<Path> sources = List.of(
                tempDir.resolve("a/Nome.kf"),
                tempDir.resolve("b/Nome.kf"));
        CompilationResult r = driver.compileSources(sources,
                tempDir.resolve("out"), Target.JVM, tempDir);
        assertTrue(r.success(),
                "mesmo nome simples em pacotes diferentes deve compilar (PKG005): "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void duplicateTypeInSamePackageIsDiagnostic(@TempDir Path tempDir) throws IOException {
        // Colisão REAL: mesmo nome, mesmo pacote, arquivos diferentes.
        Files.createDirectories(tempDir.resolve("a"));
        Files.writeString(tempDir.resolve("a/Nome.kf"), """
            package a
            class Nome {}
            """);
        Files.writeString(tempDir.resolve("a/Outro.kf"), """
            package a
            class Nome {}
            """);
        List<Path> sources = List.of(
                tempDir.resolve("a/Nome.kf"),
                tempDir.resolve("a/Outro.kf"));
        CompilationResult r = driver.compileSources(sources,
                tempDir.resolve("out"), Target.JVM, tempDir);
        assertFalse(r.success(), "mesmo nome no MESMO pacote deve falhar");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "PKG005".equals(d.code())),
                "gap PKG005 esperado: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void moduleRootDerivedFromCommonAncestor(@TempDir Path tempDir) throws IOException {
        // P1-4: sem moduleRoot explícito, fontes em subdiretórios diferentes
        // precisam do menor ancestral comum como raiz — `import b.Y` a partir
        // de a/X.kf só resolve se a raiz for a pasta pai de a/ e b/.
        Path root = tempDir.resolve("proj");
        Files.createDirectories(root.resolve("a"));
        Files.createDirectories(root.resolve("b"));
        Files.writeString(root.resolve("a/X.kf"), """
            package a

            import b.Y

            class X {
                static Int somar() { return 1 + 1 }
            }
            """);
        Files.writeString(root.resolve("b/Y.kf"), """
            package b

            import a.X

            class Y {
                static Int total() { return X.somar() * 10 }
            }
            """);
        Files.writeString(root.resolve("main.kf"), """
            import a.X
            import b.Y

            main() {
                println(Y.total())
            }
            """);
        // 3-arg: moduleRoot é derivado (LCA = proj/), não de sources.get(0)
        CompilationResult r = driver.compileSources(
                List.of(
                        root.resolve("a/X.kf"),
                        root.resolve("b/Y.kf"),
                        root.resolve("main.kf")),
                root.resolve("out"), Target.JVM);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
    }

    @Test
    void twoMainsIsDiagnostic(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.kf"), """
            main() { println("um") }
            """);
        Files.writeString(tempDir.resolve("b.kf"), """
            main() { println("dois") }
            """);
        List<Path> sources = List.of(tempDir.resolve("a.kf"), tempDir.resolve("b.kf"));
        CompilationResult r = driver.compileSources(sources,
                tempDir.resolve("out"), Target.JVM, tempDir);
        assertFalse(r.success(), "dois main() deve falhar");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "PKG002".equals(d.code())),
                "gap PKG002 esperado: " + r.diagnostics().getDiagnostics());
    }
}
