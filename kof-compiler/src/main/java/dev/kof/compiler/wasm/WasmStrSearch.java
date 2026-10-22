package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * Blocos de CONSULTA de String do runtime WASI (TIER 15, #776): casamento de
 * bytes numa posicao e contagem de code units num prefixo. Separados de
 * {@link WasmStrInspect} (i) pelo gate `check_500` e (ii) porque sao LACOS
 * PROPRIOS de responsabilidade unica.
 *
 * <p>A razao de existirem: o `indexOf`/`lastIndexOf` falhavam por aritmetica de
 * profundidade de `br` em wasm escrito a mao. Extrair cada laco para uma funcao
 * de bloco-de-saida unico elimina a aritmetica — o chamador so' pergunta
 * sim/nao (`strMatchAt`) e resolve o indice (`strUnitsBefore`).
 */
final class WasmStrSearch {

    private WasmStrSearch() {
    }

    /**
     * `kof.strMatchAt(a, b, p) -> 1/0`: os `lb` bytes de `b` aparecem em `a`
     * comecando no byte `p`?
     *
     * <p>Funcao PROPRIA de laco unico: o chamador (`indexOf`/`lastIndexOf`) varre
     * `p` e so' pergunta sim/nao aqui. Isso elimina o `br` de dentro de IFs
     * aninhados que quebrou as tentativas anteriores — cada funcao tem UM block
     * de saida e um unico laco.
     *
     * <p>Locais: 0=a, 1=b, 2=p; 3=lb (i32), 4=i (i32)
     */
    static WasmFunc kofStrMatchAt() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "b"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "lb"));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "i"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "mx", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "ml", 0x40));
        // i >= lb -> todos iguais: devolve 1
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "lb"));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Branch("mx", 1, true));
        // byte difere -> devolve 0 (i fica < lb)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "a"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "p"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "b"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 4));
        b.add(new WasmInstr.Simple(0x47, "i32.ne"));
        b.add(new WasmInstr.Branch("mx", 1, true));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "i"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "i"));
        b.add(new WasmInstr.Branch("ml", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ml", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "mx", 0x40));
        // resultado: i == lb
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "lb"));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        return new WasmFunc("kof.strMatchAt", List.of(0x7f, 0x7f, 0x7f), List.of(0x7f),
                List.of(0x7f, 0x7f), b);
    }

    /**
     * `kof.strUnitsBefore(h, p) -> i64`: quantos CODE UNITS UTF-16 existem nos
     * primeiros `p` BYTES de `h`.
     *
     * <p>Funcao PROPRIA de laco unico (mesma razao do `strMatchAt`). Conta
     * LEADERS: 1 unit por lider de 1/2/3 bytes, 2 por lider de 4, 0 por byte de
     * continuacao.
     *
     * <p>Locais: 0=h, 1=p (i32); 2=i (i32), 3=c (i32), 4=u (i64)
     */
    static WasmFunc kofStrUnitsBefore() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "i"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "ux", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "ul", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "p"));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Branch("ux", 1, true));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "c"));
        // leader? (c & 0xC0) != 0x80
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "c"));
        b.add(new WasmInstr.Const(0, 0xC0));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0x80));
        b.add(new WasmInstr.Simple(0x47, "i32.ne"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ld", 0x40));
        // +1 por lider, e +1 EXTRA se for lider de 4 bytes (par surrogate)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "u"));
        b.add(new WasmInstr.Const(1, 1));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "u"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "c"));
        b.add(new WasmInstr.Const(0, 0xF8));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xF0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "q4", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "u"));
        b.add(new WasmInstr.Const(1, 1));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "q4", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ld", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "i"));
        b.add(new WasmInstr.Branch("ul", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ul", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ux", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "u"));
        return new WasmFunc("kof.strUnitsBefore", List.of(0x7f, 0x7f), List.of(0x7e),
                // locais 2,3: i,c (i32); 4: u (i64)
                List.of(0x7f, 0x7f, 0x7e), b);
    }
}
