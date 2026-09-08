package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de UI (kof_ui_color/window) do runtime nativo. Domínio isolado
 * do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeUi {

    private RuntimeUi() {}

    public static void emitUiColorFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lui_rgb: .asciz "rgb("
            .Lui_rgba: .asciz "rgba("
            .Lui_comma: .asciz ", "
            .Lui_close_str: .asciz ")"
            .section .text

            kof_ui_color_to_css:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movl %edi, %ebx
                movl %ebx, %r12d
                andl $255, %r12d
                xorl %r14d, %r14d
                cmpl $255, %r12d
                je .Lui_rgb_prefix
                leaq .Lui_rgba(%rip), %rdi
                movq $5, %rsi
                call kof_string_from_literal
                movq %rax, %r15
                movq $1, %r14
                jmp .Lui_red
            .Lui_rgb_prefix:
                leaq .Lui_rgb(%rip), %rdi
                movq $4, %rsi
                call kof_string_from_literal
                movq %rax, %r15
            .Lui_red:
                movl %ebx, %r12d
                shrl $24, %r12d
                andl $255, %r12d
                movl %r12d, %edi
                call kof_int_to_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                leaq .Lui_comma(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                movl %ebx, %r12d
                shrl $16, %r12d
                andl $255, %r12d
                movl %r12d, %edi
                call kof_int_to_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                leaq .Lui_comma(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                movl %ebx, %r12d
                shrl $8, %r12d
                andl $255, %r12d
                movl %r12d, %edi
                call kof_int_to_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                testq %r14, %r14
                je .Lui_close
                leaq .Lui_comma(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                movl %ebx, %r12d
                andl $255, %r12d
                movl %r12d, %edi
                call kof_int_to_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
            .Lui_close:
                leaq .Lui_close_str(%rip), %rdi
                movq $1, %rsi
                call kof_string_from_literal
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }

    public static void emitUiWindowFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lui_empty: .asciz ""
            .section .text

            kof_ui_window_new:
                movl $1, %eax
                ret
            kof_ui_window_set_title:
                ret
            kof_ui_window_title:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_window_bind:
                ret
            kof_ui_window_show:
                ret
            kof_ui_window_close:
                ret
            kof_ui_window_set_size:
                ret
            kof_ui_window_set_theme:
                ret
            kof_ui_label_new:
                movl $1, %eax
                ret
            kof_ui_label_set_text:
                ret
            kof_ui_label_text:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_label_set_font_size:
                ret
            kof_ui_label_font_size:
                xorl %eax, %eax
                ret
            kof_ui_label_set_bold:
                ret
            kof_ui_label_bold:
                xorl %eax, %eax
                ret
            kof_ui_label_set_color:
                ret
            kof_ui_label_color:
                xorl %eax, %eax
                ret
            kof_ui_label_remove:
                ret
            kof_ui_button_new:
                movl $1, %eax
                ret
            kof_ui_button_new_action:
                movl $1, %eax
                ret
            kof_ui_button_set_text:
                ret
            kof_ui_button_text:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_button_remove:
                ret
            kof_ui_input_new:
                movl $1, %eax
                ret
            kof_ui_input_set_text:
                ret
            kof_ui_input_set_placeholder:
                ret
            kof_ui_input_set_name:
                ret
            kof_ui_input_set_readonly:
                ret
            kof_ui_input_set_type:
                ret
            kof_ui_input_set_checked:
                ret
            kof_ui_input_checked:
                xorl %eax, %eax
                ret
            kof_ui_input_text:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_input_remove:
                ret
            kof_ui_textarea_new:
                movl $1, %eax
                ret
            kof_ui_textarea_set_text:
                ret
            kof_ui_textarea_text:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_textarea_set_name:
                ret
            kof_ui_textarea_set_readonly:
                ret
            kof_ui_textarea_set_placeholder:
                ret
            kof_ui_textarea_remove:
                ret
            kof_ui_select_new:
                movl $1, %eax
                ret
            kof_ui_select_set_options:
                ret
            kof_ui_select_set_selected:
                ret
            kof_ui_select_selected:
                xorl %eax, %eax
                ret
            kof_ui_select_remove:
                ret
            kof_ui_ul_new:
            kof_ui_ol_new:
                movl $1, %eax
                ret
            kof_ui_ul_set_items:
            kof_ui_ol_set_items:
            kof_ui_ul_remove:
            kof_ui_ol_remove:
                ret
            kof_ui_table_new:
                movl $1, %eax
                ret
            kof_ui_table_set_rows:
            kof_ui_table_remove:
                ret
            kof_ui_fieldset_new:
            kof_ui_fieldset_new_legend:
            kof_ui_iframe_new:
            kof_ui_video_new:
            kof_ui_audio_new:
                movl $1, %eax
                ret
            kof_ui_fieldset_remove:
            kof_ui_iframe_remove:
            kof_ui_video_remove:
            kof_ui_audio_remove:
            kof_ui_hr_remove:
                ret
            kof_ui_hr_new:
                movl $1, %eax
                ret
            # Font / Icon / Image / Link / widget-font — no-op (paridade com
            # JVM; antes: undefined reference [COMP001] no link, R6/UI001).
            kof_ui_font_new:
                movl $1, %eax
                ret
            kof_ui_font_new_bold:
                movl $1, %eax
                ret
            kof_ui_icon_new:
                movl $1, %eax
                ret
            kof_ui_icon_new_size:
                movl $1, %eax
                ret
            kof_ui_icon_size:
                movl $24, %eax
                ret
            kof_ui_icon_name:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_icon_remove:
                ret
            kof_ui_icon_set_name:
                ret
            kof_ui_icon_set_size:
                ret
            kof_ui_image_new:
                movl $1, %eax
                ret
            kof_ui_image_src:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_image_remove:
                ret
            kof_ui_image_set_src:
                ret
            kof_ui_image_set_alt:
                ret
            kof_ui_image_set_width:
                ret
            kof_ui_image_set_height:
                ret
            kof_ui_link_new:
                movl $1, %eax
                ret
            kof_ui_link_text:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_link_url:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_link_remove:
                ret
            kof_ui_link_set_text:
                ret
            kof_ui_link_set_url:
                ret
            kof_ui_widget_font:
                movl $-1, %eax
                ret
            kof_ui_widget_set_font:
                ret
            kof_ui_widget_set_id:
                ret
            kof_ui_widget_set_class:
                ret
            kof_ui_widget_set_disabled:
                ret
            kof_ui_widget_on:
                ret
            kof_ui_column_new:
                movl $1, %eax
                ret
            kof_ui_form_new:
                movl $1, %eax
                ret
            kof_ui_form_on_submit:
                ret
            kof_ui_form_submit:
                ret
            kof_ui_row_new:
                movl $1, %eax
                ret
            kof_ui_view_new:
                movl $1, %eax
                ret
            kof_ui_style_new:
                movl $1, %eax
                ret
            kof_ui_view_bind:
                ret
            kof_ui_view_remove:
                ret
            // ── Fase 4: primitivas de layout (no-ops) ──
            kof_ui_box_new:
                movl $1, %eax
                ret
            kof_ui_stack_new:
                movl $1, %eax
                ret
            kof_ui_wrap_new:
                movl $1, %eax
                ret
            kof_ui_grid_new:
                movl $1, %eax
                ret
            kof_ui_spacer_new:
                movl $1, %eax
                ret
            kof_ui_center_new:
                movl $1, %eax
                ret
            kof_ui_align_new:
                movl $1, %eax
                ret
            // ── Component Core (docs/ui/architecture.md) ──
            kof_ui_component_new:
                movl $1, %eax
                ret
            kof_ui_component_state_get:
                xorl %eax, %eax
                ret
            kof_ui_component_state_set:
                ret
            kof_ui_component_view:
                ret
            kof_ui_component_on_mount:
                ret
            kof_ui_component_on_dispose:
                ret
            kof_ui_component_effect:
                ret
            kof_ui_component_on:
                ret
            kof_ui_component_bind:
                ret
            kof_ui_component_remove:
                ret
            kof_ui_component_mount:
                ret
            kof_ui_component_unmount:
                ret
            kof_ui_nodes_live:
                xorl %eax, %eax
                ret
            kof_ui_flush_ui:
                ret
            kof_ui_event_type:
                movq %rdi, %rax
                ret
            kof_ui_event_key:
            kof_ui_event_value:
                leaq .Lui_empty(%rip), %rax
                ret
            kof_ui_event_x:
            kof_ui_event_y:
                xorl %eax, %eax
                ret
            kof_ui_emit:
                ret
            kof_ui_event_stop:
                ret
            // e.type() / e.stopPropagation() on a kof.ui.Event receiver: the
            // backend mangles the owner class name (Event_type), aliased to
            // the runtime intrinsics.
            .globl Event_type
            Event_type:
                jmp kof_ui_event_type
            .globl Event_stopPropagation
            Event_stopPropagation:
                jmp kof_ui_event_stop
            .globl Event_key
            Event_key:
                jmp kof_ui_event_key
            .globl Event_value
            Event_value:
                jmp kof_ui_event_value
            .globl Event_x
            Event_x:
                jmp kof_ui_event_x
            .globl Event_y
            Event_y:
                jmp kof_ui_event_y
            // ── Fase 8: Store observável (no-ops) ──
            kof_ui_store_new:
                movl $1, %eax
                ret
            kof_ui_store_get:
                xorl %eax, %eax
                ret
            kof_ui_store_set:
                ret
            kof_ui_store_subscribe:
                ret
            kof_ui_store_unsubscribe:
                ret
            kof_ui_stores_live:
                xorl %eax, %eax
                ret
            .globl Store_get
            Store_get:
                xorl %eax, %eax
                ret
            // ── Fase 7: Router (no-ops -- UI é KofJS) ──
            kof_ui_route_register:
                ret
            kof_ui_router_go1:
            kof_ui_router_go2:
            kof_ui_router_replace1:
            kof_ui_router_replace2:
            kof_ui_router_back:
            kof_ui_router_forward:
                xorl %eax, %eax           # false (não navegou)
                ret
            kof_ui_router_param:
            kof_ui_router_current:
                leaq .Lkrtr_empty(%rip), %rdi
                movl $0, %esi
                jmp kof_string_from_literal
            kof_ui_router_depth:
                xorl %eax, %eax
                ret
            .Lkrtr_empty: .asciz ""
            // ── Canvas 2D (no-ops — UI é KofJS) ──
            kof_ui_canvas_new:
                movl $1, %eax
                ret
            kof_ui_canvas_begin_path:
            kof_ui_canvas_close_path:
            kof_ui_canvas_fill:
            kof_ui_canvas_stroke:
            kof_ui_canvas_remove:
                ret
            kof_ui_canvas_move_to:
            kof_ui_canvas_line_to:
            kof_ui_canvas_set_line_width:
                ret
            kof_ui_canvas_arc:
                ret
            kof_ui_canvas_set_fill:
            kof_ui_canvas_set_stroke:
                ret
            kof_ui_canvas_save:
            kof_ui_canvas_restore:
            kof_ui_canvas_set_global_alpha:
            kof_ui_canvas_fill_text:
            kof_ui_canvas_transform:
                ret
            kof_ui_canvas_measure_text:
                xorpd %xmm0, %xmm0
                ret
            kof_ui_canvas_draw_image:
                ret
            kof_ui_canvas_clear_rect:
                ret
            """);
    }

}