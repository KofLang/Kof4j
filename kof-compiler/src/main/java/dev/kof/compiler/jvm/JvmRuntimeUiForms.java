package dev.kof.compiler.jvm;

/**
 * UI do runtime JVM: widgets de forms (input/textarea/select) — no-ops JVM.
 * Fragmento do source do KofRuntime gerado (concatenação byte-a-byte),
 * extraído de JvmRuntimeUi para o gate <=500.
 */
public final class JvmRuntimeUiForms {

    private JvmRuntimeUiForms() {}

    static String source() {
        return """
                public static int kof_ui_input_new(String text) {
                    return 1;
                }

                public static void kof_ui_input_set_text(int input, String text) {
                }

                public static void kof_ui_input_set_placeholder(int input, String placeholder) {
                }

                public static void kof_ui_input_set_type(int input, String type) {
                }

                public static void kof_ui_input_set_checked(int input, int checked) {
                }

                public static int kof_ui_input_checked(int input) {
                    return 0;
                }

                public static int kof_ui_textarea_new(String text) {
                    return 1;
                }

                public static void kof_ui_textarea_set_text(int ta, String text) {
                }

                public static String kof_ui_textarea_text(int ta) {
                    return "";
                }

                public static void kof_ui_textarea_set_placeholder(int ta, String placeholder) {
                }

                public static void kof_ui_textarea_remove(int ta) {
                }

                public static int kof_ui_select_new(java.util.ArrayList<String> options) {
                    return 1;
                }

                public static void kof_ui_select_set_options(int sel, java.util.ArrayList<String> options) {
                }

                public static void kof_ui_select_set_selected(int sel, int index) {
                }

                public static int kof_ui_select_selected(int sel) {
                    return 0;
                }

                public static void kof_ui_select_remove(int sel) {
                }

                public static int kof_ui_ul_new(java.util.ArrayList<String> items) {
                    return 1;
                }

                public static void kof_ui_ul_set_items(int ul, java.util.ArrayList<String> items) {
                }

                public static void kof_ui_ul_remove(int ul) {
                }

                public static int kof_ui_ol_new(java.util.ArrayList<String> items) {
                    return 1;
                }

                public static void kof_ui_ol_set_items(int ol, java.util.ArrayList<String> items) {
                }

                public static void kof_ui_ol_remove(int ol) {
                }

                public static int kof_ui_table_new(java.util.ArrayList<String> header, java.util.ArrayList rows) {
                    return 1;
                }

                public static void kof_ui_table_set_rows(int table, java.util.ArrayList rows) {
                }

                public static void kof_ui_table_remove(int table) {
                }

                public static String kof_ui_input_text(int input) {
                    return "";
                }

                public static void kof_ui_input_remove(int input) {
                }
                """;
    }
}
