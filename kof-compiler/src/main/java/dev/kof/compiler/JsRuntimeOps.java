package dev.kof.compiler;

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
final class JsRuntimeOps {

    private final JsMethodParser p;

    JsRuntimeOps(JsMethodParser p) {
        this.p = p;
    }

boolean isRuntimeOp(KofCall kc) {
        String name = kc.methodName();
        return name.startsWith("kof_json_") || name.startsWith("kof_io_")
                || name.startsWith("kof_ui_")
                || name.startsWith("kof_sec_")
                || name.startsWith("kof_validation_")
                || name.startsWith("kof_enum_")
                || name.startsWith("kof_config_")
                || name.startsWith("kof_cache_")
                || name.startsWith("kof_web_") || name.startsWith("kof_db_") || name.startsWith("kof_http_")
                || name.equals("kof_spawn") || name.equals("kof_spawn_result") || name.equals("kof_await")
                || name.equals("kof_poll") || name.equals("kof_done")
                || name.equals("kof_cancel") || name.equals("kof_cancelled")
                || name.equals("kof_await_timeout")
                || name.equals("kof_select_any")
                || name.equals("kof_list_map") || name.equals("kof_list_filter")
                || name.equals("kof_list_reduce")
                || name.startsWith("kof_observability_")
                || name.startsWith("kof_time_")
                || name.startsWith("kof_scheduler_")
                || name.startsWith("kof_mq_")
                || name.startsWith("kof_log_")
                || name.equals("kof_ui_color_to_css")
                || name.equals("kof_now") || name.equals("kof_read_line")
                || name.equals("kof_read_file") || name.equals("kof_write_file")
                || name.equals("kof_process_run") || name.equals("kof_process_exit")
                || name.equals("kof_args")
                || name.equals("kof_box") || name.equals("kof_unbox");
    }

void handleRuntimeOp(MethodCtx ctx, List<Object> stack,
                              List<JsIr.JsExpression> preambleExprs, KofCall kc,
                              JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String name = kc.methodName();
        if (name.startsWith("kof_json_")) {
            // JSON encode/decode maps directly to JSON.stringify/parse; the
            // type information stays in the Kof compiler (generics erasure).
            JsIr.JsExpression value = kc.kind() == KofCallKind.FUNCTION
                    ? args.get(0) : receiver;
            if (name.contains("encode")) {
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
        if (name.equals("kof_process_run")) {
            p.lc.registerIoRuntime("kofProcessRun");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofProcessRun"), args));
            return;
        }
        if (name.equals("kof_process_exit")) {
            // sentinel capturado pelo KofJsRunner — nunca use System.exit
            // dentro da engine (mataria o processo hospedeiro)
            p.lc.registerIoRuntime("kofProcessExit");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofProcessExit"), args));
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
                || name.equals("kof_ui_row_new") || name.equals("kof_ui_view_new")
                || name.equals("kof_ui_box_new") || name.equals("kof_ui_stack_new")
                || name.equals("kof_ui_wrap_new") || name.equals("kof_ui_grid_new")
                || name.equals("kof_ui_spacer_new") || name.equals("kof_ui_center_new")
                || name.equals("kof_ui_align_new")
                || name.equals("kof_ui_style_new") || name.equals("kof_ui_view_bind")
                || name.equals("kof_ui_window_set_title") || name.equals("kof_ui_window_title")
                || name.equals("kof_ui_window_bind") || name.equals("kof_ui_window_show")
                || name.equals("kof_ui_window_close") || name.equals("kof_ui_label_set_text")
                || name.equals("kof_ui_label_text") || name.equals("kof_ui_label_remove")
                || name.equals("kof_ui_button_set_text") || name.equals("kof_ui_button_text")
                || name.equals("kof_ui_button_remove") || name.equals("kof_ui_input_set_text")
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
                || name.equals("kof_ui_store_new") || name.equals("kof_ui_store_get")
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
        if (name.equals("kof_web_status") && args.size() == 2) {
            stack.add(args.get(1));
            return;
        }
        if (name.equals("kof_web_header_set") && args.size() == 2) {
            stack.add(args.get(1));
            return;
        }
        if (name.startsWith("kof_web_")) {
            // JS target: WEB001 REAL IMPLEMENTATION via GraalJS HttpServer
            // Uses Java.type('com.sun.net.8') + Value-based handler invoke
            // for GraalJS CreateObject interop. The handler (lambda obj) has
            // an 'invoke' method that processes Exchange.
            if (name.equals("kof_web_app_new")) {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebAppNew"), List.of()));
                return;
            }
            if (name.equals("kof_web_route")) {
                p.lc.registerRuntime("kofWebRoute");
                JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofWebRoute"), args);
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
            if (name.equals("kof_web_status") && args.size() == 2) {
                stack.add(args.get(1));
                return;
            }
            if (name.equals("kof_web_header_set") && args.size() == 2) {
                stack.add(args.get(1));
                return;
            }
            // fallback: stub for unimplemented web functions
            p.lc.registerRuntime("kofWebStub");
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofWebStub"), args);
            if (Type.isVoid(kc.returnType())) {
                throw new StatementEnd(call);
            }
            stack.add(call);
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
        String fn = JsTypeMapper.runtimeJsName(name);
        if (name.startsWith("kof_io_") || name.equals("kof_read_line")
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
        if (name.equals("kof_await") || name.equals("kof_await_timeout")
                || name.equals("kof_select_any")) {
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
}
