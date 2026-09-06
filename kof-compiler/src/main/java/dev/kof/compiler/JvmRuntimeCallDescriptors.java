package dev.kof.compiler;

/**
 * Descritores de chamada do runtime JVM (nomes kof_* → descritoers JVM).
 * Extraído de JvmRuntime (REFACTOR-500 Fase 5) — SRP: só mapeamento de
 * assinatura, sem geração de source.
 */
final class JvmRuntimeCallDescriptors {

    private JvmRuntimeCallDescriptors() {}

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
            // ── Canvas 2D ──
            case "kof_ui_canvas_new" -> "(II)I";
            case "kof_ui_canvas_begin_path", "kof_ui_canvas_close_path",
                    "kof_ui_canvas_fill", "kof_ui_canvas_stroke",
                    "kof_ui_canvas_remove" -> "(I)V";
            case "kof_ui_canvas_move_to", "kof_ui_canvas_line_to",
                    "kof_ui_canvas_set_line_width" -> "(III)V";
            case "kof_ui_canvas_arc" -> "(IIIIDD)V";
            case "kof_ui_canvas_set_fill", "kof_ui_canvas_set_stroke" -> "(II)V";
            case "kof_ui_canvas_clear_rect" -> "(IIIII)V";
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
}
