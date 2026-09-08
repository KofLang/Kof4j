package dev.kof.compiler.jvm;

/**
 * Descritores de RETORNO de chamadas do runtime JVM (REFACTOR-500:
 * extraído de JvmRuntimeCallDescriptors para manter cada classe ≤500).
 * SRP: o que o stack devolve (primitivo vs referência) por nome kof_*.
 */
public final class JvmRuntimeReturnDescriptors {

    private JvmRuntimeReturnDescriptors() {}

    static String callReturnDescriptor(String methodName) {
        return switch (methodName) {
            case "kof_json_decode_int", "kof_json_decode_bool" -> "I";
            case "kof_ffi_i", "kof_ffi_si" -> "I";
            case "kof_ffi_dd" -> "D";
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
            // ── kof.math (STDLIB S1) — Int e Bool ambos são "I" no stack ──
            case "kof_math_abs", "kof_math_sign", "kof_math_clamp", "kof_math_min",
                    "kof_math_max", "kof_math_isEven", "kof_math_isOdd", "kof_math_isPositive",
                    "kof_math_isNegative", "kof_math_isZero" -> "I";
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
