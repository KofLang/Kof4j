package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de alocação/liberação (kof_alloc/kof_free/kof_init_object/kof_memstats)
 * do runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeMemory {

    private RuntimeMemory() {}

    public static void emitInitObject(StringBuilder sb) {
        sb.append("""
            .globl kof_init_object
            .type kof_init_object, @function
            kof_init_object:
                movl %esi, 0(%rdi)
                movl $0, 4(%rdi)
                movq %rdx, 8(%rdi)
                ret
            """);
    }

    public static void emitAlloc(StringBuilder sb) {
        sb.append("""
            .section .bss
            .balign 8
            kof_alloc_lock: .space 40          # pthread_mutex_t (zero-init = default)
            kof_free_head: .quad 0
            .globl kof_gc_head
            .balign 8
            kof_gc_head: .quad 0
            .balign 8
            kof_heap_low: .quad 0
            .balign 8
            kof_heap_high: .quad 0
            # §542: arena contígua do HOST (mmap única; bump). 0 nos demais
            # perfis (freestanding/BIOS/UEFI) -> o GC cai na varredura antiga.
            .balign 8
            kof_arena_base: .quad 0
            kof_arena_ptr: .quad 0
            # §542-fix: tamanho REAL da arena reservada (o host tenta 4 GiB e,
            # sob RLIMIT_AS apertado como `ulimit -v 256M`, cai para a maior
            # potência-de-dois que couber). _ prefixado: não entra em kofSymbols.
            .balign 8
            _kof_arena_size: .quad 0
            # §542: ponteiro p/ o bitmap de inícios-de-bloco do HOST. O bitmap
            # NÃO fica em .bss: o GC varre .data.._end como raízes a cada
            # passada, então um bitmap grande em .bss encareceria TODA coleta.
            # Em vez disso ele é mmap'd (fora do intervalo varrido) — pode ser
            # grande sem custo de varredura. 0 nos perfis sem arena (o GC cai na
            # varredura linear antiga e kof_bm_set é no-op).
            .balign 8
            _kof_bm_ptr: .quad 0
            .balign 8
            kof_main_tid: .quad 0              # tid do main thread p/ o GC (conservador lê a stack)
            kof_main_stack_bottom: .quad 0     # rsp do _start: topo da pilha main; o mark varre rsp..ate_isto (G-6b)
            .section .data
            .Lstr_alloc_fail: .asciz "Runtime error: out of memory"
            .section .rodata
            .Lkof_alloc_dbg: .ascii "."
            .section .text
            .globl kof_alloc
            .type kof_alloc, @function
            kof_alloc:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $24, %rsp
                movq %rdi, (%rsp)                # tamanho solicitado
                leaq kof_alloc_lock(%rip), %rsi  # &lock
                xorl %eax, %eax                  # esperado 0
            .Lkof_alloc_lock_try:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)        # 0->1 atomically?
                testl %eax, %eax
                jz .Lkof_alloc_locked
                # ocupado: futex wait
                movl $1, %edx                    # val=1
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                call kof_plat_sync
                jmp .Lkof_alloc_lock_try
            .Lkof_alloc_locked:
                movq $0, 8(%rsp)                 # flag: GC ainda nao tentou
                movq (%rsp), %r12
                addq $7, %r12
                andq $~7, %r12
                addq $32, %r12
                movq kof_free_head(%rip), %r13
                xorq %r14, %r14
                movq $1048576, %r11
            .Lkof_alloc_search:
                testq %r13, %r13
                je .Lkof_alloc_maybe_gc
                decq %r11
                je .Lkof_alloc_mmap
                movq 0(%r13), %r15
                cmpq %r12, %r15
                jb .Lkof_alloc_next
                cmpq $0, %r14
                je .Lkof_alloc_found_head
                movq 8(%r13), %r15
                movq %r15, 8(%r14)
                jmp .Lkof_alloc_found
            .Lkof_alloc_found_head:
                movq 8(%r13), %rax
                movq %rax, kof_free_head(%rip)
            .Lkof_alloc_found:
                # §541: a memória devolvida da free-list é LIXO da vida anterior
                # (o antigo mmap por alocação vinha zerado; a arena/free-list do
                # §542 não). A JVM (oráculo) zera `new Int[n]` SEMPRE — zerar
                # aqui p/ paridade (medido: `new Int[16].count[8]` = 6 no x86 e 0
                # na JVM → tabela Huffman corrompida no inflate/PNG). O bloco é
                # [header 32B][payload sizeB]; rax=0 alimenta `rep stosb`.
                movq 0(%r13), %rcx
                subq $32, %rcx
                leaq 32(%r13), %rdi
                xorl %eax, %eax
                rep stosb
                movb $0, 24(%r13)
                movq %r13, %rdi                  # §542: registra início-de-bloco
                call kof_bm_set                  #        no bitmap O(1) do GC
                movq %r13, %rax
                addq $32, %rax
                incq .Lkof_alloc_count(%rip)
                addq %r12, .Lkof_alloc_bytes(%rip)
                movq %rax, (%rsp)                # preserva retorno
                leaq kof_alloc_lock(%rip), %rdi
                movl $0, (%rdi)
                movl $1, %esi                    # FUTEX_WAKE, 1 waiter
                xorl %edx, %edx
                xorq %r10, %r10
                call kof_plat_sync
                movq (%rsp), %rax
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_alloc_next:
                movq %r13, %r14
                movq 8(%r13), %r13
                jmp .Lkof_alloc_search
            .Lkof_alloc_maybe_gc:
                # G-6(a) (native-multiarch, §260): free-list exausta -> UMA
                # passada de collect_now antes do mmap (flag 8(%rsp), ja
                # zerada no prologo). Antes o trigger era INSOND (temporario
                # vivo em caller-saved invisivel ao mark -> sweep liberava
                # bloco vivo -> SIGSEGV 139 medido em KofStringParse/
                # supervisor). Agora kof_gc_collect_now derrama os 15 GPRs
                # (blanket spill) e o cursor de busca aqui e NULL (falhou) —
                # nada vivo em registrador nosso alem dos salvos. Gate
                # kof_spawn_count==0 (contador CUMULATIVO — apos qualquer
                # spawn o auto-collect fica desligado: pilhas de worker nao
                # sao varridas; face "scan de stack de worker" catalogada,
                # nunca silenciosa). Sem gate/flag o hang antigo (status.md)
                # voltava: collect reentrante com cursor vivo.
                cmpq $0, 8(%rsp)
                jne .Lkof_alloc_mmap
                cmpq $0, kof_spawn_count(%rip)
                jne .Lkof_alloc_mmap
                movq $1, 8(%rsp)
                call kof_gc_collect_now
                movq kof_free_head(%rip), %r13
                xorq %r14, %r14
                movq $1048576, %r11
                jmp .Lkof_alloc_search
            .Lkof_alloc_maybe_gc_skip:
                jmp .Lkof_alloc_mmap
            .Lkof_alloc_mmap:
                # B-0/B-2: o crescimento do heap cruza a costura
                # kof_plat_heap_grow (host = mmap syscall; UEFI =
                # AllocatePool) — o alocador não sabe de plataforma.
                movq %r12, %rdi
                subq $8, %rsp          # alinhamento p/ a chamada (frame 24+40 ≡ 8)
                call kof_plat_heap_grow
                addq $8, %rsp
                testq %rax, %rax
                js .Lkof_alloc_fail
                movq %r12, 0(%rax)
                movq $0, 8(%rax)
                movq kof_gc_head(%rip), %rcx
                movq %rcx, 16(%rax)
                movb $0, 24(%rax)
                movq %rax, kof_gc_head(%rip)
                movq kof_heap_low(%rip), %rcx
                testq %rcx, %rcx
                je .Lheap_set_low
                cmpq %rcx, %rax
                jae .Lheap_low_ok
            .Lheap_set_low:
                movq %rax, kof_heap_low(%rip)
            .Lheap_low_ok:
                movq kof_heap_high(%rip), %rcx
                movq %rax, %rdx
                addq %r12, %rdx
                cmpq %rdx, %rcx
                jae .Lheap_high_ok
                movq %rdx, kof_heap_high(%rip)
            .Lheap_high_ok:
                movq %rax, %rdi                  # §542: registra início-de-bloco
                call kof_bm_set                  #        no bitmap O(1) do GC
                movq %rdi, %rax                  # (kof_bm_set preserva rdi)
                addq $32, %rax
                incq .Lkof_alloc_count(%rip)
                addq %r12, .Lkof_alloc_bytes(%rip)
                movq %rax, (%rsp)                # preserva retorno
                leaq kof_alloc_lock(%rip), %rdi
                movl $0, (%rdi)
                movl $1, %esi                    # FUTEX_WAKE, 1 waiter
                xorl %edx, %edx
                xorq %r10, %r10
                call kof_plat_sync
                movq (%rsp), %rax
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_alloc_fail:
                leaq kof_alloc_lock(%rip), %rdi
                movl $0, (%rdi)
                movl $1, %esi
                xorl %edx, %edx
                xorq %r10, %r10
                call kof_plat_sync
                leaq .Lstr_alloc_fail(%rip), %rdi
                call kof_panic
            """);
        // §542: kof_bm_set(header@rdi) — seta o bit de início-de-bloco no
        // bitmap global (1 bit por 16B) p/ o GC host achar um bloco em O(1).
        // PRESERVA rdi. No-op quando kof_arena_base==0 (freestanding/BIOS/UEFI
        // usam a varredura antiga). Clobbers rax/rcx/rdx/rsi/r8-r11.
        sb.append("""
            .section .text
            .globl kof_bm_set
            .type kof_bm_set, @function
            kof_bm_set:
                movq kof_arena_base(%rip), %rax
                testq %rax, %rax
                je .Lbm_done
                movq _kof_bm_ptr(%rip), %rdx
                movq %rdi, %rsi
                subq %rax, %rsi          # offset do header no arena
                shrq $4, %rsi            # indice do bit
                movq %rsi, %rax
                shrq $6, %rax            # indice da palavra
                leaq (%rdx,%rax,8), %rdx
                movl %esi, %ecx
                andl $63, %ecx           # bit na palavra
                movq $1, %rax
                shlq %cl, %rax
                orq %rax, (%rdx)
            .Lbm_done:
                ret
            """);
        // B-0/B-2: corpo da costura kof_plat_heap_grow POR PERFIL —
        // host/freestanding = mmap syscall (semântica idêntica ao que estava
        // inline); UEFI = AllocatePool via RuntimeUefi. O alocador fica
        // agnóstico de plataforma (o seam é o ponto único de plataforma).
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()) {
            RuntimeUefi.emitUefiHeapGrow(sb);
        } else if (dev.kof.compiler.nat.NativeProfile.active == dev.kof.compiler.nat.NativeProfile.FREESTANDING
                || dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            // B-1: sem SO para mmap — a arena vem do linker script.
            // B-3b-3: no BIOS (bare, sem syscalls) o heap cresce da MESMA arena
            // do linker (__kof_heap_start..end); o corpo host (syscall mmap)
            // travava no primeiro alloc (kof_array_alloc do args array do main).
            RuntimeFreestanding.emitHeapGrow(sb);
        } else {
            sb.append("""
            .section .text
            .globl kof_plat_heap_grow
            .type kof_plat_heap_grow, @function
            kof_plat_heap_grow:
                # §542: arena CONTÍGUA única (bump) p/ o GC usar bitmap O(1).
                # rdi=tamanho -> rax=ptr | -1 (mesma semântica: ptr válido ou -1).
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx              # salva o tamanho (rdi é clobberado)
                movq kof_arena_base(%rip), %rax
                testq %rax, %rax
                jne .Lgrow_have
                # primeiro alloc: escolhe a MAIOR arena que couber. O host antigo
                # mmap-por-alocação era ilimitado -> tenta 4 GiB primeiro; sob
                # RLIMIT_AS apertado (`ulimit -v 256M`, KofGcE2ETest) a mmap de
                # 4 GiB dá ENOMEM e cai para potências menores. O bitmap (fora de
                # .bss, para o GC não varrê-lo) tem 1 bit/16B -> tamanho/128.
                #   r12 = arena candidata (bytes)
                movabsq $0x100000000, %r12   # 4 GiB
            .Lgrow_try:
                # bitmap = r12/128
                movq %r12, %rsi
                shrq $7, %rsi
                movq $0, %rdi
                movq $3, %rdx                # PROT_READ|PROT_WRITE
                movq $0x4022, %r10           # MAP_PRIVATE|ANONYMOUS|NORESERVE
                movq $-1, %r8
                movq $0, %r9
                movq $9, %rax                # mmap
                syscall
                cmpq $-4095, %rax            # erro mmap = [-4095,-1]
                jae .Lgrow_smaller
                movq %rax, %r13              # bitmap ptr (temporário)
                # arena = r12
                movq $0, %rdi
                movq %r12, %rsi
                movq $3, %rdx
                movq $0x4022, %r10
                movq $-1, %r8
                movq $0, %r9
                movq $9, %rax
                syscall
                cmpq $-4095, %rax
                jb .Lgrow_arena_ok
                # arena falhou: devolve o bitmap (< cap) antes de tentar menor,
                # senão os bitmaps de cada tentativa vazam o address space.
                movq %r13, %rdi
                movq %r12, %rsi
                shrq $7, %rsi
                movq $11, %rax               # munmap
                syscall
                jmp .Lgrow_smaller
            .Lgrow_arena_ok:
                movq %rax, kof_arena_base(%rip)
                movq %rax, kof_arena_ptr(%rip)
                movq %r12, _kof_arena_size(%rip)
                movq %r13, _kof_bm_ptr(%rip)
                jmp .Lgrow_have
            .Lgrow_smaller:
                shrq $1, %r12                # 4G->2G->...->64M
                movabsq $0x4000000, %rax     # 64 MiB piso
                cmpq %rax, %r12
                jae .Lgrow_try
                jmp .Lgrow_fail
            .Lgrow_have:
                movq kof_arena_ptr(%rip), %rax
                addq $15, %rax
                andq $-16, %rax              # 16-align (bit exato no bitmap)
                movq %rax, %rcx
                addq %rbx, %rcx              # fim desta alocação
                movq kof_arena_base(%rip), %rdx
                addq _kof_arena_size(%rip), %rdx
                cmpq %rdx, %rcx
                ja .Lgrow_fail
                movq %rcx, kof_arena_ptr(%rip)
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lgrow_fail:
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
        }
    }

    public static void emitFree(StringBuilder sb) {
        sb.append("""
            .globl kof_free
            .type kof_free, @function
            kof_free:
                testq %rdi, %rdi
                jz .Lkof_free_done
                movq -32(%rdi), %rsi
                leaq -32(%rdi), %rdi
                movb $2, 24(%rdi)           # bit1: esta na free list (sweep nao re-insere)
                movq kof_free_head(%rip), %rax
                movq %rax, 8(%rdi)
                movq %rdi, kof_free_head(%rip)
                incq .Lkof_free_count(%rip)
                addq %rsi, .Lkof_free_bytes(%rip)
            .Lkof_free_done:
                ret
            """);
    }

    public static void emitMemstats(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lkof_alloc_count: .quad 0
            .Lkof_free_count: .quad 0
            .Lkof_alloc_bytes: .quad 0
            .Lkof_free_bytes: .quad 0
            .Lkof_memstats_lbl_alloc: .asciz "allocs: "
            .Lkof_memstats_lbl_free: .asciz "frees: "
            .Lkof_memstats_lbl_live: .asciz "live bytes: "
            .Lkof_memstats_nl: .asciz "\\n"
            .section .text
            .globl kof_memstats
            .type kof_memstats, @function
            kof_memstats:
                pushq %rbx
                leaq .Lkof_memstats_lbl_alloc(%rip), %rdi
                call kof_print
                movq .Lkof_alloc_count(%rip), %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                leaq .Lkof_memstats_lbl_free(%rip), %rdi
                call kof_print
                movq .Lkof_free_count(%rip), %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                leaq .Lkof_memstats_lbl_live(%rip), %rdi
                call kof_print
                movq .Lkof_alloc_bytes(%rip), %rbx
                subq .Lkof_free_bytes(%rip), %rbx
                movq %rbx, %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            """);
    }

}