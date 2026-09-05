package dev.kof.compiler;

/**
 * UI do runtime JVM: primitivas de janela/widget, Component Core, Router e layout (no-ops JVM).
 * Extraído de JvmRuntime.sourceCore (REFACTOR-500 Fase 5) — fragmento do
 * source do KofRuntime gerado; a concatenação preserva conteúdo byte-a-byte.
 */
final class JvmRuntimeUi {

    private JvmRuntimeUi() {}

    static String source() {
        return """

                public static int kof_ui_window_new(String title) {
                    return 1;
                }

                public static void kof_ui_window_set_title(int window, String title) {
                }

                public static String kof_ui_window_title(int window) {
                    return "";
                }

                public static void kof_ui_window_bind(int window, int label) {
                }

                public static void kof_ui_window_show(int window) {
                }

                public static void kof_ui_window_close(int window) {
                }

                public static void kof_ui_window_set_size(int window, int width, int height) {
                }

                public static void kof_ui_window_set_theme(int window, int theme) {
                }

                public static int kof_ui_label_new(String text) {
                    return 1;
                }

                public static int kof_ui_link_new(String text, String url) {
                    return 1;
                }

                public static void kof_ui_link_set_text(int link, String text) {
                }

                public static String kof_ui_link_text(int link) {
                    return "";
                }

                public static void kof_ui_link_set_url(int link, String url) {
                }

                public static String kof_ui_link_url(int link) {
                    return "";
                }

                public static void kof_ui_link_remove(int link) {
                }

                public static int kof_ui_image_new(String src) {
                    return 1;
                }

                public static void kof_ui_image_set_src(int image, String src) {
                }

                public static String kof_ui_image_src(int image) {
                    return "";
                }

                public static void kof_ui_image_remove(int image) {
                }

                public static int kof_ui_icon_new(String name) {
                    return 1;
                }

                public static int kof_ui_icon_new_size(String name, int size) {
                    return 1;
                }

                public static void kof_ui_icon_set_name(int icon, String name) {
                }

                public static String kof_ui_icon_name(int icon) {
                    return "";
                }

                public static void kof_ui_icon_set_size(int icon, int size) {
                }

                public static int kof_ui_icon_size(int icon) {
                    return 24;
                }

                public static void kof_ui_icon_remove(int icon) {
                }

                public static int kof_ui_font_new(String family, int size) {
                    return 1;
                }

                public static int kof_ui_font_new_bold(String family, int size, boolean bold) {
                    return 1;
                }

                public static void kof_ui_widget_set_font(int widget, int font) {
                }

                // ── Component Core (docs/ui/architecture.md) ──
                // JVM/Native: kof.ui é KofJS — os handles de componente são
                // no-ops (a renderização/lifecycle/estado rodam no alvo JS).
                private static int kofUiCompSeq = 0;
                private static final java.util.Set<Integer> kofUiCompLive = new java.util.HashSet<>();
                private static final java.util.Map<Integer, Integer> kofUiCompParent = new java.util.HashMap<>();
                public static int kof_ui_component_new(int state) {
                    int id = ++kofUiCompSeq;
                    kofUiCompLive.add(id);
                    return id;
                }

                public static int kof_ui_component_state_get(int c) {
                    return 0;
                }

                public static void kof_ui_component_state_set(int c, int value) {
                }

                public static void kof_ui_component_view(int c, Object builder) {
                }

                public static void kof_ui_component_on_mount(int c, Object fn) {
                }

                public static void kof_ui_component_on_dispose(int c, Object fn) {
                }

                public static void kof_ui_component_effect(int c, Object fn) {
                }

                public static void kof_ui_component_on(int c, String type, Object handler) {
                }

                public static void kof_ui_component_bind(int c, int child) {
                    // JVM mirrors the component tree (no rendering): bind
                    // records the child under the parent so remove() can
                    // free the whole subtree like the KofJS target does.
                    kofUiCompParent.put(child, c);
                }

                public static void kof_ui_component_remove(int c) {
                    kofUiCompRemoveTree(c);
                }

                private static void kofUiCompRemoveTree(int c) {
                    for (var it = kofUiCompParent.entrySet().iterator(); it.hasNext(); ) {
                        var e = it.next();
                        if (e.getValue() == c) {
                            kofUiCompRemoveTree(e.getKey());
                            it.remove();
                        }
                    }
                    kofUiCompLive.remove(c);
                }

                public static void kof_ui_component_mount(int c) {
                }

                public static void kof_ui_component_unmount(int c) {
                    kofUiCompLive.remove(c);
                }

                public static int kof_ui_nodes_live() {
                    return kofUiCompLive.size();
                }

                public static void kof_ui_flush_ui() {
                }

                public static String kof_ui_event_type(String type) {
                    return type == null ? "" : type;
                }

                public static void kof_ui_emit(int c, String type) {
                }

                public static void kof_ui_event_stop(Object ev) {
                }

                // ── Fase 8: Store observável (no-ops) ──
                private static int kofUiStoreSeq = 0;
                private static final java.util.Set<Integer> kofUiStoreLive = new java.util.HashSet<>();

                public static int kof_ui_store_new(int initial) {
                    int id = ++kofUiStoreSeq;
                    kofUiStoreLive.add(id);
                    return id;
                }

                public static int kof_ui_store_get(int s) {
                    return 0;
                }

                public static void kof_ui_store_set(int s, int value) {
                }

                public static void kof_ui_store_subscribe(int s, Object fn) {
                }

                public static void kof_ui_store_unsubscribe(int s, Object fn) {
                }

                public static int kof_ui_stores_live() {
                    return kofUiStoreLive.size();
                }

                // ── Fase 7: Router (no-ops — UI é KofJS) ──
                public static void kof_ui_route_register(String name, int root) {
                }

                public static boolean kof_ui_router_go1(String name) {
                    return false;
                }

                public static boolean kof_ui_router_go2(String name, String param) {
                    return false;
                }

                public static boolean kof_ui_router_replace1(String name) {
                    return false;
                }

                public static boolean kof_ui_router_replace2(String name, String param) {
                    return false;
                }

                public static boolean kof_ui_router_back() {
                    return false;
                }

                public static boolean kof_ui_router_forward() {
                    return false;
                }

                public static String kof_ui_router_param() {
                    return "";
                }

                public static String kof_ui_router_current() {
                    return "";
                }

                public static int kof_ui_router_depth() {
                    return 0;
                }

                // ── Fase 4: primitivas de layout (no-ops) ──
                public static int kof_ui_box_new(java.util.ArrayList ids) {
                    return 1;
                }

                public static int kof_ui_stack_new(java.util.ArrayList ids) {
                    return 1;
                }

                public static int kof_ui_wrap_new(java.util.ArrayList ids) {
                    return 1;
                }

                public static int kof_ui_grid_new(int cols, java.util.ArrayList ids) {
                    return 1;
                }

                public static int kof_ui_spacer_new(int size) {
                    return 1;
                }

                public static int kof_ui_center_new(java.util.ArrayList ids) {
                    return 1;
                }

                public static int kof_ui_align_new(int horizontal, int vertical, java.util.ArrayList ids) {
                    return 1;
                }

                public static int kof_ui_widget_font(int widget) {
                    return -1;
                }

                public static void kof_ui_label_set_text(int label, String text) {
                }

                public static String kof_ui_label_text(int label) {
                    return "";
                }

                public static void kof_ui_label_set_font_size(int label, int size) {
                }

                public static int kof_ui_label_font_size(int label) {
                    return 0;
                }

                public static void kof_ui_label_set_bold(int label, int bold) {
                }

                public static int kof_ui_label_bold(int label) {
                    return 0;
                }

                public static void kof_ui_label_set_color(int label, int color) {
                }

                public static int kof_ui_label_color(int label) {
                    return 0;
                }

                public static void kof_ui_label_remove(int label) {
                }

                public static int kof_ui_button_new(String text) {
                    return 1;
                }

                public static int kof_ui_button_new_action(String text, Object action) {
                    return 1;
                }

                public static void kof_ui_button_set_text(int button, String text) {
                }

                public static String kof_ui_button_text(int button) {
                    return "";
                }

                public static void kof_ui_button_remove(int button) {
                }

                public static int kof_ui_input_new(String text) {
                    return 1;
                }

                public static void kof_ui_input_set_text(int input, String text) {
                }

                public static String kof_ui_input_text(int input) {
                    return "";
                }

                public static void kof_ui_input_remove(int input) {
                }

                public static int kof_ui_column_new(java.util.ArrayList ids) {
                    return 1;
                }

                public static int kof_ui_row_new(java.util.ArrayList ids) {
                    return 1;
                }

                public static int kof_ui_view_new(int style) {
                    return 1;
                }

                public static int kof_ui_style_new(int background, int foreground, int padding, int radius) {
                    return 1;
                }

                public static void kof_ui_view_bind(int view, int child) {
                }

                public static void kof_ui_view_remove(int view) {
                }

                public static String kof_ui_color_to_css(int color) {
                    int r = (color >>> 24) & 0xFF;
                    int g = (color >>> 16) & 0xFF;
                    int b = (color >>> 8) & 0xFF;
                    int a = color & 0xFF;
                    if (a == 255) {
                        return "rgb(" + r + ", " + g + ", " + b + ")";
                    }
                    return "rgba(" + r + ", " + g + ", " + b + ", " + a + ")";
                }

""";
    }
}
