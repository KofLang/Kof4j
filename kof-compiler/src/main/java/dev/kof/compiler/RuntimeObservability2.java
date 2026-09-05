package dev.kof.compiler;

/**
 * Emissão do ASM de observability2 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeObservability2 {

    private RuntimeObservability2() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                movq %r10, %rsi
                call kof_string_concat
                movq %rax, %r14
                popq %rsi
                ret

            # ── export counters ──
            .Lobs_m_cnt_loop:
                movq .Lkof_obs_counter_len(%rip), %r13
                cmpq %r13, %r12
                jge .Lobs_m_gauges
                leaq .Lkof_obs_counters(%rip), %rbx
                movq %r12, %rax
                shlq $4, %rax
                addq %rax, %rbx
                movq 0(%rbx), %rax
                testq %rax, %rax
                jz .Lobs_m_cnt_next
                leaq .Lstr_obs_type_counter(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_ws_nl_counter(%rip), %rdi
                movl $10, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_space(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movl 8(%rbx), %edi
                call kof_int_to_string
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_nl(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
            .Lobs_m_cnt_next:
                incq %r12
                jmp .Lobs_m_cnt_loop

            # ── export gauges ──
            .Lobs_m_gauges:
                xorq %r12, %r12
            .Lobs_m_ga_loop:
                movq .Lkof_obs_gauge_len(%rip), %r13
                cmpq %r13, %r12
                jge .Lobs_m_hists
                leaq .Lkof_obs_gauges(%rip), %rbx
                movq %r12, %rax
                shlq $4, %rax
                addq %rax, %rbx
                movq 0(%rbx), %rax
                testq %rax, %rax
                jz .Lobs_m_ga_next
                leaq .Lstr_obs_type_counter(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_ws_nl_gauge(%rip), %rdi
                movl $9, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_space(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movl 8(%rbx), %edi
                call kof_int_to_string
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_nl(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
            .Lobs_m_ga_next:
                incq %r12
                jmp .Lobs_m_ga_loop

            # ── export histograms ──
            .Lobs_m_hists:
                xorq %r12, %r12
            .Lobs_m_hi_loop:
                movq .Lkof_obs_histogram_len(%rip), %r13
                cmpq %r13, %r12
                jge .Lobs_m_done
                leaq .Lkof_obs_histograms(%rip), %rbx
                movq %r12, %rax
                shlq $5, %rax
                addq %rax, %rbx
                movq 0(%rbx), %rax
                testq %rax, %rax
                jz .Lobs_m_hi_next
                # # TYPE <n>_count counter\n
                leaq .Lstr_obs_type_counter(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_suffix_count(%rip), %rdi
                movl $6, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_ws_nl_counter(%rip), %rdi
                movl $10, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                # <n>_count <c>\n
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_suffix_count(%rip), %rdi
                movl $6, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_space(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 16(%rbx), %rax
                movl %eax, %edi
                call kof_int_to_string
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_nl(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                # # TYPE <n>_sum gauge\n
                leaq .Lstr_obs_type_counter(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_suffix_sum(%rip), %rdi
                movl $4, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_ws_nl_gauge(%rip), %rdi
                movl $9, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                # <n>_sum <s>\n
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_suffix_sum(%rip), %rdi
                movl $4, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_space(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 8(%rbx), %rax
                movl %eax, %edi
                call kof_int_to_string
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_nl(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
            .Lobs_m_hi_next:
                incq %r12
                jmp .Lobs_m_hi_loop

            # ── final: libera temp restante e retorna acc ──
            .Lobs_m_done:
                testq %r15, %r15
                jz .Lobs_m_free_done
                movq %r15, %rdi
                call kof_free
            .Lobs_m_free_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_observability_request_id() -> String
            .globl kof_observability_request_id
            .type kof_observability_request_id, @function
            kof_observability_request_id:
                movq $16, %rdi
                jmp kof_sec_random_hex

            # kof_observability_correlation_id() -> String
            .globl kof_observability_correlation_id
            .type kof_observability_correlation_id, @function
            kof_observability_correlation_id:
                movq $16, %rdi
                jmp kof_sec_random_hex

            # kof_observability_trace_id() -> String (16 bytes = 32 hex, W3C)
            .globl kof_observability_trace_id
            .type kof_observability_trace_id, @function
            kof_observability_trace_id:
                movq $16, %rdi
                jmp kof_sec_random_hex

            # kof_observability_span_id() -> String (8 bytes = 16 hex, W3C)
            .globl kof_observability_span_id
            .type kof_observability_span_id, @function
            kof_observability_span_id:
                movq $8, %rdi
                jmp kof_sec_random_hex

            # kof_observability_span_start(rdi=name) -> String handle
            # handle = traceId(32 hex) + spanId(16 hex) = 48 chars
            .globl kof_observability_span_start
            .type kof_observability_span_start, @function
            kof_observability_span_start:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                # traceId
                movq $16, %rdi
                call kof_sec_random_hex
                movq %rax, %rbx
                # spanId
                movq $8, %rdi
                call kof_sec_random_hex
                movq %rax, %r12
                # handle = traceId + spanId
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_string_concat
                movq %rax, %r14
                # registrar start (ms) na tabela de spans
                movq .Lkof_obs_span_len(%rip), %r13
                cmpq $64, %r13
                jge .Lobs_span_start_done
                call kof_now
                movq %r13, %rcx
                imulq $16, %rcx
                leaq .Lkof_obs_span_handles(%rip), %rdx
                addq %rcx, %rdx
                movq %r14, 0(%rdx)
                movq %rax, 8(%rdx)
                incq %r13
                movq %r13, .Lkof_obs_span_len(%rip)
            .Lobs_span_start_done:
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_observability_span_end(rdi=handle) -> String (JSON)
            .globl kof_observability_span_end
            .type kof_observability_span_end, @function
            kof_observability_span_end:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq .Lkof_obs_span_len(%rip), %r12
                xorq %r13, %r13
            .Lobs_span_end_search:
                cmpq %r12, %r13
                jge .Lobs_span_end_missing
                leaq .Lkof_obs_span_handles(%rip), %r14
                movq %r13, %rax
                imulq $16, %rax
                addq %rax, %r14
                movq 0(%r14), %r15
                testq %r15, %r15
                jz .Lobs_span_end_next
                movq %rbx, %rdi
                movq %r15, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lobs_span_end_found
            .Lobs_span_end_next:
                incq %r13
                jmp .Lobs_span_end_search
            .Lobs_span_end_found:
                movq 8(%r14), %r15          # start ms
                call kof_now
                subq %r15, %rax             # duration ms
                movq %rax, %r12
                # limpar a entrada
                movq $0, 0(%r14)
                movq $0, 8(%r14)
                # traceId = handle.substring(0, 32)
                movq %rbx, %rdi
                movq $0, %rsi
                movq $32, %rdx
                call kof_string_substring
                movq %rax, %r15            # traceId
                # spanId = handle.substring(32, 48)
                movq %rbx, %rdi
                movq $32, %rsi
                movq $48, %rdx
                call kof_string_substring
                movq %rax, %r14            # spanId
                # acc = "{"traceId":"
                leaq .Lstr_obs_span_1(%rip), %rdi
                movq $12, %rsi
                call kof_string_from_literal
                movq %rax, %r13            # acc
                # acc = concat(acc, traceId)
                movq %r13, %rdi
                movq %r15, %rsi
                call kof_string_concat
                movq %rax, %r13
                # acc = concat(acc, ","spanId":")
                leaq .Lstr_obs_span_2(%rip), %rdi
                movq $12, %rsi
                call kof_string_from_literal
                movq %r13, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r13
                # acc = concat(acc, spanId)
                movq %r13, %rdi
                movq %r14, %rsi
                call kof_string_concat
                movq %rax, %r13
                # acc = concat(acc, ","durationMs":")
                leaq .Lstr_obs_span_3(%rip), %rdi
                movq $14, %rsi
                call kof_string_from_literal
                movq %r13, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r13
                # acc = concat(acc, duration)
                movq %r12, %rdi
                call kof_long_to_string
                movq %r13, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r13
                # acc = concat(acc, "}")
                leaq .Lstr_obs_span_4(%rip), %rdi
                movq $1, %rsi
                call kof_string_from_literal
                movq %r13, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r13
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lobs_span_end_missing:
                movq $0, %rax
                popq %r15
            """);
    }
}