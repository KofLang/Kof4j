package dev.kof.compiler.nat;

import dev.kof.compiler.NativeRuntime;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #97 / T1a.1 (S-2): o inventário de fatias do runtime x86 é FIEL ao
 * caminho de produção e ao grafo real, sem tocar na emissão.
 *
 * <p>Pilares provados aqui (do plano §T1a.1, "refactor mecânico, prova =
 * bins byte-idênticos"):
 * <ol>
 *   <li><b>Paridade de renderização:</b> préâmbulo + concatenação das fatias
 *       na ordem derivada do fonte == {@code NativeRuntime.generateRuntimeAssembly()}
 *       byte-a-byte. Uma reordenação/inserção/remoção em {@code NativeRuntime}
 *       que escape ao registro quebra AQUI (e o parse do fonte é a fonte da
 *       ordem — nada transcrito à mão).</li>
 *   <li><b>Injetividade do mapa:</b> cada {@code kof_*} .globl tem exatamente
 *       1 fatia-dona (colisão de símbolo entre fatias = erro de inventário —
 *       o linker montaria, o mapa não pode mentir).</li>
 *   <li><b>Grafo honesto:</b> todo {@code needs} aponta para um símbolo
 *       provido por ALGUMA fatia (senão T1a.2 cortaria um símbolo inexistente
 *       — e o binário de hoje linka, então needs órfão é bug do parse).</li>
 *   <li><b>A tese da issue mensurada pelo mapa:</b> o fechamento a partir dos
 *       roots obrigatórios (print/alloc/panic) é uma FRAÇÃO pequena das
 *       fatias — o resto do runtime (crypto/web/mq/vk…) é inalcançável no
 *       hello, exatamente os 627 símbolos que S-3 vai derrubar.</li>
 * </ol>
 */
class NativeRuntimeSliceRegistryTest {

    @Test
    void registryRendersByteIdenticalToProductionAssembly() {
        String production = NativeRuntime.generateRuntimeAssembly();
        String registry = RuntimeSlices.renderRuntime();
        assertEquals(production.length(), registry.length(),
                "tamanho divergiu — NativeRuntime mudou a ordem/conteúdo das fatias?");
        int n = Math.min(production.length(), registry.length());
        for (int i = 0; i < n; i++) {
            if (production.charAt(i) != registry.charAt(i)) {
                fail("divergência no byte " + i + ": prod[" + snippet(production, i)
                        + "] vs registry[" + snippet(registry, i) + "]");
            }
        }
        assertEquals(production, registry);
    }

    private static String snippet(String s, int i) {
        return s.substring(Math.max(0, i - 40), Math.min(s.length(), i + 40)).replace('\n', '|');
    }

    @Test
    void everyProvidedSymbolHasExactlyOneOwnerSlice() {
        Map<String, Integer> owners = assertDoesNotThrow(RuntimeSlices::providerIndex,
                "símbolo definido por mais de uma fatia");
        int total = 0;
        for (RuntimeSlices.Slice s : RuntimeSlices.slices()) total += s.provides().size();
        // owners = fatia-provê + préâmbulo (root do GC); fatias não colidem
        // entre si nem com o préâmbulo (providerIndex lança se colidirem).
        assertEquals(total + RuntimeSlices.preambleProvides().size(), owners.size(),
                "colisão: owners != soma de provides + preâmbulo");
        assertTrue(owners.size() >= 180,
                "o runtime de produção define ~183 kof_* — inventário sumiu fatia? foi " + owners.size());
    }

    @Test
    void needsAreClosedWithinTheMap() {
        Map<String, Integer> owners = RuntimeSlices.providerIndex();
        Set<String> extern = new HashSet<>(RuntimeSlices.programSideSymbols());
        extern.addAll(RuntimeSlices.preambleProvides());
        for (RuntimeSlices.Slice s : RuntimeSlices.slices()) {
            for (String n : s.needs()) {
                assertTrue(owners.containsKey(n) || extern.contains(n),
                        "fatia " + s.className() + "." + s.method()
                                + " referencia " + n + " que NENHUMA fatia/prov-pre"
                                + " nem o programa emite (needs órfão)");
            }
        }
    }

    @Test
    void helloClosureIsAMinorityOfSlicesAndSymbols() {
        List<RuntimeSlices.Slice> slices = RuntimeSlices.slices();
        Set<Integer> roots = RuntimeSlices.mandatoryRoots();
        assertFalse(roots.isEmpty(), "fechamento vazio — seeds de print/alloc/panic sumiram?");
        Set<String> helloSyms = new HashSet<>();
        for (RuntimeSlices.Slice s : slices) {
            if (roots.contains(s.index())) helloSyms.addAll(s.provides());
        }
        int all = 0;
        for (RuntimeSlices.Slice s : slices) all += s.provides().size();
        // O hello NÃO pode puxar o runtime todo — senão a tese da issue não se
        // sustenta e não há nada para S-3/S-4 podar. Tolerante (o mapa define o
        // piso, não a poda final): raiz obrigatória < metade.
        assertTrue(helloSyms.size() * 2 < all,
                "fechamento de hello (" + helloSyms.size() + ") já é maioria dos símbolos ("
                        + all + ") — roots largados demais ou fatias monolíticas (inverteram o problema?)");
        assertTrue(all >= 180, "inventário incompleto: " + all);
    }

    @Test
    void localLabelEdgesExistAndKofOnlyClosureIsUnsafe() {
        // A DESCOBERTA S-3: existem arestas `.L` entre fatias que só funcionam
        // porque o runtime é concatenado hoje. Provar que são REAIS e que o
        // fecho puramente-kof seria INSEGURO (poda a fatia-dona de um `.L` que
        // uma fatia viva lê → `as`: undefined label). O `mandatoryRoots` usa o
        // fecho UNIFICADO; este teste trava que ele é estritamente maior.
        assertTrue(RuntimeSlices.crossSliceLocalEdgeCount() > 0,
                "sem arestas .L cross-slice? então a poda kof-only bastaria — "
                        + "mas existem; se sumiram, este teste avisa.");
        Set<String> hello = Set.of("kof_panic", "kof_alloc", "kof_print",
                "kof_println", "kof_print_string", "kof_println_string");
        Set<Integer> unified = RuntimeSlices.reachableFrom(hello, new HashSet<>());
        Set<Integer> kofOnly = RuntimeSlices.reachableKofOnly(hello);
        assertTrue(unified.containsAll(kofOnly),
                "unificado deve conter o kof-only (mais arestas)");
        assertTrue(unified.size() > kofOnly.size(),
                "o fecho .L-aware deve ser ESTRITAMENTE maior que o kof-only "
                        + "(senão as 119 arestas não puxam fatias extras e a "
                        + "descoberta S-3 não se sustenta) — unificado=" + unified.size()
                        + " kofOnly=" + kofOnly.size());
        // E o mandatoryRoots (usado de verdade pela poda) É o unificado:
        assertEquals(unified, RuntimeSlices.mandatoryRoots(),
                "mandatoryRoots deve ser o fecho .L-aware, não o kof-only");
    }

    @Test
    void everyLocalNeedResolvesToADefinedLocalOrIsProgramSide() {
        Map<String, Integer> lp = RuntimeSlices.localProviderIndex();
        Set<String> extern = new HashSet<>(RuntimeSlices.programSideLocals());
        for (RuntimeSlices.Slice s : RuntimeSlices.slices()) {
            for (String l : s.localNeeds()) {
                assertTrue(lp.containsKey(l) || extern.contains(l),
                        "fatia " + s.className() + "." + s.method()
                                + " referencia .L " + l + " que nenhuma fatia define "
                                + "nem é do programa (localNeeds órfão = o fecho não pode fechar essa aresta)");
            }
        }
    }

    @Test
    void sliceCountMatchesProductionParse() {
        // O parse do fonte exige >=100 chamadas (RuntimeSlices); a contagem de
        // hoje é 113. A FIEL-renderização (outro teste) é o guard de conteúdo —
        // aqui só trava que o registro é completo. Há no-ops deliberados
        // (emitPrintDouble = "emitido junto de emitPrintFloat"), então NÃO se
        // exige texto não-vazio por fatia; exige que a soma reproduza a produção.
        List<RuntimeSlices.Slice> slices = RuntimeSlices.slices();
        assertTrue(slices.size() >= 113, "esperadas >=113 fatias, vieram " + slices.size());
        int nonEmpty = 0;
        for (RuntimeSlices.Slice s : slices) if (!s.text().isEmpty()) nonEmpty++;
        assertTrue(nonEmpty >= 100, "fatias vivas de menos: " + nonEmpty + "/" + slices.size());
    }
}
