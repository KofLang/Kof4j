package dev.kof.compiler.nat;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofProcess;
import dev.kof.compiler.Type;

/**
 * valueOf(...) do caminho println/print do x86-64 (REFACTOR-500: bloco
 * extraido 1:1 de NativeX86Calls; ultima mudanca logica = guard de null do
 * §396 no ramo de toString de record). Recebe o backend via campo para os
 * helpers que ainda vivem la (findVirtualMethodIndex, printDescriptorCounter).
 */
final class NativeX86ValueOf {

    private NativeX86ValueOf() {}

    static boolean emit(NativeBackend nb, StringBuilder sb, KofCall kc) {
        if (!(kc.kind() == KofCallKind.STATIC && "valueOf".equals(kc.methodName()))) {
            return false;
        }
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            if (KofProcess.isResult(argType)) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_process_result_to_string\n");
                sb.append("    pushq %rax\n");
                return true;
            }
            // T? (get de Map, SG-008): o despacho usa o INNER — sem isso o
            // Nullable(primitivo) não casava nenhum branch e o raw int
            // seguia para println_string (SIGSEGV, bug 87)
            Type dispatchType = argType instanceof Type.NullableType nt ? nt.inner() : argType;
            // §284-map (18/09): Nullable(Int/Short/Byte/Long) = caixa fisica do
            // slot de Map (escrita no lowerer; leitura Nullable(V) preserva o
            // null). O INNER cru NAO vale aqui — despacha pela caixa
            // (box_to_string imprime Int/Long como numero = golden JVM do
            // contexto de erasure; o Char nulavel ja chega DESEMBALADO do
            // lowerer, ramo acima, e nao passa por aqui).
            if (argType instanceof Type.NullableType nnt
                    && nnt.inner() instanceof Type.PrimitiveType ipt
                    && NativeBoxTags.unboxFn(ipt.name()) != null) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_box_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && "char".equals(pt.name())) {
                // char → string UTF-8 (kof_int_to_string imprimia o
                // número do codepoint: String.valueOf(0xE9 as Char)
                // devolvia "233" em vez de "é")
                sb.append("    popq %rdi\n");
                sb.append("    call kof_char_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && ("int".equals(pt.name())
                    || "short".equals(pt.name()) || "byte".equals(pt.name()))) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_int_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && "long".equals(pt.name())) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_long_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && "bool".equals(pt.name())) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_bool_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && NativeTypeKinds.isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_float_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && NativeTypeKinds.isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_double_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.ArrayType at) {
                // §388-B (voto mantenedora 21/09): println de array cru no
                // formato de container da casa. Antes o dispatch caía no
                // terminal do elem e imprimia UM elemento como char ("A" —
                // face medida 21/09). O descritor do componente é o MESMO
                // idioma §107 (.rodata no call-site); o runtime faz o laço
                // sobre o bloco [len@16][esz@20][data@24].
                sb.append("    popq %rdi\n");
                String ld = NativePrintDescriptors.emit(sb, nb.printDescriptorCounter++,
                        NativePrintDescriptors.node(nb, at.componentType(), false));
                sb.append("    leaq ").append(ld).append("(%rip), %rsi\n");
                sb.append("    call kof_array_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.ClassType ct && BuiltinTypes.isList(ct)) {
                // §107: List/Map/Set são tipos de RUNTIME (sem vtable) — o
                // ramo genérico abaixo achava tosIdx=-1 e NÃO EMITIA NADA:
                // o ponteiro cru caía em kof_println_string = lixo (R6).
                // O descritor do elemento sai do typer (SEM056: homogênea);
                // 19/09: nó recursivo (record/vtable + List/Set/Map filhos)
                // em .rodata no próprio call-site (NativePrintDescriptors).
                sb.append("    popq %rdi\n");
                String ld = NativePrintDescriptors.emit(sb, nb.printDescriptorCounter++,
                        NativePrintDescriptors.node(nb, BuiltinTypes.listElement(ct), false));
                sb.append("    leaq ").append(ld).append("(%rip), %rsi\n");
                sb.append("    call kof_list_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.ClassType ct && BuiltinTypes.isSet(ct)) {
                sb.append("    popq %rdi\n");
                String ld = NativePrintDescriptors.emit(sb, nb.printDescriptorCounter++,
                        NativePrintDescriptors.node(nb, BuiltinTypes.setElement(ct), false));
                sb.append("    leaq ").append(ld).append("(%rip), %rsi\n");
                sb.append("    call kof_set_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.ClassType ct && BuiltinTypes.isMap(ct)) {
                sb.append("    popq %rdi\n");
                String lk = NativePrintDescriptors.emit(sb, nb.printDescriptorCounter++,
                        NativePrintDescriptors.node(nb, BuiltinTypes.mapKey(ct), false));
                String lv = NativePrintDescriptors.emit(sb, nb.printDescriptorCounter++,
                        NativePrintDescriptors.node(nb, BuiltinTypes.mapValue(ct), true));
                sb.append("    leaq ").append(lk).append("(%rip), %rsi\n");
                sb.append("    leaq ").append(lv).append("(%rip), %rdx\n");
                sb.append("    call kof_map_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (BuiltinTypes.isObject(dispatchType)) {
                // §284: `Object` tem vtable base registrada (typeId 0) — o
                // ramo genérico abaixo LERIA o campo tag do box como ponteiro
                // de vtable (SIGSEGV). O static type Object na erasure SEMPRE
                // carrega um box de primitivo ou referencia real:
                // kof_box_to_string despacha por MAGIC+tag e passa nao-box
                // cru (referencia real com toString = caso raro, vira
                // passthrough — paridade de impressao mantida p/ o corpus).
                sb.append("    popq %rdi\n");
                sb.append("    call kof_box_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.TypeVariable) {
                // §444: TypeVariable NÃO casava ramo nenhum e o emit terminava
                // `return true` SEM emitir conversão — o box de erasure cru
                // (o ctor genérico boxeia o primitivo) caía no println_string
                // = lixo/stdio vazio (silent, R6). Mesma invariante §284 do
                // ramo Object: valor de T apagado é box de primitivo ou
                // referência real — kof_box_to_string despacha por MAGIC+tag
                // e passa não-box cru.
                sb.append("    popq %rdi\n");
                sb.append("    call kof_box_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.ClassType ct && !BuiltinTypes.isString(dispatchType)) {
                // valueOf(objeto) → obj.toString() via vtable (records têm
                // toString no IR; String é identity). Paridade com o JVM.
                int tosIdx = nb.findVirtualMethodIndex(ct.name(), "toString", java.util.List.of());
                if (tosIdx >= 0) {
                    // §396: receiver NULL (T? miss, ex.: orm.find sem hit) —
                    // o host roda String.valueOf(null) = null e o println do
                    // consumidor imprime "null". O guard NAO imprime (isso
                    // duplicaria a saida — o println_string externo ja cuida
                    // do zero em kof_print_string/.Lkof_null_str); ele apenas
                    // PULA o dispatch de vtable deixando o 0 como resultado.
                    int psn = nb.printDescriptorCounter++;
                    sb.append("    popq %rax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    testq %rax, %rax\n");
                    sb.append("    je .Lstr_nullv").append(psn).append("\n");
                    sb.append("    movq 8(%rax), %rbx\n");
                    sb.append("    addq $").append(tosIdx * 8).append(", %rbx\n");
                    sb.append("    movq (%rbx), %rbx\n");
                    sb.append("    popq %rdi\n");
                    sb.append("    call *%rbx\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp .Lstr_nullv_e").append(psn).append("\n");
                    sb.append(".Lstr_nullv").append(psn).append(":\n");
                    sb.append("    popq %rdi\n");
                    sb.append("    pushq $0\n");
                    sb.append(".Lstr_nullv_e").append(psn).append(":\n");
                } else {
                    // §284: static type sem vtable (Object/Nullable(Object))
                    // — o valor na pilha pode ser um BOX de erasure; sem este
                    // ramo o box cru caia em println_string (SIGSEGV).
                    // kof_box_to_string despacha por MAGIC+tag e passa
                    // nao-box cru (invariante preservado).
                    sb.append("    popq %rdi\n");
                    sb.append("    call kof_box_to_string\n");
                    sb.append("    pushq %rax\n");
                }
            }
            return true;
    }
}
