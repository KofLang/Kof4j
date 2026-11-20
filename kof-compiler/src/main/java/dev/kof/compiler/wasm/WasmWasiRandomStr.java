package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * As faces de {@code kof.random} que ALOCAM String na fase 4 do WASI (#776):
 * {@code kof_random_hex(n)} (2n dígitos hex minúsculo; {@code n<=0} => null) e
 * {@code kof_random_string(n, alphabet)} (n chars uniformes do alfabeto;
 * {@code n<=0} OU alfabeto vazio => "" — a face leniente do plano).
 *
 * <p>Os contratos são os do oráculo JVM ({@code JvmStringRandomRuntime}:
 * {@code kof_random_hex} delega a {@code kof_sec_random_hex}; o
 * {@code kof_random_string} monta n chars com {@code nextInt(len)}), medidos
 * no binário: {@code random.hex(0) == null} é true e
 * {@code random.randomString(0, "abc").length()} é 0 (a String VAZIA, não o
 * null). A entropia vem do MESMO primitivo do SO ({@code random_get}, a face
 * WASI do getrandom(2)/SecureRandom; R11: nunca caseira).
 *
 * <p>O handle de String é o MESMO dos outros runtimes: {@code [len i32][bytes]\n}
 * bump-alocado no heap (global 0) — o {@code kof.heapAlloc} + o
 * {@code kof.strLit} provado verde na 15.3c. A tabela de dígitos hex é um
 * data segment (bind por uso, o mesmo padrão do {@code gTable} do Schubfach).
 *
 * <p>CONVENÇÕES (medidas no runtime existente, não supostas): o
 * {@code i32.store8} consome o ENDEREÇO e depois o VALOR; os locals são
 * indexados de forma ABSOLUTA (params + locals); os inteiros do Kof são i64
 * (o wrap para i32 acontece só na fronteira da memória).
 */
final class WasmWasiRandomStr {

    private WasmWasiRandomStr() {
    }

    /** o endereco do data segment da tabela hex ("0123456789abcdef"). */
    private static int hexTableAddr = -1;

    static void bindHexTable(int addr) {
        hexTableAddr = addr;
    }

    static byte[] hexTable() {
        return "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * {@code kof_random_hex(n) -> handle String} — 2n dígitos hex minúsculo
     * (n bytes de entropia, dois dígitos por byte, o byte alto primeiro).
     * {@code n<=0} => null (o handle 0, a MESMA lei do {@code map.get} — e a
     * lei do oráculo JVM, medido: {@code random.hex(0) == null} é true).
     */
    static WasmFunc kofRandomHex() {
        List<WasmInstr> b = new ArrayList<>();
        // locals ABSOLUTOS: 0=n(param i64), 1=i(i64), 2=h(i32), 3=byte(i64),
        // 4=len(i64), 5=res(i32)
        // n <= 0 -> o handle 0 (null)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "n"));
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Simple(0x57, "i64.le_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // h = heapAlloc(2n + 5); len = 2n
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "n"));
        b.add(new WasmInstr.Const(1, 2L));
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "len"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "len"));
        b.add(new WasmInstr.Const(1, 5L));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "len"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        // i = 0; i < n; i++ -> 2 dígitos do byte de entropia
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "i"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "hexall", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "hex_c", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "n"));
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Branch("hexall", 1, true));
        // byte = random_get(1 byte)
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_RND));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Call("random_get"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_RND));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        // o byte do random_get e' i32; o local 3 e' i64 (o Kof Int) — o extend
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "byte"));
        // digito alto = (byte >> 4) & 15 -> [h + 4 + 2i]
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "i"));
        b.add(new WasmInstr.Const(1, 2L));
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "byte"));
        b.add(new WasmInstr.Const(1, 4L));
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Const(1, 15L));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, hexTableAddr));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Store8(0));
        // digito baixo = byte & 15 -> [h + 4 + 2i + 1]
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "i"));
        b.add(new WasmInstr.Const(1, 2L));
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));   // 2i + 1 (o 2o digito)
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "byte"));
        b.add(new WasmInstr.Const(1, 15L));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, hexTableAddr));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Store8(0));
        // i++
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "i"));
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "i"));
        b.add(new WasmInstr.Branch("hex_c", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "hex_c", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "hexall", 0x40));
        // o terminador '\n' do layout [len][bytes]\n
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "len"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Const(0, '\n'));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "res"));
        return new WasmFunc("kof_random_hex", List.of(0x7e), List.of(0x7f),
                List.of(0x7e, 0x7f, 0x7e, 0x7e, 0x7f), b);
    }

    /**
     * {@code kof_random_string(n, alphabet) -> handle String} — n chars
     * uniformes do alfabeto (o índice vem do MESMO
     * {@code kof_random_int(len)} — rejection sampling, nunca o módulo
     * enviesado).
     *
     * <p>A lei leniente do plano: {@code n<=0} OU alfabeto vazio => "" (uma
     * String VAZIA, não o null). O handle da String vazia é um bloco com
     * {@code len=0} — o handle 0 é o NULL (a lei do {@code map.get}), e o
     * oráculo JVM devolve "" (não null) nesse caso: os dois são
     * distinguíveis, então o runtime preserva a distinção.
     */
    static WasmFunc kofRandomString() {
        List<WasmInstr> b = new ArrayList<>();
        // locals ABSOLUTOS: 0=n(param i64), 1=alphabet(param i32), 2=h(i32),
        // 3=i(i64), 4=len(i64), 5=idx(i64), 6=res(i32)
        // o alfabeto nulo (handle 0) => a String vazia
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "alphabet"));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // len = [alphabet] (o comprimento em code units do handle)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "alphabet"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        // o comprimento vive como i32 no handle; o local 4 e' i64 (o Kof Int)
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "len"));
        // len == 0 => a String vazia
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "len"));
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // n <= 0 => a String vazia
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "n"));
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Simple(0x57, "i64.le_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // h = heapAlloc(n + 5); [h] = n
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "n"));
        b.add(new WasmInstr.Const(1, 5L));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "n"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        // i = 0; i < n; i++ -> [h+4+i] = alphabet[4 + idx]
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "i"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "rsall", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "rs_c", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "n"));
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Branch("rsall", 1, true));
        // idx = kof_random_int(len) — a MESMA lei uniforme [0,len)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "len"));
        b.add(new WasmInstr.Call("kof_random_int"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "idx"));
        // [h+4+i] = alphabet[4 + idx]
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "alphabet"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "idx"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Store8(0));
        // i++
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "i"));
        b.add(new WasmInstr.Branch("rs_c", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rs_c", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rsall", 0x40));
        // o terminador '\n' do layout [len][bytes]\n
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "n"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Const(0, '\n'));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "res"));
        return new WasmFunc("kof_random_string", List.of(0x7e, 0x7f), List.of(0x7f),
                List.of(0x7f, 0x7e, 0x7e, 0x7e, 0x7f), b);
    }
}
