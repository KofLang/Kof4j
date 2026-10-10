package dev.kof.compiler.wasm;

import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.FieldLayout;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.wasm.WasmLowering.Ctx;
import java.util.List;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/**
 * Lowering WASI das operacoes de CONSTRUcao/ACESSO de record (15.3d): aloc no
 * bump heap com save/restore de receiver por profundidade (slice D), stores de
 * campo pelo ClassLayout, loads por largura e o fold de igualdade opaco
 * `kofRecordEq` (fatia C2). Extrair daqui manteve `WasmLowering` abaixo do teto
 * de 600 linhas (`check_500`); os shapes de pilha sao os mesmos do oraculo JVM
 * (`CompilerRecordSupport`/`RecordEqualityLowerer`).
 */
final class WasmRecordCtor {

    private WasmRecordCtor() {
    }

    static boolean isRecordInit(Ctx ctx, KofCall kc) {
        return ctx.wasi && kc.kind() == KofCallKind.CONSTRUCTOR && "<init>".equals(kc.methodName())
                && ctx.recordOf(kc.ownerType()) != null;
    }

    static boolean isRecordEq(Ctx ctx, KofCall kc) {
        return ctx.wasi && kc.kind() == KofCallKind.FUNCTION && "kofRecordEq".equals(kc.methodName());
    }

    static void lowerNewObject(Ctx ctx, KofNewObject no, List<WasmInstr> out) {
        ClassLayout layout = ctx.recordOf(no.type());
        if (layout == null || ctx.objIdx < 0) {
            throw new WasmUnsupportedException("alocacao de '" + no.type()
                    + "' fora do subset de records da 15.3d (WASM002) — docs/wasm-wasi-plan.md (#776)");
        }
        // 15.3d incremento 1: slots de 8 bytes gravados como i64 — so campos
        // Int/Long; String/Bool/Char/Double/record (i32/f64 no stack) recusam
        // honesto (WASM002, SEM artefato) em vez de emitir modulo invalido (Q7).
        for (FieldLayout f : layout.fields()) {
            boolean recordField = WasmRecordOps.isRecordClassField(f.type())
                    && ctx.recordOf(f.type()) != null;
            if (!recordField && !WasmRecordOps.isWideField(f.type())
                    && !WasmRecordOps.isI32Field(f.type()) && !WasmRecordOps.isF64Field(f.type())) {
                if (WasmRecordOps.isRecordClassField(f.type())) {
                    throw new WasmUnsupportedException("record '" + no.type() + "' campo '" + f.name()
                            + "' de tipo '" + typeName(f.type()) + "' que nao e um record conhecido (WASM002)"
                            + " — campos de classe estranha fora do plano; docs/wasm-wasi-plan.md (#776)");
                }
                throw new WasmUnsupportedException("record '" + no.type() + "' campo '" + f.name()
                        + "' de tipo '" + typeName(f.type()) + "' fora do subset de larguras da 15.3d inc2 (WASM002)"
                        + " — tipos fora do plano ficam fora; docs/wasm-wasi-plan.md (#776)");
            }
        }
        // slice D: um alloc dentro de argumentos de outro record nao pode
        // corromper o receiver do construtor em andamento — salva objIdx no
        // slot da profundidade (nestR) e o <init> do filho restaura
        if (ctx.allocDepth > 0) {
            if (ctx.nestR == null || ctx.allocDepth >= ctx.nestR.length) {
                throw new WasmUnsupportedException("aninhamento de construtores alem do medido (WASM002)"
                        + " — docs/wasm-wasi-plan.md (#776)");
            }
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.objIdx, "obj"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.nestR[ctx.allocDepth], "rv"));
        }
        ctx.allocDepth++;
        // h = global 0; global 0 += totalSize; empilha h
        out.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.objIdx, "obj"));
        out.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        out.add(new WasmInstr.Const(0, layout.totalSize()));
        out.add(new WasmInstr.Simple(0x6a, "i32.add"));
        out.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.objIdx, "obj"));
        ctx.lastPush = "record";
    }

    static void lowerLoadField(Ctx ctx, KofLoadField lf, List<WasmInstr> out) {
        ClassLayout layout = ctx.recordOf(lf.ownerType());
        if (layout == null) {
            throw new WasmUnsupportedException("leitura de campo '" + lf.name() + "' em tipo nao-record '"
                    + lf.ownerType() + "' (WASM002) — docs/wasm-wasi-plan.md (#776)");
        }
        // pilha tem o receiver handle (i32) -> soma offset -> load pelo tipo da largura
        out.add(new WasmInstr.Const(0, layout.fieldOffset(lf.name())));
        out.add(new WasmInstr.Simple(0x6a, "i32.add"));
        if (WasmRecordOps.isWideField(lf.fieldType())) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        } else if (WasmRecordOps.isF64Field(lf.fieldType())) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 0));
        } else {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0)); // i32: Bool/Char/String-handle
        }
        ctx.lastPush = WasmRecordOps.isStringField(lf.fieldType()) ? "string" : typeName(lf.fieldType());
        ctx.lastPushWide = WasmRecordOps.isI64Field(lf.fieldType());
    }

    /** kofRecordEq: pilha [L, R]; resolve o layout pelo ultimo tipo empilhado
     * (ou o do operando anterior) e sintetiza o fold reto. */
    static void lowerRecordEq(Ctx ctx, KofCall kc, List<WasmInstr> out) {
        ClassLayout lay = WasmPrintCode.recordLayoutOf(ctx, ctx.lastPush);
        if (lay == null) {
            String v = WasmTypeOracle.operandTypeBefore(ctx.flat, ctx.flat.indexOf(kc));
            lay = WasmPrintCode.recordLayoutOf(ctx, v);
        }
        if (lay == null) {
            throw new WasmUnsupportedException("igualdade de '" + ctx.lastPush
                    + "' fora dos records da 15.3d (WASM002) — docs/wasm-wasi-plan.md (#776)");
        }
        WasmRecordCode.emitEquals(lay, ctx, out, 0);
    }

    /** <init> de record: grava os argumentos empilhados nos offsets do layout
     * (ordem reversa) e restaura o receiver do construtor-pai (slice D). */
    static void lowerRecordInit(Ctx ctx, KofCall kc, List<WasmInstr> out) {
        ClassLayout layout = ctx.recordOf(kc.ownerType());
        List<FieldLayout> fls = layout.fields();
        for (int fi = fls.size() - 1; fi >= 0; fi--) {
            FieldLayout f = fls.get(fi);
            int sLocal;
            int storeOp;
            if (WasmRecordOps.isWideField(f.type())) {
                sLocal = ctx.vIdx; storeOp = WasmInstr.Mem.STORE64;
            } else if (WasmRecordOps.isF64Field(f.type())) {
                sLocal = ctx.vf64Idx; storeOp = WasmInstr.Mem.STORE_F64;
            } else {
                sLocal = ctx.v32Idx; storeOp = WasmInstr.Mem.STORE;
            }
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, sLocal, "v_w"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.objIdx, "obj"));
            out.add(new WasmInstr.Const(0, f.offset()));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, sLocal, "v_w"));
            out.add(new WasmInstr.Mem(storeOp, 0));
        }
        ctx.allocDepth--;
        if (ctx.allocDepth > 0) {
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.nestR[ctx.allocDepth], "rv"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.objIdx, "obj"));
        }
        ctx.lastPush = "record:" + layout.className();
    }
}
