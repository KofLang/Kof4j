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
 * `equals`/`==` NAO e sintetizado aqui ainda: o desugar JVM do `==` cruza
 * blocos (merge por fluxo de controle) que a linearizacao por pc ainda nao
 * modela — a face recusa `WASM002` honesto (Q7, sem artefatos).
 * Cada helper de String le o bloco `[len][bytes]\n` (15.3c-sliceA) e escreve um
 * bloco novo; o receiver e um handle i32 no bump heap (15.3d inc1).
 */
final class WasmRecordCode {

    private WasmRecordCode() {
    }

    /** `Name[f1=v1, ...]`; o handle do receiver esta no TOPO da pilha (i32) — e
     * guardado em `ctx.objIdx`, o handle da String resultante fica no topo
     * (consumido por writeStr / concat). */
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
