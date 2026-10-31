package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GC-1 (29/10, #776): o post-pass de SPILL de raízes do coletor — o
 * equivalente WASM do safe-point/root-map do design nativo (D-WASM-GO).
 * Na WASM o coletor não pode escanear a pilha máquina (a pilha wasm real é
 * implementação-privada), então o compilador ASSINA as raízes no ponto
 * seguro: antes de CADA chamada a um alvo alloc-alcançável, todos os
 * locals i32 da função (params incluídos — são os handles) são
 * derramados no root buffer estático (um store por slot, a pilha fica
 * neutra: o get empilha, o store desempilha). O over-mark conservador =
 * a MESMA classe de comportamento do scan de stack conservador do nativo
 * (os slots velhos marcando blocos mortos são inofensivos).
 *
 * POR QUÊ O CALLEE COBRE OS ARGS DO CALLER (a lei do spill): no momento
 * da chamada, a pilha wasm contém EXATAMENTE os args — que VIRAM os params
 * do callee na chamada. O spill do callee nos seus próprios allocs cobre
 * os args; o spill do caller cobre os locals do caller que vivem através
 * da chamada. Os efêmeros intermediários de expressão NÃO vivem através
 * de call (o modelo de pilha do lowering consome tudo na ordem) — a
 * combinação é completa.
 *
 * A alcançabilidade é o fecho transitivo sobre o grafo de calls das funcs
 * finais: a semente = as funcs cujo corpo contém `Call("kof.heapAlloc")`;
 * `f` chama `g` alcançável ⇒ `f` alcançável. As funcs do próprio
 * coletor (`kof.heapAlloc/kof.gc{Collect,TryFree,TryBump}`) ficam FORA —
 * a collect é pura e o alloc é o ponto de entrada.
 */
final class WasmGcSpill {

    private WasmGcSpill() {
    }

    /** As funcs que nunca levam spill (o coletor = puro, sem raízes). */
    private static final Set<String> GC_SELF = Set.of(
            "kof.heapAlloc", "kof.gcTryFree", "kof.gcTryBump", "kof.gcCollect");

    /** O resultado da fase de análise (a injeção vem depois, com o
     *  rootBuf resolvido). */
    static final class Scan {
        /** O alvo de call ⇒ precisa de spill antes. */
        final Set<String> reachable = new LinkedHashSet<>();
        /** O máximo de slots i32 (params + locals) entre as funcs. */
        int maxSlots = 0;
    }

    /** GC-2 (29/10, #776): os allocs INTERNOS das funcs de runtime (o nome
     *  `kof.*`) viram `kof.heapAllocNG` — NUNCA coletam. A lei: o collect
     *  dentro de uma func de runtime pode disparar enquanto a pilha do
     *  CHAMADOR segura intermediários pendentes (`"tag" + i + "x"`: o
     *  handle de "tag" está na pilha do caller enquanto o intToStr aloca)
     *  — invisíveis a todo spill (o nativo os cobre pelo scan de stack
     *  conservador; a WASM não pode). O NG cresce em vez de coletar; a
     *  coleta fica nos sites de nível de programa com a pilha limpa
     *  (depthAt == 0). O bloco continua na gc-list (o alloc colocou) — a
     *  coleta seguinte o recupera. Deve rodar ANTES do analyze/inject. */
    static void rewriteRuntimeAllocs(List<WasmFunc> funcs) {
        for (WasmFunc f : funcs) {
            if (!f.name().startsWith("kof.") || GC_SELF.contains(f.name())) continue;
            List<WasmInstr> body = f.instrs();
            for (int i = 0; i < body.size(); i++) {
                WasmInstr in = body.get(i);
                if (in instanceof WasmInstr.Call c && "kof.heapAlloc".equals(c.targetName)) {
                    body.set(i, new WasmInstr.Call("kof.heapAllocNG"));
                }
            }
        }
    }

    /** Fase A: o fecho de alcançabilidade + o maxSlots (sem tocar corpos). */
    static Scan analyze(List<WasmFunc> funcs) {
        Scan s = new Scan();
        // os i32-índices por func + os calls por func (o grafo)
        Map<String, Set<String>> callees = new LinkedHashMap<>();
        for (WasmFunc f : funcs) {
            Set<String> cs = new LinkedHashSet<>();
            for (WasmInstr in : f.instrs()) {
                if (in instanceof WasmInstr.Call c) cs.add(c.targetName);
            }
            callees.put(f.name(), cs);
            int n = i32Count(f);
            if (n > s.maxSlots) s.maxSlots = n;
        }
        if (s.maxSlots < 1) s.maxSlots = 1;
        Set<String> reach = new LinkedHashSet<>();
        for (WasmFunc f : funcs) {
            if (GC_SELF.contains(f.name())) continue;
            Set<String> cs = callees.getOrDefault(f.name(), Set.of());
            if (cs.contains("kof.heapAlloc")) reach.add(f.name());
        }
        // o fecho: f chama g alcançável ⇒ f alcançável
        boolean grew = true;
        while (grew) {
            grew = false;
            for (WasmFunc f : funcs) {
                if (GC_SELF.contains(f.name()) || reach.contains(f.name())) continue;
                for (String c : callees.getOrDefault(f.name(), Set.of())) {
                    if (reach.contains(c)) { reach.add(f.name()); grew = true; break; }
                }
            }
        }
        s.reachable.addAll(reach);
        return s;
    }

    /** Fase B: injeta o spill antes de cada Call a alvo alcançável (o
     *  rootBuf = a CONSTANTE assada: `i32.const rootBuf+4L; local.get L;
     *  i32.store`). */
    static void inject(List<WasmFunc> funcs, Scan s, int rootBuf) {
        for (WasmFunc f : funcs) {
            if (GC_SELF.contains(f.name())) continue;
            List<Integer> slots = i32Slots(f);
            if (slots.isEmpty()) {
                // sem locals i32: só checa que o call site continua válido
                // (não precisa de spill — a função não segura handle)
                continue;
            }
            List<WasmInstr> body = f.instrs();
            for (int i = 0; i < body.size(); i++) {
                WasmInstr in = body.get(i);
                if (in instanceof WasmInstr.Call c
                        && (s.reachable.contains(c.targetName)
                            || "kof.heapAlloc".equals(c.targetName))) {
                    List<WasmInstr> spill = new ArrayList<>();
                    for (int l : slots) {
                        spill.add(new WasmInstr.Const(0, (long) rootBuf + 4L * l));
                        spill.add(new WasmInstr.Local(WasmInstr.Local.GET, l, "gcL"));
                        spill.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
                    }
                    body.addAll(i, spill);
                    i += spill.size();
                }
            }
        }
    }

    /** Os índices i32 da func (params 0..P-1 + locals deslocados de P). */
    private static List<Integer> i32Slots(WasmFunc f) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < f.params().size(); i++) {
            if (f.params().get(i) == WasmFunc.TYPE_I32) out.add(i);
        }
        for (int i = 0; i < f.locals().size(); i++) {
            if (f.locals().get(i) == WasmFunc.TYPE_I32) out.add(f.params().size() + i);
        }
        return out;
    }

    private static int i32Count(WasmFunc f) {
        return i32Slots(f).size();
    }
}
