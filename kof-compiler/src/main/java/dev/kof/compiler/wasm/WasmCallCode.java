package dev.kof.compiler.wasm;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.Type;
import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.KofConditionalJump;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.kof.compiler.wasm.WasmScalarOps.ownerSimpleName;
import static dev.kof.compiler.wasm.WasmScalarOps.typeName;
import static dev.kof.compiler.wasm.WasmTypeOracle.operandTypeBefore;
import static dev.kof.compiler.wasm.WasmTypeOracle.printlnFollows;

/**
 * Fase E (27/10, #776 — split check_500): o DESPACHO de {@code KofCall} do
 * backend WASI, extraído verbatim do {@code WasmLowering.emitPlain} (684 ≥ 600
 * derrubava o gate CI). Responsabilidade única: resolver UMA chamada — records
 * (eq/init/toString), String intrinsics (WasmStrCalls), valueOf (raw + Object→
 * caixa), math family, caixa §284, println e o fallback FUNCTION/STATIC.
 * Comportamento idêntico (freeze rule 3).
 */
final class WasmCallCode {

    private WasmCallCode() {
    }

    static void emitCall(KofCall kc, List<KofOperation> ops, int idx,
                         WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (WasmRecordCtor.isRecordEq(ctx, kc) || WasmRecordCtor.isRecordEqualsCall(ctx, kc)) {
            WasmRecordCtor.lowerRecordEq(ctx, kc, out);
        } else if (WasmRecordCtor.isRecordInit(ctx, kc)) {
            WasmRecordCtor.lowerRecordInit(ctx, kc, out);
        } else if (WasmClassCall.isClassInit(ctx, kc)) {
            WasmClassCall.lowerClassInit(ctx, kc, out);
        } else if (WasmClassCall.isClassMethod(ctx, kc)) {
            WasmClassCall.lowerClassMethod(ctx, kc, ops, idx, out);
        } else if (WasmStrCalls.emit(kc, ops, idx, ctx, out)) {
            // chamadas de String (==/!=, length, isEmpty, equals,
            // startsWith, endsWith, concat) — WasmStrCalls
        } else if (ctx.wasi && "valueOf".equals(kc.methodName())
                && printlnFollows(ops, idx)) {
            // String.valueOf(X) seguido de println: o valor bruto ja esta na
            // pilha; pula o valueOf e deixa o helper escalar consumir
            String v = (!kc.parameterTypes().isEmpty()
                    && !(kc.parameterTypes().get(0) instanceof Type.UnknownType))
                    ? typeName(kc.parameterTypes().get(0))
                    : operandTypeBefore(ops, idx);
            if (v == null) {
                // o desugar do == passa o bool por um local `Object` entre blocos;
                // o lastPush do equals/Boolean.valueOf anterior ja diz o tipo real
                v = ctx.lastPush;
            }
            // Fase E (27/10, #776): o MAP.get devolve o HANDLE DA CAIXA (a
            // nullable anda na §284, rastro "mapval") — o rastro TEM de
            // vencer sobre o walk (o NullableType[int] dizia "int" e o
            // writeInt consumia o handle: o validador rejeitou). A cadeia
            // valueOf tem DOIS passes (Integer.valueOf e String.valueOf) —
            // CADA pass normaliza v e RE-ARMA o rastro (o "object" PURO dos
            // records nao entra: o walk do record diz o tipo real e o print
            // usa o toString dele — a regressao dos records mediu isso)
            if ("mapval".equals(ctx.lastPush)) {
                // o valor e' o HANDLE DA CAIXA: normaliza E RE-ARMA (a cadeia
                // valueOf tem dois passes — o segundo walk diz "int" de novo)
                v = "object";
                ctx.lastPush = "mapval";
            } else if ("nullabledouble".equals(ctx.lastPush)) {
                // FATIA MAP-D (28/10, #776): o Double? na cadeia do println —
                // o Double.valueOf do desugar (o param = o PRIMITIVE apagado)
                // NAO unboxa no WASI: a caixa anda (§284) e o rastro RE-ARMA
                // (os DOIS passes chegam nullabledouble; o print renderiza via
                // boxToStr — o null = `null`, o MESMO do valueOf)
                ctx.lastPush = "nullabledouble";
            } else if ("nullablebool".equals(ctx.lastPush)) {
                // FATIA MAP-B (28/10, #776): o Bool? na cadeia do println — o
                // Boolean.valueOf do desugar chega AQUI (o printlnFollows
                // vence o ramo Boolean do valueOf) e o else apagava o rastro
                // pelo param PRIMITIVO ("bool" — o writeBool despachava sobre
                // o handle da caixa, medido). O re-arme: a caixa anda e o
                // print renderiza via boxToStr
                ctx.lastPush = "nullablebool";
            } else if ("nullableint".equals(ctx.lastPush)) {
                // FATIA MAP-KI-VAL (28/10, #776): o Int? na cadeia do println
                // — o Integer.valueOf do desugar chega AQUI (o param apagado
                // dizia "int" e o writeInt consumia o handle da caixa: módulo
                // inválido, medido). O re-arme: a caixa anda e o print
                // renderiza via boxToStr (o MESMO padrão do nullabledouble)
                ctx.lastPush = "nullableint";
            } else {
                ctx.lastPush = v;   // o comportamento ORIGINAL (o char/records)
            }
        } else if (ctx.wasi && "valueOf".equals(kc.methodName())
                && "String".equals(ownerSimpleName(kc.ownerType()))) {
            // String.valueOf como VALOR (variavel/concat) — paridade JVM:
            // Int|Long -> kof.intToStr (handle `[len][digits]` no bump heap);
            // String -> identidade (o `+` do JVM reaplica valueOf no handle);
            // outros tipos recusam honesto (WASM002) sem artefato (Q7).
            if ("string".equals(ctx.lastPush)) {
                ctx.lastPush = "string";
            } else if ("nullablestring".equals(ctx.lastPush)) {
                // FATIA MV (28/10, #776): o String? na cadeia valueOf — o
                // 0 = a literal `null` (o MESMO render do print); o handle
                // cru passa (o scratch dupIdx = sempre alocado)
                out.add(new WasmInstr.Local(WasmInstr.Local.TEE, ctx.dupIdx, "sv"));
                out.add(new WasmInstr.Const(0, 0));
                out.add(new WasmInstr.Simple(0x46, "i32.eq"));
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7f));
                int na = ctx.internString("null");
                out.add(new WasmInstr.Const(0, na));
                out.add(new WasmInstr.Const(0, 4));
                out.add(new WasmInstr.Call("kof.strLit"));
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7f));
                out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.dupIdx, "sv"));
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7f));
                ctx.lastPush = "string";
            } else if ("mapval".equals(ctx.lastPush)) {
                // FATIA SNULL (28/10, #776): o valor do map.get na CADEIA
                // valueOf do concat — o HANDLE DA CAIXA (o mapval) → o
                // boxToStr renderiza (o null = `null`, o MESMO do print
                // medido); RE-ARMA o mapval (a cadeia tem dois passes)
                out.add(new WasmInstr.Call("kof.boxToStr"));
                ctx.lastPush = "mapval";
            } else if ("nullablebool".equals(ctx.lastPush)) {
                // FATIA MAP-B (28/10, #776): o Bool? na cadeia valueOf — a
                // CAIXA (o boxBool): o boxToStr renderiza (a tag 3 = strBool;
                // o null = `null`, o MESMO render do print). Sem re-arm: o
                // valueOf e por OPERANDO (o passe ve "string" e a identidade
                // nao emite)
                out.add(new WasmInstr.Call("kof.boxToStr"));
                ctx.lastPush = "string";
            } else if ("nullabledouble".equals(ctx.lastPush)) {
                // FATIA MAP-D (28/10, #776): o Double? na cadeia valueOf — a
                // CAIXA (o boxDouble): o boxToStr renderiza (o null = `null`,
                // o MESMO render do print). Sem re-arm: o passe 2 ve "string"
                // e a identidade nao emite
                out.add(new WasmInstr.Call("kof.boxToStr"));
                ctx.lastPush = "string";
            } else if ("nullableint".equals(ctx.lastPush)) {
                // FATIA MAP-KI-VAL (28/10, #776): o Int? na cadeia valueOf —
                // a CAIXA (o boxInt): o boxToStr renderiza (a tag 0 =
                // intToStr; o null = `null`, o MESMO render do print)
                out.add(new WasmInstr.Call("kof.boxToStr"));
                ctx.lastPush = "string";
            } else if ("Object".equalsIgnoreCase(ctx.lastPush)
                    && (kc.parameterTypes().isEmpty()
                        || !(kc.parameterTypes().get(0) instanceof Type.PrimitiveType))) {
                // Fase E (27/10, #776): valueOf sobre um local OBJECT — o
                // valor e CRU (handle String/record) ou CAIXA §284 (join
                // heterogeneo): kof.boxToStr despacha pelo MAGIC/tag e
                // devolve handle de String nos dois casos. O walk do
                // operandTypeBefore PULA loads Object (compat do desugar
                // bool) e chegaria a um produtor aleatorio — convertido
                // errado era memory fault (medido: "m=" + m trapava).
                out.add(new WasmInstr.Call("kof.boxToStr"));
                ctx.lastPush = "string";
            } else {
                Type p0 = kc.parameterTypes().isEmpty() ? null : kc.parameterTypes().get(0);
                String vt = (p0 instanceof Type.PrimitiveType)
                        ? typeName(p0)
                        : operandTypeBefore(ops, idx);
                ClassLayout vl = ctx.records == null ? null : ctx.records.get(vt);
                if ("string".equalsIgnoreCase(vt)) {
                    ctx.lastPush = "string";
                } else if ("int".equalsIgnoreCase(vt) || "long".equalsIgnoreCase(vt)) {
                    out.add(new WasmInstr.Call("kof.intToStr"));
                    ctx.lastPush = "string";
                } else if ("bool".equalsIgnoreCase(vt) || "boolean".equalsIgnoreCase(vt)) {
                    // "t=" + true — paridade JVM (String.valueOf(boolean)).
                    out.add(new WasmInstr.Call("kof.strBool"));
                    ctx.lastPush = "string";
                } else if ("char".equalsIgnoreCase(vt)) {
                    // Fase E (27/10, #776): "s" + c / String.valueOf(c) — o
                    // MESMO kof.strChar dos campos Char de record (o runtime
                    // ja existia; o gancho e' que faltava). O Char na pilha
                    // e' i64 e o strChar recebe i64 (wrap interno).
                    // FATIA MAP-C (28/10, #776): o operador tem de estar NA
                    // PILHA — o ternary do desugar do Char? produz o operando
                    // ANTES do null-check (a maquina de estados nao o carrega
                    // — o if-expression slice, o recorded seguinte): SEM o
                    // operando = a recusa honesta (o strChar sobre a pilha
                    // vazia = o modulo invalido, a classe Q7 — medido)
                    // FATIA JOIN (28/10, #776): o ternary do desugar produz
                    // o operando ANTES do null-check — ele anda no scratch do
                    // braco: o carrega + o UNBOX (LOAD64 16 do qword da
                    // caixa — o rastro underPending diz a largura) antes do
                    // strChar; SEM o under = a recusa honesta (a pilha vazia
                    // = o modulo invalido, a classe Q7 — medido)
                    if (ctx.underPending) {
                        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.armIdx, "av"));
                        out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 16));
                        ctx.underPending = false;
                    } else if (WasmOperandShape.depthAt(ops, idx, ctx) == 0) {
                        throw new WasmUnsupportedException("String.valueOf(char) sem o operando na pilha"
                                + " — o ternary do desugar do Char? produz o valor antes do null-check"
                                + " (o if-expression da maquina de estados = o slice seguinte) (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    out.add(new WasmInstr.Call("kof.strChar"));
                    ctx.lastPush = "string";
                } else if ("double".equalsIgnoreCase(vt)) {
                    // Fase E (27/10, #776): "s" + d / String.valueOf(d) — o
                    // MESMO kof.doubleToStr do println (o Schubfach completo);
                    // o registro das tabelas/funcoes segue o gancho
                    // WasmTypeOracle.usesDoubleValueOf.
                    out.add(new WasmInstr.Call("kof.doubleToStr"));
                    ctx.lastPush = "string";
                } else if (vl != null) {
                    WasmRecordCode.emitToString(vl, vt, ctx, out, 0);
                    ctx.lastPush = "string";
                } else {
                    throw new WasmUnsupportedException("String.valueOf('" + vt
                            + "') fora da 15.3d inc2 (WASM002) — docs/wasm-wasi-plan.md (#776)");
                }
            }
        } else if (ctx.wasi && kc.kind() == KofCallKind.STATIC
                && "valueOf".equals(kc.methodName())
                && "Boolean".equals(ownerSimpleName(kc.ownerType()))) {
            // Boolean.valueOf(bool) e identidade no backend WASI (bool i32); se o
            // merge do desugar deixou i64 na pilha (literal int 0/1), wrap p/ i32.
            // FATIA MAP-B (28/10, #776): o Bool? (a CAIXA §284) NAO unboxa — o
            // rastro RE-ARMA (o print renderiza via boxToStr; o clobber para
            // "bool" despachava writeBool sobre o handle da caixa — medido)
            if ("nullablebool".equals(ctx.lastPush)) {
                ctx.lastPush = "nullablebool";
            } else {
                if (ctx.lastPushWide) {
                    out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
                }
                ctx.lastPush = "bool";
            }
            ctx.lastPushWide = false;
        } else if (ctx.wasi && "toString".equals(kc.methodName())
                && ctx.recordOf(kc.ownerType()) != null) {
            ClassLayout sl = ctx.recordOf(kc.ownerType());
            WasmRecordCode.emitToString(sl, sl.className(), ctx, out, 0);
            ctx.lastPush = "string";
        } else if (ctx.wasi && "equals".equals(kc.methodName())
                && ctx.recordOf(kc.ownerType()) != null) {
            throw new WasmUnsupportedException("chamada explicita de 'equals' de record (WASM002)"
                    + " — o backend WASI ainda nao tem dispatch virtual; o `==` de conteudo ja e"
                    + " suportado (15.3d inc2 C2, kofRecordEq inline). docs/wasm-wasi-plan.md (#776)");
        } else if (ctx.wasi && WasmCollCallCode.emitCollCall(kc, ctx, out)) {
            // as COLECOES (Map/Set/List) — WasmCollCallCode (split check_500)
        } else if (ctx.wasi && WasmBoxRuntime.NAMES.contains(kc.methodName())) {
            // Fase E (27/10, #776): a CAIXA DE ERASURE — o MESMO contrato
            // da nativa §284 (24B [MAGIC][tag][valor]); o join HETEROGENEO
            // do if-expression unifica em Object com os ramos primitivos
            // boxeados (Integer.valueOf/String.valueOf no JVM medido). O
            // resultado e um handle i32 — "object" no rastro, nunca wide.
            String rn = switch (kc.methodName()) {
                case "kof_box_int" -> "kof.boxInt";
                case "kof_box_long" -> "kof.boxLong";
                case "kof_box_bool" -> "kof.boxBool";
                case "kof_box_double" -> "kof.boxDouble";
                default -> "kof.boxToStr";
            };
            out.add(new WasmInstr.Call(rn));
            ctx.lastPush = "object";
            ctx.lastPushWide = false;
        } else if (ctx.wasi && WasmMathRuntime.NAMES.contains(kc.methodName())) {
            // Fase E (26/10, #776): a familia INT do math — o nome DA
            // funcao e' o nome do runtime (kof_math_*, aritmetica i64
            // pura). O rastro de largura TEM de ser atualizado: sem ele o
            // println(isEven(x)) despachava para o println i64 com um
            // resultado i32 (modulo invalido).
            out.add(new WasmInstr.Call(kc.methodName()));
            boolean pred = kc.methodName().startsWith("kof_math_is");
            boolean dbl = kc.methodName().equals("kof_math_roundTo")
                    || kc.methodName().equals("kof_math_dabs")
                    || kc.methodName().equals("kof_math_floor")
                    || kc.methodName().equals("kof_math_ceil")
                    || kc.methodName().equals("kof_math_dmin")
                    || kc.methodName().equals("kof_math_dmax")
                    || kc.methodName().equals("kof_math_dclamp")
                    || kc.methodName().equals("kof_math_sin")
                    || kc.methodName().equals("kof_math_tan")
                    || kc.methodName().equals("kof_math_atan")
                    || kc.methodName().equals("kof_math_asin")
                    || kc.methodName().equals("kof_math_acos")
                    || kc.methodName().equals("kof_math_atan2");
            ctx.lastPush = pred ? "bool" : (dbl ? "double" : "int");
            ctx.lastPushWide = !pred;
        } else if (ctx.wasi && "println".equals(kc.methodName())) {
            WasmPrintCode.emitPrintln(kc, ctx, out);
            // o print CONSOME o valor: limpar o rastro de largura (sem
            // isto o literal 0 do statement seguinte herdava "bool" e
            // saia i32.const no parametro i64 do call seguinte)
            ctx.lastPush = null;
            ctx.lastPushWide = false;
        } else if (kc.kind() == KofCallKind.FUNCTION || kc.kind() == KofCallKind.STATIC) {
            out.add(new WasmInstr.Call(kc.methodName()));
            // CLASS-2b (29/10, #776): o resultado de CLASSE alcançada =
            // TEE no scratch clsIdx (o MESMO save do LoadLocal/campo: a
            // provenância da tag do despacho sobre o retorno de chamada)
            if (ctx.classOf(kc.returnType()) != null) {
                out.add(new WasmInstr.Local(WasmInstr.Local.TEE, ctx.clsIdx, "cls"));
            }
        } else {
            throw new WasmUnsupportedException("chamada '" + kc.methodName()
                    + "' fora do subset escalar 15.2 (WASM002) — docs/wasm-wasi-plan.md (#776)");
        }
    }
}
