package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeclarationStartPositionTest {

    @TempDir
    Path tmp;

    @Test
    void typeDeclarationsStartAtTheirKeyword() {
        CompilationUnitNode unit = parse("""
                class Point {
                    Int x
                }

                interface Shape {
                    Int sides()
                }

                record Pair(Int a, Int b)

                class Box(Int v) {
                    Int get() {
                        return v
                    }
                }

                enum Color {
                    RED,
                    GREEN
                }

                entity User {
                    name: String
                }

                main() {
                    println(1)
                }
                """);
        List<? extends AstNode> decls = unit.declarations();
        assertEquals(1, decls.get(0).position().line());
        assertEquals(5, decls.get(1).position().line());
        assertEquals(9, decls.get(2).position().line());
        assertEquals(11, decls.get(3).position().line());
        assertEquals(17, decls.get(4).position().line());
        assertEquals(22, decls.get(5).position().line());
    }

    @Test
    void fieldsAndConstructorsStartOnTheirOwnLine() {
        CompilationUnitNode unit = parse("""
                class Counter {
                    Int n
                    Int step = 1
                    constructor(Int start) {
                        n = start
                    }
                    Int next() {
                        n = n + step
                        return n
                    }
                }
                """);
        ClassDeclarationNode c = (ClassDeclarationNode) unit.declarations().get(0);
        assertEquals(2, c.members().get(0).position().line());
        assertEquals(3, c.members().get(1).position().line());
        assertEquals(4, c.members().get(2).position().line());
    }

    @Test
    void classDiagnosticsPointAtTheClass() throws Exception {
        Diagnostic finalAbstract = firstError("""
                final abstract class Shape {
                    Int sides
                }

                main() {
                    println("x")
                }
                """, "SEM069");
        assertEquals(1, finalAbstract.line(), finalAbstract.format());

        Diagnostic extendsFinal = firstError("""
                final class Base {
                    Int v
                }

                class Child extends Base {
                    Int w
                }

                main() {
                    println("x")
                }
                """, "SEM070");
        assertEquals(5, extendsFinal.line(), extendsFinal.format());

        Diagnostic duplicateCtor = firstError("""
                class Point {
                    Int x
                    constructor(Int a) {
                        x = a
                    }
                    constructor(Int b) {
                        x = b
                    }
                }

                main() {
                    println(Point(1).x)
                }
                """, "SEM061");
        assertEquals(6, duplicateCtor.line(), duplicateCtor.format());
        assertTrue(duplicateCtor.message().contains("at line 3"), duplicateCtor.format());
    }

    @Test
    void fmtKeepsCommentsInsideTheClass() {
        String src = """
                // top
                class Point {
                    // x field
                    Int x
                    /* y field,
                     * with a default */
                    Int y = 2
                    // constructor
                    constructor(Int a) {
                        // inside
                        x = a
                    }
                }

                // entry point
                main() {
                    println(Point(1).x)
                }
                """;
        String expected = """
                // top
                class Point {
                    // x field
                    Int x
                    /* y field,
                     * with a default */
                    Int y = 2
                    // constructor
                    constructor(a: Int) {
                        // inside
                        x = a
                    }
                }

                // entry point
                main() {
                    println(Point(1).x)
                }
                """;
        String out = KofFormatter.format(src, "Main.kf");
        assertEquals(expected, out);
        assertEquals(out, KofFormatter.format(out, "Main.kf"));
    }

    private Diagnostic firstError(String src, String code) throws Exception {
        Path dir = Files.createTempDirectory(tmp, code);
        Path file = dir.resolve("Main.kf");
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, dir.resolve("out"), Target.JVM);
        return r.diagnostics().getDiagnostics().stream()
                .filter(d -> code.equals(d.code()))
                .findFirst().orElseThrow(() -> new AssertionError(code + " expected: " + r.diagnostics().getDiagnostics()));
    }

    private static CompilationUnitNode parse(String src) {
        DiagnosticCollector diags = new DiagnosticCollector();
        List<Token> tokens = new Lexer(src, "Main.kf", diags).tokenize();
        CompilationUnitNode unit = new Parser(tokens, diags, "Main.kf").parse();
        assertTrue(diags.getDiagnostics().isEmpty(), diags.getDiagnostics().toString());
        return unit;
    }
}
