package dev.kof.compiler.wasm;

import dev.kof.compiler.KofCall;

import dev.kof.compiler.ClassLayout;

import java.util.List;

import static dev.kof.compiler.wasm.WasmTypeOracle.operandTypeBefore;
import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/**
 * Rota de `println` do lowering WASI (TIER 15, #776): despacho por tipo do
 * ultimo push (record via toString sintetico da fatia C1; escalar via helpers
 * kof.write*). Separacao de responsabilidade do dispatcher (`WasmLowering`) —
 * gate 500, small-parts.
 */
final class WasmPrintCode {

    private WasmPrintCode() {
    }

    static void emitPrintln(KofCall kc, WasmLowering.Ctx ctx, List<WasmInstr> out) {
                String t = ctx.lastPush == null ? "?" : ctx.lastPush.toLowerCase();
                if ("?".equals(t)) {
                    String v = operandTypeBefore(ctx.flat, ctx.flat.indexOf(kc));
                    if (v != null) { t = v.toLowerCase(); ctx.lastPush = v; }
                }
                String recKey = t.startsWith("record:") ? ctx.lastPush.substring(7) : ctx.lastPush;
                ClassLayout recLayout = ctx.records == null ? null : ctx.records.get(recKey);
                if (recLayout != null) {
                    WasmRecordCode.emitToString(recLayout, recKey, ctx, out);
                    out.add(new WasmInstr.Call("kof.writeStr"));
                    ctx.lastPush = "string";
                    return;
                }
                switch (t) {
                    case "int", "long" -> out.add(new WasmInstr.Call("kof.writeInt"));
                    case "bool", "boolean" -> out.add(new WasmInstr.Call("kof.writeBool"));
                    case "char" -> out.add(new WasmInstr.Call("kof.writeChar"));
                    case "string" -> out.add(new WasmInstr.Call("kof.writeStr"));
                    default -> throw new WasmUnsupportedException("println '" + t
                            + "' fora da fatia 1 da unidade 15.3 (WASM002) — o runtime de strings/"
                            + "records/colecoes chega com o runtime (D-WASM-03/04);"
                            + " docs/development/wasm-wasi-plan.md (#776)");
                }
    }

    static ClassLayout recordLayoutOf(WasmLowering.Ctx ctx, String key) {
        if (ctx.records == null || key == null) return null;
        String k = key.startsWith("record:") ? key.substring(7) : key;
        ClassLayout lay = ctx.records.get(k);
        return lay != null ? lay : ctx.records.get(k.substring(k.lastIndexOf('/') + 1));
    }

}
