package dev.kof.compiler.js;

/**
 * Fragmento do runtime JS (REFACTOR-500 slices; concatenado por
 * JsRuntimeSlices). D-UI-FOCUS (issue #811): a família de FOCO extraída de
 * JsRuntimeUiWidgets no MESMO commit em que a fatia 2 (setAutoFocus) a
 * empurrou sobre a linha vermelha de 600 — check_500 exige o split no mesmo
 * commit. Real só no KofJS; JVM/Native são no-op (JvmRuntimeUi/RuntimeUi).
 *
 * <p>O CICLO DE VIDA é o ponto: os nós entram em {@code __kofNodes} na
 * CONSTRUÇÃO, antes de serem anexados ao documento — focar um nó desanexado é
 * no-op no DOM real. Um {@code focus()} emitido dentro do {@code view} fica
 * PENDENTE ({@code __kofPendingFocus}) e é aplicado quando o nó conecta
 * ({@code kofUiFocusFlush}, chamado na montagem/bind). A fatia 2 acrescenta
 * {@code setAutoFocus(Bool)}: o PRIMEIRO descendente focável do contêiner
 * recebe o foco quando ele conecta.
 */
public final class JsRuntimeUiFocus {

    private JsRuntimeUiFocus() {}

    static String UI_FOCUS_RUNTIME = """
            // D-UI-FOCUS (issue #811): foco explícito + auto-focus na montagem.
            // Real só no KofJS (no-op JVM/Native, como setId/setDisabled).
            //
            // O CICLO DE VIDA é o ponto, e é a razão de isto não ser um
            // `n.focus()` solto: os nós entram em __kofNodes na CONSTRUÇÃO,
            // ANTES de serem anexados ao documento, e focar um nó DESANEXADO é
            // no-op no DOM real. Um `focus()` emitido dentro do view (o caso
            // que a issue pede) fica PENDENTE e é aplicado quando o nó fica
            // conectado — nunca descartado em silêncio.
            const __kofPendingFocus = new Set();

            function kofUiFocusConnected(n) {
                if (typeof n.isConnected === "boolean") return n.isConnected;
                // Shim do DOM (hostless/SSR) não modela `isConnected`: a conexão
                // é inferida subindo `parentNode` até o corpo do documento —
                // mesma semântica (nó desanexado não recebe foco).
                if (typeof document === "undefined" || !document.body) return true;
                let p = n;
                while (p) {
                    if (p === document.body) return true;
                    p = p.parentNode;
                }
                return false;
            }
            function kofUiFocusTake(id) {
                const n = window.__kofNodes && window.__kofNodes[id];
                if (!n || !n.focus) return false;
                if (!kofUiFocusConnected(n)) return false;
                n.focus();
                return true;
            }
            export function kofUiWidgetFocus(widget) {
                if (!kofUiFocusTake(widget)) __kofPendingFocus.add(widget);
            }
            export function kofUiFocusFlush() {
                for (const id of Array.from(__kofPendingFocus)) {
                    if (kofUiFocusTake(id)) __kofPendingFocus.delete(id);
                }
            }

            // D-UI-FOCUS (#811 fatia 2): setAutoFocus(Bool) num widget
            // contêiner — o PRIMEIRO descendente focável recebe o foco quando
            // o contêiner conecta (o caso do formulário: o primeiro campo). A
            // flag vive no nó; o apply é idempotente e re-executado no
            // bind/mount (o mesmo ciclo de vida da fatia 1).
            export function kofUiWidgetSetAutoFocus(widget, on) {
                const n = window.__kofNodes && window.__kofNodes[widget];
                if (!n) return;
                n.__kofAutoFocus = !!on;
                kofUiAutoFocusApply(n);
            }
            function kofUiFirstFocusable(el) {
                if (!el) return null;
                const tag = String(el.tagName || "").toUpperCase();
                if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || tag === "BUTTON") return el;
                const kids = el.children || [];
                for (const ch of kids) {
                    const f = kofUiFirstFocusable(ch);
                    if (f) return f;
                }
                return null;
            }
            export function kofUiAutoFocusApply(root) {
                if (!root || !root.__kofAutoFocus) return;
                if (!kofUiFocusConnected(root)) return;
                const t = kofUiFirstFocusable(root);
                if (t && typeof t.focus === "function") t.focus();
            }
            """;
}
