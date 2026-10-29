package dev.kof.compiler.wasm;

import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofDup2;
import dev.kof.compiler.KofDupX1;
import dev.kof.compiler.KofDupX2;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofOperation;
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
        // a TAG de classe no offset 0 (os primeiros 4B do header — os
        // campos começam em 16: sem colisão; o despacho virtual lê)
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.clsIdx, "h"));
        out.add(new WasmInstr.Const(0, WasmClassScan.classTag(ctx, simpleName(no.type()))));
        out.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
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

    /** O método de instância: a pilha = [receiver, args...] (a ordem do
     *  IR). A IMPLEMENTAÇÃO ÚNICA = o Call DIRETO (a devirtualização); o
     *  FECHO > 1 = o DESPACHO VIRTUAL: o load da TAG (a provenança do
     *  receiver localizada pela simulação) + a cadeia if MULTI-VALOR por
     *  typeidx — os params = [receiver, args...] (a pilha no if), os
     *  braços = as chamadas diretas por implementação (a lei do if: os
     *  valores ABAIXO do label sobrevivem ao end — o braço só os consome
     *  se forem params declarados; medido: `call` solitário no braço =
     *  "expected i32 but nothing on stack" no END). */
    static void lowerClassMethod(Ctx ctx, KofCall kc, List<KofOperation> ops, int idx,
            List<WasmInstr> out) {
        String name = simpleName(kc.ownerType());
        var impls = WasmClassScan.implsOf(ctx.module, ctx.classes(), name);
        int rt = resultType(kc);
        if (impls.size() <= 1) {
            out.add(new WasmInstr.Call(name + "." + kc.methodName()));
            // CLASS-2b (29/10, #776): o retorno de CLASSE do call direto =
            // TEE no scratch clsIdx (o MESMO save — o despacho sobre o
            // resultado encadeado lê a provenância)
            if (ctx.classOf(kc.returnType()) != null) {
                out.add(new WasmInstr.Local(WasmInstr.Local.TEE, ctx.clsIdx, "cls"));
            }
        } else {
            // o block type multi-valor: os params = [i32 receiver, args...]
            // (a ordem do IR); o resultado = o retorno do call (o void =
            // nenhum)
            java.util.List<Integer> ps = new java.util.ArrayList<>();
            ps.add(dev.kof.compiler.wasm.WasmFunc.TYPE_I32);
            for (Type t : kc.parameterTypes()) ps.add(argWasmType(ctx, t, name));
            java.util.List<Integer> rs = rt == 0x40 ? java.util.List.of() : java.util.List.of(rt);
            int bt = ctx.blockTypes().register(ps, rs);
            emitTagLoad(ctx, kc, ops, idx, out);
            emitDispatchChain(ctx, kc, impls, 0, rt, bt, out);
        }
        ctx.lastPush = typeName(kc.returnType());
        ctx.lastPushWide = isWide(kc.returnType()) || "double".equalsIgnoreCase(typeName(kc.returnType()));
    }

    /** O tipo wasm do arg no block type (os escalares = o mapa; as classes
     *  = o handle i32). */
    private static int argWasmType(Ctx ctx, Type t, String name) {
        if (t instanceof Type.ClassType) return dev.kof.compiler.wasm.WasmFunc.TYPE_I32;
        return dev.kof.compiler.wasm.WasmScalarOps.mapType(typeName(t), name, "parametro");
    }

    /** O load da TAG do receiver (o despacho virtual). A PROVENANÇA
     *  (CLASS-2): a simulação BACKWARD localiza o PRODUTOR do receiver —
     *  o LoadLocal de classe (o `local.get` relê o valor VIVO do local: o
     *  IR é statement-framed, nenhum store cruza a avaliação de operandos)
     *  OU os produtores que TEE'd o handle no scratch clsIdx (o alloc/init
     *  — o SET do alloc; o campo de classe — o tee da CLASS-2b; o retorno
     *  de chamada de classe — o tee da CLASS-2b): válido só se NENHUM
     *  valor de classe foi produzido entre o save e o call (o scan do
     *  intervalo: cada um tee'd POR CIMA do scratch). Fora disso = a
     *  recusa honesta (Q7: nunca tag errado silencioso — um tag errado
     *  despacha a implementação errada). */
    private static void emitTagLoad(Ctx ctx, KofCall kc, List<KofOperation> ops, int idx,
            List<WasmInstr> out) {
        int prodIdx = receiverProducer(ops, idx, kc, ctx);
        if (prodIdx < 0) {
            throw new WasmUnsupportedException("despacho virtual com provenança de receiver fora do subset medido (WASM002)"
                    + " — receiver de dup/campo de classe não-alcançada: fatia CLASS-2c;"
                    + " docs/wasm-wasi-plan.md (#776)");
        }
        KofOperation prod = ops.get(prodIdx);
        if (prod instanceof KofLoadLocal ll && ctx.classOf(ll.type()) != null) {
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.w(ll.index()), "recv"));
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.tagIdx, "tag"));
            return;
        }
        boolean scratchProducer = (prod instanceof KofNewObject no && ctx.classOf(no.type()) != null)
                || (prod instanceof KofLoadField lf && ctx.classOf(lf.fieldType()) != null)
                || (prod instanceof KofCall c && ctx.classOf(c.returnType()) != null);
        if (scratchProducer) {
            // O ÍNDICE VEM DA SIMULAÇÃO (o indexOf casa por equals: os dois
            // loads do MESMO campo são records IGUAIS — o indexOf devolvia o
            // primeiro e o intervalo errado recusava um despacho legal,
            // medido em PB.kf 29/10)
            for (int i = prodIdx + 1; i < idx; i++) {
                if (producesScannedClass(ctx, ops.get(i))) {
                    throw new WasmUnsupportedException("despacho virtual sobre receiver com valor de classe entre o produtor e o call (WASM002)"
                            + " — o tee do arg sobrescreve o scratch do receiver; fatia CLASS-2c;"
                            + " docs/wasm-wasi-plan.md (#776)");
                }
            }
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.clsIdx, "recv"));
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.tagIdx, "tag"));
            return;
        }
        throw new WasmUnsupportedException("despacho virtual com produtor de receiver fora do subset medido (WASM002)"
                + " — receiver de dup/campo de classe não-alcançada: fatia CLASS-2c;"
                + " docs/wasm-wasi-plan.md (#776)");
    }

    /** O produtor do RECEIVER da chamada: a simulação BACKWARD dos
     *  operandos (o mesmo modelo de popsOf/pushedShape que decide a
     *  emissão). O receiver = o valor mais FUNDO consumido pelo call — o
     *  produtor = o push que fecha a conta `need` (os operandos das
     *  chamadas aninhadas ficam ABAIXO da janela: o contador `skip` salta
     *  o intervalo deles). null = a simulação não localizou (a recusa). */
    /** O ÍNDICE do produtor do receiver (identidade da posição — o
     *  indexOf casava por equals e pegava o PRIMEIRO igual: medido). -1 =
     *  a simulação não localizou (a recusa). */
    private static int receiverProducer(List<KofOperation> ops, int callIdx, KofCall kc,
            Ctx ctx) {
        int need = 1 + kc.parameterTypes().size(); // o receiver + os args
        int skip = 0;
        for (int i = callIdx - 1; i >= 0; i--) {
            KofOperation op = ops.get(i);
            if (op instanceof KofConditionalJump) return -1; // a fronteira da região
            int pops = WasmOperandShape.popsOf(op);
            boolean pushes = WasmOperandShape.pushedShape(op, ops, i, ctx) != null
                    || op instanceof KofDup || op instanceof KofDup2
                    || op instanceof KofDupX1 || op instanceof KofDupX2;
            if (skip > 0) {
                if (pushes) skip--;
                skip += pops;
                continue;
            }
            if (!pushes) {
                skip += pops;
                continue;
            }
            if (need == 1) return i;
            need--;
            skip += pops;
        }
        return -1;
    }

    /** O op produz valor de CLASSE alcançada (o tee dispara nele — o
     *  scratch clsIdx é sobrescrito). */
    private static boolean producesScannedClass(Ctx ctx, KofOperation op) {
        if (op instanceof KofLoadLocal ll) return ctx.classOf(ll.type()) != null;
        if (op instanceof KofNewObject no) return ctx.classOf(no.type()) != null;
        if (op instanceof KofLoadField lf) return ctx.classOf(lf.fieldType()) != null;
        if (op instanceof KofCall c) return ctx.classOf(c.returnType()) != null;
        return false;
    }

    /** A cadeia de despacho: o if por tag (o block type MULTI-VALOR por
     *  typeidx — os params = o receiver + os args da pilha, os braços os
     *  consomem); o else = a próxima implementação; a ÚLTIMA = o call
     *  DIRETO SEM if (a tag = sempre uma das implementações — a cadeia é
     *  exaustiva: o if result-typed SEM else = o validador recusa —
     *  medido). */
    private static void emitDispatchChain(Ctx ctx, KofCall kc, java.util.List<String> impls,
            int i, int rt, int bt, List<WasmInstr> out) {
        String impl = impls.get(i);
        if (i == impls.size() - 1) {
            out.add(new WasmInstr.Call(impl + "." + kc.methodName()));
            return;
        }
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.tagIdx, "tag"));
        out.add(new WasmInstr.Const(0, WasmClassScan.classTag(ctx, impl)));
        out.add(new WasmInstr.Simple(0x46, "i32.eq"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, bt, true));
        out.add(new WasmInstr.Call(impl + "." + kc.methodName()));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, bt, true));
        emitDispatchChain(ctx, kc, impls, i + 1, rt, bt, out);
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, bt, true));
    }

    /** O tipo do resultado do if (o 0x40 void; o escalonar da largura). */
    private static int resultType(KofCall kc) {
        Type rt = kc.returnType();
        String tn = typeName(rt);
        return switch (tn) {
            case "int", "long" -> WasmFunc.TYPE_I64;
            case "double" -> WasmFunc.TYPE_F64;
            case "bool", "boolean", "char", "string" -> WasmFunc.TYPE_I32;
            default -> 0x40;
        };
    }

    private static String simpleName(Type t) {
        if (t instanceof Type.ClassType ct) return ct.name();
        return WasmScalarOps.typeName(t);
    }
}
