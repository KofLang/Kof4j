package dev.kof.compiler.js;

import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.Type;
import dev.kof.compiler.js.JsIr;

import java.util.ArrayList;
import java.util.List;

/**
 * As operações de MEMBRO de String do backend JS (o switch de
 * {@code handleStringOp}), extraídas do JsCallEmitter pelo ratchet de 500
 * linhas; o comportamento é o mesmo, linha a linha.
 *
 * <p>§102/§730/§738/§739/§741: os membros cujo {@code String.prototype} do
 * host diverge do JDK (startsWith/from, charAt, substring, trim, equals
 * ignore-case, replace de agulha vazia) baixam para helpers de runtime com os
 * clamps do JDK; o resto é mapeamento direto.
 */
final class JsStringOpEmitter {

    private JsStringOpEmitter() {}

    /** §264: wrapper-boxed Double/Float (o typer boxou o receiver de `toString`). */
    static boolean isJdkWrapperFp(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        return inner instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                && ("Double".equals(ct.name()) || "Float".equals(ct.name()));
    }


    static void handleStringOp(JsCallEmitter call, JsMethodParser p, MethodCtx ctx, List<Object> stack,
                                List<JsIr.JsExpression> preambleExprs, KofCall kc,
                                JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        // §102 (paridade absoluta): com 2 args (needle + from), o
        // String.prototype do JS diverge do JDK no clamp do `from`
        // (lastIndexOf(from<0) JS=0 vs JDK=-1; startsWith(from>len) JS=true vs
        // JDK=false; vazio+from JS difere). Baixa p/ helper top-level com os
        // clamps do JDK. 1-arg cai no default (nativo, bate o JDK).
        if (args.size() >= 2) {
            String s2fn = switch (kc.methodName()) {
                case "indexOf" -> "kof_string_index_of2";
                case "lastIndexOf" -> "kof_string_last_index_of2";
                case "startsWith" -> "kof_string_starts_with2";
                default -> null;
            };
            if (s2fn != null) {
                ctx.lc.registerRuntime(s2fn);
                List<JsIr.JsExpression> full = new ArrayList<>();
                full.add(receiver);
                full.addAll(args);
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(s2fn), full));
                return;
            }
        }
        switch (kc.methodName()) {
            case "kof_string_concat" -> stack.add(new JsIr.JsBinary(args.get(0), "+", args.get(1)));
            case "kof_string_equals" -> stack.add(new JsIr.JsConditional(
                    new JsIr.JsBinary(args.get(0), "===", args.get(1)),
                    new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
            case "valueOf" -> {
                // §682: o `String.valueOf` também chega por AQUI (não só pelo
                // ramo isJdkValueOfOwner) — o `String(x)` cru usava o
                // `Array.prototype.toString`/`[object Set]` do host para
                // List/Set/Map/array, divergindo do JVM. kofFormat é o
                // formatador de container e é idêntico ao String(x) fora deles.
                Type vt = kc.parameterTypes().isEmpty() ? null : kc.parameterTypes().get(0);
                if (vt == null || JsCallEmitter.isCollectionValue(vt) || JsCallEmitter.isErasedType(vt) || BuiltinTypes.isObject(vt)) {
                    call.emitContainerFormat(stack, args.get(0), vt);
                } else {
                    stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String"), List.of(args.get(0))));
                }
            }
            case "charAt" -> {
                // §730: o host `charCodeAt` devolve NaN fora de faixa (→ "");
                // o JVM/Script lançam `Index i out of bounds for length N`.
                p.lc.registerRuntime("kofStrCharAt");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStrCharAt"),
                        List.of(receiver, args.get(0))));
            }
            case "substring" -> {
                // §730: o `String.prototype.substring` do host CLAMPA e TROCA
                // os args; o JVM/Script lançam `Range [a, b) out of bounds ...`.
                p.lc.registerRuntime("kofStrSubstring");
                java.util.List<JsIr.JsExpression> sa = new java.util.ArrayList<>();
                sa.add(receiver);
                sa.addAll(args);
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStrSubstring"), sa));
            }
            case "trim" -> {
                // §739: o trim do HOST nao segue o contrato do JVM (ve o helper).
                p.lc.registerRuntime("kofStrTrim");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStrTrim"),
                        List.of(receiver)));
            }
            case "length" -> stack.add(new JsIr.JsMember(receiver, "length"));
            // §145 (12/09, #101): String.prototype NÃO tem isEmpty (é Java);
            // o default gerava `t.isEmpty()` = TypeError. length === 0.
            case "isEmpty" -> stack.add(new JsIr.JsBinary(
                    new JsIr.JsMember(receiver, "length"), "===",
                    new JsIr.JsNumber("0")));
            case "equals" -> stack.add(new JsIr.JsBinary(receiver, "===", args.get(0)));
            case "equalsIgnoreCase" -> {
                // §741: o toUpperCase do HOST nao segue o contrato do JVM
                // (fold char-a-char 1:1, upper OU lower) -> helper de runtime.
                p.lc.registerRuntime("kofStrEqIgnoreCase");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStrEqIgnoreCase"),
                        List.of(receiver, args.get(0))));
            }
            case "replace" -> {
                // §738: a agulha VAZIA divergia (SILENCIOSO, regra 5) — a baixa
                // `split(from).join(to)` não gera elemento de BORDA, e o
                // JVM/Script inserem `to` em TODA posição (`-a-X-b-X-c-`). O
                // IR do JS não tem nó condicional de EXPRESSÃO, então o ramo
                // vazio vive no helper `kofStrReplace`; o ramo não-vazio é a
                // baixa anterior, preservada. With two String arguments the
                // args are used as-is; with two characters (Kof Ints) they are
                // converted with String.fromCharCode.
                Type first = !kc.parameterTypes().isEmpty() ? kc.parameterTypes().get(0) : null;
                boolean charArgs = first instanceof Type.PrimitiveType pt
                        && "char".equals(Type.canonicalPrimitiveName(pt.name()));
                JsIr.JsExpression from = charArgs
                        ? new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("String"), "fromCharCode"),
                                List.of(args.get(0)))
                        : args.get(0);
                JsIr.JsExpression to = charArgs
                        ? new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("String"), "fromCharCode"),
                                List.of(args.get(1)))
                        : args.get(1);
                p.lc.registerRuntime("kofStrReplace");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStrReplace"),
                        List.of(receiver, from, to)));
            }
            // Conversões String→número (github #51): não existem em String.prototype;
            // o backend JS baixa p/ helper top-level do runtime (paridade JVM/Native:
            // parseInt(s.trim()), erro de parse → exceção). Antes caíam no default
            // e geravam `texto.kof_string_to_int()` → TypeError em runtime.
            case "kof_string_to_int", "kof_string_to_long",
                 "kof_string_to_double", "kof_string_to_float" -> {
                ctx.lc.registerRuntime(kc.methodName());
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(kc.methodName()), List.of(receiver)));
            }
            // bug 97 (face JS): hashCode/compareTo NÃO existem em
            // String.prototype → o default mapeava p/ `a.compareTo()` =
            // TypeError. hashCode reusa kofHashCode (bug 42 — 31*h+unit
            // UTF-16, mesmo algoritmo JVM/x86); compareTo baixa p/ helper
            // kofStringCompareTo (walk de code units, semântica JVM).
            case "hashCode" -> {
                ctx.lc.registerRuntime("kofHashCode");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofHashCode"), List.of(receiver)));
            }
            case "compareTo" -> {
                ctx.lc.registerRuntime("kofStringCompareTo");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStringCompareTo"),
                        List.of(receiver, args.get(0))));
            }
            // D-FULL-PARITY-050 row 11: compareToIgnoreCase baixa para helper
            // kofStringCompareToIgnoreCase (CASE_INSENSITIVE_ORDER do JVM: fold
            // SIMPLES por code unit — upper, senão lower; expandir nao cabe).
            case "compareToIgnoreCase" -> {
                ctx.lc.registerRuntime("kofStringCompareToIgnoreCase");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStringCompareToIgnoreCase"),
                        List.of(receiver, args.get(0))));
            }
            // D-FULL-PARITY-050 row 11: String.prototype NÃO tem toCharArray →
            // o default gerava TypeError. Helper kofToCharArray (array de code
            // units UTF-16, igual ao JVM).
            case "toCharArray" -> {
                ctx.lc.registerRuntime("kofToCharArray");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofToCharArray"),
                        List.of(receiver)));
            }
            case "split" -> {
                // §111: JS String.prototype.split PRESERVA vazios trailing
                // ("a,".split(",")=["a",""]) mas o contrato é o Java
                // (remove trailing, exceto input "" → [""]). helper kofSplit.
                ctx.lc.registerRuntime("kofSplit");
                List<JsIr.JsExpression> sa = new ArrayList<>();
                sa.add(receiver);
                sa.addAll(args);
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofSplit"), sa));
            }
            default -> {
                // substring, contains, indexOf, trim, toUpperCase, toLowerCase,
                // startsWith, endsWith, concat, split — direct JS mapping.
                JsIr.JsExpression method = "contains".equals(kc.methodName())
                        ? new JsIr.JsMember(receiver, "includes")
                        : new JsIr.JsMember(receiver, JsTypeMapper.sanitizeName(kc.methodName()));
                stack.add(new JsIr.JsCall(method, args));
            }
        }
    }

}
