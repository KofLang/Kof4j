package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #97 / S-4.1: o inventário de PEÇAS do runtime riscv64 é FIEL ao
 * caminho de produção, sem tocar na emissão (aarch64 herda a mesma
 * concatenação via tradutor). Pilares:
 * (a) concatenação das 48 peças POR REFLEXÃO/ordem-derivada = byte-a-byte o
 *     bloco que o NativeArchEmitter anexa hoje (a prova "bins idênticos");
 * (b) 1 dona por símbolo kof_* (0 homônimos globl — medido);
 * (c) needs fechados no mapa ∪ program-side;
 * (d) 0 homônimos .L cross-peça, MAS 73 arestas .L cross-peça → o fecho
 *     UNIFICADO é obrigatório no riscv também (a lição da S-2.5);
 * (e) piso print/panic/alloc = 7/48 peças (medido).
 */
class NativeRiscvRuntimeSliceRegistryTest {

    @Test
    void piecesRenderByteIdenticalToProduction() {
        assertEquals(
                NativeRiscvAsm.RISCV_RUNTIME_ASM + NativeRiscvAsm.RISCV_STRN002_ASM
                        + NativeRiscvAsm.RISCV_RUNTIME_ASM_B + NativeRiscvAsm.RISCV_MAPSET_ASM,
                RiscvSlices.renderRuntime(),
                "concatenação reflexiva na ordem derivada deve ser byte-idêntica ao runtime de produção");
    }

    @Test
    void piecesCoverProductionCount() {
        List<RiscvSlices.Piece> ps = RiscvSlices.pieces();
        assertTrue(ps.size() >= 40, "esperava ~48 peças, veio " + ps.size());
    }

    @Test
    void eachSymbolHasExactlyOneOwner() {
        Map<String, Integer> idx = RiscvSlices.providerIndex(); // lança se houver 2 donos
        assertTrue(idx.size() > 200, "símbolos definidos: " + idx.size());
    }

    @Test
    void needsAreClosedOverMapPlusProgramSide() {
        Map<String, Integer> gp = RiscvSlices.providerIndex();
        Set<String> ext = RiscvSlices.programSideSymbols();
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            for (String n : p.needs()) {
                assertTrue(gp.containsKey(n) || ext.contains(n),
                        "needs órfão em " + p.className() + "." + p.field() + ": " + n);
            }
        }
    }

    @Test
    void localLabelEdgesExistAndDemandUnifiedClosure() {
        // 0 homônimos cross-peça (namespace riscv) mas >0 arestas .L cross-peça
        // (medido 33 pares distintos — o probe de 73 contava OCORRÊNCIAS de
        // referência, o modelo dedupe por peça): podar a peça-dona de um .L lido
        // por peça viva quebraria o `as` — a BFS da poda DEVE ser o fecho
        // unificado (a regra da S-2.5 vale no riscv também). travado:
        assertTrue(RiscvSlices.crossPieceLocalEdgeCount() >= 30,
                "arestas .L cross-peça (medido 33) sumiram — o modelo riscv não exige mais fecho unificado?");
        // e o piso unificado é pequeno (dá podar 48→poucas):
        Set<String> floor = Set.of("kof_panic", "kof_alloc", "kof_print", "kof_println",
                "kof_print_string", "kof_println_string");
        Set<Integer> uni = RiscvSlices.reachableFrom(floor, Set.of());
        assertTrue(uni.size() <= 10, "piso riscv deveria ser ~7 peças, veio " + uni.size());
    }

    @Test
    void mandatoryFloorIsSmall() {
        Set<Integer> floor = RiscvSlices.mandatoryRoots();
        // piso do hello riscv: print/panic/alloc — pequeno, prova que dá podar
        assertTrue(floor.size() < RiscvSlices.pieces().size() / 2,
                "piso deveria ser minoria das peças, veio " + floor.size());
    }
}
