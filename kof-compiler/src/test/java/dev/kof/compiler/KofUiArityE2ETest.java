package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A ARIDADE ERRADA de um construtor {@code kof.ui} era aceita sem NENHUM
 * diagnóstico e emitia uma chamada CRUA — o gerador escrevia
 * {@code x = Label("a", 1)} (o nome do construtor, sem o helper do runtime) e
 * o browser morria com {@code ReferenceError: Label is not defined}. A lei do
 * repo (regra 5 / R6: nenhum fallback silencioso) exige a recusa honesta no
 * compile-time, com o código de gap nomeado (SEM115).
 *
 * <p>A ARBITRAGEM de cada construtor foi MEDIDA no lowering (30 construtores x
 * 5 aridades: o JS gerado, inspecionado) e mora em {@link KofUiArity} — este
 * teste e' a RED-first do contrato: antes do fix, cada caso "errado" COMPILAVA
 * (success=true) e o modulo quebrado era emitido.
 */
class KofUiArityE2ETest {

    /** A forma aceita de cada construtor (a tabela medida). */
    private static final String[][] FORMS = {
            {"Window", "1"},
            {"Label", "1"},
            {"Textarea", "1"},
            {"Image", "1"},
            {"Component", "1"},
            {"Store", "1"},
            {"Button", "1", "2"},
            {"Icon", "1", "2"},
            {"Fieldset", "0", "1", "2"},
            {"Link", "2"},
            {"Canvas", "2"},
            {"Grid", "0", "2"},
            {"Table", "0", "2"},
            {"Color", "1", "3"},
            {"Font", "2", "3"},
            {"Align", "0", "3"},
            {"Style", "0", "1", "4"},
            {"Hr", "0"},
            {"Iframe", "1"},
            {"Video", "1"},
            {"Audio", "1"},
            {"Input", "0", "1"},
            {"Select", "0", "1"},
            {"View", "0", "1"},
            {"Column", "0", "1"},
            {"Row", "0", "1"},
            {"Form", "0", "1"},
            {"Ul", "0", "1"},
            {"Ol", "0", "1"},
            {"Box", "0", "1"},
            {"Stack", "0", "1"},
            {"Spacer", "0", "1"},
            {"Wrap", "0", "1"},
            {"Center", "0", "1"},
            {"Scroll", "0", "1"},
    };

    private static String argsOf(int arity) {
        return switch (arity) {
            case 0 -> "";
            // o Icon aceita um NOME de icone (o KofUiIcons valida o literal) e
            // o Style aceita a declaracao "prop: value" (o KofStyleParser
            // valida o conteudo) — um texto qualquer cai no SEM077/SEM079 (uma
            // recusa DE CONTEUDO, nao de aridade), entao o caso usa a forma real
            case 1 -> "\"home\"";
            case 2 -> "\"home\", \"x\"";
            case 3 -> "\"home\", 1, 2";
            case 4 -> "\"home\", 1, 2, 3";
            default -> throw new IllegalArgumentException("arity " + arity);
        };
    }

    /** A forma do Style na forma DECLARATIVA (1 argumento): "prop: value". */
    private static String argsOfStyle(int arity) {
        return arity == 1 ? "\"color: red\"" : argsOf(arity);
    }

    private static CompilationResult compile(Path dir, String source) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, source, StandardCharsets.UTF_8);
        return new CompilerDriver().compile(src, dir.resolve("out"), Target.JS);
    }

    @Test
    void theAcceptedFormsStillCompile(@TempDir Path dir) throws Exception {
        for (String[] form : FORMS) {
            String name = form[0];
            for (int i = 1; i < form.length; i++) {
                int arity = Integer.parseInt(form[i]);
                String args = "Style".equals(name) ? argsOfStyle(arity) : argsOf(arity);
                CompilationResult r = compile(dir,
                        "import kof.ui\nmain() { val x = " + name + "(" + args + ") }\n");
                assertTrue(r.success(), name + "/" + arity + " is a documented form and must compile: "
                        + r.diagnostics().getDiagnostics());
            }
        }
    }

    @Test
    void aWrongArityIsRefusedHonestlyWithTheGapCode(@TempDir Path dir) throws Exception {
        for (String[] form : FORMS) {
            String name = form[0];
            java.util.Set<Integer> accepted = new java.util.HashSet<>();
            for (int i = 1; i < form.length; i++) accepted.add(Integer.parseInt(form[i]));
            for (int arity = 0; arity <= 4; arity++) {
                if (accepted.contains(arity)) continue;
                CompilationResult r = compile(dir,
                        "import kof.ui\nmain() { val x = " + name + "(" + argsOf(arity) + ") }\n");
                assertFalse(r.success(),
                        name + "/" + arity + " is NOT a documented form and must be refused");
                String diags = r.diagnostics().getDiagnostics().toString();
                assertTrue(diags.contains("SEM115"),
                        name + "/" + arity + " names the gap code: " + diags);
                assertTrue(diags.contains(name),
                        name + "/" + arity + " names the constructor: " + diags);
            }
        }
    }

    @Test
    void aWrongArityEmitsNoBrokenModule(@TempDir Path dir) throws Exception {
        CompilationResult r = compile(dir,
                "import kof.ui\nmain() { val x = Label(\"a\", 1) }\n");
        assertFalse(r.success(), "the wrong arity must be refused");
        assertFalse(Files.exists(dir.resolve("out").resolve("Default").resolve("Default.mjs")),
                "a refusal emits NO artifact (Q7/R6: no silently broken module)");
    }
}
