package dev.kof.compiler.wasm;

import dev.kof.compiler.KofCall;

import java.util.List;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/**
 * Roteamento das chamadas de STRING do lowering WASI (TIER 15, #776): operador
 * `==`/`!=` e os metodos `length`/`isEmpty`/`equals`/`startsWith`/`endsWith`/
 * `concat`. Separado de {@link WasmLowering} pelo gate `check_500`.
 *
 * <p>Todas apontam para helpers ja existentes e provados verdes
 * ({@code kof.strEq}/{@code kof.strConcat}) ou para os de
 * {@link WasmStrInspect}. O ponto que importa: o handle guarda BYTES UTF-8,
 * enquanto a semantica do Kof e' de CODE UNITS UTF-16 — por isso `length`
 * conta leaders e `charAt`/`substring`/`indexOf` NAO entram aqui (precisariam
 * da mesma varredura; ficam como face seguinte).
 */
final class WasmStrCalls {

    private WasmStrCalls() {
    }

    /** {@code true} se a chamada foi tratada aqui. */
    static boolean emit(KofCall kc, WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (!ctx.wasi) return false;
        String m = kc.methodName();
        switch (m) {
            case "kof_string_concat" -> {
                out.add(new WasmInstr.Call("kof.strConcat"));
                ctx.lastPush = "string";
                return true;
            }
            case "kof_string_equals" -> {
                // `a == b` / `a != b` (o `!=` acrescenta `== 0` DEPOIS desta chamada).
                out.add(new WasmInstr.Call("kof.strEq"));
                ctx.lastPush = "bool";
                ctx.lastPushWide = false;
                return true;
            }
            default -> {
                if (!"string".equalsIgnoreCase(typeName(kc.ownerType()))) return false;
                switch (m) {
                    case "length" -> {
                        out.add(new WasmInstr.Call("kof.strLen"));
                        ctx.lastPush = "long";
                        ctx.lastPushWide = true;
                        return true;
                    }
                    case "isEmpty" -> {
                        out.add(new WasmInstr.Call("kof.strIsEmpty"));
                        ctx.lastPush = "bool";
                        ctx.lastPushWide = false;
                        return true;
                    }
                    case "equals" -> {
                        out.add(new WasmInstr.Call("kof.strEq"));
                        ctx.lastPush = "bool";
                        ctx.lastPushWide = false;
                        return true;
                    }
                    case "startsWith" -> {
                        // `startsWith(str, fromIndex)` empilha um TERCEIRO
                        // operando (i64) que o helper de 2 handles nao consome —
                        // sem esta guarda o modulo saia INVALIDO (host recusa)
                        // em vez de recusar honesto na compilacao (Q7/R6).
                        if (kc.parameterTypes().size() != 1) {
                            throw new WasmUnsupportedException("String.startsWith com "
                                    + kc.parameterTypes().size() + " argumentos (fromIndex) "
                                    + "fora da fatia F (WASM002) — so' a forma de 1 argumento;"
                                    + " docs/wasm-wasi-plan.md (#776)");
                        }
                        out.add(new WasmInstr.Call("kof.strStarts"));
                        ctx.lastPush = "bool";
                        ctx.lastPushWide = false;
                        return true;
                    }
                    case "endsWith" -> {
                        out.add(new WasmInstr.Call("kof.strEnds"));
                        ctx.lastPush = "bool";
                        ctx.lastPushWide = false;
                        return true;
                    }
                    case "indexOf" -> {
                        if (kc.parameterTypes().isEmpty()) {
                            throw new WasmUnsupportedException("String.indexOf sem argumentos"
                                    + " (WASM002); docs/wasm-wasi-plan.md (#776)");
                        }
                        // A pilha e' [receiver, str, (from)] e a assinatura do
                        // helper e' (a, b, from) — MESMA ordem, sem reordenar
                        // operandos (era o que quebrava a tentativa anterior).
                        if (kc.parameterTypes().size() == 1) {
                            out.add(new WasmInstr.Const(1, 0));   // from = 0
                        }
                        out.add(new WasmInstr.Call("kof.strIndexOf"));
                        ctx.lastPush = "long";
                        ctx.lastPushWide = true;
                        return true;
                    }
                    case "lastIndexOf" -> {
                        if (kc.parameterTypes().isEmpty()) {
                            throw new WasmUnsupportedException("String.lastIndexOf sem"
                                    + " argumentos (WASM002); docs/wasm-wasi-plan.md (#776)");
                        }
                        if (kc.parameterTypes().size() == 1) {
                            // "sem limite": from no maximo, para todos os acertos
                            // contarem sem precisar de um caminho separado.
                            out.add(new WasmInstr.Const(1, Long.MAX_VALUE));
                        }
                        out.add(new WasmInstr.Call("kof.strLastIndexOf"));
                        ctx.lastPush = "long";
                        ctx.lastPushWide = true;
                        return true;
                    }
                    case "contains" -> {
                        // contains(t) === indexOf(t) >= 0 — reusa a busca ja'
                        // provada; from = 0, e o resultado e' um Bool.
                        out.add(new WasmInstr.Const(1, 0));   // from = 0
                        out.add(new WasmInstr.Call("kof.strIndexOf"));
                        out.add(new WasmInstr.Const(1, 0));
                        out.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
                        ctx.lastPush = "bool";
                        ctx.lastPushWide = false;
                        return true;
                    }
                    case "charAt" -> {
                        // Entrega o code unit como Char (o Kof Char e' i64 na
                        // pilha — ver emitLiteral).
                        out.add(new WasmInstr.Call("kof.strCharAt"));
                        ctx.lastPush = "char";
                        ctx.lastPushWide = true;
                        return true;
                    }
                    case "substring" -> {
                        // `substring(i)` (1 arg) chega so' com o indice: sintetiza
                        // o sentinela j = -1 que `kof.strSub` traduz p/ "ate' o fim".
                        if (kc.parameterTypes().size() == 1) {
                            out.add(new WasmInstr.Const(1, -1));
                        }
                        out.add(new WasmInstr.Call("kof.strSub"));
                        ctx.lastPush = "string";
                        ctx.lastPushWide = false;
                        return true;
                    }
                    case "replace" -> {
                        // SO' a forma (String, String) esta implementada. A de
                        // (Char, Char) empilha dois i64 (Char e' i64 na pilha),
                        // enquanto o helper espera dois HANDLES i32 — sem esta
                        // guarda o modulo saia INVALIDO (host recusa) em vez de
                        // recusar honesto na compilacao (Q7/R6).
                        if (!kc.parameterTypes().isEmpty()
                                && kc.parameterTypes().stream().anyMatch(
                                        t -> "char".equalsIgnoreCase(typeName(t)))) {
                            throw new WasmUnsupportedException("String.replace(Char, Char) "
                                    + "fora da fatia F (WASM002) — so' a forma"
                                    + " (String, String); docs/wasm-wasi-plan.md (#776)");
                        }
                        out.add(new WasmInstr.Call("kof.strReplace"));
                        ctx.lastPush = "string";
                        ctx.lastPushWide = false;
                        return true;
                    }
                    case "compareTo" -> {
                        out.add(new WasmInstr.Call("kof.strCompare"));
                        ctx.lastPush = "long";
                        ctx.lastPushWide = true;
                        return true;
                    }
                    case "trim" -> {
                        out.add(new WasmInstr.Call("kof.strTrim"));
                        ctx.lastPush = "string";
                        ctx.lastPushWide = false;
                        return true;
                    }
                    case "concat" -> {
                        out.add(new WasmInstr.Call("kof.strConcat"));
                        ctx.lastPush = "string";
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        }
    }
}
