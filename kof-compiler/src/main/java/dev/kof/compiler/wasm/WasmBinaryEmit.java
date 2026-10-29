package dev.kof.compiler.wasm;

import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.Type;
import dev.kof.compiler.wasm.WasmLowering.Ctx;

import java.util.List;

import static dev.kof.compiler.wasm.WasmScalarOps.emitBinaryOp;
import static dev.kof.compiler.wasm.WasmScalarOps.typeName;
import static dev.kof.compiler.wasm.WasmTypeOracle.operandTypeBefore;

/**
 * CLASS-2 (28/10, #776 — split check_500): a emissão dos BINÁRIOS do
 * emitPlain (o rastro de referência contra `null`, o bool i32 do desugar,
 * as três danças MAP-MX de double e o emitBinaryOp). Comportamento
 * idêntico — o mesmo código movido do WasmLowering (o avô congelado 633).
 */
final class WasmBinaryEmit {

    private WasmBinaryEmit() {
    }

    static void emit(KofBinary bin, List<KofOperation> ops, int idx, Ctx ctx, List<WasmInstr> out) {
        if (ctx.wasi && bin.operandType() instanceof Type.ClassType) {
            // comparacao de REFERENCIA contra o literal `null` (ramo proprio
            // do desugar, nunca o kofRecordEq de conteudo): handle i32 vs 0
            String ref = operandTypeBefore(ops, idx);
            if ("null".equals(ref)) {
                out.add(new WasmInstr.Simple(bin.op() == KofBinaryOp.EQ ? 0x46 : 0x47,
                        bin.op() == KofBinaryOp.EQ ? "i32.eq" : "i32.ne"));
                ctx.lastPush = "bool";
                ctx.lastPushWide = false;
                return;
            }
        }
        if (bin.operandType() instanceof Type.ClassType) {
            throw new WasmUnsupportedException("operador binario '" + bin.op()
                    + "' com operando de classe '" + typeName(bin.operandType())
                    + "' fora do subset WASI (WASM002) — igualdade de records chega como"
                    + " kofRecordEq; identidade de Objetos nao esta no plano; docs/wasm-wasi-plan.md (#776)");
        }
        if (ctx.wasi && "bool".equals(ctx.lastPush)
                && (bin.op() == KofBinaryOp.EQ || bin.op() == KofBinaryOp.NE)
                && "int".equalsIgnoreCase(typeName(bin.operandType()))) {
            // `!=` do desugar sobre o bool i32 do kofRecordEq: i32, nao o
            // caminho i64 do escalar 15.2
            out.add(new WasmInstr.Simple(bin.op() == KofBinaryOp.EQ ? 0x46 : 0x47,
                    bin.op() == KofBinaryOp.EQ ? "i32.eq" : "i32.ne"));
            ctx.lastPush = "bool";
            ctx.lastPushWide = false;
            return;
        }
        String binT = typeName(bin.operandType());
        if (ctx.wasi && "double".equalsIgnoreCase(binT)) {
            // FATIA MAP-MX (28/10, #776): as TRÊS danças pelas formas dos
            // operandos (o rastro do topo diz op2; a simulação
            // WasmOperandShape diz op1). O null (o handle 0) trapa no
            // load = a paridade do NPE do unbox do JVM; a forma NÃO-coberta
            // (ex.: op1 desconhecido + op2 caixa) recusa no validate
            boolean op2Box = "mapval".equals(ctx.lastPush) || "nullabledouble".equals(ctx.lastPush);
            String[] pair = WasmOperandShape.pairAt(ops, idx, ctx);
            boolean op1Box = pair != null && WasmOperandShape.isBox(pair[0]);
            if (op2Box && op1Box) {
                // caixa+caixa (o get+get — a lei MAP-D): [h2] load → v2 no
                // scratch vf64; [h1] load → v1; o op
                out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 16));
                out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.vf64Idx, "vf64"));
                out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 16));
                out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.vf64Idx, "vf64"));
            } else if (op2Box) {
                // escalar+caixa: [v1, h2] — o load 16 do op2 basta
                out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 16));
            } else if (op1Box) {
                // caixa+escalar: [h1, v2] — o v2 no scratch vf64, o load
                // do h1, o v2 de volta
                out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.vf64Idx, "vf64"));
                out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 16));
                out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.vf64Idx, "vf64"));
            }
        }
        emitBinaryOp(bin.op(), binT, out);
        ctx.lastPush = binT;
        ctx.lastPushWide = "int".equalsIgnoreCase(ctx.lastPush) || "long".equalsIgnoreCase(ctx.lastPush)
                || "double".equalsIgnoreCase(ctx.lastPush);
    }
}
