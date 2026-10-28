package dev.kof.compiler.wasm;

import dev.kof.compiler.KofCall;
import dev.kof.compiler.Type;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

import java.util.List;

/**
 * Fase E (27/10, #776 — split check_500): o DESPACHO das COLECOES do backend
 * WASI (Map/Set/List), extraido verbatim do {@code WasmCallCode.emitCall}
 * (620 ≥ 600 derrubava o gate CI). Responsabilidade unica: UMA chamada de
 * colecao — as celulas indiretas (WasmMapRuntime/WasmSetRuntime/
 * WasmListRuntime), os slots i64 com as conversoes de largura (bool estende,
 * double vai como BITS f64→i64) e o rastro {@code ctx.lastPush}. As chaves de
 * elem vem do type argument do OWNER (medido no IR). Comportamento identico
 * (freeze rule 3).
 */
final class WasmCollCallCode {

    private WasmCollCallCode() {
    }


    /** A chave/o valor do Map pelo type argument do OWNER (medido no IR:
     *  `Map<String, Int>` chega com typeArguments=[String, int]). */
    private static String mapKeyType(KofCall kc) {
        if (kc.ownerType() instanceof Type.ClassType ct && ct.typeArguments().size() > 0) {
            return typeName(ct.typeArguments().get(0));
        }
        return "";
    }

    private static String mapValueType(KofCall kc) {
        if (kc.ownerType() instanceof Type.ClassType ct && ct.typeArguments().size() > 1) {
            return typeName(ct.typeArguments().get(1));
        }
        return "";
    }


    /** true = a chamada era de colecao e ja emitiu. */
    static boolean emitCollCall(KofCall kc, WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (WasmMapRuntime.NAMES.contains(kc.methodName())) {
            // Fase E (27/10, #776): MAP fatia 1 — Map<String, Int> (o par
            // medido): put [h, key(handle), v(i64)] SEM fantasma; get [h,
            // key] -> o HANDLE DA CAIXA (boxInt no hit, 0=null na falta —
            // o rastro "object" manda pro boxToStr, que renderiza "null");
            // size -> i64. O rastro do tee do desugar e' "map".
            switch (kc.methodName()) {
                case "kof_map_new" -> {
                    out.add(new WasmInstr.Call("kof.mapNew"));
                    ctx.lastPush = "map";
                    ctx.lastPushWide = false;
                }
                case "kof_map_put" -> {
                    // os tipos do par vem do IR (o typer ja' garante a
                    // compatibilidade com o owner — a fatia e' <String, Int>
                    // e, desde a MV (28/10), <String, String>)
                    boolean strVal = "string".equalsIgnoreCase(mapValueType(kc));
                    boolean dblVal = "double".equalsIgnoreCase(mapValueType(kc));
                    boolean boolVal = "bool".equalsIgnoreCase(mapValueType(kc));
                    // FATIA MAP-C (28/10, #776): o valor CHAR = o mapPut CRU
                    // (o Char = o i64 RAW — SEM conversao, o MESMO slot do
                    // Int; o null vive na CAIXA do get, nao no slot)
                    boolean charVal = "char".equalsIgnoreCase(mapValueType(kc));
                    // FATIA MAP-KI (28/10, #776): a chave INT entra com o valor
                    // STRING (o mapPutKi — a busca i64.eq); as demais formas
                    // de chave continuam na fatia String. FATIA MAP-B: o valor
                    // BOOL estende o i32 no call site (o mapPut CRU)
                    boolean strKey = "string".equalsIgnoreCase(mapKeyType(kc));
                    boolean intKey = "int".equalsIgnoreCase(mapKeyType(kc));
                    // FATIA MAP-KI-VAL (28/10, #776): a chave INT admite as
                    // CINCO larguras de valor (o Int/Char = o mapPutKiRaw
                    // CRU; o Double = os bits; o Bool = o estendido; só o
                    // String continua no mapPutKi)
                    if ((!strKey && !intKey)
                            || (!"int".equalsIgnoreCase(mapValueType(kc)) && !strVal && !dblVal && !boolVal && !charVal)) {
                        throw new WasmUnsupportedException("Map<"
                                + mapKeyType(kc) + ", " + mapValueType(kc)
                                + "> fora das fatias Map<String, Int|String|Double|Bool|Char> /"
                                + " Map<Int, Int|String|Double|Bool|Char> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // FATIA MV (28/10, #776): o valor String = o mapPutStr (o
                    // handle ESTENDIDO no slot i64, o res CRU); a largura do
                    // valor esta' na pilha como i32 — o extend fica NO RUNTIME.
                    // FATIA MAP-D (28/10, #776): o valor Double = o mapPut CRU
                    // com os BITS (o reinterpret fica no CALL SITE — o rastro
                    // double = o f64 na pilha; o mapPut pede i64); o res CRU
                    // (o i64 dos bits) nunca consume — dropa na forma VOID da
                    // cadeia (a链 do mapOf) e na forma Nullable
                    if (dblVal) {
                        out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                        out.add(new WasmInstr.Call(intKey ? "kof.mapPutKiRaw" : "kof.mapPut"));
                        if (kc.returnType() instanceof Type.PrimitiveType pt
                                && "void".equalsIgnoreCase(pt.name())) {
                            out.add(new WasmInstr.Simple(0x1a, "drop"));
                        }
                    } else if (boolVal) {
                        // FATIA MAP-B: o bool = o i32 na pilha — o extend para
                        // o slot i64 (o qword zero-estendido, o MESMO do
                        // setAdd do bool) e o put CRU
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                        out.add(new WasmInstr.Call(intKey ? "kof.mapPutKiRaw" : "kof.mapPut"));
                        if (kc.returnType() instanceof Type.PrimitiveType pt
                                && "void".equalsIgnoreCase(pt.name())) {
                            out.add(new WasmInstr.Simple(0x1a, "drop"));
                        }
                    } else if (strVal) {
                        // FATIA MAP-KI: a chave Int = o mapPutKi (a chave raw
                        // i64 no slot; o valor Str estende — o MESMO slot)
                        out.add(new WasmInstr.Call(intKey ? "kof.mapPutKi" : "kof.mapPutStr"));
                        if (kc.returnType() instanceof Type.PrimitiveType pt
                                && "void".equalsIgnoreCase(pt.name())) {
                            out.add(new WasmInstr.Simple(0x1a, "drop"));
                        }
                    } else {
                        // o Int/Char = o CRU (o Char = o MESMO slot do Int —
                        // a lei MAP-C); a chave Int = o mapPutKiRaw
                        out.add(new WasmInstr.Call(intKey ? "kof.mapPutKiRaw" : "kof.mapPut"));
                        if (kc.returnType() instanceof Type.PrimitiveType pt
                                && "void".equalsIgnoreCase(pt.name())) {
                            out.add(new WasmInstr.Simple(0x1a, "drop"));
                        }
                    }
                    // o rastro volta "map": CADA put da cadeia tem o seu Dup
                    // (o tee deixou o original na pilha e ele permanece — a
                    // MESMA lei do add da lista; o null aqui deixava o
                    // segundo put sem receiver: nothing on stack, medido)
                    ctx.lastPush = "map";
                    ctx.lastPushWide = false;
                }
                case "kof_map_contains" -> {
                    // FATIA MAP-KI: a chave Int = o mapContainsKi (o i64.eq)
                    boolean cStrKey = "string".equalsIgnoreCase(mapKeyType(kc));
                    boolean cIntKey = "int".equalsIgnoreCase(mapKeyType(kc));
                    if (!cStrKey && !cIntKey) {
                        throw new WasmUnsupportedException("Map.contains com chave '"
                                + mapKeyType(kc) + "' fora da fatia Map<String, Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // SEM phantom: o IR do contains tem UM param (a chave —
                    // medido; o drop aqui consumia o handle da chave)
                    out.add(new WasmInstr.Call(cIntKey ? "kof.mapContainsKi" : "kof.mapContains"));
                    ctx.lastPush = "bool";
                    ctx.lastPushWide = false;
                }
                case "kof_map_remove" -> {
                    boolean strVal = "string".equalsIgnoreCase(mapValueType(kc));
                    boolean dblVal = "double".equalsIgnoreCase(mapValueType(kc));
                    boolean boolVal = "bool".equalsIgnoreCase(mapValueType(kc));
                    boolean charVal = "char".equalsIgnoreCase(mapValueType(kc));
                    // FATIA MAP-KI (28/10, #776): a chave INT + o valor STRING
                    // = o mapRemoveKi (a busca i64.eq; o rastro continua o
                    // nullablestring do VALOR). FATIA MAP-B: o valor BOOL BOXA
                    // (o boxBool do qword — o rastro nullablebool). FATIA
                    // MAP-C: o valor CHAR BOXA no boxInt (a MESMA caixa do
                    // Int — o rastro nullablechar: o render = o strChar,
                    // NUNCA o boxToStr da tag 0)
                    boolean rStrKey = "string".equalsIgnoreCase(mapKeyType(kc));
                    boolean rIntKey = "int".equalsIgnoreCase(mapKeyType(kc));
                    // FATIA MAP-KI-VAL (28/10, #776): a chave INT admite as
                    // CINCO larguras (o remove = a caixa por largura; o
                    // String continua no mapRemoveKi)
                    if ((!rStrKey && !rIntKey)
                            || (!"int".equalsIgnoreCase(mapValueType(kc)) && !strVal && !dblVal && !boolVal && !charVal)) {
                        throw new WasmUnsupportedException("Map<"
                                + mapKeyType(kc) + ", " + mapValueType(kc)
                                + "> fora das fatias Map<String, Int|String|Double|Bool|Char> /"
                                + " Map<Int, Int|String|Double|Bool|Char> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // FATIA MV: o remove do valor String devolve o CRU; a
                    // FATIA MAP-D: o remove do Double BOXA (o boxDouble dos bits)
                    out.add(new WasmInstr.Call(strVal
                            ? (rIntKey ? "kof.mapRemoveKi" : "kof.mapRemoveStr")
                            : (dblVal ? (rIntKey ? "kof.mapRemoveKiDbl" : "kof.mapRemoveDbl")
                                : (boolVal ? (rIntKey ? "kof.mapRemoveKiBool" : "kof.mapRemoveBool")
                                    : (rIntKey ? "kof.mapRemoveKiInt" : "kof.mapRemove")))));
                    // o remove devolve a CAIXA do valor removido (0=null): a
                    // forma declaracao tem o KofPop (consome); a forma VOID
                    // tem o KofPop DO STATEMENT (o hasReturnValue do Compiler
                    // Comparisons — o mesmo pop dos outros valores). FATIA MV:
                    // o rastro do CRU (o String? no KofPop/renderizar `null`)
                    // e' o nullablestring
                    // FATIA MAP-KI-VAL (28/10, #776): o rastro do valor INT
                    // = "mapval" (o PASSTHROUGH da caixa — o boxToStr renderiza
                    // intToStr/`null` e a cadeia valueOf RE-ARMA; o "object"
                    // puro caia no else do valueOf e era ESMAGADO pelo param
                    // apagado "int" — o writeInt consumia o handle: módulo
                    // inválido, medido nas sondas M2/M3)
                    ctx.lastPush = strVal ? "nullablestring"
                            : (dblVal ? "nullabledouble"
                                : (boolVal ? "nullablebool"
                                    : (charVal ? "nullablechar" : "mapval")));
                    ctx.lastPushWide = false;
                }
                case "kof_map_get" -> {
                    // FATIA MAP-KI: a chave Int = o mapGetKi (o i64.eq)
                    boolean gStrKey = "string".equalsIgnoreCase(mapKeyType(kc));
                    boolean gIntKey = "int".equalsIgnoreCase(mapKeyType(kc));
                    if (!gStrKey && !gIntKey) {
                        throw new WasmUnsupportedException("Map.get com chave '"
                                + mapKeyType(kc) + "' fora da fatia Map<String, Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // FATIA MV: o valor String = o mapGetStr (o CRU — o
                    // mapval passthrough do boxToStr renderiza o String e o
                    // `null` no 0; o MESMO rastro dos dois consumidores). A
                    // FATIA MAP-D: o Double = o mapGetDbl (o boxDouble — o
                    // mapval rota)
                    boolean strVal = "string".equalsIgnoreCase(mapValueType(kc));
                    boolean dblVal = "double".equalsIgnoreCase(mapValueType(kc));
                    boolean boolVal = "bool".equalsIgnoreCase(mapValueType(kc));
                    boolean charVal = "char".equalsIgnoreCase(mapValueType(kc));
                    // FATIA MAP-B: o valor Bool = o mapGetBool (o boxBool)
                    if (!"int".equalsIgnoreCase(mapValueType(kc)) && !strVal && !dblVal && !boolVal && !charVal) {
                        throw new WasmUnsupportedException("Map.get com valor '"
                                + mapValueType(kc) + "' fora da fatia Map<String, Int|String|Double|Bool|Char>"
                                + " (WASM002) — docs/wasm-wasi-plan.md (#776)");
                    }
                    out.add(new WasmInstr.Call(strVal
                            ? (gIntKey ? "kof.mapGetKi" : "kof.mapGetStr")
                            : (dblVal ? (gIntKey ? "kof.mapGetKiDbl" : "kof.mapGetDbl")
                                : (boolVal ? (gIntKey ? "kof.mapGetKiBool" : "kof.mapGetBool")
                                    : (gIntKey ? "kof.mapGetKiInt" : "kof.mapGet")))));
                    // FATIA MAP-C: o valor CHAR anda no MESMO mapGet do Int (a
                    // MESMA caixa boxInt) mas o rastro e' nullablechar — o
                    // render = o strChar (o boxToStr da tag 0 = intToStr viria
                    // "97" para o 'a')
                    ctx.lastPush = charVal ? "nullablechar" : "mapval";
                    ctx.lastPushWide = false;
                }
                case "kof_map_keys", "kof_map_values" -> {
                    // FATIA MAP-KX (28/10, #776): a extração é AGNÓSTICA da
                    // chave — o extract copia os slots 16B RAW (LOAD64→
                    // STORE64): as chaves String = os handles estendidos (as
                    // chaves Int = os i64 cru, a MESMA fatia da lista Int);
                    // o values() do valor Str = os handles (o MESMO do mapa
                    // Str). Medido no MK4: o size/contains/get da lista nas
                    // duas larguras. 
                    // FATIA 3 (27/10): a iteracao — keys/values devolvem a
                    // LISTA nova (o rastro "list" entrega à maquinaria da
                    // LISTA provada: size/get/contains/for-in). A ORDEM nao
                    // e' o contrato (HashMap nao a expoe; a celula WASI e'
                    // de insercao) — as faces de paridade sao livres de
                    // ordem + o get de par unico.
                    out.add(new WasmInstr.Call("kof_map_keys".equals(kc.methodName())
                            ? "kof.mapKeys" : "kof.mapValues"));
                    ctx.lastPush = "list";
                    ctx.lastPushWide = false;
                }
                default -> {                                             // kof_map_size
                    out.add(new WasmInstr.Call("kof.mapSize"));
                    ctx.lastPush = "int";
                    ctx.lastPushWide = true;
                }
            }
            // o despacho do MAP consumiu a chamada (o return da seção — o
            // trim do split da rodada 54 o levou junto: sem ele a chamada
            // caía no fallback e recusava "fora do subset")
            return true;
        } else if (ctx.wasi && WasmCollSetCall.emit(kc, ctx, out)) {
            // o despacho do SET — WasmCollSetCall (split check_500 da rodada 54)
            return true;
        } else if (ctx.wasi && WasmCollListCall.emit(kc, ctx, out)) {
            // o despacho do LIST — WasmCollListCall (split check_500 da rodada 54)
            return true;
        }
        return false;
    }
}
