package dev.kof.compiler;

import java.util.List;


final class NativeRuntime {

    private NativeRuntime() {}


    static String generateRuntimeAssembly() {
        StringBuilder sb = new StringBuilder();
        // marcador de início da área de raízes estáticas: o GC mark conservador
        // precisa varrer .data (cache/config/mq além de .bss). As emissões de
        // .data acontecem abaixo; o primeiro rótulo fica aqui (antes de tudo).
        // IMPORTANTE: voltar pra .text — senão emitPrint grava kof_print em .data
        // e o executável inteiro quebra (visto: SIGSEGV em println "a").
        sb.append("            .section .data\n");
        sb.append("            .globl kof_heap_root_start\n");
        sb.append("            kof_heap_root_start:\n");
        sb.append("            .quad 0\n");
        sb.append("            .section .text\n");
        RuntimePrint.emitPrint(sb);
        RuntimePrint.emitPrintln(sb);
RuntimePrintNum.emitPrintInt(sb);
RuntimePrintNum.emitPrintFloat(sb);
RuntimePrintNum.emitPrintDouble(sb);
        RuntimeStringConv.emitFloatToString(sb);
        RuntimeStringConv.emitDoubleToString(sb);
        RuntimeStringConv.emitIntToString(sb);
        RuntimeStringConv.emitCharToString(sb);
        RuntimeStringConv.emitLongToString(sb);
        RuntimeStringConv.emitBoolToString(sb);
        RuntimeList.emitListFunctions(sb);
        RuntimeJsonBuilder.emitJsonBuilder(sb);
        RuntimeJsonEncode.emitJsonEncode(sb);
        RuntimeJsonDecode.emitJsonDecode(sb);
        emitJsonArrayDecode(sb);
        emitJsonQuote(sb);
        emitJsonFindValue(sb);
        RuntimeMemory.emitAlloc(sb);
        RuntimeMemory.emitFree(sb);
        RuntimeGc.emitGc(sb);
        RuntimeConcurrency.emitConcurrency(sb);
        RuntimeChannel.emitChannel(sb);
        RuntimeScheduler.emitScheduler(sb);
        RuntimeMq.emitMq(sb);
        RuntimeGc.emitProcessExit(sb);
        RuntimeGc.emitPanic(sb);
        RuntimeGc.emitNullError(sb);
        RuntimeGc.emitBoundsError(sb);
        RuntimeStringBase.emitMemcpy(sb);
        RuntimeStringBase.emitStringFromLiteral(sb);
        RuntimeStringBase.emitStringLength(sb);
        RuntimeStringBase.emitStringConcat(sb);
        RuntimeStringBase.emitStringEquals(sb);
        RuntimeStringParse.emitStringToInt(sb);
        RuntimeStringParse.emitStringToLong(sb);
        RuntimeStringParse.emitStringToDouble(sb);
        RuntimeStringParse.emitStringToFloat(sb);
        RuntimeStringBase.emitPrintString(sb);
        RuntimeStringBase.emitPrintlnString(sb);
        RuntimeStringOps.emitStringCharAt(sb);
        RuntimeStringOps.emitStringSubstring(sb);
        RuntimeStringSearch.emitStringContains(sb);
        RuntimeStringSearch.emitStringStartsWith(sb);
        RuntimeStringSearch.emitStringEndsWith(sb);
        RuntimeStringSearch.emitStringIndexOf(sb);
        RuntimeStringSearch.emitStringLastIndexOf(sb);
        RuntimeStringOps.emitStringTrim(sb);
        RuntimeStringOps.emitStringCase(sb);
        RuntimeStringEdit.emitStringReplace(sb);
        RuntimeStringOps.emitStringEqualsIgnoreCase(sb);
        RuntimeStringEdit.emitStringSplit(sb);
        RuntimeArray.emitArrayAlloc(sb);
        RuntimeArray.emitArrayLength(sb);
        RuntimeArray.emitArrayGet(sb);
        RuntimeArray.emitArraySet(sb);
        RuntimeMemory.emitMemstats(sb);
        emitIoTimeFunctions(sb);
        emitKofTimeFunctions(sb);
        emitCacheFunctions(sb);
RuntimeVk.emitVkStubs(sb);
        emitLogFunctions(sb);
        emitConfigFunctions(sb);
        emitIoFileFunctions(sb);
        emitUiColorFunctions(sb);
        emitUiWindowFunctions(sb);
RuntimeNet.emitNetSocket(sb);
RuntimeNet.emitNetBind(sb);
RuntimeNet.emitNetListen(sb);
RuntimeNet.emitNetAccept(sb);
RuntimeNet.emitNetRead(sb);
RuntimeNet.emitNetWrite(sb);
RuntimeNet.emitNetClose(sb);
RuntimeMisc.emitInstanceof(sb);
        RuntimeSecurity1.emit(sb);
        RuntimeSecurity2.emit(sb);
        RuntimeSecurity3.emit(sb);
        RuntimeSecurity4.emit(sb);
        RuntimeSecurity5.emit(sb);
        RuntimeSecurity6.emit(sb);
        RuntimeSecurity7.emit(sb);
        RuntimeSecurity8.emit(sb);
        RuntimeSecurity9.emit(sb);
        RuntimeSecurity10.emit(sb);
        RuntimeSecurity11.emit(sb);
        RuntimeValidation.emit(sb);
        RuntimeObservability1.emit(sb);
        RuntimeObservability2.emit(sb);
        RuntimeObservability3.emit(sb);
        RuntimeEnum.emit(sb);
        RuntimeMap.emit(sb);
        RuntimeSet.emit(sb);
        return sb.toString();
    }


    static void generateMethodTable(StringBuilder sb, String className, List<String> methodNames) {
        sb.append(".balign 8\n");
        sb.append(".globl ").append(className).append("_vtable\n");
        sb.append(".type ").append(className).append("_vtable, @object\n");
        sb.append(className).append("_vtable:\n");
        for (String methodName : methodNames) {
            sb.append("    .quad ").append(methodName).append("\n");
        }
        sb.append("    .quad 0\n");
    }











    /**
     * CONC001: spawn/await no Native via pthread.
     * Handle (32 bytes): 0=tag(2), 4=done, 8=pthread_t, 16=result.
     * Bloco do trampolim (16 bytes): 0=task, 8=handle.
     * kof_spawn_track adiciona o handle na lista global; o fim do main
     * chama kof_spawn_join_all (join implicito, sem tarefa orfa).
     */



    /**
     * MQ001 (01/09): kof.mq no Native — pub/sub + filas in-process.
     * Estruturas (alocadas via kof_alloc, nunca liberadas — processo único):
     *   topic node (40B): [next, topic KofString*, subs KofList*, _, _]
     *   queue  node (40B): [next, name KofString*, items KofList*, _, _]
     * invoke-com-arg = padrão do kof_list_map (rdi=fn, rsi=arg).
     */




    /** process.exit(code): syscall exit — termina o processo na hora. */









    static final int KOF_STRING_TYPE_ID = 1;
    static final int KOF_STRING_HEADER_SIZE = 24;









































    /** lastIndexOf: varre do fim para o início; retorna -1 se não achar. */














    static final int KOF_ARRAY_TYPE_ID = 2;
    static final int KOF_ARRAY_HEADER_SIZE = 24;












    /**
     * kof.log no Native — mesmo contrato do JVM testado por KofLogE2ETest:
     * "yyyy-MM-dd HH:mm:ss.SSS LEVEL msg"; KOF_LOG_LEVEL filtra
     * (debug&lt;info&lt;warn&lt;error&lt;off, default info); warn/error vão
     * para stderr. Data civil via clock_gettime + conversão de dias da
     * época (algoritmo de Hinnant) em aritmética inteira pura, sem libc.
     * Delta documentado: horário é UTC (JVM usa fuso local) e KOF_LOG_JSON
     * ainda não tem efeito no Native.
     */

    /**
     * JSN003: json.decode de arrays no Native ("[1,2,3]" -> Int[] etc).
     * Int/String delegam para os decoders de lista ja testados e copiam
     * para o array (stride 4/8). Long/Bool tem parser proprio (stride 8/1).
     * Double permanece sob o gap FP (JSN001).
     */
    private static void emitJsonArrayDecode(StringBuilder sb) {
        sb.append("""
            .globl kof_json_decode_int_array
            .type kof_json_decode_int_array, @function
            kof_json_decode_int_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                call kof_json_decode_int_list
                movq %rax, %r12              # lista
                movq %r12, %rdi
                call kof_list_size
                movl %eax, %r13d             # n
                movl %r13d, %edi
                movl $4, %esi
                call kof_array_alloc         # (n, 4)
                movq %rax, %r14              # array
                xorq %r15, %r15              # i
            .Ljdia_fill:
                cmpq %r13, %r15
                jge .Ljdia_done
                movq %r12, %rdi
                movq %r15, %rsi
                call kof_list_get
                leaq (%r14,%r15,4), %rcx
                addq $24, %rcx
                movl %eax, (%rcx)
                incq %r15
                jmp .Ljdia_fill
            .Ljdia_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_string_array
            .type kof_json_decode_string_array, @function
            kof_json_decode_string_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                call kof_json_decode_string_list
                movq %rax, %r12
                movq %r12, %rdi
                call kof_list_size
                movl %eax, %r13d
                movl %r13d, %edi
                movl $8, %esi
                call kof_array_alloc         # (n, 8) ponteiros
                movq %rax, %r14
                xorq %r15, %r15
            .Ljdsa_fill:
                cmpq %r13, %r15
                jge .Ljdsa_done
                movq %r12, %rdi
                movq %r15, %rsi
                call kof_list_get
                leaq (%r14,%r15,8), %rcx
                addq $24, %rcx
                movq %rax, (%rcx)
                incq %r15
                jmp .Ljdsa_fill
            .Ljdsa_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Lcfg_parse_i64-like para JSON: rbx=json KofString*, edx=pos
            # -> rax=valor, rdx=nova pos (para apos o ultimo digito/sinal)
            .Ljad_i64_at:
                movl 16(%rbx), %ecx          # json len
            .Ljadi_ws:
                cmpl %ecx, %edx
                jge .Ljadi_bad
                movzbl 24(%rbx,%rdx), %eax
                cmpb $32, %al
                je .Ljadi_ws_inc
                cmpb $9, %al
                je .Ljadi_ws_inc
                cmpb $10, %al
                je .Ljadi_ws_inc
                cmpb $13, %al
                je .Ljadi_ws_inc
                jmp .Ljadi_sign
            .Ljadi_ws_inc:
                incq %rdx
                jmp .Ljadi_ws
            .Ljadi_sign:
                xorq %r11, %r11              # acc
                xorl %esi, %esi              # neg
                cmpl %ecx, %edx
                jge .Ljadi_bad
                movzbl 24(%rbx,%rdx), %eax
                cmpb $45, %al                # '-'
                je .Ljadi_neg
                cmpb $43, %al                # '+'
                je .Ljadi_pos
                jmp .Ljadi_dcheck
            .Ljadi_neg:
                movl $1, %esi
            .Ljadi_pos:
                incq %rdx
            .Ljadi_dcheck:
                cmpl %ecx, %edx
                jge .Ljadi_bad               # sinal sem digitos
                movzbl 24(%rbx,%rdx), %eax
                subb $48, %al
                cmpb $9, %al
                ja .Ljadi_bad
            .Ljadi_digit:
                imulq $10, %r11
                movzbl %al, %eax
                addq %rax, %r11
                incq %rdx
                cmpl %ecx, %edx
                jge .Ljadi_end
                movzbl 24(%rbx,%rdx), %eax
                subb $48, %al
                cmpb $9, %al
                ja .Ljadi_end                # nao-digito encerra o numero
                movzbl %al, %eax
                jmp .Ljadi_digit
            .Ljadi_end:
                movq %r11, %rax
                testl %esi, %esi
                jz .Ljadi_ok
                negq %rax
            .Ljadi_ok:
                ret
            .Ljadi_bad:
                xorl %edx, %edx              # pos invalida -> trata como fim
                xorl %eax, %eax
                ret

            .globl kof_json_decode_long_array
            .type kof_json_decode_long_array, @function
            kof_json_decode_long_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp               # [rsp]=len, [rsp+8]=count
                movq %rdi, %rbx              # json
                movl 16(%rbx), %r15d         # len
                xorq %r13, %r13              # i
            .Ljla_skip0:
                cmpl %r15d, %r13d
                jge .Ljla_empty
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al                # '['
                je .Ljla_open
                incq %r13
                jmp .Ljla_skip0
            .Ljla_open:
                incq %r13
                xorq %r14, %r14              # count
            .Ljla_count:
                cmpl %r15d, %r13d
                jge .Ljla_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al                # ']'
                je .Ljla_alloc
                cmpb $44, %al                # ','
                je .Ljla_cnext
                cmpb $32, %al
                je .Ljla_cws
                cmpb $10, %al
                je .Ljla_cws
                cmpb $9, %al
                je .Ljla_cws
                cmpb $45, %al                # '-' (inicio de valor)
                je .Ljla_cvskip
                cmpb $43, %al
                je .Ljla_cvskip
                cmpb $48, %al
                jb .Ljla_cws
                cmpb $57, %al
                ja .Ljla_cws
                incq %r14                    # primeiro digito -> conta elemento
            .Ljla_cvskip:
                cmpl %r15d, %r13d
                jge .Ljla_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $44, %al
                je .Ljla_cnext2
                cmpb $93, %al
                je .Ljla_alloc
                incq %r13
                jmp .Ljla_cvskip
            .Ljla_cnext2:
                incq %r13
                jmp .Ljla_count
            .Ljla_cws:
                incq %r13
                jmp .Ljla_count
            .Ljla_cnext:
                incq %r13
                jmp .Ljla_count
            .Ljla_alloc:
                movl %r14d, %edi
                movl $8, %esi
                call kof_array_alloc
                movq %rax, %r12              # array
                # pass 2: preencher — reposiciona i apos '['
                xorq %r13, %r13
            .Ljla_rescan0:
                cmpl %r15d, %r13d
                jge .Ljla_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al
                je .Ljla_rescan1
                incq %r13
                jmp .Ljla_rescan0
            .Ljla_rescan1:
                incq %r13
                xorq %r14, %r14              # idx
            .Ljla_fill:
                cmpl %r15d, %r13d
                jge .Ljla_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al                # ']'
                je .Ljla_done
                cmpb $44, %al                # ','
                je .Ljla_fnext
                cmpb $32, %al
                je .Ljla_fws
                cmpb $10, %al
                je .Ljla_fws
                cmpb $9, %al
                je .Ljla_fws
                # parse long em r13
                movq %r13, %rdx
                call .Ljad_i64_at            # rax=valor, rdx=nova pos
                # store 8 bytes em arr+24+idx*8
                movq %r14, %rcx
                shlq $3, %rcx
                addq $24, %rcx
                addq %r12, %rcx
                movq %rax, (%rcx)
                incq %r14
                movq %rdx, %r13              # nova pos
                jmp .Ljla_fill
            .Ljla_fnext:
                incq %r13
                jmp .Ljla_fill
            .Ljla_fws:
                incq %r13
                jmp .Ljla_fill
            .Ljla_done:
                movq %r12, %rax
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ljla_empty:
                xorl %edi, %edi
                movl $8, %esi
                call kof_array_alloc
                jmp .Ljla_done

            .globl kof_json_decode_bool_array
            .type kof_json_decode_bool_array, @function
            kof_json_decode_bool_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, %rbx
                movl 16(%rbx), %r15d
                xorq %r13, %r13
            .Ljba_skip0:
                cmpl %r15d, %r13d
                jge .Ljba_empty
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al
                je .Ljba_open
                incq %r13
                jmp .Ljba_skip0
            .Ljba_open:
                incq %r13
                xorq %r14, %r14              # count
            .Ljba_count:
                cmpl %r15d, %r13d
                jge .Ljba_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al
                je .Ljba_alloc
                cmpb $44, %al
                je .Ljba_cnext
                cmpb $32, %al
                je .Ljba_cws
                cmpb $10, %al
                je .Ljba_cws
                cmpb $9, %al
                je .Ljba_cws
                cmpb $116, %al               # 't' (true)
                je .Ljba_ct
                cmpb $102, %al               # 'f' (false)
                je .Ljba_cf
                jmp .Ljba_cws
            .Ljba_ct:
                incq %r14                    # true
                addq $4, %r13                # pula "true"
                jmp .Ljba_count
            .Ljba_cf:
                incq %r14                    # false
                addq $5, %r13                # pula "false"
                jmp .Ljba_count
            .Ljba_cnext:
                incq %r13
                jmp .Ljba_count
            .Ljba_cws:
                incq %r13
                jmp .Ljba_count
            .Ljba_alloc:
                movl %r14d, %edi
                movl $1, %esi
                call kof_array_alloc
                movq %rax, %r12
                xorq %r13, %r13
            .Ljba_rescan0:
                cmpl %r15d, %r13d
                jge .Ljba_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al
                je .Ljba_rescan1
                incq %r13
                jmp .Ljba_rescan0
            .Ljba_rescan1:
                incq %r13
                xorq %r14, %r14              # idx
            .Ljba_fill:
                cmpl %r15d, %r13d
                jge .Ljba_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al
                je .Ljba_done
                cmpb $44, %al
                je .Ljba_fnext
                cmpb $32, %al
                je .Ljba_fws
                cmpb $10, %al
                je .Ljba_fws
                cmpb $9, %al
                je .Ljba_fws
                cmpb $116, %al               # 't'
                je .Ljba_ft
                cmpb $102, %al               # 'f'
                je .Ljba_ff
                incq %r13
                jmp .Ljba_fill
            .Ljba_ft:
                movq %r14, %rcx
                addq $24, %rcx
                addq %r12, %rcx
                movb $1, (%rcx)
                addq $4, %r13
                incq %r14
                jmp .Ljba_fill
            .Ljba_ff:
                movq %r14, %rcx
                addq $24, %rcx
                addq %r12, %rcx
                movb $0, (%rcx)
                addq $5, %r13
                incq %r14
                jmp .Ljba_fill
            .Ljba_fnext:
                incq %r13
                jmp .Ljba_fill
            .Ljba_fws:
                incq %r13
                jmp .Ljba_fill
            .Ljba_done:
                movq %r12, %rax
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ljba_empty:
                xorl %edi, %edi
                movl $1, %esi
                call kof_array_alloc
                jmp .Ljba_done

            # helper: parser FP minimalista sobre (rbx=json, r13=pos)
            # -> xmm0 = valor, r13 = pos apos o token. Aceita [-+0-9.eE].
            .Ljad_f64_at:
                pushq %rbx
                subq $192, %rsp             # token em 0(%rsp), KofString em 128(%rsp)
                movq %rbx, %r10             # salva rbx (json) em r10
                xorq %rbx, %rbx             # len do token
            .Ljad_f64_loop:
                cmpl 16(%r10), %r13d
                jge .Ljad_f64_build
                movl 16(%r10), %eax
                cmpl %eax, %r13d
                jge .Ljad_f64_build
                movzbl 24(%r10,%r13), %eax
                cmpb $43, %al
                je .Ljad_f64_take
                cmpb $45, %al
                je .Ljad_f64_take
                cmpb $46, %al
                je .Ljad_f64_take
                cmpb $101, %al
                je .Ljad_f64_take
                cmpb $69, %al
                je .Ljad_f64_take
                cmpb $48, %al
                jb .Ljad_f64_build
                cmpb $57, %al
                ja .Ljad_f64_build
            .Ljad_f64_take:
                cmpq $127, %rbx
                jge .Ljad_f64_build
                movzbl 24(%r10,%r13), %eax
                movb %al, (%rsp,%rbx)
                incq %rbx
                incq %r13
                jmp .Ljad_f64_loop
            .Ljad_f64_build:
                testq %rbx, %rbx
                jz .Ljad_f64_zero
                movq $1, 128(%rsp)          # tag string (header nao sobrescreve o token)
                movq $0, 136(%rsp)
                movl %ebx, 144(%rsp)        # len
                xorq %rcx, %rcx
            .Ljad_f64_copy:
                cmpq %rbx, %rcx
                jge .Ljad_f64_call
                movzbl (%rsp,%rcx), %eax
                movb %al, 152(%rsp,%rcx)
                incq %rcx
                jmp .Ljad_f64_copy
            .Ljad_f64_call:
                movb $0, 152(%rsp,%rbx)
                leaq 128(%rsp), %rdi
                call kof_string_to_double
                addq $192, %rsp
                popq %rbx
                ret
            .Ljad_f64_zero:
                xorpd %xmm0, %xmm0
                addq $192, %rsp
                popq %rbx
                ret

            .globl kof_json_decode_double_array
            .type kof_json_decode_double_array, @function
            kof_json_decode_double_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, %rbx
                movl 16(%rbx), %r15d
                xorq %r13, %r13
            .Ljda_skip0:
                cmpl %r15d, %r13d
                jge .Ljda_empty
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al                # '['
                je .Ljda_open
                incq %r13
                jmp .Ljda_skip0
            .Ljda_open:
                incq %r13
                xorq %r14, %r14              # count
            .Ljda_count:
                cmpl %r15d, %r13d
                jge .Ljda_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al                # ']'
                je .Ljda_alloc
                cmpb $44, %al                # ','
                je .Ljda_cnext
                cmpb $32, %al
                je .Ljda_cws
                cmpb $10, %al
                je .Ljda_cws
                cmpb $9, %al
                je .Ljda_cws
                incq %r14                    # qualquer char de numero conta
            .Ljda_cvskip:
                cmpl %r15d, %r13d
                jge .Ljda_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $44, %al
                je .Ljda_cnext2
                cmpb $93, %al
                je .Ljda_alloc
                incq %r13
                jmp .Ljda_cvskip
            .Ljda_cnext2:
                incq %r13
                jmp .Ljda_count
            .Ljda_cws:
                incq %r13
                jmp .Ljda_count
            .Ljda_cnext:
                incq %r13
                jmp .Ljda_count
            .Ljda_alloc:
                movl %r14d, %edi
                movl $8, %esi
                call kof_array_alloc
                movq %rax, %r12
                xorq %r13, %r13
            .Ljda_rescan0:
                cmpl %r15d, %r13d
                jge .Ljda_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al
                je .Ljda_rescan1
                incq %r13
                jmp .Ljda_rescan0
            .Ljda_rescan1:
                incq %r13
                xorq %r14, %r14              # idx
            .Ljda_fill:
                cmpl %r15d, %r13d
                jge .Ljda_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al
                je .Ljda_done
                cmpb $44, %al
                je .Ljda_fnext
                cmpb $32, %al
                je .Ljda_fws
                cmpb $10, %al
                je .Ljda_fws
                cmpb $9, %al
                je .Ljda_fws
                call .Ljad_f64_at            # xmm0 = valor, r13 ja avancado
                movq %r14, %rcx
                shlq $3, %rcx
                addq $24, %rcx
                addq %r12, %rcx
                movsd %xmm0, (%rcx)
                incq %r14
                jmp .Ljda_fill
            .Ljda_fnext:
                incq %r13
                jmp .Ljda_fill
            .Ljda_fws:
                incq %r13
                jmp .Ljda_fill
            .Ljda_done:
                movq %r12, %rax
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ljda_empty:
                xorl %edi, %edi
                movl $8, %esi
                call kof_array_alloc
                jmp .Ljda_done
            """);
    }

    /**
     * JSN002 (parte 1/2): kof_json_quote(rdi=KofString*|0) -> KofString*
     * Envolve em aspas com escapes JSON (" \\ \n \r \t e sequencia u00XX p/ <32).
     * Entrada nula -> produz o texto null (sem aspas).
     */
    private static void emitJsonQuote(StringBuilder sb) {
        sb.append("""
            .globl kof_json_quote
            .type kof_json_quote, @function
            kof_json_quote:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                xorl %r15d, %r15d           # src data ptr (0 = entrada nula)
                xorl %r14d, %r14d           # srclen
                testq %rdi, %rdi
                jz .Ljq_bound
                movl 16(%rdi), %r14d        # srclen
                leaq 24(%rdi), %r15         # src data
            .Ljq_bound:
                imulq $6, %r14, %rax        # pior caso: tudo unicode-escapado
                addq $30, %rax
                movq %rax, %rdi
                call kof_alloc              # r15/r14 sobrevivem (callee-saved)
                movq %rax, %r12             # bloco destino
                movl $1, 0(%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                leaq 24(%r12), %r13         # cursor
                testq %r15, %r15
                jnz .Ljq_have_src
                movb $110, (%r13)           # null
                movb $117, 1(%r13)
                movb $108, 2(%r13)
                movb $108, 3(%r13)
                addq $4, %r13
                jmp .Ljq_close
            .Ljq_have_src:
                movb $34, (%r13)            # abre aspas
                incq %r13
                xorq %rbx, %rbx             # i
            .Ljq_loop:
                cmpq %r14, %rbx
                jge .Ljq_close_str
                movzbl (%r15,%rbx), %eax
                cmpb $34, %al               # aspa dupla
                je .Ljq_e_q
                cmpb $92, %al               # barra invertida
                je .Ljq_e_bs
                cmpb $10, %al               # LF
                je .Ljq_e_nl
                cmpb $13, %al               # CR
                je .Ljq_e_cr
                cmpb $9, %al                # TAB
                je .Ljq_e_tb
                cmpb $32, %al               # < 32 -> unicode escape
                jb .Ljq_e_uni
                movb %al, (%r13)
                incq %r13
                jmp .Ljq_next
            .Ljq_e_q:
                movw $8796, (%r13)          # backslash+aspa: LE -> 5C 22
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_bs:
                movw $23644, (%r13)         # 2x backslash: LE -> 5C 5C
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_nl:
                movw $28252, (%r13)         # backslash+n: LE -> 5C 6E
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_cr:
                movw $29276, (%r13)         # backslash+r: LE -> 5C 72
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_tb:
                movw $29788, (%r13)         # backslash+t: LE -> 5C 74
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_uni:
                movb $92, (%r13)            # backslash
                movb $117, 1(%r13)          # u
                movb $48, 2(%r13)           # 0
                movb $48, 3(%r13)           # 0
                movzbl (%r15,%rbx), %edx
                shrl $4, %edx
                andl $15, %edx
                cmpb $10, %dl
                jb .Ljq_uh1
                addb $39, %dl
                jmp .Ljq_uh2
            .Ljq_uh1:
                addb $48, %dl
            .Ljq_uh2:
                movb %dl, 4(%r13)
                movzbl (%r15,%rbx), %edx
                andl $15, %edx
                cmpb $10, %dl
                jb .Ljq_ul1
                addb $39, %dl
                jmp .Ljq_ul2
            .Ljq_ul1:
                addb $48, %dl
            .Ljq_ul2:
                movb %dl, 5(%r13)
                addq $6, %r13
                jmp .Ljq_next
            .Ljq_next:
                incq %rbx
                jmp .Ljq_loop
            .Ljq_close_str:
                movb $34, (%r13)
                incq %r13
            .Ljq_close:
                movq %r13, %rax
                subq %r12, %rax
                subq $24, %rax
                movl %eax, 16(%r12)
                movb $0, 24(%r12,%rax)
                movq %r12, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitJsonFindValue(StringBuilder sb) {
        sb.append("""
            # ---- JSN002: find_value(json kstr@rbx, key kstr@r15) -------
            # Retorna slice bruto do valor ou string vazia.
            # Registradores: rbx=json base(fixo), r13=len(fixo),
            #   r14=scan ptr, r15=key data ptr(fixo),
            #   rsi=key len, rax/rdx/rcx/rdi=r8-r11 scratch.
            .globl kof_json_find_value
            .type kof_json_find_value, @function
            kof_json_find_value:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # json
                movl 16(%rbx), %r13d     # json len
                leaq 24(%rbx), %r12      # json data
                movl 16(%rsi), %r15d     # key len
                testl %r15d, %r15d
                jz .Jfv_empty
                addq $24, %rsi           # key data
                leaq (%r12,%r13), %rbx   # json end
                movq %r12, %r14          # i (offset)
            .Jfv_scan:
                cmpq %rbx, %r14
                jae .Jfv_empty
                cmpb $34, (%r14)
                jne .Jfv_adv
                leaq 1(%r14), %rdi       # inicio da string candidata
                movq %rdi, %r8
                xorq %r9, %r9            # klen
            .Jfv_strwalk:
                cmpq %rbx, %rdi
                jae .Jfv_adv
                movzbl (%rdi), %eax
                cmpb $92, %al            # escape
                je .Jfv_esc
                cmpb $34, %al
                je .Jfv_strdone
                incq %rdi
                incq %r9
                jmp .Jfv_strwalk
            .Jfv_esc:
                addq $2, %rdi
                addq $2, %r9
                jmp .Jfv_strwalk
            .Jfv_strdone:
                cmpq %r15, %r9
                jne .Jfv_nomatch
                xorq %rcx, %rcx
            .Jfv_kcmp:
                cmpq %r15, %rcx
                jge .Jfv_match
                movzbl (%r8,%rcx), %eax
                cmpb (%rsi,%rcx), %al
                jne .Jfv_nomatch
                incq %rcx
                jmp .Jfv_kcmp
            .Jfv_nomatch:
                incq %r14
                jmp .Jfv_scan
            .Jfv_adv:
                incq %r14
                jmp .Jfv_scan
            .Jfv_match:
                incq %rdi                # apos a aspa de fechamento
                movq %rdi, %r8
            .Jfv_ws1:
                cmpq %rbx, %r8
                jae .Jfv_empty
                movzbl (%r8), %eax
                cmpb $32, %al
                je .Jfv_ws1n
                cmpb $9, %al
                je .Jfv_ws1n
                cmpb $10, %al
                je .Jfv_ws1n
                cmpb $13, %al
                jne .Jfv_colon
            .Jfv_ws1n:
                incq %r8
                jmp .Jfv_ws1
            .Jfv_colon:
                cmpb $58, (%r8)
                jne .Jfv_nomatch
                incq %r8
            .Jfv_ws2:
                cmpq %rbx, %r8
                jae .Jfv_empty
                movzbl (%r8), %eax
                cmpb $32, %al
                je .Jfv_ws2n
                cmpb $9, %al
                je .Jfv_ws2n
                cmpb $10, %al
                je .Jfv_ws2n
                cmpb $13, %al
                jne .Jfv_value
            .Jfv_ws2n:
                incq %r8
                jmp .Jfv_ws2
            .Jfv_value:
                cmpb $34, (%r8)
                je .Jfv_vstr
                movq %r8, %rdi
            .Jfv_prim:
                cmpq %rbx, %r8
                jae .Jfv_primdone
                movzbl (%r8), %eax
                cmpb $44, %al
                je .Jfv_primdone
                cmpb $125, %al
                je .Jfv_primdone
                cmpb $93, %al
                je .Jfv_primdone
                incq %r8
                jmp .Jfv_prim
            .Jfv_primdone:
                movq %rdi, %rcx
                movq %r8, %rsi
                subq %rcx, %rsi
                jmp .Jfv_mk
            .Jfv_vstr:
                incq %r8
                movq %r8, %rdi
            .Jfv_vstrwalk:
                cmpq %rbx, %r8
                jae .Jfv_empty
                movzbl (%r8), %eax
                cmpb $92, %al
                je .Jfv_vscesc
                cmpb $34, %al
                je .Jfv_vstrdone
                incq %r8
                jmp .Jfv_vstrwalk
            .Jfv_vscesc:
                addq $2, %r8
                jmp .Jfv_vstrwalk
            .Jfv_vstrdone:
                movq %r8, %rsi
                subq %rdi, %rsi
            .Jfv_mk:
                call .Ljf_mkstr
                jmp .Jfv_exit
            .Jfv_empty:
                movl $25, %edi
                call kof_alloc
                movq %rax, %rcx
                movl $1, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl $0, 16(%rcx)
                movl $0, 20(%rcx)
                movb $0, 24(%rcx)
                movq %rcx, %rax
            .Jfv_exit:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .Ljf_mkstr:
                # rdi=src, rsi=len -> rax=KofString*
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx             # src
                movq %rsi, %r12             # len (r12 preservado pelo kof_alloc)
                leaq 25(%r12), %rdi
                call kof_alloc
                movq %rax, %rcx
                movl $1, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl %r12d, 16(%rcx)
                movl $0, 20(%rcx)
                xorq %rdx, %rdx
            .Ljf_mk_cpy:
                cmpq %r12, %rdx
                jge .Ljf_mk_nul
                movzbl (%rbx,%rdx), %eax
                movb %al, 24(%rcx,%rdx)
                incq %rdx
                jmp .Ljf_mk_cpy
            .Ljf_mk_nul:
                movb $0, 24(%rcx,%r12)
                movq %rcx, %rax
                popq %r12
                popq %rbx
                ret
            .Ljf_exit:
                addq $16, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitLogFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            kof_log_threshold: .quad -1
            # KofString "KOF_LOG_LEVEL" (layout: len em +16, data em +24)
            .Llog_env_kstr:
                .quad 0
                .quad 0
                .long 13
                .long 0
                .ascii "KOF_LOG_LEVEL"
            .Llog_env_name:    .asciz "KOF_LOG_LEVEL="
            .Llog_w_debug:     .asciz "debug"
            .Llog_w_info:      .asciz "info"
            .Llog_w_warn:      .asciz "warn"
            .Llog_w_warning:   .asciz "warning"
            .Llog_w_error:     .asciz "error"
            .Llog_w_off:       .asciz "off"
            .Llog_lbl_debug:   .asciz "DEBUG"
            .Llog_lbl_info:    .asciz "INFO"
            .Llog_lbl_warn:    .asciz "WARN"
            .Llog_lbl_error:   .asciz "ERROR"
            .Llog_nullmsg:     .asciz "null"
            .section .text
            .globl kof_log_debug
            .type kof_log_debug, @function
            kof_log_debug:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $0, %rdi
                leaq .Llog_lbl_debug(%rip), %rsi
                jmp kof_log_write

            .globl kof_log_info
            .type kof_log_info, @function
            kof_log_info:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $1, %rdi
                leaq .Llog_lbl_info(%rip), %rsi
                jmp kof_log_write

            .globl kof_log_warn
            .type kof_log_warn, @function
            kof_log_warn:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $2, %rdi
                leaq .Llog_lbl_warn(%rip), %rsi
                jmp kof_log_write

            .globl kof_log_error
            .type kof_log_error, @function
            kof_log_error:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $3, %rdi
                leaq .Llog_lbl_error(%rip), %rsi
                jmp kof_log_write

            # .Llog_ci_eq(rdi=candidato, rsi=bytes, edx=len) -> eax=1 se igual (case-insensitive)
            .Llog_ci_eq:
                xorl %eax, %eax
                testl %edx, %edx
                jle .Llog_ci_no
            .Llog_ci_loop:
                movzbl (%rdi), %r8d
                movzbl (%rsi), %r9d
                orb $0x20, %r8b
                orb $0x20, %r9b
                cmpl %r9d, %r8d
                jne .Llog_ci_no
                incq %rdi
                incq %rsi
                decl %edx
                jnz .Llog_ci_loop
                movl $1, %eax
            .Llog_ci_no:
                ret

            # .Llog_parse_level -> rax = threshold (lazy, uma vez por processo).
            # Autocontido: abre /proc/self/environ e procura "KOF_LOG_LEVEL="
            # (o kof_sec_secret_get espera KofString e não serve aqui).
            .Llog_parse_level:
                pushq %rbx
                pushq %r12
                pushq %r13
                subq $16384, %rsp
                leaq .Lsec_environ_path(%rip), %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax               # SYS_open
                syscall
                testq %rax, %rax
                js .Llog_pl_default
                movq %rax, %r12             # fd
                movq %r12, %rdi
                leaq 0(%rsp), %rsi
                movq $16384, %rdx
                xorq %rax, %rax             # SYS_read
                syscall
                movq %rax, %r13             # bytes lidos
                movq %r12, %rdi
                movq $3, %rax               # close
                syscall
                cmpq $15, %r13
                jl .Llog_pl_default
                xorq %rbx, %rbx             # índice no buffer
            .Llog_scan:
                movq %rsp, %r8
                addq %rbx, %r8
                leaq .Llog_env_name(%rip), %r10
                xorq %r9, %r9
            .Llog_pcmp:
                cmpq $14, %r9
                je .Llog_pfound
                leaq (%rbx,%r9), %rdx
                cmpq %r13, %rdx
                jge .Llog_pl_default
                movzbl (%r8,%r9), %eax
                movzbl (%r10,%r9), %ecx
                cmpl %ecx, %eax
                jne .Llog_padvance
                incq %r9
                jmp .Llog_pcmp
            .Llog_padvance:
                incq %rbx
                jmp .Llog_scan
            .Llog_pfound:
                leaq 14(%r8), %rsi          # valor
                xorl %edx, %edx             # len até NUL
            .Llog_vlen:
                movq %rbx, %rax
                addq $14, %rax               # salta o prefixo "KOF_LOG_LEVEL="
                addq %rdx, %rax
                cmpq %r13, %rax
                jge .Llog_vdone
                cmpb $0, (%rsp,%rax)
                je .Llog_vdone
                incq %rdx
                jmp .Llog_vlen
            .Llog_vdone:
                call .Llog_ci_word
                # dispatch pelo comprimento (debug5 info4 warn4 warning7 error5 off3)
                movl %edx, %ebx
                cmpq $7, %rdx
                jne .Llog_pl_5
                leaq .Llog_w_warning(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_warn
                jmp .Llog_pl_default
            .Llog_pl_5:
                cmpq $5, %rdx
                jne .Llog_pl_4
                movl %ebx, %edx
                leaq .Llog_w_debug(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_debug
                movl %ebx, %edx
                leaq .Llog_w_error(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_error
                jmp .Llog_pl_default
            .Llog_pl_4:
                cmpq $4, %rdx
                jne .Llog_pl_3
                movl %ebx, %edx
                leaq .Llog_w_info(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_default
                movl %ebx, %edx
                leaq .Llog_w_warn(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_warn
                jmp .Llog_pl_default
            .Llog_pl_3:
                cmpq $3, %rdx
                jne .Llog_pl_default
                movl %ebx, %edx
                leaq .Llog_w_off(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_off
                jmp .Llog_pl_default
            .Llog_pl_debug:
                movq $0, %rax
                jmp .Llog_pl_exit
            .Llog_pl_warn:
                movq $2, %rax
                jmp .Llog_pl_exit
            .Llog_pl_error:
                movq $3, %rax
                jmp .Llog_pl_exit
            .Llog_pl_off:
                movq $4, %rax
                jmp .Llog_pl_exit
            .Llog_pl_default:
                movq $1, %rax
            .Llog_pl_exit:
                addq $16384, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Llog_ci_word(rsi=valor, edx=len) -> eax=1 se é uma das palavras válidas
            .Llog_ci_word:
                cmpq $7, %rdx
                je .Llog_ciw_yes
                cmpq $5, %rdx
                je .Llog_ciw_yes
                cmpq $4, %rdx
                je .Llog_ciw_yes
                cmpq $3, %rdx
                je .Llog_ciw_yes
                xorl %eax, %eax
                ret
            .Llog_ciw_yes:
                movl $1, %eax
                ret

            # .Llog_ci_eq2(rdi=candidato lowercase, rsi=bytes, edx=len) -> eax=1 se igual
            .Llog_ci_eq2:
                pushq %rbx
                movl %edx, %ebx
                xorl %eax, %eax
                testl %ebx, %ebx
                jle .Llog_ci2_no
            .Llog_ci2_loop:
                movzbl (%rdi), %r8d
                movzbl (%rsi), %r9d
                orb $0x20, %r8b
                orb $0x20, %r9b
                cmpl %r9d, %r8d
                jne .Llog_ci2_no
                incq %rdi
                incq %rsi
                decl %ebx
                jnz .Llog_ci2_loop
                movl $1, %eax
            .Llog_ci2_no:
                popq %rbx
                ret

            # kof_log_write(rdi=level, rsi=label cstr, rdx=msg KofString|0)
            # slots locais: 0..15 timespec | 16 hh | 20 mi | 24 ss | 28 ms
            #               32 year | 36 mon | 40 day | 44 tempA | 48 tempB
            #               52 doe  | 56 era | 60 epochsec
            kof_log_write:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movq %rdi, %r12
                movq %rsi, %r13
                movq %rdx, %r14
                movq $-1, 44(%rsp)
                # threshold lazy
                movq kof_log_threshold(%rip), %rax
                cmpq $-1, %rax
                jne .Llog_have_thresh
                call .Llog_parse_level
                movq %rax, kof_log_threshold(%rip)
                movq kof_log_threshold(%rip), %rax
            .Llog_have_thresh:
                cmpq %rax, %r12
                jl .Llog_suppressed
                # clock_gettime(CLOCK_REALTIME)
                leaq 0(%rsp), %rdi
                xorq %rsi, %rsi
                movq $96, %rax
                syscall
                movq 8(%rsp), %rax           # nsec
                xorq %rdx, %rdx
                movq $1000000, %rcx
                divq %rcx
                movl %eax, 28(%rsp)          # ms
                movq 0(%rsp), %rax           # epoch sec (timespec já é em segundos)
                movq %rax, 60(%rsp)
                # hora do dia
                movq 60(%rsp), %rax
                xorq %rdx, %rdx
                movq $86400, %rcx
                divq %rcx                    # rax=dias, rdx=secs do dia
                movq %rdx, %rax
                xorq %rdx, %rdx
                movq $3600, %rcx
                divq %rcx
                movl %eax, 16(%rsp)          # hh
                movq %rdx, %rax
                xorq %rdx, %rdx
                movq $60, %rcx
                divq %rcx
                movl %eax, 20(%rsp)          # mi
                movl %edx, 24(%rsp)          # ss
                # data civil (Hinnant) — só registradores, sem slots:
                # r8=era, r9=doe, r10/r11/rdi temporários
                movq 60(%rsp), %rax
                xorq %rdx, %rdx
                movq $86400, %rcx
                divq %rcx                     # rax = dias
                addq $719468, %rax            # z
                movq $146097, %rcx
                xorq %rdx, %rdx
                divq %rcx                     # rax=era, rdx=doe
                movq %rax, %r8
                movq %rdx, %r9
                # N = doe - doe/1460 + doe/36524 - doe/146096
                movq %r9, %rax
                xorq %rdx, %rdx
                movq $1460, %rcx
                divq %rcx
                movq %rax, %r10               # doe/1460
                movq %r9, %rax
                xorq %rdx, %rdx
                movq $36524, %rcx
                divq %rcx
                movq %rax, %r11               # doe/36524
                movq %r9, %rax
                xorq %rdx, %rdx
                movq $146096, %rcx
                divq %rcx                     # rax = doe/146096
                movq %r9, %rdi
                subq %r10, %rdi
                addq %r11, %rdi
                subq %rax, %rdi               # N
                movq $365, %rcx
                xorq %rdx, %rdx
                movq %rdi, %rax
                divq %rcx                     # rax = yoe
                movq %rax, %r11               # r11 = yoe
                # ano = yoe + era*400
                movq %r11, %rax
                imulq $400, %r8
                addq %r8, %rax
                movq %rax, %r10               # r10 = year (provisório)
                # doy = doe(r9) - (365*yoe + yoe/4 - yoe/100)
                movq %r11, %rax
                imulq $365, %rax
                movq %rax, %rdi               # rdi = 365*yoe
                movq %r11, %rax
                shrq $2, %rax
                addq %rax, %rdi               # + yoe/4
                movq %r11, %rax
                xorq %rdx, %rdx
                movq $100, %rcx
                divq %rcx
                subq %rax, %rdi               # - yoe/100
                movq %r9, %rax
                subq %rdi, %rax               # doy
                movq %rax, %r9                # r9 = doy
                # mp = (5*doy + 2)/153
                imulq $5, %rax
                addq $2, %rax
                xorq %rdx, %rdx
                movq $153, %rcx
                divq %rcx                     # rax = mp
                movq %rax, %r11               # r11 = mp (yoe livre agora)
                # day = doy - (153*mp+2)/5 + 1
                imulq $153, %rax
                addq $2, %rax
                xorq %rdx, %rdx
                movq $5, %rcx
                divq %rcx                     # rax = correção
                movq %r9, %rdi                # doy
                subq %rax, %rdi
                incq %rdi                     # day
                # month = mp + 3 - 12*(mp/10)
                movq %r11, %rax
                xorq %rdx, %rdx
                movq $10, %rcx
                divq %rcx
                imulq $12, %rax
                movq %rax, %r8                # r8 = 12*(mp/10) (era livre)
                movq %r11, %rax
                addq $3, %rax
                subq %r8, %rax                # month
                movl %r10d, 32(%rsp)          # year
                movl %eax, 36(%rsp)           # month
                movl %edi, 40(%rsp)           # day
                cmpl $2, 36(%rsp)
                jg .Llog_year_ok
                incl 32(%rsp)
            .Llog_year_ok:
                # buffer = msglen + 80
                movq $4, %rdi
                testq %r14, %r14
                jz .Llog_alloc
                movl 16(%r14), %edi
                addq $4, %rdi
            .Llog_alloc:
                addq $80, %rdi
                call kof_alloc
                movq %rax, %r15
                movq %rax, %rbx
                # ano (4 dígitos)
                movl 32(%rsp), %eax
                xorl %edx, %edx
                movl $1000, %r8d
                divl %r8d
                addb $48, %al
                movb %al, (%rbx)
                # resto em edx: centena
                movl %edx, %eax
                xorl %edx, %edx
                movl $100, %r8d
                divl %r8d
                addb $48, %al
                movb %al, 1(%rbx)
                movl %edx, %eax
                xorl %edx, %edx
                movl $10, %r8d
                divl %r8d
                addb $48, %al
                movb %al, 2(%rbx)
                addb $48, %dl
                movb %dl, 3(%rbx)
                addq $4, %rbx
                movb $45, (%rbx)
                incq %rbx
                movl 36(%rsp), %eax
                call .Llog_put2_at_bx
                movb $45, (%rbx)
                incq %rbx
                movl 40(%rsp), %eax
                call .Llog_put2_at_bx
                movb $32, (%rbx)
                incq %rbx
                movl 16(%rsp), %eax
                call .Llog_put2_at_bx
                movb $58, (%rbx)
                incq %rbx
                movl 20(%rsp), %eax
                call .Llog_put2_at_bx
                movb $58, (%rbx)
                incq %rbx
                movl 24(%rsp), %eax
                call .Llog_put2_at_bx
                movb $46, (%rbx)
                incq %rbx
                movl 28(%rsp), %eax
                call .Llog_put3_at_bx
                movb $32, (%rbx)
                incq %rbx
                # label
                movq %r13, %rax
            .Llog_copy_label:
                movzbl (%rax), %ecx
                testl %ecx, %ecx
                jz .Llog_label_done
                movb %cl, (%rbx)
                incq %rax
                incq %rbx
                jmp .Llog_copy_label
            .Llog_label_done:
                movb $32, (%rbx)
                incq %rbx
                # mensagem
                testq %r14, %r14
                jnz .Llog_copy_msg
                leaq .Llog_nullmsg(%rip), %rax
            .Llog_copy_loop:
                movzbl (%rax), %ecx
                testl %ecx, %ecx
                jz .Llog_msg_done
                movb %cl, (%rbx)
                incq %rax
                incq %rbx
                jmp .Llog_copy_loop
            .Llog_copy_msg:
                movl 16(%r14), %ecx
                testl %ecx, %ecx
                jle .Llog_msg_done
                leaq 24(%r14), %rax
                movl %ecx, %edx
            .Llog_copy_bytes:
                movzbl (%rax), %ecx
                movb %cl, (%rbx)
                incq %rax
                incq %rbx
                decl %edx
                jnz .Llog_copy_bytes
            .Llog_msg_done:
                movb $10, (%rbx)
                incq %rbx
                # write(fd, buf, len)
                movq %rbx, %rdx
                subq %r15, %rdx
                movq %r15, %rsi
                movq $1, %rax
                cmpq $2, %r12
                jl .Llog_fd_stdout
                movq $2, %rdi
                jmp .Llog_do_write
            .Llog_fd_stdout:
                movq $1, %rdi
            .Llog_do_write:
                syscall
                movq %r15, %rdi
                call kof_free
            .Llog_suppressed:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # helpers que usam %rbx como cursor (dentro de kof_log_write)
            .Llog_put2_at_bx:
                pushq %rax
                movl %eax, %ecx
                xorl %edx, %edx
                movl $10, %r8d
                divl %r8d
                addb $48, %al
                movb %al, (%rbx)
                addb $48, %dl
                movb %dl, 1(%rbx)
                addq $2, %rbx
                popq %rax
                ret
            .Llog_put3_at_bx:
                pushq %rax
                movl %eax, %ecx
                xorl %edx, %edx
                movl $100, %r8d
                divl %r8d
                addb $48, %al
                movb %al, (%rbx)
                incq %rbx                    # centena gravada; dezena/unidade via put2
                movl %edx, %eax
                call .Llog_put2_at_bx
                popq %rax
                ret
            """);
    }


    /**
     * kof.config no Native — mesma semântica do JVM (KofConfigE2ETest):
     * precedência KOF_CONFIG > env KOF_&lt;KEY&gt; (pontos/traços viram
     * underscore, maiúsculas) > kof.&lt;profile&gt;.config / kof.config no
     * diretório de trabalho. Arquivo: linhas "chave = valor", "#" comenta,
     * bordas aparadas. Conversores tipados com default em valor inválido.
     * Tudo em asm puro sobre syscalls, sem libc.
     */
    private static void emitConfigFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lcfg_s_kofconfig:  .asciz "KOF_CONFIG"
            .Lcfg_s_kofprofile: .asciz "KOF_PROFILE"
            .Lcfg_s_default:    .asciz "kof.config"
            .Lcfg_w_true:  .asciz "true"
            .Lcfg_w_yes:   .asciz "yes"
            .Lcfg_w_one:   .asciz "1"
            .Lcfg_w_false: .asciz "false"
            .Lcfg_w_no:    .asciz "no"
            .Lcfg_w_zero:  .asciz "0"
            .section .text

            # kof_env_getc(rdi=nome C-string) -> rax = KofString*|0
            # busca linear simples: acha "NAME=" como substring do environ;
            # valor = ate o NUL da entrada. Estado: r10=nlen, r13=len,
            # r14=i (posicao), r9=j (casamento), r8=ptr corrente.
            kof_env_getc:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16384, %rsp
                movq %rdi, %rbx              # nome
                xorq %r10, %r10              # strlen(nome)
            .Lceg_nlen:
                cmpb $0, (%rbx,%r10)
                je .Lceg_nlen_done
                incq %r10
                jmp .Lceg_nlen
            .Lceg_nlen_done:
                leaq .Lsec_environ_path(%rip), %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax                # SYS_open
                syscall
                testq %rax, %rax
                js .Lceg_fail
                movq %rax, %r12              # fd
                movq %r12, %rdi
                leaq 0(%rsp), %rsi
                movq $16384, %rdx
                xorq %rax, %rax              # SYS_read
                syscall
                movq %rax, %r13              # bytes lidos
                movq %r12, %rdi
                movq $3, %rax                # close
                testq %r10, %r10             # nome vazio -> falha
                jle .Lceg_fail
                cmpq %r10, %r13
                jle .Lceg_fail               # buffer menor que o nome
                xorq %r14, %r14              # i = 0
            .Lceg_scan:
                cmpq %r13, %r14
                jge .Lceg_fail
                movq %rsp, %r8
                addq %r14, %r8               # r8 = buf + i
                xorq %r9, %r9                # j = 0
            .Lceg_pcmp:
                cmpq %r10, %r9
                je .Lceg_pmatched            # casou o nome inteiro
                movq %r14, %rax
                addq %r9, %rax
                cmpq %r13, %rax
                jge .Lceg_fail
                movzbl (%rbx,%r9), %eax      # name[j]
                movzbl (%r8,%r9), %ecx       # buf[i+j]
                cmpl %ecx, %eax
                jne .Lceg_advance
                incq %r9
                jmp .Lceg_pcmp
            .Lceg_pmatched:
                # exige '=' imediatamente apos o nome
                cmpb $61, (%r8,%r10)
                jne .Lceg_advance
                leaq 1(%r8,%r10), %rdi       # valor = buf + i + nlen + 1
                movq %r14, %rsi
                addq %r10, %rsi
                incq %rsi                    # inicio do valor (offset)
                movq %r13, %rdx
                subq %rsi, %rdx              # limite restante
                movq %rdx, %r15              # r15 = len maximo
                xorq %rcx, %rcx              # comprimento do valor ate NUL
            .Lceg_vscan:
                cmpq %r15, %rcx
                jge .Lceg_vdone
                cmpb $0, (%rdi,%rcx)
                je .Lceg_vdone
                incq %rcx
                jmp .Lceg_vscan
            .Lceg_vdone:
                movq %rcx, %rsi              # vallen
                call kof_string_from_literal
                jmp .Lceg_exit
            .Lceg_advance:
                # avanca ate passar do proximo NUL (fim da entrada)
                cmpq %r13, %r14
                jge .Lceg_fail
                cmpb $0, (%r8)
                je .Lceg_adv_null
                incq %r8
                incq %r14
                jmp .Lceg_advance
            .Lceg_adv_null:
                incq %r14                    # pula o proprio NUL
                jmp .Lceg_scan
            .Lceg_fail:
                xorl %eax, %eax
            .Lceg_exit:
                addq $16384, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Lcfg_file_find(rdi=path C-string, rsi=key KofString*) -> KofString*|0
            # Ponteiros ABSOLUTOS apenas: rbx=buffer base, r14=fim dos dados,
            # r13=key data, r15=key len, r12=cursor de linha, r10/r8/r11=temp.
            .Lcfg_file_find:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16384, %rsp
                movq %rsi, %r12              # key
                movq %rdi, %rbx              # path cstr
                movq %rbx, %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax                # SYS_open (ausente -> null)
                syscall
                testq %rax, %rax
                js .Lcff_fail
                movq %rax, %rcx              # fd
                movq %rcx, %rdi
                movq %rsp, %rsi
                movq $16384, %rdx
                xorq %rax, %rax              # SYS_read
                syscall
                movq %rsp, %rbx              # rbx = buffer base (fixo)
                movq %rax, %r14              # r14 = bytes lidos
                movq %rcx, %rdi
                movq $3, %rax                # close
                syscall
                testq %r14, %r14
                jle .Lcff_fail
                leaq 24(%r12), %r13          # r13 = endereco dos dados da chave
                movl 16(%r12), %r15d         # r15 = key len
                leaq (%rbx,%r14), %r9        # r9 = fim dos dados
                movq %rbx, %r12              # r12 = inicio da linha corrente
            .Lcff_line:
                cmpq %r9, %r12
                jae .Lcff_fail
                movq %r12, %r10              # acha o fim da linha (LF ou fim)
            .Lcff_findeol:
                cmpq %r9, %r10
                jae .Lcff_haveeol
                cmpb $10, (%r10)
                je .Lcff_haveeol
                incq %r10
                jmp .Lcff_findeol
            .Lcff_haveeol:
                movq %r12, %r11              # trim esquerdo -> r11
            .Lcff_tls:
                cmpq %r10, %r11
                jae .Lcff_blank
                movzbl (%r11), %eax
                cmpb $32, %al
                je .Lcff_tls1
                cmpb $9, %al
                jne .Lcff_tle
            .Lcff_tls1:
                incq %r11
                jmp .Lcff_tls
            .Lcff_tle:
                movq %r10, %r8               # trim direito -> r8 (exclusivo)
            .Lcff_tle_loop:
                cmpq %r11, %r8
                jbe .Lcff_blank
                movzbl -1(%r8), %eax
                cmpb $32, %al
                je .Lcff_tle1
                cmpb $9, %al
                je .Lcff_tle1
                cmpb $13, %al
                je .Lcff_tle1
                jmp .Lcff_hash
            .Lcff_tle1:
                decq %r8
                jmp .Lcff_tle_loop
            .Lcff_blank:
                leaq 1(%r10), %r12
                jmp .Lcff_line
            .Lcff_hash:
                cmpb $35, (%r11)             # '#'
                je .Lcff_blank
                movq %r11, %rcx              # procura '=' em [r11,r8)
            .Lcff_eqscan:
                cmpq %r8, %rcx
                jae .Lcff_blank
                cmpb $61, (%rcx)
                je .Lcff_eqfound
                incq %rcx
                jmp .Lcff_eqscan
            .Lcff_eqfound:
                movq %rcx, %r12              # salva o offset do '=' 
            .Lcff_keytrim:
                movq %rcx, %rdi              # chave direita-aparada: [r11, rdi)
            .Lcff_keyt:
                cmpq %r11, %rdi
                jbe .Lcff_blank
                movzbl -1(%rdi), %eax
                cmpb $32, %al
                je .Lcff_keyt1
                cmpb $9, %al
                jne .Lcff_keycmp
            .Lcff_keyt1:
                decq %rdi
                jmp .Lcff_keyt
            .Lcff_keycmp:
                movq %rdi, %rdx              # comprimento da chave na linha
                subq %r11, %rdx
                cmpq %r15, %rdx
                jne .Lcff_valskip
                xorq %rcx, %rcx              # indice i
            .Lcff_cmpline:
                cmpq %r15, %rcx
                jae .Lcff_matched
                movzbl (%r11,%rcx), %eax     # linha[i]
                movzbl (%r13,%rcx), %edx     # key[i]
                cmpb %dl, %al
                jne .Lcff_valskip
                incq %rcx
                jmp .Lcff_cmpline
            .Lcff_matched:
                leaq 1(%r12), %rsi           # vs = eq + 1 (r12 guarda o offset do '=')
            .Lcff_vtls:
                cmpq %r8, %rsi
                jae .Lcff_vmk
                movzbl (%rsi), %eax
                cmpb $32, %al
                je .Lcff_vtls1
                cmpb $9, %al
                jne .Lcff_vmk
            .Lcff_vtls1:
                incq %rsi
                jmp .Lcff_vtls
            .Lcff_vmk:
                movq %r8, %rax               # vallen = fim efetivo - inicio do valor
                subq %rsi, %rax
                movq %rsi, %rdi              # rdi = endereco do valor
                movl %eax, %esi              # ESI = vallen (contrato do from_literal!)
                call kof_string_from_literal
                jmp .Lcff_exit
            .Lcff_valskip:
                leaq 1(%r10), %r12
                jmp .Lcff_line
            .Lcff_fail:
                xorl %eax, %eax
            .Lcff_exit:
                addq $16384, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            # .Lcfg_envname(rdi=key KofString*, rsi=dest) -> escreve "KOF_<KEY>" C-string
            .Lcfg_envname:
                movl 16(%rdi), %ecx          # keylen
                leaq 24(%rdi), %rdx          # data
                movb $75, 0(%rsi)            # 'K'
                movb $79, 1(%rsi)            # 'O'
                movb $70, 2(%rsi)            # 'F'
                movb $95, 3(%rsi)            # '_'
                movq $4, %rax
                xorq %r9, %r9
            .Lce_loop:
                cmpq %rcx, %r9
                jge .Lce_done
                movzbl (%rdx,%r9), %edi
                cmpb $46, %dil               # '.'
                je .Lce_us
                cmpb $45, %dil               # '-'
                je .Lce_us
                cmpb $97, %dil               # 'a'
                jb .Lce_store
                cmpb $122, %dil              # 'z'
                ja .Lce_store
                subb $32, %dil               # maiuscula
                jmp .Lce_store
            .Lce_us:
                movb $95, %dil               # '_'
            .Lce_store:
                movb %dil, (%rsi,%rax)
                incq %rax
                incq %r9
                jmp .Lce_loop
            .Lce_done:
                movb $0, (%rsi,%rax)
                ret

            # kof_config_lookup(rdi=key KofString*) -> KofString*|0
            # público: raw + interpolação ${key} (P2)
            kof_config_lookup:
                call kof_config_lookup_raw
                testq %rax, %rax
                jz .Lkcl_null
                movq %rax, %rdi
                call kof_config_interpolate
            .Lkcl_null:
                ret

            kof_config_lookup_raw:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $512, %rsp
                movq %rdi, %rbx              # key
                # 1) arquivo explicito via KOF_CONFIG
                leaq .Lcfg_s_kofconfig(%rip), %rdi
                call kof_env_getc
                testq %rax, %rax
                jz .Lcl_envkey
                leaq 24(%rax), %rdi          # path cstr (data e NUL-terminada)
                movq %rbx, %rsi
                call .Lcfg_file_find
                testq %rax, %rax
                jnz .Lcl_exit
            .Lcl_envkey:
                # 2) env KOF_<CHAVE>
                movq %rbx, %rdi
                movq %rsp, %rsi
                call .Lcfg_envname
                movq %rsp, %rdi
                call kof_env_getc
                testq %rax, %rax
                jnz .Lcl_exit
                # 3) kof.<profile>.config ou kof.config
                leaq .Lcfg_s_kofprofile(%rip), %rdi
                call kof_env_getc
                testq %rax, %rax
                jz .Lcl_defaultfile
                # monta "kof.<profile>.config" no buffer do frame
                movq %rax, %r12              # profile KofString (antes de clobber rax)
                movq %rsp, %r8
                movl $778465131, %eax        # "kof." little-endian (6B 6F 66 2E)
                movl %eax, 0(%r8)
                movq $4, %rax
                movl 16(%r12), %ecx          # profile len
                leaq 24(%r12), %rdx          # profile data
                xorq %r9, %r9
            .Lcl_pcopy:
                cmpq %rcx, %r9
                jge .Lcl_pdone
                movzbl (%rdx,%r9), %edi
                movb %dil, (%r8,%rax)
                incq %rax
                incq %r9
                jmp .Lcl_pcopy
            .Lcl_pdone:
                movb $46, 0(%r8,%rax)        # ".config"
                movb $99, 1(%r8,%rax)
                movb $111, 2(%r8,%rax)
                movb $110, 3(%r8,%rax)
                movb $102, 4(%r8,%rax)
                movb $105, 5(%r8,%rax)
                movb $103, 6(%r8,%rax)
                addq $7, %rax
                movb $0, (%r8,%rax)
                leaq 0(%rsp), %rdi
                jmp .Lcl_ffcall
            .Lcl_defaultfile:
                leaq .Lcfg_s_default(%rip), %rdi
            .Lcl_ffcall:
                movq %rbx, %rsi
                call .Lcfg_file_find
            .Lcl_exit:
                addq $512, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ---- wrappers publicos ----

            kof_config_get:
                jmp kof_config_lookup

            # P2 (docs/stdlib-config.md §8.2): interpolação ${key}.
            # rdi = valor KofString* -> resolve referências a outras chaves.
            # Ref inexistente ou "${" sem "}" -> valor literal inalterado.
            # Profundidade máxima 16 (quebra ciclos).
            # callee-saved: r12=valor corrente, r13=depth, rbx/r14/r15=temps
            kof_config_interpolate:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp               # spills: [0]=start [8]=end [16]=sufixo
                                             # push x5 (40) + 32 = 72 -> rsp%16==8
                                             # na entrada de calls: OK (padrao SysV)
                testq %rdi, %rdi
                jz .Lkci_ret
                movq %rdi, %r12
                xorl %r13d, %r13d            # depth = 0
            .Lkci_loop:
                cmpl $16, %r13d
                jge .Lkci_done
                # achou "${" no valor corrente?
                leaq .Lkci_dollar(%rip), %rdi
                movl $2, %esi
                call kof_string_from_literal
                movq %rax, %r14              # r14 = "${"
                movq %r12, %rdi
                movq %r14, %rsi
                call kof_string_index_of
                cmpl $-1, %eax
                je .Lkci_done                # sem "${": pronto
                movl %eax, 0(%rsp)           # spill: start
                # tail = value[start+2 .. len]
                movq %r12, %rdi
                movl 0(%rsp), %esi
                addl $2, %esi
                movl 16(%r12), %edx
                call kof_string_substring
                testq %rax, %rax
                jz .Lkci_done
                movq %rax, %rbx              # rbx = tail
                # rel = index_of(tail, "}")
                leaq .Lkci_close(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %r14              # r14 = "}"
                movq %rbx, %rdi
                movq %r14, %rsi
                call kof_string_index_of
                cmpl $-1, %eax
                je .Lkci_done                # sem "}" -> literal
                leal 2(%rax), %r9d           # r9 = end (relativo ao start+2... ver: 2+rel == start+2+rel - start) 
                addl 0(%rsp), %r9d           # r9 = end absoluto
                movl %r9d, 4(%rsp)           # spill end
                # ref = value[start+2 .. end]
                movq %r12, %rdi
                movl 0(%rsp), %esi
                addl $2, %esi
                movl %r9d, %edx
                call kof_string_substring
                movq %rax, %r14              # r14 = ref KofString
                movq %r14, %rdi
                call kof_config_lookup       # resolve referência
                testq %rax, %rax
                jz .Lkci_done                # ref inexistente -> literal
                movq %rax, %r15              # r15 = resolved
                # prefixo = value[0..start]
                movq %r12, %rdi
                xorl %esi, %esi
                movl 0(%rsp), %edx
                call kof_string_substring
                movq %rax, %rbx              # rbx = prefixo
                # sufixo = value[end+1 .. len]
                movq %r12, %rdi
                movl 4(%rsp), %esi
                addl $1, %esi
                movl 16(%r12), %edx
                call kof_string_substring
                movq %rax, 8(%rsp)           # spill sufixo
                # tmp = resolved + sufixo
                movq %r15, %rdi
                movq 8(%rsp), %rsi
                call kof_string_concat
                movq %rax, %r15              # r15 = (resolved+sufixo)
                # result = prefixo + tmp
                movq %rbx, %rdi
                movq %r15, %rsi
                call kof_string_concat
                movq %rax, %r12              # novo valor corrente
                incl %r13d
                jmp .Lkci_loop
            .Lkci_done:
                movq %r12, %rax
            .Lkci_ret:
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkci_dollar: .asciz "${"
            .Lkci_close:  .asciz "}"

            # kof_config_required(rdi=key) -> KofString*|panic CONF002

            # kof_config_required(rdi=key) -> KofString*|panic CONF002
            kof_config_required:
                pushq %rbx
                subq $8, %rsp
                call kof_config_lookup
                addq $8, %rsp
                testq %rax, %rax
                jz .Lcfg_req_missing
                popq %rbx
                ret
            .Lcfg_req_missing:          # caminho sem chave: mensagem no rodata
                leaq .Lcfg_req_msg(%rip), %rdi
                call kof_panic
            .Lcfg_req_missing_key:
                movq %r12, %rsi
                movq %rbx, %rdi
                call kof_panic
            .Lcfg_req_msg: .asciz "Kof config: missing required key (CONF001)"

            kof_config_env:
                leaq 24(%rdi), %rdi          # nome KofString -> C-string (data NUL-terminada)
                jmp kof_env_getc

            kof_config_has:
                call kof_config_lookup
                testq %rax, %rax
                setne %al
                movzbq %al, %rax
                ret

            kof_config_str:
                pushq %rbx
                movq %rsi, %rbx              # default (lookup preserva rbx)
                call kof_config_lookup
                testq %rax, %rax
                cmovzq %rbx, %rax
                popq %rbx
                ret

            kof_config_int:
                pushq %rbx
                movl %esi, %ebx              # default
                call kof_config_lookup
                testq %rax, %rax
                jz .Lci_def
                movq %rax, %rdi
                call .Lcfg_parse_i64
                testl %edx, %edx
                jz .Lci_def
                # range int32
                cmpq $2147483647, %rax
                jg .Lci_def
                cmpq $-2147483648, %rax
                jl .Lci_def
                popq %rbx
                ret
            .Lci_def:
                movl %ebx, %eax
                popq %rbx
                ret

            kof_config_long:
                pushq %rbx
                movq %rsi, %rbx              # default long
                call kof_config_lookup
                testq %rax, %rax
                jz .Lcl_def
                movq %rax, %rdi
                call .Lcfg_parse_i64
                testl %edx, %edx
                jz .Lcl_def
                popq %rbx
                ret
            .Lcl_def:
                movq %rbx, %rax
                popq %rbx
                ret

            kof_config_bool:
                pushq %rbx
                movl %esi, %ebx              # default
                call kof_config_lookup
                testq %rax, %rax
                jz .Lcb_def
                movl 16(%rax), %ecx          # len
                leaq 24(%rax), %rdx          # data
                # trim rapido nas bordas
                xorq %r9, %r9
            .Lcb_tls:
                cmpq %rcx, %r9
                jge .Lcb_def
                movzbl (%rdx,%r9), %eax
                cmpb $32, %al
                je .Lcb_tls1
                cmpb $9, %al
                je .Lcb_tls1
                jmp .Lcb_tle
            .Lcb_tls1:
                incq %r9
                jmp .Lcb_tls
            .Lcb_tle:
                movq %rcx, %r10
            .Lcb_tle_loop:
                cmpq %r9, %r10
                jle .Lcb_def
                movzbl -1(%rdx,%r10), %eax
                cmpb $32, %al
                je .Lcb_tle1
                cmpb $9, %al
                je .Lcb_tle1
                jmp .Lcb_dispatch
            .Lcb_tle1:
                decq %r10
                jmp .Lcb_tle_loop
            .Lcb_dispatch:
                subq %r9, %r10               # len aparado
                leaq (%rdx), %rsi            # rsi = data do valor (contrato ci_match)
                addq %r9, %rsi               # + trim esquerdo
                # true / yes / 1 -> 1
                cmpq $4, %r10
                jne .Lcb_chk3
                leaq .Lcfg_w_true(%rip), %rdi
                jmp .Lcb_cmp_true
            .Lcb_chk3:
                cmpq $3, %r10
                jne .Lcb_chk1
                leaq .Lcfg_w_yes(%rip), %rdi
                jmp .Lcb_cmp_true
            .Lcb_chk1:
                cmpq $1, %r10
                jne .Lcb_chk5
                leaq .Lcfg_w_one(%rip), %rdi
                jmp .Lcb_cmp_true
            .Lcb_chk5:
                cmpq $5, %r10
                jne .Lcb_chk2
                leaq .Lcfg_w_false(%rip), %rdi
                jmp .Lcb_cmp_false
            .Lcb_chk2:
                cmpq $2, %r10
                jne .Lcb_def
                leaq .Lcfg_w_no(%rip), %rdi
                jmp .Lcb_cmp_false
            .Lcb_cmp_true:
                call .Lcb_ci_match
                testl %eax, %eax
                jz .Lcb_def
                movl $1, %eax
                jmp .Lcb_ret1
            .Lcb_cmp_false:
                call .Lcb_ci_match
                testl %eax, %eax
                jz .Lcb_def
                xorl %eax, %eax
                jmp .Lcb_ret1
            .Lcb_def:
                movl %ebx, %eax
            .Lcb_ret1:
                popq %rbx
                ret

            # .Lcb_ci_match(rdi=candidato, rsi=data, r10=len aparado) -> eax=1 se igual
            .Lcb_ci_match:
                pushq %rbx
                movl %r10d, %ebx
                xorl %eax, %eax
                testl %ebx, %ebx
                jle .Lcbm_no
                xorq %rcx, %rcx
            .Lcbm_loop:
                movzbl (%rdi,%rcx), %r8d
                movzbl (%rsi,%rcx), %r9d
                orb $0x20, %r8b
                orb $0x20, %r9b
                cmpl %r9d, %r8d
                jne .Lcbm_no
                incq %rcx
                cmpl %ebx, %ecx
                jl .Lcbm_loop
                movl $1, %eax
            .Lcbm_no:
                popq %rbx
                ret

            # .Lcfg_parse_i64(rdi=KofString*) -> rax=valor, edx=1 ok | edx=0 invalido
            .Lcfg_parse_i64:
                movl 16(%rdi), %ecx          # len
                leaq 24(%rdi), %r8           # data
                xorq %r9, %r9
            .Lpi_tls:
                cmpq %rcx, %r9
                jge .Lpi_bad
                movzbl (%r8,%r9), %eax
                cmpb $32, %al
                je .Lpi_tls1
                cmpb $9, %al
                je .Lpi_tls1
                jmp .Lpi_tle
            .Lpi_tls1:
                incq %r9
                jmp .Lpi_tls
            .Lpi_tle:
                movq %rcx, %r10              # fim exclusivo
            .Lpi_tle_l:
                cmpq %r9, %r10
                jle .Lpi_bad                 # vazio apos trim
                movzbl -1(%r8,%r10), %eax
                cmpb $32, %al
                je .Lpi_tle1
                cmpb $9, %al
                je .Lpi_tle1
                jmp .Lpi_sign
            .Lpi_tle1:
                decq %r10
                jmp .Lpi_tle_l
            .Lpi_sign:
                xorq %r11, %r11              # acc
                xorl %esi, %esi              # neg
                cmpq %r9, %r10
                jle .Lpi_bad
                movzbl (%r8,%r9), %eax
                cmpb $45, %al                # '-'
                je .Lpi_negset
                cmpb $43, %al                # '+'
                je .Lpi_posskip
                jmp .Lpi_dcheck
            .Lpi_negset:
                movl $1, %esi
            .Lpi_posskip:
                incq %r9
            .Lpi_dcheck:
                cmpq %r9, %r10
                jle .Lpi_bad                 # sinal sem digitos
            .Lpi_digit:
                movzbl (%r8,%r9), %eax
                subb $48, %al
                cmpb $9, %al
                ja .Lpi_bad
                imulq $10, %r11
                movzbl %al, %eax
                addq %rax, %r11
                incq %r9
                cmpq %r9, %r10
                jg .Lpi_digit
                movq %r11, %rax
                testl %esi, %esi
                jz .Lpi_ok
                negq %rax
            .Lpi_ok:
                movl $1, %edx
                ret
            .Lpi_bad:
                xorl %edx, %edx
                xorl %eax, %eax
                ret
            """);
    }
    private static void emitIoTimeFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lstr_read_err: .asciz "Runtime error: cannot read"
            .section .text
            .globl kof_now
            .type kof_now, @function
            kof_now:
                subq $16, %rsp
                movq %rsp, %rdi
                xorq %rsi, %rsi
                movq $96, %rax
                syscall
                movq 8(%rsp), %rax
                xorq %rdx, %rdx
                movq $1000, %r8
                divq %r8
                movq 0(%rsp), %rcx
                imulq $1000, %rcx
                addq %rcx, %rax
                addq $16, %rsp
                ret

            .globl kof_read_line
            .type kof_read_line, @function
            kof_read_line:
                pushq %rbx
                pushq %r12
                subq $512, %rsp
                movq %rsp, %rbx
                xorq %r12, %r12
            .Lkof_read_line_loop:
                cmpq $511, %r12
                jge .Lkof_read_line_done
                movq $0, %rax
                movq $0, %rdi
                movq $1, %rsi
                movq %rbx, %rdx
                addq %r12, %rdx
                syscall
                testq %rax, %rax
                jle .Lkof_read_line_eof
                movq %rbx, %rcx
                addq %r12, %rcx
                cmpb $10, (%rcx)
                je .Lkof_read_line_done
                incq %r12
                jmp .Lkof_read_line_loop
            .Lkof_read_line_eof:
                # EOF sem nenhum byte lido -> null (paridade com o JVM,
                # que devolve null no fim do stdin); linha parcial -> devolve
                cmpq $0, %r12
                jne .Lkof_read_line_done
                xorl %eax, %eax
                addq $512, %rsp
                popq %r12
                popq %rbx
                ret
            .Lkof_read_line_done:
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %rcx
                movl $1, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl %r12d, 16(%rcx)
                movl $0, 20(%rcx)
                movq %rcx, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                movl %r12d, %edx
                call kof_memcpy
                movq %rcx, %r13
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                addq $512, %rsp
                popq %r12
                popq %rbx
                ret

            .globl kof_read_file
            .type kof_read_file, @function
            kof_read_file:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $0, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lkof_read_file_err
                movq %rax, %r12
                subq $144, %rsp
                movq %r12, %rdi
                movq %rsp, %rsi
                movq $5, %rax
                syscall
                movq 48(%rsp), %r13
                addq $144, %rsp
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %r14
                movl $1, 0(%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %r13d, 16(%r14)
                movl $0, 20(%r14)
                movq %r12, %rdi
                movq %r14, %rsi
                addq $24, %rsi
                movl %r13d, %edx
                movq $0, %rax
                syscall
                movq %r12, %rdi
                movq $3, %rax
                syscall
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_read_file_err:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_write_file
            .type kof_write_file, @function
            kof_write_file:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $577, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lkof_write_file_fail
                movq %rax, %rdi
                leaq 24(%r12), %rsi
                movl 16(%r12), %edx
                movq $1, %rax
                syscall
                movq %rdi, %rdi
                movq $3, %rax
                syscall
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret
            .Lkof_write_file_fail:
                movq $-1, %rax
                popq %r12
                popq %rbx
                ret
            """);
    }

    private static void emitKofTimeFunctions(StringBuilder sb) {
        sb.append("""
            .globl kof_time_now
            .type kof_time_now, @function
            kof_time_now:
                jmp kof_now

            .globl kof_time_sleep
            .type kof_time_sleep, @function
            kof_time_sleep:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %ebx
                movl %ebx, %eax
                xorl %edx, %edx
                movl $1000, %ecx
                divl %ecx
                movl %eax, %r12d
                movl %edx, %r13d
                imull $1000000, %r13d
                subq $16, %rsp
                movq %r12, (%rsp)
                movq %r13, 8(%rsp)
                movq %rsp, %rdi
                xorq %rsi, %rsi
                movq $35, %rax
                syscall
                addq $16, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    private static void emitCacheFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .globl kof_cache_keys
            .globl kof_cache_vals
            .globl kof_cache_exps
            .globl kof_cache_size
            kof_cache_keys: .space 512
            kof_cache_vals: .space 512
            kof_cache_exps: .space 512
            kof_cache_size: .quad 0
            .section .text
            .globl kof_cache_get
            .type kof_cache_get, @function
            kof_cache_get:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                xorq %r12, %r12
            .kof_cache_get_loop:
                cmpq $64, %r12
                jge .kof_cache_get_miss
                movq kof_cache_keys(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_get_next
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_equals
                testq %rax, %rax
                jz .kof_cache_get_next
                movq kof_cache_exps(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_get_hit
                movq %rax, %r13
                call kof_time_now
                cmpq %rax, %r13
                jle .kof_cache_get_expired
            .kof_cache_get_hit:
                movq kof_cache_vals(,%r12,8), %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_get_expired:
                movq $0, kof_cache_keys(,%r12,8)
                movq $0, kof_cache_vals(,%r12,8)
                movq $0, kof_cache_exps(,%r12,8)
            .kof_cache_get_next:
                incq %r12
                jmp .kof_cache_get_loop
            .kof_cache_get_miss:
                xorq %rax, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_set
            .type kof_cache_set, @function
            kof_cache_set:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                xorq %rax, %rax
                call kof_cache_find_slot
                movq %rbx, kof_cache_keys(,%rax,8)
                movq %r12, kof_cache_vals(,%rax,8)
                movq $0, kof_cache_exps(,%rax,8)
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_set_ttl
            .type kof_cache_set_ttl, @function
            kof_cache_set_ttl:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %r13d
                xorq %rax, %rax
                call kof_cache_find_slot
                movq %rax, %r14
                pushq %r15
                movq %rbx, kof_cache_keys(,%r14,8)
                movq %r12, kof_cache_vals(,%r14,8)
                testl %r13d, %r13d
                jz .kof_cache_set_ttl_noexp
                movl %r13d, %edi
                movq $1000, %rax
                mul %rdi
                movq %rax, %r15
                call kof_time_now
                addq %r15, %rax
                movq %rax, kof_cache_exps(,%r14,8)
                jmp .kof_cache_set_ttl_done
            .kof_cache_set_ttl_noexp:
                movq $0, kof_cache_exps(,%r14,8)
            .kof_cache_set_ttl_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_ttl
            .type kof_cache_ttl, @function
            kof_cache_ttl:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                xorq %r12, %r12
            .kof_cache_ttl_loop:
                cmpq $64, %r12
                jge .kof_cache_ttl_miss
                movq kof_cache_keys(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_ttl_next
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_equals
                testq %rax, %rax
                jz .kof_cache_ttl_next
                movq kof_cache_exps(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_ttl_noexp
                movq %rax, %r13
                call kof_time_now
                movq %r13, %rdi
                subq %rax, %rdi
                js .kof_cache_ttl_expired
                movq %rdi, %rax
                movq $1000, %rcx
                xorq %rdx, %rdx
                divq %rcx
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_ttl_noexp:
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_ttl_expired:
                movq $0, kof_cache_keys(,%r12,8)
                movq $0, kof_cache_vals(,%r12,8)
                movq $0, kof_cache_exps(,%r12,8)
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_ttl_next:
                incq %r12
                jmp .kof_cache_ttl_loop
            .kof_cache_ttl_miss:
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_ttl_miss2:
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_delete
            .type kof_cache_delete, @function
            kof_cache_delete:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                xorq %r12, %r12
            .kof_cache_del_loop:
                cmpq $64, %r12
                jge .kof_cache_del_done
                movq kof_cache_keys(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_del_next
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_equals
                testq %rax, %rax
                jz .kof_cache_del_next
                movq $0, kof_cache_keys(,%r12,8)
                movq $0, kof_cache_vals(,%r12,8)
                movq $0, kof_cache_exps(,%r12,8)
                jmp .kof_cache_del_done
            .kof_cache_del_next:
                incq %r12
                jmp .kof_cache_del_loop
            .kof_cache_del_done:
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_clear
            .type kof_cache_clear, @function
            kof_cache_clear:
                xorq %rax, %rax
            .kof_cache_clear_loop:
                cmpq $64, %rax
                jge .kof_cache_clear_done
                movq $0, kof_cache_keys(,%rax,8)
                movq $0, kof_cache_vals(,%rax,8)
                movq $0, kof_cache_exps(,%rax,8)
                incq %rax
                jmp .kof_cache_clear_loop
            .kof_cache_clear_done:
                ret

            .globl kof_cache_find_slot
            .type kof_cache_find_slot, @function
            kof_cache_find_slot:
                pushq %rbx
                pushq %r12
                xorq %r12, %r12
            .kof_cache_find_existing:
                cmpq $64, %r12
                jge .kof_cache_find_first_empty
                movq kof_cache_keys(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_find_first_empty
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_equals
                testq %rax, %rax
                jnz .kof_cache_find_done
                incq %r12
                jmp .kof_cache_find_existing
            .kof_cache_find_first_empty:
                cmpq $64, %r12
                jl .kof_cache_find_done
                xorq %rax, %rax
                jmp .kof_cache_find_ret
            .kof_cache_find_done:
                movq %r12, %rax
            .kof_cache_find_ret:
                popq %r12
                popq %rbx
                ret
            """);
    }

    // M32.3: Vulkan compute REAL no native via libvkchain.so (C, RADV validado).
    // A cadeia completa (instance/device/pipeline/buffers/cmd/dispatch) vive na
    // libvkchain.so — aqui só dlopen+dlsym de 3 símbolos. Se a lib não existe
    // ou o init falha (sem ICD, sem spv), degrada: available=0 → HAL usa os
    // goldens CPU. O programa nunca cai.

    private static void emitIoFileFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lio_slash: .asciz "/"
            .Lio_dot: .asciz "."
            .Lio_dotdot: .asciz ".."
            .Lio_read_err: .asciz "Runtime error: cannot read file"
            .section .text

            // kof_io_make_string(data_ptr, len) → new KofString
            .globl kof_io_make_string
            .type kof_io_make_string, @function
            kof_io_make_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                movq %r12, %rdx
                call kof_memcpy
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            // kof_io_strlen(rdi) → byte length of a NUL-terminated string
            .globl kof_io_strlen
            .type kof_io_strlen, @function
            kof_io_strlen:
                xorq %rax, %rax
            .Lio_strlen_loop:
                cmpb $0, (%rdi,%rax)
                je .Lio_strlen_done
                incq %rax
                jmp .Lio_strlen_loop
            .Lio_strlen_done:
                ret

            // kof_io_last_slash(str) → index of last '/' or -1
            .globl kof_io_last_slash
            .type kof_io_last_slash, @function
            kof_io_last_slash:
                movl 16(%rdi), %eax
                decl %eax
            .Lio_last_slash_loop:
                cmpl $0, %eax
                jl .Lio_last_slash_done
                leaq 24(%rdi), %rcx
                cmpb $47, (%rcx,%rax)
                je .Lio_last_slash_done
                decl %eax
                jmp .Lio_last_slash_loop
            .Lio_last_slash_done:
                ret

            // kof_io_strip_trailing(str) → effective length (trailing '/' removed, root kept)
            .globl kof_io_strip_trailing
            .type kof_io_strip_trailing, @function
            kof_io_strip_trailing:
                movl 16(%rdi), %eax
            .Lio_strip_loop:
                cmpl $1, %eax
                jle .Lio_strip_done
                leaq 24(%rdi), %rcx
                cmpb $47, -1(%rcx,%rax)
                jne .Lio_strip_done
                decl %eax
                jmp .Lio_strip_loop
            .Lio_strip_done:
                ret

            // kof_io_stat_mode(str) → st_mode or -1
            .globl kof_io_stat_mode
            .type kof_io_stat_mode, @function
            kof_io_stat_mode:
                pushq %rbx
                subq $144, %rsp
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq %rsp, %rdx
                xorq %r10, %r10
                movq $262, %rax
                syscall
                testq %rax, %rax
                js .Lio_stat_err
                movl 24(%rsp), %eax
                addq $144, %rsp
                popq %rbx
                ret
            .Lio_stat_err:
                movq $-1, %rax
                addq $144, %rsp
                popq %rbx
                ret

            // ── Path ──────────────────────────────────────────────

            .globl kof_io_file_name
            .type kof_io_file_name, @function
            kof_io_file_name:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                call kof_io_strip_trailing
                movl %eax, %r13d
                movl %eax, %r12d
                decl %r12d
            .Lio_name_find:
                cmpl $0, %r12d
                jl .Lio_name_no_slash
                leaq 24(%rbx), %rcx
                cmpb $47, (%rcx,%r12)
                je .Lio_name_found
                decl %r12d
                jmp .Lio_name_find
            .Lio_name_found:
                leal 1(%r12), %eax
                movl %r13d, %ecx
                subl %eax, %ecx
                jg .Lio_name_sub
                movq %rbx, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_name_sub:
                leaq 24(%rbx), %rdi
                addq %r12, %rdi
                incq %rdi
                movslq %ecx, %rsi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_name_no_slash:
                movslq %r13d, %rsi
                leaq 24(%rbx), %rdi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_file_name
            .type kof_io_path_file_name, @function
            kof_io_path_file_name:
                jmp kof_io_file_name

            .globl kof_io_path_parent
            .type kof_io_path_parent, @function
            kof_io_path_parent:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                call kof_io_strip_trailing
                movl %eax, %r13d
                movl %eax, %r12d
                decl %r12d
            .Lio_parent_find:
                cmpl $0, %r12d
                jl .Lio_parent_none
                leaq 24(%rbx), %rcx
                cmpb $47, (%rcx,%r12)
                je .Lio_parent_found
                decl %r12d
                jmp .Lio_parent_find
            .Lio_parent_found:
                testl %r12d, %r12d
                jne .Lio_parent_prefix
                leaq .Lio_slash(%rip), %rdi
                movq $1, %rsi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_parent_prefix:
                leaq 24(%rbx), %rdi
                movslq %r12d, %rsi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_parent_none:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_extension
            .type kof_io_path_extension, @function
            kof_io_path_extension:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                call kof_io_strip_trailing
                movl %eax, %r13d
                movl %eax, %r14d
                decl %r14d
            .Lio_ext_find_slash:
                cmpl $0, %r14d
                jl .Lio_ext_dot_from
                leaq 24(%rbx), %rcx
                cmpb $47, (%rcx,%r14)
                je .Lio_ext_dot_from
                decl %r14d
                jmp .Lio_ext_find_slash
            .Lio_ext_dot_from:
                movl %r13d, %r12d
                decl %r12d
            .Lio_ext_find_dot:
                cmpl %r14d, %r12d
                jle .Lio_ext_empty
                leaq 24(%rbx), %rcx
                cmpb $46, (%rcx,%r12)
                je .Lio_ext_found
                decl %r12d
                jmp .Lio_ext_find_dot
            .Lio_ext_found:
                movl %r13d, %ecx
                subl %r12d, %ecx
                decl %ecx
                jg .Lio_ext_sub
            .Lio_ext_empty:
                xorl %esi, %esi
                leaq .Lio_dot(%rip), %rdi
                call kof_io_make_string
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_ext_sub:
                leaq 24(%rbx), %rdi
                addq %r12, %rdi
                incq %rdi
                movslq %ecx, %rsi
                call kof_io_make_string
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_resolve
            .type kof_io_path_resolve, @function
            kof_io_path_resolve:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                cmpb $47, 24(%r12)
                je .Lio_resolve_b
                cmpl $0, 16(%rbx)
                je .Lio_resolve_b
                movl 16(%rbx), %eax
                leaq 24(%rbx), %rcx
                cmpb $47, -1(%rcx,%rax)
                je .Lio_resolve_concat
                leaq .Lio_slash(%rip), %rdi
                movq $1, %rsi
                call kof_string_from_literal
                movq %rax, %r13
                movq %rbx, %rdi
                movq %r13, %rsi
                call kof_string_concat
                movq %rax, %r14
                movq %r14, %rdi
                movq %r12, %rsi
                call kof_string_concat
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_resolve_concat:
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_string_concat
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_resolve_b:
                movq %r12, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_is_absolute
            .type kof_io_path_is_absolute, @function
            kof_io_path_is_absolute:
                cmpl $0, 16(%rdi)
                jle .Lio_abs_no
                cmpb $47, 24(%rdi)
                jne .Lio_abs_no
                movq $1, %rax
                ret
            .Lio_abs_no:
                xorl %eax, %eax
                ret

            .globl kof_io_path_to_absolute
            .type kof_io_path_to_absolute, @function
            kof_io_path_to_absolute:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                cmpb $47, 24(%rbx)
                je .Lio_toabs_ret
                subq $4096, %rsp
                movq %rsp, %rdi
                movq $4096, %rsi
                movq $79, %rax
                syscall
                testq %rax, %rax
                js .Lio_toabs_err
                movq %rsp, %rdi
                call kof_io_strlen
                movq %rax, %rsi
                movq %rsp, %rdi
                call kof_io_make_string
                movq %rax, %rdi
                movq %rbx, %rsi
                call kof_io_path_resolve
                movq %rax, %r12
                addq $4096, %rsp
                movq %r12, %rax
                popq %r12
                popq %rbx
                ret
            .Lio_toabs_err:
                addq $4096, %rsp
            .Lio_toabs_ret:
                movq %rbx, %rax
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_normalize
            .type kof_io_path_normalize, @function
            kof_io_path_normalize:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $576, %rsp
                movq %rdi, %rbx
                movq $0, 512(%rsp)
                cmpl $0, 16(%rbx)
                je .Lio_norm_scan
                cmpb $47, 24(%rbx)
                jne .Lio_norm_scan
                movq $1, 512(%rsp)
            .Lio_norm_scan:
                xorl %r12d, %r12d
                xorl %r13d, %r13d
                xorl %r14d, %r14d
            .Lio_norm_collect:
                movl 16(%rbx), %ecx
                cmpl %ecx, %r13d
                jge .Lio_norm_final
                leaq 24(%rbx), %rax
                cmpb $47, (%rax,%r13)
                jne .Lio_norm_advance
                movl %r13d, %r9d
                subl %r14d, %r9d
                jz .Lio_norm_seg_done
                cmpl $1, %r9d
                jne .Lio_norm_check_dotdot
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                je .Lio_norm_seg_done
            .Lio_norm_check_dotdot:
                cmpl $2, %r9d
                jne .Lio_norm_push
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                jne .Lio_norm_push
                cmpb $46, 1(%rax,%r14)
                jne .Lio_norm_push
                testl %r12d, %r12d
                jle .Lio_norm_dotdot_empty
                decl %r12d
                jmp .Lio_norm_seg_done
            .Lio_norm_dotdot_empty:
                cmpq $0, 512(%rsp)
                jne .Lio_norm_seg_done
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
                jmp .Lio_norm_seg_done
            .Lio_norm_push:
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
            .Lio_norm_seg_done:
                incl %r13d
                movl %r13d, %r14d
                jmp .Lio_norm_collect
            .Lio_norm_advance:
                incl %r13d
                jmp .Lio_norm_collect
            .Lio_norm_final:
                movl %r13d, %r9d
                subl %r14d, %r9d
                jz .Lio_norm_build
                cmpl $1, %r9d
                jne .Lio_norm_final_dotdot
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                je .Lio_norm_build
            .Lio_norm_final_dotdot:
                cmpl $2, %r9d
                jne .Lio_norm_final_push
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                jne .Lio_norm_final_push
                cmpb $46, 1(%rax,%r14)
                jne .Lio_norm_final_push
                testl %r12d, %r12d
                jle .Lio_norm_final_dotdot_empty
                decl %r12d
                jmp .Lio_norm_build
            .Lio_norm_final_dotdot_empty:
                cmpq $0, 512(%rsp)
                jne .Lio_norm_build
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
                jmp .Lio_norm_build
            .Lio_norm_final_push:
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
            .Lio_norm_build:
                xorl %ecx, %ecx
                cmpq $0, 512(%rsp)
                je .Lio_norm_total_segs
                incl %ecx
            .Lio_norm_total_segs:
                testl %r12d, %r12d
                jle .Lio_norm_total_done
                xorl %r9d, %r9d
            .Lio_norm_total_loop:
                cmpl %r12d, %r9d
                jge .Lio_norm_total_done
                movl 4(%rsp,%r9,8), %eax
                addl %eax, %ecx
                testl %r9d, %r9d
                je .Lio_norm_total_next
                incl %ecx
            .Lio_norm_total_next:
                incl %r9d
                jmp .Lio_norm_total_loop
            .Lio_norm_total_done:
                cmpq $0, 512(%rsp)
                jne .Lio_norm_alloc
                testl %r12d, %r12d
                jg .Lio_norm_alloc
                movl $1, %ecx
                movq $-1, 520(%rsp)
                jmp .Lio_norm_alloc2
            .Lio_norm_alloc:
                movq $0, 520(%rsp)
            .Lio_norm_alloc2:
                movslq %ecx, %rdi
                call kof_alloc
                movq %rax, %r15
                xorl %r9d, %r9d
                cmpq $0, 512(%rsp)
                je .Lio_norm_build_rel
                movb $47, (%r15)
                incl %r9d
            .Lio_norm_build_rel:
                cmpq $-1, 520(%rsp)
                jne .Lio_norm_copy_segs
                movb $46, (%r15)
                incl %r9d
                jmp .Lio_norm_done
            .Lio_norm_copy_segs:
                xorl %r10d, %r10d
            .Lio_norm_seg_loop:
                cmpl %r12d, %r10d
                jge .Lio_norm_done
                testl %r10d, %r10d
                je .Lio_norm_seg_no_sep
                movb $47, (%r15,%r9)
                incl %r9d
            .Lio_norm_seg_no_sep:
                movl (%rsp,%r10,8), %r11d
                movl 4(%rsp,%r10,8), %eax
                movl %eax, %ecx
            .Lio_norm_seg_inner:
                testl %ecx, %ecx
                jle .Lio_norm_seg_next
                leaq 24(%rbx), %rdi
                movb (%rdi,%r11), %dl
                movb %dl, (%r15,%r9)
                incl %r9d
                incq %r11
                decl %ecx
                jmp .Lio_norm_seg_inner
            .Lio_norm_seg_next:
                incl %r10d
                jmp .Lio_norm_seg_loop
            .Lio_norm_done:
                movq %r15, %rdi
                movslq %r9d, %rsi
                call kof_io_make_string
                addq $576, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            // ── File ─────────────────────────────────────────────

            .globl kof_io_file_exists
            .type kof_io_file_exists, @function
            kof_io_file_exists:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_exists_no
                movq $1, %rax
                popq %rbx
                ret
            .Lio_exists_no:
                xorl %eax, %eax
                popq %rbx
                ret

            .globl kof_io_file_is_file
            .type kof_io_file_is_file, @function
            kof_io_file_is_file:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_is_file_no
                andq $61440, %rax
                cmpq $32768, %rax
                jne .Lio_is_file_no
                movq $1, %rax
                popq %rbx
                ret
            .Lio_is_file_no:
                xorl %eax, %eax
                popq %rbx
                ret

            .globl kof_io_file_is_dir
            .type kof_io_file_is_dir, @function
            kof_io_file_is_dir:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_is_dir_no
                andq $61440, %rax
                cmpq $16384, %rax
                jne .Lio_is_dir_no
                movq $1, %rax
                popq %rbx
                ret
            .Lio_is_dir_no:
                xorl %eax, %eax
                popq %rbx
                ret

            .globl kof_io_read_text
            .type kof_io_read_text, @function
            kof_io_read_text:
                jmp kof_read_file

            .globl kof_io_write_text
            .type kof_io_write_text, @function
            kof_io_write_text:
                call kof_write_file
                testq %rax, %rax
                je .Lio_ok1
                xorl %eax, %eax
                ret
            .Lio_ok1:
                movq $1, %rax
                ret

            .globl kof_io_append_text
            .type kof_io_append_text, @function
            kof_io_append_text:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $1089, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_append_fail
                movq %rax, %rdi
                leaq 24(%r12), %rsi
                movl 16(%r12), %edx
                movq $1, %rax
                syscall
                movq %rdi, %rdi
                movq $3, %rax
                syscall
                movq $1, %rax
                popq %r12
                popq %rbx
                ret
            .Lio_append_fail:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_io_delete
            .type kof_io_delete, @function
            kof_io_delete:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_del_fail
                andq $61440, %rax
                cmpq $16384, %rax
                je .Lio_del_rmdir
                leaq 24(%rbx), %rdi
                movq $87, %rax
                syscall
                testq %rax, %rax
                je .Lio_del_ok
            .Lio_del_fail:
                xorl %eax, %eax
                popq %rbx
                ret
            .Lio_del_rmdir:
                leaq 24(%rbx), %rdi
                movq $84, %rax
                syscall
                testq %rax, %rax
                jne .Lio_del_fail
            .Lio_del_ok:
                movq $1, %rax
                popq %rbx
                ret

            .Lstr_io_size_prefix: .byte 115,105,122,101,58,32,102,105,108,101,32,110,111,116,32,102,111,117,110,100,58,32
            .globl kof_io_file_size
            .type kof_io_file_size, @function
            kof_io_file_size:
                pushq %rbx
                subq $144, %rsp
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq %rsp, %rdx
                xorq %r10, %r10
                movq $262, %rax
                syscall
                testq %rax, %rax
                js .Lio_size_err
                movq 48(%rsp), %rax
                addq $144, %rsp
                popq %rbx
                ret
            .Lio_size_err:
                leaq .Lstr_io_size_prefix(%rip), %rdi
                movl $22, %esi
                call kof_string_from_literal   # rax = KofString "size: file not found: "
                movq %rax, %rdi
                movq %rbx, %rsi                 # path (preservado em rbx)
                call kof_string_concat          # rax = prefixo + path
                movq %rax, %rdi
                call kof_throw_string           # longjmp p/ o try; panic se não houver — não retorna

            // ── Bytes ────────────────────────────────────────────

            .globl kof_io_read_bytes
            .type kof_io_read_bytes, @function
            kof_io_read_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $0, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_bytes_err
                movq %rax, %rbx
                subq $144, %rsp
                movq %rbx, %rdi
                movq %rsp, %rsi
                movq $5, %rax
                syscall
                movq 48(%rsp), %r12
                addq $144, %rsp
                movq %r12, %rdi
                call kof_alloc
                movq %rax, %r15
                movq %rbx, %rdi
                movq %r15, %rsi
                movq %r12, %rdx
                movq $0, %rax
                syscall
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                movl %r12d, %edi
                movq $4, %rsi
                call kof_array_alloc
                movq %rax, %r13
                xorq %rcx, %rcx
            .Lio_bytes_spread:
                cmpq %r12, %rcx
                jge .Lio_bytes_done
                movb (%r15,%rcx), %al
                movb %al, 24(%r13,%rcx,4)
                incq %rcx
                jmp .Lio_bytes_spread
            .Lio_bytes_done:
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_bytes_err:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── M32-gguf: leitura com offset p/ arquivos grandes (sem carregar o todo)
            # kof_io_read_range(rdi=path KofString*, rsi=offset, rdx=len) → Int[]|0
            # kof_io_read_range_path idêntica (o File receiver resolve o path em
            # compile-time; no native as duas caem no mesmo corpo)
            .globl kof_io_read_range
            .type kof_io_read_range, @function
            kof_io_read_range:
            .globl kof_io_read_range_path
            .type kof_io_read_range_path, @function
            kof_io_read_range_path:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $8, %rsp
                movq %rdi, %rbx      # path KofString*
                movq %rsi, %r14      # offset
                movq %rdx, %r15      # len
                # openat(AT_FDCWD=-100, path+24, O_RDONLY=0, 0)
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                xorl %edx, %edx
                xorl %r10d, %r10d
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_range_err
                movq %rax, %r12      # fd
                # buf = kof_alloc(len)
                movq %r15, %rdi
                call kof_alloc
                movq %rax, %r13      # buf
                # pread(fd, buf, len, offset)
                movq %r12, %rdi
                movq %r13, %rsi
                movq %r15, %rdx
                movq %r14, %r10
                movq $17, %rax
                syscall
                movq %rax, %r14      # lidos (reusa o slot do offset)
                # close(fd)
                movq %r12, %rdi
                movq $3, %rax
                syscall
                # Int[] elemsize 4
                movl %r14d, %edi
                movq $4, %rsi
                call kof_array_alloc
                movq %rax, %r12      # array (fd slot já não precisa)
                # spread byte → Int
                xorq %rcx, %rcx
            .Lio_range_spread:
                cmpq %r14, %rcx
                jge .Lio_range_done
                movb (%r13,%rcx), %al
                movzbl %al, %eax
                movl %eax, 24(%r12,%rcx,4)
                incq %rcx
                jmp .Lio_range_spread
            .Lio_range_done:
                movq %r12, %rax
                addq $8, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_range_err:
                xorl %eax, %eax
                addq $8, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_write_bytes
            .type kof_io_write_bytes, @function
            kof_io_write_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $577, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_wb_err
                movq %rax, %rbx
                movl 16(%r12), %r14d
                leal 16(%r14), %edi
                call kof_alloc
                movq %rax, %r13
                xorq %rcx, %rcx
            .Lio_wb_copy:
                cmpq %r14, %rcx
                jge .Lio_wb_write
                movl 24(%r12,%rcx,4), %eax
                movb %al, (%r13,%rcx)
                incq %rcx
                jmp .Lio_wb_copy
            .Lio_wb_write:
                movq %rbx, %rdi
                movq %r13, %rsi
                movq %r14, %rdx
                movq $1, %rax
                syscall
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                movq $1, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_wb_err:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_append_bytes
            .type kof_io_append_bytes, @function
            kof_io_append_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $1089, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_ab_err
                movq %rax, %rbx
                movl 16(%r12), %r14d
                leal 16(%r14), %edi
                call kof_alloc
                movq %rax, %r13
                xorq %rcx, %rcx
            .Lio_ab_copy:
                cmpq %r14, %rcx
                jge .Lio_ab_write
                movl 24(%r12,%rcx,4), %eax
                movb %al, (%r13,%rcx)
                incq %rcx
                jmp .Lio_ab_copy
            .Lio_ab_write:
                movq %rbx, %rdi
                movq %r13, %rsi
                movq %r14, %rdx
                movq $1, %rax
                syscall
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                movq $1, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_ab_err:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            // ── Directory ────────────────────────────────────────

            .globl kof_io_dir_create
            .type kof_io_dir_create, @function
            kof_io_dir_create:
                pushq %rbx
                movq %rdi, %rbx
                leaq 24(%rbx), %rdi
                movq $493, %rsi
                movq $83, %rax
                syscall
                testq %rax, %rax
                je .Lio_mkdir_ok
                xorl %eax, %eax
                popq %rbx
                ret
            .Lio_mkdir_ok:
                movq $1, %rax
                popq %rbx
                ret

            .globl kof_io_dir_create_dirs
            .type kof_io_dir_create_dirs, @function
            kof_io_dir_create_dirs:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $520, %rsp
                movq %rdi, %rbx
                movl 16(%rbx), %r13d
                movq $1, %r12
            .Lio_dirs_scan:
                cmpq %r13, %r12
                jg .Lio_dirs_final
                leaq 24(%rbx), %rax
                cmpb $47, (%rax,%r12)
                jne .Lio_dirs_advance
                call .Lio_dirs_mkdir_prefix
                testq %rax, %rax
                je .Lio_dirs_advance
                jmp .Lio_dirs_err
            .Lio_dirs_advance:
                incq %r12
                jmp .Lio_dirs_scan
            .Lio_dirs_final:
                leaq 24(%rbx), %rdi
                movq $493, %rsi
                movq $83, %rax
                syscall
                testq %rax, %rax
                je .Lio_dirs_ok
                cmpq $-17, %rax
                jne .Lio_dirs_err
            .Lio_dirs_ok:
                movq $1, %rax
                addq $520, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_dirs_err:
                xorl %eax, %eax
                addq $520, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_dirs_mkdir_prefix:
                leaq 8(%rsp), %r8
                movq %r12, %rcx
                xorq %r9, %r9
                leaq 24(%rbx), %rsi
                movq %r8, %rdi
            .Lio_dirs_copy:
                cmpq %rcx, %r9
                jge .Lio_dirs_copy_done
                movb (%rsi,%r9), %al
                movb %al, (%rdi,%r9)
                incq %r9
                jmp .Lio_dirs_copy
            .Lio_dirs_copy_done:
                movb $0, (%rdi,%r9)
                cmpq $1, %rcx
                jle .Lio_dirs_prefix_skip
                movq %r8, %rdi
                movq $493, %rsi
                movq $83, %rax
                syscall
                testq %rax, %rax
                je .Lio_dirs_prefix_skip
                cmpq $-17, %rax
                je .Lio_dirs_prefix_skip
                movq $-1, %rax
                ret
            .Lio_dirs_prefix_skip:
                xorl %eax, %eax
                ret

            .globl kof_io_dir_delete
            .type kof_io_dir_delete, @function
            kof_io_dir_delete:
                pushq %rbx
                movq %rdi, %rbx
                leaq 24(%rbx), %rdi
                movq $84, %rax
                syscall
                testq %rax, %rax
                je .Lio_rmdir_ok
                xorl %eax, %eax
                popq %rbx
                ret
            .Lio_rmdir_ok:
                movq $1, %rax
                popq %rbx
                ret

            .globl kof_io_dir_list
            .type kof_io_dir_list, @function
            kof_io_dir_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32768, %rsp
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $65536, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_list_err
                movq %rax, %rbx
                movq %rsp, %r13
                call kof_list_new
                movq %rax, %r12
            .Lio_list_loop:
                movq %rbx, %rdi
                movq %r13, %rsi
                movq $32768, %rdx
                movq $217, %rax
                syscall
                testq %rax, %rax
                jle .Lio_list_done
                movq %rax, %r14
                movq %r13, %r15
            .Lio_list_entry:
                movzwq 16(%r15), %rdx
                testq %rdx, %rdx
                je .Lio_list_next_buf
                cmpb $46, 19(%r15)
                jne .Lio_list_add
                cmpb $0, 20(%r15)
                je .Lio_list_skip
                cmpb $46, 20(%r15)
                jne .Lio_list_add
                cmpb $0, 21(%r15)
                je .Lio_list_skip
            .Lio_list_add:
                leaq 19(%r15), %rdi
                call kof_io_strlen
                movq %rax, %rsi
                leaq 19(%r15), %rdi
                call kof_io_make_string
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_list_add
            .Lio_list_skip:
                movzwq 16(%r15), %rdx
                addq %rdx, %r15
                leaq (%r13,%r14), %rax
                cmpq %rax, %r15
                jb .Lio_list_entry
                jmp .Lio_list_loop
            .Lio_list_next_buf:
                jmp .Lio_list_done
            .Lio_list_done:
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                call .Lio_list_sort
                movq %r12, %rax
                addq $32768, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_list_sort:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r11
                movq %r12, %rbx
                movl 16(%rbx), %r12d
                movq 24(%rbx), %r11
                movq $1, %r13
            .Lio_sort_i:
                cmpl %r12d, %r13d
                jge .Lio_sort_done
                movq (%r11,%r13,8), %r15
                movl %r13d, %r14d
            .Lio_sort_j:
                testl %r14d, %r14d
                jle .Lio_sort_place
                movq -8(%r11,%r14,8), %rdi
                movq %r15, %rsi
                call .Lio_str_less
                testq %rax, %rax
                jne .Lio_sort_place
                movq -8(%r11,%r14,8), %rax
                movq %rax, (%r11,%r14,8)
                decl %r14d
                jmp .Lio_sort_j
            .Lio_sort_place:
                movq %r15, (%r11,%r14,8)
                incq %r13
                jmp .Lio_sort_i
            .Lio_sort_done:
                popq %r11
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_str_less:
                movl 16(%rdi), %ecx
                movl 16(%rsi), %r8d
                xorl %r9d, %r9d
            .Lio_less_loop:
                cmpl %ecx, %r9d
                jge .Lio_less_left_done
                cmpl %r8d, %r9d
                jge .Lio_less_longer
                movzbl 24(%rdi,%r9), %eax
                movzbl 24(%rsi,%r9), %r10d
                cmpl %r10d, %eax
                jl .Lio_less_true
                jg .Lio_less_false
                incl %r9d
                jmp .Lio_less_loop
            .Lio_less_left_done:
                cmpl %r8d, %r9d
                jl .Lio_less_true
            .Lio_less_false:
                xorl %eax, %eax
                ret
            .Lio_less_longer:
                xorl %eax, %eax
                ret
            .Lio_less_true:
                movq $1, %rax
                ret
            .Lio_list_err:
                xorl %eax, %eax
                addq $32768, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }

    private static void emitUiColorFunctions(StringBuilder sb) {
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

    /**
     * kof.db no target Native — link direto da client library (libsqlite3)
     * no passo do ld (padrão kof-webview: sem JDBC driver, sem headers).
     *
     * A convenção segue o System V usado pelo NativeBackend: args em
     * rdi/rsi/rdx/rcx/r8/r9, retorno em rax. O handle de conexão é um
     * KofString "db<N>" indexando o registry abaixo. Binds chegam crus
     * (kof_box é no-op no native): valores &lt; 0x1000000 são Ints, senão
     * ponteiros de KofString — heurística documentada (um bind Int maior
     * que 16MB seria interpretado como String).
     */
    static void emitDbSqlite(StringBuilder sb) {
        sb.append("""
            .section .data
            .Ldb_null: .asciz "null"
            # KofStrings (header 24B: 1@0, 0@4, 0@8, len@16, 0@20; dados em +24)
            # p/ transaction — kf_db_execute lê em leaq 24(%rsi).
            .Ldb_begin_str:
                .long 1
                .long 0
                .quad 0
                .long 5
                .long 0
                .asciz "begin"
            .Ldb_commit_str:
                .long 1
                .long 0
                .quad 0
                .long 6
                .long 0
                .asciz "commit"
            .Ldb_rollback_str:
                .long 1
                .long 0
                .quad 0
                .long 8
                .long 0
                .asciz "rollback"
            .section .bss
            .Ldb_slots: .zero 512
            .Ldb_types: .zero 64
            .Ldb_count: .quad 0
            # handle (KofString*) da conexão "default" — o que transaction {} usa.
            # 0 = sem conexão aberta.
            .Ldb_default_handle: .quad 0
            .Ldb_mysql_buf: .zero 16384
            .Ldb_mysql_names: .zero 1024
            .Ldb_mysql_seq: .zero 1
            # estado do reader de pacotes (query):
            #   .Ldb_mysql_fd    — fd atual
            #   .Ldb_mysql_ppos  — offset do payload atual (buf+4)
            #   .Ldb_mysql_pend  — fim do payload atual
            #   .Ldb_mysql_next  — 1 se o próximo pacote já está em buf
            .Ldb_mysql_fd: .quad 0
            .Ldb_mysql_ppos: .quad 0
            .Ldb_mysql_pend: .quad 0
            .Ldb_mysql_next: .long 0
            .section .data
            .Ldb_mysql_plugin: .asciz "mysql_native_password"
            .Ldb_mysql_empty: .asciz ""
            .Ldb_mysql_nullstr: .asciz "null"
            .section .text

            # ── SHA-1 (para o auth scramble do MySQL) ────────────────
            # kof_sec_sha1_block: (rdi=h[5] em LE na stack, rsi=bloco 64B)
            .globl kof_sec_sha1_block
            .type kof_sec_sha1_block, @function
            kof_sec_sha1_block:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $352, %rsp           # w[80]=320 + h[5]=20
                movq %rdi, %r12
                movq %rsi, %r13
                movl 0(%r12), %eax
                movl %eax, 320(%rsp)
                movl 4(%r12), %eax
                movl %eax, 324(%rsp)
                movl 8(%r12), %eax
                movl %eax, 328(%rsp)
                movl 12(%r12), %eax
                movl %eax, 332(%rsp)
                movl 16(%r12), %eax
                movl %eax, 336(%rsp)
                # w[0..15] = bloco em BE
                xorl %ecx, %ecx
            .Lsha1_load:
                cmpl $16, %ecx
                jge .Lsha1_load_done
                movl (%r13,%rcx,4), %eax
                bswapl %eax
                movl %eax, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsha1_load
            .Lsha1_load_done:
                # w[16..79]
                movl $16, %ecx
            .Lsha1_w:
                cmpl $80, %ecx
                jge .Lsha1_w_done
                movl -12(%rsp,%rcx,4), %eax
                xorl -32(%rsp,%rcx,4), %eax
                xorl -56(%rsp,%rcx,4), %eax
                xorl -64(%rsp,%rcx,4), %eax
                roll $1, %eax
                movl %eax, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsha1_w
            .Lsha1_w_done:
                # a..e = h[0..4]
                movl 320(%rsp), %r8d
                movl 324(%rsp), %r9d
                movl 328(%rsp), %r10d
                movl 332(%rsp), %r11d
                movl 336(%rsp), %ebx
                xorl %r15d, %r15d
            .Lsha1_round:
                cmpl $80, %r15d
                jge .Lsha1_round_done
                # f/g/K por fase
                cmpl $20, %r15d
                jge .Lsha1_phase2
                movl %r9d, %eax
                andl %r10d, %eax
                movl %r9d, %edx
                notl %edx
                andl %r11d, %edx
                orl %edx, %eax
                movl $0x5A827999, %r14d
                jmp .Lsha1_f_done
            .Lsha1_phase2:
                cmpl $40, %r15d
                jge .Lsha1_phase3
                movl %r9d, %eax
                xorl %r10d, %eax
                xorl %r11d, %eax
                movl $0x6ED9EBA1, %r14d
                jmp .Lsha1_f_done
            .Lsha1_phase3:
                cmpl $60, %r15d
                jge .Lsha1_phase4
                movl %r9d, %eax
                andl %r10d, %eax
                movl %r9d, %edx
                andl %r11d, %edx
                orl %edx, %eax
                movl %r10d, %edx
                andl %r11d, %edx
                orl %edx, %eax
                movl $0x8F1BBCDC, %r14d
                jmp .Lsha1_f_done
            .Lsha1_phase4:
                movl %r9d, %eax
                xorl %r10d, %eax
                xorl %r11d, %eax
                movl $0xCA62C1D6, %r14d
            .Lsha1_f_done:
                # temp = ROTL5(a) + f + e + K + W[i]
                movl %r8d, %edx
                roll $5, %edx
                addl %eax, %edx
                addl %ebx, %edx
                addl %r14d, %edx
                addl (%rsp,%r15,4), %edx
                movl %r9d, %eax
                movl %r11d, %ebx
                movl %r10d, %r11d
                roll $30, %eax
                movl %eax, %r10d
                movl %r8d, %r9d
                movl %edx, %r8d
                incq %r15
                jmp .Lsha1_round
            .Lsha1_round_done:
                addl %r8d, 320(%rsp)
                addl %r9d, 324(%rsp)
                addl %r10d, 328(%rsp)
                addl %r11d, 332(%rsp)
                addl %ebx, 336(%rsp)
                movl 320(%rsp), %eax
                movl %eax, 0(%r12)
                movl 324(%rsp), %eax
                movl %eax, 4(%r12)
                movl 328(%rsp), %eax
                movl %eax, 8(%r12)
                movl 332(%rsp), %eax
                movl %eax, 12(%r12)
                movl 336(%rsp), %eax
                movl %eax, 16(%r12)
                addq $352, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_sha1_internal(rdi=out20, rsi=src, rdx=len)
            .globl kof_sec_sha1_internal
            .type kof_sec_sha1_internal, @function
            kof_sec_sha1_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $168, %rsp          # h[5]=20 + bloco 64 + pad 128
                movq %rdi, %r12
                movq %rsi, %r13
                movq %rdx, %r14
                movl $0x67452301, 0(%rsp)
                movl $0xEFCDAB89, 4(%rsp)
                movl $0x98BADCFE, 8(%rsp)
                movl $0x10325476, 12(%rsp)
                movl $0xC3D2E1F0, 16(%rsp)
                xorq %r15, %r15
            .Lsha1_full:
                movq %r14, %rax
                subq %r15, %rax
                cmpq $64, %rax
                jl .Lsha1_final
                movq %rsp, %rdi
                leaq (%r13,%r15), %rsi
                call kof_sec_sha1_block
                addq $64, %r15
                jmp .Lsha1_full
            .Lsha1_final:
                movq %r14, %rax
                subq %r15, %rax
                movq %rax, %rcx
                leaq 20(%rsp), %rdi
                xorq %rdx, %rdx
            .Lsha1_copy:
                cmpq %rcx, %rdx
                jge .Lsha1_copy_done
                leaq (%r13,%r15), %rsi
                movb (%rsi,%rdx), %al
                movb %al, (%rdi,%rdx)
                incq %rdx
                jmp .Lsha1_copy
            .Lsha1_copy_done:
                movb $0x80, (%rdi,%rcx)
                movq %rcx, %r15
                movq %rcx, %rdx
                incq %rdx
            .Lsha1_pad:
                cmpq $128, %rdx
                jge .Lsha1_pad_done
                movb $0, (%rdi,%rdx)
                incq %rdx
                jmp .Lsha1_pad
            .Lsha1_pad_done:
                movq %r14, %rax
                shlq $3, %rax
                bswapq %rax
                movq %rax, 56(%rdi)
                # primeiro bloco do pad (sempre o final: 1 bloco p/ len<56)
                movq %rsp, %rdi
                leaq 20(%rsp), %rsi
                call kof_sec_sha1_block
                movq %r12, %rdi
                movq %rsp, %rsi
                # escreve o digest (BE) direto no out
                movl 0(%rsp), %eax
                bswapl %eax
                movl %eax, 0(%r12)
                movl 4(%rsp), %eax
                bswapl %eax
                movl %eax, 4(%r12)
                movl 8(%rsp), %eax
                bswapl %eax
                movl %eax, 8(%r12)
                movl 12(%rsp), %eax
                bswapl %eax
                movl %eax, 12(%r12)
                movl 16(%rsp), %eax
                bswapl %eax
                movl %eax, 16(%r12)
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_db_mysql_scramble(rdi=out20, rsi=seed, rdx=seedlen, rcx=pass KofString)
            .globl kof_db_mysql_scramble
            .type kof_db_mysql_scramble, @function
            kof_db_mysql_scramble:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $128, %rsp
                movq %rdi, %r12          # out
                movq %rsi, %r13          # seed
                movq %rdx, %r14          # seedlen
                movq %rcx, %r15          # pass
                # stage1 = SHA1(pass)
                leaq 96(%rsp), %rdi
                leaq 24(%r15), %rsi
                movslq 16(%r15), %rdx
                call kof_sec_sha1_internal
                # stage2 = SHA1(stage1)
                leaq 76(%rsp), %rdi
                leaq 96(%rsp), %rsi
                movl $20, %edx
                call kof_sec_sha1_internal
                # stage3 = SHA1(seed + stage2) → 56(%rsp)
                leaq 56(%rsp), %rdi
                leaq 36(%rsp), %rsi
                # copia seed para 36(%rsp)
                xorq %rcx, %rcx
            .Lscr_copy_seed:
                cmpq %r14, %rcx
                jge .Lscr_copy_seed_done
                movb (%r13,%rcx), %al
                movb %al, 36(%rsp,%rcx)
                incq %rcx
                jmp .Lscr_copy_seed
            .Lscr_copy_seed_done:
                movq %r14, %r8
                xorl %ecx, %ecx
            .Lscr_copy_st2:
                cmpl $20, %ecx
                jge .Lscr_copy_st2_done
                movb 76(%rsp,%rcx), %al
                movb %al, 36(%rsp,%r8)
                incq %rcx
                incq %r8
                jmp .Lscr_copy_st2
            .Lscr_copy_st2_done:
                leaq 36(%rsp), %rsi
                movq %r8, %rdx
                call kof_sec_sha1_internal
                # result = stage1 XOR stage3
                xorl %ecx, %ecx
            .Lscr_xor:
                cmpl $20, %ecx
                jge .Lscr_xor_done
                movb 96(%rsp,%rcx), %al
                xorb 56(%rsp,%rcx), %al
                movb %al, (%r12,%rcx)
                incq %rcx
                jmp .Lscr_xor
            .Lscr_xor_done:
                addq $128, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_db_mysql_lenenc: rsi=buf → eax=valor, rsi=proxima pos
            kof_db_mysql_lenenc:
                movzbl (%rsi), %eax
                cmpb $0xFC, %al
                je .Ldb_len_2
                cmpb $0xFD, %al
                je .Ldb_len_3
                incq %rsi
                ret
            .Ldb_len_2:
                movzwl 1(%rsi), %eax
                addq $3, %rsi
                ret
            .Ldb_len_3:
                movzbl 1(%rsi), %eax
                movzbl 2(%rsi), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl 3(%rsi), %edx
                shll $16, %edx
                orl %edx, %eax
                addq $4, %rsi
                ret

            # kof_db_mysql_render(rdi=value) → rax: literal SQL p/ bind.
            # Int (rdx<0x1000000) -> decimais; String (rdi>=0x1000000) -> 'escaped'
            # (MySQL: ' -> '' e \\ -> \\\\).
            .globl kof_db_mysql_render
            .type kof_db_mysql_render, @function
            kof_db_mysql_render:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $4096, %rsp
                cmpq $0x1000000, %rdi
                jb .Ldb_rnd_int
            .Ldb_rnd_str:
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leaq 24(%rbx), %rsi
                movq %rsp, %r13               # scratch start
                movq %rsp, %r14              # out cursor
                xorl %ecx, %ecx
            .Ldb_rnd_esc:
                cmpl %r12d, %ecx
                jge .Ldb_rnd_esc_done
                movzbl (%rsi,%rcx), %eax
                cmpb $'\'', %al
                je .Ldb_rnd_dbl
                cmpb $'\\', %al
                je .Ldb_rnd_dbl
                movb %al, (%r14)
                incq %r14
            .Ldb_rnd_next:
                incl %ecx
                jmp .Ldb_rnd_esc
            .Ldb_rnd_dbl:
                movb %al, (%r14)
                incq %r14
                movb %al, (%r14)
                incq %r14
                jmp .Ldb_rnd_next
            .Ldb_rnd_esc_done:
                movq %r14, %r15
                subq %r13, %r15                  # escaped len
                leal 2(%r15), %r12d              # novo len (com aspas)
                leal 25(%r12d), %edi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl %r12d, 16(%rbx)
                movl $0, 20(%rbx)
                movb $39, 24(%rbx)               # ' (abre)
                leaq 25(%rbx), %rdi
                movq %r13, %rsi
                movq %r15, %rdx
                call kof_memcpy
                leaq 23(%rbx,%r12), %rdi         # fecha em data[len-1] = 24+len-1
                movb $39, (%rdi)                 # ' (fecha)
                incq %rdi
                movb $0, (%rdi)                  # NUL em 24+len
                movq %rbx, %rax
                jmp .Ldb_rnd_done
            .Ldb_rnd_int:
                movl %edi, %edi
                call kof_int_to_string
                movq %rax, %rbx
            .Ldb_rnd_done:
                addq $4096, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_mysql_replace_q(rdi=sql, rsi=literal) → rax: troca o 1º '?'
            # por literal (buffer bruto). Sem '?': devolve sql inalterado.
            .globl kof_db_mysql_replace_q
            .type kof_db_mysql_replace_q, @function
            kof_db_mysql_replace_q:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $4096, %rsp
                movq %rdi, %rbx                 # sql
                movq %rsi, %r12                 # literal
                movl 16(%rbx), %r13d            # sql len
                leaq 24(%rbx), %r14             # sql data
                # acha o 1º '?'
                xorl %ecx, %ecx
            .Ldb_rq_scan:
                cmpl %r13d, %ecx
                jge .Ldb_rq_none
                cmpb $'?', (%r14,%rcx)
                jne .Ldb_rq_adv
                jmp .Ldb_rq_found
            .Ldb_rq_adv:
                incl %ecx
                jmp .Ldb_rq_scan
            .Ldb_rq_none:
                movq %rbx, %rax
                jmp .Ldb_rq_done
            .Ldb_rq_found:
                movl %ecx, %r15d                # idx
                movq %rsp, %r8                   # out
                # copia sql[0..idx)
                xorl %esi, %esi
            .Ldb_rq_c1:
                cmpl %r15d, %esi
                jge .Ldb_rq_c1_done
                movzbl (%r14,%rsi), %eax
                movb %al, (%r8)
                incq %r8
                incl %esi
                jmp .Ldb_rq_c1
            .Ldb_rq_c1_done:
                # copia literal (forward)
                xorl %esi, %esi
            .Ldb_rq_c2:
                cmpl 16(%r12), %esi
                jge .Ldb_rq_c2_done
                movzbl 24(%r12,%rsi), %eax
                movb %al, (%r8)
                incq %r8
                incl %esi
                jmp .Ldb_rq_c2
            .Ldb_rq_c2_done:
                # copia sql[idx+1..end)
                leal 1(%r15d), %esi
            .Ldb_rq_c3:
                cmpl %r13d, %esi
                jge .Ldb_rq_c3_done
                movzbl (%r14,%rsi), %eax
                movb %al, (%r8)
                incq %r8
                incl %esi
                jmp .Ldb_rq_c3
            .Ldb_rq_c3_done:
                movq %r8, %r13
                movq %rsp, %r14
                subq %r14, %r13                 # novo len
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl %r13d, 16(%rbx)
                movl $0, 20(%rbx)
                leaq 24(%rbx), %rdi
                movq %rsp, %rsi
                movq %r13, %rdx
                call kof_memcpy
                movq %rbx, %rax
            .Ldb_rq_done:
                addq $4096, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_mysql_reset(rdi=fd): zera o estado do reader de pacotes.
            .globl kof_db_mysql_reset
            .type kof_db_mysql_reset, @function
            kof_db_mysql_reset:
                movq %rdi, .Ldb_mysql_fd(%rip)
                leaq .Ldb_mysql_buf(%rip), %rax
                movq %rax, .Ldb_mysql_ppos(%rip)
                movq %rax, .Ldb_mysql_pend(%rip)
                ret

            # kof_db_mysql_next: lê o PRÓXIMO pacote do stream (buf interno).
            # rsi = ponteiro do payload (buf+4 do pacote), rax = len do payload.
            # rax = 0 em fim de stream / erro. Clobbers rax rsi rdx rcx r8 r9
            # r10 r11 (leaf: sem call interno além de kof_net_read).
            .globl kof_db_mysql_next
            .type kof_db_mysql_next, @function
            kof_db_mysql_next:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq .Ldb_mysql_ppos(%rip), %r12   # start do pacote não lido
                movq .Ldb_mysql_pend(%rip), %rbx   # fim dos dados válidos
                cmpq %rbx, %r12
                jb .Ldb_mynxt_extract
            .Ldb_mynxt_read:
                movq .Ldb_mysql_fd(%rip), %rdi
                leaq .Ldb_mysql_buf(%rip), %rsi
                movl $16384, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_mynxt_fail
                leaq .Ldb_mysql_buf(%rip), %r12            # ppos = inicio do buf
                leaq .Ldb_mysql_buf(%rip), %rbx
                addq %rax, %rbx                             # pend = buf + rlen
                movq %rbx, .Ldb_mysql_pend(%rip)
                jmp .Ldb_mynxt_extract
            .Ldb_mynxt_extract:
                movzbl (%r12), %eax
                movzbl 1(%r12), %ecx
                shll $8, %ecx
                orl %ecx, %eax
                movzbl 2(%r12), %ecx
                shll $16, %ecx
                orl %ecx, %eax
                cmpl $0xFFFFFF, %eax
                je .Ldb_mynxt_fail                 # chunk de 16MB — fora de escopo
                leaq 4(%r12), %rsi                 # payload
                leaq .Ldb_mysql_buf(%rip), %r13
                addq $16384, %r13                  # buf end
                addq $4, %r12                      # pula o header
                addq %rax, %r12                    # + payload
                cmpq %r13, %r12
                ja .Ldb_mynxt_fail
                movq %r12, .Ldb_mysql_ppos(%rip)   # próximo não lido
                # return: rsi=payload, rax=len
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ldb_mynxt_fail:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # resolve "db<N>" → sqlite3* em rax (leaf: clobbera rsi/rdx/rax/rcx)
            kof_db_resolve:
                testq %rdi, %rdi
                jz .Ldb_res_null
                leaq 26(%rdi), %rsi
                xorl %ecx, %ecx
            .Ldb_res_parse:
                movzbl (%rsi), %edx
                testb %dl, %dl
                je .Ldb_res_done
                subl $'0', %edx
                imull $10, %ecx, %ecx
                addl %edx, %ecx
                incq %rsi
                jmp .Ldb_res_parse
            .Ldb_res_done:
                decl %ecx
                movq .Ldb_slots(,%rcx,8), %rax
                ret
            .Ldb_res_null:
                xorl %eax, %eax
                ret

            # kof_db_type(id) → eax: 1=sqlite 2=mysql 3=oracle 4=mongo
            kof_db_type:
                testq %rdi, %rdi
                jz .Ldb_typ_null
                leaq 26(%rdi), %rsi
                xorl %ecx, %ecx
            .Ldb_typ_parse:
                movzbl (%rsi), %edx
                testb %dl, %dl
                je .Ldb_typ_done
                subl $'0', %edx
                imull $10, %ecx, %ecx
                addl %edx, %ecx
                incq %rsi
                jmp .Ldb_typ_parse
            .Ldb_typ_done:
                decl %ecx
                movzbl .Ldb_types(,%rcx,1), %eax
                ret
            .Ldb_typ_null:
                xorl %eax, %eax
                ret

            # kof_db_connect(url) — sqlite: ou mysql:// (user/pass = NULL)
            .globl kof_db_connect
            .type kof_db_connect, @function
            kof_db_connect:
                xorq %r14, %r14
                xorq %r15, %r15
                jmp kof_db_connect_inner

            # kof_db_connect2(url, user, pass)
            .globl kof_db_connect2
            .type kof_db_connect2, @function
            kof_db_connect2:
                movq %rsi, %r14
                movq %rdx, %r15

            kof_db_connect_inner:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                cmpb $'s', 24(%rbx)
                jne .Ldb_conn_maybe_mysql
                cmpb $'q', 25(%rbx)
                jne .Ldb_connect_bad
                cmpb $'l', 26(%rbx)
                jne .Ldb_connect_bad
                cmpb $'i', 27(%rbx)
                jne .Ldb_connect_bad
                cmpb $'t', 28(%rbx)
                jne .Ldb_connect_bad
                cmpb $'e', 29(%rbx)
                jne .Ldb_connect_bad
                cmpb $':', 30(%rbx)
                jne .Ldb_connect_bad
                leaq 31(%rbx), %rdi
                subq $40, %rsp
                movq %rsp, %rsi
                call sqlite3_open
                testl %eax, %eax
                jne .Ldb_connect_fail
                movq (%rsp), %r12
                addq $40, %rsp
                movl $1, %eax
                jmp .Ldb_connect_register
            .Ldb_conn_maybe_mysql:
                cmpb $'m', 24(%rbx)
                jne .Ldb_connect_bad
                cmpb $'y', 25(%rbx)
                jne .Ldb_connect_bad
                cmpb $'s', 26(%rbx)
                jne .Ldb_connect_bad
                cmpb $'q', 27(%rbx)
                jne .Ldb_connect_bad
                cmpb $'l', 28(%rbx)
                jne .Ldb_connect_bad
                cmpb $':', 29(%rbx)
                jne .Ldb_connect_bad
                cmpb $'/', 30(%rbx)
                jne .Ldb_connect_bad
                cmpb $'/', 31(%rbx)
                jne .Ldb_connect_bad
                # mysql:// — parse [user[:pass]@]host[:port][/db] (also supports mysql://host:port/db)
                # Simple host:port/db parsing from after "mysql://"
                # If URL contains '@', treat host as after last '@' (fallback)
                leaq 24(%rbx), %r12
                leaq 8(%r12), %rsi
                movq %rsi, %r10
                xorq %r11, %r11
                movq %rsi, %rdx
            .Ldb_find_at_simple:
                movzbl (%rdx), %eax
                testb %al, %al
                jz .Ldb_at_simple_done
                cmpb $'@', %al
                jne .Ldb_at_simple_next
                movq %rdx, %r11
            .Ldb_at_simple_next:
                cmpb $'/', %al
                je .Ldb_at_simple_done
                incq %rdx
                jmp .Ldb_find_at_simple
            .Ldb_at_simple_done:
                testq %r11, %r11
                jz .Ldb_no_at_simple
                # user:pass@ — extrai userinfo e monta KofStrings (r14=user, r15=pass)
                leaq 8(%r12), %rdi
                subq $32, %rsp
                movq %rdi, 0(%rsp)       # u0
                leaq (%r11), %rdx        # len userinfo = @ - u0
                subq %rdi, %rdx
                movq %rdx, 24(%rsp)
                xorl %r8d, %r8d          # colon (0 se ausente)
            .Ldb_up_find_colon:
                cmpq %rdx, %r8
                jge .Ldb_up_find_colon_done
                cmpb $':', 0(%rdi,%r8)
                je .Ldb_up_find_colon_done
                incq %r8
                jmp .Ldb_up_find_colon
            .Ldb_up_find_colon_done:
                movq %r8, 8(%rsp)        # colon offset (0 se ausente)
                movq %r11, 16(%rsp)      # '@' (kof_alloc faz syscall: recarregar depois)
                # NUL no fim do user (muta o buffer da URL in-place)
                testq %r8, %r8
                jz .Ldb_up_term_at
                movb $0, (%rdi,%r8)
                jmp .Ldb_up_term_done
            .Ldb_up_term_at:
                movb $0, (%r11)
            .Ldb_up_term_done:
                # pass = [colon+1, @) -> r15
                testq %r8, %r8
                jz .Ldb_up_nopass
                leaq 1(%rdi,%r8), %rsi   # colon+1 (ptr real)
                movq %r11, %rdx
                subq %rsi, %rdx
                jle .Ldb_up_nopass
                leal 25(%rdx), %edi
                call kof_alloc
                movl $1, 0(%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movq 0(%rsp), %rsi
                movq 8(%rsp), %r8
                leaq 1(%rsi,%r8), %rsi   # colon+1
                movq 16(%rsp), %rdx
                subq %rsi, %rdx
                movl %edx, 16(%rax)
                movl $0, 20(%rax)
                xorl %ecx, %ecx
            .Ldb_up_pcopy:
                cmpl %edx, %ecx
                jge .Ldb_up_pcopy_done
                movzbl (%rsi,%rcx), %edi
                movb %dil, 24(%rax,%rcx)
                incl %ecx
                jmp .Ldb_up_pcopy
            .Ldb_up_pcopy_done:
                movb $0, 24(%rax,%rdx)
                movq %rax, %r15
            .Ldb_up_nopass:
                # user = [u0, colon ou @) -> r14
                movq 0(%rsp), %rsi
                movq 8(%rsp), %rdx
                testq %rdx, %rdx
                jz .Ldb_up_nocolon
                # com colon: len = colon; rdx precisa ser u0+colon p/ o subq abaixo
                addq %rsi, %rdx
                jmp .Ldb_up_ulen
            .Ldb_up_nocolon:
                movq %r11, %rdx            # '@' absoluto
            .Ldb_up_ulen:
                subq %rsi, %rdx
                jle .Ldb_up_nouser
                leal 25(%rdx), %edi
                movq %rdx, 24(%rsp)      # salva len (kof_alloc clobbera edx)
                call kof_alloc
                movq 24(%rsp), %rdx
                movl $1, 0(%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movq 0(%rsp), %rsi
                movl %edx, 16(%rax)
                movl $0, 20(%rax)
                xorl %ecx, %ecx
            .Ldb_up_ucopy:
                cmpl %edx, %ecx
                jge .Ldb_up_ucopy_done
                movzbl (%rsi,%rcx), %edi
                movb %dil, 24(%rax,%rcx)
                incl %ecx
                jmp .Ldb_up_ucopy
            .Ldb_up_ucopy_done:
                movb $0, 24(%rax,%rdx)
                movq %rax, %r14
            .Ldb_up_nouser:
                movq 16(%rsp), %r11   # syscall clobberou r11
                addq $32, %rsp
                leaq 1(%r11), %r10
                jmp .Ldb_no_at
            .Ldb_no_at_simple:
            .Ldb_no_at:
                movq %r10, %rsi
                xorl %r10d, %r10d
                xorq %r13, %r13
            .Ldb_mysql_host2:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Ldb_mysql_open
                cmpb $':', %al
                je .Ldb_mysql_colon2
                cmpb $'/', %al
                je .Ldb_mysql_slash2
                incq %rsi
                jmp .Ldb_mysql_host2
            .Ldb_mysql_colon2:
                movb $0, (%rsi)
                incq %rsi
            .Ldb_mysql_port2:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Ldb_mysql_open
                cmpb $'/', %al
                je .Ldb_mysql_slash2
                subl $'0', %eax
                imull $10, %r10d, %r10d
                addl %eax, %r10d
                incq %rsi
                jmp .Ldb_mysql_port2
            .Ldb_mysql_slash2:
                movb $0, (%rsi)
                incq %rsi
                movq %rsi, %r13
            .Ldb_mysql_open:
                testl %r10d, %r10d
                jnz .Ldb_mysql_port_ok
                movl $3306, %r10d
            .Ldb_mysql_port_ok:
                subq $16, %rsp
                movl %r10d, 8(%rsp)
                movq %r11, 0(%rsp)
                movl $2, %edi
                movl $1, %esi
                xorl %edx, %edx
                call kof_net_socket
                movq 0(%rsp), %r11
                movl 8(%rsp), %r10d
                addq $16, %rsp
                testq %rax, %rax
                js .Ldb_connect_bad
                movq %rax, %rbx
                leaq -48(%rsp), %r8
                subq $48, %rsp
                movw $2, (%r8)
                movl %r10d, %eax
                xchgb %al, %ah
                movw %ax, 2(%r8)
                testq %r11, %r11
                jz .Ldb_host_orig
                leaq 1(%r11), %rsi
                jmp .Ldb_host_ip
            .Ldb_host_orig:
                leaq 8(%r12), %rsi
            .Ldb_host_ip:
                movzbl (%rsi), %eax
                cmpb $'0', %al
                jb .Ldb_ip_fallback
                cmpb $'9', %al
                ja .Ldb_ip_fallback
                xorl %r9d, %r9d
                xorl %ecx, %ecx
                jmp .Ldb_ip
            .Ldb_ip_fallback:
                movb $127, 4(%r8)
                movb $0, 5(%r8)
                movb $0, 6(%r8)
                movb $1, 7(%r8)
                jmp .Ldb_ip_done
            .Ldb_ip:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Ldb_ip_last
                cmpb $'.', %al
                je .Ldb_ip_store
                subl $'0', %eax
                imull $10, %ecx, %ecx
                addl %eax, %ecx
                incq %rsi
                jmp .Ldb_ip
            .Ldb_ip_store:
                movb %cl, 4(%r8,%r9,1)
                incq %r9
                xorl %ecx, %ecx
                incq %rsi
                jmp .Ldb_ip
            .Ldb_ip_last:
                movb %cl, 4(%r8,%r9,1)
            .Ldb_ip_done:
                movq %rbx, %rdi
                movq %r8, %rsi
                movl $16, %edx
                movq $42, %rax
                syscall
                testq %rax, %rax
                js .Ldb_connect_bad
                addq $48, %rsp
                # handshake: read server greeting via kof_net_read
                leaq .Ldb_mysql_buf(%rip), %r12
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_connect_bad
                cmpb $0x0A, 4(%r12)
                jne .Ldb_connect_bad
                # --- Parse greeting seed (20 bytes) into .Ldb_mysql_names ---
                leaq 4(%r12), %rsi
                incq %rsi
            .Ldb_skip_ver:
                movzbl (%rsi), %eax
                testb %al, %al
                je .Ldb_skip_ver_done
                incq %rsi
                jmp .Ldb_skip_ver
            .Ldb_skip_ver_done:
                incq %rsi
                addq $4, %rsi
                leaq .Ldb_mysql_names(%rip), %rdi
                xorl %ecx, %ecx
            .Ldb_copy_seed8:
                cmpl $8, %ecx
                jge .Ldb_seed8_done
                movb (%rsi,%rcx), %al
                movb %al, (%rdi,%rcx)
                incq %rcx
                jmp .Ldb_copy_seed8
            .Ldb_seed8_done:
                addq $8, %rsi
                addq $19, %rsi
                xorl %ecx, %ecx
            .Ldb_copy_seed12:
                cmpl $12, %ecx
                jge .Ldb_seed12_done
                movb (%rsi,%rcx), %al
                testb %al, %al
                je .Ldb_seed12_done
                movb %al, 8(%rdi,%rcx)
                incq %rcx
                jmp .Ldb_copy_seed12
            .Ldb_seed12_done:
                testq %r15, %r15
                jz .Ldb_no_scramble_needed
                cmpl $0, 16(%r15)
                je .Ldb_no_scramble_needed
                leaq .Ldb_mysql_names+32(%rip), %rdi
                leaq .Ldb_mysql_names(%rip), %rsi
                movl $20, %edx
                movq %r15, %rcx
                call kof_db_mysql_scramble
                jmp .Ldb_scramble_done
            .Ldb_no_scramble_needed:
                # no scramble needed
                nop
            .Ldb_scramble_done:
                leaq .Ldb_mysql_buf(%rip), %r8
                movl $0x00088209, 4(%r8)   # +0x0008 CLIENT_CONNECT_WITH_DB (db no auth)
                movl $0x01000000, 8(%r8)
                movb $0x21, 12(%r8)
                leaq 13(%r8), %rdi
                xorl %ecx, %ecx
            .Ldb_auth_zero2:
                cmpl $23, %ecx
                jge .Ldb_auth_zero_done2
                movb $0, (%rdi,%rcx)
                incq %rcx
                jmp .Ldb_auth_zero2
            .Ldb_auth_zero_done2:
                leaq 36(%r8), %rdi
                testq %r14, %r14
                jz .Ldb_auth_user_empty2
                leaq 24(%r14), %rsi
                movl 16(%r14), %ecx
                movq %rcx, %rdx
                call kof_memcpy
                leaq 36(%r8), %rdi
                addq %rcx, %rdi
                jmp .Ldb_auth_user_end2
            .Ldb_auth_user_empty2:
                movq %rdi, %rsi
                movq %rsi, %rdi
            .Ldb_auth_user_end2:
                movb $0, (%rdi)
                incq %rdi
                testq %r15, %r15
                jz .Ldb_auth_no_pass
                cmpl $0, 16(%r15)
                je .Ldb_auth_no_pass
                movb $20, (%rdi)
                incq %rdi
                leaq .Ldb_mysql_names+32(%rip), %rsi
                xorl %ecx, %ecx
            .Ldb_copy_scramble_first:
                cmpl $20, %ecx
                jge .Ldb_scramble_copied
                movb (%rsi,%rcx), %al
                movb %al, (%rdi,%rcx)
                incq %rcx
                jmp .Ldb_copy_scramble_first
            .Ldb_scramble_copied:
                addq $20, %rdi
                jmp .Ldb_auth_db2
            .Ldb_auth_no_pass:
                movb $0, (%rdi)
                incq %rdi
            .Ldb_auth_db2:
                testq %r13, %r13
                jz .Ldb_auth_db_empty
                movq %r13, %rax
                jmp .Ldb_auth_db_copy
            .Ldb_auth_db_empty:
                leaq .Ldb_mysql_empty(%rip), %rax
            .Ldb_auth_db_copy:
            .Ldb_auth_db_loop:
                movzbl (%rax), %ecx
                movb %cl, (%rdi)
                testb %cl, %cl
                je .Ldb_auth_db_done
                incq %rax
                incq %rdi
                jmp .Ldb_auth_db_loop
            .Ldb_auth_db_done:
                incq %rdi
                leaq .Ldb_mysql_plugin(%rip), %rsi
            .Ldb_auth_plug_loop:
                movzbl (%rsi), %ecx
                movb %cl, (%rdi)
                testb %cl, %cl
                je .Ldb_auth_plug_done
                incq %rsi
                incq %rdi
                jmp .Ldb_auth_plug_loop
            .Ldb_auth_plug_done:
                incq %rdi
                # header: len = rdi - (buf+4), seq 1
                leaq .Ldb_mysql_buf(%rip), %r12
                subq %r12, %rdi
                subq $4, %rdi
                movl %edi, %eax
                movb %al, 0(%r12)
                shrl $8, %eax
                movb %al, 1(%r12)
                shrl $8, %eax
                movb %al, 2(%r12)
                movb $1, 3(%r12)
                leaq 4(%rdi), %rdx
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_net_write
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_connect_bad
                cmpb $0xFE, 4(%r12)
                jne .Ldb_auth_done
                # AuthSwitchRequest: [0xFE][plugin NUL][seed...]
                # acha o seed após o plugin; seedlen = len - offset
                leaq 5(%r12), %rsi
            .Ldb_switch_find_plugin_end:
                movzbl (%rsi), %eax
                testb %al, %al
                je .Ldb_switch_plugin_end
                incq %rsi
                jmp .Ldb_switch_find_plugin_end
            .Ldb_switch_plugin_end:
                incq %rsi
                movq %rsi, %r13          # seed
                # len do pacote (3 bytes LE) = header len
                movzbl 0(%r12), %eax
                movzbl 1(%r12), %ecx
                shll $8, %ecx
                orl %ecx, %eax
                movzbl 2(%r12), %ecx
                shll $16, %ecx
                orl %ecx, %eax
                subq %r12, %rsi
                subq $4, %rsi
                subl %esi, %eax          # seedlen = pacote - offset (should be 21, but use 20 without terminator)
                movl $20, %edx
                movq %r13, %rsi
                # sem pass: responde vazio
                testq %r15, %r15
                jz .Ldb_switch_no_scramble2
                # out do scramble em .Ldb_mysql_names+32; seed NOVA do switch em r13
                leaq .Ldb_mysql_names+32(%rip), %rdi
                movq %r13, %rsi
                movl $20, %edx
                movq %r15, %rcx
                call kof_db_mysql_scramble
                jmp .Ldb_switch_scramble_done2
            .Ldb_switch_no_scramble2:
                leaq .Ldb_mysql_names+32(%rip), %rdi
                movl $0, 0(%rdi)
            .Ldb_switch_scramble_done2:
                # AuthSwitchResponse: 20-byte scramble (sem plugin name), seq 3
                leaq .Ldb_mysql_buf(%rip), %r8
                leaq .Ldb_mysql_names+32(%rip), %rsi
                leaq 4(%r8), %rdi
                xorl %ecx, %ecx
            .Ldb_switch_copy_scramble2:
                cmpl $20, %ecx
                jge .Ldb_switch_copy_done2
                movb (%rsi,%rcx), %al
                movb %al, (%rdi,%rcx)
                incq %rcx
                jmp .Ldb_switch_copy_scramble2
            .Ldb_switch_copy_done2:
                movb $20, 0(%r8)
                movb $0, 1(%r8)
                movb $0, 2(%r8)
                movb $3, 3(%r8)
                movq %rbx, %rdi
                movq %r8, %rsi
                movq $24, %rdx
                call kof_net_write
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_connect_bad
            .Ldb_auth_done:
                cmpb $0xFF, 4(%r12)
                je .Ldb_connect_bad
                movb $4, .Ldb_mysql_seq(%rip)
                movq %rbx, %r12
                movl $2, %eax
            .Ldb_connect_register:
                movq .Ldb_count(%rip), %r13
                cmpq $63, %r13
                jge .Ldb_connect_bad
                movq %r12, .Ldb_slots(,%r13,8)
                movb %al, .Ldb_types(,%r13,1)
                incq %r13
                movq %r13, .Ldb_count(%rip)
                # handle = "db" + decimal(r13) em buffer de 48 bytes
                leaq -96(%rsp), %r14
                movq %r13, %rax
                leaq 47(%r14), %rcx
                movb $0, (%rcx)
                decq %rcx
                movq $10, %rbx
            .Ldb_itoa:
                xorl %edx, %edx
                divq %rbx
                addb $'0', %dl
                movb %dl, (%rcx)
                testq %rax, %rax
                je .Ldb_itoa_done
                decq %rcx
                jmp .Ldb_itoa
            .Ldb_itoa_done:
                decq %rcx
                movb $'b', (%rcx)
                decq %rcx
                movb $'d', (%rcx)
                # handle KofString na mao: alloc + header + copia inline
                movq %rcx, %rbx
                leaq 48(%r14), %rdx
                subq %rcx, %rdx
                decq %rdx
                movq %rdx, %rsi
                movq %rsi, %r13
                leal 25(%rsi), %edi
                call kof_alloc
                movq %rax, %r12
                movl $1, 0(%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                movl %r13d, 16(%r12)
                movl $0, 20(%r12)
                xorl %ecx, %ecx
            .Ldb_handle_copy:
                cmpl %r13d, %ecx
                jge .Ldb_handle_copy_done
                movzbl (%rbx,%rcx), %eax
                movb %al, 24(%r12,%rcx)
                incq %rcx
                jmp .Ldb_handle_copy
            .Ldb_handle_copy_done:
                movb $0, 24(%r12,%r13)
                movq %r12, .Ldb_default_handle(%rip)   # conexao atual = default (tx)
                movq %r12, %rax
                addq $96, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ldb_connect_fail:
                addq $40, %rsp
            .Ldb_connect_bad:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_close(id: KofString) — handles both sqlite and mysql fd
            # (2 pushes apos andq: call em rsp≡0 — SSE do sqlite3_close exige)
            .globl kof_db_close
            .type kof_db_close, @function
            kof_db_close:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                call kof_db_type
                cmpl $2, %eax
                je .Ldb_close_mysql
                movq %rbx, %rdi
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_close_ret
                movq %rax, %rdi
                call sqlite3_close
                jmp .Ldb_close_ret
            .Ldb_close_mysql:
                movq %rbx, %rdi
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_close_ret
                movq %rax, %rdi
                call kof_net_close
            .Ldb_close_ret:
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_transaction(task) — BEGIN; invoca a lambda; COMMIT (ou
            # ROLLBACK + re-throw). Reusa kof_db_execute (sqlite3_exec / MySQL
            # COM_QUERY) e o EH (kf_throw_string chega no handler com %rdi=exceção
            # e a chain apontando p/ o try externo). Conexão: a última aberta
            # (.Ldb_default_handle), paridade com KOF_DB_DEFAULT no JVM.
            .globl kof_db_transaction
            .type kof_db_transaction, @function
            kof_db_transaction:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r14
                movq %rdi, %rbx                    # task (lambda)
                movq .Ldb_default_handle(%rip), %r12
                testq %r12, %r12
                jz .Ltx_begin_done
                movq %r12, %rdi
                leaq .Ldb_begin_str(%rip), %rsi
                call kof_db_execute
            .Ltx_begin_done:
                # ── try start (mesmo layout de KofTryStart no NativeBackend) ──
                subq $32, %rsp
                leaq .Ltx_rollback(%rip), %rax
                movq %rax, 0(%rsp)
                movq %rsp, 8(%rsp)
                movq %rbp, 16(%rsp)
                movq kof_exc_chain(%rip), %rcx
                movq %rcx, 24(%rsp)
                movq %rsp, kof_exc_chain(%rip)
                # invoca a lambda (vtable[0] = invoke); rdi = this (a lambda,
                # onde ficam as capturas) — mesmo padrão do sched_trampoline.
                movq %rbx, %rdi
                movq 8(%rbx), %rax
                movq (%rax), %rax
                call *%rax
                # ── try end / commit ── (re-carrega o handle do BSS: a lambda
                # pode ter clobberado r12)
                movq 24(%rsp), %rcx
                movq %rcx, kof_exc_chain(%rip)
                addq $32, %rsp
                movq .Ldb_default_handle(%rip), %r12
                testq %r12, %r12
                jz .Ltx_done
                movq %r12, %rdi
                leaq .Ldb_commit_str(%rip), %rsi
                call kof_db_execute
            .Ltx_done:
                popq %r14
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ltx_rollback:
                movq %rdi, %r14                    # preserva a exceção
                addq $32, %rsp                     # desfaz o subq do try
                movq .Ldb_default_handle(%rip), %r12   # re-carrega (lambda clobberou)
                testq %r12, %r12
                jz .Ltx_rethrow
                movq %r12, %rdi
                leaq .Ldb_rollback_str(%rip), %rsi
                call kof_db_execute
            .Ltx_rethrow:
                movq %r14, %rdi
                call kof_throw_string

            # kof_db_execute(id, sql) → sqlite3_exec
            .globl kof_db_execute
            .type kof_db_execute, @function
            kof_db_execute:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rsi, %rbx
                movq %rdi, %r12
                testq %r12, %r12
                jz .Ldb_exec0_bad
                call kof_db_type
                testl %eax, %eax
                jz .Ldb_exec0_bad
                cmpl $1, %eax
                je .Ldb_exec0_sqlite
                cmpl $2, %eax
                je .Ldb_exec0_mysql
                jmp .Ldb_exec0_bad
            .Ldb_exec0_sqlite:
                movq %r12, %rdi
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_exec0_bad
                movq %rax, %r12
                movq %r12, %rdi
                leaq 24(%rbx), %rsi
                xorl %edx, %edx
                xorl %ecx, %ecx
                xorl %r8d, %r8d
                subq $8, %rsp
                call sqlite3_exec
                addq $8, %rsp
                movl %eax, %eax
                jmp .Ldb_exec0_done
            .Ldb_exec0_mysql:
                movq %r12, %rdi
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_exec0_bad
                movq %rax, %r12
                # COM_QUERY no fd
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%rbx), %rsi
                movl 16(%rbx), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb $0, 3(%r13)
                movq %r12, %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq %r12, %rdi
                movq %r13, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_exec0_bad
                cmpb $0xFF, 4(%r13)
                je .Ldb_exec0_bad
                cmpb $0x00, 4(%r13)
                jne .Ldb_exec0_bad
                movzbl 5(%r13), %eax
                cmpb $0xFC, %al
                je .Ldb_exec0_afc
                cmpb $0xFD, %al
                je .Ldb_exec0_afd
                jmp .Ldb_exec0_done
            .Ldb_exec0_afc:
                movzwl 6(%r13), %eax
                jmp .Ldb_exec0_done
            .Ldb_exec0_afd:
                movzbl 6(%r13), %eax
                movzbl 7(%r13), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl 8(%r13), %edx
                shll $16, %edx
                orl %edx, %eax
            .Ldb_exec0_done:
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ldb_exec0_bad:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # bind helper: rdi=stmt, esi=index, rdx=valor cru (Int ou KofString*)
            kof_db_bind:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                cmpq $0x1000000, %rdx
                jae .Ldb_bind_str
                pushq %rbx
                movl %edx, %ebx
                subq $8, %rsp
                call sqlite3_bind_int
                addq $8, %rsp
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ldb_bind_str:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdx, %rbx
                movl %esi, %r12d
                movq %rdi, %r13
                movq %r13, %rdi
                movl %r12d, %esi
                leaq 24(%rbx), %rdx
                movq $-1, %rcx
                movq $-1, %r8
                subq $8, %rsp
                call sqlite3_bind_text
                addq $8, %rsp
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # Execute/query com binds: layout de pilha uniforme —
            #   6 pushes (48 bytes: rbx,r12,r13,r14,r15,rbp)
            #   +16 para &stmt (sempre), +8 extra para b4 (n>=4)
            #   rbx=id→, rbp=db, r12=sql, r13..r15=b1..b3, 16(%rsp)=b4
            .macro KOF_DB_EXEC_N n
            .globl kof_db_execute\\n
            .type kof_db_execute\\n, @function
            kof_db_execute\\n:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $40, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                .if \\n >= 1
                movq %rdx, %r13
                .endif
                .if \\n >= 2
                movq %rcx, %r14
                .endif
                .if \\n >= 3
                movq %r8, %r15
                .endif
                .if \\n >= 4
                movq %r9, 16(%rsp)
                .endif
                call kof_db_resolve
                movq %rax, 32(%rsp)
                movq %rbx, %rdi
                call kof_db_type
                cmpl $1, %eax
                je .Ldb_exec_sqlite\\n
                cmpl $2, %eax
                je .Ldb_exec_mysql\\n
                jmp .Ldb_exec_bad\\n
            .Ldb_exec_sqlite\\n:
                leaq 24(%r12), %rsi
                movq 32(%rsp), %rdi
                movq $-1, %rdx
                movq %rsp, %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq (%rsp), %r12
                .if \\n >= 1
                movq %r12, %rdi
                movl $1, %esi
                movq %r13, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 2
                movq %r12, %rdi
                movl $2, %esi
                movq %r14, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 3
                movq %r12, %rdi
                movl $3, %esi
                movq %r15, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 4
                movq %r12, %rdi
                movl $4, %esi
                movq 16(%rsp), %rdx
                call kof_db_bind
                .endif
                movq %r12, %rdi
                call sqlite3_step
                movq %r12, %rdi
                call sqlite3_finalize
                movq 32(%rsp), %rdi
                call sqlite3_changes
                jmp .Ldb_exec_done\\n
            .Ldb_exec_mysql\\n:
                .if \\n >= 1
                # binario: COM_STMT_PREPARE + COM_STMT_EXECUTE (fecha o gap prepared)
                # stash args
                leaq .Ldb_prep_args(%rip), %rax
                movq %r13, 0(%rax)
                .if \\n >= 2
                movq %r14, 8(%rax)
                .endif
                .if \\n >= 3
                movq %r15, 16(%rax)
                .endif
                .if \\n >= 4
                movq 16(%rsp), %rcx
                movq %rcx, 24(%rax)
                .endif
                movq 32(%rsp), %rdi
                movq %r12, %rsi
                call kof_db_mysql_prepare
                testl %eax, %eax
                jz .Ldb_exec_subst\\n
                movq 32(%rsp), %rdi
                movl %eax, %esi
                movl $\\n, %edx
                call kof_db_mysql_exec
                # reply: 1 packet OK/ERR — parser reuse
                movq 32(%rsp), %rdi
                leaq .Ldb_mysql_buf(%rip), %r13
                movq %r13, %rsi
                movl $16384, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_exec_bad\\n
                cmpb $0xFF, 4(%r13)
                je .Ldb_exec_bad\\n
                cmpb $0x00, 4(%r13)
                jne .Ldb_exec_bad\\n
                movzbl 5(%r13), %eax
                cmpb $0xFC, %al
                je .Ldb_exec_afc\\n
                cmpb $0xFD, %al
                je .Ldb_exec_afd\\n
                jmp .Ldb_exec_done\\n
            .Ldb_exec_subst\\n:
                # fallback: binds '?' -> literais (COM_QUERY não suporta ?)
                .endif
                .if \\n >= 1
                movq %r13, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 2
                movq %r14, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 3
                movq %r15, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 4
                movq 16(%rsp), %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                # COM_QUERY: [len 3][seq 0][0x03][sql]
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb $0, 3(%r13)
                movq 32(%rsp), %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq 32(%rsp), %rdi
                movq %r13, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_exec_bad\\n
                cmpb $0xFF, 4(%r13)
                je .Ldb_exec_bad\\n
                cmpb $0x00, 4(%r13)
                jne .Ldb_exec_bad\\n
                movzbl 5(%r13), %eax
                cmpb $0xFC, %al
                je .Ldb_exec_afc\\n
                cmpb $0xFD, %al
                je .Ldb_exec_afd\\n
                jmp .Ldb_exec_done\\n
            .Ldb_exec_afc\\n:
                movzwl 6(%r13), %eax
                jmp .Ldb_exec_done\\n
            .Ldb_exec_afd\\n:
                movzbl 6(%r13), %eax
                movzbl 7(%r13), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl 8(%r13), %edx
                shll $16, %edx
                orl %edx, %eax
                jmp .Ldb_exec_done\\n
            .Ldb_exec_bad\\n:
                xorl %eax, %eax
            .Ldb_exec_done\\n:
                addq $40, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .endm

            KOF_DB_EXEC_N 1
            KOF_DB_EXEC_N 2
            KOF_DB_EXEC_N 3
            KOF_DB_EXEC_N 4

            .macro KOF_DB_QUERY_N n
            .globl kof_db_query\\n
            .type kof_db_query\\n, @function
            kof_db_query\\n:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $40, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                .if \\n >= 1
                movq %rdx, %r13
                .endif
                .if \\n >= 2
                movq %rcx, %r14
                .endif
                .if \\n >= 3
                movq %r8, %r15
                .endif
                .if \\n >= 4
                movq %r9, 16(%rsp)
                .endif
                call kof_db_resolve
                movq %rax, 32(%rsp)
                movq %rbx, %rdi
                call kof_db_type
                cmpl $1, %eax
                je .Ldb_query_sqlite\\n
                cmpl $2, %eax
                je .Ldb_query_mysql\\n
                jmp .Ldb_query_bad\\n
            .Ldb_query_sqlite\\n:
                leaq 24(%r12), %rsi
                movq 32(%rsp), %rdi
                movq $-1, %rdx
                movq %rsp, %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq (%rsp), %r12
                .if \\n >= 1
                movq %r12, %rdi
                movl $1, %esi
                movq %r13, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 2
                movq %r12, %rdi
                movl $2, %esi
                movq %r14, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 3
                movq %r12, %rdi
                movl $3, %esi
                movq %r15, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 4
                movq %r12, %rdi
                movl $4, %esi
                movq 16(%rsp), %rdx
                call kof_db_bind
                .endif
                call kof_list_new
                movq %rax, %r14
            .Ldb_query_row\\n:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax
                jne .Ldb_query_sqlite_done\\n
                call kof_json_builder_new
                movq %rax, %r15
                movq %r15, %rdi
                movl $'{', %esi
                call kof_json_builder_char
                xorl %ebx, %ebx
            .Ldb_query_col\\n:
                movq %r12, %rdi
                call sqlite3_column_count
                cmpl %eax, %ebx
                jge .Ldb_query_end\\n
                testl %ebx, %ebx
                jz .Ldb_query_comma\\n
                movq %r15, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Ldb_query_comma\\n:
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_name
                movq %rax, 24(%rsp)
                movq 24(%rsp), %rdi
                call kof_io_strlen
                movq 24(%rsp), %rdi
                movq %rax, %rsi
                call kof_io_make_string
                movq %rax, %rdi
                call kof_json_encode_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq %r15, %rdi
                movl $58, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_type
                cmpl $1, %eax
                je .Ldb_query_int\\n
                cmpl $3, %eax
                je .Ldb_query_text\\n
                leaq .Ldb_null(%rip), %rdi
                xorl %esi, %esi
                call kof_io_make_string
                jmp .Ldb_query_val\\n
            .Ldb_query_int\\n:
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_int
                movl %eax, %edi
                call kof_json_encode_int
                jmp .Ldb_query_val\\n
            .Ldb_query_text\\n:
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_text
                movq %rax, 24(%rsp)
                movq 24(%rsp), %rdi
                call kof_io_strlen
                movq 24(%rsp), %rdi
                movq %rax, %rsi
                call kof_io_make_string
                movq %rax, %rdi
                call kof_json_encode_string
            .Ldb_query_val\\n:
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incl %ebx
                jmp .Ldb_query_col\\n
            .Ldb_query_end\\n:
                movq %r15, %rdi
                movl $'}', %esi
                call kof_json_builder_char
                movq %r15, %rdi
                call kof_json_builder_result
                movq %r14, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Ldb_query_row\\n
            .Ldb_query_sqlite_done\\n:
                movq %r12, %rdi
                call sqlite3_finalize
                movq %r14, %rax
                jmp .Ldb_query_done\\n
            .Ldb_query_mysql\\n:
                .if \\n >= 1
                # binario: PREPARE + EXECUTE + parse de linhas binarias
                leaq .Ldb_prep_args(%rip), %rax
                movq %r13, 0(%rax)
                .if \\n >= 2
                movq %r14, 8(%rax)
                .endif
                .if \\n >= 3
                movq %r15, 16(%rax)
                .endif
                .if \\n >= 4
                movq 16(%rsp), %rcx
                movq %rcx, 24(%rax)
                .endif
                movq 32(%rsp), %rdi
                movq %r12, %rsi
                call kof_db_mysql_prepare
                testl %eax, %eax
                jz .Ldb_query_subst\\n
                movq 32(%rsp), %rdi
                movl %eax, %esi
                movl $\\n, %edx
                call kof_db_mysql_prep_query
                movq %rax, %r14              # list (ou 0)
                testq %r14, %r14
                jz .Ldb_query_subst\\n
                movq %r14, %rax
                jmp .Ldb_query_done\\n
            .Ldb_query_subst\\n:
                movq %r13, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 2
                movq %r14, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 3
                movq %r15, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 4
                movq 16(%rsp), %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                # COM_QUERY + parse do resultset pacote a pacote
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb $0, 3(%r13)
                movq 32(%rsp), %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                # reader de pacotes: reset + 1º pacote
                movq 32(%rsp), %rdi
                call kof_db_mysql_reset
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Ldb_query_bad\\n
                movq %rsi, 24(%rsp)          # salva payload (kof_list_new clobbera rsi)
                cmpb $0xFF, (%rsi)
                je .Ldb_query_bad\\n
                call kof_list_new
                movq %rax, %r14
                movq 24(%rsp), %rsi          # restaura payload
                # col count (lenenc) do 1º pacote
                call kof_db_mysql_lenenc
                movl %eax, %r13d
                # column definitions: um pacote por coluna
                xorl %ebx, %ebx
            .Ldb_mysql_cols\\n:
                cmpl %r13d, %ebx
                jge .Ldb_mysql_cols_done\\n
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Ldb_query_bad\\n
                # pula cat, schema, table, org_table (4 lenenc: len + addq p/ dados)
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                # name = lenenc string: len em eax, dados em rsi
                call kof_db_mysql_lenenc
                movq %rsi, .Ldb_mysql_names(,%rbx,8)
                movl %eax, .Ldb_mysql_names+512(,%rbx,4)
                # pula org_name (lenenc)
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                incq %rbx
                jmp .Ldb_mysql_cols\\n
            .Ldb_mysql_cols_done\\n:
                # pacote apos colunas: 0x00 (OK, sem resultset) ou 0xFE (EOF)
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Ldb_query_done\\n
                cmpb $0x00, (%rsi)
                je .Ldb_query_mydone\\n
                jmp .Ldb_mysql_rows\\n
            .Ldb_mysql_rows\\n:
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Ldb_query_mydone\\n
                movzbl (%rsi), %eax
                cmpb $0xFF, %al
                je .Ldb_query_mydone\\n
                cmpb $0xFE, %al
                je .Ldb_query_mydone\\n
                # pacote de linha: monta o JSON object (cursor do pacote em 24(%rsp))
                movq %rsi, 24(%rsp)
                call kof_json_builder_new
                movq %rax, %r15
                movq %r15, %rdi
                movl $'{', %esi
                call kof_json_builder_char
                xorl %ebx, %ebx
            .Ldb_mysql_col\\n:
                cmpl %r13d, %ebx
                jge .Ldb_mysql_row_end\\n
                testl %ebx, %ebx
                jz .Ldb_mysql_nocomma\\n
                movq %r15, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Ldb_mysql_nocomma\\n:
                # nome da coluna
                movq .Ldb_mysql_names(,%rbx,8), %rdi
                movl .Ldb_mysql_names+512(,%rbx,4), %esi
                call kof_io_make_string
                movq %rax, %rdi
                call kof_json_encode_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq %r15, %rdi
                movl $58, %esi
                call kof_json_builder_char
                # valor: lenenc; NULL = 0xFB
                movq 24(%rsp), %rsi
                movzbl (%rsi), %eax
                cmpb $0xFB, %al
                je .Ldb_mysql_null\\n
                call kof_db_mysql_lenenc
                leaq (%rsi,%rax), %r10
                movq %r10, 24(%rsp)           # cursor = fim dos dados
                movq %rsi, %rdi               # rdi = src (dados)
                movq %rax, %rsi               # rsi = len (make_string: rdi=src, rsi=len)
                call kof_io_make_string
                movq %rax, %r12
                xorl %r10d, %r10d
            .Ldb_mysql_num\\n:
                cmpl 16(%r12), %r10d
                jge .Ldb_mysql_is_num\\n
                movzbl 24(%r12,%r10), %eax
                cmpb $'0', %al
                jb .Ldb_mysql_is_str\\n
                cmpb $'9', %al
                ja .Ldb_mysql_is_str\\n
                incq %r10
                jmp .Ldb_mysql_num\\n
            .Ldb_mysql_is_num\\n:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_json_builder_str
                jmp .Ldb_mysql_val\\n
            .Ldb_mysql_is_str\\n:
                movq %r12, %rdi
                call kof_json_encode_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                jmp .Ldb_mysql_val\\n
            .Ldb_mysql_null\\n:
                leaq .Ldb_mysql_nullstr(%rip), %rdi
                xorl %esi, %esi
                call kof_io_make_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq 24(%rsp), %rsi
                incq %rsi
                movq %rsi, 24(%rsp)
            .Ldb_mysql_val\\n:
                incl %ebx
                jmp .Ldb_mysql_col\\n
            .Ldb_mysql_row_end\\n:
                movq %r15, %rdi
                movl $'}', %esi
                call kof_json_builder_char
                movq %r15, %rdi
                call kof_json_builder_result
                movq %r14, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Ldb_mysql_rows\\n
            .Ldb_query_bad\\n:
                call kof_list_new
            .Ldb_query_mydone\\n:
                movq %r14, %rax
            .Ldb_query_done\\n:
                addq $40, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .endm

            KOF_DB_QUERY_N 0
            KOF_DB_QUERY_N 1
            KOF_DB_QUERY_N 2
            KOF_DB_QUERY_N 3
            KOF_DB_QUERY_N 4
            """);
    }

    private static void emitUiWindowFunctions(StringBuilder sb) {
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
            // ── Fase 7: Router (no-ops — UI é KofJS) ──
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
            """);
    }
}
