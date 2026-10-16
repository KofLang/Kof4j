package dev.kof.compiler;

/**
 * #861: a tabela dos containers de LAYOUT construídos vazios pelo nome —
 * {@code Box()}, {@code Stack()}, {@code Spacer()}, {@code Scroll()},
 * {@code Wrap()}, {@code Grid()}, {@code Center()}, {@code Align()} aceitam
 * aridade 0 (sem filhos) ou 1 (lista de filhos).
 * <p>#873: a tabela dos WIDGETS construídos vazios pelo nome —
 * {@code Form()}, {@code Fieldset()}, {@code View()}, {@code Input()},
 * {@code Style()}, {@code Select()}, {@code Ul()}, {@code Ol()},
 * {@code Table()} aceitam aridade 0. Só aridade 0: as formas com args têm
 * branches próprios com inferência de args (detecção de fantasmas) que a
 * tabela não pode pular.
 *
 * <p>Vive num arquivo próprio (e não no {@link KofUi}, que está a poucas linhas
 * do limite de 600 do {@code check_500}) porque era um {@code if} repetido por
 * widget nos DOIS typers — sem-typer e emit —, e quem paga a repetição é sempre
 * o mesmo arquivo grande. A tabela num lugar só mantém os dois em sincronia.
 */
final class KofUiContainers {

    private KofUiContainers() {
    }


    /** O receptor É um container de layout? (o `KofUi` testava cada um em 7 blocos) */
    static boolean isLayoutContainer(Type t) {
        return KofUi.isBox(t) || KofUi.isStack(t) || KofUi.isCenter(t) || KofUi.isAlign(t)
                || KofUi.isScroll(t) || KofUi.isWrap(t) || KofUi.isGrid(t) || KofUi.isSpacer(t);
    }

    /**
     * O membro de INSTÂNCIA dos containers de layout: {@code bind(filho)}. Devolve
     * a chamada, ou {@code null} quando o membro pedido não existe — o chamador
     * trata um receiver de container como "resolvido" (um container nunca cai nos
     * despachos de outros widgets). {@code Spacer} não tem filhos, logo não tem
     * {@code bind}.
     */
    static KofUi.UiCall bindCall(Type receiver, String name, int argCount) {
        if (!"bind".equals(name) || argCount != 1) return null;
        String fn = bindFunction(receiver);
        return fn == null ? null
                : new KofUi.UiCall(fn, Type.PrimitiveType.VOID, java.util.List.of(Type.PrimitiveType.INT));
    }

    private static String bindFunction(Type t) {
        if (KofUi.isBox(t)) return "kof_ui_box_bind";
        if (KofUi.isStack(t)) return "kof_ui_stack_bind";
        if (KofUi.isCenter(t)) return "kof_ui_center_bind";
        if (KofUi.isAlign(t)) return "kof_ui_align_bind";
        if (KofUi.isScroll(t)) return "kof_ui_scroll_bind";
        if (KofUi.isWrap(t)) return "kof_ui_wrap_bind";
        if (KofUi.isGrid(t)) return "kof_ui_grid_bind";
        return null; // Spacer
    }

    /** Tipo do container, ou {@code null} quando o nome não é um container de layout. */
    static Type emptyContainerType(String name) {
        return switch (name) {
            case "Box" -> KofUi.BOX;
            case "Stack" -> KofUi.STACK;
            case "Spacer" -> KofUi.SPACER;
            case "Scroll" -> KofUi.SCROLL;
            case "Wrap" -> KofUi.WRAP;
            case "Grid" -> KofUi.GRID;
            case "Center" -> KofUi.CENTER;
            case "Align" -> KofUi.ALIGN;
            default -> null;
        };
    }

    /** Tipo do widget, ou {@code null} quando o nome não é um widget 0-arg (#873). */
    static Type emptyWidgetType(String name) {
        return switch (name) {
            case "Form" -> KofUi.FORM;
            case "Fieldset" -> KofUi.FIELDSET;
            case "View" -> KofUi.VIEW;
            case "Input" -> KofUi.INPUT;
            case "Style" -> KofUi.STYLE;
            case "Select" -> KofUi.SELECT;
            case "Ul" -> KofUi.UL;
            case "Ol" -> KofUi.OL;
            case "Table" -> KofUi.TABLE;
            default -> null;
        };
    }
}
