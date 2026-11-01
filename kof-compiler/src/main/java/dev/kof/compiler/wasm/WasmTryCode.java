package dev.kof.compiler.wasm;

import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofExcUnlink;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;

import java.util.List;

import dev.kof.compiler.wasm.WasmLowering.Ctx;
import dev.kof.compiler.wasm.WasmInstr.Mem;

/**
 * TRY-1 (01/11, #776 — round 85): o try/catch/throw de {@code String} no
 * nível de instrução — o ÚLTIMO construto de linguagem fora do subset
 * escalar 15.2. A proposta EH do wasm (medida: wasmtime 49.0.2 executa
 * tag + try_table/throw) dá o desenrolar ENTRE FRAMES; a pilha de
 * exceções nativa (§551) dá o despacho DENTRO do frame — o protocolo é
 * re-expresso sobre a máquina de estados `$pc`:
 *
 * <ul>
 *   <li>a escrivania vive na memória linear (zero-init, SEM globals e SEM
 *       segmento de dados): contagem @{code @0}, msg estacionada
 *       @{code @4}, registros de handler de 8 bytes @{code @8+8n}
 *       {@code {handlerBlock}} — a região de 4KB é reservada LAZILY pelo
 *       cursor de módulo no primeiro try (módulos sem try NÃO se
 *       deslocam: os binários ficam byte-estáveis);</li>
 *   <li>{@code KofTryStart} empurra {@code {blockOf(handlerLabel)}} — a
 *       label do handler é FRONTEIRA de bloco (o laço de corte do
 *       WasmLoweringLocals, porque o corte atual descartaria o catch 2 —
 *       medido);</li>
 *   <li>{@code KofExcUnlink} desempilha — o IR emite um unlink por try
 *       aberto em TODOS os caminhos de saída (returns/breaks/continues,
 *       medido em StatementLowerer), então a contagem volta à profundidade
 *       de entrada sozinha nos sites de `return` (sem guarda extra);</li>
 *   <li>{@code KofThrow} = o {@code throw $kof.exc} do wasm com o handle
 *       da msg na pilha (o IR põe o handle ali — o wrapper
 *       RuntimeException é JVM/Android only);</li>
 *   <li>só funções COM try ganham o wrapper ({@code openFrame}: loop
 *       redrive + block + try_table com uma cláusula catch apontando para
 *       o block que a cerca — profundidade 0 medida no round-trip do
 *       wasm-tools); frames SEM try são transparentes (o throw propaga
 *       pelo wasm EH até o try_table do chamador);</li>
 *   <li>o despacho do catch estaciona a msg @{code @4}, compara a contagem
 *       com a profundidade de entrada (o rethrow de fuga: sem registro
 *       DESTE frame → re-throw da msg estacionada), desempilha o registro
 *       do topo, ajusta {@code $pc} e re-entra no loop de despacho — um
 *       pop por despacho, o MESMO protocolo do nativo (§551);</li>
 *   <li>{@code KofCatchStart} carrega a msg estacionada no local da
 *       exceção.</li>
 * </ul>
 *
 * <p>FATIA 1: exceções {@code String} ONLY, sem finally/catchAll — a
 * maquinaria {@code "Throwable"} (o catchAll do desugar de finally)
 * recusa honestamente no CatchStart (WASM002 nomeando a próxima fatia).
 * FATIA T-2 (01/11, round 86): o finally/catchAll ENTRA — o desugar do
 * StatementLowererTry emite {@code KofCatchStart(catchAllLabel, "Throwable",
 * excTmp)} + o epílogo rethrow ({@code LoadLocal(Throwable, excTmp)} +
 * {@code KofThrow}); o catchAll é um catch SEM checagem de tipo (qualquer
 * msg estacionada entra; o valor NÃO se liga ao usuário — o local excTmp
 * é interno). A lei nova: o slot do local {@code #excTmp} = o handle i32
 * (a MESMA disciplina do String — o handle da msg estacionada@4 anda
 * nele). A msg estacionada nunca cruza um alloc: o despacho a lê no estado
 * seguinte (o handle sobrevive; o coletor nunca a alcança — GC-1 não
 * compacta e a leitura é imediata).
 */
final class WasmTryCode {

    private WasmTryCode() {
    }

    /** A cláusula do tag único: kind 0 (catch), tag 0, label 0 = o block
     *  que cerca o try_table (medido no round-trip). */
    private static final int[][] CATCH_CLAUSE = {{0, 0, 0}};

    static void openFrame(Ctx ctx, List<WasmInstr> out) {
        if (!ctx.hasTry) return;
        ctx.excBase = ctx.data.reserveExc();
        ctx.data.usesTry = true;
        // a profundidade de entrada: a contagem de handlers na entrada
        out.add(new WasmInstr.Const(0, ctx.excBase));
        out.add(new Mem(Mem.LOAD, 0));
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.excEntry, "excEntry"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "redrive", 0x40));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "handler", 0x7f));
        out.add(new WasmInstr.TryTable(0x40, CATCH_CLAUSE));
    }

    static void closeFrame(Ctx ctx, List<WasmInstr> out) {
        if (!ctx.hasTry) return;
        // o corpo do try_table nunca completa (o loop de despacho só sai
        // por return/br internos; o `unreachable` fecha o corpo)
        out.add(new WasmInstr.Simple(0x00, "unreachable"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "try_table", 0x40));
        // o block `handler` tem resultado i32 (a msg chega pela cláusula)
        out.add(new WasmInstr.Simple(0x00, "unreachable"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "handler", 0x7f));
        // ---- o caminho do catch: [msg i32] ----
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.joinIdx, "join"));
        out.add(new WasmInstr.Const(0, ctx.excBase));
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.joinIdx, "join"));
        out.add(new Mem(Mem.STORE, 4)); // a msg estacionada
        out.add(new WasmInstr.Const(0, ctx.excBase));
        out.add(new Mem(Mem.LOAD, 0));
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.excEntry, "excEntry"));
        out.add(new WasmInstr.Simple(0x47, "i32.ne"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        {
            // o registro do TOPO = o handler deste frame: handlerBlock
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new Mem(Mem.LOAD, 0));
            out.add(new WasmInstr.Const(0, 3));
            out.add(new WasmInstr.Simple(0x74, "i32.shl"));
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new Mem(Mem.LOAD, 0));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.pcIdx, "pc"));
            // a contagem volta um registro (o MESMO pop do despacho nativo)
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new Mem(Mem.LOAD, 0));
            out.add(new WasmInstr.Const(0, 1));
            out.add(new WasmInstr.Simple(0x6b, "i32.sub"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.joinIdx, "join"));
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.joinIdx, "join"));
            out.add(new Mem(Mem.STORE, 0));
            // re-entra no despacho: o if é profundidade 0, o redrive 1
            out.add(new WasmInstr.Branch("redrive", 1));
        }
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "if", 0x40));
        // sem registro DESTE frame (o throw veio de fora de todo try): a
        // FUGA — re-throw da msg estacionada (o wasm EH desenrola para o
        // try_table do chamador)
        out.add(new WasmInstr.Const(0, ctx.excBase));
        out.add(new Mem(Mem.LOAD, 4));
        out.add(new WasmInstr.Throw(0));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "redrive", 0x40));
    }

    /** Os cinco ops try/catch/throw dentro dos estados. */
    static boolean emit(KofOperation op, Ctx ctx, List<WasmInstr> out) {
        if (op instanceof KofTryStart ts) {
            refuseNonString(ts.exceptionType(), ctx.funcName, "try");
            int handlerBlock = ctx.blockOf(ts.handlerLabel());
            // o registro do handler no topo: addr = base + 8 + contagem*8
            // (o header 8 = contagem@0 + msg@4; os registros começam @8)
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new Mem(Mem.LOAD, 0));
            out.add(new WasmInstr.Const(0, 3));
            out.add(new WasmInstr.Simple(0x74, "i32.shl"));
            out.add(new WasmInstr.Const(0, 8));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new WasmInstr.Const(0, handlerBlock));
            out.add(new Mem(Mem.STORE, 0));
            // contagem++
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new Mem(Mem.LOAD, 0));
            out.add(new WasmInstr.Const(0, 1));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.joinIdx, "join"));
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.joinIdx, "join"));
            out.add(new Mem(Mem.STORE, 0));
            return true;
        }
        if (op instanceof KofExcUnlink) {
            // contagem-- (o IR emite um unlink por try aberto em todo
            // caminho de saída — StatementLowerer medido)
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new Mem(Mem.LOAD, 0));
            out.add(new WasmInstr.Const(0, 1));
            out.add(new WasmInstr.Simple(0x6b, "i32.sub"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.joinIdx, "join"));
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.joinIdx, "join"));
            out.add(new Mem(Mem.STORE, 0));
            return true;
        }
        if (op instanceof KofThrow) {
            // o handle da msg na pilha (o IR põe ali); o tag [i32]->[].
            // o throw CONSOME o handle: o rastro de largura não sobrevive
            // (o crossLiveValue salvaria o handle morto no join — medido:
            // o `local.set join` espinha após o `throw`)
            ctx.lastPush = null;
            ctx.lastPushWide = false;
            out.add(new WasmInstr.Throw(0));
            return true;
        }
        if (op instanceof KofCatchStart cs) {
            refuseNonString(cs.exceptionType(), ctx.funcName, "catch");
            // a msg estacionada entra no local da exceção
            out.add(new WasmInstr.Const(0, ctx.excBase));
            out.add(new Mem(Mem.LOAD, 4));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.w(cs.localIndex()), "e" + cs.localIndex()));
            ctx.lastPush = null;
            ctx.lastPushWide = false;
            return true;
        }
        if (op instanceof KofTryEnd) {
            // marcador — o registro já foi desempilhado (o ExcUnlink do
            // caminho normal ou o pop do despacho)
            return true;
        }
        return false;
    }

    private static void refuseNonString(String excType, String funcName, String where) {
        if ("String".equals(excType) || "java.lang.String".equals(excType)) return;
        // T-2 (01/11, round 86): o catchAll do desugar de finally é um
        // catchALL REAL — não é um catch TIPADO de Throwable: NÃO checa o
        // tipo (qualquer msg estacionada entra) e NÃO liga o valor ao
        // usuário (o epílogo rethrow o lê do local excTmp interno). A msg
        // de Kof §243 segue String — um catch (Throwable e) TIPADO do
        // usuário segue fora do subset (a fatia T-3).
        if ("Throwable".equals(excType) || "java.lang.Throwable".equals(excType)) return;
        throw new WasmUnsupportedException("try/catch de exceção não-String ('" + excType
                + "') fora do subset TRY-1/T-2 no " + where + " de '" + funcName
                + "' (WASM002) — a exceção de Kof é String (§243); o catch tipado "
                + "não-String é a fatia T-3; docs/wasm-wasi-plan.md (#776)");
    }
}
