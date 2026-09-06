package dev.kof.compiler;

/**
 * Emissão do ASM de UI (kof_ui_color/window) do runtime nativo. Domínio isolado
 * do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeUi {

    private RuntimeUi() {}

    static void emitUiColorFunctions(StringBuilder sb) {
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

    static void emitUiWindowFunctions(StringBuilder sb) {
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
            kof_ui_input_text:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_input_remove:
                ret
            kof_ui_column_new:
                movl $1, %eax
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
            kof_ui_canvas_clear_rect:
                ret
            """);
    }

}