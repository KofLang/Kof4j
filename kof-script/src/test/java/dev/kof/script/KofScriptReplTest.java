package dev.kof.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressões do REPL / modo script de topo (KofScript). Vivem fora de
 * {@code KofScriptTest} para não empurrar a classe além do teto de 500 linhas
 * (ratchet de higiene, Fase 2/3 da arquitetura de testes).
 */
class KofScriptReplTest {

    @Test
    void topLevelVarReassignmentBeforeReadUsesNewValue() throws Exception {
        // #739 (REPL): `var x = 5` vira `KofScriptGlobals.x`; reatribuir ANTES
        // de qualquer leitura precisa valer — o interpretador semeava o valor
        // inicial por cima da escrita (`putstatic` não inicializava a classe).
        // Forma do REPL: `var x = 5` / `x = 7` / `println(x)` = 7.
        var r = KofScript.eval("""
                var x = 5
                x = 7
                println(x)
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("7", r.stdout().trim());
    }

    @Test
    void topLevelVarReassignmentKeepsOrderAcrossLines() throws Exception {
        // Duas escritas antes da primeira leitura: ambas valem (a classe
        // inicializa uma vez, depois cada `putstatic` aplica).
        var r = KofScript.eval("""
                var x = 1
                x = 2
                x = 3
                println(x)
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("3", r.stdout().trim());
    }
}
