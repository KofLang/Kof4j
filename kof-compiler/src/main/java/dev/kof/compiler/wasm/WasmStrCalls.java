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
                        out.add(new WasmInstr.Call("kof.strIndexOf"));
                        ctx.lastPush = "long";
                        ctx.lastPushWide = true;
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
