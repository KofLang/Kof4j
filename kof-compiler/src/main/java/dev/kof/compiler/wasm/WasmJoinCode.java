package dev.kof.compiler.wasm;

import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.wasm.WasmLowering;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.Type;

import java.util.List;

import static dev.kof.compiler.wasm.WasmScalarOps.cmp;
import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/**
 * FATIA JOIN (28/10, #776 — split check_500): a MACHINARIA da fronteira de
 * estado do WASI — o ternary do desugar (o print do nullable) produz o
 * operando ANTES do null-check e o VALOR do braco cruza para o join: a DANCA
 * do salto (os operandos do cmp + o under nos scratches), o CROSS do valor do
 * braco (o scratch join) e a entrada do join. Extraída verbatim do
 * {@link WasmLowering} (711 ≥ 600 derrubava o gate CI); comportamento
 * idêntico (freeze rule 3).
 */
final class WasmJoinCode {

    private WasmJoinCode() {
    }

    /**
     * FATIA JOIN (28/10, #776): o ternary do desugar duplica o operando (o
     * KofDup real no rastro de caixa) e o null-check compara a COPIA — a
     * pilha no salto = [under, lhs, rhs] (a profundidade 3). A DANCA: os dois
     * operandos do cmp vao para os scratches, o under (o FUNDO — o braco THEN
     * o consome) tambem, os operandos voltam e o emitCondJump compara. A
     * profundidade > 3 (o concat = [esq, V, copia, rhs]) e os shapes WIDE = a
     * recusa honesta (o slice seguinte).
     */
    static void storeUnderForCondJump(List<KofOperation> ops, int i,
                                      WasmLowering.Ctx ctx, List<WasmInstr> out) {
        java.util.List<String> stk = WasmOperandShape.stackAt(ops, i, ctx);
        int depth = stk.size();
        if (depth > 4) {
            throw new WasmUnsupportedException("pilha com mais de 4 valores no salto"
                    + " condicional (WASM002) — docs/wasm-wasi-plan.md (#776)");
        }
        // A lista = TOP-DOWN: o get(0) = o topo (o rhs), o FUNDO = get(depth-1)
        if (depth == 3 && WasmOperandShape.isBox(stk.get(2))
                && !isWideShape(stk.get(0)) && !isWideShape(stk.get(1))) {
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.cmp1Idx, "c1"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.cmp2Idx, "c2"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.armIdx, "av"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.cmp2Idx, "c2"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.cmp1Idx, "c1"));
            ctx.underPending = true;
            ctx.underTrace = stk.get(0);
        } else if (depth == 4 && WasmOperandShape.isBox(stk.get(2))
                && "string".equals(stk.get(3))
                && !isWideShape(stk.get(0)) && !isWideShape(stk.get(1))) {
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.cmp1Idx, "c1"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.cmp2Idx, "c2"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.armIdx, "av"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.leftIdx, "lv"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.cmp2Idx, "c2"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.cmp1Idx, "c1"));
            ctx.underPending = true;
            ctx.underTrace = stk.get(0);
            ctx.leftPending = true;
            ctx.leftTrace = stk.get(3);
        }
    }

    /** FATIA JOIN (28/10, #776): o valor que cruzou a fronteira chega pelo
     *  scratch (o join do ternary e' a ENTRADA deste estado). */
    static void emitJoinEntry(WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (ctx.joinTrace != null) {
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.joinIdx, "jv"));
            ctx.lastPush = ctx.joinTrace;
            ctx.lastPushWide = false;
            ctx.joinTrace = null;
        }
    }

    /** As formas WIDE (i64/f64) — o under/valor delas fica na recusa honesta. */
    private static boolean isWideShape(String shape) {
        return "int".equals(shape) || "long".equals(shape) || "double".equals(shape)
                || "char".equals(shape);
    }

    /**
     * FATIA JOIN (28/10, #776): o valor vivo na fronteira de bloco CRUZA pelo
     * scratch do join (o braco do ternary empilha o valor; o join o carrega).
     * O wide e a SEGUNDA juncao com trace DIFERENTE (os bracos heterogeneos)
     * ficam na recusa honesta (o recorded slice seguinte).
     */
    static void crossLiveValue(List<KofOperation> ops, int terminatorIdx,
                                       WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (ctx.lastPush != null && !ctx.lastPushWide
                && (ctx.joinTrace == null || ctx.joinTrace.equals(ctx.lastPush))) {
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.joinIdx, "jv"));
            ctx.joinTrace = ctx.lastPush;
            ctx.lastPush = null;
            ctx.lastPushWide = false;
            return;
        }
        refuseLiveStack(ops, terminatorIdx);
    }

    static void refuseLiveStack(List<KofOperation> ops, int terminatorIdx) {
        if (terminatorIdx > 0 && isValueProducer(ops.get(terminatorIdx - 1))) {
            throw new WasmUnsupportedException("valor vivo na fronteira de bloco ("
                    + ops.get(terminatorIdx - 1).getClass().getSimpleName()
                    + ") — a maquina de estados do WASI nao carrega a pilha entre estados"
                    + " (ex.: `a && b` sobre variaveis); WASM002 — docs/wasm-wasi-plan.md (#776)");
        }
    }

    private static boolean isValueProducer(KofOperation op) {
        if (op instanceof KofCall kc) {
            // println/print sao VOID (o WasmPrintCode consome o valor); so uma
            // chamada com retorno deixa valor na pilha
            if ("println".equals(kc.methodName()) || "print".equals(kc.methodName())) return false;
            return kc.returnType() != null && !(kc.returnType() instanceof Type.PrimitiveType pt
                    && "void".equalsIgnoreCase(pt.name()));
        }
        return op instanceof KofLoadLocal || op instanceof KofLoadLiteral
                || op instanceof KofBinary || op instanceof KofUnary
                || op instanceof KofLoadField || op instanceof KofArrayLoad
                || op instanceof KofNewObject;
    }

    static void emitCondJump(KofConditionalJump cj, WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (cj.operandType() instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())) {
            boolean wide = ctx.lastPushWide; // null-check do desugar: i64 p/ bool, i32 p/ handle
            switch (cj.comparison()) {
                case EQ -> out.add(new WasmInstr.Simple(wide ? 0x51 : 0x46, wide ? "i64.eq" : "i32.eq"));
                case NE -> out.add(new WasmInstr.Simple(wide ? 0x52 : 0x47, wide ? "i64.ne" : "i32.ne"));
                default -> throw new WasmUnsupportedException("comparacao de handle '" + cj.comparison()
                        + "' fora do subset (WASM002) — docs/wasm-wasi-plan.md (#776)");
            }
        } else if ("bool".equals(ctx.lastPush)) {
            // Fase E (24/10, #776): a truthiness sobre um BOOL-VARIAVEL —
            // [load b (slot i32 no WASI), literal 0] + este salto. O literal
            // ja sai i32 (o caso especial do KofLoadLiteral mantem
            // lastPush=="bool"), mas o operando do IR carrega o tipo PADRAO
            // INT e o cmp emitia i64.ne sobre dois i32 -> MODULO INVALIDO
            // ("type mismatch: expected i64, found i32" — o defeito medido:
            // `var b = 1 > 2` + `if (b)`; os mesmos produtores de truthiness
            // do if/while/do-while/for/&&). A largura REAL da pilha decide:
            // bool -> i32; so eq/ne fazem sentido sobre um bool.
            switch (cj.comparison()) {
                case EQ -> out.add(new WasmInstr.Simple(0x46, "i32.eq"));
                case NE -> out.add(new WasmInstr.Simple(0x47, "i32.ne"));
                default -> throw new WasmUnsupportedException("comparacao '" + cj.comparison()
                        + "' sobre Bool fora do subset (WASM002) — docs/wasm-wasi-plan.md (#776)");
            }
        } else {
            switch (cj.comparison()) {
                case EQ -> out.add(cmp(cj, "eq", 0x51, 0x61));
                case NE -> out.add(cmp(cj, "ne", 0x52, 0x62));
                case LT -> out.add(cmp(cj, "lt_s", 0x53, 0x63));
                case GT -> out.add(cmp(cj, "gt_s", 0x55, 0x64));
                case LE -> out.add(cmp(cj, "le_s", 0x57, 0x65));
                case GE -> out.add(cmp(cj, "ge_s", 0x59, 0x66));
            }
        }
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        WasmLowering.jumpTo(ctx.blockOf(cj.trueLabel()), 2, ctx, out);
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        WasmLowering.jumpTo(ctx.blockOf(cj.falseLabel()), 2, ctx, out);
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // o salto consumiu os dois operandos: o rastro de largura nao vale mais
        ctx.lastPush = null;
        ctx.lastPushWide = false;
    }

    static void terminateBlock(boolean cleanEnd, List<WasmInstr> out) {
        // ops apos um terminador = CFG nao-linear desconhecido -> bloco so
        // termina limpo se o terminador era a ultima op
        if (!cleanEnd) {
            throw new WasmUnsupportedException("operacoes apos salto/retorno no mesmo bloco"
                    + " (WASM002) — docs/wasm-wasi-plan.md (#776)");
        }
    }
}
