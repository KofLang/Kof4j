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

    // ── type-arguments genéricos via import (bug 32) ──────────────────────
    // `List<NodeUI>` com `import com.dev.NodeUI` precisa do pacote no ARG,
    // não só no tipo externo. Antes o arg ficava ClassType("","NodeUI") e o
    // checkcast/descritor JVM saía sem pacote → NoClassDefFoundError.

    private static final String NODE_UI = """
            package com.dev

            class NodeUI {
                String label
                public constructor(String label) {
                    this.label = label
                }
                String render() { return "ui:" + label }
            }
            """;

    private String runGenericCase(Path root, String mainSrc) throws Exception {
        Files.createDirectories(root.resolve("com/dev"));
        Files.writeString(root.resolve("com/dev/NodeUI.kf"), NODE_UI);
        Files.writeString(root.resolve("Main.kf"), mainSrc);
        List<Path> sources = List.of(
                root.resolve("com/dev/NodeUI.kf"), root.resolve("Main.kf"));
        Path out = root.resolve("out");
        CompilationResult r = driver.compileSources(sources, out, Target.JVM, root);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "runtime: " + output);
        return output;
    }

    @Test
    void genericArgViaImportResolvesPackage(@TempDir Path tempDir) throws Exception {
        // O bug reportado: List<NodeUI> com import → cast com pacote correto.
        String out = runGenericCase(tempDir.resolve("p1"), """
            import com.dev.NodeUI

            class Screen {
                List<NodeUI> children
                public constructor() {
                    this.children = listOf(NodeUI("a"), NodeUI("b"))
                }
                String first() {
                    var child = children.get(0)
                    return child.render()
                }
            }

            main() {
                println(Screen().first())
            }
            """);
        assertEquals("ui:a", out);
    }

    @Test
    void genericArgFullyQualifiedStillWorks(@TempDir Path tempDir) throws Exception {
        // Não regredir: List<com.dev.NodeUI> (antes dava ClassFormatError —
        // descritor com pontos).
        String out = runGenericCase(tempDir.resolve("p2"), """
            import com.dev.NodeUI

            class Screen {
                List<com.dev.NodeUI> children
                public constructor() {
                    this.children = listOf(NodeUI("z"))
                }
                String first() { return children.get(0).render() }
            }

            main() {
                println(Screen().first())
            }
            """);
        assertEquals("ui:z", out);
    }

    @Test
    void nestedGenericArgViaImport(@TempDir Path tempDir) throws Exception {
        // List<List<NodeUI>> — qualificação recursiva profunda.
        String out = runGenericCase(tempDir.resolve("p3"), """
            import com.dev.NodeUI

            class Board {
                List<List<NodeUI>> rows
                public constructor() {
                    this.rows = listOf(listOf(NodeUI("x")), listOf(NodeUI("y")))
                }
                String all() {
                    return rows.get(0).get(0).render() + "|" + rows.get(1).get(0).render()
                }
            }

            main() {
                println(Board().all())
            }
            """);
        assertEquals("ui:x|ui:y", out);
    }

    @Test
    void genericArgSamePackageNoImport(@TempDir Path tempDir) throws Exception {
        // Tipo do MESMO pacote (sem import) — SymbolTable resolve.
        Path root = tempDir.resolve("p4");
        Files.createDirectories(root.resolve("com/dev"));
        Files.writeString(root.resolve("com/dev/Widget.kf"), """
            package com.dev
            class Widget {
                String name
                public constructor(String name) { this.name = name }
                String show() { return "w:" + name }
            }
            """);
        Files.writeString(root.resolve("com/dev/Panel.kf"), """
            package com.dev
            class Panel {
                List<Widget> items
                public constructor() { this.items = listOf(Widget("x")) }
                String first() { return items.get(0).show() }
            }
            """);
        Files.writeString(root.resolve("Main.kf"), """
            import com.dev.Panel
            main() { println(Panel().first()) }
            """);
        List<Path> sources = List.of(
                root.resolve("com/dev/Widget.kf"),
                root.resolve("com/dev/Panel.kf"),
                root.resolve("Main.kf"));
        Path out = root.resolve("out");
        CompilationResult r = driver.compileSources(sources, out, Target.JVM, root);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "runtime: " + output);
        assertEquals("w:x", output);
    }

    @Test
    void genericArgCastHasPackageInBytecode(@TempDir Path tempDir) throws Exception {
        // Prova estrutural: o checkcast do get() referencia com/dev/NodeUI
        // (não "NodeUI" sem pacote).
        Path root = tempDir.resolve("p5");
        runGenericCase(root, """
            import com.dev.NodeUI

            class Screen {
                List<NodeUI> children
                public constructor() { this.children = listOf(NodeUI("a")) }
                String first() { return children.get(0).render() }
            }

            main() { println(Screen().first()) }
            """);
        Path cls = root.resolve("out").resolve("Screen.class");
        assertTrue(Files.exists(cls));
        Process p = new ProcessBuilder("javap", "-c", "-p", cls.toString())
                .redirectErrorStream(true).start();
        String dis = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        assertTrue(dis.contains("checkcast") && dis.contains("com/dev/NodeUI"),
                "cast deve ter o pacote: " + dis);
        assertFalse(dis.matches("(?s).*checkcast\\s+#\\d+\\s+// class NodeUI\\b.*"),
                "não deve haver cast para NodeUI sem pacote: " + dis);
    }
}
