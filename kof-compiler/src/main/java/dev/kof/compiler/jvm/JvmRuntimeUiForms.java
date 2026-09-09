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

                public static void kof_ui_input_set_name(int input, String name) {
                }

                public static void kof_ui_input_set_readonly(int input, int readonly) {
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

                public static void kof_ui_textarea_set_name(int ta, String name) {
                }

                public static void kof_ui_textarea_set_readonly(int ta, int readonly) {
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

                public static int kof_ui_fieldset_new(java.util.ArrayList children) {
                    return 1;
                }

                public static int kof_ui_iframe_new(String src) {
                    return 1;
                }

                public static void kof_ui_iframe_set_src(int iframe, String src) {
                }

                public static void kof_ui_iframe_remove(int iframe) {
                }

                public static int kof_ui_video_new(String src) {
                    return 1;
                }

                public static void kof_ui_video_set_src(int video, String src) {
                }

                public static void kof_ui_video_set_controls(int video, int controls) {
                }

                public static void kof_ui_video_play(int video) {
                }

                public static void kof_ui_video_pause(int video) {
                }

                public static void kof_ui_video_remove(int video) {
                }

                public static int kof_ui_audio_new(String src) {
                    return 1;
                }

                public static void kof_ui_audio_set_src(int audio, String src) {
                }

                public static void kof_ui_audio_set_controls(int audio, int controls) {
                }

                public static void kof_ui_audio_play(int audio) {
                }

                public static void kof_ui_audio_pause(int audio) {
                }

                public static void kof_ui_audio_remove(int audio) {
                }

                public static int kof_ui_hr_new() {
                    return 1;
                }

                public static void kof_ui_hr_remove(int hr) {
                }
                """;
    }
}
