package dev.kof.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapa slot→nome do método (JVMS 2.6.1: Long/Double ocupam DOIS slots).
 *
 * <p>Antes disto, {@code slotName} assumia 1 slot por parâmetro — com um
 * parâmetro wide (ex.: {@code add(long a, long b)}), o segundo long (slot 2)
 * virava {@code v2}, um nome que não existe no código emitido → decompilado
 * NÃO compilável (SEM011). R6: nunca emitir código errado, mesmo alto.
 * O frame resolve cada slot de parâmetro pelo descriptor (largura real) e
 * devolve {@code null} p/ slots que não são parâmetro nem {@code this} — o
 * chamador decide entre "local v+n" (consistente store/load) e recusar.
 */
final class BytecodeFrame {

    private final String[] names;      // índice = slot; null = não-parâmetro
    private final boolean isStatic;
    private final String ret;          // tipo JVM do retorno ('I'/'J'/'D'/'V'/...); null p/ quebrado
    // §7 degrau 2–3 (índice multi-classe): escopo de resolução da árvore
    // (só em `kof decompile <dir>`; null no modo 1-arquivo = comportamento
    // idêntico ao anterior). Por-frame (instância nova por método/teste) —
    // nunca vaza entre arquivos (sem estático global). O TreeScope é
    // POR-ARQUIVO (compartilhado pelos frames dos métodos) e acumula os
    // imports usados p/ emissão.
    TreeScope treeScope;

    BytecodeFrame(String descriptor, boolean isStatic) {
        this.isStatic = isStatic;
        this.ret = returnTypeOf(descriptor);
        List<String> params = parameterDescriptors(descriptor);
        int slots = isStatic ? 0 : 1;
        int[] widths = new int[params.size()];
        int total = slots;
        for (int i = 0; i < params.size(); i++) {
            widths[i] = isWide(params.get(i)) ? 2 : 1;
            total += widths[i];
        }
        this.names = new String[total];
        if (!isStatic) names[0] = "this";
        int slot = slots;
        for (int i = 0; i < params.size(); i++) {
            names[slot] = "arg" + i;        // primeiro slot do parâmetro
            slot += widths[i];              // wide: o segundo slot fica null
        }
    }

    /** Nome do slot p/ load/store: parâmetro, {@code this}, ou local "v+n". */
    String name(int slot) {
        if (slot >= 0 && slot < names.length && names[slot] != null) return names[slot];
        return "v" + slot;
    }

    /** true se o slot é parâmetro/`this` (tem nome do descriptor) — um local
     *  hoistável é justamente o oposto (slot "v+n"), p/ o prelude do bug 134. */
    boolean isNamedSlot(int slot) {
        return slot >= 0 && slot < names.length && names[slot] != null;
    }

    /** Tipo JVM do retorno ('I','J','F','D','L','[','V'); null se descriptor quebrado. */
    String retType() { return ret; }

    private static String returnTypeOf(String desc) {
        if (desc == null) return null;
        int close = desc.lastIndexOf(')');
        if (close < 0 || close + 1 >= desc.length()) return null;
        return String.valueOf(desc.charAt(close + 1));
    }

    /** true p/ J/D (dois slots). Parâmetros de referência/array têm 1. */
    static boolean isWide(String paramDesc) {
        return paramDesc.equals("J") || paramDesc.equals("D");
    }

    static List<String> parameterDescriptors(String descriptor) {
        List<String> out = new ArrayList<>();
        if (descriptor == null) return out;
        int open = descriptor.indexOf('(');
        int close = descriptor.indexOf(')');
        if (open < 0 || close < 0 || close < open) return out;
        int i = open + 1;
        while (i < close) {
            int start = i;
            while (descriptor.charAt(i) == '[') i++;
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int semi = descriptor.indexOf(';', i);
                if (semi < 0) return out;                 // descriptor quebrado
                i = semi + 1;
            } else {
                i++;
            }
            out.add(descriptor.substring(start, i));
        }
        return out;
    }
}
