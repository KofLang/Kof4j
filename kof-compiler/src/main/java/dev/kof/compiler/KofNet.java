package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.net} (STDLIB S8, plan §4 — decisão
 * 09/09: 6 escalares em vez de record, pois nenhuma fn de runtime asm devolve
 * objeto estruturado no Native; mesma família do precedente validation).
 *
 * Semântica v1 travada em plan-stdlib-expansion §4 (RFC 3986 subset, escopo
 * honesto): scheme = [A-Za-z][A-Za-z0-9+.-]* antes do 1º ':' (senão "");
 * authority só após "//" (userinfo após o último '@' ignorado; host até o 1º
 * ':' — v1 SEM colchetes IPv6, documentado); path até '?'/'#'; query após o
 * 1º '?' até '#'; fragment após o 1º '#'; campo ausente => ""; null => null;
 * NUNCA lança. queryEncode/Decode = fachada de intenção sobre encoding.url*.
 *
 * NET001 (padrão SECN000/ENC002-histórico): os 6 campos exigem byte-scan no
 * runtime nativo — x86/riscv/aarch ainda sem o port; gate honesto em
 * compile-time, nunca link quebrado. queryEncode/Decode só compõem encoding
 * existente (JVM/JS) — gated junto até o port dos nativos (mesma matriz).
 */
public final class KofNet {

    private KofNet() {}

    private static final Type STR = BuiltinTypes.STRING;

    static final List<String> NAMESPACES = List.of("net");

    static boolean isNetNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record NetCall(String function, Type returnType, List<Type> parameterTypes) {}

    static NetCall staticMethod(String namespace, String name, List<Type> argTypes) {
        int argc = argTypes.size();
        return switch (name) {
            case "scheme", "host", "port", "path", "query", "fragment",
                    "queryEncode", "queryDecode" -> argc == 1
                    ? new NetCall("kof_net_" + name, STR, List.of(STR)) : null;
            default -> null;
        };
    }

    static boolean supportedOn(String function, Target target) {
        // NET001: byte-scan nativo pendente (port x86/riscv em unidades
        // próprias); JVM/SCRIPT/JS já implementados.
        return target != Target.NATIVE && target != Target.NATIVE_RISCV64
                && target != Target.NATIVE_AARCH64;
    }

    static String gapCode(String function) {
        return "NET001";
    }
}
