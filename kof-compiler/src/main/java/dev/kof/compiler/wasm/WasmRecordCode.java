package dev.kof.compiler.wasm;

import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.FieldLayout;
import java.util.List;

import static dev.kof.compiler.wasm.WasmRecordOps.isF64Field;
import static dev.kof.compiler.wasm.WasmRecordOps.isStringField;

/**
 * Sintetiza os metodos de INSTANCIA de record (15.3d inc2 fatia C) INLINE no
 * call site — o backend WASI ainda nao tem dispatch virtual, entao `toString`
 * e expandido onde o programa o usa (mesma decisao de shape do
 * `CompilerRecordSupport`/`RecordEqualityLowerer` da JVM, o oraculo). Shapes:
 *  - `toString`: `Name[f1=v1, f2=v2]` (JVM record); concat dos handles no bump
 *    heap via `kof.strLit`/`kof.strConcat`; Int/Long via `kof.intToStr`,
 *    Bool via `kof.strBool`, Char via `kof.strChar`, String = o proprio handle.
 * `equals`/`==` (15.3d inc2 fatia C2): o call site do frontend WASI e a chamada
 * opaca `kofRecordEq(L,R)` (precedente do JS no `RecordEqualityLowerer`);
 * `emitEquals` sintetiza o fold de campo INLINE, reto, com accumulator local —
 * nunca merge por fluxo de controle (a linearizacao por pc nao o modela).
 * Semantica = `java.util.Objects.equals`: ambos nulos -> 1, um nulo -> 0, senao
 * igualdade de CONTEUDO campo a campo (String via `kof.strEq`, Double pelos bits
 * `i64.reinterpret_f64` = `Double.equals` do JVM).
 * Cada helper de String le o bloco `[len][bytes]\n` (15.3c-sliceA) e escreve um
 * bloco novo; o receiver e um handle i32 no bump heap (15.3d inc1).
 */
final class WasmRecordCode {

    private WasmRecordCode() {
    }

    /** `Name[f1=v1, ...]`; o handle do receiver esta no TOPO da pilha (i32) — e
     * guardado em `ctx.objIdx`, o handle da String resultante fica no topo
     * (consumido por writeStr / concat). */
    /** Pilha na entrada: [L, R] como handles i32 do mesmo record; sai 1/0 (i32,
     * `ctx.lastPush="bool"`). Fold reto por accumulator (`eqAcc`), sem valores
     * cruzando blocos — todo `if`/`block` e de corpo void. */
    static void emitEquals(ClassLayout layout, WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (ctx.eqEqIdx < 0 || ctx.eqAccIdx < 0 || ctx.eqCmpIdx < 0 || ctx.eqDblIdx < 0) {
            throw new WasmUnsupportedException("igualdade de record sem scratch (WASM002)"
                    + " — docs/development/wasm-wasi-plan.md (#776)");
        }
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.eqEqIdx, "eqB"));   // R
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.eqAccIdx, "eqA"));  // handle L (nunca reescrito)
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.eqAccIdx, "eqA"));
        out.add(new WasmInstr.Const(0, 0));
        out.add(new WasmInstr.Simple(0x46, "i32.eq"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "l0", 0x40));
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.eqEqIdx, "eqB"));
        out.add(new WasmInstr.Const(0, 0));
        out.add(new WasmInstr.Simple(0x46, "i32.eq"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "b0", 0x40));
        out.add(new WasmInstr.Const(0, 1));
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.eqAndIdx, "eqN"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "b0", 0x40));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "l0", 0x40));
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.eqEqIdx, "eqB"));
        out.add(new WasmInstr.Const(0, 0));
        out.add(new WasmInstr.Simple(0x46, "i32.eq"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "b1", 0x40));
        out.add(new WasmInstr.Const(0, 0));
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.eqAndIdx, "eqN"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "b1", 0x40));
        // ambos nao-nulos: AND dedicado em eqAnd; eqAcc/eqEq ficam os handles
        out.add(new WasmInstr.Const(0, 1));
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.eqAndIdx, "eqN"));
        for (FieldLayout f : layout.fields()) {
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.eqAccIdx, "eqA"));
            out.add(new WasmInstr.Mem(fieldLoadOp(f), f.offset()));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.eqEqIdx, "eqB"));
            out.add(new WasmInstr.Mem(fieldLoadOp(f), f.offset()));
            emitFieldEq(f, ctx, out);
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.eqAndIdx, "eqN"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.eqCmpIdx, "eqC"));
            out.add(new WasmInstr.Simple(0x71, "i32.and"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.eqAndIdx, "eqN"));
        }
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "b1", 0x40));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "l0", 0x40));
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.eqAndIdx, "eqN"));
        ctx.lastPush = "bool";
        ctx.lastPushWide = false;
    }

    private static int fieldLoadOp(FieldLayout f) {
        if (WasmRecordOps.isF64Field(f.type())) return WasmInstr.Mem.LOAD_F64;
        if (WasmRecordOps.isWideField(f.type())) return WasmInstr.Mem.LOAD64;
        return WasmInstr.Mem.LOAD;
    }

    /** Pilha: [vL, vR] na largura do campo; deixa o cmp 1/0 (i32) em `ctx.eqCmp`. */
    private static void emitFieldEq(FieldLayout f, WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (WasmRecordOps.isF64Field(f.type())) {
            // Double.equals do JVM = bits exatos (NaN==NaN, +0.0 != -0.0): reinterpret p/ i64
            out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.eqDblIdx, "eqD"));
            out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.eqDblIdx, "eqD"));
            out.add(new WasmInstr.Simple(0x51, "i64.eq"));
        } else if (WasmRecordOps.isWideField(f.type())) {
            out.add(new WasmInstr.Simple(0x51, "i64.eq"));
        } else if (WasmRecordOps.isStringField(f.type())) {
            out.add(new WasmInstr.Call("kof.strEq"));
        } else {
            out.add(new WasmInstr.Simple(0x46, "i32.eq"));
        }
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.eqCmpIdx, "eqC"));
    }

    static void emitToString(ClassLayout layout, String recName, WasmLowering.Ctx ctx,
            List<WasmInstr> out) {
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.objIdx, "ro"));
        pushStrLit(recName + "[", ctx, out);
        List<FieldLayout> fls = layout.fields();
        for (int i = 0; i < fls.size(); i++) {
            if (i > 0) {
                pushStrLit(", ", ctx, out);
                concatTop(out);
            }
            FieldLayout f = fls.get(i);
            pushStrLit(f.name() + "=", ctx, out);
            concatTop(out);
            emitFieldValueStr(f, ctx, out);
            concatTop(out);
        }
        pushStrLit("]", ctx, out);
        concatTop(out);
    }

    private static void pushStrLit(String s, WasmLowering.Ctx ctx, List<WasmInstr> out) {
        int addr = ctx.internString(s);
        out.add(new WasmInstr.Const(0, addr));
        out.add(new WasmInstr.Const(0, ctx.internLen(s)));
        out.add(new WasmInstr.Call("kof.strLit"));
    }

    private static void concatTop(List<WasmInstr> out) {
        out.add(new WasmInstr.Call("kof.strConcat"));
    }

    /** Empilha o handle da String do campo `f` lendo do receiver `ctx.objIdx` (i32). */
    private static void emitFieldValueStr(FieldLayout f, WasmLowering.Ctx ctx, List<WasmInstr> out) {
        String tn = dev.kof.compiler.wasm.WasmScalarOps.typeName(f.type());
        loadObj(ctx, out);
        out.add(new WasmInstr.Const(0, f.offset()));
        out.add(new WasmInstr.Simple(0x6a, "i32.add"));
        if (isStringField(f.type())) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0)); // handle ja e a String
        } else if ("char".equalsIgnoreCase(tn)) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
            out.add(new WasmInstr.Call("kof.strChar"));
        } else if ("bool".equalsIgnoreCase(tn) || "boolean".equalsIgnoreCase(tn)) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
            out.add(new WasmInstr.Call("kof.strBool"));
        } else if (dev.kof.compiler.wasm.WasmRecordOps.isI64Field(f.type())) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
            out.add(new WasmInstr.Call("kof.intToStr"));
        } else if (dev.kof.compiler.wasm.WasmRecordOps.isF64Field(f.type())) {
            throw new WasmUnsupportedException("toString de record campo '" + f.name() + "' Double fora da 15.3d inc2 (WASM002)"
                    + " — Double.toString paridade chega na proxima fatia;"
                    + " docs/development/wasm-wasi-plan.md (#776)");
        } else {
            throw new WasmUnsupportedException("toString de record campo '" + tn
                    + "' fora da 15.3d inc2 (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
        }
    }

    private static void loadObj(WasmLowering.Ctx ctx, List<WasmInstr> out) {
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.objIdx, "ro"));
    }
}
