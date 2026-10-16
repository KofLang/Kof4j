package dev.kof.compiler.nat;

import dev.kof.compiler.IRClass;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * F2b (D-DB-GAPS, 20/09): emissor do resolver {@code kof_orm_ctors} — a
 * ponte className → (vtable, typeId, totalSize) que {@code kof_orm_find}
 * ({@code RuntimeOrm5}) precisa para CONSTRUIR o record no runtime: typeId
 * e vtable são constantes do programa, conhecidas só aqui (o vtable é o
 * rótulo {@code sanitizeName(clazz.name()) + "_vtable"} que o
 * {@code NativeOpHelpers.emitNewObject} já usa para {@code new}).
 *
 * <p>O backend coleta os className das chamadas {@code kof_orm_find} no scan
 * de ops e emite uma entrada por entidade: comparação de comprimento +
 * byte a byte do className ({@code rdi=body, esi=len}) e, no match, retorna
 * {@code rax=vtable*, rdx=typeId, rcx=totalSize}; sem match, {@code rax=0}
 * (o runtime lança ORM001 honesto — não acontece por construção).
 */
final class NativeOrmCtors {

    private NativeOrmCtors() {}

    static void emit(NativeBackend nb, StringBuilder sb, Set<String> classNames) {
        sb.append("            # ---- kof_orm_ctors: className -> (vtab,tid,size) ---\n");
        sb.append("            .globl kof_orm_ctors\n");
        sb.append("            .type kof_orm_ctors, @function\n");
        sb.append("        kof_orm_ctors:\n");
        // #835: o ajuste de sufixo de CADA entrada avança %rdi (rdi += len-n)
        // — com 2+ entradas o ponteiro CUMULATIVAMENTE desloca e as entradas
        // seguintes comparam bytes errados (Ghost após User/Item lia "ost" →
        // ORM001). r11 (scratch, a função é folha) guarda o body ORIGINAL e
        // cada entrada o restaura antes do add.
        sb.append("            movq %rdi, %r11\n");
        // F2b (23/09): os dados .ascii são emitidos no FIM (fora do fluxo) —
        // antes ficavam colados ao rótulo de miss e o fall-through executava
        // bytes como instrução (quebrava com 2+ entidades). O miss de cada
        // entrada salta para a próxima; a última cai no retorno 0.
        int emitted = 0;
        int total = 0;
        for (String cn : classNames) {
            if (findClass(nb, cn) != null) total++;
        }
        for (String cn : classNames) {
            IRClass clazz = findClass(nb, cn);
            if (clazz == null) continue;
            int typeId = clazz.typeId();
            int totalSize = nb.getLayout(clazz).totalSize();
            String vtab = nb.sanitizeName(clazz.name()) + "_vtable";
            byte[] bytes = cn.getBytes(StandardCharsets.UTF_8);
            String miss = (emitted + 1 < total) ? ".Lormc_t" + (emitted + 1) : ".Lormc_miss";
            sb.append("        .Lormc_t").append(emitted).append(":\n");
            // o suffix match: o runtime pode passar o FQN (o classNameFor) ou
            // o simples — compara os ÚLTIMOS bytes.length bytes do runtime
            // contra a entrada (o NUL em +n fecha o caso exato).
            sb.append("            cmpl $").append(bytes.length).append(", %esi\n");
            sb.append("            jl ").append(miss).append("\n");
            sb.append("            movq %r11, %rdi\n");
            sb.append("            movl %esi, %edx\n");
            sb.append("            subl $").append(bytes.length).append(", %edx\n");
            sb.append("            addq %rdx, %rdi\n");
            sb.append("            movl $").append(bytes.length).append(", %edx\n");
            sb.append("            leaq .Lormc_b").append(emitted).append("(%rip), %r10\n");
            sb.append("            xorl %ecx, %ecx\n");
            sb.append("        .Lormc_c").append(emitted).append(":\n");
            sb.append("            cmpl %edx, %ecx\n");
            sb.append("            jge .Lormc_ce").append(emitted).append("\n");
            sb.append("            movzbl (%rdi,%rcx), %r8d\n");
            sb.append("            movzbl (%r10,%rcx), %r9d\n");
            sb.append("            cmpl %r9d, %r8d\n");
            sb.append("            jne ").append(miss).append("\n");
            sb.append("            incl %ecx\n");
            sb.append("            jmp .Lormc_c").append(emitted).append("\n");
            sb.append("        .Lormc_ce").append(emitted).append(":\n");
            sb.append("            cmpb $0, (%rdi,%rcx)\n");
            sb.append("            jne ").append(miss).append("\n");
            sb.append("            leaq ").append(vtab).append("(%rip), %rax\n");
            sb.append("            movl $").append(typeId).append(", %edx\n");
            sb.append("            movl $").append(totalSize).append(", %ecx\n");
            sb.append("            ret\n");
            emitted++;
        }
        sb.append("        .Lormc_miss:\n");
        sb.append("            xorl %eax, %eax\n");
        sb.append("            ret\n");
        int m = 0;
        for (String cn : classNames) {
            if (findClass(nb, cn) == null) continue;
            byte[] bytes = cn.getBytes(StandardCharsets.UTF_8);
            sb.append("        .Lormc_b").append(m).append(":\n");
            sb.append("            .ascii \"");
            for (byte b : bytes) {
                char c = (char) (b & 0xFF);
                if (c == '"' || c == '\\') sb.append('\\');
                sb.append(c);
            }
            sb.append("\"\n");
            m++;
        }
    }

    /** Mesma regra de casamento de {@code NativeOpHelpers.emitNewObject}. */
    private static IRClass findClass(NativeBackend nb, String className) {
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.name().equals(className) || clazz.name().endsWith("/" + className)
                    || className.endsWith("/" + clazz.name())
                    || className.equals(nb.sanitizeName(clazz.name()))) {
                return clazz;
            }
        }
        return null;
    }

    /** DB-3/DB-1 cross (23/09): coleta os className das faces de leitura ORM
     *  ({@code kof_orm_find/all/where/page/where_op}) no caminho riscv/aarch64
     *  — o scan de ops do x86 já faz isso; o cross precisa do mesmo conjunto
     *  p/ emitir o {@code kof_orm_ctors} por-programa. Mesma janela de 2 ops
     *  do emissor x86 (className é o literal imediatamente antes do call). */
    static void collect(NativeBackend nb, java.util.List<dev.kof.compiler.IRClass> classes) {
        for (dev.kof.compiler.IRClass clazz : classes) {
            for (dev.kof.compiler.IRMethod method : clazz.methods()) {
                for (dev.kof.compiler.IRBasicBlock block : method.basicBlocks()) {
                    java.util.List<dev.kof.compiler.KofOperation> ops = block.operations();
                    for (int i = 0; i < ops.size(); i++) {
                        dev.kof.compiler.KofOperation op = ops.get(i);
                        if (op instanceof dev.kof.compiler.KofCall kc
                                && kc.methodName().startsWith("kof_orm_")) {
                            if ((kc.methodName().equals("kof_orm_find")
                                        && kc.parameterTypes().size() == 5)
                                    || (kc.methodName().equals("kof_orm_all")
                                        && kc.parameterTypes().size() == 4)
                                    || (kc.methodName().equals("kof_orm_where")
                                        && kc.parameterTypes().size() == 6)
                                    || (kc.methodName().equals("kof_orm_page")
                                        && kc.parameterTypes().size() == 6)
                                    || (kc.methodName().equals("kof_orm_where_op")
                                        && kc.parameterTypes().size() == 7)) {
                                for (int j = i - 1; j >= i - 2 && j >= 0; j--) {
                                    if (ops.get(j) instanceof dev.kof.compiler.KofLoadLiteral lit
                                            && lit.value() instanceof String s) {
                                        nb.ormCtorClasses.add(s);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
