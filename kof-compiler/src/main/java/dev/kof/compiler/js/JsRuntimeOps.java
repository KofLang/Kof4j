package dev.kof.compiler.js;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsRuntimeOps — lowering das ops de runtime (json/io/ui/http/web/db/security/time/scheduler/mq/log/concurrency) para os helpers JS (REFACTOR-500 FASE 4).
 */
public final class JsRuntimeOps {

    private final JsMethodParser p;

    JsRuntimeOps(JsMethodParser p) {
        this.p = p;
    }

boolean isRuntimeOp(KofCall kc) {
        String name = kc.methodName();
        return name.startsWith("kof_json_") || name.startsWith("kof_io_")
                || name.startsWith("kof_buffer_")
                || name.startsWith("kof_ui_")
                || name.startsWith("kof_sec_")
                || name.startsWith("kof_validation_")
                || name.startsWith("kof_math_")
                || name.startsWith("kof_string_to_")
                || name.startsWith("kof_strings_")
                || name.startsWith("kof_encoding_")
                || name.startsWith("kof_uuid_")
                || name.startsWith("kof_random_")
                || name.startsWith("kof_rng_")
                || name.startsWith("kof_net_")
                || name.startsWith("kof_enum_")
                || name.startsWith("kof_config_")
                || name.startsWith("kof_cache_")
                || name.startsWith("kof_web_") || name.startsWith("kof_db_")
                || name.startsWith("kof_orm_") || name.startsWith("kof_http_")
                || name.equals("kof_spawn") || name.equals("kof_spawn_result") || name.equals("kof_await")
                || name.equals("kof_poll") || name.equals("kof_done")
                || name.equals("kof_cancel") || name.equals("kof_cancelled")
                || name.equals("kof_await_timeout")
                || name.equals("kof_gc_collect_now")
                || name.equals("kof_select_any")
                || name.equals("kof_list_map") || name.equals("kof_list_filter")
                || name.equals("kof_list_reduce")
                || name.startsWith("kof_observability_")
                || name.startsWith("kof_time_")
                || name.startsWith("kof_scheduler_")
                || name.startsWith("kof_mq_")
                || name.startsWith("kof_vk_") || name.startsWith("kof_mv64_")
                || name.startsWith("kof_log_")
                || name.equals("kof_ui_color_to_css")
                || name.equals("kof_now") || name.equals("kof_read_line")
                || name.equals("kof_read_file") || name.equals("kof_write_file")
                || name.equals("kof_process_run") || name.equals("kof_process_exit")
                || name.equals("kof_process_spawn") || name.equals("kof_spawn_write")
                || name.equals("kof_spawn_read_line") || name.equals("kof_spawn_exit_code")
                || name.equals("kof_spawn_kill") || name.equals("kof_spawn_alive")
                || name.equals("kof_shell_argv") || name.equals("kof_shell_runwith")
                || name.equals("kof_shell_pipeline")
                || name.equals("kof_ssh_argv") || name.equals("kof_ssh_run")
                || name.equals("kof_args")
                || name.equals("kof_ffi") || name.equals("kof_ffi_void")
                || name.equals("kof_box") || name.equals("kof_unbox");
    }

void handleRuntimeOp(MethodCtx ctx, List<Object> stack,
                              List<JsIr.JsExpression> preambleExprs, KofCall kc,
                              JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String name = kc.methodName();
        if (name.startsWith("kof_buffer_")) {
            // kof.buffer (D-R3-BUFFER): `buffer.alloc`/`Buffer.bytes()` baixam
            // para os helpers do runtime JS (args já trazem o receiver como 1º
            // operando nos dois casos).
            p.lc.registerRuntime(name);
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(name), args));
            return;
        }
        if (name.startsWith("kof_json_")) {
            // JSON encode/decode maps directly to JSON.stringify/parse; the
            // type information stays in the Kof compiler (generics erasure).
            JsIr.JsExpression value = kc.kind() == KofCallKind.FUNCTION
                    ? args.get(0) : receiver;
            if (name.equals("kof_json_encode_map")) {
                // §106 residual (13/09): Map no JS é `new Map()` — JSON.stringify
                // devolve '{}' (sem own enumerable props). Roteia p/ o helper
                // que monta o objeto com chaves SORTED (decisão 2b), igual ao
                // JVM/nativo/interp. args = (map, tag).
                p.lc.registerRuntime("kofJsonEncodeMap");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonEncodeMap"),
                        List.of(value, args.get(1))));
            } else if (name.contains("encode")) {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("JSON.stringify"), List.of(value)));
            } else if (name.startsWith("kof_json_decode_")
                    && BuiltinTypes.isList(kc.ownerType())) {
                // decode<List<T>> — bind each element to the Kof class
                Type elem = kc.ownerType() instanceof Type.ClassType lct
                        && !lct.typeArguments().isEmpty() ? lct.typeArguments().get(0) : Type.UnknownType.UNKNOWN;
                if (elem instanceof Type.ClassType ect
                        && p.lc.classMethodNames.containsKey(ect.internalName())) {
                    String jsName = JsTypeMapper.jsClassName(ect.internalName());
                    p.lc.decodeHelpers.add(jsName);
                    JsIr.JsExpression parsed = new JsIr.JsCall(
                            new JsIr.JsIdentifier("JSON.parse"), List.of(value));
                    JsIr.JsExpression mapper = new JsIr.JsCall(
                            new JsIr.JsIdentifier("__kof_decode_" + jsName),
                            List.of(new JsIr.JsIdentifier("o")));
                    stack.add(new JsIr.JsCall(
                            new JsIr.JsMember(parsed, "map"),
                            List.of(new JsIr.JsArrow(List.of("o"), mapper))));
                } else {
                    stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("JSON.parse"), List.of(value)));
                }
            } else if (name.startsWith("kof_json_decode_")
                    && BuiltinTypes.isMap(kc.ownerType())) {
                // §103.1 (#103): decode<Map<String,T>> — JSON.parse do
                // objeto + monta um Map real (o else dava objeto puro, que
                // não responde m.get/m.size). Valor classe → bind via
                // __kof_decode_<Classe> (mesmo helper da célula de lista).
                Type mv2 = BuiltinTypes.mapValue(kc.ownerType());
                JsIr.JsExpression parsed = new JsIr.JsCall(
                        new JsIr.JsIdentifier("JSON.parse"), List.of(value));
                if (mv2 instanceof Type.ClassType mct
                        && p.lc.classMethodNames.containsKey(mct.internalName())) {
                    String jsName = JsTypeMapper.jsClassName(mct.internalName());
                    p.lc.decodeHelpers.add(jsName);
                    p.lc.registerRuntime("kofJsonDecodeObjectMap");
                    stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonDecodeObjectMap"),
                            List.of(parsed, new JsIr.JsIdentifier("__kof_decode_" + jsName))));
                } else {
                    p.lc.registerRuntime("kofJsonDecodeMap");
                    stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonDecodeMap"),
                            List.of(parsed)));
                }
            } else if (name.startsWith("kof_json_decode_")
                    && p.lc.classMethodNames.containsKey(JsTypeMapper.ownerInternalName(kc.ownerType()))) {
                // decode<Class> — bind the parsed object to the Kof class
                String jsName = JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(kc.ownerType()));
                p.lc.decodeHelpers.add(jsName);
                stack.add(new JsIr.JsCall(
                        new JsIr.JsIdentifier("__kof_decode_" + jsName), List.of(value)));
            } else {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("JSON.parse"), List.of(value)));
            }
            return;
        }
        if (name.equals("kof_box") || name.equals("kof_unbox")) {
            // JS values are already boxed; these are identity.
            stack.add(kc.kind() == KofCallKind.FUNCTION ? args.get(0) : receiver);
            return;
        }
        if (name.equals("kof_args")) {
            p.lc.registerIoRuntime("kofArgs");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofArgs"), List.of()));
            return;
        }
        if (JsRuntimeProcessShellOps.handle(p, stack, kc, args)) {
            return;
        }
        if (name.equals("kof_ui_color_to_css")) {
            p.lc.registerRuntime("kofUiColorToCss");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofUiColorToCss"), List.of(args.get(0))));
            return;
        }
        if (name.equals("kof_ui_window_new") || name.equals("kof_ui_label_new")
                || name.equals("kof_ui_button_new") || name.equals("kof_ui_button_new_action")
                || name.equals("kof_ui_input_new") || name.equals("kof_ui_column_new")
                || name.equals("kof_ui_textarea_new") || name.startsWith("kof_ui_textarea_")
                || name.equals("kof_ui_select_new") || name.startsWith("kof_ui_select_")
                || name.startsWith("kof_ui_ul_") || name.startsWith("kof_ui_ol_")
                || name.startsWith("kof_ui_table_")
                || name.startsWith("kof_ui_fieldset_") || name.startsWith("kof_ui_iframe_")
                || name.startsWith("kof_ui_video_") || name.startsWith("kof_ui_audio_")
                || name.startsWith("kof_ui_hr_")
                || name.equals("kof_ui_form_new") || name.equals("kof_ui_form_on_submit")
                || name.equals("kof_ui_form_submit")
                || name.equals("kof_ui_fieldset_new")
                || name.equals("kof_ui_iframe_new") || name.startsWith("kof_ui_iframe_")
                || name.equals("kof_ui_video_new") || name.startsWith("kof_ui_video_")
                || name.equals("kof_ui_audio_new") || name.startsWith("kof_ui_audio_")
                || name.equals("kof_ui_hr_new") || name.startsWith("kof_ui_hr_")
                || name.equals("kof_ui_row_new") || name.equals("kof_ui_view_new")
                || name.equals("kof_ui_box_new") || name.equals("kof_ui_stack_new")
                || name.equals("kof_ui_wrap_new") || name.equals("kof_ui_grid_new")
                || name.equals("kof_ui_spacer_new") || name.equals("kof_ui_center_new")
                || name.equals("kof_ui_align_new")
                || name.equals("kof_ui_style_new") || name.equals("kof_ui_style_css")
                || name.equals("kof_ui_view_bind")
                || name.equals("kof_ui_window_set_title") || name.equals("kof_ui_window_title")
                || name.equals("kof_ui_window_bind") || name.equals("kof_ui_window_show")
                || name.equals("kof_ui_window_close") || name.equals("kof_ui_label_set_text")
                || name.equals("kof_ui_label_text") || name.equals("kof_ui_label_remove")
                || name.equals("kof_ui_button_set_text") || name.equals("kof_ui_button_text")
                || name.equals("kof_ui_button_remove") || name.equals("kof_ui_input_set_text")
                || name.equals("kof_ui_input_set_placeholder")
                || name.equals("kof_ui_input_set_type")
                || name.equals("kof_ui_input_set_checked") || name.equals("kof_ui_input_checked")
                || name.equals("kof_ui_input_set_name") || name.equals("kof_ui_input_set_readonly")
                || name.equals("kof_ui_input_text") || name.equals("kof_ui_input_remove")
                || name.equals("kof_ui_view_remove") || name.equals("kof_ui_window_set_theme")
                || name.equals("kof_ui_window_set_size")
                || name.equals("kof_ui_label_set_font_size") || name.equals("kof_ui_label_font_size")
                || name.equals("kof_ui_label_set_bold") || name.equals("kof_ui_label_bold")
                || name.equals("kof_ui_label_set_color") || name.equals("kof_ui_label_color")
                || name.startsWith("kof_ui_link_") || name.startsWith("kof_ui_image_")
                || name.startsWith("kof_ui_icon_") || name.startsWith("kof_ui_widget_")
                || name.startsWith("kof_ui_font_")
                || name.startsWith("kof_ui_canvas_")
                || name.equals("kof_ui_component_new") || name.equals("kof_ui_component_state_get")
                || name.equals("kof_ui_component_state_set") || name.equals("kof_ui_component_view")
                || name.equals("kof_ui_component_on_mount") || name.equals("kof_ui_component_on_dispose")
                || name.equals("kof_ui_component_effect") || name.equals("kof_ui_component_on")
                || name.equals("kof_ui_component_bind") || name.equals("kof_ui_component_remove")
                || name.equals("kof_ui_component_mount") || name.equals("kof_ui_component_unmount")
                || name.equals("kof_ui_nodes_live") || name.equals("kof_ui_flush_ui")
                || name.equals("kof_ui_event_type") || name.equals("kof_ui_emit")
                || name.equals("kof_ui_event_stop")
                || name.startsWith("kof_ui_event_key") || name.startsWith("kof_ui_event_value")
                || name.startsWith("kof_ui_event_x") || name.startsWith("kof_ui_event_y")
                || name.startsWith("kof_ui_event_target") || name.startsWith("kof_ui_event_related_target")
                || name.equals("kof_ui_store_new") || name.equals("kof_ui_app_state") || name.equals("kof_ui_store_get")
                || name.equals("kof_ui_store_set") || name.equals("kof_ui_store_subscribe")
                || name.equals("kof_ui_store_unsubscribe") || name.equals("kof_ui_stores_live")
                || name.equals("kof_ui_route_register") || name.equals("kof_ui_router_go1")
                || name.equals("kof_ui_router_go2") || name.equals("kof_ui_router_replace1")
                || name.equals("kof_ui_router_replace2") || name.equals("kof_ui_router_back")
                || name.equals("kof_ui_router_forward") || name.equals("kof_ui_router_param")
                || name.equals("kof_ui_router_current") || name.equals("kof_ui_router_depth")) {
            p.lc.registerRuntime(JsTypeMapper.capitalizeUiFn(name));
            List<JsIr.JsExpression> callArgs = new ArrayList<>(args);
            if (kc.kind() == KofCallKind.INSTANCE && receiver != null) {
                callArgs.add(0, receiver);
            }
            JsIr.JsExpression call = new JsIr.JsCall(
                    new JsIr.JsIdentifier(JsTypeMapper.capitalizeUiFn(name)), callArgs);
            if (Type.isVoid(kc.returnType())) {
                throw new StatementEnd(call);
            }
            stack.add(call);
            return;
        }
        if (name.equals("kof_scheduler_every")) {
            p.lc.registerRuntime("kofSchedulerEvery");
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofSchedulerEvery"), args);
            if (Type.isVoid(kc.returnType())) throw new StatementEnd(call);
            stack.add(call);
            return;
        }
        if (name.equals("kof_scheduler_at")) {
            p.lc.registerRuntime("kofSchedulerAt");
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofSchedulerAt"), args);
            if (Type.isVoid(kc.returnType())) throw new StatementEnd(call);
            stack.add(call);
            return;
        }
        if (name.equals("kof_scheduler_cancel")) {
            p.lc.registerRuntime("kofSchedulerCancel");
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofSchedulerCancel"), args);
            throw new StatementEnd(call);
        }
        if (name.startsWith("kof_http_")) {
            // JS real via Java HttpClient interop (GraalJS allowAllAccess)
            String jsFn = switch (name) {
                case "kof_http_get" -> "kofHttpGet";
                case "kof_http_get_headers" -> "kofHttpGetHeaders";
                case "kof_http_delete" -> "kofHttpDelete";
                case "kof_http_delete_headers" -> "kofHttpDeleteHeaders";
                case "kof_http_options" -> "kofHttpOptions";
                case "kof_http_options_headers" -> "kofHttpOptionsHeaders";
                case "kof_http_post" -> "kofHttpPost";
                case "kof_http_post_headers" -> "kofHttpPostHeaders";
                case "kof_http_put" -> "kofHttpPut";
                case "kof_http_put_headers" -> "kofHttpPutHeaders";
                case "kof_http_patch" -> "kofHttpPatch";
                case "kof_http_patch_headers" -> "kofHttpPatchHeaders";
                case "kof_http_status" -> "kofHttpStatus";
                case "kof_http_timeout_set" -> "kofHttpTimeoutSet";
                case "kof_http_retry_set" -> "kofHttpRetrySet";
                case "kof_http_circuit_set" -> "kofHttpCircuitSet";
                default -> "kofWebStub";
            };
            p.lc.registerRuntime(jsFn);
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(jsFn), args);
            if (Type.isVoid(kc.returnType())) {
                throw new StatementEnd(call);
            }
            stack.add(call);
            return;
        }
        // §265 (JS): nao colapsar status()/headerSet() no 2º arg — havia aqui
        // um ramo `status -> args.get(1)` / `headerSet -> args.get(1)` que
        // descartava a chamada inteira (side-effect perdido) e SOMBRAVA o ramo
        // correto mais abaixo (kofWebStatus/kofWebHeaderSet). Removido; cai no
        // dispatch real.
        if (name.startsWith("kof_web_")) {
            // JS target: WEB001 REAL IMPLEMENTATION via GraalJS HttpServer
            // Uses Java.type('com.sun.net.8') + Value-based handler invoke
            // for GraalJS CreateObject interop. The handler (lambda obj) has
            // an 'invoke' method that processes Exchange.
            if (name.equals("kof_web_app_new")) {
                p.lc.registerRuntime("kofWebAppNew");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebAppNew"), List.of()));
                return;
            }
            if (name.equals("kof_web_route")) {
                p.lc.registerRuntime("kofWebRoute");
                JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofWebRoute"), args);
                throw new StatementEnd(call);
            }
            // WEB001 SSE (16/09): rota SSE no host GraalJS (handler-scoped).
            if (name.equals("kof_web_sse_route")) {
                p.lc.registerRuntime("kofWebSseRoute");
                JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofWebSseRoute"), args);
                throw new StatementEnd(call);
            }
            if (name.equals("kof_web_listen")) {
                p.lc.registerRuntime("kofWebListen");
                JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofWebListen"), args);
                if (Type.isVoid(kc.returnType())) {
                    throw new StatementEnd(call);
                }
                stack.add(call);
                return;
            }
            // WEB001-T1 (13/09): helpers de contexto reais no JS — param/
            // query/header/body/method/path leem o request corrente
            // (kofWebRequest no JsRuntimeUiWeb). R6: nunca stub silencioso.
            if (name.equals("kof_web_param") && args.size() == 1) {
                p.lc.registerRuntime("kofWebParam");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebParam"), args));
                return;
            }
            if (name.equals("kof_web_query") && args.size() == 1) {
                p.lc.registerRuntime("kofWebQuery");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebQuery"), args));
                return;
            }
            if (name.equals("kof_web_header") && args.size() == 1) {
                p.lc.registerRuntime("kofWebHeader");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebHeader"), args));
                return;
            }
            if (name.equals("kof_web_body") && args.isEmpty()) {
                p.lc.registerRuntime("kofWebBody");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebBody"), List.of()));
                return;
            }
            if (name.equals("kof_web_method") && args.isEmpty()) {
                p.lc.registerRuntime("kofWebMethod");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebMethod"), List.of()));
                return;
            }
            if (name.equals("kof_web_path") && args.isEmpty()) {
                p.lc.registerRuntime("kofWebPath");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebPath"), List.of()));
                return;
            }
            if (name.equals("kof_web_status") && args.size() == 2) {
                // response.status(code, text) — o 2º arg (texto) é o corpo;
                // mantém o valor de String no topo (contrato JVM).
                p.lc.registerRuntime("kofWebStatus");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebStatus"), args));
                return;
            }
            if (name.equals("kof_web_header_set") && args.size() == 2) {
                p.lc.registerRuntime("kofWebHeaderSet");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebHeaderSet"), args));
                return;
            }
            // WEB001 SSE (16/09): sse(text) dentro do handler de app.sse —
            // escreve na conexão corrente (kofWebSseConn, handler-scoped).
            if (name.equals("kof_web_sse_send") && args.size() == 1) {
                p.lc.registerRuntime("kofWebSseSend");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebSseSend"), args));
                return;
            }
            // fallback: R6 — o gap é EXPLICITO (kofWebStub lança WEB001), nunca undefined
            p.lc.registerRuntime("kofWebStub");
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofWebStub"), args);
            if (Type.isVoid(kc.returnType())) {
                throw new StatementEnd(call);
            }
            stack.add(call);
            return;
        }
        // S13a (plan-stdlib-expansion): fachada math.parseInt/parseLong/
        // parseDouble -> as runtime fns EXISTENTES do backend JS
        // (JsRuntimeUiStdlib exporta kof_string_to_* em snake RAW; idem
        // METHOD case do JsCallEmitter — mesma export, 1 face só).
        // S13b: parse com default (briefing §43) — falha DEVOLVE o default,
        // nunca lança (wrapper try/catch em kof_string_to_*).
        if (name.startsWith("kof_string_to_") && name.endsWith("_or_default") && args.size() == 2) {
            p.lc.registerRuntime(name);
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(name), args));
            return;
        }
        if (name.startsWith("kof_string_to_") && args.size() == 1) {
            p.lc.registerRuntime(name);
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(name), List.of(args.get(0))));
            return;
        }
        if (name.equals("kof_now")) {
            stack.add(new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("Date"), "now"), List.of()));
            return;
        }
        if (name.startsWith("kof_sec_")) {
            p.lc.registerRuntime(JsTypeMapper.runtimeJsName(name));
            List<JsIr.JsExpression> callArgs = new ArrayList<>(args);
            JsIr.JsExpression call = new JsIr.JsCall(
                    new JsIr.JsIdentifier(JsTypeMapper.runtimeJsName(name)), callArgs);
            if (Type.isVoid(kc.returnType())) {
                throw new StatementEnd(call);
            }
            stack.add(call);
            return;
        }
        if (name.startsWith("kof_string_to_")) {
            // S13a (plan-stdlib-expansion): fachada math.parseInt/parseLong/
            // parseDouble -> as runtime fns EXISTENTES do backend JS
            // (JsRuntimeUiStdlib exporta kof_string_to_* em snake RAW). O
            // prefixo no isRuntimeOp também roteia o METHOD path (.toInt()
            // de StringMethodRegistry), que antes caía no handleStringOp —
            // o emit é IDÊNTOCO ao case de lá (JsCallEmitter:276: call RAW
            // com receiver); FUNCTION (fachada) usa args.get(0).
            p.lc.registerRuntime(name);
            JsIr.JsExpression sarg = kc.kind() == KofCallKind.FUNCTION && !args.isEmpty()
                    ? args.get(0) : receiver;
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(name), List.of(sarg)));
            return;
        }
        String fn = JsTypeMapper.runtimeJsName(name);
        if (name.startsWith("kof_io_") || name.startsWith("kof_db_") || name.startsWith("kof_orm_")
                || name.equals("kof_ffi") || name.equals("kof_ffi_void")
                || name.equals("kof_read_line")
                || name.equals("kof_read_file") || name.equals("kof_write_file")) {
            p.lc.registerIoRuntime(fn);
        } else {
            p.lc.registerRuntime(fn);
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        if (name.startsWith("kof_io_") && receiver != null) {
            callArgs.add(receiver);
        }
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if (name.equals("kof_ffi") && kc.returnType() instanceof Type.ClassType rt
                && p.lc.recordClassNames.contains(rt.internalName())) {
            // D6-1/3.8b (bridge JS 21/09): retorno struct por valor — o host
            // devolve os campos do struct (array, ordem de declaração) e o
            // factory estático `__kof_ffi_from` do record reconstrói a instância
            // pelo construtor canônico (paridade com kof_ffi_read_struct do JVM).
            String jsRet = JsTypeMapper.jsClassName(rt.internalName());
            call = new JsIr.JsCall(
                    new JsIr.JsMember(new JsIr.JsIdentifier(jsRet), "__kof_ffi_from"),
                    List.of(call));
        }
        if (name.startsWith("kof_db_query") && kc.returnType() instanceof Type.ClassType dbList
                && BuiltinTypes.isList(dbList) && !dbList.typeArguments().isEmpty()
                && dbList.typeArguments().get(0) instanceof Type.ClassType elem
                && p.lc.classMethodNames.containsKey(elem.internalName())) {
            // DB002 (18/09): bind tipado no GUEST. A wire é untyped (className
            // null — ExpressionDbCallLowerer), entao a ponte devolve rows JSON
            // strings; o MESMO helper `__kof_decode_<T>` do json.decode<List<T>>
            // (célula 82-101 acima) faz o parse+bind por linha. Paridade com a
            // JVM, onde db.query<T> e json.decode compartilham kof_json_bind.
            String jsName = JsTypeMapper.jsClassName(elem.internalName());
            p.lc.decodeHelpers.add(jsName);
            call = new JsIr.JsCall(new JsIr.JsMember(call, "map"),
                    List.of(new JsIr.JsArrow(List.of("o"), new JsIr.JsCall(
                            new JsIr.JsIdentifier("__kof_decode_" + jsName),
                            List.of(new JsIr.JsIdentifier("o"))))));
        }
        if (name.startsWith("kof_orm_")) {
            // ORM001 (18/09): kof.orm no JS. A ponte host devolve rows/record
            // como JSON strings (ou null p/ find ausente); o MESMO
            // `__kof_decode_<T>` do json.decode/db.query faz o bind, dando
            // paridade byte-a-byte com JvmOrmRuntime. List<T> (all/where/
            // where_op/page) binda por linha; record único (find/save) passa por
            // kofOrmSingle p/ preservar null de find sem re-executar a chamada.
            if (kc.returnType() instanceof Type.ClassType ort) {
                if (BuiltinTypes.isList(ort) && !ort.typeArguments().isEmpty()
                        && ort.typeArguments().get(0) instanceof Type.ClassType elem
                        && p.lc.classMethodNames.containsKey(elem.internalName())) {
                    String jsName = JsTypeMapper.jsClassName(elem.internalName());
                    p.lc.decodeHelpers.add(jsName);
                    call = new JsIr.JsCall(new JsIr.JsMember(call, "map"),
                            List.of(new JsIr.JsArrow(List.of("o"), new JsIr.JsCall(
                                    new JsIr.JsIdentifier("__kof_decode_" + jsName),
                                    List.of(new JsIr.JsIdentifier("o"))))));
                } else if (!BuiltinTypes.isList(ort) && !BuiltinTypes.isString(ort)
                        && p.lc.classMethodNames.containsKey(ort.internalName())) {
                    String jsName = JsTypeMapper.jsClassName(ort.internalName());
                    p.lc.decodeHelpers.add(jsName);
                    p.lc.registerIoRuntime("kofOrmSingle");
                    call = new JsIr.JsCall(new JsIr.JsIdentifier("kofOrmSingle"), List.of(call,
                            new JsIr.JsArrow(List.of("o"), new JsIr.JsCall(
                                    new JsIr.JsIdentifier("__kof_decode_" + jsName),
                                    List.of(new JsIr.JsIdentifier("o"))))));
                }
            }
        }
        if (name.equals("kof_await") || name.equals("kof_await_timeout")
                || name.equals("kof_select_any")
                || name.equals("kof_time_sleep")) { // §132/#83-JS cooperative sleep
            call = new JsIr.JsAwait(call);
        }
        if (name.equals("kof_poll") && kc.returnType() instanceof Type.PrimitiveType) {
            // poll não-pronto devolve default do primitivo (0/false), não null —
            // paridade JVM/Native e evita await acidental em função síncrona.
            call = new JsIr.JsBinary(call, "??", JsTypeMapper.defaultForType(kc.returnType()));
        }
        if (Type.isVoid(kc.returnType())) {
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    // §239 (JS): String.format(String, Object...) — o lowering compartilhado
    // (StringFormatCallLowerer) empacota os varargs num Object[] e emite um
    // KofCall STATIC owner=java/lang/String. Sem ramo proprio caia no dispatch
    // estatico generico do JsCallEmitter: jsClassName("java/lang/String") devolve
    // null (nao ha java_lang_String no class map JS) -> JsMember(null) -> ICE
    // COMP002 ("unknown JS expression: null"), familia do §235. Roteamos para o
    // export `kofStringFormat` do runtime `io`, que delega ao host
    // kof_platform.stringFormat -> java.lang.String.format (paridade byte-a-byte
    // no runner GraalJS). Browser (sem kof_platform): o Proxy do kof_platform da
    // erro honesto em runtime, mesmo degrade de kof.io/FFI/process (R6/R7), nunca
    // um valor errado em silencio. args[0]=fmt, args[1]=Object[] (array JS).
    // #466: o lowering compartilhado agora passa Locale.ROOT como 1º arg
    // (String.format(String,...) e locale-sensitive; ROOT trava o decimal
    // pont em qualquer host — paridade JVM/Script/browser). O gate aceita a
    // forma antiga de 2 params (bytecode de cache/arte preexistente) e a nova
    // de 3; no JS a Locale e ignorada — kofStringFormat ja e deterministico
    // por construcao (toFixed/raiz) e o host Graal formata com ROOT.
    boolean isStaticFormat(KofCall kc) {
        int n = kc.parameterTypes().size();
        if (kc.kind() != KofCallKind.STATIC || !"format".equals(kc.methodName())
                || !BuiltinTypes.isString(kc.ownerType())) {
            return false;
        }
        if (!(kc.parameterTypes().get(n - 1) instanceof Type.ArrayType)) {
            return false;
        }
        return n == 2 || (n == 3 && "Locale".equals(JsTypeMapper.className(kc.parameterTypes().get(0))));
    }

    void emitStaticFormat(List<Object> stack, List<JsIr.JsExpression> args) {
        p.lc.registerIoRuntime("kofStringFormat");
        int off = args.size() == 3 ? 1 : 0;
        stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStringFormat"),
                List.of(args.get(off), args.get(off + 1))));
    }
}
