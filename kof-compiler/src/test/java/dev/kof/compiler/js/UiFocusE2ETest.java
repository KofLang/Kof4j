package dev.kof.compiler.js;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #811 (fatia 1) — gestão de foco no `kof.ui`: `focus()` nos widgets DOM e
 * `setAutoFocus(Bool)` nos componentes.
 *
 * <p>Duas faces, porque uma sozinha não prova nada aqui:
 * <ul>
 *   <li><b>compilação</b>: o programa Kof baixa para as funções de runtime certas
 *       e roda nos alvos onde `kof.ui` é no-op (JVM), como setId/setDisabled;</li>
 *   <li><b>execução</b>: o comportamento é MEDIDO no shim do DOM sob GraalJS —
 *       foco pré-montagem aplicado na montagem (o caso que a issue pede),
 *       movimentação explícita e auto-focus no primeiro descendente focável.</li>
 * </ul>
 *
 * <p>O ciclo de vida é o que este teste guarda: os nós entram em `__kofNodes` na
 * CONSTRUÇÃO, antes da anexação — focar um nó desanexado é no-op no DOM real, de
 * modo que a implementação ingênua (`if (n) n.focus()`) descartaria o pedido em
 * silêncio. O shim passou a modelar `isConnected`/`activeElement` justamente para
 * que essa diferença seja observável.
 */
class UiFocusE2ETest {

    private static final String PROGRAM = """
            main() {
                var janela = Window("foco")
                var nome = Input("nome")
                var salvar = Button("Salvar", () -> println("salvou"))
                nome.focus()
                janela.bind(nome)
                janela.bind(salvar)
                println("ok")
            }
            """;

    /** O programa baixa para `kof_ui_widget_focus`/`kof_ui_component_set_auto_focus`. */
    @Test
    void lowersToTheRuntimeFocusCalls(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("focus.kf");
        Files.writeString(src, PROGRAM);
        CompilationResult js = new CompilerDriver().compile(src, tmp.resolve("js"), Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        String emitted = Files.readString(tmp.resolve("js/Default.mjs"));
        assertTrue(emitted.contains("kofUiWidgetFocus"), "focus() deve baixar para kofUiWidgetFocus");
        assertFalse(emitted.contains("setAutoFocus"),
                "setAutoFocus NÃO é desta fatia (a superfície é decisão da mantenedora)");
    }

    /** Nos alvos onde kof.ui é no-op (JVM) o programa continua compilando e rodando. */
    @Test
    void compilesAndRunsOnJvmAsANoOp(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("focus.kf");
        Files.writeString(src, PROGRAM);
        Path out = tmp.resolve("jvm");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, p.waitFor(), "JVM run: " + o);
        assertEquals("ok", o, "o foco é no-op na JVM (kof.ui é KofJS), sem quebrar o programa");
    }

    // --- face de execução: o shim do DOM -------------------------------------

    /**
     * Avalia o runtime que o artefato real monta (core + componentes + widgets)
     * num Context sem `document` — mesma condição do shim — com o trecho de teste
     * NO MESMO MÓDULO: as funções do artefato são de escopo de módulo (não
     * globais), e chamá-las de um eval separado daria `ReferenceError`, o mesmo
     * que o artefato real nunca faz.
     */
    private java.util.Map<String, Boolean> runInShim(String body) throws IOException {
        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            Source module = Source.newBuilder("js",
                            JsRuntimeCore.CORE_RUNTIME
                                    + JsRuntimeUiComponents.UI_COMPONENT_RUNTIME
                                    + JsRuntimeUiWidgets.UI_WIDGET_RUNTIME
                                    + "\nglobalThis.__kofTest = (function () {" + body + "})();\n",
                            "runtime.mjs")
                    .mimeType("application/javascript+module")
                    .build();
            ctx.eval(module);
            // Os membros são lidos AQUI dentro: o `Value` morre com o Context.
            Value r = ctx.eval("js", "globalThis.__kofTest");
            java.util.Map<String, Boolean> out = new java.util.LinkedHashMap<>();
            for (String k : r.getMemberKeys()) {
                out.put(k, r.getMember(k).asBoolean());
            }
            return out;
        }
    }

    /** **O caso que a issue pede:** `focus()` ANTES da montagem é aplicado quando
     *  o nó fica conectado — e não é descartado em silêncio. */
    @Test
    void preMountFocusIsAppliedOnMount() throws IOException {
        java.util.Map<String, Boolean> r = runInShim("""
                const l = kofUiLabelNew("nome");
                kofUiWidgetFocus(l);
                const antes = document.activeElement === window.__kofNodes[l];
                document.getElementById("kof-root").appendChild(window.__kofNodes[l]);
                kofUiFocusFlush();
                const depois = document.activeElement === window.__kofNodes[l];
                return { antes: antes, depois: depois };
                """);
        assertFalse(r.get("antes"), "nó desanexado NÃO pode receber foco (no-op no DOM)");
        assertTrue(r.get("depois"), "o foco pendente tem de ser aplicado quando o nó conecta");
    }

    /** Foco explícito num nó já conectado, e a movimentação de um para outro. */
    @Test
    void connectedFocusMovesBetweenWidgets() throws IOException {
        java.util.Map<String, Boolean> r = runInShim("""
                const a = kofUiLabelNew("a");
                const b = kofUiLabelNew("b");
                const root = document.getElementById("kof-root");
                root.appendChild(window.__kofNodes[a]);
                root.appendChild(window.__kofNodes[b]);
                kofUiWidgetFocus(a);
                const emA = document.activeElement === window.__kofNodes[a];
                kofUiWidgetFocus(b);
                const emB = document.activeElement === window.__kofNodes[b];
                return { a: emA, b: emB };
                """);
        assertTrue(r.get("a"), "focus() num nó conectado aplica na hora");
        assertTrue(r.get("b"), "o segundo focus() MOVE o foco");
    }


}
