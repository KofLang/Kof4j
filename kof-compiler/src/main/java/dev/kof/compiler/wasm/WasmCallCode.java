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
        } else if (WasmStrCalls.emit(kc, ctx, out)) {
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
            if (ctx.lastPushWide) {
                out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
            }
            ctx.lastPush = "bool";
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
                    || kc.methodName().equals("kof_math_asin");
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
        } else {
            throw new WasmUnsupportedException("chamada '" + kc.methodName()
                    + "' fora do subset escalar 15.2 (WASM002) — docs/wasm-wasi-plan.md (#776)");
        }
    }
}
