package dev.kof.compiler.wasm;

import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.Type;
import dev.kof.compiler.wasm.WasmLowering.Ctx;

import static dev.kof.compiler.wasm.WasmScalarOps.isWide;
import static dev.kof.compiler.wasm.WasmScalarOps.typeName;
import java.util.List;

/**
 * CLASS-1 (28/10, #776 — split check_500): as chamadas de CLASSE no call
 * site — o `<init>` (o alloc no bump + o zero-fill dos campos + o tee do
 * handle + os args + o Call da func do ctor) e os MÉTODOS de instância
 * (o receiver = o arg 0, o Call DIRETO — a escolha de implementação do
 * §12: a herança/despacho virtual = a fatia CLASS-2 registrada). Os
 * params escalares apenas (os String = o próximo registrado). O print da
 * instância = a recusa honesta (o toString de identidade não é
 * byte-parity-ável). O rastro do handle da instância = "class:X".
 */
final class WasmClassCall {

    private WasmClassCall() {
    }

    static boolean isClassInit(Ctx ctx, KofCall kc) {
        return ctx.wasi && kc.kind() == KofCallKind.CONSTRUCTOR
                && "<init>".equals(kc.methodName()) && ctx.classOf(kc.ownerType()) != null;
    }

    static boolean isClassMethod(Ctx ctx, KofCall kc) {
        return ctx.wasi && (kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE)
                && ctx.classOf(kc.ownerType()) != null;
    }

    /** O ALLOC da classe (a op KofNewObject): h = o global bump; global 0
     *  += totalSize; o ZERO-FILL dos campos (o bump não é zerado — a
     *  paridade 0/null do JVM); o handle fica na pilha (o rastro "class:X")
     *  e o `<init>` o consome pelo tee. */
    static void lowerClassAlloc(Ctx ctx, dev.kof.compiler.KofNewObject no, List<WasmInstr> out) {
        var layout = ctx.classOf(no.type());
        if (layout == null) {
            throw new WasmUnsupportedException("alocacao de '" + no.type()
                    + "' fora do subset de classes da CLASS-1 (WASM002)"
                    + " — docs/wasm-wasi-plan.md (#776)");
        }
        // h = o global 0; o global 0 += o tamanho (o MESMO bump do record);
        // o handle VAI PRO SCRATCH (não fica na pilha: os args do <init> se
        // empilham DEPOIS e o receiver entra POR CIMA deles no call)
        out.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.clsIdx, "h"));
        out.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        out.add(new WasmInstr.Const(0, layout.totalSize()));
        out.add(new WasmInstr.Simple(0x6a, "i32.add"));
        out.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));
        // o zero-fill dos campos (a ordem reversa do layout)
        WasmClassOps.emitZeroFill(ctx, layout, out);
        ctx.lastPush = "class:" + simpleName(no.type());
        ctx.lastPushWide = false;
    }

    /** O `<init>` da classe (a chamada): o tee do handle (o call da func
     *  CONSUME o receiver — o param 0 — e os args; o original volta pelo
     *  get: a lei do tee do desugar). */
    static void lowerClassInit(Ctx ctx, KofCall kc, List<WasmInstr> out) {
        String name = simpleName(kc.ownerType());
        // a pilha = [args...] (o receiver = o ÚLTIMO param da func): o get
        // do scratch empilha o receiver POR CIMA dos args; o call consome
        // tudo; o get final devolve o handle (o resultado)
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.clsIdx, "h"));
        out.add(new WasmInstr.Call(name + ".<init>"));
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.clsIdx, "h"));
        ctx.lastPush = "class:" + name;
        ctx.lastPushWide = false;
    }

    /** O método de instância: a pilha = [receiver, args...] (o lowering
     *  empilha o receiver como o arg 0 — a ordem do JVM); o Call DIRETO. */
    static void lowerClassMethod(Ctx ctx, KofCall kc, List<WasmInstr> out) {
        String name = simpleName(kc.ownerType());
        out.add(new WasmInstr.Call(name + "." + kc.methodName()));
        Type rt = kc.returnType();
        ctx.lastPush = typeName(rt);
        ctx.lastPushWide = isWide(rt) || "double".equalsIgnoreCase(typeName(rt));
    }

    private static String simpleName(Type t) {
        if (t instanceof Type.ClassType ct) return ct.name();
        return WasmScalarOps.typeName(t);
    }
}
