package dev.kof.compiler;

/**
 * Emissão do ASM de observability3 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeObservability3 {

    private RuntimeObservability3() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

                        .section .bss
            .Lkof_obs_span_handles: .zero 1024
            .Lkof_obs_span_len: .quad 0
            .section .data
            .Lstr_obs_span_1: .asciz "{\\"traceId\\":\\""
            .Lstr_obs_span_2: .asciz "\\",\\"spanId\\":\\""
            .Lstr_obs_span_3: .asciz "\\",\\"durationMs\\":"
            .Lstr_obs_span_4: .asciz "}"
            .section .text

            # kof_observability_metrics() -> String (Prometheus text exposition)
            # Formato por métrica: "# TYPE <name> <type>", depois "<name> <value>";
            # histogramas expostos como <name>_count (counter) + <name>_sum (gauge).
            .section .data
            .Lstr_pm_type:  .asciz "# TYPE "
            .Lstr_pm_counter: .asciz " counter\\n"
            .Lstr_pm_gauge:  .asciz " gauge\\n"
            .Lstr_pm_count_type: .asciz "_count counter\\n"
            .Lstr_pm_count_val: .asciz "_count "
            .Lstr_pm_sum_type: .asciz "_sum gauge\\n"
            .Lstr_pm_sum_val:  .asciz "_sum "
            .Lstr_pm_space: .asciz " "
            .Lstr_pm_nl:    .asciz "\\n"
            .Lstr_pm_0:     .asciz ""
            .section .text

                        .section .bss
            .Lkof_sec_rl_keys: .zero 256
            .Lkof_sec_rl_counts: .zero 128
            .Lkof_sec_rl_len: .quad 0
            .Lkof_sec_sess_ids: .zero 256
            .Lkof_sec_sess_vals: .zero 256
            .Lkof_sec_sess_len: .quad 0
            .Lkof_sec_apikeys: .zero 256
            .Lkof_sec_apikey_len: .quad 0
            .section .text

                        # ── kof.enum (P1) ────────────────────────────────────────────
            # kof_enum_value_of(rdi=list KofList*, rsi=name KofString*) -> KofString*|0
            """);
    }
}