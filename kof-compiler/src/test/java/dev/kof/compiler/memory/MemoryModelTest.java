package dev.kof.compiler.memory;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-MEMORY-SAFETY Fase 2 (fatia 1) — prova de TRACABILIDADE spec→modelo:
 * o pacote `dev.kof.compiler.memory` espelha as tabelas de
 * `docs/spec/memory-safety.md` §2/§3/§4/§6/§8/§9/§10 sem inventar regra,
 * sem diagnostico novo e sem mudar comportamento (emissao = Fase 3).
 *
 * As asserts citam a spec pela linha: se alguém re-nomear, apagar ou
 * silenciar uma regra, este teste quebra — que e o proposito dele.
 */
class MemoryModelTest {

    @Test
    void everyFamilyFromTheSpecIsRepresented() {
        Map<MemRule.Class, Integer> byFamily = new EnumMap<>(MemRule.Class.class);
        int o = 0, l = 0, b = 0, m = 0, e = 0, c = 0, n = 0;
        for (MemRule r : MemRule.values()) {
            byFamily.merge(r.classification(), 1, Integer::sum);
            switch (r.id().charAt(0)) {
                case 'O' -> o++;
                case 'L' -> l++;
                case 'B' -> b++;
                case 'M' -> m++;
                case 'E' -> e++;
                case 'C' -> c++;
                case 'N' -> n++;
                default -> fail("regra fora das familias da spec: " + r.id());
            }
        }
        assertEquals(5, o, "§2.2 Ownership: O-01..O-05");
        assertEquals(5, l, "§3.2 Lifetime: L-01..L-05");
        assertEquals(6, b, "§3.1/3.2 Borrowing: B-01..B-06");
        assertEquals(4, m, "§4 Mutability: M-01..M-04");
        assertEquals(3, e, "§6 Escape: E-01..E-03");
        assertEquals(4, c, "§8 Concurrency: C-01..C-04");
        assertEquals(4, n, "§10 Nullability: N-01..N-04");
        assertTrue(byFamily.containsKey(MemRule.Class.NONE), "regras permissivas existem (B-01)");
    }

    @Test
    void memoryDiagnosticCodesMatchTheSpecTables() {
        // §2.2: MEM001..005 · §3.2: MEM010..014 · §3.1-3.2: MEM020..023
        assertEquals("MEM001", MemRule.O_01.diagnostic());
        assertEquals("MEM005", MemRule.O_05.diagnostic());
        assertEquals("MEM010", MemRule.L_01.diagnostic());
        assertEquals("MEM014", MemRule.L_05.diagnostic());
        assertEquals("MEM020", MemRule.B_03.diagnostic());
        assertEquals("MEM023", MemRule.B_06.diagnostic());
        // reutilizacoes declaradas na propria spec (mesmo codigo, regras irmas):
        assertEquals(MemRule.L_05.diagnostic(), ManagedResource.WEB.diagnostic());
        assertEquals(MemRule.L_05.diagnostic(), ManagedResource.DB.diagnostic());
        assertEquals(MemRule.O_05.diagnostic(), ManagedResource.FFI_BUFFER.diagnostic());
        assertEquals(MemRule.B_04.diagnostic(), MemRule.C_03.diagnostic());
        // as linhas de §4/§10 citam os codigos SEM existentes — o modelo nao
        // inventa MEMxxx para o que hoje ja e SEM037/038/049/048/054.
        assertEquals("SEM037", MemRule.M_01.diagnostic());
        assertEquals("SEM049", MemRule.N_01.diagnostic());
        assertEquals("SEM048", MemRule.N_02.diagnostic());
    }

    @Test
    void ruleIdsAreUniqueAndPermissiveRulesCarryNoDiagnostic() {
        Set<String> ids = new HashSet<>();
        for (MemRule r : MemRule.values()) {
            assertTrue(ids.add(r.id()), "id duplicado: " + r.id());
            if (r.classification() == MemRule.Class.NONE) {
                assertNull(r.diagnostic(), "permissiva com diagnostico: " + r.id());
                assertFalse(r.forbids());
            }
        }
    }

    @Test
    void ownerKindsAreExactlyTheFiveFromSectionTwo() {
        assertEquals(5, OwnerKind.values().length, "§2.1 tabela: Scope/GC Root/Container/Closure/FFI");
        for (OwnerKind k : OwnerKind.values()) {
            assertFalse(k.lifetimeEnd().isEmpty(), "dono sem descricao de fim de vida: " + k);
        }
        assertEquals("when the root becomes unreachable", OwnerKind.GC_ROOT.lifetimeEnd());
    }

    @Test
    void managedResourcesCloseFlagsMatchSectionNine() {
        assertTrue(ManagedResource.WEB.closeRequired());
        assertTrue(ManagedResource.DB.closeRequired());
        assertTrue(ManagedResource.FFI_BUFFER.closeRequired());
        assertFalse(ManagedResource.FILE.closeRequired(), "§9: File fecha por chamada — nunca MEM014");
        assertNull(ManagedResource.FILE.diagnostic());
    }

    @Test
    void captureModesMatchSectionSix() {
        assertEquals(2, CaptureMode.values().length, "§6.1: apenas SNAPSHOT (E-01) e SHARED_BOX (E-02)");
        assertSame(CaptureMode.SNAPSHOT, CaptureMode.valueOf("SNAPSHOT"));
        assertSame(CaptureMode.SHARED_BOX, CaptureMode.valueOf("SHARED_BOX"));
    }


    @Test
    void moveTransferIsTheO02Fact() {
        var m = new MoveTransfer("a", "b");
        assertEquals("a", m.destination());
        assertEquals("b", m.source());
        assertTrue(m.requiresSourceNulling(), "O-02: sem nular a origem nao e move");
        assertThrows(IllegalArgumentException.class, () -> new MoveTransfer("a", "a"), "auto-move nao transfere");
        assertThrows(IllegalArgumentException.class, () -> new MoveTransfer("", "b"));
        assertThrows(IllegalArgumentException.class, () -> new MoveTransfer("a", null));
    }

}
