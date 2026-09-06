package dev.kof.compiler;

/**
 * Emissão do ASM de observability1 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeObservability1 {

    private RuntimeObservability1() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_observability_health
            .type kof_observability_health, @function
            kof_observability_health:
                leaq .Lstr_obs_up(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                ret

            # kof_observability_readiness() -> 1
            .globl kof_observability_readiness
            .type kof_observability_readiness, @function
            kof_observability_readiness:
                movl $1, %eax
                ret

            # kof_observability_liveness() -> 1
            .globl kof_observability_liveness
            .type kof_observability_liveness, @function
            kof_observability_liveness:
                movl $1, %eax
                ret

            # kof_observability_counter(rdi=name) -> Int
            .globl kof_observability_counter
            .type kof_observability_counter, @function
            kof_observability_counter:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq .Lkof_obs_counter_len(%rip), %r12
                xorq %r13, %r13
            .Lobs_counter_search:
                cmpq %r12, %r13
                jge .Lobs_counter_notfound
                leaq .Lkof_obs_counters(%rip), %r14
                movq %r13, %r15
                shlq $4, %r15
                addq %r15, %r14
                movq 0(%r14), %r15
                testq %rbx, %rbx
                jz .Lobs_counter_check_null
                testq %r15, %r15
                jz .Lobs_counter_next
                movl 16(%rbx), %eax
                movl 16(%r15), %ecx
                cmpl %ecx, %eax
                jne .Lobs_counter_next
                testl %eax, %eax
                jz .Lobs_counter_found
                leaq 24(%rbx), %rdi
                leaq 24(%r15), %rsi
                movslq %eax, %rcx
                xorq %r10, %r10
            .Lobs_counter_cmp:
                cmpq %rcx, %r10
                jge .Lobs_counter_found
                movzbl (%rdi,%r10), %eax
                movzbl (%rsi,%r10), %edx
                cmpl %edx, %eax
                jne .Lobs_counter_next
                incq %r10
                jmp .Lobs_counter_cmp
            .Lobs_counter_check_null:
                testq %r15, %r15
                jnz .Lobs_counter_next
                jmp .Lobs_counter_found
            .Lobs_counter_next:
                incq %r13
                jmp .Lobs_counter_search
            .Lobs_counter_found:
                leaq .Lkof_obs_counters(%rip), %r10
                movq %r13, %rcx
                shlq $4, %rcx
                addq %rcx, %r10
                movl 8(%r10), %eax
                incl %eax
                movl %eax, 8(%r10)
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lobs_counter_notfound:
                cmpq $32, %r12
                jge .Lobs_counter_full
                leaq .Lkof_obs_counters(%rip), %rax
                movq %r12, %rcx
                shlq $4, %rcx
                addq %rcx, %rax
                movq %rbx, 0(%rax)
                movl $1, 8(%rax)
                movl $0, 12(%rax)
                incq %r12
                movq %r12, .Lkof_obs_counter_len(%rip)
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lobs_counter_full:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_observability_increment(rdi=name, rsi=delta) -> Int
            .globl kof_observability_increment
            .type kof_observability_increment, @function
            kof_observability_increment:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movq .Lkof_obs_counter_len(%rip), %r13
                xorq %r14, %r14
            .Lobs_inc_search:
                cmpq %r13, %r14
                jge .Lobs_inc_notfound
                leaq .Lkof_obs_counters(%rip), %r15
                movq %r14, %rax
                shlq $4, %rax
                addq %rax, %r15
                movq 0(%r15), %rax
                testq %rbx, %rbx
                jz .Lobs_inc_check_null
                testq %rax, %rax
                jz .Lobs_inc_next
                movl 16(%rbx), %ecx
                movl 16(%rax), %edx
                cmpl %edx, %ecx
                jne .Lobs_inc_next
                testl %ecx, %ecx
                jz .Lobs_inc_found
                leaq 24(%rbx), %rdi
                leaq 24(%rax), %rsi
                movslq %ecx, %rcx
                xorq %r10, %r10
            .Lobs_inc_cmp:
                cmpq %rcx, %r10
                jge .Lobs_inc_found
                movzbl (%rdi,%r10), %eax
                movzbl (%rsi,%r10), %edx
                cmpl %edx, %eax
                jne .Lobs_inc_next
                incq %r10
                jmp .Lobs_inc_cmp
            .Lobs_inc_check_null:
                testq %rax, %rax
                jnz .Lobs_inc_next
                jmp .Lobs_inc_found
            .Lobs_inc_next:
                incq %r14
                jmp .Lobs_inc_search
            .Lobs_inc_found:
                leaq .Lkof_obs_counters(%rip), %r10
                movq %r14, %rcx
                shlq $4, %rcx
                addq %rcx, %r10
                movl 8(%r10), %eax
                addl %r12d, %eax
                movl %eax, 8(%r10)
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lobs_inc_notfound:
                cmpq $32, %r13
                jge .Lobs_inc_full
                leaq .Lkof_obs_counters(%rip), %rax
                movq %r13, %rcx
                shlq $4, %rcx
                addq %rcx, %rax
                movq %rbx, 0(%rax)
                movl %r12d, 8(%rax)
                movl $0, 12(%rax)
                incq %r13
                movq %r13, .Lkof_obs_counter_len(%rip)
                movl %r12d, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lobs_inc_full:
                movl %r12d, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_observability_gauge(rdi=name, rsi=value) -> void
            .globl kof_observability_gauge
            .type kof_observability_gauge, @function
            kof_observability_gauge:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movq .Lkof_obs_gauge_len(%rip), %r13
                xorq %r14, %r14
            .Lobs_gauge_search:
                cmpq %r13, %r14
                jge .Lobs_gauge_notfound
                leaq .Lkof_obs_gauges(%rip), %r15
                movq %r14, %rax
                shlq $4, %rax
                addq %rax, %r15
                movq 0(%r15), %rax
                testq %rbx, %rbx
                jz .Lobs_gauge_check_null
                testq %rax, %rax
                jz .Lobs_gauge_next
                movl 16(%rbx), %ecx
                movl 16(%rax), %edx
                cmpl %edx, %ecx
                jne .Lobs_gauge_next
                testl %ecx, %ecx
                jz .Lobs_gauge_found
                leaq 24(%rbx), %rdi
                leaq 24(%rax), %rsi
                movslq %ecx, %rcx
                xorq %r10, %r10
            .Lobs_gauge_cmp:
                cmpq %rcx, %r10
                jge .Lobs_gauge_found
                movzbl (%rdi,%r10), %eax
                movzbl (%rsi,%r10), %edx
                cmpl %edx, %eax
                jne .Lobs_gauge_next
                incq %r10
                jmp .Lobs_gauge_cmp
            .Lobs_gauge_check_null:
                testq %rax, %rax
                jnz .Lobs_gauge_next
                jmp .Lobs_gauge_found
            .Lobs_gauge_next:
                incq %r14
                jmp .Lobs_gauge_search
            .Lobs_gauge_found:
                leaq .Lkof_obs_gauges(%rip), %rax
                movq %r14, %rcx
                shlq $4, %rcx
                addq %rcx, %rax
                movl %r12d, 8(%rax)
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lobs_gauge_notfound:
                cmpq $32, %r13
                jge .Lobs_gauge_full
                leaq .Lkof_obs_gauges(%rip), %rax
                movq %r13, %rcx
                shlq $4, %rcx
                addq %rcx, %rax
                movq %rbx, 0(%rax)
                movl %r12d, 8(%rax)
                movl $0, 12(%rax)
                incq %r13
                movq %r13, .Lkof_obs_gauge_len(%rip)
            .Lobs_gauge_full:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_observability_histogram(rdi=name, rsi=value) -> void (OBS002)
            # store: entry 32 bytes = [name ptr, sum (long), count (long)];
            # procura por nome (igual ao counter), atualiza sum+=value, count+=1.
            .globl kof_observability_histogram
            .type kof_observability_histogram, @function
            kof_observability_histogram:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r12d
                movq .Lkof_obs_histogram_len(%rip), %r13
                xorq %r14, %r14
            .Lobs_hist_search:
                cmpq %r13, %r14
                jge .Lobs_hist_notfound
                leaq .Lkof_obs_histograms(%rip), %r15
                movq %r14, %rax
                shlq $5, %rax
                addq %rax, %r15
                movq 0(%r15), %rax
                testq %rbx, %rbx
                jz .Lobs_hist_check_null
                testq %rax, %rax
                jz .Lobs_hist_next
                movl 16(%rbx), %ecx
                movl 16(%rax), %edx
                cmpl %edx, %ecx
                jne .Lobs_hist_next
                testl %ecx, %ecx
                jz .Lobs_hist_found
                leaq 24(%rbx), %rdi
                leaq 24(%rax), %rsi
                movslq %ecx, %rcx
                xorq %r10, %r10
            .Lobs_hist_cmp:
                cmpq %rcx, %r10
                jge .Lobs_hist_found
                movzbl (%rdi,%r10), %eax
                movzbl (%rsi,%r10), %edx
                cmpl %edx, %eax
                jne .Lobs_hist_next
                incq %r10
                jmp .Lobs_hist_cmp
            .Lobs_hist_check_null:
                testq %rax, %rax
                jnz .Lobs_hist_next
                jmp .Lobs_hist_found
            .Lobs_hist_next:
                incq %r14
                jmp .Lobs_hist_search
            .Lobs_hist_found:
                leaq .Lkof_obs_histograms(%rip), %r10
                movq %r14, %rcx
                shlq $5, %rcx
                addq %rcx, %r10
                addq %r12, 8(%r10)
                incq 16(%r10)
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lobs_hist_notfound:
                cmpq $12, %r13
                jge .Lobs_hist_full
                leaq .Lkof_obs_histograms(%rip), %rax
                movq %r13, %rcx
                shlq $5, %rcx
                addq %rcx, %rax
                movq %rbx, 0(%rax)
                movq %r12, 8(%rax)
                movq $1, 16(%rax)
                incq %r13
                movq %r13, .Lkof_obs_histogram_len(%rip)
            .Lobs_hist_full:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_observability_metrics() -> String (Prometheus text exposition, OBS002)
            # Exporta counters, gauges e histograms em ordem de inserção, montando
            # o resultado por kof_string_concat (paridade de conteúdo com o JVM;
            # nomes de teste simples, sem sanitização promName/ordenação estável):
            #   counter: "# TYPE <name> counter" + NL + "<name>" + " " + "<v>" + NL
            #   gauge:   "# TYPE <name> gauge" + NL + "<name>" + " " + "<v>" + NL
            #   hist:    4 linhas: TYPE <name>_count counter / <name>_count <c> /
            #            TYPE <name>_sum gauge / <name>_sum <s>
            # Registros: r14=acc, r15=temp a liberar, r13=flag/len, r12=idx, rbx=entry.
            # Fragmento em %rsi, flag owned em %r8d (1 = liberar depois do append).
            .globl kof_observability_metrics
            .type kof_observability_metrics, @function
            kof_observability_metrics:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                leaq .Lstr_obs_empty(%rip), %rdi
                xorl %esi, %esi
                call kof_string_from_literal
                movq %rax, %r14
                xorq %r15, %r15
                xorq %r12, %r12
                jmp .Lobs_m_cnt_loop             # pula o appender (só via call)

            # appender: acc = kof_string_concat(acc, frag). Libera o temp anterior
            # (r15, se owned); se o frag é owned (r8d=1), vira o novo temp (r15).
            # Um push alinha rsp%16 (8→0) p/ os dois calls; o fragmento fica em r10
            # (scratch) porque kof_free clobbra %rsi.
            .Lobs_m_append:
                pushq %rsi
                movq %rsi, %r10
                testq %r15, %r15
                jz .Lobs_m_append_nodec
                movq %r15, %rdi
                call kof_free
            .Lobs_m_append_nodec:
                xorq %r15, %r15
                testl $1, %r8d
                jz .Lobs_m_append_do
                movq %r10, %r15
            .Lobs_m_append_do:
                movq %r14, %rdi
            """);
    }
}