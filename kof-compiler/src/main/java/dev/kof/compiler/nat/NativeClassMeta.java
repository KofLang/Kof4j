package dev.kof.compiler.nat;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.NativeRuntime;

import dev.kof.compiler.IRClass;
import dev.kof.compiler.Type;
import java.util.ArrayList;
import java.util.List;

/** F3: metadados de classe (vtable virtual methods, string data). */
final class NativeClassMeta {
    private NativeClassMeta() {}

    static List<String> collectVirtualMethods(NativeBackend nb, IRClass clazz) {
        List<String> methods = new ArrayList<>();
        List<String> methodNames = new ArrayList<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        // §483: nomes de slot vindos da INTERFACE — um bridge da classe (erased,
        // ACC_BRIDGE) DEVE sobrescrever esse slot (é o mesmo contrato), senão a
        // chamada via interface cai no método abstrato vazio.
        java.util.Set<String> ifaceSlotNames = new java.util.HashSet<>();
        String current = clazz.superName();
        while (current != null && !current.isEmpty() && !"java/lang/Object".equals(current)) {
            IRClass superClazz = nb.allClassesMap.get(current);
            if (superClazz == null) break;
            for (IRMethod m : superClazz.methods()) {
                addSlot(nb, methods, methodNames, superClazz.name(), m, ifaceSlotNames);
            }
            for (String iface : superClazz.interfaces()) {
                if (visited.add(iface)) queue.add(iface);
            }
            current = superClazz.superName();
        }
        // §248: as interfaces DIRETAMENTE implementadas pela classe também
        // entram na fila. Sem isso um default method herdado (ex.: `greetLoud`)
        // não ganhava slot na vtable do implementor e a chamada caía fora do
        // índice (Native imprimia `null`). A passagem de interfaces adiciona
        // por nome e os métodos próprios sobrescrevem depois.
        for (String iface : clazz.interfaces()) {
            if (visited.add(iface)) queue.add(iface);
        }
        while (!queue.isEmpty()) {
            String ifaceName = queue.poll();
            IRClass ifaceClazz = nb.allClassesMap.get(ifaceName);
            if (ifaceClazz == null) continue;
            for (IRMethod m : ifaceClazz.methods()) {
                if ("<init>".equals(m.name()) || "<clinit>".equals(m.name())
                        || m.name().startsWith("kof_")) {
                    continue;
                }
                // Interfaces são CONTRATO herdado: só ganham slot se o nome ainda
                // não existe (nunca sobrescrevem o slot da classe/superclasse — o
                // impl concreto já ocupa aquele índice). §483.
                if (!methodNames.contains(m.name())) {
                    methodNames.add(m.name());
                    ifaceSlotNames.add(m.name());
                    methods.add(NativeSymbolMangling.fnSymbol(ifaceClazz.name(), m, nb.allClassesMap));
                }
            }
            for (String iface : ifaceClazz.interfaces()) {
                if (visited.add(iface)) queue.add(iface);
            }
        }
        for (IRMethod m : clazz.methods()) {
            addSlot(nb, methods, methodNames, clazz.name(), m, ifaceSlotNames);
        }
        return methods;
    }

    /**
     * §483: acrescenta um slot de vtable usando o MESMO símbolo (fnSymbol, que
     * tageia sobrecargas E bridges da §356) e o mesmo critério de slot das três
     * passagens (superclasse, interfaces herdadas, métodos próprios). Antes o
     * loop da superclasse usava `Owner_nome` sem tag: um método herdado com
     * bridge apontava para um símbolo indefinido e o layout de slots do filho
     * não espelhava o do pai (o método concreto ficava sem slot → dispatch caía
     * no slot errado).
     */
    private static void addSlot(NativeBackend nb, List<String> methods, List<String> methodNames,
                                String ownerName, IRMethod m, java.util.Set<String> ifaceSlotNames) {
        if ("<init>".equals(m.name()) || "<clinit>".equals(m.name()) || m.name().startsWith("kof_")) {
            return;
        }
        // SG-011B: fnSymbol == sanitize+"_"+name p/ classes reais (vtable
        // idêntica); só o recipiente Main leva sufixo de assinatura — o MESMO da
        // .globl, então cada slot referencia um símbolo definido.
        // §131 (10a): método sobrecarregado (2+ defs do nome) ganha slot PRÓPRIO
        // por assinatura (fnSymbol tageia) — antes o 2º def sobrescrevia o slot
        // (methods.set) e os 2 .globl colidiam.
        String sym = NativeSymbolMangling.fnSymbol(ownerName, m, nb.allClassesMap);
        // §483: um bridge de erasure (ACC_BRIDGE) da classe é o MESMO contrato do
        // slot abstrato vindo da interface — sobrescreve por nome para a chamada
        // via interface cair no bridge (e não no abstrato vazio). Sem isto a
        // semeadura das interfaces da própria classe (§248) deslocaria o bridge.
        if ((m.accessFlags() & dev.kof.compiler.AccessFlags.BRIDGE) != 0
                && ifaceSlotNames.contains(m.name())) {
            int bi = methodNames.indexOf(m.name());
            methods.set(bi, sym);
            return;
        }
        if (NativeSymbolMangling.sigMangles(ownerName, m.name(), nb.allClassesMap)) {
            methodNames.add(m.name());
            methods.add(sym);
        } else {
            int idx = methodNames.indexOf(m.name());
            if (idx >= 0) {
                methods.set(idx, sym);
            } else {
                methodNames.add(m.name());
                methods.add(sym);
            }
        }
    }

    static int findVirtualMethodIndex(NativeBackend nb, String ownerTypeName, String methodName, int argCount) {
        return findVirtualMethodIndex(nb, ownerTypeName, methodName, arityTypes(argCount));
    }

    /** vtable da classe: método vazio = .quad 0; senão a tabela gerada do
     *  runtime (split ≤500 — movida do NativeBackend, dono = meta de classe). */
    static void emitMethodTable(NativeBackend nb, StringBuilder sb, IRClass clazz) {
        List<String> methods = collectVirtualMethods(nb, clazz);
        if (methods.isEmpty()) {
            sb.append(".balign 8\n");
            sb.append(nb.sanitizeName(clazz.name()) + "_vtable:\n");
            sb.append("    .quad 0\n");
            return;
        }
        NativeRuntime.generateMethodTable(sb, nb.sanitizeName(clazz.name()), methods);
    }

/** §131-residual (13/09): resolve o slot pelo NOME + TIPOS do call site.
     *  Só a ARIDADE não bastava — `twice(Int)`/`twice(String)` (mesma aridade,
     *  tipos diferentes) resolviam ambas para o 1º slot: o Native chamava o
     *  método errado (SIGSEGV ao passar String p/ parâmetro Int). JVM/Script/JS
     *  sempre estiveram corretos (dispatch por descritor/SAM). */
    static int findVirtualMethodIndex(NativeBackend nb, String ownerTypeName, String methodName, List<Type> argTypes) {
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.name().equals(ownerTypeName) || clazz.name().endsWith("/" + ownerTypeName)
                    || ownerTypeName.endsWith("/" + clazz.name()) || ownerTypeName.equals(nb.sanitizeName(clazz.name()))) {
                List<String> methods = collectVirtualMethods(nb, clazz);
                // #613: lookup DELIBERADAMENTE sem o sufixo de bridge (3-arg):
                // um call site com owner de CLASSE quer o método CONCRETO; a
                // assinatura apagada (bridge) só é alcançada via owner INTERFACE,
                // cujos slots usam os símbolos da própria interface (sem sufixo).
                String mangled = NativeSymbolMangling.fnSymbol(clazz.name(), methodName,
                        methodsForCall(clazz, methodName, argTypes), nb.allClassesMap);
                int bySig = indexOfSymbol(methods, mangled);
                if (bySig >= 0) return bySig;
                // sem casamento por tipo (arg Unknown): casa QUALQUER overload
                // do nome — melhor que -1 (sem dispatch).
                for (IRMethod m : clazz.methods()) {
                    if (m.name().equals(methodName) && !"<init>".equals(m.name()) && !"<clinit>".equals(m.name())) {
                        String m2 = NativeSymbolMangling.fnSymbol(clazz.name(), m.name(), m.parameterTypes(), nb.allClassesMap);
                        int idx = indexOfSymbol(methods, m2);
                        if (idx >= 0) return idx;
                    }
                }
                return -1;
            }
        }
        return -1;
    }

    private static List<Type> arityTypes(int argCount) {
        if (argCount < 0) return List.of();
        java.util.ArrayList<Type> l = new java.util.ArrayList<>();
        for (int i = 0; i < argCount; i++) l.add(Type.UnknownType.UNKNOWN);
        return l;
    }

    private static int indexOfSymbol(List<String> methods, String symbol) {
        for (int i = 0; i < methods.size(); i++) {
            if (methods.get(i).equals(symbol)) return i;
        }
        return -1;
    }

    /** §131-residual: paramTypes do método (clazz,name) que casa com os tipos
     *  do call site (aridade + tipo). Prefere casamento exato; sem ele, cai na
     *  1ª assinatura da aridade (arg Unknown). Lista vazia se não achar. */
    static java.util.List<Type> methodsForCall(IRClass clazz, String methodName, List<Type> argTypes) {
        IRMethod arityMatch = null;
        for (IRMethod m : clazz.methods()) {
            if (!m.name().equals(methodName)) continue;
            if (m.parameterTypes().size() != argTypes.size()) continue;
            if (arityMatch == null) arityMatch = m;
            boolean ok = true;
            for (int i = 0; i < argTypes.size(); i++) {
                Type declared = m.parameterTypes().get(i);
                Type arg = argTypes.get(i);
                if (!(declared.equals(arg) || declared.toString().equals(arg.toString()))) { ok = false; break; }
            }
            if (ok) return m.parameterTypes();
        }
        return arityMatch != null ? arityMatch.parameterTypes() : java.util.List.of();
    }

    /**
     * N2 (23/09) — tabela `kof_tostring_table[type_id]` = ponteiro da funcao
     * `toString` da classe (0 = sem toString). E o alvo do despacho polimorfico
     * de `kof_box_to_string` para uma REFERENCIA tipada `Object` (record/classe
     * guardado em local Object ou `X as Object`): sem isto o ponteiro cru caia
     * no println_string e imprimia vazio. O discriminador e o `type_id` no
     * offset 0 (o MESMO do `kof_instanceof`), NAO um segundo ABI.
     * Emitida para x86 (aqui, em emitStringData) e riscv (NativeArchEmitter).
     */
    static void emitToStringTable(NativeBackend nb, StringBuilder sb) {
        int maxId = 0;
        for (IRClass c : nb.allClassesMap.values()) {
            if (c.typeId() > maxId) maxId = c.typeId();
        }
        String[] table = new String[maxId + 1];
        for (IRClass c : nb.allClassesMap.values()) {
            if (c.typeId() <= 0) continue;
            int idx = findVirtualMethodIndex(nb, c.name(), "toString", List.of());
            if (idx >= 0) {
                List<String> methods = collectVirtualMethods(nb, c);
                if (idx < methods.size()) table[c.typeId()] = methods.get(idx);
            }
        }
        sb.append(".balign 8\n");
        sb.append("kof_tostring_table:\n");
        for (int i = 0; i <= maxId; i++) {
            sb.append("    .quad ").append(table[i] == null ? "0" : table[i]).append("\n");
        }
    }

    /**
     * §104b-ii (24/09) — tabela `kof_equals_table[type_id]` = ponteiro do
     * `equals(Owner)` virtual da classe (0 = sem equals). E o alvo do
     * `kof_obj_equals`, que faz a contencao por CONTEUDO em colecao (o
     * `kof_list_contains` comparava ponteiro; records ja tem equals sintetico
     * de conteudo — §114). Mesmo discriminador `type_id` no offset 0 da
     * `kof_tostring_table` (§205 N2), NAO um segundo ABI. Emitida para x86
     * (aqui, em emitStringData) e riscv (NativeArchEmitter; aarch herda).
     */
    static void emitEqualsTable(NativeBackend nb, StringBuilder sb) {
        int maxId = 0;
        for (IRClass c : nb.allClassesMap.values()) {
            if (c.typeId() > maxId) maxId = c.typeId();
        }
        String[] table = new String[maxId + 1];
        for (IRClass c : nb.allClassesMap.values()) {
            if (c.typeId() <= 0) continue;
            int idx = findVirtualMethodIndex(nb, c.name(), "equals",
                    java.util.List.of(Type.UnknownType.UNKNOWN));
            if (idx >= 0) {
                List<String> methods = collectVirtualMethods(nb, c);
                if (idx < methods.size()) table[c.typeId()] = methods.get(idx);
            }
        }
        sb.append(".balign 8\n");
        sb.append("kof_equals_table:\n");
        for (int i = 0; i <= maxId; i++) {
            sb.append("    .quad ").append(table[i] == null ? "0" : table[i]).append("\n");
        }
    }

    /**
     * §114 (face hash aninhado, 24/09) — tabela `kof_hashcode_table[type_id]` =
     * ponteiro do `hashCode()` virtual da classe (0 = sem hashCode). Alvo do
     * `kof_obj_hash`, que soma por CONTEUDO o campo record/classe no hashCode
     * sintetizado (antes somava o PONTEIRO). Mesmo discriminador `type_id` no
     * offset 0 das tabelas toString/equals. Emitida para x86 (em emitStringData)
     * e riscv (NativeArchEmitter; aarch herda).
     */
    static void emitHashCodeTable(NativeBackend nb, StringBuilder sb) {
        int maxId = 0;
        for (IRClass c : nb.allClassesMap.values()) {
            if (c.typeId() > maxId) maxId = c.typeId();
        }
        String[] table = new String[maxId + 1];
        for (IRClass c : nb.allClassesMap.values()) {
            if (c.typeId() <= 0) continue;
            int idx = findVirtualMethodIndex(nb, c.name(), "hashCode", java.util.List.of());
            if (idx >= 0) {
                List<String> methods = collectVirtualMethods(nb, c);
                if (idx < methods.size()) table[c.typeId()] = methods.get(idx);
            }
        }
        // §114: emitida SEMPRE (como toString/equals) — o `kof_obj_hash` (fatia
        // RuntimeMath/B36, puxada por math/strings) referencia o símbolo; emitir
        // condicionalmente quebrava o link de programas sem record (LinkByUseTest,
        // KofMathTest). Para o hello não inchar, o `kof_obj_hash` fica em fatia
        // on-demand (não na RuntimeList, que o hello puxa) — gate ArtifactSizeTest.
        sb.append(".balign 8\n");
        sb.append("kof_hashcode_table:\n");
        for (int i = 0; i <= maxId; i++) {
            sb.append("    .quad ").append(table[i] == null ? "0" : table[i]).append("\n");
        }
    }

    static void emitStringData(NativeBackend nb, StringBuilder sb) {
        for (String[] entry : nb.stringLiterals) {
            String value = entry[0];
            String label = entry[1];
            // §623: escape control/UTF-8 bytes (o NUL cru de "\0" quebrava o GAS).
            sb.append(label).append(": .asciz \"")
              .append(NativeGasStrings.gasEscape(value)).append("\"\n");
        }
        sb.append(".Lnewline: .asciz \"\\n\"\n");
        sb.append(".Lkof_str_true: .asciz \"true\"\n");
        sb.append(".Lkof_str_false: .asciz \"false\"\n");
        sb.append(".balign 8\n");
        sb.append("kof_super_table:\n");
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.typeId() == 0) continue;
            int superTypeId = 0;
            if (clazz.superName() != null && !clazz.superName().isEmpty()) {
                String superSimple = clazz.superName().substring(clazz.superName().lastIndexOf('/') + 1);
                for (IRClass other : nb.allClassesMap.values()) {
                    if (other.name().equals(clazz.superName()) || other.name().endsWith("/" + superSimple)
                            || superSimple.equals(nb.sanitizeName(other.name()))) {
                        superTypeId = other.typeId();
                        break;
                    }
                }
            }
            sb.append("    .long ").append(clazz.typeId()).append(", ").append(superTypeId).append("\n");
        }
        sb.append("    .long 0, 0\n");
        emitToStringTable(nb, sb);
        emitEqualsTable(nb, sb);
        emitHashCodeTable(nb, sb);
    }

}