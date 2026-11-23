package dev.kof.compiler.nat;

/**
 * HTTP x86-64 — wrappers por verbo (GET/POST/PUT/PATCH/DELETE/OPTIONS c/ e
 * s/ headers), kof_http_status e configurators §259. Extraído de
 * NativeHttpCore (gate ≤500, 17/09 — fatia retry §259); a concatenação em
 * NativeHttpRuntime preserva o assembly byte-a-byte (precedente
 * REFACTOR-500 Fase 8).
 */
public final class NativeHttpVerbs {

    private NativeHttpVerbs() {}

    static String source() {
        return """
            # wrappers ----------------------------------------------------
            .globl kof_http_get
            .type kof_http_get, @function
            kof_http_get:
                leaq .Lhttp_m_get(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_get_headers
            .type kof_http_get_headers, @function
            kof_http_get_headers:
                movq %rsi, %rcx
                leaq .Lhttp_m_get(%rip), %rsi
                xorq %rdx, %rdx
                jmp kof_http_core
            .globl kof_http_delete
            .type kof_http_delete, @function
            kof_http_delete:
                leaq .Lhttp_m_delete(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_delete_headers
            .type kof_http_delete_headers, @function
            kof_http_delete_headers:
                movq %rsi, %rcx
                leaq .Lhttp_m_delete(%rip), %rsi
                xorq %rdx, %rdx
                jmp kof_http_core
            .globl kof_http_options
            .type kof_http_options, @function
            kof_http_options:
                leaq .Lhttp_m_options(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_options_headers
            .type kof_http_options_headers, @function
            kof_http_options_headers:
                movq %rsi, %rcx
                leaq .Lhttp_m_options(%rip), %rsi
                xorq %rdx, %rdx
                jmp kof_http_core
            .globl kof_http_post
            .type kof_http_post, @function
            kof_http_post:
                movq %rsi, %rdx            # body
                leaq .Lhttp_m_post(%rip), %rsi
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_post_headers
            .type kof_http_post_headers, @function
            kof_http_post_headers:
                movq %rdx, %rcx            # headers
                movq %rsi, %rdx            # body
                leaq .Lhttp_m_post(%rip), %rsi
                jmp kof_http_core
            .globl kof_http_put
            .type kof_http_put, @function
            kof_http_put:
                movq %rsi, %rdx
                leaq .Lhttp_m_put(%rip), %rsi
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_post_headers_alias_put
            .globl kof_http_put_headers
            .type kof_http_put_headers, @function
            kof_http_put_headers:
                movq %rdx, %rcx
                movq %rsi, %rdx
                leaq .Lhttp_m_put(%rip), %rsi
                jmp kof_http_core
            .globl kof_http_patch
            .type kof_http_patch, @function
            kof_http_patch:
                movq %rsi, %rdx
                leaq .Lhttp_m_patch(%rip), %rsi
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_patch_headers
            .type kof_http_patch_headers, @function
            kof_http_patch_headers:
                movq %rdx, %rcx
                movq %rsi, %rdx
                leaq .Lhttp_m_patch(%rip), %rsi
                jmp kof_http_core
            # kof_http_status(url) -> int
            .globl kof_http_status
            .type kof_http_status, @function
            kof_http_status:
                leaq .Lhttp_m_get(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                call kof_http_core
                movq .Lhttp_last_status(%rip), %rax
                ret
            # §259: timeout REAL (era ret puro). Guarda segundos; 0 = sem
            # deadline (igual JVM: Duration.ZERO = infinito). Default 15s.
            .globl kof_http_timeout_set
            .type kof_http_timeout_set, @function
            kof_http_timeout_set:
                movslq %edi, %rdi
                testq %rdi, %rdi
                jge .Lhts_store
                xorq %rdi, %rdi
            .Lhts_store:
                movq %rdi, .Lhttp_timeout_s(%rip)
                ret
            # §259 fatia 2: retry REAL (era ret puro). Guarda N (max 0, igual
            # JVM); o core consome como N+1 tentativas.
            .globl kof_http_retry_set
            .type kof_http_retry_set, @function
            kof_http_retry_set:
                movslq %edi, %rdi
                testq %rdi, %rdi
                jge .Lhrs_store
                xorq %rdi, %rdi
            .Lhrs_store:
                movq %rdi, .Lhttp_retry_n(%rip)
                ret
            # §259 fatia 3: circuit REAL (era ret puro). trips=max(0,n) igual
            # JVM; circuit(0) fecha e limpa o estado de falhas.
            .globl kof_http_circuit_set
            .type kof_http_circuit_set, @function
            kof_http_circuit_set:
                movslq %edi, %rdi
                testq %rdi, %rdi
                jge .Lhcs_store
                xorq %rdi, %rdi
            .Lhcs_store:
                movq %rdi, .Lhttp_circuit_trips(%rip)
                testq %rdi, %rdi
                jnz .Lhcs_ret
                movq $0, .Lhttp_circuit_fails(%rip)
                movq $0, .Lhttp_circuit_open_until(%rip)
            .Lhcs_ret:
                ret

            # ---- §259 fatia 3: circuit-breaker helpers (asm proprio, relógio
            # inline p/ nao depender de kof_now sujeito a runtime prune) ----
            # kof_http_now_ms() -> rax = ms monotonicos; clobbers rax,rcx,rdx,
            # rsi,rdi,r8. M-6 (#1135): o relogio passa pela COSTURA
            # (kof_plat_time_mono) — no Linux e o syscall 228 com o
            # CLOCK_MONOTONIC=1, no macOS e o clock_gettime da libSystem com o
            # CLOCK_MONOTONIC=6 (o id do Darwin, MEDIDO no SDK). O corpo cru
            # aqui teria pedido o clock 1 ao Darwin — outro relogio.
            .globl kof_http_now_ms
            .type kof_http_now_ms, @function
            kof_http_now_ms:
                subq $16, %rsp
                movq %rsp, %rdi             # ts (o contrato da costura: rdi = &timespec)
                call kof_plat_time_mono
                movq (%rsp), %rcx           # sec
                imulq $1000, %rcx           # sec*1000
                movq 8(%rsp), %rax          # nsec
                movq $1000000, %r8
                xorq %rdx, %rdx
                divq %r8                    # nsec/1e6
                addq %rcx, %rax
                addq $16, %rsp
                ret
            # kof_http_circuit_open() -> rax=1 aberto; janela expirada -> fecha
            # (half-open, permite proxima tentativa) e rax=0.
            .globl kof_http_circuit_open
            .type kof_http_circuit_open, @function
            kof_http_circuit_open:
                movq .Lhttp_circuit_open_until(%rip), %rax
                testq %rax, %rax
                jz .Lhco_false
                pushq %rax
                call kof_http_now_ms
                popq %rcx
                cmpq %rcx, %rax
                jge .Lhco_expired
                movl $1, %eax
                ret
            .Lhco_expired:
                movq $0, .Lhttp_circuit_open_until(%rip)
            .Lhco_false:
                xorl %eax, %eax
                ret
            # kof_http_circuit_record_fail(): trips<=0 -> nao faz nada; senao
            # incrementa falhas e, ao bater trips, abre por 30s (igual JVM).
            .globl kof_http_circuit_record_fail
            .type kof_http_circuit_record_fail, @function
            kof_http_circuit_record_fail:
                movq .Lhttp_circuit_trips(%rip), %rcx
                testq %rcx, %rcx
                jle .Lhrf_done
                incq .Lhttp_circuit_fails(%rip)
                movq .Lhttp_circuit_fails(%rip), %rax
                cmpq %rcx, %rax
                jl .Lhrf_done
                call kof_http_now_ms
                addq $30000, %rax
                movq %rax, .Lhttp_circuit_open_until(%rip)
            .Lhrf_done:
                ret
            # kof_http_circuit_record_success(): zera falhas e fecha.
            .globl kof_http_circuit_record_success
            .type kof_http_circuit_record_success, @function
            kof_http_circuit_record_success:
                movq $0, .Lhttp_circuit_fails(%rip)
                movq $0, .Lhttp_circuit_open_until(%rip)
                ret

            .section .data
            .Lhttp_circuit_trips: .quad 0
            .Lhttp_circuit_fails: .quad 0
            .Lhttp_circuit_open_until: .quad 0
            .section .text

            .section .data
            .Lhttp_m_get: .asciz "GET"
            .Lhttp_m_post: .asciz "POST"
            .Lhttp_m_put: .asciz "PUT"
            .Lhttp_m_patch: .asciz "PATCH"
            .Lhttp_m_delete: .asciz "DELETE"
            .Lhttp_m_options: .asciz "OPTIONS"
            .Lhttp_str_copen: .asciz "kof.http circuit open (fail fast): "
            .section .text            """;
    }
}
