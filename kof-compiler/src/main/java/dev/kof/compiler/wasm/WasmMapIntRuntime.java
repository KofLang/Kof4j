package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * FATIA MAP-KI (28/10, #776 — split check_500): as chaves INT do map — os
 * variantes da CELULA em que a chave anda RAW (o Int = o i64 do slot; a
 * busca = o {@code i64.eq}, nunca o strEq) e o valor STRING estende no slot
 * (o MESMO contrato do {@link WasmMapStrRuntime}). Registrados pelo all() do
 * {@link WasmMapRuntime}.
 */
final class WasmMapIntRuntime {

    private WasmMapIntRuntime() {
    }

static WasmFunc mapPutKi() {
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
        WasmMapStrBodies.growBody(b, "gw", 0, 7, 8);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "grow", 0x40));
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 9, "data");
        // a busca pela chave existente (a chave INT = o i64 RAW — o
        // i64.eq, nunca o strEq). DOIS blocos (a
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
        get(b, 1, "key");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        set(b, 11, "eq");
        get(b, 11, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "pm", 0x40));
        // achou: res = o VELHO CRU (o mapval); [data+i*16+8] = v; sai (o len fica)
        get(b, 9, "data");
        get(b, 10, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));       // o velho
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
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
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
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
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
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
        return new WasmFunc("kof.mapPutKi", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32), b);
    }
static WasmFunc mapGetKi() {
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
        get(b, 1, "key");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        set(b, 6, "eq");
        get(b, 6, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "gm", 0x40));
        // achou: res = o valor CRU (o mapval); sai
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
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
        return new WasmFunc("kof.mapGetKi", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }
static WasmFunc mapRemoveKi() {
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
        get(b, 1, "key");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
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
        // achou: res = o valor CRU (o mapval passthrough); desloca os pares; len-1
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
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
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
        // 6=res(I32), 7=eq(I32 — o resultado do i64.eq!), 8=j(I64),
        // 9=livre, 10=res2 — a ordem antiga punha eq em I64
        return new WasmFunc("kof.mapRemoveKi", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32), b);
    }
static WasmFunc mapContainsKi() {
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
        get(b, 1, "key");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
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
        return new WasmFunc("kof.mapContainsKi", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }
}
