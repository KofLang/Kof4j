package dev.kof.compiler.wasm;

import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofStoreField;
import dev.kof.compiler.wasm.WasmLowering.Ctx;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;
import java.util.List;

/**
 * CLASS-1 (28/10, #776 — split check_500): as operações de CAMPO das
 * CLASSES mutáveis — a leitura = o MESMO layout do record (o
 * {@code lowerLoadField} do record reusa o ownerLayout; aqui só despacha)
 * e o STORE = a op nova: a pilha = [receiver, valor] (o mesmo par do load,
 * com o valor por cima), o store pela largura do campo (i64/i32/f64).
 * O zero-fill do ctor = os stores explícitos por campo antes do corpo
 * (o bump não é zerado — o campo não atribuído = a paridade 0/null do JVM).
 */
final class WasmClassOps {

    private WasmClassOps() {
    }

    /** true = o dono é uma CLASSE alcançada (o roteamento do load/store —
     *  o lowerLoadField do record consulta SÓ o recordOf e recusava). */
    static boolean isClassFieldOp(dev.kof.compiler.Type ownerType, Ctx ctx) {
        return ctx.classOf(ownerType) != null;
    }

    /** A leitura de campo da classe: o MESMO formato do record (o receiver
     *  na pilha → o offset → o load pela largura) — a busca pelo classOf (o
     *  lowerLoadField do record consulta SÓ o recordOf e recusava). */
    static void lowerClassLoadField(Ctx ctx, KofLoadField lf, List<WasmInstr> out) {
        ClassLayout layout = ctx.classOf(lf.ownerType());
        if (layout == null) {
            throw new WasmUnsupportedException("leitura de campo '" + lf.name()
                    + "' em classe nao alcancada '" + lf.ownerType()
                    + "' (WASM002) — docs/wasm-wasi-plan.md (#776)");
        }
        // o receiver na pilha -> o offset -> o load pela largura (o MESMO
        // formato do lowerLoadField do record)
        out.add(new WasmInstr.Const(0, layout.fieldOffset(lf.name())));
        out.add(new WasmInstr.Simple(0x6a, "i32.add"));
        if (WasmRecordOps.isWideField(lf.fieldType())) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        } else if (WasmRecordOps.isF64Field(lf.fieldType())) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 0));
        } else {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        }
        ctx.lastPush = WasmRecordOps.isStringField(lf.fieldType()) ? "string" : WasmScalarOps.typeName(lf.fieldType());
        ctx.lastPushWide = WasmRecordOps.isI64Field(lf.fieldType());
        // CLASS-2b (29/10, #776): o campo de CLASSE alcançada = o handle —
        // o TEE no scratch clsIdx (o MESMO save do LoadLocal de classe: o
        // valor na pilha para o consumidor + a cópia para a provenância da
        // tag do despacho; o scan do intervalo no emitTagLoad detecta o
        // clobber de um valor de classe posterior)
        if (ctx.classOf(lf.fieldType()) != null) {
            out.add(new WasmInstr.Local(WasmInstr.Local.TEE, ctx.clsIdx, "cls"));
        }
    }

    /** O store de campo da classe: a pilha = [receiver, valor]; o width =
     *  a largura (o wide/char = i64 via o scratch vIdx; o f64 = o
     *  vf64Idx; o i32 = o v32Idx — a MESMA disciplina do record's
     *  lowerRecordInit). */
    static void lowerStoreField(Ctx ctx, KofStoreField sf, List<WasmInstr> out) {
        ClassLayout layout = ctx.classOf(sf.ownerType());
        if (layout == null) {
            // o store de RECORD (o ctor inlinado = os stores do lowerRecordInit
            // — o record nunca chega aqui) ou de classe não-alcançada = a
            // recusa honesta (WASM002)
            throw new WasmUnsupportedException("escrita de campo '" + sf.name()
                    + "' em '" + sf.ownerType() + "' fora da CLASS-1 (WASM002)"
                    + " — docs/wasm-wasi-plan.md (#776)");
        }
        int sLocal;
        int storeOp;
        if (WasmRecordOps.isWideField(sf.fieldType())) {
            sLocal = ctx.vIdx;
            storeOp = WasmInstr.Mem.STORE64;
        } else if (WasmRecordOps.isF64Field(sf.fieldType())) {
            sLocal = ctx.vf64Idx;
            storeOp = WasmInstr.Mem.STORE_F64;
        } else {
            sLocal = ctx.v32Idx;
            storeOp = WasmInstr.Mem.STORE;
        }
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, sLocal, "v_w"));
        // o receiver VEM DA PILHA (o par [receiver, valor] do lowering) — o
        // scratch clsIdx DEDICADO (o objIdx = o scratch do alloc do ctor:
        // reusá-lo corrompia o objeto em construção)
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.clsIdx, "recv"));
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.clsIdx, "recv"));
        out.add(new WasmInstr.Const(0, layout.fieldOffset(sf.name())));
        out.add(new WasmInstr.Simple(0x6a, "i32.add"));
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, sLocal, "v_w"));
        out.add(new WasmInstr.Mem(storeOp, 0));
    }

    /** O ZERO-FILL do bloco recém-alocado: um store 0 por campo (a ordem
     *  reversa do layout — o MESMO padrão do lowerRecordInit; o bump não é
     *  zerado e o campo não atribuído no ctor = a paridade 0/null do JVM). */
    static void emitZeroFill(Ctx ctx, ClassLayout layout, List<WasmInstr> out) {
        var fls = layout.fields();
        for (int fi = fls.size() - 1; fi >= 0; fi--) {
            var f = fls.get(fi);
            int storeOp;
            if (WasmRecordOps.isWideField(f.type())) {
                storeOp = WasmInstr.Mem.STORE64;
            } else if (WasmRecordOps.isF64Field(f.type())) {
                storeOp = WasmInstr.Mem.STORE_F64;
            } else {
                storeOp = WasmInstr.Mem.STORE;
            }
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.clsIdx, "obj"));
            out.add(new WasmInstr.Const(0, f.offset()));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            if (WasmRecordOps.isF64Field(f.type())) {
                out.add(new WasmInstr.Const(0.0));
            } else if (WasmRecordOps.isWideField(f.type())) {
                out.add(new WasmInstr.Const(1, 0));
            } else {
                out.add(new WasmInstr.Const(0, 0));
            }
            out.add(new WasmInstr.Mem(storeOp, 0));
        }
    }
}
