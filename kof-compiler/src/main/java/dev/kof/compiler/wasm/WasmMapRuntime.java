package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * Fase E (27/10, #776): MAP fatia 1 — `Map<String, Int>` basico no WASI (o
 * restante da unidade 15.3 que o subset escalar recusava). O contrato
 * OBSERVAVEL e o do `java.util.HashMap` da JVM: put SOBRESCREVE chave
 * existente (igualdade de CONTEUDO — o `kof.strEq` existente, o mesmo do
 * fold de `==`; os bits do handle seriam identidade, errado) e get de
 * chave ausente devolve NULL — a nullable anda na CAIXA §284: `kof.boxInt`
 * no hit, o handle 0 na falta (o `kof.boxToStr(0)` renderiza "null", a
 * paridade JVM de String.valueOf(null), medida).
 *
 * <pre>celula 16B: [dataPtr i32][len i64];  dados: cap pares de 16B [k i64][v i64]</pre>
 *
 * A FATIA 3 (a iteracao — keys/values) vive no {@link WasmMapExtract}
 * (split check_500: 645 ≥ 600).
 *
 * O handle e' INDIRETO (o grow copia e reescreve a celula — o chamador nao
 * invalida); o bump heap nao libera o bloco velho (documentado). A busca e'
 * LINEAR — a semantica do HashMap nao expõe ordem nem complexidade; o O(1)
 * e' propriedade de performance, nao de contrato.
 */
final class WasmMapRuntime {

    private WasmMapRuntime() {
    }

    /** Os nomes de desugar atendidos (a whitelist do scan e o despacho). */
    static final Set<String> NAMES = Set.of(
            "kof_map_new", "kof_map_put", "kof_map_get", "kof_map_size",
            "kof_map_contains", "kof_map_remove", "kof_map_keys", "kof_map_values");

    /** `kof.mapNew() -> i32` — a celula + o bloco inicial cap=8 pares. */
    private static void growBody(List<WasmInstr> b, String lbl, int hLocal,
                                 int lenLocal, int capLocal) {
        // 3=od, 4=nc, 5=nd, 6=i(i64)
        get(b, hLocal, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 3, "od");
        get(b, capLocal, "cap");
        get(b, capLocal, "cap");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 4, "nc");
        get(b, 4, "nc");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 5, "nd");
        i64c(b, 0);
        set(b, 6, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl + "c", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, lbl + "l", 0x40));
        get(b, 6, "i");
        get(b, lenLocal, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch(lbl + "c", 1, true));
        // nd[i*16 .. +15] = od[i*16 .. +15] (8 loads/stores de 8B? nao — 2 de 8B)
        for (int off = 0; off <= 8; off += 8) {
            get(b, 5, "nd");
            get(b, 6, "i");
            i64c(b, 16);
            b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
            b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
            b.add(new WasmInstr.Simple(0x6a, "i32.add"));
            if (off == 8) {
                i32c(b, 8);
                b.add(new WasmInstr.Simple(0x6a, "i32.add"));
            }
            get(b, 3, "od");
            get(b, 6, "i");
            i64c(b, 16);
            b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
            b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
            b.add(new WasmInstr.Simple(0x6a, "i32.add"));
            if (off == 8) {
                i32c(b, 8);
                b.add(new WasmInstr.Simple(0x6a, "i32.add"));
            }
            b.add(new WasmInstr.Mem(off == 0 ? WasmInstr.Mem.LOAD64 : WasmInstr.Mem.LOAD64, 0));
            b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        }
        get(b, 6, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 6, "i");
        b.add(new WasmInstr.Branch(lbl + "l", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "l", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "c", 0x40));
        get(b, hLocal, "h");
        get(b, 5, "nd");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));        // [h] = nd
        get(b, hLocal, "h");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 4, "nc");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // [h+16] = nc
    }

    private static WasmFunc mapNew() {
        List<WasmInstr> b = new ArrayList<>();
        // A LEI DA CELULA (o fix da rodada 54 — o MESMO bug que a rodada 53
        // pegou no setNew): a celula = 24 BYTES ([0]=data i32, [8]=len i64,
        // [16]=cap i64) — o heapAlloc(16) original fazia o store da cap em
        // cell+16 CAIR NA CHAVE DO PRIMEIRO PAR (a proxima alocacao): a cap
        // lida virava o valor da primeira chave, o grow NUNCA disparava e
        // os appends > 8 estouravam no bloco vizinho do heap
        i32c(b, 24);
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 0, "cell");
        i32c(b, 128);                                            // 8 pares × 16B
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 1, "data");
        get(b, 0, "cell");
        get(b, 1, "data");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));        // [cell] = data
        get(b, 0, "cell");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i64c(b, 0);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // len = 0
        get(b, 0, "cell");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i64c(b, 8);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // [cell+16] = cap 8
        get(b, 0, "cell");
        return new WasmFunc("kof.mapNew", List.of(), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** O GROW comum do put: dobra o bloco de pares, copia, reescreve a celula. */

    /** `kof.mapPut(h i32, key i32, v i64)` — sobrescreve ou anexa. */
    private static WasmFunc mapPut() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=key(i32), 2=v(i64); 3..6 grow; 7=len, 8=cap, 9=data, 10=i, 11=tmp
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 7, "len");
        get(b, 0, "h");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 8, "cap");
        // if len == cap: dobrar
        get(b, 7, "len");
        get(b, 8, "cap");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "grow", 0x40));
        growBody(b, "gw", 0, 7, 8);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "grow", 0x40));
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 9, "data");
        // a busca pela chave existente (conteudo — strEq). DOIS blocos (a
        // lei do scanBody): as saidas de FRONTEIRA bram no bloco DE DENTRO
        // (caindo no anexa, que mora depois do loop de dentro do bloco de
        // fora); o br do MATCH mira o bloco DE FORA (salta o anexa — um
        // bloco so' fazia o match cair nele: o size 3 medido)
        i64c(b, 0);
        set(b, 10, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "pk", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "pki", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "pkl", 0x40));
        get(b, 10, "i");
        get(b, 7, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("pki", 1, true));
        get(b, 9, "data");
        get(b, 10, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        get(b, 1, "key");
        b.add(new WasmInstr.Call("kof.strEq"));
        set(b, 11, "eq");
        get(b, 11, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "pm", 0x40));
        // achou: res = boxInt(VELHO); [data+i*16+8] = v; sai (o len fica)
        get(b, 9, "data");
        get(b, 10, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));       // o velho
        b.add(new WasmInstr.Call("kof.boxInt"));
        set(b, 12, "res");
        get(b, 9, "data");
        get(b, 10, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 2, "v");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        // DENTRO do if: 0=if, 1=loop, 2=bloco interno, 3=bloco externo
        b.add(new WasmInstr.Branch("pk", 3));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pm", 0x40));
        get(b, 10, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 10, "i");
        b.add(new WasmInstr.Branch("pkl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pkl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pki", 0x40));
        // anexa o par novo (DENTRO do bloco externo: so' as saidas de
        // FRONTEIRA caem aqui — o match saltou pro bloco externo):
        // [data+len*16]=key; [+8]=v; len+1
        get(b, 9, "data");
        get(b, 7, "len");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 1, "key");
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 9, "data");
        get(b, 7, "len");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 2, "v");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 7, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        // anexou: res = 0 (null — nao havia valor anterior)
        i32c(b, 0);
        set(b, 12, "res");
        // fecha o bloco EXTERNO (o patch dos dois blocos esqueceu este END:
        // 4 aberturas, 3 fechamentos — o validator via o get-res DENTRO do
        // bloco void: values remaining at end of block) e o get do resultado
        // vem DEPOIS dele (o bloco e' void)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pk", 0x40));
        get(b, 12, "res");
        // o mapa REAL: 3=od(I32), 4=nc(I64), 5=nd(I32), 6=i(I64) [o grow],
        // 7=len(I64), 8=cap(I64), 9=data(I32), 10=i(I64), 11=eq(I32),
        // 12=res(I32 — o HANDLE da caixa do velho) — a ordem antiga punha
        // od em I64 (o validador rejeitou o set 3)
        return new WasmFunc("kof.mapPut", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32), b);
    }

    /** `kof.mapGet(h i32, key i32) -> i32` — o HANDLE da caixa (0 = null). */
    private static WasmFunc mapGet() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=key; 2=len, 3=data, 4=i, 5=res, 6=tmp
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 3, "data");
        i64c(b, 0);
        set(b, 4, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "gk", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "gkl", 0x40));
        get(b, 4, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("gk", 1, true));
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        get(b, 1, "key");
        b.add(new WasmInstr.Call("kof.strEq"));
        set(b, 6, "eq");
        get(b, 6, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "gm", 0x40));
        // achou: res = boxInt([data+i*16+8]); sai
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Call("kof.boxInt"));
        set(b, 5, "res");
        // DENTRO do if: 0=if, 1=loop, 2=bloco gk — o br 2 salta o 0-default
        b.add(new WasmInstr.Branch("gk", 2));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "gm", 0x40));
        get(b, 4, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 4, "i");
        b.add(new WasmInstr.Branch("gkl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "gkl", 0x40));
        // nao achei: res = 0 (o NULL — boxToStr renderiza "null")
        i32c(b, 0);
        set(b, 5, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "gk", 0x40));
        get(b, 5, "res");
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=res(I32 — o
        // HANDLE da caixa!), 6=eq(I32) — res era I64 e o validador rejeitou
        // o set do handle
        return new WasmFunc("kof.mapGet", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    private static WasmFunc mapSize() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        return new WasmFunc("kof.mapSize", List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64), List.of(), b);
    }


    /** `kof.mapContains(h i32, key i32) -> i32` — o strEq scan (bool). */
    private static WasmFunc mapContains() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=key; 2=len, 3=data, 4=i, 5=found, 6=eq
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 3, "data");
        i64c(b, 0);
        set(b, 4, "i");
        i32c(b, 0);
        set(b, 5, "found");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "ck", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "ckl", 0x40));
        get(b, 4, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("ck", 1, true));
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        get(b, 1, "key");
        b.add(new WasmInstr.Call("kof.strEq"));
        set(b, 6, "eq");
        get(b, 6, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "cm", 0x40));
        i32c(b, 1);
        set(b, 5, "found");
        b.add(new WasmInstr.Branch("ck", 2));                    // 0=if,1=loop,2=block
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cm", 0x40));
        get(b, 4, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 4, "i");
        b.add(new WasmInstr.Branch("ckl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ckl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ck", 0x40));
        get(b, 5, "found");
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=found(I32),
        // 6=eq(I32) — found era I64 (a emissao grava i32.const)
        return new WasmFunc("kof.mapContains", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** `kof.mapRemove(h i32, key i32) -> i32` — o HANDLE da caixa do valor
     *  removido (0 = null); desloca os pares e len-1 quando achou. */
    private static WasmFunc mapRemove() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=key; 2=len, 3=data, 4=i, 5=idx, 6=res, 7=eq, 8=j, 9=tmp
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 3, "data");
        i64c(b, -1);
        set(b, 5, "idx");
        i32c(b, 0);
        set(b, 6, "res");
        i64c(b, 0);
        set(b, 4, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "rk", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "rkl", 0x40));
        get(b, 4, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("rk", 1, true));
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        get(b, 1, "key");
        b.add(new WasmInstr.Call("kof.strEq"));
        set(b, 7, "eq");
        get(b, 7, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "rm", 0x40));
        get(b, 4, "i");
        set(b, 5, "idx");
        b.add(new WasmInstr.Branch("rk", 2));                    // 0=if,1=loop,2=block
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rm", 0x40));
        get(b, 4, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 4, "i");
        b.add(new WasmInstr.Branch("rkl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rkl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rk", 0x40));
        // achou: res = boxInt(valor); desloca os pares; len-1
        // (o idx e' I64: o comparador tambem — o i32.const aqui dava
        // expected i64 found i32)
        get(b, 5, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "has", 0x40));
        get(b, 3, "data");
        get(b, 5, "idx");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Call("kof.boxInt"));
        set(b, 6, "res");
        get(b, 5, "idx");
        set(b, 8, "j");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "rs", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "rsl", 0x40));
        get(b, 8, "j");
        get(b, 2, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("rs", 1, true));
        get(b, 3, "data");
        get(b, 8, "j");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 3, "data");
        get(b, 8, "j");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 3, "data");
        get(b, 8, "j");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 3, "data");
        get(b, 8, "j");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 8, "j");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 8, "j");
        b.add(new WasmInstr.Branch("rsl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rsl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rs", 0x40));
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 2, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "has", 0x40));
        get(b, 6, "res");
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=idx(I64),
        // 6=res(I32), 7=eq(I32 — o resultado do strEq!), 8=j(I64),
        // 9=livre, 10=res2 — a ordem antiga punha eq em I64
        return new WasmFunc("kof.mapRemove", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32), b);
    }


    /** Toda a familia (o chamador registra quando um kof_map_* aparece). */
    static List<WasmFunc> all() {
        List<WasmFunc> fs = new ArrayList<>();
        fs.add(mapNew());
        fs.add(mapPut());
        fs.add(mapGet());
        fs.add(mapSize());
        fs.add(mapContains());
        fs.add(mapRemove());
        // FATIA MV (28/10, #776): os valores STRING — os variantes CRUZ (o
        // res = o handle do valor, nunca o boxInt; o mapval passthrough do
        // boxToStr renderiza o String cru e o `null` no 0)
        // FATIA MV: os valores String — WasmMapStrRuntime (split check_500)
        fs.add(WasmMapStrRuntime.mapPutStr());
        fs.add(WasmMapStrRuntime.mapGetStr());
        fs.add(WasmMapStrRuntime.mapRemoveStr());
        // FATIA MAP-D (28/10, #776): os valores Double — o get/remove BOXAM
        // (boxDouble dos bits — o Double? tem o null REAL: os 0-bits = 0.0
        // nao pode ser a null-sentinel); o put = o mapPut CRU (o reinterpret
        // fica no CALL SITE)
        fs.add(WasmMapDblRuntime.mapGetDbl());
        fs.add(WasmMapDblRuntime.mapRemoveDbl());
        // FATIA MAP-KI (28/10, #776): as chaves INT — a busca i64.eq (a
        // chave raw no slot; o valor Str estende — o MESMO slot do putStr)
        fs.add(WasmMapIntRuntime.mapPutKi());
        fs.add(WasmMapIntRuntime.mapGetKi());
        fs.add(WasmMapIntRuntime.mapRemoveKi());
        fs.add(WasmMapIntRuntime.mapContainsKi());
        // FATIA MAP-KI-VAL (28/10, #776): as chaves INT com os valores
        // ESCALARES — o put CRU (o mapPutKiRaw) + os get/remove BOXADOS por
        // largura (o Int/Char = o boxInt, o Bool = o boxBool do qword, o
        // Double = o boxDouble dos bits; o Char anda na fatia Int pela lei
        // MAP-C — só o rastro nullablechar do despacho difere)
        fs.add(WasmMapKiValRuntime.mapPutKiRaw());
        fs.add(WasmMapKiValRuntime.mapGetKiInt());
        fs.add(WasmMapKiValRuntime.mapGetKiBool());
        fs.add(WasmMapKiValRuntime.mapGetKiDbl());
        fs.add(WasmMapKiValRemove.mapRemoveKiInt());
        fs.add(WasmMapKiValRemove.mapRemoveKiBool());
        fs.add(WasmMapKiValRemove.mapRemoveKiDbl());
        // FATIA MAP-B (28/10, #776): os valores BOOL — o get/remove BOXAM
        // (o boxBool do qword — o 0 = false nunca e' a sentinela)
        fs.add(WasmMapBoolRuntime.mapGetBool());
        fs.add(WasmMapBoolRuntime.mapRemoveBool());
        fs.add(WasmMapExtract.mapKeys());
        fs.add(WasmMapExtract.mapValues());
        return fs;
    }
}
