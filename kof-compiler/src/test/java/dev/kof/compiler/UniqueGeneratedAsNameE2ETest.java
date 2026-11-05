package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §826 — {@code unique} and {@code generated} are contextual keywords
 * (maintainer decision 05/11): modifiers of an {@code entity} field, ordinary
 * names anywhere else, like {@code sealed} and {@code audited}. Before, the
 * lexer reserved them everywhere and {@code var unique = listOf<String>()}
 * was PARSE037 plus a cascade of PARSE041.
 */
class UniqueGeneratedAsNameE2ETest {

    @TempDir Path tmp;

    @Test
    void uniqueAndGeneratedAreOrdinaryNamesOutsideAnEntity() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
                class Ids {
                    Int generated = 0
                    Int unique(Int x) { return x + 1 }
                }
                main() {
                    var unique = listOf<String>()
                    unique.add("a")
                    val generated = Ids()
                    generated.generated = 2
                    println(unique.size() + generated.generated + generated.unique(3))
                }
                """);
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out"), Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                tmp.resolve("out").toString(), "Default.Main").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p.waitFor();
        assertEquals("7", out);
    }

    @Test
    void entityFieldsKeepTheModifiersAndMayUseTheWordsAsNames() {
        DiagnosticCollector diags = new DiagnosticCollector();
        CompilationUnitNode unit = new Parser(new Lexer("""
                entity User {
                    id: Long generated
                    email: String unique
                    code: String generated unique
                    unique: Bool
                    generated: Long
                }
                main() { }
                """, "Main.kf", diags).tokenize(), diags, "Main.kf").parse();
        assertTrue(diags.getDiagnostics().isEmpty(), "must parse: " + diags.getDiagnostics());
        EntityDeclarationNode user = (EntityDeclarationNode) unit.declarations().get(0);
        List<String> shape = user.fields().stream()
                .map(f -> f.name() + ":" + f.type() + (f.generated() ? " G" : "") + (f.unique() ? " U" : ""))
                .toList();
        assertEquals(List.of("id:Long G", "email:String U", "code:String G U", "unique:Bool", "generated:Long"),
                shape);
    }
}
