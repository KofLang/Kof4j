package dev.kof.compiler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


final class JvmRuntime {

    private JvmRuntime() {}

static boolean hasRuntimeFn(String methodName) {
        return methodName.startsWith("kof_json_")
                || methodName.startsWith("kof_io_")
                || methodName.startsWith("kof_web_")
                || methodName.startsWith("kof_config_")
                || methodName.startsWith("kof_cache_")
                || methodName.startsWith("kof_log_")
                || methodName.startsWith("kof_db_")
                || methodName.startsWith("kof_orm_")
                || methodName.startsWith("kof_string_to_")
                || methodName.startsWith("kof_ui_")
                || methodName.startsWith("kof_sec_")
                || methodName.startsWith("kof_validation_")
                || methodName.startsWith("kof_enum_")
                || methodName.equals("kof_spawn_result") || methodName.equals("kof_await")
                || methodName.equals("kof_poll") || methodName.equals("kof_done")
                || methodName.equals("kof_cancel") || methodName.equals("kof_cancelled")
                || methodName.equals("kof_await_timeout")
                || methodName.equals("kof_select_any")
                || methodName.equals("kof_list_map") || methodName.equals("kof_list_filter") || methodName.equals("kof_list_reduce")
                || methodName.startsWith("kof_observability_")
                || methodName.startsWith("kof_media_")
                || methodName.startsWith("kof_tetris_")
                || methodName.startsWith("kof_http_")
                || methodName.startsWith("kof_mq_")
                || methodName.startsWith("kof_time_")
                || methodName.startsWith("kof_vk_")
                || methodName.startsWith("kof_mv64_")
                || methodName.startsWith("kof_scheduler_")
                || methodName.equals("kof_now")
                || methodName.equals("kof_read_line")
                || methodName.equals("kof_read_file")
                || methodName.equals("kof_write_file")
                || methodName.equals("kof_spawn")
                || methodName.startsWith("kof_spawn_")
                || methodName.equals("kof_process_spawn")
                || methodName.equals("kof_process_run")
                || methodName.equals("kof_process_exit")
                || methodName.equals("kof_ffi_i")
                || methodName.equals("kof_ffi_si")
                || methodName.equals("kof_ffi_dd")
                || methodName.equals("kof_ffi_ai")
                || methodName.equals("kof_args_list");
    }

    static void ensureCompiled(Path outputDir, List<IRClass> classes, boolean usesVk) throws IOException {
        ensureCompiled(outputDir, classes, usesVk, false);
    }

    static void ensureCompiled(Path outputDir, List<IRClass> classes, boolean usesVk, boolean usesExtern) throws IOException {
        Path runtimeDir = outputDir.resolve("dev/kof/runtime");
        if (Files.exists(runtimeDir.resolve("KofRuntime.class"))) return;
        Files.createDirectories(runtimeDir);
        Path sourceFile = outputDir.resolve("KofRuntime.java");
        Files.writeString(sourceFile, source(classes, usesVk, usesExtern));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("JVM runtime requires a full JDK (javac not available)");
        }
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        // O bloco Vulkan usa FFM (java.lang.foreign). No JDK 21 é preview API:
        // exige --release 21 --enable-preview. No JDK 22+ é API FINAL (JEP 454)
        // e NENHUM flag é necessário — o usuário dessa sessão roda JDK 25, e o
        // caminho antigo quebrava com "invalid source release 21 with
        // --enable-preview" (COMP001). Capability/link-por-uso (R2) mantido:
        // o bloco só entra no source quando o programa realmente chama kof.vk.
        List<String> args = new java.util.ArrayList<>(List.of("-d", outputDir.toString()));
        if ((usesVk || usesExtern) && Runtime.version().feature() < 22) {
            args.add("--release");
            args.add("21");
            args.add("--enable-preview");
        }
        args.add("-classpath");
        args.add(outputDir.toString());
        args.add(sourceFile.toString());
        int rc = compiler.run(null, null, err, args.toArray(new String[0]));
        if (rc != 0) {
            String detail = err.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            throw new IOException("failed to compile KofRuntime helper (javac exit " + rc + "): "
                    + (detail.isEmpty() ? "unknown error" : detail));
        }
        if (System.getenv("KOF_KEEP_SRC") == null) Files.deleteIfExists(sourceFile);
    }

    static String callDescriptor(String methodName) {
        return switch (methodName) {
            case "kof_json_encode_int" -> "(I)Ljava/lang/String;";
            case "kof_json_encode_long" -> "(J)Ljava/lang/String;";
            case "kof_json_encode_bool" -> "(I)Ljava/lang/String;";
            case "kof_json_encode_float" -> "(F)Ljava/lang/String;";
            case "kof_json_encode_double" -> "(D)Ljava/lang/String;";
            case "kof_json_encode_string" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_json_encode_list" -> "(Ljava/util/List;I)Ljava/lang/String;";
            case "kof_json_encode_array", "kof_json_encode" -> "(Ljava/lang/Object;)Ljava/lang/String;";
            case "kof_json_decode_int", "kof_json_decode_bool" -> "(Ljava/lang/String;)I";
            case "kof_json_decode_long" -> "(Ljava/lang/String;)J";
            case "kof_json_decode_float" -> "(Ljava/lang/String;)F";
            case "kof_json_decode_double" -> "(Ljava/lang/String;)D";
            case "kof_json_decode_string" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_json_decode_int_list", "kof_json_decode_string_list", "kof_json_decode_list"
                    -> "(Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_json_decode_int_array", "kof_json_decode_bool_array" -> "(Ljava/lang/String;)[I";
            case "kof_json_decode_long_array" -> "(Ljava/lang/String;)[J";
            case "kof_json_decode_double_array" -> "(Ljava/lang/String;)[D";
            case "kof_json_decode_string_array" -> "(Ljava/lang/String;)[Ljava/lang/String;";
            case "kof_json_decode_object_list" -> "(Ljava/lang/String;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_now" -> "()J";
            case "kof_ffi_i" -> "(Ljava/lang/String;Ljava/lang/String;I)I";
            case "kof_ffi_si" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I";
            case "kof_ffi_dd" -> "(Ljava/lang/String;Ljava/lang/String;D)D";
            case "kof_ffi_ai" -> "(Ljava/lang/String;Ljava/lang/String;[I)I";
            case "kof_read_line" -> "()Ljava/lang/String;";
            case "kof_read_file" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_write_file" -> "(Ljava/lang/String;Ljava/lang/String;)I";
            case "kof_spawn" -> "(Ljava/lang/Object;)V";
            case "kof_io_file_exists", "kof_io_file_is_file", "kof_io_file_is_dir" -> "(Ljava/lang/String;)I";
            case "kof_io_read_text" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_io_write_text", "kof_io_append_text" -> "(Ljava/lang/String;Ljava/lang/String;)I";
            case "kof_io_read_bytes" -> "(Ljava/lang/String;)[I";
            case "kof_io_read_range" -> "(Ljava/lang/String;JJ)[I";
            case "kof_io_read_range_path" -> "(Ljava/lang/String;JJ)[I";
            case "kof_io_write_bytes", "kof_io_append_bytes" -> "(Ljava/lang/String;[I)I";
            case "kof_io_delete", "kof_io_dir_create", "kof_io_dir_create_dirs", "kof_io_dir_delete"
                    -> "(Ljava/lang/String;)I";
            case "kof_io_file_size" -> "(Ljava/lang/String;)J";
            case "kof_io_file_name", "kof_io_path_parent", "kof_io_path_file_name",
                    "kof_io_path_extension", "kof_io_path_normalize", "kof_io_path_to_absolute"
                    -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_io_path_resolve" -> "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_process_run" -> "(Ljava/lang/String;Ljava/util/List;)Ldev/kof/runtime/KofRuntime$ProcessResult;";
            case "kof_process_exit" -> "(I)V";
            case "kof_process_spawn" -> "(Ljava/lang/String;Ljava/util/List;)Ljava/lang/Long;";
            case "kof_spawn_read_line" -> "(Ljava/lang/Long;)Ljava/lang/String;";
            case "kof_spawn_write" -> "(Ljava/lang/Long;Ljava/lang/String;)V";
            case "kof_spawn_exit_code" -> "(Ljava/lang/Long;)I";
            case "kof_spawn_kill" -> "(Ljava/lang/Long;)V";
            case "kof_spawn_alive" -> "(Ljava/lang/Long;)Z";
            case "kof_args_list" -> "([Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_io_path_is_absolute" -> "(Ljava/lang/String;)I";
            case "kof_ui_color_to_css" -> "(I)Ljava/lang/String;";
            case "kof_ui_window_new", "kof_ui_label_new", "kof_ui_button_new", "kof_ui_input_new"
                    -> "(Ljava/lang/String;)I";
            case "kof_ui_button_new_action" -> "(Ljava/lang/String;Ljava/lang/Object;)I";
            case "kof_ui_window_set_title", "kof_ui_label_set_text", "kof_ui_button_set_text",
                    "kof_ui_input_set_text" -> "(ILjava/lang/String;)V";
            case "kof_ui_window_bind", "kof_ui_view_bind" -> "(II)V";
            case "kof_ui_window_set_size" -> "(III)V";
            case "kof_ui_column_new", "kof_ui_row_new" -> "(Ljava/util/ArrayList;)I";
            case "kof_ui_view_new" -> "(I)I";
            case "kof_ui_style_new" -> "(IIII)I";
            case "kof_ui_window_set_theme", "kof_ui_label_set_font_size", "kof_ui_label_set_bold",
                    "kof_ui_label_set_color" -> "(II)V";
            case "kof_ui_label_font_size", "kof_ui_label_bold", "kof_ui_label_color" -> "(I)I";
            case "kof_ui_box_new", "kof_ui_stack_new",
                    "kof_ui_wrap_new", "kof_ui_center_new" -> "(Ljava/util/ArrayList;)I";
            case "kof_ui_grid_new", "kof_ui_align_new" -> "(ILjava/util/ArrayList;)I";
            case "kof_ui_spacer_new" -> "(I)I";
            // ── Component Core (docs/ui/architecture.md) ──
            case "kof_ui_component_new" -> "(I)I";
            case "kof_ui_component_state_get" -> "(I)I";
            case "kof_ui_component_state_set" -> "(II)V";
            case "kof_ui_component_view", "kof_ui_component_on_mount",
                    "kof_ui_component_on_dispose", "kof_ui_component_effect" -> "(ILjava/lang/Object;)V";
            case "kof_ui_component_on" -> "(ILjava/lang/String;Ljava/lang/Object;)V";
            case "kof_ui_component_bind" -> "(II)V";
            case "kof_ui_component_remove", "kof_ui_component_mount",
                    "kof_ui_component_unmount", "kof_ui_flush_ui" -> "(I)V";
            case "kof_ui_nodes_live" -> "()I";
            case "kof_ui_event_type" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_ui_emit" -> "(ILjava/lang/String;)V";
            case "kof_ui_event_stop" -> "(Ljava/lang/Object;)V";
            case "kof_ui_store_new" -> "(I)I";
            case "kof_ui_store_get" -> "(I)I";
            case "kof_ui_store_set" -> "(II)V";
            case "kof_ui_store_subscribe", "kof_ui_store_unsubscribe" -> "(ILjava/lang/Object;)V";
            case "kof_ui_stores_live" -> "()I";
            // Fase 7: Router (no-ops JVM — UI é KofJS)
            case "kof_ui_route_register" -> "(Ljava/lang/String;I)V";
            case "kof_ui_router_go1" -> "(Ljava/lang/String;)Z";
            case "kof_ui_router_go2" -> "(Ljava/lang/String;Ljava/lang/String;)Z";
            case "kof_ui_router_replace1" -> "(Ljava/lang/String;)Z";
            case "kof_ui_router_replace2" -> "(Ljava/lang/String;Ljava/lang/String;)Z";
            case "kof_ui_router_back", "kof_ui_router_forward" -> "()Z";
            case "kof_ui_router_param", "kof_ui_router_current" -> "()Ljava/lang/String;";
            case "kof_ui_router_depth" -> "()I";
            case "kof_ui_window_title", "kof_ui_label_text", "kof_ui_button_text", "kof_ui_input_text"
                    -> "(I)Ljava/lang/String;";
            case "kof_ui_window_show", "kof_ui_window_close", "kof_ui_label_remove", "kof_ui_button_remove",
                    "kof_ui_input_remove", "kof_ui_view_remove", "kof_ui_link_remove",
                    "kof_ui_image_remove", "kof_ui_icon_remove" -> "(I)V";
            case "kof_ui_link_new" -> "(Ljava/lang/String;Ljava/lang/String;)I";
            case "kof_ui_image_new" -> "(Ljava/lang/String;)I";
            case "kof_ui_icon_new" -> "(Ljava/lang/String;)I";
            case "kof_ui_icon_new_size" -> "(Ljava/lang/String;I)I";
            case "kof_ui_font_new" -> "(Ljava/lang/String;I)I";
            case "kof_ui_font_new_bold" -> "(Ljava/lang/String;IZ)I";
            case "kof_ui_widget_set_font" -> "(II)V";
            case "kof_ui_widget_font" -> "(I)I";
            case "kof_ui_link_set_text", "kof_ui_link_set_url", "kof_ui_image_set_src",
                    "kof_ui_icon_set_name" -> "(ILjava/lang/String;)V";
            case "kof_ui_link_text", "kof_ui_link_url", "kof_ui_image_src", "kof_ui_icon_name"
                    -> "(I)Ljava/lang/String;";
            case "kof_ui_icon_size" -> "(I)I";
            case "kof_ui_icon_set_size" -> "(II)V";
            case "kof_io_dir_list" -> "(Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_web_app_new" -> "()Ljava/lang/String;";
            case "kof_web_route" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_sse_route" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_ws_route" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_use" -> "(Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_listen" -> "(Ljava/lang/String;I)V";
            case "kof_web_listen_secure" -> "(Ljava/lang/String;I)V";
            case "kof_web_serve_dir" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";
            case "kof_web_health" -> "(Ljava/lang/String;Ljava/lang/String;)V";
            case "kof_web_configure" -> "(Ljava/lang/String;Ljava/lang/String;I)V";
            case "kof_web_stats" -> "(Ljava/lang/String;)Ljava/lang/String;";
            // ── kof.media: imagem / áudio / microfone ──
            case "kof_media_image_open", "kof_media_audio_open_wav", "kof_media_video_open"
                    -> "(Ljava/lang/String;)I";
            case "kof_media_image_width", "kof_media_image_height",
                    "kof_media_audio_sample_rate", "kof_media_audio_duration_ms",
                    "kof_media_video_size", "kof_media_video_duration_ms",
                    "kof_media_mic_record" -> "(I)I";
            case "kof_media_image_save", "kof_media_audio_save_wav" -> "(ILjava/lang/String;)I";
            case "kof_media_image_format", "kof_media_image_data_uri" -> "(I)Ljava/lang/String;";
            case "kof_media_image_save_fmt" -> "(ILjava/lang/String;Ljava/lang/String;)I";
            case "kof_media_image_bytes", "kof_media_audio_pcm_bytes" -> "(I)[I";
            case "kof_media_image_bytes_fmt" -> "(ILjava/lang/String;)[I";
            case "kof_media_video_bytes" -> "(I)[I";
            case "kof_media_image_close", "kof_media_video_close" -> "(I)V";
            case "kof_media_video_path", "kof_media_video_format" -> "(I)Ljava/lang/String;";
            case "kof_media_audio_from_pcm_bytes" -> "([III)I";
            case "kof_media_mic_list" -> "()Ljava/util/ArrayList;";
            case "kof_web_port" -> "(Ljava/lang/String;)I";
            case "kof_web_close" -> "(Ljava/lang/String;)V";
            case "kof_web_param", "kof_web_query", "kof_web_header"
                    -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_web_body", "kof_web_method", "kof_web_path" -> "()Ljava/lang/String;";
            case "kof_web_ws_message" -> "()Ljava/lang/String;";
            case "kof_web_ws_send" -> "(Ljava/lang/String;)V";
            case "kof_web_sse_send" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_web_status" -> "(ILjava/lang/String;)Ljava/lang/String;";
            case "kof_web_header_set" -> "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_config_get", "kof_config_env", "kof_config_required" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_http_get", "kof_http_delete", "kof_http_options" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_http_get_headers", "kof_http_delete_headers", "kof_http_options_headers"
                    -> "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_http_post", "kof_http_put", "kof_http_patch"
                    -> "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_http_post_headers", "kof_http_put_headers", "kof_http_patch_headers"
                    -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_http_status" -> "(Ljava/lang/String;)I";
            case "kof_http_timeout_set", "kof_http_retry_set", "kof_http_circuit_set" -> "(I)V";
            case "kof_mq_publish", "kof_mq_push" -> "(Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_mq_subscribe", "kof_mq_unsubscribe" -> "(Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_mq_queue" -> "()Ljava/lang/String;";
            case "kof_mq_pop" -> "(Ljava/lang/String;)Ljava/lang/Object;";
            case "kof_mq_queue_size" -> "(Ljava/lang/String;)I";
            case "kof_time_sleep" -> "(I)V";
            case "kof_time_now" -> "()J";
            case "kof_time_interval" -> "(ILjava/lang/Object;)Ljava/lang/String;";
            case "kof_time_cancel" -> "(Ljava/lang/String;)V";
            case "kof_scheduler_every" -> "(ILjava/lang/Object;)Ljava/lang/String;";
            case "kof_scheduler_at" -> "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;";
            case "kof_scheduler_cancel" -> "(Ljava/lang/String;)V";
            case "kof_config_str" -> "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_config_has" -> "(Ljava/lang/String;)I";
            case "kof_config_int", "kof_config_bool" -> "(Ljava/lang/String;I)I";
            case "kof_config_long" -> "(Ljava/lang/String;J)J";
            case "kof_cache_get" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_cache_set" -> "(Ljava/lang/String;Ljava/lang/String;)V";
            case "kof_cache_set_ttl" -> "(Ljava/lang/String;Ljava/lang/String;I)V";
            case "kof_cache_ttl" -> "(Ljava/lang/String;)I";
            case "kof_cache_delete" -> "(Ljava/lang/String;)V";
            case "kof_cache_clear" -> "()V";
            case "kof_vk_available" -> "()Z";
            case "kof_vk_fail_reason" -> "()Ljava/lang/String;";
            case "kof_vk_dispatch" -> "([I[I[IIII)I";
            case "kof_vk_dispatch64" -> "([J[J[JIII)I";
            case "kof_mv64_set_shape" -> "(II)I";
            case "kof_mv64_load_w" -> "([JII)I";
            case "kof_mv64_matvec" -> "([J[JII)I";
            case "kof_mv64_wput" -> "(I[JII)I";
            case "kof_mv64_wrun" -> "(I[J[JIIJ)I";
            case "kof_mv64_wput32" -> "(I[III)I";
            case "kof_mv64_wrun32" -> "(I[J[JIIJ)I";
            case "kof_mv64_wputsp" -> "(I[I[III)I";
            case "kof_mv64_wrunsp" -> "(I[J[JIIJ)I";
            case "kof_log_debug", "kof_log_info", "kof_log_warn", "kof_log_error"
                    -> "(Ljava/lang/String;)V";
            case "kof_db_connect" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_db_connect2" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_db_close" -> "(Ljava/lang/String;)V";
            case "kof_db_execute" -> "(Ljava/lang/String;Ljava/lang/String;)I";
            case "kof_db_execute1" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)I";
            case "kof_db_execute2" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)I";
            case "kof_db_execute3" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)I";
            case "kof_db_execute4" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)I";
            case "kof_db_query0" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_db_query1" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_db_query2" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_db_query3" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_db_query4" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_db_transaction" -> "(Ljava/lang/Object;)V";
            case "kof_string_to_int" -> "(Ljava/lang/String;)I";
            case "kof_string_to_long" -> "(Ljava/lang/String;)J";
            case "kof_string_to_double" -> "(Ljava/lang/String;)D";
            case "kof_string_to_float" -> "(Ljava/lang/String;)F";
            case "kof_orm_create" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z";
            case "kof_orm_save" -> "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;";
            case "kof_orm_find" -> "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;";
            case "kof_orm_all" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_orm_where" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_orm_delete" -> "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z";
            case "kof_orm_count" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)J";
            case "kof_orm_migrate" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z";
            case "kof_orm_where_op" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_orm_save_all" -> "(Ljava/lang/String;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;)Z";
            case "kof_orm_page" -> "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_orm_count_where" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)J";
            case "kof_orm_delete_all" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z";
            // ── kof.security (docs/security.md §5) ───────────────────
            case "kof_sec_sha256", "kof_sec_sha512", "kof_sec_redact", "kof_sec_secret_get",
                    "kof_sec_password_hash", "kof_sec_auth_user" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_sec_hmac_sha256", "kof_sec_aesgcm_encrypt", "kof_sec_aesgcm_decrypt",
                    "kof_sec_secret_get_default", "kof_sec_jwt_create", "kof_sec_jwt_verify"
                    -> "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_sec_jwt_create_ttl" -> "(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;";
            case "kof_sec_jwt_verify_iss_aud"
                    -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_sec_random_hex" -> "(I)Ljava/lang/String;";
            case "kof_sec_random_int" -> "(I)I";
            case "kof_sec_constant_time_equals", "kof_sec_password_verify", "kof_sec_cors_allowed"
                    -> "(Ljava/lang/String;Ljava/lang/String;)Z";
            case "kof_sec_password_needs_rehash", "kof_sec_csrf_valid",
                    "kof_sec_auth_secret", "kof_sec_auth_has_role", "kof_sec_auth_has_permission"
                    -> "(Ljava/lang/String;)Z";
            case "kof_sec_auth_authenticated" -> "()Z";
            // ── kof.validation (G4) ─────────────────────────────────────
            case "kof_validation_required", "kof_validation_notBlank", "kof_validation_isEmail",
                    "kof_validation_isUrl", "kof_validation_isInt", "kof_validation_isLong" -> "(Ljava/lang/String;)Z";
            case "kof_validation_minLength", "kof_validation_maxLength" -> "(Ljava/lang/String;I)Z";
            case "kof_validation_lengthBetween" -> "(Ljava/lang/String;II)Z";
            case "kof_validation_matches" -> "(Ljava/lang/String;Ljava/lang/String;)Z";
            case "kof_validation_inRange" -> "(III)Z";
            case "kof_validation_min", "kof_validation_max" -> "(II)Z";
            // ── kof.observability (G5) ────────────────────────────────
            case "kof_observability_health", "kof_observability_request_id", "kof_observability_correlation_id",
                    "kof_observability_trace_id", "kof_observability_span_id",
                    "kof_observability_metrics" -> "()Ljava/lang/String;";
            case "kof_observability_readiness", "kof_observability_liveness" -> "()Z";
            case "kof_observability_counter" -> "(Ljava/lang/String;)I";
            case "kof_observability_increment" -> "(Ljava/lang/String;I)I";
            case "kof_observability_gauge", "kof_observability_histogram" -> "(Ljava/lang/String;I)V";
            case "kof_observability_span_start", "kof_observability_span_end" -> "(Ljava/lang/String;)Ljava/lang/String;";
            // ── kof.security G9 (rate limiting / sessions / API keys) ──
            case "kof_sec_rate_limit" -> "(Ljava/lang/String;II)Z";
            case "kof_sec_session_create" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_sec_session_get" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_sec_session_destroy" -> "(Ljava/lang/String;)Z";
            case "kof_sec_api_key_generate" -> "()Ljava/lang/String;";
            case "kof_sec_api_key_valid" -> "(Ljava/lang/String;)Z";
            case "kof_enum_value_of" -> "(Ljava/util/List;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_list_map", "kof_list_filter" -> "(Ljava/util/ArrayList;Ljava/lang/Object;)Ljava/util/ArrayList;";
            case "kof_list_reduce" -> "(Ljava/util/ArrayList;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
            case "kof_spawn_result", "kof_await" -> "(Ljava/lang/Object;)Ljava/lang/Object;";
            case "kof_poll" -> "(Ljava/lang/Object;)Ljava/lang/Object;";
            case "kof_done", "kof_cancel" -> "(Ljava/lang/Object;)Z";
            case "kof_cancelled" -> "()Z";
            case "kof_select_any" -> "(Ljava/util/List;)Ljava/lang/Object;";
            case "kof_await_timeout" -> "(Ljava/lang/Object;I)Ljava/lang/Object;";
            case "kof_tetris_run" -> "()V";
            case "kof_sec_jwt_secret", "kof_sec_csrf_token", "kof_sec_csp_header",
                    "kof_sec_hsts_header", "kof_sec_content_type_options_header",
                    "kof_sec_frame_header", "kof_sec_referrer_header", "kof_sec_auth_token",
                    "kof_sec_auth_claims" -> "()Ljava/lang/String;";
            default -> "(Ljava/lang/String;)Ljava/lang/Object;";
        };
    }

    static String callReturnDescriptor(String methodName) {
        return switch (methodName) {
            case "kof_json_decode_int", "kof_json_decode_bool" -> "I";
            case "kof_json_decode_long", "kof_now" -> "J";
            case "kof_ffi_i", "kof_ffi_si", "kof_ffi_ai" -> "I";
            case "kof_ffi_dd" -> "D";
            case "kof_json_decode_float" -> "F";
            case "kof_json_decode_double" -> "D";
            case "kof_json_decode_int_list", "kof_json_decode_string_list", "kof_json_decode_list"
                    -> "Ljava/util/ArrayList;";
            case "kof_json_decode_object_list" -> "Ljava/util/ArrayList;";
            case "kof_json_decode_int_array", "kof_json_decode_bool_array" -> "[I";
            case "kof_json_decode_long_array" -> "[J";
            case "kof_json_decode_double_array" -> "[D";
            case "kof_json_decode_string_array" -> "[Ljava/lang/String;";
            case "kof_json_decode_string", "kof_read_line", "kof_read_file" -> "Ljava/lang/String;";
            case "kof_write_file" -> "I";
            case "kof_io_file_exists", "kof_io_file_is_file", "kof_io_file_is_dir",
                    "kof_io_write_text", "kof_io_append_text", "kof_io_write_bytes", "kof_io_append_bytes",
                    "kof_io_delete", "kof_io_dir_create", "kof_io_dir_create_dirs", "kof_io_dir_delete",
                    "kof_io_path_is_absolute" -> "I";
            case "kof_io_read_text", "kof_io_file_name", "kof_io_path_parent", "kof_io_path_file_name",
                    "kof_io_path_extension", "kof_io_path_normalize", "kof_io_path_resolve",
                    "kof_io_path_to_absolute" -> "Ljava/lang/String;";
            case "kof_process_run" -> "Ldev/kof/runtime/KofRuntime$ProcessResult;";
            case "kof_process_exit" -> "V";
            case "kof_args_list" -> "Ljava/util/ArrayList;";
            case "kof_io_read_bytes" -> "[I";
            case "kof_io_read_range", "kof_io_read_range_path" -> "[I";
            case "kof_io_file_size" -> "J";
            case "kof_io_dir_list" -> "Ljava/util/ArrayList;";
            case "kof_web_app_new", "kof_web_param", "kof_web_query", "kof_web_header",
                    "kof_web_body", "kof_web_method", "kof_web_path",
                    "kof_web_status", "kof_web_header_set",
                    "kof_scheduler_every", "kof_scheduler_at" -> "Ljava/lang/String;";
            case "kof_config_get", "kof_config_env", "kof_config_str", "kof_config_required" -> "Ljava/lang/String;";
            case "kof_cache_get" -> "Ljava/lang/String;";
            case "kof_cache_set", "kof_cache_set_ttl", "kof_cache_delete", "kof_cache_clear" -> "V";
            case "kof_cache_ttl" -> "I";
            case "kof_vk_available" -> "Z";
            case "kof_vk_fail_reason" -> "Ljava/lang/String;";
            case "kof_vk_dispatch" -> "I";
            case "kof_vk_dispatch64" -> "I";
            case "kof_mv64_set_shape" -> "I";
            case "kof_mv64_load_w" -> "I";
            case "kof_mv64_matvec" -> "I";
            case "kof_mv64_wput" -> "I";
            case "kof_mv64_wrun" -> "I";
            case "kof_mv64_wput32" -> "I";
            case "kof_mv64_wrun32" -> "I";
            case "kof_mv64_wputsp" -> "I";
            case "kof_mv64_wrunsp" -> "I";
            case "kof_http_get", "kof_http_get_headers", "kof_http_delete", "kof_http_delete_headers",
                    "kof_http_options", "kof_http_options_headers", "kof_http_post", "kof_http_post_headers",
                    "kof_http_put", "kof_http_put_headers", "kof_http_patch", "kof_http_patch_headers"
                    -> "Ljava/lang/String;";
            case "kof_http_status", "kof_mq_queue_size" -> "I";
            case "kof_mq_queue" -> "Ljava/lang/String;";
            case "kof_mq_pop" -> "Ljava/lang/Object;";
            case "kof_web_sse_route", "kof_web_ws_route", "kof_http_timeout_set",
                    "kof_http_retry_set", "kof_http_circuit_set",
                    "kof_mq_publish", "kof_mq_subscribe", "kof_mq_unsubscribe",
                    "kof_mq_push", "kof_time_sleep", "kof_time_cancel", "kof_scheduler_cancel" -> "V";
            case "kof_time_now" -> "J";
            case "kof_time_interval" -> "Ljava/lang/String;";
            case "kof_config_int", "kof_config_bool", "kof_config_has" -> "I";
            case "kof_config_long" -> "J";
            case "kof_web_configure" -> "V";
            case "kof_web_stats" -> "Ljava/lang/String;";
            case "kof_log_debug", "kof_log_info", "kof_log_warn", "kof_log_error" -> "V";
            case "kof_db_connect", "kof_db_connect2" -> "Ljava/lang/String;";
            case "kof_db_close", "kof_db_transaction" -> "V";
            case "kof_db_execute", "kof_db_execute1", "kof_db_execute2", "kof_db_execute3", "kof_db_execute4" -> "I";
            case "kof_db_query0", "kof_db_query1", "kof_db_query2", "kof_db_query3", "kof_db_query4",
                    "kof_orm_all", "kof_orm_where" -> "Ljava/util/ArrayList;";
            case "kof_string_to_int" -> "I";
            case "kof_string_to_long" -> "J";
            case "kof_string_to_double" -> "D";
            case "kof_string_to_float" -> "F";
            case "kof_orm_create", "kof_orm_delete", "kof_orm_migrate" -> "Z";
            case "kof_orm_save", "kof_orm_find" -> "Ljava/lang/Object;";
            case "kof_orm_count" -> "J";
             case "kof_web_port" -> "I";
             case "kof_ui_label_font_size", "kof_ui_label_bold", "kof_ui_label_color" -> "I";
             case "kof_ui_component_state_get", "kof_ui_component_new", "kof_ui_nodes_live" -> "I";
             case "kof_ui_component_state_set", "kof_ui_component_view", "kof_ui_component_on_mount",
                     "kof_ui_component_on_dispose", "kof_ui_component_effect", "kof_ui_component_on",
                     "kof_ui_component_bind", "kof_ui_component_remove", "kof_ui_component_mount",
                     "kof_ui_component_unmount", "kof_ui_flush_ui", "kof_ui_emit",
                     "kof_ui_event_stop", "kof_ui_store_set", "kof_ui_store_subscribe",
                     "kof_ui_store_unsubscribe" -> "V";
             case "kof_ui_store_get", "kof_ui_store_new", "kof_ui_stores_live" -> "I";
             case "kof_ui_router_go1", "kof_ui_router_go2", "kof_ui_router_replace1",
                     "kof_ui_router_replace2", "kof_ui_router_back", "kof_ui_router_forward" -> "Z";
             case "kof_ui_router_param", "kof_ui_router_current" -> "Ljava/lang/String;";
             case "kof_ui_label_set_font_size", "kof_ui_label_set_bold", "kof_ui_label_set_color",
                     "kof_ui_window_set_theme" -> "V";
            // ── kof.security (docs/security.md §5) ───────────────────
            case "kof_sec_sha256", "kof_sec_sha512", "kof_sec_hmac_sha256", "kof_sec_redact",
                    "kof_sec_secret_get", "kof_sec_secret_get_default", "kof_sec_password_hash",
                    "kof_sec_aesgcm_encrypt", "kof_sec_aesgcm_decrypt", "kof_sec_jwt_create",
                    "kof_sec_jwt_create_ttl", "kof_sec_jwt_verify", "kof_sec_jwt_verify_iss_aud",
                    "kof_sec_jwt_secret", "kof_sec_random_hex", "kof_sec_csrf_token",
                    "kof_sec_csp_header", "kof_sec_hsts_header", "kof_sec_content_type_options_header",
                    "kof_sec_frame_header", "kof_sec_referrer_header", "kof_sec_auth_token",
                    "kof_sec_auth_claims", "kof_sec_auth_user" -> "Ljava/lang/String;";
            case "kof_sec_random_int", "kof_sec_constant_time_equals", "kof_sec_password_verify",
                    "kof_sec_password_needs_rehash", "kof_sec_csrf_valid", "kof_sec_cors_allowed",
                    "kof_sec_auth_secret", "kof_sec_auth_authenticated", "kof_sec_auth_has_role",
                    "kof_sec_auth_has_permission" -> "I";
            // ── kof.validation (G4) ─────────────────────────────────────
            case "kof_validation_required", "kof_validation_notBlank", "kof_validation_isEmail",
                    "kof_validation_isUrl", "kof_validation_isInt", "kof_validation_isLong",
                    "kof_validation_minLength", "kof_validation_maxLength", "kof_validation_lengthBetween",
                    "kof_validation_matches", "kof_validation_inRange", "kof_validation_min",
                    "kof_validation_max" -> "I";
            // ── kof.observability (G5) ────────────────────────────────
            case "kof_observability_health", "kof_observability_request_id", "kof_observability_correlation_id",
                    "kof_observability_trace_id", "kof_observability_span_id",
                    "kof_observability_metrics",
                    "kof_observability_span_start", "kof_observability_span_end" -> "Ljava/lang/String;";
            case "kof_observability_readiness", "kof_observability_liveness", "kof_observability_counter", "kof_observability_increment" -> "I";
            case "kof_observability_gauge", "kof_observability_histogram" -> "V";
            // ── kof.media ─────────────────────────────────────────────
            case "kof_media_image_open", "kof_media_image_width", "kof_media_image_height",
                    "kof_media_image_save", "kof_media_audio_open_wav",
                    "kof_media_audio_sample_rate", "kof_media_audio_duration_ms",
                    "kof_media_audio_save_wav", "kof_media_audio_from_pcm_bytes",
                    "kof_media_mic_record", "kof_media_video_open",
                    "kof_media_video_size", "kof_media_video_duration_ms" -> "I";
            case "kof_media_image_format", "kof_media_image_data_uri",
                    "kof_media_video_path", "kof_media_video_format" -> "Ljava/lang/String;";
            case "kof_media_image_bytes", "kof_media_image_bytes_fmt",
                    "kof_media_audio_pcm_bytes", "kof_media_video_bytes" -> "[I";
            case "kof_media_image_close", "kof_media_video_close", "kof_web_serve_dir" -> "V";
            case "kof_media_mic_list" -> "Ljava/util/ArrayList;";
            case "kof_list_map", "kof_list_filter" -> "Ljava/util/ArrayList;";
            case "kof_list_reduce" -> "Ljava/lang/Object;";
            // ── kof.security G9 (rate limiting / sessions / API keys) ──
            case "kof_sec_session_create", "kof_sec_api_key_generate" -> "Ljava/lang/String;";
            case "kof_sec_rate_limit", "kof_sec_session_destroy", "kof_sec_api_key_valid" -> "I";
            case "kof_sec_session_get", "kof_enum_value_of" -> "Ljava/lang/String;";
            case "kof_spawn_result", "kof_await", "kof_poll" -> "Ljava/lang/Object;";
            case "kof_await_timeout" -> "Ljava/lang/Object;";
            case "kof_done", "kof_cancel", "kof_cancelled" -> "I";
            case "kof_select_any" -> "Ljava/lang/Object;";
            case "kof_tetris_run" -> "V";
            default -> "Ljava/lang/Object;";
        };
    }

    private static String source(List<IRClass> classes, boolean usesVk, boolean usesExtern) {
        StringBuilder decoders = new StringBuilder();
        for (IRClass clazz : classes) {
            String internal = clazz.name();
            if (internal == null || internal.isBlank() || internal.equals("java/lang/Object")) continue;
            if ("Main".equals(internal) || internal.endsWith("/Main")) continue;
            String javaName = internal.replace('/', '.');
            String mangle = javaName.replace('.', '_');
            decoders.append("""
                        public static Object kof_json_decode_%s(String json) throws Exception {
                            return kof_json_decode_object(json, Class.forName("%s"));
                        }

                    """.formatted(mangle, javaName));
        }
                return sourceCore(decoders.toString())
                + JvmWebRuntime.source()
                + JvmMediaRuntime.source()
                + """
                public static void kof_web_listen(String appId, int port) {
                    WebApp app = kof_web_app(appId);
                    if (app.serverSocket != null) {
                        throw new IllegalStateException("app already listening: " + appId);
                    }
                    try {
                        app.serverSocket = new java.net.ServerSocket(port, 64,
                                java.net.InetAddress.getByName("0.0.0.0"));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("cannot bind port " + port + ": " + e.getMessage(), e);
                    }
                    app.running = true;
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> kof_web_close(appId)));
                    while (app.running) {
                        try {
                            java.net.Socket client = app.serverSocket.accept();
                            // 15s default protects HTTP `readRequest` against
                            // client half-open sockets. WS raises this after
                            // the handshake.
                            client.setSoTimeout(15000);
                            Thread.startVirtualThread(() -> kof_web_handle(app, client));
                        } catch (java.io.IOException e) {
                            if (!app.running) break;
                        }
                    }
                }

                public static void kof_web_listen_secure(String appId, int port) {
                    WebApp app = kof_web_app(appId);
                    if (app.serverSocket != null) {
                        throw new IllegalStateException("app already listening: " + appId);
                    }
                    try {
                        javax.net.ssl.SSLContext ctx = kof_web_ssl_context();
                        javax.net.ssl.SSLServerSocketFactory ssf = ctx.getServerSocketFactory();
                        javax.net.ssl.SSLServerSocket ss = (javax.net.ssl.SSLServerSocket) ssf.createServerSocket(port, 64,
                                java.net.InetAddress.getByName("0.0.0.0"));
                        ss.setNeedClientAuth(false);
                        app.serverSocket = ss;
                    } catch (Exception e) {
                        throw new RuntimeException("cannot bind TLS port " + port + ": " + e.getMessage(), e);
                    }
                    app.running = true;
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> kof_web_close(appId)));
                    while (app.running) {
                        try {
                            java.net.Socket client = app.serverSocket.accept();
                            client.setSoTimeout(15000);
                            Thread.startVirtualThread(() -> kof_web_handle(app, client));
                        } catch (java.io.IOException e) {
                            if (!app.running) break;
                        }
                    }
                }

                private static javax.net.ssl.SSLContext kof_web_ssl_context() throws Exception {
                    String ksPath = System.getProperty("java.io.tmpdir") + "/kof-tls-" + System.nanoTime() + ".jks";
                    try {
                        Process p = new ProcessBuilder("keytool", "-genkeypair", "-alias", "kof", "-keyalg", "RSA", "-keysize", "2048", "-validity", "365", "-dname", "CN=localhost, OU=Kof, O=Kof, L=Test, ST=Test, C=US", "-ext", "SAN=IP:127.0.0.1,DNS:localhost", "-keystore", ksPath, "-storepass", "changeit", "-keypass", "changeit", "-storetype", "JKS", "-noprompt").redirectErrorStream(true).start();
                        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        int ec = p.waitFor();
                        if (ec != 0) throw new RuntimeException("keytool failed: " + out);
                        java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(ksPath)) { ks.load(fis, "changeit".toCharArray()); }
                        javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance("SunX509");
                        kmf.init(ks, "changeit".toCharArray());
                        javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        }};
                        javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
                        ctx.init(kmf.getKeyManagers(), trustAll, new java.security.SecureRandom());
                        return ctx;
                    } finally {
                        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(ksPath)); } catch (Exception ignored) {}
                    }
                }

                private static final java.util.concurrent.ExecutorService KOF_SSE_HANDLERS =
                        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

                private static void kof_web_handle(WebApp app, java.net.Socket client) {
                    if (app.activeConnections.incrementAndGet() > app.maxConnections) {
                        app.activeConnections.decrementAndGet();
                        try (client) {
                            client.getOutputStream().write(("HTTP/1.1 503 Service Unavailable\\r\\n"
                                    + "Content-Length: 0\\r\\n"
                                    + "Connection: close\\r\\n"
                                    + "\\r\\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            client.getOutputStream().flush();
                            // Drain the rejected request so close sends FIN, not RST.
                            client.setSoTimeout(200);
                            byte[] drain = new byte[4096];
                            try {
                                while (client.getInputStream().read(drain) >= 0) {
                                }
                            } catch (java.net.SocketTimeoutException ignored) {
                            }
                        } catch (java.io.IOException ignored) {
                        }
                        return;
                    }
                    try {
                        try (client) {
                            WebRequest req = readRequest(client.getInputStream());
                            WebDispatchResult result = kof_web_dispatch(app, req);
                            if (result.kind == RouteKind.SSE) {
                                java.io.OutputStream out = client.getOutputStream();
                                out.write(("HTTP/1.1 200 OK\\r\\n"
                                        + "Content-Type: text/event-stream\\r\\n"
                                        + "Cache-Control: no-cache\\r\\n"
                                        + "Connection: keep-alive\\r\\n"
                                        + "X-Accel-Buffering: no\\r\\n"
                                        + "\\r\\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                out.flush();
                                SseConnection sse = new SseConnection(out);
                                SSE_CONNECTIONS_ACTIVE.incrementAndGet();
                                try {
                                    java.util.concurrent.Future<?> handler = KOF_SSE_HANDLERS.submit(() -> {
                                        try {
                                            KOF_WEB_REQUEST.set(req);
                                            KOF_SSE_SENDER.set(sse);
                                            try {
                                                kof_web_invoke(result.route.handler, sse);
                                            } finally {
                                                KOF_SSE_SENDER.remove();
                                                KOF_WEB_REQUEST.remove();
                                            }
                                        } catch (Exception e) {
                                            System.err.println("kof web sse handler error: "
                                                    + e.getMessage());
                                        } finally {
                                            sse.close();
                                        }
                                    });
                                    try {
                                        handler.get(KofRuntime.idleMs.get() * 4L,
                                                java.util.concurrent.TimeUnit.MILLISECONDS);
                                    } catch (java.util.concurrent.TimeoutException timeout) {
                                        System.err.println("kof web sse handler timeout");
                                        sse.close();
                                        handler.cancel(true);
                                    }
                                } finally {
                                    SSE_CONNECTIONS_ACTIVE.decrementAndGet();
                                }
                                return;
                            }
                            if (result.kind == RouteKind.WS) {
                                kof_web_ws_handshake(result, req, client);
                                return;
                            }
                            client.getOutputStream().write(result.response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            if (result.body() != null) {
                                // arquivo estático: head na response + bytes crus
                                client.getOutputStream().write(result.body());
                            }
                            client.getOutputStream().flush();
                        } catch (Exception e) {
                            System.err.println("kof web connection error: " + e.getMessage());
                        }
                    } finally {
                        app.activeConnections.decrementAndGet();
                    }
                }

                private static final java.util.concurrent.atomic.AtomicInteger idleMs =
                        new java.util.concurrent.atomic.AtomicInteger(300_000);

                private static void kof_web_ws_handshake(WebDispatchResult result, WebRequest req,
                        java.net.Socket client)
                        throws java.io.IOException {
                    String upgradeVal = req.headers.get("upgrade");
                    String connectionVal = req.headers.get("connection");
                    String version = req.headers.get("sec-websocket-version");
                    String key = req.headers.get("sec-websocket-key");

                    boolean hasUpgrade = containsToken(upgradeVal, "websocket");
                    boolean hasConnection = containsToken(connectionVal, "upgrade");
                    boolean valid = hasUpgrade
                            && hasConnection
                            && "13".equals(version)
                            && key != null;

                    if (!valid) {
                        java.io.OutputStream out = client.getOutputStream();
                        out.write("HTTP/1.1 400 Bad Request\\r\\n\\r\\n"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        out.flush();
                        return;
                    }

                    String accept = wsAccept(key);
                    java.io.OutputStream out = client.getOutputStream();
                    out.write(("HTTP/1.1 101 Switching Protocols\\r\\n"
                            + "Upgrade: websocket\\r\\n"
                            + "Connection: Upgrade\\r\\n"
                            + "Sec-WebSocket-Accept: " + accept + "\\r\\n"
                            + "\\r\\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    out.flush();

                    // Frame loop (PR4): handles RFC 6455 frame codec.
                    // PING -> PONG; CLOSE -> ack CLOSE; oversize -> CLOSE 1009.
                    // TEXT -> invokes the Kof handler once per message.
                    client.setSoTimeout(idleMs.get());
                    WsConnection conn = new WsConnection(client.getOutputStream());
                    WS_CONNECTIONS_ACTIVE.incrementAndGet();
                    try {
                        frameLoop: while (true) {
                            // 1) Read until we have at least 2 bytes for the header
                            int headerBytes = 0;
                            byte[] headerBuf = new byte[2];
                            while (headerBytes < 2) {
                                int n = client.getInputStream().read(headerBuf, headerBytes, 2 - headerBytes);
                                if (n < 0) break frameLoop;
                                headerBytes += n;
                            }
                            boolean fin = (headerBuf[0] & 0x80) != 0;
                            int op = headerBuf[0] & 0x0F;
                            boolean masked = (headerBuf[1] & 0x80) != 0;
                            long plen = headerBuf[1] & 0x7F;
                            // 2) Extended length
                            if (plen == 126) {
                                byte[] ext = new byte[2];
                                readFully(client.getInputStream(), ext);
                                plen = ((long)(ext[0] & 0xFF) << 8) | (ext[1] & 0xFF);
                            } else if (plen == 127) {
                                byte[] ext = new byte[8];
                                readFully(client.getInputStream(), ext);
                                plen = 0;
                                for (int i = 0; i < 8; i++) plen = (plen << 8) | (ext[i] & 0xFF);
                            }
                            if (!fin) {
                                conn.close(WsFrame.CLOSE_UNSUPPORTED, "fragmented frames not supported");
                                break frameLoop;
                            }
                            // 3) Mask key (must be present on client->server)
                            byte[] mask = new byte[4];
                            if (masked) {
                                readFully(client.getInputStream(), mask);
                            } else {
                                conn.close(WsFrame.CLOSE_PROTOCOL_ERROR, "client frame must be masked");
                                break frameLoop;
                            }
                            // 4) Payload (with size cap)
                            if (plen > WsFrame.maxFrameBytes.get()) {
                                conn.close(WsFrame.CLOSE_TOO_BIG, "frame too large");
                                break frameLoop;
                            }
                            byte[] payload = new byte[(int) plen];
                            if (plen > 0) {
                                readFully(client.getInputStream(), payload);
                                if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                            }
                            // 5) React
                            if (op == 0x9 /* PING */) { conn.pong(payload); }
                            else if (op == 0xA /* PONG */) { /* ignore */ }
                            else if (op == 0x8 /* CLOSE */) { conn.close(1000, ""); break frameLoop; }
                            else if (op == 0x1 /* TEXT */) {
                                WS_MESSAGES_RECEIVED.incrementAndGet();
                                String text = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                                KOF_WEB_REQUEST.set(req);
                                KOF_WS_CONNECTION.set(conn);
                                KOF_WS_MESSAGE.set(text);
                                try {
                                    result.route.handler.getClass().getMethod("invoke").invoke(result.route.handler);
                                } catch (Exception e) {
                                    System.err.println("kof web ws handler error: " + e.getMessage());
                                } finally {
                                    KOF_WEB_REQUEST.remove();
                                    KOF_WS_CONNECTION.remove();
                                    KOF_WS_MESSAGE.remove();
                                }
                            }
                        }
                    } catch (java.net.SocketTimeoutException idle) {
                        // graceful close after idleMs
                    } catch (java.io.IOException eof) {
                        // client closed
                    } finally {
                        WS_CONNECTIONS_ACTIVE.decrementAndGet();
                    }
                }

                private static void readFully(java.io.InputStream in, byte[] buf) throws java.io.IOException {
                    int off = 0;
                    while (off < buf.length) {
                        int n = in.read(buf, off, buf.length - off);
                        if (n < 0) throw new java.io.IOException("EOF reading " + buf.length + " bytes (got " + off + ")");
                        off += n;
                    }
                }

                // Splits a comma-separated header value (RFC 7230 §3.2.2) into
                // trimmed tokens, returning true when any token equals the
                // expected name case-insensitively. Null/empty header -> false.
                // Used by WS handshake to validate Upgrade/Connection tokens
                // even when the same line carries unrelated tokens.
                private static boolean containsToken(String headerValue, String expected) {
                    if (headerValue == null) return false;
                    for (String token : headerValue.split(",")) {
                        if (token.trim().equalsIgnoreCase(expected)) return true;
                    }
                    return false;
                }

                private static WebDispatchResult kof_web_dispatch(WebApp app, WebRequest req) {
                    KOF_WEB_REQUEST.set(req);
                    KOF_WEB_STATUS.remove();
                    KOF_WEB_HEADERS.get().clear();
                    KOF_LOG_REQUEST_ID.set(kof_sec_random_hex(16));
                    try {
                        // app.health(path): built-in — responde antes dos
                        // middlewares (sondas de load balancer não passam por
                        // auth/middleware): estado de saúde em JSON.
                        for (String hp : app.healthPaths) {
                            if (req.path.equals(hp)) {
                                String resp = kof_web_build(200, "OK",
                                        "{\\"status\\": \\"" + kof_observability_health()
                                        + "\\","
                                        + "\\"ready\\": " + kof_observability_readiness()
                                        + ", \\"alive\\": " + kof_observability_liveness() + "}");
                                return new WebDispatchResult(RouteKind.HTTP, resp, null);
                            }
                        }
                        for (Object middleware : app.middlewares) {
                            Object result = kof_web_invoke(middleware, req);
                            if (result != null) {
                                Integer st = KOF_WEB_STATUS.get();
                                int code = st != null ? st : 200;
                                String text = kof_web_status_text(code);
                                String resp = kof_web_build(code, text, String.valueOf(result));
                                KOF_WEB_STATUS.remove();
                                KOF_WEB_HEADERS.get().clear();
                                return new WebDispatchResult(RouteKind.HTTP, resp, null);
                            }
                        }
                        for (WebRoute route : app.routes) {
                            if (route.kind == RouteKind.HTTP && !route.method.equals(req.method)) continue;
                            String[] pathSegs = req.path.split("/");
                            if (pathSegs.length != route.segments.length) continue;
                            boolean match = true;
                            java.util.Map<String, String> params = new java.util.HashMap<>();
                            for (int i = 0; i < pathSegs.length; i++) {
                                if (route.params[i]) {
                                    params.put(route.segments[i].substring(1), pathSegs[i]);
                                } else if (!route.segments[i].equals(pathSegs[i])) {
                                    match = false;
                                    break;
                                }
                            }
                            if (!match) continue;
                            req.params.putAll(params);
                            if (route.kind != RouteKind.HTTP) {
                                KOF_WEB_STATUS.remove();
                                KOF_WEB_HEADERS.get().clear();
                                return new WebDispatchResult(route.kind, null, route);
                            }
                            KOF_WEB_STATUS.remove();
                            KOF_WEB_HEADERS.get().clear();
                            Object result = kof_web_invoke(route.handler, req);
                            if (result == null) {
                                KOF_WEB_STATUS.remove();
                                KOF_WEB_HEADERS.get().clear();
                                return new WebDispatchResult(RouteKind.HTTP,
                                        kof_web_build(404, "Not Found", "{\\"error\\": \\"not found\\"}"), null);
                            }
                            Integer st2 = KOF_WEB_STATUS.get();
                            int code2 = st2 != null ? st2 : 200;
                            String text2 = kof_web_status_text(code2);
                            String resp2 = kof_web_build(code2, text2, String.valueOf(result));
                            KOF_WEB_STATUS.remove();
                            KOF_WEB_HEADERS.get().clear();
                            return new WebDispatchResult(RouteKind.HTTP, resp2, null);
                        }
                        // Arquivos estáticos (app.serveDir): fallback quando
                        // nenhuma rota dinâmica casa — conteúdo binário do
                        // disco com content-type e Range (vídeo navegável no
                        // browser), sem o app colar base64 em String.
                        if (kof_web_static_match(app, req.path) == 0) {
                            String staticMeta = kof_web_static_meta();
                            if (staticMeta != null) {
                                int sep = staticMeta.indexOf('|');
                                String mime = staticMeta.substring(0, sep);
                                long total = Long.parseLong(staticMeta.substring(sep + 1));
                                String range = req.header("range");
                                long start = 0, end = total - 1;
                                boolean ranged = false;
                                if (range != null && range.startsWith("bytes=")) {
                                    String spec = range.substring(6).split(",", 2)[0].trim();
                                    int dash = spec.indexOf('-');
                                    if (dash > 0) {
                                        String s = spec.substring(0, dash).trim();
                                        String e = spec.substring(dash + 1).trim();
                                        start = s.isEmpty()
                                                ? Math.max(0, total - Long.parseLong(e))
                                                : Long.parseLong(s);
                                        end = e.isEmpty()
                                                ? total - 1
                                                : Math.min(Long.parseLong(e), total - 1);
                                        ranged = true;
                                    }
                                }
                                if (ranged && (start > end || start >= total)) {
                                    String h416 = "HTTP/1.1 416 Range Not Satisfiable\\r\\n"
                                            + "Content-Range: bytes */" + total + "\\r\\n"
                                            + "Content-Length: 0\\r\\nConnection: close\\r\\n\\r\\n";
                                    return new WebDispatchResult(RouteKind.HTTP, h416, null);
                                }
                                byte[] staticBody = kof_web_static_read(start, end);
                                if (staticBody != null) {
                                    String head = (ranged ? "HTTP/1.1 206 Partial Content"
                                            : "HTTP/1.1 200 OK") + "\\r\\n"
                                            + "Content-Type: " + mime + "\\r\\n"
                                            + "Accept-Ranges: bytes\\r\\n"
                                            + "Cache-Control: public, max-age=86400\\r\\n"
                                            + (ranged
                                            ? "Content-Range: bytes " + start + "-"
                                                    + (start + staticBody.length - 1) + "/" + total + "\\r\\n"
                                            : "")
                                            + "Content-Length: " + staticBody.length + "\\r\\n"
                                            + "Connection: close\\r\\n\\r\\n";
                                    return new WebDispatchResult(
                                            RouteKind.HTTP, head, null, staticBody);
                                }
                            }
                            kof_web_static_done();
                        }
                        return new WebDispatchResult(RouteKind.HTTP,
                                kof_web_build(404, "Not Found", "{\\"error\\": \\"not found\\"}"), null);
                    } catch (Exception e) {
                        // handler lambda é invocado via reflection: a exceção
                        // real chega embrulhada em InvocationTargetException —
                        // sem desempacotar, o 500 diz só "InvocationTargetException"
                        // e esconde o diagnóstico (violation R6).
                        Throwable root = e;
                        while (root instanceof java.lang.reflect.InvocationTargetException
                                && root.getCause() != null) {
                            root = root.getCause();
                        }
                        String msg = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
                        KOF_WEB_STATUS.remove();
                        KOF_WEB_HEADERS.get().clear();
                        return new WebDispatchResult(RouteKind.HTTP,
                                kof_web_build(500, "Internal Server Error",
                                        "{\\"error\\": \\"handler error: " + msg + "\\"}"), null);
                    } finally {
                        KOF_WEB_REQUEST.remove();
                        KOF_LOG_REQUEST_ID.remove();
                        KOF_WEB_STATUS.remove();
                        KOF_WEB_HEADERS.remove();
                    }
                }

                private static Object kof_web_invoke(Object target, WebRequest req) throws Exception {
                    try {
                        return target.getClass().getMethod("invoke").invoke(target);
                    } catch (NoSuchMethodException e) {
                        return target.getClass()
                                .getMethod("invoke", String.class, String.class, String.class,
                                        String.class, String.class)
                                .invoke(target, req.method, req.path, req.body, req.query, req.rawHeaders);
                    }
                }

                private static Object kof_web_invoke(Object target, SseConnection sse) throws Exception {
                    return target.getClass().getMethod("invoke", SseConnection.class)
                            .invoke(target, sse);
                }

                // ── WS/SSE context (kof_web_ws_message/wsSend/sse) ──
                private static final ThreadLocal<Object> KOF_WS_CONNECTION = new ThreadLocal<>();
                private static final ThreadLocal<String> KOF_WS_MESSAGE = new ThreadLocal<>();
                private static final ThreadLocal<Object> KOF_SSE_SENDER = new ThreadLocal<>();

                /** wsMessage() — mensagem TEXT corrente da conexão WebSocket. */
                public static String kof_web_ws_message() {
                    return KOF_WS_MESSAGE.get();
                }

                /** wsSend(text) — envia TEXT pela conexão WebSocket corrente. */
                public static void kof_web_ws_send(String text) {
                    Object conn = KOF_WS_CONNECTION.get();
                    if (conn instanceof WsSender ws) {
                        ws.sendText(text);
                        WS_MESSAGES_SENT.incrementAndGet();
                    }
                }

                /** sse(text) — envia um evento SSE pela conexão corrente. */
                public static String kof_web_sse_send(String text) {
                    Object sender = KOF_SSE_SENDER.get();
                    if (sender instanceof SseSender s) {
                        s.send(text);
                    }
                    return text;
                }

                private static String kof_web_build(int status, String statusText, String body) {
                    byte[] bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    String contentType = "text/plain; charset=utf-8";
                    String trimmed = body.trim();
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        contentType = "application/json; charset=utf-8";
                    }
                    java.util.Map<String, String> extra = KOF_WEB_HEADERS.get();
                    boolean hasContentType = false;
                    StringBuilder hdr = new StringBuilder();
                    if (extra != null) {
                        for (java.util.Map.Entry<String, String> e : extra.entrySet()) {
                            if (e.getKey().equalsIgnoreCase("Content-Type")) hasContentType = true;
                            hdr.append(e.getKey()).append(": ").append(e.getValue()).append("\\r\\n");
                        }
                    }
                    String ctHeader = hasContentType ? "" : "Content-Type: " + contentType + "\\r\\n";
                    return "HTTP/1.1 " + status + " " + statusText + "\\r\\n"
                            + ctHeader
                            + hdr.toString()
                            + "Content-Length: " + bodyBytes.length + "\\r\\n"
                            + "Connection: close\\r\\n"
                            + "\\r\\n"
                            + body;
                }

                private static WebRequest readRequest(java.io.InputStream in) throws java.io.IOException {
                    StringBuilder head = new StringBuilder();
                    byte[] buffer = new byte[8192];
                    int headerEnd = -1;
                    while (true) {
                        int n = in.read(buffer);
                        if (n == -1) throw new java.io.IOException("connection closed before headers");
                        head.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                        headerEnd = head.indexOf("\\r\\n\\r\\n");
                        if (headerEnd >= 0) break;
                        if (head.length() > 65536) throw new java.io.IOException("headers too large");
                    }

                    String requestText = head.toString();
                    String headerBlock = requestText.substring(0, headerEnd);
                    StringBuilder body = new StringBuilder(requestText.substring(headerEnd + 4));

                    int contentLength = 0;
                    for (String line : headerBlock.split("\\r\\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            try {
                                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    while (body.length() < contentLength) {
                        int n = in.read(buffer);
                        if (n == -1) break;
                        body.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                    }
                    if (body.length() > contentLength) {
                        body.setLength(contentLength);
                    }

                    String[] lines = headerBlock.split("\\r\\n");
                    String[] parts = lines.length > 0 ? lines[0].split(" ") : new String[0];
                    String method = parts.length > 0 ? parts[0] : "GET";
                    String fullPath = parts.length > 1 ? parts[1] : "/";
                    String path = fullPath;
                    String query = "";
                    int q = fullPath.indexOf('?');
                    if (q >= 0) {
                        path = fullPath.substring(0, q);
                        query = fullPath.substring(q + 1);
                    }
                    return new WebRequest(method, path, query, headerBlock, body.toString());
                }

                public static String kof_web_param(String name) {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.param(name);
                }

                public static String kof_web_query(String name) {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.query(name);
                }

                public static String kof_web_header(String name) {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.header(name);
                }

                public static String kof_web_body() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.body;
                }

                public static String kof_web_method() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.method;
                }

                public static String kof_web_path() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.path;
                }

"""
                + JvmConfigRuntime.source()
                + JvmCacheRuntime.source()
                + JvmOrmRuntime.source()
                + JvmTimeRuntime.source()
                + JvmStringRuntime.source()
                + (usesExtern ? JvmFfiRuntime.source() : "")
                + (usesVk ? JvmVkRuntime.source() : "\n            }");
    }

    private static String sourceCore(String decoders) {
        return """
            package dev.kof.runtime;

            import java.lang.reflect.Field;
            import java.lang.reflect.Modifier;
            import java.lang.reflect.RecordComponent;
            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;

            /**
             * JSON helpers for the JVM target of the Kof compiler.
             * Generated by JvmRuntime at build time; the native target has its
             * own assembly implementations of the same functions.
             */
            public final class KofRuntime {

                private KofRuntime() {}

                public static String kof_json_encode_int(int value) {
                    return Integer.toString(value);
                }

                public static String kof_json_encode_long(long value) {
                    return Long.toString(value);
                }

                public static String kof_json_encode_bool(int value) {
                    return value != 0 ? "true" : "false";
                }

                public static String kof_json_encode_float(float value) {
                    if (Float.isNaN(value) || Float.isInfinite(value)) return "null";
                    return Float.toString(value);
                }

                public static String kof_json_encode_double(double value) {
                    if (Double.isNaN(value) || Double.isInfinite(value)) return "null";
                    return Double.toString(value);
                }

                public static String kof_json_encode_string(String value) {
                    if (value == null) return "null";
                    StringBuilder sb = new StringBuilder(value.length() + 2);
                    sb.append('"');
                    for (int i = 0; i < value.length(); i++) {
                        char c = value.charAt(i);
                        switch (c) {
                            case '"' -> sb.append("\\\\\\"");
                            case '\\\\' -> sb.append("\\\\\\\\");
                            case '\\n' -> sb.append("\\\\n");
                            case '\\r' -> sb.append("\\\\r");
                            case '\\t' -> sb.append("\\\\t");
                            default -> {
                                if (c < 0x20) {
                                    sb.append("\\\\u");
                                    String hex = Integer.toHexString(c);
                                    sb.append("0".repeat(4 - hex.length()));
                                    sb.append(hex);
                                } else {
                                    sb.append(c);
                                }
                            }
                        }
                    }
                    sb.append('"');
                    return sb.toString();
                }

                public static String kof_json_encode_list(List<?> list, int tag) {
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(',');
                        Object e = list.get(i);
                        switch (tag) {
                            case 1 -> sb.append(kof_json_encode_string((String) e));
                            case 2 -> sb.append(kof_json_encode_bool(((Integer) e).intValue()));
                            default -> sb.append(kof_json_encode(e));
                        }
                    }
                    sb.append(']');
                    return sb.toString();
                }

                public static String kof_json_encode_array(Object array) {
                    StringBuilder sb = new StringBuilder("[");
                    int length = java.lang.reflect.Array.getLength(array);
                    for (int i = 0; i < length; i++) {
                        if (i > 0) sb.append(',');
                        sb.append(kof_json_encode(java.lang.reflect.Array.get(array, i)));
                    }
                    sb.append(']');
                    return sb.toString();
                }

                public static String kof_json_encode(Object value) {
                    if (value == null) return "null";
                    if (value instanceof String s) return kof_json_encode_string(s);
                    if (value instanceof Integer i) return kof_json_encode_int(i);
                    if (value instanceof Long l) return kof_json_encode_long(l);
                    if (value instanceof Boolean b) return kof_json_encode_bool(b ? 1 : 0);
                    if (value instanceof Float f) return kof_json_encode_float(f);
                    if (value instanceof Double d) return kof_json_encode_double(d);
                    if (value instanceof List<?> l) {
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 0; i < l.size(); i++) {
                            if (i > 0) sb.append(',');
                            sb.append(kof_json_encode(l.get(i)));
                        }
                        sb.append(']');
                        return sb.toString();
                    }
                    if (value.getClass().isArray()) return kof_json_encode_array(value);
                    return kof_json_encode_object(value);
                }

                private static String kof_json_encode_object(Object value) {
                    StringBuilder sb = new StringBuilder("{");
                    boolean first = true;
                    for (Field f : value.getClass().getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers())) continue;
                        try {
                            f.setAccessible(true);
                            Object v = f.get(value);
                            if (!first) sb.append(',');
                            first = false;
                            sb.append(kof_json_encode_string(f.getName()));
                            sb.append(':');
                            sb.append(kof_json_encode(v));
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("cannot encode field " + f.getName(), e);
                        }
                    }
                    sb.append('}');
                    return sb.toString();
                }

                public static int kof_json_decode_int(String json) {
                    return Integer.parseInt(json.trim());
                }

                public static long kof_json_decode_long(String json) {
                    return Long.parseLong(json.trim());
                }

                public static float kof_json_decode_float(String json) {
                    return Float.parseFloat(json.trim());
                }

                public static double kof_json_decode_double(String json) {
                    return Double.parseDouble(json.trim());
                }

                public static int kof_json_decode_bool(String json) {
                    return Boolean.parseBoolean(json.trim()) ? 1 : 0;
                }

                public static String kof_json_decode_string(String json) {
                    String s = json.trim();
                    if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                        return s.substring(1, s.length() - 1);
                    }
                    return s;
                }

                public static int[] kof_json_decode_int_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (parsed instanceof List<?> l) {
                        int[] out = new int[l.size()];
                        for (int i = 0; i < l.size(); i++) out[i] = ((Number) l.get(i)).intValue();
                        return out;
                    }
                    return new int[0];
                }

                public static long[] kof_json_decode_long_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (parsed instanceof List<?> l) {
                        long[] out = new long[l.size()];
                        for (int i = 0; i < l.size(); i++) out[i] = ((Number) l.get(i)).longValue();
                        return out;
                    }
                    return new long[0];
                }

                public static int[] kof_json_decode_bool_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (parsed instanceof List<?> l) {
                        int[] out = new int[l.size()];
                        for (int i = 0; i < l.size(); i++) out[i] = ((Boolean) l.get(i)) ? 1 : 0;
                        return out;
                    }
                    return new int[0];
                }

                public static double[] kof_json_decode_double_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (parsed instanceof List<?> l) {
                        double[] out = new double[l.size()];
                        for (int i = 0; i < l.size(); i++) out[i] = ((Number) l.get(i)).doubleValue();
                        return out;
                    }
                    return new double[0];
                }

                public static String[] kof_json_decode_string_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (parsed instanceof List<?> l) {
                        String[] out = new String[l.size()];
                        for (int i = 0; i < l.size(); i++) out[i] = String.valueOf(l.get(i));
                        return out;
                    }
                    return new String[0];
                }

                public static ArrayList<Integer> kof_json_decode_int_list(String json) {
                    Object parsed = kof_json_parse(json);
                    ArrayList<Integer> result = new ArrayList<>();
                    if (parsed instanceof List<?> l) {
                        for (Object e : l) result.add(((Number) e).intValue());
                    }
                    return result;
                }

                public static ArrayList<String> kof_json_decode_string_list(String json) {
                    Object parsed = kof_json_parse(json);
                    ArrayList<String> result = new ArrayList<>();
                    if (parsed instanceof List<?> l) {
                        for (Object e : l) result.add(e == null ? null : String.valueOf(e));
                    }
                    return result;
                }

                public static ArrayList<Object> kof_json_decode_list(String json) {
                    Object parsed = kof_json_parse(json);
                    if (parsed instanceof ArrayList<?> l) return new ArrayList<Object>(l);
                    return new ArrayList<Object>();
                }

                public static ArrayList<Object> kof_json_decode_object_list(String json, String className)
                        throws Exception {
                    Object parsed = kof_json_parse(json);
                    ArrayList<Object> result = new ArrayList<>();
                    if (parsed instanceof List<?> l) {
                        Class<?> type = Class.forName(className);
                        for (Object e : l) result.add(kof_json_bind(type, e));
                    }
                    return result;
                }

                public static Object kof_json_decode_object(String json, Class<?> type) throws Exception {
                    return kof_json_bind(type, kof_json_parse(json));
                }

                private static Object kof_json_bind(Class<?> type, Object value) throws Exception {
                    if (value == null) return null;
                    if (type == String.class) return value instanceof String s ? s : String.valueOf(value);
                    if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                            || type == byte.class || type == short.class || type == float.class || type == double.class
                            || type == Number.class) {
                        return value;
                    }
                    if (type == boolean.class || type == Boolean.class) {
                        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
                    }
                    if (type == char.class || type == Character.class) {
                        return value.toString().charAt(0);
                    }
                    if (type.isAssignableFrom(ArrayList.class) || type == List.class || type == java.util.Collection.class) {
                        if (value instanceof List<?> l) return new ArrayList<Object>(l);
                    }
                    if (value instanceof Map<?, ?> m) {
                        if (type.isRecord()) {
                            RecordComponent[] comps = type.getRecordComponents();
                            Class<?>[] argTypes = new Class<?>[comps.length];
                            Object[] args = new Object[comps.length];
                            for (int i = 0; i < comps.length; i++) {
                                argTypes[i] = comps[i].getType();
                                args[i] = kof_json_bind(comps[i].getType(), m.get(comps[i].getName()));
                            }
                            return type.getDeclaredConstructor(argTypes).newInstance(args);
                        }
                        Object obj = type.getDeclaredConstructor().newInstance();
                        for (Field f : type.getDeclaredFields()) {
                            if (Modifier.isStatic(f.getModifiers())) continue;
                            if (!m.containsKey(f.getName())) continue;
                            f.setAccessible(true);
                            Object v = kof_json_bind(f.getType(), m.get(f.getName()));
                            if (v == null) continue;
                            if (f.getType() == int.class) f.setInt(obj, ((Number) v).intValue());
                            else if (f.getType() == long.class) f.setLong(obj, ((Number) v).longValue());
                            else if (f.getType() == short.class) f.setShort(obj, ((Number) v).shortValue());
                            else if (f.getType() == byte.class) f.setByte(obj, ((Number) v).byteValue());
                            else if (f.getType() == float.class) f.setFloat(obj, ((Number) v).floatValue());
                            else if (f.getType() == double.class) f.setDouble(obj, ((Number) v).doubleValue());
                            else if (f.getType() == boolean.class) f.setBoolean(obj, (Boolean) v);
                            else f.set(obj, v);
                        }
                        return obj;
                    }
                    return value;
                }

                public static Object kof_json_parse(String json) {
                    JsonParser p = new JsonParser(json);
                    Object v = p.parseValue();
                    p.skipWs();
                    if (p.pos < json.length()) throw new IllegalArgumentException("trailing JSON content at " + p.pos);
                    return v;
                }

                private static final class JsonParser {
                    private final String s;
                    private int pos;

                    JsonParser(String s) {
                        this.s = s;
                    }

                    void skipWs() {
                        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
                    }

                    Object parseValue() {
                        skipWs();
                        if (pos >= s.length()) throw new IllegalArgumentException("unexpected end of JSON");
                        char c = s.charAt(pos);
                        if (c == '{') return parseObject();
                        if (c == '[') return parseArray();
                        if (c == '"') return parseString();
                        if (c == 't') { expect("true"); return Boolean.TRUE; }
                        if (c == 'f') { expect("false"); return Boolean.FALSE; }
                        if (c == 'n') { expect("null"); return null; }
                        if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
                        throw new IllegalArgumentException("unexpected JSON char '" + c + "' at " + pos);
                    }

                    private void expect(String word) {
                        if (!s.startsWith(word, pos)) throw new IllegalArgumentException("invalid JSON literal at " + pos);
                        pos += word.length();
                    }

                    private Map<String, Object> parseObject() {
                        Map<String, Object> map = new LinkedHashMap<>();
                        pos++;
                        skipWs();
                        if (pos < s.length() && s.charAt(pos) == '}') { pos++; return map; }
                        while (true) {
                            skipWs();
                            String key = parseString();
                            skipWs();
                            if (pos >= s.length() || s.charAt(pos) != ':') throw new IllegalArgumentException("expected ':' at " + pos);
                            pos++;
                            map.put(key, parseValue());
                            skipWs();
                            if (pos >= s.length()) throw new IllegalArgumentException("unterminated JSON object");
                            char c = s.charAt(pos);
                            if (c == ',') { pos++; continue; }
                            if (c == '}') { pos++; return map; }
                            throw new IllegalArgumentException("expected ',' or '}' at " + pos);
                        }
                    }

                    private List<Object> parseArray() {
                        List<Object> list = new ArrayList<>();
                        pos++;
                        skipWs();
                        if (pos < s.length() && s.charAt(pos) == ']') { pos++; return list; }
                        while (true) {
                            list.add(parseValue());
                            skipWs();
                            if (pos >= s.length()) throw new IllegalArgumentException("unterminated JSON array");
                            char c = s.charAt(pos);
                            if (c == ',') { pos++; continue; }
                            if (c == ']') { pos++; return list; }
                            throw new IllegalArgumentException("expected ',' or ']' at " + pos);
                        }
                    }

                    private String parseString() {
                        if (pos >= s.length() || s.charAt(pos) != '"') throw new IllegalArgumentException("expected string at " + pos);
                        pos++;
                        StringBuilder sb = new StringBuilder();
                        while (true) {
                            if (pos >= s.length()) throw new IllegalArgumentException("unterminated JSON string");
                            char c = s.charAt(pos);
                            if (c == '"') { pos++; return sb.toString(); }
                            if (c == '\\\\') {
                                pos++;
                                if (pos >= s.length()) throw new IllegalArgumentException("unterminated escape");
                                char e = s.charAt(pos);
                                switch (e) {
                                    case '"' -> sb.append('"');
                                    case '\\\\' -> sb.append('\\\\');
                                    case '/' -> sb.append('/');
                                    case 'n' -> sb.append('\\n');
                                    case 't' -> sb.append('\\t');
                                    case 'r' -> sb.append('\\r');
                                    case 'b' -> sb.append('\\b');
                                    case 'f' -> sb.append('\\f');
                                    case 'u' -> {
                                        if (pos + 4 >= s.length()) throw new IllegalArgumentException("bad \\\\u escape");
                                        sb.append((char) Integer.parseInt(s.substring(pos + 1, pos + 5), 16));
                                        pos += 4;
                                    }
                                    default -> throw new IllegalArgumentException("bad escape '\\\\" + e + "'");
                                }
                                pos++;
                            } else {
                                sb.append(c);
                                pos++;
                            }
                        }
                    }

                    private Object parseNumber() {
                        int start = pos;
                        if (pos < s.length() && s.charAt(pos) == '-') pos++;
                        while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                        boolean isDouble = false;
                        if (pos < s.length() && s.charAt(pos) == '.') {
                            isDouble = true;
                            pos++;
                            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                        }
                        if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                            isDouble = true;
                            pos++;
                            if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                        }
                        String num = s.substring(start, pos);
                        if (isDouble) return Double.parseDouble(num);
                        long l = Long.parseLong(num);
                        if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                        return l;
                    }
                }

            %s

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

                // ── kof.time ───────────────────────────────────────

                public static long kof_now() {
                    return System.currentTimeMillis();
                }

                // ── kof.io ─────────────────────────────────────────

                private static final java.io.BufferedReader KOF_STDIN =
                        new java.io.BufferedReader(new java.io.InputStreamReader(System.in));

                public static String kof_read_line() {
                    // BufferedReader compartilhado: criar um por chamada
                    // perdia o buffer entre leituras (OBS: readLine repetido
                    // retornava null após a primeira linha).
                    try {
                        return KOF_STDIN.readLine();
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static String kof_read_file(String path) {
                    try {
                        return java.nio.file.Files.readString(java.nio.file.Path.of(path));
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int kof_write_file(String path, String content) {
                    try {
                        java.nio.file.Files.writeString(java.nio.file.Path.of(path), content);
                        return 0;
                    } catch (java.io.IOException e) {
                        return -1;
                    }
                }

                // ── kof.concurrent ─────────────────────────────────

                private static final java.util.concurrent.atomic.AtomicInteger KOF_ACTIVE_TASKS =
                        new java.util.concurrent.atomic.AtomicInteger();

                static {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        while (KOF_ACTIVE_TASKS.get() > 0) {
                            Thread.onSpinWait();
                        }
                    }, "kof-wait-tasks"));
                }

                private static final ThreadLocal<Object> KOF_CURRENT_HANDLE = new ThreadLocal<>();
                private static final java.util.Set<Object> KOF_CANCELLED =
                        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

                /**
                 * Thread de tarefa: virtual quando disponível (JVM 21+), platform
                 * thread no ART (Android não tem Thread.startVirtualThread).
                 * Detecta uma vez; sem reflection, sem falha em runtime antigo.
                 */
                private static final boolean KOF_VIRTUAL_OK = kofProbeVirtual();
                private static boolean kofProbeVirtual() {
                    try {
                        Thread.class.getMethod("startVirtualThread", Runnable.class);
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                }
                static void kofStartTask(Runnable body) {
                    if (KOF_VIRTUAL_OK) {
                        try {
                            Thread.class.getMethod("startVirtualThread", Runnable.class)
                                    .invoke(null, body);
                            return;
                        } catch (Throwable ignored) {}
                    }
                    new Thread(body, "kof-task").start();
                }

                public static Object kof_spawn_result(Object task) {
                    java.util.concurrent.CompletableFuture<Object> future =
                            new java.util.concurrent.CompletableFuture<>();
                    KOF_ACTIVE_TASKS.incrementAndGet();
                    kofStartTask(() -> {
                        KOF_CURRENT_HANDLE.set(future);
                        try {
                            future.complete(task.getClass().getMethod("invoke").invoke(task));
                        } catch (Throwable e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            future.completeExceptionally(cause);
                        } finally {
                            KOF_CURRENT_HANDLE.remove();
                            KOF_ACTIVE_TASKS.decrementAndGet();
                        }
                    });
                    return future;
                }

                /** cancel(handle) -> true (marca; a tarefa vê via cancelled()). */
                public static boolean kof_cancel(Object handle) {
                    if (handle == null) return false;
                    KOF_CANCELLED.add(handle);
                    return true;
                }

                /** cancelled() -> a tarefa ATUAL foi marcada como cancelada? */
                public static boolean kof_cancelled() {
                    Object h = KOF_CURRENT_HANDLE.get();
                    return h != null && KOF_CANCELLED.contains(h);
                }

                /** selectAny(handles) -> valor do primeiro handle pronto. */
                public static Object kof_select_any(java.util.List<?> handles) throws Exception {
                    if (handles == null || handles.isEmpty()) {
                        throw new IllegalArgumentException("selectAny: nenhuma tarefa");
                    }
                    java.util.concurrent.CompletableFuture<?>[] arr =
                            handles.stream()
                                .map(h -> {
                                    if (!(h instanceof java.util.concurrent.CompletableFuture<?> cf)) {
                                        throw new IllegalStateException(
                                                "selectAny: argumento não é Handle");
                                    }
                                    return cf;
                                })
                               .toArray(java.util.concurrent.CompletableFuture[]::new);
                    return java.util.concurrent.CompletableFuture.anyOf(arr).get();
                }

                public static Object kof_await(Object handle) throws Exception {
                    if (handle instanceof java.util.concurrent.Future<?> f) {
                        try {
                            return f.get();
                        } catch (java.util.concurrent.ExecutionException e) {
                            // re-lança a causa original: try/catch do Kof vê a
                            // exceção da tarefa, não o wrapper
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            if (cause instanceof RuntimeException re) throw re;
                            if (cause instanceof Error err) throw err;
                            throw new RuntimeException(cause);
                        }
                    }
                    throw new IllegalStateException("await: handle inválido");
                }

                /** awaitTimeout(handle, timeoutMs) -> valor; lança exceção no estouro. */
                public static Object kof_await_timeout(Object handle, int timeoutMs) throws Exception {
                    if (!(handle instanceof java.util.concurrent.Future<?> f)) {
                        throw new IllegalStateException("awaitTimeout: handle inválido");
                    }
                    try {
                        return f.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException te) {
                        throw new RuntimeException("awaitTimeout: estourou o tempo limite de " + timeoutMs + "ms");
                    } catch (java.util.concurrent.ExecutionException e) {
                        // re-lança a causa original (mesma semântica do kof_await)
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof RuntimeException re) throw re;
                        if (cause instanceof Error err) throw err;
                        throw new RuntimeException(cause);
                    }
                }

                /** poll(rdi=handle) -> valor pronto | 0 (null) — não bloqueia. */
                public static Object kof_poll(Object handle) {
                    if (handle instanceof java.util.concurrent.CompletableFuture<?> cf) {
                        return cf.getNow(null);
                    }
                    if (handle instanceof java.util.concurrent.Future<?> f && f.isDone()) {
                        try { return f.get(); } catch (Exception e) { return null; }
                    }
                    return null;
                }

                /** kof_done(handle) -> true se a tarefa terminou. */
                public static boolean kof_done(Object handle) {
                    return handle instanceof java.util.concurrent.Future<?> f && f.isDone();
                }

                public static void kof_spawn(Object task) {
                    KOF_ACTIVE_TASKS.incrementAndGet();
                    kofStartTask(() -> {
                        try {
                            task.getClass().getMethod("invoke").invoke(task);
                        } catch (Exception e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            System.err.println("spawn task failed: " + cause.getMessage());
                        } finally {
                            KOF_ACTIVE_TASKS.decrementAndGet();
                        }
                    });
                }

                public static ArrayList<String> kof_args_list(String[] args) {
                    ArrayList<String> list = new ArrayList<>(args.length);
                    for (String a : args) list.add(a);
                    return list;
                }

                // ── kof.process — multiplatform process abstraction ──

                public static final class ProcessResult {
                    public final String stdout;
                    public final String stderr;
                    public final int exitCode;

                    public ProcessResult(String stdout, String stderr, int exitCode) {
                        this.stdout = stdout;
                        this.stderr = stderr;
                        this.exitCode = exitCode;
                    }
                }

                public static void kof_process_exit(int code) {
                    // process.exit(code): termina na hora, sem stack trace
                    System.exit(code);
                }

                public static ProcessResult kof_process_run(String program, List<String> args) {                    try {
                        List<String> cmd = new ArrayList<>();
                        cmd.add(program);
                        cmd.addAll(args);
                        Process p = new ProcessBuilder(cmd)
                                .redirectErrorStream(false)
                                .redirectInput(java.lang.ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                                .start();
                        java.util.concurrent.FutureTask<String> outTask = new java.util.concurrent.FutureTask<>(
                                () -> new String(p.getInputStream().readAllBytes(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        java.util.concurrent.FutureTask<String> errTask = new java.util.concurrent.FutureTask<>(
                                () -> new String(p.getErrorStream().readAllBytes(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        kofStartTask(outTask);
                        kofStartTask(errTask);
                        int code = p.waitFor();
                        String out = outTask.get();
                        String err = errTask.get();
                        return new ProcessResult(out, err, code);
                    } catch (Exception e) {
                        return new ProcessResult("", e.getMessage() == null
                                ? e.getClass().getSimpleName() : e.getMessage(), -1);
                    }
                }

                // ── process.spawn — stdin/stdout vivos (F10) ────────

                private static final java.util.concurrent.ConcurrentHashMap<Long, Process> SPAWNED =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<Long, java.io.BufferedReader> SPAWN_READERS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<Long, java.io.PrintWriter> SPAWN_WRITERS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static long SPAWN_SEQ = 0;                public static Long kof_process_spawn(String program, List<String> args) {
                    try {
                        List<String> cmd = new ArrayList<>();
                        cmd.add(program);
                        cmd.addAll(args);
                        Process p = new ProcessBuilder(cmd)
                                .redirectErrorStream(false)
                                .redirectInput(java.lang.ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                                .start();
                        long id;
                        synchronized (KofRuntime.class) { id = ++SPAWN_SEQ; }
                        SPAWNED.put(id, p);
                        SPAWN_READERS.put(id, new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8)));
                        SPAWN_WRITERS.put(id, new java.io.PrintWriter(
                                new java.io.OutputStreamWriter(p.getOutputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8), true));
                        return Long.valueOf(id);
                    } catch (Exception e) {
                        return Long.valueOf(-1);
                    }
                }

                public static String kof_spawn_read_line(Long handleBoxed) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var r = SPAWN_READERS.get(handle);
                    if (r == null) return "";
                    try {
                        String line = r.readLine();
                        return line == null ? "" : line;
                    } catch (Exception e) {
                        return "";
                    }
                }

                public static void kof_spawn_write(Long handleBoxed, String data) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var w = SPAWN_WRITERS.get(handle);
                    if (w == null) return;
                    w.println(data);
                    w.flush();
                }

                public static int kof_spawn_exit_code(Long handleBoxed) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var p = SPAWNED.get(handle);
                    if (p == null) return -1;
                    try {
                        if (p.isAlive()) return Integer.MIN_VALUE;
                        return p.exitValue();
                    } catch (Exception e) {
                        return -1;
                    }
                }

                public static void kof_spawn_kill(Long handleBoxed) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var p = SPAWNED.get(handle);
                    if (p != null) {
                        p.destroyForcibly();
                        SPAWNED.remove(handle);
                        SPAWN_WRITERS.remove(handle);
                        SPAWN_READERS.remove(handle);
                    }
                }

                public static boolean kof_spawn_alive(Long handleBoxed) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var p = SPAWNED.get(handle);
                    return p != null && p.isAlive();
                }

                // ── kof.io — File / Path / Directory ──────────────

                private static java.nio.file.Path p(String path) {
                    return java.nio.file.Path.of(path.replace('\\\\', '/'));
                }

                /** Caminho canônico Kof: separador sempre '/' (multiplataforma). */
                private static String s(java.nio.file.Path path) {
                    return path.toString().replace('\\\\', '/');
                }

                public static int kof_io_file_exists(String path) {
                    return java.nio.file.Files.exists(p(path)) ? 1 : 0;
                }

                public static int kof_io_file_is_file(String path) {
                    return java.nio.file.Files.isRegularFile(p(path)) ? 1 : 0;
                }

                public static int kof_io_file_is_dir(String path) {
                    return java.nio.file.Files.isDirectory(p(path)) ? 1 : 0;
                }

                public static String kof_io_read_text(String path) {
                    try {
                        return java.nio.file.Files.readString(p(path), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int kof_io_write_text(String path, String content) {
                    try {
                        java.nio.file.Files.writeString(p(path), content, java.nio.charset.StandardCharsets.UTF_8);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_append_text(String path, String content) {
                    try {
                        java.nio.file.Files.writeString(p(path), content, java.nio.charset.StandardCharsets.UTF_8,
                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int[] kof_io_read_bytes(String path) {
                    try {
                        byte[] b = java.nio.file.Files.readAllBytes(p(path));
                        int[] out = new int[b.length];
                        for (int i = 0; i < b.length; i++) out[i] = b[i] & 0xFF;
                        return out;
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int[] kof_io_read_range(String path, long offset, long len) {
                    return kof_io_read_range_path(path, offset, len);
                }

                public static int[] kof_io_read_range_path(String path, long offset, long len) {
                    int ilen = (int) Math.min(len, Integer.MAX_VALUE);
                    try {
                        byte[] b = new byte[ilen];
                        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(p(path).toFile(), "r")) {
                            raf.seek(offset);
                            int read = raf.read(b, 0, ilen);
                            int[] out = new int[read < 0 ? 0 : read];
                            for (int i = 0; i < out.length; i++) out[i] = b[i] & 0xFF;
                            return out;
                        }
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int kof_io_write_bytes(String path, int[] bytes) {
                    try {
                        byte[] b = new byte[bytes.length];
                        for (int i = 0; i < bytes.length; i++) b[i] = (byte) (bytes[i] & 0xFF);
                        java.nio.file.Files.write(p(path), b);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_append_bytes(String path, int[] bytes) {
                    try {
                        byte[] b = new byte[bytes.length];
                        for (int i = 0; i < bytes.length; i++) b[i] = (byte) (bytes[i] & 0xFF);
                        java.nio.file.Files.write(p(path), b,
                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_delete(String path) {
                    try {
                        if (!java.nio.file.Files.exists(p(path))) return 0;
                        java.nio.file.Files.deleteIfExists(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static long kof_io_file_size(String path) {
                    try {
                        return java.nio.file.Files.size(p(path));
                    } catch (java.io.IOException e) {
                        // sentinela -1 era um anti-pattern do próprio corpus: erro é
                        // exceção recuperável (catch (String e)), nunca valor de dados.
                        throw new RuntimeException("file not found: " + path);
                    }
                }

                public static String kof_io_file_name(String path) {
                    java.nio.file.Path pp = p(path).getFileName();
                    return pp == null ? path.replace('\\\\', '/') : s(pp);
                }

                public static String kof_io_path_resolve(String base, String child) {
                    return s(p(base).resolve(child));
                }

                public static String kof_io_path_parent(String path) {
                    java.nio.file.Path pp = p(path).getParent();
                    return pp == null ? null : s(pp);
                }

                public static String kof_io_path_file_name(String path) {
                    return kof_io_file_name(path);
                }

                public static String kof_io_path_extension(String path) {
                    String name = kof_io_file_name(path);
                    int dot = name.lastIndexOf('.');
                    return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
                }

                public static String kof_io_path_normalize(String path) {
                    String n = s(p(path).normalize());
                    return n.isEmpty() ? "." : n;
                }

                public static int kof_io_path_is_absolute(String path) {
                    return p(path).isAbsolute() ? 1 : 0;
                }

                public static String kof_io_path_to_absolute(String path) {
                    return s(p(path).toAbsolutePath());
                }

                public static int kof_io_dir_create(String path) {
                    try {
                        java.nio.file.Files.createDirectory(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_dir_create_dirs(String path) {
                    try {
                        java.nio.file.Files.createDirectories(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_dir_delete(String path) {
                    try {
                        if (!java.nio.file.Files.exists(p(path))) return 0;
                        // recursivo: precisa remover sub-árvore (Files.delete
                        // falha em diretório não-vazio)
                        try (var walk = java.nio.file.Files.walk(p(path))) {
                            walk.sorted(java.util.Comparator.reverseOrder())
                                    .forEach(pp -> {
                                        try { java.nio.file.Files.deleteIfExists(pp); }
                                        catch (java.io.IOException ignored) {}
                                    });
                        }
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static java.util.ArrayList<String> kof_io_dir_list(String path) {
                    try (var stream = java.nio.file.Files.list(p(path))) {
                        return stream.map(java.nio.file.Path::getFileName)
                                .map(java.nio.file.Path::toString).sorted()
                                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

""".formatted(decoders);
    }
}
