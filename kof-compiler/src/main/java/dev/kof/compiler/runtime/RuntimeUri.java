package dev.kof.compiler.runtime;

/**
 * Runtime nativo x86_64 do kof.net (STDLIB S8-B) — MESMA máquina do
 * JvmStringNetRuntime / JsRuntimeUiNet / oracle Python (17 vetores em
 * KofNetTest; paridade JVM==JS comprovada). RFC 3986 subset v1 do plan §4:
 * scheme [A-Za-z][A-Za-z0-9+.-]* antes do 1º ':'; authority só após "//"
 * (userinfo até o último '@'; host até o 1º ':' — sem colchetes IPv6);
 * path [after, q); query (q, bodyEnd); fragment após 1º '#'; ausente => "";
 * null => NULL; len<=0 => "" novo. Delimitadores são ASCII — bytes >=128
 * fluem como conteúdo (UTF-8 por bytes, como o resto da stdlib).
 *
 * ⚠️ NÃO confundir com RuntimeNet.java (sockets kof_net_socket/... — lane
 * http). Este é URI-parse; símbolos próprios kof_net_{scheme,host,port,
 * path,query,fragment,field}.
 *
 * Layout de pilha: 6 pares {int32 start,int32 len} em 48(%rsp):
 * +0 sch  +8 host  +16 port  +24 path  +32 query  +40 frag.
 * Estado do scan: rbx=v, r12=len, r13=idx, r14=after, r15=bodyEnd.
 * Callers kof_alloc/kof_memcpy só NO FIM (spans na pilha; start/len em
 * r14/r15 callee-saved; novo em r13). Frame: 5 pushes + sub 48 =>
 * 16-align no call. Helpers .Lv_nt_le/.Lv_nt_sc em eax->eax (clobber r11),
 * faixas por SUBTRAÇÃO UNSIGNED (lição isIpv4).
 */
public final class RuntimeUri {

    private RuntimeUri() {}

    public static void emit(StringBuilder sb) {
        sb.append("""

            # ── kof.net (STDLIB S8-B) ─────────────────────────────────────
            .globl kof_net_scheme
            kof_net_scheme:
                xorl %esi, %esi
                jmp kof_net_field
            .globl kof_net_host
            kof_net_host:
                movl $1, %esi
                jmp kof_net_field
            .globl kof_net_port
            kof_net_port:
                movl $2, %esi
                jmp kof_net_field
            .globl kof_net_path
            kof_net_path:
                movl $3, %esi
                jmp kof_net_field
            .globl kof_net_query
            kof_net_query:
                movl $4, %esi
                jmp kof_net_field
            .globl kof_net_fragment
            kof_net_fragment:
                movl $5, %esi
                jmp kof_net_field

            .globl kof_net_field
            .type kof_net_field, @function
            kof_net_field:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                movq %rdi, %rbx
                movl %esi, %r13d
                movq $0, 0(%rsp)
                movq $0, 8(%rsp)
                movq $0, 16(%rsp)
                movq $0, 24(%rsp)
                movq $0, 32(%rsp)
                movq $0, 40(%rsp)
                testq %rbx, %rbx
                jz .Lv_nt_null
                movl 16(%rbx), %r12d
                testl %r12d, %r12d
                jle .Lv_nt_zero
                xorl %r14d, %r14d        # after = 0
                # --- A: 1º ':' em [0,len) ---
                xorl %eax, %eax
            .Lv_nt_cfc:
                cmpl %r12d, %eax
                jae .Lv_nt_nocf
                cmpb $58, 24(%rbx,%rax)
                je .Lv_nt_cf
                incl %eax
                jmp .Lv_nt_cfc
            .Lv_nt_cf:                   # eax = cp > 0?
                testl %eax, %eax
                jle .Lv_nt_nocf
                movl %eax, %r15d         # (reusa r15 p/ cp temporário)
                movzbl 24(%rbx), %eax
                call .Lv_nt_le
                testl %eax, %eax
                jz .Lv_nt_nocf
                movl $1, %ecx
            .Lv_nt_ckv:
                cmpl %r15d, %ecx
                jae .Lv_nt_vok
                movzbl 24(%rbx,%rcx), %eax
                call .Lv_nt_sc
                testl %eax, %eax
                jz .Lv_nt_nocf
                incl %ecx
                jmp .Lv_nt_ckv
            .Lv_nt_vok:
                movl %r15d, 4(%rsp)      # sch len = cp (start 0)
                leal 1(%r15), %r14d      # after = cp+1
            .Lv_nt_nocf:
                # --- B: 1º '#' em [after,len) => bodyEnd ---
                movl %r12d, %r15d        # bodyEnd = len (sem '#')
                movl %r14d, %eax
            .Lv_nt_fsc:
                cmpl %r12d, %eax
                jae .Lv_nt_fend
                cmpb $35, 24(%rbx,%rax)
                je .Lv_nt_fh
                incl %eax
                jmp .Lv_nt_fsc
            .Lv_nt_fh:
                movl %eax, %r15d         # bodyEnd = fp
                leal 1(%rax), %ecx
                movl %ecx, 40(%rsp)      # frag start
                movl %r12d, %eax
                subl %ecx, %eax
                movl %eax, 44(%rsp)      # frag len
            .Lv_nt_fend:
                # --- C: 1º '?' em [after,bodyEnd) => qpos; path/query ---
                movl %r15d, %ecx         # qpos = bodyEnd (default: sem '?')
                movl %r14d, %eax
            .Lv_nt_qsc:
                cmpl %r15d, %eax
                jae .Lv_nt_qend
                cmpb $63, 24(%rbx,%rax)
                je .Lv_nt_qh
                incl %eax
                jmp .Lv_nt_qsc
            .Lv_nt_qh:
                movl %eax, %ecx          # qpos
                incl %eax                # query start = qpos+1
                movl %eax, 32(%rsp)
                movl %r15d, %edx
                subl %eax, %edx
                movl %edx, 36(%rsp)      # query len = bodyEnd - (qpos+1)
            .Lv_nt_qend:
                movl %r14d, 24(%rsp)     # path start = after
                movl %ecx, %eax
                subl %r14d, %eax
                movl %eax, 28(%rsp)      # path len = qpos - after
                # --- D: path começa com "//"? authority ---
                movl 28(%rsp), %ecx
                cmpl $2, %ecx
                jb .Lv_nt_save
                movl 24(%rsp), %eax
                cmpb $47, 24(%rbx,%rax)
                jne .Lv_nt_save
                cmpb $47, 25(%rbx,%rax)
                jne .Lv_nt_save
                leal 2(%rax), %r8d       # ast = pstart+2
                movl %eax, %r9d
                addl %ecx, %r9d          # pathEnd = pstart+plen
                movl %r9d, %r10d         # aend = pathEnd (default)
                movl %r8d, %eax
            .Lv_nt_asc:
                cmpl %r9d, %eax
                jae .Lv_nt_aend
                movzbl 24(%rbx,%rax), %edx
                cmpb $47, %dl
                je .Lv_nt_acut
                cmpb $63, %dl
                je .Lv_nt_acut
                cmpb $35, %dl
                je .Lv_nt_acut
                incl %eax
                jmp .Lv_nt_asc
            .Lv_nt_acut:
                movl %eax, %r10d
            .Lv_nt_aend:
                movl %r10d, 24(%rsp)     # path start = aend
                movl %r9d, %eax
                subl %r10d, %eax
                movl %eax, 28(%rsp)      # path len = pathEnd - aend
                # último '@' em [ast,aend) => auth start
                movl %r10d, %eax
            .Lv_nt_ats:
                cmpl %r8d, %eax
                jbe .Lv_nt_atd
                decl %eax
                cmpb $64, 24(%rbx,%rax)
                je .Lv_nt_atf
                jmp .Lv_nt_ats
            .Lv_nt_atf:
                leal 1(%rax), %r8d       # ast = at+1
            .Lv_nt_atd:
                # 1º ':' em [ast,aend) => host/port split
                movl %r8d, %eax
            .Lv_nt_hsc:
                cmpl %r10d, %eax
                jae .Lv_nt_nohp
                cmpb $58, 24(%rbx,%rax)
                je .Lv_nt_hp
                incl %eax
                jmp .Lv_nt_hsc
            .Lv_nt_hp:
                movl %r8d, 8(%rsp)       # host start
                movl %eax, %ecx
                subl %r8d, %ecx
                movl %ecx, 12(%rsp)      # host len
                leal 1(%rax), %ecx
                movl %ecx, 16(%rsp)      # port start
                movl %r10d, %ecx
                subl %eax, %ecx
                decl %ecx
                movl %ecx, 20(%rsp)      # port len
                jmp .Lv_nt_save
            .Lv_nt_nohp:
                movl %r8d, 8(%rsp)
                movl %r10d, %ecx
                subl %r8d, %ecx
                movl %ecx, 12(%rsp)
            .Lv_nt_save:
                # --- E: extrai campo idx ---
                leal (%r13d,%r13d), %eax
                movslq %eax, %rax
                movl 0(%rsp,%rax,4), %r14d   # start
                movl 4(%rsp,%rax,4), %r15d   # len
                jmp .Lv_nt_new
            .Lv_nt_zero:
                xorl %r14d, %r14d
                xorl %r15d, %r15d
            .Lv_nt_new:
                movl %r15d, %edi
                addl $25, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r15d, 16(%r13)
                movl $0, 20(%r13)
                movb $0, 24(%r13,%r15)
                testl %r15d, %r15d
                jle .Lv_nt_ret
                movq %r13, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                addq $24, %rsi
                addq %r14, %rsi
                movl %r15d, %edx
                call kof_memcpy
            .Lv_nt_ret:
                movq %r13, %rax
                jmp .Lv_nt_done
            .Lv_nt_null:
                xorl %eax, %eax
            .Lv_nt_done:
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .Lv_nt_le:                   # letra? eax->eax (clobbers r11)
                movl %eax, %r11d
                subl $65, %r11d
                cmpl $26, %r11d
                jb .Lv_nt_le1
                movl %eax, %r11d
                subl $97, %r11d
                cmpl $26, %r11d
                jb .Lt_nt_le1x
                xorl %eax, %eax
                ret
            .Lt_nt_le1x:
            .Lv_nt_le1:
                movl $1, %eax
                ret
            .Lv_nt_sc:                   # letra|dígito|+-. eax->eax (clobbers r11)
                movl %eax, %r11d
                subl $65, %r11d
                cmpl $26, %r11d
                jb .Lv_nt_sc1
                movl %eax, %r11d
                subl $97, %r11d
                cmpl $26, %r11d
                jb .Lv_nt_sc1
                movl %eax, %r11d
                subl $48, %r11d
                cmpl $10, %r11d
                jb .Lv_nt_sc1
                cmpb $43, %al
                je .Lv_nt_sc1
                cmpb $45, %al
                je .Lv_nt_sc1
                cmpb $46, %al
                je .Lv_nt_sc1
                xorl %eax, %eax
                ret
            .Lv_nt_sc1:
                movl $1, %eax
                ret
        

            # ── fachada queryEncode/queryDecode (regra 2: delega encoding.url*) ──
            .globl kof_net_queryEncode
            .type kof_net_queryEncode, @function
            kof_net_queryEncode:
                jmp kof_encoding_urlEncode
            .globl kof_net_queryDecode
            .type kof_net_queryDecode, @function
            kof_net_queryDecode:
                jmp kof_encoding_urlDecode
        """);
    }
}
