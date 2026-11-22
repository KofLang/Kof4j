package dev.kof.compiler;

import java.util.List;
import java.util.Map;

/**
 * As FORMAS ACEITAS dos construtores de {@code kof.ui} — a aridade de cada
 * construtor, medida no lowering (a fonte da verdade: o que o
 * {@code ExpressionUiStaticLowerer} de fato emite).
 *
 * <p>POR QUE ISTO EXISTE (medido 21/10): um construtor UI com a aridade
 * ERRADA compilava SEM NENHUM DIAGNÓSTICO e emitia uma chamada crua — o
 * gerador escrevia {@code x = Label("a", 1)} (o nome do construtor, sem o
 * helper do runtime), e o browser morria com {@code ReferenceError: Label is
 * not defined}. A lei do repo (regra 5 / R6: nenhum fallback silencioso) exige
 * uma RECUSA HONESTA no compile-time, com o código de gap nomeado.
 *
 * <p>A MEDIÇÃO (o lowering como oráculo — 30 construtores × 5 aridades,
 * cada combinação compilada e o JS gerado inspecionado):
 * <ul>
 *   <li>{@code Window}/{@code Label}/{@code Textarea}/{@code Image}/
 *       {@code Component}/{@code Store}: 1 argumento;</li>
 *   <li>{@code Button}: 1 ou 2 (o 2º e' a acao); {@code Icon}: 1 ou 2
 *       (o 1o e' o nome — o 1o argumento literal e' validado pelo
 *       {@code KofUiIcons}); {@code Fieldset}: 0, 1 ou 2 (a legenda);</li>
 *   <li>{@code Link}: 2 (texto + url); {@code Canvas}: 2 (largura + altura);
 *       {@code Grid}: 2; {@code Table}: 2 ou 0;</li>
 *   <li>{@code Color}: 1 ou 3 (o hex ou o rgb); {@code Font}: 2 ou 3;</li>
 *   <li>{@code Align}: 0 ou 3; {@code Style}: 1 (declarativa) ou 4
 *       (posicional); {@code Hr}: 0; {@code Iframe}/{@code Video}/
 *       {@code Audio}: 1;</li>
 *   <li>{@code Input}/{@code Select}/{@code View}/{@code Column}/{@code Row}/
 *       {@code Form}/{@code Ul}/{@code Ol}: 0 ou 1;</li>
 *   <li>{@code Box}/{@code Stack}/{@code Spacer}/{@code Wrap}/{@code Center}/
 *       {@code Scroll}: 0 ou 1.</li>
 * </ul>
 *
 * <p>Um construtor FORA desta lista nao tem forma declarada: o chamador
 * decide o que fazer com ele (a UI tem construtores de aridade variavel que
 * nao cabem numa tabela — {@code Icon}, {@code Style} — e os containers que
 * aceitam 0 ou 1).
 */
final class KofUiArity {

    private KofUiArity() {
    }

    /** nome do construtor -> as aridades aceitas. */
    private static final Map<String, List<Integer>> FORMS = Map.ofEntries(
            Map.entry("Window", List.of(1)),
            Map.entry("Label", List.of(1)),
            Map.entry("Textarea", List.of(1)),
            Map.entry("Image", List.of(1)),
            Map.entry("Component", List.of(1)),
            Map.entry("Store", List.of(1)),
            Map.entry("Button", List.of(1, 2)),
            Map.entry("Icon", List.of(1, 2)),
            Map.entry("Fieldset", List.of(0, 1, 2)),
            Map.entry("Link", List.of(2)),
            Map.entry("Canvas", List.of(2)),
            Map.entry("Grid", List.of(0, 2)),
            Map.entry("Table", List.of(0, 2)),
            Map.entry("Color", List.of(1, 3)),
            Map.entry("Font", List.of(2, 3)),
            Map.entry("Align", List.of(0, 3)),
            Map.entry("Style", List.of(0, 1, 4)),
            Map.entry("Input", List.of(0, 1)),
            Map.entry("Select", List.of(0, 1)),
            Map.entry("View", List.of(0, 1)),
            Map.entry("Column", List.of(0, 1)),
            Map.entry("Row", List.of(0, 1)),
            Map.entry("Form", List.of(0, 1)),
            Map.entry("Ul", List.of(0, 1)),
            Map.entry("Ol", List.of(0, 1)),
            Map.entry("Box", List.of(0, 1)),
            Map.entry("Stack", List.of(0, 1)),
            Map.entry("Spacer", List.of(0, 1)),
            Map.entry("Wrap", List.of(0, 1)),
            Map.entry("Center", List.of(0, 1)),
            Map.entry("Scroll", List.of(0, 1)),
            Map.entry("Hr", List.of(0)),
            Map.entry("Iframe", List.of(1)),
            Map.entry("Video", List.of(1)),
            Map.entry("Audio", List.of(1)));

    /** as aridades aceitas do construtor, ou null quando ele nao tem forma
     *  declarada (o chamador decide). */
    static List<Integer> formsOf(String name) {
        return FORMS.get(name);
    }

    /**
     * {@code true} quando a aridade dada e' aceita. Um construtor sem forma
     * declarada aceita qualquer aridade (a TABELA e' a knowns; quem nao esta
     * nela nao e' silenciosamente quebrado por aridade).
     */
    static boolean accepts(String name, int arity) {
        List<Integer> forms = FORMS.get(name);
        if (forms == null) return true;
        if (forms.contains(arity)) return true;
        // A aridade 0 e' DERIVADA das MESMAS tabelas que o typer usa
        // (KofUiContainers: os containers de layout e os widgets construidos
        // vazios) — duplicar a lista aqui foi a fonte de TRES erros no
        // primeiro corte (Grid/Style/Align tinham a forma 0 nao declarada e
        // a recusa quebrava programas que funcionavam). A fonte da verdade e'
        // uma so.
        return arity == 0 && (KofUiContainers.emptyContainerType(name) != null
                || KofUiContainers.emptyWidgetType(name) != null);
    }

    /** A mensagem da recusa — nomeia a forma aceita (o contrato real). */
    static String message(String name, int arity) {
        List<Integer> forms = FORMS.get(name);
        String expected;
        if (forms == null) {
            expected = "a forma documentada";
        } else if (forms.size() == 1) {
            expected = forms.get(0) + " argumento" + (forms.get(0) == 1 ? "" : "s");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < forms.size(); i++) {
                if (i > 0) sb.append(i == forms.size() - 1 ? " ou " : ", ");
                sb.append(forms.get(i));
            }
            expected = sb + " argumentos";
        }
        return "'" + name + "' espera " + expected + " (recebeu " + arity
                + ") — a forma errada nao tem runtime: construa o widget pela "
                + "forma documentada em training/idioms/ui.md";
    }
}
