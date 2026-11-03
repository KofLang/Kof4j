package dev.kof.compiler.jvm;

/**
 * Mapeamento de assinaturas do front web/http/acme (REFACTOR-500 Fase 6):
 * separado de {@link JvmRuntimeCallDescriptors} quando a fatia 3 do TLS
 * (cliente HTTP verificador + opt-in) empurrou o arquivo além de 600 linhas.
 * Responsabilidade: comunicação (web, http, acme) — SRP, nunca funcionalidade.
 */
public final class JvmRuntimeCallDescriptorsWeb {

    private JvmRuntimeCallDescriptorsWeb() {}

    static String descriptor(String methodName) {
        return switch (methodName) {
            case "kof_web_app_new" -> "()Ljava/lang/String;";
            case "kof_web_route" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_sse_route" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_ws_route" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_use" -> "(Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_security" -> "(Ljava/lang/String;)V";
            case "kof_web_security_opts" -> "(Ljava/lang/String;Ljava/util/Map;)V";
            case "kof_web_policy" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V";
            case "kof_web_route_opts" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/Object;)V";
            case "kof_web_listen" -> "(Ljava/lang/String;I)V";
            case "kof_web_listen_secure" -> "(Ljava/lang/String;I)V";
            case "kof_web_listen_secure_pem" -> "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V";
            case "kof_web_listen_secure_sni" -> "(Ljava/lang/String;I[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)V";
            case "kof_web_reload_tls_pem" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";
            case "kof_web_reload_tls_sni" -> "(Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)V";
            case "kof_web_serve_dir" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";
            case "kof_web_health" -> "(Ljava/lang/String;Ljava/lang/String;)V";
            case "kof_web_configure" -> "(Ljava/lang/String;Ljava/lang/String;I)V";
            case "kof_web_stats" -> "(Ljava/lang/String;)Ljava/lang/String;";
            // ── kof.image ──
            case "kof_image_decode" -> "(Ljava/lang/String;)[I";
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
            case "kof_http_insecure_set" -> "()V";
            case "kof_acme_jws_es256" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_acme_new_order" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_acme_dns01_txt" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_acme_challenge" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_acme_new_account" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            default -> "(Ljava/lang/String;)Ljava/lang/Object;";
        };
    }
}
