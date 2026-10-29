package dev.kof.compiler.wasm;

import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import java.util.Map;

/**
 * CLASS-1 (28/10, #776 — split check_500): a varredura das CLASSES mutáveis
 * (não-Record) do backend WASI — o mapa de layouts (o MESMO ClassLayout:
 * os slots de 8B) e os decls com a chave QUALIFICADA ("X.<init>"/"X.metodo"
 * — os nomes de método colidem entre classes). A classe de ENTRADA
 * (Default/Main) não é classe de usuário: incluí-la fazia o main baixar
 * como método de instância (o receiver no param 0 + o mapType do String[]
 * recusando — medido).
 */
final class WasmClassScan {

    private WasmClassScan() {
    }

    /** As CLASSES mutáveis (não-Record): o MESMO ClassLayout (os slots de
     *  8B); o handle = o bloco no bump heap; os métodos = as funcs com o
     *  receiver (o this = o ÚLTIMO param do ctor, o PRIMEIRO dos métodos);
     *  o print da instância recusa (o toString de identidade não é
     *  byte-parity-ável). */
    static Map<String, ClassLayout> buildClassLayouts(IRModule module) {
        Map<String, ClassLayout> classes = new java.util.LinkedHashMap<>();
        for (IRClass clazz : module.classes()) {
            if ("java/lang/Record".equals(clazz.superName())) continue;
            // a classe de ENTRADA (Default/Main) não é uma classe de usuário:
            // incluí-la fazia o main baixar como método de instância (o
            // receiver no param 0 + o mapType do String[] recusando)
            if ("Default/Main".equals(clazz.name())) continue;
            String simple = clazz.name().substring(clazz.name().lastIndexOf('/') + 1);
            // CLASS-2 (28/10, #776): o layout = buildWithSuper (os campos
            // HERDADOS — o build = os próprios apenas: o bloco da subclasse
            // subdimensionado e os stores da base estouravam no espaço
            // livre do bump — o bug latente medido)
            classes.put(simple, ClassLayout.buildWithSuper(clazz, k -> findClass(module, k)));
        }
        return classes;
    }

    /** A classe por nome interno (o resolver do buildWithSuper). */
    private static IRClass findClass(IRModule module, String internal) {
        for (IRClass c : module.classes()) {
            if (c.name().equals(internal)) return c;
        }
        return null;
    }

    /** Os métodos de instância e os ctors das classes entram no decls com
     *  a chave QUALIFICADA (o fecho resolve pelo dono — os nomes de método
     *  colidem entre classes). */
    static void declareClassMethods(IRModule module, Map<String, ClassLayout> classes,
                                    Map<String, IRMethod> decls) {
        for (IRClass clazz : module.classes()) {
            if (!"java/lang/Record".equals(clazz.superName())) {
                String simple = clazz.name().substring(clazz.name().lastIndexOf('/') + 1);
                if (!classes.containsKey(simple)) continue;
                for (IRMethod m : clazz.methods()) {
                    decls.putIfAbsent(simple + "." + m.name(), m);
                }
            }
        }
    }

    /** Os métodos de instância/ctors ALCANÇADOS (o fecho do `seen`) — os
     *  não-alcançados não saem (a mesma regra de alcance dos statics). */
    static java.util.List<IRMethod> reachedClassMethods(IRModule module,
            Map<String, ClassLayout> classes, java.util.Set<IRMethod> seen) {
        java.util.List<IRMethod> classEntries = new java.util.ArrayList<>();
        for (IRClass clazz : module.classes()) {
            if ("java/lang/Record".equals(clazz.superName())) continue;
            String simple = clazz.name().substring(clazz.name().lastIndexOf('/') + 1);
            if (!classes.containsKey(simple)) continue;
            for (IRMethod m : clazz.methods()) {
                if (seen.contains(m)) classEntries.add(m);
            }
        }
        return classEntries;
    }

    /** O nome simples da DONA do método (o nome qualificado da func — o
     *  Call resolve por nome e os métodos colidem entre classes). */
    static String ownerSimpleNameOf(IRMethod m, IRModule module) {
        for (IRClass clazz : module.classes()) {
            for (IRMethod mm : clazz.methods()) {
                if (mm == m) {
                    return clazz.name().substring(clazz.name().lastIndexOf('/') + 1);
                }
            }
        }
        return m.name();
    }

    /** A LEI DA ORDEM (CLASS-1, 28/10): o MÉTODO de instância = [recv,
     *  args...] (a ordem do IR: o receiver empilhado PRIMEIRO) — o this =
     *  o param 0. O CTOR = o receiver ÚLTIMO (a mecânica do alloc: os args
     *  se empilham antes do <init> e o get do scratch clsIdx empilha o
     *  receiver POR CIMA — a ordem da pilha manda). Devolve o jvmSlot após
     *  os params (o ponto de partida dos locals). CLASS-2b (29/10): o
     *  param de CLASSE alcançada = o handle i32 (o mesmo slot do
     *  record/local — o mapType escalar recusava). */
    static int buildParams(dev.kof.compiler.IRMethod m, java.util.List<Integer> params,
            Map<Integer, Integer> slotMap, boolean receiver, Map<String, ClassLayout> classes,
            Map<String, ClassLayout> records) {
        if (!receiver) {
            int jvmSlot = 0;
            for (int i = 0; i < m.parameterTypes().size(); i++) {
                dev.kof.compiler.Type t = m.parameterTypes().get(i);
                params.add(paramWasmType(t, m, classes, records));
                slotMap.put(jvmSlot, i);
                jvmSlot += WasmScalarOps.isWide(t) ? 2 : 1;
            }
            return jvmSlot;
        }
        if ("<init>".equals(m.name())) {
            int jvmSlot = 1;
            for (int i = 0; i < m.parameterTypes().size(); i++) {
                dev.kof.compiler.Type t = m.parameterTypes().get(i);
                params.add(paramWasmType(t, m, classes, records));
                slotMap.put(jvmSlot, i);
                jvmSlot += WasmScalarOps.isWide(t) ? 2 : 1;
            }
            params.add(WasmFunc.TYPE_I32);                          // o this = o ÚLTIMO param
            slotMap.put(0, params.size() - 1);
            return jvmSlot;
        }
        params.add(WasmFunc.TYPE_I32);                              // o this = o param 0
        slotMap.put(0, 0);
        int jvmSlot = 1;
        for (int i = 0; i < m.parameterTypes().size(); i++) {
            dev.kof.compiler.Type t = m.parameterTypes().get(i);
            params.add(paramWasmType(t, m, classes, records));
            slotMap.put(jvmSlot, i + 1);                            // o receiver ocupa o wasm 0
            jvmSlot += WasmScalarOps.isWide(t) ? 2 : 1;
        }
        return jvmSlot;
    }

    /** O tipo wasm do param (CLASS-2b, 29/10): a classe alcançada = o
     *  handle i32; os escalares = o mapa. ARG-REC (29/10, #776): o RECORD
     *  alcançado = o MESMO handle i32 (o mesmo slot do local/param de
     *  classe). As coleções/arrays como params continuam FORA (sem sonda —
     *  a recusa honesta fica). */
    private static int paramWasmType(dev.kof.compiler.Type t, dev.kof.compiler.IRMethod m,
            Map<String, ClassLayout> classes, Map<String, ClassLayout> records) {
        if (t instanceof dev.kof.compiler.Type.ClassType ct
                && classes != null && classes.containsKey(ct.name())) {
            return WasmFunc.TYPE_I32;
        }
        if (t instanceof dev.kof.compiler.Type.ClassType ct
                && records != null && records.containsKey(ct.name())) {
            return WasmFunc.TYPE_I32;
        }
        return WasmScalarOps.mapType(WasmScalarOps.typeName(t), m.name(), "parametro");
    }

    /** CLASS-2b (29/10, #776): a assinatura de função top-level com
     *  params/retorno de CLASSE alcançada = dentro do subset (o handle i32
     *  — o mesmo slot dos escalares; o corpo = a maquinaria das classes).
     *  ARG-REC (29/10, #776): o RECORD alcançado = o MESMO handle i32 (o
     *  param/retorno de record entra no subset — o fold/toString das
     *  rodadas anteriores já pousaram a maquinaria do record). Os outros
     *  não-escalares (List/Map/Set/void/IO) continuam fora (a recusa
     *  honesta — sem sonda, sem lei). */
    static boolean classScalarSignature(dev.kof.compiler.IRMethod m,
            Map<String, ClassLayout> classes, Map<String, ClassLayout> records) {
        for (dev.kof.compiler.Type t : m.parameterTypes()) {
            if (t instanceof dev.kof.compiler.Type.ClassType ct
                    && classes != null && classes.containsKey(ct.name())) continue;
            if (t instanceof dev.kof.compiler.Type.ClassType ct
                    && records != null && records.containsKey(ct.name())) continue;
            if (t instanceof dev.kof.compiler.Type.PrimitiveType pt && isScalarPrim(pt.name())) continue;
            return false;
        }
        dev.kof.compiler.Type r = m.returnType();
        if (r instanceof dev.kof.compiler.Type.ClassType ct && classes != null
                && classes.containsKey(ct.name())) return true;
        if (r instanceof dev.kof.compiler.Type.ClassType ct && records != null
                && records.containsKey(ct.name())) return true;
        return r instanceof dev.kof.compiler.Type.PrimitiveType pt && isScalarPrim(pt.name());
    }

    private static boolean isScalarPrim(String name) {
        return switch (name.toLowerCase()) {
            case "int", "long", "double", "bool", "boolean", "char" -> true;
            default -> false;
        };
    }

    /** As IMPLEMENTAÇÕES do tipo estático: o próprio tipo + o fecho
     *  TRANSITIVO das subclasses (as classes do mapa cuja cadeia de
     *  superName passa pelo tipo) — a ordem = a do mapa (determinística =
     *  a ordem das tags). */
    static java.util.List<String> implsOf(IRModule module,
            Map<String, ClassLayout> classes, String simple) {
        java.util.List<String> impls = new java.util.ArrayList<>();
        impls.add(simple);
        for (String cand : classes.keySet()) {
            String cur = cand;
            boolean isSub = false;
            while (cur != null) {
                if (simple.equals(cur)) { isSub = !cand.equals(simple); break; }
                dev.kof.compiler.IRClass c = findClass(module, cur);
                if (c == null) break;
                cur = c.superName();
            }
            if (isSub) impls.add(cand);
        }
        return impls;
    }

    /** O índice (tag) determinístico da classe = a posição no mapa (a
     *  ordem de inserção = a ordem do IRModule). */
    static int classTag(WasmLowering.Ctx ctx, String simple) {
        int i = 0;
        for (String k : ctx.classes().keySet()) {
            if (k.equals(simple)) return i;
            i++;
        }
        throw new WasmUnsupportedException("tag de '" + simple + "' fora do mapa de classes (WASM002)"
                + " — docs/wasm-wasi-plan.md (#776)");
    }
}
